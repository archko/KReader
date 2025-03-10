package com.archko.reader.viewer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas

// 将 Page 类重命名为 PageNode
class PageNode(
    var rect: Rect,
    val aPage: APage  // 添加 APage 属性
) {
    fun draw(drawScope: DrawScope, offset: Offset) {
        // 检查页面是否在可视区域内
        if (!isVisible(drawScope, offset)) {
            //println("is not Visible:${aPage.index}, $offset, $rect")
            return
        }

        // 绘制边框
        val drawRect = Rect(
            rect.left + offset.x,
            rect.top + offset.y,
            rect.right + offset.x,
            rect.bottom + offset.y
        )
        drawScope.drawRect(
            color = Color.Green,
            topLeft = drawRect.topLeft,
            size = rect.size,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
        )

        // 绘制 ID
        /*drawScope.drawContext.canvas.nativeCanvas.drawText(
            aPage.index.toString(),
            drawRect.topLeft.x + drawRect.size.width / 2,
            drawRect.topLeft.y + drawRect.size.height / 2,
            android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 60f
                textAlign = android.graphics.Paint.Align.CENTER
            }
        )*/
    }

    private fun isVisible(drawScope: DrawScope, offset: Offset): Boolean {
        // 获取画布的可视区域
        val visibleRect = Rect(
            left = -offset.x,
            top = -offset.y,
            right = drawScope.size.width - offset.x,
            bottom = drawScope.size.height - offset.y
        )

        // 检查页面是否与可视区域相交
        val flag = rect.intersectsWith(visibleRect)
        //println("isVisible:${aPage.index}, $flag, $visibleRect, $rect")
        return flag
    }
}

fun Rect.intersectsWith(other: Rect): Boolean {
    return !(left > other.right ||
            right < other.left ||
            top > other.bottom ||
            bottom < other.top)
}