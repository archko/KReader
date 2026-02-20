package com.archko.reader.pdf.decoder

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.cache.APageSizeLoader
import com.archko.reader.pdf.cache.BitmapPool
import com.archko.reader.pdf.cache.CustomImageFetcher
import com.archko.reader.pdf.cache.ImageCache
import com.archko.reader.pdf.component.Size
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.APage
import com.archko.reader.pdf.entity.Hyperlink
import com.archko.reader.pdf.entity.Item
import com.archko.reader.pdf.entity.PageSizeBean
import com.archko.reader.pdf.entity.ReflowBean
import com.archko.reader.pdf.entity.ReflowCacheBean
import com.archko.reader.pdf.util.FileTypeUtils
import com.archko.reader.pdf.util.SmartCropUtils
import java.io.File

/**
 * @author: archko 2026/2/13 :22:18
 * PdfiumDecoder使用Android SDK内置的PdfRenderer进行PDF渲染
 */
public class PdfiumDecoder(public val file: File) : ImageDecoder {

    private var pdfRenderer: PdfRenderer? = null
    private var parcelFileDescriptor: ParcelFileDescriptor? = null

    public override var pageCount: Int = 0

    // 私有变量存储原始页面尺寸
    public override var originalPageSizes: List<Size> = listOf()

    // 对外提供的缩放后页面尺寸
    public override var pageSizes: List<Size> = listOf()

    public override var outlineItems: List<Item>? = listOf()

    public override var imageSize: IntSize = IntSize.Zero

    public var viewSize: IntSize = IntSize.Zero

    // 密码相关状态 - Android PdfRenderer不支持密码保护
    public var needsPassword: Boolean = false
    public var isAuthenticated: Boolean = true // PdfRenderer不支持密码，默认已认证

    // 页面缓存，使用PdfRenderer.Page对象
    private val pageCache = mutableMapOf<Int, PdfRenderer.Page>()
    private val maxPageCache = 8

    public override val aPageList: MutableList<APage>? = ArrayList()
    private var pageSizeBean: PageSizeBean? = null
    private var cachePage = true
    public override var cacheBean: ReflowCacheBean? = null
    public override var filePath: String? = null

    // 链接缓存 - Android PdfRenderer不支持链接解析
    private val linksCache = mutableMapOf<Int, List<Hyperlink>>()

    public companion object {
        /**
         * 渲染封面页面，根据高宽比进行特殊处理
         */
        public fun renderCoverPage(
            path: String,
            page: PdfRenderer.Page,
            targetWidth: Int = 160,
            targetHeight: Int = 200
        ): Bitmap? {
            val pWidth = page.width
            val pHeight = page.height

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
                println("PdfiumDecoder.decode.thumb:$path, large.width-height:$cropWidth-$cropHeight")
                val cropBitmap = BitmapPool.acquire(cropWidth, cropHeight)
                page.render(cropBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                cropBitmap
            } else if (pWidth > pHeight) {
                // 对于宽大于高的页面，按最大比例缩放后截取
                val scale = maxOf(targetWidth.toFloat() / pWidth, targetHeight.toFloat() / pHeight)

                val scaledWidth = (pWidth * scale).toInt()
                val scaledHeight = (pHeight * scale).toInt()

                // 确保裁剪区域不超过目标尺寸
                val cropWidth = maxOf(targetWidth, scaledWidth)
                val cropHeight = maxOf(targetHeight, scaledHeight)

                println("PdfiumDecoder.decode.thumb:$path, wide.width-height:$cropWidth-$cropHeight")
                val cropBitmap = BitmapPool.acquire(cropWidth, cropHeight)
                page.render(cropBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
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

                println("PdfiumDecoder.decode.thumb:$path, width-height:$cropWidth-$cropHeight")
                val cropBitmap = BitmapPool.acquire(cropWidth, cropHeight)
                page.render(cropBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                cropBitmap
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
            // Android PdfRenderer不支持reflow功能
            if (FileTypeUtils.isReflowable(file.absolutePath)) {
                println("PdfiumDecoder: Android PdfRenderer不支持reflow功能，将使用普通模式")
            }

            // 打开ParcelFileDescriptor
            parcelFileDescriptor =
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(parcelFileDescriptor!!)

            // Android PdfRenderer不支持密码保护
            needsPassword = false
            isAuthenticated = true

            initializeDocument()
        } catch (e: Exception) {
            throw RuntimeException("无法打开文档: ${file.absolutePath}, 错误: ${e.message}", e)
        }
    }

    /**
     * 使用密码认证文档 - Android PdfRenderer不支持密码
     * @param password 密码
     * @return 认证是否成功
     */
    public fun authenticatePassword(password: String): Boolean {
        println("PdfiumDecoder: Android PdfRenderer不支持密码保护")
        return false
    }

    /**
     * 初始化文档
     */
    private fun initializeDocument() {
        pdfRenderer?.let { renderer ->
            pageCount = renderer.pageCount

            // 先尝试从缓存加载页面尺寸和切边数据
            initPageSizeBean()

            // 如果缓存不存在或不完整，从文档加载页面尺寸
            if (originalPageSizes.isEmpty()) {
                originalPageSizes = prepareSizes()
            }

            // Android PdfRenderer不支持大纲解析
            outlineItems = emptyList()
            cacheCoverIfNeeded()
        }
    }

    private fun initPageSizeBean() {
        try {
            val count: Int = pageCount
            val psb: PageSizeBean? = APageSizeLoader.loadPageSizeFromFile(count, file.absolutePath)
            println("PdfiumDecoder.initPageSizeBean:$psb")

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
                println("PdfiumDecoder.initPageSizeBean: 从缓存加载了 ${list.size} 个页面尺寸")
                return
            }

            // 缓存不存在或不完整，需要从文档加载
            pageSizeBean = PageSizeBean()
            pageSizeBean!!.list = aPageList
        } catch (e: Exception) {
            println("PdfiumDecoder.initPageSizeBean error: ${e.message}")
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
            page.close()

            CustomImageFetcher.cacheBitmap(bitmap, path)
        } catch (e: Exception) {
            println("PdfiumDecoder缓存封面失败: ${e.message}")
        }
    }

    override fun size(viewportSize: IntSize): IntSize {
        if ((imageSize == IntSize.Zero || viewSize != viewportSize)
            && viewportSize.width > 0 && viewportSize.height > 0
            && pdfRenderer != null
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

                println("PdfiumDecoder.caculateSize: page $i - original: ${originalPage.width}x${originalPage.height}, scale: $scale, scaled: ${scaledWidth}x${scaledHeight}")
            }

            // 更新对外提供的页面尺寸
            pageSizes = scaledPageSizes

            imageSize = IntSize(documentWidth, totalHeight)
            println("PdfiumDecoder.caculateSize: documentWidth=$documentWidth, totalHeight=$totalHeight, pageCount=${originalPageSizes.size}")
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
            println("PdfiumDecoder.close:${aPageList.size}")
            APageSizeLoader.savePageSizeToFile(false, file.absolutePath, aPageList)
        }

        // 清理页面缓存
        pageCache.values.forEach { page ->
            try {
                page.close()
            } catch (e: Exception) {
                println("PdfiumDecoder Error closing cached page: $e")
            }
        }
        pageCache.clear()

        // 清理链接缓存
        linksCache.clear()

        pdfRenderer?.close()
        pdfRenderer = null

        parcelFileDescriptor?.close()
        parcelFileDescriptor = null

        ImageCache.clear()
        BitmapPool.clear()
    }

    private fun prepareSizes(): List<Size> {
        val list = mutableListOf<Size>()
        var totalHeight = 0
        pdfRenderer?.let { renderer ->
            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)
                val width = page.width
                val height = page.height
                val size = Size(
                    width,
                    height,
                    i,
                    scale = 1.0f,
                    totalHeight,
                )
                totalHeight += size.height
                page.close()
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
        println("PdfiumDecoder.prepareSizes: 从文档加载了 ${list.size} 个页面尺寸")
        return list
    }

    /**
     * 获取页面上的链接 - Android PdfRenderer不支持链接解析
     * @param pageIndex 页面索引
     * @return 链接列表
     */
    public override fun getPageLinks(pageIndex: Int): List<Hyperlink> {
        // Android PdfRenderer不支持链接解析
        return emptyList()
    }

    private fun decode(
        index: Int,
        scale: Float,
        bitmap: Bitmap,
        patchX: Int,
        patchY: Int,
        decodeLink: Boolean
    ) {
        val page = getPage(index)

        try {
            // 计算页面原始尺寸
            val pageWidth = page.width
            val pageHeight = page.height

            // 计算缩放后的尺寸
            val scaledWidth = (pageWidth * scale).toInt()
            val scaledHeight = (pageHeight * scale).toInt()

            if (patchX != 0 || patchY != 0) {
                val tempBitmap =
                    Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)

                val fullDestRect = android.graphics.Rect(0, 0, scaledWidth, scaledHeight)
                page.render(
                    tempBitmap,
                    fullDestRect,
                    null,
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                )

                val canvas = android.graphics.Canvas(bitmap)
                val srcRect = android.graphics.Rect(
                    maxOf(0, patchX),
                    maxOf(0, patchY),
                    minOf(scaledWidth, patchX + bitmap.width),
                    minOf(scaledHeight, patchY + bitmap.height)
                )

                val dstRect = android.graphics.Rect(
                    0,
                    0,
                    srcRect.width(),
                    srcRect.height()
                )

                // 确保源矩形在临时位图范围内
                if (srcRect.left >= 0 && srcRect.top >= 0 &&
                    srcRect.right <= tempBitmap.width && srcRect.bottom <= tempBitmap.height &&
                    srcRect.width() > 0 && srcRect.height() > 0
                ) {
                    canvas.drawBitmap(tempBitmap, srcRect, dstRect, null)
                }

                tempBitmap.recycle()
            } else {
                val destRect = android.graphics.Rect(
                    0,
                    0,
                    minOf(bitmap.width, scaledWidth),
                    minOf(bitmap.height, scaledHeight)
                )

                page.render(bitmap, destRect, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }

            if (decodeLink) {
                // 空实现，PdfRenderer不支持链接
            }
        } finally {
            // 确保页面被关闭
            page.close()
        }
    }

    public override fun renderPage(
        aPage: APage,
        viewSize: IntSize,
        outWidth: Int,
        outHeight: Int,
        crop: Boolean
    ): ImageBitmap {
        if (pdfRenderer == null) {
            return ImageBitmap(outWidth, outHeight, ImageBitmapConfig.Rgb565)
        }

        try {
            val index = aPage.index
            if (aPage.cropBounds != null && crop) {
                val start = System.currentTimeMillis()
                val cropBounds = aPage.cropBounds!!

                val scaleX = outWidth.toFloat() / cropBounds.width
                val scaleY = outHeight.toFloat() / cropBounds.height
                val scale = minOf(scaleX, scaleY)

                val patchX = cropBounds.left.toInt() * scale
                val patchY = cropBounds.top.toInt() * scale
                val height = scale * cropBounds.height
                val bitmap =
                    acquireReusableBitmap((scale * cropBounds.width).toInt(), height.toInt())
                println("PdfiumDecoder.renderPage:croped page=$index, cos:${System.currentTimeMillis() - start}, $outWidth-$outHeight, 切边后尺寸=${bitmap.width}x${bitmap.height}, patch:$patchX-$patchY, bounds=$cropBounds")

                decode(index, scale, bitmap, patchX.toInt(), patchY.toInt(), true)
                val imageBitmap = bitmap.asImageBitmap()
                return imageBitmap
            } else {
                val start = System.currentTimeMillis()
                val cropBounds = Rect(0f, 0f, aPage.width.toFloat(), aPage.height.toFloat())

                val patchX = cropBounds.left.toInt()
                val patchY = cropBounds.top.toInt()
                // 根据输出尺寸计算合适的缩放比例
                val originalSize = originalPageSizes[index]
                val scaleX = outWidth.toFloat() / originalSize.width
                val scaleY = outHeight.toFloat() / originalSize.height
                val scale = minOf(scaleX, scaleY)
                val height = scale * aPage.getHeight(crop)
                val bitmap = acquireReusableBitmap(outWidth, height.toInt())
                println("PdfiumDecoder.renderPage:page=$index, cos:${System.currentTimeMillis() - start}, 目标尺寸=$outWidth-$outHeight, patch:$patchX-$patchY, bounds=$cropBounds")

                decode(index, scale, bitmap, patchX, patchY, true)
                val imageBitmap = bitmap.asImageBitmap()
                // 如果启用了切边功能但没有cropBounds，检测并设置
                if (crop) {
                    val cropBounds = SmartCropUtils.detectSmartCropBounds(imageBitmap)
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

                        println("PdfiumDecoder.cropBounds:$index, 原始尺寸=${originalPage.width}x${originalPage.height}, 切边区域=($cropBounds), 切边后尺寸=${pdfCropBounds}")
                        if (pdfCropBounds.width < 0 || pdfCropBounds.height < 0) {
                            aPage.cropBounds = Rect(
                                0f,
                                0f,
                                imageBitmap.width.toFloat(),
                                imageBitmap.height.toFloat()
                            )
                            return imageBitmap
                        }
                        aPage.cropBounds = pdfCropBounds

                        // 真正对图片进行切边处理
                        val croppedBitmap = cropImageBitmap(index, imageBitmap, cropBounds)
                        return croppedBitmap
                    }
                }
                return imageBitmap
            }
        } catch (e: Exception) {
            println("PdfiumDecoder.renderPage error: $e")
            // 返回一个空的位图，避免崩溃
            return ImageBitmap(outWidth, outHeight, ImageBitmapConfig.Rgb565)
        }
    }

    /**
     * 对图片进行切边处理
     */
    private fun cropImageBitmap(
        index: Int,
        originalBitmap: ImageBitmap,
        cropBounds: Rect
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

        println("PdfiumDecoder.cropImageBitmap:$index, 原始尺寸=${originalBitmap.width}x${originalBitmap.height}, 切边区域=($safeX,$safeY,$safeWidth,$safeHeight), 切边后尺寸=${croppedImageBitmap.width}x${croppedImageBitmap.height}")

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
        if (pdfRenderer == null) {
            return ImageBitmap(outWidth, outHeight, ImageBitmapConfig.Rgb565)
        }

        try {
            val patchX = region.left.toInt()
            val patchY = region.top.toInt()

            val start = System.currentTimeMillis()
            val bitmap = acquireReusableBitmap(outWidth, outHeight)
            decode(index, scale, bitmap, patchX, patchY, false)
            println("PdfiumDecoder.renderPageRegion:index:$index, cos:${System.currentTimeMillis() - start}, scale:$scale, w-h:$outWidth-$outHeight, offset:$patchX-$patchY, bounds:$region")

            return (bitmap.asImageBitmap())
        } catch (e: Exception) {
            println("PdfiumDecoder.renderPageRegion error: $e")
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
        if (pdfRenderer == null) {
            return ImageBitmap(tileWidth, tileHeight, ImageBitmapConfig.Rgb565)
        }

        // 计算tile在页面中的实际位置（rect已经是相对于页面的坐标）
        val tileX = rect.left.toInt()
        val tileY = rect.top.toInt()
        val tileWidth = rect.width.toInt()
        val tileHeight = rect.height.toInt()

        println("PdfiumDecoder.renderPageRegion:index:$index, scale:$scale, tile:$tileX-$tileY-$tileWidth-$tileHeight, bounds:$rect")

        val bitmap: Bitmap = BitmapPool.acquire(tileWidth, tileHeight)
        decode(index, scale, bitmap, tileX, tileY, false)

        return (bitmap.asImageBitmap())
    }

    public override fun getStructuredText(index: Int): Any? {
        // Android PdfRenderer不支持结构化文本提取
        return null
    }

    /**
     * 解析PDF页面为reflow内容（文本和图片）
     * 注意：Android PdfRenderer不支持文本提取
     */
    public fun decodeReflowItem(pageIndex: Int): List<ReflowBean> {
        // Android PdfRenderer不支持文本提取
        println("PdfiumDecoder: Android PdfRenderer不支持文本提取功能")
        return emptyList()
    }

    /**
     * 解析单个页面的文本内容（用于TTS快速启动）
     */
    public override fun decodeReflowSinglePage(pageIndex: Int): ReflowBean? {
        // Android PdfRenderer不支持文本提取
        println("PdfiumDecoder: Android PdfRenderer不支持文本提取功能")
        return null
    }

    /**
     * 解析所有页面的文本内容（用于TTS后台缓存）
     */
    public override fun decodeReflowAllPages(): List<ReflowBean> {
        // Android PdfRenderer不支持文本提取
        println("PdfiumDecoder: Android PdfRenderer不支持文本提取功能")
        return emptyList()
    }

    /**
     * 获取页面，Android PdfRenderer不允许缓存页面，每次都需要新建页面
     */
    private fun getPage(index: Int): PdfRenderer.Page {
        return pdfRenderer!!.openPage(index)
    }

    /**
     * 优先尝试从ImageCache/BitmapPool复用Bitmap
     */
    private fun acquireReusableBitmap(width: Int, height: Int): Bitmap {
        // 优先从BitmapPool获取，这样可以复用已回收的bitmap
        return BitmapPool.acquire(width, height)
    }
}
