package com.archko.reader.pdf.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.APage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

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

    // 全局单线程解码作用域
    public val decodeScope: CoroutineScope =
        CoroutineScope(Dispatchers.Default.limitedParallelism(1))

    private var lastPageKeys: Set<String> = emptySet()

    public fun isTileVisible(spec: TileSpec): Boolean {
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
        return list
    }

    public fun updateViewSize(viewSize: IntSize, vZoom: Float) {
        val isViewSizeChanged = this.viewSize != viewSize
        val isZoomChanged = this.vZoom != vZoom

        this.viewSize = viewSize
        this.vZoom = vZoom
        if (isViewSizeChanged || isZoomChanged) {
            invalidatePageSizes()
            // update++ 不再需要
        } else {
            println("PdfViewState.viewSize未变化: vZoom:$vZoom, totalHeight:$totalHeight, viewSize:$viewSize")
        }
    }

    public fun updateVisiblePages(offset: Offset, viewSize: IntSize, currentVZoom: Float = vZoom) {
        // 优化：使用二分查找定位可见页面范围（仅y方向）
        if (pages.isEmpty()) {
            pageToRender = emptyList()
            return
        }
        val visibleTop = -offset.y
        val visibleBottom = viewSize.height - offset.y

        // 二分查找第一个可见页面
        fun findFirstVisible(): Int {
            var low = 0
            var high = pages.size - 1
            var result = pages.size
            while (low <= high) {
                val mid = (low + high) ushr 1
                val page = pages[mid]
                // 在缩放过程中，需要考虑当前缩放比例
                val scaleRatio = currentVZoom / this.vZoom
                val currentBottom = page.bounds.bottom * scaleRatio
                if (currentBottom > visibleTop) {
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
                // 在缩放过程中，需要考虑当前缩放比例
                val scaleRatio = currentVZoom / this.vZoom
                val currentTop = page.bounds.top * scaleRatio
                if (currentTop < visibleBottom) {
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
            if (page is Page) {
                page.nodes.map { node ->
                    "${node.aPage.index}-${node.bounds}-${node.aPage.scale}"
                }
            } else emptyList()
        }.toSet()
        val toRemove = lastPageKeys - newPageKeys
        toRemove.forEach { key ->
            // key格式: "index-bounds-scale"
            val index = key.substringBefore("-").toIntOrNull() ?: return@forEach
            val page = pages.getOrNull(index) as? Page ?: return@forEach
            page.recycle()
        }
        lastPageKeys = newPageKeys
        if (tilesToRenderCopy != pageToRender) {
            pageToRender = tilesToRenderCopy
        }
    }

    public fun updateOffset(newOffset: Offset) {
        this.viewOffset = newOffset
        // setVisibilePage() 调用移除，由 DocumentView 主动调用 updateVisiblePages
    }

    public enum class Align { Top, Center, Bottom }

    public fun goToPage(pageIndex: Int, align: Align = Align.Top) {
        val page = pages.getOrNull(pageIndex) ?: return
        val targetOffsetY = when (align) {
            Align.Top -> page.bounds.top
            Align.Center -> page.bounds.top - (viewSize.height - page.height) / 2
            Align.Bottom -> page.bounds.bottom - viewSize.height
        }
        val clampedY =
            -targetOffsetY.coerceIn(-(totalHeight - viewSize.height).coerceAtLeast(0f), 0f)
        updateOffset(Offset(viewOffset.x, clampedY))
    }

    public fun drawVisiblePages(
        drawScope: androidx.compose.ui.graphics.drawscope.DrawScope,
        offset: Offset,
        vZoom: Float,
        viewSize: IntSize
    ) {
        updateVisiblePages(offset, viewSize, vZoom)
        pageToRender.forEach { page ->
            page.draw(drawScope, offset, vZoom)
        }
    }
}