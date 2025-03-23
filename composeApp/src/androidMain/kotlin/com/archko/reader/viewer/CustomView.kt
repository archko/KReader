package com.archko.reader.viewer

import androidx.compose.animation.core.animateDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import com.archko.reader.pdf.entity.APage
import com.archko.reader.pdf.flinger.FlingConfiguration
import com.archko.reader.pdf.flinger.SplineBasedFloatDecayAnimationSpec
import com.archko.reader.pdf.state.PdfState
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

// 将 Page 类重命名为 PageNode
private class PageNode(
    public var rect: Rect,
    public val aPage: APage  // 添加 APage 属性
) {
    public fun draw(drawScope: DrawScope, offset: Offset) {
        // 检查页面是否在可视区域内
        if (!isVisible(drawScope)) {
            //println("is not Visible:${aPage.index}, $offset, $rect")
            return
        }

        // 绘制边框
        drawScope.drawRect(
            color = Color.Green,
            topLeft = rect.topLeft,
            size = rect.size,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8f)
        )

        // 绘制 ID
        /*drawScope.drawContext.canvas.nativeCanvas.drawText(
            aPage.index.toString(),
            rect.topLeft.x + rect.size.width / 2,
            rect.topLeft.y + rect.size.height / 2,
            android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 60f
                textAlign = android.graphics.Paint.Align.CENTER
            }
        )*/
    }

    private fun isVisible(drawScope: DrawScope): Boolean {
        // 获取画布的可视区域
        val visibleRect = Rect(
            left = 0f,
            top = 0f,
            right = drawScope.size.width,
            bottom = drawScope.size.height
        )

        // 检查页面是否与可视区域相交
        return rect.intersectsWith(visibleRect)
    }
}

private fun Rect.intersectsWith(other: Rect): Boolean {
    return !(left > other.right ||
            right < other.left ||
            top > other.bottom ||
            bottom < other.top)
}

private class Page(
    private var viewSize: IntSize,
    private var zoom: Float,
    private var offset: Offset,
    private var aPage: APage,
    private var pageOffset: Float = 0f
) {
    private var nodes: List<PageNode> = emptyList()
    private var lastContentOffset: Offset = Offset.Zero

    public fun update(viewSize: IntSize, zoom: Float, offset: Offset, pageOffset: Float) {
        val isViewSizeChanged = this.viewSize != viewSize
        val isZoomChanged = this.zoom != zoom
        val offsetChanged = this.offset != offset

        this.viewSize = viewSize
        this.zoom = zoom
        this.offset = offset
        this.pageOffset = pageOffset

        if (isViewSizeChanged || isZoomChanged || offsetChanged || nodes.isEmpty()) {
            recalculateNodes()
        }
    }

    public fun draw(drawScope: DrawScope, offset: Offset) {
        nodes.forEach { node ->
            node.draw(drawScope, offset)
            val contentOffset = calculateContentOffset()
            drawScope.drawContext.canvas.nativeCanvas.drawText(
                aPage.index.toString(),
                contentOffset.x + drawScope.size.width / 2,
                contentOffset.y + drawScope.size.height / 2,
                android.graphics.Paint().apply {
                    color = android.graphics.Color.YELLOW
                    textSize = 160f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
            )
        }
    }

    public fun updateOffset(newOffset: Offset) {
        this.offset = newOffset
        updateNodesPosition()
    }

    private fun updateNodesPosition() {
        val contentOffset = calculateContentOffset()
        if (contentOffset != lastContentOffset) {
            nodes.forEach { node ->
                node.rect = node.rect.translate(contentOffset - lastContentOffset)
            }
            lastContentOffset = contentOffset
        }
    }

    private fun calculateContentOffset(): Offset {
        val viewWidth = viewSize.width.toFloat()
        val pageScale = viewWidth / aPage.width
        val pageHeight = aPage.height * pageScale
        val scaledWidth = viewWidth * zoom
        val scaledHeight = pageHeight * zoom

        return Offset(
            (viewSize.width - scaledWidth) / 2 + offset.x,
            pageOffset * zoom + offset.y
        )
    }

    private fun recalculateNodes() {
        if (viewSize == IntSize.Zero) return

        val contentOffset = calculateContentOffset()
        lastContentOffset = contentOffset

        val viewWidth = viewSize.width.toFloat()
        val pageScale = viewWidth / aPage.width
        val scaledWidth = viewWidth * zoom
        val scaledHeight = aPage.height * pageScale * zoom

        val rootNode = PageNode(
            Rect(
                left = contentOffset.x,
                top = contentOffset.y,
                right = contentOffset.x + scaledWidth,
                bottom = contentOffset.y + scaledHeight
            ),
            aPage
        )

        nodes = calculatePages(rootNode)
    }

    private fun calculatePages(page: PageNode): List<PageNode> {
        val rect = page.rect
        if (rect.width * rect.height > maxSize) {
            val halfWidth = rect.width / 2
            val halfHeight = rect.height / 2
            return listOf(
                PageNode(
                    Rect(rect.left, rect.top, rect.left + halfWidth, rect.top + halfHeight),
                    page.aPage
                ),
                PageNode(
                    Rect(rect.left + halfWidth, rect.top, rect.right, rect.top + halfHeight),
                    page.aPage
                ),
                PageNode(
                    Rect(rect.left, rect.top + halfHeight, rect.left + halfWidth, rect.bottom),
                    page.aPage
                ),
                PageNode(
                    Rect(rect.left + halfWidth, rect.top + halfHeight, rect.right, rect.bottom),
                    page.aPage
                )
            ).flatMap {
                calculatePages(it)
            }
        }
        return listOf(page)
    }

    override fun toString(): String {
        //return "Page(viewSize=$viewSize, zoom=$zoom, offset=$offset, aPage=$aPage, pageOffset=$pageOffset, lastContentOffset=$lastContentOffset)"
        return "Page(viewSize=$viewSize, offset=$offset"
    }

    public companion object {
        private const val maxSize = 256 * 384 * 4f * 2
    }
}

private class PdfViewState(
    public val list: List<APage>,
    public val state: PdfState,
    public var viewSize: IntSize,
) {
    public var init: Boolean by mutableStateOf(false)
    public var totalHeight: Float by mutableFloatStateOf(0f)

    public var pages: List<Page> by mutableStateOf(createPages())

    //public var viewSize: IntSize by mutableStateOf(IntSize.Zero)
    public var vZoom: Float by mutableFloatStateOf(1f)
    var pagePositions: List<Float> by mutableStateOf(createPositions())

    private fun createPositions(): List<Float> {
        return emptyList<Float>()
    }

    fun updatePositions() {
        if (viewSize.width == 0) emptyList<Float>() else {
            var currentY = 0f
            pagePositions = list.map { aPage ->
                val pageScale = viewSize.width.toFloat() / aPage.width
                val pageHeight = aPage.height * pageScale
                val position = currentY
                currentY += pageHeight
                position
            }
        }
        println("PdfViewState.updatePositions:$pagePositions")
    }

    fun updateHeight() {
        if (pagePositions.isEmpty()) 0f else {
            var currentY = 0f
            list.forEachIndexed { index, aPage ->
                val pageScale = viewSize.width.toFloat() / aPage.width
                currentY += aPage.height * pageScale
            }
            totalHeight = currentY
        }
    }

    public fun invalidatePageSizes() {
        updatePositions()
        updateHeight()
        updatePage()
        var currentY = 0f
        /*if (viewSize.width == 0 || viewSize.height == 0 || list.isEmpty()) {
            println("PdfViewState.viewSize高宽为0,或list为空,不计算page: viewSize:$viewSize, list:$list, totalHeight:$totalHeight")
            totalHeight = viewSize.height.toFloat()
            init = false
        } else {
            list.zip(pages).forEach { (aPage, page) ->
                val pageWidth = viewSize.width * vZoom
                val pageScale = pageWidth / aPage.width
                val pageHeight = aPage.height * pageScale
                val bounds = Rect(
                    0f, currentY,
                    pageWidth,
                    currentY + pageHeight
                )
                currentY += pageHeight
                //page.update(viewSize, vZoom, bounds)
                //println("PdfViewState.bounds:$currentY, bounds:$bounds, page:${page.bounds}")
            }
            init = true
        }
        totalHeight = currentY*/
        println("invalidatePageSizes.totalHeight:$totalHeight, zoom:$vZoom, viewSize:$viewSize")
    }

    fun updatePage() {
        if (pages.isEmpty()) {
            pages = list.zip(pagePositions).map { (aPage, yPos) ->
                Page(viewSize, 1f, Offset.Zero, aPage, yPos)
            }
        }
        pages.zip(pagePositions).map { (page, yPos) ->
            page.update(viewSize, 1f, Offset(0f, yPos), yPos)
        }
        println("PdfViewState.updatePage:$pages")
    }

    private fun createPages(): List<Page> {
        return emptyList()
    }

    public fun updateViewSize(viewSize: IntSize, vZoom: Float) {
        val isViewSizeChanged = this.viewSize != viewSize
        val isZoomChanged = this.vZoom != vZoom

        this.viewSize = viewSize
        this.vZoom = vZoom
        if (isViewSizeChanged || isZoomChanged || totalHeight == 0f) {
            invalidatePageSizes()
            println("PdfViewState.viewSize变化: vZoom:$vZoom, totalHeight:$totalHeight, viewSize:$viewSize")
        } else {
            println("PdfViewState.viewSize未变化: vZoom:$vZoom, totalHeight:$totalHeight, viewSize:$viewSize")
        }
    }
}

@Composable
fun CustomView(
    list: MutableList<APage>,
    state: PdfState
) {
    // 初始化状态
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var vZoom by remember { mutableFloatStateOf(1f) }
    val velocityTracker = remember { VelocityTracker() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var flingJob by remember { mutableStateOf<Job?>(null) }

    val pdfState = remember(list) {
        println("DocumentView: 创建新的PdfViewState:$viewSize, vZoom:$vZoom，list: ${list.size}")
        PdfViewState(list, state, viewSize)
    }
    // 确保在 list 变化时重新计算总高度
    LaunchedEffect(list, viewSize) {
        if (viewSize != IntSize.Zero) {
            println("DocumentView: 更新ViewSize:$viewSize, vZoom:$vZoom, list: ${list.size}")
            pdfState.updateViewSize(viewSize, vZoom)
        }
    }

    //这里存在pages的变化监听不到的问题.

    // 定义背景渐变
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(Color.Green, Color.Red)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged {
                viewSize = it
                pdfState.pages.zip(pdfState.pagePositions).forEach { (page, yPos) ->
                    page.update(viewSize, vZoom, offset, yPos)
                }
            },
        contentAlignment = Alignment.TopStart
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        var wasCancelled = false

                        try {
                            var zoom = 1f
                            var pastTouchSlop = false

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
                                        val scaledHeight = pdfState.totalHeight * vZoom

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
                                        pdfState.pages.forEach { page ->
                                            page.updateOffset(offset)
                                        }

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
                                            val scaledHeight = pdfState.totalHeight/* newZoom*/
                                            val maxX =
                                                (scaledWidth - viewSize.width).coerceAtLeast(0f) / 2
                                            val maxY =
                                                (scaledHeight - viewSize.height).coerceAtLeast(0f)

                                            // 更新偏移量
                                            offset = Offset(
                                                newOffset.x.coerceIn(-maxX, maxX),
                                                newOffset.y.coerceIn(-maxY, 0f)
                                            )

                                            // 更新状态
                                            vZoom = newZoom
                                            println("zoom:$vZoom, $centroid, $offset")
                                            pdfState.updateViewSize(viewSize, vZoom)
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
                                val scaledHeight = pdfState.totalHeight * vZoom
                                val maxX = (scaledWidth - viewSize.width).coerceAtLeast(0f) / 2
                                val maxY = (scaledHeight - viewSize.height).coerceAtLeast(0f)

                                // 创建两个协程同时处理x和y方向的动画
                                launch {
                                    if (kotlin.math.abs(velocity.x) > 50f) {  // 添加最小速度阈值
                                        animateDecay(
                                            initialValue = offset.x,
                                            initialVelocity = velocity.x,
                                            animationSpec = decayAnimationSpec
                                        ) { value, _ ->
                                            offset = offset.copy(x = value.coerceIn(-maxX, maxX))
                                            pdfState.pages.forEach { page ->
                                                page.updateOffset(
                                                    offset
                                                )
                                            }
                                        }
                                    }
                                }

                                launch {
                                    if (kotlin.math.abs(velocity.y) > 50f) {  // 添加最小速度阈值
                                        animateDecay(
                                            initialValue = offset.y,
                                            initialVelocity = velocity.y,
                                            animationSpec = decayAnimationSpec
                                        ) { value, _ ->
                                            offset = offset.copy(y = value.coerceIn(-maxY, 0f))
                                            pdfState.pages.forEach { page ->
                                                page.updateOffset(
                                                    offset
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            val scaledHeight = pdfState.totalHeight * vZoom

            translate(left = offset.x, top = offset.y) {
                //只绘制可见区域.
                val visibleRect = Rect(
                    left = -offset.x,
                    top = -offset.y,
                    right = size.width - offset.x,
                    bottom = size.height - offset.y
                )
                drawRect(
                    brush = gradientBrush,
                    topLeft = visibleRect.topLeft,
                    size = visibleRect.size
                )
            }
            /*drawRect(
                brush = gradientBrush,
                topLeft = Offset(
                    (size.width - viewSize.width * vZoom) / 2 + offset.x,
                    offset.y
                ),
                size = Size(
                    width = viewSize.width * vZoom,
                    height = scaledHeight
                )
            )*/

            pdfState.pages.forEach { page -> page.draw(this, offset) }
        }
    }
}