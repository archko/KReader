package com.archko.reader.viewer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.archko.reader.pdf.flinger.FlingConfiguration
import com.archko.reader.pdf.flinger.SplineBasedFloatDecayAnimationSpec
import kotlinx.coroutines.launch

data class APage(val index: Int, val width: Int, val height: Int, val scale: Float = 1f)

@Composable
fun CustomView(list: MutableList<APage>) {
    // 初始化状态
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var vZoom by remember { mutableFloatStateOf(1f) }
    val velocityTracker = remember { VelocityTracker() }
    val scope = rememberCoroutineScope()
    // 在scope.launch之前添加：
    val density = LocalDensity.current
    val velocityDecay: DecayAnimationSpec<Float> = remember { exponentialDecay() }
    var offsetX = remember { Animatable(0f) }
    var offsetY = remember { Animatable(0f) }

    // 计算每个页面的位置和总高度
    val pagePositions = remember(viewSize.width, list) {
        if (viewSize.width == 0) emptyList() else {
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

    // 计算总高度
    val totalHeight = remember(pagePositions) {
        if (pagePositions.isEmpty()) 0f else {
            var currentY = 0f
            list.forEachIndexed { index, aPage ->
                val pageScale = viewSize.width.toFloat() / aPage.width
                currentY += aPage.height * pageScale
            }
            currentY
        }
    }

    // 创建并记住每个页面的Page对象
    var pages by remember(list, pagePositions) {
        mutableStateOf(list.zip(pagePositions).map { (aPage, yPos) ->
            Page(IntSize.Zero, 1f, Offset.Zero, aPage, yPos)
        })
    }

    // 定义背景渐变
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(Color.Green, Color.Red)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(totalHeight.dp)
            .onSizeChanged {
                viewSize = it
                pages.zip(pagePositions).forEach { (page, yPos) ->
                    page.update(viewSize, vZoom, offset, yPos)
                }
            },
        contentAlignment = Alignment.TopStart
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalHeight.dp)
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
                                            val scaledHeight = totalHeight * vZoom

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
                                            pages.forEach { page ->
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
                                            val maxX =
                                                (viewSize.width * (newZoom - 1) / 2).coerceAtLeast(
                                                    0f
                                                )
                                            val maxY =
                                                (totalHeight * newZoom - viewSize.height).coerceAtLeast(
                                                    0f
                                                )
                                            Offset(
                                                newOffset.x.coerceIn(-maxX, maxX),
                                                newOffset.y.coerceIn(-maxY, 0f)
                                            )
                                        }

                                        // 更新状态
                                        vZoom = newZoom
                                        pages.zip(pagePositions).forEach { (page, yPos) ->
                                            page.update(viewSize, vZoom, offset, yPos)
                                        }
                                    }
                                }
                            } while (event.changes.any { it.pressed })

                            // 修改fling部分代码：
                            scope.launch {
                                val velocity = velocityTracker.calculateVelocity()

                                /*val scaledHeight = totalHeight * vZoom
                                val maxScroll = (scaledHeight - viewSize.height).coerceAtLeast(0f)
                                offsetY.animateTo(
                                    targetValue = offset.y,
                                    initialVelocity = velocity.y,
                                    animationSpec = TweenSpec(
                                        durationMillis = 2000,
                                        easing = LinearEasing
                                    )
                                ) {
                                    val newY = value.coerceIn(-maxScroll, 0f)
                                    offset = offset.copy(y = newY)
                                    pages.zip(pagePositions).forEach { (page, yPos) ->
                                        page.updateOffset(offset)
                                    }
                                }*/

                                // 创建decay动画spec
                                val decayAnimationSpec = SplineBasedFloatDecayAnimationSpec(
                                    density, FlingConfiguration.Builder()
                                        .build()
                                )

                                // 水平fling
                                animateDecay(
                                    initialValue = offset.x,
                                    initialVelocity = velocity.x,
                                    animationSpec = decayAnimationSpec
                                ) { value, _ ->
                                    val scaledWidth = viewSize.width * vZoom
                                    val maxX = (scaledWidth - viewSize.width).coerceAtLeast(0f) / 2
                                    offset = offset.copy(x = value.coerceIn(-maxX, maxX))
                                    pages.forEach { page ->
                                        page.updateOffset(offset)
                                    }
                                }

                                // 垂直fling
                                animateDecay(
                                    initialValue = offset.y,
                                    initialVelocity = velocity.y,
                                    animationSpec = decayAnimationSpec
                                ) { value, _ ->
                                    val scaledHeight = totalHeight * vZoom
                                    val maxY = (scaledHeight - viewSize.height).coerceAtLeast(0f)
                                    offset = offset.copy(y = value.coerceIn(-maxY, 0f))
                                    pages.forEach { page ->
                                        page.updateOffset(offset)
                                    }
                                }
                            }

                            // 重置初始值
                            initialDistance = 0f
                            initialCenter = Offset.Zero
                        }
                    }
                }
        ) {
            val scaledHeight = totalHeight * vZoom

            drawRect(
                brush = gradientBrush,
                topLeft = Offset(
                    (size.width - viewSize.width * vZoom) / 2 + offset.x,
                    offset.y
                ),
                size = Size(
                    width = viewSize.width * vZoom,
                    height = scaledHeight
                )
            )

            pages.forEach { page -> page.draw(this) }
        }
    }
}