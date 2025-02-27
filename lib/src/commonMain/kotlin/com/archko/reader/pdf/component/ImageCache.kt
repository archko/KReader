package com.archko.reader.pdf.component

import androidx.compose.ui.graphics.painter.Painter

private const val MAX_IMAGE_COUNT = 3

/**
 * @author: archko 2025/2/25 :16:59
 */
// 定义 ImageCache 类
public object ImageCache {
    private val cache = mutableMapOf<String, Painter>()

    public fun get(key: String): Painter? {
        return cache[key]
    }

    public fun put(key: String, painter: Painter) {
        println("put:${cache.size}, key->$key")
        if (cache.size > MAX_IMAGE_COUNT) {
            cache.clear()
        }
        cache[key] = painter
    }

    public fun remove(key: String) {
        cache.remove(key)
    }

    public fun clear() {
        cache.clear()
    }
}