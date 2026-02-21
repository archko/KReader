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
import com.archko.reader.pdf.entity.CustomImageData
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.geom.Line2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO

public class CustomImageFetcher(
    private val data: CustomImageData,
    private val options: Options
) : Fetcher {
    public companion object {

        private var cacheImage: BufferedImage? = null

        public fun cacheBitmap(image: ImageBitmap?, path: String) {
            if (null == image) {
                return
            }

            val cacheDir = FileUtils.getImageCacheDirectory()
            val fileName = File(path).name
            val cacheFile = File(cacheDir, "${fileName.hashCode()}.png")

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
            val fileName = File(path).name
            val cacheFile = File(cacheDir, "${fileName.hashCode()}.png")
            if (cacheFile.exists()) {
                cacheFile.delete()
            }
        }

        private fun loadImageFromCache(data: CustomImageData): ImageBitmap? {
            val cacheDir = FileUtils.getImageCacheDirectory()
            val fileName = File(data.path).name
            val cacheFile = File(cacheDir, "${fileName.hashCode()}.png")

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
            if (cacheImage == null) {
                cacheImage = createDefaultBookImage(width, height)
            }

            return cacheImage!!.toComposeImageBitmap()
        }

        public fun createDefaultBookImage(width: Int, height: Int): BufferedImage {
            val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            val g2 = image.createGraphics()

            // 开启抗锯齿，保证渲染质量
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON
            )

            // 2. 绘制背景渐变
            val bgGradient = GradientPaint(
                0f, 0f, Color(0xF5F5F5),
                width.toFloat(), height.toFloat(), Color.WHITE
            )
            g2.setPaint(bgGradient)
            g2.fillRect(0, 0, width, height)

            // 3. 绘制书脊阴影折痕
            val spineWidth = width / 50.0f
            g2.color = Color(0xE0E0E0)
            g2.stroke = BasicStroke(spineWidth)
            g2.draw(Line2D.Float(spineWidth, 0f, spineWidth, height.toFloat()))

            // 4. 绘制带阴影和渐变的字母 "B"
            val text = "B"
            val font = Font(Font.SERIF, Font.BOLD or Font.ITALIC, width / 2)
            g2.font = font

            // 获取文字测量信息以居中
            val fm: FontMetrics = g2.fontMetrics
            val textBounds: Rectangle2D = fm.getStringBounds(text, g2)
            val x = (width - textBounds.width) / 2
            val y: Double = (height - textBounds.height) / 2 + fm.ascent

            // 特效 B: 模拟阴影 (Java 2D 没有简单的 setShadowLayer，通常先画一层深色偏移)
            g2.color = Color(0, 0, 0, 40)
            g2.drawString(text, x.toFloat() + 4, y.toFloat() + 4)

            // 特效 A: 文字渐变色
            val textGradient = GradientPaint(
                0f, height.toFloat() / 3, Color(0x424242),
                0f, height.toFloat() * 1.5f / 2, Color(0x9E9E9E)
            )
            g2.setPaint(textGradient)
            g2.drawString(text, x.toFloat(), y.toFloat())

            // 5. 绘制底部的“作者名”横线
            g2.paint = Color(0xEEEEEE)
            g2.stroke = BasicStroke(6f)
            val lineY = y.toFloat() + (height / 8f)
            g2.draw(Line2D.Float(width / 3f, lineY, width * 2 / 3f, lineY))

            g2.dispose()
            return image
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

                /*if (FileTypeUtils.isDjvuFile(data.path)) {
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
                }*/
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
