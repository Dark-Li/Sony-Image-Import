package com.codex.sonyedge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Arrays;

public class DownloadValidatorTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void acceptsCompleteJpeg() throws Exception {
        File file = temporaryFolder.newFile("DSC00001.JPG.part");
        Files.write(file.toPath(), new byte[]{(byte) 0xFF, (byte) 0xD8,
                (byte) 0xFF, (byte) 0xD9});

        DownloadValidator.ValidationResult result = DownloadValidator.inspect(
                file, "image/jpeg", 4, 4, "DSC00001.JPG"
        );

        assertEquals("JPEG", result.format);
        assertEquals(4, result.bytes);
        assertEquals(0, result.trailingBytes);
    }

    @Test
    public void acceptsJpegWithTrailingDataAfterEndMarker() throws Exception {
        File file = temporaryFolder.newFile("DSC00003.JPG.part");
        Files.write(file.toPath(), new byte[]{(byte) 0xFF, (byte) 0xD8,
                (byte) 0xFF, (byte) 0xD9, 0x11, 0x22, 0x33});

        DownloadValidator.ValidationResult result = DownloadValidator.inspect(
                file, "image/jpeg", 7, 7, "DSC00003.JPG"
        );

        assertEquals("JPEG", result.format);
        assertEquals(3, result.trailingBytes);
        assertEquals(
                "JPEG, 7 bytes, MIME=image/jpeg, header=FF D8 FF D9 11 22 33, trailing=3 bytes",
                result.toDisplayString()
        );
    }

    @Test
    public void acceptsJpegWith16135TrailingBytes() throws Exception {
        File file = temporaryFolder.newFile("DSC00004.JPG.part");
        byte[] bytes = new byte[4 + 16135];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xD8;
        bytes[2] = (byte) 0xFF;
        bytes[3] = (byte) 0xD9;
        Files.write(file.toPath(), bytes);

        DownloadValidator.ValidationResult result = DownloadValidator.inspect(
                file, "image/jpeg", bytes.length, bytes.length, "DSC00004.JPG"
        );

        assertEquals(16135, result.trailingBytes);
    }

    @Test
    public void rejectsEmbeddedThumbnailEoiWhenTopLevelEoiIsMissing() throws Exception {
        File file = temporaryFolder.newFile("embedded-thumbnail.JPG.part");
        Files.write(file.toPath(), new byte[]{
                (byte) 0xFF, (byte) 0xD8,
                (byte) 0xFF, (byte) 0xE1, 0x00, 0x06,
                0x11, (byte) 0xFF, (byte) 0xD9, 0x22
        });

        DownloadValidator.ValidationException error = assertThrows(
                DownloadValidator.ValidationException.class,
                () -> DownloadValidator.inspect(file, "image/jpeg", 10, 10,
                        "embedded-thumbnail.JPG")
        );

        assertEquals("JPEG end marker is missing", error.getMessage());
        assertTrue(error.isTruncated());
    }

    @Test
    public void acceptsStuffedRestartFilledAndMultipleScanData() throws Exception {
        File file = temporaryFolder.newFile("multi-scan.JPG.part");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(new byte[]{(byte) 0xFF, (byte) 0xD8});
        bytes.write(new byte[]{(byte) 0xFF, (byte) 0xDA, 0x00, 0x02});
        bytes.write(new byte[]{0x11, (byte) 0xFF, 0x00, 0x22, (byte) 0xFF, (byte) 0xD0});
        bytes.write(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xDA, 0x00, 0x02});
        bytes.write(new byte[]{0x33, (byte) 0xFF, (byte) 0xD9});
        Files.write(file.toPath(), bytes.toByteArray());

        DownloadValidator.ValidationResult result = DownloadValidator.inspect(
                file, "image/jpeg", bytes.size(), bytes.size(), "multi-scan.JPG"
        );

        assertEquals("JPEG", result.format);
        assertEquals(0, result.trailingBytes);
    }

    @Test(timeout = 15_000)
    public void acceptsSeveralMegabytesOfEntropyCodedData() throws Exception {
        File file = temporaryFolder.newFile("large-scan.JPG.part");
        byte[] entropyChunk = new byte[64 * 1024];
        Arrays.fill(entropyChunk, (byte) 0x5A);

        try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(file))) {
            output.write(new byte[]{(byte) 0xFF, (byte) 0xD8});
            output.write(new byte[]{(byte) 0xFF, (byte) 0xDA, 0x00, 0x02});
            for (int i = 0; i < 64; i++) {
                output.write(entropyChunk);
                output.write(new byte[]{(byte) 0xFF, 0x00});
                if ((i & 7) == 7) {
                    output.write(new byte[]{(byte) 0xFF, (byte) 0xD0});
                }
            }
            output.write(new byte[]{(byte) 0xFF, (byte) 0xD9});
        }

        DownloadValidator.ValidationResult result = DownloadValidator.inspect(
                file, "image/jpeg", file.length(), file.length(), "large-scan.JPG"
        );

        assertEquals("JPEG", result.format);
        assertEquals(file.length(), result.bytes);
        assertEquals(0, result.trailingBytes);
    }

    @Test
    public void rejectsJpegWithoutEndMarker() throws Exception {
        File file = temporaryFolder.newFile("broken.JPG.part");
        Files.write(file.toPath(), new byte[]{
                (byte) 0xFF, (byte) 0xD8,
                (byte) 0xFF, (byte) 0xE0, 0x00, 0x04, 0x01, 0x02
        });

        DownloadValidator.ValidationException error = assertThrows(
                DownloadValidator.ValidationException.class,
                () -> DownloadValidator.inspect(file, "image/jpeg", 8, 8, "broken.JPG")
        );

        assertEquals("JPEG end marker is missing", error.getMessage());
        assertTrue(error.isTruncated());
    }

    @Test
    public void acceptsArwTiffHeader() throws Exception {
        File file = temporaryFolder.newFile("DSC00002.ARW.part");
        Files.write(file.toPath(), new byte[]{'I', 'I', 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00});

        DownloadValidator.ValidationResult result = DownloadValidator.inspect(
                file, "image/x-sony-arw", 8, 8, "DSC00002.ARW"
        );

        assertEquals("RAW/ARW", result.format);
    }

    @Test
    public void rejectsArwWhenNoExpectedLengthIsAvailable() throws Exception {
        File file = temporaryFolder.newFile("DSC00003.ARW.part");
        Files.write(file.toPath(), new byte[]{'I', 'I', 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00});

        DownloadValidator.ValidationException error = assertThrows(
                DownloadValidator.ValidationException.class,
                () -> DownloadValidator.inspect(
                        file, "image/x-sony-arw", -1, -1, "DSC00003.ARW"
                )
        );

        assertEquals(
                "ARW completeness cannot be verified without HTTP Content-Length or DIDL size",
                error.getMessage()
        );
        assertTrue(error.isTruncated());
    }

    @Test
    public void rejectsDeclaredLengthMismatch() throws Exception {
        File file = temporaryFolder.newFile("short.JPG.part");
        Files.write(file.toPath(), new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9});

        assertThrows(DownloadValidator.ValidationException.class, () ->
                DownloadValidator.inspect(file, "image/jpeg", 8, 4, "short.JPG"));
    }
}
