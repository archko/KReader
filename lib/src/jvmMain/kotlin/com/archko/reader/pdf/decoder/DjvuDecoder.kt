package com.archko.reader.pdf.decoder

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntSize
import java.awt.image.BufferedImage
import com.archko.reader.image.DjvuLoader
import com.archko.reader.pdf.cache.APageSizeLoader
import com.archko.reader.pdf.cache.APageSizeLoader.PageSizeBean
import com.archko.reader.pdf.cache.CustomImageFetcher
import com.archko.reader.pdf.cache.ImageCache
import com.archko.reader.pdf.component.Size
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.APage
import com.archko.reader.pdf.entity.Hyperlink
import com.archko.reader.pdf.entity.Item
import com.archko.reader.pdf.entity.ReflowCacheBean
import com.archko.reader.pdf.util.SmartCropUtils
import com.artifex.mupdf.fitz.Page
import java.io.File

/**
 * @author: archko 2025/11/9 :6:26
 */
public class DjvuDecoder(public val file: File) : ImageDecoder {

    public override var pageCount: Int = 0

    // 私有变量存储原始页面尺寸
    public override var originalPageSizes: List<Size> = listOf()

    // 对外提供的缩放后页面尺寸
    public override var pageSizes: List<Size> = listOf()

    public override var outlineItems: List<Item>? = listOf()

    public override var imageSize: IntSize = IntSize.Zero

    public var viewSize: IntSize = IntSize.Zero

    // 页面缓存，最多缓存8页
    private val pageCache = mutableMapOf<Int, Page>()
    private val maxPageCache = 8

    public override val aPageList: MutableList<APage>? = ArrayList()
    private var pageSizeBean: PageSizeBean? = null
    private var cachePage = true
    public var cacheBean: ReflowCacheBean? = null

    // 链接缓存，避免重复解析
    private val linksCache = mutableMapOf<Int, List<Hyperlink>>()
    private var djvuLoader: DjvuLoader? = null

    init {
        if (!file.exists()) {
            throw IllegalArgumentException("文档文件不存在: ${file.absolutePath}")
        }

        if (!file.canRead()) {
            throw SecurityException("无法读取文档文件: ${file.absolutePath}")
        }

        djvuLoader = DjvuLoader()

        originalPageSizes = prepareSizes()
        outlineItems = prepareOutlines()

        initPageSizeBean()
        cacheCoverIfNeeded()
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
        /*val path = file.absolutePath
        try {
            if (null != ImageCache.acquirePage(path)) {
                return
            }
            val page = djvuLoader!!.getPageInfo(0)
            val bitmap = renderCoverPage(page)

            CustomImageFetcher.cacheBitmap(bitmap, path)
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
            var totalHeight = 0f  // 使用浮点数累积，避免舍入误差

            // 计算缩放后的页面尺寸
            val scaledPageSizes = mutableListOf<Size>()

            for (i in originalPageSizes.indices) {
                val originalPage = originalPageSizes[i]
                // 计算每页的缩放比例，使宽度等于viewportSize.width
                val scale = 1f * documentWidth / originalPage.width
                val scaledWidth = documentWidth
                val scaledHeight = originalPage.height * scale  // 保持浮点数精度

                // 创建缩放后的页面尺寸，yOffset使用当前累积的浮点数值转换为整数
                val scaledPage =
                    Size(scaledWidth, scaledHeight.toInt(), i, scale, totalHeight.toInt())
                scaledPageSizes.add(scaledPage)
                totalHeight += scaledHeight  // 浮点数累积
            }

            // 更新对外提供的页面尺寸
            pageSizes = scaledPageSizes
            imageSize = IntSize(documentWidth, totalHeight.toInt())
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

        ImageCache.clear()
    }

    private fun prepareSizes(): List<Size> {
        val list = mutableListOf<Size>()

        djvuLoader!!.openDjvu(file.absolutePath)
        val djvuInfo = djvuLoader!!.djvuInfo

        if (djvuInfo == null) {
            println("Failed to get DjVu info")
            return list
        }

        var totalHeight = 0
        pageCount = djvuInfo.pages
        println("DjVu document has $pageCount pages")

        for (i in 0 until pageCount) {
            val pageInfo = djvuLoader!!.getPageInfo(i)
            if (pageInfo != null) {
                val size = Size(
                    pageInfo.width,
                    pageInfo.height,
                    i,
                    scale = 1.0f,
                    totalHeight,
                )
                totalHeight += size.height
                list.add(size)
            }
        }
        return list
    }

    private fun prepareOutlines(): List<Item> {
        return emptyList()
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

        if (djvuLoader == null) {
            return emptyList()
        }

        return emptyList()
    }

    private fun decodePageLinks(pageIndex: Int): List<Hyperlink> {
        // 先检查缓存
        if (linksCache.containsKey(pageIndex)) {
            return linksCache[pageIndex]!!
        }
        return linksCache[pageIndex]!!
    }

    public override fun renderPageRegion(
        region: Rect,
        index: Int,
        scale: Float,
        viewSize: IntSize,
        outWidth: Int,
        outHeight: Int
    ): ImageBitmap {
        return try {
            val pageWidth = (region.width / scale).toInt()
            val pageHeight = (region.height / scale).toInt()
            val patchX = (region.left / scale).toInt()
            val patchY = (region.top / scale).toInt()
            //精度的问题,jni那边不支持小数进位四舍五入,所以这里的高宽要修正一下.
            val bitmap = djvuLoader!!.decodeRegionToBitmap(
                index,
                patchX,
                patchY,
                pageWidth,
                pageHeight,
                scale,
            )

            println("PdfDecoder.renderPageRegion:index:$index, scale:$scale, patch:$patchX-$patchY-page:$pageWidth-$pageHeight, region:$region")

            bitmap?.toComposeImageBitmap() ?: ImageBitmap(outWidth, outHeight, ImageBitmapConfig.Rgb565)
        } catch (e: Exception) {
            println("renderPageRegion error for file ${file.absolutePath}: $e")
            ImageBitmap(outWidth, outHeight, ImageBitmapConfig.Rgb565)
        }
    }

    override fun renderPage(
        aPage: APage,
        viewSize: IntSize,
        outWidth: Int,
        outHeight: Int,
        crop: Boolean
    ): ImageBitmap {
        try {
            val index = aPage.index
            val originalSize = originalPageSizes[index]

            if (aPage.cropBounds != null && crop) {
                // 有切边信息且启用切边
                val cropBounds = aPage.cropBounds!!

                val scaleX = outWidth.toFloat() / cropBounds.width
                val scaleY = outHeight.toFloat() / cropBounds.height
                val scale = minOf(scaleX, scaleY)

                println("DjvuDecoder.renderPage:croped page=$index, $outWidth-$outHeight, 切边后尺寸=${(scale * cropBounds.width).toInt()}x${(scale * cropBounds.height).toInt()}, bounds=$cropBounds")

                val bitmap = djvuLoader!!.decodeRegionToBitmap(
                    index,
                    cropBounds.left.toInt(),
                    cropBounds.top.toInt(),
                    cropBounds.width.toInt(),
                    cropBounds.height.toInt(),
                    scale,
                )

                return bitmap?.toComposeImageBitmap() ?: ImageBitmap(
                    outWidth,
                    outHeight,
                    ImageBitmapConfig.Rgb565
                )
            } else {
                // 没有切边信息或未启用切边
                val scaleX = outWidth.toFloat() / originalSize.width
                val scaleY = outHeight.toFloat() / originalSize.height
                val scale = minOf(scaleX, scaleY)

                println("DjvuDecoder.renderPage:page=$index, 目标尺寸=$outWidth-$outHeight, 原始=${originalSize.width}x${originalSize.height}, 缩放=$scale")

                val bitmap = djvuLoader!!.decodeRegionToBitmap(
                    index,
                    0,
                    0,
                    originalSize.width,
                    originalSize.height,
                    scale,
                )

                val imageBitmap = bitmap?.toComposeImageBitmap() ?: ImageBitmap(
                    outWidth,
                    outHeight,
                    ImageBitmapConfig.Rgb565
                )

                // 如果启用了切边功能但没有cropBounds，检测并设置
                if (crop && bitmap != null) {
                    val cropBounds = SmartCropUtils.detectSmartCropBounds(imageBitmap)
                    if (cropBounds != null) {
                        // 将缩略图坐标转换为原始DjVu坐标
                        val ratio = originalSize.width.toFloat() / imageBitmap.width

                        val leftBound = (cropBounds.left * ratio)
                        val topBound = (cropBounds.top * ratio)
                        val rightBound = (cropBounds.right * ratio)
                        val bottomBound = (cropBounds.bottom * ratio)
                        val djvuCropBounds = Rect(
                            leftBound,
                            topBound,
                            rightBound,
                            bottomBound
                        )

                        println("DjvuDecoder.cropBounds:$index, 原始尺寸=${originalSize.width}x${originalSize.height}, 切边区域=($cropBounds), 切边后尺寸=${djvuCropBounds}")

                        if (djvuCropBounds.width < 0 || djvuCropBounds.height < 0) {
                            aPage.cropBounds = Rect(
                                0f,
                                0f,
                                imageBitmap.width.toFloat(),
                                imageBitmap.height.toFloat()
                            )
                            return imageBitmap
                        }
                        aPage.cropBounds = djvuCropBounds

                        // 真正对图片进行切边处理
                        val croppedBitmap = cropImageBitmap(index, imageBitmap, cropBounds)
                        return croppedBitmap
                    }
                }

                return imageBitmap
            }

        } catch (e: Exception) {
            println("DjvuDecoder.renderPage error: $e")
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
        decodeLink: Boolean
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

        println("DjvuDecoder.cropImageBitmap:$index, 原始尺寸=${originalBitmap.width}x${originalBitmap.height}, 切边区域=($safeX,$safeY,$safeWidth,$safeHeight)")

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

            val croppedImageBitmap = croppedImage.toComposeImageBitmap()
            println("DjvuDecoder.cropImageBitmap: 切边后尺寸=${croppedImageBitmap.width}x${croppedImageBitmap.height}")

            return croppedImageBitmap
        } catch (e: Exception) {
            println("DjvuDecoder.cropImageBitmap error: $e")
            // 如果切边失败，返回原始图片
            return originalBitmap
        }
    }

    override fun getStructuredText(index: Int): Any? {
        return null
    }
}