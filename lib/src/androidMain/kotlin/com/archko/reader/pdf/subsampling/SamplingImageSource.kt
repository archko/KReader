package com.archko.reader.pdf.subsampling

import android.text.TextUtils
import androidx.compose.runtime.Immutable
import com.archko.reader.pdf.subsampling.internal.ImageRegionDecoder
import okio.Closeable
import java.io.File

@Immutable
public class SamplingImageSource(
    public val path: String,
    public val onClose: Closeable? = null
) : SubSamplingImageSource {

    init {
        check(!TextUtils.isEmpty(path))
    }

    override suspend fun decoder(): ImageRegionDecoder.Factory {
        return AndroidDecoderDelegate.Factory(this) {
            PdfDecoder(File(path))
        }
    }

    override fun close() {
        onClose?.close()
    }
}