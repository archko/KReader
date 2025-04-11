package com.archko.reader.pdf.subsampling

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig

@Immutable
public class ImageBitmapOptions internal constructor(
    public val config: ImageBitmapConfig = ImageBitmapConfig.Argb8888,
) {
    public companion object {
        public val Default: ImageBitmapOptions = ImageBitmapOptions()
    }
}

public fun ImageBitmapOptions(from: ImageBitmap): ImageBitmapOptions {
    return ImageBitmapOptions(
        config = ImageBitmapConfig.Argb8888,
    )
}