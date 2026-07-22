package com.codex.sonyedge

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

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
    val downloadBatchBytesDone: Long = 0,
    val downloadElapsedSeconds: Long = 0,
    val transferEvents: List<String> = emptyList(),
    val failedItems: List<CameraContentItem> = emptyList(),
    val cameraSelectionStatus: String = "Choose photos on the camera, then receive them here."
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

    private var activeSession: SonyCameraSession? = null
    private var activeXPushSession: CameraSelectionSession? = null
    private val pendingXPushGuard = AtomicReference<XPushSessionGuard?>()
    private val cleared = AtomicBoolean(false)
    private val folderCache = linkedMapOf<String, CachedFolder>()
    private var browseRequestId = 0

    override fun onCleared() {
        cleared.set(true)
        scope.cancel()
        pendingXPushGuard.getAndSet(null)?.let { abortXPushGuard(it, "ViewModel cleared") }
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
            }.onSuccess { session ->
                activeSession = session
                addLog("Connected: ${session.device.friendlyName} ${session.device.modelName}")
                _uiState.update { it.copy(connectionState = ConnectionState.Connected, errorMessage = null) }
                openFolder("0", "Camera", pushCurrent = false, autoOpenDateDirectory = true)
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

    private fun openFolder(
        id: String,
        title: String,
        pushCurrent: Boolean,
        preferCache: Boolean = true,
        autoOpenDateDirectory: Boolean = false
    ) {
        val session = activeSession
        if (session == null) {
            setError("Connect to the camera Wi-Fi first.", SonyEdgeTab.Camera)
            return
        }
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
                val autoTarget = autoNextDateFolder(cleanTitle(title, id), id, cached.folders, cached.photos, autoOpenDateDirectory)
                if (autoTarget != null) {
                    applyFolder(
                        cached = cached.copy(title = cleanTitle(title, id)),
                        folderStack = nextStack,
                        status = "Opening ${cleanTitle(autoTarget.title, autoTarget.id)}...",
                        loading = true
                    )
                    addLog("Auto-opening ${cleanTitle(autoTarget.title, autoTarget.id)}")
                    openFolder(autoTarget.id, autoTarget.title, pushCurrent = true, autoOpenDateDirectory = true)
                    return
                }
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
                repository.browse(session, id) { addLog(it) }
            }.onSuccess { result ->
                if (requestId != browseRequestId) return@launch
                val cached = CachedFolder(id, cleanTitle, result.containers, result.items)
                folderCache[id] = cached
                val autoTarget = autoNextDateFolder(cleanTitle, id, result.containers, result.items, autoOpenDateDirectory)
                if (autoTarget != null) {
                    addLog("Auto-opening ${cleanTitle(autoTarget.title, autoTarget.id)}")
                    openFolder(autoTarget.id, autoTarget.title, pushCurrent = true, autoOpenDateDirectory = true)
                    return@onSuccess
                }
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

    private fun autoNextDateFolder(
        currentTitle: String,
        currentId: String,
        folders: List<DmsContainerItem>,
        photos: List<CameraContentItem>,
        enabled: Boolean
    ): DmsContainerItem? {
        if (!enabled || photos.isNotEmpty() || folders.isEmpty()) return null
        val normalizedTitle = currentTitle.lowercase()
        return when {
            currentId == "0" || normalizedTitle == "camera" ->
                folders.firstOrNull { cleanTitle(it.title, it.id).equals("PhotoRoot", ignoreCase = true) }

            normalizedTitle == "photoroot" ->
                folders.firstOrNull { cleanTitle(it.title, it.id).equals("Date", ignoreCase = true) }

            else -> null
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

    fun receiveCameraSelection() {
        val session = activeSession
        if (session == null) {
            setError("Connect to the camera Wi-Fi first.", SonyEdgeTab.Settings)
            return
        }
        setLoading("Waiting for the camera selection...", SonyEdgeTab.Settings)
        _uiState.update { it.copy(cameraSelectionStatus = "Reading the camera-selected transfer list...") }
        scope.launch {
            runCatching {
                repository.startCameraSelection(
                    session,
                    { addLog(it) },
                    ::registerXPushGuard
                )
            }.onSuccess { selection ->
                activeXPushSession = selection
                browseRequestId++
                val title = "Camera selection"
                val cached = CachedFolder(selection.pushRoot, title, emptyList(), selection.items)
                folderCache[selection.pushRoot] = cached
                _uiState.update {
                    it.copy(
                        activeTab = SonyEdgeTab.Library,
                        connectionState = ConnectionState.Connected,
                        status = "Received ${selection.items.size} camera-selected items.",
                        errorMessage = null,
                        loading = false,
                        currentFolderId = selection.pushRoot,
                        currentFolderTitle = title,
                        folders = emptyList(),
                        photos = selection.items,
                        selectedKeys = selection.items.mapTo(linkedSetOf()) { item -> itemKey(item) },
                        previewIndex = null,
                        cameraSelectionStatus = "${selection.items.size} items received and selected for import."
                    )
                }
                startDownload(selection.items)
            }.onFailure { ex ->
                pendingXPushGuard.getAndSet(null)?.let {
                    abortXPushGuard(it, "Camera selection failed after setup cleanup")
                }
                _uiState.update { it.copy(cameraSelectionStatus = "Camera selection failed: ${ex.message}") }
                setError("Camera selection failed: ${ex.message}", SonyEdgeTab.Settings)
            }
        }
    }

    fun cancelDownloads() {
        getApplication<Application>().startService(Intent(getApplication(), DownloadService::class.java).apply {
            action = DownloadService.ACTION_CANCEL
        })
    }

    private fun startDownload(requestedItems: List<CameraContentItem>) {
        if (requestedItems.isEmpty()) return
        val selection = activeXPushSession
        val pushKeys = selection?.items?.mapTo(hashSetOf()) { itemKey(it) }.orEmpty()
        val usesCameraSelection = pushKeys.isNotEmpty() && requestedItems.any { itemKey(it) in pushKeys }
        val items = if (usesCameraSelection) selection!!.items else requestedItems
        val array = JSONArray()
        items.forEach { array.put(it.toJson()) }
        val intent = Intent(getApplication(), DownloadService::class.java).apply {
            action = DownloadService.ACTION_START
            putExtra(DownloadService.EXTRA_ITEMS, array.toString())
            if (usesCameraSelection) {
                putExtra(DownloadService.EXTRA_XPUSH_CONTROL_URL, selection!!.controlUrl)
                putExtra(DownloadService.EXTRA_XPUSH_SERVICE_TYPE, selection.serviceType)
                putExtra(DownloadService.EXTRA_XPUSH_TOTAL, selection.items.size)
            }
        }
        try {
            if (usesCameraSelection) {
                val guard = selection!!.guard
                if (!guard.handoffToService {
                        getApplication<Application>().startService(intent)
                    }) {
                    throw IllegalStateException("Camera-selected transfer session is no longer active")
                }
                pendingXPushGuard.compareAndSet(guard, null)
                activeXPushSession = null
            } else {
                getApplication<Application>().startService(intent)
            }
        } catch (ex: Exception) {
            if (usesCameraSelection) {
                val guard = selection!!.guard
                pendingXPushGuard.compareAndSet(guard, null)
                abortXPushGuard(guard, "Download service handoff failed")
            }
            setError("Unable to start import: ${ex.message}", SonyEdgeTab.Transfers)
            return
        }
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
                downloadBatchBytesDone = 0,
                downloadElapsedSeconds = 0,
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
        val batchBytesDone = intent.getLongExtra(DownloadService.EXTRA_BATCH_BYTES_DONE, 0).coerceAtLeast(0)
        val elapsedSeconds = intent.getLongExtra(DownloadService.EXTRA_ELAPSED_SECONDS, 0).coerceAtLeast(0)
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
                downloadBatchBytesDone = batchBytesDone,
                downloadElapsedSeconds = elapsedSeconds,
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
        Log.d("SonyEdge-Flow", message)
        _uiState.update { it.copy(logs = (listOf(message) + it.logs).take(160)) }
    }

    private fun registerXPushGuard(guard: XPushSessionGuard) {
        if (cleared.get()) {
            abortXPushGuard(guard, "ViewModel already cleared")
            return
        }
        pendingXPushGuard.getAndSet(guard)?.let { previous ->
            if (previous !== guard) abortXPushGuard(previous, "Superseded by a new camera selection")
        }
        if (cleared.get() && pendingXPushGuard.compareAndSet(guard, null)) {
            abortXPushGuard(guard, "ViewModel cleared during camera selection")
        }
    }

    private fun abortXPushGuard(guard: XPushSessionGuard, reason: String) {
        XPushCleanupCoordinator.schedule(guard, reason) { message ->
            Log.w("SonyEdge-XPush", message)
        }
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

data class SonyCameraSession(
    val device: SonyDeviceDescription,
    val dmsService: DmsServiceInfo?,
    val scalarClient: SonyRpcClient?
)

data class CameraSelectionSession(
    val client: XPushListClient,
    val pushRoot: String,
    val items: List<CameraContentItem>,
    val protocolLog: StringBuilder,
    val controlUrl: String,
    val serviceType: String,
    val guard: XPushSessionGuard
)

class SonyCameraRepository(private val context: Context) {
    private var discoveryClient = DiscoveryClient(context)

    suspend fun connect(log: (String) -> Unit): SonyCameraSession = withContext(Dispatchers.IO) {
        withWifiNetwork(context, log) {
            var devices = discoverAndLog(discoveryClient, log)
            var device = chooseDevice(devices)
                ?: throw IllegalStateException("No Sony camera services discovered over SSDP")
            var session = sessionFor(device, log)

            if (session.dmsService == null) {
                val rpc = session.scalarClient
                val cameraApis = rpc?.let { availableApis(it, "camera", log) }.orEmpty()
                if (rpc != null && cameraApis.contains("setCameraFunction")) {
                    log("ContentDirectory is absent; entering Contents Transfer through ScalarWebAPI")
                    rpc.setContentsTransferModeIfAvailable(cameraApis)
                    discoveryClient = DiscoveryClient(context)
                    devices = discoverAndLog(discoveryClient, log)
                    device = devices.firstOrNull { candidate ->
                        device.udn.isNotBlank() && candidate.udn == device.udn
                    } ?: chooseDevice(devices) ?: device
                    session = sessionFor(device, log)
                }
            }

            val scalarCanBrowse = session.scalarClient
                ?.let { availableApis(it, "avContent", log) }
                ?.contains("getContentList") == true
            if (session.dmsService == null && !scalarCanBrowse) {
                throw IllegalStateException("Camera exposes neither ContentDirectory nor Scalar getContentList")
            }
            session
        }
    }

    suspend fun browse(session: SonyCameraSession, folderId: String, log: (String) -> Unit): DmsBrowseResult =
        withContext(Dispatchers.IO) {
            withWifiNetwork(context, log) {
                val builder = StringBuilder()
                val dms = session.dmsService
                val result = if (dms != null) {
                    DmsContentClient(dms, builder).browseDirectChildren(folderId)
                } else {
                    if (folderId != "0") {
                        throw IllegalArgumentException("Scalar fallback exposes a flat camera library only")
                    }
                    val rpc = session.scalarClient
                        ?: throw IllegalStateException("ScalarWebAPI service is unavailable")
                    val matrix = CapabilityMatrix()
                    DmsBrowseResult("0").apply {
                        items.addAll(rpc.discoverContent(builder, matrix))
                        numberReturned = items.size
                        totalMatches = items.size
                    }
                }
                flushLog(builder, log)
                result
            }
        }

    suspend fun startCameraSelection(
        session: SonyCameraSession,
        log: (String) -> Unit,
        onStarted: (XPushSessionGuard) -> Unit
    ): CameraSelectionSession = withContext(Dispatchers.IO) {
        withWifiNetwork(context, log) {
            val xPush = session.device.services.firstOrNull { it.isXPushList() && it.controlUrl.isNotBlank() }
                ?: throw IllegalStateException("Camera does not advertise XPushList")
            val dms = session.dmsService
                ?: throw IllegalStateException("Camera-selected transfer requires ContentDirectory")
            val builder = StringBuilder()
            val client = XPushListClient(xPush.controlUrl, xPush.serviceType, builder)
            var guard: XPushSessionGuard? = null
            try {
                val start = client.transferStart()
                if (start.errorCode != 0) {
                    throw IllegalStateException("X_TransferStart returned ${start.errorCode}")
                }
                guard = XPushSessionGuard(context, client)
                onStarted(guard)
                val pushRoot = client.getPushRoot()
                if (pushRoot.isBlank()) {
                    throw IllegalStateException("X_GetPushRoot returned an empty object ID")
                }
                val result = DmsContentClient(dms, builder)
                    .browseDirectChildren(pushRoot)
                if (result.items.isEmpty()) {
                    throw IllegalStateException("Camera push list contains no downloadable items")
                }
                flushLog(builder, log)
                CameraSelectionSession(
                    client,
                    pushRoot,
                    result.items,
                    builder,
                    xPush.controlUrl,
                    xPush.serviceType,
                    guard
                )
            } catch (ex: Throwable) {
                flushLog(builder, log)
                guard?.let {
                    XPushCleanupCoordinator.schedule(it, "Camera selection setup failed", log)
                }
                throw ex
            }
        }
    }

    private fun discoverAndLog(client: DiscoveryClient, log: (String) -> Unit): List<SonyDeviceDescription> {
        val builder = StringBuilder()
        val devices = client.discoverDevices(builder)
        flushLog(builder, log)
        devices.forEach { device ->
            log("Device friendly=${device.friendlyName.ifBlank { "<unknown>" }} " +
                "model=${device.modelName.ifBlank { "<unknown>" }} udn=${device.udn.ifBlank { "<unknown>" }} " +
                "location=${device.location}")
            device.services.forEach { service ->
                log(
                    "Service type=${service.serviceType.ifBlank { service.apiType }} " +
                        "control=${service.controlUrl.ifBlank { "<none>" }} " +
                        "scpd=${service.scpdUrl.ifBlank { "<none>" }} " +
                        "event=${service.eventSubUrl.ifBlank { "<none>" }} " +
                        "actionList=${service.actionListUrl.ifBlank { "<none>" }}"
                )
            }
        }
        return devices
    }

    private fun chooseDevice(devices: List<SonyDeviceDescription>): SonyDeviceDescription? =
        devices.maxByOrNull { device: SonyDeviceDescription ->
            device.services.fold(0) { score: Int, service: SonyServiceDescription ->
                score + when {
                    service.isContentDirectory() -> 8
                    service.isXPushList() -> 4
                    service.isScalarWebApi() -> 2
                    else -> 0
                }
            }
        }

    private fun sessionFor(device: SonyDeviceDescription, log: (String) -> Unit): SonyCameraSession {
        val contentDirectory = device.services.firstOrNull {
            it.isContentDirectory() && it.controlUrl.isNotBlank()
        }?.let { DmsServiceInfo(device, it) }
        val scalar = runCatching { SonyRpcClient(device) }
            .onFailure { log("ScalarWebAPI unavailable: ${it.message}") }
            .getOrNull()
        return SonyCameraSession(device, contentDirectory, scalar)
    }

    private fun availableApis(client: SonyRpcClient, service: String, log: (String) -> Unit): List<String> {
        val builder = StringBuilder()
        val result = client.safeGetAvailableApiList(service, builder)
        flushLog(builder, log)
        return result
    }

    private fun flushLog(builder: StringBuilder, log: (String) -> Unit) {
        builder.lines().filter { it.isNotBlank() }.forEach(log)
        builder.setLength(0)
    }
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

private inline fun <T> withWifiNetwork(context: Context, log: (String) -> Unit, block: () -> T): T {
    var lease: CameraWifiBinding.Lease? = null
    try {
        lease = CameraWifiBinding.acquire(context)
        log("Acquired protocol Wi-Fi lease ${lease.network}")
        return block()
    } catch (ex: Exception) {
        log("Wi-Fi protocol task failed: ${ex.message ?: ex}")
        throw ex
    } finally {
        lease?.close()
        log("Released protocol Wi-Fi lease")
    }
}
