package com.codex.sonyedge;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

public final class DownloadValidator {
    private DownloadValidator() {
    }

    public static ValidationResult inspect(File file, String contentType) throws IOException {
        return inspect(file, contentType, -1, -1);
    }

    public static ValidationResult inspect(
            File file,
            String contentType,
            long httpContentLength,
            long didlExpectedSize
    ) throws IOException {
        return inspect(file, contentType, httpContentLength, didlExpectedSize, file == null ? "" : file.getName());
    }

    public static ValidationResult inspect(
            File file,
            String contentType,
            long httpContentLength,
            long didlExpectedSize,
            String declaredFilename
    ) throws IOException {
        if (file == null || !file.isFile()) {
            throw new ValidationException("Downloaded file is missing", false);
        }

        long actualSize = file.length();
        if (actualSize <= 0) {
            throw new ValidationException("Downloaded file is empty", true);
        }
        verifyLength("HTTP Content-Length", httpContentLength, actualSize);
        verifyLength("DIDL resource size", didlExpectedSize, actualSize);

        byte[] header = new byte[16];
        int headerCount;
        try (FileInputStream input = new FileInputStream(file)) {
            headerCount = input.read(header);
        }

        boolean jpegStart = headerCount >= 2
                && (header[0] & 0xFF) == 0xFF
                && (header[1] & 0xFF) == 0xD8;
        long jpegTrailingBytes = jpegStart ? inspectJpegMarkerStream(file, actualSize) : 0L;
        boolean rawTiffHeader = headerCount >= 4
                && ((header[0] == 'I' && header[1] == 'I' && (header[2] & 0xFF) == 0x2A && header[3] == 0)
                || (header[0] == 'M' && header[1] == 'M' && header[2] == 0 && (header[3] & 0xFF) == 0x2A));

        String lowerName = stripPartSuffix(declaredFilename).toLowerCase(Locale.US);
        String lowerContentType = normalizeContentType(contentType);
        boolean nameSaysJpeg = lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg");
        boolean nameSaysRaw = lowerName.endsWith(".arw");
        boolean mimeSaysJpeg = lowerContentType.contains("jpeg") || lowerContentType.contains("jpg");
        boolean mimeSaysRaw = lowerContentType.contains("sony-arw")
                || lowerContentType.contains("x-arw")
                || lowerContentType.contains("tiff");

        final String format;
        if (jpegStart) {
            if (nameSaysRaw || mimeSaysRaw) {
                throw new ValidationException("JPEG data does not match the declared ARW format", false);
            }
            format = "JPEG";
        } else if (rawTiffHeader) {
            if (nameSaysJpeg || mimeSaysJpeg) {
                throw new ValidationException("ARW/TIFF data does not match the declared JPEG format", false);
            }
            if (httpContentLength <= 0 && didlExpectedSize <= 0) {
                throw new ValidationException(
                        "ARW completeness cannot be verified without HTTP Content-Length or DIDL size",
                        true
                );
            }
            format = "RAW/ARW";
        } else {
            throw new ValidationException("Unknown or unsupported file signature", false);
        }

        if ((nameSaysJpeg || mimeSaysJpeg) && !"JPEG".equals(format)) {
            throw new ValidationException("Downloaded format does not match JPEG metadata", false);
        }
        if ((nameSaysRaw || mimeSaysRaw) && !"RAW/ARW".equals(format)) {
            throw new ValidationException("Downloaded format does not match ARW metadata", false);
        }

        return new ValidationResult(
                actualSize,
                contentType == null ? "" : contentType,
                format,
                hex(header, headerCount),
                httpContentLength,
                didlExpectedSize,
                "JPEG".equals(format) ? jpegTrailingBytes : 0L
        );
    }

    private static long inspectJpegMarkerStream(File file, long fileSize) throws IOException {
        try (JpegInput input = new JpegInput(new BufferedInputStream(
                new FileInputStream(file), 64 * 1024))) {
            if (input.readUnsignedByte() != 0xFF || input.readUnsignedByte() != 0xD8) {
                throw new ValidationException("JPEG start marker is missing", false);
            }

            JpegMarker pendingMarker = null;
            while (true) {
                JpegMarker marker = pendingMarker != null ? pendingMarker : readMarker(input, fileSize);
                pendingMarker = null;
                if (marker == null) {
                    throw new ValidationException("JPEG end marker is missing", true);
                }

                if (marker.code == 0xD9) {
                    return fileSize - marker.offsetAfterMarker;
                }
                if (marker.code == 0xD8) {
                    throw new ValidationException("Unexpected JPEG start marker", false);
                }
                if (isStandaloneMarker(marker.code)) {
                    continue;
                }

                skipMarkerSegment(input, fileSize);
                if (marker.code == 0xDA) {
                    pendingMarker = readMarkerAfterScanData(input, fileSize);
                    if (pendingMarker == null) {
                        throw new ValidationException("JPEG end marker is missing", true);
                    }
                }
            }
        }
    }

    private static JpegMarker readMarker(JpegInput input, long fileSize) throws IOException {
        if (input.position() >= fileSize) {
            return null;
        }
        if (input.readUnsignedByte() != 0xFF) {
            throw new ValidationException("Invalid JPEG marker stream", false);
        }

        int code;
        do {
            if (input.position() >= fileSize) {
                return null;
            }
            code = input.readUnsignedByte();
        } while (code == 0xFF);

        if (code == 0x00) {
            throw new ValidationException("Unexpected stuffed byte outside JPEG scan data", false);
        }
        return new JpegMarker(code, input.position());
    }

    private static JpegMarker readMarkerAfterScanData(JpegInput input, long fileSize) throws IOException {
        while (input.position() < fileSize) {
            if (input.readUnsignedByte() != 0xFF) {
                continue;
            }

            int code;
            do {
                if (input.position() >= fileSize) {
                    return null;
                }
                code = input.readUnsignedByte();
            } while (code == 0xFF);

            if (code == 0x00 || code == 0x01 || (code >= 0xD0 && code <= 0xD7)) {
                continue;
            }
            return new JpegMarker(code, input.position());
        }
        return null;
    }

    private static void skipMarkerSegment(JpegInput input, long fileSize) throws IOException {
        if (fileSize - input.position() < 2) {
            throw new ValidationException("JPEG marker segment is truncated", true);
        }
        int segmentLength = input.readUnsignedShort();
        if (segmentLength < 2) {
            throw new ValidationException("Invalid JPEG marker segment length", false);
        }
        long segmentEnd = input.position() + segmentLength - 2L;
        if (segmentEnd > fileSize) {
            throw new ValidationException("JPEG marker segment is truncated", true);
        }
        input.skipFully(segmentLength - 2L);
    }

    private static boolean isStandaloneMarker(int code) {
        return code == 0x01 || (code >= 0xD0 && code <= 0xD7);
    }

    private static void verifyLength(String source, long expected, long actual) throws ValidationException {
        if (expected <= 0 || expected == actual) {
            return;
        }
        throw new ValidationException(
                source + " mismatch: expected " + expected + " bytes, received " + actual,
                actual < expected
        );
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int semicolon = contentType.indexOf(';');
        String normalized = semicolon >= 0 ? contentType.substring(0, semicolon) : contentType;
        return normalized.trim().toLowerCase(Locale.US);
    }

    private static String stripPartSuffix(String filename) {
        String result = filename == null ? "" : filename;
        while (result.toLowerCase(Locale.US).endsWith(".part")) {
            result = result.substring(0, result.length() - 5);
        }
        return result;
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

    private static final class JpegMarker {
        final int code;
        final long offsetAfterMarker;

        JpegMarker(int code, long offsetAfterMarker) {
            this.code = code;
            this.offsetAfterMarker = offsetAfterMarker;
        }
    }

    private static final class JpegInput implements AutoCloseable {
        private final InputStream input;
        private long position;

        JpegInput(InputStream input) {
            this.input = input;
        }

        int readUnsignedByte() throws IOException {
            int value = input.read();
            if (value < 0) {
                throw new IOException("Unexpected end of JPEG stream");
            }
            position++;
            return value;
        }

        int readUnsignedShort() throws IOException {
            return (readUnsignedByte() << 8) | readUnsignedByte();
        }

        void skipFully(long byteCount) throws IOException {
            long remaining = byteCount;
            while (remaining > 0) {
                long skipped = input.skip(remaining);
                if (skipped > 0) {
                    position += skipped;
                    remaining -= skipped;
                    continue;
                }
                readUnsignedByte();
                remaining--;
            }
        }

        long position() {
            return position;
        }

        @Override
        public void close() throws IOException {
            input.close();
        }
    }

    public static final class ValidationException extends IOException {
        private final boolean truncated;

        ValidationException(String message, boolean truncated) {
            super(message);
            this.truncated = truncated;
        }

        public boolean isTruncated() {
            return truncated;
        }
    }

    public static final class ValidationResult {
        public final long bytes;
        public final String contentType;
        public final String format;
        public final String headerHex;
        public final long httpContentLength;
        public final long didlExpectedSize;
        public final long trailingBytes;

        public ValidationResult(
                long bytes,
                String contentType,
                String format,
                String headerHex,
                long httpContentLength,
                long didlExpectedSize,
                long trailingBytes
        ) {
            this.bytes = bytes;
            this.contentType = contentType;
            this.format = format;
            this.headerHex = headerHex;
            this.httpContentLength = httpContentLength;
            this.didlExpectedSize = didlExpectedSize;
            this.trailingBytes = trailingBytes;
        }

        public String toDisplayString() {
            String trailingInfo = trailingBytes > 0 ? ", trailing=" + trailingBytes + " bytes" : "";
            return format + ", " + bytes + " bytes, MIME=" + contentType
                    + ", header=" + headerHex + trailingInfo;
        }
    }
}
