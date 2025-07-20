package ovh.plrapps.mapcompose.core

import com.archko.reader.pdf.subsampling.PdfDecoder
import kotlin.math.*

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

    // 计算每页在总高度中的位置
    val pagePositions: List<Int> by lazy {
        val positions = mutableListOf<Int>()
        var currentY = 0
        for (i in decoder.pageSizes.indices) {
            val pageSize = decoder.pageSizes[i]
            positions.add(currentY)
            //println("pagePositions: page $i starts at $currentY, height=${pageSize.height}")
            currentY += pageSize.height
        }
        println("pagePositions: total height=$currentY, positions=$positions")
        positions
    }

    /**
     * Returns the level based on current scale
     * Level 0: scale < 1.0
     * Level 1: 1.0 <= scale < 2.0
     * Level 2: 2.0 <= scale < 4.0
     * etc.
     */
    internal fun getLevel(scale: Float, magnifyingFactor: Int = 0): Int {
        if (scale < 1.0) {
            println("getLevel: scale=$scale, level=0")
            return 0
        }
        
        // 简单的向下取整计算level
        val level = floor(scale.toDouble()).toInt()
        val result = min(level, levelCount - 1)
        return result
    }

    /**
     * Get the [VisibleTiles], given the visible area in pixels.
     *
     * @param viewport The [Viewport] which represents the visible area. Its values depend on the
     * scale.
     */
    fun getVisibleTiles(viewport: Viewport): VisibleTiles {
        val scale = scaleProvider.getScale()
        val level = getLevel(scale, magnifyingFactor)

        println("getVisibleTiles: scale=$scale, level=$level, viewport=(${viewport.left},${viewport.top},${viewport.right},${viewport.bottom})")

        fun makeVisibleTiles(left: Int, top: Int, right: Int, bottom: Int): VisibleTiles {
            // viewport坐标已经是基于当前scale的，需要转换回文档坐标系
            val docLeft = floor(left / scale).toInt()
            val docTop = floor(top / scale).toInt()
            val docRight = ceil(right / scale).toInt()
            val docBottom = ceil(bottom / scale).toInt()

            //println("makeVisibleTiles: docLeft=$docLeft, docTop=$docTop, docRight=$docRight, docBottom=$docBottom")

            // 计算可见的页面范围
            val visiblePages = mutableListOf<Int>()
            for (i in pagePositions.indices) {
                val pageStart = pagePositions[i]
                val pageEnd = if (i < pagePositions.size - 1) pagePositions[i + 1] else fullHeight
                val pageHeight = decoder.pageSizes[i].height
                val pageWidth = decoder.pageSizes[i].width
                
                // 检查页面是否与可见区域相交
                if (docTop < pageEnd && docBottom > pageStart) {
                    visiblePages.add(i)
                    println("makeVisibleTiles: page $i is visible, pageStart=$pageStart, pageEnd=$pageEnd, pageWidth=$pageWidth, pageHeight=$pageHeight")
                }
            }

            // 收集所有可见的tile
            val visibleTiles = mutableListOf<TileSpec>()

            for (pageIndex in visiblePages) {
                val pageStart = pagePositions[pageIndex]
                val pageWidth = decoder.pageSizes[pageIndex].width
                val pageHeight = decoder.pageSizes[pageIndex].height
                
                // 计算页面在文档中的可见区域
                val pageVisibleTop = max(docTop, pageStart)
                val pageVisibleBottom = min(docBottom, pageStart + pageHeight)
                val pageVisibleLeft = max(docLeft, 0)
                val pageVisibleRight = min(docRight, pageWidth)
                
                //println("makeVisibleTiles: page $pageIndex visible rect:(left=$pageVisibleLeft, right=$pageVisibleRight, top=$pageVisibleTop, bottom=$pageVisibleBottom)")
                
                // 计算页面内可见的tile范围
                val pageVisibleLeftInPage = pageVisibleLeft - 0  // 页面在文档中的x偏移是0
                val pageVisibleRightInPage = pageVisibleRight - 0
                val pageVisibleTopInPage = pageVisibleTop - pageStart
                val pageVisibleBottomInPage = pageVisibleBottom - pageStart
                
                // 计算页面需要的tile数量（按最大tileSize划分）
                val cols = ceil(pageWidth / tileSize.toDouble()).toInt()
                val rows = ceil(pageHeight / tileSize.toDouble()).toInt()
                
                // 计算每个tile的实际宽高，最后一个tile使用页面边界避免精度问题
                val tileWidth = tileSize
                val tileHeight = tileSize
                
                // 计算可见区域对应的tile范围
                val colLeft = floor(pageVisibleLeftInPage.toDouble() / tileWidth).toInt().coerceAtLeast(0)
                val colRight = (ceil(pageVisibleRightInPage.toDouble() / tileWidth).toInt() - 1).coerceAtMost(cols - 1)
                val rowTop = floor(pageVisibleTopInPage.toDouble() / tileHeight).toInt().coerceAtLeast(0)
                val rowBottom = (ceil(pageVisibleBottomInPage.toDouble() / tileHeight).toInt() - 1).coerceAtMost(rows - 1)
                
                //println("makeVisibleTiles: page $pageIndex tile calculation: cols=$cols, rows=$rows, tile=$tileWidth-$tileHeight, visible tiles:colLeft=$colLeft, colRight=$colRight, rowTop=$rowTop, rowBottom=$rowBottom")
                //println("makeVisibleTiles: page $pageIndex tile calculation: pageVisibleRect=($pageVisibleLeftInPage, $pageVisibleTopInPage, $pageVisibleRightInPage, $pageVisibleBottomInPage")
                //println("makeVisibleTiles: page $pageIndex tile calculation: pageWidth=$pageWidth, pageHeight=$pageHeight, tileSize=$tileSize, maxCols=${ceil((pageWidth / tileSize).toDouble()).toInt()}, maxRows=${ceil((pageHeight / tileSize).toDouble()).toInt()}")

                // 创建页面内的tile
                for (rowInPage in rowTop..rowBottom) {
                    for (col in colLeft..colRight) {
                        // 计算tile的实际边界，最后一个tile使用页面边界避免精度问题
                        val tileLeft = col * tileWidth
                        val tileTop = rowInPage * tileHeight
                        val tileRight = if (col == cols - 1) pageWidth else (col + 1) * tileWidth
                        val tileBottom = if (rowInPage == rows - 1) pageHeight else (rowInPage + 1) * tileHeight
                        
                        val tileSpec = TileSpec(
                            zoom = scale,
                            level = level,
                            pageIndex = pageIndex,
                            pageOffsetX = tileLeft,
                            pageOffsetY = tileTop,
                            tileWidth = tileRight - tileLeft,
                            tileHeight = tileBottom - tileTop
                        )
                        visibleTiles.add(tileSpec)
                        //println("makeVisibleTiles: created tile spec=$tileSpec, bounds=($tileLeft,$tileTop,$tileRight,$tileBottom)")
                    }
                }
            }

            println("makeVisibleTiles: final visibleTiles count=${visibleTiles.size}, visibleTiles details:")
            visibleTiles.forEach { spec ->
                println("  - spec=$spec")
            }
            return VisibleTiles(level, visibleTiles, visibleTiles.size, scale)
        }

        return makeVisibleTiles(viewport.left, viewport.top, viewport.right, viewport.bottom)
    }

    /**
     * Get the current scale from the scale provider
     */
    fun getScale(): Float {
        return scaleProvider.getScale()
    }

    /**
     * Get the start position of a page in the document
     */
    fun getPageStart(pageIndex: Int): Int {
        return if (pageIndex == 0) 0 else {
            pagePositions[pageIndex]
        }
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