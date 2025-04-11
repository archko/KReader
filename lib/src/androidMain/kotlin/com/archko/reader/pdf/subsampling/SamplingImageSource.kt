package com.archko.reader.pdf.subsampling

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import com.archko.reader.pdf.subsampling.internal.ImageRegionDecoder
import okio.Closeable
import okio.Path

@Immutable
public class SamplingImageSource(
    public val path: Path,
    public val onClose: Closeable?
) : SubSamplingImageSource {
    init {
        check(path.isAbsolute)
    }

    override val preview: ImageBitmap? = ImageBitmap(100, 100, ImageBitmapConfig.Argb8888)

    override suspend fun decoder(): ImageRegionDecoder.Factory {
        return AndroidImageRegionDecoder.Factory(this) {
            PdfDecoder(path.toFile())
        }
    }

    override fun close() {
        onClose?.close()
    }
}