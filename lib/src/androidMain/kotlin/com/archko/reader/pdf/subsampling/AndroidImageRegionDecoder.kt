package com.archko.reader.pdf.subsampling

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.subsampling.internal.ExifMetadata
import com.archko.reader.pdf.subsampling.internal.ImageRegionDecoder
import com.archko.reader.pdf.subsampling.internal.RotatedBitmapPainter
import com.archko.reader.pdf.subsampling.internal.rotateBy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

internal class AndroidImageRegionDecoder private constructor(
    private val imageSource: SubSamplingImageSource,
    private val imageOptions: ImageBitmapOptions,
    private val decoder: ImageDecoder,
    private val exif: ExifMetadata,
    private val dispatcher: CoroutineDispatcher,
) : ImageRegionDecoder {

    override var imageSize: IntSize = IntSize.Zero
        get() = decoder.size()

    override suspend fun decodeRegion(
        region: IntRect,
        sampleSize: Int
    ): ImageRegionDecoder.DecodeResult {
        val bounds = region.rotateBy(
            degrees = -exif.orientation.degrees,
            unRotatedParent = IntRect(offset = IntOffset.Zero, size = imageSize)
        )

        val bitmap: ImageBitmap? = withContext(dispatcher) {
            decoder.decodeRegion(bounds, 1)
        }
        if (bitmap != null) {
            return ImageRegionDecoder.DecodeResult(
                painter = BitmapPainter(
                    image = bitmap,
                )
            )
        } else {
            error("BitmapRegionDecoder returned a null bitmap. Image format may not be supported: $imageSource.")
        }
    }

    override fun close() {
    }

    /*private fun ImageDecoder.size(): IntSize {
        val shouldFlip = when (exif.orientation) {
            ExifMetadata.ImageOrientation.Orientation90,
            ExifMetadata.ImageOrientation.Orientation270 -> true

            else -> false
        }

        return IntSize(
            width = if (shouldFlip) imageSize.height else imageSize.width,
            height = if (shouldFlip) imageSize.width else imageSize.height,
        )
    }*/

    companion object {
        @OptIn(ExperimentalCoroutinesApi::class)
        fun Factory(
            imageSource: SubSamplingImageSource,
            createDecoder: () -> ImageDecoder,
        ) = ImageRegionDecoder.Factory { params ->
            val dispatcher = Dispatchers.IO.limitedParallelism(1)

            AndroidImageRegionDecoder(
                imageSource = imageSource,
                imageOptions = params.imageOptions,
                decoder = withContext(dispatcher) { createDecoder() },
                exif = ExifMetadata(ExifMetadata.ImageOrientation.None),
                dispatcher = dispatcher,
            )
        }
    }
}