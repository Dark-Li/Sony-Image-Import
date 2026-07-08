package com.codex.sonyedge

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.LinkedHashSet

enum class SonyEdgeTab {
    Library,
    Camera,
    Transfers,
    Settings
}

enum class ConnectionState {
    Idle,
    Searching,
    Connected,
    Error
}

data class FolderState(val id: String, val title: String)

private data class CachedFolder(
    val id: String,
    val title: String,
    val folders: List<DmsContainerItem>,
    val photos: List<CameraContentItem>
)

data class SonyEdgeUiState(
    val activeTab: SonyEdgeTab = SonyEdgeTab.Library,
    val connectionState: ConnectionState = ConnectionState.Idle,
    val status: String = "Connect to the camera Wi-Fi, then browse.",
    val errorMessage: String? = null,
    val loading: Boolean = false,
    val currentFolderId: String = "0",
    val currentFolderTitle: String = "Camera",
    val folderStack: List<FolderState> = emptyList(),
    val folders: List<DmsContainerItem> = emptyList(),
    val photos: List<CameraContentItem> = emptyList(),
    val selectedKeys: Set<String> = emptySet(),
    val logs: List<String> = emptyList(),
    val previewIndex: Int? = null,
    val downloadMessage: String = "Ready to import originals to DCIM/Sony Picture",
    val downloadProgress: Int = 0,
    val downloadTotal: Int = 0,
    val downloadSuccess: Int = 0,
    val downloadFailed: Int = 0,
    val downloadState: String = "",
    val downloadBytesDone: Long = 0,
    val downloadBytesTotal: Long = 0,
    val downloadSpeedBps: Long = 0,
    val downloadEtaSeconds: Long = 0,
    val transferEvents: List<String> = emptyList(),
    val failedItems: List<CameraContentItem> = emptyList()
) {
    val shouldHandleBack: Boolean
        get() = previewIndex != null ||
            selectedKeys.isNotEmpty() ||
            folderStack.isNotEmpty() ||
            activeTab != browseTabFor(this)
}

class SonyEdgeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SonyCameraRepository(application)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _uiState = MutableStateFlow(SonyEdgeUiState())
    val uiState: StateFlow<SonyEdgeUiState> = _uiState

    private var activeService: DmsServiceInfo? = null
    private val folderCache = linkedMapOf<String, CachedFolder>()
    private var browseRequestId = 0

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }

    fun selectTab(tab: SonyEdgeTab) {
        _uiState.update { state ->
            val target = if (tab == SonyEdgeTab.Library) browseTabFor(state) else tab
            state.copy(activeTab = target)
        }
    }

    fun handleBack() {
        val state = _uiState.value
        when {
            state.previewIndex != null -> closePreview()
            state.selectedKeys.isNotEmpty() -> clearSelection()
            state.activeTab != browseTabFor(state) -> selectTab(SonyEdgeTab.Library)
            state.folderStack.isNotEmpty() -> goBack()
        }
    }

    fun connectAndBrowse() {
        setLoading("Finding camera services...", SonyEdgeTab.Camera)
        scope.launch {
            runCatching {
                repository.connect { addLog(it) }
            }.onSuccess { service ->
                activeService = service
                repository.saveCachedEndpoint(service.controlUrl)
                addLog("Connected: ${service.controlUrl}")
                _uiState.update { it.copy(connectionState = ConnectionState.Connected, errorMessage = null) }
                openFolder("0", "Camera", pushCurrent = false)
            }.onFailure { ex ->
                setError("Connect failed: ${ex.message}", SonyEdgeTab.Camera)
            }
        }
    }

    fun browseRoot() {
        openFolder("0", "Camera", pushCurrent = false)
    }

    fun refresh() {
        val state = _uiState.value
        folderCache.remove(state.currentFolderId)
        openFolder(state.currentFolderId, state.currentFolderTitle, pushCurrent = false, preferCache = false)
    }

    fun openFolder(folder: DmsContainerItem) {
        openFolder(folder.id, folder.title, pushCurrent = true)
    }

    fun goBack() {
        val state = _uiState.value
        val previous = state.folderStack.lastOrNull() ?: return
        val nextStack = state.folderStack.dropLast(1)
        val cached = folderCache[previous.id]
        if (cached != null) {
            browseRequestId++
            applyFolder(
                cached = cached,
                folderStack = nextStack,
                status = "Loaded ${cached.folders.size} folders and ${cached.photos.size} photos.",
                loading = false
            )
        } else {
            _uiState.update { it.copy(folderStack = nextStack) }
            openFolder(previous.id, previous.title, pushCurrent = false)
        }
    }

    private fun openFolder(id: String, title: String, pushCurrent: Boolean, preferCache: Boolean = true) {
        val service = activeService ?: repository.cachedService()
        if (service.controlUrl.isNullOrBlank()) {
            setError("Connect to the camera Wi-Fi first.", SonyEdgeTab.Camera)
            return
        }
        activeService = service
        val previousState = _uiState.value
        val cleanTitle = cleanTitle(title, id)
        val requestId = ++browseRequestId
        val nextStack = if (pushCurrent) {
            _uiState.value.folderStack + FolderState(_uiState.value.currentFolderId, _uiState.value.currentFolderTitle)
        } else {
            _uiState.value.folderStack
        }
        if (preferCache) {
            val cached = folderCache[id]
            if (cached != null) {
                applyFolder(
                    cached = cached.copy(title = cleanTitle(title, id)),
                    folderStack = nextStack,
                    status = "Loaded ${cached.folders.size} folders and ${cached.photos.size} photos.",
                    loading = false
                )
                return
            }
        }
        _uiState.update {
            it.copy(
                activeTab = SonyEdgeTab.Camera,
                connectionState = ConnectionState.Connected,
                status = "Opening $cleanTitle...",
                errorMessage = null,
                loading = true,
                currentFolderId = id,
                currentFolderTitle = cleanTitle,
                folderStack = nextStack,
                folders = emptyList(),
                photos = emptyList(),
                selectedKeys = emptySet(),
                previewIndex = null
            )
        }
        scope.launch {
            runCatching {
                repository.browse(service, id) { addLog(it) }
            }.onSuccess { result ->
                if (requestId != browseRequestId) return@launch
                val cached = CachedFolder(id, cleanTitle, result.containers, result.items)
                folderCache[id] = cached
                _uiState.update {
                    it.copy(
                        activeTab = if (result.items.isEmpty()) SonyEdgeTab.Camera else SonyEdgeTab.Library,
                        connectionState = ConnectionState.Connected,
                        status = "Loaded ${result.containers.size} folders and ${result.items.size} photos.",
                        errorMessage = null,
                        loading = false,
                        folders = result.containers,
                        photos = result.items
                    )
                }
            }.onFailure { ex ->
                if (requestId != browseRequestId) return@launch
                _uiState.value = previousState.copy(
                    loading = false,
                    connectionState = ConnectionState.Error,
                    status = "Open folder failed: ${ex.message}",
                    errorMessage = "Open folder failed: ${ex.message}",
                    selectedKeys = emptySet(),
                    previewIndex = null
                )
                addLog("Open folder failed: ${ex.message}")
            }
        }
    }

    private fun applyFolder(cached: CachedFolder, folderStack: List<FolderState>, status: String, loading: Boolean) {
        _uiState.update {
            it.copy(
                activeTab = browseTabFor(cached.folders, cached.photos),
                connectionState = ConnectionState.Connected,
                status = status,
                errorMessage = null,
                loading = loading,
                currentFolderId = cached.id,
                currentFolderTitle = cached.title,
                folderStack = folderStack,
                folders = cached.folders,
                photos = cached.photos,
                selectedKeys = emptySet(),
                previewIndex = null
            )
        }
    }

    fun setPreviewIndex(index: Int) {
        _uiState.update { state ->
            if (index in state.photos.indices) state.copy(previewIndex = index) else state
        }
    }

    fun toggleSelection(item: CameraContentItem) {
        val key = itemKey(item)
        _uiState.update { state ->
            val next = state.selectedKeys.toMutableSet()
            if (!next.add(key)) next.remove(key)
            state.copy(selectedKeys = next)
        }
    }

    fun selectAllVisible() {
        _uiState.update { state -> state.copy(selectedKeys = state.photos.mapTo(mutableSetOf()) { itemKey(it) }) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedKeys = emptySet()) }
    }

    fun invertSelection() {
        _uiState.update { state ->
            val next = state.photos.mapTo(mutableSetOf()) { itemKey(it) }
            next.removeAll(state.selectedKeys)
            state.copy(selectedKeys = next)
        }
    }

    fun showPreview(item: CameraContentItem) {
        val index = _uiState.value.photos.indexOfFirst { itemKey(it) == itemKey(item) }
        if (index >= 0) _uiState.update { it.copy(previewIndex = index) }
    }

    fun closePreview() {
        _uiState.update { it.copy(previewIndex = null) }
    }

    fun previewNext(delta: Int) {
        _uiState.update { state ->
            val current = state.previewIndex ?: return@update state
            if (state.photos.isEmpty()) return@update state
            val next = (current + delta).coerceIn(0, state.photos.lastIndex)
            state.copy(previewIndex = next)
        }
    }

    fun downloadSelected() {
        val state = _uiState.value
        startDownload(state.photos.filter { state.selectedKeys.contains(itemKey(it)) })
    }

    fun downloadPreview() {
        val state = _uiState.value
        val item = state.previewIndex?.let { state.photos.getOrNull(it) } ?: return
        closePreview()
        startDownload(listOf(item))
    }

    fun retryFailed() {
        startDownload(_uiState.value.failedItems)
    }

    fun cancelDownloads() {
        getApplication<Application>().startService(Intent(getApplication(), DownloadService::class.java).apply {
            action = DownloadService.ACTION_CANCEL
        })
    }

    private fun startDownload(items: List<CameraContentItem>) {
        if (items.isEmpty()) return
        val array = JSONArray()
        items.forEach { array.put(it.toJson()) }
        val intent = Intent(getApplication(), DownloadService::class.java).apply {
            action = DownloadService.ACTION_START
            putExtra(DownloadService.EXTRA_ITEMS, array.toString())
        }
        getApplication<Application>().startService(intent)
        _uiState.update {
            it.copy(
                activeTab = SonyEdgeTab.Transfers,
                selectedKeys = emptySet(),
                downloadMessage = "Queued ${items.size} imports.",
                downloadProgress = 0,
                downloadTotal = items.size.coerceAtLeast(1),
                downloadSuccess = 0,
                downloadFailed = 0,
                downloadState = DownloadService.STATE_STARTED,
                downloadBytesDone = 0,
                downloadBytesTotal = 0,
                downloadSpeedBps = 0,
                downloadEtaSeconds = 0,
                failedItems = emptyList(),
                transferEvents = listOf("Queued ${items.size} imports.")
            )
        }
    }

    fun onDownloadProgress(intent: Intent) {
        val message = intent.getStringExtra(DownloadService.EXTRA_MESSAGE).orEmpty()
        val state = intent.getStringExtra(DownloadService.EXTRA_STATE).orEmpty()
        val total = intent.getIntExtra(DownloadService.EXTRA_TOTAL, 1).coerceAtLeast(1)
        val index = intent.getIntExtra(DownloadService.EXTRA_INDEX, 0).coerceAtLeast(0)
        val success = intent.getIntExtra(DownloadService.EXTRA_SUCCESS, 0).coerceAtLeast(0)
        val failed = intent.getIntExtra(DownloadService.EXTRA_FAILED, 0).coerceAtLeast(0)
        val failedJson = intent.getStringExtra(DownloadService.EXTRA_ITEM_JSON).orEmpty()
        val bytesDone = intent.getLongExtra(DownloadService.EXTRA_BYTES_DONE, 0).coerceAtLeast(0)
        val bytesTotal = intent.getLongExtra(DownloadService.EXTRA_BYTES_TOTAL, 0).coerceAtLeast(0)
        val speedBps = intent.getLongExtra(DownloadService.EXTRA_SPEED_BPS, 0).coerceAtLeast(0)
        val etaSeconds = intent.getLongExtra(DownloadService.EXTRA_ETA_SECONDS, 0).coerceAtLeast(0)
        _uiState.update { current ->
            val completed = (success + failed).coerceIn(0, total)
            val events = if (state in eventStates && message.isNotBlank()) {
                (listOf(message) + current.transferEvents).take(80)
            } else {
                current.transferEvents
            }
            val failedItems = if (state == DownloadService.STATE_FILE_FAILED && failedJson.isNotBlank()) {
                (current.failedItems + CameraContentItem.fromJson(JSONObject(failedJson))).distinctBy { itemKey(it) }
            } else {
                current.failedItems
            }
            current.copy(
                activeTab = if (state == DownloadService.STATE_STARTED) SonyEdgeTab.Transfers else current.activeTab,
                downloadMessage = message.ifBlank { current.downloadMessage },
                downloadProgress = completed,
                downloadTotal = total,
                downloadSuccess = success,
                downloadFailed = failed,
                downloadState = state,
                downloadBytesDone = bytesDone,
                downloadBytesTotal = bytesTotal,
                downloadSpeedBps = speedBps,
                downloadEtaSeconds = etaSeconds,
                transferEvents = events,
                failedItems = failedItems
            )
        }
    }

    fun clearLogs() {
        _uiState.update { it.copy(logs = emptyList()) }
    }

    private fun setLoading(message: String, tab: SonyEdgeTab) {
        _uiState.update {
            it.copy(
                activeTab = tab,
                connectionState = ConnectionState.Searching,
                status = message,
                errorMessage = null,
                loading = true
            )
        }
    }

    private fun setError(message: String, tab: SonyEdgeTab) {
        addLog(message)
        _uiState.update {
            it.copy(
                activeTab = tab,
                connectionState = ConnectionState.Error,
                status = message,
                errorMessage = message,
                loading = false
            )
        }
    }

    private fun addLog(message: String) {
        if (message.isBlank()) return
        _uiState.update { it.copy(logs = (listOf(message) + it.logs).take(160)) }
    }

    companion object {
        private val eventStates = setOf(
            DownloadService.STATE_FILE_DONE,
            DownloadService.STATE_FILE_FAILED,
            DownloadService.STATE_DONE,
            DownloadService.STATE_FATAL,
            DownloadService.STATE_CANCELLED
        )
    }
}

class SonyCameraRepository(private val context: Context) {
    suspend fun connect(log: (String) -> Unit): DmsServiceInfo = withContext(Dispatchers.IO) {
        bindProcessToWifi(context)
        val builder = StringBuilder()
        val hosts = LinkedHashSet<String>()
        wifiGateway(context)?.let { hosts.add(it) }
        hosts.add("192.168.122.1")
        val service = DiscoveryClient(context).discoverFirstDmsService(hosts, builder)
            ?: throw IllegalStateException("No camera DMS service found")
        builder.lines().filter { it.isNotBlank() }.takeLast(12).forEach(log)
        service
    }

    suspend fun browse(service: DmsServiceInfo, folderId: String, log: (String) -> Unit): DmsBrowseResult =
        withContext(Dispatchers.IO) {
            bindProcessToWifi(context)
            val builder = StringBuilder()
            val result = DmsContentClient(service, builder).browseDirectChildren(folderId)
            builder.lines().filter { it.isNotBlank() }.takeLast(8).forEach(log)
            result
        }

    fun cachedService(): DmsServiceInfo = DmsServiceInfo.cached(loadCachedEndpoint())

    fun saveCachedEndpoint(url: String?) {
        if (!url.isNullOrBlank()) {
            context.getSharedPreferences("sonyedge", Context.MODE_PRIVATE)
                .edit()
                .putString("dms_control_url", url)
                .apply()
        }
    }

    private fun loadCachedEndpoint(): String =
        context.getSharedPreferences("sonyedge", Context.MODE_PRIVATE)
            .getString("dms_control_url", "")
            .orEmpty()
}

fun itemKey(item: CameraContentItem): String =
    if (!item.uri.isNullOrBlank()) item.uri else item.bestDownloadUrl() ?: item.title

fun cleanTitle(title: String?, fallback: String): String =
    title?.takeIf { it.isNotBlank() } ?: fallback

fun browseTabFor(state: SonyEdgeUiState): SonyEdgeTab =
    browseTabFor(state.folders, state.photos)

fun browseTabFor(folders: List<DmsContainerItem>, photos: List<CameraContentItem>): SonyEdgeTab =
    if (photos.isEmpty() && folders.isNotEmpty()) SonyEdgeTab.Camera else SonyEdgeTab.Library

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "Original"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1) String.format("%.1f MB", mb) else "${(bytes / 1024).coerceAtLeast(1)} KB"
}

fun bindProcessToWifi(context: Context) {
    if (Build.VERSION.SDK_INT < 23) return
    try {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifiNetwork = manager.allNetworks.firstOrNull { network ->
            val capabilities = manager.getNetworkCapabilities(network)
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        if (wifiNetwork != null) {
            manager.bindProcessToNetwork(wifiNetwork)
        }
    } catch (_: Exception) {
    }
}

private fun wifiGateway(context: Context): String? =
    try {
        val manager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val gateway = manager.dhcpInfo?.gateway ?: 0
        if (gateway == 0) null else String.format(
            "%d.%d.%d.%d",
            gateway and 0xff,
            gateway shr 8 and 0xff,
            gateway shr 16 and 0xff,
            gateway shr 24 and 0xff
        )
    } catch (_: Exception) {
        null
    }
