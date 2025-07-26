package com.archko.reader.pdf.component

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize

/**
 * @author: archko 2025/7/26 :10:58
 */
public data class TileSpec(
    val page: Int,
    val pageScale: Float,
    val bounds: Rect, // 0~1
    val pageWidth: Int,
    val pageHeight: Int,
    val viewSize: IntSize,
    val cacheKey: String,
    var imageBitmap: ImageBitmap?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TileSpec

        if (page != other.page) return false
        if (pageScale != other.pageScale) return false
        if (pageWidth != other.pageWidth) return false
        if (pageHeight != other.pageHeight) return false
        if (bounds != other.bounds) return false
        if (cacheKey != other.cacheKey) return false

        return true
    }

    override fun hashCode(): Int {
        var result = page
        result = 31 * result + pageScale.hashCode()
        result = 31 * result + pageWidth
        result = 31 * result + pageHeight
        result = 31 * result + bounds.hashCode()
        result = 31 * result + cacheKey.hashCode()
        return result
    }
}