package com.archko.reader.pdf.cache

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
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
import com.archko.reader.pdf.entity.CustomImageData
import com.archko.reader.pdf.util.FileTypeUtils
import com.artifex.mupdf.fitz.ColorSpace
import com.artifex.mupdf.fitz.Cookie
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.DrawDevice
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.Pixmap
import com.artifex.mupdf.fitz.Rect
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO

public class CustomImageFetcher(
    private val data: CustomImageData,
    private val options: Options
) : Fetcher {
    public companion object {
        private fun getCacheDirectory(): File {
            val osName = System.getProperty("os.name").lowercase()
            val cacheDir = if (osName.contains("mac")) {
                // macOS: ~/Library/Caches/com.archko.reader.viewer/image
                val userHome = System.getProperty("user.home")
                File(userHome, "Library/Caches/com.archko.reader.viewer/image")
            } else if (osName.contains("win")) {
                // Windows: %APPDATA%/com.archko.reader.viewer/cache/image
                val appData = System.getenv("APPDATA") ?: System.getProperty("user.home")
                File(appData, "com.archko.reader.viewer/cache/image")
            } else {
                // 其他系统：使用用户主目录下的隐藏应用目录
                val userHome = System.getProperty("user.home")
                File(userHome, ".kreader/cache/image")
            }
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            return cacheDir
        }

        public fun cacheBitmap(image: ImageBitmap?, path: String) {
            if (null == image) {
                return
            }
            // 内存缓存使用ImageCache存储ImageBitmap
            ImageCache.putPage(path, image)

            val cacheDir = getCacheDirectory()
            val cacheFile = File(cacheDir, "${path.hashCode()}.png")

            // 将ImageBitmap转换为BufferedImage，然后使用ImageIO保存为PNG
            val bufferedImage = image.toAwtImage()
            ImageIO.write(bufferedImage, "PNG", cacheFile)
        }

        public fun deleteCache(path: String?) {
            if (path == null) {
                return
            }
            // 删除内存缓存
            ImageCache.removePage(path)

            // 删除磁盘缓存
            val cacheDir = getCacheDirectory()
            val cacheFile = File(cacheDir, "${path.hashCode()}.png")
            if (cacheFile.exists()) {
                cacheFile.delete()
            }
        }

        private fun loadImageFromCache(data: CustomImageData): ImageBitmap? {
            // 先检查内存缓存
            val cachedImage = ImageCache.acquirePage(data.path)
            if (cachedImage != null) {
                return cachedImage.bitmap
            }

            // 检查磁盘缓存 - 使用包名作为缓存目录
            val cacheDir = getCacheDirectory()

            val cacheFile = File(cacheDir, "${data.path.hashCode()}.png")

            if (cacheFile.exists()) {
                try {
                    // 使用ImageIO读取磁盘缓存的图片文件
                    val bufferedImage = ImageIO.read(cacheFile)
                    if (bufferedImage != null) {
                        // 将BufferedImage转换为ImageBitmap
                        val image = bufferedImage.toComposeImageBitmap()

                        // 放入内存缓存
                        ImageCache.putPage(data.path, image)
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
            // 先检查缓存
            var bitmap = loadImageFromCache(data)
            if (bitmap == null) {
                if (FileTypeUtils.isDocumentFile(data.path)) {
                    val doc = Document.openDocument(data.path)
                    val image = renderPage(doc, 0, data.width, data.height)

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
                dataSource = DataSource.NETWORK
            )
        } catch (_: IOException) {
            null
        }
    }

    public fun renderPage(
        document: Document,
        index: Int,
        viewWidth: Int,
        viewHeight: Int
    ): ImageBitmap? {
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

        // 创建白色背景的 bitmap
        val whiteImage = createWhiteBitmap(data.width, data.height)
        return whiteImage
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