package com.archko.reader.pdf.subsampling.internal.tile

import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.component.Size

internal fun ImageRegionTileGrid.Companion.generate(
    scaleFactor: ScaleFactor,
    viewportSize: IntSize,
    unscaledImageSize: IntSize,
): ImageRegionTileGrid {
    val baseSampleSize = ImageSampleSize.calculateFor(
        viewportSize = viewportSize,
        scaledImageSize = unscaledImageSize
    )

    val baseTile = ImageRegionTile(
        scale = scaleFactor,
        index = 0,
        sampleSize = baseSampleSize,
        bounds = IntRect(IntOffset.Zero, unscaledImageSize)
    )

    val possibleSampleSizes = generateSequence(seed = baseSampleSize) { current ->
        if (current.size < 2) null else current / 2
    }.drop(1) // Skip base size.

    val foregroundTiles = possibleSampleSizes.associateWith { sampleSize ->
        val scale = scaleFactor.scaleX
        val pageWidth = scale * unscaledImageSize.width
        val pageHeight = scale * unscaledImageSize.height
        val cols: Int = (pageWidth / PART_SIZE).toInt()
        val rows: Int = (pageHeight / PART_SIZE).toInt()
        val partWidth = pageWidth / cols
        val partHeight = pageHeight / rows
        println("getPageColsRows:$scaleFactor, Page.w-h:$pageWidth-$pageHeight, Part:w-h:$partWidth-$partHeight, rows-cols:$rows-$cols")

        val tileGrid = ArrayList<ImageRegionTile>(rows * cols)
        for (x in 0 until cols) {
            for (y in 0 until rows) {
                val tile = ImageRegionTile(
                    scale = scaleFactor,
                    index = 0,
                    sampleSize = sampleSize,
                    bounds = IntRect(
                        left = (x * partWidth).toInt(),
                        top = (y * partHeight).toInt(),
                        right = ((x + 1) * partWidth).toInt(),
                        bottom = ((y + 1) * partHeight).toInt(),
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

public const val PART_SIZE: Int = 512

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
