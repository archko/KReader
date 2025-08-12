package com.archko.reader.pdf.decoder

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntSize
import com.archko.reader.image.TiffLoader
import com.archko.reader.pdf.component.Size
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.APage
import com.archko.reader.pdf.entity.Hyperlink
import com.archko.reader.pdf.entity.Item
import java.io.File
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.round

/**
 * @author: archko 2025/8/9 :6:26
 */
public class TiffDecoder(public val file: File) : ImageDecoder {

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

    init {
        if (!file.exists()) {
            throw IllegalArgumentException("文档文件不存在: ${file.absolutePath}")
        }

        if (!file.canRead()) {
            throw SecurityException("无法读取文档文件: ${file.absolutePath}")
        }

        tiffLoader = TiffLoader()

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

        tiffLoader!!.openTiff(file.absolutePath)
        val tiffInfo = tiffLoader!!.tiffInfo
        tiffInfo?.run {
            val width = tiffInfo.width
            val height = tiffInfo.height

            val size = Size(
                width,
                height,
                0,
                scale = 1.0f,
                0,
            )
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
        return try {
            val pageWidth = (region.width / scale).toInt()
            val pageHeight = (region.height / scale).toInt()
            val patchX = (region.left / scale).toInt()
            val patchY = (region.top / scale).toInt()
            val bitmapWidth = (pageWidth * scale).toInt()
            val bitmapHeight = (pageHeight * scale).toInt()

            //println("TiffDecoder.renderPageRegion: region=${region}, 原始=${originalSize.width}x${originalSize.height}, offset:$patchX-$patchY, 截取=${bitmapWidth}x${bitmapHeight}, ${pageWidth}x${pageHeight}, 缩放=$scale")

            val bitmap = tiffLoader!!.decodeRegionToBitmap(
                patchX,
                patchY,
                bitmapWidth,
                bitmapHeight,
                scale,
            )

            if (null != bitmap) {
                return bitmap.toComposeImageBitmap()
            } else {
                return ImageBitmap(outWidth, outHeight, ImageBitmapConfig.Rgb565)
            }
        } catch (e: Exception) {
            println("renderPageRegion error for file ${file.absolutePath}: $e")
            ImageBitmap(outWidth, outHeight, ImageBitmapConfig.Rgb565)
        }
    }

    /**
     * 计算最接近的2的n次方缩放比例的倒数
     */
    private fun calculatePowerOfTwoScale(scale: Float): Float {
        if (scale <= 0) return 1f

        // 计算最接近的2的n次方，然后取倒数
        val power = round(log2(1f / scale))
        return 1f / 2f.pow(power.coerceAtLeast(0f))
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
            val scale = if (aPage.width > 0) {
                viewSize.width.toFloat() / aPage.getWidth(false)
            } else {
                1f
            }

            // 使用传入的scale参数计算目标尺寸
            val targetWidth = (originalSize.width * scale).toInt()
            val targetHeight = (originalSize.height * scale).toInt()
            println("TiffDecoder.renderPage: 原始=${originalSize.width}x${originalSize.height}, 输出=${outWidth}x${outHeight}, 缩放=$scale, 目标=${targetWidth}x${targetHeight}")

            val bitmapWidth = (targetWidth * scale).toInt()
            val bitmapHeight = (targetHeight * scale).toInt()

            //println("TiffDecoder.renderPageRegion: region=${region}, 原始=${originalSize.width}x${originalSize.height}, offset:$patchX-$patchY, 截取=${bitmapWidth}x${bitmapHeight}, ${pageWidth}x${pageHeight}, 缩放=$scale")

            val bitmap = tiffLoader!!.decodeRegionToBitmap(
                0,
                0,
                bitmapWidth,
                bitmapHeight,
                scale,
            )

            if (null != bitmap) {
                return bitmap.toComposeImageBitmap()
            } else {
                return ImageBitmap(outWidth, outHeight, ImageBitmapConfig.Rgb565)
            }
        } catch (e: Exception) {
            println("renderPage error: $e")
            return ImageBitmap(outWidth, outHeight, ImageBitmapConfig.Rgb565)
        }
    }

    override fun close() {
        tiffLoader?.close()
    }
}