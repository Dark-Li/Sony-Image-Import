package com.codex.sonyedge;

import java.util.Locale;

public final class SonyResourceProfile {
    public final String url;
    public final String protocolInfo;
    public final String protocol;
    public final String network;
    public final String mimeType;
    public final String additionalInfo;
    public final String dlnaProfileName;
    public final String sonyProfileName;
    public final long size;
    public final int width;
    public final int height;
    public final String duration;

    public SonyResourceProfile(
            String url,
            String protocolInfo,
            long size,
            int width,
            int height,
            String duration
    ) {
        this.url = value(url);
        this.protocolInfo = value(protocolInfo);
        String[] parts = this.protocolInfo.split(":", 4);
        this.protocol = part(parts, 0);
        this.network = part(parts, 1);
        this.mimeType = part(parts, 2).toLowerCase(Locale.US);
        this.additionalInfo = part(parts, 3);
        this.dlnaProfileName = parameter(this.additionalInfo, "DLNA.ORG_PN");
        this.sonyProfileName = parameter(this.additionalInfo, "SONY.COM_PN");
        this.size = size;
        this.width = width;
        this.height = height;
        this.duration = value(duration);
    }

    public boolean isJpeg() {
        return contains(mimeType, "jpeg", "jpg") || profileContains("JPEG");
    }

    public boolean isHeif() {
        return contains(mimeType, "heif", "heic") || profileContains("HEIF", "HEIC");
    }

    public boolean isRaw() {
        return contains(mimeType, "arw", "sony-raw", "x-sony") || profileContains("ARW", "RAW");
    }

    public boolean isVideo() {
        return mimeType.startsWith("video/") || profileContains("MP4", "AVC", "XAVC", "MPEG");
    }

    public boolean isMp4() {
        return contains(mimeType, "mp4") || profileContains("MP4", "AVC_MP4");
    }

    public boolean isXavc() {
        return profileContains("XAVC") || contains(additionalInfo.toLowerCase(Locale.US), "xavc");
    }

    public boolean isOriginalProfile() {
        return profileEquals("PN_ORIGINAL") || profileContains("ORIGINAL");
    }

    public boolean isJpegLarge() {
        return profileContains("JPEG_LRG", "JPEG_LARGE");
    }

    public boolean isThumbnail() {
        return profileContains("JPEG_TN", "THUMBNAIL");
    }

    public boolean isSmallPreview() {
        return profileContains("JPEG_SM", "JPEG_SMALL");
    }

    public boolean isExplicitOriginalMedia() {
        if (isThumbnail() || isSmallPreview() || isJpegLarge()) {
            return false;
        }
        if (isRaw() || isHeif() || isVideo()) {
            return true;
        }
        if (!isJpeg()) {
            return false;
        }
        String name = fileName(url).toLowerCase(Locale.US);
        return !(name.startsWith("tn_") || name.startsWith("sm_") || name.startsWith("lrg_")
                || name.contains("thumbnail"));
    }

    public int originalPriority() {
        if (isOriginalProfile()) {
            return 100_000 + detailScore();
        }
        if (isExplicitOriginalMedia()) {
            return 80_000 + detailScore();
        }
        if (isJpegLarge()) {
            return 60_000 + detailScore();
        }
        if (isRaw() || isHeif() || isVideo() || isJpeg()) {
            return 40_000 + detailScore();
        }
        return detailScore();
    }

    public int thumbnailPriority() {
        if (isThumbnail()) {
            return 100_000 + detailScore();
        }
        if (isSmallPreview()) {
            return 80_000 + detailScore();
        }
        if (isJpegLarge()) {
            return 60_000 + detailScore();
        }
        if (isJpeg()) {
            return 40_000 + detailScore();
        }
        return detailScore();
    }

    public int largePreviewPriority() {
        if (isJpegLarge()) {
            return 100_000 + detailScore();
        }
        if (isSmallPreview()) {
            return 80_000 + detailScore();
        }
        if (isThumbnail()) {
            return 60_000 + detailScore();
        }
        if (isJpeg()) {
            return 40_000 + detailScore();
        }
        return detailScore();
    }

    public String mediaKind() {
        if (isRaw()) {
            return "ARW";
        }
        if (isHeif()) {
            return "HEIF";
        }
        if (isXavc()) {
            return "XAVC";
        }
        if (isMp4() || isVideo()) {
            return "MP4";
        }
        if (isJpeg()) {
            return "JPEG";
        }
        return mimeType.isEmpty() ? "UNKNOWN" : mimeType;
    }

    private int detailScore() {
        long pixels = width > 0 && height > 0 ? (long) width * height : 0;
        int score = (int) Math.min(pixels / 100_000L, 2_000L);
        if (size > 0) {
            score += (int) Math.min(size / (1024L * 1024L), 2_000L);
        }
        return score;
    }

    private boolean profileEquals(String expected) {
        return expected.equalsIgnoreCase(dlnaProfileName) || expected.equalsIgnoreCase(sonyProfileName);
    }

    private boolean profileContains(String... needles) {
        String profiles = (dlnaProfileName + ";" + sonyProfileName).toUpperCase(Locale.US);
        for (String needle : needles) {
            if (profiles.contains(needle.toUpperCase(Locale.US))) {
                return true;
            }
        }
        return false;
    }

    private static String parameter(String additionalInfo, String key) {
        for (String token : value(additionalInfo).split(";")) {
            int separator = token.indexOf('=');
            if (separator > 0 && key.equalsIgnoreCase(token.substring(0, separator).trim())) {
                String found = token.substring(separator + 1).trim();
                if (found.length() >= 2 && found.startsWith("\"") && found.endsWith("\"")) {
                    found = found.substring(1, found.length() - 1);
                }
                return found;
            }
        }
        return "";
    }

    private static String part(String[] parts, int index) {
        return index < parts.length ? value(parts[index]) : "";
    }

    private static boolean contains(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String fileName(String url) {
        int query = url.indexOf('?');
        String path = query >= 0 ? url.substring(0, query) : url;
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static String value(String text) {
        return text == null ? "" : text.trim();
    }
}
