package com.archko.reader.pdf.component

import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import com.archko.reader.pdf.cache.ImageCache
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.APage
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs

public enum class Orientation {
    Vertical, Horizontal
}

@Composable
public fun DocumentView(
    list: MutableList<APage>,
    state: ImageDecoder,
    jumpToPage: Int? = null,
    align: PdfViewState.Align = PdfViewState.Align.Top,
    orientation: Orientation = Orientation.Vertical,
    onDocumentClosed: ((page: Int, pageCount: Int, zoom: Double) -> Unit)? = null,
    onDoubleTapToolbar: (() -> Unit)? = null, // 新增参数
    onPageChanged: ((page: Int) -> Unit)? = null // 新增页面变化回调
) {
    // 初始化状态
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var vZoom by remember { mutableFloatStateOf(1f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val keepPx = with(density) { 6.dp.toPx() }
    var flingJob by remember { mutableStateOf<Job?>(null) }
    
    // 跟踪上一次的orientation
    var lastOrientation by remember { mutableStateOf(orientation) }

    val pdfViewState = remember(list, orientation) {
        println("DocumentView: 创建新的PdfViewState:$viewSize, vZoom:$vZoom，list: ${list.size}, orientation: $orientation")
        PdfViewState(list, state, orientation)
    }
    // 确保在 list 或 orientation 变化时重新计算总高度
    LaunchedEffect(list, orientation) {
        if (viewSize != IntSize.Zero) {
            println("DocumentView: 更新ViewSize:$viewSize, vZoom:$vZoom, list: ${list.size}, orientation: $orientation")
            // 当orientation改变时，重置offset和zoom
            if (orientation != lastOrientation) {
                println("DocumentView: orientation改变，重置offset和zoom: $lastOrientation -> $orientation")
                offset = Offset.Zero
                vZoom = 1f
                lastOrientation = orientation
            }
            pdfViewState.updateViewSize(viewSize, vZoom, orientation)
        }
    }

    // jumpToPage 跳转逻辑 - 只在有明确跳转请求时执行
    LaunchedEffect(jumpToPage, pdfViewState.init, align, orientation) {
        println("DocumentView: jumpToPage:$jumpToPage, vZoom:$vZoom, init: ${pdfViewState.init}, orientation: $orientation")
        if (jumpToPage != null && pdfViewState.init) {
            println("DocumentView: 执行跳转到第 $jumpToPage 页, $offset")
            val page = pdfViewState.pages.getOrNull(jumpToPage)
            if (page != null) {
                if (orientation == Orientation.Vertical) {
                    val targetOffsetY = when (align) {
                        PdfViewState.Align.Top -> page.bounds.top
                        PdfViewState.Align.Center -> page.bounds.top - (viewSize.height - page.height) / 2
                        PdfViewState.Align.Bottom -> page.bounds.bottom - viewSize.height
                    }
                    val maxOffsetY = (pdfViewState.totalHeight - viewSize.height).coerceAtLeast(0f)
                    val clampedTargetY = targetOffsetY.coerceIn(0f, maxOffsetY)
                    val clampedY = -clampedTargetY
                    offset = Offset(offset.x, clampedY)
                } else {
                    val targetOffsetX = when (align) {
                        PdfViewState.Align.Top -> page.bounds.left
                        PdfViewState.Align.Center -> page.bounds.left - (viewSize.width - page.width) / 2
                        PdfViewState.Align.Bottom -> page.bounds.right - viewSize.width
                    }
                    val maxOffsetX = (pdfViewState.totalWidth - viewSize.width).coerceAtLeast(0f)
                    val clampedTargetX = targetOffsetX.coerceIn(0f, maxOffsetX)
                    val clampedX = -clampedTargetX
                    offset = Offset(clampedX, offset.y)
                }
                // 同步到PdfViewState
                println("DocumentView: 执行跳转到=$offset, ${page.bounds.top}")
                pdfViewState.updateOffset(offset)
            }
        }
    }

    // 监听页面变化并回调
    LaunchedEffect(offset, viewSize, orientation) {
        val pages = pdfViewState.pages
        if (pages.isNotEmpty()) {
            val offsetY = offset.y
            val offsetX = offset.x
            val firstVisible = pages.indexOfFirst { page ->
                if (orientation == Orientation.Vertical) {
                    val top = -offsetY
                    val bottom = top + viewSize.height
                    page.bounds.bottom > top && page.bounds.top < bottom
                } else {
                    val left = -offsetX
                    val right = left + viewSize.width
                    page.bounds.right > left && page.bounds.left < right
                }
            }
            if (firstVisible != -1) {
                onPageChanged?.invoke(firstVisible)
            }
        }
    }

    DisposableEffect(list) {
        onDispose {
            var lastPage = 0
            val pages = pdfViewState.pages
            if (pages.isNotEmpty()) {
                val offsetY = offset.y
                val offsetX = offset.x
                val firstVisible = pages.indexOfFirst { page ->
                    if (orientation == Orientation.Vertical) {
                        val top = -offsetY
                        val bottom = top + viewSize.height
                        page.bounds.bottom > top && page.bounds.top < bottom
                    } else {
                        val left = -offsetX
                        val right = left + viewSize.width
                        page.bounds.right > left && page.bounds.left < right
                    }
                }
                if (firstVisible != -1 && firstVisible != lastPage) {
                    lastPage = firstVisible
                }
            }

            val currentPage = lastPage
            val pageCount = list.size
            val zoom = vZoom.toDouble()
            println("DocumentView: shutdown:page:$currentPage, pc:$pageCount, $viewSize, vZoom:$vZoom, list: ${list.size}, orientation: $orientation")
            onDocumentClosed?.invoke(currentPage, pageCount, zoom)

            pdfViewState.shutdown()
            ImageCache.clear()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged {
                viewSize = it
                println("DocumentView: onSizeChanged:$viewSize, vZoom:$vZoom, list: ${list.size}, orientation: $orientation")
                pdfViewState.updateViewSize(viewSize, vZoom, orientation)
            }
            .pointerInput(viewSize, keepPx, orientation) {
                detectTapGestures(
                    onTap = { offsetTap ->
                        // 单击翻页逻辑
                        if (orientation == Orientation.Vertical) {
                            val y = offsetTap.y
                            val height = viewSize.height.toFloat()
                            when {
                                y < height / 4 -> {
                                    val newY = (offset.y + viewSize.height - keepPx).coerceAtMost(0f)
                                    offset = Offset(offset.x, newY)
                                    pdfViewState.updateOffset(offset)
                                }
                                y > height * 3 / 4 -> {
                                    val maxY = (pdfViewState.totalHeight - viewSize.height).coerceAtLeast(0f)
                                    val newY = (offset.y - viewSize.height + keepPx).coerceAtLeast(-maxY)
                                    offset = Offset(offset.x, newY)
                                    pdfViewState.updateOffset(offset)
                                }
                            }
                        } else {
                            val x = offsetTap.x
                            val width = viewSize.width.toFloat()
                            when {
                                x < width / 4 -> {
                                    val newX = (offset.x + viewSize.width - keepPx).coerceAtMost(0f)
                                    offset = Offset(newX, offset.y)
                                    pdfViewState.updateOffset(offset)
                                }
                                x > width * 3 / 4 -> {
                                    val maxX = if (orientation == Orientation.Vertical) {
                                        (pdfViewState.totalHeight - viewSize.height).coerceAtLeast(0f)
                                    } else {
                                        (pdfViewState.totalWidth - viewSize.width).coerceAtLeast(0f)
                                    }
                                    val newX = (offset.x - viewSize.width + keepPx).coerceAtLeast(-maxX)
                                    offset = Offset(newX, offset.y)
                                    pdfViewState.updateOffset(offset)
                                }
                            }
                        }
                    },
                    onDoubleTap = { offsetTap ->
                        val y = offsetTap.y
                        val height = viewSize.height.toFloat()
                        if (y >= height / 4 && y <= height * 3 / 4) {
                            onDoubleTapToolbar?.invoke()
                        }
                    }
                )
            },
        contentAlignment = Alignment.TopStart
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        var zooming = false
                        var initialZoom = vZoom
                        var initialOffset = offset
                        var lastCentroid = Offset.Zero
                        // pan惯性
                        val panVelocityTracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                        var pan = Offset.Zero
                        try {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            lastCentroid = down.position
                            initialOffset = offset
                            initialZoom = vZoom
                            pan = Offset.Zero
                            panVelocityTracker.resetTracking()
                            flingJob?.cancel()
                            do {
                                val event = awaitPointerEvent()
                                val pointerCount = event.changes.size
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                val centroid = event.calculateCentroid()
                                // 采集pan速度
                                val uptime = event.changes.maxByOrNull { it.uptimeMillis }?.uptimeMillis ?: 0L
                                pan += panChange
                                panVelocityTracker.addPosition(uptime, pan)
                                if (pointerCount > 1) {
                                    zooming = true
                                    val newZoom = (zoomChange * vZoom).coerceIn(1f, 10f)
                                    val zoomFactor = newZoom / vZoom
                                    
                                    // 计算缩放中心点：手势中心相对于内容的位置
                                    // centroid 是手势中心在视图中的位置
                                    // 需要将其转换为相对于内容的位置
                                    val contentCenterX = centroid.x - offset.x
                                    val contentCenterY = centroid.y - offset.y
                                    
                                    // 计算新的偏移量，保持内容中心点不变
                                    val newOffsetX = centroid.x - contentCenterX * zoomFactor
                                    val newOffsetY = centroid.y - contentCenterY * zoomFactor
                                    
                                    // 调试信息
                                    //println("缩放中心点: centroid=$centroid, contentCenter=($contentCenterX, $contentCenterY), zoomFactor=$zoomFactor")
                                    //println("偏移量: oldOffset=$offset, newOffset=($newOffsetX, $newOffsetY)")
                                    
                                    vZoom = newZoom
                                    offset = Offset(newOffsetX, newOffsetY)
                                    
                                    // 边界检查
                                    if (orientation == Orientation.Vertical) {
                                        val scaledWidth = viewSize.width * vZoom
                                        // 在缩放过程中，需要根据当前缩放比例调整总高度
                                        val scaleRatio = vZoom / pdfViewState.vZoom
                                        val scaledHeight = pdfViewState.totalHeight * scaleRatio
                                        val minX = minOf(0f, viewSize.width - scaledWidth)
                                        val maxX = 0f
                                        val minY =
                                            if (scaledHeight > viewSize.height) viewSize.height - scaledHeight else 0f
                                        val maxY = 0f
                                        offset = Offset(
                                            offset.x.coerceIn(minX, maxX),
                                            offset.y.coerceIn(minY, maxY)
                                        )
                                    } else {
                                        val scaledHeight = viewSize.height * vZoom
                                        val scaleRatio = vZoom / pdfViewState.vZoom
                                        val scaledWidth = pdfViewState.totalWidth * scaleRatio
                                        val minY = minOf(0f, viewSize.height - scaledHeight)
                                        val maxY = 0f
                                        val minX =
                                            if (scaledWidth > viewSize.width) viewSize.width - scaledWidth else 0f
                                        val maxX = 0f
                                        offset = Offset(
                                            offset.x.coerceIn(minX, maxX),
                                            offset.y.coerceIn(minY, maxY)
                                        )
                                    }
                                    pdfViewState.updateOffset(offset)
                                    // 缩放过程中不调用 updateViewSize，避免频繁的页面重新计算
                                    // pdfViewState.updateViewSize(viewSize, vZoom)
                                    event.changes.fastForEach { if (it.positionChanged()) it.consume() }
                                } else {
                                    // 单指拖动
                                    if (!zooming) {
                                        offset += panChange
                                        if (orientation == Orientation.Vertical) {
                                            val scaledWidth = viewSize.width * vZoom
                                            val scaledHeight = pdfViewState.totalHeight
                                            val minX = minOf(0f, viewSize.width - scaledWidth)
                                            val maxX = 0f
                                            val minY =
                                                if (scaledHeight > viewSize.height) viewSize.height - scaledHeight else 0f
                                            val maxY = 0f
                                            offset = Offset(
                                                offset.x.coerceIn(minX, maxX),
                                                offset.y.coerceIn(minY, maxY)
                                            )
                                        } else {
                                            val scaledHeight = viewSize.height * vZoom
                                            val scaledWidth = pdfViewState.totalWidth
                                            val minY = minOf(0f, viewSize.height - scaledHeight)
                                            val maxY = 0f
                                            val minX =
                                                if (scaledWidth > viewSize.width) viewSize.width - scaledWidth else 0f
                                            val maxX = 0f
                                            offset = Offset(
                                                offset.x.coerceIn(minX, maxX),
                                                offset.y.coerceIn(minY, maxY)
                                            )
                                        }
                                        pdfViewState.updateOffset(offset)
                                    }
                                }
                                event.changes.fastForEach { if (it.positionChanged()) it.consume() }
                            } while (event.changes.fastAny { it.pressed })
                        } catch (_: CancellationException) {
                        } finally {
                            // 缩放结束后调用 updateViewSize 重新计算页面
                            if (zooming) {
                                pdfViewState.updateViewSize(viewSize, vZoom, orientation)
                            }
                            // 计算pan velocity
                            val velocity =
                                runCatching { panVelocityTracker.calculateVelocity() }.getOrDefault(Velocity.Zero)
                            //val velocitySquared = velocity.x * velocity.x + velocity.y * velocity.y
                            //val velocityThreshold = with(density) { 50.dp.toPx() * 50.dp.toPx() }
                            flingJob?.cancel()
                            //if (velocitySquared > velocityThreshold) {
                            val decayAnimationSpec = exponentialDecay<Float>(frictionMultiplier = 0.35f)
                            flingJob = scope.launch {
                                if (orientation == Orientation.Vertical) {
                                    // X方向
                                    if (abs(velocity.x) > 50f) {
                                        val animX = androidx.compose.animation.core.AnimationState(
                                            initialValue = offset.x,
                                            initialVelocity = velocity.x
                                        )
                                        launch {
                                            animX.animateDecay(decayAnimationSpec) {
                                                val scaledWidth = viewSize.width * vZoom
                                                val minX = minOf(0f, viewSize.width - scaledWidth)
                                                val maxX = 0f
                                                val newX = value.coerceIn(minX, maxX)
                                                offset = Offset(newX, offset.y)
                                                pdfViewState.updateOffset(offset)
                                            }
                                        }
                                    }
                                    // Y方向
                                    if (abs(velocity.y) > 50f) {
                                        val animY = androidx.compose.animation.core.AnimationState(
                                            initialValue = offset.y,
                                            initialVelocity = velocity.y
                                        )
                                        launch {
                                            animY.animateDecay(decayAnimationSpec) {
                                                val scaledHeight = if (orientation == Orientation.Vertical) {
                                                    pdfViewState.totalHeight
                                                } else {
                                                    pdfViewState.totalWidth
                                                }
                                                val minY =
                                                    if (scaledHeight > viewSize.height) viewSize.height - scaledHeight else 0f
                                                val maxY = 0f
                                                val newY = value.coerceIn(minY, maxY)
                                                offset = Offset(offset.x, newY)
                                                pdfViewState.updateOffset(offset)
                                            }
                                        }
                                    }
                                } else {
                                    // X方向
                                    if (abs(velocity.x) > 50f) {
                                        val animX = androidx.compose.animation.core.AnimationState(
                                            initialValue = offset.x,
                                            initialVelocity = velocity.x
                                        )
                                        launch {
                                            animX.animateDecay(decayAnimationSpec) {
                                                val scaledWidth = if (orientation == Orientation.Vertical) {
                                                    pdfViewState.totalHeight
                                                } else {
                                                    pdfViewState.totalWidth
                                                }
                                                val minX =
                                                    if (scaledWidth > viewSize.width) viewSize.width - scaledWidth else 0f
                                                val maxX = 0f
                                                val newX = value.coerceIn(minX, maxX)
                                                offset = Offset(newX, offset.y)
                                                pdfViewState.updateOffset(offset)
                                            }
                                        }
                                    }
                                    // Y方向
                                    if (abs(velocity.y) > 50f) {
                                        val animY = androidx.compose.animation.core.AnimationState(
                                            initialValue = offset.y,
                                            initialVelocity = velocity.y
                                        )
                                        launch {
                                            animY.animateDecay(decayAnimationSpec) {
                                                val scaledHeight = viewSize.height * vZoom
                                                val minY = minOf(0f, viewSize.height - scaledHeight)
                                                val maxY = 0f
                                                val newY = value.coerceIn(minY, maxY)
                                                offset = Offset(offset.x, newY)
                                                pdfViewState.updateOffset(offset)
                                            }
                                        }
                                    }
                                }
                            }
                            //}
                        }
                    }
                }
        ) {
            translate(left = offset.x, top = offset.y) {
                //只绘制可见区域.
                /*val visibleRect = Rect(
                    left = -offset.x,
                    top = -offset.y,
                    right = size.width - offset.x,
                    bottom = size.height - offset.y
                )
                drawRect(
                    brush = gradientBrush,
                    topLeft = visibleRect.topLeft,
                    size = visibleRect.size
                )*/
                pdfViewState.drawVisiblePages(this, offset, vZoom, viewSize)
            }
        }
    }
}