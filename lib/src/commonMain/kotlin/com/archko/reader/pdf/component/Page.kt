package com.archko.reader.pdf.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.cache.BitmapState
import com.archko.reader.pdf.cache.ImageCache
import com.archko.reader.pdf.entity.APage
import com.archko.reader.pdf.entity.Hyperlink
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.round

/**
 * 页面：基础布局矩形（文档空间，不含 zoom）+ 内容几何 + 页内 LOD tile。
 * 页缩略图始终作为底层，当前档 tile 覆盖其上。
 */
public class Page internal constructor(
    private val pageViewState: PageViewState,
    internal var aPage: APage,
    internal var geometry: PageGeometry,
    initialRect: Rect
) {
    public var baseRect: Rect = initialRect
        internal set

    public val index: Int get() = aPage.index
    public val width: Float get() = baseRect.width
    public val height: Float get() = baseRect.height
    public val bounds: Rect get() = baseRect
    public val xOffset: Float get() = baseRect.left
    public val yOffset: Float get() = baseRect.top

    private var currentLevel: Int = Int.MIN_VALUE
    private val tileNodes = ArrayList<PageNode>()
    private var visibleTiles: List<PageNode> = emptyList()
    private var fallbackTiles: List<PageNode> = emptyList()

    // 上次计算出的可见 tile 范围，范围不变时不做任何重建
    private var lastColStart: Int = -1
    private var lastColEnd: Int = -1
    private var lastRowStart: Int = -1
    private var lastRowEnd: Int = -1

    private var thumbBitmap: BitmapState? by mutableStateOf(null)
    private val thumbLoading: AtomicBoolean = AtomicBoolean(false)
    private var thumbKey: String? = null

    internal var cropProbeRequested: Boolean = false

    // 页面链接
    public var links: List<Hyperlink> by mutableStateOf(emptyList())
    private var linksLoaded = false

    // 文本选择相关
    private var structuredText: StructuredText? = null
    private var textLoaded = false
    public var currentSelection: TextSelection? by mutableStateOf(null)
    private var isSelecting = false
    private var selectionStartPoint: Offset? = null

    internal fun updateLayout(rect: Rect) {
        baseRect = rect
        resetVisibleRange()
    }

    private fun resetVisibleRange() {
        lastColStart = -1
        lastColEnd = -1
        lastRowStart = -1
        lastRowEnd = -1
    }

    internal fun updateGeometry(newGeometry: PageGeometry) {
        if (newGeometry == geometry) {
            return
        }
        releaseTileNodes()
        releaseFallbackTiles()
        releaseThumbnail()
        geometry = newGeometry
        currentLevel = Int.MIN_VALUE
        resetVisibleRange()
        currentSelection = null
    }

    private fun colsAt(level: Int): Int {
        val scale = textureScale(level)
        val target = if (geometry.width > geometry.height * 3f) {
            TILE_LONG_CONTENT_PX
        } else {
            TILE_CONTENT_PX
        }
        return ceil(geometry.width * scale / target).toInt().coerceAtLeast(1)
    }

    private fun rowsAt(level: Int): Int {
        val scale = textureScale(level)
        val target = if (geometry.height > geometry.width * 3f) {
            TILE_LONG_CONTENT_PX
        } else {
            TILE_CONTENT_PX
        }
        return ceil(geometry.height * scale / target).toInt().coerceAtLeast(1)
    }

    private fun textureScale(level: Int): Float {
        return 2.0.pow(level.toDouble() / LEVEL_STEPS_PER_OCTAVE).toFloat()
    }

    internal fun levelFor(zoom: Float): Int {
        val vertical = pageViewState.orientation == Vertical || pageViewState.columnCount > 1
        val baseMain = if (vertical) baseRect.width else baseRect.height
        val contentMain = if (vertical) geometry.width else geometry.height
        if (baseMain <= 0f || contentMain <= 0f || zoom <= 0f) {
            return 0
        }
        val baseScale = baseMain / contentMain
        val needed = baseScale * zoom
        if (needed <= 0.0001f) {
            return MIN_TILE_LEVEL
        }
        // 细档位：保证 textureScale 只略大于 needed，tile 在屏幕上接近 TILE_CONTENT_PX，
        // 避免 2 的幂档位产生一堆 512px 的小 tile
        val level = ceil(log2(needed.toDouble()) * LEVEL_STEPS_PER_OCTAVE).toInt()
        return level.coerceIn(MIN_TILE_LEVEL, MAX_TILE_LEVEL)
    }

    internal fun updateVisibleTiles(visibleDoc: Rect, zoom: Float, request: Boolean): Boolean {
        if (baseRect.width <= 0f || baseRect.height <= 0f ||
            visibleDoc.left >= baseRect.right || visibleDoc.right <= baseRect.left ||
            visibleDoc.top >= baseRect.bottom || visibleDoc.bottom <= baseRect.top
        ) {
            val hadTiles = visibleTiles.isNotEmpty()
            clearVisibleTiles()
            return hadTiles
        }
        val previousVisible = visibleTiles

        // 档位只在真正请求解码时切换，捏合过程中沿用当前档位，避免逐帧重建
        val level = if (request || currentLevel == Int.MIN_VALUE) {
            levelFor(zoom)
        } else {
            currentLevel
        }
        if (level != currentLevel) {
            releaseFallbackTiles()
            val oldVisible = visibleTiles
            fallbackTiles = oldVisible
            for (node in tileNodes) {
                if (!oldVisible.contains(node)) {
                    node.release()
                }
            }
            tileNodes.clear()
            visibleTiles = emptyList()
            currentLevel = level
            resetVisibleRange()
        }

        val cols = colsAt(level)
        val rows = rowsAt(level)
        val localLeft = ((visibleDoc.left - baseRect.left) / baseRect.width).coerceIn(0f, 1f)
        val localRight = ((visibleDoc.right - baseRect.left) / baseRect.width).coerceIn(0f, 1f)
        val localTop = ((visibleDoc.top - baseRect.top) / baseRect.height).coerceIn(0f, 1f)
        val localBottom = ((visibleDoc.bottom - baseRect.top) / baseRect.height).coerceIn(0f, 1f)

        val colStart = floor(localLeft * cols).toInt().coerceIn(0, cols - 1)
        val colEnd = (ceil(localRight * cols).toInt() - 1).coerceIn(0, cols - 1)
        val rowStart = floor(localTop * rows).toInt().coerceIn(0, rows - 1)
        val rowEnd = (ceil(localBottom * rows).toInt() - 1).coerceIn(0, rows - 1)

        // 可见范围没变（平移/缩放的绝大多数帧）：不重建任何东西
        if (colStart == lastColStart && colEnd == lastColEnd &&
            rowStart == lastRowStart && rowEnd == lastRowEnd
        ) {
            if (request) {
                requestPendingDecodes(visibleTiles)
            }
            return false
        }
        lastColStart = colStart
        lastColEnd = colEnd
        lastRowStart = rowStart
        lastRowEnd = rowEnd

        val scale = textureScale(level)
        val nodes = ArrayList<PageNode>((colEnd - colStart + 1) * (rowEnd - rowStart + 1))

        for (row in rowStart..rowEnd) {
            var col = colStart
            while (col <= colEnd) {
                val key = tileKey(level, row, col)
                val existing = findNode(key)
                val node = existing ?: createNode(
                    key,
                    level,
                    row,
                    col,
                    Rect(
                        col.toFloat() / cols,
                        row.toFloat() / rows,
                        (col + 1).toFloat() / cols,
                        (row + 1).toFloat() / rows
                    ),
                    scale
                )?.also { tileNodes.add(it) }
                if (node != null) {
                    nodes.add(node)
                }
                col++
            }
        }

        // 未解码的离屏 tile 立刻取消；已解码的保留少量作为回滚缓冲。
        var retainedOffscreen = tileNodes.size - nodes.size
        var last = tileNodes.size - 1
        while (last >= 0) {
            val node = tileNodes[last]
            val inRange = node.level == level &&
                    node.row >= rowStart && node.row <= rowEnd &&
                    node.col >= colStart && node.col <= colEnd
            if (!inRange && (node.bitmap == null || retainedOffscreen > MAX_RETAINED_TILES)) {
                node.release()
                tileNodes.removeAt(last)
                retainedOffscreen--
            }
            last--
        }

        visibleTiles = nodes

        if (request) {
            requestPendingDecodes(nodes)
        }
        if (fallbackTiles.isNotEmpty() && nodes.isNotEmpty() &&
            nodes.all { it.bitmap != null }
        ) {
            releaseFallbackTiles()
        }
        return previousVisible != nodes
    }

    private fun requestPendingDecodes(nodes: List<PageNode>) {
        for (node in nodes) {
            val current = node.bitmap
            if (current != null && current.isRecycled()) {
                node.bitmap = null
                node.queued.set(false)
            }
            if (node.bitmap == null && node.queued.get() &&
                System.currentTimeMillis() - node.requestAt > 3000L
            ) {
                node.queued.set(false)
            }
            if (node.bitmap == null && !node.queued.get()) {
                requestDecode(node)
            }
        }
    }

    private fun findNode(key: Long): PageNode? {
        for (i in tileNodes.indices) {
            val node = tileNodes[i]
            if (node.key == key) {
                return node
            }
        }
        return null
    }

    private fun createNode(
        key: Long,
        level: Int,
        row: Int,
        col: Int,
        fraction: Rect,
        scale: Float
    ): PageNode? {
        // 内容像素边界取整：相邻 tile 在公共边上算出的整数完全一致，
        // 解码区域与绘制区域因此共用同一条边，接缝处不会重复绘制一列/一行像素。
        // 旧实现用 ceil(宽) + floor/ceil(目标矩形) 会让相邻 tile 相互覆盖，
        // 放大后这段重复内容被放大成可见的“文字重叠”。
        val cx = round(geometry.left + fraction.left * geometry.width)
        val cy = round(geometry.top + fraction.top * geometry.height)
        val cx2 = round(geometry.left + fraction.right * geometry.width)
        val cy2 = round(geometry.top + fraction.bottom * geometry.height)
        if (cx2 <= cx || cy2 <= cy) {
            return null
        }
        // 纹理像素边界同样取整，保证位图覆盖的纹理区间正好是 [tx, tx2)，
        // 解码器不再需要 floor/ceil 向两侧“补一像素”。
        val tx = round(cx * scale)
        val ty = round(cy * scale)
        val tx2 = round(cx2 * scale)
        val ty2 = round(cy2 * scale)
        val outWidth = (tx2 - tx).toInt().coerceAtLeast(1)
        val outHeight = (ty2 - ty).toInt().coerceAtLeast(1)
        if (outWidth > 4096 || outHeight > 4096) {
            return null
        }
        // 目标矩形改用取整后的内容边界反算归一化比例，
        // 绘制时再统一 round，相邻 tile 的边界值相同 -> 不重叠也不留缝。
        val snappedFraction = Rect(
            (cx - geometry.left) / geometry.width,
            (cy - geometry.top) / geometry.height,
            (cx2 - geometry.left) / geometry.width,
            (cy2 - geometry.top) / geometry.height
        )
        return PageNode(
            key = key,
            cacheKey = "tile/$index/${geometry.id}/$level/$row/$col",
            level = level,
            row = row,
            col = col,
            fraction = snappedFraction,
            srcRect = Rect(tx, ty, tx2, ty2),
            outWidth = outWidth,
            outHeight = outHeight,
            textureScale = scale,
            crop = pageViewState.isCropEnabled()
        )
    }

    internal fun requestThumbnail() {
        if (thumbBitmap?.isRecycled() == true) {
            releaseThumbnail()
        }
        if (thumbBitmap != null || thumbLoading.get()) {
            return
        }
        val service = pageViewState.decodeService ?: return
        val (thumbWidth, thumbHeight) = DecoderAdapter.calculateThumbnailSize(
            aPage.width,
            aPage.height
        )
        val geometryId = geometry.id
        val key = "thumb-${index}-${thumbWidth}x${thumbHeight}-$geometryId"
        val cached = ImageCache.acquirePage(key)
        if (cached != null) {
            thumbBitmap = cached
            thumbKey = key
            return
        }
        thumbKey = key
        thumbLoading.set(true)
        val task = DecodeTask(
            type = TaskType.PAGE,
            pageIndex = index,
            key = key,
            aPage = aPage,
            zoom = 1f,
            pageSliceBounds = Rect(0f, 0f, 1f, 1f),
            width = thumbWidth,
            height = thumbHeight,
            crop = pageViewState.isCropEnabled() && aPage.cropBounds != null,
            callback = object : DecodeCallback {
                override fun onDecodeComplete(
                    bitmap: androidx.compose.ui.graphics.ImageBitmap?,
                    isThumb: Boolean,
                    error: Throwable?
                ) {
                    if (bitmap == null || pageViewState.isShutdown()) {
                        thumbLoading.set(false)
                        return
                    }
                    val newState = ImageCache.putPage(key, bitmap)
                    val acquired = newState.acquire()
                    pageViewState.mainScope.launch {
                        thumbLoading.set(false)
                        if (acquired &&
                            !pageViewState.isShutdown() &&
                            thumbKey == key &&
                            geometry.id == geometryId &&
                            thumbBitmap == null
                        ) {
                            thumbBitmap = newState
                        } else if (acquired) {
                            ImageCache.releasePage(newState)
                        }
                        // 缩略图解码时已顺带解析链接，这里及时补上
                        loadLinks()
                    }
                }

                override fun shouldRender(pageNumber: Int, isFullPage: Boolean): Boolean {
                    return thumbKey == key &&
                            !pageViewState.isShutdown() &&
                            pageViewState.isPageWanted(pageNumber)
                }

                override fun onFinish(pageNumber: Int) {
                    thumbLoading.set(false)
                }
            }
        )
        service.submitTask(task)
    }

    internal fun clearVisibleTiles() {
        releaseTileNodes()
        releaseFallbackTiles()
        releaseThumbnail()
        cropProbeRequested = false
        currentLevel = Int.MIN_VALUE
        resetVisibleRange()
    }

    private fun releaseFallbackTiles() {
        fallbackTiles.forEach { it.release() }
        fallbackTiles = emptyList()
    }

    private fun releaseTileNodes() {
        for (node in tileNodes) {
            node.release()
        }
        tileNodes.clear()
        visibleTiles = emptyList()
    }

    private fun releaseThumbnail() {
        thumbBitmap?.let { ImageCache.releasePage(it) }
        thumbBitmap = null
        thumbLoading.set(false)
        thumbKey = null
    }

    private fun requestDecode(node: PageNode) {
        if (pageViewState.isShutdown()) {
            return
        }
        val service = pageViewState.decodeService ?: return
        node.queued.set(true)
        node.requestAt = System.currentTimeMillis()
        val cached = ImageCache.acquireNode(node.cacheKey)
        if (cached != null) {
            if (!cached.isRecycled()) {
                node.bitmap = cached
            } else {
                ImageCache.releaseNode(cached)
            }
            node.queued.set(false)
            return
        }
        val task = DecodeTask(
            type = TaskType.NODE,
            pageIndex = index,
            key = node.cacheKey,
            aPage = aPage,
            zoom = node.textureScale,
            pageSliceBounds = node.srcRect,
            width = node.outWidth,
            height = node.outHeight,
            crop = node.crop,
            callback = object : DecodeCallback {
                override fun onDecodeComplete(
                    bitmap: androidx.compose.ui.graphics.ImageBitmap?,
                    isThumb: Boolean,
                    error: Throwable?
                ) {
                    if (bitmap == null || pageViewState.isShutdown()) {
                        node.queued.set(false)
                        return
                    }
                    val newState = ImageCache.putNode(node.cacheKey, bitmap)
                    val acquired = newState.acquire()
                    pageViewState.mainScope.launch {
                        if (acquired && !node.removed.get() && !pageViewState.isShutdown()) {
                            node.bitmap?.let { ImageCache.releaseNode(it) }
                            node.bitmap = newState
                        } else if (acquired) {
                            ImageCache.releaseNode(newState)
                        }
                        node.queued.set(false)
                        if (fallbackTiles.isNotEmpty() && visibleTiles.isNotEmpty() &&
                            visibleTiles.all { it.bitmap != null }
                        ) {
                            releaseFallbackTiles()
                        }
                    }
                }

                override fun shouldRender(pageNumber: Int, isFullPage: Boolean): Boolean {
                    return !node.removed.get() && !pageViewState.isShutdown() && pageViewState.isPageWanted(pageNumber)
                }

                override fun onFinish(pageNumber: Int) {
                    node.queued.set(false)
                }
            }
        )
        service.submitTask(task)
    }

    internal fun drawTiles(drawScope: DrawScope) {
        if (baseRect.width <= 0f || baseRect.height <= 0f) {
            return
        }
        thumbBitmap?.let { state ->
            if (!state.isRecycled()) {
                drawBitmap(drawScope, state, baseRect)
            }
        }
        for (node in fallbackTiles) {
            val state = node.bitmap
            if (state != null && !state.isRecycled()) {
                drawBitmap(drawScope, state, node.docRect(baseRect))
            }
        }
        for (node in visibleTiles) {
            val state = node.bitmap
            if (state != null && !state.isRecycled()) {
                drawBitmap(drawScope, state, node.docRect(baseRect))
            }
        }
    }

    private fun drawBitmap(drawScope: DrawScope, state: BitmapState, rect: Rect) {
        // 统一 round 而不是外扩的 floor/ceil：相邻 tile 共享同一条边，
        // 否则每个 tile 都向外多占 1 个文档像素，缩放后会互相覆盖。
        val left = round(rect.left).toInt()
        val top = round(rect.top).toInt()
        val right = round(rect.right).toInt()
        val bottom = round(rect.bottom).toInt()
        drawScope.drawImage(
            image = state.bitmap,
            dstOffset = IntOffset(left, top),
            dstSize = IntSize(
                (right - left).coerceAtLeast(1),
                (bottom - top).coerceAtLeast(1)
            )
        )
    }

    /**
     * 加载页面文本结构
     */
    public fun loadText() {
        if (textLoaded || pageViewState.textSelector == null) {
            return
        }
        structuredText = pageViewState.textSelector.getStructuredText(index)
        textLoaded = true
    }

    public fun clearTextSelection() {
        currentSelection = null
        isSelecting = false
        selectionStartPoint = null
    }

    public fun loadLinks() {
        if (linksLoaded) return
        val loaded = pageViewState.state.getPageLinks(index)
        if (loaded.isNotEmpty()) {
            links = loaded
            linksLoaded = true
        }
    }

    internal fun ensureLinks() {
        loadLinks()
    }

    public fun findLinkAtPoint(docX: Float, docY: Float): Hyperlink? {
        if (links.isEmpty() || !baseRect.contains(Offset(docX, docY))) {
            return null
        }
        val point = geometry.docToOriginal(docX, docY, baseRect)
        return Hyperlink.findLinkAtPoint(links, point.x, point.y)
    }

    /**
     * 开始文本选择，坐标是文档空间
     */
    public fun startTextSelection(docX: Float, docY: Float): Boolean {
        if (structuredText == null) {
            loadText()
        }
        val original = geometry.docToOriginal(docX, docY, baseRect)
        val startPoint = PagePoint(original.x, original.y)
        if (structuredText == null) {
            return false
        }
        isSelecting = true
        selectionStartPoint = Offset(docX, docY)
        currentSelection = TextSelection(
            startPoint = startPoint,
            endPoint = startPoint,
            text = "",
            quads = emptyArray()
        )
        return true
    }

    public fun updateTextSelection(docX: Float, docY: Float) {
        if (!isSelecting || selectionStartPoint == null) return
        val structText = structuredText ?: return
        val startPoint = currentSelection?.startPoint ?: return
        val original = geometry.docToOriginal(docX, docY, baseRect)
        val endPoint = PagePoint(original.x, original.y)
        if (startPoint.x != endPoint.x || startPoint.y != endPoint.y) {
            val quads = structText.highlight(startPoint, endPoint)
            val selectedText = structText.selectText(index, startPoint, endPoint)
            currentSelection = TextSelection(
                startPoint = startPoint,
                endPoint = endPoint,
                text = selectedText,
                quads = quads
            )
        }
    }

    public fun endTextSelection(): TextSelection? {
        isSelecting = false
        selectionStartPoint = null
        val selection = currentSelection
        return if (selection != null && selection.text.isNotBlank() && selection.quads.isNotEmpty()) {
            selection
        } else {
            null
        }
    }

    internal fun recycle() {
        clearVisibleTiles()
        clearTextSelection()
        links = emptyList()
        linksLoaded = false
        structuredText = null
        textLoaded = false
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        return index == (other as Page).index
    }

    override fun hashCode(): Int = index

    override fun toString(): String {
        return "Page(page=$index, base=$baseRect, geometry=$geometry)"
    }

    private companion object {
        // 已解码的离屏 tile 由 ImageCache 统一缓存，页内只保留极少量作回滚缓冲，
        // 否则每页最多常驻 16 张 1024x1024 位图，放大后内存/纹理压力会非常大。
        const val MAX_RETAINED_TILES = 4
    }
}
