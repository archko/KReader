package com.archko.reader.pdf.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.cache.ImageCache
import com.archko.reader.pdf.entity.APage
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * @author: archko 2025/7/24 :08:19
 */
public class PageNode(
    private val pdfViewState: PdfViewState,
    public var bounds: Rect,  // 逻辑坐标(0~1)
    public val aPage: APage
) {
    // 逻辑rect转实际像素，直接用Page的width/height
    public fun toPixelRect(pageWidth: Float, pageHeight: Float, yOffset: Float): Rect {
        return Rect(
            left = bounds.left * pageWidth,
            top = bounds.top * pageHeight + yOffset,
            right = bounds.right * pageWidth,
            bottom = bounds.bottom * pageHeight + yOffset
        )
    }

    public val cacheKey: String
        get() = "${aPage.index}-${bounds}-${pdfViewState.vZoom}"

    // 每个PageNode持有自己的图片state
    private var imageBitmap by mutableStateOf<ImageBitmap?>(null)
    private var isDecoding = false
    private var decodeJob: Job? = null

    public fun recycle() {
        imageBitmap = null
        // TODO: 如果有Bitmap缓存池，可以在这里回收bitmap
        isDecoding = false
        decodeJob?.cancel()
        decodeJob = null
    }

    public fun draw(
        drawScope: DrawScope,
        offset: Offset,
        pageWidth: Float,
        pageHeight: Float,
        yOffset: Float,
        totalScale: Float
    ) {
        val pixelRect = toPixelRect(pageWidth, pageHeight, yOffset)
        // 页码合法性判断，防止越界
        if (aPage.index < 0 || aPage.index >= pdfViewState.list.size) {
            recycle()
            return
        }
        if (!isVisible(drawScope, offset, pixelRect, aPage.index)) {
            // 可选：失效时释放图片并取消解码
            recycle()
            return
        }

        //println("[PageNode.draw] page=${aPage.index}, bounds=$bounds, pageWidth-Height=$pageWidth-$pageHeight, yOffset=$yOffset, offset=$offset, totalScale=$totalScale, pixelRect=$pixelRect, bitmapSize=${imageBitmap?.width}x${imageBitmap?.height}")
        if (imageBitmap != null) {
            drawScope.drawImage(
                imageBitmap!!,
                dstOffset = IntOffset(pixelRect.left.toInt(), pixelRect.top.toInt()),
                dstSize = IntSize(pixelRect.width.toInt(), pixelRect.height.toInt())
            )
        } else {
            if (!isDecoding) {
                isDecoding = true
                decodeJob = pdfViewState.decodeScope.launch {
                    // 解码前判断可见性和协程活跃性
                    if (!isActive) {
                        println("[PageNode.decodeScope] page=!isActive")
                        isDecoding = false
                        return@launch
                    }
                    
                    // 检查 PdfViewState 是否已关闭
                    if (pdfViewState.isShutdown()) {
                        println("[PageNode.decodeScope] page=PdfViewState已关闭")
                        isDecoding = false
                        return@launch
                    }
                    
                    // 先查缓存
                    val cacheBitmap = ImageCache.get(cacheKey)
                    if (cacheBitmap != null) {
                        imageBitmap = cacheBitmap
                        isDecoding = false
                        return@launch
                    }
                    val tileSpec = TileSpec(
                        aPage.index,
                        totalScale, // totalScale
                        bounds,
                        pageWidth.toInt(), // 原始宽高
                        pageHeight.toInt(),
                        pdfViewState.viewSize,
                        cacheKey,
                        null
                    )

                    if (!pdfViewState.isTileVisible(tileSpec)) {
                        println("[PageNode.decodeScope] page=!isTileVisible")
                        isDecoding = false
                        return@launch
                    }

                    val srcRect = Rect(
                        left = bounds.left * pageWidth,
                        top = bounds.top * pageHeight,
                        right = bounds.right * pageWidth,
                        bottom = bounds.bottom * pageHeight
                    )
                    val outWidth = ((srcRect.right - srcRect.left)).toInt()
                    val outHeight = ((srcRect.bottom - srcRect.top)).toInt()

                    // 再次检查 PdfViewState 是否已关闭
                    if (pdfViewState.isShutdown()) {
                        println("[PageNode.decodeScope] page=渲染前PdfViewState已关闭")
                        isDecoding = false
                        return@launch
                    }

                    val bitmap = pdfViewState.state.renderPageRegion(
                        srcRect,
                        aPage.index,
                        totalScale, // totalScale
                        pdfViewState.viewSize,
                        outWidth,
                        outHeight
                    )

                    // 解码后再次判断可见性和协程活跃性
                    if (!isActive) {
                        isDecoding = false
                        return@launch
                    }
                    if (pdfViewState.isShutdown()) {
                        println("[PageNode.decodeScope] page=解码后PdfViewState已关闭")
                        isDecoding = false
                        return@launch
                    }
                    if (!pdfViewState.isTileVisible(tileSpec)) {
                        isDecoding = false
                        return@launch
                    }

                    imageBitmap = bitmap
                    // 放入缓存
                    ImageCache.put(cacheKey, bitmap)
                    isDecoding = false
                }
            }
            /*drawScope.drawRect(
                color = Color.Magenta,
                topLeft = Offset(pixelRect.left, pixelRect.top),
                size = pixelRect.size,
                style = Stroke(width = 4f)
            )*/
        }
    }
}