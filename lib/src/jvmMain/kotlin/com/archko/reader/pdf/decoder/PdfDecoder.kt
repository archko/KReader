package com.archko.reader.pdf.decoder

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.component.Size
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.Hyperlink
import com.archko.reader.pdf.entity.Item
import com.archko.reader.pdf.util.loadOutlineItems
import com.artifex.mupdf.fitz.ColorSpace
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

    private var document: Document? = null
    public override var pageCount: Int = 0

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

    // 密码相关状态
    public var needsPassword: Boolean = false
    public var isAuthenticated: Boolean = false

    // 页面缓存，最多缓存8页
    private val pageCache = mutableMapOf<Int, com.artifex.mupdf.fitz.Page>()
    private val maxPageCache = 8

    // 链接缓存，避免重复解析
    private val linksCache = mutableMapOf<Int, List<Hyperlink>>()

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
                isAuthenticated = true // 不需要密码的文档直接设置为已认证
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
            && document != null && (isAuthenticated || !needsPassword)
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

    /**
     * 获取或创建页面，支持缓存
     */
    private fun getPage(index: Int): com.artifex.mupdf.fitz.Page {
        // 如果缓存已满且当前索引不在缓存中，移除最旧的项
        if (pageCache.size >= maxPageCache && !pageCache.containsKey(index)) {
            val oldestIndex = pageCache.keys.first()
            val oldestPage = pageCache.remove(oldestIndex)
            oldestPage?.destroy()
            println("Removed page $oldestIndex from cache to make room for page $index")
        }

        return pageCache.getOrPut(index) {
            document!!.loadPage(index)
        }
    }

    override fun close() {
        // 清理页面缓存
        pageCache.values.forEach { page ->
            try {
                page.destroy()
            } catch (e: Exception) {
                println("Error destroying cached page: $e")
            }
        }
        pageCache.clear()

        // 清理链接缓存
        linksCache.clear()

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

    /**
     * 获取页面上的链接
     * @param pageIndex 页面索引
     * @return 链接列表
     */
    public override fun getPageLinks(pageIndex: Int): List<Hyperlink> {
        // 先检查缓存
        if (linksCache.containsKey(pageIndex)) {
            return linksCache[pageIndex]!!
        }

        if (document == null || (!isAuthenticated && needsPassword)) {
            return emptyList()
        }

        return try {
            val page = getPage(pageIndex)
            val links = page.links ?: return emptyList()

            val hyperlinks = mutableListOf<Hyperlink>()

            for (link in links) {
                val hyperlink = Hyperlink()
                hyperlink.bbox = androidx.compose.ui.geometry.Rect(
                    link.bounds.x0,
                    link.bounds.y0,
                    link.bounds.x1,
                    link.bounds.y1
                )

                val location = document!!.resolveLink(link)
                val targetPage = document!!.pageNumberFromLocation(location)

                if (targetPage >= 0) {
                    // 页面链接
                    hyperlink.linkType = Hyperlink.LINKTYPE_PAGE
                    hyperlink.page = targetPage
                    hyperlink.url = null
                } else {
                    // URL链接
                    hyperlink.linkType = Hyperlink.LINKTYPE_URL
                    hyperlink.url = link.uri
                    hyperlink.page = -1
                }

                hyperlinks.add(hyperlink)
            }

            // 缓存结果
            linksCache[pageIndex] = hyperlinks
            println("PdfDecoder.getPageLinks: page=$pageIndex, links=${hyperlinks.size}")

            hyperlinks
        } catch (e: Exception) {
            println("获取页面链接失败: $e")
            emptyList()
        }
    }

    /**
     * 在解码缩略图时同时解析链接
     * @param pageIndex 页面索引
     * @param forceParse 是否强制解析（即使已缓存）
     */
    private fun parseLinksIfNeeded(pageIndex: Int, forceParse: Boolean = false) {
        if (!forceParse && linksCache.containsKey(pageIndex)) {
            return
        }

        getPageLinks(pageIndex)
    }

    public override fun renderPageRegion(
        region: androidx.compose.ui.geometry.Rect,
        index: Int,
        scale: Float,
        viewSize: IntSize,
        outWidth: Int,
        outHeight: Int
    ): ImageBitmap {
        if (document == null || (!isAuthenticated && needsPassword)) {
            return ImageBitmap(outWidth, outHeight, ImageBitmapConfig.Rgb565)
        }

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
                val page = getPage(index)
                page.run(dev, ctm, null)
                dev.close()
                dev.destroy()

                // 在解码缩略图时同时解析链接
                parseLinksIfNeeded(index)

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