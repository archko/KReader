package com.archko.reader.pdf.util

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.util.Log
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter

internal fun PdfRenderer.renderPage(index: Int, viewWidth: Int, viewHeight: Int): BitmapPainter {
    require(index in 0 until pageCount) { "Page index out of bounds" }
    val bmp: Bitmap

    openPage(index).use { page ->
        var w = viewWidth
        var h = viewHeight
        if (viewWidth > 0) {
            val xscale = 1f * viewWidth / page.width
            h = (page.height * xscale).toInt()
        } else {
            w = page.width
            h = page.height
        }
        Log.d(
            "",
            "open:${page.width}, ${page.height}, $viewWidth, $viewHeight, $w, $h, ${Thread.currentThread()}"
        )

        bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
    }

    return BitmapPainter(bmp.asImageBitmap())
}