package com.archko.reader.pdf.util

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
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

    public fun drawableToBitmap(drawable: Drawable): Bitmap // drawable 转换成 bitmap
    {
        val width = drawable.getIntrinsicWidth() // 取 drawable 的长宽
        val height = drawable.getIntrinsicHeight()
        val config =
            if (drawable.getOpacity() != PixelFormat.OPAQUE) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565 // 取 drawable 的颜色格式
        val bitmap = Bitmap.createBitmap(width, height, config) // 建立对应 bitmap
        val canvas = Canvas(bitmap) // 建立对应 bitmap 的画布
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas) // 把 drawable 内容画到画布中
        return bitmap
    }

    public fun zoomDrawable(drawable: Drawable, w: Int, h: Int): Drawable {
        val width = drawable.getIntrinsicWidth()
        val height = drawable.getIntrinsicHeight()
        val oldbmp = drawableToBitmap(drawable) // drawable 转换成 bitmap
        val matrix = Matrix() // 创建操作图片用的 Matrix 对象
        val scaleWidth = (w.toFloat() / width) // 计算缩放比例
        val scaleHeight = (h.toFloat() / height)
        matrix.postScale(scaleWidth, scaleHeight) // 设置缩放比例
        val newbmp = Bitmap.createBitmap(
            oldbmp,
            0,
            0,
            width,
            height,
            matrix,
            true
        ) // 建立新的 bitmap ，其内容是对原 bitmap 的缩放后的图
        return BitmapDrawable(newbmp) // 把 bitmap 转换成 drawable 并返回
    }

    public fun zoomDrawable(bitmap: Bitmap, w: Int, h: Int): Drawable {
        val width = bitmap.getWidth()
        val height = bitmap.getHeight()
        val matrix = Matrix() // 创建操作图片用的 Matrix 对象
        val scaleWidth = (w.toFloat() / width) // 计算缩放比例
        val scaleHeight = (h.toFloat() / height)
        matrix.postScale(scaleWidth, scaleHeight) // 设置缩放比例
        val newbmp = Bitmap.createBitmap(
            bitmap,
            0,
            0,
            width,
            height,
            matrix,
            true
        ) // 建立新的 bitmap ，其内容是对原 bitmap 的缩放后的图
        val bitmapDrawable = BitmapDrawable(newbmp) // 把 bitmap 转换成 drawable 并返回
        bitmapDrawable.setBounds(0, 0, w, h)
        return bitmapDrawable
    }

    private val zoom = 160f / 72

    public fun getDrawable(bitmap: Bitmap, screenWidth: Int): Drawable {
        var width = bitmap.getWidth() * zoom
        var height = bitmap.getHeight() * zoom
        if (width > screenWidth) {
            val ratio = screenWidth / width
            height = ratio * height
            width = screenWidth.toFloat()
        }
        return zoomDrawable(bitmap, width.toInt(), height.toInt())
    }

    public fun decodeFile(file: File): Bitmap? {
        return BitmapFactory.decodeFile(file.getAbsolutePath())
    }
}
