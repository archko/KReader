package com.archko.reader.pdf.cache

import android.graphics.Bitmap

/**
 * @author: archko 2019/12/25 :15:54
 */
public object BitmapCache {

    private val pageCache: InnerCache
    private val nodeCache: InnerCache

    public fun getBitmap(key: String): Bitmap? {
        return pageCache.getBitmap(key)
    }

    public fun addBitmap(key: String, value: Bitmap): Bitmap? {
        return pageCache.addBitmap(key, value)
    }

    public fun remove(key: String): Bitmap? {
        return pageCache.remove(key)
    }

    public fun getNodeBitmap(key: String): Bitmap? {
        return nodeCache.getBitmap(key)
    }

    public fun addNodeBitmap(key: String, value: Bitmap): Bitmap? {
        return nodeCache.addBitmap(key, value)
    }

    public fun removeNode(key: String): Bitmap? {
        return nodeCache.remove(key)
    }

    public fun clear() {
        pageCache.clear()
        nodeCache.clear()
    }

    /**
     * 页面缩略图的缓存大小,通常按页面高宽的1/4,如果页面非常大,比如4000,那么缓存能存10多屏
     */
    private var MAX_PAGE_SIZE_IN_BYTES = 160 * 1024 * 1024

    /**
     * 节点的缓存,平均一个1080*2240的屏幕上的node需要2419200*4,大约8mb多点,32m可以缓存几个屏幕
     */
    private var MAX_NODE_SIZE_IN_BYTES = 36 * 1024 * 1024

    init {
        pageCache = InnerCache(MAX_PAGE_SIZE_IN_BYTES)
        nodeCache = InnerCache(MAX_NODE_SIZE_IN_BYTES)
    }

    private class InnerCache(maxByte: Int) {
        private var maxByte: Int = MAX_PAGE_SIZE_IN_BYTES
        private var mPoolSizeInBytes = 0

        private val bitmapLinkedMap = LinkedHashMap<String?, Bitmap?>(16, 0.75f, true)

        private var putCount = 0
        private val createCount = 0
        private var evictionCount = 0
        private var hitCount = 0
        private var missCount = 0

        init {
            this.maxByte = maxByte
        }

        public fun getBitmap(key: String): Bitmap? {
            if (key == null) {
                throw NullPointerException("key == null")
            }

            val mapValue: Bitmap?
            synchronized(this) {
                mapValue = bitmapLinkedMap.get(key)
                if (mapValue != null) {
                    hitCount++
                    if (mapValue.isRecycled()) {
                        bitmapLinkedMap.remove(key)
                        return null
                    }
                    return mapValue
                }
                missCount++
            }

            /*Bitmap createdValue = create(key);
        if (createdValue == null) {
            return null;
        }

        synchronized (this) {
            createCount++;
            mapValue = map.put(key, createdValue);

            if (mapValue != null) {
                // There was a conflict so undo that last put
                map.put(key, mapValue);
            } else {
                //size += safeSizeOf(key, createdValue);
            }
        }

        if (mapValue != null) {
            entryRemoved(false, key, createdValue, mapValue);
            return mapValue;
        } else {
            trimToSize(maxSize);
            return createdValue;
        }*/
            return null
        }

        public fun addBitmap(key: String, value: Bitmap): Bitmap? {
            if (key == null || value == null) {
                throw NullPointerException("key == null || value == null")
            }
            if (value.isRecycled()) {
                return null
            }
            while (mPoolSizeInBytes > MAX_PAGE_SIZE_IN_BYTES) {
                removeLast()
            }

            mPoolSizeInBytes += value.getByteCount()

            val previous: Bitmap?
            synchronized(this) {
                putCount++
                previous = bitmapLinkedMap.put(key, value)
                if (previous != null) {
                    mPoolSizeInBytes -= previous.getByteCount()
                }
            }

            //System.out.println(String.format("put.size:%s, key:%s, val:%s, size:%s", map.size(), key, value, mPoolSizeInBytes));
            if (previous != null) {
                entryRemoved(false, key, previous, value)
            }

            return previous
        }

        fun removeLast() {
            val key: String
            val value: Bitmap?
            synchronized(this) {
                if (bitmapLinkedMap.isEmpty()) {
                    return
                }
                val toEvict = bitmapLinkedMap.entries.iterator().next()
                key = toEvict.key.toString()
                value = toEvict.value
                bitmapLinkedMap.remove(key)
                if (value == null || value.isRecycled) {
                    mPoolSizeInBytes -= value!!.getByteCount()
                } else {
                    caculateSize()
                }
                evictionCount++
            }

            //System.out.println(String.format("removeLast.size:%s, key:%s,val:%s, size:%s", map.size(), key, value, mPoolSizeInBytes));
            value?.let { entryRemoved(true, key, it, null) }
        }

        fun caculateSize() {
            var size = 0
            val oldSize = mPoolSizeInBytes
            for (entry in bitmapLinkedMap.entries) {
                if (!entry.value!!.isRecycled()) {
                    size += entry.value!!.getByteCount()
                }
            }
            mPoolSizeInBytes = size
            //System.out.println("caculateSize:" + size + " old:" + oldSize);
        }

        public fun remove(key: String): Bitmap? {
            if (key == null) {
                throw NullPointerException("key == null")
            }

            val previous: Bitmap?
            synchronized(this) {
                previous = bitmapLinkedMap.remove(key)
                if (previous != null) {
                    if (!previous.isRecycled()) {
                        mPoolSizeInBytes -= previous.getByteCount()
                    } else {
                        caculateSize()
                    }
                }
            }

            if (previous != null) {
                entryRemoved(false, key, previous, null)
            }

            return previous
        }

        protected fun entryRemoved(
            evicted: Boolean, key: String, oldValue: Bitmap,
            newValue: Bitmap?
        ) {
        }

        protected fun create(key: String): Bitmap? {
            return null
        }

        public fun clear() {
            val size = bitmapLinkedMap.size
            for (i in 0..<size) {
                removeLast()
            }
        }

        @Synchronized
        public fun hitCount(): Int {
            return hitCount
        }

        @Synchronized
        public fun missCount(): Int {
            return missCount
        }

        @Synchronized
        public fun createCount(): Int {
            return createCount
        }

        @Synchronized
        public fun putCount(): Int {
            return putCount
        }

        @Synchronized
        public fun evictionCount(): Int {
            return evictionCount
        }

        @Synchronized
        public fun snapshot(): MutableMap<String?, Bitmap?> {
            return LinkedHashMap<String?, Bitmap?>(bitmapLinkedMap)
        }
    }

    public fun setMaxMemory(maxMemory: Float) {
        MAX_PAGE_SIZE_IN_BYTES = (maxMemory * 0.3).toInt()
        MAX_NODE_SIZE_IN_BYTES = (maxMemory - MAX_PAGE_SIZE_IN_BYTES).toInt()
    }
}
