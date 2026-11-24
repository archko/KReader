package com.archko.reader.pdf.component

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.archko.reader.k2pdfopt.K2PdfOpt
import com.archko.reader.pdf.util.Logger

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
        opt.loadBufferedImage(source.toAwtImage())

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
            val tile = opt.renderTileBufferedImage(0, y, pageWidth, h) ?: break
            list.add(tile.toComposeImageBitmap())
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
