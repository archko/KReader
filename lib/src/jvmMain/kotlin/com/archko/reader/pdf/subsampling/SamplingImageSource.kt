package com.archko.reader.pdf.subsampling

import androidx.compose.runtime.Immutable
import com.archko.reader.pdf.subsampling.internal.ImageRegionDecoder
import okio.Closeable

@Immutable
public class SamplingImageSource(
    public val path: String,
    public val onClose: Closeable?
) : SubSamplingImageSource {
    init {
        //check(path)
    }

    override suspend fun decoder(): ImageRegionDecoder.Factory {
        return null
    }

    override fun close() {
        onClose?.close()
    }
}