package com.archko.reader.viewer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntSize

class Page(
    private var viewSize: IntSize,
    private var scale: Float,
    private var offset: Offset
) {
    private var nodes: List<PageNode> = emptyList()

    fun update(viewSize: IntSize, scale: Float, offset: Offset) {
        this.viewSize = viewSize
        this.scale = scale
        this.offset = offset
        recalculateNodes()
    }

    fun draw(drawScope: DrawScope) {
        nodes.forEach { node ->
            node.draw(drawScope)
        }
    }

    private fun recalculateNodes() {
        if (viewSize == IntSize.Zero) return

        val scaledSize = IntSize(
            (viewSize.width * scale).toInt(),
            (CONTENT_HEIGHT * scale).toInt()
        )
        val contentOffset = Offset(
            (viewSize.width - scaledSize.width) / 2 + offset.x,
            (viewSize.height - scaledSize.height) / 2 + offset.y
        )

        val rootNode = PageNode(
            "0",
            Rect(
                left = contentOffset.x,
                top = contentOffset.y,
                right = contentOffset.x + scaledSize.width,
                bottom = contentOffset.y + scaledSize.height
            ),
            0
        )

        nodes = calculatePages(rootNode)
    }

    private fun calculatePages(page: PageNode): List<PageNode> {
        val rect = page.rect
        if (rect.width * rect.height > maxSize) {
            val level = page.level + 1
            val halfWidth = rect.width / 2
            val halfHeight = rect.height / 2
            return listOf(
                PageNode(
                    "${level},0",
                    Rect(rect.left, rect.top, rect.left + halfWidth, rect.top + halfHeight),
                    level
                ),
                PageNode(
                    "${level},1",
                    Rect(rect.left + halfWidth, rect.top, rect.right, rect.top + halfHeight),
                    level
                ),
                PageNode(
                    "${level},2",
                    Rect(rect.left, rect.top + halfHeight, rect.left + halfWidth, rect.bottom),
                    level
                ),
                PageNode(
                    "${level},3",
                    Rect(rect.left + halfWidth, rect.top + halfHeight, rect.right, rect.bottom),
                    level
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
