package com.archko.reader.pdf.cache

import androidx.compose.ui.graphics.ImageBitmap

private const val MAX_IMAGE_COUNT = 48 // 主缓存最大图片数
private const val CANDIDATE_TIMEOUT = 3000L // 候选池图片最大保留时间（毫秒）
private const val MAX_CANDIDATE_COUNT = 32 // 候选池最大图片数，防止内存泄漏

/**
 * 通用Kotlin LRU缓存实现，支持Compose多平台。
 * 1. 主缓存LRU，满时淘汰最久未用的1/3图片。
 * 2. 淘汰图片进入二级候选池，延迟回收，短时间内可复用。
 * 3. 候选池超时或超量时自动回收，防止内存泄漏。
 * 4. 回收时可安全交给BitmapPool（Android端）。
 */
public object ImageCache {
    private val cache = object : LinkedHashMap<String, ImageBitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean {
            return false // 手动控制淘汰
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
                    addToCandidatePool(entry.key, entry.value)
                    iterator.remove()
                }
            }
            cleanCandidatePool()
        }
    }

    @Synchronized
    public fun remove(key: String) {
        cache.remove(key)?.let {
            addToCandidatePool(key, it)
        }
        cleanCandidatePool()
    }

    @Synchronized
    public fun clear() {
        cache.forEach { (key, value) -> addToCandidatePool(key, value) }
        cache.clear()
        cleanCandidatePool(force = true)
    }

    // 加入候选池，超量时优先回收最早的
    private fun addToCandidatePool(key: String, image: ImageBitmap) {
        if (candidatePool.size >= MAX_CANDIDATE_COUNT) {
            // 回收最早的
            val oldest = candidatePool.entries.minByOrNull { it.value.second }
            if (oldest != null) {
                recycleImageBitmap(oldest.value.first)
                candidatePool.remove(oldest.key)
            }
        }
        candidatePool[key] = Pair(image, System.currentTimeMillis())
    }

    // 定期清理候选池，超时或强制时回收
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
        // 多平台兼容，Android端可反射交给BitmapPool
        /*try {
            val method = imageBitmap::class.java.getMethod("asAndroidBitmap")
            val bitmap = method.invoke(imageBitmap) as? android.graphics.Bitmap
            if (bitmap != null && !bitmap.isRecycled) {
                val poolClass = Class.forName("com.archko.reader.pdf.cache.BitmapPool")
                val releaseMethod = poolClass.getMethod("release", android.graphics.Bitmap::class.java)
                releaseMethod.invoke(null, bitmap)
            }
        } catch (_: Exception) {
        }*/
    }
}