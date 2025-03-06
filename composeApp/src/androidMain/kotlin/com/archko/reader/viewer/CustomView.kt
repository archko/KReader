package com.archko.reader.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

data class APage(val index: Int, val width: Int, val height: Int, val scale: Float = 1f)

@Composable
fun CustomView(list: MutableList<APage>) {
    // 初始化状态
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var vZoom by remember { mutableFloatStateOf(1f) }
    
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
                    detectTransformGestures(
                        onGesture = { centroid, pan, zoom, _ ->
                            // 处理缩放和移动
                            vZoom = (vZoom * zoom).coerceIn(1.0f, 5f)
                            offset = offset + pan

                            // 限制移动范围
                            val scaledWidth = viewSize.width * vZoom
                            val scaledHeight = totalHeight * vZoom

                            // 计算水平方向的边界
                            val horizontalExcess = (scaledWidth - viewSize.width) / 2
                            val (minX, maxX) = if (scaledWidth > viewSize.width) {
                                -horizontalExcess to horizontalExcess
                            } else {
                                0f to 0f
                            }

                            // 计算垂直方向的边界
                            val maxScroll = (scaledHeight - viewSize.height).coerceAtLeast(0f)
                            val minY = -maxScroll
                            val maxY = 0f

                            // 限制offset在合法范围内
                            val newX = if (minX != maxX) offset.x.coerceIn(minX, maxX) else 0f
                            val newY = offset.y.coerceIn(minY, maxY)
                            offset = Offset(newX, newY)

                            // 更新所有页面
                            pages.zip(pagePositions).forEach { (page, yPos) ->
                                page.update(viewSize, vZoom, offset, yPos)
                            }
                        }
                    )
                }
        ) {
            // 绘制背景渐变
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

            // 绘制所有页面
            pages.forEach { page -> page.draw(this) }
        }
    }
}