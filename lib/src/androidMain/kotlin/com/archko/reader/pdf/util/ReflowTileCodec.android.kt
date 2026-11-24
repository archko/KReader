package com.archko.reader.pdf.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream

internal actual fun encodeReflowTile(image: ImageBitmap): EncodedTile? {
    return try {
        val bitmap = image.asAndroidBitmap()
        val compressFormat: Bitmap.CompressFormat
        val formatTag: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            compressFormat = Bitmap.CompressFormat.WEBP_LOSSLESS
            formatTag = TILE_FORMAT_WEBP
        } else {
            compressFormat = Bitmap.CompressFormat.PNG
            formatTag = TILE_FORMAT_PNG
        }
        val buffer = ByteArrayOutputStream()
        if (!bitmap.compress(compressFormat, 100, buffer)) {
            return null
        }
        EncodedTile(formatTag, buffer.toByteArray())
    } catch (_: Exception) {
        null
    }
}

internal actual fun decodeReflowTile(bytes: ByteArray): ImageBitmap? {
    return try {
        val options = BitmapFactory.Options()
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

internal actual fun recycleReflowTile(image: ImageBitmap) {
    try {
        image.asAndroidBitmap().recycle()
    } catch (_: Exception) {
    }
}
