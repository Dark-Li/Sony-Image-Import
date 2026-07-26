package com.codex.sonyedge.ui.preview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.sonyedge.CameraContentItem
import com.codex.sonyedge.itemKey
import com.codex.sonyedge.ui.image.CameraImageLoader
import com.codex.sonyedge.ui.image.ProgressiveCameraImage
import com.codex.sonyedge.ui.image.isVideoItem
import com.codex.sonyedge.ui.image.previewFallbackUrl
import com.codex.sonyedge.ui.image.previewPrimaryUrl
import com.codex.sonyedge.ui.shell.MonoText
import com.codex.sonyedge.ui.theme.Dimens
import com.codex.sonyedge.ui.theme.Motion
import com.codex.sonyedge.ui.theme.PreviewBlack
import com.codex.sonyedge.ui.theme.SonyEdgeTheme

/**
 * 02e · 大图预览：纯黑沉浸层。
 * 保留原有 pager / 捏合缩放 / 双击 / EXIF / 邻图预取逻辑。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoPreviewOverlay(
    items: List<CameraContentItem>,
    currentIndex: Int,
    selectedKeys: Set<String>,
    onClose: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPageSettled: (Int) -> Unit,
    onSelect: (CameraContentItem) -> Unit,
    onDownload: () -> Unit
) {
    if (items.isEmpty()) return
    val colors = SonyEdgeTheme.colors
    val startPage = currentIndex.coerceIn(items.indices)
    val pagerState = rememberPagerState(initialPage = startPage, pageCount = { items.size })
    val context = LocalContext.current.applicationContext
    var controlsVisible by remember { mutableStateOf(false) }
    var previewZoomed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { controlsVisible = true }
    LaunchedEffect(currentIndex) {
        val target = currentIndex.coerceIn(items.indices)
        if (pagerState.currentPage != target) {
            pagerState.animateScrollToPage(target)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            onPageSettled(page)
        }
    }
    val page = pagerState.currentPage.coerceIn(items.indices)
    val currentItem = items[page]
    val selected = selectedKeys.contains(itemKey(currentItem))
    val canPrevious = page > 0
    val canNext = page < items.lastIndex
    LaunchedEffect(page) { previewZoomed = false }
    LaunchedEffect(page, items) {
        val start = (page - 2).coerceAtLeast(0)
        val end = (page + 2).coerceAtMost(items.lastIndex)
        for (index in start..end) {
            val item = items[index]
            if (!isVideoItem(item)) {
                CameraImageLoader.loadBitmap(context, previewFallbackUrl(item), 900)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(PreviewBlack)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(items.size) {
                    detectTapGestures(onTap = { controlsVisible = !controlsVisible })
                },
            beyondViewportPageCount = 1,
            userScrollEnabled = !previewZoomed
        ) { pageIndex ->
            val frameItem = items[pageIndex]
            if (isVideoItem(frameItem)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Movie,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(52.dp)
                    )
                }
            } else {
                ProgressiveCameraImage(
                    primaryUrl = previewPrimaryUrl(frameItem),
                    fallbackUrl = previewFallbackUrl(frameItem),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    maxDimension = 2400,
                    resetZoomKey = pagerState.settledPage,
                    onZoomChanged = { zoomed ->
                        if (pageIndex == pagerState.currentPage) {
                            previewZoomed = zoomed
                        }
                    }
                )
            }
        }

        // 顶部渐变遮罩：关闭 + 文件名 + 计数
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(Motion.toolbar)) + slideInVertically(tween(Motion.toolbar)) { -it / 3 },
            exit = fadeOut(tween(Motion.toolbar)) + slideOutVertically(tween(Motion.toolbar)) { -it / 3 },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)
                        )
                    )
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose, modifier = Modifier.size(Dimens.touchTarget)) {
                        Icon(Icons.Default.Close, contentDescription = "关闭预览", tint = Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        MonoText(
                            currentItem.title,
                            color = Color.White,
                            fontSize = 12.5.sp
                        )
                        MonoText(
                            "${page + 1} / ${items.size}",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.5.sp
                        )
                    }
                }
            }
        }

        // 底部渐变：翻页箭头 + 导入 + 选择
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(Motion.toolbar)) + slideInVertically(tween(Motion.toolbar)) { it / 3 },
            exit = fadeOut(tween(Motion.toolbar)) + slideOutVertically(tween(Motion.toolbar)) { it / 3 },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                        )
                    )
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp)
                        .padding(top = 26.dp, bottom = 26.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(
                        onClick = onPrevious,
                        enabled = canPrevious,
                        modifier = Modifier.size(Dimens.touchTarget)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "上一张",
                            tint = if (canPrevious) Color.White else Color.White.copy(alpha = 0.25f),
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    // 实心「导入」
                    Row(
                        Modifier
                            .defaultMinSize(minHeight = Dimens.touchTarget)
                            .background(colors.accentFill, RoundedCornerShape(Dimens.cornerCard))
                            .clickable(onClick = onDownload)
                            .padding(horizontal = 22.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            tint = colors.onAccentFill,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "导入",
                            color = colors.onAccentFill,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    // 幽灵「选择/已选」
                    Row(
                        Modifier
                            .defaultMinSize(minHeight = Dimens.touchTarget)
                            .background(
                                Color.White.copy(alpha = 0.14f),
                                RoundedCornerShape(Dimens.cornerCard)
                            )
                            .clickable { onSelect(currentItem) }
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (selected) colors.accentFill else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (selected) "已选" else "选择",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = onNext,
                        enabled = canNext,
                        modifier = Modifier.size(Dimens.touchTarget)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "下一张",
                            tint = if (canNext) Color.White else Color.White.copy(alpha = 0.25f),
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }
        }
    }
}
