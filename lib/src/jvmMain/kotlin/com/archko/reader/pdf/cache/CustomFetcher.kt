package com.archko.reader.pdf.cache

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import com.archko.reader.image.DjvuLoader
import com.archko.reader.pdf.decoder.DjvuDecoder
import com.archko.reader.pdf.decoder.PdfDecoder
import com.archko.reader.pdf.entity.CustomImageData
import com.archko.reader.pdf.util.FileTypeUtils
import com.artifex.mupdf.fitz.Document
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO

public class CustomImageFetcher(
    private val data: CustomImageData,
    private val options: Options
) : Fetcher {
    public companion object {

        public fun cacheBitmap(image: ImageBitmap?, path: String) {
            if (null == image) {
                return
            }

            val cacheDir = FileUtils.getImageCacheDirectory()
            val cacheFile = File(cacheDir, "${path.hashCode()}.png")

            // 将ImageBitmap转换为BufferedImage，然后使用ImageIO保存为PNG
            val bufferedImage = image.toAwtImage()
            ImageIO.write(bufferedImage, "PNG", cacheFile)
        }

        public fun deleteCache(path: String?) {
            if (path == null) {
                return
            }

            // 删除磁盘缓存
            val cacheDir = FileUtils.getImageCacheDirectory()
            val cacheFile = File(cacheDir, "${path.hashCode()}.png")
            if (cacheFile.exists()) {
                cacheFile.delete()
            }
        }

        private fun loadImageFromCache(data: CustomImageData): ImageBitmap? {
            val cacheDir = FileUtils.getImageCacheDirectory()

            val cacheFile = File(cacheDir, "${data.path.hashCode()}.png")

            if (cacheFile.exists()) {
                try {
                    // 使用ImageIO读取磁盘缓存的图片文件
                    val bufferedImage = ImageIO.read(cacheFile)
                    if (bufferedImage != null) {
                        // 将BufferedImage转换为ImageBitmap
                        val image = bufferedImage.toComposeImageBitmap()
                        return image
                    }
                } catch (e: Exception) {
                    System.err.println("Error loading cached image: ${e.message}")
                    // 如果读取失败，删除损坏的缓存文件
                    cacheFile.delete()
                }
            }

            return null
        }

        public fun createWhiteBitmap(width: Int, height: Int): ImageBitmap {
            val whiteImage = BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR)
            val graphics = whiteImage.createGraphics()
            graphics.color = java.awt.Color.WHITE
            graphics.fillRect(0, 0, width, height)
            graphics.dispose()
            return whiteImage.toComposeImageBitmap()
        }
    }

    override suspend fun fetch(): FetchResult? {
        return try {
            var bitmap = loadImageFromCache(data)
            if (bitmap == null) {
                val file = File(data.path)
                if (!file.exists()) {
                    return ImageFetchResult(
                        image = createWhiteBitmap(data.width, data.height).asSkiaBitmap().asImage(),
                        isSampled = false,
                        dataSource = DataSource.DISK
                    )
                }

                if (FileTypeUtils.isDjvuFile(data.path)) {
                    val djvuLoader = DjvuLoader()
                    djvuLoader.openDjvu(data.path)
                    val image =
                        DjvuDecoder.renderCoverPage(djvuLoader, data.width, data.height)
                    bitmap = image
                    if (image != null) {
                        bitmap = image
                        cacheBitmap(bitmap, data.path)
                    }
                } else if (FileTypeUtils.isDocumentFile(data.path)) {
                    val doc = Document.openDocument(data.path)
                    val image = PdfDecoder.renderCoverPage(
                        data.path,
                        doc.loadPage(0),
                        data.width,
                        data.height
                    )

                    if (image != null) {
                        bitmap = image
                        cacheBitmap(bitmap, data.path)
                    }
                }
            }
            if (bitmap == null) {
                // 创建白色背景的 bitmap
                bitmap = createWhiteBitmap(data.width, data.height)
            }

            ImageFetchResult(
                image = bitmap.asSkiaBitmap().asImage(),
                isSampled = false,
                dataSource = DataSource.DISK
            )
        } catch (_: IOException) {
            null
        }
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
