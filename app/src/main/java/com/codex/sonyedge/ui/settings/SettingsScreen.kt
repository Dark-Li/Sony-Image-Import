package com.codex.sonyedge.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.sonyedge.BuildConfig
import com.codex.sonyedge.SonyEdgeUiState
import com.codex.sonyedge.isCameraTransferBusy
import com.codex.sonyedge.ui.shell.ConfirmDialog
import com.codex.sonyedge.ui.shell.MonoText
import com.codex.sonyedge.ui.shell.SectionLabel
import com.codex.sonyedge.ui.shell.SonyEdgeActions
import com.codex.sonyedge.ui.shell.TokenCard
import com.codex.sonyedge.ui.theme.Dimens
import com.codex.sonyedge.ui.theme.SonyEdgeTheme
import com.codex.sonyedge.ui.theme.ThemeMode

/** 04a · 设置：分组卡 + 折叠高级。 */
@Composable
fun SettingsScreen(state: SonyEdgeUiState, actions: SonyEdgeActions) {
    val colors = SonyEdgeTheme.colors
    val busy = isCameraTransferBusy(state)
    var advancedExpanded by remember { mutableStateOf(false) }
    var forgetConfirm by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.listMargin)
    ) {
        Spacer(Modifier.height(18.dp))
        Text("设置", color = colors.text1, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))

        // 已保存的相机
        SectionLabel("已保存的相机")
        Spacer(Modifier.height(8.dp))
        TokenCard(Modifier.fillMaxWidth()) {
            Column {
                val remembered = state.rememberedCamera
                if (remembered != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 6.dp, top = 14.dp, bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = colors.text2,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                remembered.modelName?.takeIf { it.isNotBlank() } ?: "Sony 相机",
                                color = colors.text1,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            MonoText(remembered.ssid, color = colors.text3, fontSize = 12.sp)
                        }
                        TextButton(onClick = { forgetConfirm = true }, enabled = !busy) {
                            Text("忘记", color = colors.rose, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    HorizontalDivider(color = colors.line, modifier = Modifier.padding(horizontal = 16.dp))
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !busy, onClick = actions.onScanCameraQr)
                        .defaultMinSize(minHeight = 52.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Text("添加其他相机", color = colors.accent, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        // 接收
        SectionLabel("接收")
        Spacer(Modifier.height(8.dp))
        TokenCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("接收相机选择", color = colors.text1, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "在相机上选择“发送到智能手机”时自动接收照片",
                        color = colors.text2,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = state.receiveCameraSelectionEnabled,
                    onCheckedChange = actions.onSetReceiveSelection,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = colors.accentFill,
                        checkedThumbColor = if (colors.isDark) colors.bg else colors.surface,
                        uncheckedTrackColor = colors.surface2,
                        uncheckedThumbColor = colors.text3,
                        uncheckedBorderColor = colors.line
                    )
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        // 外观
        SectionLabel("外观")
        Spacer(Modifier.height(8.dp))
        TokenCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text("深色模式", color = colors.text1, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                ThemeModeSelector(state.themeMode, actions.onSetThemeMode)
            }
        }
        Spacer(Modifier.height(20.dp))

        // 存储
        SectionLabel("存储")
        Spacer(Modifier.height(8.dp))
        TokenCard(Modifier.fillMaxWidth()) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = colors.text2,
                        modifier = Modifier.size(21.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("保存位置", color = colors.text1, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(2.dp))
                        MonoText("DCIM/Sony Picture", color = colors.text3, fontSize = 12.sp)
                    }
                }
                HorizontalDivider(color = colors.line, modifier = Modifier.padding(horizontal = 16.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = actions.onOpenGallery)
                        .defaultMinSize(minHeight = 52.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        tint = colors.text2,
                        modifier = Modifier.size(21.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "打开系统相册",
                        color = colors.text1,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = colors.text3,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        // 高级（可折叠）
        SectionLabel("高级")
        Spacer(Modifier.height(8.dp))
        TokenCard(Modifier.fillMaxWidth()) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { advancedExpanded = !advancedExpanded }
                        .defaultMinSize(minHeight = 56.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = null,
                        tint = colors.text2,
                        modifier = Modifier.size(21.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "高级设置",
                        color = colors.text1,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        if (advancedExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (advancedExpanded) "收起高级设置" else "展开高级设置",
                        tint = colors.text3
                    )
                }
                AnimatedVisibility(visible = advancedExpanded) {
                    Column {
                        HorizontalDivider(color = colors.line, modifier = Modifier.padding(horizontal = 16.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(onClick = actions.onExportLogs)
                                .defaultMinSize(minHeight = 52.dp)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = null,
                                tint = colors.text2,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(14.dp))
                            Text("导出诊断日志", color = colors.text1, fontSize = 15.sp)
                        }
                        HorizontalDivider(color = colors.line, modifier = Modifier.padding(horizontal = 16.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(onClick = actions.onClearThumbnailCache)
                                .defaultMinSize(minHeight = 52.dp)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = null,
                                tint = colors.text2,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(14.dp))
                            Text("清除缩略图缓存", color = colors.text1, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        // 关于
        SectionLabel("关于")
        Spacer(Modifier.height(8.dp))
        TokenCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(colors.accentFill, RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("SonyEdge", color = colors.text1, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    MonoText(
                        "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        color = colors.text3,
                        fontSize = 12.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }

    if (forgetConfirm) {
        ConfirmDialog(
            title = "忘记这台相机?",
            message = "将删除已保存的 Wi-Fi 信息,下次连接需要重新扫描二维码。",
            confirmText = "忘记",
            danger = true,
            onConfirm = {
                forgetConfirm = false
                actions.onForgetCamera()
            },
            onDismiss = { forgetConfirm = false }
        )
    }
}

/** 主题模式三选：跟随系统 / 浅色 / 深色。 */
@Composable
private fun ThemeModeSelector(current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val colors = SonyEdgeTheme.colors
    val options = listOf(
        ThemeMode.System to "跟随系统",
        ThemeMode.Light to "浅色",
        ThemeMode.Dark to "深色"
    )
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surface2, RoundedCornerShape(Dimens.cornerSmall))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEach { (mode, label) ->
            val selected = mode == current
            Box(
                Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 36.dp)
                    .background(
                        if (selected) colors.surface else androidx.compose.ui.graphics.Color.Transparent,
                        RoundedCornerShape(Dimens.cornerSmall - 1.dp)
                    )
                    .clickable { onSelect(mode) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (selected) colors.text1 else colors.text2,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}
