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

const val CONTENT_HEIGHT = 6000f

@Composable
fun CustomView() {
    // 初始化状态
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var pages by remember { mutableStateOf<List<PageNode>>(emptyList()) }

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
                // 当视图大小改变时，重新计算页面
                pages = caculatePage(viewSize, scale, offset)
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
                            val maxX = (viewSize.width * (scale - 1) / 2).coerceAtLeast(0f)
                            val scaledContentHeight = CONTENT_HEIGHT * scale
                            val maxY = (scaledContentHeight - viewSize.height) / 2
                            val minY = -maxY

                            offset = Offset(
                                offset.x.coerceIn(-maxX, maxX),
                                offset.y.coerceIn(minY, maxY)
                            )

                            // 重新计算页面
                            pages = caculatePage(viewSize, scale, offset)
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

            // 绘制所有页面
            pages.forEach { page ->
                page.draw(this)
            }
        }
    }
}

private fun caculatePage(
    viewSize: IntSize,
    scale: Float,
    offset: Offset
): List<PageNode> {
    val scaledSize = IntSize(
        (viewSize.width * scale).toInt(),
        (CONTENT_HEIGHT * scale).toInt()
    )
    val contentOffset = Offset(
        (viewSize.width - scaledSize.width) / 2 + offset.x,
        (viewSize.height - scaledSize.height) / 2 + offset.y
    )
    val pages = calculatePages(
            PageNode(
            "0",
            Rect(
                left = contentOffset.x,
                top = contentOffset.y,
                right = contentOffset.x + scaledSize.width,
                bottom = contentOffset.y + scaledSize.height
            ),
                0
            )
    )
    return pages
}

const val maxSize = 512 * 512f
private fun calculatePages(page: PageNode): List<PageNode> {
    val rect = page.rect
    if (rect.width * rect.height > maxSize) {
        val level = page.level + 1
        val halfWidth = rect.width / 2
        val halfHeight = rect.height / 2
        return listOf(
            PageNode(
                "${level},0",
                Rect(rect.left, rect.top, rect.left + halfWidth, rect.top + halfHeight),
                level
            ),
            PageNode(
                "${level},1",
                Rect(rect.left + halfWidth, rect.top, rect.right, rect.top + halfHeight),
                level
            ),
            PageNode(
                "${level},2",
                Rect(rect.left, rect.top + halfHeight, rect.left + halfWidth, rect.bottom),
                level
            ),
            PageNode(
                "${level},3",
                Rect(rect.left + halfWidth, rect.top + halfHeight, rect.right, rect.bottom),
                level
            )
        ).flatMap {
            calculatePages(it)
        }
    }
    return listOf(page)
}