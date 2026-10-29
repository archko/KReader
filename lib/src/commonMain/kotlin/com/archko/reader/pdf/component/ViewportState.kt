package com.archko.reader.pdf.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize

/**
 * 视口变换：screen = doc * zoom + offset。
 * 文档尺寸不含 zoom，缩放/平移只影响这里。
 */
internal class ViewportState {
    var viewSize: IntSize = IntSize.Zero
    var zoom: Float = 1f
    var offset: Offset = Offset.Zero
    var docWidth: Float = 0f
    var docHeight: Float = 0f

    val scaledWidth: Float get() = docWidth * zoom
    val scaledHeight: Float get() = docHeight * zoom

    fun setContentSize(width: Float, height: Float) {
        docWidth = width
        docHeight = height
    }

    fun toDoc(screen: Offset): Offset {
        if (zoom <= 0f) return Offset.Zero
        return Offset((screen.x - offset.x) / zoom, (screen.y - offset.y) / zoom)
    }

    fun toScreen(doc: Offset): Offset {
        return Offset(doc.x * zoom + offset.x, doc.y * zoom + offset.y)
    }

    val docRect: Rect
        get() {
            val topLeft = toDoc(Offset.Zero)
            val bottomRight = toDoc(
                Offset(viewSize.width.toFloat(), viewSize.height.toFloat())
            )
            return Rect(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
        }

    fun clampOffset(target: Offset): Offset {
        val contentWidth = scaledWidth
        val contentHeight = scaledHeight
        val minX = if (contentWidth > viewSize.width) viewSize.width - contentWidth else 0f
        val minY = if (contentHeight > viewSize.height) viewSize.height - contentHeight else 0f
        return Offset(
            target.x.coerceIn(minX, 0f),
            target.y.coerceIn(minY, 0f)
        )
    }

    fun clampZoom(value: Float, minZoom: Float, maxZoom: Float): Float {
        return value.coerceIn(minZoom, maxZoom)
    }
}
