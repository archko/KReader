package com.archko.reader.pdf.cache

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import com.archko.reader.pdf.PdfApp
import com.archko.reader.pdf.entity.CustomImageData
import com.archko.reader.pdf.util.BitmapUtils
import java.io.File
import java.nio.ByteBuffer

public class CustomImageFetcher(
    private val data: CustomImageData,
    private val options: Options
) : Fetcher {

    public companion object {

        private var cachedBitmap: Bitmap? = null

        public fun cacheBitmap(bitmap: Bitmap?, path: String) {
            if (null == bitmap) {
                return
            }
            val dir = PdfApp.app!!.externalCacheDir
            val cacheDir = File(dir, "image")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val fileName = File(path).name
            val filePath = "${cacheDir.absolutePath}/${fileName.hashCode()}"
            val bmp = Bitmap.createBitmap(
                bitmap.width,
                bitmap.height,
                bitmap.config!!
            )
            val buffer = ByteBuffer.allocate(bitmap.getByteCount())
            bitmap.copyPixelsToBuffer(buffer)
            buffer.position(0)
            bmp.copyPixelsFromBuffer(buffer)
            BitmapUtils.saveBitmapToFile(bmp, File(filePath))
        }

        public fun deleteCache(path: String?) {
            if (path == null) {
                return
            }

            // 删除磁盘缓存
            val dir = PdfApp.app!!.externalCacheDir
            val cacheDir = File(dir, "image")
            val fileName = File(path).name
            val filePath = "${cacheDir.absolutePath}/${fileName.hashCode()}"
            val cacheFile = File(filePath)
            if (cacheFile.exists()) {
                cacheFile.delete()
            }
        }

        private fun loadBitmapFromCache(data: CustomImageData): Bitmap? {
            val dir = PdfApp.app!!.externalCacheDir
            val cacheDir = File(dir, "image")
            val fileName = File(data.path).name
            val key = "${cacheDir.absolutePath}/${fileName.hashCode()}"
            val bitmap = BitmapFactory.decodeFile(key)
            return bitmap
        }

        public fun createWhiteBitmap(width: Int, height: Int): Bitmap {
            if (cachedBitmap == null) {
                cachedBitmap = createDefaultBookBitmap(width, height)
            }

            return cachedBitmap!!
        }

        public fun createDefaultBookBitmap(width: Int, height: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) // 使用 ARGB_8888 保证渐变平滑
            val canvas = Canvas(bitmap)

            // 1. 绘制背景渐变 (浅灰到白色，模拟自然光)
            val bgPaint = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    intArrayOf(0xFFF5F5F5.toInt(), 0xFFFFFFFF.toInt()),
                    null, Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            // 2. 绘制书脊阴影折痕 (左侧边缘)
            val spinePaint = Paint().apply {
                color = Color.parseColor("#E0E0E0")
                strokeWidth = (width / 50).toFloat()
            }
            canvas.drawLine(spinePaint.strokeWidth, 0f, spinePaint.strokeWidth, height.toFloat(), spinePaint)

            // 3. 准备字母 "B" 的画笔 (带特效)
            val textPaint = Paint().apply {
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC) // 使用衬线体更有书卷气
                textSize = (width / 2).toFloat()

                // 特效 A: 文字渐变色 (深灰到中灰)
                shader = LinearGradient(
                    0f, height/3f, 0f, height/1.5f,
                    intArrayOf(0xFF424242.toInt(), 0xFF9E9E9E.toInt()),
                    null, Shader.TileMode.CLAMP
                )

                // 特效 B: 添加淡淡的投影
                setShadowLayer(10f, 6f, 6f, Color.argb(70, 0, 0, 0))
            }

            // 4. 绘制文字
            val fontMetrics = textPaint.fontMetrics
            val x = (width / 2f)
            val y = (height / 2f) - (fontMetrics.ascent + fontMetrics.descent) / 2f
            canvas.drawText("B", x, y, textPaint)

            // 5. 可选：在下方画几条虚构的“作者名”横线，增加真实感
            val linePaint = Paint().apply {
                color = Color.parseColor("#EEEEEE")
                strokeWidth = 6f
            }
            val lineY = y + (height / 8f)
            canvas.drawLine(width/3f, lineY, width * 2/3f, lineY, linePaint)

            return bitmap
        }
    }

    override suspend fun fetch(): FetchResult {
        println("fetch:${data.path}")
        var bitmap = loadBitmapFromCache(data)
        if (bitmap == null) {
            val file = File(data.path)
            if (!file.exists()) {
                return ImageFetchResult(
                    image = createWhiteBitmap(data.width, data.height).asImage(),
                    isSampled = false,
                    dataSource = DataSource.DISK
                )
            }
            /*if (FileTypeUtils.isDjvuFile(data.path)) {
                val djvuLoader = DjvuLoader()
                djvuLoader.openDjvu(data.path)
                val image =
                    DjvuDecoder.renderCoverPage(djvuLoader, data.width, data.height)
                if (image != null) {
                    bitmap = image.asAndroidBitmap()
                    cacheBitmap(bitmap, data.path)
                }
            } else if (FileTypeUtils.isDocumentFile(data.path)) {
                val doc = Document.openDocument(data.path)
                bitmap =
                    PdfDecoder.renderCoverPage(data.path, doc.loadPage(0), data.width, data.height)

                if (bitmap != null) {
                    cacheBitmap(bitmap, data.path)
                }
            }*/
        }

        if (bitmap == null) {
            bitmap = createWhiteBitmap(data.width, data.height)
        }

        return ImageFetchResult(
            image = bitmap.asImage(),
            dataSource = DataSource.DISK,
            isSampled = false
        )
    }

    /*private fun decodeMuPdf(): Bitmap? {
        val document: Document = Document.openDocument(data.path)

        val bitmap = if (document.countPages() > 0)
            renderPdfPage(
                document.loadPage(0),
                data.width,
                data.height
            ) else null
        return bitmap
    }

    private fun renderPdfPage(page: Page, width: Int, height: Int): Bitmap {
        val pWidth = page.bounds.x1 - page.bounds.x0
        val pHeight = page.bounds.y1 - page.bounds.y0
        val xscale = 1f * width / pWidth
        val yscale = 1f * height / pHeight

        // For images with aspect ratio less than 1 (taller than wide), we crop from top-left
        return if (pWidth / pHeight < 1f) {
            // Crop to width x width from top-left
            val cropWidth = minOf(pWidth, pHeight)
            val cropHeight = cropWidth
            val cropBitmap = BitmapPool.acquire(cropWidth.toInt(), cropHeight.toInt())
            val cropDev =
                AndroidDrawDevice(cropBitmap, 0, 0, 0, 0, cropWidth.toInt(), cropHeight.toInt())
            val cropCtm = Matrix()
            cropCtm.scale(1f, 1f)
            page.run(cropDev, cropCtm, null as Cookie?)
            cropDev.close()
            cropDev.destroy()
            cropBitmap
        } else {
            // Original scaling logic for other images
            var w: Int = width
            var h: Int = height
            if (xscale > yscale) {
                h = (pHeight * xscale).toInt()
            } else {
                w = (pWidth * yscale).toInt()
            }

            val ctm = Matrix()
            ctm.scale(xscale, yscale)
            val scaledBitmap = BitmapPool.acquire(w, h)
            val dev = AndroidDrawDevice(
                scaledBitmap,
                0,
                0,
                0,
                0,
                scaledBitmap.getWidth(),
                scaledBitmap.getHeight()
            )
            page.run(dev, ctm, null as Cookie?)
            dev.close()
            dev.destroy()
            scaledBitmap
        }
    }*/

    /*private fun decodePdfSys(): Bitmap? {
        val parcelFileDescriptor =
            ParcelFileDescriptor.open(File(data.path), ParcelFileDescriptor.MODE_READ_ONLY)
        val pdfRenderer = PdfRenderer(parcelFileDescriptor)

        val bitmap = if (pdfRenderer.pageCount > 0)
            renderPdfPageSys(
                pdfRenderer.openPage(0),
                data.width,
                data.height
            ) else null
        return bitmap
    }

    private fun renderPdfPageSys(page: PdfRenderer.Page, width: Int, height: Int): Bitmap {
        val size = caculateSize(page.width, page.height, width, height)

        val bitmap = BitmapPool
            .acquire(size.width, size.height, Bitmap.Config.ARGB_8888)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        return bitmap
    }

    private fun caculateSize(pWidth: Int, pHeight: Int, tWidth: Int, tHeight: Int): Size {
        val xscale = 1f * tWidth / pWidth
        val yscale = 1f * tHeight / pHeight
        var w: Int = tWidth
        var h: Int = tHeight
        if (xscale > yscale) {
            h = (pHeight * xscale).toInt()
        } else {
            w = (pWidth * yscale).toInt()
        }
        return Size(w, h, 0)
    }*/

    public class Factory : Fetcher.Factory<CustomImageData> {

        override fun create(
            data: CustomImageData,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher {
            return CustomImageFetcher(data, options)
        }
    }
}