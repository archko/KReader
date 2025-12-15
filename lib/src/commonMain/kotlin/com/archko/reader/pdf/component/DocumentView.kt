package com.archko.reader.pdf.component

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
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
import com.archko.reader.pdf.util.HyperLinkUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs

public const val Vertical: Int = 0
public const val Horizontal: Int = 1

public const val velocityDistance: Float = 50f

public data class JumpIntent(
    val page: Int = 0,
    val mode: JumpMode = JumpMode.PageRestore
)

public sealed class JumpMode {
    public object PageRestore : JumpMode()     // 书签恢复，使用精确offset
    public object PageNavigation : JumpMode()  // 页面跳转，使用页面顶部
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
public fun DocumentView(
    list: MutableList<APage>,
    state: ImageDecoder,
    jumpToPage: Int? = null,
    jumpMode: JumpMode = JumpMode.PageRestore,
    initialOrientation: Int,
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
    isTextSelectionMode: Boolean = false, // 是否为文本选择模式
    speakingPageIndex: Int? = null, // 正在朗读的页面索引
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
        createTextSelector { pageIndex ->
            // 从PdfDecoder获取真实的StructuredText
            val structuredText = state.getStructuredText(pageIndex)
            if (structuredText != null) {
                createStructuredTextImpl(structuredText)
            } else {
                null
            }
        }
    }

    val pageViewState = remember(list) {
        println("DocumentView: 创建新的PageViewState:$viewSize, vZoom:$vZoom，list: ${list.size}, orientation: $orientation")
        PageViewState(list, state, orientation, crop, textSelector)
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

        if (initialOrientation != orientation && pageViewState.init) {
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
                        firstPage(pageViewState, testOffset, orientation, viewSize, null)

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
            firstPage(pageViewState, offset, orientation, viewSize, onPageChanged)
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
        var renderTrigger by remember { mutableIntStateOf(0) }

        LaunchedEffect(pageViewState.renderFlow, Unit) {
            pageViewState.renderFlow.collect {
                //println("收到渲染更新通知")
                renderTrigger++
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .pointerInput("gestures", isTextSelectionMode) {
                    awaitEachGesture {
                        var zooming = false
                        var dragging = false
                        // pan惯性
                        val panVelocityTracker = VelocityTracker()
                        var pan: Offset
                        var totalDrag = Offset.Zero
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val wasFlingActive = flingJob?.isActive == true // 记录按下时是否有fling动画
                        try {
                            pan = Offset.Zero
                            totalDrag = Offset.Zero
                            panVelocityTracker.resetTracking()
                            flingJob?.cancel()
                            do {
                                val event = awaitPointerEvent()
                                val pointerCount = event.changes.size
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                val centroid = event.calculateCentroid()
                                // 采集pan速度
                                val uptime =
                                    event.changes.maxByOrNull { it.uptimeMillis }?.uptimeMillis
                                        ?: 0L
                                pan += panChange
                                totalDrag += panChange
                                panVelocityTracker.addPosition(uptime, -panChange)

                                // 检测是否开始拖拽
                                if (totalDrag.getDistance() > 10f) {
                                    dragging = true
                                }
                                if (pointerCount > 1) {
                                    zooming = true
                                    val newZoom = (zoomChange * vZoom).coerceIn(1f, 10f)
                                    val zoomFactor = newZoom / vZoom

                                    // 计算缩放中心点：手势中心相对于内容的位置
                                    // centroid 是手势中心在视图中的位置
                                    // 需要将其转换为相对于内容的位置
                                    val contentCenterX = centroid.x - offset.x
                                    val contentCenterY = centroid.y - offset.y

                                    // 计算新的偏移量，保持内容中心点不变
                                    val newOffsetX = centroid.x - contentCenterX * zoomFactor
                                    val newOffsetY = centroid.y - contentCenterY * zoomFactor

                                    vZoom = newZoom
                                    offset = Offset(newOffsetX, newOffsetY)

                                    // 边界检查
                                    if (orientation == Vertical) {
                                        val scaledWidth = viewSize.width * vZoom
                                        // 在缩放过程中，需要根据当前缩放比例调整总高度
                                        val scaleRatio = vZoom / pageViewState.vZoom
                                        val scaledHeight = pageViewState.totalHeight * scaleRatio
                                        val minX = minOf(0f, viewSize.width - scaledWidth)
                                        val maxX = 0f
                                        val minY =
                                            if (scaledHeight > viewSize.height) viewSize.height - scaledHeight else 0f
                                        val maxY = 0f
                                        offset = Offset(
                                            offset.x.coerceIn(minX, maxX),
                                            offset.y.coerceIn(minY, maxY)
                                        )
                                    } else {
                                        val scaledHeight = viewSize.height * vZoom
                                        val scaleRatio = vZoom / pageViewState.vZoom
                                        val scaledWidth = pageViewState.totalWidth * scaleRatio
                                        val minY = minOf(0f, viewSize.height - scaledHeight)
                                        val maxY = 0f
                                        val minX =
                                            if (scaledWidth > viewSize.width) viewSize.width - scaledWidth else 0f
                                        val maxX = 0f
                                        offset = Offset(
                                            offset.x.coerceIn(minX, maxX),
                                            offset.y.coerceIn(minY, maxY)
                                        )
                                    }
                                    //pageViewState.updateOffset(offset) //缩放过程不更新偏移,否则会导致页面跳动
                                    event.changes.fastForEach { if (it.positionChanged()) it.consume() }
                                } else {
                                    // 单指拖动
                                    if (!zooming) {
                                        if (isTextSelectionMode && !isTextSelecting) {
                                            // 文本选择模式：开始文本选择
                                            isTextSelecting = true
                                            selectionStartPos = down.position
                                            selectionEndPos = down.position + totalDrag
                                            showTextActionToolbar = false

                                            // 找到点击的页面并开始选择
                                            val clickedPageIndex = calculateClickedPage(
                                                down.position,
                                                offset,
                                                orientation,
                                                pageViewState
                                            )
                                            val clickedPage =
                                                pageViewState.pages.getOrNull(clickedPageIndex)
                                            if (clickedPage != null) {
                                                val contentX = down.position.x - offset.x
                                                val contentY = down.position.y - offset.y
                                                clickedPage.startTextSelection(contentX, contentY)
                                                selectedPage = clickedPage
                                            }
                                        } else if (isTextSelectionMode && isTextSelecting) {
                                            // 文本选择模式：更新文本选择
                                            selectionEndPos = down.position + totalDrag

                                            selectedPage?.let { page ->
                                                val contentX =
                                                    (down.position + totalDrag).x - offset.x
                                                val contentY =
                                                    (down.position + totalDrag).y - offset.y
                                                page.updateTextSelection(contentX, contentY)
                                            }
                                        } else {
                                            // 普通拖拽模式：滚动页面
                                            offset += panChange
                                            if (orientation == Vertical) {
                                                val scaledWidth = viewSize.width * vZoom
                                                val scaledHeight = pageViewState.totalHeight
                                                val minX = minOf(0f, viewSize.width - scaledWidth)
                                                val maxX = 0f
                                                val minY =
                                                    if (scaledHeight > viewSize.height) viewSize.height - scaledHeight else 0f
                                                val maxY = 0f
                                                offset = Offset(
                                                    offset.x.coerceIn(minX, maxX),
                                                    offset.y.coerceIn(minY, maxY)
                                                )
                                            } else {
                                                val scaledHeight = viewSize.height * vZoom
                                                val scaledWidth = pageViewState.totalWidth
                                                val minY = minOf(0f, viewSize.height - scaledHeight)
                                                val maxY = 0f
                                                val minX =
                                                    if (scaledWidth > viewSize.width) viewSize.width - scaledWidth else 0f
                                                val maxX = 0f
                                                offset = Offset(
                                                    offset.x.coerceIn(minX, maxX),
                                                    offset.y.coerceIn(minY, maxY)
                                                )
                                            }
                                            pageViewState.updateOffset(offset)
                                        }
                                    }
                                }
                                event.changes.fastForEach { if (it.positionChanged()) it.consume() }
                            } while (event.changes.fastAny { it.pressed })
                        } catch (_: CancellationException) {
                        } finally {
                            // 缩放结束后调用 updateViewSize 重新计算页面
                            if (zooming) {
                                pageViewState.updateOffset(offset)
                                pageViewState.updateViewSize(viewSize, vZoom, orientation)
                            }
                            pageViewState.updateVisiblePages(offset, viewSize, vZoom)

                            // 处理文本选择结束
                            if (isTextSelectionMode && isTextSelecting) {
                                val selection = selectedPage?.endTextSelection()
                                isTextSelecting = false

                                if (selection != null && selection.text.isNotBlank()) {
                                    showTextActionToolbar = true
                                    println("文本选择完成: ${selection.text}")
                                } else {
                                    // 如果没有选中文本，清理状态
                                    selectedPage?.clearTextSelection()
                                    selectedPage = null
                                    selectionStartPos = null
                                    selectionEndPos = null
                                }
                                return@awaitEachGesture
                            }

                            // 如果没有拖拽和缩放，且按下时没有fling动画，处理点击事件
                            if (!dragging && !zooming && !wasFlingActive && totalDrag.getDistance() < 10f) {
                                val tapOffset = down.position
                                val currentTime = System.currentTimeMillis()

                                // 检查是否是双击（简化检测，只检查时间间隔）
                                val isDoubleTap = currentTime - lastTapTime < 300

                                if (isDoubleTap) {
                                    // 取消延迟的单击处理
                                    tapDelayJob?.cancel()

                                    // 处理双击
                                    val y = tapOffset.y
                                    val height = viewSize.height.toFloat()
                                    if (y >= height / 4 && y <= height * 3 / 4) {
                                        onDoubleTapToolbar?.invoke()
                                    }
                                } else {
                                    // 延迟处理单击，等待可能的第二次点击
                                    tapDelayJob?.cancel()
                                    tapDelayJob = scope.launch {
                                        delay(300) // 等待300ms

                                        // 如果有文本选择工具栏显示，先隐藏它
                                        if (showTextActionToolbar) {
                                            showTextActionToolbar = false
                                            selectedPage?.clearTextSelection()
                                            selectedPage = null
                                            selectionStartPos = null
                                            selectionEndPos = null
                                            return@launch
                                        }

                                        // 将点击坐标转换为相对于内容的位置
                                        val contentX = tapOffset.x - offset.x
                                        val contentY = tapOffset.y - offset.y

                                        // 首先尝试处理链接点击
                                        val linkHandled =
                                            pageViewState.handleClick(contentX, contentY)
                                        //println("DocumentView.onTap: 链接处理结果: $linkHandled")

                                        // 如果没有处理链接，再处理翻页逻辑
                                        if (!linkHandled) {
                                            val isPageTurned = handleTapGesture(
                                                tapOffset,
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
                                                val clickedPage =
                                                    calculateClickedPage(
                                                        tapOffset,
                                                        offset,
                                                        orientation,
                                                        pageViewState
                                                    )
                                                onTapNonPageArea?.invoke(clickedPage)
                                            }
                                        }
                                    }
                                }

                                lastTapTime = currentTime
                            }
                            // 计算pan velocity
                            val velocity = runCatching { panVelocityTracker.calculateVelocity() }
                                .getOrDefault(
                                    Velocity.Zero
                                )
                            val velocitySquared = velocity.x * velocity.x + velocity.y * velocity.y
                            val velocityThreshold = with(density) { 32.dp.toPx() * 32.dp.toPx() }
                            flingJob?.cancel()
                            if (velocitySquared > velocityThreshold) {
                                val decayAnimationSpec = exponentialDecay<Float>(
                                    frictionMultiplier = 0.4f,
                                    absVelocityThreshold = 0.45f
                                )
                                flingJob = scope.launch {
                                    try {
                                        if (orientation == Vertical) {
                                            // X方向
                                            if (abs(velocity.x) > velocityDistance) {
                                                val animX = AnimationState(
                                                    initialValue = offset.x,
                                                    initialVelocity = velocity.x
                                                )
                                                launch {
                                                    animX.animateDecay(decayAnimationSpec) {
                                                        val scaledWidth = viewSize.width * vZoom
                                                        val minX =
                                                            minOf(0f, viewSize.width - scaledWidth)
                                                        val maxX = 0f
                                                        // 2. 检查边界：如果增量导致越界，则取消动画并设置到边界
                                                        if (value > maxX || value < minX) {
                                                            cancelAnimation()
                                                        }

                                                        val newX = value.coerceIn(minX, maxX)
                                                        offset = Offset(newX, offset.y)
                                                        pageViewState.updateOffset(offset)
                                                    }
                                                }
                                            }
                                            // Y方向
                                            if (abs(velocity.y) > velocityDistance) {
                                                val animY = AnimationState(
                                                    initialValue = offset.y,
                                                    initialVelocity = velocity.y
                                                )
                                                launch {
                                                    animY.animateDecay(decayAnimationSpec) {
                                                        val scaledHeight =
                                                            if (orientation == Vertical) {
                                                                pageViewState.totalHeight
                                                            } else {
                                                                pageViewState.totalWidth
                                                            }
                                                        val minY =
                                                            if (scaledHeight > viewSize.height) viewSize.height - scaledHeight else 0f
                                                        val maxY = 0f
                                                        if (value > maxY || value < minY) {
                                                            cancelAnimation()
                                                        }

                                                        val newY = value.coerceIn(minY, maxY)
                                                        offset = Offset(offset.x, newY)
                                                        pageViewState.updateOffset(offset)
                                                    }
                                                }
                                            }
                                        } else {
                                            // X方向
                                            if (abs(velocity.x) > velocityDistance) {
                                                val animX = AnimationState(
                                                    initialValue = offset.x,
                                                    initialVelocity = velocity.x
                                                )
                                                launch {
                                                    animX.animateDecay(decayAnimationSpec) {
                                                        val scaledWidth =
                                                            if (orientation == Vertical) {
                                                                pageViewState.totalHeight
                                                            } else {
                                                                pageViewState.totalWidth
                                                            }
                                                        val minX =
                                                            minOf(0f, viewSize.width - scaledWidth)
                                                        val maxX = 0f
                                                        if (value > maxX || value < minX) {
                                                            cancelAnimation()
                                                        }

                                                        val newX = value.coerceIn(minX, maxX)
                                                        offset = Offset(newX, offset.y)
                                                        pageViewState.updateOffset(offset)
                                                    }
                                                }
                                            }
                                            // Y方向
                                            if (abs(velocity.y) > velocityDistance) {
                                                val animY = AnimationState(
                                                    initialValue = offset.y,
                                                    initialVelocity = velocity.y
                                                )
                                                launch {
                                                    animY.animateDecay(decayAnimationSpec) {
                                                        val scaledHeight = viewSize.height * vZoom
                                                        val minY = minOf(
                                                            0f,
                                                            viewSize.height - scaledHeight
                                                        )
                                                        val maxY = 0f
                                                        if (value > maxY || value < minY) {
                                                            cancelAnimation()
                                                        }

                                                        val newY = value.coerceIn(minY, maxY)
                                                        offset = Offset(offset.x, newY)
                                                        pageViewState.updateOffset(offset)
                                                    }
                                                }
                                            }
                                        }
                                    } finally {
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            //居中绘制不够屏幕高宽
            val centerOffsetX =
                if (orientation == Horizontal && pageViewState.totalWidth < viewSize.width) {
                    (viewSize.width - pageViewState.totalWidth) / 2
                } else 0f
            val centerOffsetY =
                if (orientation == Vertical && pageViewState.totalHeight < viewSize.height) {
                    (viewSize.height - pageViewState.totalHeight) / 2
                } else 0f
            translate(left = offset.x + centerOffsetX, top = offset.y + centerOffsetY) {
                pageViewState.drawVisiblePages(this, offset, vZoom)

                // 绘制选择区域的调试可视化
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
            }
        }
    }

    // 文本操作工具栏
    if (showTextActionToolbar && selectedPage?.currentSelection != null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            TextActionToolbar(
                selection = selectedPage!!.currentSelection!!,
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

private fun firstPage(
    pageViewState: PageViewState,
    offset: Offset,
    orientation: Int,
    viewSize: IntSize,
    onPageChanged: ((Int) -> Unit)?
): Int {
    var firstVisible = 0
    val pages = pageViewState.pages
    if (pages.isNotEmpty()) {
        val offsetY = offset.y
        val offsetX = offset.x
        firstVisible = pages.indexOfFirst { page ->
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
            onPageChanged?.invoke(firstVisible)
        } else {
            firstVisible = 0
            println("firstPage.error:$offset, ori:$orientation, view:$viewSize")
        }
    }
    return firstVisible
}

/**
 * 根据点击坐标计算点击的页面索引
 */
private fun calculateClickedPage(
    tapOffset: Offset,
    currentOffset: Offset,
    orientation: Int,
    pageViewState: PageViewState
): Int {
    // 将点击坐标转换为相对于内容的位置
    val contentX = tapOffset.x - currentOffset.x
    val contentY = tapOffset.y - currentOffset.y

    // 查找包含该坐标的页面
    val pages = pageViewState.pages
    for (i in pages.indices) {
        val page = pages[i]
        if (orientation == Vertical) {
            // 垂直模式：检查Y坐标是否在页面范围内
            if (contentY >= page.bounds.top && contentY <= page.bounds.bottom) {
                return i
            }
        } else {
            // 水平模式：检查X坐标是否在页面范围内
            if (contentX >= page.bounds.left && contentX <= page.bounds.right) {
                return i
            }
        }
    }

    // 如果没有找到匹配的页面，返回第一个可见页面
    return pages.indexOfFirst { page ->
        if (orientation == Vertical) {
            val top = -currentOffset.y
            val bottom = top + pageViewState.viewSize.height
            page.bounds.bottom > top && page.bounds.top < bottom
        } else {
            val left = -currentOffset.x
            val right = left + pageViewState.viewSize.width
            page.bounds.right > left && page.bounds.left < right
        }
    }.coerceAtLeast(0)
}

/**
 * 处理点击手势的公共方法，避免重复代码
 * @return 是否发生了翻页操作
 */
private fun handleTapGesture(
    offsetTap: Offset,
    viewSize: IntSize,
    currentOffset: Offset,
    orientation: Int,
    pageViewState: PageViewState,
    keepPx: Float,
    onOffsetChanged: (Offset) -> Unit
): Boolean {
    if (orientation == Vertical) {
        // 垂直方向：上下翻页
        val y = offsetTap.y
        val height = viewSize.height.toFloat()
        return when {
            y < height / 4 -> {
                // 点击上方区域，向上翻页
                val newY = (currentOffset.y + viewSize.height - keepPx).coerceAtMost(0f)
                onOffsetChanged(Offset(currentOffset.x, newY))
                true
            }

            y > height * 3 / 4 -> {
                // 点击下方区域，向下翻页
                val maxY = (pageViewState.totalHeight - viewSize.height).coerceAtLeast(0f)
                val newY = (currentOffset.y - viewSize.height + keepPx).coerceAtLeast(-maxY)
                onOffsetChanged(Offset(currentOffset.x, newY))
                true
            }

            else -> false // 点击中间区域，不是翻页
        }
    } else {
        // 水平方向：左右翻页
        val x = offsetTap.x
        val width = viewSize.width.toFloat()
        return when {
            x < width / 4 -> {
                // 点击左侧区域，向左翻页
                val newX = (currentOffset.x + viewSize.width - keepPx).coerceAtMost(0f)
                onOffsetChanged(Offset(newX, currentOffset.y))
                true
            }

            x > width * 3 / 4 -> {
                // 点击右侧区域，向右翻页
                val maxX = (pageViewState.totalWidth - viewSize.width).coerceAtLeast(0f)
                val newX = (currentOffset.x - viewSize.width + keepPx).coerceAtLeast(-maxX)
                onOffsetChanged(Offset(newX, currentOffset.y))
                true
            }

            else -> false // 点击中间区域，不是翻页
        }
    }
}

/**
 * 文本操作工具栏
 */
@Composable
public fun TextActionToolbar(
    selection: TextSelection,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 80.dp, horizontal = 40.dp),
        color = Color.Black.copy(alpha = 0.8f),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Selected Text",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                SelectionContainer {
                    Text(
                        text = selection.text,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .padding(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
            ) {
                TextButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(selection.text))
                        onCopy(selection.text)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Text("Copy")
                }

                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}
