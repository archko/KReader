package com.archko.reader.pdf.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.archko.reader.pdf.cache.ImageCache
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.APage
import com.archko.reader.pdf.state.AnnotationManager
import com.archko.reader.pdf.util.HyperLinkUtils
import com.archko.reader.pdf.util.ViewUtils
import kotlin.math.max
import kotlin.math.min

/**
 * 桌面端文档视图，专注于鼠标滚轮和键盘事件
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
public fun DesktopDocumentView(
    list: MutableList<APage>,
    state: ImageDecoder,
    jumpToPage: Int? = null,
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
    gestureMode: GestureMode = GestureMode.VIEW,
    pathConfig: PathConfig,
    annotationManager: AnnotationManager,
    currentPath: String,
) {
    // 平台判断 - 只在初始化时判断一次
    val isMacOs by remember {
        mutableStateOf(
            System.getProperty("os.name", "").lowercase().contains("mac")
        )
    }

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
    val density = LocalDensity.current
    val keepPx = with(density) { 6.dp.toPx() }
    var isJumping by remember { mutableStateOf(false) } // 添加跳转标志
    // 焦点请求器，用于键盘操作
    val focusRequester = remember { FocusRequester() }

    // 缩放相关状态
    val minZoom = 0.5f
    val maxZoom = 5.0f

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
            crop = crop,
            columnCount = columnCount,
            textSelector = textSelector
        )
    }

    LaunchedEffect(columnCount) {
        pageViewState.updateColumnCount(columnCount)
        pageViewState.updateVisiblePages(offset, viewSize, vZoom)
    }

    // 文本选择相关状态
    var isTextSelecting by remember { mutableStateOf(false) }
    var selectedPage by remember { mutableStateOf<Page?>(null) }
    var showTextActionToolbar by remember { mutableStateOf(false) }
    var selectionStartPos by remember { mutableStateOf<Offset?>(null) }
    var selectionEndPos by remember { mutableStateOf<Offset?>(null) }

    // 处理键盘和鼠标滚轮事件的函数
    val handleKeyboardEvent = { event: KeyEvent ->
        if (event.type == KeyEventType.KeyDown) {
            when (event.key) {
                Key.Spacebar -> {
                    // 空格键翻页
                    if (orientation == Vertical) {
                        val maxY = (pageViewState.totalHeight - viewSize.height).coerceAtLeast(0f)
                        val newY = (offset.y - viewSize.height + keepPx).coerceAtLeast(-maxY)
                        offset = Offset(offset.x, newY)
                    } else {
                        val maxX = (pageViewState.totalWidth - viewSize.width).coerceAtLeast(0f)
                        val newX = (offset.x - viewSize.width + keepPx).coerceAtLeast(-maxX)
                        offset = Offset(newX, offset.y)
                    }
                    pageViewState.updateOffset(offset)
                    true
                }

                Key.PageUp -> {
                    // PageUp键向上翻页
                    if (orientation == Vertical) {
                        val newY = (offset.y + viewSize.height - keepPx).coerceAtMost(0f)
                        offset = Offset(offset.x, newY)
                    } else {
                        val newX = (offset.x + viewSize.width - keepPx).coerceAtMost(0f)
                        offset = Offset(newX, offset.y)
                    }
                    pageViewState.updateOffset(offset)
                    true
                }

                Key.PageDown -> {
                    // PageDown键向下翻页
                    if (orientation == Vertical) {
                        val maxY = (pageViewState.totalHeight - viewSize.height).coerceAtLeast(0f)
                        val newY = (offset.y - viewSize.height + keepPx).coerceAtLeast(-maxY)
                        offset = Offset(offset.x, newY)
                    } else {
                        val maxX = (pageViewState.totalWidth - viewSize.width).coerceAtLeast(0f)
                        val newX = (offset.x - viewSize.width + keepPx).coerceAtLeast(-maxX)
                        offset = Offset(newX, offset.y)
                    }
                    pageViewState.updateOffset(offset)
                    true
                }

                Key.DirectionUp -> {
                    // 上方向键滚动
                    if (orientation == Vertical) {
                        val newY = (offset.y + 120f).coerceAtMost(0f)
                        offset = Offset(offset.x, newY)
                    } else {
                        val newX = (offset.x + 120f).coerceAtMost(0f)
                        offset = Offset(newX, offset.y)
                    }
                    pageViewState.updateOffset(offset)
                    true
                }

                Key.DirectionDown -> {
                    // 下方向键滚动
                    if (orientation == Vertical) {
                        val maxY = (pageViewState.totalHeight - viewSize.height).coerceAtLeast(0f)
                        val newY = (offset.y - 120f).coerceAtLeast(-maxY)
                        offset = Offset(offset.x, newY)
                    } else {
                        val maxX = (pageViewState.totalWidth - viewSize.width).coerceAtLeast(0f)
                        val newX = (offset.x - 120f).coerceAtLeast(-maxX)
                        offset = Offset(newX, offset.y)
                    }
                    pageViewState.updateOffset(offset)
                    true
                }

                Key.DirectionLeft -> {
                    // 左方向键滚动
                    if (orientation == Vertical) {
                        val newX = (offset.x + 120f).coerceAtMost(0f)
                        offset = Offset(newX, offset.y)
                    } else {
                        val newX = (offset.x + 120f).coerceAtMost(0f)
                        offset = Offset(newX, offset.y)
                    }
                    pageViewState.updateOffset(offset)
                    true
                }

                Key.DirectionRight -> {
                    // 右方向键滚动
                    if (orientation == Vertical) {
                        val maxX = (pageViewState.totalWidth - viewSize.width).coerceAtLeast(0f)
                        val newX = (offset.x - 120f).coerceAtLeast(-maxX)
                        offset = Offset(newX, offset.y)
                    } else {
                        val maxX = (pageViewState.totalWidth - viewSize.width).coerceAtLeast(0f)
                        val newX = (offset.x - 120f).coerceAtLeast(-maxX)
                        offset = Offset(newX, offset.y)
                    }
                    pageViewState.updateOffset(offset)
                    true
                }

                Key.NumPad0 -> {
                    // Home键回到顶部
                    if (orientation == Vertical) {
                        offset = Offset(offset.x, 0f)
                    } else {
                        offset = Offset(0f, offset.y)
                    }
                    pageViewState.updateOffset(offset)
                    true
                }

                Key.NumPad1 -> {
                    // End键到底部
                    if (orientation == Vertical) {
                        val maxY = (pageViewState.totalHeight - viewSize.height).coerceAtLeast(0f)
                        offset = Offset(offset.x, -maxY)
                    } else {
                        val maxX = (pageViewState.totalWidth - viewSize.width).coerceAtLeast(0f)
                        offset = Offset(-maxX, offset.y)
                    }
                    pageViewState.updateOffset(offset)
                    true
                }

                Key.Equals -> {
                    // 加号键放大
                    if (event.isCtrlPressed || event.isMetaPressed) {
                        val newZoom = min(vZoom * 1.2f, maxZoom)
                        if (newZoom != vZoom) {
                            val centerX = viewSize.width / 2f
                            val centerY = viewSize.height / 2f
                            handleZoom(
                                newZoom,
                                centerX,
                                centerY,
                                vZoom,
                                offset,
                                pageViewState,
                                viewSize,
                                orientation
                            ) { newOffset, newVZoom ->
                                offset = newOffset
                                vZoom = newVZoom
                                pageViewState.updateViewSize(viewSize, vZoom, orientation)
                                pageViewState.updateOffset(newOffset)
                            }
                        }
                        true
                    } else false
                }

                Key.Minus -> {
                    // 减号键缩小
                    if (event.isCtrlPressed || event.isMetaPressed) {
                        val newZoom = max(vZoom / 1.2f, minZoom)
                        if (newZoom != vZoom) {
                            val centerX = viewSize.width / 2f
                            val centerY = viewSize.height / 2f
                            handleZoom(
                                newZoom,
                                centerX,
                                centerY,
                                vZoom,
                                offset,
                                pageViewState,
                                viewSize,
                                orientation
                            ) { newOffset, newVZoom ->
                                offset = newOffset
                                vZoom = newVZoom
                                pageViewState.updateViewSize(viewSize, vZoom, orientation)
                                pageViewState.updateOffset(newOffset)
                            }
                        }
                        true
                    } else false
                }

                Key.Zero -> {
                    // 0键重置缩放
                    if (event.isCtrlPressed || event.isMetaPressed) {
                        if (vZoom != 1.0f) {
                            val centerX = viewSize.width / 2f
                            val centerY = viewSize.height / 2f
                            handleZoom(
                                1.0f,
                                centerX,
                                centerY,
                                vZoom,
                                offset,
                                pageViewState,
                                viewSize,
                                orientation
                            ) { newOffset, newVZoom ->
                                offset = newOffset
                                vZoom = newVZoom
                                pageViewState.updateViewSize(viewSize, vZoom, orientation)
                                pageViewState.updateOffset(newOffset)
                            }
                        }
                        true
                    } else false
                }

                else -> false
            }
        } else {
            false
        }
    }

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
            val firstPageIndex =
                ViewUtils.firstPage(pageViewState, offset, orientation, viewSize, onPageChanged)
            println("DocumentView: orientation改变，重置offset和zoom: $orientation->$initialOrientation, page:$firstPageIndex")
            orientation = initialOrientation
            offset = Offset.Zero
            vZoom = 1f
            pageViewState.updateViewSize(viewSize, vZoom, orientation)

            //如果方向变化,不需要通过页码定位,通过偏移量就行.
            if (firstPageIndex < pageViewState.pages.size - 1) {
                val page = pageViewState.pages.get(firstPageIndex)
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
                println("DocumentView: orientation改变，跳转: $offset, $firstPageIndex, page:$page")
                pageViewState.updateOffset(offset)
            }
            isJumping = false // 清除跳转标志
            return@LaunchedEffect
        }

        // 只有在以下情况才执行页面跳转：
        // 1. 有明确的跳转页码
        // 2. PageViewState已初始化
        // 3. 是用户主动跳转（如进度条拖动）或者没有初始偏移量
        if (null != jumpToPage && toPage != jumpToPage && pageViewState.init) {
            isJumping = true // 设置跳转标志
            toPage = jumpToPage
            val page = pageViewState.pages.getOrNull(toPage)
            //println("DocumentView: 执行跳转到第${jumpToPage}页, offset:$offset, page:$page")
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
                println("DocumentView: 执行跳转到:$offset, top:${page.bounds.top}, toPage:$toPage")
                pageViewState.updateOffset(offset)
            }
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
            pageViewState.invalidatePageSizes()
            pageViewState.updateOffset(offset)
        }
    }

    LaunchedEffect(initialZoom) {
        if (vZoom != initialZoom.toFloat()) {
            println("DocumentView: initialZoom变化:$initialZoom")
            val centerX = viewSize.width / 2f
            val centerY = viewSize.height / 2f
            handleZoom(
                initialZoom.toFloat(),
                centerX,
                centerY,
                vZoom,
                offset,
                pageViewState,
                viewSize,
                orientation
            ) { newOffset, newVZoom ->
                offset = newOffset
                vZoom = newVZoom
                pageViewState.updateViewSize(viewSize, vZoom, orientation)
                pageViewState.updateOffset(newOffset)
            }
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
        // 内部定义临时状态
        val drawingPoints = remember { mutableStateListOf<Offset>() }
        var activeDrawingPage by remember { mutableStateOf(-1) }

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
                .pointerInput("tap_and_double_tap_gestures") {
                    detectTapGestures(
                        onTap = { offsetTap ->
                            focusRequester.requestFocus()

                            // 如果有文本选择工具栏显示，先隐藏它
                            if (showTextActionToolbar) {
                                showTextActionToolbar = false
                                selectedPage?.clearTextSelection()
                                selectedPage = null
                                selectionStartPos = null
                                selectionEndPos = null
                                return@detectTapGestures
                            }

                            // 将点击坐标转换为相对于内容的位置
                            val contentX = offsetTap.x - offset.x
                            val contentY = offsetTap.y - offset.y

                            val linkHandled = pageViewState.handleClick(contentX, contentY)
                            //println("DocumentView.onTap: 链接处理结果: $linkHandled")

                            // 如果没有处理链接，再处理翻页逻辑
                            if (!linkHandled) {
                                val isPageTurned = ViewUtils.handleTapGesture(
                                    offsetTap,
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
                                        offsetTap,
                                        offset,
                                        orientation,
                                        pageViewState
                                    )
                                    onTapNonPageArea?.invoke(clickedPage)
                                }
                            }
                        },
                        onDoubleTap = { offsetTap ->
                            focusRequester.requestFocus()
                            val y = offsetTap.y
                            val height = viewSize.height.toFloat()
                            if (y >= height / 4 && y <= height * 3 / 4) {
                                onDoubleTapToolbar?.invoke()
                            }
                        }
                    )
                }
                .pointerInput("drag_gestures", gestureMode, pathConfig) {
                    when (gestureMode) {
                        GestureMode.SELECTION -> {
                            var dragStartPos: Offset? = null
                            var currentDragPos: Offset? = null

                            detectDragGestures(
                                onDragStart = { startPos ->
                                    focusRequester.requestFocus()
                                    dragStartPos = startPos
                                    currentDragPos = startPos

                                    // 文本选择模式：开始文本选择
                                    isTextSelecting = true
                                    selectionStartPos = startPos
                                    selectionEndPos = startPos
                                    showTextActionToolbar = false

                                    // 找到点击的页面并开始选择
                                    val clickedPageIndex = ViewUtils.calculateClickedPage(
                                        startPos,
                                        offset,
                                        orientation,
                                        pageViewState
                                    )
                                    val clickedPage =
                                        pageViewState.pages.getOrNull(clickedPageIndex)
                                    if (clickedPage != null) {
                                        val contentX = startPos.x - offset.x
                                        val contentY = startPos.y - offset.y
                                        //println("DocumentView: 开始文本选择 - startPos: $startPos, offset: $offset, contentPos: ($contentX, $contentY)")
                                        clickedPage.startTextSelection(contentX, contentY)
                                        selectedPage = clickedPage
                                    }
                                },
                                onDrag = { change ->
                                    // 文本选择模式：只处理文本选择，不处理拖拽滚动
                                    if (isTextSelecting && dragStartPos != null) {
                                        // 累积拖拽位置：从起始位置开始累加所有变化
                                        currentDragPos =
                                            (currentDragPos ?: dragStartPos!!) + change
                                        selectionEndPos = currentDragPos

                                        selectedPage?.let { page ->
                                            val contentX = currentDragPos!!.x - offset.x
                                            val contentY = currentDragPos!!.y - offset.y
                                            //println("DocumentView: 更新文本选择 - currentDragPos: $currentDragPos, offset: $offset, contentPos: ($contentX, $contentY)")
                                            page.updateTextSelection(contentX, contentY)
                                        }
                                    }
                                },
                                onDragEnd = {
                                    if (isTextSelecting) {
                                        // 文本选择模式：结束文本选择
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
                                    }

                                    // 重置拖拽状态
                                    dragStartPos = null
                                    currentDragPos = null
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
                            detectDragGestures(
                                onDragStart = { startPos ->
                                    focusRequester.requestFocus()
                                },
                                onDrag = { change ->
                                    // 普通拖拽滚动
                                    val maxX =
                                        (pageViewState.totalWidth - viewSize.width).coerceAtLeast(
                                            0f
                                        )
                                    val maxY =
                                        (pageViewState.totalHeight - viewSize.height).coerceAtLeast(
                                            0f
                                        )

                                    val newX = (offset.x + change.x).coerceIn(-maxX, 0f)
                                    val newY = (offset.y + change.y).coerceIn(-maxY, 0f)

                                    offset = Offset(newX, newY)
                                    pageViewState.updateOffset(offset)
                                },
                                onDragEnd = {
                                }
                            )
                        }
                    }
                }
                .onPointerEvent(PointerEventType.Scroll) { event ->
                    focusRequester.requestFocus()
                    val rawScrollDelta =
                        event.changes.firstOrNull()?.scrollDelta ?: return@onPointerEvent
                    val scrollAmount = Offset(
                        x = if (isMacOs) -rawScrollDelta.x else rawScrollDelta.x,
                        y = if (isMacOs) -rawScrollDelta.y else rawScrollDelta.y
                    )

                    // 普通滚动（鼠标滚轮和触摸板滚动）
                    val scrollMultiplier = 30f

                    // 计算边界限制
                    val maxX = (pageViewState.totalWidth - viewSize.width).coerceAtLeast(0f)
                    val maxY = (pageViewState.totalHeight - viewSize.height).coerceAtLeast(0f)

                    // 支持双向滚动，特别是在缩放后
                    val newX = (offset.x + scrollAmount.x * scrollMultiplier).coerceIn(-maxX, 0f)
                    val newY = (offset.y + scrollAmount.y * scrollMultiplier).coerceIn(-maxY, 0f)

                    offset = Offset(newX, newY)
                    pageViewState.updateOffset(offset)
                }
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent(handleKeyboardEvent)
        ) {
            val translateY =
                if (orientation == Vertical && pageViewState.totalHeight < viewSize.height) {
                    (viewSize.height - pageViewState.totalHeight) / 2
                } else {
                    0f
                }
            translate(left = offset.x, top = offset.y + translateY) {
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

    // 自动请求焦点，确保键盘事件能被捕获
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // 文本操作工具栏
    if (showTextActionToolbar && selectedPage?.currentSelection != null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            TextActionToolbar(
                selectedPage = selectedPage!!,
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

/**
 * 处理缩放操作的公共方法
 */
private fun handleZoom(
    newZoom: Float,
    centerX: Float,
    centerY: Float,
    currentZoom: Float,
    currentOffset: Offset,
    pageViewState: PageViewState,
    viewSize: IntSize,
    orientation: Int,
    onZoomChanged: (Offset, Float) -> Unit
) {
    // 计算缩放前的内容坐标
    val contentX = centerX - currentOffset.x
    val contentY = centerY - currentOffset.y

    // 计算缩放比例
    val zoomRatio = newZoom / currentZoom

    // 计算缩放后的新偏移量，保持缩放中心点不变
    val newOffsetX = centerX - contentX * zoomRatio
    val newOffsetY = centerY - contentY * zoomRatio

    // 计算边界限制
    val maxX = (pageViewState.totalWidth - viewSize.width).coerceAtLeast(0f)
    val maxY = (pageViewState.totalHeight - viewSize.height).coerceAtLeast(0f)

    // 应用边界限制
    val clampedOffsetX = newOffsetX.coerceIn(-maxX, 0f)
    val clampedOffsetY = newOffsetY.coerceIn(-maxY, 0f)

    val newOffset = Offset(clampedOffsetX, clampedOffsetY)

    onZoomChanged(newOffset, newZoom)
}