package com.archko.reader.pdf.component

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
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
    orientation: Int,
    onDocumentClosed: ((page: Int, pageCount: Int, zoom: Double, scrollX: Long, scrollY: Long, scrollOri: Long) -> Unit)? = null,
    onDoubleTapToolbar: (() -> Unit)? = null,
    onPageChanged: ((page: Int) -> Unit)? = null,
    onTapNonPageArea: ((pageIndex: Int) -> Unit)? = null,
    initialScrollX: Long = 0L,
    initialScrollY: Long = 0L,
    initialZoom: Double = 1.0,
    isUserJump: Boolean = false
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val keepPx = with(density) { 6.dp.toPx() }
    
    // 焦点请求器，用于键盘操作
    val focusRequester = remember { FocusRequester() }
    
    // 状态管理
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var offset by remember { mutableStateOf(Offset(initialScrollX.toFloat(), initialScrollY.toFloat())) }
    var vZoom by remember { mutableFloatStateOf(initialZoom.toFloat()) }

    val pdfViewState = remember(list, orientation) {
        PdfViewState(list, state, orientation)
    }
    
    // 处理键盘事件的函数
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

    // 处理视图大小变化
    val handleViewSizeChanged = { newViewSize: IntSize ->
        viewSize = newViewSize
        pdfViewState.updateViewSize(viewSize, vZoom, orientation)
    }

    // 桌面端特定的修饰符
    val desktopModifier = Modifier
        // 添加鼠标滚轮支持（桌面端特有）
        .onPointerEvent(PointerEventType.Scroll) { event ->
            val scrollAmount = event.changes.firstOrNull()?.scrollDelta ?: return@onPointerEvent
            
            // 检测键盘修饰符（这里简化处理，实际可以添加键盘状态检测）
            val isCtrlPressed = false // 可以通过键盘状态检测
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
                        val minY = if (scaledHeight > viewSize.height) viewSize.height - scaledHeight else 0f
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
                        val minX = if (scaledWidth > viewSize.width) viewSize.width - scaledWidth else 0f
                        val maxX = 0f
                        offset = Offset(
                            offset.x.coerceIn(minX, maxX),
                            offset.y.coerceIn(minY, maxY)
                        )
                    }
                    pdfViewState.updateOffset(offset)
                    //pdfViewState.updateViewSize(viewSize, vZoom, orientation)
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
                        val maxY = (pdfViewState.totalHeight - viewSize.height).coerceAtLeast(0f)
                        val newY = (offset.y + scrollAmount.y * 50f).coerceIn(-maxY, 0f)
                        offset = Offset(offset.x, newY)
                    } else {
                        val maxX = (pdfViewState.totalWidth - viewSize.width).coerceAtLeast(0f)
                        val newX = (offset.x + scrollAmount.y * 50f).coerceIn(-maxX, 0f)
                        offset = Offset(newX, offset.y)
                    }
                    pdfViewState.updateOffset(offset)
                }
            }
        }
        // 添加键盘支持
        .focusRequester(focusRequester)
        .focusable()
        .onKeyEvent(handleKeyboardEvent)

    // 处理页面跳转
    LaunchedEffect(jumpToPage, pdfViewState.init, align, orientation) {
        if (jumpToPage != null && pdfViewState.init && isUserJump) {
            val page = pdfViewState.pages.getOrNull(jumpToPage)
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
                pdfViewState.updateOffset(offset)
            }
        }
    }

    // 处理初始偏移量和缩放
    LaunchedEffect(pdfViewState.init, viewSize) {
        if (pdfViewState.init && viewSize != IntSize.Zero) {
            val hasInitialOffset = initialScrollX != 0L || initialScrollY != 0L
            val hasInitialZoom = initialZoom != 1.0

            if (hasInitialOffset || hasInitialZoom) {
                if (hasInitialZoom) {
                    vZoom = initialZoom.toFloat()
                }
                if (hasInitialOffset) {
                    offset = Offset(initialScrollX.toFloat(), initialScrollY.toFloat())
                    pdfViewState.updateOffset(offset)
                }
            }
        }
    }

    // 使用基础组件
    DocumentView(
        list = list,
        state = state,
        viewSize = viewSize,
        offset = offset,
        vZoom = vZoom,
        orientation = orientation,
        onDocumentClosed = onDocumentClosed,
        onPageChanged = onPageChanged,
        onViewSizeChanged = handleViewSizeChanged,
        additionalModifier = desktopModifier
    )
    
    // 自动请求焦点，确保键盘事件能被捕获
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
} 