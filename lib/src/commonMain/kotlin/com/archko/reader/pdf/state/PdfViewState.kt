package com.archko.reader.pdf.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.component.Page
import com.archko.reader.pdf.entity.APage

/**
 * @author: archko 2025/3/11 :13:57
 */
public class PdfViewState(
    public val list: List<APage>,
    public val state: LocalPdfState,
) {
    public var init: Boolean by mutableStateOf(false)
    public var totalHeight: Float by mutableFloatStateOf(0f)
    public var pages: List<Page> by mutableStateOf(createPages())
    public var viewSize: IntSize by mutableStateOf(IntSize.Zero)
    public var vZoom: Float by mutableFloatStateOf(1f)

    public fun invalidatePageSizes() {
        var currentY = 0f
        if (viewSize.width == 0 || viewSize.height == 0 || list.isEmpty()) {
            println("PdfViewState.viewSize高宽为0,或list为空,不计算page: viewSize:$viewSize, list:$list, totalHeight:$totalHeight")
            totalHeight = viewSize.height.toFloat()
            init = false
        } else {
            list.zip(pages).forEach { (aPage, page) ->
                val pageWidth = viewSize.width * vZoom
                val pageScale = pageWidth / aPage.width
                val pageHeight = aPage.height * pageScale
                val bounds = Rect(
                    0f, currentY,
                    pageWidth,
                    currentY + pageHeight
                )
                currentY += pageHeight
                page.update(viewSize, vZoom, bounds)
                //println("PdfViewState.bounds:$currentY, bounds:$bounds, page:${page.bounds}")
            }
            init = true
        }
        totalHeight = currentY
        println("invalidatePageSizes.totalHeight:$totalHeight, zoom:$vZoom, viewSize:$viewSize")
    }

    private fun createPages(): List<Page> {
        return list.map { aPage ->
            Page(this, state, IntSize.Zero, 1f, aPage, Rect(0f, 0f, 0f, 0f))
        }
    }

    public fun updateViewSize(viewSize: IntSize, vZoom: Float) {
        val isViewSizeChanged = this.viewSize != viewSize
        val isZoomChanged = this.vZoom != vZoom

        this.viewSize = viewSize
        this.vZoom = vZoom
        if (isViewSizeChanged || isZoomChanged) {
            invalidatePageSizes()
        } else {
            println("PdfViewState.viewSize未变化: vZoom:$vZoom, totalHeight:$totalHeight, viewSize:$viewSize")
        }
    }
}