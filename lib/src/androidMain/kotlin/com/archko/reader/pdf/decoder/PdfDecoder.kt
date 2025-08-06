package com.archko.reader.pdf.decoder

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Environment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.cache.APageSizeLoader
import com.archko.reader.pdf.cache.APageSizeLoader.PageSizeBean
import com.archko.reader.pdf.cache.BitmapCache
import com.archko.reader.pdf.cache.BitmapPool
import com.archko.reader.pdf.cache.CustomImageFetcher
import com.archko.reader.pdf.component.Size
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.APage
import com.archko.reader.pdf.entity.Hyperlink
import com.archko.reader.pdf.entity.Item
import com.archko.reader.pdf.entity.ReflowBean
import com.archko.reader.pdf.util.BitmapUtils
import com.archko.reader.pdf.util.CropUtils
import com.archko.reader.pdf.util.loadOutlineItems
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.Page
import com.artifex.mupdf.fitz.android.AndroidDrawDevice
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

            //initPageSizeBean()
            cacheCoverIfNeeded()
        }
    }

    private fun initPageSizeBean() {
        try {
            val count: Int = originalPageSizes.size
            val psb: PageSizeBean? = APageSizeLoader.loadPageSizeFromFile(count, file.absolutePath)
            println("PdfDecoder.initPageSizeBean:$psb")
            if (null != psb) {
                pageSizeBean = psb
                aPageList!!.addAll(psb.list as MutableList)
                return
            } else {
                pageSizeBean = PageSizeBean()
                pageSizeBean!!.list = aPageList
            }
            for (i in 0..<count) {
                val aPage = APage(i, originalPageSizes[i].width, originalPageSizes[i].height, 1f)
                aPageList!!.add(aPage)
            }

            if (cachePage) {
                APageSizeLoader.savePageSizeToFile(false, file.absolutePath, aPageList)
            }
        } catch (_: Exception) {
            aPageList!!.clear()
        }
    }

    /**
     * 检查并缓存封面图片
     */
    private fun cacheCoverIfNeeded() {
        try {
            if (null != BitmapCache.getBitmap(file.absolutePath)) {
                return
            }
            val page = getPage(0)
            val bitmap = renderCoverPage(page)

            CustomImageFetcher.cacheBitmap(bitmap, file.absolutePath)
        } catch (e: Exception) {
            println("缓存封面失败: ${e.message}")
        }
    }

    /**
     * 渲染封面页面，根据高宽比进行特殊处理
     */
    private fun renderCoverPage(page: Page): Bitmap {
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
        if (cachePage && aPageList != null && !aPageList.isEmpty()) {
            println("PdfDecoder.close:$aPageList")
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
                hyperlink.bbox = Rect(
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

        try {
            val index = aPage.index
            val cropBounds =
                if (aPage.cropBounds != null) aPage.cropBounds!!
                else Rect(0f, 0f, aPage.width.toFloat(), aPage.height.toFloat())

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

        decodePageLinks(pageIndex)
    }

    /**
     * 对图片进行切边处理
     */
    private fun cropImageBitmap(originalBitmap: ImageBitmap, cropBounds: Rect): ImageBitmap {
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
        val androidBitmap = originalBitmap.asAndroidBitmap()
        val croppedAndroidBitmap = Bitmap.createBitmap(
            androidBitmap,
            safeX,
            safeY,
            safeWidth,
            safeHeight
        )

        val croppedImageBitmap = croppedAndroidBitmap.asImageBitmap()

        println("PdfDecoder.cropImageBitmap: 原始尺寸=${originalBitmap.width}x${originalBitmap.height}, 切边区域=($safeX,$safeY,$safeWidth,$safeHeight), 切边后尺寸=${croppedImageBitmap.width}x${croppedImageBitmap.height}")

        return croppedImageBitmap
    }

    public override fun renderPageRegion(
        region: Rect,
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
            val ctm = Matrix(scale)
            val dev = AndroidDrawDevice(bitmap, patchX, patchY, 0, 0, outWidth, outHeight)

            val page = getPage(index)
            page.run(dev, ctm, null)

            dev.close()
            dev.destroy()

            // 在解码缩略图时同时解析链接
            parseLinksIfNeeded(index)

            /*val file = File(
                Environment.getExternalStorageDirectory(),
                "/Download/$index-${region.left}-${region.top}-${region.right}-${region.bottom}.png"
            )
            println("PdfDecoder.renderPageRegion.path:${file.absolutePath}")
            BitmapUtils.saveBitmapToFile(bitmap, file)*/
            return (bitmap.asImageBitmap())
        } catch (e: Exception) {
            println("PdfDecoder.renderPageRegion error: $e")
            // 返回一个空的位图，避免崩溃
            return ImageBitmap(outWidth, outHeight, ImageBitmapConfig.Rgb565)
        }
    }

    public fun renderPageRegion(
        rect: Rect,
        index: Int,
        scale: Float,
        tileWidth: Int,
        tileHeight: Int
    ): ImageBitmap {
        if (document == null || (!isAuthenticated && needsPassword)) {
            return ImageBitmap(tileWidth, tileHeight, ImageBitmapConfig.Rgb565)
        }

        // 计算tile在页面中的实际位置（rect已经是相对于页面的坐标）
        val tileX = rect.left.toInt()
        val tileY = rect.top.toInt()
        val tileWidth = rect.width.toInt()
        val tileHeight = rect.height.toInt()

        println("PdfDecoder.renderPageRegion:index:$index, scale:$scale, tile:$tileX-$tileY-$tileWidth-$tileHeight, bounds:$rect")

        val bitmap: Bitmap = BitmapPool.acquire(tileWidth, tileHeight)
        val ctm = Matrix(scale)
        val dev = AndroidDrawDevice(bitmap, tileX, tileY, 0, 0, tileWidth, tileHeight)

        val page = getPage(index)
        page.run(dev, ctm, null)
        // 注意：这里不调用page.destroy()，因为页面被缓存了

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

        if (document == null || (!isAuthenticated && needsPassword)) {
            return reflowBeans
        }

        try {
            val page = getPage(pageIndex)

            // 提取文本内容
            val result = page.textAsText("preserve-whitespace,inhibit-spaces,preserve-images")
            val text = if (null != result) {
                ParseTextMain.parseAsHtmlList(result, pageIndex)
            } else null

            if (text != null && text.isNotEmpty()) {
                reflowBeans.addAll(text)
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
            println("Removed page $oldestIndex from cache to make room for page $index")
        }

        return pageCache.getOrPut(index) {
            document!!.loadPage(index)
        }
    }

    /**
     * 优先尝试从ImageCache/BitmapPool复用Bitmap
     */
    private fun acquireReusableBitmap(width: Int, height: Int): Bitmap {
        // 优先从BitmapPool获取，这样可以复用已回收的bitmap
        return BitmapPool.acquire(width, height)
    }

    // ========== 切边相关方法 ==========

    /**
     * 在ImageBitmap上绘制cropBounds矩形
     * @param imageBitmap 原始图片
     * @param cropBounds 切边区域
     * @return 绘制了cropBounds的Bitmap
     */
    private fun drawCropBoundsOnBitmap(
        imageBitmap: ImageBitmap,
        cropBounds: Rect,
        index: Int
    ): Bitmap {
        val androidBitmap = imageBitmap.asAndroidBitmap()

        // 创建可变的bitmap副本用于绘制
        val mutableBitmap = androidBitmap.copy(androidBitmap.config!!, true)
        val canvas = Canvas(mutableBitmap)

        // 创建画笔
        val paint = Paint().apply {
            color = android.graphics.Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }

        // 绘制cropBounds矩形
        val rect = RectF(
            cropBounds.left,
            cropBounds.top,
            cropBounds.right,
            cropBounds.bottom
        )
        canvas.drawRect(rect, paint)

        // 在矩形四角绘制小圆圈
        val circlePaint = Paint().apply {
            color = android.graphics.Color.BLUE
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val circleRadius = 4f
        canvas.drawCircle(cropBounds.left, cropBounds.top, circleRadius, circlePaint) // 左上角
        canvas.drawCircle(cropBounds.right, cropBounds.top, circleRadius, circlePaint) // 右上角
        canvas.drawCircle(cropBounds.left, cropBounds.bottom, circleRadius, circlePaint) // 左下角
        canvas.drawCircle(cropBounds.right, cropBounds.bottom, circleRadius, circlePaint) // 右下角

        println("PdfDecoder.drawCropBoundsOnBitmap: 在图片上绘制了cropBounds矩形，区域=$cropBounds")

        val file = File(
            Environment.getExternalStorageDirectory(),
            "/Download/$index-${imageBitmap.width}-${imageBitmap.height}-${cropBounds.left}-${cropBounds.top}-${cropBounds.right}-${cropBounds.bottom}.png"
        )
        println("PdfDecoder.save.path:${file.absolutePath}")
        BitmapUtils.saveBitmapToFile(mutableBitmap, file)

        return mutableBitmap
    }
}