package com.codex.sonyedge;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class SonyRpcClient {
    private static final int CONTENT_PAGE_SIZE = 100;
    private static final int MAX_CONTENT_PAGES = 200;

    private final String baseUrl;
    private final Map<String, String> actionListUrls = new LinkedHashMap<>();
    private final Map<String, List<String>> apiCache = new HashMap<>();
    private final AtomicInteger rpcId = new AtomicInteger(1);

    /** Compatibility constructor for legacy callers that already have a Scalar host root. */
    public SonyRpcClient(String baseUrl) {
        this.baseUrl = trimTrailingSlash(requireEndpoint(baseUrl));
        actionListUrls.put("*", scalarRoot(this.baseUrl));
    }

    /** Builds service routes exclusively from the discovered Sony device description. */
    public SonyRpcClient(SonyDeviceDescription device) {
        if (device == null) {
            throw new IllegalArgumentException("Sony device description is required");
        }
        String first = "";
        for (SonyServiceDescription service : device.services) {
            if (!service.isScalarWebApi() || service.actionListUrl.isEmpty()) {
                continue;
            }
            String type = normalizeService(service.apiType);
            actionListUrls.put(type.isEmpty() ? "*" : type, trimTrailingSlash(service.actionListUrl));
            if (first.isEmpty()) {
                first = trimTrailingSlash(service.actionListUrl);
            }
        }
        if (first.isEmpty()) {
            throw new IllegalArgumentException("Device does not advertise ScalarWebAPI action-list endpoints");
        }
        baseUrl = first;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public JSONObject call(String service, String method, JSONArray params, String version) throws IOException, JSONException {
        JSONObject request = new JSONObject();
        request.put("method", method);
        request.put("params", params == null ? new JSONArray() : params);
        request.put("id", rpcId.getAndIncrement());
        request.put("version", version == null ? "1.0" : version);

        byte[] payload = request.toString().getBytes(StandardCharsets.UTF_8);
        URL url = new URL(endpointFor(service));
        IOException lastError = null;
        for (String contentType : new String[]{"application/json", "application/json; charset=utf-8"}) {
            try {
                return postJson(url, payload, contentType, service, method);
            } catch (IOException ex) {
                lastError = ex;
                if (ex.getMessage() == null || !ex.getMessage().contains("HTTP 415")) {
                    throw ex;
                }
            }
        }
        throw lastError == null ? new IOException("Sony RPC failed for " + service + "." + method) : lastError;
    }

    private JSONObject callAvailable(String service, String method, JSONArray params, String version)
            throws IOException, JSONException {
        List<String> available = getAvailableApiList(service);
        if (!available.contains(method)) {
            throw new IOException("Sony RPC method unavailable: " + service + "." + method
                    + " available=" + available);
        }
        return call(service, method, params, version);
    }

    private JSONObject postJson(URL url, byte[] payload, String contentType, String service, String method)
            throws IOException, JSONException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(3500);
        connection.setReadTimeout(12000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setUseCaches(false);
        connection.setRequestProperty("Content-Type", contentType);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Accept-Encoding", "identity");
        connection.setRequestProperty("Connection", "close");
        connection.setFixedLengthStreamingMode(payload.length);

        try {
            try (OutputStream output = connection.getOutputStream()) {
                output.write(payload);
            }
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            String body = readAll(stream);
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code + " using " + contentType + " from " + url + ": " + body);
            }
            JSONObject response = new JSONObject(body);
            if (response.has("error")) {
                throw new IOException("Sony RPC error for " + service + "." + method + ": " + response.opt("error"));
            }
            return response;
        } finally {
            connection.disconnect();
        }
    }

    public List<String> getAvailableApiList(String service) throws IOException, JSONException {
        String key = normalizeService(service);
        List<String> cached = apiCache.get(key);
        if (cached != null) {
            return cached;
        }
        JSONObject response = call(service, "getAvailableApiList", new JSONArray(), "1.0");
        JSONArray result = response.optJSONArray("result");
        List<String> methods = new ArrayList<>();
        if (result != null && result.length() > 0) {
            JSONArray apiList = result.optJSONArray(0);
            if (apiList != null) {
                for (int i = 0; i < apiList.length(); i++) {
                    String method = apiList.optString(i, "");
                    if (!method.isEmpty()) {
                        methods.add(method);
                    }
                }
            }
        }
        List<String> immutable = Collections.unmodifiableList(methods);
        apiCache.put(key, immutable);
        return immutable;
    }

    public List<String> safeGetAvailableApiList(String service, StringBuilder log) {
        try {
            List<String> apis = getAvailableApiList(service);
            append(log, service + " APIs: " + apis);
            return apis;
        } catch (Exception ex) {
            append(log, service + " failed: " + message(ex));
            return Collections.emptyList();
        }
    }

    public void setContentsTransferModeIfAvailable(List<String> cameraApis) throws IOException, JSONException {
        if (cameraApis == null || !cameraApis.contains("setCameraFunction")) {
            return;
        }
        // Refreshing through getAvailableApiList is intentional: methods are never invoked blindly.
        if (!getAvailableApiList("camera").contains("setCameraFunction")) {
            return;
        }
        JSONArray params = new JSONArray().put("Contents Transfer");
        callAvailable("camera", "setCameraFunction", params, "1.0");
    }

    public List<CameraContentItem> discoverContent(StringBuilder log, CapabilityMatrix matrix)
            throws IOException, JSONException {
        List<String> cameraApis = safeGetAvailableApiList("camera", log);
        List<String> avApis = safeGetAvailableApiList("avContent", log);

        matrix.set("camera endpoint", !cameraApis.isEmpty(), cameraApis.isEmpty() ? "not found" : cameraApis.size() + " APIs");
        matrix.set("avContent endpoint", !avApis.isEmpty(), avApis.isEmpty() ? "not found" : avApis.size() + " APIs");
        matrix.set("can enter contents transfer", cameraApis.contains("setCameraFunction"), "setCameraFunction");
        matrix.set("can list content", avApis.contains("getContentList"), "getContentList");

        if (!avApis.contains("getContentList")) {
            return Collections.emptyList();
        }
        List<String> roots = discoverRoots(avApis, log);
        LinkedHashMap<String, CameraContentItem> items = new LinkedHashMap<>();
        Set<String> visited = new HashSet<>();
        for (String root : roots) {
            collectContent(root, items, visited, log, 0, avApis);
        }

        boolean hasJpeg = false;
        boolean hasRawCandidate = false;
        for (CameraContentItem item : items.values()) {
            String name = value(item.title).toLowerCase(Locale.US);
            hasJpeg |= name.endsWith(".jpg") || name.endsWith(".jpeg");
            hasRawCandidate |= name.endsWith(".arw")
                    || value(item.originalUrl).toLowerCase(Locale.US).contains(".arw");
        }
        matrix.set("download URLs exposed", !items.isEmpty(), items.size() + " listed items");
        matrix.set("JPEG visible", hasJpeg, "based on listed filenames");
        matrix.set("RAW candidate visible", hasRawCandidate, "must be confirmed by download validation");
        return new ArrayList<>(items.values());
    }

    private List<String> discoverRoots(List<String> avApis, StringBuilder log) throws IOException, JSONException {
        Set<String> schemes = new LinkedHashSet<>();
        if (avApis.contains("getSchemeList")) {
            JSONObject response = callAvailable("avContent", "getSchemeList", new JSONArray(), "1.0");
            collectNamedValues(response.optJSONArray("result"), "scheme", schemes);
            append(log, "Scalar schemes: " + schemes);
        }
        if (schemes.isEmpty()) {
            schemes.add("storage");
        }

        Set<String> roots = new LinkedHashSet<>();
        if (avApis.contains("getSourceList")) {
            for (String scheme : schemes) {
                JSONArray params = new JSONArray().put(new JSONObject().put("scheme", scheme));
                try {
                    JSONObject response = callAvailable("avContent", "getSourceList", params, "1.0");
                    collectNamedValues(response.optJSONArray("result"), "source", roots);
                    collectNamedValues(response.optJSONArray("result"), "uri", roots);
                } catch (Exception ex) {
                    append(log, "getSourceList(" + scheme + ") failed: " + message(ex));
                }
            }
        }
        append(log, "Scalar content roots: " + roots);
        return new ArrayList<>(roots);
    }

    private void collectContent(
            String uri,
            Map<String, CameraContentItem> items,
            Set<String> visited,
            StringBuilder log,
            int depth,
            List<String> avApis
    ) throws IOException, JSONException {
        if (depth > 4 || uri == null || uri.isEmpty() || !visited.add(uri)) {
            return;
        }
        int expected = avApis.contains("getContentCount") ? getContentCount(uri, log) : -1;
        int start = 0;
        for (int page = 0; page < MAX_CONTENT_PAGES; page++) {
            JSONObject response = getContentList(uri, start, CONTENT_PAGE_SIZE);
            List<JSONObject> entries = contentEntries(response);
            append(log, "getContentList(" + uri + ") page=" + (page + 1) + " start=" + start
                    + " returned=" + entries.size() + " expected=" + expected);
            if (entries.isEmpty()) {
                break;
            }
            for (JSONObject entry : entries) {
                CameraContentItem item = parseItem(entry);
                if (item.hasDownloadUrl()) {
                    items.put(itemIdentity(item), item);
                    continue;
                }
                String childUri = firstNonEmpty(entry, "uri", "source");
                if (!childUri.isEmpty() && !childUri.equals(uri)) {
                    if (childUri.startsWith("image:") || childUri.startsWith("video:")) {
                        CameraContentItem enriched = tryGetContentInfo(childUri, log, avApis);
                        if (enriched != null && enriched.hasDownloadUrl()) {
                            items.put(itemIdentity(enriched), enriched);
                        }
                    } else {
                        try {
                            collectContent(childUri, items, visited, log, depth + 1, avApis);
                        } catch (Exception ex) {
                            append(log, "Nested Scalar list failed for " + childUri + ": " + message(ex));
                        }
                    }
                }
            }
            start += entries.size();
            if (entries.size() < CONTENT_PAGE_SIZE || (expected >= 0 && start >= expected)) {
                break;
            }
        }
    }

    private int getContentCount(String uri, StringBuilder log) {
        try {
            JSONArray params = new JSONArray().put(new JSONObject().put("uri", uri));
            JSONObject response = callAvailable("avContent", "getContentCount", params, "1.0");
            return findFirstInt(response.optJSONArray("result"), "count", "contentCount", "totalCount");
        } catch (Exception ex) {
            append(log, "getContentCount(" + uri + ") failed: " + message(ex));
            return -1;
        }
    }

    private CameraContentItem tryGetContentInfo(String uri, StringBuilder log, List<String> avApis) {
        if (!avApis.contains("getContentInfo")) {
            return null;
        }
        try {
            JSONArray params = new JSONArray().put(new JSONObject().put("uri", uri));
            JSONObject response;
            try {
                response = callAvailable("avContent", "getContentInfo", params, "1.3");
            } catch (IOException first) {
                response = callAvailable("avContent", "getContentInfo", params, "1.0");
            }
            List<JSONObject> entries = contentEntries(response);
            return entries.isEmpty() ? null : parseItem(entries.get(0));
        } catch (Exception ex) {
            append(log, "getContentInfo failed for " + uri + ": " + message(ex));
            return null;
        }
    }

    private JSONObject getContentList(String uri, int startIndex, int count) throws IOException, JSONException {
        IOException last = null;
        for (String view : new String[]{"flat", "date", ""}) {
            JSONObject arg = new JSONObject()
                    .put("uri", uri)
                    .put("stIdx", startIndex)
                    .put("cnt", Math.min(CONTENT_PAGE_SIZE, Math.max(1, count)))
                    .put("sort", "descending");
            if (!view.isEmpty()) {
                arg.put("view", view);
            }
            JSONArray params = new JSONArray().put(arg);
            try {
                try {
                    return callAvailable("avContent", "getContentList", params, "1.3");
                } catch (IOException first) {
                    return callAvailable("avContent", "getContentList", params, "1.0");
                }
            } catch (IOException ex) {
                last = ex;
            }
        }
        throw last == null ? new IOException("getContentList failed for " + uri) : last;
    }

    private CameraContentItem parseItem(JSONObject outer) {
        JSONObject content = outer.optJSONObject("content");
        JSONObject primary = content == null ? outer : content;
        String uri = firstNonEmpty(outer, "uri", "source");
        if (uri.isEmpty()) uri = firstNonEmpty(primary, "uri", "source");
        String title = firstNonEmpty(outer, "title", "fileName", "name");
        if (title.isEmpty()) title = firstNonEmpty(primary, "title", "fileName", "name");
        String kind = firstNonEmpty(outer, "contentKind", "type");
        if (kind.isEmpty()) kind = firstNonEmpty(primary, "contentKind", "type");

        String thumbnailUrl = firstUrl(outer, "thumbnailUrl", "thumbnail", "smallUrl", "small");
        String largeUrl = firstUrl(outer, "largeUrl", "large", "url");
        String originalUrl = firstUrl(outer, "downloadUrl", "originalUrl", "contentUrl", "original");
        long size = firstLong(outer, "size", "fileSize");
        if (size < 0 && content != null) size = firstLong(content, "size", "fileSize");
        return new CameraContentItem(
                title.isEmpty() ? uri : title,
                uri,
                kind,
                thumbnailUrl,
                largeUrl,
                originalUrl,
                size,
                outer.toString()
        );
    }

    private String endpointFor(String service) throws IOException {
        String normalized = normalizeService(service);
        String actionList = actionListUrls.get(normalized);
        if (actionList == null) actionList = actionListUrls.get("*");
        if (actionList == null || actionList.isEmpty()) {
            throw new IOException("No discovered Scalar endpoint for service " + service);
        }
        String lower = actionList.toLowerCase(Locale.US);
        String suffix = "/" + normalized.toLowerCase(Locale.US);
        if (lower.endsWith(suffix)) {
            return actionList;
        }
        if (lower.endsWith("/sony")) {
            return actionList + "/" + service;
        }
        if (lower.contains("/sony/")) {
            return actionList.substring(0, lower.indexOf("/sony/") + 5) + service;
        }
        return trimTrailingSlash(actionList) + "/" + service;
    }

    private static String scalarRoot(String value) {
        String trimmed = trimTrailingSlash(value);
        String lower = trimmed.toLowerCase(Locale.US);
        int sony = lower.indexOf("/sony/");
        if (sony >= 0) return trimmed.substring(0, sony + 5);
        return lower.endsWith("/sony") ? trimmed : trimmed + "/sony";
    }

    private static List<JSONObject> contentEntries(JSONObject response) {
        List<JSONObject> entries = new ArrayList<>();
        JSONArray result = response.optJSONArray("result");
        if (result == null) return entries;
        for (int index = 0; index < result.length(); index++) {
            Object value = result.opt(index);
            addEntryContainer(value, entries);
            if (!entries.isEmpty()) break;
        }
        return entries;
    }

    private static void addEntryContainer(Object value, List<JSONObject> entries) {
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                Object child = array.opt(i);
                if (child instanceof JSONObject) entries.add((JSONObject) child);
            }
        } else if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            JSONArray content = object.optJSONArray("content");
            if (content != null) addEntryContainer(content, entries);
            else if (object.has("uri") || object.has("title") || object.has("fileName")) entries.add(object);
        }
    }

    private static String firstUrl(JSONObject object, String... keys) {
        for (String key : keys) {
            String direct = object.optString(key, "");
            if (isHttp(direct)) return direct;
            Object nested = object.opt(key);
            String found = findUrl(nested);
            if (!found.isEmpty()) return found;
        }
        JSONObject content = object.optJSONObject("content");
        if (content != null && content != object) {
            for (String key : keys) {
                Object nested = content.opt(key);
                String found = findUrl(nested);
                if (!found.isEmpty()) return found;
            }
        }
        return "";
    }

    private static String findUrl(Object value) {
        if (value instanceof String) return isHttp((String) value) ? (String) value : "";
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            for (String key : new String[]{"downloadUrl", "originalUrl", "contentUrl", "url", "uri"}) {
                String candidate = object.optString(key, "");
                if (isHttp(candidate)) return candidate;
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                String candidate = findUrl(array.opt(i));
                if (!candidate.isEmpty()) return candidate;
            }
        }
        return "";
    }

    private static void collectNamedValues(Object value, String key, Set<String> output) {
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) collectNamedValues(array.opt(i), key, output);
        } else if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            String candidate = object.optString(key, "");
            if (!candidate.isEmpty()) output.add(candidate);
            java.util.Iterator<String> names = object.keys();
            while (names.hasNext()) {
                String name = names.next();
                collectNamedValues(object.opt(name), key, output);
            }
        }
    }

    private static int findFirstInt(Object value, String... keys) {
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                int found = findFirstInt(array.opt(i), keys);
                if (found >= 0) return found;
            }
        } else if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            for (String key : keys) if (object.has(key)) return object.optInt(key, -1);
            java.util.Iterator<String> names = object.keys();
            while (names.hasNext()) {
                String name = names.next();
                int found = findFirstInt(object.opt(name), keys);
                if (found >= 0) return found;
            }
        } else if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return -1;
    }

    private static long firstLong(JSONObject object, String... keys) {
        for (String key : keys) if (object.has(key)) return object.optLong(key, -1);
        return -1;
    }

    private static String firstNonEmpty(JSONObject object, String... keys) {
        for (String key : keys) {
            String value = object.optString(key, "");
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static String itemIdentity(CameraContentItem item) {
        if (!value(item.uri).isEmpty()) return item.uri;
        if (!value(item.originalUrl).isEmpty()) return item.originalUrl;
        return value(item.title);
    }

    private static String readAll(InputStream stream) throws IOException {
        if (stream == null) return "";
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
        }
        return builder.toString();
    }

    private static String normalizeService(String service) {
        String normalized = value(service).trim();
        if (normalized.equalsIgnoreCase("avcontent")) return "avContent";
        if (normalized.equalsIgnoreCase("camera")) return "camera";
        if (normalized.equalsIgnoreCase("system")) return "system";
        return normalized;
    }

    private static String requireEndpoint(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Scalar endpoint is required");
        }
        return value.trim();
    }

    private static String trimTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }

    private static boolean isHttp(String value) {
        return value != null && (value.startsWith("http://") || value.startsWith("https://"));
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static String message(Exception exception) {
        return exception.getMessage() == null ? exception.toString() : exception.getMessage();
    }

    private static void append(StringBuilder log, String message) {
        if (log != null) log.append(message).append('\n');
    }
}
