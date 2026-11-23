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
import kotlin.math.abs
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

/**
 * 类似 RecyclerView LinearLayoutManager 的 AnchorInfo。
 *
 * pageIndex:
 *   当前作为布局锚点的 page
 *
 * offsetDocY:
 *   锚点在该 page 内的文档坐标
 *
 * screenY:
 *   这个锚点当前在屏幕上的 Y 坐标
 */
private class LayoutAnchor(
    val pageIndex: Int,
    val offsetDocY: Float,
    val screenY: Float,
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
    horizontalPadding: Dp = 10.dp,
    pageGap: Dp = 10.dp,
) {
    val context = LocalContext.current
    val dm = context.resources.displayMetrics
    val scope = rememberCoroutineScope()
    val viewConfiguration = LocalViewConfiguration.current
    val density = LocalDensity.current

    val tapKeepPx = remember(density) {
        with(density) { 16.dp.toPx() }
    }

    val hPaddingDoc = remember(density, horizontalPadding) {
        with(density) { horizontalPadding.toPx() }
    }

    val gapDoc = remember(density, pageGap) {
        with(density) { pageGap.toPx() }
    }

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
        onDispose {
            decodeService.shutdown()
        }
    }

    var viewSize by remember {
        mutableStateOf(IntSize.Zero)
    }

    var zoom by remember {
        mutableFloatStateOf(
            initialZoom.toFloat().coerceIn(minZoom, maxZoom)
        )
    }

    var scrollOffset by remember {
        mutableStateOf(
            Offset(
                -initialScrollX.toFloat(),
                -initialScrollY.toFloat()
            )
        )
    }

    val clampedInitialPage =
        initialPage.coerceIn(0, max(0, pageCount - 1))

    var currentPage by remember {
        mutableIntStateOf(clampedInitialPage)
    }

    var lastHandledJumpPage by remember {
        mutableIntStateOf(-1)
    }

    var initializedForSize by remember {
        mutableStateOf(false)
    }

    var layoutTick by remember {
        mutableIntStateOf(0)
    }

    var flingJob by remember {
        mutableStateOf<Job?>(null)
    }

    /*
     * 这个状态非常重要。
     *
     * true:
     *   当前还处于 RecyclerView 式的 pending-anchor restore。
     *
     *   比如打开第 5 页：
     *
     *       targetPage = 5
     *       targetOffset = 0
     *       targetScreenY = 0
     *
     *   在第 5 页完成 reflow 之前，无论前面第 0~4 页高度
     *   怎么变化，都不能让这个 anchor 丢掉。
     */
    var restoringInitialPosition by remember {
        mutableStateOf(true)
    }

    var restoreAnchorPage by remember {
        mutableIntStateOf(clampedInitialPage)
    }

    val slots = remember(pageCount, currentPath) {
        Array(max(0, pageCount)) { idx ->
            PageSlot(pageIndex = idx)
        }
    }

    fun contentWidthDoc(viewWidthPx: Float): Float {
        return max(
            1f,
            viewWidthPx - hPaddingDoc * 2f
        )
    }

    fun defaultPageHeight(
        pageIndex: Int,
        contentW: Float
    ): Float {
        val orig = decoder.originalPageSizes
            .getOrNull(pageIndex)

        return if (
            orig != null &&
            orig.width > 0 &&
            orig.height > 0
        ) {
            contentW *
                (orig.height.toFloat() / orig.width.toFloat())
        } else {
            max(
                600f,
                contentW * 1.3f
            )
        }
    }

    /*
     * 注意：
     *
     * recomputeTops() 现在只负责：
     *
     *     height 已确定 -> 计算 top
     *
     * 不再在这里偷偷修改 height。
     *
     * 这是很重要的。
     *
     * 原来的 recomputeTops() 每次执行都会：
     *
     *     if (!slot.loaded) {
     *         slot.heightDoc = defaultPageHeight(...)
     *     }
     *
     * 这样布局计算本身也可能改变 layout state。
     *
     * 现在把“设置估算高度”和“计算 top”分开。
     */
    fun recomputeTops(
        contentW: Float
    ): Float {
        var y = gapDoc * 0.5f

        for (slot in slots) {
            slot.topDocY = y
            y += slot.heightDoc + gapDoc
        }

        return max(
            0f,
            y - gapDoc * 0.5f
        )
    }

    fun resetEstimatedHeights(
        contentW: Float
    ) {
        for (slot in slots) {
            if (!slot.loaded) {
                slot.heightDoc =
                    defaultPageHeight(
                        slot.pageIndex,
                        contentW
                    )
            }
        }
    }

    fun clampOffset(
        raw: Offset,
        currentZoom: Float,
        docWidth: Float,
        totalDocHeight: Float
    ): Offset {
        if (
            viewSize.width <= 0 ||
            viewSize.height <= 0 ||
            totalDocHeight <= 0f
        ) {
            return Offset.Zero
        }

        val contentW = docWidth * currentZoom
        val contentH = totalDocHeight * currentZoom

        val minX =
            if (contentW > viewSize.width) {
                viewSize.width - contentW
            } else {
                0f
            }

        val maxX =
            if (contentW > viewSize.width) {
                0f
            } else {
                (viewSize.width - contentW) / 2f
            }

        val clampedX = raw.x.coerceIn(
            min(minX, maxX),
            max(minX, maxX)
        )

        val minY =
            if (contentH > viewSize.height) {
                viewSize.height - contentH
            } else {
                min(
                    0f,
                    viewSize.height - contentH
                )
            }

        val maxY = 0f

        val clampedY = raw.y.coerceIn(
            min(minY, maxY),
            max(minY, maxY)
        )

        return Offset(
            clampedX,
            clampedY
        )
    }

    fun findFirstVisiblePage(
        currentOffset: Offset,
        currentZoom: Float
    ): Int {
        if (
            slots.isEmpty() ||
            currentZoom <= 0f
        ) {
            return 0
        }

        val topDoc =
            (-currentOffset.y / currentZoom)
                .coerceAtLeast(0f)

        for (slot in slots) {
            /*
             * gap 仍然归属于前一个 page，
             * 避免手指滚到 page gap 时 currentPage 来回抖。
             */
            if (
                topDoc <
                slot.topDocY +
                slot.heightDoc +
                gapDoc
            ) {
                return slot.pageIndex
            }
        }

        return slots.last().pageIndex
    }

    /*
     * 捕获“当前真实布局”的 anchor。
     *
     * 注意：
     * 必须在修改任何 page height 之前调用。
     */
    fun captureAnchor(): LayoutAnchor? {
        if (
            slots.isEmpty() ||
            zoom <= 0f
        ) {
            return null
        }

        val focusDocY =
            (-scrollOffset.y / zoom)
                .coerceAtLeast(0f)

        var anchorSlot =
            slots.last()

        for (slot in slots) {
            if (
                focusDocY <
                slot.topDocY +
                slot.heightDoc
            ) {
                anchorSlot = slot
                break
            }
        }

        val offsetDocY =
            (
                focusDocY -
                    anchorSlot.topDocY
                ).coerceIn(
                    0f,
                    max(0f, anchorSlot.heightDoc)
                )

        /*
         * 这里实际上就是 viewport top。
         *
         * 保留成真实计算，而不是硬编码 0，
         * 以后如果改成中心 anchor 也不用重写。
         */
        val screenY =
            focusDocY * zoom +
                scrollOffset.y

        return LayoutAnchor(
            pageIndex =
                anchorSlot.pageIndex,
            offsetDocY =
                offsetDocY,
            screenY =
                screenY
        )
    }

    /*
     * 核心：
     *
     * page height 发生变化时：
     *
     *     OLD layout
     *          ↓
     *     capture anchor
     *          ↓
     *     修改 page height
     *          ↓
     *     NEW layout
     *          ↓
     *     找回同一个 anchor
     *          ↓
     *     修正 scrollOffset
     *
     * 这才是真正的 RecyclerView 式 layout compensation。
     */
    fun applyPageHeight(
        pageIndex: Int,
        newHeight: Float
    ) {
        val slot =
            slots.getOrNull(pageIndex)
                ?: return

        if (
            viewSize.width <= 0 ||
            viewSize.height <= 0
        ) {
            slot.heightDoc = newHeight
            slot.loaded = true
            layoutTick++
            return
        }

        val docWidth =
            viewSize.width.toFloat()

        val contentW =
            contentWidthDoc(docWidth)

        /*
         * 最关键的一步：
         *
         * 必须在修改 slot.heightDoc 之前
         * 捕获旧布局的 anchor。
         *
         * 如果是 initial restore，
         * 则不能使用动态 visible page，
         * 而必须使用 pending restore page。
         */
        val anchor =
            if (restoringInitialPosition) {
                val target =
                    slots.getOrNull(
                        restoreAnchorPage
                    )

                if (target != null) {
                    LayoutAnchor(
                        pageIndex =
                            restoreAnchorPage,
                        offsetDocY = 0f,
                        screenY = 0f
                    )
                } else {
                    captureAnchor()
                }
            } else {
                captureAnchor()
            }

        val oldHeight =
            slot.heightDoc

        if (
            abs(newHeight - oldHeight) < 0.5f
        ) {
            slot.heightDoc = newHeight
            slot.loaded = true
            layoutTick++
            return
        }

        /*
         * 修改 page。
         */
        slot.heightDoc =
            max(1f, newHeight)

        slot.loaded = true

        /*
         * 新布局。
         */
        val newTotalH =
            recomputeTops(contentW)

        /*
         * 找回 OLD anchor 在 NEW layout
         * 中的位置。
         */
        if (anchor != null) {
            val anchorSlot =
                slots.getOrNull(
                    anchor.pageIndex
                )

            if (anchorSlot != null) {

                /*
                 * 不再做：
                 *
                 * offset * (newHeight / oldHeight)
                 *
                 * 那会把用户当前看到的位置
                 * 人为缩放掉。
                 *
                 * 对 anchor 来说，page 内 offset
                 * 必须保持不变。
                 */
                val maxOffset =
                    max(
                        0f,
                        anchorSlot.heightDoc
                    )

                val anchorOffset =
                    anchor.offsetDocY.coerceIn(
                        0f,
                        maxOffset
                    )

                val newAnchorDocY =
                    anchorSlot.topDocY +
                        anchorOffset

                val newScrollY =
                    anchor.screenY -
                        newAnchorDocY * zoom

                scrollOffset =
                    clampOffset(
                        Offset(
                            scrollOffset.x,
                            newScrollY
                        ),
                        zoom,
                        docWidth,
                        newTotalH
                    )
            } else {
                scrollOffset =
                    clampOffset(
                        scrollOffset,
                        zoom,
                        docWidth,
                        newTotalH
                    )
            }
        } else {
            scrollOffset =
                clampOffset(
                    scrollOffset,
                    zoom,
                    docWidth,
                    newTotalH
                )
        }

        /*
         * initial restore：
         *
         * target page 一旦完成真正的 reflow，
         * 就可以结束 pending-anchor 状态。
         *
         * 之后就进入正常 RecyclerView 式
         * visible anchor 模式。
         */
        if (
            restoringInitialPosition &&
            pageIndex == restoreAnchorPage &&
            slot.loaded
        ) {
            restoringInitialPosition = false
        }

        layoutTick++
    }

    /*
     * 字号 / 字间距变化：
     *
     * 所有 page 的真实高度失效。
     * 重新变成 estimate。
     */
    LaunchedEffect(
        fontSize,
        wordSpacing,
        currentPath
    ) {
        val docWidth =
            viewSize.width.toFloat()

        val contentW =
            contentWidthDoc(
                max(1f, docWidth)
            )

        for (slot in slots) {
            slot.tiles = emptyList()
            slot.loaded = false
            slot.loading = false
            slot.heightDoc =
                defaultPageHeight(
                    slot.pageIndex,
                    contentW
                )
        }

        /*
         * 参数改变后重新进入 pending anchor。
         *
         * 这里使用当前页，而不是初始页。
         */
        restoreAnchorPage =
            currentPage.coerceIn(
                0,
                max(0, pageCount - 1)
            )

        restoringInitialPosition = true

        layoutTick++
    }

    /*
     * 初始布局 / 外部 jump。
     *
     * 这里完全采用 RecyclerView 的
     * pending scroll position 思路。
     */
    LaunchedEffect(
        viewSize,
        jumpToPage
    ) {
        if (
            viewSize.width <= 0 ||
            viewSize.height <= 0 ||
            slots.isEmpty()
        ) {
            return@LaunchedEffect
        }

        val docWidth =
            viewSize.width.toFloat()

        val contentW =
            contentWidthDoc(docWidth)

        resetEstimatedHeights(contentW)

        val totalH =
            recomputeTops(contentW)

        if (!initializedForSize) {

            initializedForSize = true

            val targetIdx =
                clampedInitialPage

            restoreAnchorPage =
                targetIdx

            restoringInitialPosition =
                true

            lastHandledJumpPage =
                jumpToPage ?: targetIdx

            val targetTop =
                max(
                    0f,
                    slots[targetIdx].topDocY -
                        gapDoc * 0.5f
                )

            /*
             * pending anchor：
             *
             * page targetIdx 的顶部
             * 对齐 viewport top。
             */
            scrollOffset =
                clampOffset(
                    Offset(
                        0f,
                        -targetTop * zoom
                    ),
                    zoom,
                    docWidth,
                    totalH
                )

            currentPage =
                targetIdx

            onPageChanged?.invoke(
                targetIdx
            )

            layoutTick++

        } else if (
            jumpToPage != null &&
            jumpToPage != lastHandledJumpPage &&
            jumpToPage in 0 until pageCount
        ) {

            val targetIdx =
                jumpToPage

            lastHandledJumpPage =
                targetIdx

            restoreAnchorPage =
                targetIdx

            restoringInitialPosition =
                true

            val targetTop =
                max(
                    0f,
                    slots[targetIdx].topDocY -
                        gapDoc * 0.5f
                )

            scrollOffset =
                clampOffset(
                    Offset(
                        0f,
                        -targetTop * zoom
                    ),
                    zoom,
                    docWidth,
                    totalH
                )

            currentPage =
                targetIdx

            onPageChanged?.invoke(
                targetIdx
            )

            layoutTick++
        }
    }

    /*
     * 滚动 / 缩放 / 页面高度变化。
     */
    LaunchedEffect(
        scrollOffset,
        zoom,
        viewSize,
        fontSize,
        wordSpacing,
        layoutTick
    ) {
        if (
            viewSize.width <= 0 ||
            viewSize.height <= 0 ||
            slots.isEmpty()
        ) {
            return@LaunchedEffect
        }

        val docWidth =
            viewSize.width.toFloat()

        val contentW =
            contentWidthDoc(docWidth)

        resetEstimatedHeights(contentW)

        recomputeTops(contentW)

        /*
         * 非 restore 状态下，
         * currentPage 才允许由真实 viewport
         * 决定。
         *
         * restore 状态下绝对禁止：
         *
         *     page 5 -> page 4 -> page 3
         *
         * 这种 transient 状态向外传播。
         */
        if (!restoringInitialPosition) {
            val visPage =
                findFirstVisiblePage(
                    scrollOffset,
                    zoom
                )

            if (visPage != currentPage) {
                currentPage =
                    visPage

                onPageChanged?.invoke(
                    visPage
                )
            }
        }

        /*
         * restore 时必须优先保证 target page
         * 以及它附近的 page 被加载。
         *
         * 正常滚动则按照 viewport 加载。
         */
        val basePage =
            if (restoringInitialPosition) {
                restoreAnchorPage
            } else {
                findFirstVisiblePage(
                    scrollOffset,
                    zoom
                )
            }

        val startIdx =
            max(
                0,
                basePage - 1
            )

        val endIdx =
            min(
                slots.lastIndex,
                basePage + 2
            )

        if (startIdx <= endIdx) {
            for (idx in startIdx..endIdx) {

                val slot =
                    slots[idx]

                if (
                    !slot.loaded &&
                    !slot.loading
                ) {
                    slot.loading =
                        true

                    scope.launch {

                        val tiles =
                            decodeService.requestPage(
                                idx,
                                fontSize,
                                wordSpacing
                            )

                        slot.loading =
                            false

                        slot.tiles =
                            tiles

                        val totalTileH =
                            tiles.sumOf {
                                it.height
                            }.toFloat()

                        val tileW =
                            tiles.firstOrNull()
                                ?.width
                                ?.toFloat()
                                ?: 0f

                        val newHeight =
                            if (
                                tiles.isNotEmpty() &&
                                tileW > 0f &&
                                totalTileH > 0f
                            ) {
                                contentW *
                                    (totalTileH / tileW)
                            } else {
                                defaultPageHeight(
                                    idx,
                                    contentW
                                )
                            }

                        applyPageHeight(
                            idx,
                            newHeight
                        )
                    }
                }
            }
        }
    }

    fun saveState() {
        val safeZoom =
            max(0.01f, zoom)

        val docX =
            (-scrollOffset.x / safeZoom)
                .toLong()
                .coerceAtLeast(0L)

        val docY =
            (-scrollOffset.y / safeZoom)
                .toLong()
                .coerceAtLeast(0L)

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

    val lifecycleOwner =
        LocalLifecycleOwner.current

    DisposableEffect(
        lifecycleOwner
    ) {
        val observer =
            LifecycleEventObserver {
                    _, event ->
                if (
                    event ==
                    Lifecycle.Event.ON_PAUSE
                ) {
                    saveState()
                }
            }

        lifecycleOwner.lifecycle
            .addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle
                .removeObserver(observer)

            saveState()
        }
    }

    var lastTapTime by remember {
        mutableStateOf(0L)
    }

    var tapJob by remember {
        mutableStateOf<Job?>(null)
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .onSizeChanged {
                viewSize = it
            }
            .pointerInput(
                viewSize,
                minZoom,
                maxZoom,
                tapKeepPx
            ) {
                val touchSlop =
                    viewConfiguration.touchSlop

                awaitEachGesture {

                    val velocityTracker =
                        VelocityTracker()

                    var isZooming =
                        false

                    var isDragging =
                        false

                    var totalPan =
                        Offset.Zero

                    var lastChange:
                        PointerInputChange? =
                        null

                    val down =
                        awaitFirstDown(
                            requireUnconsumed = false
                        )

                    val wasFlinging =
                        flingJob?.isActive == true

                    tapJob?.cancel()
                    flingJob?.cancel()

                    /*
                     * 一旦用户真正开始操作，
                     * 就退出 pending restore anchor。
                     *
                     * 之后使用真实 viewport anchor。
                     */
                    fun beginUserScroll() {
                        if (
                            restoringInitialPosition
                        ) {
                            restoringInitialPosition =
                                false

                            restoreAnchorPage =
                                currentPage
                        }
                    }

                    do {
                        val event =
                            awaitPointerEvent()

                        if (
                            !event.changes.fastAny {
                                it.isConsumed
                            }
                        ) {
                            lastChange =
                                event.changes
                                    .firstOrNull()

                            val zoomChange =
                                event.calculateZoom()

                            val panChange =
                                event.calculatePan()

                            if (
                                !isZooming &&
                                event.changes.size > 1
                            ) {
                                isZooming =
                                    true

                                /*
                                 * pinch 也意味着用户主动
                                 * 接管 viewport。
                                 */
                                beginUserScroll()
                            }

                            val docWidth =
                                max(
                                    1f,
                                    size.width.toFloat()
                                )

                            val contentW =
                                contentWidthDoc(
                                    docWidth
                                )

                            val totalH =
                                recomputeTops(
                                    contentW
                                )

                            if (isZooming) {

                                val centroid =
                                    event.calculateCentroid(
                                        useCurrent = false
                                    )

                                if (
                                    centroid.isSpecified &&
                                    zoomChange != 1f
                                ) {

                                    val oldZoom =
                                        zoom

                                    val newZoom =
                                        (
                                            oldZoom *
                                                zoomChange
                                            ).coerceIn(
                                                minZoom,
                                                maxZoom
                                            )

                                    if (
                                        newZoom !=
                                        oldZoom
                                    ) {

                                        val docX =
                                            (
                                                centroid.x -
                                                    scrollOffset.x
                                                ) /
                                                oldZoom

                                        val docY =
                                            (
                                                centroid.y -
                                                    scrollOffset.y
                                                ) /
                                                oldZoom

                                        val nextOffset =
                                            Offset(
                                                centroid.x -
                                                    docX *
                                                        newZoom,
                                                centroid.y -
                                                    docY *
                                                        newZoom
                                            )

                                        zoom =
                                            newZoom

                                        scrollOffset =
                                            clampOffset(
                                                nextOffset,
                                                newZoom,
                                                docWidth,
                                                totalH
                                            )
                                    }
                                }

                            } else {

                                totalPan +=
                                    panChange

                                val delta =
                                    when {

                                        !isDragging &&
                                            totalPan
                                                .getDistance() >
                                            touchSlop -> {

                                            isDragging =
                                                true

                                            beginUserScroll()

                                            tapJob?.cancel()
                                            lastTapTime = 0L

                                            totalPan
                                        }

                                        isDragging -> {
                                            panChange
                                        }

                                        else -> {
                                            Offset.Zero
                                        }
                                    }

                                if (
                                    delta !=
                                    Offset.Zero
                                ) {

                                    beginUserScroll()

                                    scrollOffset =
                                        clampOffset(
                                            scrollOffset +
                                                delta,
                                            zoom,
                                            docWidth,
                                            totalH
                                        )

                                    velocityTracker
                                        .addPosition(
                                            lastChange
                                                ?.uptimeMillis
                                                ?: 0L,
                                            lastChange
                                                ?.position
                                                ?: Offset.Zero
                                        )
                                }
                            }

                            event.changes.fastForEach {
                                if (
                                    it.positionChanged()
                                ) {
                                    it.consume()
                                }
                            }
                        }

                    } while (
                        event.changes.fastAny {
                            it.pressed
                        }
                    )

                    val finalChange =
                        lastChange
                            ?: return@awaitEachGesture

                    val docWidth =
                        max(
                            1f,
                            size.width.toFloat()
                        )

                    val contentW =
                        contentWidthDoc(
                            docWidth
                        )

                    val totalH =
                        recomputeTops(
                            contentW
                        )

                    if (!isZooming) {

                        val dragDist =
                            (
                                finalChange.position -
                                    down.position
                                ).getDistance()

                        if (
                            !isDragging &&
                            !wasFlinging &&
                            dragDist < touchSlop
                        ) {

                            val tapPos =
                                finalChange.position

                            val now =
                                System.currentTimeMillis()

                            if (
                                now -
                                    lastTapTime <
                                300L
                            ) {

                                tapJob?.cancel()
                                lastTapTime = 0L

                                val h =
                                    size.height.toFloat()

                                if (
                                    tapPos.y in
                                    (h / 4f)..
                                    (h * 3f / 4f)
                                ) {
                                    onDoubleTapToolbar
                                        ?.invoke()
                                }

                            } else {

                                lastTapTime =
                                    now

                                tapJob?.cancel()

                                tapJob =
                                    scope.launch {

                                        delay(
                                            300L
                                        )

                                        val h =
                                            size.height
                                                .toFloat()

                                        when {

                                            tapPos.y <
                                                h / 4f -> {

                                                beginUserScroll()

                                                val step =
                                                    max(
                                                        0f,
                                                        h -
                                                            tapKeepPx
                                                    )

                                                scrollOffset =
                                                    clampOffset(
                                                        Offset(
                                                            scrollOffset.x,
                                                            scrollOffset.y +
                                                                step
                                                        ),
                                                        zoom,
                                                        docWidth,
                                                        totalH
                                                    )
                                            }

                                            tapPos.y >
                                                h * 3f / 4f -> {

                                                beginUserScroll()

                                                val step =
                                                    max(
                                                        0f,
                                                        h -
                                                            tapKeepPx
                                                    )

                                                scrollOffset =
                                                    clampOffset(
                                                        Offset(
                                                            scrollOffset.x,
                                                            scrollOffset.y -
                                                                step
                                                        ),
                                                        zoom,
                                                        docWidth,
                                                        totalH
                                                    )
                                            }

                                            else -> {

                                                onTapNonPageArea
                                                    ?.invoke(
                                                        currentPage
                                                    )
                                            }
                                        }
                                    }
                            }

                        } else if (
                            isDragging
                        ) {

                            beginUserScroll()

                            val vel =
                                velocityTracker
                                    .calculateVelocity()

                            val decay =
                                exponentialDecay<Offset>(
                                    frictionMultiplier =
                                        0.35f,
                                    absVelocityThreshold =
                                        0.50f
                                )

                            val scaledVel =
                                Offset(
                                    vel.x * 1.1f,
                                    vel.y * 1.1f
                                )

                            flingJob =
                                scope.launch {

                                    val animState =
                                        AnimationState(
                                            typeConverter =
                                                ReflowOffsetToVector,
                                            initialValue =
                                                scrollOffset,
                                            initialVelocity =
                                                scaledVel
                                        )

                                    animState.animateDecay(
                                        decay
                                    ) {

                                        val bounded =
                                            clampOffset(
                                                value,
                                                zoom,
                                                docWidth,
                                                totalH
                                            )

                                        scrollOffset =
                                            bounded

                                        val v =
                                            this.velocity

                                        val blockedX =
                                            v.x == 0f ||
                                                v.x *
                                                (value.x -
                                                    bounded.x) >
                                                0f

                                        val blockedY =
                                            v.y == 0f ||
                                                v.y *
                                                (value.y -
                                                    bounded.y) >
                                                0f

                                        if (
                                            blockedX &&
                                            blockedY
                                        ) {
                                            cancelAnimation()
                                        }
                                    }
                                }
                        }
                    }
                }
            }
    ) {

        if (
            size.width <= 0 ||
            size.height <= 0 ||
            slots.isEmpty()
        ) {
            return@Canvas
        }

        @Suppress("UNUSED_EXPRESSION")
        layoutTick

        val docWidth =
            size.width.toFloat()

        val contentW =
            contentWidthDoc(
                docWidth
            )

        recomputeTops(
            contentW
        )

        val visTopDoc =
            -scrollOffset.y /
                zoom

        val visBottomDoc =
            (
                -scrollOffset.y +
                    size.height
                ) /
                zoom

        for (slot in slots) {

            val itemBottomDoc =
                slot.topDocY +
                    slot.heightDoc

            if (
                itemBottomDoc <
                    visTopDoc ||
                slot.topDocY >
                    visBottomDoc
            ) {
                continue
            }

            val leftPx =
                (
                    scrollOffset.x +
                        hPaddingDoc *
                        zoom
                    ).roundToInt()

            val rightPx =
                (
                    scrollOffset.x +
                        (hPaddingDoc +
                            contentW) *
                        zoom
                    ).roundToInt()

            if (
                slot.tiles.isNotEmpty()
            ) {

                var tileTopDoc =
                    slot.topDocY

                for (
                    bmp in slot.tiles
                ) {

                    val bmpW =
                        max(
                            1,
                            bmp.width
                        ).toFloat()

                    val bmpH =
                        max(
                            1,
                            bmp.height
                        ).toFloat()

                    val tileDocH =
                        contentW *
                            (bmpH / bmpW)

                    val topPx =
                        (
                            scrollOffset.y +
                                tileTopDoc *
                                zoom
                            ).roundToInt()

                    val bottomPx =
                        (
                            scrollOffset.y +
                                (
                                    tileTopDoc +
                                        tileDocH
                                    ) *
                                    zoom
                            ).roundToInt()

                    if (
                        bottomPx >= 0 &&
                        topPx <= size.height
                    ) {

                        drawImage(
                            image =
                                bmp.asImageBitmap(),

                            dstOffset =
                                IntOffset(
                                    leftPx,
                                    topPx
                                ),

                            dstSize =
                                IntSize(
                                    max(
                                        1,
                                        rightPx -
                                            leftPx
                                    ),
                                    max(
                                        1,
                                        bottomPx -
                                            topPx
                                    )
                                )
                        )
                    }

                    tileTopDoc +=
                        tileDocH
                }

            } else {

                val topPx =
                    (
                        scrollOffset.y +
                            slot.topDocY *
                            zoom
                        ).roundToInt()

                val bottomPx =
                    (
                        scrollOffset.y +
                            itemBottomDoc *
                            zoom
                    ).roundToInt()

                drawRect(
                    color =
                        Color(0xFFF6F6F6),

                    topLeft =
                        Offset(
                            leftPx.toFloat(),
                            topPx.toFloat()
                        ),

                    size =
                        Size(
                            max(
                                1,
                                rightPx -
                                    leftPx
                            ).toFloat(),

                            max(
                                1,
                                bottomPx -
                                    topPx
                            ).toFloat()
                        )
                )
            }

            val divTopDoc =
                slot.topDocY +
                    slot.heightDoc

            val divTopPx =
                scrollOffset.y +
                    divTopDoc *
                    zoom

            val divBottomPx =
                scrollOffset.y +
                    (
                        divTopDoc +
                            gapDoc
                        ) *
                        zoom

            if (
                divBottomPx >= 0f &&
                divTopPx <= size.height
            ) {

                val lineY =
                    (
                        divTopPx +
                            divBottomPx
                        ) *
                        0.5f

                drawLine(
                    color =
                        Color(0xFF888888),

                    start =
                        Offset(
                            leftPx
                                .toFloat()
                                .coerceAtLeast(
                                    0f
                                ),
                            lineY
                        ),

                    end =
                        Offset(
                            rightPx
                                .toFloat()
                                .coerceAtMost(
                                    size.width
                                        .toFloat()
                                ),
                            lineY
                        ),

                    strokeWidth =
                        1.dp.toPx()
                )
            }
        }
    }
}


private val ReflowOffsetToVector:
    TwoWayConverter<
        Offset,
        AnimationVector2D
        > =
    TwoWayConverter(
        convertFromVector = {
            Offset(
                it.v1,
                it.v2
            )
        },

        convertToVector = {
            AnimationVector2D(
                it.x,
                it.y
            )
        }
    )