package com.archko.reader.pdf.util

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import com.archko.reader.pdf.cache.getCacheDirectory
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * 重排结果缓存。
 * 目录: <appCacheDir>/reflow/books/<书名>-<文档签名>/
 * 每个 页+参数 一个 .tiles 文件, 里面按横条保存 WebP 无损/PNG 图片。
 *
 * 使用 [ReflowCacheHolder] 获取单例实例，避免为每本书重建缓存目录扫描。
 */
public class ReflowCache private constructor(
    private val documentPath: String,
    private val cacheRoot: File,
) {

    public companion object {
        private const val MAGIC = 0x4b325246
        private const val CACHE_VERSION = 3
        private const val MAX_SIZE_BYTES = 256L * 1024 * 1024
        private const val MAX_TILE_BYTES = 64 * 1024 * 1024
        private val writer = ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue<Runnable>(1),
            ThreadPoolExecutor.DiscardPolicy()
        )

        /**
         * Create a [ReflowCache] bound to [documentPath].
         *
         * 缓存根目录复用项目统一的缓存目录: <getStorageDirPath>/amupdf/reflow/books/
         * 即 Android/data/<package>/files/amupdf/reflow/books/（外部存储不可用时
         * [FileUtils.getStorageDirPath] 自带回退到内部 filesDir）。
         */
        public fun create(documentPath: String): ReflowCache {
            val root = getCacheDirectory("reflow/books")
            return ReflowCache(documentPath, root)
        }
    }

    private val bookDir: File? = createBookDir()

    public val available: Boolean
        get() = bookDir != null

    private fun createBookDir(): File? {
        return try {
            if (!cacheRoot.exists() && !cacheRoot.mkdirs()) {
                return null
            }
            val file = File(documentPath)
            val signature = String.format(
                Locale.US,
                "%s:%d:%d",
                file.absolutePath,
                file.length(),
                file.lastModified()
            )
            val name = sanitize(file.nameWithoutExtension)
            val dir = File(cacheRoot, "$name-${sha1(signature).substring(0, 10)}")
            if (!dir.exists() && !dir.mkdirs()) {
                return null
            }
            dir.setLastModified(System.currentTimeMillis())
            writer.execute { prune(cacheRoot) }
            dir
        } catch (e: Exception) {
            null
        }
    }

    public fun buildKey(
        pageNumber: Int,
        targetWidth: Int,
        targetHeight: Int,
        densityDpi: Int,
        renderDpi: Float,
        fontSize: Float,
        wordSpacing: Float,
        crop: Boolean,
        orientation: Int,
        pageSliceBounds: Rect?,
    ): String {
        val source = String.format(
            Locale.US,
            "%d|%d|%d|%d|%d|%.2f|%.3f|%.3f|%s|%d|%s",
            CACHE_VERSION,
            pageNumber,
            targetWidth,
            targetHeight,
            densityDpi,
            renderDpi,
            fontSize,
            wordSpacing,
            crop,
            orientation,
            pageSliceBounds?.toString() ?: ""
        )
        return String.format(Locale.US, "%d-%s", pageNumber, sha1(source).substring(0, 16))
    }

    public fun load(key: String): MutableList<ImageBitmap>? {
        val dir = bookDir ?: return null
        val file = File(dir, "$key.tiles")
        if (!file.exists()) {
            return null
        }
        try {
            DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
                if (input.readInt() != MAGIC || input.readInt() != CACHE_VERSION) {
                    file.delete()
                    return null
                }
                if (input.readUTF() != key) {
                    file.delete()
                    return null
                }
                val tileWidth = input.readInt()
                val tileCount = input.readInt()
                if (tileWidth <= 0 || tileCount <= 0 || tileCount > 512) {
                    file.delete()
                    return null
                }
                val list = ArrayList<ImageBitmap>(tileCount)
                for (i in 0 until tileCount) {
                    val width = input.readInt()
                    val height = input.readInt()
                    input.readInt()
                    val length = input.readInt()
                    if (width <= 0 || height <= 0 || width > tileWidth
                        || length <= 0 || length > MAX_TILE_BYTES
                    ) {
                        recycleAll(list)
                        file.delete()
                        return null
                    }
                    val bytes = ByteArray(length)
                    input.readFully(bytes)
                    val bitmap = decodeReflowTile(bytes)
                    if (bitmap == null) {
                        recycleAll(list)
                        file.delete()
                        return null
                    }
                    list.add(bitmap)
                }
                file.setLastModified(System.currentTimeMillis())
                dir.setLastModified(System.currentTimeMillis())
                return list
            }
        } catch (e: Exception) {
            file.delete()
            return null
        }
    }

    public fun save(key: String, bitmaps: List<ImageBitmap>) {
        val dir = bookDir ?: return
        if (bitmaps.isEmpty()) {
            return
        }
        val tiles = ArrayList(bitmaps)
        writer.execute {
            val target = File(dir, "$key.tiles")
            val tmp = File(dir, "$key.tmp")
            try {
                DataOutputStream(BufferedOutputStream(FileOutputStream(tmp))).use { output ->
                    output.writeInt(MAGIC)
                    output.writeInt(CACHE_VERSION)
                    output.writeUTF(key)
                    output.writeInt(tiles[0].width)
                    output.writeInt(tiles.size)
                    for (bitmap in tiles) {
                        val encoded = encodeReflowTile(bitmap)
                        if (encoded == null) {
                            tmp.delete()
                            return@execute
                        }
                        output.writeInt(bitmap.width)
                        output.writeInt(bitmap.height)
                        output.writeInt(encoded.format)
                        output.writeInt(encoded.bytes.size)
                        output.write(encoded.bytes)
                    }
                }
                if (!tmp.renameTo(target)) {
                    target.delete()
                    if (!tmp.renameTo(target)) {
                        tmp.delete()
                        return@execute
                    }
                }
                target.setLastModified(System.currentTimeMillis())
                dir.setLastModified(System.currentTimeMillis())
                prune(dir.parentFile ?: dir)
            } catch (e: Exception) {
                tmp.delete()
            }
        }
    }

    private fun prune(root: File) {
        try {
            val now = System.currentTimeMillis()
            val files = ArrayList<File>()
            root.walkTopDown().forEach { file ->
                if (file.isFile) {
                    if (file.name.endsWith(".tmp") && now - file.lastModified() > 24L * 60 * 60 * 1000) {
                        file.delete()
                    } else if (file.name.endsWith(".tiles")) {
                        files.add(file)
                    }
                }
            }
            var total = files.sumOf { it.length() }
            if (total <= MAX_SIZE_BYTES) {
                return
            }
            files.sortBy { it.lastModified() }
            for (file in files) {
                if (total <= MAX_SIZE_BYTES) {
                    break
                }
                val length = file.length()
                if (file.delete()) {
                    total -= length
                }
            }
            root.walkTopDown()
                .filter { it.isDirectory && it != root }
                .toList()
                .asReversed()
                .forEach {
                    if (it.list()?.isEmpty() == true) {
                        it.delete()
                    }
                }
        } catch (e: Exception) {
        }
    }

    private fun recycleAll(bitmaps: List<ImageBitmap>) {
        for (bitmap in bitmaps) {
            recycleReflowTile(bitmap)
        }
    }

    private fun sanitize(name: String): String {
        var result = name.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").trim('_')
        if (result.isEmpty()) {
            result = "book"
        }
        if (result.length > 40) {
            result = result.substring(0, 40)
        }
        return result
    }

    private fun sha1(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray(Charsets.UTF_8))
        val builder = StringBuilder(digest.size * 2)
        for (byte in digest) {
            builder.append(String.format(Locale.US, "%02x", byte))
        }
        return builder.toString()
    }
}
