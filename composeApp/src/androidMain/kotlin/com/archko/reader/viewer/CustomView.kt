package com.archko.reader.viewer

import androidx.compose.animation.core.animateDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
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
import com.archko.reader.pdf.decoder.DecoderService
import com.archko.reader.pdf.entity.APage
import com.archko.reader.pdf.flinger.FlingConfiguration
import com.archko.reader.pdf.flinger.SplineBasedFloatDecayAnimationSpec
import com.archko.reader.pdf.decoder.PdfDecoder
import com.archko.reader.pdf.decoder.TileSpec
import kotlinx.coroutines.*
import kotlinx.coroutines.NonCancellable.isActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.receiveAsFlow
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.cancellation.CancellationException

fun CoroutineScope.throttle(wait: Long, block: suspend () -> Unit): SendChannel<Unit> {
    val channel = Channel<Unit>(capacity = Channel.CONFLATED)
    val flow = channel.receiveAsFlow()

    launch {
        flow.collect {
            block()
            delay(wait)
        }
    }
    return channel
}

private class PageNode(
    private val pdfViewState: PdfViewState,
    public var rect: Rect,  //view offset rect
    public var bounds: Rect,    //page bounds
    public val aPage: APage  // 添加 APage 属性
) {

    val cacheKey = "${aPage.index}-${rect}-${aPage.scale}"
    public fun draw(drawScope: DrawScope, offset: Offset) {
        // 检查页面是否在可视区域内
        if (!isVisible(drawScope, offset, rect, aPage.index)) {
            //println("is not Visible:${aPage.index}, $offset, $rect")
            pdfViewState.remove(bounds, aPage, cacheKey)
            return
        }
        var loadedBitmap: ImageBitmap? = ImageCache.get(cacheKey)
        //println("pageNode.draw.loadedBitmap:${loadedBitmap == null}, $rect, $aPage")
        if (loadedBitmap != null) {
            //println("pageNode.draw.loadedBitmap:$rect, $aPage")
            drawScope.drawImage(
                loadedBitmap,
                dstOffset = IntOffset(rect.left.toInt(), rect.top.toInt())
            )
        } else {
            pdfViewState.decode(bounds, aPage, cacheKey)
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
    private var scale: Float,   //view*vZoom/page.width
    internal var aPage: APage,
    private var yOffset: Float = 0f
) {
    var nodes: List<PageNode> = emptyList()

    //page bound, should be caculate after view measured
    internal var bounds = Rect(0f, 0f, 0f, 0f)

    /**
     * @param viewSize view size
     * @param scale view zoom,not the page zoom,default=1f
     * @param yOffset page.top
     */
    public fun update(viewSize: IntSize, scale: Float, rect: Rect) {
        this.viewSize = viewSize
        this.scale = scale
        this.bounds = rect
        this.yOffset = bounds.top

        recalculateNodes()
    }

    public fun draw(drawScope: DrawScope, offset: Offset) {
        if (!isVisible(drawScope, offset, bounds, aPage.index)) {
            return
        }
        //println("page.draw.offset:$offset, $bounds, $aPage")
        nodes.forEach { node ->
            node.draw(drawScope, offset)
        }
        drawScope.drawRect(
            color = Color.Yellow,
            topLeft = Offset(0f, bounds.top),
            size = Size(bounds.width, bounds.height),
            style = Stroke(width = 8f)
        )
        drawScope.drawContext.canvas.nativeCanvas.drawText(
            aPage.index.toString(),
            bounds.left + bounds.width / 2,
            bounds.top + bounds.height / 2,
            android.graphics.Paint().apply {
                color = android.graphics.Color.YELLOW
                textSize = 160f
                textAlign = android.graphics.Paint.Align.CENTER
            }
        )
    }

    private fun recalculateNodes() {
        if (viewSize == IntSize.Zero) return

        val contentOffset = Offset(0f, yOffset)

        val rootNode = PageNode(
            pdfViewState,
            Rect(
                left = contentOffset.x,
                top = contentOffset.y,
                right = contentOffset.x + bounds.width,
                bottom = contentOffset.y + bounds.height
            ),
            bounds,
            aPage
        )

        println("page.recalculateNodes.scale:$scale, offset:$contentOffset, $bounds, w-h:${bounds.width}-${bounds.height}, $aPage")
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
                    Rect(0f, 0f, halfWidth, halfHeight),
                    page.aPage
                ),
                PageNode(
                    pdfViewState,
                    Rect(rect.left + halfWidth, rect.top, rect.right, rect.top + halfHeight),
                    Rect(halfWidth, 0f, rect.width, halfHeight),
                    page.aPage
                ),
                PageNode(
                    pdfViewState,
                    Rect(rect.left, rect.top + halfHeight, rect.left + halfWidth, rect.bottom),
                    Rect(0f, halfHeight, halfWidth, rect.height),
                    page.aPage
                ),
                PageNode(
                    pdfViewState,
                    Rect(rect.left + halfWidth, rect.top + halfHeight, rect.right, rect.bottom),
                    Rect(halfWidth, halfHeight, rect.width, rect.height),
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Page

        if (viewSize != other.viewSize) return false
        if (scale != other.scale) return false
        if (aPage != other.aPage) return false
        if (yOffset != other.yOffset) return false
        if (nodes != other.nodes) return false
        if (bounds != other.bounds) return false

        return true
    }

    override fun hashCode(): Int {
        var result = viewSize.hashCode()
        result = 31 * result + scale.hashCode()
        result = 31 * result + aPage.hashCode()
        result = 31 * result + yOffset.hashCode()
        result = 31 * result + nodes.hashCode()
        result = 31 * result + bounds.hashCode()
        return result
    }

    public companion object {
        private const val maxSize = 256 * 384 * 4f * 2
    }
}

private class PdfViewState(
    public val list: List<APage>,
    public val state: PdfDecoder,
) {
    private var viewOffset: Offset = Offset.Zero
    public var init: Boolean by mutableStateOf(false)
    public var totalHeight: Float by mutableFloatStateOf(0f)

    public var viewSize: IntSize by mutableStateOf(IntSize.Zero)
    internal var pageToRender: List<Page> by mutableStateOf(listOf())
    public var pages: List<Page> by mutableStateOf(createPages())
    public var vZoom: Float by mutableFloatStateOf(1f)
    public var update: Int by mutableIntStateOf(1)

    private val parentScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val singleThreadDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(
        parentScope.coroutineContext + singleThreadDispatcher
    )
    private val visibleTilesChannel = Channel<TileSpec>(capacity = Channel.RENDEZVOUS)
    private val tilesOutput = Channel<TileSpec>(capacity = Channel.RENDEZVOUS)
    internal val decoderService: DecoderService
    internal val tilesCollected = mutableListOf<TileSpec>()
    internal val requestedTiles = mutableSetOf<String>() // 新增，记录已请求解码的tile

    private var lastPageKeys: Set<String> = emptySet()

    init {
        //scope.launch {
        //collectNewTiles()
        //}

        decoderService = DecoderService(1, state)
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
        /*val sortedPages = pages.sortedBy { it.aPage.index }
        val pagesIndices = sortedPages.map { it.aPage.index }.toIntArray()

        val tilesToRenderCopy = mutableListOf<Page>()
        val addedIndices = mutableSetOf<Int>()

        for (tile in tilesCollected) {
            val pageIndex = tile.page
            val index = pagesIndices.binarySearch(pageIndex)
            if (index >= 0 && !addedIndices.contains(pageIndex)) {
                tilesToRenderCopy.add(sortedPages[index])
                addedIndices.add(pageIndex)
            }
        }
        println("PdfViewState:renderTask:${tilesCollected.size}, ${tilesToRenderCopy.size}")
        pageToRender = tilesToRenderCopy*/
        setVisibilePage()
    }

    private suspend fun consumeTiles(tileChannel: ReceiveChannel<TileSpec>) {
        for (tile in tileChannel) {
            println("PdfViewState:consumeTiles:$tile")
            if (tile.imageBitmap == null) {
                requestedTiles.remove(tile.cacheKey) // 解码失败也要移除
                continue
            }
            ImageCache.put(tile.cacheKey, tile.imageBitmap!!)
            requestedTiles.remove(tile.cacheKey) // 解码完成，移除
            if (isTileVisible(tile)) {
                if (!tilesCollected.contains(tile)) {
                    tilesCollected.add(tile)
                }
            } else {
                tilesCollected.remove(tile)
                //ImageCache.remove(tile.cacheKey)
            }

            renderThrottled()
        }
    }

    private fun renderThrottled() {
        renderTask.trySend(Unit)
    }

    private fun isTileVisible(spec: TileSpec): Boolean {
        return isVisible(viewSize, viewOffset, spec.rect, spec.page)
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

    public fun invalidatePageSizes() {
        var currentY = 0f
        if (viewSize.width == 0 || viewSize.height == 0 || list.isEmpty()) {
            println("PdfViewState.viewSize高宽为0,或list为空,不计算page: viewSize:$viewSize, totalHeight:$totalHeight")
            totalHeight = viewSize.height.toFloat()
            init = false
        } else {
            list.zip(pages).forEach { (aPage, page) ->
                val scaledPageWidth = viewSize.width * vZoom
                val pageScale = scaledPageWidth / aPage.width
                aPage.scale = pageScale
                val scaledPageHeight = aPage.height * pageScale
                val bounds = Rect(
                    0f,
                    currentY,
                    scaledPageWidth,
                    currentY + scaledPageHeight
                )
                page.update(viewSize, pageScale, bounds)
                currentY += scaledPageHeight
                println("PdfViewState.pageScale:$pageScale, y:$currentY, bounds:$bounds, aPage:$aPage")
            }
            init = true
        }
        totalHeight = currentY
        println("PdfViewState.invalidatePageSizes.viewSize:$viewSize, totalHeight:$totalHeight, zoom:$vZoom")
    }

    private fun createPages(): List<Page> {
        val list = list.map { aPage ->
            Page(
                this,
                viewSize,
                1f,
                aPage,
                0f,
            )
        }
        pageToRender = list
        return list
    }

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
            viewSize,
            cacheKey,
            null
        )
        if (tilesCollected.contains(tileSpec)) {
            println("PdfViewState:remove:$tileSpec")
            tilesCollected.remove(tileSpec)
        }
    }

    fun decode(rect: Rect, page: APage, cacheKey: String) {
        if (ImageCache.get(cacheKey) != null) return // 已有缓存
        if (requestedTiles.contains(cacheKey)) return // 已经在解码队列
        requestedTiles.add(cacheKey)
        val tileSpec = TileSpec(
            page.index,
            page.scale,
            Rect(rect.left, rect.top, rect.right, rect.bottom),
            page.width,
            page.height,
            viewSize,
            cacheKey,
            null
        )
        if (tilesCollected.contains(tileSpec)) {
            val index = tilesCollected.indexOf(tileSpec)
            val bitmap = tilesCollected[index].imageBitmap
            if (null != bitmap) {
                ImageCache.put(cacheKey, bitmap)
                println("PdfViewState:no decode:$tileSpec")
                return
            }
        }
        tilesCollected.add(tileSpec)
        scope.launch {
            visibleTilesChannel.send(tileSpec)
        }
    }

    public fun updateOffset(newOffset: Offset) {
        this.viewOffset = newOffset
        setVisibilePage()
    }

    private fun setVisibilePage() {
        // 优化：使用二分查找定位可见页面范围（仅y方向）
        if (pages.isEmpty()) {
            pageToRender = emptyList()
            return
        }
        val visibleTop = -viewOffset.y
        val visibleBottom = viewSize.height - viewOffset.y

        // 二分查找第一个可见页面
        fun findFirstVisible(): Int {
            var low = 0
            var high = pages.size - 1
            var result = pages.size
            while (low <= high) {
                val mid = (low + high) ushr 1
                val page = pages[mid]
                if (page.bounds.bottom > visibleTop) {
                    result = mid
                    high = mid - 1
                } else {
                    low = mid + 1
                }
            }
            return result
        }

        // 二分查找最后一个可见页面
        fun findLastVisible(): Int {
            var low = 0
            var high = pages.size - 1
            var result = -1
            while (low <= high) {
                val mid = (low + high) ushr 1
                val page = pages[mid]
                if (page.bounds.top < visibleBottom) {
                    result = mid
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }
            return result
        }

        val first = findFirstVisible()
        val last = findLastVisible()
        val tilesToRenderCopy = if (first <= last && first < pages.size && last >= 0) {
            pages.subList(first, last + 1)
        } else {
            emptyList()
        }
        // 主动移除不再可见的页面图片缓存
        val newPageKeys = tilesToRenderCopy.flatMap { page ->
            // Page 可能有多个 tile，需与缓存 key 生成方式一致
            // 这里假设 PageNode.cacheKey 生成方式为："${aPage.index}-${rect}-${aPage.scale}"
            if (page is Page) {
                page.nodes.map { node ->
                    "${node.aPage.index}-${node.rect}-${node.aPage.scale}"
                }
            } else emptyList()
        }.toSet()
        val toRemove = lastPageKeys - newPageKeys
        toRemove.forEach { key ->
            ImageCache.remove(key)
        }
        lastPageKeys = newPageKeys
        if (tilesToRenderCopy != pageToRender) {
            println("PdfViewState:updateOffset:${tilesToRenderCopy.size}, $update")
            update++
            pageToRender = tilesToRenderCopy
        }
    }
}

@Composable
fun CustomView(path: String) {
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var decoder: PdfDecoder? by remember { mutableStateOf(null) }
    LaunchedEffect(path) {
        withContext(Dispatchers.IO) {
            println("init:$viewportSize, $path")
            val pdfDecoder = if (viewportSize == IntSize.Zero) {
                null
            } else {
                PdfDecoder(File(path))
            }
            if (pdfDecoder != null) {
                pdfDecoder.getSize(viewportSize)
                println("init.size:${pdfDecoder.imageSize.width}-${pdfDecoder.imageSize.height}")
                decoder = pdfDecoder
            }
        }
    }
    DisposableEffect(path) {
        onDispose {
            println("onDispose:$path, $decoder")
            decoder?.close()
        }
    }

    if (null == decoder) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewportSize = it }) {
            Text(
                "Loading",
                modifier = Modifier
            )
        }
    } else {
        fun createList(decoder: PdfDecoder): MutableList<APage> {
            var list = mutableListOf<APage>()
            for (i in 0 until decoder.originalPageSizes.size) {
                val page = decoder.originalPageSizes[i]
                val aPage = APage(i, page.width, page.height, 1f)
                list.add(aPage)
            }
            return list
        }

        var list: MutableList<APage> = remember {
            createList(decoder!!)
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewportSize = it }) {
            CustomView(
                list,
                decoder!!
            )
        }
    }
}

@Composable
fun CustomView(
    list: MutableList<APage>,
    state: PdfDecoder
) {
    // 初始化状态
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var vZoom by remember { mutableFloatStateOf(1f) }
    val velocityTracker = remember { VelocityTracker() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
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
            pdfViewState.update++
        }
    }

    DisposableEffect(list) {
        onDispose {
            println("DocumentView: shutdown:$viewSize, vZoom:$vZoom, list: ${list.size}")
            pdfViewState.shutdown()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged {
                viewSize = it
                pdfViewState.updateViewSize(viewSize, vZoom)
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
                                            println("zoom:$vZoom, $centroid, $offset")
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
                                    if (kotlin.math.abs(velocity.x) > 50f) {  // 添加最小速度阈值
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
                                    if (kotlin.math.abs(velocity.y) > 50f) {  // 添加最小速度阈值
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
                    pdfViewState.pages.forEach { page -> page.draw(this, offset) }
                }
        }
    }
}