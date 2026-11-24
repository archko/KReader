package com.archko.reader.viewer

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.archko.reader.k2pdfopt.K2PdfOpt
import com.archko.reader.viewer.utils.Logger

internal actual fun renderReflowTiles(
    source: ImageBitmap,
    fontSize: Float,
    viewWidth: Int,
    viewHeight: Int,
    devDpi: Int,
    sourceDpi: Float,
    wordSpacing: Float,
): List<ImageBitmap> {
    val list = mutableListOf<ImageBitmap>()
    val opt = K2PdfOpt()
    try {
        opt.create(viewWidth, viewHeight, devDpi)
        opt.setSourceDpi(sourceDpi)
        opt.setWordSpacing(wordSpacing)
        opt.fontSize = fontSize
        opt.load(source.asAndroidBitmap())

        val pageWidth = opt.pageWidth
        val pageHeight = opt.pageHeight
        if (pageWidth <= 0 || pageHeight <= 0) {
            Logger.w("ReflowDecodeService", "k2pdfopt empty output: $pageWidth×$pageHeight")
            return list
        }

        val tileHeight = if (viewHeight > 0) viewHeight else 1024
        var y = 0
        while (y < pageHeight) {
            val h = minOf(tileHeight, pageHeight - y)
            val tile = opt.renderTile(0, y, pageWidth, h) ?: break
            list.add(tile.asImageBitmap())
            y += h
        }
    } catch (e: Exception) {
        Logger.e("ReflowDecodeService", "k2pdfopt error: ${e.message}", e)
    } finally {
        try {
            opt.releasePage()
        } catch (_: Exception) {
        }
    }
    return list
}
