package com.codex.sonyedge;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

public final class DiscoveryClient {
    private static final String TAG_SSDP = "SonyEdge-SSDP";
    private static final String TAG_UPNP = "SonyEdge-UPnP";
    private static final String SSDP_ADDRESS = "239.255.255.250";
    private static final int SSDP_PORT = 1900;
    private static final int SSDP_MX_SECONDS = 2;
    private static final int SSDP_RECEIVE_TIMEOUT_MS = 300;
    private static final int SSDP_DISCOVERY_WINDOW_MS = 4500;
    private static final int SSDP_LOCATION_SETTLE_MS = 700;
    private static final int DESCRIPTION_CONNECT_TIMEOUT_MS = 3000;
    private static final int DESCRIPTION_READ_TIMEOUT_MS = 4000;
    private static final int MAX_DESCRIPTION_BYTES = 1024 * 1024;
    private static final String[] SEARCH_TARGETS = {
            "ssdp:all",
            "upnp:rootdevice",
            "urn:schemas-upnp-org:device:MediaServer:1",
            "urn:schemas-upnp-org:service:ContentDirectory:1",
            "urn:schemas-sony-com:service:XPushList:1",
            "urn:schemas-sony-com:service:ScalarWebAPI:1"
    };

    private final Context context;

    public DiscoveryClient(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Runs Sony and standard UPnP discovery and returns validated HTTP 200 responses. */
    public List<SsdpResponse> discoverSsdpResponses(StringBuilder log) {
        LinkedHashMap<String, SsdpResponse> responses = new LinkedHashMap<>();
        Set<String> discoveredLocations = new LinkedHashSet<>();
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiManager.MulticastLock lock = null;
        try {
            if (wifiManager != null) {
                lock = wifiManager.createMulticastLock("sonyedge-ssdp");
                lock.setReferenceCounted(false);
                lock.acquire();
            }

            InetAddress multicastAddress = InetAddress.getByName(SSDP_ADDRESS);
            try (DatagramSocket socket = new MulticastSocket()) {
                socket.setReuseAddress(true);
                socket.setBroadcast(true);
                socket.setSoTimeout(SSDP_RECEIVE_TIMEOUT_MS);

                for (int pass = 0; pass < 2; pass++) {
                    for (String target : SEARCH_TARGETS) {
                        byte[] payload = buildSearchRequest(target);
                        socket.send(new DatagramPacket(payload, payload.length, multicastAddress, SSDP_PORT));
                        Log.d(TAG_SSDP, "M-SEARCH pass=" + (pass + 1) + " st=" + target);
                    }
                    if (pass == 0) {
                        Thread.sleep(180L);
                    }
                }

                long searchesSentAt = System.currentTimeMillis();
                long deadline = searchesSentAt + SSDP_DISCOVERY_WINDOW_MS;
                long lastNewSonyLocationAt = 0L;
                Set<String> sonyLocations = new LinkedHashSet<>();
                while (System.currentTimeMillis() < deadline) {
                    long now = System.currentTimeMillis();
                    if (lastNewSonyLocationAt > 0L
                            && now - searchesSentAt >= SSDP_MX_SECONDS * 1000L
                            && now - lastNewSonyLocationAt >= SSDP_LOCATION_SETTLE_MS) {
                        append(log, "SSDP discovery settled after " + responses.size()
                                + " response type(s), " + discoveredLocations.size() + " location(s)");
                        break;
                    }
                    byte[] buffer = new byte[16 * 1024];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    try {
                        socket.receive(packet);
                    } catch (java.net.SocketTimeoutException timeout) {
                        continue;
                    }
                    String raw = new String(
                            packet.getData(),
                            packet.getOffset(),
                            packet.getLength(),
                            StandardCharsets.ISO_8859_1
                    );
                    SsdpResponse response = SsdpResponse.parse(raw, packet.getAddress());
                    if (response == null) {
                        append(log, "Ignored malformed SSDP response from " + packet.getAddress().getHostAddress());
                        continue;
                    }
                    if (response.statusCode != 200 || response.location.isEmpty()) {
                        append(log, "Ignored SSDP status=" + response.statusCode + " location=" + response.location);
                        continue;
                    }
                    String key = response.location + '\n' + response.usn + '\n' + response.searchTarget;
                    boolean newResponse = !responses.containsKey(key);
                    responses.put(key, response);
                    discoveredLocations.add(response.location);
                    if (isSonyResponse(response) && sonyLocations.add(response.location)) {
                        lastNewSonyLocationAt = System.currentTimeMillis();
                    }
                    if (!newResponse) {
                        continue;
                    }
                    String summary = "SSDP status=" + response.statusCode + " st=" + response.searchTarget
                            + " location=" + response.location + " usn=" + response.usn
                            + " server=" + response.server + " cache=" + response.cacheControl;
                    append(log, summary);
                    Log.d(TAG_SSDP, summary);
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            append(log, "SSDP discovery interrupted");
            Log.w(TAG_SSDP, "Discovery interrupted", interrupted);
        } catch (Exception ex) {
            append(log, "SSDP discovery failed: " + message(ex));
            Log.w(TAG_SSDP, "Discovery failed", ex);
        } finally {
            if (lock != null && lock.isHeld()) {
                lock.release();
            }
        }
        return new ArrayList<>(responses.values());
    }

    /** Fetches each unique SSDP LOCATION once and parses the full UPnP device description. */
    public List<SonyDeviceDescription> discoverDevices(StringBuilder log) {
        LinkedHashMap<String, String> locations = new LinkedHashMap<>();
        for (SsdpResponse response : discoverSsdpResponses(log)) {
            try {
                URL url = new URL(response.location);
                String normalized = url.toURI().normalize().toString();
                locations.put(normalized, normalized);
            } catch (Exception invalidLocation) {
                append(log, "Invalid SSDP LOCATION " + response.location + ": " + message(invalidLocation));
            }
        }

        List<SonyDeviceDescription> devices = new ArrayList<>();
        for (String location : locations.values()) {
            try {
                devices.add(fetchDeviceDescription(location, log));
            } catch (Exception ex) {
                append(log, "Device description failed " + location + ": " + message(ex));
                Log.w(TAG_UPNP, "Description failed: " + location, ex);
            }
        }
        return devices;
    }

    private static boolean isSonyResponse(SsdpResponse response) {
        return response != null
                && response.server != null
                && response.server.toLowerCase(java.util.Locale.US).contains("sony");
    }

    public SonyDeviceDescription fetchDeviceDescription(String location, StringBuilder log) throws Exception {
        URL descriptionUrl = new URL(location);
        byte[] xml = fetchDescriptionBytes(descriptionUrl);
        Document document = parseXml(xml);
        Element rootDevice = firstElement(document.getDocumentElement(), "device");
        if (rootDevice == null) {
            throw new IllegalArgumentException("UPnP description has no device element");
        }

        String urlBaseText = directChildText(document.getDocumentElement(), "URLBase");
        URL resolutionBase = urlBaseText.isEmpty()
                ? descriptionUrl
                : normalizeUrlBase(new URL(descriptionUrl, urlBaseText.trim()));

        List<SonyServiceDescription> services = new ArrayList<>();
        collectUpnpServices(rootDevice, resolutionBase, services);
        collectScalarServices(document.getDocumentElement(), resolutionBase, services);

        SonyDeviceDescription device = new SonyDeviceDescription(
                descriptionUrl.toString(),
                urlBaseText.isEmpty() ? resolutionBase.toString() : new URL(descriptionUrl, urlBaseText.trim()).toString(),
                directChildText(rootDevice, "deviceType"),
                directChildText(rootDevice, "friendlyName"),
                directChildText(rootDevice, "manufacturer"),
                resolveOptionalUrl(resolutionBase, directChildText(rootDevice, "manufacturerURL")),
                directChildText(rootDevice, "modelDescription"),
                directChildText(rootDevice, "modelName"),
                directChildText(rootDevice, "modelNumber"),
                resolveOptionalUrl(resolutionBase, directChildText(rootDevice, "modelURL")),
                directChildText(rootDevice, "serialNumber"),
                directChildText(rootDevice, "UDN"),
                resolveOptionalUrl(resolutionBase, directChildText(rootDevice, "presentationURL")),
                services
        );
        String summary = "Device " + device.friendlyName + " model=" + device.modelName
                + " udn=" + device.udn + " services=" + device.services.size();
        append(log, summary);
        Log.i(TAG_UPNP, summary);
        return device;
    }

    /** Compatibility API: returns ScalarWebAPI host roots discovered from device descriptions. */
    public Set<String> discoverBaseUrls(StringBuilder log) {
        Set<String> urls = new LinkedHashSet<>();
        for (SonyDeviceDescription device : discoverDevices(log)) {
            for (SonyServiceDescription service : device.services) {
                if (service.isScalarWebApi() && !service.actionListUrl.isEmpty()) {
                    String baseUrl = trimSonyPath(service.actionListUrl);
                    urls.add(baseUrl);
                    append(log, "ScalarWebAPI base URL: " + baseUrl);
                }
            }
        }
        return urls;
    }

    /** Compatibility API. Host guesses are intentionally ignored; LOCATION comes from SSDP. */
    public Set<String> discoverKnownDescriptionUrls(Set<String> hosts, StringBuilder log) {
        return discoverBaseUrls(log);
    }

    /** Compatibility API. Host guesses are intentionally ignored; LOCATION comes from SSDP. */
    public List<DmsServiceInfo> discoverDmsServices(Set<String> hosts, StringBuilder log) {
        List<DmsServiceInfo> result = new ArrayList<>();
        for (SonyDeviceDescription device : discoverDevices(log)) {
            for (SonyServiceDescription service : device.services) {
                if (service.isContentDirectory() && !service.controlUrl.isEmpty()) {
                    result.add(new DmsServiceInfo(device, service));
                }
            }
        }
        return result;
    }

    /** Compatibility API. Host guesses are intentionally ignored; LOCATION comes from SSDP. */
    public DmsServiceInfo discoverFirstDmsService(Set<String> hosts, StringBuilder log) {
        List<DmsServiceInfo> services = discoverDmsServices(hosts, log);
        return services.isEmpty() ? null : services.get(0);
    }

    private void collectUpnpServices(Element element, URL base, List<SonyServiceDescription> output) {
        if ("service".equals(localName(element))) {
            String serviceType = directChildText(element, "serviceType");
            if (!serviceType.isEmpty()) {
                SonyServiceDescription service = SonyServiceDescription.upnp(
                        serviceType,
                        directChildText(element, "serviceId"),
                        resolveOptionalUrl(base, directChildText(element, "SCPDURL")),
                        resolveOptionalUrl(base, directChildText(element, "controlURL")),
                        resolveOptionalUrl(base, directChildText(element, "eventSubURL"))
                );
                output.add(service);
                Log.d(TAG_UPNP, "Service " + service.serviceType + " control=" + service.controlUrl);
            }
        }
        for (Element child : directChildren(element)) {
            collectUpnpServices(child, base, output);
        }
    }

    private void collectScalarServices(Element element, URL base, List<SonyServiceDescription> output) {
        if ("X_ScalarWebAPI_Service".equals(localName(element))) {
            SonyServiceDescription service = SonyServiceDescription.scalarWebApi(
                    directChildText(element, "X_ScalarWebAPI_ServiceType"),
                    resolveOptionalUrl(base, directChildText(element, "X_ScalarWebAPI_ActionList_URL")),
                    directChildText(element, "X_ScalarWebAPI_AccessType")
            );
            if (!service.actionListUrl.isEmpty()) {
                output.add(service);
                Log.d(TAG_UPNP, "Scalar service " + service.apiType + " action=" + service.actionListUrl);
            }
        }
        for (Element child : directChildren(element)) {
            collectScalarServices(child, base, output);
        }
    }

    private byte[] buildSearchRequest(String target) {
        String request = "M-SEARCH * HTTP/1.1\r\n"
                + "HOST: " + SSDP_ADDRESS + ':' + SSDP_PORT + "\r\n"
                + "MAN: \"ssdp:discover\"\r\n"
                + "MX: " + SSDP_MX_SECONDS + "\r\n"
                + "ST: " + target + "\r\n"
                + "USER-AGENT: Android UPnP/1.0 SonyEdge/" + BuildConfig.VERSION_NAME + "\r\n\r\n";
        return request.getBytes(StandardCharsets.ISO_8859_1);
    }

    private byte[] fetchDescriptionBytes(URL location) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) location.openConnection();
        connection.setConnectTimeout(DESCRIPTION_CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(DESCRIPTION_READ_TIMEOUT_MS);
        connection.setRequestProperty("Accept", "application/xml, text/xml, */*");
        connection.setRequestProperty(
                "User-Agent",
                "UPnP/1.0 DLNADOC/1.50 SonyEdge/" + BuildConfig.VERSION_NAME
        );
        connection.setRequestProperty("Connection", "close");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new java.io.IOException("HTTP " + status);
            }
            try (BufferedInputStream input = new BufferedInputStream(connection.getInputStream());
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int total = 0;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_DESCRIPTION_BYTES) {
                        throw new java.io.IOException("Device description exceeds " + MAX_DESCRIPTION_BYTES + " bytes");
                    }
                    output.write(buffer, 0, read);
                }
                Log.d(TAG_UPNP, "Fetched " + location + " bytes=" + total);
                return output.toByteArray();
            }
        } finally {
            connection.disconnect();
        }
    }

    private Document parseXml(byte[] xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        try {
            factory.setXIncludeAware(false);
        } catch (UnsupportedOperationException unsupported) {
            Log.d(TAG_UPNP, "XInclude configuration unsupported by Android XML parser");
        }
        try {
            factory.setExpandEntityReferences(false);
        } catch (UnsupportedOperationException unsupported) {
            Log.d(TAG_UPNP, "Entity expansion configuration unsupported by Android XML parser");
        }
        setFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        setFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        setFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
        setFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        setFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        try {
            factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "");
            factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "");
        } catch (IllegalArgumentException unsupported) {
            Log.d(TAG_UPNP, "External XML access attributes unsupported");
        }
        return factory.newDocumentBuilder().parse(new InputSource(new ByteArrayInputStream(xml)));
    }

    private void setFeature(DocumentBuilderFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (Exception unsupported) {
            Log.d(TAG_UPNP, "XML feature unsupported: " + feature);
        }
    }

    private Element firstElement(Element parent, String wantedLocalName) {
        if (wantedLocalName.equals(localName(parent))) {
            return parent;
        }
        for (Element child : directChildren(parent)) {
            Element result = firstElement(child, wantedLocalName);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private List<Element> directChildren(Element parent) {
        List<Element> result = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                result.add((Element) node);
            }
        }
        return result;
    }

    private String directChildText(Element parent, String wantedLocalName) {
        for (Element child : directChildren(parent)) {
            if (wantedLocalName.equals(localName(child))) {
                String text = child.getTextContent();
                return text == null ? "" : text.trim();
            }
        }
        return "";
    }

    private String localName(Node node) {
        String local = node.getLocalName();
        if (local != null && !local.isEmpty()) {
            return local;
        }
        String name = node.getNodeName();
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }

    private URL normalizeUrlBase(URL base) throws Exception {
        String text = base.toString();
        return text.endsWith("/") ? base : new URL(text + '/');
    }

    private String resolveOptionalUrl(URL base, String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        try {
            return new URL(base, value.trim()).toString();
        } catch (Exception ex) {
            Log.w(TAG_UPNP, "Invalid relative URL: " + value, ex);
            return "";
        }
    }

    private String trimSonyPath(String actionListUrl) {
        String value = actionListUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.endsWith("/sony")) {
            value = value.substring(0, value.length() - "/sony".length());
        }
        return value;
    }

    private void append(StringBuilder log, String line) {
        if (log != null) {
            log.append(line).append('\n');
        }
    }

    private String message(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }
}
