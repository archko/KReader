package com.archko.reader.viewer

import androidx.compose.animation.core.animateDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import com.archko.reader.pdf.component.ImageCache
import com.archko.reader.pdf.entity.APage
import com.archko.reader.pdf.flinger.FlingConfiguration
import com.archko.reader.pdf.flinger.SplineBasedFloatDecayAnimationSpec
import com.archko.reader.pdf.state.PdfState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable.isActive
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import ovh.plrapps.mapcompose.core.throttle
import java.util.concurrent.Executors
import kotlin.coroutines.cancellation.CancellationException

// 将 Page 类重命名为 PageNode
private class PageNode(
    private val pdfViewState: PdfViewState,
    public var rect: Rect,
    public val aPage: APage  // 添加 APage 属性
) {
    private var isDecoding = false
    val cacheKey = "${aPage.index}-${rect}-${aPage.scale}"
    public fun draw(drawScope: DrawScope, offset: Offset) {
        // 检查页面是否在可视区域内
        if (!isVisible(drawScope, offset, rect, aPage.index)) {
            //println("is not Visible:${aPage.index}, $offset, $rect")
            pdfViewState.remove(rect, aPage, cacheKey)
            return
        }
        var loadedBitmap: ImageBitmap? = ImageCache.get(cacheKey)
        if (loadedBitmap != null) {
            //println("pageNode.draw.loadedBitmap:$rect, $aPage")
            isDecoding = false
            drawScope.drawImage(
                loadedBitmap,
                dstOffset = IntOffset(rect.left.toInt(), rect.top.toInt())
            )
        } else {
            if (!isDecoding) {
                isDecoding = true
                pdfViewState.decode(rect, aPage, cacheKey)
            }
            //println("pageNode.draw.offset:$offset, $rect, $aPage")
            // 绘制边框
            drawScope.drawRect(
                color = Color.Magenta,
                topLeft = Offset(rect.left, rect.top),
                size = rect.size,
                style = Stroke(width = 6f)
            )

            drawScope.drawContext.canvas.nativeCanvas.drawText(
                "page:${aPage.index}, scale:${aPage.scale},",
                rect.topLeft.x + rect.size.width / 2,
                rect.topLeft.y + rect.size.height / 2,
                android.graphics.Paint().apply {
                    color = android.graphics.Color.MAGENTA
                    textSize = 40f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
            )
            drawScope.drawContext.canvas.nativeCanvas.drawText(
                "${rect.width}-${rect.height}",
                rect.topLeft.x + rect.size.width / 2,
                rect.topLeft.y + rect.size.height / 2 + 60,
                android.graphics.Paint().apply {
                    color = android.graphics.Color.MAGENTA
                    textSize = 36f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
            )
            drawScope.drawContext.canvas.nativeCanvas.drawText(
                rect.toString2(),
                rect.topLeft.x + rect.size.width / 2,
                rect.topLeft.y + rect.size.height / 2 + 120,
                android.graphics.Paint().apply {
                    color = android.graphics.Color.MAGENTA
                    textSize = 32f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
            )
        }
    }

    private fun Rect.toString2(): String {
        return "($left, $top, $right, $bottom)"
    }
}

private fun isVisible(drawScope: DrawScope, offset: Offset, bounds: Rect, page: Int): Boolean {
    // 获取画布的可视区域
    val visibleRect = Rect(
        left = -offset.x,
        top = -offset.y,
        right = drawScope.size.width - offset.x,
        bottom = drawScope.size.height - offset.y
    )

    // 检查页面是否与可视区域相交
    val visible = bounds.overlaps(visibleRect)
    //println("page.draw.isVisible:$visible, offset:$offset, bounds:$bounds, visibleRect:$visibleRect, $page")
    return visible
}

private class Page(
    private val pdfViewState: PdfViewState,
    private var viewSize: IntSize,
    private var zoom: Float,
    private var viewOffset: Offset,
    private var aPage: APage,
    private var yOffset: Float = 0f
) {
    private var nodes: List<PageNode> = emptyList()

    //page bound, should be caculate after view measured
    private var bounds = Rect(0f, 0f, 0f, 0f)

    /**
     * @param viewSize view size
     * @param zoom view zoom,not the page zoom,default=1f
     * @param yOffset page.top
     */
    public fun update(viewSize: IntSize, zoom: Float, offset: Offset, yOffset: Float) {
        val isViewSizeChanged = this.viewSize != viewSize
        val isZoomChanged = this.zoom != zoom

        this.viewSize = viewSize
        this.zoom = zoom
        this.viewOffset = offset
        this.yOffset = yOffset

        if (isViewSizeChanged || isZoomChanged) {
            recalculateNodes()
        }
    }

    public fun draw(drawScope: DrawScope, offset: Offset) {
        if (!isVisible(drawScope, offset, bounds, aPage.index)) {
            return
        }
        //println("page.draw.offset:$offset, $bounds, $aPage")
        nodes.forEach { node ->
            drawScope.drawRect(
                color = Color.Yellow,
                topLeft = Offset(0f, bounds.top),
                size = Size(bounds.width, bounds.height),
                style = Stroke(width = 8f)
            )
            drawScope.drawContext.canvas.nativeCanvas.drawText(
                aPage.index.toString(),
                0 + drawScope.size.width / 2,
                yOffset + drawScope.size.height / 2,
                android.graphics.Paint().apply {
                    color = android.graphics.Color.YELLOW
                    textSize = 160f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
            )
            node.draw(drawScope, offset)
        }
    }

    public fun updateOffset(newOffset: Offset) {
        this.viewOffset = newOffset
    }

    private fun recalculateNodes() {
        if (viewSize == IntSize.Zero) return

        val contentOffset = Offset(0f, yOffset)

        val viewWidth = viewSize.width.toFloat()
        val pageScale = viewWidth / aPage.width
        val scaledWidth = viewWidth * zoom
        val scaledHeight = aPage.height * pageScale * zoom
        bounds = Rect(0f, contentOffset.y, scaledWidth, contentOffset.y + scaledHeight)

        val rootNode = PageNode(
            pdfViewState,
            Rect(
                left = contentOffset.x,
                top = contentOffset.y,
                right = contentOffset.x + scaledWidth,
                bottom = contentOffset.y + scaledHeight
            ),
            aPage
        )

        println("page.recalculateNodes.viewSize:$viewSize, offset:$contentOffset, $bounds, w-h:$scaledWidth-$scaledHeight, $aPage")
        nodes = calculatePages(rootNode)
    }

    private fun calculatePages(page: PageNode): List<PageNode> {
        val rect = page.rect
        if (rect.width * rect.height > maxSize) {
            val halfWidth = rect.width / 2
            val halfHeight = rect.height / 2
            val list = listOf(
                PageNode(
                    pdfViewState,
                    Rect(rect.left, rect.top, rect.left + halfWidth, rect.top + halfHeight),
                    page.aPage
                ),
                PageNode(
                    pdfViewState,
                    Rect(rect.left + halfWidth, rect.top, rect.right, rect.top + halfHeight),
                    page.aPage
                ),
                PageNode(
                    pdfViewState,
                    Rect(rect.left, rect.top + halfHeight, rect.left + halfWidth, rect.bottom),
                    page.aPage
                ),
                PageNode(
                    pdfViewState,
                    Rect(rect.left + halfWidth, rect.top + halfHeight, rect.right, rect.bottom),
                    page.aPage
                )
            ).flatMap {
                calculatePages(it)
            }
            //list.forEach {
            //    println("page.calculatePages.rect:${it.rect}, $aPage")
            //}
            return list
        }
        return listOf(page)
    }

    public companion object {
        private const val maxSize = 256 * 384 * 4f * 2
    }
}

private class PdfViewState(
    public val list: List<APage>,
    public val state: PdfState,
) {
    private var viewOffset: Offset = Offset.Zero
    public var init: Boolean by mutableStateOf(false)
    public var totalHeight: Float by mutableFloatStateOf(0f)

    //public var pages: List<Page> by mutableStateOf(createPages())
    public var viewSize: IntSize by mutableStateOf(IntSize.Zero)
    public var vZoom: Float by mutableFloatStateOf(1f)
    var pagePositions: List<Float> by mutableStateOf(createPositions())

    private val parentScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val singleThreadDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(
        parentScope.coroutineContext + singleThreadDispatcher
    )
    private val visibleTilesChannel = Channel<TileSpec>(capacity = Channel.RENDEZVOUS)
    private val tilesOutput = Channel<TileSpec>(capacity = Channel.RENDEZVOUS)
    internal val decoderService: DecoderService
    internal val tilesCollected = mutableListOf<TileSpec>()
    internal var tilesToRender: List<TileSpec> by mutableStateOf(listOf())

    init {
        //scope.launch {
        //collectNewTiles()
        //}

        decoderService = DecoderService(1)
        scope.launch {
            decoderService.collectTiles(
                visibleTilesChannel,
                tilesOutput
            )
        }

        scope.launch {
            consumeTiles(tilesOutput)
        }
    }

    private val renderTask = scope.throttle(wait = 34) {
        //evictTiles(lastVisible)
        val tilesToRenderCopy = tilesCollected.sortedBy {
            val priority = it.page
            priority
        }
        println("PdfViewState:renderTask:${tilesToRenderCopy.size}")
        tilesToRender = tilesToRenderCopy
    }

    private suspend fun consumeTiles(tileChannel: ReceiveChannel<TileSpec>) {
        for (tile in tileChannel) {
            //println("PdfViewState:consumeTiles:$tile")
            if (tile.imageBitmap == null) {
                continue
            }
            ImageCache.put(tile.cacheKey, tile.imageBitmap!!)
            /*if (isTileVisible(tile)) {
                if (!tilesCollected.contains(tile)) {
                    tilesCollected.add(tile)
                } else {

                }
            } else {
                //tile.recycle()
                tilesCollected.remove(tile)
                ImageCache.remove(tile.cacheKey)
            }*/
            renderThrottled()
        }
    }

    private fun renderThrottled() {
        renderTask.trySend(Unit)
    }

    private fun isTileVisible(spec: TileSpec): Boolean {
        return isVisible(IntSize(0, 0), viewOffset, spec.rect, spec.page)
    }

    private fun isVisible(viewSize: IntSize, offset: Offset, bounds: Rect, page: Int): Boolean {
        // 获取画布的可视区域
        val visibleRect = Rect(
            left = -offset.x,
            top = -offset.y,
            right = viewSize.width - offset.x,
            bottom = viewSize.height - offset.y
        )

        // 检查页面是否与可视区域相交
        val visible = bounds.overlaps(visibleRect)
        //println("page.draw.isVisible:$visible, offset:$offset, bounds:$bounds, visibleRect:$visibleRect, $page")
        return visible
    }

    fun shutdown() {
        singleThreadDispatcher.close()
        decoderService.shutdownNow()
    }

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
            totalHeight = currentY
        }
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
        //updateHeight()
        //var currentY = 0f
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
        println("PdfViewState.invalidatePageSizes.viewSize:$viewSize, totalHeight:$totalHeight, zoom:$vZoom")
    }

    /*private fun createPages(): List<Page> {
        return list.map { aPage ->
            Page(
                viewSize,
                1f,
                IntSize.Zero,
                aPage,
                1f,
            )
        }
    }*/

    public fun updateViewSize(viewSize: IntSize, vZoom: Float) {
        val isViewSizeChanged = this.viewSize != viewSize
        val isZoomChanged = this.vZoom != vZoom

        this.viewSize = viewSize
        this.vZoom = vZoom
        if (isViewSizeChanged || isZoomChanged) {
            invalidatePageSizes()
        } else {
            println("PdfViewState.viewSize未变化: vZoom:$vZoom, totalHeight:$totalHeight, viewSize:$viewSize")
        }
    }

    fun remove(rect: Rect, page: APage, cacheKey: String) {
        val tileSpec = TileSpec(
            page.index,
            page.scale,
            rect,
            rect.width.toInt(),
            rect.height.toInt(),
            cacheKey,
            null
        )
        if (tilesCollected.contains(tileSpec)) {
            println("PdfViewState:remove:$tileSpec")
            tilesCollected.remove(tileSpec)
        }
    }

    fun decode(rect: Rect, page: APage, cacheKey: String) {
        val tileSpec = TileSpec(
            page.index,
            page.scale,
            rect,
            rect.width.toInt(),
            rect.height.toInt(),
            cacheKey,
            null
        )
        if (!tilesCollected.contains(tileSpec)) {
            tilesCollected.add(tileSpec)
            scope.launch {
                visibleTilesChannel.send(tileSpec)
            }
        }
    }

    public fun updateOffset(newOffset: Offset) {
        this.viewOffset = newOffset
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
        PdfViewState(list, state)
    }
    // 确保在 list 变化时重新计算总高度
    LaunchedEffect(list, viewSize) {
        if (viewSize != IntSize.Zero) {
            println("DocumentView: 更新ViewSize:$viewSize, vZoom:$vZoom, list: ${list.size}")
            pdfState.updateViewSize(viewSize, vZoom)
        }
    }

    DisposableEffect(list) {
        onDispose {
            println("DocumentView: shutdown:$viewSize, vZoom:$vZoom, list: ${list.size}")
            pdfState.shutdown()
        }
    }

    // 创建并记住每个页面的Page对象
    var pages by remember(list, pdfState.pagePositions) {
        mutableStateOf(list.zip(pdfState.pagePositions).map { (aPage, yPos) ->
            println("DocumentView.createPage.viewSize:$viewSize, vZoom:$vZoom, y: $yPos, $aPage")
            Page(pdfState, IntSize.Zero, vZoom, Offset.Zero, aPage, yPos)
        })
    }

    // 定义背景渐变
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(Color.Green, Color.Red)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged {
                viewSize = it
                pages.zip(pdfState.pagePositions).forEach { (page, yPos) ->
                    page.update(viewSize, vZoom, offset, yPos)
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
                                        /*pages.forEach { page ->
                                            page.updateOffset(offset)
                                        }*/
                                        pdfState.updateOffset(offset)

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
                                            pages.zip(pdfState.pagePositions)
                                                .forEach { (page, yPos) ->
                                                    page.update(viewSize, vZoom, offset, yPos)
                                                }
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
                                            pages.forEach { page -> page.updateOffset(offset) }

                                            pdfState.updateOffset(offset)
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
                                            pages.forEach { page -> page.updateOffset(offset) }

                                            pdfState.updateOffset(offset)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
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
                pages.forEach { page -> page.draw(this, offset) }
            }
        }
    }
}