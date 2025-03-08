package com.archko.reader.pdf.cache

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
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

    private fun cacheBitmap(bitmap: Bitmap?) {
        if (null == bitmap) {
            return
        }
        BitmapCache.addBitmap(data.path, bitmap)
        val dir = PdfApp.app!!.externalCacheDir
        val cacheDir = File(dir, "image")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val path = "${cacheDir.absolutePath}/${data.path.hashCode()}"
        val bmp = Bitmap.createBitmap(
            bitmap.width,
            bitmap.height,
            bitmap.config!!
        )
        val buffer = ByteBuffer.allocate(bitmap.getByteCount())
        bitmap.copyPixelsToBuffer(buffer)
        buffer.position(0)
        bmp.copyPixelsFromBuffer(buffer)
        BitmapUtils.saveBitmapToFile(bmp, File(path))
    }

    private fun loadBitmapFromCache(): Bitmap? {
        var bmp = BitmapCache.getBitmap(data.path)
        if (null != bmp) {
            return bmp
        }
        val dir = PdfApp.app!!.externalCacheDir
        val cacheDir = File(dir, "image")
        val key = "${cacheDir.absolutePath}/${data.path.hashCode()}"
        bmp = BitmapFactory.decodeFile(key)
        return bmp
    }

    override suspend fun fetch(): FetchResult {
        var bitmap = loadBitmapFromCache()
        if (bitmap == null) {
            //bitmap = decodePdfSys()
            bitmap = decodeMuPdf()
        }

        if (bitmap == null) {
            bitmap = Bitmap.createBitmap(data.width, data.height, Bitmap.Config.RGB_565)
        } else {
            cacheBitmap(bitmap)
        }

        val imageBitmap: BitmapImage = bitmap.asImage()

        return ImageFetchResult(
            image = imageBitmap,
            dataSource = DataSource.MEMORY,
            isSampled = false
        )
    }

    private fun decodeMuPdf(): Bitmap? {
        var document: Document = Document.openDocument(data.path)

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
        val ctm = Matrix()
        val xscale = 1f * width / pWidth
        val yscale = 1f * height / pHeight
        var w: Int = width
        var h: Int = height
        if (xscale > yscale) {
            h = (pHeight * xscale).toInt()
        } else {
            w = (pWidth * yscale).toInt()
        }

        ctm.scale(xscale, yscale)
        val bitmap = BitmapPool.acquire(w, h)
        val dev =
            AndroidDrawDevice(bitmap, 0, 0, 0, 0, bitmap.getWidth(), bitmap.getHeight())
        page.run(dev, ctm, null as Cookie?)
        dev.close()
        dev.destroy()
        return bitmap
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