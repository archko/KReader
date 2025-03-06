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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

const val CONTENT_HEIGHT = 2000f

@Composable
fun CustomView() {
    // 初始化状态
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var page by remember { mutableStateOf(Page(IntSize.Zero, 1f, Offset.Zero)) }

    // 定义背景渐变
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(Color.Green, Color.Red)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(CONTENT_HEIGHT.dp)
            .onSizeChanged {
                viewSize = it
                // 当视图大小改变时，更新页面
                page.update(viewSize, scale, offset)
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(CONTENT_HEIGHT.dp)
                .pointerInput(Unit) {
                    detectTransformGestures(
                        onGesture = { centroid, pan, zoom, _ ->
                            // 处理缩放和移动
                            scale = (scale * zoom).coerceIn(1.0f, 5f)
                            offset = offset + pan

                            // 限制移动范围
                            val scaledWidth = viewSize.width * scale
                            val scaledHeight = CONTENT_HEIGHT * scale
                            
                            // 计算水平方向的边界
                            val horizontalExcess = (scaledWidth - viewSize.width) / 2
                            val (minX, maxX) = if (scaledWidth > viewSize.width) {
                                -horizontalExcess to horizontalExcess
                            } else {
                                0f to 0f
                            }
                            
                            // 计算垂直方向的边界
                            val verticalExcess = (scaledHeight - viewSize.height) / 2
                            val (minY, maxY) = if (scaledHeight > viewSize.height) {
                                -verticalExcess to verticalExcess
                            } else {
                                0f to 0f
                            }

                            // 限制offset在合法范围内
                            val newX = if (minX != maxX) offset.x.coerceIn(minX, maxX) else 0f
                            val newY = if (minY != maxY) offset.y.coerceIn(minY, maxY) else 0f
                            offset = Offset(newX, newY)

                            // 更新页面
                            page.update(viewSize, scale, offset)
                        }
                    )
                }
        ) {
            // 绘制背景渐变
            val scaledContentHeight = CONTENT_HEIGHT * scale
            val contentOffset = Offset(
                (size.width - viewSize.width * scale) / 2 + offset.x,
                (size.height - scaledContentHeight) / 2 + offset.y
            )

            drawRect(
                brush = gradientBrush,
                topLeft = contentOffset,
                size = Size(
                    width = viewSize.width * scale,
                    height = scaledContentHeight
                )
            )

            // 绘制页面
            page.draw(this)
        }
    }
}