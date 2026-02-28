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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.archko.reader.pdf.cache.ImageCache
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.APage
import com.archko.reader.pdf.state.AnnotationManager
import com.archko.reader.pdf.util.HyperLinkUtils
import com.archko.reader.pdf.util.ViewUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val max_zoom = 30f

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
    state: ImageDecoder,
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

    // 创建文本选择器 - 使用expect/actual模式
    val textSelector = remember {
        createTextSelector(currentPath) { pageIndex ->
            val structuredText = state.getStructuredText(pageIndex)
            if (structuredText != null) {
                createStructuredTextImpl(currentPath, structuredText)
            } else {
                null
            }
        }
    }

    val pageViewState = remember(list) {
        println("DocumentView: 创建新的PageViewState:$viewSize, vZoom:$vZoom，list: ${list.size}, orientation: $orientation")
        PageViewState(
            list,
            state,
            annotationManager,
            orientation.intValue,
            crop,
            columnCount = columnCount,
            textSelector = textSelector
        )
    }

    // 文本选择相关状态
    val isTextSelecting = remember { mutableStateOf(false) }
    val selectedPage = remember { mutableStateOf<Page?>(null) }
    val showTextActionToolbar = remember { mutableStateOf(false) }
    val selectionStartPos = remember { mutableStateOf<Offset?>(null) }
    val selectionEndPos = remember { mutableStateOf<Offset?>(null) }

    // 临时存储当前正在画的线（比例坐标）
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
    initialOrientation: Int,
    initialScrollX: Long,
    initialScrollY: Long,
    initialZoom: Double,
    reflow: Long,
    crop: Boolean,
    speakingPageIndex: Int?,
    columnCount: Int,
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
        pageViewState.updateVisiblePages(offset.value, viewSize.value, vZoom.value)
    }

    LaunchedEffect(speakingPageIndex) {
        flingJob.value?.cancel()
        pageViewState.updateSpeakingPageIndex(speakingPageIndex)
    }

    // 设置页面跳转回调
    LaunchedEffect(pageViewState) {
        pageViewState.onPageLinkClick = { pageIndex ->
            isJumping.value = true
            val targetPage = pageViewState.pages[pageIndex]
            val newOffset = Offset(offset.value.x, -targetPage.bounds.top)
            offset.value = newOffset
            pageViewState.updateOffset(offset.value)
            isJumping.value = false
        }

        pageViewState.onUrlLinkClick = { url ->
            println("DocumentView: URL链接点击，URL: $url")
            HyperLinkUtils.openSystemBrowser(url)
        }
    }

    // 确保在 list 变化时重新计算总高度
    LaunchedEffect(list) {
        if (viewSize.value != IntSize.Zero) {
            println("DocumentView: 更新ViewSize:$viewSize, vZoom:$vZoom, list: ${list.size}, orientation: $orientation")
            pageViewState.updateViewSize(viewSize.value, vZoom.value, orientation.value)
        }
    }

    // 监听外部参数的变化
    LaunchedEffect(jumpToPage, initialOrientation, pageViewState.init) {
        if (columnCount == 1 && initialOrientation != orientation.value && pageViewState.init) {
            isJumping.value = true
            val currentPage = jumpToPage ?: 0
            println("DocumentView: orientation改变，重置offset和zoom: $orientation->$initialOrientation, page:$currentPage")
            orientation.value = initialOrientation
            offset.value = Offset.Zero
            vZoom.value = 1f
            pageViewState.updateViewSize(viewSize.value, vZoom.value, orientation.value)

            val page = pageViewState.pages.getOrNull(currentPage)
            if (page != null) {
                if (orientation.value == Vertical) {
                    val clampedTargetY = page.bounds.top.coerceIn(
                        0f,
                        (pageViewState.totalHeight - viewSize.value.height).coerceAtLeast(0f)
                    )
                    val clampedY = -clampedTargetY
                    offset.value = Offset(offset.value.x, clampedY)
                } else {
                    val clampedTargetX = page.bounds.left.coerceIn(
                        0f,
                        (pageViewState.totalWidth - viewSize.value.width).coerceAtLeast(0f)
                    )
                    val clampedX = -clampedTargetX
                    offset.value = Offset(clampedX, offset.value.y)
                }
                println("DocumentView: orientation改变，跳转: ${offset.value}, $currentPage, page:$page")
                flingJob.value?.cancel()
                pageViewState.updateOffset(offset.value)
            }
            isJumping.value = false
            return@LaunchedEffect
        }

        if (null != jumpToPage && state.toPage.value != jumpToPage && pageViewState.init) {
            isJumping.value = true
            state.toPage.value = jumpToPage

            when (jumpMode) {
                JumpMode.PageRestore -> {
                    val offsetX = initialScrollX.toFloat()
                    val offsetY = initialScrollY.toFloat()
                    val testOffset = Offset(offsetX, offsetY)
                    val offsetPage =
                        ViewUtils.firstPage(
                            pageViewState,
                            testOffset,
                            orientation.value,
                            viewSize.value,
                            null
                        )

                    if (offsetPage == state.toPage.value) {
                        offset.value = Offset(
                            offsetX.coerceIn(
                                -(pageViewState.totalWidth - viewSize.value.width).coerceAtLeast(
                                    0f
                                ), 0f
                            ),
                            offsetY.coerceIn(
                                -(pageViewState.totalHeight - viewSize.value.height).coerceAtLeast(
                                    0f
                                ), 0f
                            )
                        )
                    } else {
                        val page = pageViewState.pages.getOrNull(state.toPage.value)
                        if (page != null) {
                            val clampedTargetY = page.bounds.top.coerceIn(
                                0f,
                                (pageViewState.totalHeight - viewSize.value.height).coerceAtLeast(0f)
                            )
                            val clampedY = -clampedTargetY
                            val clampedTargetX = page.bounds.left.coerceIn(
                                0f,
                                (pageViewState.totalWidth - viewSize.value.width).coerceAtLeast(0f)
                            )
                            val clampedX = -clampedTargetX
                            offset.value = Offset(clampedX, clampedY)
                        }
                    }
                    println("DocumentView: PageRestore跳转 toPage=${state.toPage.value}, offsetPage=$offsetPage, useOffset=${offsetPage == state.toPage.value}, offset:$testOffset")
                }

                JumpMode.PageNavigation -> {
                    val page = pageViewState.pages.getOrNull(state.toPage.value)
                    if (page != null) {
                        if (orientation.value == Vertical) {
                            val clampedTargetY = page.bounds.top.coerceIn(
                                0f,
                                (pageViewState.totalHeight - viewSize.value.height).coerceAtLeast(0f)
                            )
                            val clampedY = -clampedTargetY
                            offset.value = Offset(offset.value.x, clampedY)
                        } else {
                            val clampedTargetX = page.bounds.left.coerceIn(
                                0f,
                                (pageViewState.totalWidth - viewSize.value.width).coerceAtLeast(0f)
                            )
                            val clampedX = -clampedTargetX
                            offset.value = Offset(clampedX, offset.value.y)
                        }
                        println("DocumentView: PageNavigation跳转到:${offset.value}, top:${page.bounds.top}, toPage:${state.toPage.value}")
                    } else {
                        println("DocumentView: PageNavigation找不到页面:${state.toPage.value}")
                    }
                }
            }

            flingJob.value?.cancel()
            pageViewState.updateOffset(offset.value)
            isJumping.value = false
        }
    }

    LaunchedEffect(crop) {
        val old = pageViewState.isCropEnabled()
        if (old != crop) {
            println("DocumentView: 切边变化:$crop")
            pageViewState.setCropEnabled(crop)
            pageViewState.pages.forEach { page ->
                page.recycle()
            }
            ImageCache.clear()
            pageViewState.invalidatePageSizes()
            pageViewState.updateOffset(offset.value)
        }
    }

    // 监听页面变化并回调
    LaunchedEffect(offset.value) {
        if (!isJumping.value) {
            ViewUtils.firstPage(
                pageViewState,
                offset.value,
                orientation.value,
                viewSize.value,
                onPageChanged
            )
        }
    }

    // 获取生命周期所有者
    val lifecycleOwner = LocalLifecycleOwner.current

    // 保存文档状态的公共方法
    fun saveDocumentState() {
        val pages = pageViewState.pages
        var currentPage = 0
        if (pages.isNotEmpty()) {
            val offsetY = offset.value.y
            val offsetX = offset.value.x
            val firstVisible = pages.indexOfFirst { page ->
                if (orientation.value == Vertical) {
                    val top = -offsetY
                    val bottom = top + viewSize.value.height
                    page.bounds.bottom > top && page.bounds.top < bottom
                } else {
                    val left = -offsetX
                    val right = left + viewSize.value.width
                    page.bounds.right > left && page.bounds.left < right
                }
            }
            if (firstVisible != -1) {
                currentPage = firstVisible
            }
        }
        val pageCount = list.size
        val zoom = vZoom.value.toDouble()
        println("DocumentView: 保存记录:page:$currentPage, pc:$pageCount, ${viewSize.value}, vZoom:$vZoom, list: ${list.size}, orientation: $orientation, crop: $crop,${pageViewState.isCropEnabled()}")

        if (!list.isEmpty()) {
            onSaveDocument?.invoke(
                currentPage,
                pageCount,
                zoom,
                offset.value.x.toLong(),
                offset.value.y.toLong(),
                orientation.value.toLong(),
                reflow,
                if (pageViewState.isCropEnabled()) 0L else 1L
            )
        }
    }

    // 监听生命周期事件，在onPause时保存记录
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
    val orientation = state.orientation
    val scope = state.scope
    val flingJob = state.flingJob
    val isTextSelecting = state.isTextSelecting
    val selectedPage = state.selectedPage
    val showTextActionToolbar = state.showTextActionToolbar
    val selectionStartPos = state.selectionStartPos
    val selectionEndPos = state.selectionEndPos
    val drawingPoints = state.drawingPoints
    val activeDrawingPage = state.activeDrawingPage

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .pointerInput(viewSize, gestureMode, pathConfig) {
                when (gestureMode) {
                    GestureMode.SELECTION -> {
                        detectDragGestures(
                            onDragStart = { pos ->
                                isTextSelecting.value = true
                                selectionStartPos.value = pos
                                showTextActionToolbar.value = false

                                val clickedPageIndex = ViewUtils.calculateClickedPage(
                                    pos,
                                    offset.value,
                                    orientation.value,
                                    pageViewState
                                )
                                pageViewState.pages.getOrNull(clickedPageIndex)?.let {
                                    it.startTextSelection(
                                        pos.x - offset.value.x,
                                        pos.y - offset.value.y
                                    )
                                    selectedPage.value = it
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                selectionEndPos.value = change.position
                                selectedPage.value?.updateTextSelection(
                                    change.position.x - offset.value.x,
                                    change.position.y - offset.value.y
                                )
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
                                val targetPage = pageViewState.pageToRender.find {
                                    it.bounds.contains(startOffset - offset.value)
                                }
                                activeDrawingPage.value = targetPage?.aPage?.index ?: -1

                                if (activeDrawingPage.value != -1) {
                                    val targetPage =
                                        pageViewState.pageToRender.find { it.aPage.index == activeDrawingPage.value }!!
                                    val localX = startOffset.x - offset.value.x - targetPage.xOffset
                                    val localY = startOffset.y - offset.value.y - targetPage.yOffset
                                    val startRel = Offset(
                                        localX / targetPage.width,
                                        localY / targetPage.height
                                    )
                                    drawingPoints.add(startRel)
                                }
                            },
                            onDrag = { change, dragAmount ->
                                if (activeDrawingPage.value != -1) {
                                    val targetPage =
                                        pageViewState.pageToRender.find { it.aPage.index == activeDrawingPage.value }!!
                                    val localX =
                                        change.position.x - offset.value.x - targetPage.xOffset
                                    val localY =
                                        change.position.y - offset.value.y - targetPage.yOffset
                                    val currentRel = Offset(
                                        localX / targetPage.width,
                                        localY / targetPage.height
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
                        awaitEachGesture {
                            val velocityTracker = VelocityTracker()
                            var isZooming = false
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
                                            val zoomFactor = newZoom / vZoom.value

                                            // 计算居中偏移
                                            val centerOffsetX =
                                                if (orientation.value == Horizontal && pageViewState.totalWidth < viewSize.width) {
                                                    (viewSize.width - pageViewState.totalWidth) / 2
                                                } else 0f
                                            val centerOffsetY =
                                                if (orientation.value == Vertical && pageViewState.totalHeight < viewSize.height) {
                                                    (viewSize.height - pageViewState.totalHeight) / 2
                                                } else 0f

                                            // 计算缩放中心点：手势中心相对于内容的位置（考虑居中偏移）
                                            val contentCenterX =
                                                centroid.x - offset.value.x - centerOffsetX
                                            val contentCenterY =
                                                centroid.y - offset.value.y - centerOffsetY

                                            // 计算新的偏移量，保持内容中心点不变
                                            val newOffsetX =
                                                centroid.x - contentCenterX * zoomFactor - centerOffsetX
                                            val newOffsetY =
                                                centroid.y - contentCenterY * zoomFactor - centerOffsetY

                                            vZoom.value = newZoom
                                            val targetOffset = Offset(newOffsetX, newOffsetY)
                                            if (targetOffset.isSpecified) {
                                                offset.value = calculateBounds(
                                                    targetOffset,
                                                    vZoom.value,
                                                    viewSize,
                                                    pageViewState,
                                                    orientation.value
                                                )
                                            }
                                        }
                                    } else {
                                        offset.value += panChange
                                        offset.value = calculateBounds(
                                            offset.value,
                                            vZoom.value,
                                            viewSize,
                                            pageViewState,
                                            orientation.value
                                        )
                                        pageViewState.updateOffset(offset.value)
                                        velocityTracker.addPosition(
                                            lastChange?.uptimeMillis ?: 0,
                                            lastChange?.position ?: Offset(0f, 0f)
                                        )
                                    }
                                    event.changes.fastForEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.fastAny { it.pressed })

                            val finalChange = lastChange ?: return@awaitEachGesture

                            if (!isZooming) {
                                val dragDistance =
                                    (finalChange.position - down.position).getDistance()
                                if (dragDistance < 10f) {
                                    handleTapGestureInternal(
                                        finalChange.position,
                                        viewSize,
                                        offset,
                                        state.lastTapTime,
                                        state.tapDelayJob,
                                        scope,
                                        pageViewState,
                                        orientation.value,
                                        onDoubleTapToolbar,
                                        onTapNonPageArea,
                                        onPageChanged,
                                    )
                                }

                                val velocity = velocityTracker.calculateVelocity()
                                performFling(
                                    velocity,
                                    viewSize,
                                    pageViewState,
                                    offset,
                                    vZoom.value,
                                    orientation.value,
                                    scope,
                                    flingJob,
                                )
                            } else {
                                pageViewState.updateOffset(offset.value)
                                pageViewState.updateViewSize(
                                    viewSize,
                                    vZoom.value,
                                    orientation.value
                                )
                            }
                            pageViewState.updateVisiblePages(offset.value, viewSize, vZoom.value)
                        }
                    }
                }
            }
    )
}

/**
 * 文档视图绘制器
 */
@Composable
internal fun DocumentViewCanvas(
    state: DocumentViewState,
    viewSize: IntSize,
    modifier: Modifier = Modifier,
) {
    val pageViewState = state.pageViewState
    val isTextSelecting = state.isTextSelecting
    val selectionStartPos = state.selectionStartPos
    val selectionEndPos = state.selectionEndPos

    Canvas(
        modifier = modifier.fillMaxSize()
            .graphicsLayer {
                translationX = state.offset.value.x
                translationY = state.offset.value.y
                clip = false
                renderEffect = null
            }
    ) {
        //val centerOffsetX =
        //    if (orientation.value == Horizontal && pageViewState.totalWidth < viewSize.width) {
        //        (viewSize.width - pageViewState.totalWidth) / 2
        //    } else 0f
        //val centerOffsetY =
        //    if (orientation.value == Vertical && pageViewState.totalHeight < viewSize.height) {
        //        (viewSize.height - pageViewState.totalHeight) / 2
        //    } else 0f
        //translate(left = offset.value.x + centerOffsetX, top = offset.value.y + centerOffsetY) {
        pageViewState.drawVisiblePages(this, state.offset.value, state.vZoom.value)

        if (isTextSelecting.value && selectionStartPos.value != null && selectionEndPos.value != null) {
            val start = selectionStartPos.value!!
            val end = selectionEndPos.value!!
            val left = minOf(start.x, end.x) - state.offset.value.x
            val top = minOf(start.y, end.y) - state.offset.value.y
            val right = maxOf(start.x, end.x) - state.offset.value.x
            val bottom = maxOf(start.y, end.y) - state.offset.value.y

            drawRect(
                color = Color.Blue.copy(alpha = 0.3f),
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(right - left, bottom - top)
            )
        }
        //}
    }
}

/**
 * 统一的点击处理（包含双击检测）
 */
internal fun handleTapGestureInternal(
    tapPos: Offset,
    viewSize: IntSize,
    offset: MutableState<Offset>,
    lastTapTime: MutableState<Long>,
    tapDelayJob: MutableState<Job?>,
    scope: CoroutineScope,
    pageViewState: PageViewState,
    orientation: Int,
    onDoubleTapToolbar: () -> Unit,
    onTapNonPageArea: (pageIndex: Int) -> Unit,
    onPageChanged: (page: Int) -> Unit,
) {
    val currentTime = System.currentTimeMillis()
    val isDoubleTap = currentTime - lastTapTime.value < 300

    if (isDoubleTap) {
        tapDelayJob.value?.cancel()

        if (tapPos.y in (viewSize.height / 4f)..(viewSize.height * 3 / 4f)) {
            onDoubleTapToolbar()
        }
        lastTapTime.value = 0L
    } else {
        lastTapTime.value = currentTime
        tapDelayJob.value?.cancel()

        tapDelayJob.value = scope.launch {
            delay(300)

            val linkHandled = pageViewState.handleClick(
                tapPos.x - offset.value.x,
                tapPos.y - offset.value.y
            )

            if (!linkHandled) {
                val isPageTurned = ViewUtils.handleTapGesture(
                    tapPos,
                    viewSize,
                    offset.value,
                    orientation,
                    pageViewState,
                    6f // keepPx
                ) { newOffset ->
                    offset.value = newOffset
                    pageViewState.updateOffset(offset.value)
                }

                if (!isPageTurned) {
                    val clickedPage = ViewUtils.calculateClickedPage(
                        tapPos,
                        offset.value,
                        orientation,
                        pageViewState
                    )
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
    viewSize: IntSize,
    pageViewState: PageViewState,
    offset: MutableState<Offset>,
    vZoom: Float,
    orientation: Int,
    scope: CoroutineScope,
    flingJob: MutableState<Job?>,
) {
    val decay = exponentialDecay<Offset>(
        frictionMultiplier = 0.35f,
        absVelocityThreshold = 0.50f,
    )

    val velocity = Offset(velocity.x * 1.1f, velocity.y * 1.1f)
    val job = scope.launch {
        val animatable = Animatable(
            initialValue = offset.value,
            typeConverter = OffsetToVector
        )

        animatable.animateDecay(velocity, decay) {
            val boundOffset = calculateBounds(
                value,
                vZoom,
                viewSize,
                pageViewState,
                orientation
            )
            offset.value = boundOffset
            pageViewState.updateOffset(offset.value)
        }
    }
    flingJob.value = job
}

/**
 * 边界计算逻辑
 */
internal fun calculateBounds(
    targetOffset: Offset,
    currentZoom: Float,
    size: IntSize,
    pageViewState: PageViewState,
    ori: Int
): Offset {
    val scaleRatio = currentZoom / pageViewState.vZoom
    val contentWidth =
        if (ori == Vertical) size.width * currentZoom else pageViewState.totalWidth * scaleRatio
    val contentHeight =
        if (ori == Vertical) pageViewState.totalHeight * scaleRatio else size.height * currentZoom

    val minX = if (contentWidth > size.width) size.width - contentWidth else 0f
    val minY = if (contentHeight > size.height) size.height - contentHeight else 0f

    return Offset(
        targetOffset.x.coerceIn(minX, 0f),
        targetOffset.y.coerceIn(minY, 0f)
    )
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
                    println("复制文本: $text")
                    onCopy(text)
                },
                onDismiss = onDismiss
            )
        }
    }
}


