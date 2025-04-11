package com.archko.reader.pdf.subsampling

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import com.archko.reader.pdf.subsampling.internal.ImageRegionDecoder
import okio.Closeable
import okio.Path

@Immutable
public actual class SamplingImageSource(
    public val path: Path,
    public val onClose: Closeable?
) : SubSamplingImageSource {
    init {
        check(path.isAbsolute)
    }

    actual override val preview: ImageBitmap? = null

    actual override suspend fun decoder(): ImageRegionDecoder.Factory {
        return null
    }

    actual override fun close() {
        onClose?.close()
    }
}