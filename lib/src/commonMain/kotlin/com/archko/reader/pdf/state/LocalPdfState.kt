package com.archko.reader.pdf.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import java.io.File
import java.net.URL

@Stable
public expect class LocalPdfState public constructor(file: File) : PdfState {
    public override var pageCount: Int

    public override fun renderPage(index: Int, viewWidth: Int, viewHeight: Int): ImageBitmap

    public override fun renderPageRegion(index: Int, viewWidth: Int, viewHeight: Int, xOffset: Int, yOffset: Int): ImageBitmap
}

/**
 * Remembers a [LocalPdfState] for the given [url].
 *
 * @param url
 * @return [LocalPdfState]
 */
@Composable
public expect fun rememberLocalPdfState(url: URL): LocalPdfState

/**
 * Remembers a [LocalPdfState] for the given [file].
 *
 * @param file
 * @return [LocalPdfState]
 */
@Composable
public expect fun rememberLocalPdfState(file: File): LocalPdfState