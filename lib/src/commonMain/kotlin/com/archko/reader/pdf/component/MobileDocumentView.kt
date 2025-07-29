package com.archko.reader.pdf.component

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.APage
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs

/**
 * 移动端文档视图，添加完整的触摸手势支持
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
public fun MobileDocumentView(
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
    val scope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val keepPx = with(density) { 6.dp.toPx() }

    // 状态管理
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
    var flingJob by remember { mutableStateOf<Job?>(null) }

    val pdfViewState = remember(list, orientation) {
        PdfViewState(list, state, orientation)
    }

    // 处理视图大小变化
    val handleViewSizeChanged = { newViewSize: IntSize ->
        viewSize = newViewSize
        pdfViewState.updateViewSize(viewSize, vZoom, orientation)
    }

    // 移动端特定的修饰符
    val mobileModifier = Modifier
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = { offsetTap ->
                    // 单击翻页逻辑
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
                    // 如果不是翻页区域，计算点击的页面并触发回调
                    if (!isPageTurned) {
                        val clickedPage =
                            calculateClickedPage(offsetTap, offset, orientation, pdfViewState)
                        onTapNonPageArea?.invoke(clickedPage)
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
        // 添加完整的手势处理（触摸、拖拽、缩放、惯性滚动）
        .pointerInput(Unit) {
            awaitEachGesture {
                var zooming = false
                var initialZoom = vZoom
                var initialOffset = offset
                var lastCentroid = Offset.Zero
                // pan惯性
                val panVelocityTracker = VelocityTracker()
                var pan: Offset
                try {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    lastCentroid = down.position
                    initialOffset = offset
                    initialZoom = vZoom
                    pan = Offset.Zero
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
                            event.changes.maxByOrNull { it.uptimeMillis }?.uptimeMillis ?: 0L
                        pan += panChange
                        panVelocityTracker.addPosition(uptime, pan)
                        if (pointerCount > 1) {
                            zooming = true
                            val newZoom = (zoomChange * vZoom).coerceIn(1f, 10f)
                            val zoomFactor = newZoom / vZoom

                            // 计算缩放中心点：手势中心相对于内容的位置
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
                        event.changes.fastForEach { if (it.positionChanged()) it.consume() }
                    } while (event.changes.fastAny { it.pressed })
                } catch (_: CancellationException) {
                } finally {
                    // 缩放结束后调用 updateViewSize 重新计算页面
                    if (zooming) {
                        pdfViewState.updateViewSize(viewSize, vZoom, orientation)
                    }
                    // 计算pan velocity
                    val velocity =
                        runCatching { panVelocityTracker.calculateVelocity() }.getOrDefault(Velocity.Zero)
                    flingJob?.cancel()
                    val decayAnimationSpec = exponentialDecay<Float>(frictionMultiplier = 0.35f)
                    flingJob = scope.launch {
                        if (orientation == Vertical) {
                            // X方向
                            if (abs(velocity.x) > 50f) {
                                val animX = AnimationState(
                                    initialValue = offset.x,
                                    initialVelocity = velocity.x
                                )
                                launch {
                                    animX.animateDecay(decayAnimationSpec) {
                                        val scaledWidth = viewSize.width * vZoom
                                        val minX = minOf(0f, viewSize.width - scaledWidth)
                                        val maxX = 0f
                                        val newX = value.coerceIn(minX, maxX)
                                        offset = Offset(newX, offset.y)
                                        pdfViewState.updateOffset(offset)
                                    }
                                }
                            }
                            // Y方向
                            if (abs(velocity.y) > 50f) {
                                val animY = AnimationState(
                                    initialValue = offset.y,
                                    initialVelocity = velocity.y
                                )
                                launch {
                                    animY.animateDecay(decayAnimationSpec) {
                                        val scaledHeight = if (orientation == Vertical) {
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
                            if (abs(velocity.x) > 50f) {
                                val animX = AnimationState(
                                    initialValue = offset.x,
                                    initialVelocity = velocity.x
                                )
                                launch {
                                    animX.animateDecay(decayAnimationSpec) {
                                        val scaledWidth = if (orientation == Vertical) {
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
                            if (abs(velocity.y) > 50f) {
                                val animY = AnimationState(
                                    initialValue = offset.y,
                                    initialVelocity = velocity.y
                                )
                                launch {
                                    animY.animateDecay(decayAnimationSpec) {
                                        val scaledHeight = viewSize.height * vZoom
                                        val minY = minOf(0f, viewSize.height - scaledHeight)
                                        val maxY = 0f
                                        val newY = value.coerceIn(minY, maxY)
                                        offset = Offset(offset.x, newY)
                                        pdfViewState.updateOffset(offset)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

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
        additionalModifier = mobileModifier
    )
}

/**
 * 根据点击坐标计算点击的页面索引
 */
internal fun calculateClickedPage(
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
internal fun handleTapGesture(
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