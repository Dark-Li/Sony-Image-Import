package com.codex.sonyedge.ui.browse

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.sonyedge.DmsContainerItem
import com.codex.sonyedge.SonyEdgeTab
import com.codex.sonyedge.SonyEdgeUiState
import com.codex.sonyedge.ui.image.RemoteCameraImage
import com.codex.sonyedge.ui.image.isVideoItem
import com.codex.sonyedge.ui.parseDateFolderLabel
import com.codex.sonyedge.ui.shell.ConfirmDialog
import com.codex.sonyedge.ui.shell.MonoText
import com.codex.sonyedge.ui.shell.PrimaryButton
import com.codex.sonyedge.ui.shell.SonyEdgeActions
import com.codex.sonyedge.ui.theme.Dimens
import com.codex.sonyedge.ui.theme.DisplayFamily
import com.codex.sonyedge.ui.theme.SonyEdgeTheme

/** 01d/01e · 日期时间线：连接成功后的落点。 */
@Composable
fun DateTimelineScreen(state: SonyEdgeUiState, actions: SonyEdgeActions) {
    val colors = SonyEdgeTheme.colors
    var disconnectConfirm by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ConnectedStatusBar(
            state = state,
            onRefresh = actions.onRefresh,
            onDisconnect = { disconnectConfirm = true }
        )

        if (state.cameraSelectionReceiving) {
            ReceivingPushBanner(
                message = state.cameraSelectionStatus,
                onView = { actions.onTab(SonyEdgeTab.Transfers) }
            )
        }

        when {
            state.errorMessage != null && !state.loading -> TimelineTimeoutState(actions)
            state.loading && state.folders.isEmpty() -> TimelineSkeleton()
            state.folders.isEmpty() && !state.loading -> TimelineEmptyState()
            else -> TimelineList(state, actions)
        }
    }

    if (disconnectConfirm) {
        ConfirmDialog(
            title = "断开相机连接?",
            message = "断开后需要重新连接相机 Wi-Fi 才能继续浏览照片。",
            confirmText = "断开",
            danger = true,
            onConfirm = {
                disconnectConfirm = false
                actions.onDisconnectCamera()
            },
            onDismiss = { disconnectConfirm = false }
        )
    }
}

/** 顶部状态条 58dp：teal 呼吸点 + 机型 + 刷新 + 断开。 */
@Composable
private fun ConnectedStatusBar(
    state: SonyEdgeUiState,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit
) {
    val colors = SonyEdgeTheme.colors
    val model = state.connectedCamera?.modelName?.takeIf { it.isNotBlank() }
        ?: state.rememberedCamera?.modelName?.takeIf { it.isNotBlank() }
        ?: "Sony 相机"
    val transition = rememberInfiniteTransition(label = "breathing")
    val dotAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "breathingDot"
    )
    Row(
        Modifier
            .fillMaxWidth()
            .height(Dimens.statusBar)
            .padding(horizontal = Dimens.pageMargin),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .alpha(dotAlpha)
                .background(colors.teal, CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                model,
                color = colors.text1,
                fontFamily = DisplayFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(8.dp))
            Text("已连接", color = colors.text3, fontSize = 12.5.sp, maxLines = 1)
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onRefresh, enabled = !state.loading, modifier = Modifier.size(Dimens.touchTarget)) {
            Icon(Icons.Default.Refresh, contentDescription = "刷新", tint = colors.text1, modifier = Modifier.size(22.dp))
        }
        TextButton(onClick = onDisconnect) {
            Text("断开", color = colors.text1, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** 相机推送横幅（teal）：正在接收相机选择 · 查看。 */
@Composable
private fun ReceivingPushBanner(message: String, onView: () -> Unit) {
    val colors = SonyEdgeTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.listMargin, vertical = 4.dp)
            .background(colors.tealBg, RoundedCornerShape(Dimens.cornerCard))
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.5.dp,
                color = colors.teal
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("正在接收相机选择", color = colors.teal, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    message,
                    color = colors.text2,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onView) {
                Text("查看", color = colors.teal, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun TimelineList(state: SonyEdgeUiState, actions: SonyEdgeActions) {
    val colors = SonyEdgeTheme.colors
    val totalItems = state.folders.sumOf { it.childCount.coerceAtLeast(0) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.pageMargin, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("拍摄日期", color = colors.text2, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            MonoText("$totalItems 张 · ${state.folders.size} 个日期", color = colors.text3, fontSize = 12.sp)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.folders, key = { it.id }) { folder ->
                TimelineRow(
                    folder = folder,
                    previews = state.folderPreviews[folder.id].orEmpty(),
                    onOpen = { actions.onOpenFolder(folder) }
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

/** 时间线行 72dp：大日期数字 + 三格胶片条 + mono 计数。 */
@Composable
private fun TimelineRow(
    folder: DmsContainerItem,
    previews: List<com.codex.sonyedge.CameraContentItem>,
    onOpen: () -> Unit
) {
    val colors = SonyEdgeTheme.colors
    val label = parseDateFolderLabel(folder.title, folder.date)
    val count = folder.childCount.coerceAtLeast(0)
    val empty = count == 0
    Row(
        Modifier
            .fillMaxWidth()
            .height(Dimens.timelineRow + 14.dp)
            .then(if (empty) Modifier.alpha(0.55f) else Modifier.clickable(onClick = onOpen))
            .padding(horizontal = Dimens.pageMargin),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.width(84.dp)) {
            Text(
                label.day ?: label.raw,
                color = colors.text1,
                fontFamily = DisplayFamily,
                fontWeight = FontWeight.Bold,
                fontSize = if (label.day != null) 30.sp else 16.sp,
                maxLines = 1
            )
            if (label.subtitle != null) {
                Text(label.subtitle, color = colors.text3, fontSize = 10.5.sp)
            }
        }
        Spacer(Modifier.weight(1f))
        if (empty) {
            EmptyFilmstripPlaceholder()
        } else {
            Filmstrip(previews)
        }
        Spacer(Modifier.width(18.dp))
        MonoText(
            "$count",
            color = if (empty) colors.text3 else colors.text2,
            fontSize = 14.sp,
            modifier = Modifier.width(40.dp)
        )
    }
}

/** 三格胶片条：56 + 34 + 34 × 46dp，5dp 圆角。 */
@Composable
private fun Filmstrip(previews: List<com.codex.sonyedge.CameraContentItem>) {
    val colors = SonyEdgeTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (index in 0 until 3) {
            val width = if (index == 0) Dimens.filmstripLead else Dimens.filmstripCell
            val item = previews.getOrNull(index)
            Box(
                Modifier
                    .width(width)
                    .height(Dimens.filmstripHeight)
                    .clip(RoundedCornerShape(Dimens.filmstripCorner))
                    .background(colors.surface2)
            ) {
                if (item != null && !isVideoItem(item)) {
                    RemoteCameraImage(
                        url = item.previewUrl(),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        maxDimension = 200
                    )
                }
            }
        }
    }
}

/** 空目录行：虚线占位 + 「空目录」。 */
@Composable
private fun EmptyFilmstripPlaceholder() {
    val colors = SonyEdgeTheme.colors
    Box(
        Modifier
            .width(Dimens.filmstripLead + Dimens.filmstripCell * 2 + 6.dp)
            .height(Dimens.filmstripHeight),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                color = colors.text3,
                cornerRadius = CornerRadius(5.dp.toPx()),
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                )
            )
        }
        Text("空目录", color = colors.text3, fontSize = 12.sp)
    }
}

/** 骨架加载：呼吸透明度的占位行。 */
@Composable
private fun TimelineSkeleton() {
    val colors = SonyEdgeTheme.colors
    val transition = rememberInfiniteTransition(label = "skeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "skeletonPulse"
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = Dimens.pageMargin)) {
        Spacer(Modifier.height(16.dp))
        repeat(5) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(Dimens.timelineRow + 14.dp)
                    .alpha(pulse),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .width(56.dp)
                        .height(34.dp)
                        .background(colors.surface2, RoundedCornerShape(6.dp))
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .width(130.dp)
                        .height(Dimens.filmstripHeight)
                        .background(colors.surface2, RoundedCornerShape(Dimens.filmstripCorner))
                )
            }
        }
    }
}

/** 读取超时：cloud_off + 重试。 */
@Composable
private fun TimelineTimeoutState(actions: SonyEdgeActions) {
    val colors = SonyEdgeTheme.colors
    Column(
        Modifier.fillMaxSize().padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.CloudOff,
            contentDescription = null,
            tint = colors.text3,
            modifier = Modifier.size(52.dp)
        )
        Spacer(Modifier.height(18.dp))
        Text("读取相机内容超时", color = colors.text1, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "请确认相机未休眠或关机,\n靠近相机后重试。",
            color = colors.text2,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(22.dp))
        PrimaryButton(
            text = "重试",
            icon = Icons.Default.Refresh,
            onClick = actions.onRefresh
        )
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun TimelineEmptyState() {
    val colors = SonyEdgeTheme.colors
    Column(
        Modifier.fillMaxSize().padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("没有可浏览的照片", color = colors.text1, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "相机存储卡中没有找到按日期整理的照片。",
            color = colors.text2,
            fontSize = 13.5.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(96.dp))
    }
}
