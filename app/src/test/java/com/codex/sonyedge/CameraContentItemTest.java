package com.codex.sonyedge;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CameraContentItemTest {
    @Test
    public void separatesThumbnailLargePreviewAndOriginal() {
        CameraContentItem item = item("TN_DSC00001.JPG", "LRG_DSC00001.JPG", "ORG_DSC00001.JPG");

        assertEquals("TN_DSC00001.JPG", item.previewThumbnailUrl());
        assertEquals("LRG_DSC00001.JPG", item.fullPreviewUrl());
        assertEquals("ORG_DSC00001.JPG", item.bestDownloadUrl());
    }

    @Test
    public void fallsBackToThumbnailWhenLargeUrlIsOriginal() {
        CameraContentItem item = item("TN_DSC00002.JPG", "ORG_DSC00002.JPG", "ORG_DSC00002.JPG");

        assertEquals("TN_DSC00002.JPG", item.fullPreviewUrl());
        assertEquals("ORG_DSC00002.JPG", item.bestDownloadUrl());
    }

    @Test
    public void previewsLargeJpegAndDownloadsArw() {
        CameraContentItem item = item("LRG_DSC00003.JPG", "LRG_DSC00003.JPG", "DSC00003.ARW");

        assertEquals("LRG_DSC00003.JPG", item.fullPreviewUrl());
        assertEquals("DSC00003.ARW", item.bestDownloadUrl());
    }

    @Test
    public void neverUsesOriginalAsPreviewFallback() {
        CameraContentItem item = item("ORG_DSC00004.JPG", "ORG_DSC00004.JPG", "ORG_DSC00004.JPG");

        assertEquals("", item.previewThumbnailUrl());
        assertEquals("", item.fullPreviewUrl());
        assertEquals("ORG_DSC00004.JPG", item.bestDownloadUrl());
    }

    private static CameraContentItem item(String thumbnail, String large, String original) {
        return new CameraContentItem(
                "DSC00001.JPG",
                "item-1",
                "object.item.imageItem.photo",
                thumbnail,
                large,
                original,
                10,
                "{}"
        );
    }
}
