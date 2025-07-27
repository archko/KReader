package com.archko.reader.pdf.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.asImageBitmap
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
public actual class LocalPdfState(private val document: Document) {
    public actual var pageCount: Int = document.countPages()
    public actual var pageSizes: List<Size> = listOf()
        get() = field
        set(value) {
            field = value
        }

    public actual var outlineItems: List<Item>? = listOf()
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
        document = try {
            // 检查文件是否存在
            if (!file.exists()) {
                throw IllegalArgumentException("文档文件不存在: ${file.absolutePath}")
            }
            
            // 检查文件是否可读
            if (!file.canRead()) {
                throw SecurityException("无法读取文档文件: ${file.absolutePath}")
            }
            
            Document.openDocument(file.absolutePath)
        } catch (e: Exception) {
            throw RuntimeException("无法打开文档: ${file.absolutePath}, 错误: ${e.message}", e)
        }
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

    public actual fun renderPage(
        index: Int,
        viewWidth: Int,
        viewHeight: Int
    ): ImageBitmap {
        if (viewWidth <= 0) {
            return (ImageBitmap(1024, viewHeight, ImageBitmapConfig.Rgb565))
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

    public actual fun renderPageRegion(
        index: Int,
        viewWidth: Int,
        viewHeight: Int,
        xOffset: Int,
        yOffset: Int,
        zoom: Float
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

    public actual fun close() {
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