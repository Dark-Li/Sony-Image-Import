package com.codex.sonyedge.ui.image

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.codex.sonyedge.ui.theme.PreviewBlack
import com.codex.sonyedge.ui.theme.SonyEdgeTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** 网格缩略图：单级加载 + 内存/磁盘缓存。 */
@Composable
fun RemoteCameraImage(
    url: String?,
    modifier: Modifier,
    contentScale: ContentScale,
    maxDimension: Int,
    trimLetterbox: Boolean = false
) {
    val context = LocalContext.current
    val bitmap by produceState(
        initialValue = CameraImageLoader.cachedBitmap(url, maxDimension, trimLetterbox),
        url, maxDimension, trimLetterbox
    ) {
        value = CameraImageLoader.loadBitmap(
            context.applicationContext,
            url.orEmpty(),
            maxDimension,
            trimLetterbox
        )
    }
    val renderedBitmap = bitmap
    if (renderedBitmap == null) {
        Box(modifier.background(SonyEdgeTheme.colors.surface2))
    } else {
        Image(
            bitmap = renderedBitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier.background(SonyEdgeTheme.colors.surface2)
        )
    }
}

/** 预览大图：缩略图先行 + 原图渐进替换，保留缩放手势。 */
@Composable
fun ProgressiveCameraImage(
    primaryUrl: String?,
    fallbackUrl: String?,
    modifier: Modifier,
    contentScale: ContentScale,
    maxDimension: Int,
    resetZoomKey: Any? = Unit,
    onZoomChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val primary = primaryUrl.orEmpty()
    val fallback = fallbackUrl.orEmpty().takeIf { it.isNotBlank() && it != primary }
    val fallbackBitmap by produceState(
        initialValue = fallback?.let { CameraImageLoader.cachedBitmap(it, 900) },
        fallback
    ) {
        value = fallback?.let { CameraImageLoader.loadBitmap(context.applicationContext, it, 900) }
    }
    val primaryBitmap by produceState(
        initialValue = CameraImageLoader.cachedBitmap(primary, maxDimension),
        primary, maxDimension
    ) {
        value = CameraImageLoader.loadBitmap(context.applicationContext, primary, maxDimension)
    }
    val bitmap = primaryBitmap ?: fallbackBitmap
    if (bitmap == null) {
        Box(modifier.background(PreviewBlack))
    } else {
        ZoomablePreviewImage(
            bitmap = bitmap,
            modifier = modifier,
            contentScale = contentScale,
            resetKey = resetZoomKey,
            onZoomChanged = onZoomChanged
        )
    }
}

/** 1×–5× 捏合缩放 + 双击缩放，原逻辑迁移（双击倍率调整为 2.2×）。 */
@Composable
fun ZoomablePreviewImage(
    bitmap: Bitmap,
    modifier: Modifier,
    contentScale: ContentScale,
    resetKey: Any?,
    onZoomChanged: (Boolean) -> Unit
) {
    var scale by remember(bitmap) { mutableStateOf(1f) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }
    var animateTransform by remember(bitmap) { mutableStateOf(false) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val transformSpec = tween<Float>(durationMillis = if (animateTransform) 180 else 0)
    val displayedScale by animateFloatAsState(
        targetValue = scale,
        animationSpec = transformSpec,
        label = "previewScale"
    )
    val displayedOffsetX by animateFloatAsState(
        targetValue = offset.x,
        animationSpec = transformSpec,
        label = "previewOffsetX"
    )
    val displayedOffsetY by animateFloatAsState(
        targetValue = offset.y,
        animationSpec = transformSpec,
        label = "previewOffsetY"
    )

    fun fittedImageSize(): Pair<Float, Float> {
        val containerWidth = containerSize.width.toFloat()
        val containerHeight = containerSize.height.toFloat()
        if (containerWidth <= 0f || containerHeight <= 0f || bitmap.width <= 0 || bitmap.height <= 0) {
            return containerWidth to containerHeight
        }
        if (contentScale != ContentScale.Fit) {
            return containerWidth to containerHeight
        }
        val imageScale = min(containerWidth / bitmap.width.toFloat(), containerHeight / bitmap.height.toFloat())
        return bitmap.width * imageScale to bitmap.height * imageScale
    }

    fun clampOffset(candidate: Offset, nextScale: Float): Offset {
        if (nextScale <= 1.01f || containerSize.width <= 0 || containerSize.height <= 0) return Offset.Zero
        val (imageWidth, imageHeight) = fittedImageSize()
        val maxX = max(0f, (imageWidth * nextScale - containerSize.width) / 2f)
        val maxY = max(0f, (imageHeight * nextScale - containerSize.height) / 2f)
        return Offset(
            x = candidate.x.coerceIn(-maxX, maxX),
            y = candidate.y.coerceIn(-maxY, maxY)
        )
    }

    LaunchedEffect(bitmap, resetKey) {
        animateTransform = false
        scale = 1f
        offset = Offset.Zero
        onZoomChanged(false)
    }
    LaunchedEffect(containerSize, bitmap, scale) {
        offset = clampOffset(offset, scale)
    }
    Box(
        modifier = modifier
            .background(PreviewBlack)
            .onSizeChanged { size ->
                containerSize = size
            }
            .pointerInput(bitmap, containerSize) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        val nextScale = if (scale > 1.01f) 1f else 2.2f
                        val nextOffset = if (nextScale <= 1.01f) {
                            Offset.Zero
                        } else {
                            val rawOffset = Offset(
                                x = (containerSize.width / 2f - tapOffset.x) * (nextScale - 1f),
                                y = (containerSize.height / 2f - tapOffset.y) * (nextScale - 1f)
                            )
                            clampOffset(rawOffset, nextScale)
                        }
                        animateTransform = true
                        scale = nextScale
                        offset = nextOffset
                        onZoomChanged(nextScale > 1.01f)
                    }
                )
            }
            .pointerInput(bitmap, containerSize) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var hasPressedPointers: Boolean
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        hasPressedPointers = pressed.isNotEmpty()
                        val currentScale = scale
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        if (pressed.size > 1 || currentScale > 1.01f) {
                            val nextScale = (currentScale * zoom).coerceIn(1f, 5f)
                            val zoomChanged = abs(nextScale - currentScale) > 0.001f
                            val nextOffset = if (nextScale <= 1.01f) {
                                Offset.Zero
                            } else {
                                clampOffset(offset + pan, nextScale)
                            }
                            animateTransform = false
                            scale = nextScale
                            offset = nextOffset
                            onZoomChanged(nextScale > 1.01f)
                            if (zoomChanged || pan != Offset.Zero) {
                                event.changes.forEach { change -> change.consume() }
                            }
                        }
                    } while (hasPressedPointers)
                    if (scale <= 1.01f) {
                        animateTransform = false
                        scale = 1f
                        offset = Offset.Zero
                        onZoomChanged(false)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val (imageWidthPx, imageHeightPx) = fittedImageSize()
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .size(
                    width = with(density) { max(1f, imageWidthPx).toDp() },
                    height = with(density) { max(1f, imageHeightPx).toDp() }
                )
                .graphicsLayer {
                    scaleX = displayedScale
                    scaleY = displayedScale
                    translationX = displayedOffsetX
                    translationY = displayedOffsetY
                }
        )
    }
}
