package com.archko.reader.pdf.subsampling

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.cache.BitmapPool
import com.archko.reader.pdf.component.Size
import com.archko.reader.pdf.entity.Item
import com.archko.reader.pdf.util.loadOutlineItems
import com.artifex.mupdf.fitz.Cookie
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.android.AndroidDrawDevice
import java.io.File

/**
 * @author: archko 2025/4/11 :11:26
 */
public class PdfDecoder(file: File) : ImageDecoder {

    private val document: Document = Document.openDocument(file.absolutePath)
    public var pageCount: Int = document.countPages()
    public var pageSizes: List<Size> = listOf()
        get() = field
        set(value) {
            field = value
        }

    public var outlineItems: List<Item>? = listOf()
        get() = field
        set(value) {
            field = value
        }

    public var viewSize: IntSize = IntSize.Zero
    public var imageSize: IntSize = IntSize.Zero
        get() = field
        set(value) {
            field = value
        }

    init {
        val fontSize = 54f
        document.layout(1280f, 2160f, fontSize)
        pageCount = document.countPages()
        pageSizes = prepareSizes()
        outlineItems = prepareOutlines()
    }

    override fun decodeRegion(
        region: IntRect,
        index: Int
    ): ImageBitmap? {
        val bitmap = renderPageRegion(0, region.width, region.height, region.left, region.top)
        return bitmap
    }

    override fun size(viewportSize: IntSize): IntSize {
        if (imageSize == IntSize.Zero
            && viewSize != viewportSize
            && viewportSize.width > 0 && viewportSize.height > 0
        ) {
            viewSize = viewportSize
            caculateSize(viewportSize)
        }
        return imageSize
    }

    private fun caculateSize(viewportSize: IntSize) {
        if (pageSizes.isNotEmpty()) {
            var width = viewportSize.width
            var height = 0
            for (page in pageSizes) {
                val zoom = 1f * viewportSize.width / page.width
                page.zoom = zoom
                val h = (page.height * zoom).toInt()
                height += h
                break
            }
            val size = pageSizes[0]
            width = size.width
            height = size.height
            imageSize = IntSize(width, height)
        }
    }

    override fun close() {
        document.destroy()
    }

    private fun prepareSizes(): List<Size> {
        val list = mutableListOf<Size>()
        for (i in 0 until pageCount) {
            val page = document.loadPage(i)
            val bounds = page.bounds
            val size = Size(
                bounds.x1.toInt() - bounds.x0.toInt(),
                bounds.y1.toInt() - bounds.y0.toInt(),
                i
            )
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
        if (viewWidth <= 0) {
            return (ImageBitmap(viewWidth, viewHeight, ImageBitmapConfig.Rgb565))
        }
        val page = document.loadPage(index)
        val bounds = page.bounds
        val scale = (1f * viewWidth / (bounds.x1 - bounds.x0))
        var w = viewWidth
        var h = ((bounds.y1 - bounds.y0) * scale).toInt()

        println("renderPage:index:$index, scale:$scale, $viewWidth-$viewHeight, bounds:${page.bounds}")
        val ctm = Matrix()
        ctm.scale(scale, scale)
        val bitmap = BitmapPool.acquire(w, h)
        val dev =
            AndroidDrawDevice(bitmap, 0, 0, 0, 0, bitmap.getWidth(), bitmap.getHeight())
        page.run(dev, ctm, null as Cookie?)
        dev.close()
        dev.destroy()

        return (bitmap.asImageBitmap())
    }

    public fun renderPageRegion(
        index: Int,
        viewWidth: Int,
        viewHeight: Int,
        xOffset: Int,
        yOffset: Int
    ): ImageBitmap {
        if (viewWidth <= 0) {
            return (ImageBitmap(viewWidth, viewHeight, ImageBitmapConfig.Rgb565))
        }
        val page = document.loadPage(index)
        val bounds = page.bounds

        // 计算原始页面的缩放比例
        val originalScale = (1f * viewWidth * 2 / (bounds.x1 - bounds.x0)) // 因为viewWidth是原页面的一半
        val originalHeight = ((bounds.y1 - bounds.y0) * originalScale).toInt()

        // 计算子页面的实际尺寸
        val subPageWidth = viewWidth
        val subPageHeight = originalHeight / 2 // 因为viewHeight是原页面的一半

        println("renderPageRegion:index:$index, scale:$originalScale, w-h:$viewWidth-$viewHeight, offset:$xOffset-$yOffset, bounds:${page.bounds}")

        val ctm = Matrix()
        ctm.scale(originalScale, originalScale)
        val bitmap = BitmapPool.acquire(subPageWidth, subPageHeight)
        val dev =
            AndroidDrawDevice(bitmap, xOffset, yOffset, 0, 0, bitmap.getWidth(), bitmap.getHeight())
        page.run(dev, ctm, null as Cookie?)
        dev.close()
        dev.destroy()

        return (bitmap.asImageBitmap())
    }
}