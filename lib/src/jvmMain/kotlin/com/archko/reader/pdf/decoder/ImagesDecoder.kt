package com.archko.reader.pdf.decoder

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.cache.ImageCache
import com.archko.reader.pdf.component.Size
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.APage
import com.archko.reader.pdf.entity.Hyperlink
import com.archko.reader.pdf.entity.Item
import com.artifex.mupdf.fitz.Document
import java.io.File

/**
 * 图片文件解码器，支持多个图片文件
 * @author: archko 2025/1/20
 */
public class ImagesDecoder(private val files: List<File>) : ImageDecoder {

    public override var pageCount: Int = files.size

    // 私有变量存储原始页面尺寸
    public override var originalPageSizes: List<Size> = listOf()

    // 对外提供的缩放后页面尺寸
    public override var pageSizes: List<Size> = listOf()

    public override var outlineItems: List<Item>? = emptyList()

    public override var imageSize: IntSize = IntSize.Zero

    public var viewSize: IntSize = IntSize.Zero
    public override val aPageList: MutableList<APage> = ArrayList()

    // 缓存MuPDF Document，避免重复创建，限制数量为10个
    private val regionDecoders = mutableMapOf<Int, Document>()
    private val maxRegionDecoders = 10

    init {
        if (files.isEmpty()) {
            throw IllegalArgumentException("图片文件列表不能为空")
        }

        // 检查所有文件是否存在且可读
        files.forEach { file ->
            if (!file.exists()) {
                throw IllegalArgumentException("图片文件不存在: ${file.absolutePath}")
            }
            if (!file.canRead()) {
                throw SecurityException("无法读取图片文件: ${file.absolutePath}")
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

    private fun prepareSizes(): List<Size> {
        val list = mutableListOf<Size>()
        var totalHeight = 0

        for (i in files.indices) {
            val file = files[i]
            val doc = Document.openDocument(file.absolutePath)
            val page = doc.loadPage(0) // 图片文件只有一页，索引为0
            val bounds = page.bounds
            val size = Size(
                bounds.x1.toInt() - bounds.x0.toInt(),
                bounds.y1.toInt() - bounds.y0.toInt(),
                i,
                scale = 1.0f,
                totalHeight,
            )
            totalHeight += size.height
            page.destroy()
            doc.destroy()
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
        if (index >= files.size) {
            return ImageBitmap(outWidth, outHeight, ImageBitmapConfig.Rgb565)
        }

        return try {
            val patchX = region.left.toInt()
            val patchY = region.top.toInt()
            println("ImagesDecoder.renderPageRegion:index:$index scale:$scale, w-h:$outWidth-$outHeight, offset:$patchX-$patchY, bounds:$region")

            val document = getRegionDecoder(index)
            if (document != null) {
                val page = document.loadPage(0) // 图片文件只有一页

                val ctm = com.artifex.mupdf.fitz.Matrix(scale)

                val bbox = com.artifex.mupdf.fitz.Rect(
                    0f,
                    0f,
                    outWidth.toFloat(),
                    outHeight.toFloat()
                )
                val pixmap = com.artifex.mupdf.fitz.Pixmap(
                    com.artifex.mupdf.fitz.ColorSpace.DeviceBGR,
                    bbox,
                    true
                )
                pixmap.clear(255)
                com.artifex.mupdf.fitz.Context.disableICC()

                val dev = com.artifex.mupdf.fitz.DrawDevice(pixmap)

                // 添加偏移，使渲染区域正确
                ctm.translate(-(patchX / scale), -(patchY / scale))

                page.run(dev, ctm, null)

                dev.close()
                dev.destroy()
                page.destroy()

                // Convert pixmap to BufferedImage and then to ImageBitmap
                val pixmapWidth = pixmap.width
                val pixmapHeight = pixmap.height
                val image = java.awt.image.BufferedImage(
                    pixmapWidth,
                    pixmapHeight,
                    java.awt.image.BufferedImage.TYPE_3BYTE_BGR
                )
                image.setRGB(0, 0, pixmapWidth, pixmapHeight, pixmap.pixels, 0, pixmapWidth)

                pixmap.destroy()

                image.toComposeImageBitmap()
            } else {
                // 如果无法创建document，返回默认图片
                ImageBitmap(outWidth, outHeight, ImageBitmapConfig.Rgb565)
            }
        } catch (e: Exception) {
            println("renderPageRegion error for file ${files[index].absolutePath}: $e")
            ImageBitmap(outWidth, outHeight, ImageBitmapConfig.Rgb565)
        }
    }

    override fun renderPage(
        aPage: APage,
        viewSize: IntSize,
        outWidth: Int,
        outHeight: Int,
        crop: Boolean
    ): ImageBitmap {
        return try {
            val index = aPage.index
            val scale = if (aPage.width > 0) {
                outWidth.toFloat() / aPage.getWidth(crop)
            } else {
                1f
            }

            // 使用MuPDF进行整页解码，参考PdfDecoder的实现
            val document = getRegionDecoder(index)
            if (document != null) {
                val page = document.loadPage(0) // 图片文件只有一页

                val ctm = com.artifex.mupdf.fitz.Matrix(scale)

                val bbox = com.artifex.mupdf.fitz.Rect(
                    0f,
                    0f,
                    outWidth.toFloat(),
                    outHeight.toFloat()
                )
                val pixmap = com.artifex.mupdf.fitz.Pixmap(
                    com.artifex.mupdf.fitz.ColorSpace.DeviceBGR,
                    bbox,
                    true
                )
                pixmap.clear(255)
                com.artifex.mupdf.fitz.Context.disableICC()

                val dev = com.artifex.mupdf.fitz.DrawDevice(pixmap)

                page.run(dev, ctm, null)

                dev.close()
                dev.destroy()
                page.destroy()

                // Convert pixmap to BufferedImage and then to ImageBitmap
                val pixmapWidth = pixmap.width
                val pixmapHeight = pixmap.height
                val image = java.awt.image.BufferedImage(
                    pixmapWidth,
                    pixmapHeight,
                    java.awt.image.BufferedImage.TYPE_3BYTE_BGR
                )
                image.setRGB(0, 0, pixmapWidth, pixmapHeight, pixmap.pixels, 0, pixmapWidth)

                pixmap.destroy()

                image.toComposeImageBitmap()
            } else {
                ImageBitmap(outWidth, outHeight, ImageBitmapConfig.Rgb565)
            }
        } catch (e: Exception) {
            println("ImagesDecoder.renderPage error: $e")
            ImageBitmap(outWidth, outHeight, ImageBitmapConfig.Rgb565)
        }
    }

    /**
     * 获取或创建MuPDF Document，限制缓存数量为10个
     */
    private fun getRegionDecoder(index: Int): Document? {
        if (index >= files.size) return null

        // 如果缓存已满且当前索引不在缓存中，移除最旧的项
        if (regionDecoders.size >= maxRegionDecoders && !regionDecoders.containsKey(index)) {
            val oldestIndex = regionDecoders.keys.first()
            val oldestDecoder = regionDecoders.remove(oldestIndex)
            oldestDecoder?.destroy()
            println("Removed region decoder for index $oldestIndex to make room for index $index")
        }

        return regionDecoders.getOrPut(index) {
            val file = files[index]
            return Document.openDocument(file.absolutePath)
        }
    }

    override fun close() {
        // 关闭所有MuPDF documents
        regionDecoders.values.forEach { decoder ->
            try {
                decoder.destroy()
            } catch (e: Exception) {
                println("Error closing MuPDF Document: $e")
            }
        }
        regionDecoders.clear()

        ImageCache.clear()
    }

    override fun getStructuredText(index: Int): Any? {
        return null
    }
} 