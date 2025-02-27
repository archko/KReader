package com.archko.reader.pdf.state

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.painter.Painter
import com.archko.reader.pdf.component.Size
import com.archko.reader.pdf.entity.Item

/** Represents a PDF file. */
@Stable
public interface PdfState : AutoCloseable {

    /** Total number of pages in the PDF file. */
    public val pageCount: Int

    public var pageSizes: List<Size>

    public var outlineItems: List<Item>?

    /**
     * @param index the page number to render
     * @return [Painter]
     */
    public fun renderPage(index: Int, viewWidth: Int, viewHeight: Int): Painter
}