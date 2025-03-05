package com.archko.reader.pdf.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.archko.reader.pdf.component.Size
import com.archko.reader.pdf.entity.Item
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
import java.io.InputStream
import java.net.URL

internal const val SCALE = 1.0f

@Stable
public actual class LocalPdfState(private val document: Document) : PdfState {
    public actual override var pageCount: Int = document.countPages()
    override var pageSizes: List<Size> = listOf()
        get() = field
        set(value) {
            field = value
        }

    override var outlineItems: List<Item>? = listOf()
        get() = field
        set(value) {
            field = value
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

    /*public constructor(inputStream: InputStream) : this(
        document = Document.openDocument(inputStream).apply {
            setInputStream(inputStream, null)
        }
    ) {
        pageSizes = prepareSizes()
    }*/

    public actual constructor(file: File) : this(
        document = Document.openDocument(file.absolutePath)
    ) {
        val fontSize = 26f;
        document.layout(1280f, 1280f, fontSize)
        pageCount = document.countPages()
        pageSizes = prepareSizes()
        outlineItems = prepareOutlines()
    }

    /*public constructor(url: URL) : this(
        document = Document().apply {
            setUrl(url)
        }
    ) {
        pageSizes = prepareSizes()
    }*/

    public actual override fun renderPage(
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

    public actual override fun renderPageRegion(
        index: Int,
        viewWidth: Int,
        viewHeight: Int,
        xOffset: Int,
        yOffset: Int
    ): ImageBitmap {
        val page = document.loadPage(index)
        val bounds = page.bounds
        val scale: Float
        if (viewWidth > 0) {
            // 计算原始页面的缩放比例
            scale = (1f * viewWidth * 2 / (bounds.x1 - bounds.x0))
        } else {
            return (ImageBitmap(viewWidth, viewHeight, ImageBitmapConfig.Rgb565))
        }
        
        val originalHeight = ((bounds.y1 - bounds.y0) * scale).toInt()
        val subPageHeight = originalHeight / 2
        
        println("renderPageRegion:index:$index, scale:$scale, w-h:$viewWidth-$viewHeight, offset:$xOffset-$yOffset, bounds:${page.bounds}")
        
        // 创建变换矩阵，包含缩放和偏移
        val ctm = Matrix()
        ctm.scale(scale, scale)
        // 添加偏移，使渲染区域正确，需要考虑缩放比例
        ctm.translate(-(xOffset / scale).toFloat(), -(yOffset / scale).toFloat())

        /* Render page to an RGB pixmap without transparency. */
        val bmp: ImageBitmap?
        try {
            // 创建子页面大小的 pixmap
            val subPageBbox = Rect(
                0f,
                0f,
                viewWidth.toFloat(),
                viewHeight.toFloat()
            )
            val pixmap = Pixmap(ColorSpace.DeviceBGR, subPageBbox, true)
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
            System.err.println(("Error loading page region " + (index + 1)) + ": " + e)
        }

        return (ImageBitmap(viewWidth, viewHeight, ImageBitmapConfig.Rgb565))
    }

    override fun close() {
        document.destroy()
    }
}

/**
 * Remembers a [LocalPdfState] for the given [inputStream].
 *
 * @param inputStream
 * @return [LocalPdfState]
 */
@Composable
public fun rememberLocalPdfState(inputStream: InputStream): LocalPdfState {
    return remember { LocalPdfState(File("")) }
}

/**
 * Remembers a [LocalPdfState] for the given [url].
 *
 * @param url
 * @return [LocalPdfState]
 */
@Composable
public actual fun rememberLocalPdfState(url: URL): LocalPdfState {
    return remember { LocalPdfState(File("")) }
}

/**
 * Remembers a [LocalPdfState] for the given [file].
 *
 * @param file
 * @return [LocalPdfState]
 */
@Composable
public actual fun rememberLocalPdfState(file: File): LocalPdfState {
    return remember { LocalPdfState(file) }
}