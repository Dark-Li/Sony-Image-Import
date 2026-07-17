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
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModelProvider

class ComposeMainActivity : ComponentActivity() {
    private lateinit var viewModel: SonyEdgeViewModel
    private var downloadReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[SonyEdgeViewModel::class.java]
        configureBars()
        requestNeededPermissions()
        registerDownloadReceiver()
        setContent {
            val state = viewModel.uiState.collectAsState().value
            BackHandler(enabled = state.shouldHandleBack) {
                viewModel.handleBack()
            }
            SonyEdgeApp(
                state = state,
                onTab = viewModel::selectTab,
                onConnect = viewModel::connectAndBrowse,
                onRefresh = viewModel::refresh,
                onRoot = viewModel::browseRoot,
                onBack = viewModel::goBack,
                onOpenFolder = viewModel::openFolder,
                onPreview = viewModel::showPreview,
                onClosePreview = viewModel::closePreview,
                onPreviewNext = viewModel::previewNext,
                onPreviewPage = viewModel::setPreviewIndex,
                onToggleSelection = viewModel::toggleSelection,
                onSelectAll = viewModel::selectAllVisible,
                onClearSelection = viewModel::clearSelection,
                onInvertSelection = viewModel::invertSelection,
                onDownloadSelected = viewModel::downloadSelected,
                onDownloadPreview = viewModel::downloadPreview,
                onCancelDownloads = viewModel::cancelDownloads,
                onRetryFailed = viewModel::retryFailed,
                onOpenGallery = ::openGallery,
                onClearLogs = viewModel::clearLogs
            )
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

    private fun configureBars() {
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = android.graphics.Color.rgb(250, 251, 253)
        window.navigationBarColor = android.graphics.Color.WHITE
        if (Build.VERSION.SDK_INT >= 23) {
            var flags = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            if (Build.VERSION.SDK_INT >= 26) {
                flags = flags or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
            window.decorView.systemUiVisibility = flags
        }
    }

    private fun requestNeededPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
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
            Toast.makeText(this, "No gallery app available", Toast.LENGTH_SHORT).show()
        }
    }
}
