package com.archko.reader.pdf.cache

import androidx.compose.ui.graphics.painter.Painter

/**
 * @author: archko 2025/4/14 :09:05
 */
public class ImageBitmapCache private constructor() {

    private object ImageFactory {
        val instance = ImageBitmapCache()
    }

    private val pageCache: InnerCache

    public fun getBitmap(key: String): Painter? {
        return pageCache.getBitmap(key)
    }

    public fun addBitmap(key: String, value: Painter): Painter? {
        return pageCache.addBitmap(key, value)
    }

    public fun remove(key: String): Painter? {
        return pageCache.remove(key)
    }

    public fun clear() {
        pageCache.clear()
    }

    init {
        pageCache = InnerCache(MAX_PAGE_SIZE)
    }

    private inner class InnerCache(maxByte: Int) {
        private var maxByte = MAX_PAGE_SIZE

        private val bitmapLinkedMap: LinkedHashMap<String, Painter> =
            LinkedHashMap<String, Painter>(16, 0.75f, true)

        private var putCount = 0
        private val createCount = 0
        private var evictionCount = 0
        private var hitCount = 0
        private var missCount = 0

        init {
            this.maxByte = maxByte
        }

        fun getBitmap(key: String?): Painter? {
            if (key == null) {
                throw NullPointerException("key == null")
            }

            val mapValue: Painter?
            synchronized(this) {
                mapValue = bitmapLinkedMap.get(key)
                if (mapValue != null) {
                    hitCount++
                    bitmapLinkedMap.remove(key)
                    return mapValue
                }
                missCount++
            }

            return null
        }

        fun addBitmap(key: String?, value: Painter?): Painter? {
            if (key == null || value == null) {
                throw NullPointerException("key == null || value == null")
            }
            while (putCount > MAX_PAGE_SIZE) {
                removeLast()
            }

            val previous: Painter?
            synchronized(this) {
                putCount++
                previous = bitmapLinkedMap.put(key, value)
            }

            if (previous != null) {
                entryRemoved(false, key, previous, value)
            }

            return previous
        }

        fun removeLast() {
            val key: String
            val value: Painter
            synchronized(this) {
                if (bitmapLinkedMap.isEmpty()) {
                    return
                }
                val toEvict: MutableMap.MutableEntry<String, Painter> =
                    bitmapLinkedMap.entries.iterator().next()
                key = toEvict.key
                value = toEvict.value
                bitmapLinkedMap.remove(key)
                putCount--
                evictionCount++
            }

            //System.out.println(String.format("removeLast.size:%s, key:%s,val:%s, size:%s", map.size(), key, value, mPoolSizeInBytes));
            entryRemoved(true, key, value, null)
        }

        fun remove(key: String?): Painter? {
            if (key == null) {
                throw NullPointerException("key == null")
            }

            val previous: Painter?
            synchronized(this) {
                previous = bitmapLinkedMap.remove(key)
                putCount--
            }

            if (previous != null) {
                entryRemoved(false, key, previous, null)
            }

            return previous
        }

        protected fun entryRemoved(
            evicted: Boolean, key: String, oldValue: Painter,
            newValue: Painter?
        ) {
        }

        protected fun create(key: String): Painter? {
            return null
        }

        fun clear() {
            val size = bitmapLinkedMap.size
            for (i in 0..<size) {
                removeLast()
            }
        }

        @Synchronized
        fun hitCount(): Int {
            return hitCount
        }

        @Synchronized
        fun missCount(): Int {
            return missCount
        }

        @Synchronized
        fun createCount(): Int {
            return createCount
        }

        @Synchronized
        fun putCount(): Int {
            return putCount
        }

        @Synchronized
        fun evictionCount(): Int {
            return evictionCount
        }

        @Synchronized
        fun snapshot(): MutableMap<String?, Painter?> {
            return LinkedHashMap<String?, Painter?>(bitmapLinkedMap)
        }
    }

    public companion object {

        public fun getInstance(): ImageBitmapCache {
            return ImageFactory.instance
        }

        private var MAX_PAGE_SIZE = 16
    }
}
