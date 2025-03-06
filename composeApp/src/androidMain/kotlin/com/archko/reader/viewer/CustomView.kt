package com.archko.reader.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

data class APage(val index: Int, val width: Int, val height: Int, val scale: Float = 1f)

@Composable
fun CustomView(list: MutableList<APage>) {
    // 初始化状态
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var vZoom by remember { mutableFloatStateOf(1f) }
    val velocityTracker = remember { VelocityTracker() }
    val scope = rememberCoroutineScope()

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
                            val down = awaitFirstDown()
                            velocityTracker.resetTracking()

                            var pastTouchSlop = false
                            val touchSlop = viewConfiguration.touchSlop

                            do {
                                val event = awaitPointerEvent()
                                val dragEvent = event.changes[0]

                                if (!dragEvent.pressed) break

                                if (!pastTouchSlop) {
                                    val delta = dragEvent.position - dragEvent.previousPosition
                                    if (abs(delta.y) > touchSlop) {
                                        pastTouchSlop = true
                                    }
                                }

                                if (pastTouchSlop) {
                                    dragEvent.consume()
                                    velocityTracker.addPosition(
                                        dragEvent.uptimeMillis,
                                        dragEvent.position
                                    )

                                    val delta = dragEvent.position - dragEvent.previousPosition
                                    val scaledHeight = totalHeight * vZoom
                                    val maxScroll =
                                        (scaledHeight - viewSize.height).coerceAtLeast(0f)
                                    offset = offset.copy(
                                        y = (offset.y + delta.y).coerceIn(-maxScroll, 0f)
                                    )
                                }
                            } while (dragEvent.pressed)

                            // 处理惯性滚动
                            if (pastTouchSlop) {
                                val velocity = velocityTracker.calculateVelocity()
                                scope.launch {
                                    // 显著增加初始速度
                                    var velocityY = velocity.y * 3f
                                    var lastValue = offset.y
                                    var lastTime = System.nanoTime()

                                    // 降低停止阈值，让滚动持续更长
                                    while (abs(velocityY) > 0.1f) {
                                        val currentTime = System.nanoTime()
                                        val deltaSeconds = (currentTime - lastTime) / 1_000_000_000f
                                        lastTime = currentTime

                                        // 增加滚动系数使滚动更流畅
                                        val scrollDelta = velocityY * deltaSeconds * 1.2f
                                        val scaledHeight = totalHeight * vZoom
                                        val maxScroll = (scaledHeight - viewSize.height).coerceAtLeast(0f)
                                        val newY = (lastValue - scrollDelta).coerceIn(-maxScroll, 0f)
                                        println("scroll:velocityY:$velocityY, second:$deltaSeconds, scrollDelta:$scrollDelta, maxScroll:$maxScroll, newY:$newY, lastValue:$lastValue")

                                        if (newY == lastValue) break

                                        offset = offset.copy(y = newY)
                                        lastValue = newY

                                        // 极慢的衰减率
                                        velocityY *= 0.995f
                                    }
                                }
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        vZoom = (vZoom * zoom).coerceIn(1.0f, 5f)

                        val scaledWidth = viewSize.width * vZoom
                        val scaledHeight = totalHeight * vZoom

                        val horizontalExcess = (scaledWidth - viewSize.width) / 2
                        val (minX, maxX) = if (scaledWidth > viewSize.width) {
                            -horizontalExcess to horizontalExcess
                        } else {
                            0f to 0f
                        }

                        val maxScroll = (scaledHeight - viewSize.height).coerceAtLeast(0f)
                        val minY = -maxScroll
                        val maxY = 0f

                        val newX = if (minX != maxX) (offset.x + pan.x).coerceIn(minX, maxX) else 0f
                        val newY = (offset.y + pan.y).coerceIn(minY, maxY)
                        offset = Offset(newX, newY)

                        pages.zip(pagePositions).forEach { (page, yPos) ->
                            page.update(viewSize, vZoom, offset, yPos)
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