package com.codex.sonyedge.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.sonyedge.CameraContentItem
import com.codex.sonyedge.DmsContainerItem
import com.codex.sonyedge.SonyEdgeTab
import com.codex.sonyedge.SonyEdgeUiState
import com.codex.sonyedge.ui.browse.ConnectingScreen
import com.codex.sonyedge.ui.browse.DateTimelineScreen
import com.codex.sonyedge.ui.browse.DisconnectedScreen
import com.codex.sonyedge.ui.browse.PhotoGridScreen
import com.codex.sonyedge.ui.image.CameraImageLoader
import com.codex.sonyedge.ui.preview.PhotoPreviewOverlay
import com.codex.sonyedge.ui.settings.SettingsScreen
import com.codex.sonyedge.ui.theme.Dimens
import com.codex.sonyedge.ui.theme.LocalReduceMotion
import com.codex.sonyedge.ui.theme.Motion
import com.codex.sonyedge.ui.theme.SonyEdgeAppTheme
import com.codex.sonyedge.ui.theme.SonyEdgeTheme
import com.codex.sonyedge.ui.theme.ThemeMode
import com.codex.sonyedge.ui.theme.resolveDarkTheme
import com.codex.sonyedge.ui.transfers.TransfersScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 全部 UI 回调集合，避免层层透传散装 lambda。 */
class SonyEdgeActions(
    val onTab: (SonyEdgeTab) -> Unit,
    val onConnectCameraWifi: () -> Unit,
    val onScanCameraQr: () -> Unit,
    val onSubmitCameraCredentials: (String, String) -> Unit,
    val onDismissCameraCredentials: () -> Unit,
    val onConnectCurrentWifi: () -> Unit,
    val onCancelCameraConnection: () -> Unit,
    val onAddCamera: () -> Unit,
    val onDisconnectCamera: () -> Unit,
    val onForgetCamera: () -> Unit,
    val onRefresh: () -> Unit,
    val onBack: () -> Unit,
    val onOpenFolder: (DmsContainerItem) -> Unit,
    val onPreview: (CameraContentItem) -> Unit,
    val onClosePreview: () -> Unit,
    val onPreviewNext: (Int) -> Unit,
    val onPreviewPage: (Int) -> Unit,
    val onToggleSelection: (CameraContentItem) -> Unit,
    val onSelectAll: () -> Unit,
    val onClearSelection: () -> Unit,
    val onInvertSelection: () -> Unit,
    val onEnterSelection: () -> Unit,
    val onExitSelection: () -> Unit,
    val onDownloadSelected: () -> Unit,
    val onDownloadPreview: () -> Unit,
    val onCancelDownloads: () -> Unit,
    val onRetryFailed: () -> Unit,
    val onReceiveCameraSelection: () -> Unit,
    val onOpenGallery: () -> Unit,
    val onClearLogs: () -> Unit,
    val onExportLogs: () -> Unit,
    val onSetThemeMode: (ThemeMode) -> Unit,
    val onSetReceiveSelection: (Boolean) -> Unit,
    val onDismissGridHint: () -> Unit,
    val onClearThumbnailCache: () -> Unit,
    val onConsumeTransientMessage: () -> Unit
)

private data class NavSpec(val tab: SonyEdgeTab, val label: String, val icon: ImageVector)

private val navItems = listOf(
    NavSpec(SonyEdgeTab.Library, "浏览", Icons.Default.PhotoLibrary),
    NavSpec(SonyEdgeTab.Transfers, "传输", Icons.Default.ImportExport),
    NavSpec(SonyEdgeTab.Settings, "设置", Icons.Default.Settings)
)

@Composable
fun SonyEdgeApp(state: SonyEdgeUiState, actions: SonyEdgeActions) {
    val previewOpen = state.previewIndex != null
    SonyEdgeAppTheme(
        darkTheme = resolveDarkTheme(state.themeMode),
        previewImmersive = previewOpen
    ) {
        val colors = SonyEdgeTheme.colors
        val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
        LaunchedEffect(appContext) {
            withContext(Dispatchers.IO) {
                CameraImageLoader.cleanupExpiredCache(appContext, force = true)
            }
        }
        val snackbarHostState = remember { SnackbarHostState() }
        LaunchedEffect(state.transientMessage) {
            val message = state.transientMessage ?: return@LaunchedEffect
            snackbarHostState.showSnackbar(message)
            actions.onConsumeTransientMessage()
        }

        Surface(Modifier.fillMaxSize(), color = colors.bg) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val widthDp = maxWidth
                val useRail = widthDp >= 600.dp
                val route = routeFor(state)
                val navVisible = !previewOpen && !state.selectionMode

                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.weight(1f)) {
                        if (useRail && navVisible) {
                            SonyEdgeNavRail(route, actions.onTab)
                        }
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            RouteContent(
                                route = route,
                                state = state,
                                actions = actions,
                                widthDp = widthDp
                            )
                        }
                    }
                    if (!useRail) {
                        AnimatedVisibility(
                            visible = navVisible,
                            enter = fadeIn(tween(Motion.toolbar)),
                            exit = fadeOut(tween(Motion.toolbar))
                        ) {
                            SonyEdgeBottomNav(route, actions.onTab)
                        }
                    }
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (navVisible && !useRail) Dimens.bottomNav + 14.dp else 14.dp)
                        .navigationBarsPadding()
                ) { data ->
                    Snackbar(
                        snackbarData = data,
                        shape = RoundedCornerShape(Dimens.cornerCard),
                        containerColor = colors.text1,
                        contentColor = colors.bg
                    )
                }

                PreviewLayer(state, actions)
            }
        }
    }
}

@Composable
private fun RouteContent(
    route: SonyEdgeRoute,
    state: SonyEdgeUiState,
    actions: SonyEdgeActions,
    widthDp: androidx.compose.ui.unit.Dp
) {
    val reduceMotion = LocalReduceMotion.current
    // 时间线 ↔ 网格：240ms 共享轴水平；其余 180ms fade-through
    AnimatedContent(
        targetState = route,
        transitionSpec = {
            val gridPair =
                (initialState is SonyEdgeRoute.DateTimeline && targetState is SonyEdgeRoute.PhotoGrid) ||
                    (initialState is SonyEdgeRoute.PhotoGrid && targetState is SonyEdgeRoute.DateTimeline)
            if (gridPair && !reduceMotion) {
                val forward = targetState is SonyEdgeRoute.PhotoGrid
                (slideInHorizontally(tween(Motion.grid)) { full -> if (forward) full / 4 else -full / 4 } +
                    fadeIn(tween(Motion.grid))) togetherWith
                    (slideOutHorizontally(tween(Motion.grid)) { full -> if (forward) -full / 4 else full / 4 } +
                        fadeOut(tween(Motion.grid)))
            } else {
                fadeIn(tween(Motion.nav, delayMillis = 40)) togetherWith fadeOut(tween(Motion.nav))
            }
        },
        label = "route"
    ) { targetRoute ->
        // 600–839dp：列内容限宽 560dp 居中；网格/时间线全宽
        val constrainWidth = widthDp >= 600.dp && widthDp < 840.dp &&
            targetRoute !is SonyEdgeRoute.PhotoGrid && targetRoute !is SonyEdgeRoute.DateTimeline
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(if (constrainWidth) Modifier.widthIn(max = 560.dp).fillMaxHeight() else Modifier.fillMaxSize()) {
                when (targetRoute) {
                    SonyEdgeRoute.Disconnected -> DisconnectedScreen(state, actions)
                    SonyEdgeRoute.Connecting -> ConnectingScreen(state, actions)
                    SonyEdgeRoute.DateTimeline -> DateTimelineScreen(state, actions)
                    SonyEdgeRoute.PhotoGrid -> PhotoGridScreen(state, actions, widthDp)
                    SonyEdgeRoute.Transfers -> TransfersScreen(state, actions)
                    SonyEdgeRoute.Settings -> SettingsScreen(state, actions)
                }
            }
        }
    }
}

@Composable
private fun PreviewLayer(state: SonyEdgeUiState, actions: SonyEdgeActions) {
    val reduceMotion = LocalReduceMotion.current
    val previewIndex = state.previewIndex
    var retainedPreviewIndex by remember { mutableIntStateOf(previewIndex ?: 0) }
    SideEffect {
        if (previewIndex != null && state.photos.isNotEmpty()) {
            retainedPreviewIndex = previewIndex.coerceIn(state.photos.indices)
        }
    }
    AnimatedVisibility(
        visible = previewIndex != null,
        modifier = Modifier.fillMaxSize(),
        enter = if (reduceMotion) {
            fadeIn(tween(Motion.preview))
        } else {
            fadeIn(tween(Motion.preview)) + scaleIn(tween(Motion.preview), initialScale = 0.96f)
        },
        exit = fadeOut(tween(Motion.previewExit))
    ) {
        if (state.photos.isNotEmpty()) {
            PhotoPreviewOverlay(
                items = state.photos,
                currentIndex = (previewIndex ?: retainedPreviewIndex).coerceIn(state.photos.indices),
                selectedKeys = state.selectedKeys,
                onClose = actions.onClosePreview,
                onPrevious = { actions.onPreviewNext(-1) },
                onNext = { actions.onPreviewNext(1) },
                onPageSettled = actions.onPreviewPage,
                onSelect = actions.onToggleSelection,
                onDownload = actions.onDownloadPreview
            )
        }
    }
}

@Composable
private fun SonyEdgeBottomNav(route: SonyEdgeRoute, onTab: (SonyEdgeTab) -> Unit) {
    val colors = SonyEdgeTheme.colors
    Column(Modifier.fillMaxWidth().background(colors.surface)) {
        HorizontalDivider(color = colors.line)
        Row(
            Modifier
                .fillMaxWidth()
                .height(Dimens.bottomNav)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEach { item ->
                val selected = isNavSelected(item.tab, route)
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTab(item.tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (selected && item.tab == SonyEdgeTab.Library) {
                        // 浏览选中：琥珀实底方形图标
                        Box(
                            Modifier
                                .size(26.dp)
                                .background(colors.accentFill, RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                item.icon,
                                contentDescription = item.label,
                                tint = colors.onAccentFill,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        Icon(
                            item.icon,
                            contentDescription = item.label,
                            tint = if (selected) colors.accent else colors.text3,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.label,
                        color = if (selected) colors.text1 else colors.text3,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun SonyEdgeNavRail(route: SonyEdgeRoute, onTab: (SonyEdgeTab) -> Unit) {
    val colors = SonyEdgeTheme.colors
    Row(Modifier.fillMaxHeight()) {
        Column(
            Modifier
                .fillMaxHeight()
                .width(84.dp)
                .background(colors.surface)
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(18.dp))
            navItems.forEach { item ->
                val selected = isNavSelected(item.tab, route)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTab(item.tab) }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (selected) {
                        Box(
                            Modifier
                                .size(30.dp)
                                .background(colors.accentFill, RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                item.icon,
                                contentDescription = item.label,
                                tint = colors.onAccentFill,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    } else {
                        Icon(
                            item.icon,
                            contentDescription = item.label,
                            tint = colors.text3,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.label,
                        color = if (selected) colors.text1 else colors.text3,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
        androidx.compose.material3.VerticalDivider(color = colors.line)
    }
}

private fun isNavSelected(tab: SonyEdgeTab, route: SonyEdgeRoute): Boolean = when (tab) {
    SonyEdgeTab.Library, SonyEdgeTab.Camera -> route.isBrowse
    SonyEdgeTab.Transfers -> route is SonyEdgeRoute.Transfers
    SonyEdgeTab.Settings -> route is SonyEdgeRoute.Settings
}
