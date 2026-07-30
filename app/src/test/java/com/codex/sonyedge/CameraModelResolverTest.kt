package com.codex.sonyedge

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraModelResolverTest {
    @Test
    fun prefersExplicitModelNumber() {
        assertEquals(
            "ILCE-7M4",
            resolveCameraModel("ILCE-7M4", "SonyImagingDevice", "Sony Camera", "DIRECT-AB:ILCE-7RM3")
        )
    }

    @Test
    fun extractsLegacyModelFromCameraSsidWhenDescriptionIsGeneric() {
        assertEquals(
            "ILCE-7RM3",
            resolveCameraModel("", "SonyImagingDevice", "SonyImagingDevice", "DIRECT-leE1:ILCE-7RM3")
        )
    }

    @Test
    fun doesNotTreatArbitrarySsidSuffixAsModel() {
        assertEquals(
            "Sony camera",
            resolveCameraModel("", "SonyImagingDevice", "SonyImagingDevice", "Camera-WiFi")
        )
    }
}
