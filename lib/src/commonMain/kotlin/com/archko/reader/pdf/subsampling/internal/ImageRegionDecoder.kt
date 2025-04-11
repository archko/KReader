package com.archko.reader.pdf.subsampling.internal

import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.subsampling.ImageBitmapOptions

/**
 * An image decoder, responsible for loading partial regions for
 * [SubSamplingImage][me.saket.telephoto.subsamplingimage.SubSamplingImage]'s tiles.
 */
public interface ImageRegionDecoder {
    /** Size of the full image, without any scaling applied. */
    public var imageSize: IntSize

    public suspend fun decodeRegion(region: IntRect, sampleSize: Int): DecodeResult

    /** Called when the image is no longer visible. */
    public fun close(): Unit = Unit

    public fun interface Factory {
        public suspend fun create(params: FactoryParams): ImageRegionDecoder
    }

    public class DecodeResult(
        public val painter: Painter,
    )

    public interface FactoryParams {
        public val imageOptions: ImageBitmapOptions
    }
}

internal data class AndroidImageDecoderFactoryParams(
    override val imageOptions: ImageBitmapOptions,
) : ImageRegionDecoder.FactoryParams
