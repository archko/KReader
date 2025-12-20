package com.archko.reader.pdf.component

import androidx.compose.ui.geometry.Rect
import com.archko.reader.pdf.entity.APage

/**
 * PageNode 对象池，用于减少高倍缩放时频繁创建几万个对象的开销
 */
public class PageNodePool {
    private val pool = ArrayDeque<PageNode>(200) // 预留初始容量

    // 获取一个 Node，如果没有则新建
    public fun acquire(pageViewState: PageViewState, bounds: Rect, aPage: APage): PageNode {
        val node = pool.removeLastOrNull()
        if (node != null) {
            node.update(bounds, aPage) // 更新数据而不是新建
            return node
        }
        return PageNode(pageViewState, bounds, aPage)
    }

    // 回收 Node
    public fun release(node: PageNode) {
        node.recycle() // 释放位图和 Job
        if (pool.size < 1000) { // 限制池子大小，防止极端内存占用
            pool.addLast(node)
        }
    }

    public fun clear() {
        pool.forEach { it.recycle() }
        pool.clear()
    }
}