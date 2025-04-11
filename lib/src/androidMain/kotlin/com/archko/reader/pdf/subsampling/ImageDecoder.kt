package com.archko.reader.pdf.subsampling

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.subsampling.internal.tile.ImageRegionTile

/**
 * @author: archko 2025/4/11 :15:51
 */
public interface ImageDecoder {
    public fun decodeRegion(rect: IntRect, tile: ImageRegionTile): ImageBitmap?
    public fun size(viewportSize: IntSize): IntSize
    public fun close()
}