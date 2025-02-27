package com.archko.reader.pdf.util

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import coil3.Bitmap
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import com.archko.reader.pdf.entity.CustomImageData
import com.artifex.mupdf.fitz.ColorSpace
import com.artifex.mupdf.fitz.Cookie
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.DrawDevice
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.Pixmap
import com.artifex.mupdf.fitz.Rect
import org.jetbrains.skia.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.imageio.ImageIO

public class CustomImageFetcher(
    private val data: CustomImageData,
    private val options: Options
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        return try {
            val doc = Document.openDocument(data.path)
            val image = renderPage(doc, 0, data.width, data.height)
            val outputStream = ByteArrayOutputStream()
            val awtImage = image.toAwtImage()
            ImageIO.write(awtImage, "png", outputStream)
            val byteArray = outputStream.toByteArray()

            val skiaImage = Image.makeFromEncoded(byteArray)
            val coilImage = Bitmap.makeFromImage(skiaImage).asImage()

            ImageFetchResult(
                image = coilImage,
                isSampled = false,
                dataSource = DataSource.MEMORY
            )
        } catch (e: IOException) {
            null
        }
    }

    public fun renderPage(
        document: Document,
        index: Int,
        viewWidth: Int,
        viewHeight: Int
    ): ImageBitmap {
        val page = document.loadPage(index)
        val bounds = page.bounds
        val scale: Float
        if (viewWidth > 0) {
            scale = (1f * viewWidth / (bounds.x1 - bounds.x0))
        } else {
            return ImageBitmap(viewWidth, viewHeight, ImageBitmapConfig.Rgb565)
        }
        println("renderPage:index:$index, scale:$scale, $viewWidth-$viewHeight, bounds:${page.bounds}")
        val ctm: Matrix = Matrix.Scale(scale)

        /* Render page to an RGB pixmap without transparency. */
        val bmp: ImageBitmap?
        try {
            val bbox: Rect = Rect(bounds).transform(ctm)
            val pixmap = Pixmap(ColorSpace.DeviceBGR, bbox, true)
            pixmap.clear(255)
            val dev = DrawDevice(pixmap)
            page.run(dev, ctm, Cookie())
            dev.close()
            dev.destroy()

            val pixmapWidth = pixmap.width
            val pixmapHeight = pixmap.height
            val image = BufferedImage(pixmapWidth, pixmapHeight, BufferedImage.TYPE_3BYTE_BGR)
            image.setRGB(0, 0, pixmapWidth, pixmapHeight, pixmap.pixels, 0, pixmapWidth)
            bmp = image.toComposeImageBitmap()
            return bmp
        } catch (e: Exception) {
            System.err.println(("Error loading page " + (index + 1)) + ": " + e)
        }

        return ImageBitmap(viewWidth, viewHeight, ImageBitmapConfig.Rgb565)
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