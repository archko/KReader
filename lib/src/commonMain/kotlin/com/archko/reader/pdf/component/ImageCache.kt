package com.archko.reader.pdf.component

import androidx.compose.ui.graphics.ImageBitmap

private const val MAX_IMAGE_COUNT = 16

/**
 * @author: archko 2025/2/25 :16:59
 */
// 定义 ImageCache 类
public object ImageCache {
    private val cache = mutableMapOf<String, ImageBitmap>()

    public fun get(key: String): ImageBitmap? {
        return cache[key]
    }

    public fun put(key: String, painter: ImageBitmap) {
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