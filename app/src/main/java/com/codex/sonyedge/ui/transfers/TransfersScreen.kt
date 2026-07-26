package com.codex.sonyedge.ui.transfers

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.sonyedge.CameraConnectPhase
import com.codex.sonyedge.DownloadService
import com.codex.sonyedge.SonyEdgeTab
import com.codex.sonyedge.SonyEdgeUiState
import com.codex.sonyedge.TransferOutcome
import com.codex.sonyedge.TransferRecord
import com.codex.sonyedge.isDownloadTransferActive
import com.codex.sonyedge.ui.formatTransferBytes
import com.codex.sonyedge.ui.shell.MonoText
import com.codex.sonyedge.ui.shell.PrimaryButton
import com.codex.sonyedge.ui.shell.SecondaryButton
import com.codex.sonyedge.ui.shell.SonyEdgeActions
import com.codex.sonyedge.ui.shell.TokenCard
import com.codex.sonyedge.ui.theme.Dimens
import com.codex.sonyedge.ui.theme.DisplayFamily
import com.codex.sonyedge.ui.theme.Motion
import com.codex.sonyedge.ui.theme.SonyEdgeTheme
import com.codex.sonyedge.ui.transferEtaText
import com.codex.sonyedge.ui.transferSpeedText

/** 03a–03e · 传输：空 / 下载中 / 完成 / 部分失败 / 中断。 */
@Composable
fun TransfersScreen(state: SonyEdgeUiState, actions: SonyEdgeActions) {
    val colors = SonyEdgeTheme.colors
    val active = isDownloadTransferActive(state)
    val disconnected = state.connectPhase != CameraConnectPhase.Connected
    val showInterruptBanner = disconnected &&
        (active || state.transferHistory.firstOrNull()?.outcome == TransferOutcome.Interrupted)
    val hasContent = active || state.transferHistory.isNotEmpty()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = Dimens.listMargin)
    ) {
        Spacer(Modifier.height(18.dp))
        Text("传输", color = colors.text1, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MonoText("DCIM/Sony Picture", color = colors.text3, fontSize = 12.5.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = actions.onOpenGallery) {
                Text("打开相册", color = colors.accent, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(6.dp))

        if (showInterruptBanner) {
            InterruptedBanner(onReconnect = actions.onConnectCameraWifi)
            Spacer(Modifier.height(10.dp))
        }

        if (!hasContent) {
            TransfersEmptyState(onBrowse = { actions.onTab(SonyEdgeTab.Library) })
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (active) {
                    item(key = "active") {
                        ActiveTransferCard(state, actions, interrupted = disconnected)
                    }
                }
                items(state.transferHistory, key = { it.id }) { record ->
                    HistoryTransferCard(
                        record = record,
                        isLatest = record == state.transferHistory.firstOrNull(),
                        state = state,
                        actions = actions
                    )
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

/** 03e · 顶部 amber 横幅：连接已中断。 */
@Composable
private fun InterruptedBanner(onReconnect: () -> Unit) {
    val colors = SonyEdgeTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .background(colors.amberBg, RoundedCornerShape(Dimens.cornerCard))
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.WifiOff,
                contentDescription = null,
                tint = colors.amber,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "连接已中断,任务已暂停",
                    color = colors.amber,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text("重新连接相机后可继续导入", color = colors.text2, fontSize = 12.5.sp)
            }
            TextButton(onClick = onReconnect) {
                Text("重新连接", color = colors.amber, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

/** 03b · 下载中任务卡（连接中断时状态转 已中断）。 */
@Composable
private fun ActiveTransferCard(
    state: SonyEdgeUiState,
    actions: SonyEdgeActions,
    interrupted: Boolean
) {
    val colors = SonyEdgeTheme.colors
    val total = state.downloadTotal.coerceAtLeast(1)
    val fraction = (state.downloadProgress.toFloat() / total).coerceIn(0f, 1f)
    val percent = (fraction * 100).toInt()
    val statusText = if (interrupted) "已中断" else "正在导入"
    val statusColor = if (interrupted) colors.amber else colors.accent

    TokenCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${state.downloadBatchTitle.ifBlank { "导入任务" }} · ${state.downloadTotal} 张",
                        color = colors.text1,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(statusText, color = statusColor, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "$percent%",
                    color = colors.text1,
                    fontFamily = DisplayFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            TransferProgressBar(fraction, if (interrupted) colors.amber else colors.accent)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                MonoText(
                    state.downloadCurrentFile.ifBlank { "等待中" },
                    color = colors.text2,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                if (!interrupted) {
                    MonoText(
                        "${transferSpeedText(state.downloadSpeedBps)} · " +
                            transferEtaText(
                                state.downloadEtaSeconds,
                                state.downloadSpeedBps,
                                state.downloadBytesTotal
                            ),
                        color = colors.text2,
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth()) {
                MonoText(
                    "${state.downloadProgress}/${state.downloadTotal} 张",
                    color = colors.text3,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                if (state.downloadBytesTotal > 0) {
                    MonoText(
                        "${formatTransferBytes(state.downloadBytesDone)} / ${formatTransferBytes(state.downloadBytesTotal)}",
                        color = colors.text3,
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            if (interrupted && state.failedItems.isNotEmpty()) {
                Row {
                    PrimaryButton(
                        text = "继续",
                        onClick = actions.onRetryFailed,
                        modifier = Modifier.weight(1f),
                        minHeight = Dimens.buttonSecondary
                    )
                    Spacer(Modifier.width(10.dp))
                    SecondaryButton(
                        text = "取消导入",
                        onClick = actions.onCancelDownloads,
                        modifier = Modifier.weight(1f),
                        minHeight = Dimens.buttonSecondary
                    )
                }
            } else {
                SecondaryButton(
                    text = "取消导入",
                    onClick = actions.onCancelDownloads,
                    modifier = Modifier.fillMaxWidth(),
                    minHeight = Dimens.buttonSecondary
                )
            }
        }
    }
}

/** 3dp 进度条（400ms 动画）。 */
@Composable
private fun TransferProgressBar(fraction: Float, color: androidx.compose.ui.graphics.Color) {
    val colors = SonyEdgeTheme.colors
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(Motion.progress),
        label = "transferProgress"
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(colors.surface2)
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .background(color)
        )
    }
}

/** 03c/03d/03e · 历史任务卡。 */
@Composable
private fun HistoryTransferCard(
    record: TransferRecord,
    isLatest: Boolean,
    state: SonyEdgeUiState,
    actions: SonyEdgeActions
) {
    val colors = SonyEdgeTheme.colors
    val (statusText, statusColor) = when (record.outcome) {
        TransferOutcome.Done -> "已完成" to colors.teal
        TransferOutcome.PartialFail -> "部分失败" to colors.rose
        TransferOutcome.Interrupted -> "已中断" to colors.amber
    }
    val canRetry = isLatest && state.failedItems.isNotEmpty() &&
        record.outcome != TransferOutcome.Done &&
        !isDownloadTransferActive(state)

    TokenCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${record.title} · ${record.total} 张",
                        color = colors.text1,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(statusText, color = statusColor, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }
                if (record.outcome == TransferOutcome.Interrupted && canRetry) {
                    TextButton(onClick = actions.onRetryFailed) {
                        Text("继续", color = colors.amber, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
            if (record.outcome == TransferOutcome.Interrupted) {
                Spacer(Modifier.height(10.dp))
                TransferProgressBar(
                    fraction = if (record.total > 0) record.success.toFloat() / record.total else 0f,
                    color = colors.amber
                )
                Spacer(Modifier.height(8.dp))
                MonoText("已导入 ${record.success}/${record.total} 张", color = colors.text3, fontSize = 12.5.sp)
            } else {
                Spacer(Modifier.height(8.dp))
                val detail = when (record.outcome) {
                    TransferOutcome.PartialFail ->
                        "${record.success} 张成功 · ${record.failed} 张失败 · ${formatTransferBytes(record.bytes)}"
                    else ->
                        "${record.success} 张 · ${formatTransferBytes(record.bytes)} · DCIM/Sony Picture"
                }
                MonoText(detail, color = colors.text2, fontSize = 12.5.sp)
            }
            if (record.outcome == TransferOutcome.PartialFail && canRetry) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PrimaryButton(
                        text = "重试失败",
                        icon = Icons.Default.Refresh,
                        onClick = actions.onRetryFailed,
                        minHeight = Dimens.buttonSecondary
                    )
                    Spacer(Modifier.width(14.dp))
                    TextButton(onClick = actions.onOpenGallery) {
                        Text("查看相册", color = colors.text1, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

/** 03a · 空态。 */
@Composable
private fun TransfersEmptyState(onBrowse: () -> Unit) {
    val colors = SonyEdgeTheme.colors
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.ImportExport,
            contentDescription = null,
            tint = colors.text3,
            modifier = Modifier.size(46.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text("还没有传输任务", color = colors.text1, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "从相机照片中选择并导入后,任务会显示在这里。",
            color = colors.text2,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(22.dp))
        SecondaryButton(text = "浏览相机照片", onClick = onBrowse)
        Spacer(Modifier.height(110.dp))
    }
}
