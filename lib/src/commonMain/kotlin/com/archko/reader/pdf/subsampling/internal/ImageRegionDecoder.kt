package com.archko.reader.pdf.subsampling.internal

import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.subsampling.tile.ImageTile

/**
 * An image decoder, responsible for loading partial regions for
 * [SubSamplingImage][me.saket.telephoto.subsamplingimage.SubSamplingImage]'s tiles.
 */
public interface ImageRegionDecoder : ImageDecoder{

    public suspend fun decodeRegion(tile: ImageTile): DecodeResult

    public fun interface Factory {
        public suspend fun create(params: FactoryParams): ImageRegionDecoder
    }

    public class DecodeResult(
        public val painter: Painter,
    )

    public interface FactoryParams {
        public val viewportSize: IntSize
    }
}

internal data class AndroidImageDecoderFactoryParams(
    override val viewportSize: IntSize,
) : ImageRegionDecoder.FactoryParams
