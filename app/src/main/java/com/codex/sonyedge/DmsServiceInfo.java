package com.codex.sonyedge;

public final class DmsServiceInfo {
    public final String descriptionUrl;
    public final String serviceType;
    public final String controlUrl;
    public final SonyDeviceDescription device;
    public final SonyServiceDescription service;

    public DmsServiceInfo(String descriptionUrl, String serviceType, String controlUrl) {
        this(descriptionUrl, serviceType, controlUrl, null, null);
    }

    public DmsServiceInfo(SonyDeviceDescription device, SonyServiceDescription service) {
        this(
                device == null ? "" : device.location,
                service == null ? "" : service.serviceType,
                service == null ? "" : service.controlUrl,
                device,
                service
        );
    }

    private DmsServiceInfo(
            String descriptionUrl,
            String serviceType,
            String controlUrl,
            SonyDeviceDescription device,
            SonyServiceDescription service
    ) {
        this.descriptionUrl = descriptionUrl;
        this.serviceType = serviceType;
        this.controlUrl = controlUrl;
        this.device = device;
        this.service = service;
    }

    public static DmsServiceInfo cached(String controlUrl) {
        return new DmsServiceInfo("cached", "urn:schemas-upnp-org:service:ContentDirectory:1", controlUrl);
    }

    @Override
    public String toString() {
        return serviceType + " -> " + controlUrl;
    }
}
