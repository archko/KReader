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

@Composable
public fun DocumentView(
    list: MutableList<APage>,
    state: ImageDecoder,
    jumpToPage: Int? = null,
    align: PdfViewState.Align = PdfViewState.Align.Top,
    onDocumentClosed: ((page: Int, pageCount: Int, zoom: Double) -> Unit)? = null
) {
    // 初始化状态
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var vZoom by remember { mutableFloatStateOf(1f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val keepPx = with(density) { 6.dp.toPx() }
    var flingJob by remember { mutableStateOf<Job?>(null) }

    val pdfViewState = remember(list) {
        println("DocumentView: 创建新的PdfViewState:$viewSize, vZoom:$vZoom，list: ${list.size}")
        PdfViewState(list, state)
    }
    // 确保在 list 变化时重新计算总高度
    LaunchedEffect(list) {
        if (viewSize != IntSize.Zero) {
            println("DocumentView: 更新ViewSize:$viewSize, vZoom:$vZoom, list: ${list.size}")
            pdfViewState.updateViewSize(viewSize, vZoom)
        }
    }

    // jumpToPage 只在页面真正初始化完成后且仅执行一次
    var didJump by remember { mutableStateOf(false) }
    LaunchedEffect(pdfViewState.init, jumpToPage, align) {
        println("DocumentView: jumpToPage:$jumpToPage, vZoom:$vZoom, init: ${pdfViewState.init}")
        if (!didJump && jumpToPage != null && pdfViewState.init) {
            pdfViewState.goToPage(jumpToPage, align)
            didJump = true
        }
    }

    DisposableEffect(list) {
        onDispose {
            var lastPage = 0
            val pages = pdfViewState.pages
            if (pages.isNotEmpty()) {
                val offsetY = offset.y
                val firstVisible = pages.indexOfFirst { page ->
                    val top = -offsetY
                    val bottom = top + viewSize.height
                    page.bounds.bottom > top && page.bounds.top < bottom
                }
                if (firstVisible != -1 && firstVisible != lastPage) {
                    lastPage = firstVisible
                }
            }

            val currentPage = lastPage
            val pageCount = list.size
            val zoom = vZoom.toDouble()
            println("DocumentView: shutdown:page:$currentPage, pc:$pageCount, $viewSize, vZoom:$vZoom, list: ${list.size}")
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
                println("DocumentView: onSizeChanged:$viewSize, vZoom:$vZoom, list: ${list.size}")
                pdfViewState.updateViewSize(viewSize, vZoom)
            }
            .pointerInput(viewSize, keepPx) {
                detectTapGestures { offsetTap ->
                    val y = offsetTap.y
                    val height = viewSize.height.toFloat()
                    when {
                        y < height / 4 -> {
                            // 顶部1/4，向上滚动一屏，保留6dp
                            val newY = (offset.y + viewSize.height - keepPx).coerceAtMost(0f)
                            offset = Offset(offset.x, newY)
                            pdfViewState.updateOffset(offset)
                        }

                        y > height * 3 / 4 -> {
                            // 底部1/4，向下滚动一屏，保留6dp
                            val maxY =
                                (pdfViewState.totalHeight - viewSize.height).coerceAtLeast(0f)
                            val newY = (offset.y - viewSize.height + keepPx).coerceAtLeast(-maxY)
                            offset = Offset(offset.x, newY)
                            pdfViewState.updateOffset(offset)
                        }
                    }
                }
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
                                    val newZoom = (zoomChange * vZoom).coerceIn(1f, 5f)
                                    val zoomFactor = newZoom / vZoom
                                    val newOffset = centroid + (offset - centroid) * zoomFactor
                                    vZoom = newZoom
                                    offset = newOffset
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
                                    pdfViewState.updateOffset(offset)
                                    pdfViewState.updateViewSize(viewSize, vZoom)
                                    event.changes.fastForEach { if (it.positionChanged()) it.consume() }
                                } else {
                                    // 单指拖动
                                    if (!zooming) {
                                        offset += panChange
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
                                        pdfViewState.updateOffset(offset)
                                    }
                                }
                                event.changes.fastForEach { if (it.positionChanged()) it.consume() }
                            } while (event.changes.fastAny { it.pressed })
                        } catch (_: CancellationException) {
                        } finally {
                            // 计算pan velocity
                            val velocity =
                                runCatching { panVelocityTracker.calculateVelocity() }.getOrDefault(Velocity.Zero)
                            //val velocitySquared = velocity.x * velocity.x + velocity.y * velocity.y
                            //val velocityThreshold = with(density) { 50.dp.toPx() * 50.dp.toPx() }
                            flingJob?.cancel()
                            //if (velocitySquared > velocityThreshold) {
                            val decayAnimationSpec = exponentialDecay<Float>(frictionMultiplier = 0.35f)
                            flingJob = scope.launch {
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
                                            val scaledHeight = pdfViewState.totalHeight
                                            val minY =
                                                if (scaledHeight > viewSize.height) viewSize.height - scaledHeight else 0f
                                            val maxY = 0f
                                            val newY = value.coerceIn(minY, maxY)
                                            offset = Offset(offset.x, newY)
                                            pdfViewState.updateOffset(offset)
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