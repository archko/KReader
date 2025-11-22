package com.archko.reader.pdf.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.cache.BitmapState
import com.archko.reader.pdf.cache.ImageCache
import com.archko.reader.pdf.entity.APage
import com.archko.reader.pdf.entity.Hyperlink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil

/**
 * @author: archko 2025/7/24 :08:20
 */
public class Page(
    private val pageViewState: PageViewState,
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

    private var thumbBitmapState by mutableStateOf<BitmapState?>(null)
    private var thumbDecoding = false
    private var thumbJob: Job? = null
    private var aspectRatio = 0f

    // 缓存的cacheKey，只在viewSize有值时计算一次
    private var cachedCacheKey: String? = null

    // 页面链接
    public var links: List<Hyperlink> = emptyList()
    private var linksLoaded = false

    // 文本选择相关
    private var structuredText: StructuredText? = null
    private var textLoaded = false
    public var currentSelection: TextSelection? by mutableStateOf(null)
    private var isSelecting = false
    private var selectionStartPoint: Offset? = null

    public fun recycleThumb() {
        thumbBitmapState?.let { ImageCache.releasePage(it) }
        thumbBitmapState = null
        thumbDecoding = false
        thumbJob?.cancel()
        thumbJob = null
    }

    /**
     * 加载页面文本结构
     */
    public fun loadText() {
        if (textLoaded || pageViewState.textSelector == null) return

        structuredText = pageViewState.textSelector.getStructuredText(aPage.index)
        textLoaded = true
    }

    /**
     * 清理文本选择状态
     */
    public fun clearTextSelection() {
        currentSelection = null
        isSelecting = false
        selectionStartPoint = null
    }

    /**
     * 加载页面链接
     */
    public fun loadLinks() {
        if (linksLoaded) return

        links = pageViewState.state.getPageLinks(aPage.index)
        linksLoaded = true
    }

    /**
     * 查找点击位置的链接
     */
    public fun findLinkAtPoint(x: Float, y: Float): Hyperlink? {
        if (links.isEmpty()) {
            return null
        }

        val pagePoint = screenToPagePoint(x, y)
        val foundLink = Hyperlink.findLinkAtPoint(links, pagePoint.x, pagePoint.y)
        return foundLink
    }

    /**
     * 将屏幕坐标转换为Page坐标
     */
    private fun screenToPagePoint(screenX: Float, screenY: Float): Offset {
        // 将点击坐标转换为页面相对坐标
        val pageX = screenX - bounds.left
        val pageY = screenY - bounds.top

        // 转换为原始Page坐标
        val orignalX: Float
        val orignalY: Float

        if (aPage.hasCrop() && pageViewState.isCropEnabled()) {
            // 有切边且启用切边：先转换为切边区域内的相对坐标，再转换为原始Page坐标
            val cropBounds = aPage.cropBounds!!
            val relativeX = pageX / bounds.width
            val relativeY = pageY / bounds.height

            orignalX = cropBounds.left + relativeX * cropBounds.width
            orignalY = cropBounds.top + relativeY * cropBounds.height
        } else {
            // 无切边：直接按比例转换
            val relativeX = pageX / bounds.width
            val relativeY = pageY / bounds.height

            orignalX = relativeX * aPage.width
            orignalY = relativeY * aPage.height
        }

        //println("Page.screenToPagePoint: screen($screenX, $screenY) -> page($pageX, $pageY) -> page($orignalX, $orignalY), bounds: $bounds, aPage: ${aPage.width}x${aPage.height}")
        return Offset(orignalX, orignalY)
    }

    /**
     * 开始文本选择
     */
    public fun startTextSelection(screenX: Float, screenY: Float): Boolean {
        loadText()

        val pagePoint = screenToPagePoint(screenX, screenY)
        val startPoint = PagePoint(pagePoint.x, pagePoint.y)

        structuredText ?: return false

        // 开始选择时不立即高亮，等待拖拽
        isSelecting = true
        selectionStartPoint = Offset(screenX, screenY)

        // 创建初始选择状态，但不包含任何高亮区域
        currentSelection = TextSelection(
            startPoint = startPoint,
            endPoint = startPoint,
            text = "",
            quads = emptyArray()
        )

        //println("startTextSelection: 开始选择，起始点: $startPoint")
        return true
    }

    /**
     * 更新文本选择
     */
    public fun updateTextSelection(screenX: Float, screenY: Float) {
        if (!isSelecting || selectionStartPoint == null) return

        val structText = structuredText ?: return
        val startPoint = currentSelection?.startPoint ?: return

        val pagePoint = screenToPagePoint(screenX, screenY)
        val endPoint = PagePoint(pagePoint.x, pagePoint.y)

        // 只有当起始点和结束点不同时才进行高亮
        if (startPoint.x != endPoint.x || startPoint.y != endPoint.y) {
            val quads = structText.highlight(startPoint, endPoint)
            //println("updateTextSelection.highlight: startPoint=$startPoint, endPoint=$endPoint, quads.size=${quads.size}")

            val selectedText = structText.copy(startPoint, endPoint)
            currentSelection = TextSelection(
                startPoint = startPoint,
                endPoint = endPoint,
                text = selectedText,
                quads = quads
            )

            //println("updateTextSelection: 选中文本: '$selectedText'")
        }
    }

    /**
     * 结束文本选择
     */
    public fun endTextSelection(): TextSelection? {
        isSelecting = false
        selectionStartPoint = null

        val selection = currentSelection
        // 只有当有实际选中的文本时才返回选择结果
        return if (selection != null && selection.text.isNotBlank() && selection.quads.isNotEmpty()) {
            println("endTextSelection: 返回选择结果: '${selection.text}'")
            selection
        } else {
            println("endTextSelection: 没有选中文本，返回null")
            null
        }
    }

    // 异步加载缩略图，参考PageNode解码逻辑
    public fun loadThumbnail() {
        if (thumbDecoding) return

        val cacheKey = cachedCacheKey ?: run {
            val (thumbWidth, thumbHeight) = DecoderAdapter.calculateThumbnailSize(
                aPage.width,
                aPage.height
            )
            val cacheKey =
                "thumb-${aPage.index}-${thumbWidth}x${thumbHeight}-${pageViewState.isCropEnabled()}"
            cachedCacheKey = cacheKey
            cacheKey
        }

        val cachedState = ImageCache.acquirePage(cacheKey)
        if (cachedState != null) {
            thumbBitmapState?.let { ImageCache.releasePage(it) }
            thumbBitmapState = cachedState
            return
        }

        // 开始解码
        startThumbnailDecoding(cacheKey)
    }

    private fun startThumbnailDecoding(cacheKey: String) {
        thumbDecoding = true
        thumbJob?.cancel()
        thumbJob = pageViewState.decodeScope.launch {
            if (!isScopeActive()) {
                thumbDecoding = false
                return@launch
            }

            val decodeTask = DecodeTask(
                type = DecodeTask.TaskType.PAGE,
                pageIndex = aPage.index,
                decodeKey = cacheKey,
                aPage = aPage,
                zoom = 1f,
                bounds,
                width.toInt(),
                height.toInt(),
                crop = pageViewState.isCropEnabled(),
                callback = object : DecodeCallback {
                    override fun onDecodeComplete(
                        bitmap: ImageBitmap?,
                        isThumb: Boolean,
                        error: Throwable?
                    ) {
                        if (bitmap != null && !pageViewState.isShutdown()) {
                            val newState = ImageCache.putPage(cacheKey, bitmap)
                            pageViewState.decodeScope.launch(Dispatchers.Main) {
                                if (!pageViewState.isShutdown()) {
                                    thumbBitmapState?.let { ImageCache.releasePage(it) }
                                    thumbBitmapState = newState
                                    setAspectRatio(bitmap.width, bitmap.height)
                                } else {
                                    ImageCache.releasePage(newState)
                                }
                            }
                        } else {
                            if (error != null) {
                                println("Page thumbnail decode error: ${error.message}")
                            }
                        }
                        thumbDecoding = false
                    }

                    override fun shouldRender(pageNumber: Int, isFullPage: Boolean): Boolean {
                        return !pageViewState.isShutdown() && isPageInRenderList(pageNumber)
                    }
                }
            )

            // 提交任务到DecodeService
            pageViewState.decodeService?.submitTask(decodeTask)
        }
    }

    private fun isPageInRenderList(pageNumber: Int): Boolean {
        return pageViewState.pageToRender.any { it.aPage.index == pageNumber }
    }

    private fun isScopeActive(): Boolean {
        if (pageViewState.isShutdown()) {
            println("[Page.loadThumbnail] page=PageViewState已关闭:${aPage.index}")
            thumbDecoding = false
            return false
        }
        return true
    }

    private fun setAspectRatio(width: Int, height: Int) {
        setAspectRatio(width * 1.0f / height)
    }

    private fun setAspectRatio(aspectRatio: Float) {
        if (pageViewState.isCropEnabled() && this.aspectRatio != aspectRatio) {
            val abs: Float = abs(aspectRatio - this.aspectRatio)
            val changed = abs > 0.008
            this.aspectRatio = aspectRatio
            if (changed) {
                //println("Page.loadThumbnail: 页面${aPage.index}检测到切边，${abs}, bounds=${aPage.cropBounds}")
                pageViewState.invalidatePageSizes()
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

        if (aspectRatio == 0f) {
            aspectRatio = width * 1.0f / height
        }
        invalidateNodes()
    }

    public fun draw(drawScope: DrawScope, offset: Offset, vZoom: Float) {
        // 计算当前缩放下的实际显示尺寸和位置
        // Page 的属性是基于 pageViewState.vZoom 计算的，但当前的 vZoom 可能已经改变,直接用bounds在缩放的时候会白屏
        val scaleRatio = vZoom / pageViewState.vZoom
        val currentBounds = Rect(
            bounds.left * scaleRatio,
            bounds.top * scaleRatio,
            bounds.right * scaleRatio,
            bounds.bottom * scaleRatio
        )

        // 检查页面是否真正可见（用于绘制判断）
        val isActuallyVisible = isPageVisible(drawScope, offset, currentBounds)

        // 如果页面不在可见区域且不在预加载列表中，直接返回
        if (!isActuallyVisible && !isPageInRenderList(aPage.index)) {
            return
        }

        val cacheKey = cachedCacheKey ?: run {
            val (thumbWidth, thumbHeight) = DecoderAdapter.calculateThumbnailSize(
                aPage.width,
                aPage.height
            )
            val cacheKey =
                "thumb-${aPage.index}-${thumbWidth}x${thumbHeight}-${pageViewState.isCropEnabled()}"
            cachedCacheKey = cacheKey
            cacheKey
        }

        // 尝试获取缓存的缩略图
        if (thumbBitmapState == null) {
            val cachedState = ImageCache.acquirePage(cacheKey)
            if (cachedState != null) {
                thumbBitmapState = cachedState
            }
        }

        if (null != thumbBitmapState && thumbBitmapState!!.isRecycled()) {
            thumbBitmapState = null
        }

        // 始终尝试加载缩略图（包括预加载页面）
        if (thumbBitmapState == null && !thumbDecoding) {
            loadThumbnail()
            return
        }

        // 计算当前尺寸（无论是否可见都需要，因为node需要这些参数）
        val currentWidth = width * scaleRatio
        val currentHeight = height * scaleRatio

        // 只有真正可见的页面才绘制缩略图和UI元素
        if (isActuallyVisible) {
            //println("page.draw.page:${aPage.index}, offset:$offset, bounds:$bounds, currentBounds:$currentBounds, $thumbBitmapState")

            // 优先绘制缩略图
            thumbBitmapState?.let { state ->
                drawScope.drawImage(
                    image = state.bitmap,
                    dstOffset = IntOffset(currentBounds.left.toInt(), currentBounds.top.toInt()),
                    dstSize = IntSize(currentWidth.toInt(), currentHeight.toInt())
                )

                if (!linksLoaded) {
                    loadLinks()
                }
            }
        }

        // 无论是否可见，都要调用node.draw（包括预加载区域）
        nodes.forEach { node ->
            node.draw(
                drawScope,
                currentWidth,
                currentHeight,
                currentBounds.left,
                currentBounds.top,
            )
        }

        // 绘制分割线
        if (isActuallyVisible) {
            thumbBitmapState?.let { _ ->
                drawLinks(drawScope, currentBounds)

                // 绘制文本选择高亮
                drawTextSelection(drawScope, currentBounds)
                drawSpeakingIndicator(drawScope, currentBounds)
                drawSeparator(drawScope, currentBounds)
            }
        }
    }

    /**
     * 绘制链接区域
     */
    private fun drawLinks(drawScope: DrawScope, currentBounds: Rect) {
        if (links.isEmpty()) return

        for (link in links) {
            val bbox = link.bbox ?: continue

            // 将Page坐标转换为屏幕坐标
            val linkRect = if (aPage.hasCrop() && pageViewState.isCropEnabled()) {
                // 有切边且启用切边：link的bbox是原始Page坐标，需要转换为切边后的相对坐标
                val cropBounds = aPage.cropBounds!!

                // 检查link是否在切边区域内
                if (bbox.left >= cropBounds.left && bbox.top >= cropBounds.top &&
                    bbox.right <= cropBounds.right && bbox.bottom <= cropBounds.bottom
                ) {

                    // 转换为切边后的相对坐标，然后缩放到屏幕坐标
                    val relativeLeft = (bbox.left - cropBounds.left) / cropBounds.width
                    val relativeTop = (bbox.top - cropBounds.top) / cropBounds.height
                    val relativeRight = (bbox.right - cropBounds.left) / cropBounds.width
                    val relativeBottom = (bbox.bottom - cropBounds.top) / cropBounds.height

                    Rect(
                        left = currentBounds.left + relativeLeft * currentBounds.width,
                        top = currentBounds.top + relativeTop * currentBounds.height,
                        right = currentBounds.left + relativeRight * currentBounds.width,
                        bottom = currentBounds.top + relativeBottom * currentBounds.height
                    )
                } else {
                    // link在切边区域外，跳过绘制
                    continue
                }
            } else {
                // 无切边：直接按比例转换
                val relativeLeft = bbox.left / aPage.width
                val relativeTop = bbox.top / aPage.height
                val relativeRight = bbox.right / aPage.width
                val relativeBottom = bbox.bottom / aPage.height

                Rect(
                    left = currentBounds.left + relativeLeft * currentBounds.width,
                    top = currentBounds.top + relativeTop * currentBounds.height,
                    right = currentBounds.left + relativeRight * currentBounds.width,
                    bottom = currentBounds.top + relativeBottom * currentBounds.height
                )
            }

            // 根据链接类型选择颜色
            val linkColor = if (link.linkType == Hyperlink.LINKTYPE_URL) {
                Color(0x66336EE5) // 半透明蓝色
            } else {
                Color(0x40FFA500) // 半透明橙色
            }

            drawScope.drawRect(
                color = linkColor,
                topLeft = Offset(linkRect.left, linkRect.top),
                size = Size(linkRect.width, linkRect.height)
            )
        }
    }

    /**
     * 绘制文本选择高亮
     */
    private fun drawTextSelection(drawScope: DrawScope, currentBounds: Rect) {
        val selection = currentSelection ?: return
        val textSelector = pageViewState.textSelector ?: return
        val selectionColor = Color(0x6633B5E5) // 半透明蓝色

        selection.quads.forEach { quad ->
            // 使用TextSelector将Page坐标的Quad转换为屏幕坐标
            val screenQuad = textSelector.quadToScreenQuad(quad) { pageX, pageY ->
                pagePointToScreenPoint(pageX, pageY, currentBounds)
            }

            // 绘制高亮矩形（简化处理，使用quad的边界框）
            val left = minOf(screenQuad.ul.x, screenQuad.ll.x, screenQuad.ur.x, screenQuad.lr.x)
            val top = minOf(screenQuad.ul.y, screenQuad.ll.y, screenQuad.ur.y, screenQuad.lr.y)
            val right = maxOf(screenQuad.ul.x, screenQuad.ll.x, screenQuad.ur.x, screenQuad.lr.x)
            val bottom = maxOf(screenQuad.ul.y, screenQuad.ll.y, screenQuad.ur.y, screenQuad.lr.y)

            drawScope.drawRect(
                color = selectionColor,
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top)
            )
        }
    }

    /**
     * 将Page坐标点转换为屏幕坐标点
     */
    private fun pagePointToScreenPoint(pageX: Float, pageY: Float, currentBounds: Rect): Offset {
        return if (aPage.hasCrop() && pageViewState.isCropEnabled()) {
            val cropBounds = aPage.cropBounds!!
            val relativeX = (pageX - cropBounds.left) / cropBounds.width
            val relativeY = (pageY - cropBounds.top) / cropBounds.height

            Offset(
                currentBounds.left + relativeX * currentBounds.width,
                currentBounds.top + relativeY * currentBounds.height
            )
        } else {
            val relativeX = pageX / aPage.width
            val relativeY = pageY / aPage.height

            Offset(
                currentBounds.left + relativeX * currentBounds.width,
                currentBounds.top + relativeY * currentBounds.height
            )
        }
    }

    /**
     * 绘制分割线
     */
    private fun drawSeparator(drawScope: DrawScope, currentBounds: Rect) {
        val separatorColor = Color(0xFF999999) // 浅灰色

        if (pageViewState.orientation == Vertical) {
            // 垂直滚动，从左侧开始绘制1/4宽度的水平分割线
            val separatorWidth = (currentBounds.width / 4).coerceAtLeast(1f)
            val separatorHeight = 2f

            drawScope.drawRect(
                color = separatorColor,
                topLeft = Offset(
                    currentBounds.left,
                    currentBounds.bottom - separatorHeight
                ),
                size = Size(separatorWidth, separatorHeight)
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
                size = Size(separatorWidth, separatorHeight)
            )
        }
    }

    /**
     * 绘制朗读指示边框
     */
    private fun drawSpeakingIndicator(drawScope: DrawScope, currentBounds: Rect) {
        val speakingPage = pageViewState.speakingPageIndex
        if (speakingPage != null && aPage.index == speakingPage) {
            // 绘制红色边框
            drawScope.drawRect(
                color = Color.Red,
                topLeft = Offset(currentBounds.left, currentBounds.top),
                size = Size(currentBounds.width, currentBounds.height),
                style = Stroke(width = 8f)
            )
        }
    }

    public fun recycle() {
        //println("Page.recycle:${aPage.index}, $width-$height, $yOffset")
        recycleThumb()
        clearTextSelection()
        nodes.forEach { it.recycle() }
    }

    // 计算分块配置
    private data class TileConfig(val xBlocks: Int, val yBlocks: Int) {
        // 当 xBlocks 和 yBlocks 都是 1 时，表示整个页面作为一个块
        // 这种情况下不需要分块，直接返回原始页面
        val isSingleBlock: Boolean get() = xBlocks == 1 && yBlocks == 1
    }

    public fun invalidateNodes() {
        val config = calculateTileConfig(width, height)
        //println("Page.invalidateNodes: currentConfig=$currentTileConfig, config=$config, ${aPage.index}, $width-$height, $yOffset")
        if (config == currentTileConfig) {
            return
        }

        // 先回收旧的nodes
        val oldNodes = nodes

        // 保存当前配置
        currentTileConfig = config

        // 如果是单个块，直接返回原始页面
        if (config.isSingleBlock) {
            nodes = listOf(PageNode(pageViewState, Rect(0f, 0f, 1f, 1f), aPage))
            // 回收旧nodes
            oldNodes.forEach { it.recycle() }
            return
        }

        // 创建分块节点，确保边界重叠以避免间隙
        val newNodes = mutableListOf<PageNode>()
        for (y in 0 until config.yBlocks) {
            for (x in 0 until config.xBlocks) {
                // 计算基础边界
                val baseLeft = x / config.xBlocks.toFloat()
                val baseTop = y / config.yBlocks.toFloat()
                val baseRight = (x + 1) / config.xBlocks.toFloat()
                val baseBottom = (y + 1) / config.yBlocks.toFloat()

                // 添加微小的重叠以避免间隙（除了边缘块）
                val overlap = 0.0001f // 0.1% 的重叠
                val left = if (x == 0) baseLeft else baseLeft - overlap
                val top = if (y == 0) baseTop else baseTop - overlap
                val right = if (x == config.xBlocks - 1) baseRight else baseRight + overlap
                val bottom = if (y == config.yBlocks - 1) baseBottom else baseBottom + overlap

                val rect = Rect(left, top, right, bottom)
                newNodes.add(PageNode(pageViewState, rect, aPage))
                //println("Page[${aPage.index}], scaled.w-h:$width-$height, , orignal:${aPage.getWidth(false)}-${aPage.getHeight(false)}, tile:$rect")
            }
        }
        nodes = newNodes
        // 回收旧nodes
        oldNodes.forEach { it.recycle() }
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
        public const val MIN_BLOCK_SIZE: Float = 256f * 3f // 768
        private const val MAX_BLOCK_SIZE = 256f * 4f // 1024

        // 计算分块数的通用函数
        private fun calcBlockCount(length: Float): Int {
            if (length <= MIN_BLOCK_SIZE) {
                return 1
            }
            val blockCount = ceil(length / MAX_BLOCK_SIZE).toInt()
            val actualBlockSize = length / blockCount
            if (actualBlockSize in MIN_BLOCK_SIZE..MAX_BLOCK_SIZE) {
                return blockCount
            } else {
                return ceil(length / MIN_BLOCK_SIZE).toInt()
            }
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

        private fun isPageVisible(drawScope: DrawScope, offset: Offset, bounds: Rect): Boolean {
            // 获取画布的可视区域
            val visibleRect = Rect(
                left = -offset.x,
                top = -offset.y,
                right = drawScope.size.width - offset.x,
                bottom = drawScope.size.height - offset.y
            )

            // 检查页面是否与可视区域相交
            return bounds.overlaps(visibleRect)
        }
    }
}