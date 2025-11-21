package com.archko.reader.pdf.cache

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val CANDIDATE_TIMEOUT = 5000L
private var MAX_MEMORY_BYTES = 128 * 1024 * 1024L
private var MAX_CANDIDATE_MEMORY_BYTES = MAX_MEMORY_BYTES / 3

private var PAGE_CACHE_MEMORY_BYTES = 24 * 1024 * 1024L
private var PAGE_CANDIDATE_MEMORY_BYTES = PAGE_CACHE_MEMORY_BYTES / 4

/**
 * Bitmap状态管理器，解决并发访问和生命周期问题
 * 使用引用计数确保正在使用的bitmap不会被回收
 */
public class BitmapState(
    public val bitmap: ImageBitmap,
    public val key: String
) {
    private val mutex = Mutex()
    private var referenceCount = 0
    private var isRecycled = false

    /**
     * 获取bitmap的使用权，增加引用计数
     */
    public fun acquire(): Boolean {
        return runBlocking {
            mutex.withLock {
                if (isRecycled) {
                    return@withLock false
                }
                referenceCount++
                return@withLock true
            }
        }
    }

    /**
     * 释放bitmap的使用权，减少引用计数
     */
    public fun release() {
        runBlocking {
            mutex.withLock {
                if (referenceCount > 0) {
                    referenceCount--
                }
            }
        }
    }

    /**
     * 标记bitmap为已回收状态
     */
    public fun markRecycled(): Boolean {
        return runBlocking {
            mutex.withLock {
                if (referenceCount == 0 && !isRecycled) {
                    isRecycled = true
                    return@withLock true
                }
                return@withLock false
            }
        }
    }

    /**
     * 检查是否可以安全回收
     */
    public fun canRecycle(): Boolean {
        return runBlocking {
            mutex.withLock {
                return@withLock referenceCount == 0
            }
        }
    }

    /**
     * 检查是否已被回收
     */
    public fun isRecycled(): Boolean {
        return runBlocking {
            mutex.withLock {
                return@withLock isRecycled
            }
        }
    }
}

/**
 * 线程安全的图片缓存，使用引用计数防止正在使用的bitmap被回收
 */
private class InnerImageCache(
    private val maxMemoryBytes: Long,
    private val maxCandidateMemoryBytes: Long = maxMemoryBytes / 3
) {
    private val mutex = Mutex()
    private val cache = mutableMapOf<String, BitmapState>()
    private val candidatePool = mutableMapOf<String, Pair<BitmapState, Long>>()

    private var currentMemoryBytes = 0L
    private var candidateMemoryBytes = 0L

    /**
     * 设置最大内存限制
     */
    public fun setMaxMemory(maxMemoryBytes: Long) {
        MAX_MEMORY_BYTES = maxMemoryBytes
        MAX_CANDIDATE_MEMORY_BYTES = MAX_MEMORY_BYTES / 3
    }

    /**
     * 获取bitmap，如果成功会自动增加引用计数
     */
    public fun acquire(key: String): BitmapState? {
        return runBlocking {
            mutex.withLock {
                // 先检查主缓存
                cache[key]?.let { state ->
                    if (state.acquire()) {
                        return@withLock state
                    }
                }

                // 检查候选池
                candidatePool[key]?.let { (state, timestamp) ->
                    if (System.currentTimeMillis() - timestamp < CANDIDATE_TIMEOUT) {
                        if (state.acquire()) {
                            // 从候选池移回主缓存
                            val imageSize = calculateImageSize(state.bitmap)
                            cache[key] = state
                            candidatePool.remove(key)
                            currentMemoryBytes += imageSize
                            candidateMemoryBytes -= imageSize
                            cleanCandidatePool()
                            return@withLock state
                        }
                    } else {
                        // 超时，尝试回收
                        if (state.markRecycled()) {
                            val imageSize = calculateImageSize(state.bitmap)
                            recycleImageBitmap(state.bitmap)
                            candidateMemoryBytes -= imageSize
                        }
                        candidatePool.remove(key)
                    }
                }

                cleanCandidatePool()
                return@withLock null
            }
        }
    }

    /**
     * 释放bitmap的使用权
     */
    public fun release(state: BitmapState) {
        runBlocking {
            state.release()
        }
    }

    /**
     * 添加新的bitmap到缓存
     */
    public fun put(key: String, bitmap: ImageBitmap): BitmapState {
        return runBlocking {
            mutex.withLock {
                val imageSize = calculateImageSize(bitmap)

                // 如果已存在，先处理旧的
                cache[key]?.let { oldState ->
                    val oldSize = calculateImageSize(oldState.bitmap)
                    addToCandidatePool(key, oldState)
                    currentMemoryBytes -= oldSize
                }

                val state = BitmapState(bitmap, key)
                cache[key] = state
                currentMemoryBytes += imageSize

                // 检查内存限制，优先移除没有引用的bitmap
                while (currentMemoryBytes > maxMemoryBytes && cache.isNotEmpty()) {
                    // 优先选择没有引用的bitmap进行移除
                    val entryToRemove = cache.entries.find { it.value.canRecycle() }
                        ?: cache.entries.first() // 如果都有引用，选择第一个

                    val entry = entryToRemove
                    val entrySize = calculateImageSize(entry.value.bitmap)

                    // 只有在没有引用时才移到候选池，否则保留在主缓存
                    if (entry.value.canRecycle()) {
                        addToCandidatePool(entry.key, entry.value)
                        cache.remove(entry.key)
                        currentMemoryBytes -= entrySize
                    } else {
                        // 如果所有bitmap都有引用，暂时不清理，避免崩溃
                        break
                    }
                }

                cleanCandidatePool()
                return@withLock state
            }
        }
    }

    /**
     * 移除指定key的bitmap
     */
    public fun remove(key: String) {
        runBlocking {
            mutex.withLock {
                cache.remove(key)?.let { state ->
                    val imageSize = calculateImageSize(state.bitmap)
                    currentMemoryBytes -= imageSize
                    addToCandidatePool(key, state)
                }
                cleanCandidatePool()
            }
        }
    }

    /**
     * 清空所有缓存
     */
    public fun clear() {
        runBlocking {
            mutex.withLock {
                // 强制回收所有缓存的bitmap
                cache.values.forEach { state ->
                    if (state.markRecycled()) {
                        recycleImageBitmap(state.bitmap)
                    }
                }
                cache.clear()
                currentMemoryBytes = 0L
                
                // 强制回收候选池中的bitmap
                candidatePool.values.forEach { (state, _) ->
                    if (state.markRecycled()) {
                        recycleImageBitmap(state.bitmap)
                    }
                }
                candidatePool.clear()
                candidateMemoryBytes = 0L
            }
        }
    }

    private fun addToCandidatePool(key: String, state: BitmapState) {
        val imageSize = calculateImageSize(state.bitmap)

        // 确保候选池不超限
        while (candidateMemoryBytes + imageSize > maxCandidateMemoryBytes && candidatePool.isNotEmpty()) {
            val oldest = candidatePool.entries.minByOrNull { it.value.second }
            if (oldest != null) {
                val oldState = oldest.value.first
                if (oldState.markRecycled()) {
                    val oldSize = calculateImageSize(oldState.bitmap)
                    recycleImageBitmap(oldState.bitmap)
                    candidateMemoryBytes -= oldSize
                }
                candidatePool.remove(oldest.key)
            }
        }

        candidatePool[key] = Pair(state, System.currentTimeMillis())
        candidateMemoryBytes += imageSize
    }

    private fun cleanCandidatePool(force: Boolean = false) {
        val now = System.currentTimeMillis()
        val iterator = candidatePool.iterator()
        while (iterator.hasNext()) {
            val (key, pair) = iterator.next()
            val (state, timestamp) = pair
            if (force || now - timestamp > CANDIDATE_TIMEOUT) {
                // 确保bitmap没有被回收且没有引用才进行回收
                if (!state.isRecycled() && state.canRecycle() && state.markRecycled()) {
                    val imageSize = calculateImageSize(state.bitmap)
                    recycleImageBitmap(state.bitmap)
                    candidateMemoryBytes -= imageSize
                    iterator.remove()
                } else if (state.isRecycled()) {
                    // 如果已经被标记为回收，直接从候选池移除
                    val imageSize = calculateImageSize(state.bitmap)
                    candidateMemoryBytes -= imageSize
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
        MAX_MEMORY_BYTES = maxMemoryBytes
        MAX_CANDIDATE_MEMORY_BYTES = MAX_MEMORY_BYTES / 3
        nodeCache.setMaxMemory(maxMemoryBytes)
    }

    /**
     * Node 高清图缓存操作
     */
    public fun acquireNode(key: String): BitmapState? = nodeCache.acquire(key)
    public fun releaseNode(state: BitmapState): Unit = nodeCache.release(state)
    public fun putNode(key: String, bitmap: ImageBitmap): BitmapState = nodeCache.put(key, bitmap)
    public fun removeNode(key: String): Unit = nodeCache.remove(key)
    public fun clearNodes(): Unit = nodeCache.clear()

    /**
     * Page 缩略图缓存操作
     */
    public fun acquirePage(key: String): BitmapState? = pageCache.acquire(key)
    public fun releasePage(state: BitmapState): Unit = pageCache.release(state)
    public fun putPage(key: String, bitmap: ImageBitmap): BitmapState = pageCache.put(key, bitmap)
    public fun removePage(key: String): Unit = pageCache.remove(key)
    public fun clearPages(): Unit = pageCache.clear()

    /**
     * 清空所有缓存
     */
    public fun clear() {
        nodeCache.clear()
        pageCache.clear()
    }
}
