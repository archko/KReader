package com.archko.reader.pdf.decoder

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import com.archko.reader.image.TiffLoader
import com.archko.reader.pdf.cache.BitmapPool
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
import kotlin.math.ceil

/**
 * @author: archko 2025/8/9 :6:26
 */
public class TiffDecoder(
    public val documentInfo: DocumentInfo,
    private val contentResolver: ContentResolver? = null
) : ImageDecoder {

    public override var pageCount: Int = 1

    // 私有变量存储原始页面尺寸
    public override var originalPageSizes: List<Size> = listOf()

    // 对外提供的缩放后页面尺寸
    public override var pageSizes: List<Size> = listOf()

    public override var outlineItems: List<Item>? = emptyList()

    public override var imageSize: IntSize = IntSize.Zero

    public var viewSize: IntSize = IntSize.Zero
    public override val aPageList: MutableList<APage>? = ArrayList()
    private var tiffLoader: TiffLoader? = null
    public override var cacheBean: ReflowCacheBean? = null

    init {
        val docPath = documentInfo.path
        tiffLoader = TiffLoader()

        if (contentResolver != null && documentInfo.hasUri()) {
            try {
                val uri = Uri.parse(documentInfo.uri)
                val pfd = contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    val fd = pfd.detachFd()
                    tiffLoader!!.openTiffFd(fd)
                    pfd.close()
                } else {
                    throw RuntimeException("无法打开URI: ${documentInfo.uri}")
                }
            } catch (e: Exception) {
                throw RuntimeException("无法打开文档: $docPath, 错误: ${e.message}", e)
            }
        } else if (docPath != null) {
            val file = File(docPath)
            if (!file.exists()) {
                throw IllegalArgumentException("文档文件不存在: $docPath")
            }
            if (!file.canRead()) {
                throw SecurityException("无法读取文档文件: $docPath")
            }
            tiffLoader!!.openTiff(docPath)
        } else {
            throw IllegalArgumentException("文档信息不完整: 无路径和URI")
        }

        originalPageSizes = prepareSizes()

        cacheCoverIfNeeded()
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
            var totalHeight = 0f  // 使用浮点数累积，避免舍入误差

            // 计算缩放后的页面尺寸
            val scaledPageSizes = mutableListOf<Size>()

            for (i in originalPageSizes.indices) {
                val originalPage = originalPageSizes[i]
                // 计算每页的缩放比例，使宽度等于viewportSize.width
                val scale = 1f * documentWidth / originalPage.width
                val scaledWidth = documentWidth
                val scaledHeight = originalPage.height * scale  // 保持浮点数精度

                // 创建缩放后的页面尺寸，yOffset使用当前累积的浮点数值转换为整数
                val scaledPage =
                    Size(scaledWidth, scaledHeight.toInt(), i, scale, totalHeight.toInt())
                scaledPageSizes.add(scaledPage)
                totalHeight += scaledHeight  // 浮点数累积
            }

            // 更新对外提供的页面尺寸
            pageSizes = scaledPageSizes
            imageSize = IntSize(documentWidth, totalHeight.toInt())
        }
    }

    /**
     * 获取原始页面尺寸
     */
    public fun getOriginalPageSize(index: Int): Size {
        return originalPageSizes[index]
    }

    private fun prepareSizes(): List<Size> {
        val list = mutableListOf<Size>()

        val tiffInfo = tiffLoader!!.tiffInfo
        val width = tiffInfo!!.width
        val height = tiffInfo.height

        val size = Size(
            width,
            height,
            0,
            scale = 1.0f,
            0,
        )
        list.add(size)
        return list
    }

    private fun createOutputBitmap(width: Int, height: Int): Bitmap {
        val info = tiffLoader?.tiffInfo
        val useRgb565 = info != null && info.bitsPerSample == 8 &&
                info.photometricInterpretation != 3 &&
                ((info.photometricInterpretation == 2 && info.samplesPerPixel <= 3) ||
                        ((info.photometricInterpretation == 0 || info.photometricInterpretation == 1) &&
                                info.samplesPerPixel == 1))
        val config = if (useRgb565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        return BitmapPool.acquire(width.coerceAtLeast(1), height.coerceAtLeast(1), config)
    }

    private fun decodeRegionToImageBitmap(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        scale: Float,
        outWidth: Int,
        outHeight: Int
    ): ImageBitmap {
        val bitmap = createOutputBitmap(outWidth, outHeight)
        val success = tiffLoader!!.decodeRegionToBitmap(x, y, width, height, scale, bitmap)
        if (!success) {
            BitmapPool.release(bitmap)
            return ImageBitmap(outWidth, outHeight, ImageBitmapConfig.Rgb565)
        }
        return bitmap.asImageBitmap()
    }

    public override fun renderPageRegion(
        region: Rect,
        index: Int,
        scale: Float,
        viewSize: IntSize,
        outWidth: Int,
        outHeight: Int
    ): ImageBitmap {
        return try {
            // 宽高向上取整，配合 native 的像素中心采样，避免相邻 tile 出现接缝
            val patchX = (region.left / scale).toInt()
            val patchY = (region.top / scale).toInt()
            val originalSize = originalPageSizes[index]
            val pageWidth = ceil(region.width / scale).toInt()
                .coerceIn(1, (originalSize.width - patchX).coerceAtLeast(1))
            val pageHeight = ceil(region.height / scale).toInt()
                .coerceIn(1, (originalSize.height - patchY).coerceAtLeast(1))
            //精度的问题,jni那边不支持小数进位四舍五入,所以这里的高宽要修正一下.
            decodeRegionToImageBitmap(
                patchX,
                patchY,
                pageWidth,
                pageHeight,
                scale,
                ceil(pageWidth * scale).toInt(),
                ceil(pageHeight * scale).toInt()
            )
        } catch (e: Exception) {
            println("renderPageRegion error: ${documentInfo ?: ""}, $e")
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
        try {
            val originalSize = originalPageSizes[aPage.index]

            // 根据输出尺寸计算合适的缩放比例
            // 选择能够完全适应输出尺寸的缩放比例
            val scaleX = outWidth.toFloat() / originalSize.width
            val scaleY = outHeight.toFloat() / originalSize.height
            val scale = minOf(scaleX, scaleY)

            println("TiffDecoder.renderPage: 原始=${originalSize.width}x${originalSize.height}, 输出=${outWidth}x${outHeight}, 缩放=$scale (scaleX=$scaleX, scaleY=$scaleY)")

            return decodeRegionToImageBitmap(
                0,
                0,
                originalSize.width,
                originalSize.height,
                scale,
                ceil(originalSize.width * scale).toInt(),
                ceil(originalSize.height * scale).toInt()
            )
        } catch (e: Exception) {
            println("renderPage error: $e")
            return ImageBitmap(outWidth, outHeight, ImageBitmapConfig.Rgb565)
        }
    }

    override fun close() {
        tiffLoader?.close()

        ImageCache.clear()
    }

    private fun cacheCoverIfNeeded() {
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