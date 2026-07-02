package com.codex.sonyedge

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.LruCache
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

private val SonyBlue = Color(0xFF0057B8)
private val SonyBlueDark = Color(0xFF073A73)
private val SonyBlueSoft = Color(0xFFE8F1FF)
private val SurfaceBg = Color(0xFFFAFBFD)
private val SurfaceSoft = Color(0xFFF1F5F9)
private val BorderSoft = Color(0xFFD9E2EC)
private val TextMain = Color(0xFF111827)
private val TextMuted = Color(0xFF64748B)
private val SuccessGreen = Color(0xFF0F766E)
private val SuccessSoft = Color(0xFFE6FFFA)

private val imageCache = object : LruCache<String, Bitmap>(128 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

private data class NavSpec(val tab: SonyEdgeTab, val label: String, val icon: ImageVector)

private val navItems = listOf(
    NavSpec(SonyEdgeTab.Library, "Browse", Icons.Default.PhotoLibrary),
    NavSpec(SonyEdgeTab.Transfers, "Imports", Icons.Default.CloudDownload),
    NavSpec(SonyEdgeTab.Settings, "Settings", Icons.Default.Settings)
)

@Composable
fun SonyEdgeApp(
    state: SonyEdgeUiState,
    onTab: (SonyEdgeTab) -> Unit,
    onConnect: () -> Unit,
    onRefresh: () -> Unit,
    onRoot: () -> Unit,
    onBack: () -> Unit,
    onOpenFolder: (DmsContainerItem) -> Unit,
    onPreview: (CameraContentItem) -> Unit,
    onClosePreview: () -> Unit,
    onPreviewNext: (Int) -> Unit,
    onPreviewPage: (Int) -> Unit,
    onToggleSelection: (CameraContentItem) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onDownloadSelected: () -> Unit,
    onDownloadPreview: () -> Unit,
    onCancelDownloads: () -> Unit,
    onRetryFailed: () -> Unit,
    onOpenGallery: () -> Unit,
    onClearLogs: () -> Unit
) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = SonyBlue,
            onPrimary = Color.White,
            primaryContainer = SonyBlueSoft,
            onPrimaryContainer = SonyBlueDark,
            surface = SurfaceBg,
            surfaceVariant = SurfaceSoft,
            onSurface = TextMain,
            onSurfaceVariant = TextMuted,
            outline = BorderSoft
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = SurfaceBg) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                AppHeader(state)
                Box(Modifier.weight(1f)) {
                    when (state.activeTab) {
                        SonyEdgeTab.Library -> LibraryScreen(
                            state = state,
                            onConnect = onConnect,
                            onBack = onBack,
                            onPreview = onPreview,
                            onToggle = onToggleSelection,
                            onSelectAll = onSelectAll,
                            onClear = onClearSelection,
                            onDownload = onDownloadSelected
                        )

                        SonyEdgeTab.Camera -> CameraScreen(
                            state = state,
                            onConnect = onConnect,
                            onRefresh = onRefresh,
                            onRoot = onRoot,
                            onBack = onBack,
                            onOpenFolder = onOpenFolder
                        )

                        SonyEdgeTab.Transfers -> TransfersScreen(
                            state = state,
                            onCancel = onCancelDownloads,
                            onRetry = onRetryFailed,
                            onOpenGallery = onOpenGallery
                        )

                        SonyEdgeTab.Settings -> SettingsScreen(
                            state = state,
                            onConnect = onConnect,
                            onOpenGallery = onOpenGallery,
                            onClearLogs = onClearLogs
                        )
                    }
                }
                BottomNavigation(state.activeTab, onTab)
            }
        }
    }

    val previewIndex = state.previewIndex
    val previewItem = previewIndex?.let { state.photos.getOrNull(it) }
    if (previewItem != null) {
        PhotoPreview(
            items = state.photos,
            currentIndex = previewIndex,
            selectedKeys = state.selectedKeys,
            onClose = onClosePreview,
            onPrevious = { onPreviewNext(-1) },
            onNext = { onPreviewNext(1) },
            onPageSettled = onPreviewPage,
            onSelect = onToggleSelection,
            onDownload = onDownloadPreview
        )
    }
}

@Composable
private fun AppHeader(state: SonyEdgeUiState) {
    val headerText = when (state.connectionState) {
        ConnectionState.Connected -> "Connected"
        ConnectionState.Searching -> "Searching"
        ConnectionState.Error -> "Needs attention"
        ConnectionState.Idle -> "Camera Wi-Fi"
    }
    val ready = state.connectionState == ConnectionState.Connected
    val warning = state.connectionState == ConnectionState.Error
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "SonyEdge",
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.sp,
                maxLines = 1
            )
            Text("A7R III import", color = SonyBlue, fontSize = 10.sp, maxLines = 1)
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = when {
                ready -> SuccessSoft
                warning -> Color(0xFFFFF1F2)
                else -> SonyBlueSoft
            },
            contentColor = when {
                ready -> SuccessGreen
                warning -> Color(0xFFBE123C)
                else -> SonyBlueDark
            },
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                when {
                    ready -> Color(0xFF99F6E4)
                    warning -> Color(0xFFFDA4AF)
                    else -> BorderSoft
                }
            )
        ) {
            Row(Modifier.height(28.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(5.dp))
                Text(headerText, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
    HorizontalDivider(color = BorderSoft.copy(alpha = 0.55f))
}

@Composable
private fun PageTitle(
    title: String,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (action != null) action()
    }
}

@Composable
private fun ToolbarButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false
) {
    val shape = RoundedCornerShape(8.dp)
    if (primary) {
        Button(onClick = onClick, enabled = enabled, modifier = modifier.height(46.dp), shape = shape) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, maxLines = 1)
        }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.height(46.dp), shape = shape) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, maxLines = 1)
        }
    }
}

@Composable
private fun LibraryScreen(
    state: SonyEdgeUiState,
    onConnect: () -> Unit,
    onBack: () -> Unit,
    onPreview: (CameraContentItem) -> Unit,
    onToggle: (CameraContentItem) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onDownload: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 4.dp)) {
            val titleLeading: (@Composable () -> Unit)? = if (state.photos.isNotEmpty() && state.folderStack.isNotEmpty()) {
                {
                    IconButton(onClick = onBack, enabled = !state.loading, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SonyBlue)
                    }
                }
            } else {
                null
            }
            PageTitle(
                title = if (state.photos.isEmpty()) "Photos" else state.currentFolderTitle,
                subtitle = "${state.photos.size} items  /  ${state.selectedKeys.size} selected",
                leading = titleLeading
            )
            if (state.photos.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                PathStrip(folderPath(state))
            }
            state.errorMessage?.let {
                Spacer(Modifier.height(8.dp))
                InlineErrorCard(it, onConnect)
            }
            Spacer(Modifier.height(8.dp))
            if (state.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (state.photos.isEmpty()) {
                EmptyState(
                    title = "No media loaded",
                    message = state.status,
                    button = "Connect and browse",
                    onAction = onConnect,
                    loading = state.loading
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(bottom = if (state.selectedKeys.isEmpty()) 20.dp else 96.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(state.photos, key = { itemKey(it) }) { item ->
                        PhotoTile(
                            item = item,
                            selected = state.selectedKeys.contains(itemKey(item)),
                            selectionMode = state.selectedKeys.isNotEmpty(),
                            onPreview = { onPreview(item) },
                            onToggle = { onToggle(item) }
                        )
                    }
                }
            }
        }
        if (state.selectedKeys.isNotEmpty()) {
            SelectionBar(
                selectedCount = state.selectedKeys.size,
                totalCount = state.photos.size,
                onDownload = onDownload,
                onToggleAll = {
                    if (state.selectedKeys.size == state.photos.size) onClear() else onSelectAll()
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun CameraScreen(
    state: SonyEdgeUiState,
    onConnect: () -> Unit,
    onRefresh: () -> Unit,
    onRoot: () -> Unit,
    onBack: () -> Unit,
    onOpenFolder: (DmsContainerItem) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        PageTitle(
            title = albumTitle(state),
            subtitle = albumSubtitle(state),
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (state.folderStack.isNotEmpty()) {
                        IconButton(onClick = onBack, enabled = !state.loading) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SonyBlue)
                        }
                        IconButton(onClick = onRoot, enabled = !state.loading) {
                            Icon(Icons.Default.Folder, contentDescription = "Root", tint = SonyBlue)
                        }
                    }
                    IconButton(onClick = onRefresh, enabled = !state.loading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = SonyBlue)
                    }
                }
            }
        )
        Spacer(Modifier.height(10.dp))
        state.errorMessage?.let {
            InlineErrorCard(it, onConnect)
            Spacer(Modifier.height(10.dp))
        }
        if (state.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (state.loading && state.folders.isEmpty()) {
            LoadingState("Opening ${state.currentFolderTitle}...")
        } else if (state.folders.isEmpty()) {
            EmptyState("No albums loaded", "Connect to the camera Wi-Fi and browse the card.", "Connect camera", onConnect, state.loading)
        } else {
            FolderSummary(state)
            Spacer(Modifier.height(10.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(state.folders, key = { it.id }) { folder ->
                    FolderRow(folder, enabled = !state.loading, onOpen = onOpenFolder)
                }
            }
        }
    }
}

@Composable
private fun FolderSummary(state: SonyEdgeUiState) {
    Surface(color = Color.White, shape = RoundedCornerShape(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(state.currentFolderTitle, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(folderSummarySubtitle(state), color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CountChip("${state.folders.size} folders")
                CountChip("${state.photos.size} items")
            }
        }
    }
}

@Composable
private fun TransfersScreen(state: SonyEdgeUiState, onCancel: () -> Unit, onRetry: () -> Unit, onOpenGallery: () -> Unit) {
    val importing = state.downloadState in setOf(
        DownloadService.STATE_STARTED,
        DownloadService.STATE_FILE_STARTED,
        DownloadService.STATE_FILE_DONE,
        DownloadService.STATE_FILE_FAILED
    ) && state.downloadTotal > 0 && state.downloadProgress < state.downloadTotal
    val hasFailures = state.downloadFailed > 0 || state.failedItems.isNotEmpty()
    val transferTitle = when {
        importing -> "Importing ${state.downloadProgress} of ${state.downloadTotal}"
        hasFailures -> "Some items failed"
        state.downloadTotal > 0 -> "Import complete"
        else -> "Ready to import"
    }
    val transferSubtitle = when {
        state.downloadTotal > 0 -> "Saved to DCIM/Sony Picture"
        else -> "Original files will be saved to DCIM/Sony Picture"
    }
    val downloadSummary = userDownloadSummary(state)
    val userEvents = state.transferEvents
        .mapNotNull { cleanTransferEvent(it) }
        .take(6)

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        PageTitle("Imports", transferSubtitle)
        Spacer(Modifier.height(14.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(8.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (hasFailures) Color(0xFFFFF1F2) else SuccessSoft,
                        contentColor = if (hasFailures) Color(0xFFBE123C) else SuccessGreen
                    ) {
                        Icon(
                            if (hasFailures) Icons.Default.Info else Icons.Default.DoneAll,
                            contentDescription = null,
                            modifier = Modifier.padding(10.dp).size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(transferTitle, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (state.downloadTotal > 0) "${state.downloadSuccess} imported / ${state.downloadFailed} failed" else "No active imports",
                            color = TextMuted,
                            maxLines = 1
                        )
                    }
                }
                if (state.downloadTotal > 0) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { state.downloadProgress.toFloat() / state.downloadTotal.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(downloadSummary, color = TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (state.failedItems.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    state.failedItems.take(3).forEach {
                        Text(it.title, color = Color(0xFFBE123C), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (state.failedItems.size > 3) {
                        Text("+${state.failedItems.size - 3} more failed items", color = Color(0xFFBE123C), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (importing || state.failedItems.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (importing) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Cancel")
                    }
                }
                if (state.failedItems.isNotEmpty()) {
                    OutlinedButton(onClick = onRetry, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Retry failed")
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onOpenGallery, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
            Icon(Icons.Default.Image, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Open Gallery")
        }
        Spacer(Modifier.height(18.dp))
        Text("Recent activity", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (userEvents.isEmpty()) {
            Text("No transfers yet.", color = TextMuted)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(userEvents) { event ->
                    Text(event, color = TextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(state: SonyEdgeUiState, onConnect: () -> Unit, onOpenGallery: () -> Unit, onClearLogs: () -> Unit) {
    var diagnosticsExpanded by remember { mutableStateOf(false) }
    val connectionText = when (state.connectionState) {
        ConnectionState.Connected -> "Camera service is ready."
        ConnectionState.Searching -> "Searching for the camera service..."
        ConnectionState.Error -> state.errorMessage ?: "Camera connection needs attention."
        ConnectionState.Idle -> "Join the camera Wi-Fi, then connect."
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        PageTitle("Settings", "Connection, storage and diagnostics")
        Spacer(Modifier.height(14.dp))
        InfoPanel(
            title = "Camera connection",
            message = connectionText,
            icon = Icons.Default.Wifi,
            tone = if (state.connectionState == ConnectionState.Error) Color(0xFFFFF1F2) else SonyBlueSoft,
            iconTint = if (state.connectionState == ConnectionState.Error) Color(0xFFBE123C) else SonyBlue
        ) {
            ToolbarButton(
                if (state.connectionState == ConnectionState.Connected) "Reconnect camera" else "Connect camera",
                Icons.Default.Wifi,
                onConnect,
                Modifier.fillMaxWidth(),
                primary = true
            )
        }
        Spacer(Modifier.height(10.dp))
        InfoPanel(
            title = "Saved originals",
            message = "Imports are written to DCIM/Sony Picture.",
            icon = Icons.Default.Image,
            tone = SuccessSoft,
            iconTint = SuccessGreen
        ) {
            ToolbarButton("Open gallery", Icons.Default.Image, onOpenGallery, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(10.dp))
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSoft)
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { diagnosticsExpanded = !diagnosticsExpanded }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(shape = RoundedCornerShape(8.dp), color = SurfaceSoft, contentColor = TextMuted) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.padding(10.dp).size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Advanced diagnostics", fontWeight = FontWeight.Bold)
                        Text("${state.logs.size} recent log entries", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(
                        if (diagnosticsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (diagnosticsExpanded) "Hide diagnostics" else "Show diagnostics",
                        tint = TextMuted
                    )
                }
                if (diagnosticsExpanded) {
                    HorizontalDivider(color = BorderSoft)
                    Row(
                        Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Logs", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        TextButton(onClick = onClearLogs) { Text("Clear") }
                    }
                    LazyColumn(
                        Modifier.height(300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 20.dp)
                    ) {
                        if (state.logs.isEmpty()) {
                            item { Text("Logs appear here after connecting or browsing.", color = TextMuted) }
                        } else {
                            items(state.logs) { Text(it, color = TextMuted, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }
}

private fun userDownloadSummary(state: SonyEdgeUiState): String = when {
    state.downloadTotal <= 0 -> "Choose photos, then tap Import."
    state.downloadProgress < state.downloadTotal -> toImportTerms(state.downloadMessage.substringBefore(" Output:"))
    state.downloadFailed == 0 -> "Saved ${state.downloadSuccess} item${if (state.downloadSuccess == 1) "" else "s"} to DCIM/Sony Picture."
    else -> "Imported ${state.downloadSuccess}, failed ${state.downloadFailed}. Failed items can be retried."
}

@Composable
private fun PathStrip(path: String) {
    Surface(
        color = SurfaceBg,
        contentColor = TextMuted,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 0.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(14.dp), tint = SonyBlue)
            Spacer(Modifier.width(5.dp))
            Text(path, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp)
        }
    }
}

@Composable
private fun InfoPanel(
    title: String,
    message: String,
    icon: ImageVector,
    tone: Color,
    iconTint: Color,
    action: @Composable () -> Unit
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSoft)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(8.dp), color = tone, contentColor = iconTint) {
                    Icon(icon, contentDescription = null, modifier = Modifier.padding(10.dp).size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold)
                    Text(message, color = TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            action()
        }
    }
}

@Composable
private fun InlineErrorCard(message: String, onRetry: () -> Unit) {
    Surface(
        color = Color(0xFFFFF7F7),
        contentColor = Color(0xFF991B1B),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECACA))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text("Needs attention", fontWeight = FontWeight.Bold, maxLines = 1)
                Text(message, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

private fun albumTitle(state: SonyEdgeUiState): String =
    if (state.folderStack.isEmpty() && state.currentFolderTitle == "Camera") "Albums" else state.currentFolderTitle

private fun albumSubtitle(state: SonyEdgeUiState): String =
    "${state.folders.size} folders / ${state.photos.size} items"

private fun cleanTransferEvent(event: String): String? {
    if (event.contains("MIME=", ignoreCase = true) || event.contains("header=", ignoreCase = true) || event.contains("bytes", ignoreCase = true)) {
        return null
    }
    val raw = event.substringBefore(" Output:").trim()
    if (raw.startsWith("Downloads complete", ignoreCase = true)) {
        return raw
            .replace("Downloads complete.", "Import complete:")
            .replace("Downloads complete", "Import complete:")
            .replace(Regex("Success\\s+(\\d+),\\s*failed\\s+(\\d+)\\."), "imported $1, failed $2.")
            .replace(Regex("success\\s+(\\d+),\\s*failed\\s+(\\d+)\\."), "imported $1, failed $2.")
    }
    val cleaned = toImportTerms(raw)
    return when {
        cleaned.isBlank() -> null
        else -> cleaned
    }
}

private fun toImportTerms(value: String): String =
    value
        .replace("Downloads", "Imports")
        .replace("downloads", "imports")
        .replace("Downloading", "Importing")
        .replace("downloading", "importing")
        .replace("Download", "Import")
        .replace("download", "import")

@Composable
private fun BottomNavigation(activeTab: SonyEdgeTab, onTab: (SonyEdgeTab) -> Unit) {
    NavigationBar(containerColor = Color.White, modifier = Modifier.navigationBarsPadding()) {
        navItems.forEach { item ->
            val selected = if (item.tab == SonyEdgeTab.Library) {
                activeTab == SonyEdgeTab.Library || activeTab == SonyEdgeTab.Camera
            } else {
                activeTab == item.tab
            }
            NavigationBarItem(
                selected = selected,
                onClick = { onTab(item.tab) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label, maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = SonyBlue,
                    selectedTextColor = TextMain,
                    indicatorColor = SonyBlueSoft,
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted
                )
            )
        }
    }
}

@Composable
private fun SelectionBar(
    selectedCount: Int,
    totalCount: Int,
    onDownload: () -> Unit,
    onToggleAll: () -> Unit,
    modifier: Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        color = Color.White,
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 4.dp
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "$selectedCount selected",
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(onClick = onToggleAll, modifier = Modifier.height(44.dp), shape = RoundedCornerShape(8.dp)) {
                Text(if (selectedCount == totalCount) "Clear" else "All", maxLines = 1)
            }
            Button(onClick = onDownload, shape = RoundedCornerShape(8.dp), modifier = Modifier.height(44.dp)) {
                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Import ($selectedCount)", maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoTile(
    item: CameraContentItem,
    selected: Boolean,
    selectionMode: Boolean,
    onPreview: () -> Unit,
    onToggle: () -> Unit
) {
    Box(
        modifier = Modifier.combinedClickable(
            onClick = { if (selectionMode) onToggle() else onPreview() },
            onLongClick = onToggle
        )
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceSoft)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) SonyBlue else BorderSoft.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        if (isVideoItem(item)) {
            MediaPlaceholder(Icons.Default.Movie, Modifier.fillMaxSize())
        } else {
            RemoteCameraImage(
                url = item.previewUrl(),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                maxDimension = 420
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(54.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.56f))
                    )
                )
        ) {
            Text(
                item.title.substringBeforeLast('.', item.title),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp
            )
        }

        if (selectionMode || selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .semantics {
                        contentDescription = if (selected) "Deselect ${item.title}" else "Select ${item.title}"
                    }
                    .clickable { onToggle() }
            ) {
                SelectCircle(selected = selected, size = 34.dp)
            }
        }
    }
}

@Composable
private fun SelectCircle(selected: Boolean, size: androidx.compose.ui.unit.Dp) {
    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = if (selected) SonyBlue else Color.Transparent,
        contentColor = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White.copy(alpha = 0.92f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = "Selected", modifier = Modifier.size(size * 0.56f))
            }
        }
    }
}

@Composable
private fun MediaPlaceholder(icon: ImageVector, modifier: Modifier) {
    Box(modifier.background(SurfaceSoft).border(1.dp, BorderSoft), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(44.dp))
    }
}

@Composable
private fun FolderRow(folder: DmsContainerItem, enabled: Boolean, onOpen: (DmsContainerItem) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onOpen(folder) },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(8.dp), color = SonyBlueSoft, contentColor = SonyBlue) {
                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.padding(12.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(cleanTitle(folder.title, folder.id), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                Text("${folder.childCount.coerceAtLeast(0)} items", color = TextMuted)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = TextMuted)
        }
    }
}

@Composable
private fun CountChip(text: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = SonyBlueSoft, contentColor = SonyBlueDark) {
        Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptyState(title: String, message: String, button: String, onAction: () -> Unit, loading: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = RoundedCornerShape(8.dp), color = SonyBlueSoft, contentColor = SonyBlue) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.padding(14.dp).size(34.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text(message, color = TextMuted, modifier = Modifier.padding(top = 6.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(18.dp))
            Button(onClick = onAction, enabled = !loading, shape = RoundedCornerShape(8.dp)) {
                Text(if (loading) "Working..." else button)
            }
        }
    }
}

@Composable
private fun LoadingState(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = RoundedCornerShape(8.dp), color = SonyBlueSoft, contentColor = SonyBlue) {
                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.padding(14.dp).size(34.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(message, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text("Waiting for the camera to respond.", color = TextMuted, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoPreview(
    items: List<CameraContentItem>,
    currentIndex: Int,
    selectedKeys: Set<String>,
    onClose: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPageSettled: (Int) -> Unit,
    onSelect: (CameraContentItem) -> Unit,
    onDownload: () -> Unit
) {
    if (items.isEmpty()) return
    val startPage = currentIndex.coerceIn(items.indices)
    val pagerState = rememberPagerState(initialPage = startPage, pageCount = { items.size })
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF0B1220)) {
            var controlsVisible by remember { mutableStateOf(true) }
            LaunchedEffect(currentIndex) {
                val target = currentIndex.coerceIn(items.indices)
                if (pagerState.currentPage != target) {
                    pagerState.animateScrollToPage(target)
                }
            }
            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.settledPage }.collect { page ->
                    onPageSettled(page)
                }
            }
            val page = pagerState.currentPage.coerceIn(items.indices)
            val currentItem = items[page]
            val selected = selectedKeys.contains(itemKey(currentItem))
            val canPrevious = page > 0
            val canNext = page < items.lastIndex
            Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(items.size) {
                            detectTapGestures(onTap = { controlsVisible = !controlsVisible })
                        },
                    beyondViewportPageCount = 1
                ) { pageIndex ->
                    val frameItem = items[pageIndex]
                    if (isVideoItem(frameItem)) {
                        MediaPlaceholder(Icons.Default.Movie, Modifier.fillMaxSize())
                    } else {
                        ProgressiveCameraImage(
                            primaryUrl = frameItem.largeUrl?.takeIf { url -> url.isNotBlank() } ?: frameItem.previewUrl(),
                            fallbackUrl = frameItem.previewUrl(),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                            maxDimension = 2400
                        )
                    }
                }

                if (controlsVisible) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(128.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)
                                )
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.32f), contentColor = Color.White) {
                                IconButton(onClick = onClose, modifier = Modifier.size(42.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(currentItem.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${page + 1} / ${items.size}", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { controlsVisible = false }, modifier = Modifier.size(42.dp)) {
                                Icon(Icons.Default.MoreHoriz, contentDescription = null, tint = Color.White.copy(alpha = 0.72f))
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(154.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.76f))
                                )
                            )
                    ) {
                        Row(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.30f), contentColor = Color.White) {
                                IconButton(onClick = onPrevious, enabled = canPrevious, modifier = Modifier.size(46.dp)) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                        contentDescription = "Previous photo",
                                        tint = if (canPrevious) Color.White else Color.White.copy(alpha = 0.28f)
                                    )
                                }
                            }
                            Button(
                                onClick = onDownload,
                                modifier = Modifier.weight(1f).height(46.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SonyBlue),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Import", maxLines = 1, fontSize = 14.sp)
                            }
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .semantics {
                                        contentDescription = if (selected) "Deselect photo" else "Select photo"
                                    }
                                    .clickable { onSelect(currentItem) },
                                contentAlignment = Alignment.Center
                            ) {
                                SelectCircle(selected = selected, size = 40.dp)
                            }
                            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.30f), contentColor = Color.White) {
                                IconButton(onClick = onNext, enabled = canNext, modifier = Modifier.size(46.dp)) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "Next photo",
                                        tint = if (canNext) Color.White else Color.White.copy(alpha = 0.28f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressiveCameraImage(
    primaryUrl: String?,
    fallbackUrl: String?,
    modifier: Modifier,
    contentScale: ContentScale,
    maxDimension: Int
) {
    val context = LocalContext.current
    val primary = primaryUrl.orEmpty()
    val fallback = fallbackUrl.orEmpty().takeIf { it.isNotBlank() && it != primary }
    val fallbackKey = fallback?.let { "$it@900" }
    val primaryKey = "$primary@$maxDimension"
    val fallbackBitmap by produceState<Bitmap?>(initialValue = fallbackKey?.let { imageCache.get(it) }, fallbackKey) {
        value = fallback?.let { loadBitmap(context.applicationContext, it, 900) }
    }
    val primaryBitmap by produceState<Bitmap?>(initialValue = imageCache.get(primaryKey), primaryKey) {
        value = loadBitmap(context.applicationContext, primary, maxDimension)
    }
    val bitmap = primaryBitmap ?: fallbackBitmap
    if (bitmap == null) {
        PreviewLoadingPlaceholder(modifier)
    } else {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier.background(Color(0xFF0B1220))
        )
    }
}

@Composable
private fun RemoteCameraImage(url: String?, modifier: Modifier, contentScale: ContentScale, maxDimension: Int) {
    val context = LocalContext.current
    val cacheKey = "${url.orEmpty()}@$maxDimension"
    val bitmap by produceState<Bitmap?>(initialValue = imageCache.get(cacheKey), cacheKey) {
        value = loadBitmap(context.applicationContext, url.orEmpty(), maxDimension)
    }
    val renderedBitmap = bitmap
    if (renderedBitmap == null) {
        ThumbnailLoadingPlaceholder(modifier)
    } else {
        Image(
            bitmap = renderedBitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier.background(SurfaceSoft)
        )
    }
}

@Composable
private fun ThumbnailLoadingPlaceholder(modifier: Modifier) {
    Box(
        modifier
            .background(SurfaceSoft)
            .border(1.dp, BorderSoft.copy(alpha = 0.65f))
            .padding(12.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(BorderSoft.copy(alpha = 0.70f))
        )
    }
}

@Composable
private fun PreviewLoadingPlaceholder(modifier: Modifier) {
    Box(modifier.background(Color(0xFF0B1220)), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(8.dp), color = Color.White.copy(alpha = 0.08f)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Image, contentDescription = null, tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Loading preview", color = Color.White.copy(alpha = 0.70f), fontSize = 13.sp)
            }
        }
    }
}

private suspend fun loadBitmap(context: Context, url: String, maxDimension: Int): Bitmap? = withContext(Dispatchers.IO) {
    if (url.isBlank()) return@withContext null
    val memoryKey = "$url@$maxDimension"
    imageCache.get(memoryKey)?.let { return@withContext it }
    val diskFile = imageCacheFile(context, url)
    if (diskFile.isFile && diskFile.length() > 0) {
        decodeSampledBitmap(diskFile.readBytes(), maxDimension)?.let { bitmap ->
            imageCache.put(memoryKey, bitmap)
            return@withContext bitmap
        }
    }
    var connection: HttpURLConnection? = null
    try {
        connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 3500
        connection.readTimeout = 9000
        connection.setRequestProperty("Accept", "image/*,*/*")
        if (connection.responseCode !in 200..299) return@withContext null
        val bytes = connection.inputStream.use { it.readBytes() }
        if (bytes.isNotEmpty()) {
            runCatching {
                diskFile.parentFile?.mkdirs()
                diskFile.writeBytes(bytes)
            }
        }
        val bitmap = decodeSampledBitmap(bytes, maxDimension)
        if (bitmap != null) imageCache.put(memoryKey, bitmap)
        bitmap
    } catch (_: Exception) {
        null
    } finally {
        connection?.disconnect()
    }
}

private fun decodeSampledBitmap(bytes: ByteArray, maxDimension: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
    return applyExifOrientation(bytes, decoded)
}

private fun applyExifOrientation(bytes: ByteArray, bitmap: Bitmap): Bitmap {
    val orientation = runCatching {
        ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f)
            matrix.preScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f)
            matrix.preScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        else -> return bitmap
    }
    return runCatching {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { rotated ->
            if (rotated != bitmap) bitmap.recycle()
        }
    }.getOrElse { bitmap }
}

private fun imageCacheFile(context: Context, url: String): File {
    val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
    val name = digest.joinToString("") { "%02x".format(it) }
    return File(File(context.cacheDir, "sonyedge-image-cache"), "$name.img")
}

private fun folderPath(state: SonyEdgeUiState): String {
    val parts = (state.folderStack.map { it.title } + state.currentFolderTitle)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .filterNot { it == "Camera" && state.currentFolderTitle == "Camera" && state.folderStack.isEmpty() }
    val normalized = mutableListOf<String>()
    for (part in parts) {
        if (normalized.lastOrNull() != part) normalized += part
    }
    return normalized.ifEmpty { listOf("Camera") }.joinToString(" / ")
}

private fun folderSummarySubtitle(state: SonyEdgeUiState): String =
    if (state.folderStack.isEmpty() && state.currentFolderTitle == "Camera") {
        "Card root"
    } else {
        folderPath(state)
    }

private fun isVideoItem(item: CameraContentItem): Boolean {
    val title = item.title.lowercase()
    val kind = item.contentKind.lowercase()
    return title.endsWith(".mp4") || title.endsWith(".mov") || kind.contains("video") || kind.contains("movie")
}
