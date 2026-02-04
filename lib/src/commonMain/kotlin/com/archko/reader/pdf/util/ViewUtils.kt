package com.archko.reader.pdf.util

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.component.PageViewState
import com.archko.reader.pdf.component.Vertical
import kotlin.math.abs

/**
 * @author: archko 2026/2/4 :08:34
 */
public class ViewUtils {
    public companion object {

        /**
         * 根据点击坐标计算点击的页面索引
         */
        public fun calculateClickedPage(
            tapOffset: Offset,
            currentOffset: Offset,
            orientation: Int,
            pageViewState: PageViewState
        ): Int {
            // 将点击坐标转换为相对于内容的位置
            val contentX = tapOffset.x - currentOffset.x
            val contentY = tapOffset.y - currentOffset.y

            // 查找包含该坐标的页面
            val pages = pageViewState.pages
            for (i in pages.indices) {
                val page = pages[i]
                if (orientation == Vertical) {
                    // 垂直模式：检查Y坐标是否在页面范围内
                    if (contentY >= page.bounds.top && contentY <= page.bounds.bottom) {
                        return i
                    }
                } else {
                    // 水平模式：检查X坐标是否在页面范围内
                    if (contentX >= page.bounds.left && contentX <= page.bounds.right) {
                        return i
                    }
                }
            }

            // 如果没有找到匹配的页面，返回第一个可见页面
            return pages.indexOfFirst { page ->
                if (orientation == Vertical) {
                    val top = -currentOffset.y
                    val bottom = top + pageViewState.viewSize.height
                    page.bounds.bottom > top && page.bounds.top < bottom
                } else {
                    val left = -currentOffset.x
                    val right = left + pageViewState.viewSize.width
                    page.bounds.right > left && page.bounds.left < right
                }
            }.coerceAtLeast(0)
        }

        public fun firstPage(
            pageViewState: PageViewState,
            offset: Offset,
            orientation: Int,
            viewSize: IntSize,
            onPageChanged: ((Int) -> Unit)?
        ): Int {
            var firstVisible = 0
            val pages = pageViewState.pages
            if (pages.isNotEmpty()) {
                val offsetY = offset.y
                val offsetX = offset.x
                firstVisible = pages.indexOfFirst { page ->
                    if (orientation == Vertical) {
                        val top = -offsetY
                        val bottom = top + viewSize.height
                        page.bounds.bottom > top && page.bounds.top < bottom
                    } else {
                        val left = -offsetX
                        val right = left + viewSize.width
                        page.bounds.right > left && page.bounds.left < right
                    }
                }
                if (firstVisible != -1) {
                    onPageChanged?.invoke(firstVisible)
                } else {
                    firstVisible = 0
                    println("firstPage.error:$offset, ori:$orientation, view:$viewSize")
                }
            }
            return firstVisible
        }

        /**
         * 处理点击手势的公共方法，避免重复代码
         * @return 是否发生了翻页操作
         */
        public fun handleTapGesture(
            offsetTap: Offset,
            viewSize: IntSize,
            currentOffset: Offset,
            orientation: Int,
            pageViewState: PageViewState,
            keepPx: Float,
            onOffsetChanged: (Offset) -> Unit
        ): Boolean {
            if (orientation == Vertical) {
                // 垂直方向：上下翻页
                val y = offsetTap.y
                val height = viewSize.height.toFloat()
                return when {
                    y < height / 4 -> {
                        // 点击上方区域，向上翻页
                        val newY = (currentOffset.y + viewSize.height - keepPx).coerceAtMost(0f)
                        onOffsetChanged(Offset(currentOffset.x, newY))
                        true
                    }

                    y > height * 3 / 4 -> {
                        // 点击下方区域，向下翻页
                        val maxY = (pageViewState.totalHeight - viewSize.height).coerceAtLeast(0f)
                        val newY = (currentOffset.y - viewSize.height + keepPx).coerceAtLeast(-maxY)
                        onOffsetChanged(Offset(currentOffset.x, newY))
                        true
                    }

                    else -> false // 点击中间区域，不是翻页
                }
            } else {
                // 水平方向：左右翻页
                val x = offsetTap.x
                val width = viewSize.width.toFloat()
                return when {
                    x < width / 4 -> {
                        // 点击左侧区域，向左翻页
                        val newX = (currentOffset.x + viewSize.width - keepPx).coerceAtMost(0f)
                        onOffsetChanged(Offset(newX, currentOffset.y))
                        true
                    }

                    x > width * 3 / 4 -> {
                        // 点击右侧区域，向右翻页
                        val maxX = (pageViewState.totalWidth - viewSize.width).coerceAtLeast(0f)
                        val newX = (currentOffset.x - viewSize.width + keepPx).coerceAtLeast(-maxX)
                        onOffsetChanged(Offset(newX, currentOffset.y))
                        true
                    }

                    else -> false // 点击中间区域，不是翻页
                }
            }
        }

        // 计算直线坐标的方法
        public fun calculateLinePoints(start: Offset, end: Offset): List<Offset> {
            val dx = abs(end.x - start.x)
            val dy = abs(end.y - start.y)

            return if (dx > dy) {
                // 水平线：Y坐标与起点保持一致
                listOf(start, Offset(end.x, start.y))
            } else {
                // 垂直线：X坐标与起点保持一致
                listOf(start, Offset(start.x, end.y))
            }
        }
    }
}