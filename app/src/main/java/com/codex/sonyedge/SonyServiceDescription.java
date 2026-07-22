package com.codex.sonyedge;

public final class SonyServiceDescription {
    public final String serviceType;
    public final String serviceId;
    public final String scpdUrl;
    public final String controlUrl;
    public final String eventSubUrl;
    public final String apiType;
    public final String actionListUrl;
    public final String accessType;

    private SonyServiceDescription(
            String serviceType,
            String serviceId,
            String scpdUrl,
            String controlUrl,
            String eventSubUrl,
            String apiType,
            String actionListUrl,
            String accessType
    ) {
        this.serviceType = value(serviceType);
        this.serviceId = value(serviceId);
        this.scpdUrl = value(scpdUrl);
        this.controlUrl = value(controlUrl);
        this.eventSubUrl = value(eventSubUrl);
        this.apiType = value(apiType);
        this.actionListUrl = value(actionListUrl);
        this.accessType = value(accessType);
    }

    public static SonyServiceDescription upnp(
            String serviceType,
            String serviceId,
            String scpdUrl,
            String controlUrl,
            String eventSubUrl
    ) {
        return new SonyServiceDescription(
                serviceType, serviceId, scpdUrl, controlUrl, eventSubUrl, "", "", ""
        );
    }

    public static SonyServiceDescription scalarWebApi(
            String apiType,
            String actionListUrl,
            String accessType
    ) {
        return new SonyServiceDescription(
                "urn:schemas-sony-com:service:ScalarWebAPI:1",
                "",
                "",
                "",
                "",
                apiType,
                actionListUrl,
                accessType
        );
    }

    public boolean isContentDirectory() {
        return serviceType.contains(":ContentDirectory:");
    }

    public boolean isXPushList() {
        return serviceType.contains(":XPushList:");
    }

    public boolean isScalarWebApi() {
        return serviceType.contains(":ScalarWebAPI:") || !actionListUrl.isEmpty();
    }

    private static String value(String input) {
        return input == null ? "" : input;
    }

    @Override
    public String toString() {
        if (!actionListUrl.isEmpty()) {
            return apiType + " -> " + actionListUrl;
        }
        return serviceType + " -> " + controlUrl;
    }
}
