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
    public var yOffset: Float = 0f,
    public var xOffset: Float = 0f
) {
    public var totalScale: Float = 1f
    public var nodes: List<PageNode> = emptyList()
    private var currentTileConfig: TileConfig? = null

    //page bound, should be caculate after view measured
    internal var bounds = Rect(0f, 0f, 1f, 1f)

    // 缩略图缓存，响应式
    private var thumbBitmap by mutableStateOf<ImageBitmap?>(null)
    private var thumbDecoding = false
    private var thumbJob: Job? = null
    
    // 缓存缩略图的cacheKey，避免重复计算
    private var thumbCacheKey: String? = null

    public fun recycleThumb() {
        // 回收缩略图bitmap到缓存池
        thumbBitmap?.let { bitmap ->
            val cacheKey = thumbCacheKey ?: run {
                val w = (width / 3).toInt().coerceAtLeast(1)
                val h = (height / 3).toInt().coerceAtLeast(1)
                "thumb-${aPage.index}-${w}x${h}"
            }
            
            // 直接从ImageCache中移除，这会触发bitmap回收
            ImageCache.remove(cacheKey)
        }
        thumbBitmap = null
        thumbDecoding = false
        thumbJob?.cancel()
        thumbJob = null
        thumbCacheKey = null
    }

    // 异步加载缩略图，参考PageNode解码逻辑
    public fun loadThumbnail() {
        if (thumbDecoding) return

        val w = (width / 3).toInt().coerceAtLeast(1)
        val h = (height / 3).toInt().coerceAtLeast(1)
        // 计算并缓存cacheKey
        if (thumbCacheKey == null) {
            thumbCacheKey = "thumb-${aPage.index}-${w}x${h}"
        }
        
        val cacheKey = thumbCacheKey!!
        val cached = ImageCache.get(cacheKey)
        if (cached != null) {
            thumbBitmap = cached
            return
        }
        
        thumbDecoding = true
        thumbJob = pdfViewState.decodeScope.launch {
            // 可见性判断（Page始终可见，或可加自定义判断）
            if (!isActive) {
                thumbDecoding = false
                return@launch
            }
            
            // 检查 PdfViewState 是否已关闭
            if (pdfViewState.isShutdown()) {
                println("[Page.loadThumbnail] page=PdfViewState已关闭")
                thumbDecoding = false
                return@launch
            }
            
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
                if (!isActive) {
                    thumbDecoding = false
                    return@launch
                }
                if (pdfViewState.isShutdown()) {
                    println("[Page.loadThumbnail] page=解码后PdfViewState已关闭")
                    thumbDecoding = false
                    return@launch
                }
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
     * @param xOffset page.left
     */
    public fun update(width: Float, height: Float, rect: Rect) {
        this.width = width
        this.height = height
        this.bounds = rect
        this.yOffset = bounds.top
        this.xOffset = bounds.left
        this.totalScale = if (aPage.width == 0) 1f else width / aPage.width
        invalidateNodes()
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
                dstOffset = IntOffset(currentBounds.left.toInt(), currentBounds.top.toInt()),
                dstSize = IntSize(currentWidth.toInt(), currentHeight.toInt())
            )
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
        } else {
            if (!thumbDecoding) {
                loadThumbnail()
            }
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
        //println("Page.recycle:${aPage.index}, $width-$height, $yOffset")
        // 回收缩略图
        recycleThumb()
        // 回收所有节点
        nodes.forEach { it.recycle() }
    }

    // 计算分块数的通用函数
    private fun calcBlockCount(length: Float): Int {
        if (length <= minBlockSize) {
            return 1
        }
        val blockCount = ceil(length / maxBlockSize).toInt()
        val actualBlockSize = length / blockCount
        if (actualBlockSize >= minBlockSize && actualBlockSize <= maxBlockSize) {
            return blockCount
        } else {
            return ceil(length / minBlockSize).toInt()
        }
    }

    // 计算分块配置
    private data class TileConfig(val xBlocks: Int, val yBlocks: Int) {
        // 当 xBlocks 和 yBlocks 都是 1 时，表示整个页面作为一个块
        // 这种情况下不需要分块，直接返回原始页面
        val isSingleBlock: Boolean get() = xBlocks == 1 && yBlocks == 1
    }

    private fun calculateTileConfig(width: Float, height: Float): TileConfig {
        // 如果页面的宽或高都小于最大块大小，则不分块
        if (width <= maxBlockSize && height <= maxBlockSize) {
            return TileConfig(1, 1)
        }

        val xBlocks = calcBlockCount(width)
        val yBlocks = calcBlockCount(height)

        return TileConfig(xBlocks, yBlocks)
    }

    private fun invalidateNodes() {
        val config = calculateTileConfig(width, height)
        println("Page.invalidateNodes: currentConfig=$currentTileConfig, config=$config, ${aPage.index}, $width-$height, $yOffset")
        if (config == currentTileConfig) {
            return
        }

        // 保存当前配置
        currentTileConfig = config

        // 如果是单个块，直接返回原始页面
        if (config.isSingleBlock) {
            nodes = listOf(PageNode(pdfViewState, Rect(0f, 0f, 1f, 1f), aPage))
            return
        }

        // 创建分块节点
        val newNodes = mutableListOf<PageNode>()
        for (y in 0 until config.yBlocks) {
            for (x in 0 until config.xBlocks) {
                val left = x / config.xBlocks.toFloat()
                val top = y / config.yBlocks.toFloat()
                val right = (x + 1) / config.xBlocks.toFloat()
                val bottom = (y + 1) / config.yBlocks.toFloat()
                newNodes.add(PageNode(pdfViewState, Rect(left, top, right, bottom), aPage))
            }
        }
        nodes = newNodes
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Page

        if (width != other.width) return false
        if (height != other.height) return false
        if (aPage != other.aPage) return false
        if (yOffset != other.yOffset) return false
        if (xOffset != other.xOffset) return false
        if (nodes != other.nodes) return false
        if (bounds != other.bounds) return false

        return true
    }

    override fun hashCode(): Int {
        var result = width.hashCode()
        result = 31 * result + height.hashCode()
        result = 31 * result + aPage.hashCode()
        result = 31 * result + yOffset.hashCode()
        result = 31 * result + xOffset.hashCode()
        result = 31 * result + nodes.hashCode()
        result = 31 * result + bounds.hashCode()
        return result
    }

    public companion object {
        private val minBlockSize = 256f * 3f // 768
        private val maxBlockSize = 256f * 4f // 1024
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

