package com.archko.reader.pdf.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import com.archko.reader.pdf.cache.BitmapPool
import com.archko.reader.pdf.component.Size
import com.archko.reader.pdf.entity.Item
import com.archko.reader.pdf.util.loadOutlineItems
import com.artifex.mupdf.fitz.Cookie
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.android.AndroidDrawDevice
import java.io.File
import java.io.InputStream
import java.net.URL

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
        val fontSize = 54f;
        document.layout(1280f, 2160f, fontSize)
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

    public actual override fun renderPage(index: Int, viewWidth: Int, viewHeight: Int): Painter {
        if (viewWidth <= 0) {
            return BitmapPainter(ImageBitmap(viewWidth, viewHeight, ImageBitmapConfig.Rgb565))
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

        return BitmapPainter(bitmap.asImageBitmap())
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