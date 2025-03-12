package com.archko.reader.pdf.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.entity.APage
import com.archko.reader.viewer.PageNode

public class Page(
    public var viewSize: IntSize,
    public var zoom: Float,
    public var offset: Offset,
    public var aPage: APage,
) {
    private var nodes: List<PageNode> = emptyList()
    private var lastContentOffset: Offset = Offset.Zero
    public var bounds: Rect = Rect(0f, 0f, 0f, 0f)
    private var pageOffset: Float = 0f

    public fun update(viewSize: IntSize, zoom: Float, bounds: Rect) {
        val isViewSizeChanged = this.viewSize != viewSize
        val isZoomChanged = this.zoom != zoom

        this.bounds = bounds
        this.viewSize = viewSize
        this.zoom = zoom
        this.offset = offset
        this.pageOffset = bounds.top

        if (isViewSizeChanged || isZoomChanged) {
            recalculateNodes()
        }
    }

    public fun update(viewSize: IntSize, zoom: Float, offset: Offset) {
        val isViewSizeChanged = this.viewSize != viewSize
        val isZoomChanged = this.zoom != zoom

        this.viewSize = viewSize
        this.zoom = zoom
        this.offset = offset
        this.pageOffset = bounds.top

        if (isViewSizeChanged || isZoomChanged) {
            recalculateNodes()
        }
    }

    public fun draw(drawScope: DrawScope, offset: Offset) {
        nodes.forEach { node ->
            node.draw(drawScope, offset)
        }
    }

    public fun updateOffset(newOffset: Offset) {
        this.offset = newOffset
        updateNodesPosition()
    }

    private fun updateNodesPosition() {
        val contentOffset = calculateContentOffset()
        if (contentOffset != lastContentOffset) {
            nodes.forEach { node ->
                node.rect = node.rect.translate(contentOffset - lastContentOffset)
            }
            lastContentOffset = contentOffset
        }
    }

    private fun calculateContentOffset(): Offset {
        val viewWidth = viewSize.width.toFloat()
        val pageScale = viewWidth / aPage.width
        val pageHeight = aPage.height * pageScale
        val scaledWidth = viewWidth * zoom
        val scaledHeight = pageHeight * zoom

        return Offset(
            (viewSize.width - scaledWidth) / 2 + offset.x,
            pageOffset * zoom + offset.y
        )
    }

    private fun recalculateNodes() {
        if (viewSize == IntSize.Zero) return

        val contentOffset = calculateContentOffset()
        lastContentOffset = contentOffset

        val viewWidth = viewSize.width.toFloat()
        val pageScale = viewWidth / aPage.width
        val scaledWidth = viewWidth * zoom
        val scaledHeight = aPage.height * pageScale * zoom

        val rootNode = PageNode(
            Rect(
                left = contentOffset.x,
                top = contentOffset.y,
                right = contentOffset.x + scaledWidth,
                bottom = contentOffset.y + scaledHeight
            ),
            aPage
        )

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

    public companion object {
        private const val maxSize = 256 * 384 * 4f * 2
    }
}
