package com.codex.sonyedge;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CameraContentItem {
    public final String title;
    public final String uri;
    public final String contentKind;
    public final String thumbnailUrl;
    public final String largeUrl;
    public final String originalUrl;
    public final long size;
    public final String rawJson;
    public final String parentId;
    public final String date;
    public final String mimeType;
    public final String duration;
    public final List<SonyResourceProfile> resources;

    public CameraContentItem(
            String title,
            String uri,
            String contentKind,
            String thumbnailUrl,
            String largeUrl,
            String originalUrl,
            long size,
            String rawJson
    ) {
        this(title, uri, contentKind, thumbnailUrl, largeUrl, originalUrl, size, rawJson,
                "", "", "", "", Collections.emptyList());
    }

    public CameraContentItem(
            String title,
            String uri,
            String contentKind,
            String thumbnailUrl,
            String largeUrl,
            String originalUrl,
            long size,
            String rawJson,
            String parentId,
            String date,
            String mimeType,
            String duration,
            List<SonyResourceProfile> resources
    ) {
        this.title = title;
        this.uri = uri;
        this.contentKind = contentKind;
        this.thumbnailUrl = thumbnailUrl;
        this.largeUrl = largeUrl;
        this.originalUrl = originalUrl;
        this.size = size;
        this.rawJson = rawJson;
        this.parentId = value(parentId);
        this.date = value(date);
        this.mimeType = value(mimeType);
        this.duration = value(duration);
        this.resources = Collections.unmodifiableList(new ArrayList<>(
                resources == null ? Collections.emptyList() : resources));
    }

    public String bestDownloadUrl() {
        if (originalUrl != null && !originalUrl.isEmpty()) {
            return originalUrl;
        }
        if (largeUrl != null && !largeUrl.isEmpty()) {
            return largeUrl;
        }
        return thumbnailUrl;
    }

    public String previewUrl() {
        if (thumbnailUrl != null && !thumbnailUrl.isEmpty()) {
            return thumbnailUrl;
        }
        if (largeUrl != null && !largeUrl.isEmpty()) {
            return largeUrl;
        }
        return bestDownloadUrl();
    }

    public String previewThumbnailUrl() {
        return isIndependentPreview(thumbnailUrl) ? thumbnailUrl : "";
    }

    public String fullPreviewUrl() {
        if (isIndependentPreview(largeUrl)) {
            return largeUrl;
        }
        return previewThumbnailUrl();
    }

    private boolean isIndependentPreview(String candidate) {
        return candidate != null
                && !candidate.isEmpty()
                && (originalUrl == null || originalUrl.isEmpty() || !candidate.equals(originalUrl));
    }

    public boolean hasDownloadUrl() {
        String url = bestDownloadUrl();
        return url != null && !url.isEmpty();
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("title", title);
        obj.put("uri", uri);
        obj.put("contentKind", contentKind);
        obj.put("thumbnailUrl", thumbnailUrl);
        obj.put("largeUrl", largeUrl);
        obj.put("originalUrl", originalUrl);
        obj.put("size", size);
        obj.put("rawJson", rawJson);
        obj.put("parentId", parentId);
        obj.put("date", date);
        obj.put("mimeType", mimeType);
        obj.put("duration", duration);
        JSONArray encodedResources = new JSONArray();
        for (SonyResourceProfile resource : resources) {
            JSONObject encoded = new JSONObject();
            encoded.put("url", resource.url);
            encoded.put("protocolInfo", resource.protocolInfo);
            encoded.put("size", resource.size);
            encoded.put("width", resource.width);
            encoded.put("height", resource.height);
            encoded.put("duration", resource.duration);
            encodedResources.put(encoded);
        }
        obj.put("resources", encodedResources);
        return obj;
    }

    public static CameraContentItem fromJson(JSONObject obj) {
        List<SonyResourceProfile> resources = new ArrayList<>();
        JSONArray encodedResources = obj.optJSONArray("resources");
        if (encodedResources != null) {
            for (int index = 0; index < encodedResources.length(); index++) {
                JSONObject encoded = encodedResources.optJSONObject(index);
                if (encoded == null) {
                    continue;
                }
                resources.add(new SonyResourceProfile(
                        encoded.optString("url", ""),
                        encoded.optString("protocolInfo", ""),
                        encoded.optLong("size", -1),
                        encoded.optInt("width", -1),
                        encoded.optInt("height", -1),
                        encoded.optString("duration", "")
                ));
            }
        }
        return new CameraContentItem(
                obj.optString("title", "untitled"),
                obj.optString("uri", ""),
                obj.optString("contentKind", ""),
                obj.optString("thumbnailUrl", ""),
                obj.optString("largeUrl", ""),
                obj.optString("originalUrl", ""),
                obj.optLong("size", -1),
                obj.optString("rawJson", obj.toString()),
                obj.optString("parentId", ""),
                obj.optString("date", ""),
                obj.optString("mimeType", ""),
                obj.optString("duration", ""),
                resources
        );
    }

    private static String value(String text) {
        return text == null ? "" : text;
    }
}
