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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs

public const val Vertical: Int = 0
public const val Horizontal: Int = 1

public const val velocityDistance: Float = 50f

@OptIn(ExperimentalComposeUiApi::class)
@Composable
public fun DocumentView(
    list: MutableList<APage>,
    state: ImageDecoder,
    jumpToPage: Int? = null,
    align: PdfViewState.Align = PdfViewState.Align.Top,
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

    val pdfViewState = remember(list) {
        println("DocumentView: 创建新的PdfViewState:$viewSize, vZoom:$vZoom，list: ${list.size}, orientation: $orientation")
        PdfViewState(list, state, orientation, crop, textSelector)
    }

    // 文本选择相关状态
    var isTextSelecting by remember { mutableStateOf(false) }
    var selectedPage by remember { mutableStateOf<Page?>(null) }
    var showTextActionToolbar by remember { mutableStateOf(false) }
    var selectionStartPos by remember { mutableStateOf<Offset?>(null) }
    var selectionEndPos by remember { mutableStateOf<Offset?>(null) }

    // 设置页面跳转回调
    LaunchedEffect(pdfViewState) {
        pdfViewState.onPageLinkClick = { pageIndex ->
            isJumping = true // 设置跳转标志
            val targetPage = pdfViewState.pages[pageIndex]
            val newOffset = Offset(offset.x, -targetPage.bounds.top)
            offset = newOffset
            pdfViewState.updateOffset(offset)
            isJumping = false
        }

        pdfViewState.onUrlLinkClick = { url ->
            println("DocumentView: URL链接点击，URL: $url")
            // 这里可以添加打开URL的逻辑，比如调用系统浏览器
        }
    }

    // 确保在 list 变化时重新计算总高度
    LaunchedEffect(list) {
        if (viewSize != IntSize.Zero) {
            println("DocumentView: 更新ViewSize:$viewSize, vZoom:$vZoom, list: ${list.size}, orientation: $orientation")
            pdfViewState.updateViewSize(viewSize, vZoom, orientation)
        }
    }

    // 监听外部参数的变化
    LaunchedEffect(jumpToPage, initialOrientation, pdfViewState.init) {
        //println("DocumentView: jumpToPage:$jumpToPage, initialOrientation:$initialOrientation, orientation:$orientation, init: ${pdfViewState.init}")

        if (initialOrientation != orientation && pdfViewState.init) {
            isJumping = true // 设置跳转标志
            val firstPageIndex =
                firstPage(pdfViewState, offset, orientation, viewSize, onPageChanged)
            println("DocumentView: orientation改变，重置offset和zoom: $orientation->$initialOrientation, page:$firstPageIndex")
            orientation = initialOrientation
            offset = Offset.Zero
            vZoom = 1f
            pdfViewState.updateViewSize(viewSize, vZoom, orientation)

            //如果方向变化,不需要通过页码定位,通过偏移量就行.
            if (firstPageIndex < pdfViewState.pages.size - 1) {
                val page = pdfViewState.pages.get(firstPageIndex)
                if (orientation == Vertical) {
                    val targetOffsetY = when (align) {
                        PdfViewState.Align.Top -> page.bounds.top
                        PdfViewState.Align.Center -> page.bounds.top - (viewSize.height - page.height) / 2
                        PdfViewState.Align.Bottom -> page.bounds.bottom - viewSize.height
                    }
                    val maxOffsetY = (pdfViewState.totalHeight - viewSize.height).coerceAtLeast(0f)
                    val clampedTargetY = targetOffsetY.coerceIn(0f, maxOffsetY)
                    val clampedY = -clampedTargetY
                    offset = Offset(offset.x, clampedY)
                } else {
                    val targetOffsetX = when (align) {
                        PdfViewState.Align.Top -> page.bounds.left
                        PdfViewState.Align.Center -> page.bounds.left - (viewSize.width - page.width) / 2
                        PdfViewState.Align.Bottom -> page.bounds.right - viewSize.width
                    }
                    val maxOffsetX = (pdfViewState.totalWidth - viewSize.width).coerceAtLeast(0f)
                    val clampedTargetX = targetOffsetX.coerceIn(0f, maxOffsetX)
                    val clampedX = -clampedTargetX
                    offset = Offset(clampedX, offset.y)
                }
                println("DocumentView: orientation改变，跳转: $offset, $firstPageIndex, page:$page")
                flingJob?.cancel()
                pdfViewState.updateOffset(offset)
            }
            isJumping = false // 清除跳转标志
            return@LaunchedEffect
        }

        // 只有在以下情况才执行页面跳转：
        // 1. 有明确的跳转页码
        // 2. PdfViewState已初始化
        // 3. 是用户主动跳转（如进度条拖动）或者没有初始偏移量
        if (null != jumpToPage && toPage != jumpToPage && pdfViewState.init) {
            isJumping = true // 设置跳转标志
            toPage = jumpToPage
            val page = pdfViewState.pages.getOrNull(toPage)
            //println("DocumentView: 执行跳转到第${jumpToPage}页, offset:$offset, page:$page")
            if (page != null) {
                if (orientation == Vertical) {
                    val targetOffsetY = when (align) {
                        PdfViewState.Align.Top -> page.bounds.top
                        PdfViewState.Align.Center -> page.bounds.top - (viewSize.height - page.height) / 2
                        PdfViewState.Align.Bottom -> page.bounds.bottom - viewSize.height
                    }
                    val maxOffsetY = (pdfViewState.totalHeight - viewSize.height).coerceAtLeast(0f)
                    val clampedTargetY = targetOffsetY.coerceIn(0f, maxOffsetY)
                    val clampedY = -clampedTargetY
                    offset = Offset(offset.x, clampedY)
                } else {
                    val targetOffsetX = when (align) {
                        PdfViewState.Align.Top -> page.bounds.left
                        PdfViewState.Align.Center -> page.bounds.left - (viewSize.width - page.width) / 2
                        PdfViewState.Align.Bottom -> page.bounds.right - viewSize.width
                    }
                    val maxOffsetX = (pdfViewState.totalWidth - viewSize.width).coerceAtLeast(0f)
                    val clampedTargetX = targetOffsetX.coerceIn(0f, maxOffsetX)
                    val clampedX = -clampedTargetX
                    offset = Offset(clampedX, offset.y)
                }
                // 同步到PdfViewState
                println("DocumentView: 执行跳转到:$offset, top:${page.bounds.top}, toPage:$toPage")
                flingJob?.cancel()
                pdfViewState.updateOffset(offset)
            }
            isJumping = false // 清除跳转标志
        }
    }

    LaunchedEffect(crop) {
        val old = pdfViewState.isCropEnabled()
        if (old != crop) {
            println("DocumentView: 切边变化:$crop")
            pdfViewState.setCropEnabled(crop)
            // 清理所有页面的缓存图像
            pdfViewState.pages.forEach { page ->
                page.recycle()
            }
            ImageCache.clear()
            pdfViewState.invalidatePageSizes()
            pdfViewState.updateOffset(offset)
        }
    }

    // 监听页面变化并回调
    LaunchedEffect(offset) {
        // 只有在非跳转状态下才处理页面变化回调
        if (!isJumping) {
            firstPage(pdfViewState, offset, orientation, viewSize, onPageChanged)
        }
    }

    // 获取生命周期所有者
    val lifecycleOwner = LocalLifecycleOwner.current

    // 保存文档状态的公共方法
    fun saveDocumentState() {
        val pages = pdfViewState.pages
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
        println("DocumentView: 保存记录:page:$currentPage, pc:$pageCount, $viewSize, vZoom:$vZoom, list: ${list.size}, orientation: $orientation, crop: $crop,${pdfViewState.isCropEnabled()}")

        if (!list.isEmpty()) {
            onSaveDocument?.invoke(
                currentPage,
                pageCount,
                zoom,
                offset.x.toLong(),
                offset.y.toLong(),
                orientation.toLong(),
                reflow,
                if (pdfViewState.isCropEnabled()) 0L else 1L
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
            pdfViewState.shutdown()
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
                pdfViewState.updateViewSize(viewSize, vZoom, orientation)
            },
        contentAlignment = Alignment.TopStart
    ) {
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
                                panVelocityTracker.addPosition(uptime, pan)

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
                                        val scaleRatio = vZoom / pdfViewState.vZoom
                                        val scaledHeight = pdfViewState.totalHeight * scaleRatio
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
                                        val scaleRatio = vZoom / pdfViewState.vZoom
                                        val scaledWidth = pdfViewState.totalWidth * scaleRatio
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
                                    pdfViewState.updateOffset(offset)
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
                                                pdfViewState
                                            )
                                            val clickedPage =
                                                pdfViewState.pages.getOrNull(clickedPageIndex)
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
                                                val scaledHeight = pdfViewState.totalHeight
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
                                                val scaledWidth = pdfViewState.totalWidth
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
                                            pdfViewState.updateOffset(offset)
                                        }
                                    }
                                }
                                event.changes.fastForEach { if (it.positionChanged()) it.consume() }
                            } while (event.changes.fastAny { it.pressed })
                        } catch (_: CancellationException) {
                        } finally {
                            // 缩放结束后调用 updateViewSize 重新计算页面
                            if (zooming) {
                                pdfViewState.updateViewSize(viewSize, vZoom, orientation)
                            }

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
                                            pdfViewState.handleClick(contentX, contentY)
                                        //println("DocumentView.onTap: 链接处理结果: $linkHandled")

                                        // 如果没有处理链接，再处理翻页逻辑
                                        if (!linkHandled) {
                                            val isPageTurned = handleTapGesture(
                                                tapOffset,
                                                viewSize,
                                                offset,
                                                orientation,
                                                pdfViewState,
                                                keepPx
                                            ) { newOffset ->
                                                offset = newOffset
                                                pdfViewState.updateOffset(offset)
                                            }

                                            // 如果不是翻页区域，触发非页面区域点击回调
                                            if (!isPageTurned) {
                                                val clickedPage =
                                                    calculateClickedPage(
                                                        tapOffset,
                                                        offset,
                                                        orientation,
                                                        pdfViewState
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
                                    frictionMultiplier = 0.2f,
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
                                                        val newX = value.coerceIn(minX, maxX)
                                                        offset = Offset(newX, offset.y)
                                                        pdfViewState.updateOffset(offset)
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
                                                                pdfViewState.totalHeight
                                                            } else {
                                                                pdfViewState.totalWidth
                                                            }
                                                        val minY =
                                                            if (scaledHeight > viewSize.height) viewSize.height - scaledHeight else 0f
                                                        val maxY = 0f
                                                        val newY = value.coerceIn(minY, maxY)
                                                        offset = Offset(offset.x, newY)
                                                        pdfViewState.updateOffset(offset)
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
                                                                pdfViewState.totalHeight
                                                            } else {
                                                                pdfViewState.totalWidth
                                                            }
                                                        val minX =
                                                            if (scaledWidth > viewSize.width) viewSize.width - scaledWidth else 0f
                                                        val maxX = 0f
                                                        val newX = value.coerceIn(minX, maxX)
                                                        offset = Offset(newX, offset.y)
                                                        pdfViewState.updateOffset(offset)
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
                                                        val minY =
                                                            minOf(
                                                                0f,
                                                                viewSize.height - scaledHeight
                                                            )
                                                        val maxY = 0f
                                                        val newY = value.coerceIn(minY, maxY)
                                                        offset = Offset(offset.x, newY)
                                                        pdfViewState.updateOffset(offset)
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
            val translateY =
                if (orientation == Vertical && pdfViewState.totalHeight < viewSize.height) {
                    (viewSize.height - pdfViewState.totalHeight) / 2
                } else {
                    0f
                }
            translate(left = offset.x, top = offset.y + translateY) {
                //只绘制可见区域.
                /*val visibleRect = Rect(
                    left = -offset.x,
                    top = -offset.y,
                    right = size.width - offset.x,
                    bottom = size.height - offset.y
                )
                drawRect(
                    brush = gradientBrush,
                    topLeft = visibleRect.topLeft,
                    size = visibleRect.size
                )*/
                pdfViewState.drawVisiblePages(this, offset, vZoom, viewSize)

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
    pdfViewState: PdfViewState,
    offset: Offset,
    orientation: Int,
    viewSize: IntSize,
    onPageChanged: ((Int) -> Unit)?
): Int {
    var firstVisible = 0
    val pages = pdfViewState.pages
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
    pdfViewState: PdfViewState
): Int {
    // 将点击坐标转换为相对于内容的位置
    val contentX = tapOffset.x - currentOffset.x
    val contentY = tapOffset.y - currentOffset.y

    // 查找包含该坐标的页面
    val pages = pdfViewState.pages
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
            val bottom = top + pdfViewState.viewSize.height
            page.bounds.bottom > top && page.bounds.top < bottom
        } else {
            val left = -currentOffset.x
            val right = left + pdfViewState.viewSize.width
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
    pdfViewState: PdfViewState,
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
                val maxY = (pdfViewState.totalHeight - viewSize.height).coerceAtLeast(0f)
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
                val maxX = (pdfViewState.totalWidth - viewSize.width).coerceAtLeast(0f)
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