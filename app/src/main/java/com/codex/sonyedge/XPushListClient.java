package com.codex.sonyedge;

import android.util.Log;

public final class XPushListClient {
    private static final String TAG = "SonyEdge-XPush";
    public static final String DEFAULT_SERVICE_TYPE = "urn:schemas-sony-com:service:XPushList:1";

    public static final class TransferStartResult {
        public final String transferId;
        public final int errorCode;

        TransferStartResult(String transferId, int errorCode) {
            this.transferId = transferId;
            this.errorCode = errorCode;
        }
    }

    public static final class TransferProgress {
        public final int numTotal;
        public final int numTransferred;

        TransferProgress(int numTotal, int numTransferred) {
            this.numTotal = numTotal;
            this.numTransferred = numTransferred;
        }

        public boolean isComplete() {
            return numTotal >= 0 && numTransferred >= numTotal;
        }
    }

    public static final class TransferEndResult {
        public final int errorCode;

        TransferEndResult(int errorCode) {
            this.errorCode = errorCode;
        }
    }

    private final StringBuilder log;
    private final UpnpSoapClient soapClient;

    public XPushListClient(String controlUrl, StringBuilder log) {
        this(controlUrl, DEFAULT_SERVICE_TYPE, log);
    }

    public XPushListClient(String controlUrl, String serviceType, StringBuilder log) {
        this.log = log == null ? new StringBuilder() : log;
        String advertisedType = value(serviceType).isEmpty() ? DEFAULT_SERVICE_TYPE : serviceType;
        this.soapClient = new UpnpSoapClient(controlUrl, advertisedType, this::appendLog);
    }

    public TransferStartResult transferStart() throws UpnpException {
        appendLog("XPushList X_TransferStart");
        UpnpSoapClient.Response response = soapClient.call("X_TransferStart");
        TransferStartResult result = new TransferStartResult(
                firstNonEmpty(response.text("TransferID"), response.text("TransferId")),
                parseInt(response.text("ErrCode"), 0)
        );
        appendLog("XPushList X_TransferStart OK transferId=" + display(result.transferId)
                + " errCode=" + result.errorCode);
        return result;
    }

    public String getPushRoot() throws UpnpException {
        appendLog("XPushList X_GetPushRoot");
        UpnpSoapClient.Response response = soapClient.call("X_GetPushRoot");
        String pushRoot = firstNonEmpty(response.text("PushRoot"), response.text("X_PushRoot"),
                response.text("ObjectID"), response.text("ContainerID"), response.text("Root"));
        appendLog("XPushList X_GetPushRoot OK root=" + display(pushRoot));
        return pushRoot;
    }

    public TransferProgress transferProgress(int numTotal, int numTransferred) throws UpnpException {
        int safeTotal = Math.max(0, numTotal);
        int safeTransferred = Math.max(0, Math.min(numTransferred, safeTotal));
        appendLog("XPushList X_TransferProgress transferred=" + safeTransferred + "/" + safeTotal);
        UpnpSoapClient.Response response = soapClient.call("X_TransferProgress",
                UpnpSoapClient.arguments(
                        "NumTotal", safeTotal,
                        "NumTransferd", safeTransferred
                ));
        int total = parseInt(response.text("NumTotal"), safeTotal);
        int transferred = parseInt(firstNonEmpty(
                response.text("NumTransferd"),
                response.text("NumTransferred"),
                response.text("NumTransfered")
        ), safeTransferred);
        TransferProgress progress = new TransferProgress(total, transferred);
        appendLog("XPushList X_TransferProgress OK transferred=" + progress.numTransferred
                + "/" + progress.numTotal);
        return progress;
    }

    public TransferEndResult transferEnd(int errorCode) throws UpnpException {
        return transferEnd(String.valueOf(errorCode));
    }

    public TransferEndResult transferEnd(String errorCode) throws UpnpException {
        String safeErrorCode = value(errorCode).isEmpty() ? "0" : errorCode;
        appendLog("XPushList X_TransferEnd errCode=" + safeErrorCode);
        UpnpSoapClient.Response response = soapClient.call("X_TransferEnd",
                UpnpSoapClient.arguments("ErrCode", safeErrorCode));
        TransferEndResult result = new TransferEndResult(parseInt(
                firstNonEmpty(response.text("ErrCode"), safeErrorCode),
                parseInt(safeErrorCode, -1)
        ));
        appendLog("XPushList X_TransferEnd OK errCode=" + result.errorCode);
        return result;
    }

    private void appendLog(String message) {
        Log.d(TAG, message);
        synchronized (log) {
            log.append(message).append('\n');
        }
    }

    private static String firstNonEmpty(String... values) {
        for (String candidate : values) {
            if (!value(candidate).isEmpty()) {
                return candidate.trim();
            }
        }
        return "";
    }

    private static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(value(text));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String display(String text) {
        return value(text).isEmpty() ? "<none>" : text;
    }

    private static String value(String text) {
        return text == null ? "" : text.trim();
    }
}
