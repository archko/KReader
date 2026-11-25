package com.archko.reader.viewer.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private fun fractionOf(x: Float, width: Float, radius: Float): Float {
    val usableWidth = (width - radius * 2f).coerceAtLeast(1f)
    return ((x - radius) / usableWidth).coerceIn(0f, 1f)
}

private fun pageOf(fraction: Float, pageCount: Int): Int {
    return (fraction * (pageCount - 1)).roundToInt().coerceIn(0, pageCount - 1)
}

/**
 * 细进度条 + 圆点的页码拖动组件。
 * 不展示刻度，两端对齐，按住时圆点轻微放大。
 */
@Composable
fun ThinSeekBar(
    currentPage: Int,
    pageCount: Int,
    isExternalChange: Boolean = false,
    onJumpPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
    trackHeight: Dp = 3.dp,
    thumbRadius: Dp = 6.dp,
    thumbStrokeWidth: Dp = 2.dp,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.Gray.copy(alpha = 0.4f),
) {
    if (pageCount <= 1) return

    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    val latestOnJumpPage by rememberUpdatedState(onJumpPage)
    val latestCurrentPage by rememberUpdatedState(currentPage)

    // 外部页码变化后重新跟随真实页码
    LaunchedEffect(currentPage, pageCount) {
        if (!isDragging) {
            dragFraction = null
        }
    }

    val targetFraction = (currentPage.toFloat() / (pageCount - 1)).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = dragFraction ?: targetFraction,
        animationSpec = tween(durationMillis = if (isDragging) 0 else 120),
        label = "seekFraction"
    )
    val animatedThumbRadius by animateDpAsState(
        targetValue = if (isDragging) thumbRadius * 1.35f else thumbRadius,
        animationSpec = tween(durationMillis = 120),
        label = "thumbRadius"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(pageCount, isExternalChange) {
                detectTapGestures { offset ->
                    if (isExternalChange) return@detectTapGestures
                    val fraction = fractionOf(offset.x, size.width.toFloat(), thumbRadius.toPx())
                    val targetPage = pageOf(fraction, pageCount)
                    if (targetPage != latestCurrentPage) {
                        latestOnJumpPage(targetPage)
                    }
                }
            }
            .pointerInput(pageCount, isExternalChange) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragFraction =
                            fractionOf(offset.x, size.width.toFloat(), thumbRadius.toPx())
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragFraction =
                            fractionOf(change.position.x, size.width.toFloat(), thumbRadius.toPx())
                    },
                    onDragEnd = {
                        val finalFraction = dragFraction
                        isDragging = false
                        if (finalFraction == null || isExternalChange) {
                            dragFraction = null
                        } else {
                            val targetPage = pageOf(finalFraction, pageCount)
                            if (targetPage != latestCurrentPage) {
                                latestOnJumpPage(targetPage)
                            } else {
                                dragFraction = null
                            }
                        }
                    },
                    onDragCancel = {
                        isDragging = false
                        dragFraction = null
                    }
                )
            }
    ) {
        val rPx = animatedThumbRadius.toPx()
        val baseRPx = thumbRadius.toPx()
        val startX = baseRPx
        val endX = (size.width - baseRPx).coerceAtLeast(startX)
        val usableWidth = (endX - startX).coerceAtLeast(0f)
        val centerY = size.height / 2f
        val strokePx = trackHeight.toPx()
        val ringStrokePx = thumbStrokeWidth.toPx()
        val thumbX = (startX + usableWidth * animatedFraction)
            .coerceIn(rPx, (size.width - rPx).coerceAtLeast(rPx))

        // 已走过的细进度条（在圆点处留空，保证空心效果）
        val activeEndX = (thumbX - rPx).coerceAtLeast(startX)
        if (activeEndX > startX) {
            drawLine(
                color = activeColor,
                start = Offset(startX, centerY),
                end = Offset(activeEndX, centerY),
                strokeWidth = strokePx,
                cap = StrokeCap.Round
            )
        }

        // 未走过的轨道
        val inactiveStartX = (thumbX + rPx).coerceAtMost(endX)
        if (inactiveStartX < endX) {
            drawLine(
                color = inactiveColor,
                start = Offset(inactiveStartX, centerY),
                end = Offset(endX, centerY),
                strokeWidth = strokePx,
                cap = StrokeCap.Round
            )
        }

        // 空心滑块圆环
        drawCircle(
            color = activeColor,
            radius = (rPx - ringStrokePx / 2f).coerceAtLeast(0f),
            center = Offset(thumbX, centerY),
            style = Stroke(width = ringStrokePx)
        )
    }
}
