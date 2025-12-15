package com.archko.reader.pdf.util

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.BitmapFactory
import android.os.Environment
import android.util.Base64
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

/**
 * @author: archko 2025/2/26 :14:05
 */
public object BitmapUtils {
    @JvmOverloads
    public fun saveBitmapToFile(
        bitmap: Bitmap?,
        file: File,
        format: CompressFormat = CompressFormat.JPEG,
        quality: Int = 100
    ): Boolean {
        if (null == bitmap) {
            return false
        }
        var fos: FileOutputStream? = null
        var bos: BufferedOutputStream? = null
        var baos: ByteArrayOutputStream? = null

        try {
            if (file.exists()) {
                file.delete()
            } else {
                val parent = file.getParentFile()
                if (!parent!!.exists()) {
                    parent.mkdirs()
                }
                file.createNewFile()
            }

            baos = ByteArrayOutputStream()
            bitmap.compress(format, quality, baos)
            val byteArray = baos.toByteArray() // 字节数组输出流转换成字节数组
            // 将字节数组写入到刚创建的图片文件中
            fos = FileOutputStream(file)
            bos = BufferedOutputStream(fos)
            bos.write(byteArray)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            StreamUtils.closeStream(baos)
            StreamUtils.closeStream(bos)
            StreamUtils.closeStream(fos)
        }
    }

    public fun base64ToBitmap(str: String?): Bitmap? {
        try {
            val bytes = Base64.decode(str, Base64.DEFAULT)
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    public var i: Int = 0

    public fun saveBitmapToSDCard(bitmap: Bitmap) {
        var fos: FileOutputStream? = null
        try {
            fos = FileOutputStream(
                Environment.getExternalStorageDirectory().getPath() + "/" + (i++) + ".jpg"
            )
            bitmap.compress(CompressFormat.JPEG, 100, fos)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        }
    }
}
