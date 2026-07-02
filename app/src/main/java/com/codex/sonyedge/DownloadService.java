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

import org.json.JSONArray;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadService extends Service {
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

    public static final String STATE_STARTED = "started";
    public static final String STATE_FILE_STARTED = "file_started";
    public static final String STATE_FILE_DONE = "file_done";
    public static final String STATE_FILE_FAILED = "file_failed";
    public static final String STATE_DONE = "done";
    public static final String STATE_FATAL = "fatal";
    public static final String STATE_CANCELLED = "cancelled";

    private static final String CHANNEL_ID = "sonyedge_downloads";
    private static final int NOTIFICATION_ID = 7;
    private static final String PUBLIC_OUTPUT_DIR = Environment.DIRECTORY_DCIM + "/Sony Picture";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean cancelled;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !ACTION_START.equals(intent.getAction())) {
            if (intent != null && ACTION_CANCEL.equals(intent.getAction())) {
                cancelled = true;
                publishProgress(STATE_CANCELLED, "Cancelling downloads...", 0, 0, 0, 0, "", "");
                updateNotification("Cancelling downloads");
            }
            return START_NOT_STICKY;
        }

        cancelled = false;
        startForeground(NOTIFICATION_ID, notification("Preparing downloads"));
        String itemsJson = intent.getStringExtra(EXTRA_ITEMS);
        executor.execute(() -> runDownloads(itemsJson));
        return START_REDELIVER_INTENT;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void runDownloads(String itemsJson) {
        int total = 0;
        int success = 0;
        int failed = 0;
        try {
            JSONArray array = new JSONArray(itemsJson == null ? "[]" : itemsJson);
            total = array.length();
            publishProgress(STATE_STARTED, "Queued " + total + " downloads.", total, 0, 0, 0, "", "");
            File dir = new File(getCacheDir(), "downloads");
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IllegalStateException("Cannot create output directory: " + dir);
            }

            for (int i = 0; i < array.length(); i++) {
                if (cancelled) {
                    publishProgress(STATE_CANCELLED, "Downloads cancelled. Success " + success + ", failed " + failed + ".", total, i, success, failed, "", "");
                    return;
                }
                CameraContentItem item = CameraContentItem.fromJson(array.getJSONObject(i));
                String prefix = String.format(Locale.US, "%d/%d ", i + 1, array.length());
                publishProgress(STATE_FILE_STARTED, prefix + "Downloading " + item.title, total, i + 1, success, failed, item.title, "");
                updateNotification(prefix + item.title);
                try {
                    DownloadValidator.ValidationResult result = downloadOne(item, dir);
                    success++;
                    publishProgress(STATE_FILE_DONE, prefix + item.title + " -> " + result.toDisplayString(), total, i + 1, success, failed, item.title, "");
                } catch (Exception ex) {
                    failed++;
                    publishProgress(STATE_FILE_FAILED, prefix + item.title + " failed: " + ex.getMessage(), total, i + 1, success, failed, item.title, item.toJson().toString());
                }
            }
            publishProgress(STATE_DONE, "Downloads complete. Success " + success + ", failed " + failed + ". Output: DCIM/Sony Picture", total, total, success, failed, "", "");
        } catch (Exception ex) {
            publishProgress(STATE_FATAL, "Download failed: " + ex.getMessage(), total, 0, success, failed, "", "");
        } finally {
            stopForeground(STOP_FOREGROUND_DETACH);
            stopSelf();
        }
    }

    private DownloadValidator.ValidationResult downloadOne(CameraContentItem item, File dir) throws Exception {
        String urlText = item.bestDownloadUrl();
        if (urlText == null || urlText.isEmpty()) {
            throw new IllegalArgumentException("No download URL for " + item.title);
        }

        String resolvedUrl = resolveBestDownloadUrl(urlText);
        if (!resolvedUrl.equals(urlText)) {
            publish("Using full-size candidate: " + filenameFromUrl(resolvedUrl));
        }

        URL url = new URL(resolvedUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("Accept", "*/*");
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("HTTP " + code + " for " + urlText);
        }

        String filename = safeFilename(item.title);
        if (!filename.contains(".")) {
            filename = filename + extensionFromContentType(connection.getContentType());
        }
        File output = uniqueFile(dir, filename);
        try (InputStream input = connection.getInputStream(); FileOutputStream fileOutput = new FileOutputStream(output)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (cancelled) {
                    throw new InterruptedException("Cancelled");
                }
                fileOutput.write(buffer, 0, read);
            }
        }
        DownloadValidator.ValidationResult result = DownloadValidator.inspect(output, connection.getContentType());
        saveToPublicDcim(output, filename, connection.getContentType());
        if (!output.delete()) {
            output.deleteOnExit();
        }
        return result;
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
            connection.setRequestProperty("Accept", "*/*");
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
                        // Keep content length fallback.
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

    private String filenameFromUrl(String urlText) {
        try {
            String path = new URL(urlText).getPath();
            int slash = path.lastIndexOf('/');
            return slash >= 0 ? path.substring(slash + 1) : path;
        } catch (Exception ignored) {
            return urlText;
        }
    }

    private void saveToPublicDcim(File source, String filename, String contentType) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
            values.put(MediaStore.MediaColumns.MIME_TYPE, contentType == null ? mimeFromFilename(filename) : contentType);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, PUBLIC_OUTPUT_DIR);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);

            Uri uri = getContentResolver().insert(mediaStoreUriFor(filename, contentType), values);
            if (uri == null) {
                throw new IllegalStateException("Cannot create MediaStore entry for " + filename);
            }
            try (InputStream input = new java.io.FileInputStream(source);
                 OutputStream output = getContentResolver().openOutputStream(uri)) {
                if (output == null) {
                    throw new IllegalStateException("Cannot open MediaStore output for " + filename);
                }
                copy(input, output);
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            getContentResolver().update(uri, done, null, null);
            return;
        }

        File publicDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Sony Picture");
        if (!publicDir.exists() && !publicDir.mkdirs()) {
            throw new IllegalStateException("Cannot create " + publicDir);
        }
        File outputFile = uniqueFile(publicDir, filename);
        try (InputStream input = new java.io.FileInputStream(source);
             OutputStream output = new FileOutputStream(outputFile)) {
            copy(input, output);
        }
    }

    private void copy(InputStream input, OutputStream output) throws Exception {
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
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
        if (lower.contains("tiff")) {
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

    private void publish(String message) {
        publishProgress("", message, 0, 0, 0, 0, "", "");
    }

    private void publishProgress(String state, String message, int total, int index, int success, int failed, String filename, String itemJson) {
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
        sendBroadcast(intent);
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
                .setSmallIcon(android.R.drawable.stat_sys_download)
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
