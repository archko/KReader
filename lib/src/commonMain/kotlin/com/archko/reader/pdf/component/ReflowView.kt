package com.archko.reader.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.flinger.StockFlingBehaviours
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private data class ReflowPageData(
    val tiles: List<ImageBitmap> = emptyList(),
    val height: Float = 0f,
    val loaded: Boolean = false
)

@Composable
public fun ReflowView(
    decoder: ImageDecoder,
    pageCount: Int,
    currentPath: String,
    onPageChanged: ((page: Int) -> Unit)? = null,
    jumpToPage: Int? = null,
    onSaveDocument: ((
        page: Int,
        pageCount: Int,
        zoom: Double,
        scrollX: Long,
        scrollY: Long,
        scrollOri: Long,
        reflow: Long,
        crop: Long,
        pageOffsetX: Long,
        pageOffsetY: Long
    ) -> Unit)? = null,
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
    horizontalPadding: Dp = 10.dp,
    pageGap: Dp = 10.dp,
) {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val scope = rememberCoroutineScope()
    val viewConfiguration = LocalViewConfiguration.current
    val scrollFlingBehavior = StockFlingBehaviours.smoothScroll()
    val lifecycleOwner = LocalLifecycleOwner.current

    val pageTotal = pageCount.coerceAtLeast(0)
    if (pageTotal == 0) return

    val safeInitialPage = initialPage.coerceIn(0, pageTotal - 1)

    val screenWidth = windowInfo.containerSize.width.coerceAtLeast(1)
    val screenHeight = windowInfo.containerSize.height.coerceAtLeast(1)
    val densityDpi = (density.density * 160f).roundToInt().coerceAtLeast(1)

    val decodeService = remember(
        decoder,
        currentPath,
        screenWidth,
        screenHeight,
        densityDpi
    ) {
        ReflowDecodeService(
            decoder = decoder,
            documentPath = currentPath,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            densityDpi = densityDpi,
            orientation = initialOrientation
        )
    }

    DisposableEffect(decodeService) {
        onDispose {
            decodeService.shutdown()
        }
    }

    val pages = remember(pageTotal, currentPath) {
        mutableStateMapOf<Int, ReflowPageData>()
    }

    var zoom by remember(currentPath) {
        mutableFloatStateOf(
            initialZoom.toFloat().coerceIn(minZoom, maxZoom)
        )
    }

    var horizontalOffset by remember(currentPath) {
        mutableFloatStateOf(-initialScrollX.toFloat())
    }

    var viewportSize by remember {
        mutableStateOf(IntSize.Zero)
    }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = safeInitialPage,
        initialFirstVisibleItemScrollOffset = 0
    )

    val hPaddingPx = with(density) {
        horizontalPadding.toPx()
    }

    val gapPx = with(density) {
        pageGap.toPx()
    }

    val contentWidth = remember(viewportSize, hPaddingPx) {
        (viewportSize.width - hPaddingPx * 2f).coerceAtLeast(1f)
    }

    fun estimatedPageHeight(index: Int): Float {
        val size = decoder.originalPageSizes.getOrNull(index)

        return if (
            size != null &&
            size.width > 0 &&
            size.height > 0
        ) {
            contentWidth * size.height.toFloat() / size.width.toFloat()
        } else {
            max(600f, contentWidth * 1.3f)
        }
    }

    fun pageHeight(index: Int): Float {
        val data = pages[index]
        return if (data != null && data.height > 0f) {
            data.height
        } else {
            estimatedPageHeight(index)
        }
    }

    fun clampHorizontalOffset(value: Float, currentZoom: Float): Float {
        val pageWidth = (contentWidth + hPaddingPx * 2f) * currentZoom

        return if (pageWidth <= viewportSize.width) {
            0f
        } else {
            value.coerceIn(
                viewportSize.width - pageWidth,
                0f
            )
        }
    }

    fun canPanHorizontally(currentZoom: Float): Boolean {
        val pageWidth = (contentWidth + hPaddingPx * 2f) * currentZoom
        return pageWidth > viewportSize.width
    }

    suspend fun loadPage(index: Int) {
        if (index !in 0 until pageTotal) return
        if (pages[index]?.loaded == true) return

        val tiles = try {
            decodeService.requestPage(
                index,
                fontSize,
                wordSpacing
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }

        val totalTileHeight = tiles.sumOf {
            it.height.toLong()
        }.toFloat()

        val tileWidth = tiles.firstOrNull()
            ?.width
            ?.toFloat()
            ?: 0f

        val height = if (
            tiles.isNotEmpty() &&
            tileWidth > 0f &&
            totalTileHeight > 0f
        ) {
            contentWidth * totalTileHeight / tileWidth
        } else {
            estimatedPageHeight(index)
        }

        pages[index] = ReflowPageData(
            tiles = tiles,
            height = height.coerceAtLeast(1f),
            loaded = true
        )
    }

    /*
     * 字号、字间距或文档变化时，页面布局全部失效。
     * LazyColumn 会根据新 item 高度重新布局。
     */
    LaunchedEffect(
        currentPath,
        fontSize,
        wordSpacing,
        contentWidth
    ) {
        pages.clear()
    }

    /*
     * 首次恢复位置。
     *
     * initialScrollY 在旧实现中是全书坐标，
     * 这里不再直接将它当作 LazyColumn 的页内 offset。
     */
    var initialRestoreDone by remember(currentPath) {
        mutableStateOf(false)
    }

    LaunchedEffect(
        viewportSize,
        pageTotal,
        currentPath
    ) {
        if (viewportSize.width <= 0 || viewportSize.height <= 0) {
            return@LaunchedEffect
        }

        if (!initialRestoreDone) {
            listState.scrollToItem(
                index = safeInitialPage,
                scrollOffset = 0
            )

            initialRestoreDone = true
        }
    }

    /*
     * 外部跳页。
     */
    var lastJumpPage by remember(currentPath) {
        mutableIntStateOf(-1)
    }

    LaunchedEffect(jumpToPage, pageTotal) {
        val target = jumpToPage ?: return@LaunchedEffect

        if (
            target in 0 until pageTotal &&
            target != lastJumpPage
        ) {
            lastJumpPage = target
            listState.scrollToItem(target, 0)
        }
    }

    /*
     * 根据 LazyColumn 实际可见 item 加载页面。
     * LazyColumn 自身负责可见区域和预取，不再遍历整本书。
     */
    LaunchedEffect(
        listState,
        pageTotal,
        fontSize,
        wordSpacing,
        contentWidth
    ) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .map { it.index }
                .distinct()
        }.collect { visible ->
            if (visible.isEmpty()) return@collect

            val first = visible.minOrNull() ?: return@collect
            val last = visible.maxOrNull() ?: return@collect

            val start = (first - 1).coerceAtLeast(0)
            val end = (last + 1).coerceAtMost(pageTotal - 1)

            for (index in start..end) {
                if (pages[index]?.loaded != true) {
                    launch {
                        loadPage(index)
                    }
                }
            }
        }
    }

    /*
     * 当前页以 viewport 顶部第一个可见 page 为准。
     */
    LaunchedEffect(listState, onPageChanged) {
        snapshotFlow {
            listState.firstVisibleItemIndex
        }.distinctUntilChanged().collect { page ->
            if (page in 0 until pageTotal) {
                onPageChanged?.invoke(page)
            }
        }
    }

    /*
     * 保存状态。
     *
     * pageOffsetY 保存当前页内偏移（未缩放的文档坐标）。
     * scrollY 保留为 0，避免把 LazyColumn 的局部偏移
     * 错误写成旧版的全书累计坐标。
     */
    fun saveState() {
        val page = listState.firstVisibleItemIndex
            .coerceIn(0, pageTotal - 1)

        val offset = (
                listState.firstVisibleItemScrollOffset / zoom.coerceAtLeast(0.01f)
                ).roundToLong().coerceAtLeast(0L)

        val docX = (
                -horizontalOffset / zoom.coerceAtLeast(0.01f)
                ).roundToLong().coerceAtLeast(0L)

        onSaveDocument?.invoke(
            page,
            pageTotal,
            zoom.toDouble(),
            docX,
            0L,
            initialOrientation.toLong(),
            reflow,
            1L,
            0L,
            offset
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                saveState()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            saveState()
        }
    }

    /*
     * 双指缩放。
     *
     * 单指滚动完全交给 LazyColumn。
     * 双指操作时才接管手势。
     */
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .onSizeChanged {
                viewportSize = it
            }
            .pointerInput(
                listState,
                minZoom,
                maxZoom,
                contentWidth,
                hPaddingPx,
                scrollFlingBehavior
            ) {
                val horizontalScrollScope = object : ScrollScope {
                    override fun scrollBy(pixels: Float): Float {
                        val old = horizontalOffset
                        val next = clampHorizontalOffset(old + pixels, zoom)

                        horizontalOffset = next

                        return next - old
                    }
                }

                var flingJob: Job? = null

                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)

                    flingJob?.cancel()

                    val velocityTracker = VelocityTracker()

                    var horizontalDragStarted = false
                    var horizontalDragX = 0f
                    var horizontalDragY = 0f

                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = event.changes.count { it.pressed }

                        if (pressed >= 2) {
                            val oldZoom = zoom
                            val oldOffsetX = horizontalOffset

                            val previousCentroid = event.calculateCentroid(
                                useCurrent = false
                            )
                            val currentCentroid = event.calculateCentroid(
                                useCurrent = true
                            )

                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()

                            val newZoom = (oldZoom * zoomChange)
                                .coerceIn(minZoom, maxZoom)

                            if (
                                previousCentroid.isSpecified &&
                                currentCentroid.isSpecified &&
                                newZoom != oldZoom
                            ) {
                                // 找到缩放中心所在的页面
                                val item = listState.layoutInfo.visibleItemsInfo
                                    .firstOrNull {
                                        previousCentroid.y >= it.offset &&
                                                previousCentroid.y < it.offset + it.size
                                    }

                                if (item != null) {
                                    // 缩放前，焦点在页面内的文档坐标
                                    val localDocY =
                                        (previousCentroid.y - item.offset) / oldZoom

                                    // 水平方向也以双指中心为缩放焦点
                                    val localDocX =
                                        (previousCentroid.x -
                                                hPaddingPx * oldZoom -
                                                oldOffsetX) / oldZoom

                                    zoom = newZoom

                                    horizontalOffset = clampHorizontalOffset(
                                        currentCentroid.x -
                                                hPaddingPx * newZoom -
                                                localDocX * newZoom,
                                        newZoom
                                    )

                                    // 等待 LazyColumn 使用新高度完成一次布局
                                    scope.launch {
                                        withFrameNanos { }

                                        // 将原来的页面内焦点重新放回双指中心
                                        val targetOffsetY = (
                                                localDocY * newZoom - currentCentroid.y
                                                ).roundToInt()

                                        listState.scrollToItem(
                                            index = item.index,
                                            scrollOffset = targetOffsetY
                                        )
                                    }
                                } else {
                                    zoom = newZoom
                                }
                            } else {
                                // 双指平移
                                if (panChange.y != 0f) {
                                    scope.launch {
                                        listState.scrollBy(-panChange.y)
                                    }
                                }

                                horizontalOffset = clampHorizontalOffset(
                                    oldOffsetX + panChange.x,
                                    oldZoom
                                )
                            }

                            event.changes.forEach {
                                if (it.positionChanged()) {
                                    it.consume()
                                }
                            }
                        } else if (pressed == 1) {
                            val change = event.changes.firstOrNull { it.pressed }

                            if (change != null) {
                                velocityTracker.addPosition(
                                    change.uptimeMillis,
                                    change.position
                                )
                            }

                            if (
                                change != null &&
                                canPanHorizontally(zoom)
                            ) {
                                val delta = change.positionChange()

                                if (!horizontalDragStarted) {
                                    horizontalDragX += delta.x
                                    horizontalDragY += delta.y

                                    if (
                                        abs(horizontalDragX) > viewConfiguration.touchSlop &&
                                        abs(horizontalDragX) > abs(horizontalDragY)
                                    ) {
                                        horizontalDragStarted = true

                                        horizontalOffset = clampHorizontalOffset(
                                            horizontalOffset + horizontalDragX,
                                            zoom
                                        )

                                        change.consume()
                                    }
                                } else {
                                    horizontalOffset = clampHorizontalOffset(
                                        horizontalOffset + delta.x,
                                        zoom
                                    )

                                    change.consume()
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (horizontalDragStarted) {
                        val velocityX = velocityTracker.calculateVelocity().x

                        if (abs(velocityX) > 1f) {
                            flingJob = scope.launch {
                                with(scrollFlingBehavior) {
                                    horizontalScrollScope.performFling(velocityX)
                                }
                            }
                        }
                    }
                }
            }
            .pointerInput(
                listState,
                pageTotal
            ) {
                detectTapGestures(
                    onTap = { position ->
                        val height = size.height.toFloat()

                        when {
                            position.y < height / 4f -> {
                                scope.launch {
                                    listState.scrollBy(
                                        -(height - 16.dp.toPx())
                                    )
                                }
                            }

                            position.y > height * 3f / 4f -> {
                                scope.launch {
                                    listState.scrollBy(
                                        height - 16.dp.toPx()
                                    )
                                }
                            }

                            else -> {
                                onTapNonPageArea?.invoke(
                                    listState.firstVisibleItemIndex
                                )
                            }
                        }
                    },
                    onDoubleTap = { position ->
                        val height = size.height.toFloat()

                        if (
                            position.y in
                            (height / 4f)..(height * 3f / 4f)
                        ) {
                            onDoubleTapToolbar?.invoke()
                        }
                    }
                )
            }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            flingBehavior = scrollFlingBehavior,
            contentPadding = PaddingValues(
                top = pageGap / 2,
                bottom = pageGap / 2
            ),
            verticalArrangement = Arrangement.spacedBy(
                with(density) {
                    (gapPx * zoom).toDp()
                }
            )
        ) {
            items(
                count = pageTotal,
                key = { index -> index }
            ) { index ->

                val data = pages[index]
                val baseHeight = data?.height
                    ?.takeIf { it > 0f }
                    ?: estimatedPageHeight(index)

                val scaledHeight = baseHeight * zoom

                ReflowPageItem(
                    tiles = data?.tiles.orEmpty(),
                    contentWidth = contentWidth,
                    horizontalPadding = hPaddingPx,
                    horizontalOffset = horizontalOffset,
                    zoom = zoom,
                    heightPx = scaledHeight,
                    modifier = Modifier.fillMaxWidth()
                )

                LaunchedEffect(
                    index,
                    fontSize,
                    wordSpacing,
                    contentWidth
                ) {
                    if (pages[index]?.loaded != true) {
                        loadPage(index)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReflowPageItem(
    tiles: List<ImageBitmap>,
    contentWidth: Float,
    horizontalPadding: Float,
    horizontalOffset: Float,
    zoom: Float,
    heightPx: Float,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    Canvas(
        modifier = modifier
            .height(
                with(density) {
                    heightPx.coerceAtLeast(1f).toDp()
                }
            )
    ) {
        val scaledContentWidth = contentWidth * zoom

        val left = (
                horizontalPadding * zoom +
                        horizontalOffset
                )

        if (tiles.isEmpty()) {
            drawRect(
                color = Color(0xFFF6F6F6),
                topLeft = Offset(left, 0f),
                size = Size(
                    scaledContentWidth,
                    size.height
                )
            )
            return@Canvas
        }

        var top = 0f

        tiles.forEach { bitmap ->
            if (bitmap.width <= 0 || bitmap.height <= 0) {
                return@forEach
            }

            val tileHeight =
                contentWidth *
                        bitmap.height.toFloat() /
                        bitmap.width.toFloat() *
                        zoom

            drawImage(
                image = bitmap,
                dstOffset = IntOffset(
                    left.roundToInt(),
                    top.roundToInt()
                ),
                dstSize = IntSize(
                    scaledContentWidth.roundToInt().coerceAtLeast(1),
                    tileHeight.roundToInt().coerceAtLeast(1)
                )
            )

            top += tileHeight
        }
    }
}