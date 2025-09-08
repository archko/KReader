package com.archko.reader.pdf.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.cache.BitmapState
import com.archko.reader.pdf.cache.ImageCache
import com.archko.reader.pdf.entity.APage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.floor

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
            // 使用向下取整和向上取整来避免间隙
            val left = floor(bounds.left * pageWidth)
            val top = floor(bounds.top * pageHeight + yOffset)
            val right = ceil(bounds.right * pageWidth)
            val bottom = ceil(bounds.bottom * pageHeight + yOffset)
            Rect(left, top, right, bottom)
        } else {
            val left = floor(bounds.left * pageWidth + xOffset)
            val top = floor(bounds.top * pageHeight)
            val right = ceil(bounds.right * pageWidth + xOffset)
            val bottom = ceil(bounds.bottom * pageHeight)
            Rect(left, top, right, bottom)
        }
    }

    //不能用bounds.toString(),切边切换,key变化
    public val cacheKey: String
        get() = "${aPage.index}-${bounds.left}-${bounds.top}-${bounds.right}-${bounds.bottom}-${pdfViewState.vZoom}-${pdfViewState.orientation}-${pdfViewState.isCropEnabled()}"

    private var bitmapState by mutableStateOf<BitmapState?>(null)
    private var isDecoding = false
    private var decodeJob: Job? = null

    public fun recycle() {
        bitmapState?.let { ImageCache.release(it) }
        bitmapState = null
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
            recycle()
            return
        }

        bitmapState?.let { state ->
            //println("[PageNode.draw] page=${aPage.index}, bounds=$bounds, page.W-H=$pageWidth-$pageHeight, xOffset=$xOffset, yOffset=$yOffset, pixelRect=$pixelRect, bitmapSize=${state.bitmap.width}x${state.bitmap.height}")
            // 确保绘制区域没有间隙，使用向下取整的起始位置和向上取整的尺寸
            val dstLeft = floor(pixelRect.left).toInt()
            val dstTop = floor(pixelRect.top).toInt()
            val dstWidth = ceil(pixelRect.width).toInt()
            val dstHeight = ceil(pixelRect.height).toInt()

            drawScope.drawImage(
                state.bitmap,
                dstOffset = IntOffset(dstLeft, dstTop),
                dstSize = IntSize(dstWidth, dstHeight)
            )
        } ?: run {
            decode(pageWidth, pageHeight)
        }

        /*drawScope.drawRect(
            color = Color.Red,
            topLeft = Offset(pixelRect.left, pixelRect.top),
            size = androidx.compose.ui.geometry.Size(pixelRect.width, pixelRect.height),
            style = Stroke(width = 2f)
        )*/
    }

    private fun decode(pageWidth: Float, pageHeight: Float) {
        if (!isDecoding) {
            isDecoding = true
            decodeJob = pdfViewState.decodeScope.launch {
                // 解码前判断可见性和协程活跃性
                if (!isScopeActive()) {
                    isDecoding = false
                    return@launch
                }

                // 先查安全缓存
                val cachedState = ImageCache.acquire(cacheKey)
                if (cachedState != null) {
                    withContext(Dispatchers.Main) {
                        bitmapState?.let { ImageCache.release(it) }
                        bitmapState = cachedState
                    }
                    isDecoding = false
                    return@launch
                }

                val width = aPage.getWidth(pdfViewState.isCropEnabled())
                val height = aPage.getHeight(pdfViewState.isCropEnabled())
                var scale = pageWidth / width
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

                val left =
                    (if (null != aPage.cropBounds && pdfViewState.isCropEnabled()) aPage.cropBounds!!.left
                    else 1f) * pageWidth / width
                val top =
                    (if (null != aPage.cropBounds && pdfViewState.isCropEnabled()) aPage.cropBounds!!.top
                    else 1f) * pageHeight / height
                val srcRect = Rect(
                    left = bounds.left * pageWidth + left,
                    top = bounds.top * pageHeight + top,
                    right = bounds.right * pageWidth + left,
                    bottom = bounds.bottom * pageHeight + top
                )
                //println("[PageNode].decode:$pageWidth-$pageHeight, left:$left, $scale, width:$width, $srcRect, $aPage")
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

                val newState = ImageCache.put(cacheKey, bitmap)

                if (!isScopeActive()) {
                    isDecoding = false
                    ImageCache.release(newState)
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    if (!pdfViewState.isTileVisible(tileSpec)) {
                        isDecoding = false
                        return@withContext
                    }

                    bitmapState?.let { ImageCache.release(it) }
                    bitmapState = newState
                    isDecoding = false
                }
            }
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