package com.archko.reader.pdf.component

import androidx.compose.animation.core.animateDecay
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import com.archko.reader.pdf.cache.ImageCache
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.APage
import com.archko.reader.pdf.flinger.FlingConfiguration
import com.archko.reader.pdf.flinger.SplineBasedFloatDecayAnimationSpec
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable.isActive
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
    val velocityTracker = remember { VelocityTracker() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val keepPx = with(density) { 6.dp.toPx() }
    var flingJob by remember { mutableStateOf<Job?>(null) }

    val pdfViewState = remember(list) {
        println("DocumentView: 创建新的PdfViewState:$viewSize, vZoom:$vZoom，list: ${list.size}")
        PdfViewState(list, state)
    }
    // 确保在 list 变化时重新计算总高度
    LaunchedEffect(list, viewSize) {
        if (viewSize != IntSize.Zero) {
            println("DocumentView: 更新ViewSize:$viewSize, vZoom:$vZoom, list: ${list.size}")
            pdfViewState.updateViewSize(viewSize, vZoom)
        }
    }

    // 外部传入jumpToPage时自动跳转
    LaunchedEffect(jumpToPage, align) {
        if (jumpToPage != null) {
            pdfViewState.goToPage(jumpToPage, align)
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
                            val maxY = (pdfViewState.totalHeight - viewSize.height).coerceAtLeast(0f)
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
                        var wasCancelled = false
                        var zoom = 1f
                        var pastTouchSlop = false
                        try {
                            // 等待第一个触点
                            val trackingPointerId = awaitFirstDown(requireUnconsumed = false).id
                            var pointerCount = 1
                            do {
                                flingJob?.cancel()
                                flingJob = null
                                val event = awaitPointerEvent()
                                val canceled = event.changes.fastAny { it.isConsumed }
                                pointerCount = event.changes.size
                                if (!canceled) {
                                    event.changes.fastForEach {
                                        if (it.id == trackingPointerId) {
                                            velocityTracker.addPointerInputChange(it)
                                        }
                                    }
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    if (!pastTouchSlop) {
                                        zoom *= zoomChange
                                        //pan += panChange
                                        val scaledWidth = viewSize.width * vZoom
                                        val scaledHeight = pdfViewState.totalHeight * vZoom

                                        // 计算最大滚动范围
                                        val maxX =
                                            (scaledWidth - viewSize.width).coerceAtLeast(0f) / 2
                                        val maxY =
                                            (scaledHeight - viewSize.height).coerceAtLeast(
                                                0f
                                            )

                                        // 更新偏移量
                                        offset = Offset(
                                            (offset.x + panChange.x).coerceIn(-maxX, maxX),
                                            (offset.y + panChange.y).coerceIn(-maxY, 0f)
                                        )

                                        // 更新页面位置
                                        /*pages.forEach { page ->
                                            page.updateOffset(offset)
                                        }*/
                                        pdfViewState.updateOffset(offset)

                                        if (event.changes.size > 1) {
                                            pastTouchSlop = true
                                        }
                                    }
                                    if (pastTouchSlop) {    //zoom
                                        val centroid = event.calculateCentroid(useCurrent = false)
                                        if (zoomChange != 1f) {
                                            val newZoom = (zoomChange * vZoom).coerceIn(1f, 5f)
                                            // 计算缩放前后的偏移量变化
                                            val zoomFactor = newZoom / vZoom
                                            val newOffset = Offset(
                                                centroid.x + (offset.x - centroid.x) * zoomFactor,
                                                centroid.y + (offset.y - centroid.y) * zoomFactor
                                            )

                                            // 计算最大滚动范围
                                            val scaledWidth = viewSize.width * newZoom
                                            val scaledHeight = pdfViewState.totalHeight/* newZoom*/
                                            val maxX =
                                                (scaledWidth - viewSize.width).coerceAtLeast(0f) / 2
                                            val maxY =
                                                (scaledHeight - viewSize.height).coerceAtLeast(0f)

                                            // 更新偏移量
                                            offset = Offset(
                                                newOffset.x.coerceIn(-maxX, maxX),
                                                newOffset.y.coerceIn(-maxY, 0f)
                                            )

                                            vZoom = newZoom
                                            println("DocumentView.zoom:$vZoom, $centroid, $offset")
                                            pdfViewState.updateOffset(offset)
                                        }
                                        event.changes.fastForEach {
                                            if (it.positionChanged()) {
                                                it.consume()
                                            }
                                        }
                                    }
                                }
                                val finalEvent = awaitPointerEvent(pass = PointerEventPass.Final)
                                val finallyCanceled =
                                    finalEvent.changes.fastAny { it.isConsumed } && !pastTouchSlop
                            } while (!canceled && !finallyCanceled && event.changes.fastAny { it.pressed })
                        } catch (exception: CancellationException) {
                            wasCancelled = true
                            if (!isActive) throw exception
                        } finally {
                            // 计算最终速度
                            val velocity = velocityTracker.calculateVelocity()
                            velocityTracker.resetTracking()

                            // 缩放手势结束时调用 updateViewSize
                            if (pastTouchSlop) {
                                pdfViewState.updateViewSize(viewSize, vZoom)
                            }

                            // 创建优化后的decay动画spec
                            val decayAnimationSpec = SplineBasedFloatDecayAnimationSpec(
                                density = density,
                                scrollConfiguration = FlingConfiguration.Builder()
                                    .scrollViewFriction(0.01f)  // 减小摩擦力，使滑动更流畅
                                    .numberOfSplinePoints(150)  // 提高采样率
                                    .splineInflection(0.1f)
                                    .splineStartTension(0.2f)
                                    .splineEndTension(1f)
                                    .build()
                            )

                            flingJob = scope.launch {
                                // 同时处理水平和垂直方向的惯性滑动
                                val scaledWidth = viewSize.width * vZoom
                                val scaledHeight = pdfViewState.totalHeight * vZoom
                                val maxX = (scaledWidth - viewSize.width).coerceAtLeast(0f) / 2
                                val maxY = (scaledHeight - viewSize.height).coerceAtLeast(0f)

                                // 创建两个协程同时处理x和y方向的动画
                                launch {
                                    if (abs(velocity.x) > 50f) {  // 添加最小速度阈值
                                        animateDecay(
                                            initialValue = offset.x,
                                            initialVelocity = velocity.x,
                                            animationSpec = decayAnimationSpec
                                        ) { value, _ ->
                                            offset = offset.copy(x = value.coerceIn(-maxX, maxX))
                                            pdfViewState.updateOffset(offset)
                                        }
                                    }
                                }

                                launch {
                                    if (abs(velocity.y) > 50f) {  // 添加最小速度阈值
                                        animateDecay(
                                            initialValue = offset.y,
                                            initialVelocity = velocity.y,
                                            animationSpec = decayAnimationSpec
                                        ) { value, _ ->
                                            offset = offset.copy(y = value.coerceIn(-maxY, 0f))
                                            pdfViewState.updateOffset(offset)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            if (pdfViewState.update > 0)
            //println("state:Canvas:${pdfState.tilesToRender.size}")
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
                    pdfViewState.pages.forEach { page ->
                        page.draw(this, offset, pdfViewState.vZoom)
                    }
                }
        }
    }
}