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
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class SonyRpcClient {
    private final String baseUrl;
    private final AtomicInteger rpcId = new AtomicInteger(1);

    public SonyRpcClient(String baseUrl) {
        this.baseUrl = trimTrailingSlash(baseUrl);
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
        URL url = new URL(baseUrl + "/sony/" + service);
        IOException lastError = null;
        for (String contentType : new String[]{"application/json", "application/json; charset=utf-8"}) {
            try {
                return postJson(url, payload, contentType, service, method);
            } catch (IOException ex) {
                lastError = ex;
                if (!ex.getMessage().contains("HTTP 415")) {
                    throw ex;
                }
            }
        }
        throw lastError == null ? new IOException("Sony RPC failed for " + service + "." + method) : lastError;
    }

    private JSONObject postJson(URL url, byte[] payload, String contentType, String service, String method) throws IOException, JSONException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(3500);
        connection.setReadTimeout(8000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", contentType);
        connection.setRequestProperty("Accept", "application/json");
        connection.setFixedLengthStreamingMode(payload.length);

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
            throw new IOException("Sony RPC error for " + service + "." + method + ": " + response.getJSONArray("error"));
        }
        return response;
    }

    public List<String> getAvailableApiList(String service) throws IOException, JSONException {
        JSONObject response = call(service, "getAvailableApiList", new JSONArray(), "1.0");
        JSONArray result = response.optJSONArray("result");
        List<String> methods = new ArrayList<>();
        if (result != null && result.length() > 0) {
            JSONArray apiList = result.optJSONArray(0);
            if (apiList != null) {
                for (int i = 0; i < apiList.length(); i++) {
                    methods.add(apiList.optString(i));
                }
            }
        }
        return methods;
    }

    public List<String> safeGetAvailableApiList(String service, StringBuilder log) {
        try {
            List<String> apis = getAvailableApiList(service);
            log.append(service).append(" APIs: ").append(apis).append('\n');
            return apis;
        } catch (Exception ex) {
            log.append(service).append(" failed: ").append(ex.getMessage()).append('\n');
            return new ArrayList<>();
        }
    }

    public void setContentsTransferModeIfAvailable(List<String> cameraApis) throws IOException, JSONException {
        if (!cameraApis.contains("setCameraFunction")) {
            return;
        }
        JSONArray params = new JSONArray();
        params.put("Contents Transfer");
        call("camera", "setCameraFunction", params, "1.0");
    }

    public List<CameraContentItem> discoverContent(StringBuilder log, CapabilityMatrix matrix) throws IOException, JSONException {
        List<String> cameraApis = safeGetAvailableApiList("camera", log);
        List<String> avApis = safeGetAvailableApiList("avContent", log);

        matrix.set("camera endpoint", !cameraApis.isEmpty(), cameraApis.isEmpty() ? "not found" : cameraApis.size() + " APIs");
        matrix.set("avContent endpoint", !avApis.isEmpty(), avApis.isEmpty() ? "not found" : avApis.size() + " APIs");
        matrix.set("can enter contents transfer", cameraApis.contains("setCameraFunction"), "setCameraFunction");
        matrix.set("can list content", avApis.contains("getContentList"), "getContentList");

        setContentsTransferModeIfAvailable(cameraApis);

        List<String> roots = discoverRoots(avApis, log);
        List<CameraContentItem> items = new ArrayList<>();
        for (String root : roots) {
            collectContent(root, items, log, 0);
        }

        boolean hasJpeg = false;
        boolean hasRawCandidate = false;
        for (CameraContentItem item : items) {
            String name = item.title.toLowerCase();
            hasJpeg = hasJpeg || name.endsWith(".jpg") || name.endsWith(".jpeg");
            hasRawCandidate = hasRawCandidate || name.endsWith(".arw") || (item.originalUrl != null && item.originalUrl.toLowerCase().contains(".arw"));
        }
        matrix.set("download URLs exposed", items.stream().anyMatch(CameraContentItem::hasDownloadUrl), items.size() + " listed items");
        matrix.set("JPEG visible", hasJpeg, "based on listed filenames");
        matrix.set("RAW candidate visible", hasRawCandidate, "must be confirmed by download validation");
        return items;
    }

    private List<String> discoverRoots(List<String> avApis, StringBuilder log) throws IOException, JSONException {
        Set<String> roots = new LinkedHashSet<>();
        roots.add("storage:memoryCard1");
        roots.add("storage:memoryCard2");
        roots.add("storage:memoryCard");

        if (avApis.contains("getSchemeList")) {
            JSONObject schemeResponse = call("avContent", "getSchemeList", new JSONArray(), "1.0");
            log.append("getSchemeList: ").append(schemeResponse).append("\n\n");
        }

        if (avApis.contains("getSourceList")) {
            JSONArray params = new JSONArray();
            JSONObject arg = new JSONObject();
            arg.put("scheme", "storage");
            params.put(arg);
            try {
                JSONObject sourceResponse = call("avContent", "getSourceList", params, "1.0");
                log.append("getSourceList: ").append(sourceResponse).append("\n\n");
                JSONArray result = sourceResponse.optJSONArray("result");
                if (result != null && result.length() > 0) {
                    JSONArray sourceList = result.optJSONArray(0);
                    if (sourceList != null) {
                        for (int i = 0; i < sourceList.length(); i++) {
                            JSONObject source = sourceList.optJSONObject(i);
                            if (source != null && source.has("source")) {
                                roots.add(source.optString("source"));
                            } else if (source != null && source.has("uri")) {
                                roots.add(source.optString("uri"));
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                log.append("getSourceList failed: ").append(ex.getMessage()).append("\n\n");
            }
        }

        return new ArrayList<>(roots);
    }

    private void collectContent(String uri, List<CameraContentItem> items, StringBuilder log, int depth) throws IOException, JSONException {
        if (depth > 3) {
            return;
        }
        JSONObject response = getContentList(uri, 0, 100);
        log.append("getContentList(").append(uri).append("): ").append(response).append("\n\n");
        JSONArray result = response.optJSONArray("result");
        if (result == null || result.length() == 0) {
            return;
        }
        JSONArray list = result.optJSONArray(0);
        if (list == null) {
            return;
        }
        for (int i = 0; i < list.length(); i++) {
            JSONObject obj = list.optJSONObject(i);
            if (obj == null) {
                continue;
            }
            CameraContentItem item = parseItem(obj);
            if (item.hasDownloadUrl()) {
                items.add(item);
            } else if (item.uri.startsWith("image:") || item.uri.startsWith("video:")) {
                CameraContentItem enriched = tryGetContentInfo(item.uri, log);
                if (enriched != null && enriched.hasDownloadUrl()) {
                    items.add(enriched);
                }
            } else {
                String childUri = obj.optString("uri", "");
                if (!childUri.isEmpty() && !childUri.equals(uri)) {
                    try {
                        collectContent(childUri, items, log, depth + 1);
                    } catch (Exception ex) {
                        log.append("nested list failed for ").append(childUri).append(": ").append(ex.getMessage()).append("\n\n");
                    }
                }
                JSONArray children = obj.optJSONArray("content");
                if (children != null) {
                    for (int c = 0; c < children.length(); c++) {
                        JSONObject child = children.optJSONObject(c);
                        if (child != null) {
                            CameraContentItem childItem = parseItem(child);
                            if (childItem.hasDownloadUrl()) {
                                items.add(childItem);
                            }
                        }
                    }
                }
            }
        }
    }

    private CameraContentItem tryGetContentInfo(String uri, StringBuilder log) {
        try {
            JSONArray params = new JSONArray();
            JSONObject arg = new JSONObject();
            arg.put("uri", uri);
            params.put(arg);
            JSONObject response = call("avContent", "getContentInfo", params, "1.3");
            log.append("getContentInfo(").append(uri).append("): ").append(response).append("\n\n");
            JSONArray result = response.optJSONArray("result");
            if (result != null && result.length() > 0) {
                JSONObject info = result.optJSONObject(0);
                if (info != null) {
                    return parseItem(info);
                }
                JSONArray infoList = result.optJSONArray(0);
                if (infoList != null && infoList.length() > 0) {
                    JSONObject first = infoList.optJSONObject(0);
                    if (first != null) {
                        return parseItem(first);
                    }
                }
            }
        } catch (Exception ex) {
            log.append("getContentInfo failed for ").append(uri).append(": ").append(ex.getMessage()).append("\n\n");
        }
        return null;
    }

    private JSONObject getContentList(String uri, int startIndex, int count) throws IOException, JSONException {
        List<JSONObject> candidates = Arrays.asList(
                contentListArg(uri, startIndex, count, "flat"),
                contentListArg(uri, startIndex, count, "date"),
                contentListArg(uri, startIndex, count, null)
        );
        IOException lastIo = null;
        JSONException lastJson = null;
        for (JSONObject arg : candidates) {
            try {
                JSONArray params = new JSONArray();
                params.put(arg);
                try {
                    return call("avContent", "getContentList", params, "1.3");
                } catch (IOException first) {
                    return call("avContent", "getContentList", params, "1.0");
                }
            } catch (IOException ex) {
                lastIo = ex;
            } catch (JSONException ex) {
                lastJson = ex;
            }
        }
        if (lastIo != null) {
            throw lastIo;
        }
        throw lastJson == null ? new JSONException("getContentList failed") : lastJson;
    }

    private JSONObject contentListArg(String uri, int startIndex, int count, String view) throws JSONException {
        JSONObject arg = new JSONObject();
        arg.put("uri", uri);
        arg.put("stIdx", startIndex);
        arg.put("cnt", count);
        arg.put("sort", "descending");
        if (view != null) {
            arg.put("view", view);
        }
        return arg;
    }

    private CameraContentItem parseItem(JSONObject obj) {
        String title = firstNonEmpty(obj, "title", "fileName", "name");
        String uri = obj.optString("uri", "");
        String kind = firstNonEmpty(obj, "contentKind", "type");
        String thumbnailUrl = firstNonEmpty(obj, "thumbnailUrl", "thumbnail");
        String largeUrl = firstNonEmpty(obj, "largeUrl", "smallUrl", "url");
        String originalUrl = firstNonEmpty(obj, "downloadUrl", "originalUrl", "contentUrl");
        if (originalUrl.isEmpty()) {
            originalUrl = nestedDownloadUrl(obj, "original");
        }
        if (originalUrl.isEmpty()) {
            originalUrl = nestedDownloadUrl(obj.optJSONObject("content"), "original");
        }
        long size = obj.optLong("size", obj.optLong("fileSize", -1));
        return new CameraContentItem(title.isEmpty() ? uri : title, uri, kind, thumbnailUrl, largeUrl, originalUrl, size, obj.toString());
    }

    private String nestedDownloadUrl(JSONObject obj, String key) {
        if (obj == null) {
            return "";
        }
        JSONArray array = obj.optJSONArray(key);
        if (array == null) {
            return "";
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject candidate = array.optJSONObject(i);
            if (candidate == null) {
                continue;
            }
            String url = firstNonEmpty(candidate, "downloadUrl", "url", "originalUrl");
            if (!url.isEmpty()) {
                return url;
            }
        }
        return "";
    }

    private String firstNonEmpty(JSONObject obj, String... keys) {
        for (String key : keys) {
            String value = obj.optString(key, "");
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static String readAll(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private static String trimTrailingSlash(String value) {
        String result = value == null || value.isEmpty() ? "http://192.168.122.1:8080" : value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
