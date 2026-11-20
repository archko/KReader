package com.archko.reader.viewer

import android.graphics.Bitmap
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private class PageSlot(
    val pageIndex: Int,
    var topDocY: Float = 0f,
    var heightDoc: Float = 800f,
    var tiles: List<Bitmap> = emptyList(),
    var loaded: Boolean = false,
    var loading: Boolean = false,
)

@Composable
fun ReflowView(
    decoder: ImageDecoder,
    pageCount: Int,
    currentPath: String,
    onPageChanged: ((page: Int) -> Unit)? = null,
    jumpToPage: Int? = null,
    onSaveDocument: ((page: Int, pageCount: Int, zoom: Double, scrollX: Long, scrollY: Long, scrollOri: Long, reflow: Long, crop: Long, pageOffsetX: Long, pageOffsetY: Long) -> Unit)? = null,
    onDoubleTapToolbar: (() -> Unit)? = null,
    onTapNonPageArea: ((pageIndex: Int) -> Unit)? = null,
    initialPage: Int = 0,
    initialScrollX: Long = 0L,
    initialScrollY: Long = 0L,
    initialZoom: Double = 1.0,
    initialOrientation: Int,
    reflow: Long = 1L,
    wordSpacing: Float = 0.3f,
    fontSize: Float = 1.0f,
    minZoom: Float = 1.0f,
    maxZoom: Float = 4.0f,
    // ── 边距参数：在这里统一调整左右边距与页间上下边距 ────────────────────────
    horizontalPadding: Dp = 10.dp,
    pageGap: Dp = 10.dp,
) {
    val context = LocalContext.current
    val dm = context.resources.displayMetrics
    val scope = rememberCoroutineScope()
    val viewConfiguration = LocalViewConfiguration.current
    val density = LocalDensity.current
    val tapKeepPx = remember(density) { with(density) { 16.dp.toPx() } }
    val hPaddingDoc = remember(density, horizontalPadding) { with(density) { horizontalPadding.toPx() } }
    val gapDoc = remember(density, pageGap) { with(density) { pageGap.toPx() } }

    val decodeService = remember(decoder, currentPath) {
        ReflowDecodeService(
            context = context,
            decoder = decoder,
            documentPath = currentPath,
            screenWidth = dm.widthPixels,
            screenHeight = dm.heightPixels,
            densityDpi = dm.densityDpi,
            orientation = initialOrientation,
        )
    }

    DisposableEffect(decodeService) {
        onDispose { decodeService.shutdown() }
    }

    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember { mutableFloatStateOf(initialZoom.toFloat().coerceIn(minZoom, maxZoom)) }
    var scrollOffset by remember {
        mutableStateOf(Offset(-initialScrollX.toFloat(), -initialScrollY.toFloat()))
    }
    val clampedInitialPage = initialPage.coerceIn(0, max(0, pageCount - 1))
    var currentPage by remember { mutableIntStateOf(clampedInitialPage) }
    var lastHandledJumpPage by remember { mutableIntStateOf(-1) }
    var initializedForSize by remember { mutableStateOf(false) }
    var layoutTick by remember { mutableIntStateOf(0) }
    var flingJob by remember { mutableStateOf<Job?>(null) }

    val slots = remember(pageCount, currentPath) {
        Array(max(0, pageCount)) { idx -> PageSlot(pageIndex = idx) }
    }

    fun contentWidthDoc(viewWidthPx: Float): Float {
        return max(1f, viewWidthPx - hPaddingDoc * 2f)
    }

    fun defaultPageHeight(pageIndex: Int, contentW: Float): Float {
        val orig = decoder.originalPageSizes.getOrNull(pageIndex)
        return if (orig != null && orig.width > 0 && orig.height > 0) {
            contentW * (orig.height.toFloat() / orig.width.toFloat())
        } else {
            max(600f, contentW * 1.3f)
        }
    }

    fun recomputeTops(contentW: Float): Float {
        var y = gapDoc * 0.5f
        for (slot in slots) {
            slot.topDocY = y
            if (!slot.loaded) {
                slot.heightDoc = defaultPageHeight(slot.pageIndex, contentW)
            }
            y += slot.heightDoc + gapDoc
        }
        return max(0f, y - gapDoc * 0.5f)
    }

    fun clampOffset(raw: Offset, currentZoom: Float, docWidth: Float, totalDocHeight: Float): Offset {
        if (viewSize.width <= 0 || viewSize.height <= 0 || totalDocHeight <= 0f) return Offset.Zero
        val contentW = docWidth * currentZoom
        val contentH = totalDocHeight * currentZoom

        val minX = if (contentW > viewSize.width) viewSize.width - contentW else 0f
        val maxX = if (contentW > viewSize.width) 0f else (viewSize.width - contentW) / 2f
        val clampedX = raw.x.coerceIn(min(minX, maxX), max(minX, maxX))

        val minY = if (contentH > viewSize.height) viewSize.height - contentH else min(0f, viewSize.height - contentH)
        val maxY = 0f
        val clampedY = raw.y.coerceIn(min(minY, maxY), max(minY, maxY))

        return Offset(clampedX, clampedY)
    }

    fun findFirstVisiblePage(currentOffset: Offset, currentZoom: Float): Int {
        if (slots.isEmpty() || currentZoom <= 0f) return 0
        val topDoc = (-currentOffset.y / currentZoom).coerceAtLeast(0f)
        for (slot in slots) {
            if (topDoc < slot.topDocY + slot.heightDoc + gapDoc) {
                return slot.pageIndex
            }
        }
        return slots.last().pageIndex
    }

    // 字号或字间距变化时重置已解码页，触发重新解码
    LaunchedEffect(fontSize, wordSpacing, currentPath) {
        for (slot in slots) {
            slot.tiles = emptyList()
            slot.loaded = false
            slot.loading = false
        }
        layoutTick++
    }

    // 视图尺寸就绪后，恢复初始页码；外部 jumpToPage 变化时跳转
    LaunchedEffect(viewSize, jumpToPage) {
        if (viewSize.width <= 0 || viewSize.height <= 0 || slots.isEmpty()) return@LaunchedEffect
        val docWidth = viewSize.width.toFloat()
        val cWidth = contentWidthDoc(docWidth)
        val totalH = recomputeTops(cWidth)

        if (!initializedForSize) {
            initializedForSize = true
            val targetIdx = clampedInitialPage
            lastHandledJumpPage = jumpToPage ?: targetIdx
            val targetTop = max(0f, slots[targetIdx].topDocY - gapDoc * 0.5f)
            scrollOffset = clampOffset(Offset(0f, -targetTop * zoom), zoom, docWidth, totalH)
            currentPage = targetIdx
            onPageChanged?.invoke(targetIdx)
            layoutTick++
        } else if (jumpToPage != null && jumpToPage != lastHandledJumpPage && jumpToPage in 0 until pageCount) {
            lastHandledJumpPage = jumpToPage
            val targetTop = max(0f, slots[jumpToPage].topDocY - gapDoc * 0.5f)
            scrollOffset = clampOffset(Offset(0f, -targetTop * zoom), zoom, docWidth, totalH)
            currentPage = jumpToPage
            onPageChanged?.invoke(jumpToPage)
            layoutTick++
        }
    }

    // 滚动 / 缩放 / 参数变化时，加载当前视口及前后页
    LaunchedEffect(scrollOffset, zoom, viewSize, fontSize, wordSpacing, layoutTick) {
        if (viewSize.width <= 0 || viewSize.height <= 0 || slots.isEmpty()) return@LaunchedEffect
        val docWidth = viewSize.width.toFloat()
        val cWidth = contentWidthDoc(docWidth)
        recomputeTops(cWidth)

        val visPage = findFirstVisiblePage(scrollOffset, zoom)
        if (visPage != currentPage) {
            currentPage = visPage
            onPageChanged?.invoke(visPage)
        }

        val startIdx = max(0, visPage - 1)
        val endIdx = min(slots.lastIndex, visPage + 2)

        for (idx in startIdx..endIdx) {
            val slot = slots[idx]
            if (!slot.loaded && !slot.loading) {
                slot.loading = true
                scope.launch {
                    val tiles = decodeService.requestPage(idx, fontSize, wordSpacing)
                    slot.loading = false
                    slot.loaded = true
                    slot.tiles = tiles

                    val totalTileH = tiles.sumOf { it.height }.toFloat()
                    val tileW = tiles.firstOrNull()?.width?.toFloat() ?: 0f
                    val newHeight = if (tiles.isNotEmpty() && tileW > 0f && totalTileH > 0f) {
                        cWidth * (totalTileH / tileW)
                    } else {
                        defaultPageHeight(idx, cWidth)
                    }

                    val oldHeight = slot.heightDoc
                    val deltaDocH = newHeight - oldHeight
                    slot.heightDoc = newHeight

                    val newTotalH = recomputeTops(cWidth)
                    val anchorPage = findFirstVisiblePage(scrollOffset, zoom)
                    if (idx < anchorPage && deltaDocH != 0f) {
                        scrollOffset = clampOffset(
                            Offset(scrollOffset.x, scrollOffset.y - deltaDocH * zoom),
                            zoom,
                            docWidth,
                            newTotalH
                        )
                    } else {
                        scrollOffset = clampOffset(scrollOffset, zoom, docWidth, newTotalH)
                    }
                    layoutTick++
                }
            }
        }
    }

    fun saveState() {
        val safeZoom = max(0.01f, zoom)
        val docX = (-scrollOffset.x / safeZoom).toLong().coerceAtLeast(0L)
        val docY = (-scrollOffset.y / safeZoom).toLong().coerceAtLeast(0L)
        onSaveDocument?.invoke(
            currentPage,
            pageCount,
            zoom.toDouble(),
            docX,
            docY,
            initialOrientation.toLong(),
            reflow,
            1L,
            0L,
            0L
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) saveState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            saveState()
        }
    }

    var lastTapTime by remember { mutableStateOf(0L) }
    var tapJob by remember { mutableStateOf<Job?>(null) }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .onSizeChanged { viewSize = it }
            .pointerInput(viewSize, minZoom, maxZoom, tapKeepPx) {
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val velocityTracker = VelocityTracker()
                    var isZooming = false
                    var isDragging = false
                    var totalPan = Offset.Zero
                    var lastChange: PointerInputChange? = null

                    val down = awaitFirstDown(requireUnconsumed = false)
                    val wasFlinging = flingJob?.isActive == true
                    tapJob?.cancel()
                    flingJob?.cancel()

                    do {
                        val event = awaitPointerEvent()
                        if (!event.changes.fastAny { it.isConsumed }) {
                            lastChange = event.changes.firstOrNull()
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()

                            if (!isZooming && event.changes.size > 1) {
                                isZooming = true
                            }

                            val docWidth = max(1f, size.width.toFloat())
                            val cWidth = contentWidthDoc(docWidth)
                            val totalH = recomputeTops(cWidth)

                            if (isZooming) {
                                val centroid = event.calculateCentroid(useCurrent = false)
                                if (centroid.isSpecified && zoomChange != 1f) {
                                    val oldZoom = zoom
                                    val newZoom = (oldZoom * zoomChange).coerceIn(minZoom, maxZoom)
                                    if (newZoom != oldZoom) {
                                        val docX = (centroid.x - scrollOffset.x) / oldZoom
                                        val docY = (centroid.y - scrollOffset.y) / oldZoom
                                        val nextOffset = Offset(
                                            centroid.x - docX * newZoom,
                                            centroid.y - docY * newZoom
                                        )
                                        zoom = newZoom
                                        scrollOffset = clampOffset(nextOffset, newZoom, docWidth, totalH)
                                    }
                                }
                            } else {
                                totalPan += panChange
                                val delta = when {
                                    !isDragging && totalPan.getDistance() > touchSlop -> {
                                        isDragging = true
                                        tapJob?.cancel()
                                        lastTapTime = 0L
                                        totalPan
                                    }
                                    isDragging -> panChange
                                    else -> Offset.Zero
                                }
                                if (delta != Offset.Zero) {
                                    scrollOffset = clampOffset(scrollOffset + delta, zoom, docWidth, totalH)
                                    velocityTracker.addPosition(
                                        lastChange?.uptimeMillis ?: 0L,
                                        lastChange?.position ?: Offset.Zero
                                    )
                                }
                            }

                            event.changes.fastForEach {
                                if (it.positionChanged()) it.consume()
                            }
                        }
                    } while (event.changes.fastAny { it.pressed })

                    val finalChange = lastChange ?: return@awaitEachGesture
                    val docWidth = max(1f, size.width.toFloat())
                    val cWidth = contentWidthDoc(docWidth)
                    val totalH = recomputeTops(cWidth)

                    if (!isZooming) {
                        val dragDist = (finalChange.position - down.position).getDistance()
                        if (!isDragging && !wasFlinging && dragDist < touchSlop) {
                            val tapPos = finalChange.position
                            val now = System.currentTimeMillis()
                            if (now - lastTapTime < 300L) {
                                tapJob?.cancel()
                                lastTapTime = 0L
                                val h = size.height.toFloat()
                                if (tapPos.y in (h / 4f)..(h * 3 / 4f)) {
                                    onDoubleTapToolbar?.invoke()
                                }
                            } else {
                                lastTapTime = now
                                tapJob?.cancel()
                                tapJob = scope.launch {
                                    delay(300L)
                                    val h = size.height.toFloat()
                                    when {
                                        tapPos.y < h / 4f -> {
                                            val step = max(0f, h - tapKeepPx)
                                            scrollOffset = clampOffset(
                                                Offset(scrollOffset.x, scrollOffset.y + step),
                                                zoom,
                                                docWidth,
                                                totalH
                                            )
                                        }
                                        tapPos.y > h * 3 / 4f -> {
                                            val step = max(0f, h - tapKeepPx)
                                            scrollOffset = clampOffset(
                                                Offset(scrollOffset.x, scrollOffset.y - step),
                                                zoom,
                                                docWidth,
                                                totalH
                                            )
                                        }
                                        else -> {
                                            onTapNonPageArea?.invoke(currentPage)
                                        }
                                    }
                                }
                            }
                        } else if (isDragging) {
                            // 对齐 DocumentView 的惯性参数：低摩擦系数 + 1.1x 速度放大 + 低速度阈值
                            val vel = velocityTracker.calculateVelocity()
                            val decay = exponentialDecay<Offset>(
                                frictionMultiplier = 0.35f,
                                absVelocityThreshold = 0.50f
                            )
                            val scaledVel = Offset(vel.x * 1.1f, vel.y * 1.1f)
                            flingJob = scope.launch {
                                val animState = AnimationState(
                                    typeConverter = ReflowOffsetToVector,
                                    initialValue = scrollOffset,
                                    initialVelocity = scaledVel
                                )
                                animState.animateDecay(decay) {
                                    val bounded = clampOffset(value, zoom, docWidth, totalH)
                                    scrollOffset = bounded
                                    val v = this.velocity
                                    val blockedX = v.x == 0f || v.x * (value.x - bounded.x) > 0f
                                    val blockedY = v.y == 0f || v.y * (value.y - bounded.y) > 0f
                                    if (blockedX && blockedY) {
                                        cancelAnimation()
                                    }
                                }
                            }
                        }
                    }
                }
            }
    ) {
        if (size.width <= 0 || size.height <= 0 || slots.isEmpty()) return@Canvas

        @Suppress("UNUSED_EXPRESSION")
        layoutTick

        val docWidth = size.width.toFloat()
        val cWidth = contentWidthDoc(docWidth)
        recomputeTops(cWidth)

        val visTopDoc = -scrollOffset.y / zoom
        val visBottomDoc = (-scrollOffset.y + size.height) / zoom

        for (slot in slots) {
            val itemBottomDoc = slot.topDocY + slot.heightDoc
            if (itemBottomDoc < visTopDoc || slot.topDocY > visBottomDoc) continue

            val leftPx = (scrollOffset.x + hPaddingDoc * zoom).roundToInt()
            val rightPx = (scrollOffset.x + (hPaddingDoc + cWidth) * zoom).roundToInt()

            if (slot.tiles.isNotEmpty()) {
                var tileTopDoc = slot.topDocY
                for (bmp in slot.tiles) {
                    val bmpW = max(1, bmp.width).toFloat()
                    val bmpH = max(1, bmp.height).toFloat()
                    val tileDocH = cWidth * (bmpH / bmpW)

                    val topPx = (scrollOffset.y + tileTopDoc * zoom).roundToInt()
                    val bottomPx = (scrollOffset.y + (tileTopDoc + tileDocH) * zoom).roundToInt()

                    if (bottomPx >= 0 && topPx <= size.height) {
                        drawImage(
                            image = bmp.asImageBitmap(),
                            dstOffset = IntOffset(leftPx, topPx),
                            dstSize = IntSize(max(1, rightPx - leftPx), max(1, bottomPx - topPx))
                        )
                    }
                    tileTopDoc += tileDocH
                }
            } else {
                val topPx = (scrollOffset.y + slot.topDocY * zoom).roundToInt()
                val bottomPx = (scrollOffset.y + itemBottomDoc * zoom).roundToInt()
                drawRect(
                    color = Color(0xFFF6F6F6),
                    topLeft = Offset(leftPx.toFloat(), topPx.toFloat()),
                    size = Size(
                        max(1, rightPx - leftPx).toFloat(),
                        max(1, bottomPx - topPx).toFloat()
                    )
                )
            }

            // 页间分隔线
            val divTopDoc = slot.topDocY + slot.heightDoc
            val divTopPx = scrollOffset.y + divTopDoc * zoom
            val divBottomPx = scrollOffset.y + (divTopDoc + gapDoc) * zoom
            if (divBottomPx >= 0f && divTopPx <= size.height) {
                val lineY = (divTopPx + divBottomPx) * 0.5f
                drawLine(
                    color = Color(0xFF888888),
                    start = Offset(leftPx.toFloat().coerceAtLeast(0f), lineY),
                    end = Offset(rightPx.toFloat().coerceAtMost(size.width.toFloat()), lineY),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
    }
}

private val ReflowOffsetToVector: TwoWayConverter<Offset, AnimationVector2D> = TwoWayConverter(
    convertFromVector = { Offset(it.v1, it.v2) },
    convertToVector = { AnimationVector2D(it.x, it.y) }
)