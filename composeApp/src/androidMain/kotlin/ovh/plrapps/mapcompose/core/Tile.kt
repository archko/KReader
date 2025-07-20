package ovh.plrapps.mapcompose.core

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * A [Tile] is defined by its coordinates in the "pyramid". A [Tile] is sub-sampled when the
 * scale becomes lower than the scale of the lowest level. To reflect that, there is [subSample]
 * property which is a positive integer (can be 0). When [subSample] equals 0, the [bitmap] of the
 * tile is full scale. When [subSample] equals 1, the [bitmap] is sub-sampled and its size is half
 * the original bitmap (the one at the lowest level), and so on.
 */
internal data class Tile(
    val zoom: Float,
    val level: Int,
    val pageIndex: Int, // 页码
    val pageOffsetX: Int, // 页面在文档中的X偏移
    val pageOffsetY: Int, // 页面在文档中的Y偏移
) {
    var bitmap: Bitmap? = null
}

internal data class TileSpec(
    val zoom: Float, 
    val level: Int,
    val pageIndex: Int = 0, // 页码
    val pageOffsetX: Int = 0, // 页面在文档中的X偏移
    val pageOffsetY: Int = 0  // 页面在文档中的Y偏移
)

internal fun Tile.sameSpecAs(
    zoom: Float,
    level: Int,
    pageIndex: Int,
    pageOffsetX: Int,
    pageOffsetY: Int,
): Boolean {
    return this.zoom == zoom
            && this.level == level
            && this.pageIndex == pageIndex
            && this.pageOffsetX == pageOffsetX
            && this.pageOffsetY == pageOffsetY
}
