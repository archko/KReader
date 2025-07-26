package com.archko.reader.pdf.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.cache.ImageCache
import com.archko.reader.pdf.entity.APage
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.ceil

/**
 * @author: archko 2025/7/24 :08:20
 */
public class Page(
    private val pdfViewState: PdfViewState,
    public var width: Float,   // 最终缩放后的宽
    public var height: Float,  // 最终缩放后的高
    internal var aPage: APage,
    public var yOffset: Float = 0f
) {
    public var totalScale: Float = 1f
    public var nodes: List<PageNode> = emptyList()

    //page bound, should be caculate after view measured
    internal var bounds = Rect(0f, 0f, 1f, 1f)

    // 缩略图缓存，响应式
    private var thumbBitmap by mutableStateOf<ImageBitmap?>(null)
    private var thumbDecoding = false
    private var thumbJob: Job? = null

    public fun recycleThumb() {
        thumbBitmap = null
        thumbDecoding = false
        thumbJob?.cancel()
        thumbJob = null
    }

    // 异步加载缩略图，参考PageNode解码逻辑
    public fun loadThumbnail() {
        if (thumbDecoding) return
        val w = (width / 3).toInt().coerceAtLeast(1)
        val h = (height / 3).toInt().coerceAtLeast(1)
        val cacheKey = "thumb-${aPage.index}-${w}x${h}"
        val cached = ImageCache.get(cacheKey)
        if (cached != null) {
            thumbBitmap = cached
            return
        }
        thumbDecoding = true
        thumbJob = pdfViewState.decodeScope.launch {
            // 可见性判断（Page始终可见，或可加自定义判断）
            if (!isActive) return@launch
            try {
                val region = Rect(0f, 0f, 1f, 1f)
                val bitmap = pdfViewState.state.renderPageRegion(
                    region,
                    aPage.index,
                    totalScale / 3,
                    pdfViewState.viewSize,
                    w,
                    h
                )
                if (!isActive) return@launch
                thumbBitmap = bitmap
                ImageCache.put(cacheKey, bitmap)
            } catch (_: Exception) {
            } finally {
                thumbDecoding = false
            }
        }
    }

    /**
     * @param viewSize view size
     * @param scale view zoom,not the page zoom,default=1f
     * @param yOffset page.top
     */
    public fun update(width: Float, height: Float, rect: Rect) {
        this.width = width
        this.height = height
        this.bounds = rect
        this.yOffset = bounds.top
        this.totalScale = if (aPage.width == 0) 1f else width / aPage.width
        recalculateNodes()
    }

        public fun draw(drawScope: DrawScope, offset: Offset, vZoom: Float) {
        // 计算当前缩放下的实际显示尺寸和位置
        // Page 的属性是基于 pdfViewState.vZoom 计算的，但当前的 vZoom 可能已经改变
        val scaleRatio = vZoom / pdfViewState.vZoom
        val currentWidth = width * scaleRatio
        val currentHeight = height * scaleRatio
        val currentBounds = Rect(
            bounds.left * scaleRatio,
            bounds.top * scaleRatio,
            bounds.right * scaleRatio,
            bounds.bottom * scaleRatio
        )
        
        if (!isVisible(drawScope, offset, currentBounds, aPage.index)) {
            return
        }
        
        // 优先绘制缩略图
        if (null != thumbBitmap) {
            drawScope.drawImage(
                image = thumbBitmap!!,
                dstOffset = IntOffset(0, currentBounds.top.toInt()),
                dstSize = IntSize(currentWidth.toInt(), currentHeight.toInt())
            )
        } else {
            if (!thumbDecoding) {
                loadThumbnail()
            }
        }
        // 再绘制高清块
        nodes.forEach { node ->
            node.draw(
                drawScope,
                offset,
                currentWidth,
                currentHeight,
                currentBounds.top,
                totalScale * scaleRatio
            )
        }
        // 占位框
        /*drawScope.drawRect(
            color = Color.Green,
            topLeft = Offset(0f, currentBounds.top),
            size = Size(currentBounds.width, currentBounds.height),
            style = Stroke(width = 6f)
        )*/
    }

    public fun recycle() {
        println("Page.recycle:${aPage.index}, $width-$height, $yOffset")
        nodes.forEach { it.recycle() }
    }

    private fun recalculateNodes() {
        val rootNode = PageNode(
            pdfViewState,
            Rect(0f, 0f, 1f, 1f),
            aPage
        )
        nodes = calculatePages(rootNode)
    }

    private fun calculatePages(page: PageNode): List<PageNode> {
        val minBlockSize = 256f * 3f // 768
        val maxBlockSize = 256f * 4f // 1024
        val pageWidth = width
        val pageHeight = height

        // 如果页面的宽或高都小于最小块大小，则不分块
        if (pageWidth <= maxBlockSize && pageHeight <= maxBlockSize) {
            return listOf(PageNode(pdfViewState, Rect(0f, 0f, 1f, 1f), aPage))
        }

        // 计算分块数
        fun calcBlockCount(length: Float): Int {
            if (length <= minBlockSize) {
                return 1 // 如果长度小于等于最小块大小，不分块
            }

            val blockCount = ceil(length / maxBlockSize).toInt()
            val actualBlockSize = length / blockCount

            // 确保实际块大小在合理范围内
            if (actualBlockSize >= minBlockSize && actualBlockSize <= maxBlockSize) {
                return blockCount
            } else {
                // 如果计算出的块大小不合适，调整块数
                val adjustedCount = ceil(length / minBlockSize).toInt()
                return adjustedCount
            }
        }

        val xBlocks = calcBlockCount(pageWidth)
        val yBlocks = calcBlockCount(pageHeight)

        // 如果只有一个块，直接返回
        if (xBlocks == 1 && yBlocks == 1) {
            return listOf(PageNode(pdfViewState, Rect(0f, 0f, 1f, 1f), aPage))
        }

        val nodes = mutableListOf<PageNode>()
        for (y in 0 until yBlocks) {
            for (x in 0 until xBlocks) {
                val left = x / xBlocks.toFloat()
                val top = y / yBlocks.toFloat()
                val right = (x + 1) / xBlocks.toFloat()
                val bottom = (y + 1) / yBlocks.toFloat()
                nodes.add(PageNode(pdfViewState, Rect(left, top, right, bottom), aPage))
            }
        }
        return nodes
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Page

        if (width != other.width) return false
        if (height != other.height) return false
        if (aPage != other.aPage) return false
        if (yOffset != other.yOffset) return false
        if (nodes != other.nodes) return false
        if (bounds != other.bounds) return false

        return true
    }

    override fun hashCode(): Int {
        var result = width.hashCode()
        result = 31 * result + height.hashCode()
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

public fun isVisible(drawScope: DrawScope, offset: Offset, bounds: Rect, page: Int): Boolean {
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

