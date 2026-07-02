package com.codex.sonyedge;

import org.json.JSONException;
import org.json.JSONObject;

public final class CameraContentItem {
    public final String title;
    public final String uri;
    public final String contentKind;
    public final String thumbnailUrl;
    public final String largeUrl;
    public final String originalUrl;
    public final long size;
    public final String rawJson;

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
        this.title = title;
        this.uri = uri;
        this.contentKind = contentKind;
        this.thumbnailUrl = thumbnailUrl;
        this.largeUrl = largeUrl;
        this.originalUrl = originalUrl;
        this.size = size;
        this.rawJson = rawJson;
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
        return obj;
    }

    public static CameraContentItem fromJson(JSONObject obj) {
        return new CameraContentItem(
                obj.optString("title", "untitled"),
                obj.optString("uri", ""),
                obj.optString("contentKind", ""),
                obj.optString("thumbnailUrl", ""),
                obj.optString("largeUrl", ""),
                obj.optString("originalUrl", ""),
                obj.optLong("size", -1),
                obj.optString("rawJson", obj.toString())
        );
    }
}
