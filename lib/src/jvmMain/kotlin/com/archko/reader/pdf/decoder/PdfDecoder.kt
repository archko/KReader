package com.archko.reader.pdf.decoder

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.component.Size
import com.archko.reader.pdf.entity.Item
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.util.loadOutlineItems
import com.artifex.mupdf.fitz.ColorSpace
import com.artifex.mupdf.fitz.Cookie
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.DrawDevice
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.Pixmap
import com.artifex.mupdf.fitz.Rect
import java.awt.image.BufferedImage
import java.io.File

/**
 * @author: archko 2025/4/11 :11:26
 */
public class PdfDecoder(file: File) : ImageDecoder {

    private val document: Document = Document.openDocument(file.absolutePath)
    public override var pageCount: Int = document.countPages()

    // 私有变量存储原始页面尺寸
    public override var originalPageSizes: List<Size> = listOf()

    // 对外提供的缩放后页面尺寸
    public override var pageSizes: List<Size> = listOf()
        get() = field
        set(value) {
            field = value
        }

    public override var outlineItems: List<Item>? = listOf()
        get() = field
        set(value) {
            field = value
        }

    public override var imageSize: IntSize = IntSize.Zero
        get() = field
        set(value) {
            field = value
        }

    public var viewSize: IntSize = IntSize.Zero

    init {
        val fontSize = 54f
        document.layout(1280f, 2160f, fontSize)
        pageCount = document.countPages()
        originalPageSizes = prepareSizes()
        outlineItems = prepareOutlines()
    }

    /*override fun decodeRegion(
        region: IntRect,
        tile: ImageTile
    ): ImageBitmap? {
        val bitmap = renderPageRegion(region, tile)
        return bitmap
    }*/

    override fun size(viewportSize: IntSize): IntSize {
        if ((imageSize == IntSize.Zero || viewSize != viewportSize)
            && viewportSize.width > 0 && viewportSize.height > 0
        ) {
            viewSize = viewportSize
            caculateSize(viewportSize)
        }
        return imageSize
    }

    private fun caculateSize(viewportSize: IntSize) {
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

                //println("PdfDecoder.caculateSize: page $i - original: ${originalPage.width}x${originalPage.height}, scale: $scale, scaled: ${scaledWidth}x${scaledHeight}")
            }

            // 更新对外提供的页面尺寸
            pageSizes = scaledPageSizes

            imageSize = IntSize(documentWidth, totalHeight)
            println("PdfDecoder.caculateSize: documentWidth=$documentWidth, totalHeight=$totalHeight, pageCount=${originalPageSizes.size}")
        }
    }

    public fun getSize(viewportSize: IntSize): IntSize {
        if ((imageSize == IntSize.Zero || viewSize != viewportSize)
            && viewportSize.width > 0 && viewportSize.height > 0
        ) {
            viewSize = viewportSize
            caculateSize(viewportSize)
        }
        return imageSize
    }

    /**
     * 获取原始页面尺寸
     */
    public fun getOriginalPageSize(index: Int): Size {
        return originalPageSizes[index]
    }

    override fun close() {
        document.destroy()
    }

    private fun prepareSizes(): List<Size> {
        val list = mutableListOf<Size>()
        var totalHeight = 0
        for (i in 0 until pageCount) {
            val page = document.loadPage(i)
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
            list.add(size)
        }
        return list
    }

    private fun prepareOutlines(): List<Item> {
        return document.loadOutlineItems()
    }

    public fun renderPage(
        index: Int,
        viewWidth: Int,
        viewHeight: Int
    ): ImageBitmap {
        val page = document.loadPage(index)
        val bounds = page.bounds
        val scale: Float
        if (viewWidth > 0) {
            scale = (1f * viewWidth / (bounds.x1 - bounds.x0))
        } else {
            return (ImageBitmap(viewWidth, viewHeight, ImageBitmapConfig.Rgb565))
        }
        println("renderPage:index:$index, scale:$scale, $viewWidth-$viewHeight, bounds:${page.bounds}")
        val ctm: Matrix = Matrix.Scale(scale)

        /* Render page to an RGB pixmap without transparency. */
        val bmp: ImageBitmap?
        try {
            val bbox: Rect = Rect(bounds).transform(ctm)
            val pixmap = Pixmap(ColorSpace.DeviceBGR, bbox, true)
            pixmap.clear(255)
            val dev = DrawDevice(pixmap)
            page.run(dev, ctm, Cookie())
            dev.close()
            dev.destroy()

            val pixmapWidth = pixmap.width
            val pixmapHeight = pixmap.height
            val image = BufferedImage(pixmapWidth, pixmapHeight, BufferedImage.TYPE_3BYTE_BGR)
            image.setRGB(0, 0, pixmapWidth, pixmapHeight, pixmap.pixels, 0, pixmapWidth)
            bmp = image.toComposeImageBitmap()
            return (bmp)
        } catch (e: Exception) {
            System.err.println(("Error loading page " + (index + 1)) + ": " + e)
        }

        return (ImageBitmap(viewWidth, viewHeight, ImageBitmapConfig.Rgb565))
    }

    public override fun renderPageRegion(
        region: androidx.compose.ui.geometry.Rect,
        index: Int,
        scale: Float,
        viewSize: IntSize,
        outWidth: Int,
        outHeight: Int
    ): ImageBitmap {
        val patchX = region.left.toInt()
        val patchY = region.top.toInt()
        println("renderPageRegion:index:$index scale:$scale, w-h:$outWidth-$outHeight, offset:$patchX-$patchY, bounds:$region")

        try {
            val bmp: ImageBitmap?
            try {
                // 创建子页面大小的 pixmap
                val bbox = Rect(
                    0f,
                    0f,
                    outWidth.toFloat(),
                    outHeight.toFloat()
                )
                val pixmap = Pixmap(ColorSpace.DeviceBGR, bbox, true)
                pixmap.clear(255)

                // 创建变换矩阵，包含缩放和偏移
                val ctm = Matrix()
                ctm.scale(scale, scale)
                // 添加偏移，使渲染区域正确
                // patchX 和 patchY 是基于缩放后尺寸的，需要转换为原始PDF坐标
                ctm.translate(-patchX.toFloat(), -patchY.toFloat())

                val dev = DrawDevice(pixmap)
                val page = document.loadPage(index)
                page.run(dev, ctm, null)
                dev.close()
                dev.destroy()

                val pixmapWidth = pixmap.width
                val pixmapHeight = pixmap.height
                val image = BufferedImage(pixmapWidth, pixmapHeight, BufferedImage.TYPE_3BYTE_BGR)
                image.setRGB(0, 0, pixmapWidth, pixmapHeight, pixmap.pixels, 0, pixmapWidth)
                bmp = image.toComposeImageBitmap()
                return (bmp)
            } catch (e: Exception) {
                System.err.println(("Error loading page region " + (index + 1)) + ": " + e)
            }

            return ImageBitmap(outWidth, outHeight, ImageBitmapConfig.Rgb565)
        } catch (e: Exception) {
            println("renderPageRegion error: $e")
            // 返回一个空的位图，避免崩溃
            return ImageBitmap(outWidth, outHeight, ImageBitmapConfig.Rgb565)
        }
    }
}