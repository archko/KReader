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
    private var viewSize: IntSize,
    private var scale: Float,   // pageScale
    internal var aPage: APage,
    public var yOffset: Float = 0f
) {
    public var nodes: List<PageNode> = emptyList()

    //page bound, should be caculate after view measured
    internal var bounds = Rect(0f, 0f, 0f, 0f)

    /**
     * @param viewSize view size
     * @param scale view zoom,not the page zoom,default=1f
     * @param yOffset page.top
     */
    public fun update(viewSize: IntSize, scale: Float, rect: Rect) {
        this.viewSize = viewSize
        this.scale = scale
        this.bounds = rect
        this.yOffset = bounds.top

        recalculateNodes()
    }

    public fun draw(drawScope: DrawScope, offset: Offset, vZoom: Float) {
        if (!isVisible(drawScope, offset, bounds, aPage.index)) {
            return
        }
        val pageWidth = aPage.width.toFloat()
        val pageHeight = aPage.height.toFloat()
        nodes.forEach { node ->
            node.draw(drawScope, offset, pageWidth, pageHeight, yOffset, scale, vZoom)
        }
        val totalScale = scale * vZoom
        drawScope.drawRect(
            color = Color.Yellow,
            topLeft = Offset(0f, bounds.top * totalScale),
            size = Size(bounds.width * totalScale, bounds.height * totalScale),
            style = Stroke(width = 8f)
        )
    }

    private fun recalculateNodes() {
        if (viewSize == IntSize.Zero) return

        val rootNode = PageNode(
            pdfViewState,
            Rect(0f, 0f, 1f, 1f), // 逻辑坐标全页
            Rect(0f, 0f, 1f, 1f),
            aPage
        )

        println("page.recalculateNodes.scale:$scale, offset:$yOffset, $bounds, w-h:${bounds.width}-${bounds.height}, $aPage")
        nodes = calculatePages(rootNode)
    }

    private fun calculatePages(page: PageNode): List<PageNode> {
        val rect = page.logicalRect
        val area = (rect.right - rect.left) * (rect.bottom - rect.top)
        if (area > 0.25f) { // 这里假设分4块，实际可根据需要调整
            val halfW = (rect.left + rect.right) / 2
            val halfH = (rect.top + rect.bottom) / 2
            return listOf(
                PageNode(
                    pdfViewState,
                    Rect(rect.left, rect.top, halfW, halfH),
                    Rect(0f, 0f, 0.5f, 0.5f),
                    page.aPage
                ),
                PageNode(
                    pdfViewState,
                    Rect(halfW, rect.top, rect.right, halfH),
                    Rect(0.5f, 0f, 1f, 0.5f),
                    page.aPage
                ),
                PageNode(
                    pdfViewState,
                    Rect(rect.left, halfH, halfW, rect.bottom),
                    Rect(0f, 0.5f, 0.5f, 1f),
                    page.aPage
                ),
                PageNode(
                    pdfViewState,
                    Rect(halfW, halfH, rect.right, rect.bottom),
                    Rect(0.5f, 0.5f, 1f, 1f),
                    page.aPage
                )
            ).flatMap { calculatePages(it) }
        }
        return listOf(page)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Page

        if (viewSize != other.viewSize) return false
        if (scale != other.scale) return false
        if (aPage != other.aPage) return false
        if (yOffset != other.yOffset) return false
        if (nodes != other.nodes) return false
        if (bounds != other.bounds) return false

        return true
    }

    override fun hashCode(): Int {
        var result = viewSize.hashCode()
        result = 31 * result + scale.hashCode()
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