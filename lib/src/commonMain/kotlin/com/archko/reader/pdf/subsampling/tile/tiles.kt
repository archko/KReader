@file:Suppress("DataClassPrivateConstructor")

package com.archko.reader.pdf.subsampling.tile

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.unit.IntRect

/** A region in the source image that will be drawn in a [ViewportTile]. */
@Immutable
public data class ImageTile(
    val scale: ScaleFactor,
    val index: Int,
    val bounds: IntRect,
){
    override fun toString(): String {
        return "Tile(scale=$scale, index=$index, bounds=$bounds)"
    }
}

/** A region in the viewport/canvas where a [ImageTile] image will be drawn. */
internal data class ViewportTile private constructor(
    val region: ImageTile,
    val bounds: IntRect,
    val isVisible: Boolean,
    val isBase: Boolean,
) {
    constructor(
        region: ImageTile,
        bounds: Rect,
        isVisible: Boolean,
        isBase: Boolean,
    ) : this(
        region = region,
        bounds = bounds.discardFractionalValues(),
        isVisible = isVisible,
        isBase = isBase,
    )
}

/**
 * This is kept separate from [ViewportTile] to optimize the drawing of the base tile.
 * The base tile may be present in the viewport but can be skipped during rendering if
 * it's entirely covered by foreground tiles.
 */
@Immutable
internal data class ViewportImageTile(
    private val tile: ViewportTile,
    val painter: Painter?,
) {
    val bounds get() = tile.bounds
    val isBase get() = tile.isBase
}

/** Collection of [ImageTile] needed for drawing an image at a certain zoom level. */
internal data class ImageTileGrid(
    val base: ImageTile,
    val foreground: Map<ScaleFactor, List<ImageTile>>
) {
    companion object; // For extensions.
}
