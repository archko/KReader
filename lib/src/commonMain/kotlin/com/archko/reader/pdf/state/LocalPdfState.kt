package com.archko.reader.pdf.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.ImageBitmap
import com.archko.reader.pdf.component.Size
import com.archko.reader.pdf.entity.Item
import java.io.File
import java.net.URL

@Stable
public expect class LocalPdfState public constructor(file: File){

    public var pageCount: Int

    public var pageSizes: List<Size>

    public var outlineItems: List<Item>?

    public fun close()

    public fun renderPage(index: Int, viewWidth: Int, viewHeight: Int): ImageBitmap

    public fun renderPageRegion(index: Int, viewWidth: Int, viewHeight: Int, xOffset: Int, yOffset: Int, zoom: Float): ImageBitmap
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