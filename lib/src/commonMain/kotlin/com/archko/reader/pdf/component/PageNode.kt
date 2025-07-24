package com.archko.reader.pdf.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import com.archko.reader.pdf.cache.ImageCache
import com.archko.reader.pdf.entity.APage

/**
 * @author: archko 2025/7/24 :08:19
 */
public class PageNode(
    private val pdfViewState: PdfViewState,
    public var logicalRect: Rect,  // 逻辑坐标(0~1)
    public var logicalBounds: Rect, // 逻辑坐标(0~1)
    public val aPage: APage
) {
    // 逻辑rect转实际像素，需乘以pageScale和vZoom
    public fun toPixelRect(pageWidth: Float, pageHeight: Float, yOffset: Float, pageScale: Float, vZoom: Float): Rect {
        val totalScale = pageScale * vZoom
        return Rect(
            left = logicalRect.left * pageWidth * totalScale,
            top = logicalRect.top * pageHeight * totalScale + yOffset * totalScale,
            right = logicalRect.right * pageWidth * totalScale,
            bottom = logicalRect.bottom * pageHeight * totalScale + yOffset * totalScale
        )
    }

    public fun toPixelBounds(pageWidth: Float, pageHeight: Float, yOffset: Float, pageScale: Float, vZoom: Float): Rect {
        val totalScale = pageScale * vZoom
        return Rect(
            left = logicalBounds.left * pageWidth * totalScale,
            top = logicalBounds.top * pageHeight * totalScale + yOffset * totalScale,
            right = logicalBounds.right * pageWidth * totalScale,
            bottom = logicalBounds.bottom * pageHeight * totalScale + yOffset * totalScale
        )
    }

    public val cacheKey: String
        get() = "${aPage.index}-${logicalRect}-${aPage.scale}-${pdfViewState.vZoom}"

    public fun draw(
        drawScope: DrawScope,
        offset: Offset,
        pageWidth: Float,
        pageHeight: Float,
        yOffset: Float,
        pageScale: Float,
        vZoom: Float
    ) {
        val pixelRect = toPixelRect(pageWidth, pageHeight, yOffset, pageScale, vZoom)
        val loadedBitmap: ImageBitmap? = ImageCache.get(cacheKey)
        println("[PageNode.draw] page=${aPage.index}, logicalRect=$logicalRect, pageWidth=$pageWidth, pageHeight=$pageHeight, yOffset=$yOffset, pageScale=$pageScale, vZoom=$vZoom, pixelRect=$pixelRect, bitmapSize=${loadedBitmap?.width}x${loadedBitmap?.height}")
        if (!isVisible(drawScope, offset, pixelRect, aPage.index)) {
            pdfViewState.remove(logicalRect, aPage, cacheKey, pageScale, vZoom)
            return
        }
        if (loadedBitmap != null) {
            drawScope.drawImage(
                loadedBitmap,
                dstOffset = IntOffset(pixelRect.left.toInt(), pixelRect.top.toInt())
            )
        } else {
            pdfViewState.decode(logicalRect, aPage, cacheKey, pageScale, vZoom)
            drawScope.drawRect(
                color = Color.Magenta,
                topLeft = Offset(pixelRect.left, pixelRect.top),
                size = pixelRect.size,
                style = Stroke(width = 6f)
            )
        }
    }

    private fun Rect.toString2(): String {
        return "($left, $top, $right, $bottom)"
    }
}