package com.archko.reader.pdf.cache

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap

/**
 * Android平台的ImageBitmap处理器
 */
internal actual object ImageSizeCalculator {
    /**
     * 计算ImageBitmap的内存占用大小（字节）
     * 使用Android Bitmap的getByteCount()方法
     */
    actual fun calculateImageSize(image: ImageBitmap): Long {
        return try {
            val androidBitmap = image.asAndroidBitmap()
            androidBitmap.byteCount.toLong()
        } catch (e: Exception) {
            val pixels = image.width.toLong() * image.height.toLong()
            pixels * 4L // 默认ARGB_8888
        }
    }

    /**
     * 回收ImageBitmap，释放底层资源
     * Android端将Bitmap放入BitmapPool进行复用
     */
    actual fun recycleImageBitmap(image: ImageBitmap) {
        val androidBitmap = image.asAndroidBitmap()
        BitmapPool.release(androidBitmap)
    }
}