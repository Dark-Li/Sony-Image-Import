package com.codex.sonyedge;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Locale;

public final class DownloadValidator {
    private DownloadValidator() {
    }

    public static ValidationResult inspect(File file, String contentType) throws IOException {
        byte[] header = new byte[16];
        int count;
        try (FileInputStream input = new FileInputStream(file)) {
            count = input.read(header);
        }

        String lowerName = file.getName().toLowerCase(Locale.US);
        boolean jpegHeader = count >= 2 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8;
        boolean rawTiffHeader = count >= 4
                && (((header[0] == 'I' && header[1] == 'I' && (header[2] & 0xFF) == 0x2A && header[3] == 0)
                || (header[0] == 'M' && header[1] == 'M' && header[2] == 0 && (header[3] & 0xFF) == 0x2A)));
        boolean looksRaw = lowerName.endsWith(".arw") || rawTiffHeader;
        boolean looksJpeg = lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || jpegHeader;

        String format = looksRaw ? "RAW/ARW" : looksJpeg ? "JPEG" : "UNKNOWN";
        return new ValidationResult(file.length(), contentType == null ? "" : contentType, format, hex(header, count));
    }

    private static String hex(byte[] bytes, int count) {
        StringBuilder builder = new StringBuilder();
        int safeCount = Math.max(0, Math.min(count, bytes.length));
        for (int i = 0; i < safeCount; i++) {
            if (i > 0) {
                builder.append(' ');
            }
            builder.append(String.format(Locale.US, "%02X", bytes[i] & 0xFF));
        }
        return builder.toString();
    }

    public static final class ValidationResult {
        public final long bytes;
        public final String contentType;
        public final String format;
        public final String headerHex;

        public ValidationResult(long bytes, String contentType, String format, String headerHex) {
            this.bytes = bytes;
            this.contentType = contentType;
            this.format = format;
            this.headerHex = headerHex;
        }

        public String toDisplayString() {
            return format + ", " + bytes + " bytes, MIME=" + contentType + ", header=" + headerHex;
        }
    }
}
