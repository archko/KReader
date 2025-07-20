package com.archko.reader.pdf.subsampling

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.cache.BitmapPool
import com.archko.reader.pdf.component.Size
import com.archko.reader.pdf.entity.Item
import com.archko.reader.pdf.subsampling.internal.ImageDecoder
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
        if (pageSizes.isNotEmpty()) {
            // 找到所有页面中的最大宽度
            val maxPageWidth = pageSizes.maxOf { it.width }
            var totalHeight = 0
            
            for (page in pageSizes) {
                // 使用最大宽度来计算缩放比例，确保所有页面都能完整显示
                val zoom = 1f * maxPageWidth / page.width
                val h = (page.height * zoom).toInt()
                totalHeight += h
            }
            
            imageSize = IntSize(maxPageWidth, totalHeight)
            println("PdfDecoder.caculateSize: maxPageWidth=$maxPageWidth, totalHeight=$totalHeight, pageCount=${pageSizes.size}")
        }
    }

    public fun getSize(viewportSize: IntSize): IntSize {
        if ((imageSize == IntSize.Zero || viewSize != viewportSize)
            && viewportSize.width > 0 && viewportSize.height > 0
        ) {
            viewSize = viewportSize
            if (pageSizes.isNotEmpty()) {
                // 找到所有页面中的最大宽度
                val maxPageWidth = pageSizes.maxOf { it.width }
                var totalHeight = 0
                
                for (page in pageSizes) {
                    // 使用最大宽度来计算缩放比例，确保所有页面都能完整显示
                    val zoom = 1f * maxPageWidth / page.width
                    val h = (page.height * zoom).toInt()
                    totalHeight += h
                }
                
                imageSize = IntSize(maxPageWidth, totalHeight)
                println("PdfDecoder.getSize: maxPageWidth=$maxPageWidth, totalHeight=$totalHeight, pageCount=${pageSizes.size}")
            }
        }
        return imageSize
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
        region: androidx.compose.ui.geometry.Rect,
        index: Int,
        scale: Float,
        viewSize: IntSize,
        pageWidth: Int,
        pageHeight: Int
    ): ImageBitmap {
        // 计算tile在页面中的实际位置（region已经是相对于页面的坐标）
        val tileX = region.left.toInt()
        val tileY = region.top.toInt()
        val tileWidth = region.width.toInt()
        val tileHeight = region.height.toInt()
        
        println("renderPageRegion:index:$index, scale:$scale, viewSize:$viewSize, tile:$tileX-$tileY-$tileWidth-$tileHeight, bounds:$region")

        val bitmap: Bitmap = BitmapPool.acquire(tileWidth, tileHeight)
        val ctm = Matrix(scale)
        val dev = AndroidDrawDevice(bitmap, tileX, tileY, 0, 0, tileWidth, tileHeight)

        val page = document.loadPage(index)
        page.run(dev, ctm, null)
        page.destroy()

        dev.close()
        dev.destroy()

        return (bitmap.asImageBitmap())
    }
}