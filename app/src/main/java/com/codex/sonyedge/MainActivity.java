package com.codex.sonyedge;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.method.ScrollingMovementMethod;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.chip.Chip;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.shape.CornerFamily;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String PREFS = "sonyedge";
    private static final String PREF_DMS_CONTROL_URL = "dms_control_url";
    private static final String PREF_LAST_FOLDER_ID = "last_folder_id";
    private static final String PREF_LAST_FOLDER_TITLE = "last_folder_title";
    private static final int PAGE_LIBRARY = 100;
    private static final int PAGE_ALBUMS = 101;
    private static final int PAGE_TRANSFERS = 102;
    private static final int PAGE_TOOLS = 103;

    private int COLOR_PRIMARY = 0xFF6750A4;
    private int COLOR_ON_PRIMARY = 0xFFFFFFFF;
    private int COLOR_PRIMARY_CONTAINER = 0xFFEADDFF;
    private int COLOR_ON_PRIMARY_CONTAINER = 0xFF21005D;
    private int COLOR_SECONDARY_CONTAINER = 0xFFE8DEF8;
    private int COLOR_SURFACE = 0xFFFFFBFE;
    private int COLOR_SURFACE_CONTAINER = 0xFFF3EDF7;
    private int COLOR_ON_SURFACE = 0xFF1D1B20;
    private int COLOR_ON_SURFACE_VARIANT = 0xFF49454F;
    private int COLOR_OUTLINE_VARIANT = 0xFFE7E0EC;
    private int COLOR_MUTED = 0xFF79747E;
    private int COLOR_RIPPLE_PRIMARY = 0x226750A4;
    private int COLOR_RIPPLE_ON_PRIMARY = 0x33FFFFFF;
    private int COLOR_PREVIEW_BACKGROUND = 0xFF101816;
    private static final String DIAGNOSTICS_PLACEHOLDER = "Diagnostics will appear here after Connect or Browse.";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService browseExecutor = Executors.newFixedThreadPool(2);
    private final ExecutorService imageExecutor = Executors.newFixedThreadPool(3);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> thumbnailImageCache = newBitmapCache(cacheBytes(3, 32, 128));
    private final LruCache<String, Bitmap> previewImageCache = newBitmapCache(cacheBytes(8, 16, 48));
    private final Map<String, List<ImageView>> pendingImageTargets = new HashMap<>();
    private final List<CameraContentItem> contentItems = new ArrayList<>();
    private final List<DmsContainerItem> albumContainers = new ArrayList<>();
    private final Set<String> selectedItemKeys = new HashSet<>();
    private final List<DmsServiceInfo> lastDmsServices = new ArrayList<>();
    private final List<FolderState> dmsFolderStack = new ArrayList<>();
    private DmsServiceInfo activeDmsService;
    private String currentDmsObjectId = "0";
    private String currentFolderTitle = "Camera";
    private String preferredFolderId = "0";
    private String preferredFolderTitle = "Camera";

    private EditText baseUrlInput;
    private TextView statusText;
    private TextView albumsStatusText;
    private TextView selectionText;
    private TextView downloadStatusText;
    private TextView transferHistoryEmpty;
    private LinearProgressIndicator downloadProgress;
    private TextView logText;
    private LinearLayout folderList;
    private RecyclerView albumRecycler;
    private AlbumAdapter albumAdapter;
    private RecyclerView photoRecycler;
    private PhotoAdapter photoAdapter;
    private ScrollView logScroll;
    private ScrollView transferHistoryScroll;
    private MaterialCardView logCard;
    private LinearLayout transferHistoryList;
    private Button downloadButton;
    private Button cancelDownloadButton;
    private Button openGalleryButton;
    private Button retryFailedButton;
    private Button diagnosticsButton;
    private Button libraryBackButton;
    private Button albumBackButton;
    private BottomNavigationView bottomNavigation;
    private LinearLayout selectionActionBar;
    private View libraryPage;
    private View downloadsPage;
    private View diagnosticsPage;
    private View albumsPage;
    private boolean diagnosticsVisible;
    private boolean downloadRunning;
    private boolean albumLoading;
    private boolean photoLoading;
    private String photoMessage = "Connect to the camera Wi-Fi, then browse.";
    private String albumMessage = "Camera folders appear here after browsing.";
    private int browseGeneration;
    private int currentPage = PAGE_LIBRARY;
    private int gridColumns = 2;
    private int photoCardWidthPx;
    private int transferHistoryCount;
    private JSONArray failedDownloadItems = new JSONArray();

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String message = intent.getStringExtra(DownloadService.EXTRA_MESSAGE);
            appendLog(message == null ? "download event" : message);
            updateDownloadProgress(intent);
        }
    };

    private void initThemeColors() {
        COLOR_PRIMARY = 0xFF0057B8;
        COLOR_ON_PRIMARY = 0xFFFFFFFF;
        COLOR_PRIMARY_CONTAINER = 0xFFE6F0FF;
        COLOR_ON_PRIMARY_CONTAINER = 0xFF073763;
        COLOR_SECONDARY_CONTAINER = 0xFFEAF1F8;
        COLOR_SURFACE = 0xFFFBFCFE;
        COLOR_SURFACE_CONTAINER = 0xFFF2F5F9;
        COLOR_ON_SURFACE = 0xFF1B1D21;
        COLOR_ON_SURFACE_VARIANT = 0xFF535B66;
        COLOR_OUTLINE_VARIANT = 0xFFD7DEE8;
        COLOR_MUTED = COLOR_ON_SURFACE_VARIANT;
        COLOR_RIPPLE_PRIMARY = (COLOR_PRIMARY & 0x00FFFFFF) | 0x22000000;
        COLOR_RIPPLE_ON_PRIMARY = (COLOR_ON_PRIMARY & 0x00FFFFFF) | 0x33000000;
    }

    private int cacheBytes(int divisor, int minMb, int maxMb) {
        long maxMemory = Runtime.getRuntime().maxMemory();
        long target = maxMemory / Math.max(divisor, 1);
        long min = minMb * 1024L * 1024L;
        long max = maxMb * 1024L * 1024L;
        return (int) Math.max(min, Math.min(target, max));
    }

    private LruCache<String, Bitmap> newBitmapCache(int maxBytes) {
        return new LruCache<String, Bitmap>(maxBytes) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value == null ? 0 : value.getByteCount();
            }
        };
    }

    private int themeColor(int attr, int fallback) {
        TypedValue value = new TypedValue();
        if (!getTheme().resolveAttribute(attr, value, true)) {
            return fallback;
        }
        if (value.resourceId != 0) {
            return getColor(value.resourceId);
        }
        return value.data;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        initThemeColors();
        configureSystemBars();
        setContentView(buildUi());
        loadCachedDmsEndpoint();
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        }
        if (!permissions.isEmpty()) {
            requestPermissions(permissions.toArray(new String[0]), 10);
        }
    }

    private void configureSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(COLOR_SURFACE);
        window.setNavigationBarColor(COLOR_SURFACE);
        if (Build.VERSION.SDK_INT >= 23) {
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(DownloadService.ACTION_PROGRESS);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(downloadReceiver, filter);
        }
    }

    @Override
    protected void onStop() {
        unregisterReceiver(downloadReceiver);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        browseExecutor.shutdownNow();
        imageExecutor.shutdownNow();
        super.onDestroy();
    }

    private View buildUi() {
        int pad = dp(10);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(COLOR_SURFACE);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(0, 0, 0, dp(8));
        root.addView(topBar, matchWrap());

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        topBar.addView(titleBlock, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView title = textView("SonyEdge", 16, COLOR_ON_SURFACE, true);
        titleBlock.addView(title, matchWrap());

        TextView subtitle = textView("A7R III import", 10, COLOR_PRIMARY, false);
        titleBlock.addView(subtitle, matchWrap());

        topBar.addView(infoChip("Camera Wi-Fi", COLOR_PRIMARY_CONTAINER, COLOR_ON_PRIMARY_CONTAINER),
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        baseUrlInput = new EditText(this);
        baseUrlInput.setSingleLine(true);
        baseUrlInput.setText("http://192.168.122.1:8080");
        baseUrlInput.setHint("Camera base URL");
        baseUrlInput.setTextSize(14);
        baseUrlInput.setVisibility(View.GONE);

        FrameLayout pageHost = new FrameLayout(this);
        root.addView(pageHost, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        LinearLayout library = new LinearLayout(this);
        library.setOrientation(LinearLayout.VERTICAL);
        pageHost.addView(library, pageParams());
        libraryPage = library;

        LinearLayout libraryHeader = new LinearLayout(this);
        libraryHeader.setOrientation(LinearLayout.VERTICAL);
        libraryHeader.setPadding(dp(2), dp(2), dp(2), dp(8));
        library.addView(libraryHeader, matchWrap());
        LinearLayout libraryStatus = horizontalRow();
        libraryHeader.addView(libraryStatus, matchWrap());
        LinearLayout libraryCopy = new LinearLayout(this);
        libraryCopy.setOrientation(LinearLayout.VERTICAL);
        libraryStatus.addView(libraryCopy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        libraryCopy.addView(sectionLabel("Import"), matchWrap());

        statusText = textView("Connect to the camera Wi-Fi, then browse.", 14, COLOR_ON_SURFACE_VARIANT, false);
        statusText.setPadding(0, dp(3), dp(8), 0);
        libraryCopy.addView(statusText, matchWrap());

        libraryBackButton = smallNavButton("Back", R.drawable.ic_arrow_back);
        libraryBackButton.setOnClickListener(v -> openParentFolder());
        libraryStatus.addView(libraryBackButton, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        Button quickBrowse = smallNavButton("Browse", R.drawable.ic_folder);
        quickBrowse.setOnClickListener(v -> listPhotos(baseUrlInput.getText().toString()));
        libraryStatus.addView(quickBrowse, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        selectionText = textView("0 selected / 0 photos", 13, COLOR_ON_SURFACE_VARIANT, false);
        selectionText.setPadding(0, dp(4), 0, 0);
        libraryHeader.addView(selectionText, matchWrap());

        photoAdapter = new PhotoAdapter();
        photoAdapter.setHasStableIds(true);
        photoRecycler = new RecyclerView(this);
        photoRecycler.setLayoutManager(photoGridLayoutManager());
        photoRecycler.setAdapter(photoAdapter);
        photoRecycler.setClipToPadding(false);
        photoRecycler.setPadding(0, 0, 0, dp(8));
        library.addView(photoRecycler, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        LinearLayout albums = new LinearLayout(this);
        albums.setOrientation(LinearLayout.VERTICAL);
        albums.setVisibility(View.GONE);
        pageHost.addView(albums, pageParams());
        albumsPage = albums;

        LinearLayout albumsHeader = new LinearLayout(this);
        albumsHeader.setOrientation(LinearLayout.VERTICAL);
        albumsHeader.setPadding(dp(2), dp(2), dp(2), dp(8));
        albums.addView(albumsHeader, matchWrap());
        albumsHeader.addView(sectionLabel("Albums"), matchWrap());
        albumsStatusText = textView("Camera folders appear here after browsing.", 13, COLOR_ON_SURFACE_VARIANT, false);
        albumsStatusText.setPadding(0, dp(4), 0, 0);
        albumsHeader.addView(albumsStatusText, matchWrap());

        LinearLayout albumActions = horizontalRow();
        albumActions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        albumActions.setPadding(0, dp(8), 0, 0);
        albumsHeader.addView(albumActions, matchWrap());

        albumBackButton = smallNavButton("Back", R.drawable.ic_arrow_back);
        albumBackButton.setOnClickListener(v -> openParentFolder());
        albumActions.addView(albumBackButton, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        Button albumRoot = smallNavButton("Root", R.drawable.ic_folder);
        albumRoot.setOnClickListener(v -> openRootFolder());
        albumActions.addView(albumRoot, spacedWrap(6, 0, 0, 0));

        Button albumRefresh = smallNavButton("Refresh", R.drawable.ic_refresh);
        albumRefresh.setOnClickListener(v -> refreshCurrentFolder());
        albumActions.addView(albumRefresh, spacedWrap(6, 0, 0, 0));

        albumAdapter = new AlbumAdapter();
        albumRecycler = new RecyclerView(this);
        GridLayoutManager albumLayout = new GridLayoutManager(this, albumColumnCount());
        albumLayout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return albumAdapter.isFullWidth(position) ? albumLayout.getSpanCount() : 1;
            }
        });
        albumRecycler.setLayoutManager(albumLayout);
        albumRecycler.setAdapter(albumAdapter);
        albumRecycler.setClipToPadding(false);
        albumRecycler.setPadding(0, 0, 0, dp(8));
        albums.addView(albumRecycler, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
        folderList = new LinearLayout(this);
        folderList.setOrientation(LinearLayout.VERTICAL);

        LinearLayout downloads = new LinearLayout(this);
        downloads.setOrientation(LinearLayout.VERTICAL);
        downloads.setVisibility(View.GONE);
        pageHost.addView(downloads, pageParams());
        downloadsPage = downloads;

        LinearLayout progressPanel = addSurfaceCard(downloads, dp(8));
        progressPanel.addView(sectionLabel("Transfers"), matchWrap());

        downloadStatusText = textView("Ready to download originals to DCIM/Sony Picture", 14, COLOR_ON_SURFACE_VARIANT, false);
        downloadStatusText.setPadding(0, dp(4), 0, 0);
        progressPanel.addView(downloadStatusText, matchWrap());
        downloadProgress = new LinearProgressIndicator(this);
        downloadProgress.setMax(100);
        downloadProgress.setProgress(0);
        downloadProgress.setIndeterminate(false);
        downloadProgress.setTrackThickness(dp(6));
        downloadProgress.setIndicatorColor(COLOR_PRIMARY);
        downloadProgress.setTrackColor(COLOR_OUTLINE_VARIANT);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(8)
        );
        progressParams.setMargins(0, dp(10), 0, 0);
        progressPanel.addView(downloadProgress, progressParams);

        LinearLayout downloadActions = horizontalRow();
        downloadActions.setPadding(0, dp(12), 0, 0);
        progressPanel.addView(downloadActions, matchWrap());

        cancelDownloadButton = new MaterialButton(this);
        cancelDownloadButton.setText("Cancel");
        styleButton(cancelDownloadButton, false);
        setButtonIcon(cancelDownloadButton, R.drawable.ic_close, false);
        cancelDownloadButton.setEnabled(false);
        cancelDownloadButton.setOnClickListener(v -> cancelDownloads());
        downloadActions.addView(cancelDownloadButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        openGalleryButton = new MaterialButton(this);
        openGalleryButton.setText("Gallery");
        styleButton(openGalleryButton, false);
        setButtonIcon(openGalleryButton, R.drawable.ic_image, false);
        openGalleryButton.setEnabled(false);
        openGalleryButton.setOnClickListener(v -> openGallery());
        downloadActions.addView(openGalleryButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        retryFailedButton = new MaterialButton(this);
        retryFailedButton.setText("Retry");
        styleButton(retryFailedButton, false);
        setButtonIcon(retryFailedButton, R.drawable.ic_refresh, false);
        retryFailedButton.setEnabled(false);
        retryFailedButton.setOnClickListener(v -> retryFailedDownloads());
        downloadActions.addView(retryFailedButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        MaterialCardView transferCard = new MaterialCardView(this);
        transferCard.setRadius(dp(8));
        transferCard.setStrokeWidth(dp(1));
        transferCard.setStrokeColor(COLOR_OUTLINE_VARIANT);
        transferCard.setCardBackgroundColor(COLOR_SURFACE);
        transferCard.setCardElevation(0);
        LinearLayout transferContent = new LinearLayout(this);
        transferContent.setOrientation(LinearLayout.VERTICAL);
        transferContent.setPadding(dp(14), dp(12), dp(14), dp(12));
        transferCard.addView(transferContent, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        transferContent.addView(sectionLabel("Recent Activity"), matchWrap());
        transferHistoryEmpty = textView("No transfer activity yet.", 13, COLOR_ON_SURFACE_VARIANT, false);
        transferHistoryEmpty.setPadding(0, dp(8), 0, 0);
        transferContent.addView(transferHistoryEmpty, matchWrap());
        transferHistoryScroll = new ScrollView(this);
        transferHistoryScroll.setFillViewport(false);
        transferHistoryScroll.setPadding(0, dp(8), 0, 0);
        transferHistoryList = new LinearLayout(this);
        transferHistoryList.setOrientation(LinearLayout.VERTICAL);
        transferHistoryScroll.addView(transferHistoryList, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        transferContent.addView(transferHistoryScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
        downloads.addView(transferCard, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.VERTICAL);
        tools.setVisibility(View.GONE);
        pageHost.addView(tools, pageParams());
        diagnosticsPage = tools;

        LinearLayout toolsCard = addSurfaceCard(tools, dp(8));
        toolsCard.addView(sectionLabel("Camera"), matchWrap());
        LinearLayout toolRow = horizontalRow();
        toolRow.setPadding(0, dp(8), 0, 0);
        toolsCard.addView(toolRow, matchWrap());

        Button autoDiscover = new MaterialButton(this);
        autoDiscover.setText("Connect");
        styleButton(autoDiscover, true);
        setButtonIcon(autoDiscover, R.drawable.ic_wifi, true);
        autoDiscover.setOnClickListener(v -> probeCandidateHosts());
        toolRow.addView(autoDiscover, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button listContent = new MaterialButton(this);
        listContent.setText("Browse");
        styleButton(listContent, false);
        setButtonIcon(listContent, R.drawable.ic_folder, false);
        listContent.setOnClickListener(v -> listPhotos(baseUrlInput.getText().toString()));
        toolRow.addView(listContent, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        LinearLayout toolRow2 = horizontalRow();
        toolRow2.setPadding(0, dp(8), 0, 0);
        toolsCard.addView(toolRow2, matchWrap());

        Button rootFolder = new MaterialButton(this);
        rootFolder.setText("Root");
        styleButton(rootFolder, false);
        setButtonIcon(rootFolder, R.drawable.ic_folder, false);
        rootFolder.setOnClickListener(v -> openRootFolder());
        toolRow2.addView(rootFolder, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button refreshFolder = new MaterialButton(this);
        refreshFolder.setText("Refresh");
        styleButton(refreshFolder, false);
        setButtonIcon(refreshFolder, R.drawable.ic_refresh, false);
        refreshFolder.setOnClickListener(v -> refreshCurrentFolder());
        toolRow2.addView(refreshFolder, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        diagnosticsButton = new MaterialButton(this);
        diagnosticsButton.setText("Clear log");
        styleButton(diagnosticsButton, false);
        setButtonIcon(diagnosticsButton, R.drawable.ic_clear, false);
        diagnosticsButton.setOnClickListener(v -> clearDiagnostics());
        tools.addView(diagnosticsButton, spacedMatchWrap(0, 0, 0, dp(8)));

        logCard = new MaterialCardView(this);
        logCard.setRadius(dp(8));
        logCard.setStrokeWidth(dp(1));
        logCard.setStrokeColor(COLOR_OUTLINE_VARIANT);
        logCard.setCardBackgroundColor(COLOR_SURFACE);

        logText = new TextView(this);
        logText.setText(DIAGNOSTICS_PLACEHOLDER);
        logText.setTextColor(COLOR_ON_SURFACE_VARIANT);
        logText.setTextSize(12);
        logText.setPadding(dp(12), dp(10), dp(12), dp(10));
        logText.setMovementMethod(new ScrollingMovementMethod());
        logScroll = new ScrollView(this);
        logScroll.addView(logText);
        logCard.addView(logScroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        tools.addView(logCard, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        selectionActionBar = new LinearLayout(this);
        selectionActionBar.setOrientation(LinearLayout.HORIZONTAL);
        selectionActionBar.setGravity(Gravity.CENTER_VERTICAL);
        selectionActionBar.setPadding(dp(8), dp(8), dp(8), dp(8));
        selectionActionBar.setVisibility(View.GONE);
        selectionActionBar.setBackground(cardBackground(COLOR_SURFACE, COLOR_OUTLINE_VARIANT, dp(8)));
        root.addView(selectionActionBar, spacedMatchWrap(0, dp(8), 0, dp(8)));

        downloadButton = new MaterialButton(this);
        downloadButton.setText("Download");
        styleButton(downloadButton, true);
        setButtonIcon(downloadButton, R.drawable.ic_download, true);
        downloadButton.setOnClickListener(v -> startSelectedDownloads());
        selectionActionBar.addView(downloadButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f));

        Button selectVisible = new MaterialButton(this);
        selectVisible.setText("All");
        styleButton(selectVisible, false);
        setButtonIcon(selectVisible, R.drawable.ic_select_all, false);
        selectVisible.setOnClickListener(v -> setVisibleSelection(true));
        selectionActionBar.addView(selectVisible, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button clearVisible = new MaterialButton(this);
        clearVisible.setText("Clear");
        styleButton(clearVisible, false);
        setButtonIcon(clearVisible, R.drawable.ic_clear, false);
        clearVisible.setOnClickListener(v -> setVisibleSelection(false));
        selectionActionBar.addView(clearVisible, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button invertVisible = new MaterialButton(this);
        invertVisible.setText("Invert");
        styleButton(invertVisible, false);
        setButtonIcon(invertVisible, R.drawable.ic_swap, false);
        invertVisible.setOnClickListener(v -> invertVisibleSelection());
        selectionActionBar.addView(invertVisible, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        bottomNavigation = new BottomNavigationView(this);
        Menu menu = bottomNavigation.getMenu();
        menu.add(Menu.NONE, PAGE_LIBRARY, 0, "Photos").setIcon(R.drawable.ic_image);
        menu.add(Menu.NONE, PAGE_ALBUMS, 1, "Albums").setIcon(R.drawable.ic_folder);
        menu.add(Menu.NONE, PAGE_TRANSFERS, 2, "Transfers").setIcon(R.drawable.ic_download);
        menu.add(Menu.NONE, PAGE_TOOLS, 3, "Settings").setIcon(R.drawable.ic_info);
        bottomNavigation.setLabelVisibilityMode(com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_LABELED);
        bottomNavigation.setBackgroundColor(COLOR_SURFACE);
        bottomNavigation.setItemActiveIndicatorColor(ColorStateList.valueOf(COLOR_PRIMARY_CONTAINER));
        bottomNavigation.setItemRippleColor(ColorStateList.valueOf(COLOR_RIPPLE_PRIMARY));
        bottomNavigation.setItemIconTintList(navigationColorStateList());
        bottomNavigation.setItemTextColor(navigationColorStateList());
        bottomNavigation.setOnItemSelectedListener(item -> {
            showPage(item.getItemId());
            return true;
        });
        root.addView(bottomNavigation, matchWrap());
        navigateToPage(PAGE_LIBRARY);
        updateBackButtons();

        return root;
    }

    private void probeCandidateHosts() {
        setBusy("Probing candidate camera endpoints...");
        executor.execute(() -> {
            bindProcessToWifiIfPresent();
            Set<String> candidates = new LinkedHashSet<>();
            StringBuilder log = new StringBuilder();
            log.append(networkDiagnostics());
            DiscoveryClient discoveryClient = new DiscoveryClient(this);
            String gateway = wifiGateway();
            Set<String> possibleHosts = new LinkedHashSet<>();
            possibleHosts.add(gateway);
            possibleHosts.add("192.168.122.1");

            DmsServiceInfo fastDms = discoveryClient.discoverFirstDmsService(possibleHosts, log);
            if (fastDms != null) {
                activeDmsService = fastDms;
                saveDmsEndpoint(fastDms);
                runOnUiThread(() -> {
                    lastDmsServices.clear();
                    lastDmsServices.add(fastDms);
                    statusText.setText("Found DMS endpoint: " + fastDms.controlUrl);
                    appendLog("Fast DMS endpoint: " + fastDms.controlUrl + "\n" + log);
                });
                return;
            }

            candidates.addAll(discoveryClient.discoverBaseUrls(log));
            possibleHosts.add("10.0.0.1");
            possibleHosts.add("192.168.0.1");
            possibleHosts.add("192.168.1.1");
            candidates.addAll(discoveryClient.discoverKnownDescriptionUrls(possibleHosts, log));
            List<DmsServiceInfo> dmsServices = discoveryClient.discoverDmsServices(possibleHosts, log);
            candidates.add(baseUrlInput.getText().toString());
            candidates.add("http://192.168.122.1:10000");
            candidates.add("http://192.168.122.1:8080");
            candidates.add("http://192.168.122.1");
            if (gateway != null && !gateway.isEmpty()) {
                candidates.add("http://" + gateway + ":10000");
                candidates.add("http://" + gateway + ":8080");
                candidates.add("http://" + gateway);
            }
            candidates = NetworkProbe.expandCandidates(candidates, gateway, log);

            log.append("Candidate base URLs: ").append(candidates).append("\n");
            if (!dmsServices.isEmpty()) {
                activeDmsService = dmsServices.get(0);
                saveDmsEndpoint(dmsServices.get(0));
                String ok = "Found DMS endpoint: " + dmsServices.get(0).controlUrl;
                runOnUiThread(() -> {
                    lastDmsServices.clear();
                    lastDmsServices.addAll(dmsServices);
                    statusText.setText(ok);
                    appendLog(ok + "\n" + log);
                });
                return;
            }
            for (String candidate : candidates) {
                try {
                    SonyRpcClient client = new SonyRpcClient(candidate);
                    StringBuilder serviceLog = new StringBuilder();
                    List<String> cameraApis = client.safeGetAvailableApiList("camera", serviceLog);
                    List<String> avContentApis = client.safeGetAvailableApiList("avContent", serviceLog);
                    if (cameraApis.isEmpty() && avContentApis.isEmpty()) {
                        throw new IllegalStateException(serviceLog.toString().trim());
                    }
                    String ok = "Found Sony endpoint: " + client.getBaseUrl()
                            + " camera=" + cameraApis
                            + " avContent=" + avContentApis;
                    runOnUiThread(() -> {
                        baseUrlInput.setText(client.getBaseUrl());
                        statusText.setText(ok);
                        appendLog(ok);
                    });
                    return;
                } catch (Exception ex) {
                    log.append(candidate).append(" failed: ").append(ex.getMessage()).append('\n');
                }
            }
            runOnUiThread(() -> {
                statusText.setText("No compatible endpoint found.");
                appendLog(log.toString());
            });
        });
    }

    private void listPhotos(String baseUrl) {
        setBusy("Listing camera content...");
        if (!lastDmsServices.isEmpty()) {
            activeDmsService = lastDmsServices.get(0);
            openRootFolder();
            return;
        }
        DmsServiceInfo cached = activeDmsService;
        if (cached != null) {
            lastDmsServices.add(cached);
            openRootFolder();
            return;
        }
        postLog("No cached endpoint, fast connecting...");
        quickConnectAndOpenRoot();
    }

    private void quickConnectAndOpenRoot() {
        executor.execute(() -> {
            bindProcessToWifiIfPresent();
            StringBuilder log = new StringBuilder();
            try {
                Set<String> hosts = new LinkedHashSet<>();
                hosts.add(wifiGateway());
                hosts.add("192.168.122.1");
                DmsServiceInfo service = new DiscoveryClient(this).discoverFirstDmsService(hosts, log);
                if (service == null) {
                    throw new IllegalStateException("No DMS endpoint found");
                }
                runOnUiThread(() -> {
                    activeDmsService = service;
                    lastDmsServices.clear();
                    lastDmsServices.add(service);
                    saveDmsEndpoint(service);
                    appendLog("Fast connected: " + service.controlUrl + "\n" + log);
                    openRootFolder();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    statusText.setText("Fast connect failed: " + ex.getMessage());
                    appendLog("Fast connect failed: " + ex + "\n" + log);
                });
            }
        });
    }

    private List<CameraContentItem> discoverDmsItems(StringBuilder log, boolean cachedOnly) throws Exception {
        String gateway = wifiGateway();
        Set<String> possibleHosts = new LinkedHashSet<>();
        possibleHosts.add(gateway);
        possibleHosts.add("192.168.122.1");
        possibleHosts.add("10.0.0.1");
        possibleHosts.add("192.168.0.1");
        possibleHosts.add("192.168.1.1");
        List<DmsServiceInfo> services = new ArrayList<>();
        if (!lastDmsServices.isEmpty()) {
            services.addAll(new ArrayList<>(lastDmsServices));
        }
        if (!cachedOnly) {
            postLog("Discovering DMS services...");
            for (DmsServiceInfo service : new DiscoveryClient(this).discoverDmsServices(possibleHosts, log)) {
                boolean exists = false;
                for (DmsServiceInfo known : services) {
                    if (known.controlUrl.equals(service.controlUrl)) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    services.add(service);
                }
            }
        }
        log.append("DMS services: ").append(services).append('\n');
        for (DmsServiceInfo service : services) {
            try {
                postLog("Browsing DMS: " + service.controlUrl);
                List<CameraContentItem> items = new DmsContentClient(service, log, this::postLog).discoverImages();
                if (!items.isEmpty()) {
                    return items;
                }
            } catch (Exception ex) {
                log.append("DMS browse failed ").append(service.controlUrl).append(": ").append(ex.getMessage()).append('\n');
            }
        }
        return new ArrayList<>();
    }

    private void renderItems() {
        selectedItemKeys.clear();
        if (photoAdapter != null) {
            photoAdapter.notifyDataSetChanged();
        }
        updateSelectionSummary();
    }

    private void openDmsFolder(String objectId, String title, boolean resetStack) {
        openDmsFolder(objectId, title, resetStack, false);
    }

    private void openDmsFolder(String objectId, String title, boolean resetStack, boolean fallbackToRootOnFailure) {
        if (activeDmsService == null && !lastDmsServices.isEmpty()) {
            activeDmsService = lastDmsServices.get(0);
        }
        if (activeDmsService == null) {
            appendLog("No DMS endpoint. Tap Probe Wi-Fi first.");
            return;
        }
        if (resetStack) {
            dmsFolderStack.clear();
        } else if (currentDmsObjectId != null && !currentDmsObjectId.equals(objectId)) {
            dmsFolderStack.add(new FolderState(currentDmsObjectId, currentFolderTitle));
        }
        currentDmsObjectId = objectId;
        currentFolderTitle = cleanFolderTitle(title, objectId);
        setBusy("Opening " + currentFolderTitle + "...");
        if (albumsStatusText != null) {
            albumsStatusText.setText("Opening " + currentFolderTitle + "...");
        }
        int requestId = ++browseGeneration;
        beginIncrementalFolderRender(objectId, currentFolderTitle, resetStack);
        scheduleSlowBrowseHint(requestId, currentFolderTitle);
        browseExecutor.execute(() -> {
            bindProcessToWifiIfPresent();
            StringBuilder log = new StringBuilder();
            try {
                DmsBrowseResult result = new DmsContentClient(activeDmsService, log, this::postLog)
                        .browseDirectChildren(objectId, (pageResult, loaded, total) -> runOnUiThread(() ->
                                appendDmsPage(requestId, pageResult, loaded, total)));
                runOnUiThread(() -> {
                    if (requestId != browseGeneration) {
                        appendLog("Ignoring stale folder result for " + objectId);
                        return;
                    }
                    finishIncrementalFolderRender(result);
                    statusText.setText("Loaded " + result.containers.size() + " folders and " + result.items.size() + " photos.");
                    if (albumsStatusText != null) {
                        albumsStatusText.setText(folderPathText() + "  -  "
                                + result.containers.size() + " folders, " + result.items.size() + " photos");
                    }
                    saveLastFolder(currentDmsObjectId, currentFolderTitle);
                    appendLog(log.toString());
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    if (requestId != browseGeneration) {
                        appendLog("Ignoring stale folder error for " + objectId + ": " + ex.getMessage());
                        return;
                    }
                    if (fallbackToRootOnFailure && !"0".equals(objectId)) {
                        appendLog("Last folder unavailable, returning to camera root: " + ex.getMessage());
                        saveLastFolder("0", "Camera");
                        openDmsFolder("0", "Camera", true, false);
                        return;
                    }
                    String error = "Open folder failed: " + ex.getMessage();
                    statusText.setText(error + ". Try Refresh or Back.");
                    if (albumsStatusText != null) {
                        albumsStatusText.setText(error);
                    }
                    Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
                    appendLog("Open folder failed: " + ex + "\n" + log);
                });
            }
        });
    }

    private void scheduleSlowBrowseHint(int requestId, String title) {
        mainHandler.postDelayed(() -> {
            if (requestId != browseGeneration) {
                return;
            }
            if (!photoLoading && !albumLoading) {
                return;
            }
            String message = "Still waiting for camera response...";
            if (photoLoading) {
                photoMessage = message;
                if (photoAdapter != null) {
                    photoAdapter.notifyDataSetChanged();
                }
            }
            if (albumLoading) {
                albumMessage = message;
                if (albumAdapter != null) {
                    albumAdapter.notifyDataSetChanged();
                }
            }
            if (statusText != null) {
                statusText.setText("Still opening " + title + ". Keep the phone on the camera Wi-Fi.");
            }
            if (albumsStatusText != null) {
                albumsStatusText.setText(folderPathText() + "  -  waiting for camera");
            }
        }, 8000);
    }

    private void beginIncrementalFolderRender(String objectId, String title, boolean resetStack) {
        if (resetStack && folderList != null) {
            folderList.removeAllViews();
        }
        contentItems.clear();
        selectedItemKeys.clear();
        photoLoading = true;
        photoMessage = "Opening " + title + "...";
        if (photoAdapter != null) {
            photoAdapter.notifyDataSetChanged();
        }
        if (photoRecycler != null) {
            photoRecycler.scrollToPosition(0);
        }
        albumContainers.clear();
        albumLoading = true;
        albumMessage = "Opening " + title + "...";
        if (albumAdapter != null) {
            albumAdapter.notifyDataSetChanged();
        }
        if (albumRecycler != null) {
            albumRecycler.scrollToPosition(0);
        }
        if (albumsStatusText != null) {
            albumsStatusText.setText(folderPathText() + "  -  opening");
        }
        String loadingText = "Opening " + title + "... photos will appear as they load.";
        if (statusText != null) {
            statusText.setText(loadingText);
        }
        if (selectionText != null) {
            selectionText.setText("Loading " + title + "...");
        }
        navigateToPage(isAlbumLevelFolder(objectId, title) ? PAGE_ALBUMS : PAGE_LIBRARY);
        updateSelectionSummary();
    }

    private void appendDmsPage(int requestId, DmsBrowseResult pageResult, int loaded, int total) {
        if (requestId != browseGeneration) {
            return;
        }
        if (pageResult == null) {
            return;
        }
        boolean hadNoPhotos = contentItems.isEmpty();
        if (!pageResult.items.isEmpty()) {
            photoLoading = total <= 0 || loaded < total;
            String totalText = total > 0 ? String.valueOf(total) : "?";
            photoMessage = "Loading more photos " + loaded + "/" + totalText + "...";
            contentItems.addAll(pageResult.items);
            if (photoAdapter != null) {
                photoAdapter.notifyDataSetChanged();
            }
            if (hadNoPhotos) {
                navigateToPage(PAGE_LIBRARY);
            }
        }
        if (!pageResult.containers.isEmpty()) {
            boolean wasLoadingOnly = albumLoading && albumContainers.isEmpty();
            albumLoading = false;
            int start = albumContainers.size();
            albumContainers.addAll(pageResult.containers);
            if (albumAdapter != null) {
                if (wasLoadingOnly) {
                    albumAdapter.notifyDataSetChanged();
                } else {
                    albumAdapter.notifyItemRangeInserted(start, pageResult.containers.size());
                }
            }
        } else if (loaded > 0 && albumLoading) {
            albumLoading = false;
            albumMessage = "No subfolders in " + currentFolderTitle + ".";
            if (albumAdapter != null) {
                albumAdapter.notifyDataSetChanged();
            }
        }
        String totalText = total > 0 ? String.valueOf(total) : "?";
        if (statusText != null) {
            statusText.setText("Loading " + currentFolderTitle + " " + loaded + "/" + totalText
                    + " (" + contentItems.size() + " photos)");
        }
        if (albumsStatusText != null) {
            albumsStatusText.setText(folderPathText() + "  -  loading " + loaded + "/" + totalText);
        }
        updateSelectionSummary();
    }

    private void finishIncrementalFolderRender(DmsBrowseResult result) {
        if (result == null) {
            return;
        }
        if (folderList != null) {
            folderList.removeAllViews();
            folderList.addView(folderHeader(result), matchWrap());
            for (DmsContainerItem container : result.containers) {
                folderList.addView(folderRow(container), matchWrap());
            }
            if (result.containers.isEmpty() && result.items.isEmpty()) {
                folderList.addView(emptyState("This folder is empty. Try Back or Refresh."), matchWrap());
            }
        }
        albumLoading = false;
        albumMessage = result.containers.isEmpty()
                ? "No subfolders. Photos are shown in Library."
                : "";
        albumContainers.clear();
        albumContainers.addAll(result.containers);
        if (albumAdapter != null) {
            albumAdapter.notifyDataSetChanged();
        }
        if (contentItems.size() != result.items.size()) {
            contentItems.clear();
            contentItems.addAll(result.items);
        }
        photoLoading = false;
        photoMessage = result.items.isEmpty()
                ? "No photos in this folder. Use Back or Root."
                : "";
        if (photoAdapter != null) {
            photoAdapter.notifyDataSetChanged();
        }
        navigateToPage(result.items.isEmpty() ? PAGE_ALBUMS : PAGE_LIBRARY);
        updateSelectionSummary();
    }

    private void loadCachedDmsEndpoint() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String controlUrl = prefs.getString(PREF_DMS_CONTROL_URL, "");
        preferredFolderId = prefs.getString(PREF_LAST_FOLDER_ID, "0");
        preferredFolderTitle = prefs.getString(PREF_LAST_FOLDER_TITLE, "Camera");
        if (controlUrl != null && !controlUrl.isEmpty()) {
            DmsServiceInfo cached = DmsServiceInfo.cached(controlUrl);
            activeDmsService = cached;
            lastDmsServices.clear();
            lastDmsServices.add(cached);
        }
    }

    private void saveDmsEndpoint(DmsServiceInfo serviceInfo) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(PREF_DMS_CONTROL_URL, serviceInfo.controlUrl)
                .apply();
    }

    private void saveLastFolder(String objectId, String title) {
        preferredFolderId = objectId == null || objectId.isEmpty() ? "0" : objectId;
        preferredFolderTitle = title == null || title.isEmpty() ? "Camera" : title;
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(PREF_LAST_FOLDER_ID, preferredFolderId)
                .putString(PREF_LAST_FOLDER_TITLE, preferredFolderTitle)
                .apply();
    }

    private void openPreferredFolder() {
        String folderId = preferredFolderId == null || preferredFolderId.isEmpty() ? "0" : preferredFolderId;
        String title = preferredFolderTitle == null || preferredFolderTitle.isEmpty() ? "Camera" : preferredFolderTitle;
        openDmsFolder(folderId, title, true, true);
    }

    private void renderDmsFolder(DmsBrowseResult result) {
        folderList.removeAllViews();
        contentItems.clear();
        selectedItemKeys.clear();
        photoLoading = false;
        photoMessage = "";

        folderList.addView(folderHeader(result), matchWrap());

        for (DmsContainerItem container : result.containers) {
            folderList.addView(folderRow(container), matchWrap());
        }
        albumLoading = false;
        albumMessage = result.containers.isEmpty()
                ? "No subfolders. Photos are shown in Library."
                : "";
        albumContainers.clear();
        albumContainers.addAll(result.containers);
        if (albumAdapter != null) {
            albumAdapter.notifyDataSetChanged();
        }

        contentItems.addAll(result.items);
        if (result.items.isEmpty()) {
            photoMessage = "No photos in this folder. Use Back or Root.";
        }
        if (photoAdapter != null) {
            photoRecycler.setLayoutManager(photoGridLayoutManager());
            photoAdapter.notifyDataSetChanged();
        }
        if (photoRecycler != null) {
            photoRecycler.scrollToPosition(0);
        }
        if (albumRecycler != null) {
            albumRecycler.scrollToPosition(0);
        }
        if (result.containers.isEmpty() && result.items.isEmpty()) {
            folderList.addView(emptyState("This folder is empty. Try Back or Refresh."), matchWrap());
        }
        navigateToPage(result.items.isEmpty() ? PAGE_ALBUMS : PAGE_LIBRARY);
        updateSelectionSummary();
    }

    private View folderHeader(DmsBrowseResult result) {
        LinearLayout stack = new LinearLayout(this);
        stack.setOrientation(LinearLayout.VERTICAL);
        stack.setPadding(dp(2), dp(6), dp(2), dp(10));

        TextView title = textView(currentFolderTitle, 18, COLOR_ON_SURFACE, true);
        title.setSingleLine(false);
        stack.addView(title, matchWrap());

        TextView path = textView(folderPathText(), 12, COLOR_ON_SURFACE_VARIANT, false);
        path.setSingleLine(false);
        path.setPadding(0, dp(2), 0, dp(4));
        stack.addView(path, matchWrap());

        String count = result.containers.size() + " folders  /  " + result.items.size() + " photos";
        TextView stats = textView(count, 12, COLOR_PRIMARY, true);
        stack.addView(stats, matchWrap());

        return stack;
    }

    private View emptyState(String message) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(8));
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(COLOR_OUTLINE_VARIANT);
        card.setCardBackgroundColor(COLOR_SURFACE);
        TextView text = new TextView(this);
        text.setText(message);
        text.setTextColor(COLOR_ON_SURFACE_VARIANT);
        text.setTextSize(14);
        text.setGravity(Gravity.CENTER);
        text.setPadding(dp(14), dp(24), dp(14), dp(24));
        card.addView(text, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        ));
        return card;
    }

    private View loadingState(String message) {
        LinearLayout stack = new LinearLayout(this);
        stack.setOrientation(LinearLayout.VERTICAL);
        stack.setPadding(dp(2), dp(8), dp(2), dp(10));
        stack.setTag("loading");

        TextView text = textView(message, 13, COLOR_ON_SURFACE_VARIANT, false);
        stack.addView(text, matchWrap());

        LinearProgressIndicator progress = new LinearProgressIndicator(this);
        progress.setIndeterminate(true);
        progress.setTrackThickness(dp(4));
        progress.setIndicatorColor(COLOR_PRIMARY);
        progress.setTrackColor(COLOR_OUTLINE_VARIANT);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(6)
        );
        progressParams.setMargins(0, dp(8), 0, 0);
        stack.addView(progress, progressParams);
        return stack;
    }

    private View folderRow(DmsContainerItem container) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(8));
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(COLOR_OUTLINE_VARIANT);
        card.setCardBackgroundColor(COLOR_SURFACE);
        card.setClickable(true);
        card.setFocusable(true);
        card.setRippleColor(ColorStateList.valueOf(COLOR_RIPPLE_PRIMARY));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(8));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_folder);
        icon.setColorFilter(COLOR_PRIMARY);
        icon.setPadding(dp(8), dp(8), dp(8), dp(8));
        icon.setBackground(cardBackground(COLOR_PRIMARY_CONTAINER, COLOR_PRIMARY_CONTAINER, dp(8)));
        row.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        copyParams.setMargins(dp(12), 0, dp(8), 0);
        row.addView(copy, copyParams);

        TextView name = new TextView(this);
        name.setText(cleanFolderTitle(container.title, container.id));
        name.setTextColor(COLOR_ON_SURFACE);
        name.setTextSize(15);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setSingleLine(false);
        copy.addView(name, matchWrap());

        TextView meta = textView(container.childCount >= 0 ? container.childCount + " items" : "Folder", 12, COLOR_ON_SURFACE_VARIANT, false);
        meta.setPadding(0, dp(1), 0, 0);
        copy.addView(meta, matchWrap());

        TextView arrow = new TextView(this);
        arrow.setText(">");
        arrow.setTextSize(20);
        arrow.setTextColor(COLOR_MUTED);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(20), LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.setMargins(0, 0, 0, dp(6));
        card.setLayoutParams(rowParams);
        card.addView(row, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        ));
        View.OnClickListener openListener = v -> openContainer(container);
        card.setOnClickListener(openListener);
        row.setClickable(true);
        row.setOnClickListener(openListener);
        icon.setOnClickListener(openListener);
        name.setOnClickListener(openListener);
        arrow.setOnClickListener(openListener);
        return card;
    }

    private void openContainer(DmsContainerItem container) {
        if (container == null) {
            return;
        }
        if (albumsStatusText != null) {
            albumsStatusText.setText("Opening " + cleanFolderTitle(container.title, container.id) + "...");
        }
        Toast.makeText(this, "Opening " + cleanFolderTitle(container.title, container.id), Toast.LENGTH_SHORT).show();
        appendLog("Opening folder " + container.title + " id=" + container.id);
        openDmsFolder(container.id, container.title, false);
    }

    private void addPhotoCard(CameraContentItem item) {
        // Photo rendering is handled by PhotoAdapter.
    }

    private void configurePhotoGrid() {
        gridColumns = gridColumnCount();
        photoCardWidthPx = calculatePhotoCardWidth(gridColumns);
        if (photoRecycler != null) {
            photoRecycler.setLayoutManager(photoGridLayoutManager());
        }
    }

    private GridLayoutManager photoGridLayoutManager() {
        GridLayoutManager manager = new GridLayoutManager(this, gridColumnCount());
        manager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return photoAdapter != null && photoAdapter.isFullWidth(position) ? manager.getSpanCount() : 1;
            }
        });
        return manager;
    }

    private int gridColumnCount() {
        int widthPx = getResources().getDisplayMetrics().widthPixels;
        int widthDp = (int) (widthPx / getResources().getDisplayMetrics().density);
        if (widthDp >= 760) {
            return 5;
        }
        if (widthDp >= 600) {
            return 4;
        }
        if (widthDp >= 430) {
            return 3;
        }
        return 2;
    }

    private int albumColumnCount() {
        int widthPx = getResources().getDisplayMetrics().widthPixels;
        int widthDp = (int) (widthPx / getResources().getDisplayMetrics().density);
        if (widthDp >= 760) {
            return 4;
        }
        if (widthDp >= 520) {
            return 3;
        }
        return 2;
    }

    private int calculatePhotoCardWidth(int columns) {
        int sidePadding = dp(24);
        int gaps = dp(8) * columns;
        int usable = getResources().getDisplayMetrics().widthPixels - sidePadding - gaps;
        return Math.max(dp(132), usable / Math.max(columns, 1));
    }

    private int photoCardWidth() {
        if (photoCardWidthPx <= 0) {
            photoCardWidthPx = calculatePhotoCardWidth(gridColumns);
        }
        return photoCardWidthPx;
    }

    private int photoImageHeight() {
        return Math.max(dp(104), (int) (photoCardWidth() * 0.72f));
    }

    private void updateCardSelection(View card, boolean selected) {
        if (card instanceof MaterialCardView) {
            applyCardSelectionStyle((MaterialCardView) card, selected);
            return;
        }
        card.setBackground(cardBackground(selected ? COLOR_PRIMARY_CONTAINER : COLOR_SURFACE, selected ? COLOR_PRIMARY : COLOR_OUTLINE_VARIANT, dp(8)));
        updateSelectionSummary();
    }

    private void applyCardSelectionStyle(MaterialCardView card, boolean selected) {
        card.setCardBackgroundColor(selected ? COLOR_PRIMARY_CONTAINER : COLOR_SURFACE);
        card.setStrokeColor(selected ? COLOR_PRIMARY : COLOR_OUTLINE_VARIANT);
        card.setCardElevation(selected ? dp(2) : 0);
        updateSelectionSummary();
    }

    private String folderPathText() {
        StringBuilder builder = new StringBuilder("Camera");
        for (FolderState state : dmsFolderStack) {
            if (!"0".equals(state.id) && state.title != null && !state.title.isEmpty()) {
                builder.append(" / ").append(state.title);
            }
        }
        if (!"0".equals(currentDmsObjectId) && currentFolderTitle != null && !currentFolderTitle.isEmpty()) {
            builder.append(" / ").append(currentFolderTitle);
        }
        return builder.toString();
    }

    private String cleanFolderTitle(String title, String fallback) {
        String value = title == null || title.trim().isEmpty() ? fallback : title.trim();
        if (value == null || value.isEmpty()) {
            return "Camera";
        }
        return value.length() > 54 ? value.substring(0, 24) + "..." + value.substring(value.length() - 24) : value;
    }

    private boolean isAlbumLevelFolder(String objectId, String title) {
        String clean = cleanFolderTitle(title, objectId);
        if ("0".equals(objectId)) {
            return true;
        }
        if ("Camera".equalsIgnoreCase(clean)) {
            return true;
        }
        if ("PhotoRoot".equalsIgnoreCase(clean)) {
            return true;
        }
        return false;
    }

    private static final class FolderState {
        final String id;
        final String title;

        FolderState(String id, String title) {
            this.id = id;
            this.title = title;
        }
    }

    private void updateSelectionSummary() {
        int selected = selectedItemKeys.size();
        boolean selectionMode = selected > 0;
        if (selectionText != null) {
            String unit = photoLoading ? " loaded" : " photos";
            selectionText.setText(selected + " selected / " + contentItems.size() + unit);
        }
        if (selectionActionBar != null) {
            selectionActionBar.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
        }
        if (bottomNavigation != null) {
            bottomNavigation.setVisibility(selectionMode ? View.GONE : View.VISIBLE);
        }
        if (downloadButton != null && !downloadRunning) {
            downloadButton.setEnabled(selected > 0);
            downloadButton.setText(selected > 0 ? "Download " + selected : "Download");
        }
        updateBackButtons();
    }

    private void updateBackButtons() {
        boolean canGoBack = !dmsFolderStack.isEmpty();
        if (libraryBackButton != null) {
            libraryBackButton.setEnabled(canGoBack);
            libraryBackButton.setVisibility(canGoBack ? View.VISIBLE : View.GONE);
            libraryBackButton.setAlpha(1f);
        }
        if (albumBackButton != null) {
            albumBackButton.setEnabled(canGoBack);
            albumBackButton.setVisibility(canGoBack ? View.VISIBLE : View.GONE);
            albumBackButton.setAlpha(1f);
        }
    }

    private void resetTransferHistory(String message) {
        transferHistoryCount = 0;
        if (transferHistoryList != null) {
            transferHistoryList.removeAllViews();
        }
        if (transferHistoryEmpty != null) {
            transferHistoryEmpty.setVisibility(View.GONE);
        }
        appendTransferHistory("Batch started", message, COLOR_PRIMARY);
    }

    private void appendTransferHistory(String title, String detail, int accentColor) {
        if (transferHistoryList == null) {
            return;
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setBackground(cardBackground(COLOR_SURFACE_CONTAINER, accentColor, dp(8)));

        TextView titleView = textView(title, 13, accentColor, true);
        row.addView(titleView, matchWrap());

        TextView detailView = textView(detail == null || detail.isEmpty() ? " " : detail, 12, COLOR_ON_SURFACE_VARIANT, false);
        detailView.setPadding(0, dp(2), 0, 0);
        detailView.setSingleLine(false);
        row.addView(detailView, matchWrap());

        LinearLayout.LayoutParams params = spacedMatchWrap(0, 0, 0, dp(8));
        transferHistoryList.addView(row, 0, params);
        transferHistoryCount++;
        while (transferHistoryCount > 40 && transferHistoryList.getChildCount() > 0) {
            transferHistoryList.removeViewAt(transferHistoryList.getChildCount() - 1);
            transferHistoryCount--;
        }
        if (transferHistoryEmpty != null) {
            transferHistoryEmpty.setVisibility(View.GONE);
        }
        if (transferHistoryScroll != null) {
            transferHistoryScroll.post(() -> transferHistoryScroll.scrollTo(0, 0));
        }
    }

    private void updateDownloadProgress(Intent intent) {
        if (downloadStatusText == null || downloadProgress == null) {
            return;
        }
        String state = intent.getStringExtra(DownloadService.EXTRA_STATE);
        if (state == null) {
            state = "";
        }
        int total = intent.getIntExtra(DownloadService.EXTRA_TOTAL, 0);
        int index = intent.getIntExtra(DownloadService.EXTRA_INDEX, 0);
        int success = intent.getIntExtra(DownloadService.EXTRA_SUCCESS, 0);
        int failed = intent.getIntExtra(DownloadService.EXTRA_FAILED, 0);
        String filename = intent.getStringExtra(DownloadService.EXTRA_FILENAME);
        String message = intent.getStringExtra(DownloadService.EXTRA_MESSAGE);
        String failedItemJson = intent.getStringExtra(DownloadService.EXTRA_ITEM_JSON);

        if (DownloadService.STATE_STARTED.equals(state)) {
            failedDownloadItems = new JSONArray();
            downloadProgress.setMax(Math.max(total, 1));
            downloadProgress.setProgress(0);
            downloadStatusText.setText("Downloading " + total + " selected originals...");
            resetTransferHistory("Downloading " + total + " selected originals to DCIM/Sony Picture.");
            if (downloadButton != null) {
                downloadRunning = true;
                downloadButton.setEnabled(false);
                downloadButton.setText("Downloading...");
            }
            if (cancelDownloadButton != null) {
                cancelDownloadButton.setEnabled(true);
            }
            if (openGalleryButton != null) {
                openGalleryButton.setEnabled(false);
            }
            if (retryFailedButton != null) {
                retryFailedButton.setEnabled(false);
            }
            return;
        }
        if (DownloadService.STATE_FILE_STARTED.equals(state)) {
            downloadProgress.setMax(Math.max(total, 1));
            downloadProgress.setProgress(Math.max(index - 1, 0));
            downloadStatusText.setText(index + "/" + total + "  " + safeDisplay(filename));
            return;
        }
        if (DownloadService.STATE_FILE_DONE.equals(state) || DownloadService.STATE_FILE_FAILED.equals(state)) {
            if (DownloadService.STATE_FILE_FAILED.equals(state)) {
                if (failedItemJson != null && !failedItemJson.isEmpty()) {
                    failedDownloadItems.put(failedItemJson);
                }
                appendTransferHistory("Failed  " + index + "/" + total, message, 0xFFBA1A1A);
            } else {
                appendTransferHistory("Imported  " + index + "/" + total, message, 0xFF146C43);
            }
            downloadProgress.setMax(Math.max(total, 1));
            downloadProgress.setProgress(Math.min(index, Math.max(total, 1)));
            downloadStatusText.setText("Done " + success + ", failed " + failed + " / " + total);
            if (retryFailedButton != null) {
                retryFailedButton.setEnabled(failedDownloadItems.length() > 0 && !downloadRunning);
            }
            return;
        }
        if (DownloadService.STATE_DONE.equals(state) || DownloadService.STATE_FATAL.equals(state)) {
            downloadProgress.setMax(Math.max(total, 1));
            downloadProgress.setProgress(Math.max(total, 1));
            downloadStatusText.setText(message == null || message.isEmpty() ? "Downloads complete" : message);
            appendTransferHistory(
                    DownloadService.STATE_DONE.equals(state) ? "Batch complete" : "Batch failed",
                    message,
                    DownloadService.STATE_DONE.equals(state) ? COLOR_PRIMARY : 0xFFBA1A1A
            );
            downloadRunning = false;
            if (DownloadService.STATE_DONE.equals(state) && failed == 0) {
                setVisibleSelection(false);
            }
            if (downloadButton != null) {
                downloadButton.setEnabled(true);
                updateSelectionSummary();
            }
            if (cancelDownloadButton != null) {
                cancelDownloadButton.setEnabled(false);
            }
            if (openGalleryButton != null) {
                openGalleryButton.setEnabled(DownloadService.STATE_DONE.equals(state) && success > 0);
            }
            if (retryFailedButton != null) {
                retryFailedButton.setEnabled(failedDownloadItems.length() > 0);
            }
            Toast.makeText(this, DownloadService.STATE_DONE.equals(state) ? "Downloads complete" : "Download failed", Toast.LENGTH_SHORT).show();
            return;
        }
        if (DownloadService.STATE_CANCELLED.equals(state)) {
            downloadStatusText.setText(message == null || message.isEmpty() ? "Downloads cancelled" : message);
            appendTransferHistory("Batch cancelled", message, COLOR_ON_SURFACE_VARIANT);
            if (downloadButton != null) {
                downloadRunning = false;
                downloadButton.setEnabled(true);
                updateSelectionSummary();
            }
            if (cancelDownloadButton != null) {
                cancelDownloadButton.setEnabled(false);
            }
            if (retryFailedButton != null) {
                retryFailedButton.setEnabled(failedDownloadItems.length() > 0);
            }
            Toast.makeText(this, "Downloads cancelled", Toast.LENGTH_SHORT).show();
        }
    }

    private String safeDisplay(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.length() <= 42) {
            return value;
        }
        return value.substring(0, 18) + "..." + value.substring(value.length() - 18);
    }

    private String metaText(CameraContentItem item) {
        StringBuilder builder = new StringBuilder();
        builder.append(qualityLabel(item));
        if (item.size > 0) {
            if (builder.length() > 0) {
                builder.append("  ");
            }
            builder.append(formatBytes(item.size));
        }
        return builder.length() == 0 ? "Ready" : builder.toString();
    }

    private String itemKey(CameraContentItem item, int fallbackIndex) {
        if (item == null) {
            return "missing:" + fallbackIndex;
        }
        if (item.uri != null && !item.uri.isEmpty()) {
            return "uri:" + item.uri;
        }
        String downloadUrl = item.bestDownloadUrl();
        if (downloadUrl != null && !downloadUrl.isEmpty()) {
            return "url:" + downloadUrl;
        }
        return "title:" + item.title + "|" + item.size + "|" + fallbackIndex;
    }

    private long stableItemId(CameraContentItem item, int fallbackIndex) {
        String key = itemKey(item, fallbackIndex);
        long hash = 1125899906842597L;
        for (int i = 0; i < key.length(); i++) {
            hash = 31 * hash + key.charAt(i);
        }
        return hash;
    }

    private void loadImageInto(String urlText, ImageView target) {
        loadImageInto(urlText, target, 520);
    }

    private void loadImageInto(String urlText, ImageView target, int sampleTarget) {
        if (urlText == null || urlText.isEmpty()) {
            target.setTag(null);
            return;
        }
        String requestKey = urlText + "#" + sampleTarget;
        target.setTag(requestKey);
        LruCache<String, Bitmap> memoryCache = imageCacheFor(sampleTarget);
        Bitmap cached;
        synchronized (memoryCache) {
            cached = memoryCache.get(requestKey);
        }
        if (cached != null) {
            if (requestKey.equals(target.getTag())) {
                target.setImageBitmap(cached);
            }
            return;
        }
        synchronized (pendingImageTargets) {
            List<ImageView> targets = pendingImageTargets.get(requestKey);
            if (targets != null) {
                targets.add(target);
                return;
            }
            targets = new ArrayList<>();
            targets.add(target);
            pendingImageTargets.put(requestKey, targets);
        }
        imageExecutor.execute(() -> {
            Bitmap bitmap = null;
            try {
                bitmap = readDiskCachedBitmap(requestKey, sampleTarget);
                if (bitmap == null) {
                    bitmap = fetchBitmap(urlText, sampleTarget);
                    writeDiskCachedBitmap(requestKey, bitmap);
                }
                if (bitmap != null) {
                    synchronized (memoryCache) {
                        memoryCache.put(requestKey, bitmap);
                    }
                }
            } catch (Exception ignored) {
                // Keep placeholder image.
            } finally {
                Bitmap finalBitmap = bitmap;
                List<ImageView> targets;
                synchronized (pendingImageTargets) {
                    targets = pendingImageTargets.remove(requestKey);
                }
                if (finalBitmap != null && targets != null && !targets.isEmpty()) {
                    runOnUiThread(() -> {
                        for (ImageView imageView : targets) {
                            if (requestKey.equals(imageView.getTag())) {
                                imageView.setImageBitmap(finalBitmap);
                            }
                        }
                    });
                }
            }
        });
    }

    private LruCache<String, Bitmap> imageCacheFor(int sampleTarget) {
        return sampleTarget <= 700 ? thumbnailImageCache : previewImageCache;
    }

    private Bitmap readDiskCachedBitmap(String requestKey, int sampleTarget) {
        try {
            File file = diskImageCacheFile(requestKey);
            if (!file.exists() || file.length() <= 0) {
                return null;
            }
            file.setLastModified(System.currentTimeMillis());
            BitmapFactory.Options options = new BitmapFactory.Options();
            if (sampleTarget <= 700) {
                options.inPreferredConfig = Bitmap.Config.RGB_565;
                options.inDither = true;
            }
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void writeDiskCachedBitmap(String requestKey, Bitmap bitmap) {
        if (bitmap == null) {
            return;
        }
        try {
            File file = diskImageCacheFile(requestKey);
            File parent = file.getParentFile();
            if (parent == null) {
                return;
            }
            if (!parent.exists() && !parent.mkdirs()) {
                return;
            }
            try (FileOutputStream output = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 86, output);
            }
            pruneDiskImageCache(parent, 360);
        } catch (Exception ignored) {
            // Disk cache is only an acceleration layer.
        }
    }

    private File diskImageCacheFile(String requestKey) throws Exception {
        File dir = new File(getCacheDir(), "image-previews");
        return new File(dir, sha256(requestKey) + ".jpg");
    }

    private String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = digest.digest(value.getBytes("UTF-8"));
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format(Locale.US, "%02x", b & 0xff));
        }
        return builder.toString();
    }

    private void pruneDiskImageCache(File dir, int maxFiles) {
        File[] files = dir.listFiles();
        if (files == null || files.length <= maxFiles) {
            return;
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        int removeCount = files.length - maxFiles;
        for (int i = 0; i < removeCount; i++) {
            if (files[i].isFile()) {
                files[i].delete();
            }
        }
    }

    private Bitmap fetchBitmap(String urlText, int sampleTarget) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlText).openConnection();
        connection.setConnectTimeout(3500);
        connection.setReadTimeout(8000);
        connection.setRequestProperty("Accept", "image/*,*/*");
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            return null;
        }
        try (InputStream input = connection.getInputStream()) {
            byte[] bytes = readAll(input, 12 * 1024 * 1024);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, sampleTarget);
            if (sampleTarget <= 700) {
                options.inPreferredConfig = Bitmap.Config.RGB_565;
                options.inDither = true;
            }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        } finally {
            connection.disconnect();
        }
    }

    private byte[] readAll(InputStream input, int maxBytes) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[64 * 1024];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            int allowed = Math.min(read, maxBytes - total);
            if (allowed > 0) {
                output.write(buffer, 0, allowed);
            }
            total += read;
            if (total > maxBytes) {
                break;
            }
        }
        return output.toByteArray();
    }

    private int sampleSize(int width, int height, int target) {
        int sample = 1;
        while (width / sample > target * 2 || height / sample > target * 2) {
            sample *= 2;
        }
        return Math.max(1, sample);
    }

    private void showPreviewAt(int startIndex) {
        if (startIndex < 0 || startIndex >= contentItems.size()) {
            return;
        }
        final int[] currentIndex = new int[]{startIndex};
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(false);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(14), dp(18), dp(14), dp(14));
        layout.setBackgroundColor(COLOR_PREVIEW_BACKGROUND);

        LinearLayout header = horizontalRow();
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView titleText = textView("", 16, 0xFFFFFFFF, true);
        titleText.setSingleLine(false);
        header.addView(titleText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button close = new MaterialButton(this);
        close.setText("Close");
        styleButton(close, false);
        setButtonIcon(close, R.drawable.ic_close, false);
        header.addView(close, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        layout.addView(header, matchWrap());

        TextView metaView = textView("", 12, 0xFFD6DAD8, false);
        metaView.setPadding(0, dp(4), 0, dp(12));
        layout.addView(metaView, matchWrap());

        ShapeableImageView image = new ShapeableImageView(this);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setBackgroundColor(0xFF050706);
        image.setShapeAppearanceModel(image.getShapeAppearanceModel()
                .toBuilder()
                .setAllCorners(CornerFamily.ROUNDED, dp(8))
                .build());
        layout.addView(image, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        LinearLayout navControls = horizontalRow();
        navControls.setPadding(0, dp(12), 0, 0);
        Button previous = new MaterialButton(this);
        previous.setText("Previous");
        styleButton(previous, false);
        setButtonIcon(previous, R.drawable.ic_arrow_back, false);
        Button next = new MaterialButton(this);
        next.setText("Next");
        styleButton(next, false);
        setButtonIcon(next, R.drawable.ic_arrow_forward, false);
        navControls.addView(previous, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        navControls.addView(next, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        layout.addView(navControls, matchWrap());

        LinearLayout actionControls = horizontalRow();
        actionControls.setPadding(0, dp(8), 0, 0);
        Button select = new MaterialButton(this);
        select.setText("Select");
        styleButton(select, false);
        setButtonIcon(select, R.drawable.ic_select_all, false);
        Button download = new MaterialButton(this);
        download.setText("Download");
        styleButton(download, true);
        setButtonIcon(download, R.drawable.ic_download, true);
        actionControls.addView(select, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        actionControls.addView(download, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        layout.addView(actionControls, matchWrap());

        Runnable refresh = () -> {
            CameraContentItem item = contentItems.get(currentIndex[0]);
            image.setImageResource(android.R.drawable.ic_menu_gallery);
            titleText.setText(item.title);
            metaView.setText((currentIndex[0] + 1) + " / " + contentItems.size() + "  " + metaText(item));
            boolean checked = selectedItemKeys.contains(itemKey(item, currentIndex[0]));
            select.setText(checked ? "Selected" : "Select");
            previous.setEnabled(currentIndex[0] > 0);
            next.setEnabled(currentIndex[0] < contentItems.size() - 1);
            loadImageInto(previewDisplayUrl(item), image, 1200);
        };

        close.setOnClickListener(v -> dialog.dismiss());
        previous.setOnClickListener(v -> {
            if (currentIndex[0] > 0) {
                currentIndex[0]--;
                refresh.run();
            }
        });
        next.setOnClickListener(v -> {
            if (currentIndex[0] < contentItems.size() - 1) {
                currentIndex[0]++;
                refresh.run();
            }
        });
        select.setOnClickListener(v -> {
            CameraContentItem item = contentItems.get(currentIndex[0]);
            boolean nextState = !selectedItemKeys.contains(itemKey(item, currentIndex[0]));
            selectItemAt(currentIndex[0], nextState);
            refresh.run();
        });
        download.setOnClickListener(v -> {
            CameraContentItem item = contentItems.get(currentIndex[0]);
            startDownloadsForItems(singleItemArray(item), "Queued 1 download.");
            dialog.dismiss();
        });

        refresh.run();
        dialog.setContentView(layout);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(COLOR_PREVIEW_BACKGROUND));
            window.setLayout(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        }
    }

    private String previewDisplayUrl(CameraContentItem item) {
        if (item.largeUrl != null && !item.largeUrl.isEmpty()) {
            return item.largeUrl;
        }
        if (item.thumbnailUrl != null && !item.thumbnailUrl.isEmpty()) {
            return item.thumbnailUrl;
        }
        return item.bestDownloadUrl();
    }

    private void selectItem(CameraContentItem item, boolean selected) {
        int index = contentItems.indexOf(item);
        selectItemAt(index, selected);
    }

    private void selectItemAt(int index, boolean selected) {
        boolean wasSelectionMode = !selectedItemKeys.isEmpty();
        if (index >= 0 && index < contentItems.size()) {
            String key = itemKey(contentItems.get(index), index);
            if (selected) {
                selectedItemKeys.add(key);
            } else {
                selectedItemKeys.remove(key);
            }
            if (photoAdapter != null) {
                if (wasSelectionMode != !selectedItemKeys.isEmpty()) {
                    photoAdapter.notifyDataSetChanged();
                } else {
                    photoAdapter.notifyItemChanged(index);
                }
            }
        }
        updateSelectionSummary();
    }

    private void toggleSelectionAt(int index) {
        if (index < 0 || index >= contentItems.size()) {
            return;
        }
        selectItemAt(index, !selectedItemKeys.contains(itemKey(contentItems.get(index), index)));
    }

    private final class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.PhotoHolder> {
        private static final int TYPE_PHOTO = 0;
        private static final int TYPE_STATUS = 1;

        boolean isFullWidth(int position) {
            return getItemViewType(position) == TYPE_STATUS;
        }

        @Override
        public int getItemViewType(int position) {
            if (contentItems.isEmpty()) {
                return TYPE_STATUS;
            }
            return position >= contentItems.size() ? TYPE_STATUS : TYPE_PHOTO;
        }

        @Override
        public long getItemId(int position) {
            if (getItemViewType(position) == TYPE_STATUS) {
                return Long.MIN_VALUE + position;
            }
            return stableItemId(contentItems.get(position), position);
        }

        @Override
        public PhotoHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            if (viewType == TYPE_STATUS) {
                MaterialCardView card = new MaterialCardView(MainActivity.this);
                card.setRadius(dp(8));
                card.setStrokeWidth(dp(1));
                card.setStrokeColor(COLOR_OUTLINE_VARIANT);
                card.setCardBackgroundColor(COLOR_SURFACE);

                LinearLayout stack = new LinearLayout(MainActivity.this);
                stack.setOrientation(LinearLayout.VERTICAL);
                stack.setPadding(dp(14), dp(18), dp(14), dp(18));
                card.addView(stack, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                ));

                TextView title = textView("", 14, COLOR_ON_SURFACE_VARIANT, false);
                title.setGravity(Gravity.CENTER);
                stack.addView(title, matchWrap());

                LinearProgressIndicator progress = new LinearProgressIndicator(MainActivity.this);
                progress.setIndeterminate(true);
                progress.setTrackThickness(dp(4));
                progress.setIndicatorColor(COLOR_PRIMARY);
                progress.setTrackColor(COLOR_OUTLINE_VARIANT);
                LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(6)
                );
                progressParams.setMargins(0, dp(10), 0, 0);
                stack.addView(progress, progressParams);

                RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                        RecyclerView.LayoutParams.MATCH_PARENT,
                        RecyclerView.LayoutParams.WRAP_CONTENT
                );
                params.setMargins(dp(4), dp(4), dp(4), dp(10));
                card.setLayoutParams(params);
                return new PhotoHolder(card, null, title, null, null, progress);
            }

            MaterialCardView card = new MaterialCardView(MainActivity.this);
            card.setPadding(dp(6), dp(6), dp(6), dp(6));
            card.setClickable(true);
            card.setFocusable(true);
            card.setRadius(dp(8));
            card.setStrokeWidth(dp(1));
            card.setRippleColor(ColorStateList.valueOf(COLOR_RIPPLE_PRIMARY));

            LinearLayout stack = new LinearLayout(MainActivity.this);
            stack.setOrientation(LinearLayout.VERTICAL);
            card.addView(stack, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
            ));

            ShapeableImageView image = new ShapeableImageView(MainActivity.this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(COLOR_SURFACE_CONTAINER);
            image.setShapeAppearanceModel(image.getShapeAppearanceModel()
                    .toBuilder()
                    .setAllCorners(CornerFamily.ROUNDED, dp(7))
                    .build());
            stack.addView(image, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    photoImageHeight()
            ));
            image.setImageResource(android.R.drawable.ic_menu_gallery);

            TextView title = new TextView(MainActivity.this);
            title.setTextColor(COLOR_ON_SURFACE);
            title.setTextSize(14);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setSingleLine(true);
            title.setPadding(dp(2), dp(8), dp(2), 0);
            stack.addView(title, matchWrap());

            TextView meta = new TextView(MainActivity.this);
            meta.setTextColor(COLOR_MUTED);
            meta.setTextSize(12);
            meta.setSingleLine(true);
            meta.setPadding(dp(2), dp(2), dp(2), 0);
            stack.addView(meta, matchWrap());

            MaterialCheckBox box = new MaterialCheckBox(MainActivity.this);
            box.setGravity(Gravity.CENTER);
            box.setButtonTintList(ColorStateList.valueOf(COLOR_PRIMARY));
            FrameLayout.LayoutParams boxParams = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP | Gravity.END);
            card.addView(box, boxParams);

            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(dp(4), dp(4), dp(4), dp(10));
            card.setLayoutParams(params);
            return new PhotoHolder(card, image, title, meta, box, null);
        }

        @Override
        public void onBindViewHolder(PhotoHolder holder, int position) {
            if (getItemViewType(position) == TYPE_STATUS) {
                holder.title.setText(photoMessage);
                holder.progress.setVisibility(photoLoading ? View.VISIBLE : View.GONE);
                return;
            }
            CameraContentItem item = contentItems.get(position);
            String key = itemKey(item, position);
            boolean selected = selectedItemKeys.contains(key);
            holder.title.setText(item.title);
            holder.meta.setText(metaText(item));
            holder.image.setImageResource(android.R.drawable.ic_menu_gallery);
            holder.checkBox.setOnCheckedChangeListener(null);
            holder.checkBox.setChecked(selected);
            holder.checkBox.setVisibility((selected || !selectedItemKeys.isEmpty()) ? View.VISIBLE : View.GONE);
            applyCardSelectionStyle(holder.card, selected);
            loadImageInto(item.previewUrl(), holder.image);

            holder.card.setOnClickListener(v -> {
                int adapterPosition = holder.getBindingAdapterPosition();
                if (adapterPosition == RecyclerView.NO_POSITION) {
                    return;
                }
                if (!selectedItemKeys.isEmpty()) {
                    toggleSelectionAt(adapterPosition);
                } else {
                    showPreviewAt(adapterPosition);
                }
            });
            holder.card.setOnLongClickListener(v -> {
                int adapterPosition = holder.getBindingAdapterPosition();
                if (adapterPosition == RecyclerView.NO_POSITION) {
                    return true;
                }
                selectItemAt(adapterPosition, true);
                return true;
            });
            holder.image.setOnClickListener(v -> {
                int adapterPosition = holder.getBindingAdapterPosition();
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    if (!selectedItemKeys.isEmpty()) {
                        toggleSelectionAt(adapterPosition);
                    } else {
                        showPreviewAt(adapterPosition);
                    }
                }
            });
            holder.checkBox.setOnClickListener(v -> {
                int adapterPosition = holder.getBindingAdapterPosition();
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    selectItemAt(adapterPosition, holder.checkBox.isChecked());
                }
            });
        }

        @Override
        public int getItemCount() {
            if (contentItems.isEmpty()) {
                return 1;
            }
            return contentItems.size() + (photoLoading ? 1 : 0);
        }

        final class PhotoHolder extends RecyclerView.ViewHolder {
            final MaterialCardView card;
            final ShapeableImageView image;
            final TextView title;
            final TextView meta;
            final MaterialCheckBox checkBox;
            final LinearProgressIndicator progress;

            PhotoHolder(MaterialCardView card, ShapeableImageView image, TextView title, TextView meta, MaterialCheckBox checkBox, LinearProgressIndicator progress) {
                super(card);
                this.card = card;
                this.image = image;
                this.title = title;
                this.meta = meta;
                this.checkBox = checkBox;
                this.progress = progress;
            }
        }
    }

    private final class AlbumAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_FOLDER = 0;
        private static final int TYPE_STATUS = 1;

        boolean isFullWidth(int position) {
            return getItemViewType(position) == TYPE_STATUS;
        }

        @Override
        public int getItemViewType(int position) {
            return albumContainers.isEmpty() ? TYPE_STATUS : TYPE_FOLDER;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            if (viewType == TYPE_STATUS) {
                LinearLayout stack = new LinearLayout(MainActivity.this);
                stack.setOrientation(LinearLayout.VERTICAL);
                stack.setPadding(dp(4), dp(12), dp(4), dp(12));

                TextView title = textView("", 14, COLOR_ON_SURFACE_VARIANT, false);
                title.setGravity(Gravity.CENTER);
                stack.addView(title, matchWrap());

                LinearProgressIndicator progress = new LinearProgressIndicator(MainActivity.this);
                progress.setIndeterminate(true);
                progress.setTrackThickness(dp(4));
                progress.setIndicatorColor(COLOR_PRIMARY);
                progress.setTrackColor(COLOR_OUTLINE_VARIANT);
                LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(6)
                );
                progressParams.setMargins(0, dp(10), 0, 0);
                stack.addView(progress, progressParams);

                RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                        RecyclerView.LayoutParams.MATCH_PARENT,
                        RecyclerView.LayoutParams.WRAP_CONTENT
                );
                params.setMargins(dp(4), dp(4), dp(4), dp(10));
                stack.setLayoutParams(params);
                return new AlbumStatusHolder(stack, title, progress);
            }

            MaterialCardView card = new MaterialCardView(MainActivity.this);
            card.setClickable(true);
            card.setFocusable(true);
            card.setRadius(dp(8));
            card.setStrokeWidth(dp(1));
            card.setStrokeColor(COLOR_OUTLINE_VARIANT);
            card.setCardBackgroundColor(COLOR_SURFACE);
            card.setRippleColor(ColorStateList.valueOf(COLOR_RIPPLE_PRIMARY));

            LinearLayout stack = new LinearLayout(MainActivity.this);
            stack.setOrientation(LinearLayout.VERTICAL);
            stack.setPadding(dp(8), dp(8), dp(8), dp(8));
            card.addView(stack, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
            ));

            FrameLayout art = new FrameLayout(MainActivity.this);
            art.setBackground(cardBackground(COLOR_SURFACE_CONTAINER, COLOR_OUTLINE_VARIANT, dp(8)));
            stack.addView(art, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(96)
            ));

            View sheetBack = new View(MainActivity.this);
            sheetBack.setBackground(cardBackground(COLOR_SURFACE, COLOR_OUTLINE_VARIANT, dp(6)));
            FrameLayout.LayoutParams sheetBackParams = new FrameLayout.LayoutParams(
                    dp(72),
                    dp(54),
                    Gravity.CENTER
            );
            sheetBackParams.setMargins(dp(12), dp(10), 0, 0);
            art.addView(sheetBack, sheetBackParams);

            View sheetFront = new View(MainActivity.this);
            sheetFront.setBackground(cardBackground(COLOR_PRIMARY_CONTAINER, COLOR_PRIMARY_CONTAINER, dp(6)));
            FrameLayout.LayoutParams sheetFrontParams = new FrameLayout.LayoutParams(
                    dp(72),
                    dp(54),
                    Gravity.CENTER
            );
            sheetFrontParams.setMargins(0, 0, dp(12), dp(10));
            art.addView(sheetFront, sheetFrontParams);

            ImageView icon = new ImageView(MainActivity.this);
            icon.setImageResource(R.drawable.ic_folder);
            icon.setColorFilter(COLOR_PRIMARY);
            icon.setScaleType(ImageView.ScaleType.CENTER);
            art.addView(icon, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));

            TextView badge = textView("", 11, COLOR_ON_PRIMARY_CONTAINER, true);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(cardBackground(COLOR_PRIMARY_CONTAINER, COLOR_PRIMARY_CONTAINER, dp(12)));
            FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    dp(24),
                    Gravity.TOP | Gravity.END
            );
            badgeParams.setMargins(0, dp(8), dp(8), 0);
            art.addView(badge, badgeParams);

            TextView title = new TextView(MainActivity.this);
            title.setTextColor(COLOR_ON_SURFACE);
            title.setTextSize(14);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setSingleLine(true);
            title.setPadding(0, dp(8), 0, 0);
            stack.addView(title, matchWrap());

            TextView meta = new TextView(MainActivity.this);
            meta.setTextColor(COLOR_ON_SURFACE_VARIANT);
            meta.setTextSize(12);
            meta.setSingleLine(true);
            meta.setPadding(0, dp(2), 0, 0);
            stack.addView(meta, matchWrap());

            TextView open = textView("Open", 12, COLOR_PRIMARY, true);
            open.setPadding(0, dp(6), 0, 0);
            stack.addView(open, matchWrap());

            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(dp(4), dp(4), dp(4), dp(10));
            card.setLayoutParams(params);
            return new AlbumFolderHolder(card, title, meta, badge);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof AlbumStatusHolder) {
                AlbumStatusHolder status = (AlbumStatusHolder) holder;
                status.title.setText(albumMessage);
                status.progress.setVisibility(albumLoading ? View.VISIBLE : View.GONE);
                return;
            }

            AlbumFolderHolder folderHolder = (AlbumFolderHolder) holder;
            DmsContainerItem item = albumContainers.get(position);
            folderHolder.title.setText(cleanFolderTitle(item.title, item.id));
            String count = item.childCount >= 0 ? String.valueOf(item.childCount) : "-";
            folderHolder.badge.setText("  " + count + "  ");
            folderHolder.meta.setText(isAlbumLevelFolder(item.id, item.title) ? "Camera folder" : "Photo folder");
            folderHolder.card.setOnClickListener(v -> {
                int adapterPosition = folderHolder.getBindingAdapterPosition();
                if (adapterPosition == RecyclerView.NO_POSITION || adapterPosition >= albumContainers.size()) {
                    return;
                }
                openContainer(albumContainers.get(adapterPosition));
            });
        }

        @Override
        public int getItemCount() {
            return albumContainers.isEmpty() ? 1 : albumContainers.size();
        }

        final class AlbumFolderHolder extends RecyclerView.ViewHolder {
            final MaterialCardView card;
            final TextView title;
            final TextView meta;
            final TextView badge;

            AlbumFolderHolder(MaterialCardView card, TextView title, TextView meta, TextView badge) {
                super(card);
                this.card = card;
                this.title = title;
                this.meta = meta;
                this.badge = badge;
            }
        }

        final class AlbumStatusHolder extends RecyclerView.ViewHolder {
            final TextView title;
            final LinearProgressIndicator progress;

            AlbumStatusHolder(View itemView, TextView title, LinearProgressIndicator progress) {
                super(itemView);
                this.title = title;
                this.progress = progress;
            }
        }
    }

    private GradientDrawable cardBackground(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private LinearLayout addSurfaceCard(LinearLayout parent, int bottomMargin) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(8));
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(COLOR_OUTLINE_VARIANT);
        card.setCardBackgroundColor(COLOR_SURFACE);
        card.setCardElevation(0);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        ));
        parent.addView(card, spacedMatchWrap(0, 0, 0, bottomMargin));
        return content;
    }

    private LinearLayout horizontalRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private TextView textView(String text, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        return view;
    }

    private TextView sectionLabel(String text) {
        TextView view = textView(text, 12, COLOR_PRIMARY, true);
        view.setAllCaps(false);
        return view;
    }

    private Chip infoChip(String text, int background, int foreground) {
        Chip chip = new Chip(this);
        chip.setText(text);
        chip.setTextSize(10);
        chip.setTypeface(Typeface.DEFAULT_BOLD);
        chip.setTextColor(foreground);
        chip.setChipBackgroundColor(ColorStateList.valueOf(background));
        chip.setChipStrokeWidth(0);
        chip.setChipMinHeight(dp(28));
        chip.setMinHeight(dp(28));
        chip.setMinWidth(0);
        chip.setMinimumWidth(0);
        chip.setTextStartPadding(dp(10));
        chip.setTextEndPadding(dp(10));
        chip.setCheckable(false);
        chip.setClickable(false);
        chip.setFocusable(false);
        chip.setEnsureMinTouchTargetSize(false);
        return chip;
    }

    private ColorStateList navigationColorStateList() {
        return new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_selected},
                        new int[]{}
                },
                new int[]{
                        COLOR_PRIMARY,
                        COLOR_ON_SURFACE_VARIANT
                }
        );
    }

    private void styleButton(Button button, boolean primary) {
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setMinHeight(dp(36));
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMaxLines(2);
        button.setPadding(dp(10), dp(4), dp(10), dp(4));
        if (button instanceof MaterialButton) {
            MaterialButton materialButton = (MaterialButton) button;
            materialButton.setCornerRadius(dp(18));
            materialButton.setMinHeight(0);
            materialButton.setMinimumHeight(0);
            materialButton.setMinWidth(0);
            materialButton.setMinimumWidth(0);
            materialButton.setInsetTop(0);
            materialButton.setInsetBottom(0);
            materialButton.setStrokeWidth(0);
            materialButton.setRippleColor(ColorStateList.valueOf(primary ? COLOR_RIPPLE_ON_PRIMARY : COLOR_RIPPLE_PRIMARY));
            materialButton.setBackgroundTintList(ColorStateList.valueOf(primary ? COLOR_PRIMARY : COLOR_SECONDARY_CONTAINER));
        }
        if (primary) {
            button.setTextColor(COLOR_ON_PRIMARY);
            button.setTypeface(Typeface.DEFAULT_BOLD);
        } else {
            button.setTextColor(COLOR_ON_SURFACE);
            button.setTypeface(Typeface.DEFAULT);
        }
    }

    private Button smallNavButton(String label, int iconRes) {
        Button button = new MaterialButton(this);
        button.setText(label);
        styleButton(button, false);
        button.setTextSize(11);
        button.setMinHeight(dp(32));
        button.setMinimumHeight(0);
        button.setPadding(dp(8), dp(3), dp(8), dp(3));
        setButtonIcon(button, iconRes, false);
        return button;
    }

    private void setButtonIcon(Button button, int iconRes, boolean primary) {
        if (!(button instanceof MaterialButton)) {
            return;
        }
        MaterialButton materialButton = (MaterialButton) button;
        materialButton.setIconResource(iconRes);
        materialButton.setIconSize(dp(18));
        materialButton.setIconPadding(dp(6));
        materialButton.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        materialButton.setIconTint(ColorStateList.valueOf(primary ? COLOR_ON_PRIMARY : COLOR_ON_SURFACE));
    }

    private void showPage(int page) {
        currentPage = page;
        if (libraryPage != null) {
            libraryPage.setVisibility(page == PAGE_LIBRARY ? View.VISIBLE : View.GONE);
        }
        if (albumsPage != null) {
            albumsPage.setVisibility(page == PAGE_ALBUMS ? View.VISIBLE : View.GONE);
        }
        if (downloadsPage != null) {
            downloadsPage.setVisibility(page == PAGE_TRANSFERS ? View.VISIBLE : View.GONE);
        }
        if (diagnosticsPage != null) {
            diagnosticsPage.setVisibility(page == PAGE_TOOLS ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onBackPressed() {
        if (!selectedItemKeys.isEmpty()) {
            setVisibleSelection(false);
            return;
        }
        if ((currentPage == PAGE_LIBRARY || currentPage == PAGE_ALBUMS) && !dmsFolderStack.isEmpty()) {
            openParentFolder();
            return;
        }
        if (currentPage != PAGE_LIBRARY) {
            navigateToPage(PAGE_LIBRARY);
            return;
        }
        super.onBackPressed();
    }

    private void navigateToPage(int page) {
        showPage(page);
        if (bottomNavigation != null && bottomNavigation.getSelectedItemId() != page) {
            bottomNavigation.setSelectedItemId(page);
        }
    }

    private void openParentFolder() {
        if (dmsFolderStack.isEmpty()) {
            appendLog("Already at top folder.");
            Toast.makeText(this, "Already at top folder", Toast.LENGTH_SHORT).show();
            return;
        }
        FolderState parent = dmsFolderStack.remove(dmsFolderStack.size() - 1);
        currentDmsObjectId = null;
        openDmsFolder(parent.id, parent.title, false);
    }

    private void setVisibleSelection(boolean checked) {
        selectedItemKeys.clear();
        if (checked) {
            for (int i = 0; i < contentItems.size(); i++) {
                selectedItemKeys.add(itemKey(contentItems.get(i), i));
            }
        }
        if (photoAdapter != null) {
            photoAdapter.notifyDataSetChanged();
        }
        updateSelectionSummary();
    }

    private void invertVisibleSelection() {
        Set<String> next = new HashSet<>();
        for (int i = 0; i < contentItems.size(); i++) {
            String key = itemKey(contentItems.get(i), i);
            if (!selectedItemKeys.contains(key)) {
                next.add(key);
            }
        }
        selectedItemKeys.clear();
        selectedItemKeys.addAll(next);
        if (photoAdapter != null) {
            photoAdapter.notifyDataSetChanged();
        }
        updateSelectionSummary();
    }

    private void refreshCurrentFolder() {
        if (currentDmsObjectId == null || currentDmsObjectId.isEmpty()) {
            appendLog("No current folder to refresh.");
            return;
        }
        openDmsFolder(currentDmsObjectId, currentFolderTitle, false);
    }

    private void openRootFolder() {
        openDmsFolder("0", "Camera", true);
    }

    private String qualityLabel(CameraContentItem item) {
        String url = item.bestDownloadUrl();
        if (url == null) {
            return "";
        }
        String lower = url.toLowerCase(Locale.US);
        if (lower.contains("org_") || lower.contains("original")) {
            return "Original";
        } else if (lower.contains("lrg_")) {
            return "Original candidate";
        } else if (lower.contains("thumb") || lower.contains("sm_")) {
            return "Preview";
        } else {
            return "Full size";
        }
    }

    private String formatBytes(long bytes) {
        if (bytes <= 0) {
            return "";
        }
        double mb = bytes / (1024.0 * 1024.0);
        if (mb >= 1.0) {
            return String.format(Locale.US, "%.1f MB", mb);
        }
        double kb = bytes / 1024.0;
        return String.format(Locale.US, "%.0f KB", Math.max(kb, 1.0));
    }

    private void startSelectedDownloads() {
        try {
            JSONArray selected = new JSONArray();
            for (int i = 0; i < contentItems.size(); i++) {
                if (selectedItemKeys.contains(itemKey(contentItems.get(i), i))) {
                    selected.put(contentItems.get(i).toJson());
                }
            }
            if (selected.length() == 0) {
                appendLog("No selected files.");
                Toast.makeText(this, "Select photos to download first", Toast.LENGTH_SHORT).show();
                return;
            }
            startDownloadsForItems(selected, "Queued " + selected.length() + " downloads.");
        } catch (Exception ex) {
            appendLog("Cannot start downloads: " + ex.getMessage());
        }
    }

    private JSONArray singleItemArray(CameraContentItem item) {
        JSONArray selected = new JSONArray();
        try {
            selected.put(item.toJson());
        } catch (Exception ex) {
            appendLog("Cannot queue preview item: " + ex.getMessage());
        }
        return selected;
    }

    private void startDownloadsForItems(JSONArray selected, String queuedMessage) {
        if (selected == null || selected.length() == 0) {
            appendLog("No selected files.");
            return;
        }
        if (bottomNavigation != null) {
            bottomNavigation.setVisibility(View.VISIBLE);
        }
        if (selectionActionBar != null) {
            selectionActionBar.setVisibility(View.GONE);
        }
        navigateToPage(PAGE_TRANSFERS);
        Intent intent = new Intent(this, DownloadService.class);
        intent.setAction(DownloadService.ACTION_START);
        intent.putExtra(DownloadService.EXTRA_ITEMS, selected.toString());
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        appendLog(queuedMessage);
    }

    private void cancelDownloads() {
        Intent intent = new Intent(this, DownloadService.class);
        intent.setAction(DownloadService.ACTION_CANCEL);
        startService(intent);
        appendLog("Cancel requested.");
    }

    private void retryFailedDownloads() {
        if (failedDownloadItems == null || failedDownloadItems.length() == 0) {
            appendLog("No failed downloads to retry.");
            return;
        }
        JSONArray retryItems = new JSONArray();
        try {
            for (int i = 0; i < failedDownloadItems.length(); i++) {
                retryItems.put(new JSONObject(failedDownloadItems.getString(i)));
            }
        } catch (Exception ex) {
            appendLog("Cannot prepare retry queue: " + ex.getMessage());
            return;
        }
        startDownloadsForItems(retryItems, "Retrying " + retryItems.length() + " failed downloads.");
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        try {
            startActivity(intent);
        } catch (Exception ex) {
            Toast.makeText(this, "No gallery app available", Toast.LENGTH_SHORT).show();
            appendLog("Cannot open gallery: " + ex.getMessage());
        }
    }

    private void toggleDiagnostics() {
        navigateToPage(PAGE_TOOLS);
    }

    private void clearDiagnostics() {
        if (logText != null) {
            logText.setText(DIAGNOSTICS_PLACEHOLDER);
        }
    }

    private String wifiGateway() {
        try {
            WifiManager manager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            DhcpInfo dhcp = manager == null ? null : manager.getDhcpInfo();
            if (dhcp == null || dhcp.gateway == 0) {
                return null;
            }
            return String.format(
                    Locale.US,
                    "%d.%d.%d.%d",
                    dhcp.gateway & 0xff,
                    (dhcp.gateway >> 8) & 0xff,
                    (dhcp.gateway >> 16) & 0xff,
                    (dhcp.gateway >> 24) & 0xff
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private String networkDiagnostics() {
        StringBuilder builder = new StringBuilder();
        builder.append("Network diagnostics\n");
        try {
            WifiManager manager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            DhcpInfo dhcp = manager == null ? null : manager.getDhcpInfo();
            if (dhcp != null) {
                builder
                        .append("wifi ip=").append(intToIp(dhcp.ipAddress))
                        .append(", gateway=").append(intToIp(dhcp.gateway))
                        .append(", dns1=").append(intToIp(dhcp.dns1))
                        .append('\n');
            } else {
                builder.append("wifi dhcp unavailable\n");
            }
        } catch (Exception ex) {
            builder.append("wifi diagnostics failed: ").append(ex.getMessage()).append('\n');
        }
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
                Network bound = manager == null ? null : manager.getBoundNetworkForProcess();
                builder.append("bound network=").append(bound == null ? "none" : bound.toString()).append('\n');
            } catch (Exception ex) {
                builder.append("bound network check failed: ").append(ex.getMessage()).append('\n');
            }
        }
        builder.append('\n');
        return builder.toString();
    }

    private String intToIp(int value) {
        if (value == 0) {
            return "0.0.0.0";
        }
        return String.format(
                Locale.US,
                "%d.%d.%d.%d",
                value & 0xff,
                (value >> 8) & 0xff,
                (value >> 16) & 0xff,
                (value >> 24) & 0xff
        );
    }

    private void bindProcessToWifiIfPresent() {
        if (Build.VERSION.SDK_INT < 23) {
            return;
        }
        try {
            ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (manager == null) {
                return;
            }
            for (Network network : manager.getAllNetworks()) {
                NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
                if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    manager.bindProcessToNetwork(network);
                    return;
                }
            }
        } catch (Exception ignored) {
            // If binding fails, requests still try the system default route.
        }
    }

    private void setBusy(String message) {
        statusText.setText(message);
        appendLog(message);
    }

    private void appendLog(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        if (DIAGNOSTICS_PLACEHOLDER.contentEquals(logText.getText())) {
            logText.setText("");
        }
        logText.append(message);
        if (!message.endsWith("\n")) {
            logText.append("\n");
        }
    }

    private void postLog(String message) {
        runOnUiThread(() -> {
            statusText.setText(message);
            appendLog(message);
        });
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private FrameLayout.LayoutParams pageParams() {
        return new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
    }

    private LinearLayout.LayoutParams spacedMatchWrap(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private LinearLayout.LayoutParams spacedWrap(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
