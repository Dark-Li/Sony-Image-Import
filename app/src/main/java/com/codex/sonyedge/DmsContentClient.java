package com.codex.sonyedge;

import android.util.Log;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class DmsContentClient {
    private static final String TAG = "SonyEdge-DMS";
    private static final int BROWSE_PAGE_SIZE = 32;
    private static final ConcurrentHashMap<String, SortState> SORT_STATE_CACHE = new ConcurrentHashMap<>();

    public interface ProgressListener {
        void onProgress(String message);
    }

    public interface PageListener {
        void onPage(DmsBrowseResult pageResult, int loaded, int total);
    }

    private final DmsServiceInfo serviceInfo;
    private final StringBuilder log;
    private final ProgressListener progressListener;
    private final UpnpSoapClient soapClient;
    private final String sortCacheKey;
    private boolean sortCapabilitiesLoaded;
    private String sortCapabilities = "";
    private String sortCriteria = "";

    public DmsContentClient(DmsServiceInfo serviceInfo, StringBuilder log) {
        this(serviceInfo, log, null);
    }

    public DmsContentClient(DmsServiceInfo serviceInfo, StringBuilder log, ProgressListener progressListener) {
        if (serviceInfo == null) {
            throw new IllegalArgumentException("ContentDirectory service is required");
        }
        this.serviceInfo = serviceInfo;
        this.log = log == null ? new StringBuilder() : log;
        this.progressListener = progressListener;
        String advertisedType = value(serviceInfo.serviceType).isEmpty()
                ? "urn:schemas-upnp-org:service:ContentDirectory:1"
                : serviceInfo.serviceType;
        this.soapClient = new UpnpSoapClient(serviceInfo.controlUrl, advertisedType, this::progress);
        this.sortCacheKey = buildSortCacheKey(serviceInfo);
    }

    public List<CameraContentItem> discoverImages() throws Exception {
        List<CameraContentItem> items = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        browseRecursive("0", items, visited, 0);
        return items;
    }

    public String getSortCapabilities() {
        ensureSortCapabilities();
        return sortCapabilities;
    }

    public DmsBrowseResult browseDirectChildren(String objectId) throws Exception {
        return browseDirectChildren(objectId, null);
    }

    public DmsBrowseResult browseDirectChildren(String objectId, PageListener pageListener) throws Exception {
        String safeObjectId = value(objectId).isEmpty() ? "0" : objectId;
        ensureSortCapabilities();
        progress("DMS folder " + safeObjectId + " sort=" + display(sortCriteria));
        DmsBrowseResult result = new DmsBrowseResult(safeObjectId);
        result.sortCriteria = sortCriteria;
        int start = 0;
        while (true) {
            BrowsePage page = browsePage(safeObjectId, start);
            result.sortCriteria = sortCriteria;
            DmsBrowseResult pageResult = new DmsBrowseResult(safeObjectId);
            pageResult.numberReturned = page.numberReturned;
            pageResult.totalMatches = page.totalMatches;
            pageResult.updateId = page.updateId;
            pageResult.sortCriteria = sortCriteria;
            addPageItems(page.document, pageResult);
            result.containers.addAll(pageResult.containers);
            result.items.addAll(pageResult.items);
            result.numberReturned += page.numberReturned;
            result.totalMatches = page.totalMatches;
            result.updateId = page.updateId;

            int loaded = start + page.numberReturned;
            if (pageListener != null) {
                pageListener.onPage(pageResult, loaded, page.totalMatches);
            }
            if (page.numberReturned <= 0 || loaded <= start || loaded >= page.totalMatches) {
                break;
            }
            start = loaded;
            progress("DMS folder " + safeObjectId + " loading " + start + "/" + page.totalMatches
                    + " (" + result.items.size() + " photos)");
        }
        progress("DMS folder " + safeObjectId + " -> " + result.containers.size() + " folders, "
                + result.items.size() + " files, total=" + result.totalMatches + " updateId=" + result.updateId);
        return result;
    }

    private void ensureSortCapabilities() {
        if (sortCapabilitiesLoaded) {
            return;
        }
        SortState cached = SORT_STATE_CACHE.get(sortCacheKey);
        if (cached != null) {
            applySortState(cached);
            return;
        }
        synchronized (this) {
            if (sortCapabilitiesLoaded) {
                return;
            }
            synchronized (SORT_STATE_CACHE) {
                cached = SORT_STATE_CACHE.get(sortCacheKey);
                if (cached == null) {
                    try {
                        UpnpSoapClient.Response response = soapClient.call("GetSortCapabilities");
                        String capabilities = response.text("SortCaps");
                        cached = new SortState(capabilities, chooseSortCriteria(capabilities));
                        progress("DMS sort capabilities=" + display(cached.capabilities)
                                + " selected=" + display(cached.criteria));
                    } catch (UpnpException exception) {
                        progress("DMS GetSortCapabilities unavailable: " + exception.getMessage());
                        sortCapabilities = "";
                        sortCriteria = "";
                        return;
                    }
                    SORT_STATE_CACHE.put(sortCacheKey, cached);
                }
            }
            applySortState(cached);
        }
    }

    private void applySortState(SortState state) {
        sortCapabilities = state.capabilities;
        sortCriteria = state.criteria;
        sortCapabilitiesLoaded = true;
    }

    private static final class SortState {
        final String capabilities;
        final String criteria;

        SortState(String capabilities, String criteria) {
            this.capabilities = value(capabilities);
            this.criteria = value(criteria);
        }
    }

    private static String chooseSortCriteria(String capabilities) {
        boolean date = false;
        boolean title = false;
        for (String capability : value(capabilities).split(",")) {
            String normalized = capability.trim().toLowerCase(Locale.US);
            while (normalized.startsWith("+") || normalized.startsWith("-")) {
                normalized = normalized.substring(1);
            }
            if ("dc:date".equals(normalized)) {
                date = true;
            } else if ("dc:title".equals(normalized)) {
                title = true;
            }
        }
        return date ? "-dc:date" : title ? "-dc:title" : "";
    }

    private static String buildSortCacheKey(DmsServiceInfo serviceInfo) {
        SonyDeviceDescription device = serviceInfo.device;
        String udn = device == null ? "" : value(device.udn);
        String location = device == null ? value(serviceInfo.descriptionUrl) : value(device.location);
        return udn + '\n' + location + '\n' + value(serviceInfo.controlUrl);
    }

    private void addPageItems(Document document, DmsBrowseResult result) {
        NodeList containers = document.getElementsByTagNameNS("*", "container");
        for (int index = 0; index < containers.getLength(); index++) {
            Element container = (Element) containers.item(index);
            String id = container.getAttribute("id");
            if (value(id).isEmpty()) {
                continue;
            }
            String title = childText(container, "title");
            result.containers.add(new DmsContainerItem(
                    id,
                    title.isEmpty() ? id : title,
                    parseInt(container.getAttribute("childCount"), -1),
                    container.getAttribute("parentID"),
                    childText(container, "date"),
                    childText(container, "class"),
                    parseBoolean(container.getAttribute("restricted")),
                    parseBoolean(container.getAttribute("searchable"))
            ));
        }

        NodeList itemNodes = document.getElementsByTagNameNS("*", "item");
        for (int index = 0; index < itemNodes.getLength(); index++) {
            CameraContentItem parsed = parseItem((Element) itemNodes.item(index));
            if (parsed.hasDownloadUrl()) {
                result.items.add(parsed);
            }
        }
    }

    private void browseRecursive(
            String objectId,
            List<CameraContentItem> items,
            Set<String> visited,
            int depth
    ) throws Exception {
        if (depth > 3 || !visited.add(objectId)) {
            return;
        }
        DmsBrowseResult result = browseDirectChildren(objectId);
        items.addAll(result.items);
        int traversed = 0;
        for (DmsContainerItem container : result.containers) {
            this.log.append("DMS container ").append(container.id).append(' ')
                    .append(container.title).append('\n');
            if (traversed++ < 12) {
                browseRecursive(container.id, items, visited, depth + 1);
            }
        }
        progress("DMS Browse " + objectId + " -> " + items.size() + " items so far");
    }

    private BrowsePage browsePage(String objectId, int startingIndex) throws Exception {
        try {
            return browsePage(objectId, startingIndex, sortCriteria);
        } catch (UpnpException exception) {
            if (sortCriteria.isEmpty()) {
                throw exception;
            }
            progress("DMS Browse rejected sort=" + sortCriteria
                    + "; retrying once without SortCriteria: " + exception.getMessage());
            BrowsePage page = browsePage(objectId, startingIndex, "");
            SortState fallback = new SortState(sortCapabilities, "");
            SORT_STATE_CACHE.put(sortCacheKey, fallback);
            applySortState(fallback);
            return page;
        }
    }

    private BrowsePage browsePage(String objectId, int startingIndex, String criteria) throws Exception {
        UpnpSoapClient.Response response = soapClient.call("Browse", UpnpSoapClient.arguments(
                "ObjectID", objectId,
                "BrowseFlag", "BrowseDirectChildren",
                "Filter", "*",
                "StartingIndex", startingIndex,
                "RequestedCount", BROWSE_PAGE_SIZE,
                "SortCriteria", criteria
        ));
        String didl = response.text("Result");
        int numberReturned = parseInt(response.text("NumberReturned"), 0);
        int totalMatches = parseInt(response.text("TotalMatches"), numberReturned);
        long updateId = parseLong(response.text("UpdateID"), -1);
        if (totalMatches <= 0) {
            totalMatches = numberReturned;
        }
        Document document = didl.isEmpty()
                ? UpnpSoapClient.parseXml("<DIDL-Lite/>")
                : UpnpSoapClient.parseXml(didl);
        return new BrowsePage(document, numberReturned, totalMatches, updateId);
    }

    private CameraContentItem parseItem(Element item) {
        String id = item.getAttribute("id");
        String title = childText(item, "title");
        String contentClass = childText(item, "class");
        String date = childText(item, "date");
        List<SonyResourceProfile> resources = new ArrayList<>();
        NodeList resourceNodes = item.getElementsByTagNameNS("*", "res");
        for (int index = 0; index < resourceNodes.getLength(); index++) {
            Element resourceElement = (Element) resourceNodes.item(index);
            String url = value(resourceElement.getTextContent());
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                continue;
            }
            int[] resolution = parseResolution(resourceElement.getAttribute("resolution"));
            int width = parseInt(resourceElement.getAttribute("resolutionWidth"),
                    resolution == null ? -1 : resolution[0]);
            int height = parseInt(resourceElement.getAttribute("resolutionHeight"),
                    resolution == null ? -1 : resolution[1]);
            resources.add(new SonyResourceProfile(
                    url,
                    resourceElement.getAttribute("protocolInfo"),
                    parseLong(resourceElement.getAttribute("size"), -1),
                    width,
                    height,
                    resourceElement.getAttribute("duration")
            ));
        }

        SonyResourceProfile original = best(resources, ResourceUse.ORIGINAL);
        SonyResourceProfile thumbnail = best(resources, ResourceUse.THUMBNAIL);
        SonyResourceProfile large = best(resources, ResourceUse.LARGE);
        if (large == null) {
            large = thumbnail;
        }
        if (thumbnail == null) {
            thumbnail = large;
        }
        String displayTitle = title.isEmpty() ? id : title;
        this.log.append("DMS item ").append(displayTitle)
                .append(" resources=").append(resources.size())
                .append(" original=").append(describe(original))
                .append(" large=").append(describe(large))
                .append(" thumb=").append(describe(thumbnail)).append('\n');
        return new CameraContentItem(
                displayTitle,
                id,
                contentClass,
                url(thumbnail),
                url(large),
                url(original),
                original == null ? -1 : original.size,
                itemToString(item),
                item.getAttribute("parentID"),
                date,
                original == null ? "" : original.mimeType,
                original == null ? "" : original.duration,
                resources
        );
    }

    private enum ResourceUse { ORIGINAL, THUMBNAIL, LARGE }

    private static SonyResourceProfile best(List<SonyResourceProfile> resources, ResourceUse use) {
        SonyResourceProfile best = null;
        int bestScore = Integer.MIN_VALUE;
        for (SonyResourceProfile resource : resources) {
            int score;
            if (use == ResourceUse.ORIGINAL) {
                score = resource.originalPriority();
            } else if (use == ResourceUse.THUMBNAIL) {
                score = resource.thumbnailPriority();
            } else {
                score = resource.largePreviewPriority();
            }
            if (best == null || score > bestScore) {
                best = resource;
                bestScore = score;
            }
        }
        return best;
    }

    private static final class BrowsePage {
        final Document document;
        final int numberReturned;
        final int totalMatches;
        final long updateId;

        BrowsePage(Document document, int numberReturned, int totalMatches, long updateId) {
            this.document = document;
            this.numberReturned = numberReturned;
            this.totalMatches = totalMatches;
            this.updateId = updateId;
        }
    }

    private static String childText(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element && localName.equals(child.getLocalName())) {
                return value(child.getTextContent());
            }
        }
        NodeList descendants = parent.getElementsByTagNameNS("*", localName);
        return descendants.getLength() == 0 ? "" : value(descendants.item(0).getTextContent());
    }

    private static String itemToString(Node node) {
        return node == null ? "" : value(node.getTextContent());
    }

    private static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(value(text));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static long parseLong(String text, long fallback) {
        try {
            return Long.parseLong(value(text));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean parseBoolean(String text) {
        String normalized = value(text);
        return "1".equals(normalized) || "true".equalsIgnoreCase(normalized);
    }

    private static int[] parseResolution(String resolution) {
        String normalized = value(resolution).toLowerCase(Locale.US);
        int separator = normalized.indexOf('x');
        if (separator <= 0 || separator >= normalized.length() - 1) {
            return null;
        }
        int width = parseInt(normalized.substring(0, separator), -1);
        int height = parseInt(normalized.substring(separator + 1), -1);
        return width > 0 && height > 0 ? new int[]{width, height} : null;
    }

    private static String url(SonyResourceProfile resource) {
        return resource == null ? "" : resource.url;
    }

    private static String describe(SonyResourceProfile resource) {
        if (resource == null) {
            return "none";
        }
        String profile = !resource.dlnaProfileName.isEmpty()
                ? resource.dlnaProfileName
                : resource.sonyProfileName;
        return resource.mediaKind() + (profile.isEmpty() ? "" : "/" + profile)
                + " " + shortUrl(resource.url);
    }

    private static String shortUrl(String url) {
        return url.length() <= 96 ? url : url.substring(0, 96) + "...";
    }

    private static String display(String text) {
        return value(text).isEmpty() ? "<none>" : text;
    }

    private static String value(String text) {
        return text == null ? "" : text.trim();
    }

    private void progress(String message) {
        Log.d(TAG, message);
        log.append(message).append('\n');
        if (progressListener != null) {
            progressListener.onProgress(message);
        }
    }
}
