package com.archko.reader.pdf.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.cache.ImageCache
import com.archko.reader.pdf.decoder.DecoderService
import com.archko.reader.pdf.decoder.TileSpec
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.APage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

public fun CoroutineScope.throttle(wait: Long, block: suspend () -> Unit): SendChannel<Unit> {
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

/**
 * @author: archko 2025/7/24 :08:21
 */
public class PdfViewState(
    public val list: List<APage>,
    public val state: ImageDecoder,
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

        decoderService = DecoderService(1, state, this::isTileVisible)
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

    private val renderTask = scope.throttle(wait = 50) {
        //evictTiles(lastVisible)
        setVisibilePage()
    }

    private suspend fun consumeTiles(tileChannel: ReceiveChannel<TileSpec>) {
        for (tile in tileChannel) {
            //println("PdfViewState:consumeTiles:$tile")
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
                println("PdfViewState:remove:$tile")
                tilesCollected.remove(tile)
                //ImageCache.remove(tile.cacheKey)
            }

            renderThrottled()
        }
    }

    private fun renderThrottled() {
        //println("PdfViewState:renderThrottled.size:${tilesCollected.size}")
        renderTask.trySend(Unit)
    }

    private fun isTileVisible(spec: TileSpec): Boolean {
        val page = pages.getOrNull(spec.page) ?: return false
        val yOffset = page.yOffset
        val pixelRect = Rect(
            left = spec.bounds.left * spec.pageWidth,
            top = spec.bounds.top * spec.pageHeight + yOffset,
            right = spec.bounds.right * spec.pageWidth,
            bottom = spec.bounds.bottom * spec.pageHeight + yOffset
        )
        return isVisible(viewSize, viewOffset, pixelRect, spec.page)
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

    public fun shutdown() {
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
                val scaledPageHeight = aPage.height * pageScale
                val bounds = Rect(
                    0f,
                    currentY,
                    scaledPageWidth,
                    currentY + scaledPageHeight
                )
                // 直接用最终宽高初始化Page
                page.update(scaledPageWidth, scaledPageHeight, bounds)
                currentY += scaledPageHeight
                //println("PdfViewState.pageScale:$pageScale, y:$currentY, bounds:$bounds, aPage:$aPage")
            }
            init = true
        }
        totalHeight = currentY
        println("PdfViewState.invalidatePageSizes.viewSize:$viewSize, totalHeight:$totalHeight, zoom:$vZoom")
    }

    private fun createPages(): List<Page> {
        val list = list.map { aPage ->
            // 初始化时直接用viewSize和vZoom计算的宽高
            val scaledPageWidth = viewSize.width * 1f
            val pageScale = if (aPage.width == 0) 1f else scaledPageWidth / aPage.width
            val scaledPageHeight = aPage.height * pageScale
            Page(
                this,
                scaledPageWidth,
                scaledPageHeight,
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
            update++
        } else {
            println("PdfViewState.viewSize未变化: vZoom:$vZoom, totalHeight:$totalHeight, viewSize:$viewSize")
        }
    }

    public fun remove(
        bounds: Rect,
        page: APage,
        cacheKey: String,
        pageScale: Float,
        pageWidth: Float,
        pageHeight: Float
    ) {
        val tileSpec = TileSpec(
            page.index,
            pageScale, // totalScale
            bounds,
            pageWidth.toInt(), // 原始宽高
            pageHeight.toInt(),
            viewSize,
            cacheKey,
            null
        )
        if (tilesCollected.contains(tileSpec)) {
            println("PdfViewState:remove:$tileSpec")
            tilesCollected.remove(tileSpec)
        }
    }

    public fun decode(
        bounds: Rect,
        page: APage,
        cacheKey: String,
        pageScale: Float,
        pageWidth: Float,
        pageHeight: Float
    ) {
        if (ImageCache.get(cacheKey) != null) {
            update++
            return // 已有缓存
        }
        if (requestedTiles.contains(cacheKey)) return // 已经在解码队列
        requestedTiles.add(cacheKey)
        val tileSpec = TileSpec(
            page.index,
            pageScale, // totalScale
            bounds,
            pageWidth.toInt(), // 原始宽高
            pageHeight.toInt(),
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
                    "${node.aPage.index}-${node.bounds}-${node.aPage.scale}"
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

    public enum class Align { Top, Center, Bottom }

    public fun goToPage(pageIndex: Int, align: Align = Align.Top) {
        val page = pages.getOrNull(pageIndex) ?: return
        val targetOffsetY = when (align) {
            Align.Top -> page.bounds.top
            Align.Center -> page.bounds.top - (viewSize.height - page.height) / 2
            Align.Bottom -> page.bounds.bottom - viewSize.height
        }
        val clampedY = -targetOffsetY.coerceIn(-(totalHeight - viewSize.height).coerceAtLeast(0f), 0f)
        updateOffset(Offset(viewOffset.x, clampedY))
    }
}