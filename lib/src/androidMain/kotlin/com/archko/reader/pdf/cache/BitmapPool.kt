package com.archko.reader.pdf.cache

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import androidx.core.util.Pools

/**
 * Created by archko on 16/12/24.
 */
public object BitmapPool {
    private val simplePool: FixedSimplePool<Bitmap>?

    init {
        simplePool = FixedSimplePool<Bitmap>(18)
    }

    public fun acquire(width: Int, height: Int): Bitmap {
        var bitmap = simplePool!!.acquire()
        if (null != bitmap && bitmap.isRecycled()) {
            bitmap = simplePool.acquire()
        }
        if (null == bitmap) {
            bitmap = createBitmap(width, height)
        } else {
            if (bitmap.getHeight() == height && bitmap.getWidth() == width) {
                //Log.d("TAG", String.format("use cache:%s-%s-%s%n", width, height, simplePool.mPoolSize));
                bitmap.eraseColor(0)
            } else {
                bitmap = createBitmap(width, height)
            }
        }
        return bitmap
    }

    public fun acquire(width: Int, height: Int, config: Bitmap.Config): Bitmap {
        var bitmap = simplePool!!.acquire()
        if (null != bitmap && bitmap.isRecycled()) {
            bitmap = simplePool.acquire()
        }
        if (null == bitmap) {
            bitmap = createBitmap(width, height, config)
        } else {
            if (bitmap.getConfig() == config) {
                if (bitmap.getHeight() == height && bitmap.getWidth() == width) {
                    //Log.d("TAG", String.format("use cache:%s-%s-%s%n", width, height, simplePool.mPoolSize));
                    bitmap.eraseColor(0)
                } else {
                    bitmap = createBitmap(width, height, config)
                }
            } else {
                bitmap = createBitmap(width, height, config)
            }
        }
        return bitmap
    }

    public fun release(bitmap: Bitmap?) {
        if (null == bitmap || bitmap.isRecycled()) {
            return
        }
        val isRelease = simplePool!!.release(bitmap)
        if (!isRelease) {
            println("recycle bitmap:" + bitmap)
            bitmap.recycle()
        }
    }

    @Synchronized
    public fun clear() {
        if (null != simplePool) {
            var bitmap: Bitmap?
            while ((simplePool.acquire().also { bitmap = it }) != null) {
                bitmap!!.recycle()
            }
        }
        //simplePool = null;
    }

    public class FixedSimplePool<T : Any> public constructor(maxPoolSize: Int) : Pools.Pool<T> {
        private val mPool: Array<Any?>

        private var mPoolSize = 0

        /**
         * Creates a new instance.
         *
         * @param maxPoolSize The max pool size.
         * @throws IllegalArgumentException If the max pool size is less than zero.
         */
        init {
            require(maxPoolSize > 0) { "The max pool size must be > 0" }
            mPool = arrayOfNulls<Any>(maxPoolSize)
        }

        override fun acquire(): T? {
            if (mPoolSize > 0) {
                val lastPooledIndex = mPoolSize - 1
                val instance = mPool[lastPooledIndex] as T
                mPool[lastPooledIndex] = null
                mPoolSize--
                return instance
            }
            return null
        }

        override fun release(instance: T): Boolean {
            if (isInPool(instance)) {
                return true
            }
            if (mPoolSize < mPool.size) {
                mPool[mPoolSize] = instance
                mPoolSize++
                return true
            }
            return false
        }

        private fun isInPool(instance: T): Boolean {
            for (i in 0..<mPoolSize) {
                if (mPool[i] === instance) {
                    return true
                }
            }
            return false
        }
    }
}
