package com.archko.reader.pdf.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.cache.ImageCache
import com.archko.reader.pdf.entity.APage

/**
 * @author: archko 2025/7/24 :08:19
 */
public class PageNode(
    private val pdfViewState: PdfViewState,
    public var bounds: Rect,  // 逻辑坐标(0~1)
    public val aPage: APage
) {
    // 逻辑rect转实际像素，直接用Page的width/height
    public fun toPixelRect(pageWidth: Float, pageHeight: Float, yOffset: Float): Rect {
        return Rect(
            left = bounds.left * pageWidth,
            top = bounds.top * pageHeight + yOffset,
            right = bounds.right * pageWidth,
            bottom = bounds.bottom * pageHeight + yOffset
        )
    }

    public val cacheKey: String
        get() = "${aPage.index}-${bounds}-${pdfViewState.vZoom}"

    public fun draw(
        drawScope: DrawScope,
        offset: Offset,
        pageWidth: Float,
        pageHeight: Float,
        yOffset: Float,
        totalScale: Float
    ) {
        val pixelRect = toPixelRect(pageWidth, pageHeight, yOffset)
        if (!isVisible(drawScope, offset, pixelRect, aPage.index)) {
            pdfViewState.remove(bounds, aPage, cacheKey, totalScale, pageWidth, pageHeight)
            return
        }

        val loadedBitmap: ImageBitmap? = ImageCache.get(cacheKey)
        if (loadedBitmap != null) {
            //println("[PageNode.draw] page=${aPage.index}, bounds=$bounds, pageWidth-Height=$pageWidth-$pageHeight, yOffset=$yOffset, offset=$offset, totalScale=$totalScale, pixelRect=$pixelRect, bitmapSize=${loadedBitmap.width}x${loadedBitmap.height}")
            drawScope.drawImage(
                loadedBitmap,
                dstOffset = IntOffset(pixelRect.left.toInt(), pixelRect.top.toInt()),
                dstSize = IntSize(pixelRect.width.toInt(), pixelRect.height.toInt())
            )
        } else {
            pdfViewState.decode(bounds, aPage, cacheKey, totalScale, pageWidth, pageHeight)
            drawScope.drawRect(
                color = Color.Magenta,
                topLeft = Offset(pixelRect.left, pixelRect.top),
                size = pixelRect.size,
                style = Stroke(width = 4f)
            )
        }
    }
}