package ovh.plrapps.mapcompose.core

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
    val row: Int,
    val col: Int,
    val subSample: Int,
    val width: Int = 512,
    val height: Int = 512,
) {
    var bitmap: Bitmap? = null
    var alpha: Float by mutableFloatStateOf(0f)
}

internal data class TileSpec(
    val zoom: Float,
    val row: Int, val col: Int,
    val subSample: Int = 0,
    val width: Int = 512,
    val height: Int = 512
)

internal fun Tile.sameSpecAs(
    zoom: Float,
    row: Int,
    col: Int,
    subSample: Int,
    width: Int = 512,
    height: Int = 512,
): Boolean {
    return this.zoom == zoom && this.row == row && this.col == col && this.subSample == subSample
            && this.width == width && this.height == height
}
