package com.archko.reader.pdf.subsampling.tile

import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import com.archko.reader.pdf.component.Size

internal fun ImageRegionTileGrid.Companion.generate(
    viewportSize: IntSize,
    unscaledImageSize: IntSize,
    minTileSize: IntSize = viewportSize / 2,
): ImageRegionTileGrid {
    val baseSampleSize = ImageSampleSize.calculateFor(
        viewportSize = viewportSize,
        scaledImageSize = unscaledImageSize
    )

    val scaleFactor = ScaleFactor(1f, 1f)
    val baseTile = ImageRegionTile(
        index = 0,
        scale = scaleFactor,
        sampleSize = baseSampleSize,
        bounds = IntRect(IntOffset.Zero, unscaledImageSize)
    )

    // Apart from the base layer, tiles are generated for all possible levels of
    // sample size ahead of time. This will save some allocation during zoom gestures.
    val possibleSampleSizes = generateSequence(seed = baseSampleSize) { current ->
        if (current.size < 2) null else current / 2
    }.drop(1) // Skip base size.

    val foregroundTiles = possibleSampleSizes.associateWith { sampleSize ->
        val tileSize: IntSize =
            (unscaledImageSize.toSize() * (sampleSize.size / baseSampleSize.size.toFloat()))
                .discardFractionalParts()
                .coerceIn(min = minTileSize, max = unscaledImageSize.coerceAtLeast(minTileSize))

        // Number of tiles can be fractional. To avoid this, the fractional
        // part is discarded and the last tiles on each axis are stretched
        // to cover any remaining space of the image.
        val xTileCount: Int = (unscaledImageSize.width / tileSize.width).coerceAtLeast(1)
        val yTileCount: Int = (unscaledImageSize.height / tileSize.height).coerceAtLeast(1)

        val tileGrid = ArrayList<ImageRegionTile>(xTileCount * yTileCount)
        for (x in 0 until xTileCount) {
            for (y in 0 until yTileCount) {
                val isLastXTile = x == xTileCount - 1
                val isLastYTile = y == yTileCount - 1
                val tile = ImageRegionTile(
                    index = 0,
                    scale = scaleFactor,
                    sampleSize = sampleSize,
                    bounds = IntRect(
                        left = x * tileSize.width,
                        top = y * tileSize.height,
                        // Stretch the last tiles to cover any remaining space.
                        right = if (isLastXTile) unscaledImageSize.width else (x + 1) * tileSize.width,
                        bottom = if (isLastYTile) unscaledImageSize.height else (y + 1) * tileSize.height,
                    )
                )
                tileGrid.add(tile)
            }
        }
        return@associateWith tileGrid
    }

    return ImageRegionTileGrid(
        base = baseTile,
        foreground = foregroundTiles,
    )
}

/** Calculates a [ImageSampleSize] for fitting a source image in its layout bounds. */
internal fun ImageSampleSize.Companion.calculateFor(
    viewportSize: IntSize,
    scaledImageSize: IntSize
): ImageSampleSize {
    check(viewportSize.minDimension > 0f) { "Can't calculate a sample size for $viewportSize" }

    val zoom = minOf(
        viewportSize.width / scaledImageSize.width.toFloat(),
        viewportSize.height / scaledImageSize.height.toFloat()
    )
    return calculateFor(zoom)
}

/** Calculates a [ImageSampleSize] for fitting a source image in its layout bounds. */
internal fun ImageSampleSize.Companion.calculateFor(zoom: Float): ImageSampleSize {
    if (zoom == 0f) {
        return ImageSampleSize(1)
    }

    var sampleSize = 1
    while (sampleSize * 2 <= (1 / zoom)) {
        // BitmapRegionDecoder requires values based on powers of 2.
        sampleSize *= 2
    }
    return ImageSampleSize(sampleSize)
}

private operator fun ImageSampleSize.div(other: ImageSampleSize) =
    ImageSampleSize(size / other.size)

private operator fun ImageSampleSize.div(other: Int) = ImageSampleSize(size / other)

/**
 * 为 PDF 文档生成 TileGrid，按页面计算 foregroundTiles
 */
internal fun ImageRegionTileGrid.Companion.generateForPdf(
    scaleFactor: ScaleFactor,
    viewportSize: IntSize,
    unscaledImageSize: IntSize,
    pageSizes: List<Size>,
    minTileSize: IntSize = viewportSize / 2,
): ImageRegionTileGrid {
    val baseSampleSize = ImageSampleSize.calculateFor(
        viewportSize = viewportSize,
        scaledImageSize = unscaledImageSize
    )

    // 创建基础 tile，覆盖整个 PDF
    val baseTile = ImageRegionTile(
        index = 0,
        scale = scaleFactor,
        sampleSize = baseSampleSize,
        bounds = IntRect(IntOffset.Zero, unscaledImageSize)
    )

    // 计算所有可能的采样大小
    val possibleSampleSizes = generateSequence(seed = baseSampleSize) { current ->
        if (current.size < 2) null else current / 2
    }.drop(1) // 跳过基础大小

    // 按页面计算 foregroundTiles
    val foregroundTiles = possibleSampleSizes.associateWith { sampleSize ->
        val allPageTiles = mutableListOf<ImageRegionTile>()

        // 计算每页的垂直偏移量
        var yOffset = 0

        // 为每一页生成 tiles
        for (pageIndex in pageSizes.indices) {
            val pageSize = pageSizes[pageIndex]
            val scaledPageWidth = (pageSize.width * pageSize.zoom).toInt()
            val scaledPageHeight = (pageSize.height * pageSize.zoom).toInt()

            val tileSize: IntSize =
                (IntSize(
                    scaledPageWidth,
                    scaledPageHeight
                ).toSize() * (sampleSize.size / baseSampleSize.size.toFloat()))
                    .discardFractionalParts()
                    .coerceIn(
                        min = minTileSize,
                        max = IntSize(scaledPageWidth, scaledPageHeight).coerceAtLeast(minTileSize)
                    )

            // 计算页面需要的 tile 数量
            val xTileCount: Int = (scaledPageWidth / tileSize.width).coerceAtLeast(1)
            val yTileCount: Int = (scaledPageHeight / tileSize.height).coerceAtLeast(1)

            // 为当前页面生成 tiles
            for (x in 0 until xTileCount) {
                for (y in 0 until yTileCount) {
                    val isLastXTile = x == xTileCount - 1
                    val isLastYTile = y == yTileCount - 1

                    val tile = ImageRegionTile(
                        index = pageIndex, // 使用页面索引
                        scale = scaleFactor,
                        sampleSize = sampleSize,
                        bounds = IntRect(
                            left = x * tileSize.width,
                            top = yOffset + y * tileSize.height,
                            // 拉伸最后的 tiles 以覆盖剩余空间
                            right = if (isLastXTile) scaledPageWidth else (x + 1) * tileSize.width,
                            bottom = if (isLastYTile) yOffset + scaledPageHeight else yOffset + (y + 1) * tileSize.height,
                        )
                    )
                    allPageTiles.add(tile)
                }
            }

            // 更新垂直偏移量，为下一页做准备
            yOffset += scaledPageHeight
        }

        allPageTiles
    }

    return ImageRegionTileGrid(
        base = baseTile,
        foreground = foregroundTiles,
    )
}
