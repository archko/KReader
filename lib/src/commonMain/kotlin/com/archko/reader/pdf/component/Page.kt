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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Future
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

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

    // 可见的 nodes 映射：Int key = (x shl 16) or y，按需创建，零Pair分配
    private val visibleNodes: MutableMap<Int, PageNode> = mutableMapOf()
    private var currentTileConfig: TileConfig? = null

    //page bound, should be caculate after view measured
    internal var bounds = Rect(0f, 0f, 1f, 1f)

    private var thumbBitmapState by mutableStateOf<BitmapState?>(null)
    private var thumbDecoding = false
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

    /**
     * 获取页面缩略图的缓存键
     */
    public fun getThumbnailCacheKey(): String? {
        return cachedCacheKey
    }

    public fun recycleThumb() {
        thumbBitmapState?.let { ImageCache.releasePage(it) }
        thumbBitmapState = null
        thumbDecoding = false
    }

    /**
     * 加载页面文本结构
     */
    public fun loadText() {
        if (textLoaded || pageViewState.textSelector == null) {
            return
        }

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
        // Ensure structuredText is loaded before selection
        if (structuredText == null) {
            loadText()
        }

        val pagePoint = screenToPagePoint(screenX, screenY)
        val startPoint = PagePoint(pagePoint.x, pagePoint.y)

        if (structuredText == null) {
            //println("Page.startTextSelection: structuredText is still null after loading, cannot start selection")
            return false
        }

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

        //println("Page.startTextSelection: 开始选择，起始点: $startPoint")
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
            //println("Page.updateTextSelection.highlight: startPoint=$startPoint, endPoint=$endPoint, quads.size=${quads.size}")

            val selectedText = structText.selectText(aPage.index, startPoint, endPoint)
            currentSelection = TextSelection(
                startPoint = startPoint,
                endPoint = endPoint,
                text = selectedText,
                quads = quads
            )

            //println("Page.updateTextSelection: 选中文本: '$selectedText'")
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
            println("Page.endTextSelection: 返回选择结果: '${selection.text}'")
            selection
        } else {
            println("Page.endTextSelection: 没有选中文本，返回null")
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

            if (!isScopeActive()) {
                thumbDecoding = false
                return
            }

            val decodeTask = DecodeTask(
                type = TaskType.PAGE,
                pageIndex = aPage.index,
                key = cacheKey,
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
                            CoroutineScope(Dispatchers.Main).launch {
                                if (!pageViewState.isShutdown()) {
                                    thumbBitmapState?.let { ImageCache.releasePage(it) }
                                    thumbBitmapState = newState
                                    setAspectRatio(bitmap.width, bitmap.height)
                                } else {
                                    ImageCache.releasePage(newState)
                                }
                            }
                            // 解码完成，触发UI刷新
                            pageViewState.notifyDecodeCompleted()
                        } else {
                            if (error != null) {
                                println("Page thumbnail decode error: ${error.message}")
                            }
                        }
                        thumbDecoding = false
                    }

                    override fun shouldRender(pageNumber: Int, isFullPage: Boolean): Boolean {
                        // 优化：使用快速查找方法
                        return !pageViewState.isShutdown() && pageViewState.isPageInVisibleList(
                            pageNumber
                        )
                    }

                    override fun onFinish(pageNumber: Int) {
                        thumbDecoding = false
                    }
                }
            )

            // 提交任务到DecodeService
            pageViewState.decodeService?.submitTask(decodeTask)

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
        // 尺寸变化，重新计算节点配置
        invalidateNodes()
    }

    private fun drawNodes(
        drawScope: DrawScope,
        currentWidth: Float,
        currentHeight: Float,
        currentBounds: Rect
    ) {
        val config = currentTileConfig

        // 如果没有配置或没有可见nodes，不绘制
        if (config == null || visibleNodes.isEmpty()) {
            return
        }

        if (config.isSingleBlock) {
            // 单块模式：只绘制 (0, 0) 节点，key=0
            visibleNodes[0]?.draw(
                drawScope,
                currentWidth,
                currentHeight,
                currentBounds.left,
                currentBounds.top,
            )
            return
        }

        for (node in visibleNodes) {
            node.value.draw(
                drawScope,
                currentWidth,
                currentHeight,
                currentBounds.left,
                currentBounds.top,
            )
        }
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

        // 直接计算可见区域边界，避免创建 visibleRect 对象
        val vrLeft = -offset.x
        val vrTop = -offset.y
        val vrRight = drawScope.size.width - offset.x
        val vrBottom = drawScope.size.height - offset.y

        // 检查页面是否真正可见：内联 overlaps 检查
        val isActuallyVisible = currentBounds.left < vrRight
                && currentBounds.right > vrLeft
                && currentBounds.top < vrBottom
                && currentBounds.bottom > vrTop

        // 如果页面不在可见区域且不在预加载列表中，直接返回
        if (!isActuallyVisible && !pageViewState.isPageInVisibleList(aPage.index)) {
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

        drawNodes(drawScope, currentWidth, currentHeight, currentBounds)

        // 绘制分割线
        if (isActuallyVisible) {
            thumbBitmapState?.let { _ ->
                drawLinks(drawScope, currentBounds)

                // 绘制文本选择高亮
                drawTextSelection(drawScope, currentBounds)
                drawSearchHighlight(drawScope, currentBounds)
                drawSpeakingIndicator(drawScope, currentBounds)
                drawAnnotation(drawScope)
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
     * 绘制搜索结果高亮
     */
    private fun drawSearchHighlight(drawScope: DrawScope, currentBounds: Rect) {
        val highlightQuads = pageViewState.searchHighlightQuads[aPage.index] ?: return
        val isCurrentPage = pageViewState.currentSearchPageIndex == aPage.index

        // 所有结果用黄色半透明
        val normalHighlightColor = Color(0x55FFFF00)

        highlightQuads.forEach { quad ->
            // 将Page坐标的Quad转换为屏幕坐标
            val screenQuad = quadToScreenQuad(quad, currentBounds)

            // 绘制高亮矩形（使用quad的边界框）
            val left = minOf(screenQuad.ul.x, screenQuad.ll.x, screenQuad.ur.x, screenQuad.lr.x)
            val top = minOf(screenQuad.ul.y, screenQuad.ll.y, screenQuad.ur.y, screenQuad.lr.y)
            val right = maxOf(screenQuad.ul.x, screenQuad.ll.x, screenQuad.ur.x, screenQuad.lr.x)
            val bottom = maxOf(screenQuad.ul.y, screenQuad.ll.y, screenQuad.ur.y, screenQuad.lr.y)

            drawScope.drawRect(
                color = normalHighlightColor,
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top)
            )
        }

        // 如果是当前页面，在第一个结果上再绘制一层橙色高亮
        if (isCurrentPage && highlightQuads.isNotEmpty()) {
            val currentQuad = highlightQuads[0]
            val screenQuad = quadToScreenQuad(currentQuad, currentBounds)

            val left = minOf(screenQuad.ul.x, screenQuad.ll.x, screenQuad.ur.x, screenQuad.lr.x)
            val top = minOf(screenQuad.ul.y, screenQuad.ll.y, screenQuad.ur.y, screenQuad.lr.y)
            val right = maxOf(screenQuad.ul.x, screenQuad.ll.x, screenQuad.ur.x, screenQuad.lr.x)
            val bottom = maxOf(screenQuad.ul.y, screenQuad.ll.y, screenQuad.ur.y, screenQuad.lr.y)

            drawScope.drawRect(
                color = Color(0x88FFA500), // 橙色，更不透明
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top)
            )
        }
    }

    /**
     * 将Page坐标的Quad转换为屏幕坐标的Quad
     */
    private fun quadToScreenQuad(
        quad: com.archko.reader.pdf.entity.DocQuad,
        currentBounds: Rect
    ): com.archko.reader.pdf.entity.DocQuad {
        return com.archko.reader.pdf.entity.DocQuad(
            ul = pagePointToScreenPoint(quad.ul.x, quad.ul.y, currentBounds),
            ur = pagePointToScreenPoint(quad.ur.x, quad.ur.y, currentBounds),
            ll = pagePointToScreenPoint(quad.ll.x, quad.ll.y, currentBounds),
            lr = pagePointToScreenPoint(quad.lr.x, quad.lr.y, currentBounds)
        )
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

    private fun drawAnnotation(
        drawScope: DrawScope
    ) {
        pageViewState.annotationManager.annotations[aPage.index]?.forEach { anno ->
            drawAnnotationPath(
                drawScope, anno.points,
                anno.config.color,
                anno.config.strokeWidth,
            )
        }

        // 3. 绘制【正在实时画】的线
        pageViewState.activeDrawingAnno?.let { (index, anno) ->
            if (index == aPage.index) {
                drawAnnotationPath(
                    drawScope,
                    anno.points,
                    anno.config.color,
                    anno.config.strokeWidth,
                )
            }
        }
    }

    private fun drawAnnotationPath(
        drawScope: DrawScope,
        relPoints: List<Offset>,
        color: Color,
        stroke: Float
    ) {
        if (relPoints.size < 2) return
        val path = androidx.compose.ui.graphics.Path()
        relPoints.forEachIndexed { i, relP ->
            // 关键坐标转换：基于 Page 的 xOffset/yOffset 和相对比例
            val px = xOffset + relP.x * width
            val py = yOffset + relP.y * height
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawScope.drawPath(path, color, style = Stroke(width = stroke))
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
        // 清理所有可见 nodes
        visibleNodes.values.forEach { pageViewState.nodePool.release(it) }
        visibleNodes.clear()
        currentTileConfig = null
        aspectRatio = 0f
    }

    // 计算分块配置
    private data class TileConfig(val xBlocks: Int, val yBlocks: Int) {
        // 当 xBlocks 和 yBlocks 都是 1 时，表示整个页面作为一个块
        // 这种情况下不需要分块，直接返回原始页面
        val isSingleBlock: Boolean get() = xBlocks == 1 && yBlocks == 1
    }

    /**
     * 只计算行列配置，不创建实际的 nodes
     */
    public fun invalidateNodes() {
        val config = calculateTileConfig(width, height)
        //println("Page.invalidateNodes: currentConfig=$currentTileConfig, config=$config, ${aPage.index}, $width-$height, $yOffset")
        if (config == currentTileConfig) {
            return
        }

        // 保存当前配置
        currentTileConfig = config

        // 配置变化，清空所有可见 nodes
        visibleNodes.values.forEach { pageViewState.nodePool.release(it) }
        visibleNodes.clear()
    }

    /**
     * 根据可见区域按需创建/释放 nodes
     * @param visLeft/visTop/visRight/visBottom 可见区域边界（屏幕坐标），避免Rect分配
     * @param scaleRatio 缩放比例
     */
    public fun updateVisibleNodes(
        visLeft: Float, visTop: Float, visRight: Float, visBottom: Float,
        scaleRatio: Float
    ) {
        val config = currentTileConfig ?: run {
            invalidateNodes()
            currentTileConfig!!
        }

        // 内联 currentBounds 计算，避免 Rect 分配
        val cbLeft = bounds.left * scaleRatio
        val cbTop = bounds.top * scaleRatio
        val cbRight = bounds.right * scaleRatio
        val cbBottom = bounds.bottom * scaleRatio

        val currentWidth = width * scaleRatio
        val currentHeight = height * scaleRatio

        // 单块模式：只有一个 node
        if (config.isSingleBlock) {
            visibleNodes.values.forEach { pageViewState.nodePool.release(it) }
            visibleNodes.clear()
            val node = pageViewState.nodePool.acquire(pageViewState, Rect(0f, 0f, 1f, 1f), aPage)
            visibleNodes[0] = node  // key=0 表示 (0,0)
            node.decode(currentWidth, currentHeight, pageViewState.vZoom)
            return
        }

        // 分块模式：计算可见区域在页面中的相对位置 [0, 1]
        val pageVisibleLeft = (visLeft - cbLeft) / currentWidth
        val pageVisibleRight = (visRight - cbLeft) / currentWidth
        val pageVisibleTop = (visTop - cbTop) / currentHeight
        val pageVisibleBottom = (visBottom - cbTop) / currentHeight

        // 计算需要可见的 block x/y indices 范围
        val minBlockX =
            floor(pageVisibleLeft * config.xBlocks).toInt().coerceIn(0, config.xBlocks - 1)
        val maxBlockX =
            ceil(pageVisibleRight * config.xBlocks).toInt().coerceIn(0, config.xBlocks - 1)
        val minBlockY =
            floor(pageVisibleTop * config.yBlocks).toInt().coerceIn(0, config.yBlocks - 1)
        val maxBlockY =
            ceil(pageVisibleBottom * config.yBlocks).toInt().coerceIn(0, config.yBlocks - 1)

        // 创建/更新可见的 nodes（不创建临时Set）
        val xBlockCount = config.xBlocks.toFloat()
        val yBlockCount = config.yBlocks.toFloat()
        for (y in minBlockY..maxBlockY) {
            for (x in minBlockX..maxBlockX) {
                val key = (x shl 16) or y  // Int 编码代替 Pair，零分配

                // 按需创建 node
                if (!visibleNodes.containsKey(key)) {
                    val left = x / xBlockCount
                    val top = y / yBlockCount
                    val right = (x + 1) / xBlockCount
                    val bottom = (y + 1) / yBlockCount
                    val rect = Rect(left, top, right, bottom)

                    val node = pageViewState.nodePool.acquire(pageViewState, rect, aPage)
                    visibleNodes[key] = node
                }
            }
        }

        // 移除不在范围内的 nodes，同时触发保留节点的解码
        val iterator = visibleNodes.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val key = entry.key
            val x = key ushr 16
            val y = key and 0xFFFF
            if (x !in minBlockX..maxBlockX || y !in minBlockY..maxBlockY) {
                pageViewState.nodePool.release(entry.value)
                iterator.remove()
            } else {
                entry.value.decode(currentWidth, currentHeight, pageViewState.vZoom)
            }
        }
    }

    /**
     * 清理可见 nodes（页面不再可见时调用）
     */
    public fun clearVisibleNodes() {
        visibleNodes.values.forEach { pageViewState.nodePool.release(it) }
        visibleNodes.clear()
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
        if (bounds != other.bounds) return false

        return true
    }

    override fun hashCode(): Int {
        var result = width.hashCode()
        result = 31 * result + height.hashCode()
        result = 31 * result + aPage.hashCode()
        result = 31 * result + yOffset.hashCode()
        result = 31 * result + xOffset.hashCode()
        result = 31 * result + bounds.hashCode()
        return result
    }

    override fun toString(): String {
        return "Page(page=${aPage.index}, w-h=$width-$height), x-y:$xOffset-$yOffset, $bounds"
    }

    public companion object {
        // 核心约束：仅保留最小块、最大块，取消基础块
        public const val MIN_BLOCK: Float = 256f
        public const val MAX_BLOCK: Float = 256f * 2f

        // 单轴块数计算：优先1块，仅超出MAX_BLOCK才分块（延迟重建核心）
        private fun calcAxisBlocks(length: Float): Int {
            if (length <= 0) return 1

            // 核心规则：只要长度 ≤ 最大块1536，就用1块（不管最小块512）
            if (length <= MAX_BLOCK) {
                return 1
            }

            // 长度 > 最大块1536 → 按1536分块，保证实际块大小 ≥ 512
            var blocks = ceil(length / MAX_BLOCK).toInt()
            val actualBlockSize = length / blocks

            // 兜底：如果分块后实际块大小 < 512，按最小块重新分
            if (actualBlockSize < MIN_BLOCK) {
                blocks = ceil(length / MIN_BLOCK).toInt()
            }
            return blocks
        }

        private fun calculateTileConfig(
            width: Float,
            height: Float,
        ): TileConfig {
            val xBlocks = calcAxisBlocks(width)
            val yBlocks = calcAxisBlocks(height)
            return TileConfig(xBlocks, yBlocks)
        }

        private fun isPageVisible(visibleRect: Rect, bounds: Rect): Boolean {
            // 检查页面是否与可视区域相交
            return bounds.overlaps(visibleRect)
        }
    }
}
