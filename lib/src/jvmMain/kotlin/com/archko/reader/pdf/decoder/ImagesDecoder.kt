package com.archko.reader.pdf.decoder

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntSize
import com.archko.reader.image.HeifLoader
import com.archko.reader.pdf.cache.ImageCache
import com.archko.reader.pdf.cache.CustomImageFetcher
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

    // 缓存HeifLoader，避免重复创建，限制数量为10个
    private val heifLoaders = mutableMapOf<Int, HeifLoader>()

    // 标记每个文件是否为HEIF格式
    private val isHeifFile = mutableListOf<Boolean>()

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

        // 检测文件类型
        files.forEach { file ->
            val isHeif = isHeifFormat(file)
            isHeifFile.add(isHeif)
        }

        // 初始化原始页面尺寸
        originalPageSizes = prepareSizes()
        cacheCoverIfNeeded()
    }

    /**
     * 检查并缓存封面图片
     */
    private fun cacheCoverIfNeeded() {
        if (files.size > 1) {
            return
        }
        val path = files[0].absolutePath
        try {
            if (null != ImageCache.acquirePage(path)) {
                return
            }
            val bitmap = renderCoverPage(0)

            CustomImageFetcher.cacheBitmap(bitmap, path)
        } catch (e: Exception) {
            println("缓存封面失败: ${e.message}")
        }
    }

    /**
     * 渲染封面页面，根据高宽比进行特殊处理
     */
    private fun renderCoverPage(index: Int): ImageBitmap {
        if (index >= files.size) {
            return ImageBitmap(160, 200, ImageBitmapConfig.Rgb565)
        }

        val originalSize = originalPageSizes[index]
        val pWidth = originalSize.width.toFloat()
        val pHeight = originalSize.height.toFloat()

        // 目标尺寸
        val targetWidth = 160
        val targetHeight = 200

        // 检查是否为极端长宽比的图片（某边大于8000）
        return if (pWidth > 8000 || pHeight > 8000) {
            // 对于极端长宽比，先缩放到目标尺寸之一，再截取
            val scale = if (pWidth > pHeight) {
                targetWidth.toFloat() / pWidth
            } else {
                targetHeight.toFloat() / pHeight
            }

            val cropWidth = maxOf(targetWidth, (pWidth * scale).toInt())
            val cropHeight = maxOf(targetHeight, (pHeight * scale).toInt())
            println("large.width-height:$cropWidth-$cropHeight")
            renderImageAtScale(index, scale, cropWidth, cropHeight)
        } else if (pWidth > pHeight) {
            // 对于宽大于高的页面，按最大比例缩放后截取
            val scale = maxOf(targetWidth.toFloat() / pWidth, targetHeight.toFloat() / pHeight)

            val cropWidth = maxOf(targetWidth, (pWidth * scale).toInt())
            val cropHeight = maxOf(targetHeight, (pHeight * scale).toInt())

            println("wide.width-height:$cropWidth-$cropHeight")
            renderImageAtScale(index, scale, cropWidth, cropHeight)
        } else {
            // 原始逻辑处理其他情况
            val xscale = targetWidth.toFloat() / pWidth
            val yscale = targetHeight.toFloat() / pHeight

            // 使用最大比例以确保填充整个目标区域
            val scale = maxOf(xscale, yscale)

            val cropWidth = maxOf(targetWidth, (pWidth * scale).toInt())
            val cropHeight = maxOf(targetHeight, (pHeight * scale).toInt())

            println("width-height:$cropWidth-$cropHeight")
            renderImageAtScale(index, scale, cropWidth, cropHeight)
        }
    }

    /**
     * 以指定缩放比例渲染图片
     */
    private fun renderImageAtScale(index: Int, scale: Float, outWidth: Int, outHeight: Int): ImageBitmap {
        return try {
            if (isHeifFile[index]) {
                // 使用HeifLoader解码HEIF图片
                val heifLoader = getHeifLoader(index)
                if (heifLoader != null) {
                    val originalSize = originalPageSizes[index]
                    val bitmap = heifLoader.decodeRegionToBitmap(
                        0,
                        0,
                        originalSize.width,
                        originalSize.height,
                        scale
                    )

                    bitmap?.toComposeImageBitmap()
                        ?: ImageBitmap(outWidth, outHeight, ImageBitmapConfig.Rgb565)
                } else {
                    ImageBitmap(outWidth, outHeight, ImageBitmapConfig.Rgb565)
                }
            } else {
                // 使用MuPDF解码其他格式图片
                val document = getRegionDecoder(index)
                if (document != null) {
                    val page = document.loadPage(0) // 图片文件只有一页

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
                    val ctm = com.artifex.mupdf.fitz.Matrix()
                    ctm.scale(scale, scale)

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
            }
        } catch (e: Exception) {
            println("renderImageAtScale error for file ${files[index].absolutePath}: $e")
            ImageBitmap(outWidth, outHeight, ImageBitmapConfig.Rgb565)
        }
    }

    /**
     * 检测文件是否为HEIF格式
     */
    private fun isHeifFormat(file: File): Boolean {
        val extension = file.extension.lowercase()
        return extension == "heic" || extension == "heif"
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
            val size = if (isHeifFile[i]) {
                // 使用HeifLoader获取尺寸
                val heifLoader = HeifLoader()
                heifLoader.openHeif(file.absolutePath)
                val heifInfo = heifLoader.heifInfo
                heifLoader.close()

                if (heifInfo != null) {
                    Size(
                        heifInfo.width,
                        heifInfo.height,
                        i,
                        scale = 1.0f,
                        totalHeight,
                    )
                } else {
                    // 如果无法获取信息，使用默认尺寸
                    Size(100, 100, i, scale = 1.0f, totalHeight)
                }
            } else {
                // 使用MuPDF获取尺寸
                val doc = Document.openDocument(file.absolutePath)
                val page = doc.loadPage(0) // 图片文件只有一页，索引为0
                val bounds = page.bounds
                val pageSize = Size(
                    bounds.x1.toInt() - bounds.x0.toInt(),
                    bounds.y1.toInt() - bounds.y0.toInt(),
                    i,
                    scale = 1.0f,
                    totalHeight,
                )
                page.destroy()
                doc.destroy()
                pageSize
            }

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
        if (index >= files.size) {
            return ImageBitmap(outWidth, outHeight, ImageBitmapConfig.Rgb565)
        }

        return try {
            val patchX = region.left.toInt()
            val patchY = region.top.toInt()
            println("ImagesDecoder.renderPageRegion:index:$index scale:$scale, w-h:$outWidth-$outHeight, offset:$patchX-$patchY, bounds:$region")

            if (isHeifFile[index]) {
                // 使用HeifLoader解码HEIF图片
                val heifLoader = getHeifLoader(index)
                if (heifLoader != null) {
                    val pageWidth = (region.width / scale).toInt()
                    val pageHeight = (region.height / scale).toInt()
                    val bitmap = heifLoader.decodeRegionToBitmap(
                        (patchX / scale).toInt(),
                        (patchY / scale).toInt(),
                        pageWidth,
                        pageHeight,
                        scale
                    )

                    bitmap?.toComposeImageBitmap()
                        ?: ImageBitmap(outWidth, outHeight, ImageBitmapConfig.Rgb565)
                } else {
                    ImageBitmap(outWidth, outHeight, ImageBitmapConfig.Rgb565)
                }
            } else {
                // 使用MuPDF解码其他格式图片
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

            if (isHeifFile[index]) {
                // 使用HeifLoader解码HEIF图片
                val heifLoader = getHeifLoader(index)
                if (heifLoader != null) {
                    val originalSize = originalPageSizes[index]
                    val bitmap = heifLoader.decodeRegionToBitmap(
                        0,
                        0,
                        originalSize.width,
                        originalSize.height,
                        scale
                    )

                    bitmap?.toComposeImageBitmap()
                        ?: ImageBitmap(outWidth, outHeight, ImageBitmapConfig.Rgb565)
                } else {
                    ImageBitmap(outWidth, outHeight, ImageBitmapConfig.Rgb565)
                }
            } else {
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

    /**
     * 获取或创建HeifLoader，限制缓存数量为10个
     */
    private fun getHeifLoader(index: Int): HeifLoader? {
        if (index >= files.size) return null

        // 如果缓存已满且当前索引不在缓存中，移除最旧的项
        if (heifLoaders.size >= maxRegionDecoders && !heifLoaders.containsKey(index)) {
            val oldestIndex = heifLoaders.keys.first()
            val oldestLoader = heifLoaders.remove(oldestIndex)
            oldestLoader?.close()
            println("Removed heif loader for index $oldestIndex to make room for index $index")
        }

        return heifLoaders.getOrPut(index) {
            val file = files[index]
            val loader = HeifLoader()
            loader.openHeif(file.absolutePath)
            return loader
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

        // 关闭所有HeifLoaders
        heifLoaders.values.forEach { loader ->
            try {
                loader.close()
            } catch (e: Exception) {
                println("Error closing HeifLoader: $e")
            }
        }
        heifLoaders.clear()

        ImageCache.clear()
    }

    override fun getStructuredText(index: Int): Any? {
        return null
    }
} 