package com.archko.reader.pdf.cache

import android.graphics.Bitmap

/**
 * Created by archko on 16/12/24.
 */

/**
 * 按尺寸分桶的 Bitmap 池，避免不同尺寸互相挤占导致命中率过低。
 */
public object BitmapPool {
    private const val MAX_POOL_SIZE = 18
    private const val MAX_PER_SIZE = 2

    private data class PoolKey(
        val width: Int,
        val height: Int,
        val config: Bitmap.Config
    )

    private val buckets = HashMap<PoolKey, ArrayDeque<Bitmap>>()
    private var pooledCount = 0
    private val lock = Any()

    public fun acquire(
        width: Int,
        height: Int,
        config: Bitmap.Config = Bitmap.Config.ARGB_8888
    ): Bitmap {
        synchronized(lock) {
            val key = PoolKey(width, height, config)
            val bucket = buckets[key]
            while (bucket != null && bucket.isNotEmpty()) {
                val bitmap = bucket.removeLast()
                pooledCount--

                if (!bitmap.isRecycled) {
                    bitmap.eraseColor(0)
                    return bitmap
                }
            }
            if (bucket != null && bucket.isEmpty()) {
                buckets.remove(key)
            }
        }

        return Bitmap.createBitmap(width, height, config)
    }

    public fun release(bitmap: Bitmap?) {
        if (bitmap == null || bitmap.isRecycled) return

        synchronized(lock) {
            val config = bitmap.config ?: Bitmap.Config.ARGB_8888
            val key = PoolKey(bitmap.width, bitmap.height, config)
            val bucket = buckets.getOrPut(key) { ArrayDeque() }

            if (pooledCount < MAX_POOL_SIZE && bucket.size < MAX_PER_SIZE) {
                bucket.addLast(bitmap)
                pooledCount++
            } else {
                bitmap.recycle()
            }
        }
    }

    public fun clear() {
        synchronized(lock) {
            buckets.values.forEach { bucket ->
                bucket.forEach { bitmap -> bitmap.recycle() }
            }
            buckets.clear()
            pooledCount = 0
        }
    }
}
