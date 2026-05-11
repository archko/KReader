package com.archko.reader.pdf.cache

import androidx.compose.ui.graphics.ImageBitmap
import java.util.concurrent.atomic.AtomicInteger

private const val CANDIDATE_TIMEOUT = 60_000L
private var MAX_MEMORY_BYTES = 256 * 1024 * 1024L
private var MAX_CANDIDATE_MEMORY_BYTES = MAX_MEMORY_BYTES / 4

private var PAGE_CACHE_MEMORY_BYTES = 32 * 1024 * 1024L
private var PAGE_CANDIDATE_MEMORY_BYTES = PAGE_CACHE_MEMORY_BYTES / 4

/**
 * Bitmap状态管理器，解决并发访问和生命周期问题
 * 使用引用计数确保正在使用的bitmap不会被回收
 *
 * 用一个 AtomicInteger 合并编码 refCount（低31位）和 recycled 标志（第32位），
 * 通过 CAS 循环实现所有方法完全无锁。
 */
public class BitmapState(
    public val bitmap: ImageBitmap,
    public val key: String,
    public val byteSize: Long // 缓存尺寸，避免重复计算
) {
    private val state = AtomicInteger(0)

    public fun acquire(): Boolean {
        while (true) {
            val current = state.get()
            if (current < 0) return false
            if (state.compareAndSet(current, current + 1)) return true
        }
    }

    public fun release(): Unit {
        while (true) {
            val current = state.get()
            val refCount = current and Int.MAX_VALUE
            if (refCount == 0) break
            if (state.compareAndSet(current, current - 1)) break
        }
    }

    public fun markRecycled(): Boolean {
        while (true) {
            val current = state.get()
            if (current < 0) return false
            val refCount = current and Int.MAX_VALUE
            if (refCount != 0) return false
            if (state.compareAndSet(current, current or Int.MIN_VALUE)) return true
        }
    }

    public fun canRecycle(): Boolean = (state.get() and Int.MAX_VALUE) == 0
    public fun isRecycled(): Boolean = state.get() < 0
}

/**
 * 线程安全的图片缓存，使用引用计数防止正在使用的bitmap被回收
 */
private class InnerImageCache(
    private var maxMemoryBytes: Long,
    private var maxCandidateMemoryBytes: Long = maxMemoryBytes / 4
) {
    // 使用 LinkedHashMap (accessOrder=true) 实现真正的 LRU
    private val cache = LinkedHashMap<String, BitmapState>(0, 0.75f, true)

    // 候选池：暂时不用的 Bitmap 停留区
    private val candidatePool = mutableMapOf<String, Pair<BitmapState, Long>>()

    private var currentMemoryBytes = 0L
    private var candidateMemoryBytes = 0L

    /**
     * 设置最大内存限制
     */
    public fun setMaxMemory(maxMemoryBytes: Long) {
        synchronized(this) {
            this.maxMemoryBytes = maxMemoryBytes
            this.maxCandidateMemoryBytes = maxMemoryBytes / 3
            trimToSize()
        }
    }

    public fun acquire(key: String): BitmapState? = synchronized(this) {
        // 1. 检查主缓存 (LinkedHashMap 会自动更新访问顺序)
        cache[key]?.let { state ->
            if (state.acquire()) return state
        }

        // 2. 检查候选池
        candidatePool[key]?.let { (state, timestamp) ->
            if (System.currentTimeMillis() - timestamp < CANDIDATE_TIMEOUT) {
                if (state.acquire()) {
                    candidatePool.remove(key)
                    candidateMemoryBytes -= state.byteSize

                    cache[key] = state
                    currentMemoryBytes += state.byteSize
                    return state
                }
            } else {
                // 超时自动清理
                internalRecycle(key, state, fromCandidate = true)
            }
        }
        return null
    }

    public fun release(state: BitmapState) {
        state.release()
    }

    public fun put(key: String, bitmap: ImageBitmap): BitmapState {
        val imageSize = calculateImageSize(bitmap)
        val state = BitmapState(bitmap, key, imageSize)

        synchronized(this) {
            val oldState = cache.put(key, state)
            if (oldState != null) {
                currentMemoryBytes -= oldState.byteSize
                addToCandidatePool(key, oldState)
            }
            currentMemoryBytes += imageSize
        }
        // ← 锁在这里释放了，主线程的 acquireNode() 不会被阻塞

        synchronized(this) { trimToSize() } // 第二次拿锁做 eviction
        return state
    }

    private fun indexLast(key: String) = key // 辅助方法

    /**
     * 核心逻辑：根据 LRU 顺序和引用计数清理内存
     */
    private fun trimToSize() {
        if (currentMemoryBytes <= maxMemoryBytes) return

        val iterator = cache.entries.iterator()
        while (iterator.hasNext() && currentMemoryBytes > maxMemoryBytes) {
            val entry = iterator.next()
            // 只有没有引用的才能被踢出主缓存
            if (entry.value.canRecycle()) {
                val state = entry.value
                iterator.remove()
                currentMemoryBytes -= state.byteSize
                addToCandidatePool(entry.key, state)
            }
        }
    }

    private fun addToCandidatePool(key: String, state: BitmapState) {
        // 如果候选池超限，先清理最老的
        while (candidateMemoryBytes + state.byteSize > maxCandidateMemoryBytes && candidatePool.isNotEmpty()) {
            val oldestKey = candidatePool.entries.minByOrNull { it.value.second }?.key
            oldestKey?.let { k ->
                candidatePool.remove(k)?.let { (s, _) ->
                    internalRecycle(k, s, fromCandidate = true)
                }
            }
        }

        candidatePool[key] = Pair(state, System.currentTimeMillis())
        candidateMemoryBytes += state.byteSize
    }

    private fun internalRecycle(key: String, state: BitmapState, fromCandidate: Boolean) {
        if (state.markRecycled()) {
            if (fromCandidate) candidateMemoryBytes -= state.byteSize
            else currentMemoryBytes -= state.byteSize

            recycleImageBitmap(state.bitmap)
        }
    }

    public fun remove(key: String) = synchronized(this) {
        cache.remove(key)?.let { state ->
            currentMemoryBytes -= state.byteSize
            addToCandidatePool(key, state)
        }
        cleanCandidatePool()
    }

    public fun hasNode(key: String): Boolean = synchronized(this) {
        return cache.containsKey(key) || candidatePool.containsKey(key)
    }

    public fun clear() = synchronized(this) {
        cache.values.forEach { if (it.markRecycled()) recycleImageBitmap(it.bitmap) }
        cache.clear()
        currentMemoryBytes = 0L

        candidatePool.values.forEach { (state, _) ->
            if (state.markRecycled()) recycleImageBitmap(
                state.bitmap
            )
        }
        candidatePool.clear()
        candidateMemoryBytes = 0L
    }

    public fun size(): Int = synchronized(this) { cache.size }

    private fun cleanCandidatePool() {
        val now = System.currentTimeMillis()
        val iterator = candidatePool.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val (state, timestamp) = entry.value
            if (now - timestamp > CANDIDATE_TIMEOUT) {
                if (state.markRecycled()) {
                    recycleImageBitmap(state.bitmap)
                    candidateMemoryBytes -= state.byteSize
                    iterator.remove()
                }
            }
        }
    }

    private fun calculateImageSize(image: ImageBitmap): Long {
        return ImageSizeCalculator.calculateImageSize(image)
    }

    private fun recycleImageBitmap(imageBitmap: ImageBitmap) {
        ImageSizeCalculator.recycleImageBitmap(imageBitmap)
    }
}

/**
 * 平台特定的ImageBitmap处理器
 */
internal expect object ImageSizeCalculator {
    /**
     * 计算ImageBitmap的内存占用大小（字节）
     */
    fun calculateImageSize(image: ImageBitmap): Long

    /**
     * 回收ImageBitmap，释放底层资源
     */
    fun recycleImageBitmap(image: ImageBitmap)
}

/**
 * 全局缓存实例
 */
public object ImageCache {
    // Node 高清图缓存（动态大小）
    private var nodeCache = InnerImageCache(MAX_MEMORY_BYTES, MAX_CANDIDATE_MEMORY_BYTES)

    // Page 缩略图缓存（固定 32MB）
    private val pageCache = InnerImageCache(PAGE_CACHE_MEMORY_BYTES, PAGE_CANDIDATE_MEMORY_BYTES)

    /**
     * 设置最大内存限制（只影响 Node 缓存）
     */
    public fun setMaxMemory(maxMemoryBytes: Long) {
        nodeCache.setMaxMemory(maxMemoryBytes)
        pageCache.setMaxMemory(maxMemoryBytes / 4)
    }

    /**
     * Node 高清图缓存操作
     */
    public fun acquireNode(key: String): BitmapState? = nodeCache.acquire(key)
    public fun releaseNode(state: BitmapState): Unit = nodeCache.release(state)
    public fun putNode(key: String, bitmap: ImageBitmap): BitmapState = nodeCache.put(key, bitmap)
    public fun removeNode(key: String): Unit = nodeCache.remove(key)
    public fun hasNode(key: String): Boolean = nodeCache.hasNode(key)
    public fun clearNodes(): Unit = nodeCache.clear()

    /**
     * Page 缩略图缓存操作
     */
    public fun acquirePage(key: String): BitmapState? = pageCache.acquire(key)
    public fun releasePage(state: BitmapState): Unit = pageCache.release(state)
    public fun putPage(key: String, bitmap: ImageBitmap): BitmapState = pageCache.put(key, bitmap)
    public fun removePage(key: String): Unit = pageCache.remove(key)
    public fun hasPage(key: String): Boolean = pageCache.hasNode(key)
    public fun clearPages(): Unit = pageCache.clear()
    public fun pageCount(): Int = pageCache.size()

    /**
     * 清空所有缓存
     */
    public fun clear() {
        nodeCache.clear()
        pageCache.clear()
    }
}
