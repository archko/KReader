package com.archko.reader.pdf.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.entity.APage

/**
 * @author: archko 2025/7/24 :08:20
 */
public class Page(
    private val pdfViewState: PdfViewState,
    public var width: Float,   // 最终缩放后的宽
    public var height: Float,  // 最终缩放后的高
    internal var aPage: APage,
    public var yOffset: Float = 0f
) {
    public var totalScale: Float = 1f
    public var nodes: List<PageNode> = emptyList()

    //page bound, should be caculate after view measured
    internal var bounds = Rect(0f, 0f, 0f, 0f)

    /**
     * @param viewSize view size
     * @param scale view zoom,not the page zoom,default=1f
     * @param yOffset page.top
     */
    public fun update(width: Float, height: Float, rect: Rect) {
        this.width = width
        this.height = height
        this.bounds = rect
        this.yOffset = bounds.top
        this.totalScale = if (aPage.width == 0) 1f else width / aPage.width
        recalculateNodes()
    }

    public fun draw(drawScope: DrawScope, offset: Offset, vZoom: Float) {
        if (!isVisible(drawScope, offset, bounds, aPage.index)) {
            return
        }
        nodes.forEach { node ->
            node.draw(drawScope, offset, width, height, yOffset, totalScale)
        }
        // 占位框
        drawScope.drawRect(
            color = Color.Yellow,
            topLeft = Offset(0f, bounds.top),
            size = Size(bounds.width, bounds.height),
            style = Stroke(width = 8f)
        )
    }

    private fun recalculateNodes() {
        val rootNode = PageNode(
            pdfViewState,
            Rect(0f, 0f, 1f, 1f),
            Rect(0f, 0f, 1f, 1f),
            aPage
        )
        nodes = calculatePages(rootNode)
    }

    private fun calculatePages(page: PageNode): List<PageNode> {
        val rect = page.logicalRect
        val area = (rect.right - rect.left) * (rect.bottom - rect.top)
        if (area > 0.25f) {
            val halfW = (rect.left + rect.right) / 2
            val halfH = (rect.top + rect.bottom) / 2
            return listOf(
                PageNode(pdfViewState, Rect(rect.left, rect.top, halfW, halfH), Rect(0f, 0f, 0.5f, 0.5f), page.aPage),
                PageNode(pdfViewState, Rect(halfW, rect.top, rect.right, halfH), Rect(0.5f, 0f, 1f, 0.5f), page.aPage),
                PageNode(pdfViewState, Rect(rect.left, halfH, halfW, rect.bottom), Rect(0f, 0.5f, 0.5f, 1f), page.aPage),
                PageNode(pdfViewState, Rect(halfW, halfH, rect.right, rect.bottom), Rect(0.5f, 0.5f, 1f, 1f), page.aPage)
            ).flatMap { calculatePages(it) }
        }
        return listOf(page)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Page

        if (width != other.width) return false
        if (height != other.height) return false
        if (aPage != other.aPage) return false
        if (yOffset != other.yOffset) return false
        if (nodes != other.nodes) return false
        if (bounds != other.bounds) return false

        return true
    }

    override fun hashCode(): Int {
        var result = width.hashCode()
        result = 31 * result + height.hashCode()
        result = 31 * result + aPage.hashCode()
        result = 31 * result + yOffset.hashCode()
        result = 31 * result + nodes.hashCode()
        result = 31 * result + bounds.hashCode()
        return result
    }

    public companion object {
        private const val maxSize = 256 * 384 * 4f * 2
    }
}

public fun isVisible(drawScope: DrawScope, offset: Offset, bounds: Rect, page: Int): Boolean {
    // 获取画布的可视区域
    val visibleRect = Rect(
        left = -offset.x,
        top = -offset.y,
        right = drawScope.size.width - offset.x,
        bottom = drawScope.size.height - offset.y
    )

    // 检查页面是否与可视区域相交
    val visible = bounds.overlaps(visibleRect)
    //println("page.draw.isVisible:$visible, offset:$offset, bounds:$bounds, visibleRect:$visibleRect, $page")
    return visible
}