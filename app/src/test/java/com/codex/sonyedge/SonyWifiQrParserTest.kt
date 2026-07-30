package com.codex.sonyedge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SonyWifiQrParserTest {
    @Test
    fun parsesA7r3QrCode() {
        val payload = SonyWifiQrParser.parse(
            "W01:S:leE1;P:GaModALT;C:ILCE-7RM3;M:E8E8B7349C13;"
        )

        assertEquals("leE1", payload.ssidSuffix)
        assertEquals("DIRECT-leE1:ILCE-7RM3", payload.ssid)
        assertEquals("GaModALT", payload.password)
        assertEquals("ILCE-7RM3", payload.cameraModel)
        assertEquals("E8E8B7349C13", payload.cameraIdentity)
    }

    @Test
    fun normalizesSeparatedCameraIdentity() {
        val payload = SonyWifiQrParser.parse(
            "W01:S:leE1;P:GaModALT;C:ILCE-7RM3;M:E8:E8:B7:34:9C:13;"
        )

        assertEquals("E8E8B7349C13", payload.cameraIdentity)
    }

    @Test
    fun rejectsNonSonyQrCode() {
        assertThrows(IllegalArgumentException::class.java) {
            SonyWifiQrParser.parse("WIFI:S:test;T:WPA;P:password;;")
        }
    }

    @Test
    fun rejectsMissingRequiredField() {
        assertThrows(IllegalArgumentException::class.java) {
            SonyWifiQrParser.parse("W01:S:leE1;P:GaModALT;C:ILCE-7RM3;")
        }
    }

    @Test
    fun rejectsDuplicateField() {
        assertThrows(IllegalArgumentException::class.java) {
            SonyWifiQrParser.parse(
                "W01:S:leE1;S:other;P:GaModALT;C:ILCE-7RM3;M:E8E8B7349C13;"
            )
        }
    }

    @Test
    fun rejectsInvalidCameraIdentity() {
        assertThrows(IllegalArgumentException::class.java) {
            SonyWifiQrParser.parse(
                "W01:S:leE1;P:GaModALT;C:ILCE-7RM3;M:not-a-mac;"
            )
        }
    }

    @Test
    fun extractsCameraIdentityFromSonyUdn() {
        assertEquals(
            "E8E8B7349C13",
            SonyWifiQrParser.cameraIdentityFromUdn(
                "uuid:00000000-0000-0010-8000-e8e8b7349c13"
            )
        )
    }
}
