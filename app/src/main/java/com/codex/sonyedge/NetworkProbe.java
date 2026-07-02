package com.codex.sonyedge;

import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class NetworkProbe {
    private static final int[] COMMON_PORTS = {10000, 8080, 80, 64321, 52323, 3333, 544, 21};

    private NetworkProbe() {
    }

    public static Set<String> expandCandidates(Set<String> initialUrls, String gateway, StringBuilder log) {
        Set<String> hosts = new LinkedHashSet<>();
        for (String url : initialUrls) {
            String host = hostFromUrl(url);
            if (host != null && !host.isEmpty()) {
                hosts.add(host);
            }
        }
        if (gateway != null && !gateway.isEmpty() && !"0.0.0.0".equals(gateway)) {
            hosts.add(gateway);
        }
        hosts.add("192.168.122.1");
        hosts.add("10.0.0.1");
        hosts.add("192.168.0.1");
        hosts.add("192.168.1.1");

        Set<String> urls = new LinkedHashSet<>(initialUrls);
        log.append("TCP service scan\n");
        for (String host : hosts) {
            List<Integer> openPorts = scanHost(host);
            log.append(host).append(" open ports: ").append(openPorts).append('\n');
            for (int port : openPorts) {
                String baseUrl = "http://" + host + ":" + port;
                urls.add(baseUrl);
                log.append("  ").append(baseUrl).append(" GET / -> ").append(httpGetProbe(baseUrl)).append('\n');
                log.append("  ").append(baseUrl).append(" GET /sony -> ").append(httpGetProbe(baseUrl + "/sony")).append('\n');
            }
        }
        log.append('\n');
        return urls;
    }

    private static List<Integer> scanHost(String host) {
        List<Integer> open = new ArrayList<>();
        for (int port : COMMON_PORTS) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 550);
                open.add(port);
            } catch (Exception ignored) {
                // Closed, filtered, or unreachable.
            }
        }
        return open;
    }

    private static String hostFromUrl(String url) {
        try {
            return URI.create(url).getHost();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String httpGetProbe(String url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(700);
            connection.setReadTimeout(900);
            connection.setRequestMethod("GET");
            int code = connection.getResponseCode();
            String contentType = connection.getContentType();
            return "HTTP " + code + (contentType == null ? "" : " " + contentType);
        } catch (Exception ex) {
            return ex.getClass().getSimpleName();
        }
    }
}
