package com.archko.reader.pdf.subsampling.tile

import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize

internal fun ImageTileGrid.Companion.generate(
    scaleFactor: ScaleFactor,
    viewportSize: IntSize,
    unscaledImageSize: IntSize,
): ImageTileGrid {
    val baseTile = ImageTile(
        scale = scaleFactor,
        index = 0,
        bounds = IntRect(IntOffset.Zero, unscaledImageSize)
    )

    val scale = scaleFactor.scaleX
    val pageWidth = scale * unscaledImageSize.width
    val pageHeight = scale * unscaledImageSize.height
    val cols: Int = (pageWidth / PART_SIZE).toInt()
    val rows: Int = (pageHeight / PART_SIZE).toInt()
    val partWidth = pageWidth / cols
    val partHeight = pageHeight / rows
    println("getPageColsRows:$scale, Page.w-h:$pageWidth-$pageHeight, Part:w-h:$partWidth-$partHeight, rows-cols:$rows-$cols")

    val tileGrid = ArrayList<ImageTile>(rows * cols)
    for (x in 0 until cols) {
        for (y in 0 until rows) {
            val tile = ImageTile(
                scale = scaleFactor,
                index = 0,
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

    val foregroundTiles = mutableMapOf<ScaleFactor, List<ImageTile>>()
    foregroundTiles[scaleFactor] = tileGrid

    return ImageTileGrid(
        base = baseTile,
        foreground = foregroundTiles,
    )
}

public const val PART_SIZE: Int = 256 * 3