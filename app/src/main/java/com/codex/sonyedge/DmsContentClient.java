package com.codex.sonyedge;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

public final class DmsContentClient {
    private static final int BROWSE_PAGE_SIZE = 32;
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 30000;

    public interface ProgressListener {
        void onProgress(String message);
    }

    public interface PageListener {
        void onPage(DmsBrowseResult pageResult, int loaded, int total);
    }

    private final DmsServiceInfo serviceInfo;
    private final StringBuilder log;
    private final ProgressListener progressListener;

    public DmsContentClient(DmsServiceInfo serviceInfo, StringBuilder log) {
        this(serviceInfo, log, null);
    }

    public DmsContentClient(DmsServiceInfo serviceInfo, StringBuilder log, ProgressListener progressListener) {
        this.serviceInfo = serviceInfo;
        this.log = log;
        this.progressListener = progressListener;
    }

    public List<CameraContentItem> discoverImages() throws Exception {
        List<CameraContentItem> items = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        browseRecursive("0", items, visited, 0);
        return items;
    }

    public DmsBrowseResult browseDirectChildren(String objectId) throws Exception {
        return browseDirectChildren(objectId, null);
    }

    public DmsBrowseResult browseDirectChildren(String objectId, PageListener pageListener) throws Exception {
        progress("DMS folder " + objectId);
        DmsBrowseResult result = new DmsBrowseResult(objectId);
        int start = 0;
        while (true) {
            BrowsePage page = browsePage(objectId, start);
            DmsBrowseResult pageResult = new DmsBrowseResult(objectId);
            addPageItems(page.document, pageResult);
            result.containers.addAll(pageResult.containers);
            result.items.addAll(pageResult.items);
            int loaded = start + page.numberReturned;
            if (pageListener != null) {
                pageListener.onPage(pageResult, loaded, page.totalMatches);
            }
            if (page.numberReturned <= 0 || loaded >= page.totalMatches) {
                break;
            }
            start = loaded;
            progress("DMS folder " + objectId + " loading " + start + "/" + page.totalMatches
                    + " (" + result.items.size() + " photos)");
        }
        progress("DMS folder " + objectId + " -> " + result.containers.size() + " folders, " + result.items.size() + " files");
        return result;
    }

    private void addPageItems(Document document, DmsBrowseResult result) {
        NodeList containers = document.getElementsByTagNameNS("*", "container");
        for (int i = 0; i < containers.getLength(); i++) {
            Element container = (Element) containers.item(i);
            String id = container.getAttribute("id");
            String title = text(container, "title");
            int childCount = parseInt(container.getAttribute("childCount"), -1);
            if (id != null && !id.isEmpty()) {
                result.containers.add(new DmsContainerItem(id, title == null || title.isEmpty() ? id : title, childCount));
            }
        }

        NodeList itemNodes = document.getElementsByTagNameNS("*", "item");
        for (int i = 0; i < itemNodes.getLength(); i++) {
            Element item = (Element) itemNodes.item(i);
            CameraContentItem parsed = parseItem(item);
            if (parsed.hasDownloadUrl()) {
                result.items.add(parsed);
            }
        }
    }

    private void browseRecursive(String objectId, List<CameraContentItem> items, Set<String> visited, int depth) throws Exception {
        if (depth > 3 || !visited.add(objectId)) {
            return;
        }
        progress("DMS Browse " + objectId);
        Document document = browsePage(objectId, 0).document;
        NodeList containers = document.getElementsByTagNameNS("*", "container");
        int traversed = 0;
        for (int i = 0; i < containers.getLength(); i++) {
            Element container = (Element) containers.item(i);
            String id = container.getAttribute("id");
            String title = text(container, "title");
            log.append("DMS container ").append(id).append(" ").append(title).append('\n');
            if (id != null && !id.isEmpty() && traversed < 12) {
                traversed++;
                browseRecursive(id, items, visited, depth + 1);
            }
        }

        NodeList itemNodes = document.getElementsByTagNameNS("*", "item");
        for (int i = 0; i < itemNodes.getLength(); i++) {
            Element item = (Element) itemNodes.item(i);
            CameraContentItem parsed = parseItem(item);
            if (parsed.hasDownloadUrl()) {
                items.add(parsed);
            }
        }
        progress("DMS Browse " + objectId + " -> " + items.size() + " items so far");
    }

    private BrowsePage browsePage(String objectId, int startingIndex) throws Exception {
        String body = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" "
                + "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">"
                + "<s:Body>"
                + "<u:Browse xmlns:u=\"urn:schemas-upnp-org:service:ContentDirectory:1\">"
                + "<ObjectID>" + escapeXml(objectId) + "</ObjectID>"
                + "<BrowseFlag>BrowseDirectChildren</BrowseFlag>"
                + "<Filter>*</Filter>"
                + "<StartingIndex>" + startingIndex + "</StartingIndex>"
                + "<RequestedCount>" + BROWSE_PAGE_SIZE + "</RequestedCount>"
                + "<SortCriteria></SortCriteria>"
                + "</u:Browse>"
                + "</s:Body>"
                + "</s:Envelope>";

        HttpURLConnection connection = (HttpURLConnection) new URL(serviceInfo.controlUrl).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"");
        connection.setRequestProperty("SOAPAction", "\"urn:schemas-upnp-org:service:ContentDirectory:1#Browse\"");
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(payload.length);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(payload);
        }

        int code = connection.getResponseCode();
        InputStream input = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        byte[] bytes = readAll(input);
        String response = new String(bytes, StandardCharsets.UTF_8);
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("DMS Browse HTTP " + code + ": " + response);
        }
        log.append("DMS Browse(").append(objectId).append(") OK\n");

        Document soap = parseXml(response);
        String didl = firstText(soap, "Result");
        if (didl == null || didl.isEmpty()) {
            return new BrowsePage(parseXml("<DIDL-Lite/>"), 0, 0);
        }
        int numberReturned = parseInt(firstText(soap, "NumberReturned"), 0);
        int totalMatches = parseInt(firstText(soap, "TotalMatches"), numberReturned);
        if (totalMatches <= 0) {
            totalMatches = numberReturned;
        }
        return new BrowsePage(parseXml(didl), numberReturned, totalMatches);
    }

    private static final class BrowsePage {
        final Document document;
        final int numberReturned;
        final int totalMatches;

        BrowsePage(Document document, int numberReturned, int totalMatches) {
            this.document = document;
            this.numberReturned = numberReturned;
            this.totalMatches = totalMatches;
        }
    }

    private CameraContentItem parseItem(Element item) {
        String title = text(item, "title");
        String contentClass = text(item, "class");
        NodeList resources = item.getElementsByTagNameNS("*", "res");
        ResourceCandidate best = null;
        ResourceCandidate preview = null;
        List<ResourceCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < resources.getLength(); i++) {
            Element res = (Element) resources.item(i);
            String candidate = res.getTextContent();
            String protocolInfo = res.getAttribute("protocolInfo");
            if (candidate != null && candidate.startsWith("http")) {
                ResourceCandidate resource = new ResourceCandidate(
                        candidate.trim(),
                        protocolInfo,
                        parseLong(res.getAttribute("size"), -1),
                        parseInt(res.getAttribute("resolutionWidth"), -1),
                        parseInt(res.getAttribute("resolutionHeight"), -1),
                        parseResolution(res.getAttribute("resolution"))
                );
                candidates.add(resource);
                if (best == null || resource.score(title) > best.score(title)) {
                    best = resource;
                }
                if (resource.isPreview() && (preview == null || resource.previewScore() > preview.previewScore())) {
                    preview = resource;
                }
            }
        }
        String id = item.getAttribute("id");
        String url = best == null ? "" : best.url;
        String previewUrl = preview == null ? "" : preview.url;
        long size = best == null ? -1 : best.size;
        log.append("DMS item ").append(title == null || title.isEmpty() ? id : title)
                .append(" resources=").append(candidates.size())
                .append(" selected=").append(shortUrl(url))
                .append(" preview=").append(shortUrl(previewUrl))
                .append(" score=").append(best == null ? -1 : best.score(title))
                .append('\n');
        return new CameraContentItem(
                title == null || title.isEmpty() ? id : title,
                id,
                contentClass,
                previewUrl,
                previewUrl,
                url,
                size,
                itemToString(item)
        );
    }

    private static final class ResourceCandidate {
        final String url;
        final String protocolInfo;
        final long size;
        final int width;
        final int height;

        ResourceCandidate(String url, String protocolInfo, long size, int width, int height, int[] parsedResolution) {
            this.url = url;
            this.protocolInfo = protocolInfo == null ? "" : protocolInfo;
            this.size = size;
            int parsedWidth = parsedResolution == null ? -1 : parsedResolution[0];
            int parsedHeight = parsedResolution == null ? -1 : parsedResolution[1];
            this.width = width > 0 ? width : parsedWidth;
            this.height = height > 0 ? height : parsedHeight;
        }

        int score(String title) {
            String lowerUrl = url.toLowerCase(Locale.US);
            String lowerTitle = title == null ? "" : title.toLowerCase(Locale.US);
            int score = 0;
            if (protocolInfo.toLowerCase(Locale.US).contains("jpeg")) {
                score += 1000;
            }
            if (looksOriginal(lowerUrl, lowerTitle)) {
                score += 6000;
            }
            if (lowerUrl.contains("/org_") || lowerUrl.contains("org_") || lowerUrl.contains("original")) {
                score += 5000;
            }
            if (lowerUrl.matches(".*[/_]dsc[0-9].*")) {
                score += 3000;
            }
            if (lowerUrl.contains("/lrg_") || lowerUrl.contains("lrg_") || lowerUrl.contains("large")) {
                score -= 2500;
            }
            if (lowerUrl.contains("/sm_") || lowerUrl.contains("thumb") || lowerUrl.contains("thumbnail")) {
                score -= 4000;
            }
            if (width > 0 && height > 0) {
                score += Math.min(width * height / 1000, 4000);
            }
            if (size > 0) {
                score += Math.min((int) (size / 1024), 4000);
            }
            return score;
        }

        private boolean looksOriginal(String lowerUrl, String lowerTitle) {
            String titleBase = lowerTitle;
            int dot = titleBase.lastIndexOf('.');
            if (dot > 0) {
                titleBase = titleBase.substring(0, dot);
            }
            return !titleBase.isEmpty()
                    && lowerUrl.contains(titleBase)
                    && !lowerUrl.contains("lrg_" + titleBase)
                    && !lowerUrl.contains("sm_" + titleBase);
        }

        boolean isPreview() {
            String lowerUrl = url.toLowerCase(Locale.US);
            if (lowerUrl.contains("lrg_") || lowerUrl.contains("large")
                    || lowerUrl.contains("thumb") || lowerUrl.contains("thumbnail")
                    || lowerUrl.contains("/sm_") || lowerUrl.contains("sm_")) {
                return true;
            }
            if (width > 0 && height > 0 && width * height <= 4_000_000) {
                return true;
            }
            return size > 0 && size <= 3_000_000;
        }

        int previewScore() {
            String lowerUrl = url.toLowerCase(Locale.US);
            int score = 0;
            if (lowerUrl.contains("lrg_") || lowerUrl.contains("large")) {
                score += 4000;
            }
            if (lowerUrl.contains("thumb") || lowerUrl.contains("thumbnail") || lowerUrl.contains("sm_")) {
                score += 1000;
            }
            if (width > 0 && height > 0) {
                score += Math.min(width * height / 1000, 2500);
            }
            if (size > 0) {
                score += Math.min((int) (size / 1024), 2500);
            }
            if (lowerUrl.contains("org_") || lowerUrl.contains("original")) {
                score -= 5000;
            }
            return score;
        }
    }

    private static String text(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) {
            return "";
        }
        return nodes.item(0).getTextContent();
    }

    private static String firstText(Document document, String localName) {
        NodeList nodes = document.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) {
            return "";
        }
        return nodes.item(0).getTextContent();
    }

    private static Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new InputSource(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));
    }

    private static byte[] readAll(InputStream input) throws Exception {
        if (input == null) {
            return new byte[0];
        }
        byte[] buffer = new byte[8192];
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String itemToString(Node node) {
        return node == null ? "" : node.getTextContent();
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int[] parseResolution(String resolution) {
        if (resolution == null || !resolution.contains("x")) {
            return null;
        }
        String[] parts = resolution.split("x");
        if (parts.length != 2) {
            return null;
        }
        int width = parseInt(parts[0], -1);
        int height = parseInt(parts[1], -1);
        if (width <= 0 || height <= 0) {
            return null;
        }
        return new int[]{width, height};
    }

    private static String shortUrl(String url) {
        if (url == null || url.length() <= 120) {
            return url;
        }
        return url.substring(0, 120) + "...";
    }

    private void progress(String message) {
        log.append(message).append('\n');
        if (progressListener != null) {
            progressListener.onProgress(message);
        }
    }
}
