package com.archko.reader.pdf.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.cache.ImageCache
import com.archko.reader.pdf.entity.APage
import com.archko.reader.pdf.entity.Hyperlink
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

    private var thumbBitmap by mutableStateOf<ImageBitmap?>(null)
    private var thumbDecoding = false
    private var thumbJob: Job? = null

    // 缓存的cacheKey，只在viewSize有值时计算一次
    private var cachedCacheKey: String? = null

    // 页面链接
    public var links: List<Hyperlink> = emptyList()
    private var linksLoaded = false

    public fun recycleThumb() {
        thumbBitmap = null
        thumbDecoding = false
        thumbJob?.cancel()
        thumbJob = null
    }

    /**
     * 加载页面链接
     */
    public fun loadLinks() {
        if (linksLoaded) return

        links = pdfViewState.state.getPageLinks(aPage.index)
        linksLoaded = true
    }

    /**
     * 查找点击位置的链接
     */
    public fun findLinkAtPoint(x: Float, y: Float): Hyperlink? {
        if (links.isEmpty()) {
            return null
        }

        // 将点击坐标转换为页面相对坐标
        val pageX = x - bounds.left
        val pageY = y - bounds.top

        // 转换为原始PDF坐标
        val pdfX = pageX / totalScale
        val pdfY = pageY / totalScale

        val foundLink = Hyperlink.findLinkAtPoint(links, pdfX, pdfY)
        return foundLink
    }

    // 异步加载缩略图，参考PageNode解码逻辑
    public fun loadThumbnail() {
        if (thumbDecoding) return

        // 缩略图宽度为当前view宽度的1/3
        val thumbWidth = (pdfViewState.viewSize.width / THUMB_RATIO).coerceAtLeast(1)
        // 根据原始宽高比计算缩略图高度
        val thumbHeight = if (aPage.width > 0) {
            (thumbWidth * aPage.height / aPage.width).coerceAtLeast(1)
        } else {
            (height / THUMB_RATIO).toInt().coerceAtLeast(1)
        }

        val cacheKey = cachedCacheKey ?: run {
            val cacheKey = "thumb-${aPage.index}-${thumbWidth}x${thumbHeight}"
            cachedCacheKey = cacheKey
            cacheKey
        }
        val cached = ImageCache.get(cacheKey)
        if (cached != null) {
            thumbBitmap = cached
            return
        }
        thumbDecoding = true
        thumbJob = pdfViewState.decodeScope.launch {
            if (!isActive || pdfViewState.isShutdown()) {
                println("[Page.loadThumbnail] page=PdfViewState已关闭")
                thumbDecoding = false
                return@launch
            }

            try {
                val region = Rect(0f, 0f, 1f, 1f)
                // 计算缩略图的缩放比例：缩略图宽度 / 原始页面宽度
                val thumbScale = if (aPage.width > 0) {
                    thumbWidth.toFloat() / aPage.width
                } else {
                    1f / THUMB_RATIO
                }

                val bitmap = pdfViewState.state.renderPageRegion(
                    region,
                    aPage.index,
                    thumbScale,
                    pdfViewState.viewSize,
                    thumbWidth,
                    thumbHeight
                )

                ImageCache.put(cacheKey, bitmap)
                if (!isActive || pdfViewState.isShutdown()) {
                    println("[Page.loadThumbnail] page=解码后PdfViewState已关闭")
                    thumbDecoding = false
                    return@launch
                }
                thumbBitmap = bitmap
            } catch (_: Exception) {
            } finally {
                thumbDecoding = false
            }
        }
    }

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
            val currentWidth = width * scaleRatio
            val currentHeight = height * scaleRatio
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
                    currentBounds.left,
                    currentBounds.top,
                    totalScale * scaleRatio
                )
            }

            // 加载链接（在缩略图加载完成后）
            if (!linksLoaded) {
                loadLinks()
            }

            // 绘制链接区域（调试用，可以注释掉）
            drawLinks(drawScope, currentBounds, scaleRatio)
        } else {
            if (!thumbDecoding) {
                loadThumbnail()
            }
        }

        // 绘制分割线
        drawSeparator(drawScope, currentBounds)
    }

    /**
     * 绘制链接区域
     */
    private fun drawLinks(drawScope: DrawScope, currentBounds: Rect, scaleRatio: Float) {
        if (links.isEmpty()) return

        for (link in links) {
            val bbox = link.bbox ?: continue

            // 将PDF坐标转换为屏幕坐标
            val linkRect = Rect(
                left = currentBounds.left + bbox.left * totalScale * scaleRatio,
                top = currentBounds.top + bbox.top * totalScale * scaleRatio,
                right = currentBounds.left + bbox.right * totalScale * scaleRatio,
                bottom = currentBounds.top + bbox.bottom * totalScale * scaleRatio
            )

            // 根据链接类型选择颜色
            val linkColor = if (link.linkType == Hyperlink.LINKTYPE_PAGE) {
                Color(0x40FFD700) // 半透明淡黄色
            } else {
                Color(0x40FFA500) // 半透明橙色
            }

            drawScope.drawRect(
                color = linkColor,
                topLeft = Offset(linkRect.left, linkRect.top),
                size = androidx.compose.ui.geometry.Size(linkRect.width, linkRect.height)
            )
        }
    }

    /**
     * 绘制分割线
     */
    private fun drawSeparator(drawScope: DrawScope, currentBounds: Rect) {
        val separatorColor = Color(0xFF999999) // 浅灰色

        if (pdfViewState.orientation == Vertical) {
            // 垂直滚动，从左侧开始绘制1/4宽度的水平分割线
            val separatorWidth = (currentBounds.width / 4).coerceAtLeast(1f)
            val separatorHeight = 2f

            drawScope.drawRect(
                color = separatorColor,
                topLeft = Offset(
                    currentBounds.left,
                    currentBounds.bottom - separatorHeight
                ),
                size = androidx.compose.ui.geometry.Size(separatorWidth, separatorHeight)
            )
        } else {
            // 横向滚动，从顶部开始绘1/4高度的垂直分割线
            val separatorWidth = 2f
            val separatorHeight = (currentBounds.height / 4).coerceAtLeast(1f)

            drawScope.drawRect(
                color = separatorColor,
                topLeft = Offset(
                    currentBounds.right - separatorWidth,
                    currentBounds.top
                ),
                size = androidx.compose.ui.geometry.Size(separatorWidth, separatorHeight)
            )
        }
    }

    public fun recycle() {
        //println("Page.recycle:${aPage.index}, $width-$height, $yOffset")
        recycleThumb()
        nodes.forEach { it.recycle() }
    }

    // 计算分块数的通用函数
    private fun calcBlockCount(length: Float): Int {
        if (length <= MIN_BLOCK_SIZE) {
            return 1
        }
        val blockCount = ceil(length / MAX_BLOCK_SIZE).toInt()
        val actualBlockSize = length / blockCount
        if (actualBlockSize >= MIN_BLOCK_SIZE && actualBlockSize <= MAX_BLOCK_SIZE) {
            return blockCount
        } else {
            return ceil(length / MIN_BLOCK_SIZE).toInt()
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
        if (width <= MAX_BLOCK_SIZE && height <= MAX_BLOCK_SIZE) {
            return TileConfig(1, 1)
        }

        val xBlocks = calcBlockCount(width)
        val yBlocks = calcBlockCount(height)

        return TileConfig(xBlocks, yBlocks)
    }

    private fun invalidateNodes() {
        val config = calculateTileConfig(width, height)
        //println("Page.invalidateNodes: currentConfig=$currentTileConfig, config=$config, ${aPage.index}, $width-$height, $yOffset")
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
        nodes.forEach { it -> it.recycle() }
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
        private const val MIN_BLOCK_SIZE = 256f * 3f // 768
        private const val MAX_BLOCK_SIZE = 256f * 4f // 1024
        private const val THUMB_RATIO = 4 //
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
    //println("page.draw.page:$page, isVisible:$visible, offset:$offset, bounds:$bounds, visibleRect:$visibleRect")
    return visible
}