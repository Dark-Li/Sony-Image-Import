package com.codex.sonyedge;

import android.util.Log;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

public final class UpnpSoapClient {
    private static final String TAG = "SonyEdge-SOAP";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 30000;
    private static final int MAX_RESPONSE_BYTES = 16 * 1024 * 1024;

    public interface Logger {
        void log(String message);
    }

    public static final class Response {
        public final String action;
        public final int httpStatus;
        public final Document document;

        Response(String action, int httpStatus, Document document) {
            this.action = action;
            this.httpStatus = httpStatus;
            this.document = document;
        }

        public String text(String localName) {
            return firstText(document, localName);
        }

        public Element responseElement() {
            NodeList nodes = document.getElementsByTagNameNS("*", action + "Response");
            return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
        }
    }

    private final String controlUrl;
    private final String serviceType;
    private final Logger logger;

    public UpnpSoapClient(String controlUrl, String serviceType) {
        this(controlUrl, serviceType, null);
    }

    public UpnpSoapClient(String controlUrl, String serviceType, Logger logger) {
        if (controlUrl == null || controlUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("UPnP control URL is required");
        }
        if (serviceType == null || serviceType.trim().isEmpty()) {
            throw new IllegalArgumentException("UPnP service type is required");
        }
        this.controlUrl = controlUrl.trim();
        this.serviceType = serviceType.trim();
        this.logger = logger;
    }

    public Response call(String action) throws UpnpException {
        return call(action, Collections.emptyMap());
    }

    public Response call(String action, Map<String, ?> arguments) throws UpnpException {
        if (action == null || action.trim().isEmpty()) {
            throw new IllegalArgumentException("UPnP action is required");
        }
        String normalizedAction = action.trim();
        Map<String, ?> safeArguments = arguments == null ? Collections.emptyMap() : arguments;
        byte[] payload = envelope(normalizedAction, safeArguments).getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(controlUrl).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"");
            connection.setRequestProperty("User-Agent", "UPnP/1.0 DLNADOC/1.50");
            connection.setRequestProperty("Connection", "close");
            connection.setRequestProperty(
                    "X-AV-Client-Info",
                    "av=5.0; cn=SonyEdge; mn=SonyEdge; mv=" + BuildConfig.VERSION_NAME
            );
            connection.setRequestProperty("SOAPAction", "\"" + serviceType + "#" + normalizedAction + "\"");
            connection.setFixedLengthStreamingMode(payload.length);
            log("SOAP -> " + normalizedAction + " " + controlUrl + " service=" + serviceType);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(payload);
            }

            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            byte[] bytes = readAll(stream);
            String xml = new String(bytes, StandardCharsets.UTF_8);
            Document document = xml.trim().isEmpty() ? parseXml(emptyEnvelope()) : parseXml(xml);
            Element fault = firstElement(document, "Fault");
            if (fault != null || status < 200 || status >= 300) {
                throw fault(normalizedAction, status, document, xml);
            }
            log("SOAP <- " + normalizedAction + " HTTP " + status + " bytes=" + bytes.length);
            return new Response(normalizedAction, status, document);
        } catch (UpnpException exception) {
            log("SOAP !! " + exception.getMessage());
            throw exception;
        } catch (Exception exception) {
            UpnpException wrapped = new UpnpException(normalizedAction, exception.toString(), exception);
            log("SOAP !! " + wrapped.getMessage());
            throw wrapped;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public static Map<String, Object> arguments(Object... namesAndValues) {
        if (namesAndValues == null || namesAndValues.length == 0) {
            return Collections.emptyMap();
        }
        if ((namesAndValues.length & 1) != 0) {
            throw new IllegalArgumentException("Arguments must be name/value pairs");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < namesAndValues.length; index += 2) {
            result.put(String.valueOf(namesAndValues[index]), namesAndValues[index + 1]);
        }
        return result;
    }

    public static Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        setFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        setFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
        setFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        setFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        setFeature(factory, "http://javax.xml.XMLConstants/feature/secure-processing", true);
        try {
            factory.setXIncludeAware(false);
        } catch (UnsupportedOperationException ignored) {
            // Not all Android XML providers implement XInclude.
        }
        try {
            factory.setExpandEntityReferences(false);
        } catch (UnsupportedOperationException ignored) {
            // External entities are independently disabled above.
        }
        try {
            factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "");
            factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "");
        } catch (IllegalArgumentException ignored) {
            // Older Android XML implementations may not expose JAXP access attributes.
        }
        return factory.newDocumentBuilder().parse(new InputSource(new ByteArrayInputStream(
                xml.getBytes(StandardCharsets.UTF_8))));
    }

    public static String firstText(Document document, String localName) {
        Element element = firstElement(document, localName);
        return element == null ? "" : value(element.getTextContent());
    }

    public static String childText(Element parent, String localName) {
        if (parent == null) {
            return "";
        }
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            if (node instanceof Element && localName.equals(node.getLocalName())) {
                return value(node.getTextContent());
            }
        }
        NodeList descendants = parent.getElementsByTagNameNS("*", localName);
        return descendants.getLength() == 0 ? "" : value(descendants.item(0).getTextContent());
    }

    private String envelope(String action, Map<String, ?> arguments) {
        StringBuilder body = new StringBuilder(512)
                .append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
                .append("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" ")
                .append("s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">")
                .append("<s:Body><u:").append(action).append(" xmlns:u=\"")
                .append(escapeXml(serviceType)).append("\">");
        for (Map.Entry<String, ?> argument : arguments.entrySet()) {
            String name = argument.getKey();
            if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_.-]*")) {
                throw new IllegalArgumentException("Invalid SOAP argument name: " + name);
            }
            body.append('<').append(name).append('>')
                    .append(escapeXml(argument.getValue() == null ? "" : String.valueOf(argument.getValue())))
                    .append("</").append(name).append('>');
        }
        return body.append("</u:").append(action).append("></s:Body></s:Envelope>").toString();
    }

    private UpnpException fault(String action, int status, Document document, String rawResponse) {
        String faultCode = firstText(document, "faultcode");
        String faultString = firstText(document, "faultstring");
        int errorCode = parseInt(firstText(document, "errorCode"), -1);
        String errorDescription = firstText(document, "errorDescription");
        String detail = faultCode.isEmpty() && faultString.isEmpty() && errorCode < 0
                ? abbreviate(rawResponse, 512)
                : "";
        return new UpnpException(action, status, faultCode, faultString, errorCode,
                errorDescription, detail);
    }

    private static Element firstElement(Document document, String localName) {
        if (document == null) {
            return null;
        }
        NodeList nodes = document.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() > 0) {
            return (Element) nodes.item(0);
        }
        nodes = document.getElementsByTagName(localName);
        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }

    private static byte[] readAll(InputStream input) throws Exception {
        if (input == null) {
            return new byte[0];
        }
        try (InputStream source = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = source.read(buffer)) != -1) {
                total += read;
                if (total > MAX_RESPONSE_BYTES) {
                    throw new IllegalStateException("UPnP response exceeds " + MAX_RESPONSE_BYTES + " bytes");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static void setFeature(DocumentBuilderFactory factory, String feature, boolean enabled) {
        try {
            factory.setFeature(feature, enabled);
        } catch (Exception ignored) {
            // Android XML parsers vary; all supported external-entity controls are applied.
        }
    }

    private static String emptyEnvelope() {
        return "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body/></s:Envelope>";
    }

    private static String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(value(text));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String value(String text) {
        return text == null ? "" : text.trim();
    }

    private static String abbreviate(String text, int maxLength) {
        String normalized = value(text).replaceAll("\\s+", " ");
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
    }

    private void log(String message) {
        Log.d(TAG, message);
        if (logger != null) {
            logger.log(message);
        }
    }
}
