package com.archko.reader.pdf.util

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import kotlin.math.max
import kotlin.math.min

/**
 * 切边工具类
 * 移植自amupdf-android项目的CropUtils
 * @author: archko 2025/1/20
 */
public object CropUtils {

    /** 最小宽度/高度，如果bitmap太小则不切边 */
    private const val MIN_WIDTH = 30
    
    /** 扫描步进 */
    private const val LINE_SIZE = 6
    private const val V_LINE_SIZE = 8
    
    /** 边距留白 */
    private const val LINE_MARGIN = 4
    
    /** 白色阈值，这个值越小表示忽略的空间越大 */
    private const val WHITE_THRESHOLD = 0.004

    /**
     * 检测图片的切边区域
     * @param bitmap 要检测的图片
     * @return 切边区域，如果不需要切边则返回null
     */
    public fun detectCropBounds(bitmap: ImageBitmap): Rect? {
        if (bitmap.height < MIN_WIDTH || bitmap.width < MIN_WIDTH) {
            return null
        }

        val avgLum = 225f // 固定阈值，效果比计算平均值要好
        
        val left = getLeftBound(bitmap, avgLum)
        val right = getRightBound(bitmap, avgLum)
        val top = getTopBound(bitmap, avgLum)
        val bottom = getBottomBound(bitmap, avgLum)

        // 转换为绝对坐标
        val leftPx = (left * bitmap.width).toInt()
        val topPx = (top * bitmap.height).toInt()
        val rightPx = (right * bitmap.width).toInt()
        val bottomPx = (bottom * bitmap.height).toInt()

        // 检查是否需要切边
        if (leftPx <= 0 && topPx <= 0 && rightPx >= bitmap.width && bottomPx >= bitmap.height) {
            return null // 不需要切边
        }

        return Rect(
            left = leftPx.toFloat(),
            top = topPx.toFloat(),
            right = rightPx.toFloat(),
            bottom = bottomPx.toFloat()
        )
    }

    private fun getLeftBound(bitmap: ImageBitmap, avgLum: Float): Float {
        val w = bitmap.width / 3
        var whiteCount = 0
        var x = 0
        for (x in 0 until w step LINE_SIZE) {
            val white = isRectWhite(bitmap, x, LINE_MARGIN, x + LINE_SIZE, bitmap.height - LINE_MARGIN, avgLum)
            if (white) {
                whiteCount++
            } else {
                if (whiteCount >= 1) {
                    return max(0, x - LINE_SIZE).toFloat() / bitmap.width
                }
                whiteCount = 0
            }
        }
        return if (whiteCount > 0) max(0, x - LINE_SIZE).toFloat() / bitmap.width else 0f
    }

    private fun getTopBound(bitmap: ImageBitmap, avgLum: Float): Float {
        val h = bitmap.height / 3
        var whiteCount = 0
        var y = 0
        for (y in 0 until h step V_LINE_SIZE) {
            val white = isRectWhite(bitmap, LINE_MARGIN, y, bitmap.width - LINE_MARGIN, y + LINE_SIZE, avgLum)
            if (white) {
                whiteCount++
            } else {
                if (whiteCount >= 1) {
                    return max(0, y - V_LINE_SIZE).toFloat() / bitmap.height
                }
                whiteCount = 0
            }
        }
        return if (whiteCount > 0) max(0, y - V_LINE_SIZE).toFloat() / bitmap.height else 0f
    }

    private fun getRightBound(bitmap: ImageBitmap, avgLum: Float): Float {
        val w = bitmap.width / 3
        var whiteCount = 0
        var x = 0
        for (x in (bitmap.width - LINE_SIZE) downTo (bitmap.width - w) step LINE_SIZE) {
            val white = isRectWhite(bitmap, x, LINE_MARGIN, x + LINE_SIZE, bitmap.height - LINE_MARGIN, avgLum)
            if (white) {
                whiteCount++
            } else {
                if (whiteCount >= 1) {
                    return min(bitmap.width, x + 2 * LINE_SIZE).toFloat() / bitmap.width
                }
                whiteCount = 0
            }
        }
        return if (whiteCount > 0) min(bitmap.width, x + 2 * LINE_SIZE).toFloat() / bitmap.width else 1f
    }

    private fun getBottomBound(bitmap: ImageBitmap, avgLum: Float): Float {
        val h = bitmap.height * 2 / 3
        var whiteCount = 0
        var y = 0
        for (y in (bitmap.height - V_LINE_SIZE) downTo (bitmap.height - h) step V_LINE_SIZE) {
            val white = isRectWhite(bitmap, LINE_MARGIN, y, bitmap.width - LINE_MARGIN, y + LINE_SIZE, avgLum)
            if (white) {
                whiteCount++
            } else {
                if (whiteCount >= 1) {
                    return min(bitmap.height, y + 2 * V_LINE_SIZE).toFloat() / bitmap.height
                }
                whiteCount = 0
            }
        }
        return if (whiteCount > 0) min(bitmap.height, y + 2 * V_LINE_SIZE).toFloat() / bitmap.height else 1f
    }

    private fun isRectWhite(bitmap: ImageBitmap, l: Int, t: Int, r: Int, b: Int, avgLum: Float): Boolean {
        var count = 0
        val totalPixels = (r - l) * (b - t)
        
        // 使用ImageBitmap的toArgb8888()方法获取像素数据
        val pixels = IntArray(totalPixels)
        bitmap.readPixels(
            buffer = pixels,
            startX = l,
            startY = t,
            width = r - l,
            height = b - t
        )
        
        for (pixel in pixels) {
            val lum = getLuminance(pixel)
            if (lum < avgLum && (avgLum - lum) * 10 > avgLum) {
                count++
            }
        }
        
        return (count.toFloat() / totalPixels) < WHITE_THRESHOLD
    }

    private fun getLuminance(pixel: Int): Float {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        
        val min = minOf(r, g, b)
        val max = maxOf(r, g, b)
        return (2f * min + max) / 3f
    }
} 