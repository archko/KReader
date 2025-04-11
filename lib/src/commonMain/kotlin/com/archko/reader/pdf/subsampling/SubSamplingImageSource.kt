package com.archko.reader.pdf.subsampling

import androidx.compose.ui.graphics.ImageBitmap
import com.archko.reader.pdf.subsampling.internal.ImageRegionDecoder
import okio.Closeable

/**
 * Image to display with [SubSamplingImage]. Can be one of:
 */
public interface SubSamplingImageSource : Closeable {

    public val preview: ImageBitmap?

    public suspend fun decoder(): ImageRegionDecoder.Factory

    public override fun close()
}