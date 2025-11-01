package com.archko.reader.pdf.decoder

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntSize
import com.archko.reader.image.TiffLoader
import com.archko.reader.pdf.cache.CustomImageFetcher
import com.archko.reader.pdf.cache.ImageCache
import com.archko.reader.pdf.component.Size
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.APage
import com.archko.reader.pdf.entity.Hyperlink
import com.archko.reader.pdf.entity.Item
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.Page
import java.awt.image.BufferedImage
import java.io.File

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

        cacheCoverIfNeeded()
    }

    /**
     * 检查并缓存封面图片
     */
    private fun cacheCoverIfNeeded() {
        try {
            if (null != ImageCache.acquirePage(file.absolutePath)) {
                return
            }
            val bitmap = renderCoverPage()

            CustomImageFetcher.cacheBitmap(bitmap, file.absolutePath)
        } catch (e: Exception) {
            println("缓存封面失败: ${e.message}")
        }
    }

    /**
     * 渲染封面页面，根据高宽比进行特殊处理
     */
    private fun renderCoverPage(): ImageBitmap {
        val originalSize = originalPageSizes[0]
        val pWidth = originalSize.width
        val pHeight = originalSize.height

        // 目标尺寸
        val targetWidth = 160
        val targetHeight = 200

        // 检查是否为极端长宽比的图片（某边大于8000）
        return if (pWidth > 8000 || pHeight > 8000) {
            // 对于极端长宽比，截取前面部分，避免过度缩放
            val scale = if (pWidth > pHeight) {
                targetWidth.toFloat() / targetWidth  // 保持原始比例，截取前面部分
            } else {
                targetHeight.toFloat() / targetHeight
            }

            // 截取区域：从左上角开始截取目标尺寸的区域
            val cropWidth = minOf(pWidth, (targetWidth / scale).toInt())
            val cropHeight = minOf(pHeight, (targetHeight / scale).toInt())
            
            println("large.tiff crop: ${cropWidth}x${cropHeight} from ${pWidth}x${pHeight}")
            
            val bitmap = tiffLoader!!.decodeRegionToBitmap(
                0, 0, cropWidth, cropHeight, scale
            )
            
            bitmap?.toComposeImageBitmap() ?: CustomImageFetcher.createWhiteBitmap(targetWidth, targetHeight)
        } else if (pWidth > pHeight) {
            // 对于宽大于高的页面，按最大比例缩放后截取
            val scale = maxOf(targetWidth.toFloat() / pWidth, targetHeight.toFloat() / pHeight)

            println("wide.tiff scale: $scale")
            
            val bitmap = tiffLoader!!.decodeRegionToBitmap(
                0, 0, pWidth, pHeight, scale
            )
            
            bitmap?.toComposeImageBitmap() ?: CustomImageFetcher.createWhiteBitmap(targetWidth, targetHeight)
        } else {
            // 原始逻辑处理其他情况
            val xscale = targetWidth.toFloat() / pWidth
            val yscale = targetHeight.toFloat() / pHeight

            // 使用最大比例以确保填充整个目标区域
            val scale = maxOf(xscale, yscale)

            println("normal.tiff scale: $scale")
            
            val bitmap = tiffLoader!!.decodeRegionToBitmap(
                0, 0, pWidth, pHeight, scale
            )
            
            bitmap?.toComposeImageBitmap() ?: CustomImageFetcher.createWhiteBitmap(targetWidth, targetHeight)
        }
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
            //精度的问题,jni那边不支持小数进位四舍五入,所以这里的高宽要修正一下.
            val bitmap = tiffLoader!!.decodeRegionToBitmap(
                patchX,
                patchY,
                pageWidth,
                pageHeight,
                scale,
            )

            if (null != bitmap) {
                return bitmap.toComposeImageBitmap()
            } else {
                return CustomImageFetcher.createWhiteBitmap(outWidth, outHeight)
            }
        } catch (e: Exception) {
            println("renderPageRegion error for file ${file.absolutePath}: $e")
            CustomImageFetcher.createWhiteBitmap(outWidth, outHeight)
        }
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

            val bitmap = tiffLoader!!.decodeRegionToBitmap(
                0,
                0,
                originalSize.width,
                originalSize.height,
                scale,
            )

            return bitmap?.toComposeImageBitmap() ?: CustomImageFetcher.createWhiteBitmap(outWidth, outHeight)
        } catch (e: Exception) {
            println("renderPage error: $e")
            return CustomImageFetcher.createWhiteBitmap(outWidth, outHeight)
        }
    }

    override fun close() {
        tiffLoader?.close()

        ImageCache.clear()
    }
}