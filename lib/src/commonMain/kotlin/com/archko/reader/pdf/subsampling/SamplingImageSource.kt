package com.archko.reader.pdf.subsampling

import androidx.compose.ui.graphics.ImageBitmap
import com.archko.reader.pdf.subsampling.internal.ImageRegionDecoder

public expect class SamplingImageSource : SubSamplingImageSource {
    public override val preview: ImageBitmap?

    public override suspend fun decoder(): ImageRegionDecoder.Factory

    public override fun close()
}