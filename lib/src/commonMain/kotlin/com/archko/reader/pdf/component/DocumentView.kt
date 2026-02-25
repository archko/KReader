package com.archko.reader.pdf.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import com.archko.reader.pdf.state.AnnotationManager
import com.archko.reader.pdf.util.HyperLinkUtils
import com.archko.reader.pdf.util.ViewUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val max_zoom = 30f

@OptIn(ExperimentalComposeUiApi::class)
@Composable
public fun DocumentView(
    list: MutableList<APage>,
    state: ImageDecoder,
    jumpToPage: Int? = null,
    jumpMode: JumpMode = JumpMode.PageRestore,
    initialOrientation: Int,
    columnCount: Int,
    onSaveDocument: ((page: Int, pageCount: Int, zoom: Double, scrollX: Long, scrollY: Long, scrollOri: Long, reflow: Long, crop: Long) -> Unit)? = null,
    onCloseDocument: (() -> Unit)? = null,
    onDoubleTapToolbar: (() -> Unit)? = null, // 新增参数
    onPageChanged: ((page: Int) -> Unit)? = null, // 新增页面变化回调
    onTapNonPageArea: ((pageIndex: Int) -> Unit)? = null, // 新增：点击非翻页区域回调，传递页面索引
    initialScrollX: Long = 0L, // 初始X偏移量
    initialScrollY: Long = 0L, // 初始Y偏移量
    initialZoom: Double = 1.0, // 初始缩放比例
    reflow: Long = 0, // 初始缩放比例
    crop: Boolean = false, // 是否切边
    speakingPageIndex: Int? = null, // 正在朗读的页面索引
    gestureMode: GestureMode = GestureMode.VIEW,
    pathConfig: PathConfig,
    annotationManager: AnnotationManager,
    currentPath: String,
) {
    // 初始化状态
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var offset by remember {
        mutableStateOf(
            Offset(
                initialScrollX.toFloat(),
                initialScrollY.toFloat()
            )
        )
    }
    var vZoom by remember { mutableFloatStateOf(initialZoom.toFloat()) }
    var orientation by remember { mutableIntStateOf(initialOrientation) }
    var toPage by remember { mutableIntStateOf(-1) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val keepPx = with(density) { 6.dp.toPx() }
    var flingJob by remember { mutableStateOf<Job?>(null) }
    var isJumping by remember { mutableStateOf(false) } // 添加跳转标志
    var lastTapTime by remember { mutableLongStateOf(0L) } // 上次点击时间
    var tapDelayJob by remember { mutableStateOf<Job?>(null) } // 延迟处理点击的Job

    // 创建文本选择器 - 使用expect/actual模式
    val textSelector = remember {
        createTextSelector(currentPath) { pageIndex ->
            // 从PdfDecoder获取真实的StructuredText
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
            orientation,
            crop,
            columnCount = columnCount,
            textSelector = textSelector
        )
    }

    LaunchedEffect(columnCount) {
        pageViewState.updateColumnCount(columnCount)
        pageViewState.updateVisiblePages(offset, viewSize, vZoom)
    }

    LaunchedEffect(speakingPageIndex) {
        flingJob?.cancel()
        pageViewState.updateSpeakingPageIndex(speakingPageIndex)
    }

    // 文本选择相关状态
    var isTextSelecting by remember { mutableStateOf(false) }
    var selectedPage by remember { mutableStateOf<Page?>(null) }
    var showTextActionToolbar by remember { mutableStateOf(false) }
    var selectionStartPos by remember { mutableStateOf<Offset?>(null) }
    var selectionEndPos by remember { mutableStateOf<Offset?>(null) }

    // 临时存储当前正在画的线（比例坐标）
    val drawingPoints = remember { mutableStateListOf<Offset>() }
    var activeDrawingPage by remember { mutableIntStateOf(-1) }

    // 设置页面跳转回调
    LaunchedEffect(pageViewState) {
        pageViewState.onPageLinkClick = { pageIndex ->
            isJumping = true // 设置跳转标志
            val targetPage = pageViewState.pages[pageIndex]
            val newOffset = Offset(offset.x, -targetPage.bounds.top)
            offset = newOffset
            pageViewState.updateOffset(offset)
            isJumping = false
        }

        pageViewState.onUrlLinkClick = { url ->
            println("DocumentView: URL链接点击，URL: $url")
            HyperLinkUtils.openSystemBrowser(url)
        }
    }

    // 确保在 list 变化时重新计算总高度
    LaunchedEffect(list) {
        if (viewSize != IntSize.Zero) {
            println("DocumentView: 更新ViewSize:$viewSize, vZoom:$vZoom, list: ${list.size}, orientation: $orientation")
            pageViewState.updateViewSize(viewSize, vZoom, orientation)
        }
    }

    // 监听外部参数的变化
    LaunchedEffect(jumpToPage, initialOrientation, pageViewState.init) {
        //println("DocumentView: jumpToPage:$jumpToPage, initialOrientation:$initialOrientation, orientation:$orientation, init: ${pageViewState.init}")

        if (columnCount == 1 && initialOrientation != orientation && pageViewState.init) {
            isJumping = true // 设置跳转标志
            val currentPage = jumpToPage ?: 0  // 方向变化应该使用页码，而不依赖offset计算
            println("DocumentView: orientation改变，重置offset和zoom: $orientation->$initialOrientation, page:$currentPage")
            orientation = initialOrientation
            offset = Offset.Zero
            vZoom = 1f
            pageViewState.updateViewSize(viewSize, vZoom, orientation)

            // 用页码跳转到页面top/left
            val page = pageViewState.pages.getOrNull(currentPage)
            if (page != null) {
                if (orientation == Vertical) {
                    val clampedTargetY = page.bounds.top.coerceIn(
                        0f,
                        (pageViewState.totalHeight - viewSize.height).coerceAtLeast(0f)
                    )
                    val clampedY = -clampedTargetY
                    offset = Offset(offset.x, clampedY)
                } else {
                    val clampedTargetX = page.bounds.left.coerceIn(
                        0f,
                        (pageViewState.totalWidth - viewSize.width).coerceAtLeast(0f)
                    )
                    val clampedX = -clampedTargetX
                    offset = Offset(clampedX, offset.y)
                }
                println("DocumentView: orientation改变，跳转: $offset, $currentPage, page:$page")
                flingJob?.cancel()
                pageViewState.updateOffset(offset)
            }
            isJumping = false // 清除跳转标志
            return@LaunchedEffect
        }

        if (null != jumpToPage && toPage != jumpToPage && pageViewState.init) {
            isJumping = true // 设置跳转标志
            toPage = jumpToPage

            when (jumpMode) {
                JumpMode.PageRestore -> {
                    // 恢复模式：检查偏移量是否与页码匹配
                    val offsetX = initialScrollX.toFloat()
                    val offsetY = initialScrollY.toFloat()
                    val testOffset = Offset(offsetX, offsetY)
                    val offsetPage =
                        ViewUtils.firstPage(pageViewState, testOffset, orientation, viewSize, null)

                    if (offsetPage == toPage) {
                        // 偏移量准确，直接使用精确offset
                        offset = Offset(
                            offsetX.coerceIn(
                                -(pageViewState.totalWidth - viewSize.width).coerceAtLeast(
                                    0f
                                ), 0f
                            ),
                            offsetY.coerceIn(
                                -(pageViewState.totalHeight - viewSize.height).coerceAtLeast(
                                    0f
                                ), 0f
                            )
                        )
                    } else {
                        // 偏移量不准确或失效，用页码跳转到页面top/left
                        val page = pageViewState.pages.getOrNull(toPage)
                        if (page != null) {
                            val clampedTargetY = page.bounds.top.coerceIn(
                                0f,
                                (pageViewState.totalHeight - viewSize.height).coerceAtLeast(0f)
                            )
                            val clampedY = -clampedTargetY
                            val clampedTargetX = page.bounds.left.coerceIn(
                                0f,
                                (pageViewState.totalWidth - viewSize.width).coerceAtLeast(0f)
                            )
                            val clampedX = -clampedTargetX
                            offset = Offset(clampedX, clampedY)
                        }
                    }
                    println("DocumentView: PageRestore跳转 toPage=$toPage, offsetPage=$offsetPage, useOffset=${offsetPage == toPage}, offset:$testOffset")
                }

                JumpMode.PageNavigation -> {
                    // 导航模式：跳转到页面顶部
                    val page = pageViewState.pages.getOrNull(toPage)
                    if (page != null) {
                        if (orientation == Vertical) {
                            val clampedTargetY = page.bounds.top.coerceIn(
                                0f,
                                (pageViewState.totalHeight - viewSize.height).coerceAtLeast(0f)
                            )
                            val clampedY = -clampedTargetY
                            offset = Offset(offset.x, clampedY)
                        } else {
                            val clampedTargetX = page.bounds.left.coerceIn(
                                0f,
                                (pageViewState.totalWidth - viewSize.width).coerceAtLeast(0f)
                            )
                            val clampedX = -clampedTargetX
                            offset = Offset(clampedX, offset.y)
                        }
                        println("DocumentView: PageNavigation跳转到:$offset, top:${page.bounds.top}, toPage:$toPage")
                    } else {
                        println("DocumentView: PageNavigation找不到页面:$toPage")
                    }
                }
            }

            flingJob?.cancel()
            pageViewState.updateOffset(offset)
            isJumping = false // 清除跳转标志
        }
    }

    LaunchedEffect(crop) {
        val old = pageViewState.isCropEnabled()
        if (old != crop) {
            println("DocumentView: 切边变化:$crop")
            pageViewState.setCropEnabled(crop)
            // 清理所有页面的缓存图像
            pageViewState.pages.forEach { page ->
                page.recycle()
            }
            ImageCache.clear()
            pageViewState.invalidatePageSizes()
            pageViewState.updateOffset(offset)
        }
    }

    // 监听页面变化并回调
    LaunchedEffect(offset) {
        // 只有在非跳转状态下才处理页面变化回调
        if (!isJumping) {
            ViewUtils.firstPage(pageViewState, offset, orientation, viewSize, onPageChanged)
        }
    }

    // 获取生命周期所有者
    val lifecycleOwner = LocalLifecycleOwner.current

    // 保存文档状态的公共方法
    fun saveDocumentState() {
        val pages = pageViewState.pages
        var currentPage = 0
        if (pages.isNotEmpty()) {
            val offsetY = offset.y
            val offsetX = offset.x
            val firstVisible = pages.indexOfFirst { page ->
                if (orientation == Vertical) {
                    val top = -offsetY
                    val bottom = top + viewSize.height
                    page.bounds.bottom > top && page.bounds.top < bottom
                } else {
                    val left = -offsetX
                    val right = left + viewSize.width
                    page.bounds.right > left && page.bounds.left < right
                }
            }
            if (firstVisible != -1) {
                currentPage = firstVisible
            }
        }
        val pageCount = list.size
        val zoom = vZoom.toDouble()
        println("DocumentView: 保存记录:page:$currentPage, pc:$pageCount, $viewSize, vZoom:$vZoom, list: ${list.size}, orientation: $orientation, crop: $crop,${pageViewState.isCropEnabled()}")

        if (!list.isEmpty()) {
            onSaveDocument?.invoke(
                currentPage,
                pageCount,
                zoom,
                offset.x.toLong(),
                offset.y.toLong(),
                orientation.toLong(),
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
            // 取消延迟的点击处理
            tapDelayJob?.cancel()
            // 在组件销毁时也保存一次状态
            saveDocumentState()
            onCloseDocument?.invoke()
            pageViewState.shutdown()
            ImageCache.clear()
        }
    }

    // 优化的 Fling 执行器
    fun performFling(velocity: Velocity, viewSize: IntSize, pageViewState: PageViewState) {
        val decay = exponentialDecay<Offset>(
            frictionMultiplier = 0.35f, // (摩擦系数) 作用：它决定了减速度的大小。数值越大，摩擦力越大，速度降得越快，滑动距离越短。
            absVelocityThreshold = 0.50f //(绝对速度阈值) 作用：它定义了动画“停止”的临界点。当滑动速度降到这个值以下时，动画会立即结束（不再继续计算微小的位移）。
        )

        val velocity = Offset(velocity.x * 1.1f, velocity.y * 1.1f)
        flingJob = scope.launch {
            val animatable = Animatable(
                initialValue = offset,
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
                offset = boundOffset
                //println("DocumentView.animateDecay:$offset")
                pageViewState.updateOffset(offset)
            }
        }
    }

    fun handleTapGestureInternal(tapPos: Offset) {
        val currentTime = System.currentTimeMillis()
        // 判定是否为双击（300ms内）
        val isDoubleTap = currentTime - lastTapTime < 300

        if (isDoubleTap) {
            // 如果是双击，取消掉之前挂起的单击任务
            tapDelayJob?.cancel()

            // 只有在屏幕中间区域（高度的1/4到3/4之间）双击才触发工具栏
            if (tapPos.y in (viewSize.height / 4f)..(viewSize.height * 3 / 4f)) {
                onDoubleTapToolbar?.invoke()
            }
            // 重置时间防止连续三次点击触发两次双击
            lastTapTime = 0L
        } else {
            lastTapTime = currentTime
            tapDelayJob?.cancel()

            tapDelayJob = scope.launch {
                delay(300)

                // 1. 尝试处理超链接点击
                // 注意：tapPos 是相对于屏幕的，需要转换成相对于内容页面的坐标
                val linkHandled = pageViewState.handleClick(
                    tapPos.x - offset.x,
                    tapPos.y - offset.y
                )

                // 2. 如果没有命中链接，则处理翻页点击
                if (!linkHandled) {
                    val isPageTurned = ViewUtils.handleTapGesture(
                        tapPos,
                        viewSize,
                        offset,
                        orientation,
                        pageViewState,
                        keepPx
                    ) { newOffset ->
                        offset = newOffset
                        pageViewState.updateOffset(offset)
                    }

                    // 如果不是翻页区域，触发非页面区域点击回调
                    if (!isPageTurned) {
                        val clickedPage = ViewUtils.calculateClickedPage(
                            tapPos,
                            offset,
                            orientation,
                            pageViewState
                        )
                        onTapNonPageArea?.invoke(clickedPage)
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                if (viewSize == size) {
                    return@onSizeChanged
                }
                viewSize = size
                println("DocumentView: onSizeChanged:$viewSize, vZoom:$vZoom, list: ${list.size}, orientation: $orientation")
                pageViewState.updateViewSize(viewSize, vZoom, orientation)
            },
        contentAlignment = Alignment.TopStart
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .pointerInput(gestureMode, pathConfig) {
                    when (gestureMode) {
                        GestureMode.SELECTION -> {
                            // 文本选择模式
                            detectDragGestures(
                                onDragStart = { pos ->
                                    isTextSelecting = true
                                    selectionStartPos = pos
                                    showTextActionToolbar = false

                                    // 找到点击的页面并开始选择
                                    val clickedPageIndex = ViewUtils.calculateClickedPage(
                                        pos,
                                        offset,
                                        orientation,
                                        pageViewState
                                    )
                                    pageViewState.pages.getOrNull(clickedPageIndex)?.let {
                                        it.startTextSelection(pos.x - offset.x, pos.y - offset.y)
                                        selectedPage = it
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    selectionEndPos = change.position
                                    selectedPage?.updateTextSelection(
                                        change.position.x - offset.x,
                                        change.position.y - offset.y
                                    )
                                },
                                onDragEnd = {
                                    val selection = selectedPage?.endTextSelection()
                                    isTextSelecting = false
                                    if (selection != null && selection.text.isNotBlank()) {
                                        showTextActionToolbar = true
                                    }
                                }
                            )
                        }

                        GestureMode.DRAW -> {
                            detectDragGestures(
                                onDragStart = { startOffset ->
                                    drawingPoints.clear()
                                    val targetPage = pageViewState.pageToRender.find {
                                        it.bounds.contains(startOffset - offset)
                                    }
                                    activeDrawingPage = targetPage?.aPage?.index ?: -1

                                    // 添加起始点
                                    if (activeDrawingPage != -1) {
                                        val targetPage =
                                            pageViewState.pageToRender.find { it.aPage.index == activeDrawingPage }!!
                                        val localX = startOffset.x - offset.x - targetPage.xOffset
                                        val localY = startOffset.y - offset.y - targetPage.yOffset
                                        val startRel = Offset(
                                            localX / targetPage.width,
                                            localY / targetPage.height
                                        )
                                        drawingPoints.add(startRel)
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    if (activeDrawingPage != -1) {
                                        val targetPage =
                                            pageViewState.pageToRender.find { it.aPage.index == activeDrawingPage }!!
                                        val localX =
                                            change.position.x - offset.x - targetPage.xOffset
                                        val localY =
                                            change.position.y - offset.y - targetPage.yOffset
                                        val currentRel = Offset(
                                            localX / targetPage.width,
                                            localY / targetPage.height
                                        )

                                        if (pathConfig.drawType == DrawType.LINE) {
                                            // 直线模式：始终只有起点和当前点
                                            val startRel = drawingPoints.first()
                                            val linePoints =
                                                ViewUtils.calculateLinePoints(startRel, currentRel)
                                            pageViewState.updateDrawing(
                                                activeDrawingPage,
                                                linePoints,
                                                pathConfig
                                            )
                                        } else {
                                            // 曲线模式：添加所有拖拽点
                                            drawingPoints.add(currentRel)
                                            pageViewState.updateDrawing(
                                                activeDrawingPage,
                                                drawingPoints.toList(),
                                                pathConfig
                                            )
                                        }
                                        change.consume()
                                    }
                                },
                                onDragEnd = {
                                    if (activeDrawingPage != -1) {
                                        val finalPoints =
                                            if (pathConfig.drawType == DrawType.LINE) {
                                                // 直线模式：使用当前绘制的点（起点和终点）
                                                pageViewState.activeDrawingAnno?.second?.points
                                                    ?: emptyList()
                                            } else {
                                                // 曲线模式：使用所有收集的点
                                                drawingPoints.toList()
                                            }
                                        pageViewState.finalizeDrawing(
                                            activeDrawingPage,
                                            finalPoints,
                                            pathConfig
                                        )
                                    }
                                    drawingPoints.clear()
                                    activeDrawingPage = -1
                                }
                            )
                        }

                        GestureMode.VIEW -> {
                            // 阅读模式：优化后的 拖动、缩放 与 惯性
                            awaitEachGesture {
                                val velocityTracker = VelocityTracker()
                                var isZooming = false
                                var lastChange: PointerInputChange? = null

                                val down = awaitFirstDown(requireUnconsumed = false)
                                flingJob?.cancel() // 触摸即停

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
                                            val centroid =
                                                event.calculateCentroid(useCurrent = false)
                                            if (centroid.isSpecified && zoomChange != 1f) {
                                                val newZoom =
                                                    (vZoom * zoomChange).coerceIn(1f, max_zoom)
                                                val zoomFactor = newZoom / vZoom

                                                // 计算缩放中心点：手势中心相对于内容的位置
                                                // centroid 是手势中心在视图中的位置
                                                // 需要将其转换为相对于内容的位置
                                                val contentCenterX = centroid.x - offset.x
                                                val contentCenterY = centroid.y - offset.y

                                                // 计算新的偏移量，保持内容中心点不变
                                                val newOffsetX =
                                                    centroid.x - contentCenterX * zoomFactor
                                                val newOffsetY =
                                                    centroid.y - contentCenterY * zoomFactor

                                                vZoom = newZoom
                                                val targetOffset = Offset(newOffsetX, newOffsetY)
                                                if (targetOffset.isSpecified) {
                                                    offset = calculateBounds(
                                                        targetOffset,
                                                        vZoom,
                                                        viewSize,
                                                        pageViewState,
                                                        orientation
                                                    )
                                                }
                                            }
                                        } else {
                                            // 拖动
                                            val newOffset = offset + panChange
                                            offset = calculateBounds(
                                                newOffset,
                                                vZoom,
                                                viewSize,
                                                pageViewState,
                                                orientation
                                            )
                                            pageViewState.updateOffset(offset)

                                            // 这里的 velocityTracker 记录的是指针位置，不是 offset 变量，这样更平滑
                                            velocityTracker.addPosition(
                                                lastChange?.uptimeMillis ?: 0,
                                                lastChange?.position ?: Offset(0f, 0f)
                                            )
                                        }
                                        event.changes.fastForEach { if (it.positionChanged()) it.consume() }
                                    }
                                } while (event.changes.fastAny { it.pressed })

                                // --- 抬手后的处理 ---
                                val finalChange = lastChange ?: return@awaitEachGesture
                                val dragDistance =
                                    (finalChange.position - down.position).getDistance()

                                if (dragDistance < 10f && !isZooming) {
                                    handleTapGestureInternal(finalChange.position)
                                } else if (!isZooming) {
                                    val velocity = velocityTracker.calculateVelocity()
                                    performFling(velocity, viewSize, pageViewState)
                                }

                                if (isZooming) {
                                    //先计算偏移,否则绘制刷新会有问题
                                    pageViewState.updateOffset(offset)
                                    pageViewState.updateViewSize(viewSize, vZoom, orientation)
                                }
                                pageViewState.updateVisiblePages(offset, viewSize, vZoom)
                            }
                        }
                    }
                }
                .graphicsLayer {
                    // graphicsLayer 中读取 offset，只触发绘图层位移，不触发 Recomposition
                    translationX = offset.x
                    translationY = offset.y
                }
        ) {
            //居中绘制不够屏幕高宽
            /*val centerOffsetX =
                if (orientation == Horizontal && pageViewState.totalWidth < viewSize.width) {
                    (viewSize.width - pageViewState.totalWidth) / 2
                } else 0f
            val centerOffsetY =
                if (orientation == Vertical && pageViewState.totalHeight < viewSize.height) {
                    (viewSize.height - pageViewState.totalHeight) / 2
                } else 0f*/
            //translate(left = offset.x + centerOffsetX, top = offset.y + centerOffsetY) {
                pageViewState.drawVisiblePages(this, offset, vZoom)

                if (isTextSelecting && selectionStartPos != null && selectionEndPos != null) {
                    val start = selectionStartPos!!
                    val end = selectionEndPos!!
                    val left = minOf(start.x, end.x) - offset.x
                    val top = minOf(start.y, end.y) - offset.y
                    val right = maxOf(start.x, end.x) - offset.x
                    val bottom = maxOf(start.y, end.y) - offset.y

                    drawRect(
                        color = Color.Blue.copy(alpha = 0.3f),
                        topLeft = Offset(left, top),
                        size = androidx.compose.ui.geometry.Size(right - left, bottom - top)
                    )
                }
            //}
        }
    }

    // 文本操作工具栏
    if (showTextActionToolbar && selectedPage?.currentSelection != null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            TextActionToolbar(
                selectedPage = selectedPage,
                textSelector = textSelector,
                onCopy = { text ->
                    // 复制到剪贴板
                    println("复制文本: $text")
                    // 这里需要实现剪贴板操作
                    showTextActionToolbar = false
                    selectedPage?.clearTextSelection()
                    selectedPage = null
                    selectionStartPos = null
                    selectionEndPos = null
                },
                onDismiss = {
                    showTextActionToolbar = false
                    selectedPage?.clearTextSelection()
                    selectedPage = null
                    selectionStartPos = null
                    selectionEndPos = null
                }
            )
        }
    }
}

// 边界检查逻辑
private fun calculateBounds(
    targetOffset: Offset,
    currentZoom: Float,
    size: IntSize,
    state: PageViewState,
    ori: Int
): Offset {
    // 在缩放过程中，需要根据当前缩放比例调整总高度
    val scaleRatio = currentZoom / state.vZoom
    val contentWidth =
        if (ori == Vertical) size.width * currentZoom else state.totalWidth * scaleRatio
    val contentHeight =
        if (ori == Vertical) state.totalHeight * scaleRatio else size.height * currentZoom

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