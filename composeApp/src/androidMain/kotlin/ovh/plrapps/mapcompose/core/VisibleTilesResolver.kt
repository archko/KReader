package ovh.plrapps.mapcompose.core

import com.archko.reader.pdf.component.Size
import com.archko.reader.pdf.subsampling.PdfDecoder
import kotlin.math.floor
import kotlin.math.min

/**
 * Resolves the visible tiles.
 * This class isn't thread-safe, and public methods should be invoked from the same thread to ensure
 * consistency.
 *
 * @param decoder PdfDecoder instance for page information
 * @param fullWidth Width of the map at scale 1.0f (total width of all pages)
 * @param fullHeight Height of the map at scale 1.0f (total height of all pages)
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
    private val levelCount: Int,
    private val fullWidth: Int,
    private val fullHeight: Int,
    private val tileSize: Int = 256,
    var magnifyingFactor: Int = 0,
    private val scaleProvider: ScaleProvider
) {

    /**
     * Returns the level based on current scale
     */
    internal fun getLevel(scale: Float, magnifyingFactor: Int = 0): Int {
        if (scale < 1.0) {
            println("getLevel: scale=$scale, level=0")
            return 0
        }
        val level = floor(scale.toDouble()).toInt()
        val result = min(level, levelCount - 1)
        return result
    }

    /**
     * Get the [VisibleTiles], given the visible area in pixels.
     */
    fun getVisibleTiles(viewport: Viewport): VisibleTiles {
        val scale = scaleProvider.getScale()
        val level = getLevel(scale, magnifyingFactor)

        fun makeVisibleTiles(left: Int, top: Int, right: Int, bottom: Int): VisibleTiles {
            println("makeVisibleTiles: scale=$scale, level=$level, viewport=($left,$top,$right,$bottom)")

            val visiblePages = mutableListOf<Size>()
            for (i in decoder.pageSizes.indices) {
                val pageSize = decoder.pageSizes[i]
                if (pageSize.offsetHeight >= top) {
                    if (pageSize.offsetHeight < bottom) {
                        println("makeVisibleTiles: visiblePage=$pageSize")
                        visiblePages.add(pageSize)
                    }
                }
                if (pageSize.offsetHeight > bottom) {
                    break
                }
            }

            // 收集所有可见的 tile
            val visibleTiles = mutableListOf<TileSpec>()
            for (pageSize in visiblePages) {
                val pageWidth = pageSize.width
                val pageHeight = pageSize.height
                val offsetHeight = pageSize.offsetHeight

                // tile 分割
                val cols = (pageWidth / tileSize)
                val rows = (pageHeight / tileSize)
                val tileWidth = pageWidth / cols
                val tileHeight = pageHeight / rows

                for (row in 0 .. rows) {
                    for (col in 0 .. cols) {
                        val tileLeft = col * tileWidth
                        val tileTop = row * tileHeight + offsetHeight
                        val tileRight =
                            if (cols == 0) pageWidth else if (col == cols - 1) pageWidth else (col + 1) * tileWidth
                        val tileBottom =
                            if (rows == 0) pageHeight else if (row == rows - 1) pageHeight else (row + 1) * tileHeight + offsetHeight

                        // 判断 tile 是否与可见区域相交 - 使用文档坐标系
                        val isVisible = (tileLeft <= right) and
                                (tileRight >= left) and
                                (tileTop <= bottom) and
                                (tileBottom >= top)

                        if (isVisible) {
                            val spec = TileSpec(
                                pageSize,
                                zoom = scale * pageSize.scale,
                                level = level,
                                pageIndex = pageSize.page,
                                pageOffsetX = tileLeft,
                                pageOffsetY = tileTop,
                                tileWidth = tileRight - tileLeft,
                                tileHeight = tileBottom - tileTop
                            )
                            println("visibleTiles: add.page=${pageSize.page}, ($tileLeft, $tileTop, ${tileRight}, ${tileBottom}), row=$row, col=$col, tile=$tileWidth-$tileHeight, page:$pageWidth-$pageHeight, ${pageSize.offsetHeight}")
                            visibleTiles.add(spec)
                        } else {
                            println("visibleTiles: page=${pageSize.page},isVisible=$isVisible, $tileRight>$left, $tileLeft<$right, $tileBottom>$top, $tileTop<$bottom")
                        }
                    }
                }
            }
            return VisibleTiles(level, visibleTiles, visibleTiles.size, scale)
        }
        return makeVisibleTiles(viewport.left, viewport.top, viewport.right, viewport.bottom)
    }

    fun getScale(): Float {
        return scaleProvider.getScale()
    }

    fun getPageStart(pageIndex: Int): Int {
        return (0 until pageIndex).sumOf { decoder.pageSizes[it].height }
    }

    fun interface ScaleProvider {
        fun getScale(): Float
    }
}

/**
 * Properties container for the computed visible tiles.
 * @param level 0-based level index
 * @param visibleTiles list of visible tile specifications
 * @param count the precomputed total count
 * @param subSample the current sub-sample factor. If the current scale of the [VisibleTilesResolver]
 * is lower than the scale of the minimum level, [subSample] is greater than 0. Otherwise, [subSample]
 * equals 0.
 */
internal data class VisibleTiles(
    val level: Int,
    val visibleTiles: List<TileSpec>,
    val count: Int,
    val scale: Float = 0f
)