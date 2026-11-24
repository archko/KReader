package com.archko.reader.pdf.util

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

internal actual fun encodeReflowTile(image: ImageBitmap): EncodedTile? {
    return try {
        val bufferedImage = image.toAwtImage()
        val buffer = ByteArrayOutputStream()
        ImageIO.write(bufferedImage, "PNG", buffer)
        EncodedTile(TILE_FORMAT_PNG, buffer.toByteArray())
    } catch (_: Exception) {
        null
    }
}

internal actual fun decodeReflowTile(bytes: ByteArray): ImageBitmap? {
    return try {
        val bufferedImage = ImageIO.read(ByteArrayInputStream(bytes)) ?: return null
        bufferedImage.toComposeImageBitmap()
    } catch (_: Exception) {
        null
    }
}

internal actual fun recycleReflowTile(image: ImageBitmap) {
    try {
        image.toAwtImage().flush()
    } catch (_: Exception) {
    }
}
