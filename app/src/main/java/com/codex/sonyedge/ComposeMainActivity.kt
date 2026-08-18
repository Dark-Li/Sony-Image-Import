package com.codex.sonyedge

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import com.codex.sonyedge.ui.shell.SonyEdgeActions
import com.codex.sonyedge.ui.shell.SonyEdgeApp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class ComposeMainActivity : ComponentActivity() {
    private lateinit var viewModel: SonyEdgeViewModel
    private var downloadReceiver: BroadcastReceiver? = null
    private val qrScanner = registerForActivityResult(ScanContract()) { result ->
        if (::viewModel.isInitialized) {
            viewModel.submitCameraQrCode(result.contents)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[SonyEdgeViewModel::class.java]
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        requestNeededPermissions()
        registerDownloadReceiver()
        val actions = SonyEdgeActions(
            onTab = viewModel::selectTab,
            onConnectCameraWifi = {
                if (viewModel.uiState.value.rememberedCamera == null) {
                    launchCameraQrScanner()
                } else {
                    viewModel.connectCameraWifi()
                }
            },
            onScanCameraQr = ::launchCameraQrScanner,
            onSubmitCameraCredentials = viewModel::submitCameraCredentials,
            onDismissCameraCredentials = viewModel::dismissCameraCredentials,
            onConnectCurrentWifi = viewModel::connectCurrentWifi,
            onCancelCameraConnection = viewModel::cancelCameraConnection,
            onAddCamera = viewModel::addCamera,
            onDisconnectCamera = viewModel::disconnectCamera,
            onForgetCamera = viewModel::forgetCamera,
            onRefresh = viewModel::refresh,
            onBack = viewModel::backFromCameraContent,
            onOpenFolder = viewModel::openFolder,
            onPreview = viewModel::showPreview,
            onClosePreview = viewModel::closePreview,
            onPreviewNext = viewModel::previewNext,
            onPreviewPage = viewModel::setPreviewIndex,
            onGridScrollPosition = viewModel::setGridScrollPosition,
            onToggleSelection = viewModel::toggleSelection,
            onSelectAll = viewModel::selectAllVisible,
            onClearSelection = viewModel::clearSelection,
            onInvertSelection = viewModel::invertSelection,
            onEnterSelection = viewModel::enterSelectionMode,
            onExitSelection = viewModel::exitSelectionMode,
            onDownloadSelected = viewModel::downloadSelected,
            onDownloadPreview = viewModel::downloadPreview,
            onCancelDownloads = viewModel::cancelDownloads,
            onRetryFailed = viewModel::retryFailed,
            onReceiveCameraSelection = viewModel::receiveCameraSelection,
            onOpenGallery = ::openGallery,
            onClearLogs = viewModel::clearLogs,
            onExportLogs = ::exportDiagnosticLogs,
            onSetThemeMode = viewModel::setThemeMode,
            onSetReceiveSelection = viewModel::setReceiveCameraSelection,
            onDismissGridHint = viewModel::dismissGridHint,
            onClearThumbnailCache = viewModel::clearThumbnailCache,
            onConsumeTransientMessage = viewModel::consumeTransientMessage
        )
        setContent {
            val state = viewModel.uiState.collectAsState().value
            BackHandler(enabled = state.shouldHandleBack) {
                viewModel.handleBack()
            }
            SonyEdgeApp(state = state, actions = actions)
        }
    }

    override fun onDestroy() {
        downloadReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {
            }
        }
        super.onDestroy()
    }

    private fun launchCameraQrScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setCaptureActivity(SonyQrCaptureActivity::class.java)
            setPrompt("扫描索尼相机屏幕上的 Wi-Fi 二维码")
            setBeepEnabled(false)
            setBarcodeImageEnabled(false)
            setOrientationLocked(true)
        }
        qrScanner.launch(options)
    }

    private fun requestNeededPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        if (Build.VERSION.SDK_INT <= 32 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions += Manifest.permission.NEARBY_WIFI_DEVICES
        }
        if (permissions.isNotEmpty()) {
            requestPermissions(permissions.toTypedArray(), 10)
        }
    }

    private fun registerDownloadReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                viewModel.onDownloadProgress(intent)
            }
        }
        downloadReceiver = receiver
        val filter = IntentFilter(DownloadService.ACTION_PROGRESS)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    private fun openGallery() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                type = "image/*"
            }
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "没有可用的相册应用", Toast.LENGTH_SHORT).show()
        }
    }

    /** 高级 → 导出诊断日志：通过系统分享面板导出（协议术语仅存在于日志内容中）。 */
    private fun exportDiagnosticLogs() {
        val logs = viewModel.uiState.value.logs
        if (logs.isEmpty()) {
            Toast.makeText(this, "暂无诊断日志", Toast.LENGTH_SHORT).show()
            return
        }
        val body = buildString {
            appendLine("SonyEdge 诊断日志 v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine()
            logs.asReversed().forEach { appendLine(it) }
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "SonyEdge 诊断日志")
            putExtra(Intent.EXTRA_TEXT, body)
        }
        try {
            startActivity(Intent.createChooser(intent, "导出诊断日志"))
        } catch (_: Exception) {
            Toast.makeText(this, "无法导出日志", Toast.LENGTH_SHORT).show()
        }
    }
}
