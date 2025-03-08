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
        if (!isVisible(drawScope)) {
            println("is not Visible:${aPage.index}, $offset, $rect")
            return
        }

        // 绘制边框
        drawScope.drawRect(
            color = Color.Green,
            topLeft = rect.topLeft,
            size = rect.size,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
        )

        // 绘制 ID
        drawScope.drawContext.canvas.nativeCanvas.drawText(
            aPage.index.toString(),
            rect.topLeft.x + rect.size.width / 2,
            rect.topLeft.y + rect.size.height / 2,
            android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 60f
                textAlign = android.graphics.Paint.Align.CENTER
            }
        )
    }

    private fun isVisible(drawScope: DrawScope): Boolean {
        // 获取画布的可视区域
        val visibleRect = Rect(
            left = 0f,
            top = 0f,
            right = drawScope.size.width,
            bottom = drawScope.size.height
        )

        // 检查页面是否与可视区域相交
        return rect.intersectsWith(visibleRect)
    }
}

fun Rect.intersectsWith(other: Rect): Boolean {
    return !(left > other.right ||
            right < other.left ||
            top > other.bottom ||
            bottom < other.top)
}