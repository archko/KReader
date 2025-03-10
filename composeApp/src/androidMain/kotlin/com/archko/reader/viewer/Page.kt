package com.archko.reader.viewer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntSize

class Page(
    private var pdfState: PdfState,
    private var viewSize: IntSize,
    private var zoom: Float,
    var aPage: APage,
    var bounds: Rect,
) {
    private var aspectRatio = 1f
    private var nodes: List<PageNode> = emptyList()

    fun getAspectRatio(): Float {
        return aspectRatio
    }

    fun setAspectRatio(aspectRatio: Float) {
        if (this.aspectRatio != aspectRatio) {
            this.aspectRatio = aspectRatio
            pdfState.invalidatePageSizes()
        }
    }

    fun setAspectRatio(width: Int, height: Int) {
        setAspectRatio(width * 1.0f / height)
    }

    fun update(viewSize: IntSize, zoom: Float, bounds: Rect) {
        val isViewSizeChanged = this.viewSize != viewSize
        val isZoomChanged = this.zoom != zoom

        this.viewSize = viewSize
        this.zoom = zoom
        this.bounds = bounds

        if (isViewSizeChanged || isZoomChanged) {
            recalculateNodes()
        }
    }

    fun draw(drawScope: DrawScope, offset: Offset) {
        nodes.forEach { node ->
            if (isVisible(drawScope, offset)) {
                val drawRect = Rect(
                    bounds.left + offset.x,
                    bounds.top + offset.y,
                    bounds.right + offset.x,
                    bounds.bottom + offset.y
                )
                drawScope.drawContext.canvas.nativeCanvas.drawText(
                    aPage.index.toString(),
                    drawRect.topLeft.x + drawRect.size.width / 2,
                    drawRect.topLeft.y + drawRect.size.height / 2,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 60f
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                )
                //println("page.draw:${aPage.index}, $zoom, $viewSize, $bounds")
                node.draw(drawScope, offset)
            }
        }
    }

    private fun isVisible(drawScope: DrawScope, offset: Offset): Boolean {
        val visibleRect = Rect(
            left = -offset.x,
            top = -offset.y,
            right = drawScope.size.width - offset.x,
            bottom = drawScope.size.height - offset.y
        )
        val flag = bounds.intersectsWith(visibleRect)
        //println("page:${aPage.index}, isVisible:$flag, $visibleRect, $bounds, $viewSize")
        return flag
    }

    private fun recalculateNodes() {
        if (viewSize.width == 0 || viewSize.height == 0) return

        val rootNode = PageNode(
            Rect(
                left = bounds.left,
                top = bounds.top,
                right = bounds.right,
                bottom = bounds.bottom
            ),
            aPage
        )
        println("recalculateNodes:$zoom, $viewSize, bounds:$bounds, rootNode:$rootNode")

        nodes = calculatePages(rootNode)
    }

    private fun calculatePages(page: PageNode): List<PageNode> {
        val rect = page.rect
        if (rect.width * rect.height > maxSize) {
            val halfWidth = rect.width / 2
            val halfHeight = rect.height / 2
            return listOf(
                PageNode(
                    Rect(rect.left, rect.top, rect.left + halfWidth, rect.top + halfHeight),
                    page.aPage
                ),
                PageNode(
                    Rect(rect.left + halfWidth, rect.top, rect.right, rect.top + halfHeight),
                    page.aPage
                ),
                PageNode(
                    Rect(rect.left, rect.top + halfHeight, rect.left + halfWidth, rect.bottom),
                    page.aPage
                ),
                PageNode(
                    Rect(rect.left + halfWidth, rect.top + halfHeight, rect.right, rect.bottom),
                    page.aPage
                )
            ).flatMap {
                calculatePages(it)
            }
        }
        return listOf(page)
    }

    companion object {
        private const val maxSize = 256 * 384 * 4f * 2
    }
}
