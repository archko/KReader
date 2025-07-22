package com.archko.reader.pdf.component

import androidx.compose.ui.graphics.ImageBitmap

private const val MAX_IMAGE_COUNT = 48 // 可根据平台内存调整

/**
 * 通用Kotlin LRU缓存实现，支持Compose多平台。
 * 缓存满时，自动清除最久未用的1/3图片。
 */
public object ImageCache {
    private val cache = object : LinkedHashMap<String, ImageBitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean {
            // 不在这里自动移除，手动控制
            return false
        }
    }

    @Synchronized
    public fun get(key: String): ImageBitmap? {
        return cache[key]
    }

    @Synchronized
    public fun put(key: String, painter: ImageBitmap) {
        cache[key] = painter
        if (cache.size > MAX_IMAGE_COUNT) {
            // 超过容量，清除最久未用的1/3
            val removeCount = (MAX_IMAGE_COUNT / 3).coerceAtLeast(1)
            val iterator = cache.entries.iterator()
            repeat(removeCount) {
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
        }
    }

    @Synchronized
    public fun remove(key: String) {
        cache.remove(key)
    }

    @Synchronized
    public fun clear() {
        cache.clear()
    }
}