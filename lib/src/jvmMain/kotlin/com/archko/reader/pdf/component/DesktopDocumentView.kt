package com.archko.reader.pdf.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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

/**
 * 桌面端文档视图，专注于鼠标滚轮和键盘事件
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
public fun DesktopDocumentView(
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
    val density = LocalDensity.current
    val keepPx = with(density) { 6.dp.toPx() }

    var isJumping by remember { mutableStateOf(false) } // 添加跳转标志
    // 焦点请求器，用于键盘操作
    val focusRequester = remember { FocusRequester() }

    // 鼠标拖拽状态
    var isDragging by remember { mutableStateOf(false) }
    var dragStartOffset by remember { mutableStateOf(Offset.Zero) }
    var dragStartPosition by remember { mutableStateOf(Offset.Zero) }

    val pdfViewState = remember(list) {
        println("DocumentView: 创建新的PdfViewState:$viewSize, vZoom:$vZoom，list: ${list.size}, orientation: $orientation")
        PdfViewState(list, state, orientation, crop)
    }

    // 处理键盘和鼠标滚轮事件的函数
    val handleKeyboardEvent = { event: KeyEvent ->
        if (event.type == KeyEventType.KeyDown) {
            when (event.key) {
                Key.Spacebar -> {
                    // 空格键翻页
                    if (orientation == Vertical) {
                        val maxY = (pdfViewState.totalHeight - viewSize.height).coerceAtLeast(0f)
                        val newY = (offset.y - viewSize.height + keepPx).coerceAtLeast(-maxY)
                        offset = Offset(offset.x, newY)
                    } else {
                        val maxX = (pdfViewState.totalWidth - viewSize.width).coerceAtLeast(0f)
                        val newX = (offset.x - viewSize.width + keepPx).coerceAtLeast(-maxX)
                        offset = Offset(newX, offset.y)
                    }
                    pdfViewState.updateOffset(offset)
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
                    pdfViewState.updateOffset(offset)
                    true
                }

                Key.PageDown -> {
                    // PageDown键向下翻页
                    if (orientation == Vertical) {
                        val maxY = (pdfViewState.totalHeight - viewSize.height).coerceAtLeast(0f)
                        val newY = (offset.y - viewSize.height + keepPx).coerceAtLeast(-maxY)
                        offset = Offset(offset.x, newY)
                    } else {
                        val maxX = (pdfViewState.totalWidth - viewSize.width).coerceAtLeast(0f)
                        val newX = (offset.x - viewSize.width + keepPx).coerceAtLeast(-maxX)
                        offset = Offset(newX, offset.y)
                    }
                    pdfViewState.updateOffset(offset)
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
                    pdfViewState.updateOffset(offset)
                    true
                }

                Key.DirectionDown -> {
                    // 下方向键滚动
                    if (orientation == Vertical) {
                        val maxY = (pdfViewState.totalHeight - viewSize.height).coerceAtLeast(0f)
                        val newY = (offset.y - 120f).coerceAtLeast(-maxY)
                        offset = Offset(offset.x, newY)
                    } else {
                        val maxX = (pdfViewState.totalWidth - viewSize.width).coerceAtLeast(0f)
                        val newX = (offset.x - 120f).coerceAtLeast(-maxX)
                        offset = Offset(newX, offset.y)
                    }
                    pdfViewState.updateOffset(offset)
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
                    pdfViewState.updateOffset(offset)
                    true
                }

                Key.DirectionRight -> {
                    // 右方向键滚动
                    if (orientation == Vertical) {
                        val maxX = (pdfViewState.totalWidth - viewSize.width).coerceAtLeast(0f)
                        val newX = (offset.x - 120f).coerceAtLeast(-maxX)
                        offset = Offset(newX, offset.y)
                    } else {
                        val maxX = (pdfViewState.totalWidth - viewSize.width).coerceAtLeast(0f)
                        val newX = (offset.x - 120f).coerceAtLeast(-maxX)
                        offset = Offset(newX, offset.y)
                    }
                    pdfViewState.updateOffset(offset)
                    true
                }

                Key.NumPad0 -> {
                    // Home键回到顶部
                    if (orientation == Vertical) {
                        offset = Offset(offset.x, 0f)
                    } else {
                        offset = Offset(0f, offset.y)
                    }
                    pdfViewState.updateOffset(offset)
                    true
                }

                Key.NumPad1 -> {
                    // End键到底部
                    if (orientation == Vertical) {
                        val maxY = (pdfViewState.totalHeight - viewSize.height).coerceAtLeast(0f)
                        offset = Offset(offset.x, -maxY)
                    } else {
                        val maxX = (pdfViewState.totalWidth - viewSize.width).coerceAtLeast(0f)
                        offset = Offset(-maxX, offset.y)
                    }
                    pdfViewState.updateOffset(offset)
                    true
                }

                else -> false
            }
        } else {
            false
        }
    }

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
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offsetTap ->
                        // 将点击坐标转换为相对于内容的位置
                        val contentX = offsetTap.x - offset.x
                        val contentY = offsetTap.y - offset.y

                        // 首先尝试处理链接点击
                        //println("DocumentView.onTap: 尝试处理链接点击，坐标($contentX, $contentY)")
                        val linkHandled = pdfViewState.handleClick(contentX, contentY)
                        println("DocumentView.onTap: 链接处理结果: $linkHandled")

                        // 如果没有处理链接，再处理翻页逻辑
                        if (!linkHandled) {
                            val isPageTurned = handleTapGesture(
                                offsetTap,
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
                                        offsetTap,
                                        offset,
                                        orientation,
                                        pdfViewState
                                    )
                                onTapNonPageArea?.invoke(clickedPage)
                            }
                        }
                    },
                    onDoubleTap = { offsetTap ->
                        val y = offsetTap.y
                        val height = viewSize.height.toFloat()
                        if (y >= height / 4 && y <= height * 3 / 4) {
                            onDoubleTapToolbar?.invoke()
                        }
                    }
                )
            }
            // 添加鼠标拖拽支持
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { startOffset ->
                        isDragging = true
                        dragStartOffset = offset
                        dragStartPosition = startOffset
                    },
                    onDragEnd = {
                        isDragging = false
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onDrag = { change, dragAmount ->
                        if (isDragging) {
                            val newOffset = Offset(
                                dragStartOffset.x - dragAmount.x,
                                dragStartOffset.y - dragAmount.y
                            )

                            // 边界检查
                            if (orientation == Vertical) {
                                val scaledWidth = viewSize.width * vZoom
                                val scaledHeight = pdfViewState.totalHeight
                                val minX = minOf(0f, viewSize.width - scaledWidth)
                                val maxX = 0f
                                val minY =
                                    if (scaledHeight > viewSize.height) viewSize.height - scaledHeight else 0f
                                val maxY = 0f
                                offset = Offset(
                                    newOffset.x.coerceIn(minX, maxX),
                                    newOffset.y.coerceIn(minY, maxY)
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
                                    newOffset.x.coerceIn(minX, maxX),
                                    newOffset.y.coerceIn(minY, maxY)
                                )
                            }
                            pdfViewState.updateOffset(offset)
                        }
                        change.consume()
                    }
                )
            }
            // 添加鼠标滚轮支持（支持多种模式）
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val scrollAmount = event.changes.firstOrNull()?.scrollDelta ?: return@onPointerEvent
                // 暂时简化，只支持普通滚动，后续可以添加键盘修饰符检测
                val isCtrlPressed = false
                val isShiftPressed = false

                when {
                    isCtrlPressed -> {
                        // Ctrl + 滚轮 = 缩放
                        val zoomFactor = if (scrollAmount.y > 0) 1.1f else 0.9f
                        val newZoom = (vZoom * zoomFactor).coerceIn(0.5f, 5f)

                        // 计算缩放中心点（鼠标位置）
                        val mousePosition = event.changes.firstOrNull()?.position ?: Offset.Zero
                        val contentCenterX = mousePosition.x - offset.x
                        val contentCenterY = mousePosition.y - offset.y

                        val zoomRatio = newZoom / vZoom
                        val newOffsetX = mousePosition.x - contentCenterX * zoomRatio
                        val newOffsetY = mousePosition.y - contentCenterY * zoomRatio

                        vZoom = newZoom
                        offset = Offset(newOffsetX, newOffsetY)

                        // 边界检查
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
                        pdfViewState.updateViewSize(viewSize, vZoom, orientation)
                    }

                    isShiftPressed -> {
                        // Shift + 滚轮 = 水平滚动
                        val maxX = (pdfViewState.totalWidth - viewSize.width).coerceAtLeast(0f)
                        val newX = (offset.x + scrollAmount.y * 50f).coerceIn(-maxX, 0f)
                        offset = Offset(newX, offset.y)
                        pdfViewState.updateOffset(offset)
                    }

                    else -> {
                        // 普通滚轮 = 垂直滚动
                        if (orientation == Vertical) {
                            val maxY =
                                (pdfViewState.totalHeight - viewSize.height).coerceAtLeast(0f)
                            val newY = (offset.y + scrollAmount.y * 50f).coerceIn(-maxY, 0f)
                            offset = Offset(offset.x, newY)
                        } else {
                            val maxX = (pdfViewState.totalWidth - viewSize.width).coerceAtLeast(0f)
                            val newX = (offset.x + scrollAmount.x * 50f).coerceIn(-maxX, 0f)
                            offset = Offset(newX, offset.y)
                        }
                        pdfViewState.updateOffset(offset)
                    }
                }
            }
            // 添加键盘支持
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent(handleKeyboardEvent),
        contentAlignment = Alignment.TopStart
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
        ) {
            val translateY = if (orientation == Vertical && pdfViewState.totalHeight < viewSize.height) {
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
            }
        }
    }

    // 自动请求焦点，确保键盘事件能被捕获
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
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