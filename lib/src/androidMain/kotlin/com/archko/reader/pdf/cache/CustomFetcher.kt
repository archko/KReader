package com.archko.reader.pdf.cache

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import com.archko.reader.pdf.PdfApp
import com.archko.reader.pdf.component.Size
import com.archko.reader.pdf.entity.CustomImageData
import com.archko.reader.pdf.util.BitmapUtils
import com.archko.reader.pdf.util.FileTypeUtils
import com.artifex.mupdf.fitz.Cookie
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.Page
import com.artifex.mupdf.fitz.android.AndroidDrawDevice
import java.io.File
import java.nio.ByteBuffer

public class CustomImageFetcher(
    private val data: CustomImageData,
    private val options: Options
) : Fetcher {

    public companion object {
        public fun cacheBitmap(bitmap: Bitmap?, path: String) {
            if (null == bitmap) {
                return
            }
            ImageCache.putPage(path, bitmap.asImageBitmap())
            val dir = PdfApp.app!!.externalCacheDir
            val cacheDir = File(dir, "image")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val filePath = "${cacheDir.absolutePath}/${path.hashCode()}"
            val bmp = Bitmap.createBitmap(
                bitmap.width,
                bitmap.height,
                bitmap.config!!
            )
            bitmap.eraseColor(android.graphics.Color.WHITE)
            val buffer = ByteBuffer.allocate(bitmap.getByteCount())
            bitmap.copyPixelsToBuffer(buffer)
            buffer.position(0)
            bmp.copyPixelsFromBuffer(buffer)
            BitmapUtils.saveBitmapToFile(bmp, File(filePath))
        }
        
        public fun deleteCache(path: String?) {
            if (path==null){
                return
            }
            // 删除内存缓存
            ImageCache.removePage(path)
            
            // 删除磁盘缓存
            val dir = PdfApp.app!!.externalCacheDir
            val cacheDir = File(dir, "image")
            val filePath = "${cacheDir.absolutePath}/${path.hashCode()}"
            val cacheFile = File(filePath)
            if (cacheFile.exists()) {
                cacheFile.delete()
            }
        }

        private fun loadBitmapFromCache(data: CustomImageData): Bitmap? {
            val bmp = ImageCache.acquirePage(data.path)
            if (null != bmp) {
                return bmp.bitmap.asAndroidBitmap()
            }
            val dir = PdfApp.app!!.externalCacheDir
            val cacheDir = File(dir, "image")
            val key = "${cacheDir.absolutePath}/${data.path.hashCode()}"
            val bitmap = BitmapFactory.decodeFile(key)
            return bitmap
        }

        public fun createWhiteBitmap(width: Int, height: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            return bitmap
        }
    }

    override suspend fun fetch(): FetchResult {
        var bitmap = loadBitmapFromCache(data)
        if (bitmap == null) {
            if (FileTypeUtils.isDocumentFile(data.path)) {
                //bitmap = decodePdfSys()
                bitmap = decodeMuPdf()
                if (bitmap != null) {
                    cacheBitmap(bitmap, data.path)
                }
            }
        }

        if (bitmap == null) {
            bitmap = createWhiteBitmap(data.width, data.height)
        }

        val imageBitmap: BitmapImage = bitmap.asImage()

        return ImageFetchResult(
            image = imageBitmap,
            dataSource = DataSource.MEMORY,
            isSampled = false
        )
    }

    private fun decodeMuPdf(): Bitmap? {
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
            val cropDev = AndroidDrawDevice(cropBitmap, 0, 0, 0, 0, cropWidth.toInt(), cropHeight.toInt())
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
            val dev =
                AndroidDrawDevice(scaledBitmap, 0, 0, 0, 0, scaledBitmap.getWidth(), scaledBitmap.getHeight())
            page.run(dev, ctm, null as Cookie?)
            dev.close()
            dev.destroy()
            scaledBitmap
        }
    }

    private fun decodePdfSys(): Bitmap? {
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
    }

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