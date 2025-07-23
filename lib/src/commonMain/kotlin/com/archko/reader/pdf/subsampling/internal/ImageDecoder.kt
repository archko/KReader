package com.archko.reader.pdf.subsampling.internal

import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.component.Size
import com.archko.reader.pdf.entity.Item

/**
 * @author: archko 2025/4/11 :15:51
 */
public interface ImageDecoder {

    public var pageCount: Int
    public var pageSizes: List<Size>
    public var outlineItems: List<Item>?

    /** Size of the full image, without any scaling applied. */
    public var imageSize: IntSize

    //public fun decodeRegion(rect: IntRect, tile: ImageTile): ImageBitmap?
    public fun size(viewportSize: IntSize): IntSize
    public fun close()
}