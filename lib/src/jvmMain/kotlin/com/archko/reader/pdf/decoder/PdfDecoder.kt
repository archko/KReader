package com.archko.reader.pdf.decoder

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.component.Size
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.APage
import com.archko.reader.pdf.entity.Hyperlink
import com.archko.reader.pdf.entity.Item
import com.archko.reader.pdf.util.loadOutlineItems
import com.artifex.mupdf.fitz.*
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

    // 链接缓存，避免重复解析
    private val linksCache = mutableMapOf<Int, List<Hyperlink>>()

    // 切边相关
    public override var pageCropBounds: MutableMap<Int, androidx.compose.ui.geometry.Rect> = mutableMapOf()

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

            cacheCoverIfNeeded()
        }
    }

    /**
     * 检查并缓存封面图片
     */
    private fun cacheCoverIfNeeded() {
        /*try {
            if (null != BitmapCache.getBitmap(file.absolutePath)) {
                return
            }
            val page = getPage(0)
            val bitmap = renderCoverPage(page)

            CustomImageFetcher.cacheBitmap(bitmap, file.absolutePath)
        } catch (e: Exception) {
            println("缓存封面失败: ${e.message}")
        }*/
    }

    /**
     * 渲染封面页面，根据高宽比进行特殊处理
     */
    /*private fun renderCoverPage(page: Page): Bitmap {
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
            val cropBitmap = BitmapPool.acquire(cropWidth, cropHeight)
            val cropDev = AndroidDrawDevice(cropBitmap, 0, 0, 0, 0, cropWidth, cropHeight)
            val cropCtm = Matrix()
            cropCtm.scale(scale, scale)
            page.run(cropDev, cropCtm, null)
            cropDev.close()
            cropDev.destroy()
            cropBitmap
        } else if (pWidth > pHeight) {
            // 对于宽大于高的页面，按最大比例缩放后截取
            val scale = maxOf(targetWidth.toFloat() / pWidth, targetHeight.toFloat() / pHeight)

            val scaledWidth = (pWidth * scale).toInt()
            val scaledHeight = (pHeight * scale).toInt()

            // 确保裁剪区域不超过目标尺寸
            val cropWidth = maxOf(targetWidth, scaledWidth)
            val cropHeight = maxOf(targetHeight, scaledHeight)

            println("wide.width-height:$cropWidth-$cropHeight")
            val cropBitmap = BitmapPool.acquire(cropWidth, cropHeight)
            val cropDev = AndroidDrawDevice(cropBitmap, 0, 0, 0, 0, cropWidth, cropHeight)
            val cropCtm = Matrix()
            cropCtm.scale(scale, scale)
            page.run(cropDev, cropCtm, null)
            cropDev.close()
            cropDev.destroy()
            cropBitmap
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
            val cropBitmap = BitmapPool.acquire(cropWidth, cropHeight)
            val cropDev = AndroidDrawDevice(cropBitmap, 0, 0, 0, 0, cropWidth, cropHeight)
            val cropCtm = Matrix()
            cropCtm.scale(scale, scale)
            page.run(cropDev, cropCtm, null)
            cropDev.close()
            cropDev.destroy()
            cropBitmap
        }
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

        /*try {
            val index = aPage.index
            val cropBounds =
                if (aPage.cropBounds != null) aPage.cropBounds!!
                else androidx.compose.ui.geometry.Rect(0f, 0f, aPage.width.toFloat(), aPage.height.toFloat())

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

            val ctm = Matrix(scale)
            val dev = AndroidDrawDevice(bitmap, patchX, patchY, 0, 0, bitmap.width, bitmap.height)

            val page = getPage(index)
            page.run(dev, ctm, null)
            println("PdfDecoder.renderPage:index:$index scale:$scale, w-h:$outWidth-$outHeight-$height, offset:$patchX-$patchY, page.w-h:${page.bounds.x1 - page.bounds.x0}-${page.bounds.y1 - page.bounds.y0} bounds:$cropBounds, crop:$crop")

            // 在解码缩略图时同时解析链接
            parseLinksIfNeeded(index, false)

            dev.close()
            dev.destroy()

            val imageBitmap = bitmap.asImageBitmap()

            // 如果启用了切边功能但没有cropBounds，检测并设置
            if (crop && aPage.cropBounds == null) {
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
                    val pdfCropBounds = Rect(
                        leftBound,
                        topBound,
                        rightBound,
                        bottomBound
                    )

                    aPage.cropBounds = pdfCropBounds
                    pageCropBounds[aPage.index] = pdfCropBounds

                    // 在图片上绘制cropBounds矩形并保存（仅在第一次检测时）
                    //drawCropBoundsOnBitmap(imageBitmap, cropBounds, index)

                    // 真正对图片进行切边处理
                    val croppedBitmap = cropImageBitmap(imageBitmap, cropBounds)
                    return croppedBitmap
                }
            }

            return imageBitmap
        } catch (e: Exception) {
            println("PdfDecoder.renderPage error: $e")
            // 返回一个空的位图，避免崩溃
            return ImageBitmap(outWidth, outHeight, ImageBitmapConfig.Rgb565)
        }*/
        return ImageBitmap(outWidth, outHeight, ImageBitmapConfig.Rgb565)
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

        decodePageLinks(pageIndex)
    }

    /**
     * 对图片进行切边处理
     */
    private fun cropImageBitmap(originalBitmap: ImageBitmap, cropBounds: androidx.compose.ui.geometry.Rect): ImageBitmap {
        val cropX = cropBounds.left.toInt()
        val cropY = cropBounds.top.toInt()
        val cropWidth = (cropBounds.right - cropX).toInt()
        val cropHeight = (cropBounds.bottom - cropY).toInt()

        // 确保切边区域在图片范围内
        val safeX = cropX.coerceIn(0, originalBitmap.width - 1)
        val safeY = cropY.coerceIn(0, originalBitmap.height - 1)
        val safeWidth = cropWidth.coerceIn(1, originalBitmap.width - safeX)
        val safeHeight = cropHeight.coerceIn(1, originalBitmap.height - safeY)

        // 创建切边后的图片 - 使用Android Bitmap进行切边，然后转换回ImageBitmap
        /*val androidBitmap = originalBitmap.asAndroidBitmap()
        val croppedAndroidBitmap = Bitmap.createBitmap(
            androidBitmap,
            safeX,
            safeY,
            safeWidth,
            safeHeight
        )

        val croppedImageBitmap = croppedAndroidBitmap.asImageBitmap()

        println("PdfDecoder.cropImageBitmap: 原始尺寸=${originalBitmap.width}x${originalBitmap.height}, 切边区域=($safeX,$safeY,$safeWidth,$safeHeight), 切边后尺寸=${croppedImageBitmap.width}x${croppedImageBitmap.height}")

        return croppedImageBitmap*/
        return originalBitmap
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

            val bmp: ImageBitmap?
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
            println("renderPageRegion error: $e")
            // 返回一个空的位图，避免崩溃
            return ImageBitmap(outWidth, outHeight, ImageBitmapConfig.Rgb565)
        }
    }

    // ========== 切边相关方法 ==========

    /**
     * 获取页面切边信息
     * @param pageIndex 页面索引
     * @return 切边区域，如果未切边则返回null
     */
    override fun getPageCropBounds(pageIndex: Int): androidx.compose.ui.geometry.Rect? {
        return pageCropBounds[pageIndex]
    }
}