package com.archko.reader.pdf.cache

import androidx.compose.ui.graphics.ImageBitmap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

private const val CANDIDATE_TIMEOUT = 60_000L
private var MAX_MEMORY_BYTES = 256 * 1024 * 1024L
private var MAX_CANDIDATE_MEMORY_BYTES = MAX_MEMORY_BYTES / 4

private var PAGE_CACHE_MEMORY_BYTES = 48 * 1024 * 1024L
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
    public val byteSize: Long
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
 * 线程安全的图片缓存，使用 ConcurrentHashMap + 独立的近似 LRU 列表，
 * 将内存记账 eviction 与 查询/插入 分离，主线程读写完全无锁。
 */
private class InnerImageCache(
    private var maxMemoryBytes: Long,
    private var maxCandidateMemoryBytes: Long = maxMemoryBytes / 4
) {
    // 主缓存：ConcurrentHashMap 保证并发的 get/put/remove 无锁
    private val cache = ConcurrentHashMap<String, BitmapState>()

    // LRU 顺序链表：只用于 trimToSize() eviction 决策，不在主线程热路径上
    private val lruList = LinkedHashMap<String, BitmapState>(0, 0.75f, true)

    // 候选池：用 ConcurrentHashMap 避免与主线程锁争用
    private val candidatePool = ConcurrentHashMap<String, CandidateEntry>()

    private val currentMemoryBytes = AtomicLong(0)
    private val candidateMemoryBytes = AtomicLong(0)

    private val evictionLock = Any()

    // put()/acquire() 只把 LRU 插入与候选回收入队，真正的 trim/recycle 交给独立线程
    private val lruQueue = ConcurrentLinkedQueue<PendingEntry>()
    private val candidateQueue = ConcurrentLinkedQueue<PendingEntry>()
    private val evictionScheduled = AtomicBoolean(false)

    private val evictionExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ImageCache-eviction").apply { isDaemon = true }
    }

    private class PendingEntry(
        val key: String,
        val state: BitmapState
    )

    private class CandidateEntry(
        val state: BitmapState,
        val timestamp: Long = System.currentTimeMillis()
    )

    public fun setMaxMemory(maxMemoryBytes: Long) {
        synchronized(evictionLock) {
            this.maxMemoryBytes = maxMemoryBytes
            this.maxCandidateMemoryBytes = maxMemoryBytes / 3
        }
        requestEviction()
    }

    public fun acquire(key: String): BitmapState? {
        // 1. 检查主缓存（ConcurrentHashMap 无锁读）
        // 注意：命中后不刷新 lruList 的顺序。刷新需要 evictionLock，会让主线程查询
        // 重新回到锁竞争路径；所以淘汰顺序是“近似 LRU”（按插入/候选提升顺序），
        // 被反复命中的条目仍可能先于真正的冷条目被淘汰。当前内存预算下可接受。
        cache[key]?.let { state ->
            if (state.acquire()) {
                return state
            }
        }

        // 2. 检查候选池
        candidatePool.remove(key)?.let { entry ->
            val expired = System.currentTimeMillis() - entry.timestamp >= CANDIDATE_TIMEOUT
            // acquire 失败说明位图已被其他路径标记回收，下面统一走回收兜底，
            // 避免候选条目已从池中移除但引用/内存记账没有收尾。
            if (!expired && entry.state.acquire()) {
                candidateMemoryBytes.addAndGet(-entry.state.byteSize)
                cache[key] = entry.state
                currentMemoryBytes.addAndGet(entry.state.byteSize)
                enqueueLru(key, entry.state)
                return entry.state
            }
            if (entry.state.markRecycled()) {
                candidateMemoryBytes.addAndGet(-entry.state.byteSize)
                recycleImageBitmap(entry.state.bitmap)
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

        val oldState = cache.put(key, state)
        if (oldState != null) {
            currentMemoryBytes.addAndGet(-oldState.byteSize)
            candidateQueue.add(PendingEntry(key, oldState))
        }
        currentMemoryBytes.addAndGet(imageSize)

        enqueueLru(key, state)

        return state
    }

    private fun enqueueLru(key: String, state: BitmapState) {
        lruQueue.add(PendingEntry(key, state))
        requestEviction()
    }

    // 合并 eviction 请求：同一时刻最多只有一个排队中的 trim 任务
    private fun requestEviction() {
        if (!evictionScheduled.compareAndSet(false, true)) return
        evictionExecutor.execute {
            // 先复位标志，drain 期间新 put 的请求会再调度一次，不会丢
            evictionScheduled.set(false)
            synchronized(evictionLock) {
                drainQueuesLocked()
                trimToSize()
            }
        }
    }

    private fun drainQueuesLocked() {
        while (true) {
            val entry = lruQueue.poll() ?: break
            // cache 中仍是同一个 state 才写入 LRU；已被替换/移除的旧 state 直接丢弃
            if (cache[entry.key] === entry.state) {
                lruList[entry.key] = entry.state
            }
        }
        while (true) {
            val entry = candidateQueue.poll() ?: break
            addToCandidatePoolLocked(entry.key, entry.state)
        }
    }

    // 调用方必须持有 evictionLock
    private fun trimToSize() {
        if (currentMemoryBytes.get() <= maxMemoryBytes) return

        val iterator = lruList.entries.iterator()
        while (iterator.hasNext() && currentMemoryBytes.get() > maxMemoryBytes) {
            val entry = iterator.next()
            val current = cache[entry.key]
            if (current == null) {
                iterator.remove()
                continue
            }
            if (current.canRecycle()) {
                if (cache.remove(entry.key, current)) {
                    currentMemoryBytes.addAndGet(-current.byteSize)
                    addToCandidatePoolLocked(entry.key, current)
                }
                iterator.remove()
            }
        }
    }

    private fun addToCandidatePool(key: String, state: BitmapState) {
        synchronized(evictionLock) {
            addToCandidatePoolLocked(key, state)
        }
    }

    // 调用方必须持有 evictionLock
    private fun addToCandidatePoolLocked(key: String, state: BitmapState) {
        val entry = CandidateEntry(state)
        candidatePool[key] = entry
        candidateMemoryBytes.addAndGet(state.byteSize)

        // 候选池超限时按迭代顺序回收，避免在解码线程上做 O(n) 扫描
        while (candidateMemoryBytes.get() > maxCandidateMemoryBytes) {
            val iterator = candidatePool.entries.iterator()
            if (!iterator.hasNext()) break
            val entry = iterator.next()
            if (candidatePool.remove(entry.key, entry.value)) {
                if (entry.value.state.markRecycled()) {
                    candidateMemoryBytes.addAndGet(-entry.value.state.byteSize)
                    recycleImageBitmap(entry.value.state.bitmap)
                }
            }
        }
    }

    public fun remove(key: String) {
        cache.remove(key)?.let { state ->
            currentMemoryBytes.addAndGet(-state.byteSize)
            addToCandidatePool(key, state)
        }
        synchronized(evictionLock) {
            lruList.remove(key)
            cleanCandidatePool()
        }
    }

    public fun hasNode(key: String): Boolean {
        return cache.containsKey(key) || candidatePool.containsKey(key)
    }

    public fun clear() {
        synchronized(evictionLock) {
            cache.values.forEach { if (it.markRecycled()) recycleImageBitmap(it.bitmap) }
            cache.clear()
            currentMemoryBytes.set(0)

            val candValues = candidatePool.values.toList()
            candidatePool.clear()
            candidateMemoryBytes.set(0)
            candValues.forEach { entry ->
                if (entry.state.markRecycled()) recycleImageBitmap(entry.state.bitmap)
            }

            // 尚未被 eviction 线程消费的旧 state 也要回收
            while (true) {
                val pending = candidateQueue.poll() ?: break
                if (pending.state.markRecycled()) recycleImageBitmap(pending.state.bitmap)
            }

            lruList.clear()
        }
    }

    public fun size(): Int = cache.size

    private fun cleanCandidatePool() {
        val now = System.currentTimeMillis()
        val iterator = candidatePool.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val candidate = entry.value
            if (now - candidate.timestamp > CANDIDATE_TIMEOUT) {
                if (candidate.state.markRecycled()) {
                    recycleImageBitmap(candidate.state.bitmap)
                    candidateMemoryBytes.addAndGet(-candidate.state.byteSize)
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
    fun calculateImageSize(image: ImageBitmap): Long
    fun recycleImageBitmap(image: ImageBitmap)
}

/**
 * 全局缓存实例
 */
public object ImageCache {
    private var nodeCache = InnerImageCache(MAX_MEMORY_BYTES, MAX_CANDIDATE_MEMORY_BYTES)
    private val pageCache = InnerImageCache(PAGE_CACHE_MEMORY_BYTES, PAGE_CANDIDATE_MEMORY_BYTES)

    public fun setMaxMemory(maxMemoryBytes: Long) {
        nodeCache.setMaxMemory(maxMemoryBytes)
        pageCache.setMaxMemory(maxMemoryBytes / 4)
    }

    public fun acquireNode(key: String): BitmapState? = nodeCache.acquire(key)
    public fun releaseNode(state: BitmapState): Unit = nodeCache.release(state)
    public fun putNode(key: String, bitmap: ImageBitmap): BitmapState = nodeCache.put(key, bitmap)
    public fun removeNode(key: String): Unit = nodeCache.remove(key)
    public fun hasNode(key: String): Boolean = nodeCache.hasNode(key)
    public fun clearNodes(): Unit = nodeCache.clear()

    public fun acquirePage(key: String): BitmapState? = pageCache.acquire(key)
    public fun releasePage(state: BitmapState): Unit = pageCache.release(state)
    public fun putPage(key: String, bitmap: ImageBitmap): BitmapState = pageCache.put(key, bitmap)
    public fun removePage(key: String): Unit = pageCache.remove(key)
    public fun hasPage(key: String): Boolean = pageCache.hasNode(key)
    public fun clearPages(): Unit = pageCache.clear()
    public fun pageCount(): Int = pageCache.size()

    public fun clear() {
        nodeCache.clear()
        pageCache.clear()
    }
}
