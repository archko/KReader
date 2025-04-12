package com.archko.reader.pdf.subsampling

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.component.Size
import com.archko.reader.pdf.entity.Item
import com.archko.reader.pdf.subsampling.internal.ExifMetadata
import com.archko.reader.pdf.subsampling.internal.ImageDecoder
import com.archko.reader.pdf.subsampling.internal.ImageRegionDecoder
import com.archko.reader.pdf.subsampling.internal.rotateBy
import com.archko.reader.pdf.subsampling.internal.tile.ImageRegionTile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

internal class AndroidDecoderDelegate private constructor(
    private val imageSource: SubSamplingImageSource,
    private val viewportSize: IntSize,
    private val decoder: ImageDecoder,
    private val exif: ExifMetadata,
    private val dispatcher: CoroutineDispatcher,
) : ImageRegionDecoder {
    override var pageCount: Int = decoder.pageCount
    override var pageSizes: List<Size> = decoder.pageSizes
    override var outlineItems: List<Item>? = decoder.outlineItems

    override var imageSize: IntSize = IntSize.Zero
        get() = decoder.size(viewportSize)

    override fun decodeRegion(
        rect: IntRect,
        tile: ImageRegionTile
    ): ImageBitmap? {
        return decoder.decodeRegion(rect, tile)
    }

    override fun size(viewportSize: IntSize): IntSize {
        return decoder.size(viewportSize)
    }

    override suspend fun decodeRegion(tile: ImageRegionTile): ImageRegionDecoder.DecodeResult {
        val bounds = tile.bounds.rotateBy(
            degrees = -exif.orientation.degrees,
            unRotatedParent = IntRect(offset = IntOffset.Zero, size = imageSize)
        )

        val bitmap: ImageBitmap? = withContext(dispatcher) {
            decoder.decodeRegion(bounds, tile)
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
        decoder.close()
    }

    companion object {
        @OptIn(ExperimentalCoroutinesApi::class)
        fun Factory(
            imageSource: SubSamplingImageSource,
            createDecoder: () -> ImageDecoder,
        ) = ImageRegionDecoder.Factory { params ->
            val dispatcher = Dispatchers.IO.limitedParallelism(1)

            AndroidDecoderDelegate(
                imageSource = imageSource,
                viewportSize = params.viewportSize,
                decoder = withContext(dispatcher) { createDecoder() },
                exif = ExifMetadata(ExifMetadata.ImageOrientation.None),
                dispatcher = dispatcher,
            )
        }
    }
}