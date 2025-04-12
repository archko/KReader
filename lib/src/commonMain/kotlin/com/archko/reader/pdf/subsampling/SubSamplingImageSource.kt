package com.archko.reader.pdf.subsampling

import com.archko.reader.pdf.subsampling.internal.ImageRegionDecoder
import okio.Closeable

/**
 * Image to display with [SubSamplingImage]. Can be one of:
 */
public interface SubSamplingImageSource : Closeable {

    public suspend fun decoder(): ImageRegionDecoder.Factory

    public override fun close()
}