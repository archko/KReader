package com.archko.reader.viewer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntSize

class Page(
    private var viewSize: IntSize,
    private var zoom: Float,
    private var offset: Offset,
    private var aPage: APage,
    private var pageOffset: Float = 0f  // 页面的垂直偏移量
) {
    private var nodes: List<PageNode> = emptyList()

    fun update(viewSize: IntSize, zoom: Float, offset: Offset, pageOffset: Float) {
        this.viewSize = viewSize
        this.zoom = zoom
        this.offset = offset
        this.pageOffset = pageOffset
        recalculateNodes()
    }

    fun draw(drawScope: DrawScope) {
        nodes.forEach { node ->
            node.draw(drawScope)
        }
    }

    private fun recalculateNodes() {
        if (viewSize == IntSize.Zero) return

        // 计算页面的实际尺寸
        val viewWidth = viewSize.width.toFloat()
        // 计算缩放比例：view宽度 / 原始宽度
        val pageScale = viewWidth / aPage.width
        // 计算实际高度
        val pageHeight = aPage.height * pageScale

        // 应用视图缩放
        val scaledWidth = viewWidth * zoom
        val scaledHeight = pageHeight * zoom

        val contentOffset = Offset(
            (viewSize.width - scaledWidth) / 2 + offset.x,
            pageOffset * zoom + offset.y  // 使用缩放后的pageOffset
        )

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

    companion object {
        private const val maxSize = 512 * 512f
    }
}
