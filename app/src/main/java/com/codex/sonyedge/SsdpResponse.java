package com.codex.sonyedge;

import java.net.InetAddress;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class SsdpResponse {
    public final String statusLine;
    public final int statusCode;
    public final String location;
    public final String usn;
    public final String searchTarget;
    public final String server;
    public final String cacheControl;
    public final String sourceAddress;
    public final Map<String, String> headers;

    private SsdpResponse(
            String statusLine,
            int statusCode,
            String location,
            String usn,
            String searchTarget,
            String server,
            String cacheControl,
            String sourceAddress,
            Map<String, String> headers
    ) {
        this.statusLine = statusLine;
        this.statusCode = statusCode;
        this.location = location;
        this.usn = usn;
        this.searchTarget = searchTarget;
        this.server = server;
        this.cacheControl = cacheControl;
        this.sourceAddress = sourceAddress;
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
    }

    static SsdpResponse parse(String raw, InetAddress source) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String[] lines = raw.split("\\r?\\n");
        if (lines.length == 0) {
            return null;
        }
        String statusLine = lines[0].trim();
        int statusCode = parseStatusCode(statusLine);
        if (!statusLine.toUpperCase(Locale.US).startsWith("HTTP/") || statusCode < 0) {
            return null;
        }
        Map<String, String> headers = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String name = lines[i].substring(0, colon).trim().toLowerCase(Locale.US);
            String value = lines[i].substring(colon + 1).trim();
            if (!headers.containsKey(name)) {
                headers.put(name, value);
            }
        }
        return new SsdpResponse(
                statusLine,
                statusCode,
                value(headers, "location"),
                value(headers, "usn"),
                value(headers, "st"),
                value(headers, "server"),
                value(headers, "cache-control"),
                source == null ? "" : source.getHostAddress(),
                headers
        );
    }

    private static int parseStatusCode(String statusLine) {
        String[] parts = statusLine.split("\\s+", 3);
        if (parts.length < 2) {
            return -1;
        }
        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static String value(Map<String, String> headers, String name) {
        String value = headers.get(name);
        return value == null ? "" : value;
    }
}
