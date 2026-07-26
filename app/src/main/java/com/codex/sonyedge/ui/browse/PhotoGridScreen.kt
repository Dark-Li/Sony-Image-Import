package com.codex.sonyedge.ui.browse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.sonyedge.CameraContentItem
import com.codex.sonyedge.SonyEdgeUiState
import com.codex.sonyedge.itemKey
import com.codex.sonyedge.ui.formatDateFolderTitle
import com.codex.sonyedge.ui.image.RemoteCameraImage
import com.codex.sonyedge.ui.image.isVideoItem
import com.codex.sonyedge.ui.shell.MonoText
import com.codex.sonyedge.ui.shell.PrimaryButton
import com.codex.sonyedge.ui.shell.SonyEdgeActions
import com.codex.sonyedge.ui.theme.Dimens
import com.codex.sonyedge.ui.theme.Motion
import com.codex.sonyedge.ui.theme.SonyEdgeTheme

/** 02a–02d · 照片网格：全出血自适应网格 + 多选 + 空/超时状态。 */
@Composable
fun PhotoGridScreen(state: SonyEdgeUiState, actions: SonyEdgeActions, widthDp: Dp) {
    val colors = SonyEdgeTheme.colors
    val selectionMode = state.selectionMode || state.selectedKeys.isNotEmpty()
    val title = formatDateFolderTitle(state.currentFolderTitle)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            if (selectionMode) {
                SelectionTopBar(state, actions)
            } else {
                GridTopBar(title, state, actions)
            }

            if (!selectionMode && !state.gridHintDismissed && state.photos.isNotEmpty()) {
                GridHintBar(onDismiss = actions.onDismissGridHint)
            }

            when {
                state.errorMessage != null && !state.loading -> GridTimeoutState(actions)
                state.loading && state.photos.isEmpty() -> GridSkeleton(widthDp)
                state.photos.isEmpty() && !state.loading -> EmptyFolderState()
                else -> PhotoGrid(state, actions, widthDp, selectionMode)
            }
        }

        SelectionToolbar(
            visible = selectionMode,
            selectedCount = state.selectedKeys.size,
            onClear = actions.onClearSelection,
            onImport = actions.onDownloadSelected,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/** 顶栏 54dp：返回 + 日期 + mono 计数 + 多选按钮。 */
@Composable
private fun GridTopBar(title: String, state: SonyEdgeUiState, actions: SonyEdgeActions) {
    val colors = SonyEdgeTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(Dimens.topBar)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = actions.onBack, modifier = Modifier.size(Dimens.touchTarget)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回日期时间线",
                tint = colors.text1
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            title,
            color = colors.text1,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(Modifier.width(10.dp))
        if (state.photos.isNotEmpty()) {
            MonoText("${state.photos.size} 张", color = colors.text3, fontSize = 12.5.sp)
        }
        Spacer(Modifier.weight(1f))
        if (state.photos.isNotEmpty()) {
            IconButton(onClick = actions.onEnterSelection, modifier = Modifier.size(Dimens.touchTarget)) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "进入多选",
                    tint = colors.text1,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/** 多选顶栏：关闭 + 已选 n 张（n 琥珀）+ 全选/反选。 */
@Composable
private fun SelectionTopBar(state: SonyEdgeUiState, actions: SonyEdgeActions) {
    val colors = SonyEdgeTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(Dimens.topBar)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = actions.onExitSelection, modifier = Modifier.size(Dimens.touchTarget)) {
            Icon(Icons.Default.Close, contentDescription = "退出多选", tint = colors.text1)
        }
        Spacer(Modifier.width(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("已选 ", color = colors.text1, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(
                "${state.selectedKeys.size}",
                color = colors.accent,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
            Text(" 张", color = colors.text1, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.weight(1f))
        TextButton(
            onClick = actions.onSelectAll,
            enabled = state.selectedKeys.size < state.photos.size
        ) {
            Text("全选", color = colors.text1, fontSize = 15.sp)
        }
        TextButton(onClick = actions.onInvertSelection) {
            Text("反选", color = colors.text1, fontSize = 15.sp)
        }
    }
}

/** 首次进入提示条：长按缩略图可进入多选。 */
@Composable
private fun GridHintBar(onDismiss: () -> Unit) {
    val colors = SonyEdgeTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.listMargin, vertical = 4.dp)
            .background(colors.surface2, RoundedCornerShape(Dimens.cornerSmall))
            .padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "长按缩略图可进入多选",
            color = colors.text2,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = "关闭提示",
                tint = colors.text3,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoGrid(
    state: SonyEdgeUiState,
    actions: SonyEdgeActions,
    widthDp: Dp,
    selectionMode: Boolean
) {
    val minCell = when {
        widthDp >= 840.dp -> Dimens.gridMinCellExpanded
        widthDp >= 600.dp -> Dimens.gridMinCellMedium
        else -> Dimens.gridMinCell
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minCell),
        verticalArrangement = Arrangement.spacedBy(Dimens.gridGap),
        horizontalArrangement = Arrangement.spacedBy(Dimens.gridGap),
        contentPadding = PaddingValues(
            bottom = if (selectionMode) Dimens.gridSelectionBottomInset else 16.dp
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        items(state.photos, key = { itemKey(it) }) { item ->
            PhotoTile(
                item = item,
                selected = state.selectedKeys.contains(itemKey(item)),
                selectionMode = selectionMode,
                onPreview = { actions.onPreview(item) },
                onToggle = { actions.onToggleSelection(item) },
                onLongPress = {
                    if (!selectionMode) actions.onEnterSelection()
                    actions.onToggleSelection(item)
                }
            )
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
    onToggle: () -> Unit,
    onLongPress: () -> Unit
) {
    val colors = SonyEdgeTheme.colors
    val contentScaleAnim by animateFloatAsState(
        targetValue = if (selected) 0.85f else 1f,
        animationSpec = tween(Motion.select),
        label = "tileScale"
    )
    val tileAlpha by animateFloatAsState(
        targetValue = if (selectionMode && !selected) 0.75f else 1f,
        animationSpec = tween(Motion.select),
        label = "tileAlpha"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(colors.surface2)
            .then(
                if (selected) {
                    Modifier.border(3.dp, colors.accentFill)
                } else {
                    Modifier
                }
            )
            .combinedClickable(
                onClick = { if (selectionMode) onToggle() else onPreview() },
                onLongClick = onLongPress
            )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = contentScaleAnim
                    scaleY = contentScaleAnim
                    alpha = tileAlpha
                }
        ) {
            if (isVideoItem(item)) {
                Box(
                    Modifier.fillMaxSize().background(colors.surface2),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Movie,
                        contentDescription = null,
                        tint = colors.text3,
                        modifier = Modifier.size(32.dp)
                    )
                }
            } else {
                RemoteCameraImage(
                    url = item.previewUrl(),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    maxDimension = 420
                )
            }
        }
        if (selectionMode) {
            Box(
                Modifier
                    .padding(6.dp)
                    .size(20.dp)
                    .semantics {
                        contentDescription = if (selected) "取消选择 ${item.title}" else "选择 ${item.title}"
                    }
                    .background(
                        if (selected) colors.accentFill else Color.Black.copy(alpha = 0.25f),
                        CircleShape
                    )
                    .then(
                        if (!selected) {
                            Modifier.border(1.5.dp, Color.White.copy(alpha = 0.9f), CircleShape)
                        } else {
                            Modifier
                        }
                    )
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = colors.onAccentFill,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

/** 底部悬浮多选工具条：清除 + 实心「导入 n 张」。 */
@Composable
private fun SelectionToolbar(
    visible: Boolean,
    selectedCount: Int,
    onClear: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SonyEdgeTheme.colors
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(Motion.toolbar)) + slideInVertically(tween(Motion.toolbar)) { it / 3 },
        exit = fadeOut(tween(Motion.toolbar)) + slideOutVertically(tween(Motion.toolbar)) { it / 3 },
        modifier = modifier
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp)
                .padding(bottom = 16.dp)
                .background(
                    colors.surface.copy(alpha = 0.94f),
                    RoundedCornerShape(Dimens.cornerFloating)
                )
                .border(
                    1.dp,
                    colors.line,
                    RoundedCornerShape(Dimens.cornerFloating)
                )
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onClear,
                    enabled = selectedCount > 0,
                    modifier = Modifier.defaultMinSize(minHeight = Dimens.touchTarget)
                ) {
                    Text(
                        "清除",
                        color = if (selectedCount > 0) colors.text1 else colors.text3,
                        fontSize = 15.sp
                    )
                }
                Spacer(Modifier.weight(1f))
                PrimaryButton(
                    text = "导入 $selectedCount 张",
                    icon = Icons.Default.Download,
                    onClick = onImport,
                    enabled = selectedCount > 0,
                    minHeight = Dimens.buttonPrimary
                )
            }
        }
    }
}

/** 加载态：12 格脉冲骨架。 */
@Composable
private fun GridSkeleton(widthDp: Dp) {
    val colors = SonyEdgeTheme.colors
    val transition = rememberInfiniteTransition(label = "gridSkeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "gridPulse"
    )
    val minCell = if (widthDp >= 600.dp) Dimens.gridMinCellMedium else Dimens.gridMinCell
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minCell),
        verticalArrangement = Arrangement.spacedBy(Dimens.gridGap),
        horizontalArrangement = Arrangement.spacedBy(Dimens.gridGap),
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = false
    ) {
        items(12) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .alpha(pulse)
                    .background(colors.surface2)
            )
        }
    }
}

/** 02c · 空目录。 */
@Composable
private fun EmptyFolderState() {
    val colors = SonyEdgeTheme.colors
    Column(
        Modifier.fillMaxSize().padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.HideImage,
            contentDescription = null,
            tint = colors.text3,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(18.dp))
        Text("此日期没有照片", color = colors.text1, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("相机存储卡中的该日期目录为空", color = colors.text2, fontSize = 14.sp)
        Spacer(Modifier.height(96.dp))
    }
}

/** 02d · 读取超时。 */
@Composable
private fun GridTimeoutState(actions: SonyEdgeActions) {
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
        PrimaryButton(text = "重试", icon = Icons.Default.Refresh, onClick = actions.onRefresh)
        Spacer(Modifier.height(96.dp))
    }
}
