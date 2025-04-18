package ovh.plrapps.mapcompose.core

import com.archko.reader.pdf.subsampling.PdfDecoder
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * Resolves the visible tiles.
 * This class isn't thread-safe, and public methods should be invoked from the same thread to ensure
 * consistency.
 *
 * @param levelCount Number of levels
 * @param fullWidth Width of the map at scale 1.0f
 * @param fullHeight Height of the map at scale 1.0f
 * @param magnifyingFactor Alters the level at which tiles are picked for a given scale. By default,
 * the level immediately higher (in index) is picked, to avoid sub-sampling. This corresponds to a
 * [magnifyingFactor] of 0. The value 1 will result in picking the current level at a given scale,
 * which will be at a relative scale between 1.0 and 2.0
 * @param scaleProvider Since the component which invokes [getVisibleTiles] isn't likely to be the
 * component which owns the scale state, we provide it here as a loosely coupled reference.
 *
 * @author p-lr on 25/05/2019
 */
internal class VisibleTilesResolver(
    private val decoder: PdfDecoder,
    private val fullWidth: Int,
    private val fullHeight: Int,
    private val tileSize: Int = 512,
    var magnifyingFactor: Int = 0,
    private val scaleProvider: ScaleProvider
) {

    fun getScale(): Float {
        return scaleProvider.getScale()
    }

    /**
     * Get the [VisibleTiles], given the visible area in pixels.
     *
     * @param viewport The [Viewport] which represents the visible area. Its values depend on the
     * scale.
     */
    fun getVisibleTiles(viewport: Viewport): VisibleTiles {
        val scale = scaleProvider.getScale()
        val relativeScale = scale

        /* At the current level, row and col index have maximum values */
        val maxCol = max(0.0f, ceil(1f * fullWidth / tileSize) - 1).toInt()
        val maxRow = max(0.0f, ceil(1f * fullHeight / tileSize) - 1).toInt()
        println("state.getVisibleTiles.scale:$scale, relativeScale:$relativeScale, col-row:$maxCol-$maxRow")

        fun Int.lowerThan(limit: Int): Int {
            return if (this <= limit) this else limit
        }

        val scaledTileSize = tileSize.toDouble() * relativeScale

        fun makeVisibleTiles(left: Int, top: Int, right: Int, bottom: Int): VisibleTiles {
            val colLeft = floor(left / scaledTileSize).toInt().lowerThan(maxCol).coerceAtLeast(0)
            val rowTop = floor(top / scaledTileSize).toInt().lowerThan(maxRow).coerceAtLeast(0)
            val colRight = (ceil(right / scaledTileSize).toInt() - 1).lowerThan(maxCol)
            val rowBottom = (ceil(bottom / scaledTileSize).toInt() - 1).lowerThan(maxRow)

            val tileMatrix = (rowTop..rowBottom).associateWith {
                colLeft..colRight
            }
            val count = (rowBottom - rowTop + 1) * (colRight - colLeft + 1)
            println("VisibleTiles:$colLeft, $rowTop, $colRight, $rowBottom, $count, left:$left-$top-$right-$bottom $scale, $scaledTileSize, $tileMatrix")
            return VisibleTiles(scale, tileMatrix, count, 0)
        }

        return makeVisibleTiles(viewport.left, viewport.top, viewport.right, viewport.bottom)
    }

    fun interface ScaleProvider {
        fun getScale(): Float
    }
}

/**
 * Properties container for the computed visible tiles.
 * @param level 0-based level index
 * @param tileMatrix contains all (row, col) indexes, grouped by rows
 * @param count the precomputed total count
 * @param subSample the current sub-sample factor. If the current scale of the [VisibleTilesResolver]
 * is lower than the scale of the minimum level, [subSample] is greater than 0. Otherwise, [subSample]
 * equals 0.
 */
internal data class VisibleTiles(
    val scale: Float,
    val tileMatrix: TileMatrix,
    val count: Int,
    val subSample: Int = 0
)

internal typealias Row = Int
internal typealias ColRange = IntRange
internal typealias TileMatrix = Map<Row, ColRange>