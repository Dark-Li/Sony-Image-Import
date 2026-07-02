package com.codex.sonyedge;

import android.content.Context;
import android.net.wifi.WifiManager;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

public final class DiscoveryClient {
    private static final String SEARCH_TARGET = "urn:schemas-sony-com:service:ScalarWebAPI:1";
    private static final String[] KNOWN_DESCRIPTION_PATHS = {
            "/scalarwebapi_dd.xml",
            "/ScalarWebAPI.xml",
            "/dd.xml",
            "/device.xml",
            "/DmsDesc.xml",
            "/sony/ScalarWebAPI.xml"
    };

    private final Context context;

    public DiscoveryClient(Context context) {
        this.context = context.getApplicationContext();
    }

    public Set<String> discoverBaseUrls(StringBuilder log) {
        Set<String> urls = new LinkedHashSet<>();
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiManager.MulticastLock lock = null;
        try {
            if (wifiManager != null) {
                lock = wifiManager.createMulticastLock("sonyedge-ssdp");
                lock.setReferenceCounted(false);
                lock.acquire();
            }

            String request = "M-SEARCH * HTTP/1.1\r\n"
                    + "HOST: 239.255.255.250:1900\r\n"
                    + "MAN: \"ssdp:discover\"\r\n"
                    + "MX: 1\r\n"
                    + "ST: " + SEARCH_TARGET + "\r\n\r\n";
            byte[] payload = request.getBytes(StandardCharsets.UTF_8);

            try (DatagramSocket socket = new MulticastSocket()) {
                socket.setReuseAddress(true);
                socket.setBroadcast(true);
                socket.setSoTimeout(1500);
                socket.send(new DatagramPacket(
                        payload,
                        payload.length,
                        InetAddress.getByName("239.255.255.250"),
                        1900
                ));

                long deadline = System.currentTimeMillis() + 3500;
                while (System.currentTimeMillis() < deadline) {
                    byte[] buffer = new byte[8192];
                    DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                    try {
                        socket.receive(response);
                    } catch (Exception timeout) {
                        break;
                    }
                    String text = new String(response.getData(), response.getOffset(), response.getLength(), StandardCharsets.UTF_8);
                    log.append("SSDP response:\n").append(text).append("\n");
                    String location = headerValue(text, "LOCATION");
                    if (location != null) {
                        urls.addAll(parseScalarUrls(location, log));
                    }
                }
            }
        } catch (Exception ex) {
            log.append("SSDP discovery failed: ").append(ex.getMessage()).append("\n");
        } finally {
            if (lock != null && lock.isHeld()) {
                lock.release();
            }
        }
        return urls;
    }

    public Set<String> discoverKnownDescriptionUrls(Set<String> hosts, StringBuilder log) {
        Set<String> urls = new LinkedHashSet<>();
        for (String location : knownDescriptionLocations(hosts)) {
            urls.addAll(parseScalarUrls(location, log));
        }
        return urls;
    }

    public List<DmsServiceInfo> discoverDmsServices(Set<String> hosts, StringBuilder log) {
        List<DmsServiceInfo> services = new ArrayList<>();
        for (String location : knownDescriptionLocations(hosts)) {
            services.addAll(parseDmsServices(location, log));
        }
        return services;
    }

    public DmsServiceInfo discoverFirstDmsService(Set<String> hosts, StringBuilder log) {
        String[] fastPaths = {"/dd.xml", "/DmsDesc.xml", "/device.xml", "/scalarwebapi_dd.xml"};
        for (String host : hosts) {
            if (host == null || host.isEmpty() || "0.0.0.0".equals(host)) {
                continue;
            }
            for (String path : fastPaths) {
                String location = "http://" + host + ":64321" + path;
                List<DmsServiceInfo> services = parseDmsServices(location, log);
                if (!services.isEmpty()) {
                    return services.get(0);
                }
            }
        }
        return null;
    }

    private List<String> knownDescriptionLocations(Set<String> hosts) {
        List<String> locations = new ArrayList<>();
        for (String host : hosts) {
            if (host == null || host.isEmpty() || "0.0.0.0".equals(host)) {
                continue;
            }
            for (String path : KNOWN_DESCRIPTION_PATHS) {
                locations.add("http://" + host + ":64321" + path);
            }
        }
        return locations;
    }

    private Set<String> parseScalarUrls(String location, StringBuilder log) {
        Set<String> baseUrls = new LinkedHashSet<>();
        try {
            Document document = fetchDescription(location, log);
            NodeList actionUrls = document.getElementsByTagName("X_ScalarWebAPI_ActionList_URL");
            for (int i = 0; i < actionUrls.getLength(); i++) {
                String actionListUrl = actionUrls.item(i).getTextContent();
                if (actionListUrl != null && !actionListUrl.isEmpty()) {
                    String baseUrl = trimSonyPath(actionListUrl);
                    log.append("ScalarWebAPI base URL: ").append(baseUrl).append('\n');
                    baseUrls.add(baseUrl);
                }
            }
        } catch (Exception ex) {
            log.append("Cannot parse device description ").append(location).append(": ").append(ex.getMessage()).append("\n");
        }
        return baseUrls;
    }

    private List<DmsServiceInfo> parseDmsServices(String location, StringBuilder log) {
        List<DmsServiceInfo> services = new ArrayList<>();
        try {
            Document document = fetchDescription(location, log);
            URL descriptionUrl = new URL(location);
            NodeList serviceNodes = document.getElementsByTagName("service");
            for (int i = 0; i < serviceNodes.getLength(); i++) {
                Element service = (Element) serviceNodes.item(i);
                String serviceType = childText(service, "serviceType");
                String controlUrl = childText(service, "controlURL");
                log.append("UPnP service: ").append(serviceType).append(" control=").append(controlUrl).append('\n');
                if (serviceType != null && serviceType.contains("ContentDirectory")
                        && controlUrl != null && !controlUrl.isEmpty()) {
                    services.add(new DmsServiceInfo(location, serviceType, resolveUrl(descriptionUrl, controlUrl)));
                }
            }
        } catch (Exception ex) {
            log.append("DMS parse failed ").append(location).append(": ").append(ex.getMessage()).append('\n');
        }
        return services;
    }

    private Document fetchDescription(String location, StringBuilder log) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(location).openConnection();
        connection.setConnectTimeout(2500);
        connection.setReadTimeout(2500);
        StringBuilder xml = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                xml.append(line);
            }
        }
        log.append("Device description: ").append(location).append("\n");

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        return factory
                .newDocumentBuilder()
                .parse(new InputSource(new ByteArrayInputStream(xml.toString().getBytes(StandardCharsets.UTF_8))));
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

    private String childText(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return "";
        }
        return nodes.item(0).getTextContent();
    }

    private String resolveUrl(URL base, String path) throws Exception {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        return new URL(base, path).toString();
    }

    private String headerValue(String response, String key) {
        String[] lines = response.split("\\r?\\n");
        for (String line : lines) {
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String name = line.substring(0, colon).trim();
            if (name.equalsIgnoreCase(key)) {
                return line.substring(colon + 1).trim();
            }
        }
        return null;
    }
}
