package com.archko.reader.pdf.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import com.archko.reader.pdf.cache.ImageCache
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.APage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections.synchronizedMap
import kotlin.math.abs
import kotlin.math.ceil

// 用户提供的模型
public data class SimpleNode(val row: Int, val col: Int, val rect: Rect)

public class SimplePage(
    public val index: Int,
    public val baseWidth: Float,
    public val baseHeight: Float,
    public var topOffset: Float = 0f,
    public var leftOffset: Float = 0f
) {
    // 直接根据可见区域计算需要的节点，不生成所有节点
    public fun getVisibleNodes(zoom: Float, visibleRect: Rect): List<SimpleNode> {
        val scaledWidth = baseWidth * zoom
        val scaledHeight = baseHeight * zoom

        val maxTileSize = 1024f

        // 计算总共需要多少列和行
        val cols = ceil(scaledWidth / maxTileSize).toInt().coerceAtLeast(1)
        val rows = ceil(scaledHeight / maxTileSize).toInt().coerceAtLeast(1)

        // 计算每个瓦片的尺寸（基础逻辑尺寸）
        val tileWidth = baseWidth / cols
        val tileHeight = baseHeight / rows

        // 直接计算可见区域对应的行列范围
        val startCol = (visibleRect.left / tileWidth).toInt().coerceIn(0, cols - 1)
        val endCol = (visibleRect.right / tileWidth).toInt().coerceIn(0, cols - 1)
        val startRow = (visibleRect.top / tileHeight).toInt().coerceIn(0, rows - 1)
        val endRow = (visibleRect.bottom / tileHeight).toInt().coerceIn(0, rows - 1)

        // 只生成可见的节点
        val nodes = mutableListOf<SimpleNode>()
        for (r in startRow..endRow) {
            for (c in startCol..endCol) {
                nodes.add(
                    SimpleNode(
                        r, c, Rect(
                            left = c * tileWidth,
                            top = r * tileHeight,
                            right = (c + 1) * tileWidth,
                            bottom = (r + 1) * tileHeight
                        )
                    )
                )
            }
        }

        println("getVisibleNodes:$startCol-$endCol, $startRow-$endRow, col-row:$cols-$rows, zoom:$zoom, nodes:${nodes.size}, tile:$tileWidth-$tileHeight, $visibleRect")
        return nodes
    }
}

public class DecoderService(
    private val scope: CoroutineScope,
    private val decoder: ImageDecoder,
    private val onTileDecoded: (String) -> Unit // 解码完成的回调){}
) {
    private val decoderDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val taskChannel = Channel<DecodeTask>(Channel.UNLIMITED)
    private val runningJobs = synchronizedMap(mutableMapOf<String, Job>())

    init {
        scope.launch(decoderDispatcher) {
            for (task in taskChannel) {
                val job = runningJobs[task.key] ?: continue
                if (job.isCancelled) {
                    runningJobs.remove(task.key)
                    continue
                }

                decode(task)
                runningJobs.remove(task.key)
            }
        }
    }

    public fun requestDecode(task: DecodeTask) {
        if (hasCache(task)) return
        if (runningJobs.contains(task.key)) return

        val taskJob = Job()
        runningJobs[task.key] = taskJob
        taskChannel.trySend(task)
    }

    private fun hasCache(task: DecodeTask): Boolean {
        if (task.type == TaskType.PAGE && ImageCache.hasPage(task.key)) {
            return true
        }
        if (task.type == TaskType.NODE && ImageCache.hasNode(task.key)) {
            return true
        }
        return false
    }

    public fun cancelTasksExcept(visibleKeys: Set<String>) {
        val keysToCancel = runningJobs.keys - visibleKeys
        println("cancelTasks:${visibleKeys.size}, cancel:${keysToCancel.size}")
        keysToCancel.forEach { key ->
            runningJobs[key]?.cancel()
            runningJobs.remove(key)
        }
    }

    private suspend fun decode(task: DecodeTask) {
        if (hasCache(task)) {
            withContext(Dispatchers.Main) {
                onTileDecoded(task.key)
            }
            return
        }
        val start = System.currentTimeMillis()
        val bitmap = if (task.type == TaskType.PAGE) {
            decoder.renderPageRegion(task, task.zoom)
            /*val imageBitmap = ImageBitmap(task.width, task.height)
            val canvas = Canvas(imageBitmap)
            val paint = Paint().apply {
                color = Color.LightGray
                style = PaintingStyle.Stroke
            }
            val margin = 5f
            canvas.drawRect(
                left = margin,
                top = margin,
                right = task.width - margin,
                bottom = task.height - margin,
                paint = paint
            )
            imageBitmap*/
        } else {
            // 这里需要用到上面修改的 renderPageRegion
            decoder.renderPageRegion(task, task.zoom)
            /*delay(10)
            val imageBitmap = ImageBitmap(task.width, task.height)
            val canvas = Canvas(imageBitmap)
            val paint = Paint().apply {
                color = Color(0xFF80A0FF)
                style = PaintingStyle.Stroke
            }
            val margin = 8f
            canvas.drawRect(
                left = margin,
                top = margin,
                right = task.width - margin,
                bottom = task.height - margin,
                paint = paint
            )
            imageBitmap*/
        }

        // 存入缓存
        if (task.type == TaskType.PAGE) {
            ImageCache.putPage(task.key, bitmap)
        } else {
            ImageCache.putNode(task.key, bitmap)
        }
        println("decode:cos:${System.currentTimeMillis() - start} ,$task")

        // 【关键】回到主线程通知 UI 更新
        withContext(Dispatchers.Main) {
            onTileDecoded(task.key)
        }
    }
}

// 视图状态管理器：负责手势逻辑与边界计算
@Stable
public class TiledViewerState(
    public val aPages: List<APage>,
    decoder: ImageDecoder,
    initialContainerSize: IntSize,
    scope: CoroutineScope,
    public val column: Int = 1,
) {
    // 基础状态
    public var containerSize: IntSize by mutableStateOf(initialContainerSize)
    public var vZoom: Float by mutableFloatStateOf(8.493418f)
    public var offset: Offset by mutableStateOf(Offset.Zero)

    public var renderZoom: Float by mutableFloatStateOf(1f)
    public var isScaling: Boolean by mutableStateOf(false)

    public var pages: List<SimplePage> by mutableStateOf(createPages())

    // 文档总尺寸（逻辑尺寸）
    public val totalWidth: Float = containerSize.width.toFloat()
    public val totalHeight: Float = pages.lastOrNull()?.let { it.topOffset + it.baseHeight } ?: 0f

    public var tileUpdateCounter: Int by mutableIntStateOf(0)

    public val decoder: DecoderService = DecoderService(scope, decoder) { key ->
        // 当任何一个瓦片解码完成，计数器自增，触发 Canvas 重绘
        tileUpdateCounter++
    }

    private fun createPages(): List<SimplePage> {
        val result = mutableListOf<SimplePage>()
        var currentTop = 0f
        val containerW = containerSize.width.toFloat()

        // 每一列分配的逻辑宽度
        val columnWidth = containerW / column

        // 将页面按 column 数量分组处理
        val pageChunks = aPages.chunked(column)

        pageChunks.forEach { rowPages ->
            // 1. 计算当前行中，所有页面按 columnWidth 缩放后的高度
            val rowHeights = rowPages.map { aPage ->
                val scale = columnWidth / aPage.getWidth(false)
                aPage.getHeight(false) * scale
            }

            // 2. 找出当前行最高的页面高度，作为整行的高度
            val maxHeight = rowHeights.maxOrNull() ?: 0f

            // 3. 创建 SimplePage 对象并分配位置
            rowPages.forEachIndexed { index, aPage ->
                val scale = columnWidth / aPage.getWidth(false)
                val scaledHeight = aPage.getHeight(false) * scale

                result.add(
                    SimplePage(
                        index = aPage.index,
                        baseWidth = columnWidth,
                        baseHeight = scaledHeight,
                        topOffset = currentTop,
                        leftOffset = index * columnWidth // 根据所在列分配偏移
                    )
                )
            }

            // 4. 累加行高，移动到下一行起始位置
            currentTop += maxHeight
        }

        return result
    }

    /**
     * 计算当前可见的内容（Page 和对应的 Nodes）
     * 这部分由 Canvas 调用，用来决定画什么。
     * 注意：始终使用 renderZoom 来计算分块，确保缩放过程中分块不变
     * 优化：直接根据可见区域计算需要的节点，不生成和遍历所有节点
     */
    public val visibleContent: List<Pair<SimplePage, List<SimpleNode>>> by derivedStateOf {
        if (containerSize == IntSize.Zero) return@derivedStateOf emptyList()

        // 使用 renderZoom 计算可见区域（基于已解码的缩放级别）
        val visibleArea = Rect(
            -offset.x / vZoom,
            -offset.y / vZoom,
            (-offset.x + containerSize.width) / vZoom,
            (-offset.y + containerSize.height) / vZoom
        )

        pages.filter { page ->
            val pageRect = Rect(
                page.leftOffset,
                page.topOffset,
                page.leftOffset + page.baseWidth,
                page.topOffset + page.baseHeight
            )
            pageRect.overlaps(visibleArea)
        }.map { page ->
            // 计算页面内的可见区域
            val localLeft = (visibleArea.left - page.leftOffset).coerceIn(0f, page.baseWidth)
            val localRight = (visibleArea.right - page.leftOffset).coerceIn(0f, page.baseWidth)
            val localTop = (visibleArea.top - page.topOffset).coerceIn(0f, page.baseHeight)
            val localBottom = (visibleArea.bottom - page.topOffset).coerceIn(0f, page.baseHeight)

            val pageVisibleRect = Rect(localLeft, localTop, localRight, localBottom)

            // 直接生成可见的节点，不生成所有节点
            val nodes = page.getVisibleNodes(renderZoom, pageVisibleRect)
            page to nodes
        }
    }

    // 辅助方法：生成缓存 Key
    public fun getNodeKey(pageIndex: Int, node: SimpleNode): String =
        "node_${pageIndex}_${node.row}_${node.col}_$renderZoom"

    public fun getPageKey(index: Int): String = "page_$index"

    public fun syncRenderZoom() {
        renderZoom = vZoom
    }

    /**
     * 边界修正逻辑
     */
    public fun calculateBounds(targetOffset: Offset, zoom: Float): Offset {
        val cw = totalWidth * zoom
        val ch = totalHeight * zoom
        val size = containerSize

        val minX = if (cw > size.width) size.width - cw else (size.width - cw) / 2f
        val maxX = if (cw > size.width) 0f else (size.width - cw) / 2f
        val minY = if (ch > size.height) size.height - ch else 0f
        val maxY = 0f

        return Offset(
            targetOffset.x.coerceIn(minX.coerceAtMost(maxX), maxX.coerceAtLeast(minX)),
            targetOffset.y.coerceIn(minY.coerceAtMost(maxY), maxY.coerceAtLeast(minY))
        )
    }

    /**
     * 更新偏移量（带边界检查）
     */
    public fun updateOffset(newOffset: Offset) {
        offset = calculateBounds(newOffset, vZoom)
    }

    /**
     * 处理缩放和偏移的同步更新
     */
    public fun updateZoomAndOffset(newZoom: Float, centroid: Offset) {
        val oldZoom = vZoom
        val zoomFactor = newZoom / oldZoom

        val targetOffset = Offset(
            centroid.x - (centroid.x - offset.x) * zoomFactor,
            centroid.y - (centroid.y - offset.y) * zoomFactor
        )

        vZoom = newZoom
        offset = calculateBounds(targetOffset, newZoom)
    }

    // 在 TiledViewerState 类中添加一个辅助方法
    public fun getTaskScale(pageIndex: Int): Float {
        val aPage = aPages[pageIndex]
        // 基础缩放：PDF 原始宽度 -> 当前页面占用的逻辑宽度 (columnWidth)
        val columnWidth = containerSize.width.toFloat() / column
        val baseScale = columnWidth / aPage.getWidth(false)
        // 最终解码比例 = 基础比例 * 手势缩放倍率
        return baseScale * renderZoom
    }
}

@Composable
public fun TiledDocumentViewer(
    pages: List<APage>,
    decoder: ImageDecoder,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val state =
        remember(pages, containerSize) { TiledViewerState(pages, decoder, containerSize, scope) }

    var flingJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(state.visibleContent, state.renderZoom) {
        val visibleKeys = mutableSetOf<String>()

        state.visibleContent.forEach { (page, nodes) ->
            // 1. 请求 Page 缩略图（固定大小，不随 renderZoom 变化）
            val pageKey = state.getPageKey(page.index)
            visibleKeys.add(pageKey)
            if (!ImageCache.hasPage(pageKey)) {
                val aPage = state.aPages[page.index]
                val thumbWidth = 360
                val thumbScale = (thumbWidth / aPage.getWidth(false))
                val thumbHeight = (aPage.getHeight(false) * thumbScale).toInt()

                state.decoder.requestDecode(
                    DecodeTask(
                        type = TaskType.PAGE,
                        pageIndex = page.index,
                        key = pageKey,
                        width = thumbWidth,
                        height = thumbHeight,
                        zoom = thumbScale,
                        aPage = state.aPages[page.index],
                        pageSliceBounds = Rect(0f, 0f, 0f, 0f)
                    )
                )
            }

            val totalScale = state.getTaskScale(page.index)
            // 2. 请求可见的 Node 高清图
            nodes.forEach { node ->
                val nodeKey = state.getNodeKey(page.index, node)
                visibleKeys.add(nodeKey)
                if (!ImageCache.hasNode(nodeKey)) {
                    // node.rect 现在是基础逻辑坐标，需要乘以 renderZoom 得到物理像素
                    val pdfX = (node.rect.left * state.renderZoom)
                    val pdfY = (node.rect.top * state.renderZoom)
                    state.decoder.requestDecode(
                        DecodeTask(
                            type = TaskType.NODE,
                            pageIndex = page.index,
                            key = nodeKey,
                            // 物理像素大小 = 基础逻辑尺寸 * renderZoom
                            width = (node.rect.width * state.renderZoom).toInt(),
                            height = (node.rect.height * state.renderZoom).toInt(),
                            zoom = totalScale,
                            aPage = state.aPages[page.index],
                            pageSliceBounds = Rect(pdfX, pdfY, 0f, 0f)
                        )
                    )
                }
            }
        }

        state.decoder.cancelTasksExcept(visibleKeys)
    }

    // Fling 执行函数
    fun performFling(initialVelocity: Velocity) {
        flingJob?.cancel()
        val decay = exponentialDecay<Float>(
            frictionMultiplier = 0.25f, // 摩擦系数，可根据手感微调
            absVelocityThreshold = 0.5f
        )

        flingJob = scope.launch {
            // X 和 Y 轴并行执行衰减动画
            launch {
                Animatable(state.offset.x).animateDecay(initialVelocity.x, decay) {
                    val targetX = value
                    val bounded =
                        state.calculateBounds(Offset(targetX, state.offset.y), state.vZoom)
                    state.offset = Offset(bounded.x, state.offset.y)
                    // 如果被边界卡住（实际值偏离了动画值），则停止动画
                    if (abs(targetX - bounded.x) > 0.5f) this@launch.cancel()
                }
            }
            launch {
                Animatable(state.offset.y).animateDecay(initialVelocity.y, decay) {
                    val targetY = value
                    val bounded =
                        state.calculateBounds(Offset(state.offset.x, targetY), state.vZoom)
                    state.offset = Offset(state.offset.x, bounded.y)
                    if (abs(targetY - bounded.y) > 0.5f) this@launch.cancel()
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            // 修正 1：将手势移到这里，不受 graphicsLayer 变换影响
            .pointerInput(containerSize) {
                if (containerSize == IntSize.Zero) return@pointerInput
                coroutineScope {
                    awaitEachGesture {
                        val velocityTracker = VelocityTracker()
                        var isZooming = false

                        // 1. 触摸即停：按下瞬间停止任何正在进行的惯性动画
                        awaitFirstDown(requireUnconsumed = false)
                        flingJob?.cancel() // 停止之前的惯性动画

                        do {
                            val event = awaitPointerEvent()
                            if (!event.changes.fastAny { it.isConsumed }) {
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()

                                if (!isZooming && event.changes.size > 1) isZooming = true

                                if (isZooming) {
                                    val centroid = event.calculateCentroid(useCurrent = false)
                                    if (zoomChange != 1f) {
                                        val newZoom = (state.vZoom * zoomChange).coerceIn(1f, 20f)
                                        state.updateZoomAndOffset(newZoom, centroid)
                                    }
                                } else {
                                    state.updateOffset(state.offset + panChange)
                                    val pointer = event.changes.first()
                                    velocityTracker.addPosition(
                                        pointer.uptimeMillis,
                                        pointer.position
                                    )
                                }
                                event.changes.fastForEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.fastAny { it.pressed })

                        state.syncRenderZoom()

                        // 3. 抬手后：如果没有进行过缩放，则启动惯性动画
                        if (!isZooming) {
                            val velocity = velocityTracker.calculateVelocity()
                            flingJob = scope.launch {
                                performFling(velocity)
                            }
                        }
                    }
                }
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = state.offset.x
                    translationY = state.offset.y
                    scaleX = state.vZoom
                    scaleY = state.vZoom
                    transformOrigin = TransformOrigin(0f, 0f)
                    // 修正 2：强制开启 Hardware Layer 提速
                    clip = false
                    renderEffect = null
                }
        ) {
            val _unused = state.tileUpdateCounter
            // 只负责遍历绘制，逻辑全部在 state.visibleContent 中预计算好了
            state.visibleContent.forEach { (page, nodes) ->
                val pageTop = page.topOffset
                val pageLeft = page.leftOffset

                // 1. 尝试绘制 Page 缩略图
                val pageKey = state.getPageKey(page.index)
                val pageBitmapState = ImageCache.acquirePage(pageKey)
                if (pageBitmapState != null) {
                    drawImage(
                        image = pageBitmapState.bitmap,
                        dstOffset = IntOffset(pageLeft.toInt(), pageTop.toInt()),
                        dstSize = IntSize(page.baseWidth.toInt(), page.baseHeight.toInt())
                    )
                }
                /*drawRect(
                    color = Color.Blue,
                    topLeft = Offset(0f, pageTop),
                    size = Size(page.baseWidth, page.baseHeight),
                    style = Stroke(width = 6f / state.vZoom)
                )*/

                // 2. 尝试绘制高清 Nodes
                val overlap = 1f / state.vZoom
                nodes.forEach { node ->
                    val nodeKey = state.getNodeKey(page.index, node)
                    val nodeBitmapState = ImageCache.acquireNode(nodeKey)

                    if (nodeBitmapState != null) {
                        // node.rect 是基础逻辑坐标，直接使用即可
                        drawImage(
                            image = nodeBitmapState.bitmap,
                            dstOffset = IntOffset(
                                (pageLeft + node.rect.left).toInt(),
                                (pageTop + node.rect.top).toInt()
                            ),
                            dstSize = IntSize(
                                (node.rect.width + overlap).toInt(),
                                (node.rect.height + overlap).toInt()
                            )
                        )
                    }
                    // 调试用边框
                    /*val nodeRect = node.rect.translate(0f, pageTop)
                    drawRect(
                        color = Color.Red,
                        topLeft = nodeRect.topLeft,
                        size = nodeRect.size,
                        style = Stroke(width = 4f / state.vZoom)
                    )*/
                }
            }
        }
    }
}