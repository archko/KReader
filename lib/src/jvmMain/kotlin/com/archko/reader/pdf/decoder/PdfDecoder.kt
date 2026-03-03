package com.archko.reader.pdf.decoder

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.cache.APageSizeLoader
import com.archko.reader.pdf.cache.CustomImageFetcher
import com.archko.reader.pdf.cache.ImageCache
import com.archko.reader.pdf.component.DecodeTask
import com.archko.reader.pdf.component.SearchResult
import com.archko.reader.pdf.component.Size
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.APage
import com.archko.reader.pdf.entity.DocQuad
import com.archko.reader.pdf.entity.Hyperlink
import com.archko.reader.pdf.entity.Item
import com.archko.reader.pdf.entity.PageSizeBean
import com.archko.reader.pdf.entity.ReflowBean
import com.archko.reader.pdf.entity.ReflowCacheBean
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
    private var pageSizeBean: PageSizeBean? = null
    private var cachePage = true
    public override var cacheBean: ReflowCacheBean? = null
    public override var filePath: String? = null

    // 链接缓存，避免重复解析
    private val linksCache = mutableMapOf<Int, List<Hyperlink>>()

    public companion object {
        /**
         * 计算封面页面的渲染参数
         */
        public data class CoverRenderParams(
            val scale: Float,
            val renderWidth: Float,
            val renderHeight: Float,
            val cropX: Float,
            val cropY: Float
        )

        /**
         * 计算封面页面的缩放和裁剪参数
         */
        public fun calculateCoverRenderParams(
            pWidth: Float,
            pHeight: Float,
            targetWidth: Int = 160,
            targetHeight: Int = 200
        ): CoverRenderParams {
            // 定义极宽/极高的阈值
            val extremeThreshold = 8000f
            var scale = 1f
            var renderWidth = targetWidth.toFloat()
            var renderHeight = targetHeight.toFloat()
            var cropX = 0f
            var cropY = 0f

            // 检查是否为极端长宽比的图片（某边大于8000）
            if (pWidth > extremeThreshold || pHeight > extremeThreshold) {
                // 极宽/极高图片：单边适配+从顶部/左侧裁剪
                if (pWidth > pHeight) {
                    // 极宽图：高缩到200，宽按比例缩放后从左侧裁160
                    scale = targetHeight / pHeight
                    val scaledWidth = pWidth * scale
                    // 裁剪宽度限制为目标宽度，从左侧开始
                    renderWidth = minOf(scaledWidth, targetWidth.toFloat())
                    renderHeight = targetHeight.toFloat()
                    cropX = 0f
                    cropY = 0f
                } else {
                    // 极高图：宽缩到160，高按比例缩放后从顶部裁200
                    scale = targetWidth / pWidth
                    val scaledHeight = pHeight * scale
                    // 裁剪高度限制为目标高度，从顶部开始
                    renderWidth = targetWidth.toFloat()
                    renderHeight = minOf(scaledHeight, targetHeight.toFloat())
                    cropX = 0f
                    cropY = 0f
                }
                println("extreme pWidth:$pWidth, pHeight:$pHeight, width-height:${renderWidth.toInt()}-${renderHeight.toInt()}, scale:$scale")
            } else if (pWidth > pHeight) {
                // 宽大于高：按宽度缩放到160，高度自适应
                scale = targetWidth / pWidth
                renderWidth = targetWidth.toFloat()
                renderHeight = (pHeight * scale)
                cropX = 0f
                cropY = 0f
                println("wide. pWidth:$pWidth, pHeight:$pHeight, width-height:${renderWidth.toInt()}-${renderHeight.toInt()}, scale:$scale")
            } else {
                // 高大于/等于宽：统一按宽度缩放到160，高度自适应
                scale = targetWidth / pWidth
                renderWidth = targetWidth.toFloat()
                renderHeight = (pHeight * scale)
                cropX = 0f
                cropY = 0f
                println("normal. pWidth:$pWidth, pHeight:$pHeight, width-height:${renderWidth.toInt()}-${renderHeight.toInt()}, scale:$scale")
            }

            return CoverRenderParams(scale, renderWidth, renderHeight, cropX, cropY)
        }

        /**
         * 渲染封面页面，根据高宽比进行特殊处理
         */
        public fun renderCoverPage(
            path: String,
            page: Page,
            targetWidth: Int = 160,
            targetHeight: Int = 200
        ): ImageBitmap? {
            val pWidth = page.bounds.x1 - page.bounds.x0
            val pHeight = page.bounds.y1 - page.bounds.y0

            val params = calculateCoverRenderParams(pWidth, pHeight, targetWidth, targetHeight)
            println("decode.thumb:$path")

            try {
                val pixmapBbox =
                    com.artifex.mupdf.fitz.Rect(0f, 0f, params.renderWidth, params.renderHeight)
                val pixmap = com.artifex.mupdf.fitz.Pixmap(
                    com.artifex.mupdf.fitz.ColorSpace.DeviceBGR,
                    pixmapBbox,
                    true
                )
                pixmap.clear(255)
                com.artifex.mupdf.fitz.Context.disableICC()

                val drawDevice = com.artifex.mupdf.fitz.DrawDevice(pixmap)

                val ctm = Matrix()
                ctm.scale(params.scale, params.scale)
                ctm.translate(-params.cropX, -params.cropY)

                page.run(drawDevice, ctm, null)

                val bufferedImage = BufferedImage(
                    params.renderWidth.toInt(),
                    params.renderHeight.toInt(),
                    BufferedImage.TYPE_3BYTE_BGR
                )
                bufferedImage.setRGB(
                    0,
                    0,
                    params.renderWidth.toInt(),
                    params.renderHeight.toInt(),
                    pixmap.pixels,
                    0,
                    params.renderWidth.toInt()
                )

                drawDevice.close()
                drawDevice.destroy()
                pixmap.destroy()

                return bufferedImage.toComposeImageBitmap()
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }
    }

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
            filePath = file.absolutePath
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
            if (FileTypeUtils.isReflowable(file.absolutePath)) {
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
            }
            pageCount = doc.countPages()

            // 先尝试从缓存加载页面尺寸和切边数据
            initPageSizeBean()

            // 如果缓存不存在或不完整，从文档加载页面尺寸
            if (originalPageSizes.isEmpty()) {
                originalPageSizes = prepareSizes()
            }

            outlineItems = prepareOutlines()
            cacheCoverIfNeeded()
        }
    }

    private fun initPageSizeBean() {
        try {
            val count: Int = pageCount
            val psb: PageSizeBean? = APageSizeLoader.loadPageSizeFromFile(count, file.absolutePath)
            println("PdfDecoder.initPageSizeBean:$psb")

            if (null != psb && psb.list != null && psb.list!!.size == count) {
                // 缓存存在且完整，直接使用
                pageSizeBean = psb
                aPageList!!.addAll(psb.list as MutableList)

                // 从缓存构建 originalPageSizes，避免重复加载页面
                val list = mutableListOf<Size>()
                var totalHeight = 0
                for (aPage in psb.list!!) {
                    val size = Size(
                        aPage.width,
                        aPage.height,
                        aPage.index,
                        scale = 1.0f,
                        totalHeight,
                    )
                    totalHeight += size.height
                    list.add(size)
                }
                originalPageSizes = list
                println("PdfDecoder.initPageSizeBean: 从缓存加载了 ${list.size} 个页面尺寸")
                return
            }

            // 缓存不存在或不完整，需要从文档加载
            pageSizeBean = PageSizeBean()
            pageSizeBean!!.list = aPageList
        } catch (e: Exception) {
            println("PdfDecoder.initPageSizeBean error: ${e.message}")
            aPageList!!.clear()
        }
    }

    /**
     * 检查并缓存封面图片
     */
    private fun cacheCoverIfNeeded() {
        val path = file.absolutePath
        try {
            if (null != ImageCache.acquirePage(path)) {
                return
            }
            val page = getPage(0)
            val bitmap = renderCoverPage(path, page)

            CustomImageFetcher.cacheBitmap(bitmap, path)
        } catch (e: Exception) {
            println("缓存封面失败: ${e.message}")
        }
    }

    override fun size(viewportSize: IntSize): IntSize {
        if ((imageSize == IntSize.Zero || viewSize != viewportSize)
            && viewportSize.width > 0 && viewportSize.height > 0
            && document != null && (isAuthenticated || !needsPassword)
        ) {
            viewSize = viewportSize
            calculateSize(viewportSize)
        }
        return imageSize
    }

    private fun calculateSize(viewportSize: IntSize) {
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
        if (cachePage && !aPageList.isNullOrEmpty()) {
            println("PdfDecoder.close:${aPageList.size}")
            APageSizeLoader.savePageSizeToFile(false, file.absolutePath, aPageList)
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
                val width = bounds.x1.toInt() - bounds.x0.toInt()
                val height = bounds.y1.toInt() - bounds.y0.toInt()
                val size = Size(
                    width,
                    height,
                    i,
                    scale = 1.0f,
                    totalHeight,
                )
                totalHeight += size.height
                page.destroy()
                list.add(size)

                // 同时填充 aPageList
                val aPage = APage(i, width, height, 1f)
                aPageList!!.add(aPage)
            }

            // 保存到缓存
            if (cachePage && aPageList!!.isNotEmpty()) {
                APageSizeLoader.savePageSizeToFile(false, file.absolutePath, aPageList)
            }
        }
        println("PdfDecoder.prepareSizes: 从文档加载了 ${list.size} 个页面尺寸")
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
            return CustomImageFetcher.createWhiteBitmap(outWidth, outHeight)
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
            return CustomImageFetcher.createWhiteBitmap(outWidth, outHeight)
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
            return CustomImageFetcher.createWhiteBitmap(outWidth, outHeight)
        }

        try {
            val patchX = region.left.toInt()
            val patchY = region.top.toInt()
            println("PdfDecoder.renderPageRegion:index:$index scale:$scale, w-h:$outWidth-$outHeight, offset:$patchX-$patchY, bounds:$region")

            val bitmap = acquireReusableBitmap(outWidth, outHeight)
            return decode(index, scale, bitmap, patchX, patchY, false)
        } catch (e: Exception) {
            println("PdfDecoder.renderPageRegion error: $e")
            return CustomImageFetcher.createWhiteBitmap(outWidth, outHeight)
        }
    }

    public override fun renderPageRegion(
        task: DecodeTask,
        totalScale: Float
    ): ImageBitmap {
        return ImageBitmap(
            task.width,
            task.height,
            ImageBitmapConfig.Rgb565
        )
    }

    public override fun getStructuredText(index: Int): Any? {
        if (document == null || (!isAuthenticated && needsPassword)) {
            return null
        }
        val page = getPage(index)
        return page.toStructuredText()
    }

    /**
     * 解析单个页面的文本内容（用于TTS快速启动）
     */
    public override fun decodeReflowSinglePage(pageIndex: Int): ReflowBean? {
        if (document == null || (!isAuthenticated && needsPassword)) {
            return null
        }

        if (pageIndex < 0 || pageIndex >= originalPageSizes.size) {
            return null
        }

        return try {
            val page = getPage(pageIndex)
            val result = page.textAsText("preserve-whitespace,inhibit-spaces")
            val text = if (null != result) {
                ParseTextMain.parseAsText(result)
            } else null

            if (null != text && text.isNotEmpty() && text.isNotBlank()) {
                val pageText = text.trim()
                if (pageText.length > 10) {
                    ReflowBean(
                        data = pageText,
                        type = ReflowBean.TYPE_STRING,
                        page = pageIndex.toString()
                    )
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            println("TTS: 解码第${pageIndex + 1}页失败: ${e.message}")
            null
        }
    }

    /**
     * 解析所有页面的文本内容（用于TTS后台缓存）
     */
    public override fun decodeReflowAllPages(): List<ReflowBean> {
        if (document == null || (!isAuthenticated && needsPassword)) {
            return emptyList()
        }

        val totalPages = originalPageSizes.size
        println("TTS: 开始解析所有页面，共${totalPages}页")
        val allTexts = mutableListOf<ReflowBean>()

        var addedPages = 0
        var skippedPages = 0

        for (currentPage in 0 until totalPages) {
            try {
                val page = getPage(currentPage)
                val result = page.textAsText("preserve-whitespace,inhibit-spaces")
                val text = if (null != result) {
                    ParseTextMain.parseAsText(result)
                } else null

                if (null != text && text.isNotEmpty() && text.isNotBlank()) {
                    val pageText = text.trim()
                    if (pageText.length > 10) { // 只添加有意义的文本
                        allTexts.add(
                            ReflowBean(
                                data = pageText,
                                type = ReflowBean.TYPE_STRING,
                                page = currentPage.toString()
                            )
                        )
                        addedPages++
                    } else {
                        println("TTS: 第${currentPage + 1}页文本太短: ${pageText.length}")
                        skippedPages++
                    }
                } else {
                    println("TTS: 第${currentPage + 1}页无文本内容")
                    skippedPages++
                }
            } catch (e: Exception) {
                println("TTS: 解码第${currentPage + 1}页失败: ${e.message}")
                skippedPages++
            }
        }

        println("TTS: 解析完成，有效页数=$addedPages，跳过页数=$skippedPages")
        return allTexts
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

    /**
     * 在文档中搜索文本
     */
    override fun search(
        query: String,
        caseSensitive: Boolean
    ): List<SearchResult> {
        if (query.isBlank() || document == null) {
            return emptyList()
        }

        val results = mutableListOf<SearchResult>()

        try {
            for (pageIndex in 0 until pageCount) {
                val page = getPage(pageIndex) ?: continue

                // 使用MuPDF的search功能 - 返回 Quad[][]
                val searchQuery = if (caseSensitive) query else query.lowercase()
                val quadArrays = page.search(searchQuery)

                if (quadArrays.isNotEmpty()) {
                    // 获取页面文本用于上下文
                    val result = page.textAsText("preserve-whitespace,inhibit-spaces")
                    val pageText = if (null != result) {
                        ParseTextMain.parseAsText(result) ?: ""
                    } else {
                        ""
                    }

                    // 为每个匹配创建结果 - quadArrays是二维数组，每个匹配可能有多个quad
                    quadArrays.forEach { quadArray ->
                        if (quadArray.isNotEmpty()) {
                            // 提取上下文（匹配文本前后各30个字符）
                            val matchIndex = if (caseSensitive && pageText.isNotEmpty()) {
                                pageText.indexOf(query)
                            } else if (pageText.isNotEmpty()) {
                                pageText.lowercase().indexOf(searchQuery)
                            } else {
                                -1
                            }

                            val contextStart = (matchIndex - 30).coerceAtLeast(0)
                            val contextEnd =
                                (matchIndex + query.length + 30).coerceAtMost(pageText.length)
                            val context = if (matchIndex >= 0 && pageText.isNotEmpty()) {
                                pageText.substring(contextStart, contextEnd).trim()
                            } else {
                                query
                            }

                            // 转换所有quad到我们的MuPdfQuad格式
                            val convertedQuads = quadArray.map { quad ->
                                DocQuad(
                                    ul = Offset(quad.ul_x, quad.ul_y),
                                    ur = Offset(quad.ur_x, quad.ur_y),
                                    ll = Offset(quad.ll_x, quad.ll_y),
                                    lr = Offset(quad.lr_x, quad.lr_y)
                                )
                            }

                            results.add(
                                SearchResult(
                                    pageIndex = pageIndex,
                                    text = query,
                                    quads = convertedQuads,
                                    context = context
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("PdfDecoder.search error: ${e.message}")
            e.printStackTrace()
        }

        println("PdfDecoder.search: query='$query', found ${results.size} results")
        return results
    }
}