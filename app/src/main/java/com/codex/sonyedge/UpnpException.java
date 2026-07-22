package com.codex.sonyedge;

import java.io.IOException;

public final class UpnpException extends IOException {
    public final String action;
    public final int httpStatus;
    public final String faultCode;
    public final String faultString;
    public final int upnpErrorCode;
    public final String upnpErrorDescription;

    public UpnpException(
            String action,
            int httpStatus,
            String faultCode,
            String faultString,
            int upnpErrorCode,
            String upnpErrorDescription,
            String detail
    ) {
        super(buildMessage(action, httpStatus, faultCode, faultString, upnpErrorCode,
                upnpErrorDescription, detail));
        this.action = value(action);
        this.httpStatus = httpStatus;
        this.faultCode = value(faultCode);
        this.faultString = value(faultString);
        this.upnpErrorCode = upnpErrorCode;
        this.upnpErrorDescription = value(upnpErrorDescription);
    }

    public UpnpException(String action, String detail, Throwable cause) {
        super("UPnP " + value(action) + " failed: " + value(detail), cause);
        this.action = value(action);
        this.httpStatus = -1;
        this.faultCode = "";
        this.faultString = "";
        this.upnpErrorCode = -1;
        this.upnpErrorDescription = "";
    }

    private static String buildMessage(
            String action,
            int httpStatus,
            String faultCode,
            String faultString,
            int upnpErrorCode,
            String upnpErrorDescription,
            String detail
    ) {
        StringBuilder message = new StringBuilder("UPnP ").append(value(action)).append(" failed");
        if (httpStatus > 0) {
            message.append(" (HTTP ").append(httpStatus).append(')');
        }
        if (!value(faultCode).isEmpty()) {
            message.append(": ").append(faultCode);
        }
        if (!value(faultString).isEmpty()) {
            message.append(' ').append(faultString);
        }
        if (upnpErrorCode >= 0) {
            message.append(" [UPnP ").append(upnpErrorCode);
            if (!value(upnpErrorDescription).isEmpty()) {
                message.append(' ').append(upnpErrorDescription);
            }
            message.append(']');
        }
        if (!value(detail).isEmpty()) {
            message.append(": ").append(detail);
        }
        return message.toString();
    }

    private static String value(String text) {
        return text == null ? "" : text.trim();
    }
}
