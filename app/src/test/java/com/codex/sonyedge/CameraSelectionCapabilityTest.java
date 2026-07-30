package com.codex.sonyedge;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CameraSelectionCapabilityTest {
    @Test
    public void acceptsXPushWithContentDirectory() {
        SonyDeviceDescription device = device(Arrays.asList(
                contentDirectory("http://camera/upnp/content"),
                xPush("http://camera/upnp/xpush")
        ));
        SonyCameraSession session = new SonyCameraSession(
                device,
                new DmsServiceInfo(device, device.services.get(0)),
                null
        );

        assertTrue(SonyEdgeViewModelKt.supportsCameraSelection(session));
    }

    @Test
    public void rejectsXPushWithoutControlUrl() {
        SonyDeviceDescription device = device(Arrays.asList(
                contentDirectory("http://camera/upnp/content"),
                xPush("")
        ));
        SonyCameraSession session = new SonyCameraSession(
                device,
                new DmsServiceInfo(device, device.services.get(0)),
                null
        );

        assertFalse(SonyEdgeViewModelKt.supportsCameraSelection(session));
    }

    @Test
    public void rejectsXPushWithoutContentDirectory() {
        SonyDeviceDescription device = device(Collections.singletonList(
                xPush("http://camera/upnp/xpush")
        ));

        assertFalse(SonyEdgeViewModelKt.supportsCameraSelection(
                new SonyCameraSession(device, null, null)
        ));
    }

    private static SonyServiceDescription contentDirectory(String controlUrl) {
        return SonyServiceDescription.upnp(
                "urn:schemas-upnp-org:service:ContentDirectory:1",
                "urn:upnp-org:serviceId:ContentDirectory",
                "",
                controlUrl,
                ""
        );
    }

    private static SonyServiceDescription xPush(String controlUrl) {
        return SonyServiceDescription.upnp(
                "urn:schemas-sony-com:service:XPushList:1",
                "urn:sony-com:serviceId:XPushList",
                "",
                controlUrl,
                ""
        );
    }

    private static SonyDeviceDescription device(List<SonyServiceDescription> services) {
        return new SonyDeviceDescription(
                "http://camera/device.xml",
                "http://camera/",
                "urn:schemas-upnp-org:device:MediaServer:1",
                "Sony Camera",
                "Sony",
                "",
                "",
                "ILCE-7RM3",
                "ILCE-7RM3",
                "",
                "",
                "uuid:camera",
                "",
                services
        );
    }
}
