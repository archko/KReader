package com.archko.reader.pdf.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size as GeomSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.cache.ImageCache
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.APage
import com.archko.reader.pdf.entity.DocQuad
import com.archko.reader.pdf.entity.Hyperlink
import com.archko.reader.pdf.state.AnnotationManager
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/**
 * 文档视图状态：基础布局（文档空间）+ 视口 + 页内 LOD tile。
 * 缩放只影响 ViewportState，不触发重新布局。
 */
public class PageViewState(
    public val list: List<APage>,
    public val state: ImageDecoder,
    public val annotationManager: AnnotationManager,
    public var orientation: Int = Vertical,
    crop: Boolean,
    public var columnCount: Int = 1,
    public val textSelector: TextSelector? = null
) {
    internal val viewport = ViewportState()

    private var cropEnabled: Boolean = crop
    private val pendingCropPages = LinkedHashSet<Int>()
    private var cropRelayoutScheduled: Boolean = false

    public var viewOffset: Offset = Offset.Zero
    public var init: Boolean by mutableStateOf(false)
    public var totalHeight: Float by mutableFloatStateOf(0f)
    public var totalWidth: Float by mutableFloatStateOf(0f)
    public var viewSize: IntSize by mutableStateOf(IntSize.Zero)

    internal var pageToRender: List<Page> by mutableStateOf(listOf())
    public var pages: List<Page> by mutableStateOf(createPages())
    public var vZoom: Float by mutableFloatStateOf(1f)
    public var revision: Int by mutableIntStateOf(0)
    public var layoutVersion: Int by mutableIntStateOf(0)

    private var docWidth: Float = 0f
    private var docHeight: Float = 0f

    // 正在朗读的页面索引，null 表示没有朗读
    public var speakingPageIndex: Int? by mutableStateOf(null)
        private set

    // 搜索高亮状态
    public var searchHighlightQuads: Map<Int, List<DocQuad>> by mutableStateOf(emptyMap())
        private set
    public var currentSearchPageIndex: Int? by mutableStateOf(null)
        private set
    public var currentSearchResultIndex: Int by mutableStateOf(-1)
        private set

    private var preloadScreens: Float = 0.0f
    private val scanScratch = ArrayList<Page>(16)

    private var isShutdown = false
    private var lastInteractionAt: Long = 0L

    private val wantedPagesRef = AtomicReference<Set<Int>>(emptySet())

    public var onPageLinkClick: ((pageIndex: Int) -> Unit)? = null
    public var onUrlLinkClick: ((url: String) -> Unit)? = null
    public var onDecodeCompleted: (() -> Unit)? = null
    public var onLayoutApplied: (() -> Unit)? = null

    public var decodeService: DecodeService? = null
        private set

    public var activeDrawingAnno: Pair<Int, AnnotationPath>? by mutableStateOf(null)

    private data class Anchor(
        val index: Int,
        val fx: Float,
        val fy: Float,
        val screen: Offset
    )

    init {
        initDecodeService()
    }

    private fun createPages(): List<Page> {
        return list.map { aPage ->
            Page(
                this,
                aPage,
                PageGeometry(
                    aPage.width.toFloat(),
                    aPage.height.toFloat(),
                    aPage.cropBounds,
                    cropEnabled
                ),
                Rect(0f, 0f, 1f, 1f)
            )
        }
    }

    private fun initDecodeService() {
        val decoder = DecoderAdapter(
            imageDecoder = state,
            viewSize = viewSize,
            isCropEnabled = { cropEnabled }
        )
        decodeService = DecodeService(decoder)
    }

    public fun notifyDecodeCompleted() {
    }

    public fun updateDrawing(pageIndex: Int, points: List<Offset>, pathConfig: PathConfig) {
        activeDrawingAnno = pageIndex to AnnotationPath(points, config = pathConfig)
    }

    public fun finalizeDrawing(pageIndex: Int, points: List<Offset>, pathConfig: PathConfig) {
        annotationManager.addPath(pageIndex, AnnotationPath(points, config = pathConfig))
        activeDrawingAnno = null
    }

    public fun setCropEnabled(enabled: Boolean) {
        if (cropEnabled == enabled) {
            return
        }
        cropEnabled = enabled
        pendingCropPages.clear()
        pages.forEach {
            it.cropProbeRequested = false
            it.updateGeometry(
                PageGeometry(
                    it.aPage.width.toFloat(),
                    it.aPage.height.toFloat(),
                    it.aPage.cropBounds,
                    enabled
                )
            )
        }
        relayout()
    }

    public fun isCropEnabled(): Boolean = cropEnabled

    public fun updateColumnCount(columnCount: Int) {
        if (this.columnCount != columnCount) {
            this.columnCount = columnCount
            relayout()
        }
    }

    public fun setPreloadScreens(screens: Float) {
        preloadScreens = screens.coerceAtLeast(0f)
    }

    public fun getPreloadScreens(): Float = preloadScreens

    public fun updateSpeakingPageIndex(pageIndex: Int?) {
        speakingPageIndex = pageIndex
    }

    public fun updateSearchHighlight(
        highlightQuads: Map<Int, List<DocQuad>>,
        currentPageIndex: Int?,
        currentResultIndex: Int = -1
    ) {
        searchHighlightQuads = highlightQuads
        currentSearchPageIndex = currentPageIndex
        currentSearchResultIndex = currentResultIndex
    }

    public fun clearSearchHighlight() {
        searchHighlightQuads = emptyMap()
        currentSearchPageIndex = null
        currentSearchResultIndex = -1
    }

    public fun isShutdown(): Boolean = isShutdown

    public fun shutdown() {
        isShutdown = true
        pendingCropPages.clear()
        decodeService?.shutdown()
        pages.forEach { it.recycle() }
        pages = emptyList()
        pageToRender = emptyList()
        state.close()
        ImageCache.clear()
    }

    /**
     * 兼容旧调用：viewSize/zoom/orientation 变化。
     * 只有 viewSize 或 orientation 变化才重新布局；zoom 只更新视口。
     */
    public fun updateViewSize(
        viewSize: IntSize,
        vZoom: Float,
        orientation: Int = this.orientation
    ) {
        val sizeChanged = this.viewSize != viewSize
        val orientationChanged = this.orientation != orientation
        val zoomChanged = this.vZoom != vZoom

        this.viewSize = viewSize
        this.vZoom = vZoom
        this.orientation = orientation
        viewport.viewSize = viewSize
        viewport.zoom = vZoom

        if (viewSize.width == 0 || viewSize.height == 0 || pages.isEmpty()) {
            updateTotals()
            return
        }

        if (sizeChanged || orientationChanged || !init) {
            relayout()
        } else if (zoomChanged) {
            viewport.setContentSize(docWidth, docHeight)
            viewport.offset = viewport.clampOffset(viewport.offset)
            viewOffset = viewport.offset
            updateTotals()
            updateVisibleNow(true)
        }
    }

    public fun invalidatePageSizes() {
        relayout()
    }

    private fun relayout() {
        if (viewSize.width == 0 || viewSize.height == 0 || pages.isEmpty()) {
            updateTotals()
            return
        }
        val anchor = captureAnchor()
        computeRects()
        if (anchor != null) {
            applyAnchor(anchor)
        } else {
            viewport.offset = viewport.clampOffset(viewport.offset)
        }
        viewOffset = viewport.offset
        updateTotals()
        init = true
        layoutVersion++
        onLayoutApplied?.invoke()
        updateVisibleNow(true)
        revision++
    }

    private fun captureAnchor(): Anchor? {
        if (!init || docWidth <= 0f || docHeight <= 0f || pages.isEmpty()) {
            return null
        }
        val rect = viewport.docRect
        var bestPage: Page? = null
        var bestArea = 0f
        for (page in pages) {
            val area = overlapArea(page.baseRect, rect)
            if (area > bestArea) {
                bestArea = area
                bestPage = page
            }
        }
        val page = bestPage ?: pages.first()
        if (page.baseRect.width <= 0f || page.baseRect.height <= 0f) {
            return null
        }
        val fx = ((rect.left - page.baseRect.left) / page.baseRect.width).coerceIn(0f, 1f)
        val fy = ((rect.top - page.baseRect.top) / page.baseRect.height).coerceIn(0f, 1f)
        val docPoint = Offset(
            page.baseRect.left + fx * page.baseRect.width,
            page.baseRect.top + fy * page.baseRect.height
        )
        return Anchor(page.index, fx, fy, viewport.toScreen(docPoint))
    }

    private fun applyAnchor(anchor: Anchor) {
        val page = pages.getOrNull(anchor.index) ?: return
        if (page.baseRect.width <= 0f || page.baseRect.height <= 0f) {
            return
        }
        val docX = page.baseRect.left + anchor.fx * page.baseRect.width
        val docY = page.baseRect.top + anchor.fy * page.baseRect.height
        viewport.offset = viewport.clampOffset(
            Offset(
                anchor.screen.x - docX * viewport.zoom,
                anchor.screen.y - docY * viewport.zoom
            )
        )
    }

    private fun overlapArea(a: Rect, b: Rect): Float {
        val left = max(a.left, b.left)
        val top = max(a.top, b.top)
        val right = min(a.right, b.right)
        val bottom = min(a.bottom, b.bottom)
        if (right <= left || bottom <= top) {
            return 0f
        }
        return (right - left) * (bottom - top)
    }

    private fun computeRects() {
        val viewWidth = viewSize.width.toFloat()
        val viewHeight = viewSize.height.toFloat()

        if (columnCount > 1) {
            val columnWidth = viewWidth / columnCount
            var currentY = 0f
            var i = 0
            while (i < pages.size) {
                var rowHeight = 0f
                for (col in 0 until columnCount) {
                    val pageIndex = i + col
                    if (pageIndex >= pages.size) break
                    val page = pages[pageIndex]
                    val pageHeight = columnWidth * page.geometry.height / page.geometry.width
                    page.updateLayout(
                        Rect(
                            col * columnWidth,
                            currentY,
                            (col + 1) * columnWidth,
                            currentY + pageHeight
                        )
                    )
                    if (pageHeight > rowHeight) {
                        rowHeight = pageHeight
                    }
                }
                currentY += rowHeight
                i += columnCount
            }
            docWidth = viewWidth
            docHeight = currentY
        } else if (orientation == Vertical) {
            var currentY = 0f
            for (page in pages) {
                val pageHeight = viewWidth * page.geometry.height / page.geometry.width
                page.updateLayout(Rect(0f, currentY, viewWidth, currentY + pageHeight))
                currentY += pageHeight
            }
            docWidth = viewWidth
            docHeight = currentY
        } else {
            var currentX = 0f
            for (page in pages) {
                val pageWidth = viewHeight * page.geometry.width / page.geometry.height
                page.updateLayout(Rect(currentX, 0f, currentX + pageWidth, viewHeight))
                currentX += pageWidth
            }
            docWidth = currentX
            docHeight = viewHeight
        }

        viewport.viewSize = viewSize
        viewport.zoom = vZoom
        viewport.setContentSize(docWidth, docHeight)
    }

    private fun updateTotals() {
        totalWidth = docWidth * vZoom
        totalHeight = docHeight * vZoom
    }

    /**
     * 兼容旧调用：更新视口偏移。
     */
    public fun updateOffset(newOffset: Offset) {
        if (!init) {
            viewport.offset = newOffset
            viewOffset = newOffset
            return
        }
        viewport.setContentSize(docWidth, docHeight)
        viewport.offset = viewport.clampOffset(newOffset)
        viewOffset = viewport.offset
        updateTotals()
        updateVisibleNow(true)
    }

    /**
     * 兼容旧调用：更新视口并刷新可见 tile。
     */
    public fun updateVisiblePages(
        offset: Offset,
        viewSize: IntSize,
        currentVZoom: Float = vZoom
    ) {
        if (!init) {
            updateViewSize(viewSize, currentVZoom, orientation)
            return
        }
        this.viewSize = viewSize
        this.vZoom = currentVZoom
        viewport.viewSize = viewSize
        viewport.zoom = currentVZoom
        viewport.setContentSize(docWidth, docHeight)
        viewport.offset = viewport.clampOffset(offset)
        viewOffset = viewport.offset
        updateTotals()
        updateVisibleNow(true)
    }

    internal fun updateViewport(zoom: Float, offset: Offset, request: Boolean) {
        this.vZoom = zoom
        viewport.viewSize = viewSize
        viewport.zoom = zoom
        viewport.setContentSize(docWidth, docHeight)
        viewport.offset = viewport.clampOffset(offset)
        viewOffset = viewport.offset
        updateTotals()
        updateVisibleNow(request)
        lastInteractionAt = System.currentTimeMillis()
    }

    private fun updateVisibleNow(request: Boolean) {
        if (viewSize.width == 0 || viewSize.height == 0 || pages.isEmpty() || !init) {
            pageToRender = emptyList()
            wantedPagesRef.set(emptySet())
            return
        }
        val visible = viewport.docRect
        scanPages(visible, scanScratch)
        val previous = pageToRender
        var changed = !samePages(scanScratch, previous)

        // 捏合过程中页集合没变：只靠图层变换，不做额外工作
        if (!changed && !request) {
            return
        }

        if (changed) {
            pageToRender = ArrayList(scanScratch)
            val included = scanScratch.toHashSet()
            previous.forEach {
                if (!included.contains(it)) {
                    it.clearVisibleTiles()
                }
            }
            val wanted = HashSet<Int>(scanScratch.size)
            for (page in scanScratch) {
                wanted.add(page.index)
            }
            wantedPagesRef.set(wanted)
        }

        for (page in scanScratch) {
            if (request) {
                if (page.updateVisibleTiles(visible, vZoom, true)) {
                    changed = true
                }
                requestCropProbe(page)
            }
            page.requestThumbnail()
        }
        if (changed) {
            revision++
        }
    }

    private fun samePages(scanned: List<Page>, current: List<Page>): Boolean {
        if (scanned.size != current.size) {
            return false
        }
        for (i in scanned.indices) {
            if (scanned[i].index != current[i].index) {
                return false
            }
        }
        return true
    }

    internal fun isPageWanted(pageIndex: Int): Boolean {
        return wantedPagesRef.get().contains(pageIndex)
    }

    private fun scanPages(rect: Rect, out: MutableList<Page>) {
        out.clear()
        if (pages.isEmpty()) {
            return
        }
        if (columnCount > 1) {
            for (page in pages) {
                val base = page.baseRect
                if (base.right > rect.left && base.left < rect.right &&
                    base.bottom > rect.top && base.top < rect.bottom
                ) {
                    out.add(page)
                }
            }
            return
        }
        val vertical = orientation == Vertical
        var low = 0
        var high = pages.size - 1
        var first = pages.size
        if (vertical) {
            while (low <= high) {
                val mid = (low + high) ushr 1
                if (pages[mid].baseRect.top >= rect.top) {
                    first = mid
                    high = mid - 1
                } else {
                    low = mid + 1
                }
            }
            if (first >= pages.size) {
                first = pages.size - 1
            }
            var i = first
            while (i > 0 && pages[i - 1].baseRect.bottom > rect.top) {
                i--
            }
            while (i < pages.size && pages[i].baseRect.top < rect.bottom) {
                val page = pages[i]
                val base = page.baseRect
                if (base.bottom > rect.top && base.right > rect.left && base.left < rect.right) {
                    out.add(page)
                }
                i++
            }
            return
        }

        while (low <= high) {
            val mid = (low + high) ushr 1
            if (pages[mid].baseRect.left >= rect.left) {
                first = mid
                high = mid - 1
            } else {
                low = mid + 1
            }
        }
        if (first >= pages.size) {
            first = pages.size - 1
        }
        var i = first
        while (i > 0 && pages[i - 1].baseRect.right > rect.left) {
            i--
        }
        while (i < pages.size && pages[i].baseRect.left < rect.right) {
            val page = pages[i]
            val base = page.baseRect
            if (base.right > rect.left && base.bottom > rect.top && base.top < rect.bottom) {
                out.add(page)
            }
            i++
        }
    }

    public fun firstVisiblePageIndex(): Int {
        return pageToRender.firstOrNull()?.index ?: 0
    }

    public fun offsetForDoc(doc: Offset): Offset {
        viewport.setContentSize(docWidth, docHeight)
        return viewport.clampOffset(
            Offset(-doc.x * viewport.zoom, -doc.y * viewport.zoom)
        )
    }

    public fun offsetForPage(pageIndex: Int, originalY: Float? = null): Offset {
        val page = pages.getOrNull(pageIndex) ?: return viewport.offset
        viewport.setContentSize(docWidth, docHeight)
        val vertical = orientation == Vertical || columnCount > 1
        val docX: Float
        val docY: Float
        if (vertical) {
            docX = if (columnCount > 1) page.baseRect.left else 0f
            docY = if (originalY != null) {
                page.geometry.originalToDocY(originalY, page.baseRect)
            } else {
                page.baseRect.top
            }
        } else {
            docX = page.baseRect.left
            docY = 0f
        }
        return viewport.clampOffset(
            Offset(-docX * viewport.zoom, -docY * viewport.zoom)
        )
    }

    public fun drawVisiblePages(
        drawScope: DrawScope,
        offset: Offset,
        vZoom: Float,
    ) {
        val list = pageToRender
        for (page in list) {
            page.drawTiles(drawScope)
        }
    }

    private fun requestCropProbe(page: Page) {
        if (!cropEnabled || page.aPage.cropBounds != null || page.cropProbeRequested) {
            return
        }
        val service = decodeService ?: return
        page.cropProbeRequested = true
        val (thumbWidth, thumbHeight) = DecoderAdapter.calculateThumbnailSize(
            page.aPage.width,
            page.aPage.height,
            200
        )
        val task = DecodeTask(
            type = TaskType.CROP,
            pageIndex = page.index,
            key = "crop-${page.index}-${page.geometry.id}",
            aPage = page.aPage,
            zoom = 1f,
            pageSliceBounds = Rect(0f, 0f, 1f, 1f),
            width = thumbWidth,
            height = thumbHeight,
            crop = true,
            callback = object : DecodeCallback {
                override fun onDecodeComplete(
                    bitmap: androidx.compose.ui.graphics.ImageBitmap?,
                    isThumb: Boolean,
                    error: Throwable?
                ) {
                }

                override fun shouldRender(pageNumber: Int, isFullPage: Boolean): Boolean {
                    return !isShutdown && isPageWanted(pageNumber)
                }

                override fun onFinish(pageNumber: Int) {
                    CoroutineScope(Dispatchers.Main).launch {
                        onPageGeometryDetected(page)
                    }
                }
            }
        )
        service.submitTask(task)
    }

    private fun onPageGeometryDetected(page: Page) {
        if (isShutdown) return
        val newGeometry = PageGeometry(
            page.aPage.width.toFloat(),
            page.aPage.height.toFloat(),
            page.aPage.cropBounds,
            cropEnabled
        )
        if (newGeometry == page.geometry) {
            return
        }
        pendingCropPages.add(page.index)
        if (cropRelayoutScheduled) {
            return
        }
        cropRelayoutScheduled = true
        CoroutineScope(Dispatchers.Main).launch {
            delay(50)
            cropRelayoutScheduled = false
            applyPendingCropResults()
        }
    }

    private fun applyPendingCropResults() {
        if (isShutdown) {
            pendingCropPages.clear()
            return
        }
        if (System.currentTimeMillis() - lastInteractionAt < 300L) {
            if (!cropRelayoutScheduled) {
                cropRelayoutScheduled = true
                CoroutineScope(Dispatchers.Main).launch {
                    delay(120)
                    cropRelayoutScheduled = false
                    applyPendingCropResults()
                }
            }
            return
        }
        var changed = false
        for (index in pendingCropPages) {
            val page = pages.getOrNull(index) ?: continue
            val geometry = PageGeometry(
                page.aPage.width.toFloat(),
                page.aPage.height.toFloat(),
                page.aPage.cropBounds,
                cropEnabled
            )
            if (geometry != page.geometry) {
                page.updateGeometry(geometry)
                changed = true
            }
        }
        pendingCropPages.clear()
        if (changed) {
            relayout()
        }
    }

    public fun drawOverlay(drawScope: DrawScope) {
        for (page in pageToRender) {
            val base = page.baseRect
            val visible = viewport.docRect
            if (base.right < visible.left || base.left > visible.right ||
                base.bottom < visible.top || base.top > visible.bottom
            ) {
                continue
            }
            drawLinks(drawScope, page)
            drawTextSelection(drawScope, page)
            drawSearchHighlight(drawScope, page)
            drawSpeakingIndicator(drawScope, page)
            drawAnnotations(drawScope, page)
            drawSeparator(drawScope, page)
        }
    }

    private fun toScreen(page: Page, x: Float, y: Float): Offset {
        return viewport.toScreen(page.geometry.originalToDoc(x, y, page.baseRect))
    }

    private fun drawLinks(drawScope: DrawScope, page: Page) {
        if (page.links.isEmpty()) return
        val geometry = page.geometry
        for (link in page.links) {
            val bbox = link.bbox ?: continue
            if (bbox.right <= geometry.left || bbox.left >= geometry.left + geometry.width ||
                bbox.bottom <= geometry.top || bbox.top >= geometry.top + geometry.height
            ) {
                continue
            }
            val rect = geometry.originalRectToDoc(bbox, page.baseRect)
            val topLeft = viewport.toScreen(Offset(rect.left, rect.top))
            val bottomRight = viewport.toScreen(Offset(rect.right, rect.bottom))
            val linkColor = if (link.linkType == Hyperlink.LINKTYPE_URL) {
                Color(0x66336EE5)
            } else {
                Color(0x40FFA500)
            }
            drawScope.drawRect(
                color = linkColor,
                topLeft = topLeft,
                size = GeomSize(bottomRight.x - topLeft.x, bottomRight.y - topLeft.y)
            )
        }
    }

    private fun drawTextSelection(drawScope: DrawScope, page: Page) {
        val selection = page.currentSelection ?: return
        val selector = textSelector ?: return
        val color = Color(0x6633B5E5)
        selection.quads.forEach { quad ->
            val screenQuad = selector.quadToScreenQuad(quad) { x, y ->
                toScreen(page, x, y)
            }
            val left = minOf(screenQuad.ul.x, screenQuad.ur.x, screenQuad.ll.x, screenQuad.lr.x)
            val top = minOf(screenQuad.ul.y, screenQuad.ur.y, screenQuad.ll.y, screenQuad.lr.y)
            val right = maxOf(screenQuad.ul.x, screenQuad.ur.x, screenQuad.ll.x, screenQuad.lr.x)
            val bottom = maxOf(screenQuad.ul.y, screenQuad.ur.y, screenQuad.ll.y, screenQuad.lr.y)
            drawScope.drawRect(
                color = color,
                topLeft = Offset(left, top),
                size = GeomSize(right - left, bottom - top)
            )
        }
    }

    private fun drawSearchHighlight(drawScope: DrawScope, page: Page) {
        val quads = searchHighlightQuads[page.index] ?: return
        val normalColor = Color(0x55FFFF00)
        quads.forEach { quad ->
            drawScreenQuad(drawScope, page, quad, normalColor)
        }
        if (currentSearchPageIndex == page.index && quads.isNotEmpty()) {
            drawScreenQuad(drawScope, page, quads[0], Color(0x88FFA500))
        }
    }

    private fun drawScreenQuad(drawScope: DrawScope, page: Page, quad: DocQuad, color: Color) {
        val ul = toScreen(page, quad.ul.x, quad.ul.y)
        val ur = toScreen(page, quad.ur.x, quad.ur.y)
        val ll = toScreen(page, quad.ll.x, quad.ll.y)
        val lr = toScreen(page, quad.lr.x, quad.lr.y)
        val left = minOf(ul.x, ur.x, ll.x, lr.x)
        val top = minOf(ul.y, ur.y, ll.y, lr.y)
        val right = maxOf(ul.x, ur.x, ll.x, lr.x)
        val bottom = maxOf(ul.y, ur.y, ll.y, lr.y)
        drawScope.drawRect(
            color = color,
            topLeft = Offset(left, top),
            size = GeomSize(right - left, bottom - top)
        )
    }

    private fun drawSpeakingIndicator(drawScope: DrawScope, page: Page) {
        val speakingPage = speakingPageIndex
        if (speakingPage != null && speakingPage == page.index) {
            val topLeft = viewport.toScreen(Offset(page.baseRect.left, page.baseRect.top))
            val bottomRight = viewport.toScreen(Offset(page.baseRect.right, page.baseRect.bottom))
            drawScope.drawRect(
                color = Color.Red,
                topLeft = topLeft,
                size = GeomSize(bottomRight.x - topLeft.x, bottomRight.y - topLeft.y),
                style = Stroke(width = 8f)
            )
        }
    }

    private fun drawAnnotations(drawScope: DrawScope, page: Page) {
        annotationManager.annotations[page.index]?.forEach { anno ->
            drawAnnotationPath(drawScope, page, anno)
        }
        activeDrawingAnno?.let { (index, anno) ->
            if (index == page.index) {
                drawAnnotationPath(drawScope, page, anno)
            }
        }
    }

    private fun drawAnnotationPath(drawScope: DrawScope, page: Page, anno: AnnotationPath) {
        if (anno.points.size < 2) return
        val path = Path()
        anno.points.forEachIndexed { i, rel ->
            val doc = Offset(
                page.baseRect.left + rel.x * page.baseRect.width,
                page.baseRect.top + rel.y * page.baseRect.height
            )
            val screen = viewport.toScreen(doc)
            if (i == 0) path.moveTo(screen.x, screen.y) else path.lineTo(screen.x, screen.y)
        }
        drawScope.drawPath(path, anno.config.color, style = Stroke(width = anno.config.strokeWidth))
    }

    private fun drawSeparator(drawScope: DrawScope, page: Page) {
        val color = Color(0xFF999999)
        if (orientation == Vertical || columnCount > 1) {
            val topLeft = viewport.toScreen(
                Offset(page.baseRect.left, page.baseRect.bottom - 2f / viewport.zoom)
            )
            val right = viewport.toScreen(Offset(page.baseRect.left + page.baseRect.width / 4, page.baseRect.bottom))
            drawScope.drawRect(
                color = color,
                topLeft = topLeft,
                size = GeomSize(right.x - topLeft.x, 2f)
            )
        } else {
            val topLeft = viewport.toScreen(
                Offset(page.baseRect.right - 2f / viewport.zoom, page.baseRect.top)
            )
            val bottom = viewport.toScreen(Offset(page.baseRect.right, page.baseRect.top + page.baseRect.height / 4))
            drawScope.drawRect(
                color = color,
                topLeft = topLeft,
                size = GeomSize(2f, bottom.y - topLeft.y)
            )
        }
    }

    public fun handleClick(docX: Float, docY: Float): Boolean {
        val point = Offset(docX, docY)
        for (page in pageToRender) {
            if (!page.baseRect.contains(point)) {
                continue
            }
            page.ensureLinks()
            val link = page.findLinkAtPoint(docX, docY)
            return if (link != null) handleLink(link) else false
        }
        return false
    }

    private fun handleLink(link: Hyperlink): Boolean {
        return when (link.linkType) {
            Hyperlink.LINKTYPE_PAGE -> {
                if (link.page >= 0 && link.page < pages.size) {
                    onPageLinkClick?.invoke(link.page)
                    true
                } else {
                    false
                }
            }

            Hyperlink.LINKTYPE_URL -> {
                if (!link.url.isNullOrEmpty()) {
                    onUrlLinkClick?.invoke(link.url!!)
                    true
                } else {
                    false
                }
            }

            else -> false
        }
    }

    public companion object {
        public fun isVisible(viewSize: IntSize, offset: Offset, bounds: Rect, page: Int): Boolean {
            val visibleRect = Rect(
                left = -offset.x,
                top = -offset.y,
                right = viewSize.width - offset.x,
                bottom = viewSize.height - offset.y
            )
            return bounds.overlaps(visibleRect)
        }
    }
}
