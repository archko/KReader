package com.archko.reader.pdf.component

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.APage
import com.archko.reader.pdf.entity.CropMode
import com.archko.reader.pdf.state.AnnotationManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

private const val PAGER_MIN_ZOOM = 1f
private const val PAGER_MAX_ZOOM = 8f

private val PagerOffsetToVector: TwoWayConverter<Offset, AnimationVector2D> = TwoWayConverter(
    convertFromVector = { Offset(it.v1, it.v2) },
    convertToVector = { AnimationVector2D(it.x, it.y) }
)

/**
 * 根据页面列表和视口宽度，计算第 pageIndex 页顶部在文档坐标系中的累积 Y 偏移。
 * 与 PageViewState.computeRects() 竖向模式下的算法一致：
 *   pageHeight = viewWidth * page.height / page.width
 * 这样切回连续滚动模式时 offsetForDoc 能精准还原位置。
 */
internal fun calcDocScrollY(list: List<APage>, pageIndex: Int, viewWidth: Int): Long {
    if (viewWidth <= 0 || pageIndex <= 0) return 0L
    var y = 0f
    val count = pageIndex.coerceAtMost(list.size)
    for (i in 0 until count) {
        val page = list[i]
        if (page.width > 0) {
            y += viewWidth.toFloat() * page.height / page.width
        }
    }
    return y.toLong()
}

/**
 * 单页 ViewPager 文档视图。
 *
 * 每次只显示一页，左右滑动翻页（HorizontalPager 吸附动画）。
 * 在页面内支持捏合缩放 + 平移，翻页时缩放和偏移自动重置。
 * 不支持切边，不支持多列，不支持文字选择/绘图。
 */
@Composable
public fun PagerDocumentView(
    list: MutableList<APage>,
    decoder: ImageDecoder,
    initialPage: Int = 0,
    jumpToPage: Int? = null,
    onPageChanged: ((page: Int) -> Unit)? = null,
    onTapCenter: (() -> Unit)? = null,
    cropMode: Int = CropMode.NONE,
    onSaveDocument: ((page: Int, pageCount: Int, zoom: Double, scrollX: Long, scrollY: Long, scrollOri: Long, reflow: Long, crop: Long) -> Unit)? = null,
) {
    val pageCount = list.size
    if (pageCount == 0) return

    val pagerState: PagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, pageCount - 1),
        pageCount = { pageCount }
    )

    // 记录当前视口宽度，用于计算 scrollY
    var viewWidth by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    // 放大状态下禁止 HorizontalPager 自己处理手势，避免捏合时误翻页
    var zoomedPageIndex by remember { mutableStateOf(-1) }

    // 通知外层页码变化
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            onPageChanged?.invoke(page)
        }
    }

    // 响应外部翻页请求（底部工具栏上/下一页按钮）
    LaunchedEffect(jumpToPage) {
        if (jumpToPage != null && jumpToPage != pagerState.currentPage) {
            pagerState.animateScrollToPage(jumpToPage.coerceIn(0, pageCount - 1))
        }
    }

    // 页面稳定后保存进度（settlement = 翻页动画结束）
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (onSaveDocument != null && viewWidth > 0) {
                val scrollY = calcDocScrollY(list, page, viewWidth)
                onSaveDocument.invoke(
                    page,
                    pageCount,
                    1.0,
                    0L,          // scrollX：竖向布局 X 始终 0
                    scrollY,     // 前面各页累积高度 → 切回连续模式可精准恢复
                    Vertical.toLong(),
                    0L,          // reflow
                    cropMode.toLong()
                )
            }
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewWidth = it.width },
        beyondViewportPageCount = 1,
        key = { index -> index },
        userScrollEnabled = zoomedPageIndex < 0
    ) { pageIndex ->
        PagerPage(
            aPage = list[pageIndex],
            pageIndex = pageIndex,
            decoder = decoder,
            isSettledPage = pagerState.settledPage == pageIndex,
            onZoomedChanged = { zoomed ->
                if (zoomed) {
                    zoomedPageIndex = pageIndex
                } else if (zoomedPageIndex == pageIndex) {
                    zoomedPageIndex = -1
                }
            },
            onPageTurn = { direction ->
                val target = (pageIndex + direction).coerceIn(0, pageCount - 1)
                if (target != pageIndex) {
                    scope.launch { pagerState.animateScrollToPage(target) }
                }
            },
            onTapCenter = onTapCenter,
        )
    }
}

/**
 * 单页内容：支持捏合缩放 + 平移，通过独立的 PageViewState 管理 tile 解码。
 */
@Composable
private fun PagerPage(
    aPage: APage,
    pageIndex: Int,
    decoder: ImageDecoder,
    isSettledPage: Boolean,
    onZoomedChanged: (Boolean) -> Unit,
    onPageTurn: (direction: Int) -> Unit,
    onTapCenter: (() -> Unit)?,
) {
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()
    var flingJob by remember { mutableStateOf<Job?>(null) }

    val currentOnPageTurn by rememberUpdatedState(onPageTurn)
    val currentOnZoomedChanged by rememberUpdatedState(onZoomedChanged)
    val currentOnTapCenter by rememberUpdatedState(onTapCenter)

    // 每页独立的 PageViewState（不切边，单页居中适配）
    val pageViewState = remember(pageIndex) {
        PageViewState(
            list = mutableListOf(aPage),
            state = decoder,
            annotationManager = AnnotationManager(""),
            orientation = Vertical,
            cropMode = CropMode.NONE,
            columnCount = 1,
            textSelector = null,
            closeDecoderOnShutdown = false,
            fitPageToViewport = true
        )
    }

    // 当前 zoom 下允许的最大平移量（以页面实际显示尺寸计算）
    fun maxPanOffset(): Offset {
        if (viewSize.width <= 0 || viewSize.height <= 0) {
            return Offset.Zero
        }
        val pageWidth = aPage.width.toFloat().coerceAtLeast(1f)
        val pageHeight = aPage.height.toFloat().coerceAtLeast(1f)
        val fitScale = min(
            viewSize.width.toFloat() / pageWidth,
            viewSize.height.toFloat() / pageHeight
        )
        return Offset(
            ((pageWidth * fitScale * zoom - viewSize.width) / 2f).coerceAtLeast(0f),
            ((pageHeight * fitScale * zoom - viewSize.height) / 2f).coerceAtLeast(0f)
        )
    }

    fun startPanFling(velocity: Velocity) {
        if (zoom <= 1f || (abs(velocity.x) < 50f && abs(velocity.y) < 50f)) {
            return
        }
        val bounds = maxPanOffset()
        if (bounds.x <= 0f && bounds.y <= 0f) {
            return
        }
        flingJob?.cancel()
        val flingZoom = zoom
        val startPan = panOffset
        flingJob = scope.launch {
            val animationState = AnimationState(
                typeConverter = PagerOffsetToVector,
                initialValue = startPan,
                initialVelocity = Offset(velocity.x, velocity.y)
            )
            try {
                animationState.animateDecay(
                    exponentialDecay(frictionMultiplier = 0.35f, absVelocityThreshold = 50f)
                ) {
                    val bounded = Offset(
                        value.x.coerceIn(-bounds.x, bounds.x),
                        value.y.coerceIn(-bounds.y, bounds.y)
                    )
                    panOffset = bounded
                    pageViewState.updateViewport(
                        flingZoom,
                        Offset(
                            viewSize.width * (1f - flingZoom) / 2f + bounded.x,
                            viewSize.height * (1f - flingZoom) / 2f + bounded.y
                        ),
                        true
                    )
                    val current = this.velocity
                    val blockedX = bounds.x <= 0f || current.x == 0f ||
                        current.x * (value.x - bounded.x) > 0f
                    val blockedY = bounds.y <= 0f || current.y == 0f ||
                        current.y * (value.y - bounded.y) > 0f
                    if (blockedX && blockedY) {
                        cancelAnimation()
                    }
                }
            } finally {
                pageViewState.updateViewport(
                    flingZoom,
                    Offset(
                        viewSize.width * (1f - flingZoom) / 2f + panOffset.x,
                        viewSize.height * (1f - flingZoom) / 2f + panOffset.y
                    ),
                    true
                )
            }
        }
    }

    var lastTapTime by remember { mutableStateOf(0L) }
    var tapJob by remember { mutableStateOf<Job?>(null) }

    fun handleTap() {
        val now = System.currentTimeMillis()
        if (now - lastTapTime < 300L) {
            // 双击：1x -> 2x -> 4x -> 1x
            tapJob?.cancel()
            lastTapTime = 0L
            zoom = when {
                zoom < 1.5f -> 2f
                zoom < 3f -> 4f
                else -> 1f
            }
            panOffset = Offset.Zero
        } else {
            // 单击：延迟执行，避免双击时误触发工具栏
            lastTapTime = now
            tapJob?.cancel()
            tapJob = scope.launch {
                delay(300)
                currentOnTapCenter?.invoke()
            }
        }
    }

    DisposableEffect(pageIndex) {
        onDispose {
            pageViewState.shutdown()
            currentOnZoomedChanged(false)
        }
    }

    // 放大时禁用 Pager 自身的手势，完全由缩放/平移逻辑接管
    val isZoomed = zoom > 1f
    SideEffect(isZoomed) {
        currentOnZoomedChanged(isZoomed)
    }

    // 视口或 zoom 变化时通知 PageViewState 重新布局
    SideEffect(viewSize, zoom) {
        if (viewSize.width > 0 && viewSize.height > 0) {
            // 缩放后重新限制平移范围，缩小到 1x 时自动回到居中
            val maxPan = maxPanOffset()
            val clampedPan = Offset(
                panOffset.x.coerceIn(-maxPan.x, maxPan.x),
                panOffset.y.coerceIn(-maxPan.y, maxPan.y)
            )
            if (clampedPan != panOffset) {
                panOffset = clampedPan
            }
            pageViewState.updateViewSize(viewSize, zoom, Vertical)
            pageViewState.updateViewport(
                zoom,
                Offset(
                    viewSize.width * (1f - zoom) / 2f + clampedPan.x,
                    viewSize.height * (1f - zoom) / 2f + clampedPan.y
                ),
                true
            )
        }
    }

    // 翻页动画结束后重置非当前页的缩放和平移
    SideEffect(isSettledPage) {
        if (!isSettledPage) {
            zoom = 1f
            panOffset = Offset.Zero
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color.Black)
            .onSizeChanged { size ->
                viewSize = size
            }
    ) {
        PagerPageCanvas(
            pageViewState = pageViewState,
            zoom = zoom,
            panOffset = panOffset,
            modifier = Modifier
                .fillMaxSize()
                .pagerGestures(
                    currentZoom = { zoom },
                    onZoomChange = { newZoom ->
                        zoom = newZoom.coerceIn(PAGER_MIN_ZOOM, PAGER_MAX_ZOOM)
                    },
                    onPan = { delta ->
                        if (zoom <= 1f || viewSize.width <= 0 || viewSize.height <= 0) {
                            Offset.Zero
                        } else {
                            val maxPan = maxPanOffset()
                            val oldPan = panOffset
                            val newPan = Offset(
                                (oldPan.x + delta.x).coerceIn(-maxPan.x, maxPan.x),
                                (oldPan.y + delta.y).coerceIn(-maxPan.y, maxPan.y)
                            )
                            panOffset = newPan
                            // 平移时同步视口，保证新露出的区域能请求到清晰 tile
                            pageViewState.updateViewport(
                                zoom,
                                Offset(
                                    viewSize.width * (1f - zoom) / 2f + newPan.x,
                                    viewSize.height * (1f - zoom) / 2f + newPan.y
                                ),
                                true
                            )
                            val applied = newPan - oldPan
                            applied
                        }
                    },
                    onGestureStart = { flingJob?.cancel() },
                    onFling = { velocity -> startPanFling(velocity) },
                    onPageTurn = { direction -> currentOnPageTurn(direction) },
                    onTapCenter = { handleTap() },
                )
        )
    }
}

/**
 * 用 Canvas 绘制单页 tile，graphicsLayer 做整体缩放 + 平移。
 */
@Composable
private fun PagerPageCanvas(
    pageViewState: PageViewState,
    zoom: Float,
    panOffset: Offset,
    modifier: Modifier,
) {
    androidx.compose.foundation.Canvas(
        modifier = modifier.graphicsLayer {
            scaleX = zoom
            scaleY = zoom
            // 与 PageViewState 的视口使用同一套变换：screen = doc * zoom + offset
            transformOrigin = TransformOrigin(0f, 0f)
            translationX = size.width * (1f - zoom) / 2f + panOffset.x
            translationY = size.height * (1f - zoom) / 2f + panOffset.y
        }
    ) {
        // 在绘制阶段订阅 revision 与 tile bitmap，解码完成时能直接触发重绘
        pageViewState.revision
        pageViewState.drawVisiblePages(this)
    }
}

/**
 * 捏合缩放 + 单指平移（zoom > 1 时）+ 惯性滑动 + 中心区单击。
 * zoom <= 1 时让事件透传给 HorizontalPager 翻页；
 * zoom > 1 时先平移内容，拖到水平边缘后继续拖动（或快速甩动）则翻到上/下一页。
 */
private fun Modifier.pagerGestures(
    currentZoom: () -> Float,
    onZoomChange: (Float) -> Unit,
    onPan: (Offset) -> Offset,
    onGestureStart: () -> Unit,
    onFling: (Velocity) -> Unit,
    onPageTurn: (direction: Int) -> Unit,
    onTapCenter: (() -> Unit)?,
): Modifier = this.pointerInput(Unit) {
    val turnThreshold = 48.dp.toPx()
    val minFlingVelocity = 350.dp.toPx()
    awaitEachGesture {
        var isZooming = false
        var turnTriggered = false
        var pageTurnDirection = 0
        var overscrollX = 0f
        var atLeftEdge = false
        var atRightEdge = false

        val down = awaitFirstDown(requireUnconsumed = false)
        onGestureStart()
        val velocityTracker = VelocityTracker()
        velocityTracker.addPosition(down.uptimeMillis, down.position)
        var lastPos = down.position

        do {
            val event = awaitPointerEvent()
            event.changes.forEach { velocityTracker.addPosition(it.uptimeMillis, it.position) }
            val pointers = event.changes.filter { it.pressed }

            when {
                pointers.size >= 2 -> {
                    isZooming = true
                    val factor = event.calculateZoom()
                    if (factor != 1f) {
                        // calculateZoom 是增量比例，必须基于当前 zoom 累乘
                        onZoomChange(currentZoom() * factor)
                    }
                    // 多指手势全部消费，避免 HorizontalPager 把捏合识别成翻页
                    event.changes.forEach { it.consume() }
                }
                pointers.size == 1 && !isZooming && currentZoom() > 1f -> {
                    // 放大状态：先平移，平移已到边缘时继续拖动则翻页
                    val change = pointers[0]
                    val delta = change.position - lastPos
                    lastPos = change.position
                    if (delta != Offset.Zero) {
                        val applied = onPan(delta)
                        change.consume()
                        val overflowX = delta.x - applied.x
                        if (abs(delta.x) >= abs(delta.y) && abs(overflowX) > 0.5f) {
                            if (overflowX > 0f) {
                                atLeftEdge = true
                                atRightEdge = false
                            } else {
                                atLeftEdge = false
                                atRightEdge = true
                            }
                            if (overflowX * overscrollX < 0f) {
                                overscrollX = 0f
                            }
                            overscrollX += overflowX
                            val candidate = when {
                                overscrollX > turnThreshold -> -1
                                overscrollX < -turnThreshold -> 1
                                else -> 0
                            }
                            if (candidate != 0 && !turnTriggered) {
                                // 到边缘后继续拖动立即翻页，不等手指抬起
                                turnTriggered = true
                                pageTurnDirection = candidate
                                onPageTurn(candidate)
                            }
                        } else {
                            overscrollX = 0f
                            pageTurnDirection = 0
                            atLeftEdge = false
                            atRightEdge = false
                        }
                    }
                }
                pointers.size == 1 && isZooming -> {
                    // 捏合结束剩下一根手指时，不要把手势交还给 Pager
                    lastPos = pointers[0].position
                    pointers[0].consume()
                }
                pointers.size == 1 -> {
                    lastPos = pointers[0].position
                }
            }
        } while (event.changes.fastAny { it.pressed })

        val velocity = velocityTracker.calculateVelocity()
        if (!isZooming && !turnTriggered) {
            if (pageTurnDirection == 0 && currentZoom() > 1f) {
                // 到达水平边缘后快速甩动也能切页，不需要拖满阈值
                if (velocity.x > minFlingVelocity && atLeftEdge) {
                    pageTurnDirection = -1
                } else if (velocity.x < -minFlingVelocity && atRightEdge) {
                    pageTurnDirection = 1
                }
            }
            if (pageTurnDirection != 0) {
                onPageTurn(pageTurnDirection)
            } else if (currentZoom() > 1f) {
                onFling(velocity)
            }
        }

        // 单击判断：没有缩放操作，且移动距离 < 20px
        val moved = (lastPos - down.position).getDistanceSquared() < 400f
        if (!isZooming && moved && pageTurnDirection == 0) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val tx = down.position.x
            val ty = down.position.y
            if (tx in (cx * 0.3f)..(cx * 1.7f) && ty in (cy * 0.3f)..(cy * 1.7f)) {
                onTapCenter?.invoke()
            }
        }
    }
}
