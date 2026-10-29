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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.pow

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
    private val tileNodes = HashMap<TileId, PageNode>()
    private var visibleTiles: List<PageNode> = emptyList()

    private var thumbBitmap: BitmapState? by mutableStateOf(null)
    private val thumbLoading: AtomicBoolean = AtomicBoolean(false)
    private var thumbKey: String? = null

    internal var cropProbeRequested: Boolean = false

    // 页面链接
    public var links: List<Hyperlink> = emptyList()
    private var linksLoaded = false

    // 文本选择相关
    private var structuredText: StructuredText? = null
    private var textLoaded = false
    public var currentSelection: TextSelection? by mutableStateOf(null)
    private var isSelecting = false
    private var selectionStartPoint: Offset? = null

    internal fun updateLayout(rect: Rect) {
        baseRect = rect
    }

    internal fun updateGeometry(newGeometry: PageGeometry) {
        if (newGeometry == geometry) {
            return
        }
        releaseTileNodes()
        releaseThumbnail()
        geometry = newGeometry
        currentLevel = Int.MIN_VALUE
        currentSelection = null
    }

    private fun colsAt(level: Int): Int {
        val scale = textureScale(level)
        return ceil(geometry.width * scale / TILE_CONTENT_PX).toInt().coerceAtLeast(1)
    }

    private fun rowsAt(level: Int): Int {
        val scale = textureScale(level)
        return ceil(geometry.height * scale / TILE_CONTENT_PX).toInt().coerceAtLeast(1)
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

        val level = levelFor(zoom)
        if (level != currentLevel) {
            releaseTileNodes()
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

        // 未解码的离屏 tile 立刻取消；已解码的保留少量作为回滚缓冲。
        var retainedOffscreen = tileNodes.size - ids.size
        val iterator = tileNodes.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!ids.contains(entry.key)) {
                val node = entry.value
                if (node.bitmap == null || retainedOffscreen > MAX_RETAINED_TILES) {
                    node.release()
                    iterator.remove()
                    retainedOffscreen--
                }
            }
        }

        visibleTiles = nodes

        if (request) {
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
        return previousVisible != nodes
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
                    CoroutineScope(Dispatchers.Main).launch {
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
        releaseThumbnail()
        cropProbeRequested = false
        currentLevel = Int.MIN_VALUE
    }

    private fun releaseTileNodes() {
        tileNodes.values.forEach { it.release() }
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
        val cached = ImageCache.acquireNode(node.id.key)
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
                        node.queued.set(false)
                        return
                    }
                    val newState = ImageCache.putNode(node.id.key, bitmap)
                    val acquired = newState.acquire()
                    CoroutineScope(Dispatchers.Main).launch {
                        if (acquired && !node.removed.get() && !pageViewState.isShutdown()) {
                            node.bitmap?.let { ImageCache.releaseNode(it) }
                            node.bitmap = newState
                        } else if (acquired) {
                            ImageCache.releaseNode(newState)
                        }
                        node.queued.set(false)
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
        for (node in visibleTiles) {
            val state = node.bitmap
            if (state != null && !state.isRecycled()) {
                drawBitmap(drawScope, state, node.docRect(baseRect))
            }
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
        const val MAX_RETAINED_TILES = 16
    }
}
