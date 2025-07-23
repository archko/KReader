package com.archko.reader.pdf.cache

import androidx.compose.ui.graphics.ImageBitmap

private const val MAX_IMAGE_COUNT = 48 // 可根据平台内存调整
private const val CANDIDATE_TIMEOUT = 3000L // 3秒，候选池图片最大保留时间

/**
 * 通用Kotlin LRU缓存实现，支持Compose多平台。
 * 缓存满时，自动清除最久未用的1/3图片，淘汰图片进入候选池，延迟回收。
 */
public object ImageCache {
    private val cache = object : LinkedHashMap<String, ImageBitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean {
            // 不在这里自动移除，手动控制
            return false
        }
    }

    // 二级候选池，key->(图片,进入时间)
    private val candidatePool = mutableMapOf<String, Pair<ImageBitmap, Long>>()

    @Synchronized
    public fun get(key: String): ImageBitmap? {
        cache[key]?.let { return it }
        candidatePool[key]?.let { (img, ts) ->
            if (System.currentTimeMillis() - ts < CANDIDATE_TIMEOUT) {
                // 未超时，直接复用
                cache[key] = img
                candidatePool.remove(key)
                cleanCandidatePool()
                return img
            } else {
                // 超时，安全回收
                recycleImageBitmap(img)
                candidatePool.remove(key)
                cleanCandidatePool()
            }
        }
        cleanCandidatePool()
        return null
    }

    @Synchronized
    public fun put(key: String, painter: ImageBitmap) {
        cache[key] = painter
        if (cache.size > MAX_IMAGE_COUNT) {
            val removeCount = (MAX_IMAGE_COUNT / 3).coerceAtLeast(1)
            val iterator = cache.entries.iterator()
            repeat(removeCount) {
                if (iterator.hasNext()) {
                    val entry = iterator.next()
                    candidatePool[entry.key] = Pair(entry.value, System.currentTimeMillis())
                    iterator.remove()
                }
            }
            cleanCandidatePool()
        }
    }

    @Synchronized
    public fun remove(key: String) {
        cache.remove(key)?.let {
            candidatePool[key] = Pair(it, System.currentTimeMillis())
        }
        cleanCandidatePool()
    }

    @Synchronized
    public fun clear() {
        cache.values.forEach { candidatePool["_auto_${it.hashCode()}"] = Pair(it, System.currentTimeMillis()) }
        cache.clear()
        cleanCandidatePool(force = true)
    }

    // 定期清理候选池，超时图片安全回收
    @Synchronized
    private fun cleanCandidatePool(force: Boolean = false) {
        val now = System.currentTimeMillis()
        val iterator = candidatePool.iterator()
        while (iterator.hasNext()) {
            val (key, pair) = iterator.next()
            if (force || now - pair.second > CANDIDATE_TIMEOUT) {
                recycleImageBitmap(pair.first)
                iterator.remove()
            }
        }
    }

    // 安全回收底层Bitmap（Android端）
    private fun recycleImageBitmap(imageBitmap: ImageBitmap) {
        /*try {
            // 仅Android端有效，其他平台可忽略
            val method = imageBitmap::class.java.getMethod("asAndroidBitmap")
            val bitmap = method.invoke(imageBitmap) as? android.graphics.Bitmap
            if (bitmap != null && !bitmap.isRecycled) {
                // 交给BitmapPool
                val poolClass = Class.forName("com.archko.reader.pdf.cache.BitmapPool")
                val releaseMethod = poolClass.getMethod("release", android.graphics.Bitmap::class.java)
                releaseMethod.invoke(null, bitmap)
            }
        } catch (_: Exception) {
            // 非Android平台或反射失败，忽略
        }*/
    }
}