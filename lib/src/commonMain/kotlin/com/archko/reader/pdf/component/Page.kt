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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.pow

/**
 * 页面：基础布局矩形（文档空间，不含 zoom）+ 内容几何 + 页内 LOD tile。
 * 页面内容永远以 baseRect 映射到文档空间；tile 身份是页内局部的。
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

    private var baseCols: Int = 1
    private var baseRows: Int = 1
    private var currentLevel: Int = Int.MIN_VALUE
    private val tileNodes = HashMap<TileId, PageNode>()
    private var visibleTiles: List<PageNode> = emptyList()
    private var fallbackTiles: List<PageNode> = emptyList()
    private val coarseNodes = HashMap<TileId, PageNode>()
    private var coarseLevel: Int = Int.MIN_VALUE

    // 页面链接
    public var links: List<Hyperlink> = emptyList()
    private var linksLoaded = false

    // 文本选择相关
    private var structuredText: StructuredText? = null
    private var textLoaded = false
    public var currentSelection: TextSelection? by mutableStateOf(null)
    private var isSelecting = false
    private var selectionStartPoint: Offset? = null

    internal var cropProbeRequested: Boolean = false

    init {
        refreshGrid()
    }

    internal fun updateLayout(rect: Rect) {
        baseRect = rect
    }

    internal fun updateGeometry(newGeometry: PageGeometry) {
        if (newGeometry == geometry) {
            return
        }
        clearVisibleTiles()
        geometry = newGeometry
        currentLevel = Int.MIN_VALUE
        refreshGrid()
        currentSelection = null
    }

    private fun refreshGrid() {
        baseCols = max(1, ceil(geometry.width / TILE_CONTENT_PX).toInt())
        baseRows = max(1, ceil(geometry.height / TILE_CONTENT_PX).toInt())
    }

    private fun colsAt(level: Int): Int {
        return if (level >= 0) {
            max(1, baseCols shl level)
        } else {
            max(1, baseCols shr (-level))
        }
    }

    private fun rowsAt(level: Int): Int {
        return if (level >= 0) {
            max(1, baseRows shl level)
        } else {
            max(1, baseRows shr (-level))
        }
    }

    private fun textureScale(level: Int): Float {
        return 2.0.pow(level.toDouble()).toFloat()
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
        val level = ceil(log2(needed.toDouble())).toInt()
        return level.coerceIn(MIN_TILE_LEVEL, MAX_TILE_LEVEL)
    }

    internal fun updateVisibleTiles(visibleDoc: Rect, zoom: Float, request: Boolean) {
        if (baseRect.width <= 0f || baseRect.height <= 0f ||
            visibleDoc.left >= baseRect.right || visibleDoc.right <= baseRect.left ||
            visibleDoc.top >= baseRect.bottom || visibleDoc.bottom <= baseRect.top
        ) {
            clearVisibleTiles()
            return
        }

        val level = levelFor(zoom)
        if (level != currentLevel) {
            releaseFallbackTiles()
            fallbackTiles = tileNodes.values.toList()
            tileNodes.clear()
            visibleTiles = emptyList()
            currentLevel = level
        }

        val cols = colsAt(level)
        val rows = rowsAt(level)
        val localLeft = ((visibleDoc.left - baseRect.left) / baseRect.width).coerceIn(0f, 1f)
        val localRight = ((visibleDoc.right - baseRect.left) / baseRect.width).coerceIn(0f, 1f)
        val localTop = ((visibleDoc.top - baseRect.top) / baseRect.height).coerceIn(0f, 1f)
        val localBottom = ((visibleDoc.bottom - baseRect.top) / baseRect.height).coerceIn(0f, 1f)

        val colStart = floor(localLeft * cols).toInt().coerceIn(0, cols - 1)
        val colEnd = ceil(localRight * cols).toInt().coerceIn(0, cols - 1)
        val rowStart = floor(localTop * rows).toInt().coerceIn(0, rows - 1)
        val rowEnd = ceil(localBottom * rows).toInt().coerceIn(0, rows - 1)

        val scale = textureScale(level)
        val nodes = ArrayList<PageNode>((colEnd - colStart + 1) * (rowEnd - rowStart + 1))
        val ids = HashSet<TileId>()

        for (row in rowStart..rowEnd) {
            for (col in colStart..colEnd) {
                val id = TileId(index, geometry.id, level, row, col)
                ids.add(id)
                val node = tileNodes[id] ?: createNode(
                    id,
                    Rect(
                        col.toFloat() / cols,
                        row.toFloat() / rows,
                        (col + 1).toFloat() / cols,
                        (row + 1).toFloat() / rows
                    ),
                    scale
                )?.also { tileNodes[id] = it } ?: continue
                nodes.add(node)
            }
        }

        val iterator = tileNodes.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!ids.contains(entry.key)) {
                entry.value.release()
                iterator.remove()
            }
        }

        visibleTiles = nodes

        if (request) {
            requestCoarseTiles(localLeft, localRight, localTop, localBottom, level)
            for (node in nodes) {
                if (node.bitmap == null && !node.queued) {
                    requestDecode(node)
                }
            }
        }
    }

    private fun createNode(id: TileId, fraction: Rect, scale: Float): PageNode? {
        val ox = geometry.left + fraction.left * geometry.width
        val oy = geometry.top + fraction.top * geometry.height
        val ox2 = geometry.left + fraction.right * geometry.width
        val oy2 = geometry.top + fraction.bottom * geometry.height
        val srcRect = Rect(ox * scale, oy * scale, ox2 * scale, oy2 * scale)
        val outWidth = ceil((ox2 - ox) * scale).toInt().coerceAtLeast(1)
        val outHeight = ceil((oy2 - oy) * scale).toInt().coerceAtLeast(1)
        if (outWidth > 4096 || outHeight > 4096) {
            return null
        }
        return PageNode(
            page = this,
            id = id,
            fraction = fraction,
            srcRect = srcRect,
            outWidth = outWidth,
            outHeight = outHeight,
            textureScale = scale,
            crop = pageViewState.isCropEnabled()
        )
    }

    private fun requestCoarseTiles(
        localLeft: Float,
        localRight: Float,
        localTop: Float,
        localBottom: Float,
        level: Int
    ) {
        val coarse = (level - 2).coerceAtLeast(MIN_TILE_LEVEL)
        if (coarse >= level) {
            releaseCoarseTiles()
            return
        }
        if (coarse != coarseLevel) {
            releaseCoarseTiles()
            coarseLevel = coarse
        }
        val cols = colsAt(coarse)
        val rows = rowsAt(coarse)
        val colStart = floor(localLeft * cols).toInt().coerceIn(0, cols - 1)
        val colEnd = ceil(localRight * cols).toInt().coerceIn(0, cols - 1)
        val rowStart = floor(localTop * rows).toInt().coerceIn(0, rows - 1)
        val rowEnd = ceil(localBottom * rows).toInt().coerceIn(0, rows - 1)
        val scale = textureScale(coarse)
        val ids = HashSet<TileId>()
        for (row in rowStart..rowEnd) {
            for (col in colStart..colEnd) {
                val id = TileId(index, geometry.id, coarse, row, col)
                ids.add(id)
                val node = coarseNodes[id] ?: createNode(
                    id,
                    Rect(
                        col.toFloat() / cols,
                        row.toFloat() / rows,
                        (col + 1).toFloat() / cols,
                        (row + 1).toFloat() / rows
                    ),
                    scale
                )?.also { coarseNodes[id] = it } ?: continue
                if (node.bitmap == null && !node.queued) {
                    requestDecode(node)
                }
            }
        }
        val iterator = coarseNodes.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!ids.contains(entry.key)) {
                entry.value.release()
                iterator.remove()
            }
        }
    }

    private fun releaseCoarseTiles() {
        coarseNodes.values.forEach { it.release() }
        coarseNodes.clear()
        coarseLevel = Int.MIN_VALUE
    }

    internal fun clearVisibleTiles() {
        releaseAllTiles()
        visibleTiles = emptyList()
        currentLevel = Int.MIN_VALUE
    }

    private fun releaseAllTiles() {
        tileNodes.values.forEach { it.release() }
        tileNodes.clear()
        visibleTiles = emptyList()
        releaseFallbackTiles()
        releaseCoarseTiles()
    }

    private fun releaseFallbackTiles() {
        fallbackTiles.forEach { it.release() }
        fallbackTiles = emptyList()
    }

    private fun requestDecode(node: PageNode) {
        if (pageViewState.isShutdown()) {
            return
        }
        val service = pageViewState.decodeService ?: return
        node.queued = true
        val cached = ImageCache.acquireNode(node.id.key)
        if (cached != null) {
            if (!cached.isRecycled()) {
                node.bitmap = cached
            } else {
                ImageCache.releaseNode(cached)
            }
            node.queued = false
            return
        }
        val task = DecodeTask(
            type = TaskType.NODE,
            pageIndex = index,
            key = node.id.key,
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
                        node.queued = false
                        return
                    }
                    val newState = ImageCache.putNode(node.id.key, bitmap)
                    CoroutineScope(Dispatchers.Main).launch {
                        if (!node.removed && !pageViewState.isShutdown()) {
                            node.bitmap?.let { ImageCache.releaseNode(it) }
                            node.bitmap = newState
                        } else {
                            ImageCache.releaseNode(newState)
                        }
                        node.queued = false
                    }
                }

                override fun shouldRender(pageNumber: Int, isFullPage: Boolean): Boolean {
                    return !pageViewState.isShutdown()
                }

                override fun onFinish(pageNumber: Int) {
                }
            }
        )
        service.submitTask(task)
    }

    internal fun drawTiles(drawScope: DrawScope) {
        if (baseRect.width <= 0f || baseRect.height <= 0f) {
            return
        }
        val drawnAncestors = HashSet<String>()
        for (node in coarseNodes.values) {
            val state = node.bitmap
            if (state != null && !state.isRecycled()) {
                drawBitmap(drawScope, state, node.docRect(baseRect))
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
            } else {
                drawFallback(drawScope, node, drawnAncestors)
            }
        }
    }

    private fun drawFallback(
        drawScope: DrawScope,
        node: PageNode,
        drawnAncestors: HashSet<String>
    ) {
        var level = node.id.level - 1
        while (level >= MIN_TILE_LEVEL) {
            val cols = colsAt(level)
            val rows = rowsAt(level)
            val col = (node.fraction.left * cols).toInt().coerceIn(0, cols - 1)
            val row = (node.fraction.top * rows).toInt().coerceIn(0, rows - 1)
            val key = TileId(index, geometry.id, level, row, col).key
            val state = ImageCache.acquireNode(key)
            if (state != null) {
                if (!state.isRecycled() && drawnAncestors.add(key)) {
                    val left = col.toFloat() / cols
                    val top = row.toFloat() / rows
                    val rect = Rect(
                        baseRect.left + left * baseRect.width,
                        baseRect.top + top * baseRect.height,
                        baseRect.left + (col + 1).toFloat() / cols * baseRect.width,
                        baseRect.top + (row + 1).toFloat() / rows * baseRect.height
                    )
                    drawBitmap(drawScope, state, rect)
                }
                ImageCache.releaseNode(state)
                return
            }
            level--
        }
    }

    private fun drawBitmap(drawScope: DrawScope, state: BitmapState, rect: Rect) {
        drawScope.drawImage(
            image = state.bitmap,
            dstOffset = IntOffset(rect.left.toInt(), rect.top.toInt()),
            dstSize = IntSize(rect.width.toInt() + 1, rect.height.toInt() + 1)
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
        links = pageViewState.state.getPageLinks(index)
        linksLoaded = true
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
        releaseAllTiles()
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
}
