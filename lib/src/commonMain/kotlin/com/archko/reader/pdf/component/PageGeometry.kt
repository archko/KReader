package com.archko.reader.pdf.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 * 页面内容几何。内容空间 = 原始页坐标中切边后的区域。
 * version/id 由内容矩形决定，切边开关与懒切边探测都通过它触发失效。
 */
internal class PageGeometry(
    val naturalWidth: Float,
    val naturalHeight: Float,
    val cropBounds: Rect?,
    val cropEnabled: Boolean
) {
    val left: Float
    val top: Float
    val width: Float
    val height: Float

    init {
        val cb = cropBounds
        if (cropEnabled && cb != null && cb.width > 0f && cb.height > 0f) {
            left = cb.left
            top = cb.top
            width = cb.width
            height = cb.height
        } else {
            left = 0f
            top = 0f
            width = naturalWidth.coerceAtLeast(1f)
            height = naturalHeight.coerceAtLeast(1f)
        }
    }

    val aspect: Float get() = width / height

    val id: Int
        get() = idHash

    private val idHash: Int = run {
        var result = left.hashCode()
        result = 31 * result + top.hashCode()
        result = 31 * result + width.hashCode()
        result = 31 * result + height.hashCode()
        result
    }

    fun originalToDoc(x: Float, y: Float, base: Rect): Offset {
        return Offset(
            base.left + (x - left) / width * base.width,
            base.top + (y - top) / height * base.height
        )
    }

    fun originalToDocY(y: Float, base: Rect): Float {
        return base.top + (y - top) / height * base.height
    }

    fun docToOriginal(x: Float, y: Float, base: Rect): Offset {
        return Offset(
            left + (x - base.left) / base.width * width,
            top + (y - base.top) / base.height * height
        )
    }

    fun originalRectToDoc(rect: Rect, base: Rect): Rect {
        val left = base.left + (rect.left - this.left) / width * base.width
        val top = base.top + (rect.top - this.top) / height * base.height
        val right = base.left + (rect.right - this.left) / width * base.width
        val bottom = base.top + (rect.bottom - this.top) / height * base.height
        return Rect(left, top, right, bottom)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        other as PageGeometry
        return left == other.left && top == other.top &&
                width == other.width && height == other.height
    }

    override fun hashCode(): Int = idHash

    override fun toString(): String {
        return "PageGeometry(content=$left,$top,$width,$height, crop=$cropEnabled)"
    }
}
