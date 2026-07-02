package com.codex.sonyedge;

public final class DmsServiceInfo {
    public final String descriptionUrl;
    public final String serviceType;
    public final String controlUrl;

    public DmsServiceInfo(String descriptionUrl, String serviceType, String controlUrl) {
        this.descriptionUrl = descriptionUrl;
        this.serviceType = serviceType;
        this.controlUrl = controlUrl;
    }

    public static DmsServiceInfo cached(String controlUrl) {
        return new DmsServiceInfo("cached", "urn:schemas-upnp-org:service:ContentDirectory:1", controlUrl);
    }

    @Override
    public String toString() {
        return serviceType + " -> " + controlUrl;
    }
}
