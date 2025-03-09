package com.archko.reader.viewer

import androidx.compose.animation.core.animateDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.archko.reader.pdf.flinger.FlingConfiguration
import com.archko.reader.pdf.flinger.SplineBasedFloatDecayAnimationSpec
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class APage(val index: Int, val width: Int, val height: Int, val scale: Float = 1f)

class PdfState(
    val list: MutableList<APage>,
    viewSize: IntSize
) {
    var pagePositions by mutableStateOf(calculatePagePositions(viewSize))
    var totalHeight by mutableFloatStateOf(calculateTotalHeight(viewSize))
    var pages by mutableStateOf(createPages())

    private fun calculatePagePositions(viewSize: IntSize): List<Float> {
        return if (viewSize.width == 0) emptyList() else {
            var currentY = 0f
            list.map { aPage ->
                val pageScale = viewSize.width.toFloat() / aPage.width
                val pageHeight = aPage.height * pageScale
                val position = currentY
                currentY += pageHeight
                position
            }
        }
    }

    private fun calculateTotalHeight(viewSize: IntSize): Float {
        return if (pagePositions.isEmpty()) 0f else {
            var currentY = 0f
            list.forEachIndexed { index, aPage ->
                val pageScale = viewSize.width.toFloat() / aPage.width
                currentY += aPage.height * pageScale
            }
            currentY
        }
    }

    private fun createPages(): List<Page> {
        return list.zip(pagePositions).map { (aPage, yPos) ->
            Page(IntSize.Zero, 1f, Offset.Zero, aPage, yPos)
        }
    }

    fun updateViewSize(viewSize: IntSize) {
        pagePositions = calculatePagePositions(viewSize)
        totalHeight = calculateTotalHeight(viewSize)
        pages = createPages()
    }
}

@Composable
fun CustomView(list: MutableList<APage>) {
    // 初始化状态
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val pdfState = remember(list, viewSize) { PdfState(list, viewSize) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var vZoom by remember { mutableFloatStateOf(1f) }
    val velocityTracker = remember { VelocityTracker() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var flingJob by remember { mutableStateOf<Job?>(null) }

    // 定义背景渐变
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(Color.Green, Color.Red)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(pdfState.totalHeight.dp)
            .onSizeChanged {
                viewSize = it
                pdfState.updateViewSize(it)
                pdfState.pages.zip(pdfState.pagePositions).forEach { (page, yPos) ->
                    page.update(viewSize, vZoom, offset, yPos)
                }
            },
        contentAlignment = Alignment.TopStart
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(pdfState.totalHeight.dp)
                .pointerInput(Unit) {
                    forEachGesture {
                        awaitPointerEventScope {
                            var initialZoom = vZoom
                            var startOffset = Offset.Zero
                            var initialDistance = 0f
                            var initialCenter = Offset.Zero

                            // 等待第一个触点
                            val firstDown = awaitFirstDown()
                            var pointerCount = 1

                            do {
                                flingJob?.cancel()
                                flingJob = null
                                val event = awaitPointerEvent()
                                pointerCount = event.changes.size

                                when {
                                    // 单指滑动处理
                                    pointerCount == 1 -> {
                                        event.changes[0].let { drag ->
                                            val delta = drag.position - drag.previousPosition
                                            velocityTracker.addPosition(
                                                drag.uptimeMillis,
                                                drag.position
                                            )

                                            val scaledWidth = viewSize.width * vZoom
                                            val scaledHeight = pdfState.totalHeight * vZoom

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

                                            // 更新页面位置
                                            pdfState.pages.forEach { page ->
                                                page.updateOffset(offset)
                                            }
                                        }
                                    }

                                    // 双指缩放处理
                                    pointerCount >= 2 -> {
                                        val point1 = event.changes[0].position
                                        val point2 = event.changes[1].position
                                        val currentDistance = (point1 - point2).getDistance()

                                        // 初始化缩放基准
                                        if (initialDistance == 0f) {
                                            initialDistance = currentDistance
                                            initialZoom = vZoom
                                            startOffset = offset
                                            initialCenter = (point1 + point2) / 2f
                                        }

                                        // 计算缩放比例（防止除零错误）
                                        val scaleFactor = if (initialDistance > 0) {
                                            currentDistance / initialDistance
                                        } else 1f

                                        // 应用缩放限制
                                        val newZoom = (initialZoom * scaleFactor).coerceIn(1f, 5f)

                                        // 计算基于画布中心的缩放偏移
                                        val contentCenter = Offset(
                                            size.width / 2 + offset.x,
                                            size.height / 2 + offset.y
                                        )

                                        // 计算新的偏移量
                                        offset = Offset(
                                            contentCenter.x * (1 - newZoom / vZoom) + startOffset.x * (newZoom / vZoom),
                                            contentCenter.y * (1 - newZoom / vZoom) + startOffset.y * (newZoom / vZoom)
                                        ).let { newOffset ->
                                            val maxX = (viewSize.width * (newZoom - 1) / 2)
                                                .coerceAtLeast(0f)
                                            val maxY = (pdfState.totalHeight * newZoom - viewSize.height)
                                                .coerceAtLeast(0f)
                                            Offset(
                                                newOffset.x.coerceIn(-maxX, maxX),
                                                newOffset.y.coerceIn(-maxY, 0f)
                                            )
                                        }

                                        // 更新状态
                                        vZoom = newZoom
                                        pdfState.pages.zip(pdfState.pagePositions).forEach { (page, yPos) ->
                                            page.update(viewSize, vZoom, offset, yPos)
                                        }
                                    }
                                }
                            } while (event.changes.any { it.pressed })

                            if (pointerCount > 1) {
                                return@awaitPointerEventScope
                            }
                            // 计算最终速度
                            val velocity = velocityTracker.calculateVelocity()
                            velocityTracker.resetTracking()

                            // 创建优化后的decay动画spec
                            val decayAnimationSpec = SplineBasedFloatDecayAnimationSpec(
                                density = density,
                                scrollConfiguration = FlingConfiguration.Builder()
                                    .scrollViewFriction(0.01f)  // 减小摩擦力，使滑动更流畅
                                    .numberOfSplinePoints(150)  // 提高采样率
                                    .splineInflection(0.1f)
                                    .splineStartTension(0.2f)
                                    .splineEndTension(1f)
                                    .build()
                            )

                            flingJob = scope.launch {
                                // 同时处理水平和垂直方向的惯性滑动
                                val scaledWidth = viewSize.width * vZoom
                                val scaledHeight = pdfState.totalHeight * vZoom
                                val maxX = (scaledWidth - viewSize.width).coerceAtLeast(0f) / 2
                                val maxY = (scaledHeight - viewSize.height).coerceAtLeast(0f)

                                // 创建两个协程同时处理x和y方向的动画
                                launch {
                                    if (kotlin.math.abs(velocity.x) > 50f) {  // 添加最小速度阈值
                                        animateDecay(
                                            initialValue = offset.x,
                                            initialVelocity = velocity.x,
                                            animationSpec = decayAnimationSpec
                                        ) { value, _ ->
                                            offset = offset.copy(x = value.coerceIn(-maxX, maxX))
                                            pdfState.pages.forEach { page -> page.updateOffset(offset) }
                                        }
                                    }
                                }

                                launch {
                                    if (kotlin.math.abs(velocity.y) > 50f) {  // 添加最小速度阈值
                                        animateDecay(
                                            initialValue = offset.y,
                                            initialVelocity = velocity.y,
                                            animationSpec = decayAnimationSpec
                                        ) { value, _ ->
                                            offset = offset.copy(y = value.coerceIn(-maxY, 0f))
                                            pdfState.pages.forEach { page -> page.updateOffset(offset) }
                                        }
                                    }
                                }
                            }

                            // 重置初始值
                            //initialDistance = 0f
                            //initialCenter = Offset.Zero
                        }
                    }
                }
        ) {
            val scaledHeight = pdfState.totalHeight * vZoom

            translate(left = offset.x, top = offset.y) {
                //只绘制可见区域.
                val visibleRect = Rect(
                    left = -offset.x,
                    top = -offset.y,
                    right = size.width - offset.x,
                    bottom = size.height - offset.y
                )
                drawRect(
                    brush = gradientBrush,
                    topLeft = visibleRect.topLeft,
                    size = visibleRect.size
                )
            }
            /*drawRect(
                brush = gradientBrush,
                topLeft = Offset(
                    (size.width - viewSize.width * vZoom) / 2 + offset.x,
                    offset.y
                ),
                size = Size(
                    width = viewSize.width * vZoom,
                    height = scaledHeight
                )
            )*/

            pdfState.pages.forEach { page -> page.draw(this, offset) }
        }
    }
}