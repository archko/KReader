package com.archko.reader.pdf.subsampling

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.cache.BitmapPool
import com.archko.reader.pdf.component.Size
import com.archko.reader.pdf.entity.Item
import com.archko.reader.pdf.subsampling.internal.ImageDecoder
import com.archko.reader.pdf.subsampling.tile.ImageTile
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
    public override var pageCount: Int = document.countPages()
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
        pageSizes = prepareSizes()
        outlineItems = prepareOutlines()
    }

    override fun decodeRegion(
        region: IntRect,
        tile: ImageTile
    ): ImageBitmap? {
        val bitmap = renderPageRegion(region, tile)
        return bitmap
    }

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
        region: IntRect,
        tile: ImageTile
    ): ImageBitmap {
        val cropBound = Rect()
        val scale = tile.scale.scaleX
        val pageW: Int
        val pageH: Int
        val patchX: Int
        val patchY: Int

        //如果页面的缩放为1,那么这时的pageW就是view的宽.
        pageW = (region.width).toInt()
        pageH = (region.height).toInt()

        patchX = ((region.left) + cropBound.left).toInt()
        patchY = ((region.top) + cropBound.top).toInt()
        println("renderPageRegion:index:${tile.index}, scale:${tile.scale}, w-h:$pageW-$pageH, offset:$patchX-$patchY, bounds:${region}")

        val bitmap: Bitmap = BitmapPool.acquire(pageW, pageH)
        val ctm = Matrix(scale)
        val dev = AndroidDrawDevice(bitmap, patchX, patchY, 0, 0, pageW, pageH)

        val page = document.loadPage(tile.index)
        page.run(dev, ctm, null)

        dev.close()
        dev.destroy()

        return (bitmap.asImageBitmap())
    }
}