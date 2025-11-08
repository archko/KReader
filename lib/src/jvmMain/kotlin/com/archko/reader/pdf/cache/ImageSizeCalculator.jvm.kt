package com.archko.reader.pdf.cache

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import java.awt.image.BufferedImage

/**
 * JVM平台的ImageBitmap处理器
 */
internal actual object ImageSizeCalculator {
    /**
     * 计算ImageBitmap的内存占用大小（字节）
     * 使用BufferedImage的类型信息计算
     */
    actual fun calculateImageSize(image: ImageBitmap): Long {
        return try {
            val bufferedImage = image.toAwtImage() as BufferedImage
            val width = bufferedImage.width
            val height = bufferedImage.height
            val type = bufferedImage.type

            val bytesPerPixel = when (type) {
                BufferedImage.TYPE_INT_RGB -> 4
                BufferedImage.TYPE_INT_ARGB -> 4
                BufferedImage.TYPE_INT_ARGB_PRE -> 4
                BufferedImage.TYPE_3BYTE_BGR -> 3
                BufferedImage.TYPE_4BYTE_ABGR -> 4
                BufferedImage.TYPE_4BYTE_ABGR_PRE -> 4
                BufferedImage.TYPE_USHORT_565_RGB -> 2
                BufferedImage.TYPE_USHORT_555_RGB -> 2
                BufferedImage.TYPE_BYTE_GRAY -> 1
                BufferedImage.TYPE_USHORT_GRAY -> 2
                else -> {
                    // 对于其他类型，使用ColorModel来计算
                    val colorModel = bufferedImage.colorModel
                    val pixelSize = colorModel.pixelSize
                    (pixelSize + 7) / 8 // 转换为字节数，向上取整
                }
            }

            (width * height * bytesPerPixel).toLong()
        } catch (e: Exception) {
            // 如果获取失败，使用估算值
            val pixels = image.width.toLong() * image.height.toLong()
            pixels * 4L // 默认ARGB_8888
        }
    }

    /**
     * 回收ImageBitmap，释放底层资源
     * JVM平台主要依赖GC，这里可以做一些清理工作
     */
    actual fun recycleImageBitmap(image: ImageBitmap) {
        try {
            // JVM平台主要依赖垃圾回收
            // 这里可以做一些额外的清理工作，比如清除引用等
            // 但通常不需要特殊处理，GC会自动回收

            // 如果需要，可以尝试释放BufferedImage的资源
            val bufferedImage = image.toAwtImage() as? BufferedImage
            bufferedImage?.flush()
        } catch (e: Exception) {
            // 如果回收失败，忽略错误
            println("ImageSizeCalculator.recycleImageBitmap: JVM回收失败 - ${e.message}")
        }
    }
}