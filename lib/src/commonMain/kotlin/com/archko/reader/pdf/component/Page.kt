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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
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
    private var aspectRatio = 0f

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
        val pdfX: Float
        val pdfY: Float

        if (aPage.cropBounds != null) {
            // 有切边：需要考虑切边偏移
            val cropBounds = aPage.cropBounds!!
            pdfX = pageX / totalScale + cropBounds.left
            pdfY = pageY / totalScale + cropBounds.top
        } else {
            // 无切边：直接转换
            pdfX = pageX / totalScale
            pdfY = pageY / totalScale
        }

        //println("Page.findLinkAtPoint: 页面 ${aPage.index}, 点击坐标($x, $y), 页面坐标($pageX, $pageY), PDF坐标($pdfX, $pdfY), 链接数量: ${links.size}, hasCrop:${aPage.hasCrop()}")

        val foundLink = Hyperlink.findLinkAtPoint(links, pdfX, pdfY)
        return foundLink
    }

    // 异步加载缩略图，参考PageNode解码逻辑
    public fun loadThumbnail() {
        if (thumbDecoding) return

        val ratio: Float = 1f * aPage.width / width
        val thumbWidth = 300
        val thumbHeight = (aPage.height / ratio).toInt()
        if (aspectRatio == 0f) {
            aspectRatio = 1f * thumbWidth / thumbHeight
        }

        val cacheKey = cachedCacheKey ?: run {
            val cacheKey = "thumb-${aPage.index}-${thumbWidth}x${thumbHeight}-${pdfViewState.isCropEnabled()}"
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
            if (!isScopeActive()) {
                return@launch
            }

            try {
                val bitmap = pdfViewState.state.renderPage(
                    aPage,
                    pdfViewState.viewSize,
                    thumbWidth,
                    thumbHeight,
                    pdfViewState.isCropEnabled()
                )

                ImageCache.put(cacheKey, bitmap)
                if (!isScopeActive()) {
                    return@launch
                }
                thumbBitmap = bitmap

                setAspectRatio(bitmap.width, bitmap.height)
            } catch (_: Exception) {
            } finally {
                thumbDecoding = false
            }
        }
    }

    private fun CoroutineScope.isScopeActive(): Boolean {
        if (!isActive || pdfViewState.isShutdown()) {
            println("[Page.loadThumbnail] page=PdfViewState已关闭")
            thumbDecoding = false
            return false
        }
        return true
    }

    private fun setAspectRatio(width: Int, height: Int) {
        setAspectRatio(width * 1.0f / height)
    }

    private fun setAspectRatio(aspectRatio: Float) {
        if (pdfViewState.isCropEnabled() && this.aspectRatio != aspectRatio) {
            val abs: Float = abs(aspectRatio - this.aspectRatio)
            val changed = this.aspectRatio != 0f && abs > 0.008
            println("Page.loadThumbnail: 页面${aPage.index}检测到切边，${abs}, bounds=${aPage.cropBounds}")
            this.aspectRatio = aspectRatio
            if (changed) {
                pdfViewState.invalidatePageSizes()
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
            /*nodes.forEach { node ->
                node.draw(
                    drawScope,
                    offset,
                    currentWidth,
                    currentHeight,
                    currentBounds.left,
                    currentBounds.top,
                    totalScale
                )
            }*/

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
            val linkRect = if (aPage.hasCrop()) {
                // 有切边：需要考虑切边偏移
                val cropBounds = aPage.cropBounds!!
                Rect(
                    left = currentBounds.left + (bbox.left - cropBounds.left) * totalScale * scaleRatio,
                    top = currentBounds.top + (bbox.top - cropBounds.top) * totalScale * scaleRatio,
                    right = currentBounds.left + (bbox.right - cropBounds.left) * totalScale * scaleRatio,
                    bottom = currentBounds.top + (bbox.bottom - cropBounds.top) * totalScale * scaleRatio
                )
            } else {
                // 无切边：直接转换
                Rect(
                    left = currentBounds.left + bbox.left * totalScale * scaleRatio,
                    top = currentBounds.top + bbox.top * totalScale * scaleRatio,
                    right = currentBounds.left + bbox.right * totalScale * scaleRatio,
                    bottom = currentBounds.top + bbox.bottom * totalScale * scaleRatio
                )
            }

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