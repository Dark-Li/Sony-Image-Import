package com.codex.sonyedge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;
import android.util.Log;

import org.json.JSONArray;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class DownloadService extends Service {
    private static final String TAG = "SonyEdge-Download";
    public static final String ACTION_START = "com.codex.sonyedge.START_DOWNLOAD";
    public static final String ACTION_CANCEL = "com.codex.sonyedge.CANCEL_DOWNLOAD";
    public static final String ACTION_PROGRESS = "com.codex.sonyedge.DOWNLOAD_PROGRESS";
    public static final String EXTRA_ITEMS = "items";
    public static final String EXTRA_MESSAGE = "message";
    public static final String EXTRA_STATE = "state";
    public static final String EXTRA_TOTAL = "total";
    public static final String EXTRA_INDEX = "index";
    public static final String EXTRA_SUCCESS = "success";
    public static final String EXTRA_FAILED = "failed";
    public static final String EXTRA_FILENAME = "filename";
    public static final String EXTRA_ITEM_JSON = "item_json";
    public static final String EXTRA_BYTES_DONE = "bytes_done";
    public static final String EXTRA_BYTES_TOTAL = "bytes_total";
    public static final String EXTRA_SPEED_BPS = "speed_bps";
    public static final String EXTRA_ETA_SECONDS = "eta_seconds";
    public static final String EXTRA_BATCH_BYTES_DONE = "batch_bytes_done";
    public static final String EXTRA_ELAPSED_SECONDS = "elapsed_seconds";
    public static final String EXTRA_XPUSH_CONTROL_URL = "xpush_control_url";
    public static final String EXTRA_XPUSH_SERVICE_TYPE = "xpush_service_type";
    public static final String EXTRA_XPUSH_TOTAL = "xpush_total";
    public static final String EXTRA_BATCH_TITLE = "batch_title";
    public static final String EXTRA_QUEUE_SIZE = "queue_size";

    public static final String STATE_STARTED = "started";
    public static final String STATE_FILE_STARTED = "file_started";
    public static final String STATE_FILE_PROGRESS = "file_progress";
    public static final String STATE_FILE_DONE = "file_done";
    public static final String STATE_FILE_FAILED = "file_failed";
    public static final String STATE_QUEUED = "queued";
    public static final String STATE_DONE = "done";
    public static final String STATE_FATAL = "fatal";
    public static final String STATE_CANCELLED = "cancelled";

    private static final String CHANNEL_ID = "sonyedge_downloads";
    private static final int NOTIFICATION_ID = 7;
    private static final String PUBLIC_OUTPUT_DIR = Environment.DIRECTORY_DCIM + "/Sony Picture";
    private static final String USER_AGENT = "UPnP/1.0 DLNADOC/1.50 SonyEdge/" + BuildConfig.VERSION_NAME;
    private static final int MAX_DOWNLOAD_ATTEMPTS = 5;
    private static final long[] RETRY_DELAYS_MS = {300, 800, 1500, 1500};
    private static final long[] XPUSH_RETRY_DELAYS_MS = {300, 800};

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicReference<BatchRun> activeBatch = new AtomicReference<>();
    private final Object queueLock = new Object();
    private final ArrayDeque<BatchRun> pendingBatches = new ArrayDeque<>();

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "" : intent.getAction();
        if (ACTION_CANCEL.equals(action)) {
            BatchRun run;
            List<BatchRun> pending;
            synchronized (queueLock) {
                run = activeBatch.get();
                pending = new ArrayList<>(pendingBatches);
                pendingBatches.clear();
                for (BatchRun queued : pending) {
                    queued.cancel();
                }
            }
            if (run != null) {
                run.latestStartId.set(startId);
                if (!run.terminalPublished.get()) {
                    run.cancel();
                    updateNotification("Cancelling downloads");
                }
            } else {
                stopSelfResult(startId);
            }
            if (!pending.isEmpty()) {
                publishQueueUpdate("Queued imports cancelled.");
            }
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(action)) {
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }

        BatchRun run = new BatchRun(
                startId,
                intent.getStringExtra(EXTRA_ITEMS),
                intent.getStringExtra(EXTRA_XPUSH_CONTROL_URL),
                intent.getStringExtra(EXTRA_XPUSH_SERVICE_TYPE),
                intent.getIntExtra(EXTRA_XPUSH_TOTAL, 0),
                intent.getStringExtra(EXTRA_BATCH_TITLE)
        );
        boolean startImmediately;
        int queueSize;
        synchronized (queueLock) {
            startImmediately = activeBatch.get() == null;
            if (startImmediately) {
                activeBatch.set(run);
            } else {
                pendingBatches.addLast(run);
            }
            queueSize = pendingBatches.size();
        }
        startForeground(NOTIFICATION_ID, notification("Preparing downloads"));
        if (startImmediately) {
            executor.execute(() -> runDownloads(run));
        } else {
            publishQueueUpdate("Import queued behind the current task. Pending batches: " + queueSize);
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        BatchRun run = activeBatch.get();
        if (run != null) {
            run.cancel();
        }
        synchronized (queueLock) {
            for (BatchRun queued : pendingBatches) {
                queued.cancel();
            }
            pendingBatches.clear();
        }
        executor.shutdownNow();
        super.onDestroy();
    }

    private void runDownloads(BatchRun run) {
        int total = 0;
        int success = 0;
        int failed = 0;
        long batchBytesDone = 0;
        long startedAt = System.currentTimeMillis();
        CameraWifiBinding.Lease wifiLease = null;
        try {
            if (!isCurrent(run)) {
                return;
            }
            wifiLease = CameraWifiBinding.acquire(this);
            JSONArray array = new JSONArray(run.itemsJson == null ? "[]" : run.itemsJson);
            total = array.length();
            publishFor(run, STATE_STARTED, "Queued " + total + " downloads.", total, 0, 0, 0, "", "");

            File dir = new File(getCacheDir(), "downloads");
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IllegalStateException("Cannot create output directory: " + dir);
            }

            for (int i = 0; i < array.length(); i++) {
                throwIfCancelled(run);
                CameraContentItem item = CameraContentItem.fromJson(array.getJSONObject(i));
                String prefix = String.format(Locale.US, "%d/%d ", i + 1, array.length());
                publishFor(run, STATE_FILE_STARTED, prefix + "Downloading " + item.title,
                        total, i + 1, success, failed, item.title, "");
                updateNotification(prefix + item.title);
                try {
                    DownloadValidator.ValidationResult result = downloadWithRetries(
                            run, item, dir, total, i + 1, success, failed, batchBytesDone, startedAt
                    );
                    throwIfCancelled(run);
                    batchBytesDone += result.bytes;
                    success++;
                    reportXPushProgress(run, success + failed);
                    publishFor(
                            run,
                            STATE_FILE_DONE,
                            prefix + item.title + " -> " + result.toDisplayString(),
                            total,
                            i + 1,
                            success,
                            failed,
                            item.title,
                            "",
                            result.bytes,
                            result.bytes,
                            averageBytesPerSecond(batchBytesDone, startedAt),
                            0,
                            batchBytesDone,
                            elapsedSeconds(startedAt)
                    );
                } catch (InterruptedException ex) {
                    throw ex;
                } catch (Exception ex) {
                    throwIfCancelled(run);
                    failed++;
                    reportXPushProgress(run, success);
                    publishFor(
                            run,
                            STATE_FILE_FAILED,
                            prefix + item.title + " failed: " + messageOf(ex),
                            total,
                            i + 1,
                            success,
                            failed,
                            item.title,
                            item.toJson().toString(),
                            0,
                            0,
                            averageBytesPerSecond(batchBytesDone, startedAt),
                            0,
                            batchBytesDone,
                            elapsedSeconds(startedAt)
                    );
                }
            }

            throwIfCancelled(run);
            String xPushFailure = finishXPush(run, failed == 0 ? 0 : 1);
            boolean protocolFailed = run.xPushProtocolFailed.get() || xPushFailure != null;
            publishTerminal(
                    run,
                    protocolFailed ? STATE_FATAL : STATE_DONE,
                    protocolFailed
                            ? "Downloads saved, but camera transfer finalization failed: "
                                    + (xPushFailure == null ? "X_TransferProgress failed" : xPushFailure)
                            : "Downloads complete. Success " + success + ", failed " + failed
                                    + ". Output: DCIM/Sony Picture",
                    total,
                    total,
                    success,
                    failed,
                    batchBytesDone,
                    startedAt
            );
        } catch (InterruptedException ex) {
            String xPushFailure = finishXPush(run, 1);
            publishTerminal(
                    run,
                    STATE_CANCELLED,
                    "Downloads cancelled. Success " + success + ", failed " + failed + "."
                            + xPushFailureSuffix(xPushFailure),
                    total,
                    0,
                    success,
                    failed,
                    batchBytesDone,
                    startedAt
            );
        } catch (Exception ex) {
            String xPushFailure = finishXPush(run, 1);
            if (run.cancelled.get()) {
                publishTerminal(
                        run,
                        STATE_CANCELLED,
                        "Downloads cancelled. Success " + success + ", failed " + failed + "."
                                + xPushFailureSuffix(xPushFailure),
                        total,
                        0,
                        success,
                        failed,
                        batchBytesDone,
                        startedAt
                );
            } else {
                publishTerminal(
                        run,
                        STATE_FATAL,
                        "Download failed: " + messageOf(ex) + xPushFailureSuffix(xPushFailure),
                        total,
                        0,
                        success,
                        failed,
                        batchBytesDone,
                        startedAt
                );
            }
        } finally {
            if (wifiLease != null) {
                wifiLease.close();
            }
            BatchRun next = null;
            synchronized (queueLock) {
                if (activeBatch.compareAndSet(run, null)) {
                    next = pendingBatches.pollFirst();
                    if (next != null) {
                        activeBatch.set(next);
                    }
                }
            }
            if (next != null) {
                updateNotification("Preparing queued import");
                BatchRun nextRun = next;
                executor.execute(() -> runDownloads(nextRun));
            } else {
                stopForeground(STOP_FOREGROUND_DETACH);
                stopSelfResult(run.latestStartId.get());
            }
        }
    }

    private DownloadValidator.ValidationResult downloadWithRetries(
            BatchRun run,
            CameraContentItem item,
            File dir,
            int total,
            int index,
            int success,
            int failed,
            long batchBytesBeforeFile,
            long batchStartedAt
    ) throws Exception {
        Exception lastFailure = null;
        for (int attempt = 1; attempt <= MAX_DOWNLOAD_ATTEMPTS; attempt++) {
            throwIfCancelled(run);
            try {
                return downloadAttempt(
                        run, item, dir, total, index, success, failed,
                        batchBytesBeforeFile, batchStartedAt
                );
            } catch (InterruptedException ex) {
                throw ex;
            } catch (Exception ex) {
                lastFailure = ex;
                if (!isRetryable(ex) || attempt >= MAX_DOWNLOAD_ATTEMPTS) {
                    throw ex;
                }
                long delayMs = RETRY_DELAYS_MS[Math.min(attempt - 1, RETRY_DELAYS_MS.length - 1)];
                publishFor(
                        run,
                        STATE_FILE_STARTED,
                        String.format(
                                Locale.US,
                                "%d/%d Retrying %s (%d/%d) in %d ms: %s",
                                index,
                                total,
                                item.title,
                                attempt + 1,
                                MAX_DOWNLOAD_ATTEMPTS,
                                delayMs,
                                messageOf(ex)
                        ),
                        total,
                        index,
                        success,
                        failed,
                        item.title,
                        ""
                );
                sleepWithCancellation(run, delayMs);
            }
        }
        throw lastFailure == null ? new IOException("Download failed") : lastFailure;
    }

    private DownloadValidator.ValidationResult downloadAttempt(
            BatchRun run,
            CameraContentItem item,
            File dir,
            int total,
            int index,
            int success,
            int failed,
            long batchBytesBeforeFile,
            long batchStartedAt
    ) throws Exception {
        String urlText = item.bestDownloadUrl();
        if (urlText == null || urlText.isEmpty()) {
            throw new IllegalArgumentException("No download URL for " + item.title);
        }

        String resolvedUrl = shouldProbeLegacyFullSizeCandidate(item, urlText)
                ? resolveBestDownloadUrl(urlText)
                : urlText;
        long didlExpectedSize = resolvedUrl.equals(urlText) ? item.size : -1;
        if (!resolvedUrl.equals(urlText)) {
            publishFor(run, "", "Using full-size candidate: " + filenameFromUrl(resolvedUrl),
                    0, 0, 0, 0, "", "");
        }

        HttpURLConnection connection = null;
        File partFile = null;
        try {
            connection = (HttpURLConnection) new URL(resolvedUrl).openConnection();
            run.currentConnection.set(connection);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(30000);
            setDownloadHeaders(connection);
            int code = connection.getResponseCode();
            if (code == HttpURLConnection.HTTP_UNAVAILABLE) {
                throw new RetryableHttpException(code, resolvedUrl);
            }
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code + " for " + resolvedUrl);
            }

            long contentLength = connection.getContentLengthLong();
            String contentType = connection.getContentType();
            String filename = safeFilename(item.title);
            if (!filename.contains(".")) {
                filename = filename + extensionFromContentType(contentType);
            }
            partFile = uniquePartFile(dir, filename);
            long expectedResponseLength = contentLength > 0 ? contentLength : didlExpectedSize;
            long progressTotal = Math.max(0, expectedResponseLength);

            long bytesDone = 0;
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(partFile)) {
                byte[] buffer = new byte[64 * 1024];
                long lastBytes = 0;
                long lastAt = System.currentTimeMillis();
                int read;
                while (expectedResponseLength <= 0 || bytesDone < expectedResponseLength) {
                    int requested = buffer.length;
                    if (expectedResponseLength > 0) {
                        requested = (int) Math.min(buffer.length, expectedResponseLength - bytesDone);
                    }
                    read = input.read(buffer, 0, requested);
                    if (read == -1) {
                        break;
                    }
                    throwIfCancelled(run);
                    output.write(buffer, 0, read);
                    bytesDone += read;
                    long now = System.currentTimeMillis();
                    if (now - lastAt >= 500 || (progressTotal > 0 && bytesDone >= progressTotal)) {
                        long elapsedMs = Math.max(1, now - lastAt);
                        long bytesDelta = Math.max(0, bytesDone - lastBytes);
                        long speedBps = bytesDelta * 1000L / elapsedMs;
                        long etaSeconds = 0;
                        if (progressTotal > 0 && speedBps > 0) {
                            etaSeconds = (long) Math.ceil(Math.max(0, progressTotal - bytesDone) / (double) speedBps);
                        }
                        publishFor(
                                run,
                                STATE_FILE_PROGRESS,
                                String.format(Locale.US, "%d/%d Importing %s", index, total, item.title),
                                total,
                                index,
                                success,
                                failed,
                                item.title,
                                "",
                                bytesDone,
                                progressTotal,
                                speedBps,
                                etaSeconds,
                                batchBytesBeforeFile + bytesDone,
                                elapsedSeconds(batchStartedAt)
                        );
                        lastAt = now;
                        lastBytes = bytesDone;
                    }
                }
                output.getFD().sync();
            }

            if (partFile.length() != bytesDone) {
                throw new EOFException("Temporary file length changed while downloading");
            }
            DownloadValidator.ValidationResult result = DownloadValidator.inspect(
                    partFile,
                    contentType,
                    contentLength,
                    didlExpectedSize,
                    filename
            );
            throwIfCancelled(run);
            try {
                saveToPublicDcim(run, partFile, filename, contentType);
            } catch (InterruptedException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new PublishException("Cannot publish " + filename + ": " + messageOf(ex));
            }
            return result;
        } finally {
            if (connection != null) {
                run.currentConnection.compareAndSet(connection, null);
                connection.disconnect();
            }
            if (partFile != null && partFile.exists() && !partFile.delete()) {
                partFile.deleteOnExit();
            }
        }
    }

    private void reportXPushProgress(BatchRun run, int transferred) {
        if (run.xPushClient == null || run.xPushEnded.get()) {
            return;
        }
        Exception lastFailure = null;
        for (int attempt = 0; attempt <= XPUSH_RETRY_DELAYS_MS.length; attempt++) {
            try {
                run.xPushClient.transferProgress(run.xPushTotal, transferred);
                return;
            } catch (Exception ex) {
                lastFailure = ex;
                Log.w(TAG, "XPush progress attempt " + (attempt + 1) + " failed", ex);
                if (attempt < XPUSH_RETRY_DELAYS_MS.length) {
                    sleepProtocolRetry(XPUSH_RETRY_DELAYS_MS[attempt]);
                }
            }
        }
        run.xPushProtocolFailed.set(true);
        Log.e(TAG, "XPush progress exhausted retries", lastFailure);
    }

    private String finishXPush(BatchRun run, int errorCode) {
        if (run.xPushClient == null || run.xPushEnded.get()) {
            return null;
        }
        int finalCode = run.xPushProtocolFailed.get() ? 1 : errorCode;
        Exception lastFailure = null;
        for (int attempt = 0; attempt <= XPUSH_RETRY_DELAYS_MS.length; attempt++) {
            try {
                run.xPushClient.transferEnd(finalCode);
                run.xPushEnded.set(true);
                return null;
            } catch (Exception ex) {
                lastFailure = ex;
                Log.w(TAG, "XPush end attempt " + (attempt + 1) + " failed", ex);
                if (attempt < XPUSH_RETRY_DELAYS_MS.length) {
                    sleepProtocolRetry(XPUSH_RETRY_DELAYS_MS[attempt]);
                }
            }
        }
        run.xPushEnded.set(true);
        return messageOf(lastFailure);
    }

    private void sleepProtocolRetry(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private String xPushFailureSuffix(String failure) {
        return failure == null || failure.isEmpty() ? "" : " Camera transfer finalization failed: " + failure;
    }

    private String resolveBestDownloadUrl(String originalUrl) {
        List<String> candidates = fullSizeCandidates(originalUrl);
        ProbeResult best = null;
        for (String candidate : candidates) {
            ProbeResult result = probeDownload(candidate);
            if (!result.ok) {
                continue;
            }
            if (best == null || result.score() > best.score()) {
                best = result;
            }
        }
        return best == null ? originalUrl : best.url;
    }

    private boolean shouldProbeLegacyFullSizeCandidate(CameraContentItem item, String selectedUrl) {
        if (item == null || selectedUrl == null || selectedUrl.isEmpty()) {
            return false;
        }
        for (SonyResourceProfile resource : item.resources) {
            if (!selectedUrl.equals(resource.url)) {
                continue;
            }
            return resource.isJpegLarge()
                    && !resource.isOriginalProfile()
                    && !resource.isExplicitOriginalMedia();
        }
        return false;
    }

    private List<String> fullSizeCandidates(String url) {
        List<String> candidates = new ArrayList<>();
        addCandidate(candidates, url);

        String[] previewPrefixes = {"LRG_", "lrg_", "LARGE_", "large_", "SM_", "sm_", "THM_", "thm_"};
        for (String prefix : previewPrefixes) {
            if (url.contains(prefix)) {
                addCandidate(candidates, url.replace(prefix, "ORG_"));
                addCandidate(candidates, url.replace(prefix, ""));
            }
        }
        return candidates;
    }

    private void addCandidate(List<String> candidates, String candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return;
        }
        for (String existing : candidates) {
            if (existing.equals(candidate)) {
                return;
            }
        }
        candidates.add(candidate);
    }

    private ProbeResult probeDownload(String urlText) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(urlText).openConnection();
            connection.setConnectTimeout(1200);
            connection.setReadTimeout(1800);
            setDownloadHeaders(connection);
            connection.setRequestProperty("Range", "bytes=0-0");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                return ProbeResult.failed(urlText);
            }
            long length = connection.getContentLengthLong();
            String range = connection.getHeaderField("Content-Range");
            if (range != null) {
                int slash = range.lastIndexOf('/');
                if (slash >= 0 && slash + 1 < range.length()) {
                    try {
                        length = Long.parseLong(range.substring(slash + 1));
                    } catch (Exception ignored) {
                        // Keep the Content-Length fallback.
                    }
                }
            }
            return ProbeResult.ok(urlText, length);
        } catch (Exception ex) {
            return ProbeResult.failed(urlText);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void setDownloadHeaders(HttpURLConnection connection) {
        connection.setRequestProperty("Accept", "*/*");
        connection.setRequestProperty("Accept-Encoding", "identity");
        connection.setRequestProperty("Connection", "close");
        connection.setRequestProperty("User-Agent", USER_AGENT);
    }

    private static final class ProbeResult {
        final String url;
        final boolean ok;
        final long bytes;

        private ProbeResult(String url, boolean ok, long bytes) {
            this.url = url;
            this.ok = ok;
            this.bytes = bytes;
        }

        static ProbeResult ok(String url, long bytes) {
            return new ProbeResult(url, true, bytes);
        }

        static ProbeResult failed(String url) {
            return new ProbeResult(url, false, -1);
        }

        long score() {
            String lower = url.toLowerCase(Locale.US);
            long score = Math.max(bytes, 0);
            if (lower.contains("org_") || lower.contains("original")) {
                score += 100_000_000L;
            }
            if (lower.contains("lrg_") || lower.contains("large_") || lower.contains("thumb") || lower.contains("sm_")) {
                score -= 100_000_000L;
            }
            return score;
        }
    }

    private static final class BatchRun {
        final String itemsJson;
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        final AtomicBoolean terminalPublished = new AtomicBoolean(false);
        final AtomicInteger latestStartId;
        final AtomicReference<HttpURLConnection> currentConnection = new AtomicReference<>();
        final int xPushTotal;
        final String batchTitle;
        final XPushListClient xPushClient;
        final AtomicBoolean xPushProtocolFailed = new AtomicBoolean(false);
        final AtomicBoolean xPushEnded = new AtomicBoolean(false);

        BatchRun(
                int startId,
                String itemsJson,
                String xPushControlUrl,
                String xPushServiceType,
                int xPushTotal,
                String batchTitle
        ) {
            this.itemsJson = itemsJson;
            this.latestStartId = new AtomicInteger(startId);
            this.xPushTotal = Math.max(0, xPushTotal);
            this.batchTitle = batchTitle == null ? "" : batchTitle.trim();
            String controlUrl = xPushControlUrl == null ? "" : xPushControlUrl.trim();
            this.xPushClient = controlUrl.isEmpty()
                    ? null
                    : new XPushListClient(controlUrl, xPushServiceType, new StringBuilder());
        }

        void cancel() {
            cancelled.set(true);
            HttpURLConnection connection = currentConnection.get();
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static final class RetryableHttpException extends IOException {
        RetryableHttpException(int statusCode, String url) {
            super("HTTP " + statusCode + " for " + url);
        }
    }

    private static final class PublishException extends IOException {
        PublishException(String message) {
            super(message);
        }
    }

    private String filenameFromUrl(String urlText) {
        try {
            String path = new URL(urlText).getPath();
            int slash = path.lastIndexOf('/');
            return slash >= 0 ? path.substring(slash + 1) : path;
        } catch (Exception ignored) {
            return urlText;
        }
    }

    private void saveToPublicDcim(BatchRun run, File source, String filename, String contentType) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
            values.put(MediaStore.MediaColumns.MIME_TYPE,
                    contentType == null ? mimeFromFilename(filename) : contentType);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, PUBLIC_OUTPUT_DIR);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);

            Uri uri = null;
            try {
                uri = getContentResolver().insert(mediaStoreUriFor(filename, contentType), values);
                if (uri == null) {
                    throw new IllegalStateException("Cannot create MediaStore entry for " + filename);
                }
                long copied;
                try (InputStream input = new FileInputStream(source);
                     OutputStream output = getContentResolver().openOutputStream(uri)) {
                    if (output == null) {
                        throw new IllegalStateException("Cannot open MediaStore output for " + filename);
                    }
                    copied = copy(run, input, output);
                }
                if (copied != source.length()) {
                    throw new EOFException("MediaStore copy mismatch: expected " + source.length() + ", wrote " + copied);
                }
                throwIfCancelled(run);
                ContentValues done = new ContentValues();
                done.put(MediaStore.MediaColumns.IS_PENDING, 0);
                int updated = getContentResolver().update(uri, done, null, null);
                if (updated != 1) {
                    throw new IllegalStateException("Cannot publish MediaStore entry for " + filename);
                }
                uri = null;
                return;
            } finally {
                if (uri != null) {
                    getContentResolver().delete(uri, null, null);
                }
            }
        }

        File publicDir = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                "Sony Picture"
        );
        if (!publicDir.exists() && !publicDir.mkdirs()) {
            throw new IllegalStateException("Cannot create " + publicDir);
        }
        File outputFile = uniqueFile(publicDir, filename);
        File outputPart = uniqueFile(publicDir, outputFile.getName() + ".part");
        try {
            long copied;
            try (InputStream input = new FileInputStream(source);
                 OutputStream output = new FileOutputStream(outputPart)) {
                copied = copy(run, input, output);
            }
            if (copied != source.length()) {
                throw new EOFException("Public file copy mismatch: expected " + source.length() + ", wrote " + copied);
            }
            throwIfCancelled(run);
            if (!outputPart.renameTo(outputFile)) {
                throw new IOException("Cannot publish " + outputFile);
            }
        } finally {
            if (outputPart.exists() && !outputPart.delete()) {
                outputPart.deleteOnExit();
            }
        }
    }

    private long copy(BatchRun run, InputStream input, OutputStream output) throws Exception {
        byte[] buffer = new byte[64 * 1024];
        long copied = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            throwIfCancelled(run);
            output.write(buffer, 0, read);
            copied += read;
        }
        output.flush();
        return copied;
    }

    private File uniquePartFile(File dir, String filename) throws IOException {
        for (int i = 0; i < 10; i++) {
            File candidate = new File(dir, UUID.randomUUID() + "-" + filename + ".part");
            if (candidate.createNewFile()) {
                return candidate;
            }
        }
        throw new IOException("Cannot create a unique partial file for " + filename);
    }

    private File uniqueFile(File dir, String filename) {
        File candidate = new File(dir, filename);
        if (!candidate.exists()) {
            return candidate;
        }
        int dot = filename.lastIndexOf('.');
        String base = dot > 0 ? filename.substring(0, dot) : filename;
        String ext = dot > 0 ? filename.substring(dot) : "";
        for (int i = 1; ; i++) {
            candidate = new File(dir, base + "_" + i + ext);
            if (!candidate.exists()) {
                return candidate;
            }
        }
    }

    private String safeFilename(String value) {
        String fallback = value == null || value.trim().isEmpty() ? "sony_image" : value.trim();
        return fallback.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String extensionFromContentType(String contentType) {
        if (contentType == null) {
            return ".bin";
        }
        String lower = contentType.toLowerCase(Locale.US);
        if (lower.contains("jpeg") || lower.contains("jpg")) {
            return ".jpg";
        }
        if (lower.contains("tiff") || lower.contains("arw")) {
            return ".arw";
        }
        return ".bin";
    }

    private String mimeFromFilename(String filename) {
        String lower = filename.toLowerCase(Locale.US);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".arw")) {
            return "image/x-sony-arw";
        }
        return "application/octet-stream";
    }

    private Uri mediaStoreUriFor(String filename, String contentType) {
        String mime = contentType == null ? mimeFromFilename(filename) : contentType.toLowerCase(Locale.US);
        String lower = filename.toLowerCase(Locale.US);
        if (mime.startsWith("image/") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".arw")) {
            return MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        }
        return MediaStore.Downloads.EXTERNAL_CONTENT_URI;
    }

    private boolean isRetryable(Exception exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof PublishException) {
                return false;
            }
            if (current instanceof RetryableHttpException
                    || current instanceof SocketTimeoutException
                    || current instanceof EOFException) {
                return true;
            }
            if (current instanceof DownloadValidator.ValidationException
                    && ((DownloadValidator.ValidationException) current).isTruncated()) {
                return true;
            }
            if (current instanceof SocketException || current instanceof IOException) {
                String message = current.getMessage();
                String lower = message == null ? "" : message.toLowerCase(Locale.US);
                if (lower.contains("connection reset")
                        || lower.contains("unexpected eof")
                        || lower.contains("unexpected end of stream")
                        || lower.contains("premature eof")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private void sleepWithCancellation(BatchRun run, long delayMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + delayMs;
        while (true) {
            throwIfCancelled(run);
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                return;
            }
            Thread.sleep(Math.min(remaining, 100));
        }
    }

    private void throwIfCancelled(BatchRun run) throws InterruptedException {
        if (run.cancelled.get() || !isCurrent(run)) {
            throw new InterruptedException("Cancelled");
        }
    }

    private boolean isCurrent(BatchRun run) {
        return activeBatch.get() == run;
    }

    private String messageOf(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        return message == null || message.trim().isEmpty()
                ? throwable == null ? "Unknown error" : throwable.getClass().getSimpleName()
                : message;
    }

    private void publishTerminal(
            BatchRun run,
            String state,
            String message,
            int total,
            int index,
            int success,
            int failed,
            long batchBytesDone,
            long startedAt
    ) {
        if (!isCurrent(run) || !run.terminalPublished.compareAndSet(false, true)) {
            return;
        }
        publishProgress(
                state,
                message,
                total,
                index,
                success,
                failed,
                "",
                "",
                0,
                0,
                averageBytesPerSecond(batchBytesDone, startedAt),
                0,
                batchBytesDone,
                elapsedSeconds(startedAt)
        );
    }

    private void publishFor(
            BatchRun run,
            String state,
            String message,
            int total,
            int index,
            int success,
            int failed,
            String filename,
            String itemJson
    ) {
        publishFor(run, state, message, total, index, success, failed, filename, itemJson,
                0, 0, 0, 0, 0, 0);
    }

    private void publishFor(
            BatchRun run,
            String state,
            String message,
            int total,
            int index,
            int success,
            int failed,
            String filename,
            String itemJson,
            long bytesDone,
            long bytesTotal,
            long speedBps,
            long etaSeconds,
            long batchBytesDone,
            long elapsedSeconds
    ) {
        if (!isCurrent(run) || run.terminalPublished.get()) {
            return;
        }
        publishProgress(state, message, total, index, success, failed, filename, itemJson,
                bytesDone, bytesTotal, speedBps, etaSeconds, batchBytesDone, elapsedSeconds);
    }

    private void publishProgress(String state, String message, int total, int index, int success, int failed, String filename, String itemJson) {
        publishProgress(state, message, total, index, success, failed, filename, itemJson,
                0, 0, 0, 0, 0, 0);
    }

    private void publishQueueUpdate(String message) {
        publishProgress(
                STATE_QUEUED,
                message,
                0,
                0,
                0,
                0,
                "",
                "",
                0,
                0,
                0,
                0,
                0,
                0
        );
    }

    private void publishProgress(
            String state,
            String message,
            int total,
            int index,
            int success,
            int failed,
            String filename,
            String itemJson,
            long bytesDone,
            long bytesTotal,
            long speedBps,
            long etaSeconds,
            long batchBytesDone,
            long elapsedSeconds
    ) {
        if (!STATE_FILE_PROGRESS.equals(state)) {
            Log.d(TAG, state + " index=" + index + "/" + total + " success=" + success
                    + " failed=" + failed + " message=" + message);
        }
        Intent intent = new Intent(ACTION_PROGRESS);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_MESSAGE, message);
        intent.putExtra(EXTRA_STATE, state);
        intent.putExtra(EXTRA_TOTAL, total);
        intent.putExtra(EXTRA_INDEX, index);
        intent.putExtra(EXTRA_SUCCESS, success);
        intent.putExtra(EXTRA_FAILED, failed);
        intent.putExtra(EXTRA_FILENAME, filename);
        intent.putExtra(EXTRA_ITEM_JSON, itemJson);
        intent.putExtra(EXTRA_BYTES_DONE, bytesDone);
        intent.putExtra(EXTRA_BYTES_TOTAL, bytesTotal);
        intent.putExtra(EXTRA_SPEED_BPS, speedBps);
        intent.putExtra(EXTRA_ETA_SECONDS, etaSeconds);
        intent.putExtra(EXTRA_BATCH_BYTES_DONE, batchBytesDone);
        intent.putExtra(EXTRA_ELAPSED_SECONDS, elapsedSeconds);
        intent.putExtra(EXTRA_QUEUE_SIZE, pendingQueueSize());
        BatchRun currentRun = activeBatch.get();
        if (currentRun != null && !currentRun.batchTitle.isEmpty()) {
            intent.putExtra(EXTRA_BATCH_TITLE, currentRun.batchTitle);
        }
        sendBroadcast(intent);
    }

    private int pendingQueueSize() {
        synchronized (queueLock) {
            return pendingBatches.size();
        }
    }

    private long elapsedSeconds(long startedAt) {
        return Math.max(0, (System.currentTimeMillis() - startedAt) / 1000L);
    }

    private long averageBytesPerSecond(long bytes, long startedAt) {
        long elapsedMs = Math.max(1, System.currentTimeMillis() - startedAt);
        return bytes <= 0 ? 0 : bytes * 1000L / elapsedMs;
    }

    private void updateNotification(String text) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(NOTIFICATION_ID, notification(text));
    }

    private Notification notification(String text) {
        Intent launchIntent = new Intent(this, ComposeMainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setContentTitle("SonyEdge")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_stat_sonyedge)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "SonyEdge downloads",
                NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }
}
