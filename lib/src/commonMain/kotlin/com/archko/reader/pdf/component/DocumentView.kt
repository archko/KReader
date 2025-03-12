package com.archko.reader.pdf.component

import androidx.compose.animation.core.animateDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.archko.reader.pdf.entity.APage
import com.archko.reader.pdf.flinger.FlingConfiguration
import com.archko.reader.pdf.flinger.SplineBasedFloatDecayAnimationSpec
import com.archko.reader.pdf.state.PdfState
import com.archko.reader.pdf.state.PdfViewState
import com.archko.reader.pdf.util.Dispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * @author: archko 2025/3/10 :20:09
 */
@Composable
public fun DocumentView(
    state: PdfState,
    list: List<APage>,
    width: Int,
    height: Int
) {
    // 初始化状态
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var vZoom by remember { mutableFloatStateOf(1f) }

    // 使用 derivedStateOf 来处理 list 变化
    val pdfState = remember(list) {
        println("DocumentView: 创建新的PdfViewState，list: ${list.size}")
        PdfViewState(list, state)
    }

    // 确保在 list 变化时重新计算总高度
    LaunchedEffect(list, viewSize) {
        if (viewSize != IntSize.Zero) {
            println("DocumentView: 更新ViewSize: $viewSize, list: ${list.size}")
            pdfState.updateViewSize(viewSize, vZoom)
        }
    }
    val velocityTracker = remember { VelocityTracker() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var flingJob by remember { mutableStateOf<Job?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()//画布是屏幕大就行了,否则页数多的情况,canvas无法创建
            .onSizeChanged {
                println("onSizeChanged:$it, zoom:$vZoom, $viewSize")
                viewSize = it
                pdfState.updateViewSize(it, vZoom)
            }
            .pointerInput(Unit) {
                forEachGesture {
                    awaitPointerEventScope {
                        var initialZoom = vZoom
                        var startOffset = Offset.Zero
                        var initialDistance = 0f
                        var initialCenter = Offset.Zero

                        while (true) {
                            val event = awaitPointerEvent()

                            when (event.changes.size) {
                                1 -> { // 单指滑动
                                    flingJob?.cancel()
                                    flingJob = null
                                    event.changes[0].let { drag ->
                                        val delta = drag.position - drag.previousPosition
                                        velocityTracker.addPosition(
                                            drag.uptimeMillis,
                                            drag.position
                                        )

                                        val scaledWidth = viewSize.width * vZoom
                                        val scaledHeight = pdfState.totalHeight// * vZoom

                                        // 计算最大滚动范围
                                        val maxX =
                                            (scaledWidth - viewSize.width).coerceAtLeast(0f) / 2
                                        val maxY =
                                            (scaledHeight - viewSize.height).coerceAtLeast(0f)

                                        // 更新偏移量
                                        offset = Offset(
                                            (offset.x + delta.x).coerceIn(-maxX, maxX),
                                            (offset.y + delta.y).coerceIn(-maxY, 0f)
                                        )
                                    }
                                }

                                2 -> { // 双指缩放
                                    if (event.changes.all { it.pressed }) {
                                        if (initialDistance == 0f) {
                                            // 第一次双指事件，记录初始距离和中心点
                                            initialDistance = calculateDistance(event)
                                            initialCenter = calculateCenter(event)
                                            startOffset = offset
                                            initialZoom = vZoom
                                        } else {
                                            val currentDistance = calculateDistance(event)
                                            val scale = currentDistance / initialDistance
                                            vZoom = (initialZoom * scale).coerceIn(1f, 5f)

                                            // 调整偏移量以保持缩放中心点不变
                                            val newCenter = calculateCenter(event)
                                            offset = Offset(
                                                startOffset.x + (newCenter.x - initialCenter.x) / vZoom,
                                                startOffset.y + (newCenter.y - initialCenter.y) / vZoom
                                            )

                                            pdfState.updateViewSize(viewSize, vZoom)
                                        }
                                    }
                                }

                                else -> break
                            }

                            if (!event.changes.any { it.pressed }) {
                                break
                            }
                        }

                        // 计算最终速度
                        val velocity = velocityTracker.calculateVelocity()
                        velocityTracker.resetTracking()

                        // 创建优化后的decay动画spec
                        val decayAnimationSpec = SplineBasedFloatDecayAnimationSpec(
                            density = density,
                            scrollConfiguration = FlingConfiguration.Builder()
                                .scrollViewFriction(0.0095f)  // 减小摩擦力，使滑动更流畅
                                // 减小这个值可以增加滚动速度，建议范围 0.01f - 0.02f
                                .numberOfSplinePoints(100)  // 提高采样率
                                // 增加这个值可以使滚动更平滑，但会略微增加计算量，建议范围 100 - 200
                                .splineInflection(0.25f)     // 控制曲线拐点位置
                                // 减小这个值可以使滚动更快减速，建议范围 0.1f - 0.3f
                                .splineStartTension(0.55f)   // 控制曲线起始张力
                                // 增加这个值可以使滚动初速度更快，建议范围 0.5f - 1.0f
                                .splineEndTension(0.7f)       // 控制曲线结束张力
                                // 增加这个值可以使滚动持续时间更长，建议范围 0.8f - 1.2f
                                .build()
                        )

                        flingJob = scope.launch {
                            // 同时处理水平和垂直方向的惯性滑动
                            val scaledWidth = viewSize.width * vZoom
                            val scaledHeight = pdfState.totalHeight// * vZoom
                            val maxX = (scaledWidth - viewSize.width).coerceAtLeast(0f) / 2
                            val maxY = (scaledHeight - viewSize.height).coerceAtLeast(0f)

                            // 创建两个协程同时处理x和y方向的动画
                            launch {
                                if (abs(velocity.x) > 50f) {  // 添加最小速度阈值
                                    animateDecay(
                                        initialValue = offset.x,
                                        initialVelocity = velocity.x,
                                        animationSpec = decayAnimationSpec
                                    ) { value, _ ->
                                        offset = offset.copy(x = value.coerceIn(-maxX, maxX))
                                    }
                                }
                            }

                            launch {
                                if (abs(velocity.y) > 50f) {  // 添加最小速度阈值
                                    animateDecay(
                                        initialValue = offset.y,
                                        initialVelocity = velocity.y,
                                        animationSpec = decayAnimationSpec
                                    ) { value, _ ->
                                        offset = offset.copy(y = value.coerceIn(-maxY, 0f))
                                    }
                                }
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.TopStart
    ) {
        if (pdfState.init) {
            pdfState.pages.forEach { page ->
                PdfPage(
                    state = state,
                    pdfState = pdfState,
                    page = page,
                    offset = offset,
                    size = viewSize
                )
            }
        }
    }
}

// 辅助函数：计算两点之间的距离
private fun calculateDistance(event: PointerEvent): Float {
    val first = event.changes[0].position
    val second = event.changes[1].position
    return sqrt((first.x - second.x).pow(2) + (first.y - second.y).pow(2))
}

// 辅助函数：计算两个触点的中心点
private fun calculateCenter(event: PointerEvent): Offset {
    val first = event.changes[0].position
    val second = event.changes[1].position
    return Offset((first.x + second.x) / 2, (first.y + second.y) / 2)
}

/*private fun isPageVisible(index: Int, bounds: Rect, offset: Offset, size: IntSize): Boolean {
    val visibleRect = Rect(
        left = 0f,
        top = 0f,
        right = size.width.toFloat(),
        bottom = size.height.toFloat()
    )

    // 计算页面实际位置（考虑偏移）
    val actualPageBounds = Rect(
        left = bounds.left + offset.x,
        top = bounds.top + offset.y,
        right = bounds.right + offset.x,
        bottom = bounds.bottom + offset.y
    )

    // 检查页面是否与可视区域相交
    val flag = actualPageBounds.intersectsWith(visibleRect)
    println("isVisible.index:$index, $flag, $visibleRect, $actualPageBounds")
    return flag
}*/
private fun isPageVisible(index: Int, bounds: Rect, offset: Offset, size: IntSize): Boolean {
    val visibleRect = Rect(
        left = -offset.x,
        top = -offset.y,
        right = size.width - offset.x,
        bottom = size.height - offset.y
    )

    // 检查页面是否与可视区域相交
    val flag = bounds.intersectsWith(visibleRect)
    //println("isVisible.index:$index, $flag, $visibleRect, $bounds")
    return flag
}

@Composable
public fun PdfPage(
    state: PdfState,
    pdfState: PdfViewState,
    page: Page,
    offset: Offset,
    size: IntSize
) {
    val (bitmap, setBitmap) = remember { mutableStateOf<ImageBitmap?>(null) }
    val (loading, setLoading) = remember { mutableStateOf(true) }
    val isVisible = isPageVisible(page.aPage.index, page.bounds, offset, size)

    LaunchedEffect(isVisible) {
        if (isVisible && bitmap == null && (page.bounds.width > 0f && page.bounds.height > 0)) {
            val cacheKey = "${page.aPage.index}-${page.bounds}-$size"
            var loadedBitmap: ImageBitmap? = ImageCache.get(cacheKey)

            if (loadedBitmap == null) {
                setLoading(true)
                val scope = CoroutineScope(SupervisorJob())
                scope.launch {
                    snapshotFlow {
                        if (isActive) {
                            return@snapshotFlow state.renderPage(
                                page.aPage.index,
                                page.bounds.width.toInt(),
                                page.bounds.height.toInt(),
                            )
                        } else {
                            return@snapshotFlow null
                        }
                    }.flowOn(Dispatcher.DECODE)
                        .collectLatest {
                            if (it != null) {
                                ImageCache.put(cacheKey, it)
                                loadedBitmap = it
                                //println("bitmap:${page.aPage.index}, $loadedBitmap")
                                setBitmap(loadedBitmap)
                                setLoading(false)
                            }
                        }
                }
            } else {
                //val loadedBitmap = loadPicture("/storage/emulated/0/DCIM/test.png")
                setBitmap(loadedBitmap)
                setLoading(false)
            }
        } else if (!isVisible) {
            setBitmap(null)
            setLoading(true)
        }
    }

    val width = with(LocalDensity.current) { page.bounds.width.toDp() }
    val height = with(LocalDensity.current) { page.bounds.height.toDp() }
    Box(
        Modifier
            .width(width)
            .height(height)
    ) {
        val textMeasurer = rememberTextMeasurer()

        if (isVisible) {
            //println("isVisible.draw:${page.aPage.index}, $offset, ${page.bounds}")
            Canvas(modifier = Modifier.matchParentSize()) {
                if (bitmap != null) {
                    drawImage(
                        bitmap,
                        //srcOffset = IntOffset(-offset.x.toInt(), 0),
                        dstSize = IntSize(
                            page.bounds.width.toInt(),
                            page.bounds.height.toInt()
                        ),
                        dstOffset = IntOffset(
                            page.bounds.left.toInt() + offset.x.toInt(),
                            page.bounds.top.toInt() + offset.y.toInt()
                        )
                    )
                } else if (loading) {
                    //drawRect(color = Color.LightGray)
                } else {
                    //drawRect(color = Color.Red)
                }

                // 示例文字内容
                val textToDraw = page.aPage.index.toString()
                val textStyle = TextStyle(color = Color.Red, fontSize = 60.sp)

                val textLayoutResult = textMeasurer.measure(textToDraw, style = textStyle)

                // 计算文本位置使其水平和垂直居中
                val x =
                    (page.bounds.width - textLayoutResult.size.width) / 2 + page.bounds.left + offset.x
                val y =
                    (page.bounds.height - textLayoutResult.size.height) / 2 + page.bounds.top + offset.y

                // 绘制文本
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(x, y)
                )
            }
        }
    }
}