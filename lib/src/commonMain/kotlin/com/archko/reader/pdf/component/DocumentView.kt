package com.archko.reader.pdf.component

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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.APage
import com.archko.reader.pdf.entity.CropMode
import com.archko.reader.pdf.entity.DocQuad
import com.archko.reader.pdf.state.AnnotationManager
import com.archko.reader.pdf.util.HyperLinkUtils
import com.archko.reader.pdf.util.ViewUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val max_zoom = 30f

// 点击翻页时保留的上下文高度
private val tap_page_keep_dp = 12.dp

// 平移/惯性过程中重建 tile 的最小间隔，避免每帧都做完整重建
private const val tile_refresh_interval_ms = 100L

/**
 * 文档视图状态封装
 */
internal class DocumentViewState(
    val viewSize: MutableState<IntSize>,
    val offset: MutableState<Offset>,
    val vZoom: MutableState<Float>,
    val orientation: MutableState<Int>,
    val toPage: MutableState<Int>,
    val isJumping: MutableState<Boolean>,
    val pageViewState: PageViewState,
    // 文本选择相关状态
    val isTextSelecting: MutableState<Boolean>,
    val selectedPage: MutableState<Page?>,
    val showTextActionToolbar: MutableState<Boolean>,
    val selectionStartPos: MutableState<Offset?>,
    val selectionEndPos: MutableState<Offset?>,
    // 绘图相关状态
    val drawingPoints: MutableList<Offset>,
    val activeDrawingPage: MutableState<Int>,
    // 手势相关状态
    val flingJob: MutableState<Job?>,
    val lastTapTime: MutableState<Long>,
    val tapDelayJob: MutableState<Job?>,
    val scope: CoroutineScope,
)

/**
 * 记住文档视图状态
 */
@Composable
internal fun rememberDocumentViewState(
    list: MutableList<APage>,
    decoder: ImageDecoder,
    initialScrollX: Long,
    initialScrollY: Long,
    initialZoom: Double,
    initialOrientation: Int,
    cropMode: Int,
    columnCount: Int,
    currentPath: String,
    annotationManager: AnnotationManager,
    speakingPageIndex: Int?,
    closeDecoderOnShutdown: Boolean = true,
): DocumentViewState {
    val viewSize = remember { mutableStateOf(IntSize.Zero) }
    val offset = remember {
        mutableStateOf(
            Offset(
                initialScrollX.toFloat(),
                initialScrollY.toFloat()
            )
        )
    }
    val vZoom = remember { mutableFloatStateOf(initialZoom.toFloat()) }
    val orientation = remember { mutableIntStateOf(initialOrientation) }
    val toPage = remember { mutableIntStateOf(-1) }
    val scope = rememberCoroutineScope()
    val flingJob = remember { mutableStateOf<Job?>(null) }
    val isJumping = remember { mutableStateOf(false) }
    val lastTapTime = remember { mutableLongStateOf(0L) }
    val tapDelayJob = remember { mutableStateOf<Job?>(null) }

    val textSelector = remember {
        createTextSelector(currentPath) { pageIndex ->
            val structuredText = decoder.getStructuredText(pageIndex)
            if (structuredText != null) {
                createStructuredTextImpl(currentPath, structuredText)
            } else {
                null
            }
        }
    }

    val pageViewState = remember(list) {
        PageViewState(
            list,
            decoder,
            annotationManager,
            orientation.intValue,
            cropMode,
            columnCount = columnCount,
            textSelector = textSelector,
            closeDecoderOnShutdown = closeDecoderOnShutdown
        ).also { pvs ->
            // 任何交互入口开始时都取消上一次点击遗留的 300ms 延迟任务，
            // 避免“拖动后仍然触发点击/翻页”。
            pvs.onInteractionStart = { tapDelayJob.value?.cancel() }
        }
    }

    val isTextSelecting = remember { mutableStateOf(false) }
    val selectedPage = remember { mutableStateOf<Page?>(null) }
    val showTextActionToolbar = remember { mutableStateOf(false) }
    val selectionStartPos = remember { mutableStateOf<Offset?>(null) }
    val selectionEndPos = remember { mutableStateOf<Offset?>(null) }

    val drawingPoints = remember { mutableListOf<Offset>() }
    // 直接持有 MutableIntState，避免 by 解构后又用 mutableStateOf 包一层
    val activeDrawingPage = remember { mutableIntStateOf(-1) }

    return DocumentViewState(
        viewSize = viewSize,
        offset = offset,
        vZoom = vZoom,
        orientation = orientation,
        toPage = toPage,
        isJumping = isJumping,
        pageViewState = pageViewState,
        isTextSelecting = isTextSelecting,
        selectedPage = selectedPage,
        showTextActionToolbar = showTextActionToolbar,
        selectionStartPos = selectionStartPos,
        selectionEndPos = selectionEndPos,
        drawingPoints = drawingPoints,
        activeDrawingPage = activeDrawingPage,
        flingJob = flingJob,
        lastTapTime = lastTapTime,
        tapDelayJob = tapDelayJob,
        scope = scope,
    )
}

/**
 * 文档视图的所有副作用效果
 */
@Composable
internal fun DocumentViewEffects(
    state: DocumentViewState,
    list: MutableList<APage>,
    jumpToPage: Int?,
    jumpMode: JumpMode,
    jumpOffsetY: Float? = null,
    initialOrientation: Int,
    initialScrollX: Long,
    initialScrollY: Long,
    initialZoom: Double,
    initialPageOffsetX: Long? = null,
    initialPageOffsetY: Long? = null,
    reflow: Long,
    cropMode: Int,
    manualCropRect: Rect? = null,
    onManualCropApplied: (() -> Unit)? = null,
    speakingPageIndex: Int?,
    columnCount: Int,
    searchHighlightQuads: Map<Int, List<DocQuad>>,
    currentSearchPageIndex: Int?,
    onSaveDocument: ((page: Int, pageCount: Int, zoom: Double, scrollX: Long, scrollY: Long, scrollOri: Long, reflow: Long, crop: Long, pageOffsetX: Long, pageOffsetY: Long) -> Unit)?,
    onCloseDocument: (() -> Unit)?,
    onPageChanged: ((page: Int) -> Unit)?,
) {
    val pageViewState = state.pageViewState
    val viewSize = state.viewSize
    val vZoom = state.vZoom
    val offset = state.offset
    val orientation = state.orientation
    val isJumping = state.isJumping
    val flingJob = state.flingJob

    SideEffect(columnCount) {
        pageViewState.updateColumnCount(columnCount)
        offset.value = pageViewState.viewOffset
    }

    SideEffect(speakingPageIndex) {
        flingJob.value?.cancel()
        pageViewState.endInteraction()
        pageViewState.updateSpeakingPageIndex(speakingPageIndex)
    }

    SideEffect(searchHighlightQuads, currentSearchPageIndex) {
        pageViewState.updateSearchHighlight(searchHighlightQuads, currentSearchPageIndex)
    }

    SideEffect(pageViewState) {
        pageViewState.onPageLinkClick = { pageIndex ->
            isJumping.value = true
            pageViewState.updateOffset(pageViewState.offsetForPage(pageIndex))
            offset.value = pageViewState.viewOffset
            isJumping.value = false
        }

        pageViewState.onUrlLinkClick = { url ->
            HyperLinkUtils.openSystemBrowser(url)
        }
    }

    SideEffect(pageViewState.layoutVersion) {
        offset.value = pageViewState.viewOffset
    }

    SideEffect(pageViewState, viewSize.value) {
        if (viewSize.value != IntSize.Zero) {
            pageViewState.updateViewSize(
                viewSize.value,
                vZoom.value,
                orientation.value
            )
            offset.value = pageViewState.viewOffset
        }
    }

    SideEffect(jumpToPage, jumpOffsetY, initialOrientation, pageViewState.init) {
        if (pageViewState.init && initialOrientation != orientation.value) {
            isJumping.value = true
            orientation.value = initialOrientation
            pageViewState.updateViewSize(viewSize.value, vZoom.value, initialOrientation)
            offset.value = pageViewState.viewOffset
            isJumping.value = false
        }

        if (jumpToPage != null && pageViewState.init &&
            (state.toPage.value != jumpToPage || jumpOffsetY != null)
        ) {
            isJumping.value = true
            state.toPage.value = jumpToPage

            val newOffset = when (jumpMode) {
                JumpMode.PageRestore -> {
                    if (initialPageOffsetX != null && initialPageOffsetY != null) {
                        // 新记录：先用页码定位到页面，再用页面内偏移量精确恢复位置。
                        // 页面内偏移量相对页面自身，不依赖屏幕方向/缩放等绝对坐标。
                        pageViewState.offsetForPageAt(
                            jumpToPage,
                            initialPageOffsetX,
                            initialPageOffsetY
                        )
                    } else if (initialScrollX == 0L && initialScrollY == 0L) {
                        pageViewState.offsetForPage(jumpToPage)
                    } else {
                        val restored = pageViewState.offsetForDoc(
                            Offset(initialScrollX.toFloat(), initialScrollY.toFloat())
                        )
                        // 保存的偏移量是文档坐标系下的绝对像素，依赖视口尺寸/方向/缩放等。
                        // 例如横屏时保存的偏移量在竖屏下打开就无法套用，
                        // 换算出的页码与保存的页码不一致时，以页码为准。
                        if (pageViewState.pageIndexAtViewportOffset(restored) != jumpToPage) {
                            pageViewState.offsetForPage(jumpToPage)
                        } else {
                            restored
                        }
                    }
                }

                JumpMode.PageNavigation -> {
                    pageViewState.offsetForPage(jumpToPage, jumpOffsetY)
                }
            }

            flingJob.value?.cancel()
            pageViewState.updateOffset(newOffset)
            pageViewState.endInteraction()
            offset.value = pageViewState.viewOffset
            isJumping.value = false
        }
    }

    SideEffect(cropMode to manualCropRect) {
        pageViewState.setCropMode(cropMode)
        if (cropMode == CropMode.MANUAL && manualCropRect != null) {
            pageViewState.applyManualCrop(manualCropRect)
            onManualCropApplied?.invoke()
        }
        offset.value = pageViewState.viewOffset
    }

    LaunchedEffect(pageViewState) {
        snapshotFlow { pageViewState.firstVisiblePageIndex() }
            .distinctUntilChanged()
            .collect { page ->
                onPageChanged?.invoke(page)
            }
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    fun saveDocumentState() {
        val pageCount = list.size
        val zoom = vZoom.value.toDouble()
        val currentPage = pageViewState.firstVisiblePageIndex()
        val docX = -offset.value.x / vZoom.value
        val docY = -offset.value.y / vZoom.value
        // 页面内偏移量：可见区域左上角相对当前页左上角的文档空间偏移。
        // 恢复时先按页码定位页面，再叠加该偏移，避免绝对坐标受屏幕方向/尺寸影响。
        val page = pageViewState.pages.getOrNull(currentPage)
        val pageOffsetX = if (page != null) {
            (docX - page.baseRect.left).toLong()
        } else {
            0L
        }
        val pageOffsetY = if (page != null) {
            (docY - page.baseRect.top).toLong()
        } else {
            0L
        }

        if (!list.isEmpty()) {
            onSaveDocument?.invoke(
                currentPage,
                pageCount,
                zoom,
                docX.toLong(),
                docY.toLong(),
                orientation.value.toLong(),
                reflow,
                pageViewState.getCropMode().toLong(),
                pageOffsetX,
                pageOffsetY
            )
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                saveDocumentState()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            state.tapDelayJob.value?.cancel()
            saveDocumentState()
            onCloseDocument?.invoke()
            pageViewState.shutdown()
        }
    }
}

/**
 * 统一的手势处理器
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun CommonGestureHandler(
    state: DocumentViewState,
    gestureMode: GestureMode,
    pathConfig: PathConfig,
    viewSize: IntSize,
    onDoubleTapToolbar: () -> Unit,
    onTapNonPageArea: (pageIndex: Int) -> Unit,
    onPageChanged: (page: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pageViewState = state.pageViewState
    val offset = state.offset
    val vZoom = state.vZoom
    val scope = state.scope
    val flingJob = state.flingJob
    val isTextSelecting = state.isTextSelecting
    val selectedPage = state.selectedPage
    val showTextActionToolbar = state.showTextActionToolbar
    val selectionStartPos = state.selectionStartPos
    val selectionEndPos = state.selectionEndPos
    val drawingPoints = state.drawingPoints
    val activeDrawingPage = state.activeDrawingPage

    val viewConfiguration = LocalViewConfiguration.current
    val tapPageKeepPx = with(LocalDensity.current) { tap_page_keep_dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .pointerInput(viewSize, gestureMode, pathConfig, tapPageKeepPx) {
                when (gestureMode) {
                    GestureMode.SELECTION -> {
                        detectDragGestures(
                            onDragStart = { pos ->
                                isTextSelecting.value = true
                                selectionStartPos.value = pos
                                showTextActionToolbar.value = false

                                val doc = pageViewState.viewport.toDoc(pos)
                                val clickedPageIndex =
                                    ViewUtils.calculateClickedPage(doc, pageViewState)
                                pageViewState.pages.getOrNull(clickedPageIndex)?.let {
                                    it.startTextSelection(doc.x, doc.y)
                                    selectedPage.value = it
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                selectionEndPos.value = change.position
                                val doc = pageViewState.viewport.toDoc(change.position)
                                selectedPage.value?.updateTextSelection(doc.x, doc.y)
                            },
                            onDragEnd = {
                                val selection = selectedPage.value?.endTextSelection()
                                isTextSelecting.value = false
                                if (selection != null && selection.text.isNotBlank()) {
                                    showTextActionToolbar.value = true
                                }
                            }
                        )
                    }

                    GestureMode.DRAW -> {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                drawingPoints.clear()
                                val doc = pageViewState.viewport.toDoc(startOffset)
                                val targetPage =
                                    pageViewState.pageToRender.find { it.baseRect.contains(doc) }
                                activeDrawingPage.value = targetPage?.index ?: -1

                                if (targetPage != null) {
                                    val startRel = Offset(
                                        (doc.x - targetPage.baseRect.left) / targetPage.baseRect.width,
                                        (doc.y - targetPage.baseRect.top) / targetPage.baseRect.height
                                    )
                                    drawingPoints.add(startRel)
                                }
                            },
                            onDrag = { change, _ ->
                                if (activeDrawingPage.value != -1) {
                                    val targetPage = pageViewState.pageToRender.find {
                                        it.index == activeDrawingPage.value
                                    }
                                    if (targetPage != null) {
                                        val doc = pageViewState.viewport.toDoc(change.position)
                                        val currentRel = Offset(
                                            (doc.x - targetPage.baseRect.left) / targetPage.baseRect.width,
                                            (doc.y - targetPage.baseRect.top) / targetPage.baseRect.height
                                        )

                                        if (pathConfig.drawType == DrawType.LINE) {
                                            val startRel = drawingPoints.first()
                                            val linePoints =
                                                ViewUtils.calculateLinePoints(startRel, currentRel)
                                            pageViewState.updateDrawing(
                                                activeDrawingPage.value,
                                                linePoints,
                                                pathConfig
                                            )
                                        } else {
                                            drawingPoints.add(currentRel)
                                            pageViewState.updateDrawing(
                                                activeDrawingPage.value,
                                                drawingPoints.toList(),
                                                pathConfig
                                            )
                                        }
                                        change.consume()
                                    }
                                }
                            },
                            onDragEnd = {
                                if (activeDrawingPage.value != -1) {
                                    val finalPoints =
                                        if (pathConfig.drawType == DrawType.LINE) {
                                            pageViewState.activeDrawingAnno?.second?.points
                                                ?: emptyList()
                                        } else {
                                            drawingPoints.toList()
                                        }
                                    pageViewState.finalizeDrawing(
                                        activeDrawingPage.value,
                                        finalPoints,
                                        pathConfig
                                    )
                                }
                                drawingPoints.clear()
                                activeDrawingPage.value = -1
                            }
                        )
                    }

                    GestureMode.VIEW -> {
                        val touchSlop = viewConfiguration.touchSlop

                        awaitEachGesture {
                            val velocityTracker = VelocityTracker()
                            var isZooming = false
                            var isDragging = false
                            var totalPan = Offset.Zero
                            var lastChange: PointerInputChange? = null
                            var lastTileRefreshAt = 0L

                            val down = awaitFirstDown(requireUnconsumed = false)
                            // 按下时如果正在惯性滚动，这次手势只用于“停止滚动”，
                            // 抬起时不应再触发点击（否则滚动后想停下会误弹出页码 toast）。
                            val wasFlinging = flingJob.value?.isActive == true
                            // 新一次按下即视为交互开始：取消上一次点击遗留的 300ms 延迟任务，
                            // 否则用户按下后立刻拖动，拖动结束时不会触发 tap，
                            // 但旧任务到期仍会执行翻页/链接逻辑（表现为“拖了还翻页”）。
                            state.tapDelayJob.value?.cancel()
                            pageViewState.beginInteraction()
                            flingJob.value?.cancel()

                            do {
                                val event = awaitPointerEvent()
                                if (!event.changes.fastAny { it.isConsumed }) {
                                    lastChange = event.changes.firstOrNull()
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()

                                    if (!isZooming && event.changes.size > 1) {
                                        isZooming = true
                                    }

                                    if (isZooming) {
                                        val centroid = event.calculateCentroid(useCurrent = false)
                                        if (centroid.isSpecified && zoomChange != 1f) {
                                            val newZoom =
                                                (vZoom.value * zoomChange).coerceIn(1f, max_zoom)
                                            if (newZoom != vZoom.value) {
                                                val content =
                                                    pageViewState.viewport.toDoc(centroid)
                                                val newOffset = Offset(
                                                    centroid.x - content.x * newZoom,
                                                    centroid.y - content.y * newZoom
                                                )
                                                vZoom.value = newZoom
                                                pageViewState.updateViewport(
                                                    newZoom,
                                                    newOffset,
                                                    false
                                                )
                                                offset.value = pageViewState.viewOffset
                                            }
                                        }
                                    } else {
                                        totalPan += panChange
                                        val delta = when {
                                            !isDragging && totalPan.getDistance() > touchSlop -> {
                                                isDragging = true
                                                // 兜底：拖动开始后再取消一次 pending tap，
                                                // 并重置双击判定基准，避免拖动结束后紧跟的点击被误判为双击。
                                                state.tapDelayJob.value?.cancel()
                                                state.lastTapTime.value = 0L
                                                totalPan
                                            }

                                            isDragging -> panChange
                                            else -> Offset.Zero
                                        }
                                        if (delta != Offset.Zero) {
                                            val now = System.currentTimeMillis()
                                            val requestTiles =
                                                now - lastTileRefreshAt >= tile_refresh_interval_ms
                                            if (requestTiles) {
                                                lastTileRefreshAt = now
                                            }
                                            pageViewState.updateViewport(
                                                vZoom.value,
                                                offset.value + delta,
                                                requestTiles
                                            )
                                            offset.value = pageViewState.viewOffset
                                            velocityTracker.addPosition(
                                                lastChange?.uptimeMillis ?: 0,
                                                lastChange?.position ?: Offset(0f, 0f)
                                            )
                                        }
                                    }
                                    event.changes.fastForEach {
                                        if (it.positionChanged()) it.consume()
                                    }
                                }
                            } while (event.changes.fastAny { it.pressed })

                            val finalChange = lastChange ?: return@awaitEachGesture

                            if (!isZooming) {
                                val dragDistance =
                                    (finalChange.position - down.position).getDistance()
                                if (!isDragging && !wasFlinging && dragDistance < touchSlop) {
                                    handleTapGestureInternal(
                                        finalChange.position,
                                        offset,
                                        state.lastTapTime,
                                        state.tapDelayJob,
                                        scope,
                                        pageViewState,
                                        tapPageKeepPx,
                                        onDoubleTapToolbar,
                                        onTapNonPageArea,
                                    )
                                }

                                if (isDragging) {
                                    val velocity = velocityTracker.calculateVelocity()
                                    performFling(
                                        velocity,
                                        pageViewState,
                                        offset,
                                        vZoom.value,
                                        scope,
                                        flingJob,
                                    )
                                }
                            }

                            if (!isDragging) {
                                pageViewState.endInteraction()
                            }
                            pageViewState.updateViewport(vZoom.value, offset.value, true)
                            offset.value = pageViewState.viewOffset
                        }
                    }
                }
            }
    )
}

/**
 * 文档视图绘制器：内容与覆盖层共用同一个 GPU 图层，均以文档坐标绘制。
 * 平移只更新图层位移，不触发 Canvas 重绘。
 */
@Composable
internal fun DocumentViewCanvas(
    state: DocumentViewState,
    viewSize: IntSize,
    modifier: Modifier = Modifier,
) {
    val pageViewState = state.pageViewState

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = state.offset.value.x
                    translationY = state.offset.value.y
                    scaleX = state.vZoom.value
                    scaleY = state.vZoom.value
                    transformOrigin = TransformOrigin(0f, 0f)
                    clip = false
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                pageViewState.revision
                pageViewState.drawVisiblePages(this)
            }
            // 覆盖层独立成一张 Canvas：tile 解码只让内容层失效，不再连带重算链接/选区/搜索高亮
            Canvas(modifier = Modifier.fillMaxSize()) {
                pageViewState.drawOverlay(this)
            }
        }
    }
}

/**
 * 统一的点击处理（包含双击检测）
 */
internal fun handleTapGestureInternal(
    tapPos: Offset,
    offset: MutableState<Offset>,
    lastTapTime: MutableState<Long>,
    tapDelayJob: MutableState<Job?>,
    scope: CoroutineScope,
    pageViewState: PageViewState,
    tapPageKeepPx: Float,
    onDoubleTapToolbar: () -> Unit,
    onTapNonPageArea: (pageIndex: Int) -> Unit,
) {
    val currentTime = System.currentTimeMillis()
    val isDoubleTap = currentTime - lastTapTime.value < 300

    if (isDoubleTap) {
        tapDelayJob.value?.cancel()
        val height = pageViewState.viewSize.height
        if (tapPos.y in (height / 4f)..(height * 3 / 4f)) {
            onDoubleTapToolbar()
        }
        lastTapTime.value = 0L
    } else {
        lastTapTime.value = currentTime
        tapDelayJob.value?.cancel()

        tapDelayJob.value = scope.launch {
            delay(300)

            val doc = pageViewState.viewport.toDoc(tapPos)
            val linkHandled = pageViewState.handleClick(doc.x, doc.y)

            if (!linkHandled) {
                val isPageTurned = ViewUtils.handleTapGesture(
                    tapPos,
                    pageViewState,
                    tapPageKeepPx
                ) { newOffset ->
                    offset.value = newOffset
                    pageViewState.updateOffset(newOffset)
                    offset.value = pageViewState.viewOffset
                }

                if (!isPageTurned) {
                    val clickedPage = ViewUtils.calculateClickedPage(doc, pageViewState)
                    onTapNonPageArea(clickedPage)
                }
            }
        }
    }
}

/**
 * 惯性动画
 */
internal fun performFling(
    velocity: Velocity,
    pageViewState: PageViewState,
    offset: MutableState<Offset>,
    vZoom: Float,
    scope: CoroutineScope,
    flingJob: MutableState<Job?>,
) {
    val decay = exponentialDecay<Offset>(
        frictionMultiplier = 0.35f,
        absVelocityThreshold = 0.50f,
    )

    val scaledVelocity = Offset(velocity.x * 1.1f, velocity.y * 1.1f)
    val job = scope.launch {
        val animationState = AnimationState(
            typeConverter = OffsetToVector,
            initialValue = offset.value,
            initialVelocity = scaledVelocity
        )

        var lastTileRefreshAt = 0L
        animationState.animateDecay(decay) {
            val bounded = pageViewState.viewport.clampOffset(value)
            offset.value = bounded
            val now = System.currentTimeMillis()
            val requestTiles = now - lastTileRefreshAt >= tile_refresh_interval_ms
            if (requestTiles) {
                lastTileRefreshAt = now
            }
            pageViewState.updateViewport(vZoom, bounded, requestTiles)
            // 已经顶到边界且速度还在往外推，继续跑只会空转
            val current = this.velocity
            val blockedX = current.x == 0f || current.x * (value.x - bounded.x) > 0f
            val blockedY = current.y == 0f || current.y * (value.y - bounded.y) > 0f
            if (blockedX && blockedY) {
                cancelAnimation()
            }
        }
        pageViewState.updateViewport(vZoom, offset.value, true)
        pageViewState.endInteraction()
    }
    flingJob.value = job
}

private val OffsetToVector: TwoWayConverter<Offset, AnimationVector2D> = TwoWayConverter(
    convertFromVector = { Offset(it.v1, it.v2) },
    convertToVector = { AnimationVector2D(it.x, it.y) }
)

/**
 * 文本操作工具栏
 */
@Composable
internal fun TextActionToolbarWrapper(
    show: Boolean,
    selectedPage: Page?,
    textSelector: TextSelector,
    onCopy: (text: String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (show && selectedPage?.currentSelection != null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            TextActionToolbar(
                selectedPage = selectedPage,
                textSelector = textSelector,
                onCopy = { text ->
                    onCopy(text)
                },
                onDismiss = onDismiss
            )
        }
    }
}
