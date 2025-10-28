package com.archko.reader.pdf.decoder

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.cache.CustomImageFetcher
import com.archko.reader.pdf.cache.ImageCache
import com.archko.reader.pdf.component.Size
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.APage
import com.archko.reader.pdf.entity.Hyperlink
import com.archko.reader.pdf.entity.Item
import com.archko.reader.pdf.entity.ReflowBean
import com.archko.reader.pdf.util.CropUtils
import com.archko.reader.pdf.util.FileTypeUtils
import com.archko.reader.pdf.util.FontCSSGenerator
import com.archko.reader.pdf.util.loadOutlineItems
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.Page
import java.awt.image.BufferedImage
import java.io.File

/**
 * @author: archko 2025/4/11 :11:26
 */
public class PdfDecoder(public val file: File) : ImageDecoder {

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

    // 页面缓存，最多缓存8页
    private val pageCache = mutableMapOf<Int, Page>()
    private val maxPageCache = 8

    public override val aPageList: MutableList<APage>? = ArrayList()

    //private var pageSizeBean: PageSizeBean? = null
    private var cachePage = true

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
            if (FileTypeUtils.isReflowable(file.absolutePath)) {
                val css = FontCSSGenerator.generateFontCSS(null, "10px")
                println("应用自定义CSS: $css")
                com.artifex.mupdf.fitz.Context.setUserCSS(css)
            }
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
            val fontSize = FontCSSGenerator.getDefFontSize()
            val fs = fontSize.toInt().toFloat()
            val w = 1280f
            val h = 1024f
            System.out.printf(
                "width:%s, height:%s, font:%s->%s, open:%s",
                w,
                h,
                fontSize,
                fs,
                file.absolutePath
            )
            doc.layout(w, h, fontSize)
            pageCount = doc.countPages()
            originalPageSizes = prepareSizes()
            outlineItems = prepareOutlines()

            initPageSizeBean()
            cacheCoverIfNeeded()
        }
    }

    private fun initPageSizeBean() {
        try {
            val count: Int = originalPageSizes.size
            for (i in 0..<count) {
                val aPage = APage(i, originalPageSizes[i].width, originalPageSizes[i].height, 1f)
                aPageList!!.add(aPage)
            }
        } catch (_: Exception) {
            aPageList!!.clear()
        }
    }

    /**
     * 检查并缓存封面图片
     */
    private fun cacheCoverIfNeeded() {
        val path = file.absolutePath
        if (FileTypeUtils.isImageFile(path) || FileTypeUtils.isTiffFile(path)) {
            return
        }
        try {
            if (null != ImageCache.acquirePage(path)) {
                return
            }
            val page = getPage(0)
            val bitmap = renderCoverPage(page)

            CustomImageFetcher.cacheBitmap(bitmap, path)
        } catch (e: Exception) {
            println("缓存封面失败: ${e.message}")
        }
    }

    /**
     * 渲染封面页面，根据高宽比进行特殊处理
     */
    private fun renderCoverPage(page: Page): ImageBitmap {
        val pWidth = page.bounds.x1 - page.bounds.x0
        val pHeight = page.bounds.y1 - page.bounds.y0

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

            val scaledWidth = (pWidth * scale).toInt()
            val scaledHeight = (pHeight * scale).toInt()

            val cropWidth = maxOf(targetWidth, scaledWidth)
            val cropHeight = maxOf(targetHeight, scaledHeight)
            println("large.width-height:$cropWidth-$cropHeight")
            val bbox = com.artifex.mupdf.fitz.Rect(
                0f,
                0f,
                cropWidth.toFloat(),
                cropHeight.toFloat()
            )
            val pixmap = com.artifex.mupdf.fitz.Pixmap(
                com.artifex.mupdf.fitz.ColorSpace.DeviceBGR,
                bbox,
                true
            )
            pixmap.clear(255)
            com.artifex.mupdf.fitz.Context.disableICC()
            val cropDev = com.artifex.mupdf.fitz.DrawDevice(pixmap)
            val cropCtm = Matrix()
            cropCtm.scale(scale, scale)
            page.run(cropDev, cropCtm, null)
            cropDev.close()
            cropDev.destroy()
            val pixmapWidth = pixmap.width
            val pixmapHeight = pixmap.height
            val image = BufferedImage(pixmapWidth, pixmapHeight, BufferedImage.TYPE_3BYTE_BGR)
            image.setRGB(0, 0, pixmapWidth, pixmapHeight, pixmap.pixels, 0, pixmapWidth)
            pixmap.destroy()
            image.toComposeImageBitmap()
        } else if (pWidth > pHeight) {
            // 对于宽大于高的页面，按最大比例缩放后截取
            val scale = maxOf(targetWidth.toFloat() / pWidth, targetHeight.toFloat() / pHeight)

            val scaledWidth = (pWidth * scale).toInt()
            val scaledHeight = (pHeight * scale).toInt()

            // 确保裁剪区域不超过目标尺寸
            val cropWidth = maxOf(targetWidth, scaledWidth)
            val cropHeight = maxOf(targetHeight, scaledHeight)

            println("wide.width-height:$cropWidth-$cropHeight")
            val bbox = com.artifex.mupdf.fitz.Rect(
                0f,
                0f,
                cropWidth.toFloat(),
                cropHeight.toFloat()
            )
            val pixmap = com.artifex.mupdf.fitz.Pixmap(
                com.artifex.mupdf.fitz.ColorSpace.DeviceBGR,
                bbox,
                true
            )
            pixmap.clear(255)
            com.artifex.mupdf.fitz.Context.disableICC()
            val cropDev = com.artifex.mupdf.fitz.DrawDevice(pixmap)
            val cropCtm = Matrix()
            cropCtm.scale(scale, scale)
            page.run(cropDev, cropCtm, null)
            cropDev.close()
            cropDev.destroy()
            val pixmapWidth = pixmap.width
            val pixmapHeight = pixmap.height
            val image = BufferedImage(pixmapWidth, pixmapHeight, BufferedImage.TYPE_3BYTE_BGR)
            image.setRGB(0, 0, pixmapWidth, pixmapHeight, pixmap.pixels, 0, pixmapWidth)
            pixmap.destroy()
            image.toComposeImageBitmap()
        } else {
            // 原始逻辑处理其他情况
            val xscale = targetWidth.toFloat() / pWidth
            val yscale = targetHeight.toFloat() / pHeight

            // 使用最大比例以确保填充整个目标区域
            val scale = maxOf(xscale, yscale)

            val scaledWidth = (pWidth * scale).toInt()
            val scaledHeight = (pHeight * scale).toInt()

            // 确保裁剪区域不超过目标尺寸
            val cropWidth = maxOf(targetWidth, scaledWidth)
            val cropHeight = maxOf(targetHeight, scaledHeight)

            println("width-height:$cropWidth-$cropHeight")
            val bbox = com.artifex.mupdf.fitz.Rect(
                0f,
                0f,
                cropWidth.toFloat(),
                cropHeight.toFloat()
            )
            val pixmap = com.artifex.mupdf.fitz.Pixmap(
                com.artifex.mupdf.fitz.ColorSpace.DeviceBGR,
                bbox,
                true
            )
            pixmap.clear(255)
            com.artifex.mupdf.fitz.Context.disableICC()
            val cropDev = com.artifex.mupdf.fitz.DrawDevice(pixmap)
            val cropCtm = Matrix()
            cropCtm.scale(scale, scale)
            page.run(cropDev, cropCtm, null)
            cropDev.close()
            cropDev.destroy()
            val pixmapWidth = pixmap.width
            val pixmapHeight = pixmap.height
            val image = BufferedImage(pixmapWidth, pixmapHeight, BufferedImage.TYPE_3BYTE_BGR)
            image.setRGB(0, 0, pixmapWidth, pixmapHeight, pixmap.pixels, 0, pixmapWidth)
            pixmap.destroy()
            image.toComposeImageBitmap()
        }
    }

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

    /**
     * 获取原始页面尺寸
     */
    public fun getOriginalPageSize(index: Int): Size {
        return originalPageSizes[index]
    }

    override fun close() {
        if (aPageList != null && !aPageList.isEmpty()) {
            println("PdfDecoder.close: aPageList size=${aPageList.size}")
        }

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
        document = null

        ImageCache.clear()
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

        return emptyList()
    }

    private fun decodePageLinks(pageIndex: Int): List<Hyperlink> {
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

    private fun decode(
        index: Int,
        scale: Float,
        targetBitmap: ImageBitmap,
        patchX: Int,
        patchY: Int,
        decodeLink: Boolean
    ): ImageBitmap {
        val ctm = Matrix(scale)

        val bbox = com.artifex.mupdf.fitz.Rect(
            0f,
            0f,
            targetBitmap.width.toFloat(),
            targetBitmap.height.toFloat()
        )
        val pixmap =
            com.artifex.mupdf.fitz.Pixmap(com.artifex.mupdf.fitz.ColorSpace.DeviceBGR, bbox, true)
        pixmap.clear(255)
        com.artifex.mupdf.fitz.Context.disableICC()

        val dev = com.artifex.mupdf.fitz.DrawDevice(pixmap)

        // 添加偏移，使渲染区域正确
        ctm.translate(-(patchX / scale), -(patchY / scale))

        val page = getPage(index)
        page.run(dev, ctm, null)

        // 在解码缩略图时同时解析链接
        parseLinksIfNeeded(index, false, decodeLink)

        dev.close()
        dev.destroy()

        // Convert pixmap to BufferedImage and then to ImageBitmap
        val pixmapWidth = pixmap.width
        val pixmapHeight = pixmap.height
        val image = BufferedImage(
            pixmapWidth,
            pixmapHeight,
            BufferedImage.TYPE_3BYTE_BGR
        )
        image.setRGB(0, 0, pixmapWidth, pixmapHeight, pixmap.pixels, 0, pixmapWidth)

        pixmap.destroy()

        return image.toComposeImageBitmap()
    }

    public override fun renderPage(
        aPage: APage,
        viewSize: IntSize,
        outWidth: Int,
        outHeight: Int,
        crop: Boolean
    ): ImageBitmap {
        if (document == null || (!isAuthenticated && needsPassword)) {
            return ImageBitmap(outWidth, outHeight, ImageBitmapConfig.Rgb565)
        }

        try {
            val index = aPage.index
            if (aPage.cropBounds != null && crop) {
                val cropBounds = aPage.cropBounds!!

                val scale = if (aPage.width > 0) {
                    outWidth.toFloat() / cropBounds.width
                } else {
                    1f
                }

                val patchX = cropBounds.left.toInt() * scale
                val patchY = cropBounds.top.toInt() * scale
                val height = scale * cropBounds.height
                val bitmap =
                    acquireReusableBitmap((scale * cropBounds.width).toInt(), height.toInt())

                return decode(index, scale, bitmap, patchX.toInt(), patchY.toInt(), true)
            } else {
                val cropBounds = androidx.compose.ui.geometry.Rect(
                    0f,
                    0f,
                    aPage.width.toFloat(),
                    aPage.height.toFloat()
                )

                val patchX = cropBounds.left.toInt()
                val patchY = cropBounds.top.toInt()
                // 计算缩略图的缩放比例：缩略图宽度 / 原始页面宽度
                val scale = if (aPage.width > 0) {
                    outWidth.toFloat() / aPage.getWidth(crop)
                } else {
                    1f
                }
                val height = scale * aPage.getHeight(crop)
                val bitmap = acquireReusableBitmap(outWidth, height.toInt())

                val imageBitmap = decode(index, scale, bitmap, patchX, patchY, true)

                // 如果启用了切边功能但没有cropBounds，检测并设置
                if (crop) {
                    val cropBounds = CropUtils.detectCropBounds(imageBitmap)
                    if (cropBounds != null) {
                        // 将缩略图坐标转换为原始PDF坐标
                        val originalPage = originalPageSizes[index]
                        val ratio = originalPage.width.toFloat() / outWidth

                        // 使用宽度比例转换左右边界，使用高度比例转换上下边界
                        val leftBound = (cropBounds.left * ratio)
                        val topBound = (cropBounds.top * ratio)
                        val rightBound = (cropBounds.right * ratio)
                        val bottomBound = (cropBounds.bottom * ratio)
                        val pdfCropBounds = androidx.compose.ui.geometry.Rect(
                            leftBound,
                            topBound,
                            rightBound,
                            bottomBound
                        )

                        aPage.cropBounds = pdfCropBounds

                        // 真正对图片进行切边处理
                        val croppedBitmap = cropImageBitmap(imageBitmap, cropBounds)
                        return croppedBitmap
                    }
                }
                return imageBitmap
            }
        } catch (e: Exception) {
            println("PdfDecoder.renderPage error: $e")
            // 返回一个空的位图，避免崩溃
            return ImageBitmap(outWidth, outHeight, ImageBitmapConfig.Rgb565)
        }
    }

    /**
     * 在解码缩略图时同时解析链接
     * @param pageIndex 页面索引
     * @param forceParse 是否强制解析（即使已缓存）
     */
    private fun parseLinksIfNeeded(
        pageIndex: Int,
        forceParse: Boolean = false,
        decodeLink: Boolean = true
    ) {
        if (!decodeLink) {
            return
        }
        if (!forceParse && linksCache.containsKey(pageIndex)) {
            return
        }

        decodePageLinks(pageIndex)
    }

    /**
     * 对图片进行切边处理
     */
    private fun cropImageBitmap(
        originalBitmap: ImageBitmap,
        cropBounds: androidx.compose.ui.geometry.Rect
    ): ImageBitmap {
        val cropX = cropBounds.left.toInt()
        val cropY = cropBounds.top.toInt()
        val cropWidth = (cropBounds.right - cropX).toInt()
        val cropHeight = (cropBounds.bottom - cropY).toInt()

        // 确保切边区域在图片范围内
        val safeX = cropX.coerceIn(0, originalBitmap.width - 1)
        val safeY = cropY.coerceIn(0, originalBitmap.height - 1)
        val safeWidth = cropWidth.coerceIn(1, originalBitmap.width - safeX)
        val safeHeight = cropHeight.coerceIn(1, originalBitmap.height - safeY)

        println("PdfDecoder.cropImageBitmap: 原始尺寸=${originalBitmap.width}x${originalBitmap.height}, 切边区域=($safeX,$safeY,$safeWidth,$safeHeight)")

        try {
            // 读取原始图片的像素数据
            val originalPixels = IntArray(originalBitmap.width * originalBitmap.height)
            originalBitmap.readPixels(originalPixels)

            // 创建切边后的BufferedImage
            val croppedImage = BufferedImage(safeWidth, safeHeight, BufferedImage.TYPE_INT_ARGB)

            // 复制切边区域的像素
            for (y in 0 until safeHeight) {
                for (x in 0 until safeWidth) {
                    val srcIndex = (safeY + y) * originalBitmap.width + (safeX + x)
                    if (srcIndex < originalPixels.size) {
                        croppedImage.setRGB(x, y, originalPixels[srcIndex])
                    }
                }
            }

            return croppedImage.toComposeImageBitmap()
        } catch (e: Exception) {
            println("PdfDecoder.cropImageBitmap error: $e")
            // 如果切边失败，返回原始图片
            return originalBitmap
        }
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

        try {
            val patchX = region.left.toInt()
            val patchY = region.top.toInt()
            println("PdfDecoder.renderPageRegion:index:$index scale:$scale, w-h:$outWidth-$outHeight, offset:$patchX-$patchY, bounds:$region")

            val bitmap = acquireReusableBitmap(outWidth, outHeight)
            return decode(index, scale, bitmap, patchX, patchY, false)
        } catch (e: Exception) {
            println("PdfDecoder.renderPageRegion error: $e")
            // 返回一个空的位图，避免崩溃
            return ImageBitmap(outWidth, outHeight, ImageBitmapConfig.Rgb565)
        }
    }

    /**
     * 解析PDF页面为reflow内容（文本和图片）
     * 注意：此方法必须在主线程调用，MuPDF不支持多线程
     */
    public fun decodeReflow(pageIndex: Int): List<String> {
        val reflowBeans = mutableListOf<String>()

        if (document == null || (!isAuthenticated && needsPassword)) {
            return reflowBeans
        }

        try {
            val page = getPage(pageIndex)

            // 提取文本内容
            val result = page.textAsText("preserve-whitespace,inhibit-spaces")
            val text = if (null != result) {
                ParseTextMain.parseAsText(result)
            } else null

            if (text != null && text.isNotEmpty()) {
                reflowBeans.add(text)
            }

            // 注意：这里不调用page.destroy()，因为页面被缓存了
        } catch (e: Exception) {
            println("decodeReflow error for page $pageIndex: $e")
            // 如果解析失败，返回空列表
        }

        return reflowBeans
    }

    /**
     * 获取或创建页面，支持缓存
     */
    private fun getPage(index: Int): Page {
        // 如果缓存已满且当前索引不在缓存中，移除最旧的项
        if (pageCache.size >= maxPageCache && !pageCache.containsKey(index)) {
            val oldestIndex = pageCache.keys.first()
            val oldestPage = pageCache.remove(oldestIndex)
            oldestPage?.destroy()
            //println("Removed page $oldestIndex from cache to make room for page $index")
        }

        return pageCache.getOrPut(index) {
            document!!.loadPage(index)
        }
    }

    /**
     * 优先尝试从复用池获取ImageBitmap
     */
    private fun acquireReusableBitmap(width: Int, height: Int): ImageBitmap {
        // For desktop, create a new ImageBitmap
        // In a real implementation, you might want to implement a bitmap pool for desktop too
        return ImageBitmap(width, height, ImageBitmapConfig.Argb8888)
    }
}