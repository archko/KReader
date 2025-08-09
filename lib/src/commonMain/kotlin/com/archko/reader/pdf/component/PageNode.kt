package com.archko.reader.pdf.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.cache.ImageCache
import com.archko.reader.pdf.entity.APage
import kotlinx.coroutines.CoroutineScope
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
    public fun toPixelRect(
        pageWidth: Float,
        pageHeight: Float,
        xOffset: Float,
        yOffset: Float
    ): Rect {
        return if (pdfViewState.orientation == Vertical) {
            Rect(
                left = bounds.left * pageWidth,
                top = bounds.top * pageHeight + yOffset,
                right = bounds.right * pageWidth,
                bottom = bounds.bottom * pageHeight + yOffset
            )
        } else {
            Rect(
                left = bounds.left * pageWidth + xOffset,
                top = bounds.top * pageHeight,
                right = bounds.right * pageWidth + xOffset,
                bottom = bounds.bottom * pageHeight
            )
        }
    }

    //不能用bounds.toString(),切边切换,key变化
    public val cacheKey: String
        get() = "${aPage.index}-${bounds.left}-${bounds.top}-${bounds.right}-${bounds.bottom}-${pdfViewState.vZoom}-${pdfViewState.orientation}-${pdfViewState.isCropEnabled()}"

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

    /**
     * @param pageWidth page的缩放后的宽
     * @param pageHeight page的缩放后的高
     * @param totalScale page当前显示的宽/页面原始的宽,包含了view的缩放值
     */
    public fun draw(
        drawScope: DrawScope,
        offset: Offset,
        pageWidth: Float,
        pageHeight: Float,
        xOffset: Float,
        yOffset: Float,
        totalScale: Float
    ) {
        val pixelRect = toPixelRect(pageWidth, pageHeight, xOffset, yOffset)
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

        //println("[PageNode.draw] page=${aPage.index}, bounds=$bounds, page.W-H=$pageWidth-$pageHeight, xOffset=$xOffset, yOffset=$yOffset, pixelRect=$pixelRect, bitmapSize=${imageBitmap?.width}x${imageBitmap?.height}")
        if (imageBitmap != null) {
            drawScope.drawImage(
                imageBitmap!!,
                dstOffset = IntOffset(pixelRect.left.toInt(), pixelRect.top.toInt()),
                dstSize = IntSize(pixelRect.width.toInt(), pixelRect.height.toInt())
            )

            // 绘制PageNode框架用于调试
            /*drawScope.drawRect(
                color = Color.Red,
                topLeft = Offset(pixelRect.left, pixelRect.top),
                size = androidx.compose.ui.geometry.Size(pixelRect.width, pixelRect.height),
                style = Stroke(width = 2f)
            )*/
        } else {
            if (!isDecoding) {
                isDecoding = true
                decodeJob = pdfViewState.decodeScope.launch {
                    // 解码前判断可见性和协程活跃性
                    if (!isScopeActive()) {
                        return@launch
                    }

                    // 先查缓存
                    val cacheBitmap = ImageCache.get(cacheKey)
                    if (cacheBitmap != null) {
                        imageBitmap = cacheBitmap
                        isDecoding = false
                        return@launch
                    }

                    var scale = totalScale
                    val tileSpec = TileSpec(
                        aPage.index,
                        scale,
                        bounds,
                        pageWidth.toInt(), // 原始宽高
                        pageHeight.toInt(),
                        pdfViewState.viewSize,
                        cacheKey,
                        null
                    )

                    if (!pdfViewState.isTileVisible(tileSpec)) {
                        //println("[PageNode.decodeScope] page=!isTileVisible")
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

                    val bitmap = pdfViewState.state.renderPageRegion(
                        srcRect,
                        aPage.index,
                        scale,
                        pdfViewState.viewSize,
                        outWidth,
                        outHeight
                    )

                    ImageCache.put(cacheKey, bitmap)

                    // 解码后再次判断可见性和协程活跃性
                    if (!isScopeActive()) {
                        return@launch
                    }
                    if (!pdfViewState.isTileVisible(tileSpec)) {
                        isDecoding = false
                        return@launch
                    }

                    imageBitmap = bitmap
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

    private fun CoroutineScope.isScopeActive(): Boolean {
        if (!isActive || pdfViewState.isShutdown()) {
            println("[PageNode.decodeScope] page=PdfViewState已关闭")
            isDecoding = false
            return false
        }
        return true
    }
}