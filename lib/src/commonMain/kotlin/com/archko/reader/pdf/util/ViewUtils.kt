package com.archko.reader.pdf.util

import androidx.compose.ui.geometry.Offset
import com.archko.reader.pdf.component.PageViewState
import com.archko.reader.pdf.component.Vertical
import kotlin.math.abs

/**
 * 文档空间下的交互计算工具。
 */
public class ViewUtils {
    public companion object {

        /**
         * 根据文档坐标计算点击的页面索引
         */
        public fun calculateClickedPage(
            docOffset: Offset,
            pageViewState: PageViewState
        ): Int {
            val pages = pageViewState.pages
            for (i in pages.indices) {
                if (pages[i].baseRect.contains(docOffset)) {
                    return i
                }
            }
            return pageViewState.firstVisiblePageIndex().coerceAtLeast(0)
        }

        public fun firstPage(
            pageViewState: PageViewState,
            onPageChanged: ((Int) -> Unit)?
        ): Int {
            val firstVisible = pageViewState.firstVisiblePageIndex()
            onPageChanged?.invoke(firstVisible)
            return firstVisible
        }

        /**
         * 点击翻页，坐标与 keepPx 都是屏幕空间。
         */
        public fun handleTapGesture(
            tapScreen: Offset,
            pageViewState: PageViewState,
            keepPx: Float,
            onOffsetChanged: (Offset) -> Unit
        ): Boolean {
            val viewport = pageViewState.viewport
            val offset = viewport.offset
            val vertical = pageViewState.orientation == Vertical || pageViewState.columnCount > 1
            val result = if (vertical) {
                val height = pageViewState.viewSize.height.toFloat()
                when {
                    tapScreen.y < height / 4 -> {
                        Offset(offset.x, offset.y + height - keepPx)
                    }

                    tapScreen.y > height * 3 / 4 -> {
                        Offset(offset.x, offset.y - height + keepPx)
                    }

                    else -> null
                }
            } else {
                val width = pageViewState.viewSize.width.toFloat()
                when {
                    tapScreen.x < width / 4 -> {
                        Offset(offset.x + width - keepPx, offset.y)
                    }

                    tapScreen.x > width * 3 / 4 -> {
                        Offset(offset.x - width + keepPx, offset.y)
                    }

                    else -> null
                }
            }
            if (result == null) {
                return false
            }
            onOffsetChanged(viewport.clampOffset(result))
            return true
        }

        // 计算直线坐标的方法
        public fun calculateLinePoints(start: Offset, end: Offset): List<Offset> {
            val dx = abs(end.x - start.x)
            val dy = abs(end.y - start.y)

            return if (dx > dy) {
                listOf(start, Offset(end.x, start.y))
            } else {
                listOf(start, Offset(start.x, end.y))
            }
        }
    }
}
