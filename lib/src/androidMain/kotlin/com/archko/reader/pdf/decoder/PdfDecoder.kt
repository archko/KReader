package com.archko.reader.pdf.decoder

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.cache.BitmapPool
import com.archko.reader.pdf.component.Size
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.Item
import com.archko.reader.pdf.entity.ReflowBean
import com.archko.reader.pdf.util.loadOutlineItems
import com.archko.reader.pdf.decoder.ParseTextMain
import com.artifex.mupdf.fitz.Cookie
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.android.AndroidDrawDevice
import java.io.File

/**
 * @author: archko 2025/4/11 :11:26
 */
public class PdfDecoder(file: File) : ImageDecoder {

    private var document: Document? = null
    public override var pageCount: Int = 0

    // 私有变量存储原始页面尺寸
    public override var originalPageSizes: List<Size> = listOf()

    // 对外提供的缩放后页面尺寸
    public override var pageSizes: List<Size> = listOf()

    public override var outlineItems: List<Item>? = listOf()

    public override var imageSize: IntSize = IntSize.Zero

    public var viewSize: IntSize = IntSize.Zero

    // 密码相关状态
    public var needsPassword: Boolean = false
    public var isAuthenticated: Boolean = false

    init {
        // 检查文件是否存在
        if (!file.exists()) {
            throw IllegalArgumentException("文档文件不存在: ${file.absolutePath}")
        }

        // 检查文件是否可读
        if (!file.canRead()) {
            throw SecurityException("无法读取文档文件: ${file.absolutePath}")
        }

        try {
            document = Document.openDocument(file.absolutePath)
            // 检查是否需要密码
            needsPassword = document?.needsPassword() == true
            if (!needsPassword) {
                initializeDocument()
            }
        } catch (e: Exception) {
            throw RuntimeException("无法打开文档: ${file.absolutePath}, 错误: ${e.message}", e)
        }
    }

    /**
     * 使用密码认证文档
     * @param password 密码
     * @return 认证是否成功
     */
    public fun authenticatePassword(password: String): Boolean {
        return try {
            val success = document?.authenticatePassword(password) == true
            if (success) {
                isAuthenticated = true
                needsPassword = false
                initializeDocument()
            }
            success
        } catch (e: Exception) {
            println("密码认证失败: ${e.message}")
            false
        }
    }

    /**
     * 初始化文档（在认证成功后调用）
     */
    private fun initializeDocument() {
        document?.let { doc ->
            val fontSize = 54f
            doc.layout(1280f, 2160f, fontSize)
            pageCount = doc.countPages()
            originalPageSizes = prepareSizes()
            outlineItems = prepareOutlines()
        }
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
            && document != null && isAuthenticated
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

    /**
     * 获取原始页面尺寸
     */
    public fun getOriginalPageSize(index: Int): Size {
        return originalPageSizes[index]
    }

    override fun close() {
        document?.destroy()
    }

    private fun prepareSizes(): List<Size> {
        val list = mutableListOf<Size>()
        var totalHeight = 0
        document?.let { doc ->
            for (i in 0 until pageCount) {
                val page = doc.loadPage(i)
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
        }
        return list
    }

    private fun prepareOutlines(): List<Item> {
        return document?.loadOutlineItems() ?: emptyList()
    }

    public fun renderPage(
        index: Int,
        viewWidth: Int,
        viewHeight: Int
    ): ImageBitmap {
        if (viewWidth <= 0 || document == null || !isAuthenticated) {
            return (ImageBitmap(viewWidth, viewHeight, ImageBitmapConfig.Rgb565))
        }
        val page = document!!.loadPage(index)
        val bounds = page.bounds
        val scale = (1f * viewWidth / (bounds.x1 - bounds.x0))
        val w = viewWidth
        val h = ((bounds.y1 - bounds.y0) * scale).toInt()

        println("renderPage:index:$index, scale:$scale, $viewWidth-$viewHeight, bounds:${page.bounds}")
        val ctm = Matrix()
        ctm.scale(scale, scale)
        // 优先尝试复用缓存池中的Bitmap
        val bitmap = acquireReusableBitmap(w, h)
        val dev =
            AndroidDrawDevice(bitmap, 0, 0, 0, 0, bitmap.getWidth(), bitmap.getHeight())
        page.run(dev, ctm, null as Cookie?)
        dev.close()
        dev.destroy()

        return (bitmap.asImageBitmap())
    }

    public override fun renderPageRegion(
        region: androidx.compose.ui.geometry.Rect,
        index: Int,
        scale: Float,
        viewSize: IntSize,
        outWidth: Int,
        outHeight: Int
    ): ImageBitmap {
        if (document == null || !isAuthenticated) {
            return ImageBitmap(outWidth, outHeight, ImageBitmapConfig.Rgb565)
        }
        
        val patchX = region.left.toInt()
        val patchY = region.top.toInt()
        println("renderPageRegion:index:$index scale:$scale, w-h:$outWidth-$outHeight, offset:$patchX-$patchY, bounds:$region")

        try {
            val bitmap = acquireReusableBitmap(outWidth, outHeight)
            val ctm = Matrix(scale)
            val dev = AndroidDrawDevice(bitmap, patchX, patchY, 0, 0, outWidth, outHeight)

            val page = document!!.loadPage(index)
            page.run(dev, ctm, null)

            dev.close()
            dev.destroy()

            return (bitmap.asImageBitmap())
        } catch (e: Exception) {
            println("renderPageRegion error: $e")
            // 返回一个空的位图，避免崩溃
            return ImageBitmap(outWidth, outHeight, ImageBitmapConfig.Rgb565)
        }
    }

    public fun renderPageRegion(
        rect: androidx.compose.ui.geometry.Rect,
        index: Int,
        scale: Float,
        tileWidth: Int,
        tileHeight: Int
    ): ImageBitmap {
        if (document == null || !isAuthenticated) {
            return ImageBitmap(tileWidth, tileHeight, ImageBitmapConfig.Rgb565)
        }
        
        // 计算tile在页面中的实际位置（rect已经是相对于页面的坐标）
        val tileX = rect.left.toInt()
        val tileY = rect.top.toInt()
        val tileWidth = rect.width.toInt()
        val tileHeight = rect.height.toInt()

        println("decode.renderPageRegion:index:$index, scale:$scale, tile:$tileX-$tileY-$tileWidth-$tileHeight, bounds:$rect")

        val bitmap: Bitmap = BitmapPool.acquire(tileWidth, tileHeight)
        val ctm = Matrix(scale)
        val dev = AndroidDrawDevice(bitmap, tileX, tileY, 0, 0, tileWidth, tileHeight)

        val page = document!!.loadPage(index)
        page.run(dev, ctm, null)
        page.destroy()

        dev.close()
        dev.destroy()

        return (bitmap.asImageBitmap())
    }

    /**
     * 解析PDF页面为reflow内容（文本和图片）
     * 注意：此方法必须在主线程调用，MuPDF不支持多线程
     */
    public fun decodeReflow(pageIndex: Int): List<ReflowBean> {
        val reflowBeans = mutableListOf<ReflowBean>()

        if (document == null || !isAuthenticated) {
            return reflowBeans
        }

        try {
            val page = document!!.loadPage(pageIndex)

            // 提取文本内容
            val result = page.textAsText("preserve-whitespace,inhibit-spaces,preserve-images")
            val text = if (null != result) {
                ParseTextMain.parseAsHtmlList(result, pageIndex)
            } else null
            
            if (text != null && text.isNotEmpty()) {
                reflowBeans.addAll(text)
            }

            page.destroy()
        } catch (e: Exception) {
            println("decodeReflow error for page $pageIndex: $e")
            // 如果解析失败，返回空列表
        }

        return reflowBeans
    }

    /**
     * 优先尝试从ImageCache/BitmapPool复用Bitmap
     */
    private fun acquireReusableBitmap(width: Int, height: Int): Bitmap {
        // 优先从BitmapPool获取，这样可以复用已回收的bitmap
        return BitmapPool.acquire(width, height)
    }
}