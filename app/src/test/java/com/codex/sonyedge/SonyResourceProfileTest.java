package com.codex.sonyedge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SonyResourceProfileTest {
    @Test
    public void originalProfileOutranksLargeJpeg() {
        SonyResourceProfile original = new SonyResourceProfile(
                "http://camera/DSC00001.ARW",
                "http-get:*:image/x-sony-arw:DLNA.ORG_PN=PN_ORIGINAL",
                42_000_000,
                7952,
                5304,
                ""
        );
        SonyResourceProfile large = new SonyResourceProfile(
                "http://camera/LRG_DSC00001.JPG",
                "http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_LRG",
                2_000_000,
                1920,
                1280,
                ""
        );

        assertTrue(original.isOriginalProfile());
        assertTrue(original.isRaw());
        assertTrue(original.originalPriority() > large.originalPriority());
        assertEquals("JPEG_LRG", large.dlnaProfileName);
    }

    @Test
    public void parsesSonyVideoProfile() {
        SonyResourceProfile profile = new SonyResourceProfile(
                "http://camera/C0001.MP4",
                "http-get:*:video/mp4:SONY.COM_PN=XAVC_S;DLNA.ORG_OP=01",
                100,
                3840,
                2160,
                "0:00:03"
        );

        assertEquals("XAVC_S", profile.sonyProfileName);
        assertTrue(profile.isVideo());
        assertTrue(profile.isXavc());
    }
}
