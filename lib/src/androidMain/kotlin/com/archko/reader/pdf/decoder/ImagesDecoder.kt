package com.archko.reader.pdf.decoder

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.cache.ImageCache
import com.archko.reader.pdf.component.DecodeTask
import com.archko.reader.pdf.component.Size
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.APage
import com.archko.reader.pdf.entity.DocumentInfo
import com.archko.reader.pdf.entity.Hyperlink
import com.archko.reader.pdf.entity.Item
import com.archko.reader.pdf.entity.ReflowBean
import com.archko.reader.pdf.entity.ReflowCacheBean
import java.io.File
import java.io.FileInputStream
import java.util.Collections
import java.util.LinkedHashMap
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

/**
 * 图片文件解码器，支持多个图片文件
 * @author: archko 2025/1/20
 */
public class ImagesDecoder(
    private val documents: List<DocumentInfo>,
    private val contentResolver: ContentResolver? = null
) : ImageDecoder {

    public override var pageCount: Int = documents.size

    // 私有变量存储原始页面尺寸
    public override var originalPageSizes: List<Size> = listOf()

    // 对外提供的缩放后页面尺寸
    public override var pageSizes: List<Size> = listOf()

    public override var outlineItems: List<Item>? = emptyList()

    public override var imageSize: IntSize = IntSize.Zero

    public var viewSize: IntSize = IntSize.Zero
    public override val aPageList: MutableList<APage> = ArrayList()

    // 缓存BitmapRegionDecoder，避免重复创建，按访问顺序淘汰
    private val maxRegionDecoders = 5
    private val regionDecoders = Collections.synchronizedMap(
        object : LinkedHashMap<Int, BitmapRegionDecoder>(maxRegionDecoders, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<Int, BitmapRegionDecoder>
            ): Boolean {
                if (size > maxRegionDecoders) {
                    try {
                        eldest.value.recycle()
                    } catch (e: Exception) {
                        println("Error closing BitmapRegionDecoder: $e")
                    }
                    return true
                }
                return false
            }
        }
    )
    public override var cacheBean: ReflowCacheBean? = null

    init {
        if (documents.isEmpty()) {
            throw IllegalArgumentException("图片文件列表不能为空")
        }

        // 检查所有文件（仅路径模式下）
        if (contentResolver == null) {
            documents.forEach { doc ->
                val path = doc.path
                if (path != null) {
                    val file = File(path)
                    if (!file.exists()) {
                        throw IllegalArgumentException("图片文件不存在: $path")
                    }
                    if (!file.canRead()) {
                        throw SecurityException("无法读取图片文件: $path")
                    }
                }
            }
        }

        // 初始化原始页面尺寸
        originalPageSizes = prepareSizes()
    }

    override fun size(viewportSize: IntSize): IntSize {
        if ((imageSize == IntSize.Zero || viewSize != viewportSize)
            && viewportSize.width > 0 && viewportSize.height > 0
        ) {
            viewSize = viewportSize
            calculateSize(viewportSize)
        }
        return imageSize
    }

    override fun getPageLinks(pageIndex: Int): List<Hyperlink> {
        return emptyList()
    }

    private fun calculateSize(viewportSize: IntSize) {
        if (originalPageSizes.isNotEmpty()) {
            // 文档宽度直接使用viewportSize.width
            val documentWidth = viewportSize.width
            var totalHeight = 0

            // 计算缩放后的页面尺寸
            val scaledPageSizes = mutableListOf<Size>()

            for (i in originalPageSizes.indices) {
                val originalPage = originalPageSizes[i]
                // 计算每页的缩放比例，使宽度等于viewportSize.width
                val scale = 1f * documentWidth / originalPage.width
                val scaledWidth = documentWidth
                val scaledHeight = (originalPage.height * scale).toInt()

                // 创建缩放后的页面尺寸
                val scaledPage = Size(scaledWidth, scaledHeight, i, scale, totalHeight)
                scaledPageSizes.add(scaledPage)
                totalHeight += scaledHeight
            }

            // 更新对外提供的页面尺寸
            pageSizes = scaledPageSizes
            imageSize = IntSize(documentWidth, totalHeight)
        }
    }

    /**
     * 获取原始页面尺寸
     */
    public fun getOriginalPageSize(index: Int): Size {
        return originalPageSizes[index]
    }

    private fun getImageBounds(index: Int): Pair<Int, Int> {
        val doc = documents[index]
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        if (contentResolver != null && doc.hasUri()) {
            try {
                val uri = Uri.parse(doc.uri)
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream, null, options)
                }
            } catch (e: Exception) {
                println("Failed to decode bounds for ${doc.uri}: $e")
            }
        } else if (doc.path != null) {
            BitmapFactory.decodeFile(doc.path, options)
        }
        return Pair(options.outWidth, options.outHeight)
    }

    private fun prepareSizes(): List<Size> {
        val list = mutableListOf<Size>()
        var totalHeight = 0

        for (i in documents.indices) {
            val (width, height) = getImageBounds(i)
            val size = Size(
                width,
                height,
                i,
                scale = 1.0f,
                totalHeight,
            )
            totalHeight += size.height
            list.add(size)
        }
        return list
    }

    public override fun renderPageRegion(
        region: Rect,
        index: Int,
        scale: Float,
        viewSize: IntSize,
        outWidth: Int,
        outHeight: Int
    ): ImageBitmap {
        if (index >= documents.size || scale <= 0f) {
            return ImageBitmap(outWidth, outHeight, ImageBitmapConfig.Rgb565)
        }

        return try {
            // 部分区域：使用BitmapRegionDecoder
            val regionDecoder = getRegionDecoder(index)
            if (regionDecoder != null) {
                val originalSize = originalPageSizes[index]
                // 四舍五入到内容像素边界：Page 传入的 region 由整数内容边界 × scale 得到，
                // 相邻 tile 在公共边上得到完全相同的整数，解码区域互不重叠，
                // 不会把邻居的一列/一行像素重复画进当前 tile。
                val scaledRegion = android.graphics.Rect(
                    round(region.left / scale).toInt().coerceIn(0, originalSize.width),
                    round(region.top / scale).toInt().coerceIn(0, originalSize.height),
                    round(region.right / scale).toInt().coerceIn(0, originalSize.width),
                    round(region.bottom / scale).toInt().coerceIn(0, originalSize.height)
                )

                if (scaledRegion.width() > 0 && scaledRegion.height() > 0) {
                    val options = BitmapFactory.Options().apply {
                        inSampleSize =
                            calculateInSampleSizeForRegion(scaledRegion, outWidth, outHeight)
                    }

                    val regionBitmap = regionDecoder.decodeRegion(scaledRegion, options)
                    regionBitmap?.asImageBitmap()
                        ?: ImageBitmap(outWidth, outHeight, ImageBitmapConfig.Rgb565)
                } else {
                    ImageBitmap(outWidth, outHeight, ImageBitmapConfig.Rgb565)
                }
            } else {
                ImageBitmap(outWidth, outHeight, ImageBitmapConfig.Rgb565)
            }
        } catch (e: Exception) {
            println("renderPageRegion error for index $index: $e")
            ImageBitmap(outWidth, outHeight, ImageBitmapConfig.Rgb565)
        }
    }

    public override fun renderPageRegion(
        task: DecodeTask,
        totalScale: Float
    ): ImageBitmap {
        return renderPageRegion(
            task.pageSliceBounds,
            task.pageIndex,
            totalScale,
            IntSize.Zero,
            task.width,
            task.height
        )
    }

    override fun renderPage(
        aPage: APage,
        viewSize: IntSize,
        outWidth: Int,
        outHeight: Int,
        crop: Boolean
    ): ImageBitmap {
        val originalSize = originalPageSizes[aPage.index]
        val scale = if (aPage.width > 0) {
            outWidth.toFloat() / aPage.getWidth(crop)
        } else {
            1f
        }

        val targetWidth = (originalSize.width * scale).toInt()
        val targetHeight = (originalSize.height * scale).toInt()

        // 关键更改：计算 Sampling 时结合图片的原始尺寸与 View 请求的 targetWidth/targetHeight
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(originalSize.width, originalSize.height, targetWidth, targetHeight)
        }

        println("ImagesDecoder.renderPage: 原始=${originalSize.width}x${originalSize.height}, 输出=${outWidth}x${outHeight}, 目标=$targetWidth-$targetHeight, inSampleSize=${options.inSampleSize}")

        val bitmap = decodeImage(aPage.index, options)
        if (bitmap != null) {
            return bitmap.asImageBitmap()
        } else {
            return ImageBitmap(targetWidth, targetHeight, ImageBitmapConfig.Rgb565)
        }
    }

    /**
     * 获取或创建BitmapRegionDecoder，按访问顺序淘汰，最多缓存5个
     */
    private fun getRegionDecoder(index: Int): BitmapRegionDecoder? {
        if (index >= documents.size) return null

        return regionDecoders.getOrPut(index) {
            try {
                val doc = documents[index]
                if (contentResolver != null && doc.hasUri()) {
                    val uri = Uri.parse(doc.uri)
                    val pfd = contentResolver.openFileDescriptor(uri, "r")
                    if (pfd != null) {
                        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { inputStream ->
                            BitmapRegionDecoder.newInstance(inputStream, false)
                        }
                    } else {
                        null
                    }
                } else if (doc.path != null) {
                    FileInputStream(File(doc.path)).use { inputStream ->
                        BitmapRegionDecoder.newInstance(inputStream, false)
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                println("Failed to create BitmapRegionDecoder for index $index: $e")
                null
            } ?: throw RuntimeException("Cannot create BitmapRegionDecoder")
        }
    }

    private fun decodeImage(index: Int, options: BitmapFactory.Options): Bitmap? {
        val doc = documents[index]
        return if (contentResolver != null && doc.hasUri()) {
            try {
                val uri = Uri.parse(doc.uri)
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream, null, options)
                }
            } catch (e: Exception) {
                println("Failed to decode image from URI ${doc.uri}: $e")
                null
            }
        } else if (doc.path != null) {
            BitmapFactory.decodeFile(doc.path, options)
        } else {
            null
        }
    }

    /**
     * 计算采样大小以优化内存使用
     */
    private fun calculateInSampleSize(
        srcWidth: Int,
        srcHeight: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1
        if (reqHeight <= 0 || reqWidth <= 0 || srcWidth <= 0 || srcHeight <= 0) return inSampleSize

        if (srcHeight > reqHeight || srcWidth > reqWidth) {
            val halfHeight = srcHeight / 2
            val halfWidth = srcWidth / 2

            // 选择能保证采样后的尺寸仍然大于等于目标尺寸的最大的 2 的幂次方
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * 为区域解码计算采样大小
     */
    private fun calculateInSampleSizeForRegion(
        region: android.graphics.Rect,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val regionWidth = region.width()
        val regionHeight = region.height()
        var inSampleSize = 1

        if (reqHeight <= 0 || reqWidth <= 0 || regionWidth <= 0 || regionHeight <= 0) return inSampleSize

        if (regionHeight > reqHeight || regionWidth > reqWidth) {
            val halfHeight = regionHeight / 2
            val halfWidth = regionWidth / 2

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    override fun close() {
        // 关闭所有region decoders
        regionDecoders.values.forEach { decoder ->
            try {
                decoder.recycle()
            } catch (e: Exception) {
                println("Error closing BitmapRegionDecoder: $e")
            }
        }
        regionDecoders.clear()

        ImageCache.clear()
    }

    override fun getStructuredText(index: Int): Any? {
        return null
    }

    override fun decodeReflowSinglePage(pageIndex: Int): ReflowBean? {
        return null
    }

    override fun decodeReflowAllPages(): List<ReflowBean> {
        return emptyList()
    }
}