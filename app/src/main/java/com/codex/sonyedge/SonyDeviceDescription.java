package com.codex.sonyedge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SonyDeviceDescription {
    public final String location;
    public final String urlBase;
    public final String deviceType;
    public final String friendlyName;
    public final String manufacturer;
    public final String manufacturerUrl;
    public final String modelDescription;
    public final String modelName;
    public final String modelNumber;
    public final String modelUrl;
    public final String serialNumber;
    public final String udn;
    public final String presentationUrl;
    public final List<SonyServiceDescription> services;

    public SonyDeviceDescription(
            String location,
            String urlBase,
            String deviceType,
            String friendlyName,
            String manufacturer,
            String manufacturerUrl,
            String modelDescription,
            String modelName,
            String modelNumber,
            String modelUrl,
            String serialNumber,
            String udn,
            String presentationUrl,
            List<SonyServiceDescription> services
    ) {
        this.location = value(location);
        this.urlBase = value(urlBase);
        this.deviceType = value(deviceType);
        this.friendlyName = value(friendlyName);
        this.manufacturer = value(manufacturer);
        this.manufacturerUrl = value(manufacturerUrl);
        this.modelDescription = value(modelDescription);
        this.modelName = value(modelName);
        this.modelNumber = value(modelNumber);
        this.modelUrl = value(modelUrl);
        this.serialNumber = value(serialNumber);
        this.udn = value(udn);
        this.presentationUrl = value(presentationUrl);
        this.services = Collections.unmodifiableList(new ArrayList<>(services));
    }

    public List<SonyServiceDescription> servicesContaining(String token) {
        List<SonyServiceDescription> matches = new ArrayList<>();
        if (token == null || token.isEmpty()) {
            return matches;
        }
        for (SonyServiceDescription service : services) {
            if (service.serviceType.contains(token) || service.apiType.contains(token)) {
                matches.add(service);
            }
        }
        return matches;
    }

    private static String value(String input) {
        return input == null ? "" : input;
    }

    @Override
    public String toString() {
        return friendlyName + " (" + modelName + ") " + udn;
    }
}
