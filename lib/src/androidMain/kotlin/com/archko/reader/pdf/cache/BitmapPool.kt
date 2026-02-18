package com.archko.reader.pdf.cache

import android.graphics.Bitmap
import androidx.core.util.Pools

/**
 * Created by archko on 16/12/24.
 */
public object BitmapPool {
    private val simplePool = FixedSimplePool<Bitmap>(18)

    public fun acquire(
        width: Int,
        height: Int,
        config: Bitmap.Config = Bitmap.Config.ARGB_8888
    ): Bitmap {
        var bitmap = simplePool.acquire()

        // 如果拿到的已经被回收，重试一次
        if (bitmap?.isRecycled == true) {
            bitmap = simplePool.acquire()
        }

        return if (bitmap != null &&
            bitmap.width == width &&
            bitmap.height == height &&
            bitmap.config == config
        ) {
            bitmap.eraseColor(0)
            bitmap
        } else {
            // 如果不匹配或为空，不放入池子，直接创建新的
            Bitmap.createBitmap(width, height, config)
        }
    }

    public fun release(bitmap: Bitmap?) {
        if (bitmap == null || bitmap.isRecycled) return

        val isReleased = simplePool.release(bitmap)
        if (!isReleased) {
            bitmap.recycle()
        }
    }

    @Synchronized
    public fun clear() {
        var bitmap: Bitmap?
        while ((simplePool.acquire().also { bitmap = it }) != null) {
            bitmap!!.recycle()
        }
    }

    // 增加同步机制
    private class FixedSimplePool<T : Any>(maxPoolSize: Int) : Pools.Pool<T> {
        private val mPool: Array<Any?> = arrayOfNulls(maxPoolSize)
        private var mPoolSize = 0
        private val lock = Any() // 专用的锁对象

        override fun acquire(): T? = synchronized(lock) {
            if (mPoolSize > 0) {
                val lastPooledIndex = mPoolSize - 1

                @Suppress("UNCHECKED_CAST")
                val instance = mPool[lastPooledIndex] as T?
                mPool[lastPooledIndex] = null
                mPoolSize--
                return instance
            }
            return null
        }

        override fun release(instance: T): Boolean = synchronized(lock) {
            if (isInPool(instance)) return true
            if (mPoolSize < mPool.size) {
                mPool[mPoolSize] = instance
                mPoolSize++
                return true
            }
            return false
        }

        private fun isInPool(instance: T): Boolean {
            for (i in 0 until mPoolSize) {
                if (mPool[i] === instance) return true
            }
            return false
        }
    }
}