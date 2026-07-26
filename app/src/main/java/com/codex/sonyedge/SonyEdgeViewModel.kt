package com.codex.sonyedge

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
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
import com.codex.sonyedge.ui.formatDateFolderTitle
import com.codex.sonyedge.ui.image.CameraImageLoader
import com.codex.sonyedge.ui.theme.ThemeMode
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.URI
import java.net.Socket
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

enum class CameraConnectPhase {
    Disconnected,
    WaitingForCredentials,
    RequestingWifi,
    VerifyingCamera,
    PreparingLibrary,
    Connected,
    Failed
}

data class RememberedCamera(
    val ssid: String,
    val modelName: String?
)

data class ConnectedCameraInfo(
    val ssid: String,
    val modelName: String,
    val friendlyName: String,
    val host: String
)

data class FolderState(val id: String, val title: String)

/** 传输任务的终态类型（传输页历史卡）。 */
enum class TransferOutcome { Done, PartialFail, Interrupted }

/** 一条已结束的传输任务记录（仅会话内存，UI 展示用）。 */
data class TransferRecord(
    val id: Long,
    val title: String,
    val total: Int,
    val success: Int,
    val failed: Int,
    val bytes: Long,
    val outcome: TransferOutcome
)

private data class CachedFolder(
    val id: String,
    val title: String,
    val folders: List<DmsContainerItem>,
    val photos: List<CameraContentItem>
)

data class SonyEdgeUiState(
    val activeTab: SonyEdgeTab = SonyEdgeTab.Library,
    val connectionState: ConnectionState = ConnectionState.Idle,
    val connectPhase: CameraConnectPhase = CameraConnectPhase.Disconnected,
    val rememberedCamera: RememberedCamera? = null,
    val connectedCamera: ConnectedCameraInfo? = null,
    val credentialsDialogVisible: Boolean = false,
    val credentialsSsidPrefill: String = "",
    val manualCredentialsAvailable: Boolean = false,
    val connectionHomeVisible: Boolean = false,
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
    val cameraSelectionStatus: String = "Camera-selected photos are detected automatically after connection.",
    val cameraSelectionReceiving: Boolean = false,
    val cameraSelectionCleanupInProgress: Boolean = false,
    // —— UI 层扩展状态 ——
    val themeMode: ThemeMode = ThemeMode.System,
    val receiveCameraSelectionEnabled: Boolean = true,
    val gridHintDismissed: Boolean = false,
    /** 多选模式（可为 0 张选中） */
    val selectionMode: Boolean = false,
    /** 当前批次标题（如「7月24日」/「相机选择」） */
    val downloadBatchTitle: String = "",
    /** 当前正在下载的文件名 */
    val downloadCurrentFile: String = "",
    /** 已结束批次的历史记录（最新在前） */
    val transferHistory: List<TransferRecord> = emptyList(),
    /** 一次性 Snackbar 消息 */
    val transientMessage: String? = null,
    /** 连接失败发生在第几步（0=Wi-Fi 1=验证 2=准备） */
    val connectFailedStep: Int = 1,
    /** 日期目录 id → 前 3 张内容（时间线胶片条，仅来自已缓存目录） */
    val folderPreviews: Map<String, List<CameraContentItem>> = emptyMap()
) {
    /** 是否位于某个日期目录内（照片网格层） */
    val insideDateFolder: Boolean
        get() = folderStack.lastOrNull()?.title?.equals("Date", ignoreCase = true) == true ||
            photos.isNotEmpty()

    val isConnectingPhase: Boolean
        get() = connectPhase in setOf(
            CameraConnectPhase.RequestingWifi,
            CameraConnectPhase.VerifyingCamera,
            CameraConnectPhase.PreparingLibrary,
            CameraConnectPhase.Failed
        )

    val shouldHandleBack: Boolean
        get() = previewIndex != null ||
            selectionMode ||
            selectedKeys.isNotEmpty() ||
            (insideDateFolder && folderStack.isNotEmpty()) ||
            isConnectingPhase ||
            activeTab == SonyEdgeTab.Transfers ||
            activeTab == SonyEdgeTab.Settings
}

class SonyEdgeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SonyCameraRepository(application)
    private val wifiProfileStore = CameraWifiProfileStore(application)
    private val wifiConnector = CameraWifiConnector(application)
    private val uiPrefs = UiPrefsStore(application)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _uiState = MutableStateFlow(
        SonyEdgeUiState(
            rememberedCamera = wifiProfileStore.load()?.let {
                RememberedCamera(ssid = it.ssid, modelName = it.cameraModel)
            },
            themeMode = uiPrefs.themeMode,
            receiveCameraSelectionEnabled = uiPrefs.receiveCameraSelection,
            gridHintDismissed = uiPrefs.gridHintDismissed
        )
    )
    val uiState: StateFlow<SonyEdgeUiState> = _uiState

    private var activeSession: SonyCameraSession? = null
    private var activeXPushSession: CameraSelectionSession? = null
    private val pendingXPushGuard = AtomicReference<XPushSessionGuard?>()
    private val cameraSelectionRequest = AtomicReference<Any?>()
    private val cleared = AtomicBoolean(false)
    private val folderCache = linkedMapOf<String, CachedFolder>()
    private var browseRequestId = 0
    private var connectionRequestId = 0
    private var previewPrefetchId = 0
    private var pendingCameraSsid: String? = null
    private var pendingCameraPassword: String? = null
    private var pendingCameraIdentity: String? = null
    private var pendingCameraModel: String? = null
    private var pendingConnectionFromQr = false
    private var autoBrowseAfterConnection = false
    private var awaitingCameraSelectionDisconnect = false

    override fun onCleared() {
        cleared.set(true)
        scope.cancel()
        pendingCameraPassword = null
        pendingCameraIdentity = null
        pendingCameraModel = null
        pendingConnectionFromQr = false
        cameraSelectionRequest.set(null)
        pendingXPushGuard.getAndSet(null)?.let { abortXPushGuard(it, "ViewModel cleared") }
        // Do not close wifiConnector here: its NetworkSpecifier request must outlive this
        // ViewModel while the foreground DownloadService is still using the camera network.
        super.onCleared()
    }

    fun selectTab(tab: SonyEdgeTab) {
        _uiState.update { state ->
            val target = if (tab == SonyEdgeTab.Library) browseTabFor(state) else tab
            state.copy(activeTab = target)
        }
    }

    fun handleBack() {
        backFromCameraContent()
    }

    /**
     * 系统返回优先级：关预览 → 退多选清空选择 → 网格回时间线 →
     * 连接中取消 → 传输/设置回浏览 →（未处理时退出应用）。
     */
    fun backFromCameraContent() {
        val state = _uiState.value
        when {
            state.previewIndex != null -> closePreview()
            state.selectionMode || state.selectedKeys.isNotEmpty() -> exitSelectionMode()
            state.insideDateFolder && state.folderStack.isNotEmpty() -> restorePreviousFolder(state)
            state.isConnectingPhase -> cancelCameraConnection()
            state.activeTab == SonyEdgeTab.Transfers ||
                state.activeTab == SonyEdgeTab.Settings -> selectTab(SonyEdgeTab.Library)
        }
    }

    // —— UI 偏好 ——

    fun setThemeMode(mode: ThemeMode) {
        uiPrefs.themeMode = mode
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun setReceiveCameraSelection(enabled: Boolean) {
        uiPrefs.receiveCameraSelection = enabled
        _uiState.update { it.copy(receiveCameraSelectionEnabled = enabled) }
    }

    fun dismissGridHint() {
        uiPrefs.gridHintDismissed = true
        _uiState.update { it.copy(gridHintDismissed = true) }
    }

    fun enterSelectionMode() {
        _uiState.update { it.copy(selectionMode = true) }
    }

    fun exitSelectionMode() {
        _uiState.update { it.copy(selectionMode = false, selectedKeys = emptySet()) }
    }

    fun clearThumbnailCache() {
        scope.launch {
            CameraImageLoader.clearAllCaches(getApplication())
            _uiState.update { it.copy(transientMessage = "缩略图缓存已清除") }
        }
    }

    fun consumeTransientMessage() {
        _uiState.update { it.copy(transientMessage = null) }
    }

    fun connectCameraWifi() {
        if (blockConnectionChangeWhileTransferActive()) return
        val profile = wifiProfileStore.load()
        if (profile == null) {
            _uiState.update {
                it.copy(
                    activeTab = SonyEdgeTab.Library,
                    connectPhase = CameraConnectPhase.Disconnected,
                    credentialsDialogVisible = false,
                    credentialsSsidPrefill = "",
                    manualCredentialsAvailable = true,
                    connectionHomeVisible = false,
                    status = "Scan the camera QR code to connect.",
                    errorMessage = "No saved camera. Scan its QR code or use manual Wi-Fi entry.",
                    loading = false
                )
            }
            return
        }
        _uiState.update {
            it.copy(rememberedCamera = RememberedCamera(profile.ssid, profile.cameraModel))
        }
        requestCameraWifi(
            ssid = profile.ssid,
            password = profile.password,
            autoBrowse = false,
            cameraIdentity = profile.cameraIdentity,
            cameraModel = profile.cameraModel,
        )
    }

    fun submitCameraQrCode(rawValue: String?) {
        if (blockConnectionChangeWhileTransferActive()) return
        val payload = runCatching {
            require(!rawValue.isNullOrBlank()) { "No QR code was captured." }
            SonyWifiQrParser.parse(rawValue)
        }.getOrElse { error ->
            pendingConnectionFromQr = false
            _uiState.update {
                it.copy(
                    activeTab = SonyEdgeTab.Library,
                    connectionState = ConnectionState.Error,
                    connectPhase = CameraConnectPhase.Failed,
                    credentialsDialogVisible = false,
                    manualCredentialsAvailable = true,
                    connectionHomeVisible = false,
                    status = "Camera QR scan failed.",
                    errorMessage = error.message ?: "Unable to read the Sony camera QR code.",
                    loading = false,
                )
            }
            return
        }

        addLog("Sony camera QR parsed for ${payload.cameraModel}; identity=${payload.cameraIdentity}")
        _uiState.update {
            it.copy(
                manualCredentialsAvailable = false,
                errorMessage = null,
                status = "Sony camera QR code recognized.",
            )
        }
        requestCameraWifi(
            ssid = payload.ssid,
            password = payload.password,
            autoBrowse = false,
            cameraIdentity = payload.cameraIdentity,
            cameraModel = payload.cameraModel,
            fromQr = true,
        )
    }

    fun addCamera() {
        if (blockConnectionChangeWhileTransferActive()) return
        pendingCameraSsid = null
        pendingCameraPassword = null
        pendingCameraIdentity = null
        pendingCameraModel = null
        pendingConnectionFromQr = false
        _uiState.update {
            it.copy(
                activeTab = SonyEdgeTab.Library,
                connectPhase = CameraConnectPhase.WaitingForCredentials,
                credentialsDialogVisible = true,
                credentialsSsidPrefill = "",
                manualCredentialsAvailable = true,
                errorMessage = null,
                loading = false
            )
        }
    }

    fun submitCameraCredentials(ssid: String, password: String) {
        if (blockConnectionChangeWhileTransferActive()) return
        val normalizedSsid = ssid.trim()
        if (normalizedSsid.isEmpty()) {
            _uiState.update {
                it.copy(
                    connectPhase = CameraConnectPhase.WaitingForCredentials,
                    credentialsDialogVisible = true,
                    credentialsSsidPrefill = ssid,
                    errorMessage = "Enter the camera Wi-Fi name.",
                    loading = false
                )
            }
            return
        }
        requestCameraWifi(
            ssid = normalizedSsid,
            password = password,
            autoBrowse = false,
        )
    }

    fun dismissCameraCredentials() {
        pendingCameraSsid = null
        pendingCameraPassword = null
        pendingCameraIdentity = null
        pendingCameraModel = null
        pendingConnectionFromQr = false
        val connected = activeSession != null
        _uiState.update {
            it.copy(
                connectPhase = if (connected) CameraConnectPhase.Connected else CameraConnectPhase.Disconnected,
                credentialsDialogVisible = false,
                credentialsSsidPrefill = "",
                connectionHomeVisible = connected || it.connectionHomeVisible,
                errorMessage = null,
                loading = false
            )
        }
    }

    fun connectCurrentWifi() {
        if (blockConnectionChangeWhileTransferActive()) return
        verifyCurrentWifi(autoBrowse = false)
    }

    fun cancelCameraConnection() {
        connectionRequestId++
        pendingCameraSsid = null
        pendingCameraPassword = null
        pendingCameraIdentity = null
        pendingCameraModel = null
        pendingConnectionFromQr = false
        autoBrowseAfterConnection = false
        val pendingGuard = clearCameraSession(abortPendingSelection = false)
        releaseCameraWifiAfterSelectionCleanup(pendingGuard, "Camera connection cancelled")
        _uiState.update {
            it.copy(
                activeTab = SonyEdgeTab.Library,
                connectionState = ConnectionState.Idle,
                connectPhase = CameraConnectPhase.Disconnected,
                connectedCamera = null,
                credentialsDialogVisible = false,
                credentialsSsidPrefill = "",
                connectionHomeVisible = false,
                status = "Camera connection cancelled.",
                errorMessage = null,
                loading = false
            )
        }
    }

    fun forgetCamera() {
        if (blockConnectionChangeWhileTransferActive()) return
        wifiProfileStore.clear()
        pendingCameraPassword = null
        pendingCameraIdentity = null
        pendingCameraModel = null
        pendingConnectionFromQr = false
        val connecting = _uiState.value.connectPhase in setOf(
            CameraConnectPhase.RequestingWifi,
            CameraConnectPhase.VerifyingCamera,
            CameraConnectPhase.PreparingLibrary
        )
        if (connecting) {
            cancelCameraConnection()
        }
        _uiState.update {
            it.copy(
                rememberedCamera = null,
                credentialsDialogVisible = false,
                status = if (connecting) "Saved camera removed." else it.status
            )
        }
    }

    fun browseCameraPhotos() {
        if (activeSession == null) {
            failCameraConnection("Connect to the camera Wi-Fi first.", releaseRequestedNetwork = false)
            return
        }
        _uiState.update { it.copy(connectionHomeVisible = false) }
        openFolder("0", "Camera", pushCurrent = false, autoOpenDateDirectory = true)
    }

    fun disconnectCamera() {
        if (isCameraTransferBusy(_uiState.value)) {
            _uiState.update {
                it.copy(
                    status = "Cancel or finish the active camera transfer before disconnecting.",
                    transientMessage = "请先取消或完成正在进行的传输"
                )
            }
            return
        }
        connectionRequestId++
        pendingCameraSsid = null
        pendingCameraPassword = null
        pendingCameraIdentity = null
        pendingCameraModel = null
        pendingConnectionFromQr = false
        autoBrowseAfterConnection = false
        val pendingGuard = clearCameraSession(abortPendingSelection = false)
        releaseCameraWifiAfterSelectionCleanup(pendingGuard, "Camera disconnected")
        _uiState.update {
            it.copy(
                activeTab = SonyEdgeTab.Library,
                connectionState = ConnectionState.Idle,
                connectPhase = CameraConnectPhase.Disconnected,
                connectedCamera = null,
                credentialsDialogVisible = false,
                credentialsSsidPrefill = "",
                connectionHomeVisible = false,
                status = "Camera disconnected.",
                errorMessage = null,
                loading = false
            )
        }
    }

    fun openCameraAlbums() {
        if (activeSession == null) {
            failCameraConnection("Connect to the camera Wi-Fi first.", releaseRequestedNetwork = false)
            return
        }
        _uiState.update { it.copy(connectionHomeVisible = false) }
        openFolder("0", "Camera", pushCurrent = false)
    }

    fun openImportsFromHome() {
        _uiState.update { it.copy(activeTab = SonyEdgeTab.Transfers) }
    }

    /** Compatibility entry point for the previous manually-connected, auto-browse flow. */
    fun connectAndBrowse() {
        verifyCurrentWifi(autoBrowse = true)
    }

    private fun requestCameraWifi(
        ssid: String,
        password: String,
        autoBrowse: Boolean,
        cameraIdentity: String? = null,
        cameraModel: String? = null,
        fromQr: Boolean = false,
    ) {
        val requestId = ++connectionRequestId
        clearCameraSession()
        pendingCameraSsid = ssid
        pendingCameraPassword = password
        pendingCameraIdentity = cameraIdentity
        pendingCameraModel = cameraModel
        pendingConnectionFromQr = fromQr
        autoBrowseAfterConnection = autoBrowse
        _uiState.update {
            it.copy(
                activeTab = SonyEdgeTab.Library,
                connectionState = ConnectionState.Searching,
                connectPhase = CameraConnectPhase.RequestingWifi,
                connectedCamera = null,
                credentialsDialogVisible = false,
                credentialsSsidPrefill = "",
                manualCredentialsAvailable = false,
                connectionHomeVisible = true,
                status = "Connecting to camera Wi-Fi...",
                errorMessage = null,
                loading = true
            )
        }
        runCatching {
            wifiConnector.connect(ssid, password) { event ->
                handleCameraWifiEvent(requestId, event)
            }
        }.onFailure { error ->
            if (requestId == connectionRequestId) {
                failCameraConnection("Camera Wi-Fi request failed: ${error.message}")
            }
        }
    }

    private fun verifyCurrentWifi(autoBrowse: Boolean) {
        val requestId = ++connectionRequestId
        val profile = wifiProfileStore.load()
        pendingCameraSsid = currentWifiSsid()
            ?: profile?.ssid
            ?: "Camera Wi-Fi"
        pendingCameraPassword = null
        val matchingProfile = profile?.takeIf { it.ssid == pendingCameraSsid }
        pendingCameraIdentity = matchingProfile?.cameraIdentity
        pendingCameraModel = matchingProfile?.cameraModel
        pendingConnectionFromQr = false
        autoBrowseAfterConnection = autoBrowse
        clearCameraSession()
        verifyCameraAndPrepare(requestId)
    }

    private fun handleCameraWifiEvent(requestId: Int, event: CameraWifiConnector.Event) {
        if (requestId != connectionRequestId) return
        when (event) {
            is CameraWifiConnector.Event.Connected -> verifyCameraAndPrepare(requestId)
            is CameraWifiConnector.Event.Unavailable -> {
                val detail = event.cause?.message?.takeIf { it.isNotBlank() }
                failCameraConnection(
                    if (detail == null) "Camera Wi-Fi is unavailable." else "Camera Wi-Fi is unavailable: $detail",
                    releaseRequestedNetwork = false
                )
            }
            CameraWifiConnector.Event.Lost -> {
                if (
                    awaitingCameraSelectionDisconnect &&
                    isCameraSelectionTransferTerminal(_uiState.value)
                ) {
                    finishCameraSelectionConnection()
                } else {
                    failCameraConnection("Camera Wi-Fi connection was lost.", releaseRequestedNetwork = false)
                }
            }
        }
    }

    private fun finishCameraSelectionConnection() {
        connectionRequestId++
        pendingCameraSsid = null
        pendingCameraPassword = null
        pendingCameraIdentity = null
        pendingCameraModel = null
        pendingConnectionFromQr = false
        autoBrowseAfterConnection = false
        clearCameraSession(abortPendingSelection = false)
        addLog("Camera-selected transfer finished; camera Wi-Fi closed normally.")
        _uiState.update {
            it.copy(
                activeTab = SonyEdgeTab.Transfers,
                connectionState = ConnectionState.Idle,
                connectPhase = CameraConnectPhase.Disconnected,
                connectedCamera = null,
                credentialsDialogVisible = false,
                credentialsSsidPrefill = "",
                connectionHomeVisible = false,
                status = "Camera-selected transfer complete.",
                errorMessage = null,
                loading = false
            )
        }
    }

    private fun verifyCameraAndPrepare(requestId: Int) {
        _uiState.update {
            it.copy(
                activeTab = SonyEdgeTab.Library,
                connectionState = ConnectionState.Searching,
                connectPhase = CameraConnectPhase.VerifyingCamera,
                connectedCamera = null,
                credentialsDialogVisible = false,
                credentialsSsidPrefill = "",
                connectionHomeVisible = true,
                status = "Verifying Sony camera services...",
                errorMessage = null,
                loading = true
            )
        }
        scope.launch {
            val session = runCatching { repository.connect { addLog(it) } }
                .getOrElse { error ->
                    if (requestId == connectionRequestId) {
                        failCameraConnection("Camera verification failed: ${error.message}")
                    }
                    return@launch
                }
            if (requestId != connectionRequestId) return@launch

            val expectedIdentity = pendingCameraIdentity
            val discoveredIdentity = SonyWifiQrParser.cameraIdentityFromUdn(session.device.udn)
            if (
                expectedIdentity != null &&
                discoveredIdentity != null &&
                expectedIdentity != discoveredIdentity
            ) {
                addLog(
                    "Connected Sony camera identity did not match the scanned QR code " +
                        "(expected=$expectedIdentity, discovered=$discoveredIdentity)"
                )
                failCameraConnection("The connected Sony camera does not match the scanned QR code.")
                return@launch
            }
            if (expectedIdentity != null) {
                addLog(
                    if (discoveredIdentity == null) {
                        "Sony camera identity was not advertised; continuing with service verification"
                    } else {
                        "Sony camera identity verified: $discoveredIdentity"
                    }
                )
            }

            activeSession = session
            addLog("Connected: ${session.device.friendlyName} ${session.device.modelName}")
            if (supportsCameraSelection(session)) {
                val rootFolder = CachedFolder("0", "Camera", emptyList(), emptyList())
                finishCameraConnection(session, rootFolder)
                return@launch
            }
            _uiState.update {
                it.copy(
                    connectPhase = CameraConnectPhase.PreparingLibrary,
                    status = "Preparing camera library..."
                )
            }
            val root = runCatching { repository.browse(session, "0") { addLog(it) } }
                .getOrElse { error ->
                    if (requestId == connectionRequestId) {
                        failCameraConnection("Preparing camera library failed: ${error.message}")
                    }
                    return@launch
                }
            if (requestId != connectionRequestId) return@launch

            val rootFolder = CachedFolder("0", "Camera", root.containers, root.items)
            folderCache["0"] = rootFolder
            finishCameraConnection(session, rootFolder)
        }
    }

    private fun finishCameraConnection(session: SonyCameraSession, rootFolder: CachedFolder) {
        val ssid = pendingCameraSsid?.takeIf { it.isNotBlank() }
            ?: wifiProfileStore.load()?.ssid
            ?: "Camera Wi-Fi"
        val modelName = pendingCameraModel?.takeIf { it.isNotBlank() }
            ?: resolveCameraModel(
                modelNumber = session.device.modelNumber,
                modelName = session.device.modelName,
                friendlyName = session.device.friendlyName,
                ssid = ssid
            )
        val friendlyName = session.device.friendlyName.ifBlank { modelName }
        val password = pendingCameraPassword
        val existingProfile = wifiProfileStore.load()
        val savedProfile = when {
            password != null -> CameraWifiProfileStore.CameraWifiProfile(
                cameraModel = modelName,
                ssid = ssid,
                password = password,
                cameraIdentity = pendingCameraIdentity ?: existingProfile?.cameraIdentity,
            )
            existingProfile != null -> existingProfile.copy(
                cameraModel = modelName,
                cameraIdentity = pendingCameraIdentity ?: existingProfile.cameraIdentity,
            )
            else -> null
        }
        if (savedProfile != null) {
            runCatching { wifiProfileStore.save(savedProfile) }
                .onFailure { addLog("Unable to update saved camera profile: ${it.message}") }
        }
        pendingCameraPassword = null
        pendingCameraIdentity = null
        pendingCameraModel = null
        pendingConnectionFromQr = false

        val connectedInfo = ConnectedCameraInfo(
            ssid = ssid,
            modelName = modelName,
            friendlyName = friendlyName,
            host = cameraHost(session)
        )
        val shouldAutoReceiveSelection =
            supportsCameraSelection(session) && _uiState.value.receiveCameraSelectionEnabled
        autoBrowseAfterConnection = false
        _uiState.update {
            it.copy(
                activeTab = SonyEdgeTab.Library,
                connectionState = ConnectionState.Connected,
                connectPhase = CameraConnectPhase.Connected,
                rememberedCamera = savedProfile?.let { profile ->
                    RememberedCamera(profile.ssid, profile.cameraModel)
                } ?: it.rememberedCamera,
                connectedCamera = connectedInfo,
                credentialsDialogVisible = false,
                credentialsSsidPrefill = "",
                manualCredentialsAvailable = false,
                connectionHomeVisible = false,
                status = "Connected to $modelName.",
                errorMessage = null,
                loading = false,
                currentFolderId = rootFolder.id,
                currentFolderTitle = rootFolder.title,
                folderStack = emptyList(),
                folders = emptyList(),
                photos = emptyList(),
                selectedKeys = emptySet(),
                selectionMode = false,
                previewIndex = null,
                cameraSelectionReceiving = false
            )
        }
        if (shouldAutoReceiveSelection) {
            addLog("XPushList advertised; receiving camera-selected photos automatically")
            receiveCameraSelection(autoTriggered = true)
        } else {
            // 连接成功后直接落到日期时间线（autoNextDateFolder 停在 Date 层）
            openFolder("0", "Camera", pushCurrent = false, autoOpenDateDirectory = true)
        }
    }

    private fun failCameraConnection(message: String, releaseRequestedNetwork: Boolean = true) {
        val failedStep = when (_uiState.value.connectPhase) {
            CameraConnectPhase.RequestingWifi -> 0
            CameraConnectPhase.VerifyingCamera -> 1
            CameraConnectPhase.PreparingLibrary -> 2
            else -> 1
        }
        val exposeManualFallback = pendingConnectionFromQr
        connectionRequestId++
        pendingCameraSsid = null
        pendingCameraPassword = null
        pendingCameraIdentity = null
        pendingCameraModel = null
        pendingConnectionFromQr = false
        autoBrowseAfterConnection = false
        val pendingGuard = clearCameraSession(abortPendingSelection = !releaseRequestedNetwork)
        if (releaseRequestedNetwork) {
            releaseCameraWifiAfterSelectionCleanup(pendingGuard, "Camera connection failed")
        }
        addLog(message)
        _uiState.update {
            it.copy(
                activeTab = SonyEdgeTab.Library,
                connectionState = ConnectionState.Error,
                connectPhase = CameraConnectPhase.Failed,
                connectedCamera = null,
                credentialsDialogVisible = false,
                credentialsSsidPrefill = "",
                manualCredentialsAvailable = it.manualCredentialsAvailable || exposeManualFallback,
                connectionHomeVisible = false,
                status = message,
                errorMessage = message,
                loading = false,
                connectFailedStep = failedStep
            )
        }
    }

    private fun clearCameraSession(abortPendingSelection: Boolean = true): XPushSessionGuard? {
        activeSession = null
        activeXPushSession = null
        awaitingCameraSelectionDisconnect = false
        cameraSelectionRequest.set(null)
        val pendingGuard = pendingXPushGuard.getAndSet(null)
        if (abortPendingSelection) {
            pendingGuard?.let { abortXPushGuard(it, "Camera connection reset") }
        }
        browseRequestId++
        previewPrefetchId++
        folderCache.clear()
        _uiState.update {
            it.copy(
                currentFolderId = "0",
                currentFolderTitle = "Camera",
                folderStack = emptyList(),
                folders = emptyList(),
                photos = emptyList(),
                selectedKeys = emptySet(),
                selectionMode = false,
                previewIndex = null,
                cameraSelectionReceiving = false,
                folderPreviews = emptyMap()
            )
        }
        return pendingGuard
    }

    private fun releaseCameraWifiAfterSelectionCleanup(
        guard: XPushSessionGuard?,
        reason: String
    ) {
        if (guard == null) {
            wifiConnector.cancel()
            return
        }
        _uiState.update { it.copy(cameraSelectionCleanupInProgress = true) }
        XPushNetworkReleaseCoordinator.schedule(
            guard = guard,
            reason = reason,
            log = { message -> Log.w("SonyEdge-XPush", message) }
        ) {
            wifiConnector.cancel()
            _uiState.update {
                it.copy(cameraSelectionCleanupInProgress = false)
            }
        }
    }

    private fun blockConnectionChangeWhileTransferActive(): Boolean {
        val state = _uiState.value
        if (!isCameraTransferBusy(state)) return false
        _uiState.update {
            it.copy(
                status = if (state.cameraSelectionCleanupInProgress) {
                    "Finishing the previous camera transfer session..."
                } else {
                    "Cancel or finish the active camera transfer first."
                },
                transientMessage = if (state.cameraSelectionCleanupInProgress) {
                    "正在结束上一个传输会话…"
                } else {
                    "请先取消或完成正在进行的传输"
                }
            )
        }
        return true
    }

    @Suppress("DEPRECATION")
    private fun currentWifiSsid(): String? {
        val wifiManager = getApplication<Application>()
            .applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return null
        return wifiManager.connectionInfo?.ssid
            ?.takeUnless { it == WifiManager.UNKNOWN_SSID }
            ?.removePrefix("\"")
            ?.removeSuffix("\"")
            ?.takeIf { it.isNotBlank() }
    }

    private fun cameraHost(session: SonyCameraSession): String {
        val candidates = listOf(
            session.device.location,
            session.device.urlBase,
            session.dmsService?.controlUrl.orEmpty()
        )
        return candidates.firstNotNullOfOrNull { value ->
            runCatching { URI(value).host }.getOrNull()?.takeIf { it.isNotBlank() }
        }.orEmpty()
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
        backFromCameraContent()
    }

    private fun restorePreviousFolder(state: SonyEdgeUiState) {
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
                connectionHomeVisible = false,
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
                _uiState.update {
                    it.copy(folderPreviews = it.folderPreviews + (id to result.items.take(3)))
                }
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
                        connectionHomeVisible = false,
                        status = "Loaded ${result.containers.size} folders and ${result.items.size} photos.",
                        errorMessage = null,
                        loading = false,
                        folders = result.containers,
                        photos = result.items
                    )
                }
                if (cleanTitle.equals("Date", ignoreCase = true) && result.containers.isNotEmpty()) {
                    prefetchDatePreviews(result.containers)
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

    /**
     * 日期时间线胶片条预取：对每个非空日期目录发一次「前 3 条」的 Browse 请求，
     * 串行低优先级执行；断开 / 会话变更 / 新一轮预取时自动失效。
     * 结果只写入 folderPreviews（不进 folderCache，避免污染完整浏览缓存）。
     */
    private fun prefetchDatePreviews(folders: List<DmsContainerItem>) {
        val session = activeSession ?: return
        if (folders.isEmpty()) return
        val prefetchId = ++previewPrefetchId
        scope.launch {
            for (folder in folders) {
                if (prefetchId != previewPrefetchId || activeSession !== session) return@launch
                if (folder.childCount == 0) continue
                if (_uiState.value.folderPreviews.containsKey(folder.id)) continue
                val items = runCatching {
                    repository.browseFolderPreview(session, folder.id, 3) { }
                }.getOrNull() ?: continue
                if (prefetchId != previewPrefetchId || activeSession !== session) return@launch
                _uiState.update {
                    it.copy(folderPreviews = it.folderPreviews + (folder.id to items.take(3)))
                }
            }
        }
    }

    private fun applyFolder(cached: CachedFolder, folderStack: List<FolderState>, status: String, loading: Boolean) {
        _uiState.update {
            it.copy(
                activeTab = browseTabFor(cached.folders, cached.photos),
                connectionState = ConnectionState.Connected,
                connectionHomeVisible = false,
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
        if (cached.title.equals("Date", ignoreCase = true) && cached.folders.isNotEmpty()) {
            prefetchDatePreviews(cached.folders)
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
        receiveCameraSelection(autoTriggered = false)
    }

    private fun receiveCameraSelection(autoTriggered: Boolean) {
        val session = activeSession
        if (session == null) {
            setError("Connect to the camera Wi-Fi first.", SonyEdgeTab.Settings)
            return
        }
        val requestToken = Any()
        if (!cameraSelectionRequest.compareAndSet(null, requestToken)) {
            addLog("Camera selection receive is already active")
            return
        }
        val requestId = connectionRequestId
        val requestGuard = AtomicReference<XPushSessionGuard?>()
        _uiState.update {
            it.copy(
                activeTab = if (autoTriggered) SonyEdgeTab.Library else SonyEdgeTab.Settings,
                connectionState = ConnectionState.Connected,
                connectionHomeVisible = false,
                status = "Waiting for the camera-selected photos...",
                errorMessage = null,
                loading = true,
                cameraSelectionReceiving = true,
                cameraSelectionStatus = "Reading the camera-selected transfer list..."
            )
        }
        scope.launch {
            val result = runCatching {
                repository.startCameraSelection(
                    session,
                    { addLog(it) },
                    { guard ->
                        if (requestId == connectionRequestId && activeSession === session) {
                            requestGuard.set(guard)
                            registerXPushGuard(guard)
                        } else {
                            abortXPushGuard(guard, "Camera connection changed during selection setup")
                        }
                    }
                )
            }
            result.onSuccess { selection ->
                if (requestId != connectionRequestId || activeSession !== session) {
                    requestGuard.compareAndSet(selection.guard, null)
                    pendingXPushGuard.compareAndSet(selection.guard, null)
                    abortXPushGuard(selection.guard, "Stale camera selection result")
                    addLog("Ignored camera selection from an inactive connection")
                    return@onSuccess
                }
                activeXPushSession = selection
                _uiState.update {
                    it.copy(
                        connectionState = ConnectionState.Connected,
                        status = "Received ${selection.items.size} camera-selected items.",
                        errorMessage = null,
                        loading = false,
                        cameraSelectionStatus = "${selection.items.size} items received and selected for import.",
                        cameraSelectionReceiving = false
                    )
                }
                startDownload(selection.items)
            }
            result.onFailure { ex ->
                requestGuard.getAndSet(null)?.let { guard ->
                    pendingXPushGuard.compareAndSet(guard, null)
                    abortXPushGuard(guard, "Camera selection failed after setup cleanup")
                }
                if (requestId != connectionRequestId || activeSession !== session) {
                    addLog("Ignored camera selection failure from an inactive connection: ${ex.message}")
                    return@onFailure
                }
                val message = "Camera selection failed: ${ex.message}"
                if (autoTriggered) {
                    addLog(message)
                    folderCache.remove("0")
                    _uiState.update {
                        it.copy(
                            activeTab = SonyEdgeTab.Library,
                            connectionState = ConnectionState.Connected,
                            connectPhase = CameraConnectPhase.Connected,
                            connectionHomeVisible = false,
                            status = "Connected to the camera.",
                            errorMessage = null,
                            loading = false,
                            cameraSelectionStatus = message,
                            cameraSelectionReceiving = false
                        )
                    }
                    // 接收失败后尝试常规浏览，落到日期时间线
                    openFolder("0", "Camera", pushCurrent = false, autoOpenDateDirectory = true)
                } else {
                    _uiState.update {
                        it.copy(
                            cameraSelectionStatus = message,
                            cameraSelectionReceiving = false
                        )
                    }
                    setError(message, SonyEdgeTab.Settings)
                }
            }
            cameraSelectionRequest.compareAndSet(requestToken, null)
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
                awaitingCameraSelectionDisconnect = true
                if (!guard.handoffToService {
                        startDownloadService(intent)
                    }) {
                    throw IllegalStateException("Camera-selected transfer session is no longer active")
                }
                pendingXPushGuard.compareAndSet(guard, null)
                activeXPushSession = null
            } else {
                startDownloadService(intent)
            }
        } catch (ex: Exception) {
            if (usesCameraSelection) {
                awaitingCameraSelectionDisconnect = false
                val guard = selection!!.guard
                pendingXPushGuard.compareAndSet(guard, null)
                abortXPushGuard(guard, "Download service handoff failed")
            }
            setError("Unable to start import: ${ex.message}", SonyEdgeTab.Transfers)
            return
        }
        val batchTitle = if (usesCameraSelection) {
            "相机选择"
        } else {
            formatDateFolderTitle(_uiState.value.currentFolderTitle)
        }
        _uiState.update {
            it.copy(
                activeTab = SonyEdgeTab.Transfers,
                selectedKeys = emptySet(),
                selectionMode = false,
                downloadBatchTitle = batchTitle,
                downloadCurrentFile = "",
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

    private fun startDownloadService(intent: Intent) {
        val application = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            application.startForegroundService(intent)
        } else {
            application.startService(intent)
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
        val filename = intent.getStringExtra(DownloadService.EXTRA_FILENAME).orEmpty()
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
            // 批次进入终态时归档到历史（最新在前）
            val nowTerminal = state in terminalStates
            val wasTerminal = current.downloadState in terminalStates
            val history = if (nowTerminal && !wasTerminal && current.downloadTotal > 0) {
                val outcome = when {
                    state == DownloadService.STATE_DONE && failed == 0 -> TransferOutcome.Done
                    state == DownloadService.STATE_DONE -> TransferOutcome.PartialFail
                    else -> TransferOutcome.Interrupted
                }
                val record = TransferRecord(
                    id = System.currentTimeMillis(),
                    title = current.downloadBatchTitle.ifBlank { "导入任务" },
                    total = total,
                    success = success,
                    failed = failed,
                    bytes = maxOf(batchBytesDone, current.downloadBatchBytesDone),
                    outcome = outcome
                )
                (listOf(record) + current.transferHistory).take(20)
            } else {
                current.transferHistory
            }
            current.copy(
                transferHistory = history,
                downloadCurrentFile = if (filename.isNotBlank()) filename else current.downloadCurrentFile,
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
        private val terminalStates = setOf(
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
            val defaultHostDetected = runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(DEFAULT_CAMERA_HOST, DEFAULT_CAMERA_PORT), 900)
                }
                true
            }.getOrDefault(false)
            log(
                if (defaultHostDetected) {
                    "Sony camera host detected at $DEFAULT_CAMERA_HOST:$DEFAULT_CAMERA_PORT"
                } else {
                    "Default Sony host $DEFAULT_CAMERA_HOST:$DEFAULT_CAMERA_PORT was not detected; continuing with SSDP"
                }
            )
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

    private companion object {
        const val DEFAULT_CAMERA_HOST = "192.168.122.1"
        const val DEFAULT_CAMERA_PORT = 64321
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

    /** 单次 Browse 请求取目录前 [count] 条，供日期时间线胶片条预取。 */
    suspend fun browseFolderPreview(
        session: SonyCameraSession,
        folderId: String,
        count: Int,
        log: (String) -> Unit
    ): List<CameraContentItem> = withContext(Dispatchers.IO) {
        val dms = session.dmsService ?: return@withContext emptyList()
        withWifiNetwork(context, log) {
            val builder = StringBuilder()
            val result = DmsContentClient(dms, builder).browseFirstItems(folderId, count)
            result.items
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

internal fun resolveCameraModel(
    modelNumber: String,
    modelName: String,
    friendlyName: String,
    ssid: String
): String {
    val describedModel = modelNumber.ifBlank { modelName.ifBlank { friendlyName } }
    val ssidModel = ssid.substringAfterLast(':', missingDelimiterValue = "")
        .takeIf { it.startsWith("ILCE-", ignoreCase = true) || it.startsWith("DSC-", ignoreCase = true) }
    return describedModel
        .takeUnless { it.isBlank() || it.equals("SonyImagingDevice", ignoreCase = true) }
        ?: ssidModel
        ?: "Sony camera"
}

fun cleanTitle(title: String?, fallback: String): String =
    title?.takeIf { it.isNotBlank() } ?: fallback

fun browseTabFor(state: SonyEdgeUiState): SonyEdgeTab =
    browseTabFor(state.folders, state.photos)

fun supportsCameraSelection(session: SonyCameraSession): Boolean =
    session.dmsService != null &&
        session.device.services.any { service ->
            service.isXPushList() && service.controlUrl.isNotBlank()
        }

fun isDownloadTransferActive(state: SonyEdgeUiState): Boolean =
    state.downloadTotal > 0 &&
        state.downloadProgress < state.downloadTotal &&
        state.downloadState in setOf(
            DownloadService.STATE_STARTED,
            DownloadService.STATE_FILE_STARTED,
            DownloadService.STATE_FILE_PROGRESS,
            DownloadService.STATE_FILE_DONE,
            DownloadService.STATE_FILE_FAILED
        )

fun isCameraTransferBusy(state: SonyEdgeUiState): Boolean =
    state.cameraSelectionReceiving ||
        state.cameraSelectionCleanupInProgress ||
        isDownloadTransferActive(state)

fun isCameraSelectionTransferTerminal(state: SonyEdgeUiState): Boolean =
    state.downloadState in setOf(
        DownloadService.STATE_DONE,
        DownloadService.STATE_FATAL,
        DownloadService.STATE_CANCELLED
    )

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
