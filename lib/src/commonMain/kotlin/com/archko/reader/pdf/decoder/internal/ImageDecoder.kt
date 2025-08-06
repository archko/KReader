package com.archko.reader.pdf.decoder.internal

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.component.Size
import com.archko.reader.pdf.entity.APage
import com.archko.reader.pdf.entity.Hyperlink
import com.archko.reader.pdf.entity.Item

/**
 * @author: archko 2025/4/11 :15:51
 */
public interface ImageDecoder {

    public var pageCount: Int
    public var pageSizes: List<Size>
    public var originalPageSizes: List<Size>
    public var outlineItems: List<Item>?
    public val aPageList: MutableList<APage>?

    /** Size of the full image, without any scaling applied. */
    public var imageSize: IntSize

    //public fun decodeRegion(rect: IntRect, tile: ImageTile): ImageBitmap?
    public fun size(viewportSize: IntSize): IntSize

    public fun getPageLinks(pageIndex: Int): List<Hyperlink>

    /**
     * 渲染页面区域（带切边参数）
     * @param page 页面索引
     * @param viewSize 视图大小
     * @param outWidth 页面宽度
     * @param outHeight 页面高度
     * @param crop 是否启用切边
     * @return 渲染结果
     */
    public fun renderPage(
        aPage: APage,
        viewSize: IntSize,
        outWidth: Int,
        outHeight: Int,
        crop: Boolean
    ): ImageBitmap


    public fun renderPageRegion(
        region: Rect,
        index: Int,
        scale: Float,
        viewSize: IntSize,
        outWidth: Int,
        outHeight: Int
    ): ImageBitmap

    public fun close()
}