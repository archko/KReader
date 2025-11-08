package com.archko.reader.pdf.entity

import androidx.compose.ui.geometry.Rect

/**
 * @author: archko 2025/3/11 :13:56
 */
public data class APage(
    val index: Int,
    val width: Int,
    val height: Int,
    var scale: Float = 1f,
    var cropBounds: Rect? = null  // 切边区域结果
) {

    /**
     * 获取页面宽度
     * @param useCrop 是否使用切边后的尺寸
     * @return 页面宽度
     */
    public fun getWidth(useCrop: Boolean): Float {
        return if (useCrop && cropBounds != null) {
            cropBounds!!.width
        } else {
            width.toFloat()
        }
    }

    /**
     * 获取页面高度
     * @param useCrop 是否使用切边后的尺寸
     * @return 页面高度
     */
    public fun getHeight(useCrop: Boolean): Float {
        return if (useCrop && cropBounds != null) {
            cropBounds!!.height
        } else {
            height.toFloat()
        }
    }

    /**
     * 检查是否有切边
     * @return 是否有切边
     */
    public fun hasCrop(): Boolean = cropBounds != null
}