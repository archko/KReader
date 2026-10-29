package com.archko.reader.pdf.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.TwoWayConverter
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
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.archko.reader.pdf.cache.ImageCache
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.APage
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
private val tap_page_keep_dp = 10.dp

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
    crop: Boolean,
    columnCount: Int,
    currentPath: String,
    annotationManager: AnnotationManager,
    speakingPageIndex: Int?,
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
            crop,
            columnCount = columnCount,
            textSelector = textSelector
        )
    }

    val isTextSelecting = remember { mutableStateOf(false) }
    val selectedPage = remember { mutableStateOf<Page?>(null) }
    val showTextActionToolbar = remember { mutableStateOf(false) }
    val selectionStartPos = remember { mutableStateOf<Offset?>(null) }
    val selectionEndPos = remember { mutableStateOf<Offset?>(null) }

    val drawingPoints = remember { mutableListOf<Offset>() }
    var activeDrawingPage by remember { mutableIntStateOf(-1) }

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
        activeDrawingPage = remember { mutableStateOf(activeDrawingPage) },
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
    reflow: Long,
    crop: Boolean,
    speakingPageIndex: Int?,
    columnCount: Int,
    searchHighlightQuads: Map<Int, List<DocQuad>>,
    currentSearchPageIndex: Int?,
    onSaveDocument: ((page: Int, pageCount: Int, zoom: Double, scrollX: Long, scrollY: Long, scrollOri: Long, reflow: Long, crop: Long) -> Unit)?,
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

    LaunchedEffect(columnCount) {
        pageViewState.updateColumnCount(columnCount)
        offset.value = pageViewState.viewOffset
    }

    LaunchedEffect(speakingPageIndex) {
        flingJob.value?.cancel()
        pageViewState.updateSpeakingPageIndex(speakingPageIndex)
    }

    LaunchedEffect(searchHighlightQuads, currentSearchPageIndex) {
        pageViewState.updateSearchHighlight(searchHighlightQuads, currentSearchPageIndex)
    }

    LaunchedEffect(pageViewState) {
        pageViewState.onPageLinkClick = { pageIndex ->
            isJumping.value = true
            val newOffset = pageViewState.offsetForPage(pageIndex)
            offset.value = newOffset
            pageViewState.updateOffset(newOffset)
            offset.value = pageViewState.viewOffset
            isJumping.value = false
        }

        pageViewState.onUrlLinkClick = { url ->
            HyperLinkUtils.openSystemBrowser(url)
        }
    }

    LaunchedEffect(pageViewState.layoutVersion) {
        offset.value = pageViewState.viewOffset
    }

    LaunchedEffect(pageViewState, viewSize.value) {
        if (viewSize.value != IntSize.Zero) {
            pageViewState.updateViewSize(
                viewSize.value,
                vZoom.value,
                orientation.value
            )
            offset.value = pageViewState.viewOffset
        }
    }

    LaunchedEffect(jumpToPage, jumpOffsetY, initialOrientation, pageViewState.init) {
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
                    if (initialScrollX == 0L && initialScrollY == 0L) {
                        pageViewState.offsetForPage(jumpToPage)
                    } else {
                        pageViewState.offsetForDoc(
                            Offset(initialScrollX.toFloat(), initialScrollY.toFloat())
                        )
                    }
                }

                JumpMode.PageNavigation -> {
                    pageViewState.offsetForPage(jumpToPage, jumpOffsetY)
                }
            }

            flingJob.value?.cancel()
            offset.value = newOffset
            pageViewState.updateOffset(newOffset)
            offset.value = pageViewState.viewOffset
            isJumping.value = false
        }
    }

    LaunchedEffect(crop) {
        pageViewState.setCropEnabled(crop)
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

        if (!list.isEmpty()) {
            onSaveDocument?.invoke(
                currentPage,
                pageCount,
                zoom,
                docX.toLong(),
                docY.toLong(),
                orientation.value.toLong(),
                reflow,
                if (pageViewState.isCropEnabled()) 0L else 1L
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
            ImageCache.clear()
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

                            val down = awaitFirstDown(requireUnconsumed = false)
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
                                                offset.value = newOffset
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
                                                totalPan
                                            }

                                            isDragging -> panChange
                                            else -> Offset.Zero
                                        }
                                        if (delta != Offset.Zero) {
                                            offset.value += delta
                                            pageViewState.updateViewport(
                                                vZoom.value,
                                                offset.value,
                                                true
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
                                if (!isDragging && dragDistance < touchSlop) {
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

                            pageViewState.updateViewport(vZoom.value, offset.value, true)
                            offset.value = pageViewState.viewOffset
                        }
                    }
                }
            }
    )
}

/**
 * 文档视图绘制器：内容层做 GPU 变换，覆盖层在屏幕坐标绘制。
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
                    renderEffect = null
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                pageViewState.revision
                pageViewState.drawVisiblePages(this, Offset.Zero, 1f)
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            state.offset.value
            state.vZoom.value
            pageViewState.revision
            pageViewState.drawOverlay(this)
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
        val animatable = Animatable(
            initialValue = offset.value,
            typeConverter = OffsetToVector
        )

        animatable.animateDecay(scaledVelocity, decay) {
            val bounded = pageViewState.viewport.clampOffset(value)
            offset.value = bounded
            pageViewState.updateViewport(vZoom, bounded, true)
        }
        pageViewState.updateViewport(vZoom, offset.value, true)
        offset.value = pageViewState.viewOffset
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
