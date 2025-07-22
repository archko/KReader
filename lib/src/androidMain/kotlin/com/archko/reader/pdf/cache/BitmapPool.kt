package com.archko.reader.pdf.cache

import android.graphics.Bitmap
import androidx.core.util.Pools

/**
 * Created by archko on 16/12/24.
 */
public object BitmapPool {
    private val simplePool: FixedSimplePool<Bitmap> = FixedSimplePool(18)

    public fun acquire(width: Int, height: Int): Bitmap {
        var bitmap = simplePool.acquire()
        if (null != bitmap && bitmap.isRecycled()) {
            bitmap = simplePool.acquire()
        }
        if (null == bitmap) {
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } else {
            if (bitmap.getHeight() == height && bitmap.getWidth() == width) {
                //Log.d("TAG", String.format("use cache:%s-%s-%s%n", width, height, simplePool.mPoolSize));
                bitmap.eraseColor(0)
            } else {
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            }
        }
        return bitmap
    }

    public fun acquire(width: Int, height: Int, config: Bitmap.Config): Bitmap {
        var bitmap = simplePool.acquire()
        if (null != bitmap && bitmap.isRecycled()) {
            bitmap = simplePool.acquire()
        }
        if (null == bitmap) {
            bitmap = Bitmap.createBitmap(width, height, config)
        } else {
            if (bitmap.getConfig() == config) {
                if (bitmap.getHeight() == height && bitmap.getWidth() == width) {
                    //Log.d("TAG", String.format("use cache:%s-%s-%s%n", width, height, simplePool.mPoolSize));
                    bitmap.eraseColor(0)
                } else {
                    bitmap = Bitmap.createBitmap(width, height, config)
                }
            } else {
                bitmap = Bitmap.createBitmap(width, height, config)
            }
        }
        return bitmap
    }

    public fun release(bitmap: Bitmap?) {
        if (null == bitmap || bitmap.isRecycled()) {
            return
        }
        val isRelease = simplePool.release(bitmap)
        if (!isRelease) {
            println("recycle bitmap:" + bitmap)
            bitmap.recycle()
        }
    }

    @Synchronized
    public fun clear() {
        var bitmap: Bitmap?
        while ((simplePool.acquire().also { bitmap = it }) != null) {
            bitmap!!.recycle()
        }
        //simplePool = null;
    }

    public class FixedSimplePool<T : Any> public constructor(maxPoolSize: Int) : Pools.Pool<T> {
        private val mPool: Array<Any?>

        private var mPoolSize = 0

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
//package com.archko.reader.pdf.cache
//
//import android.graphics.Bitmap
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.ExperimentalCoroutinesApi
//import kotlinx.coroutines.cancel
//import kotlinx.coroutines.channels.Channel
//import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
//import kotlinx.coroutines.coroutineScope
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.sync.Mutex
//import kotlinx.coroutines.sync.withLock
//import kotlin.coroutines.CoroutineContext
//
///**
// * A pool of bitmaps, internally split by allocation byte count.
// * This class is thread-safe.
// */
//internal class BitmapPool(coroutineContext: CoroutineContext) {
//    private val mutex = Mutex()
//    private val pool = mutableMapOf<Int, Channel<Bitmap>>()
//    private val receiveChannel = Channel<Bitmap>(capacity = UNLIMITED)
//    private val scope = CoroutineScope(coroutineContext)
//
//    init {
//        scope.launch {
//            for (b in receiveChannel) {
//                mutex.withLock {
//                    val allocationByteCount = b.allocationByteCount
//
//                    if (!pool.containsKey(allocationByteCount)) {
//                        pool[allocationByteCount] = Channel(UNLIMITED)
//                    }
//
//                    pool[allocationByteCount]?.trySend(b)
//                }
//            }
//        }
//    }
//
//    @OptIn(ExperimentalCoroutinesApi::class)
//    suspend fun get(allocationByteCount: Int): Bitmap? {
//        mutex.withLock {
//            if (pool[allocationByteCount]?.isEmpty == true) {
//                return null
//            }
//            return pool[allocationByteCount]?.tryReceive()?.getOrNull()
//        }
//    }
//
//    /**
//     * Don't make this method a suspending call. It causes ConcurrentModificationExceptions because
//     * some collection iteration become interleaved.
//     */
//    fun put(b: Bitmap) {
//        receiveChannel.trySend(b)
//    }
//
//    @OptIn(ExperimentalCoroutinesApi::class)
//    fun clear() = scope.launch {
//        mutex.withLock {
//            coroutineScope {
//                pool.forEach { (k, v) ->
//                    val channel= pool[k]
//                    if (channel != null) {
//                        launch {
//                            for (b in channel) {
//                                b.recycle()
//                                if (channel.isEmpty) {
//                                    cancel()
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//            scope.cancel()
//        }
//    }
//}