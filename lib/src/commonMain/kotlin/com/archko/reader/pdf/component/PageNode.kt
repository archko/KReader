package com.archko.reader.pdf.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.cache.BitmapState
import com.archko.reader.pdf.cache.ImageCache
import com.archko.reader.pdf.entity.APage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.floor

/**
 * @author: archko 2025/7/24 :08:19
 */
public class PageNode(
    private val pageViewState: PageViewState,
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
        return if (pageViewState.orientation == Vertical) {
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
        get() = "${aPage.index}-${bounds.left}-${bounds.top}-${bounds.right}-${bounds.bottom}-${pageViewState.vZoom}-${pageViewState.orientation}-${pageViewState.isCropEnabled()}"

    private var bitmapState by mutableStateOf<BitmapState?>(null)
    private var isDecoding = false
    private var decodeJob: Job? = null

    public fun recycle() {
        bitmapState?.let { ImageCache.releaseNode(it) }
        bitmapState = null
        isDecoding = false
        decodeJob?.cancel()
        decodeJob = null
    }

    /**
     * @param pageWidth page的缩放后的宽
     * @param pageHeight page的缩放后的高
     */
    public fun draw(
        drawScope: DrawScope,
        pageWidth: Float,
        pageHeight: Float,
        xOffset: Float,
        yOffset: Float,
    ) {
        val pixelRect = toPixelRect(pageWidth, pageHeight, xOffset, yOffset)
        // 页码合法性判断，防止越界
        if (aPage.index < 0 || aPage.index >= pageViewState.list.size) {
            recycle()
            return
        }

        val width = aPage.getWidth(pageViewState.isCropEnabled())
        val height = aPage.getHeight(pageViewState.isCropEnabled())
        val scale = pageWidth / width
        val tileSpec = TileSpec(
            aPage.index,
            scale,
            bounds,
            pageWidth.toInt(),
            pageHeight.toInt(),
            pageViewState.viewSize,
            cacheKey,
            null
        )

        // 1. 首先检查是否在预加载区域内
        val isInPreloadArea = pageViewState.isTileVisible(tileSpec, strictMode = false)
        if (!isInPreloadArea) {
            recycle()  // 完全超出预加载区域，回收
            return
        }

        // 2. 检查是否在严格可见区域内
        val isStrictlyVisible = pageViewState.isTileVisible(tileSpec, strictMode = true)

        if (null != bitmapState && bitmapState!!.isRecycled()) {
            bitmapState = null
        }

        // 3. 只有严格可见才绘制
        if (isStrictlyVisible) {
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
            }
        }

        // 4. 无论是否绘制，都尝试解码（预加载区域内）
        if (bitmapState == null) {
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
            decodeJob?.cancel()
            decodeJob = pageViewState.decodeScope.launch {
                // 解码前判断可见性和协程活跃性
                if (!isScopeActive()) {
                    isDecoding = false
                    return@launch
                }

                // 先查安全缓存
                val cachedState = ImageCache.acquireNode(cacheKey)
                if (cachedState != null) {
                    withContext(Dispatchers.Main) {
                        bitmapState?.let { ImageCache.releaseNode(it) }
                        bitmapState = cachedState
                    }
                    isDecoding = false
                    return@launch
                }

                val width = aPage.getWidth(pageViewState.isCropEnabled())
                val height = aPage.getHeight(pageViewState.isCropEnabled())
                val scale = pageWidth / width
                val tileSpec = TileSpec(
                    aPage.index,
                    scale,
                    bounds,
                    pageWidth.toInt(),
                    pageHeight.toInt(),
                    pageViewState.viewSize,
                    cacheKey,
                    null
                )

                // 使用预加载区域判断（包含可见区域）
                if (!pageViewState.isTileVisible(tileSpec, strictMode = false)) {
                    isDecoding = false
                    return@launch
                }

                val left =
                    (if (null != aPage.cropBounds && pageViewState.isCropEnabled()) aPage.cropBounds!!.left
                    else 1f) * pageWidth / width
                val top =
                    (if (null != aPage.cropBounds && pageViewState.isCropEnabled()) aPage.cropBounds!!.top
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

                val decodeTask = DecodeTask(
                    type = DecodeTask.TaskType.NODE,
                    pageIndex = aPage.index,
                    decodeKey = cacheKey,
                    aPage = aPage,
                    zoom = scale,
                    pageSliceBounds = srcRect,
                    outWidth,
                    outHeight,
                    crop = pageViewState.isCropEnabled(),
                    callback = object : DecodeCallback {
                        override fun onDecodeComplete(
                            bitmap: ImageBitmap?,
                            isThumb: Boolean,
                            error: Throwable?
                        ) {
                            if (bitmap != null && !pageViewState.isShutdown()) {
                                val newState = ImageCache.putNode(cacheKey, bitmap)
                                pageViewState.decodeScope.launch(Dispatchers.Main) {
                                    if (pageViewState.isTileVisible(
                                            tileSpec,
                                            strictMode = false
                                        ) && !pageViewState.isShutdown()
                                    ) {
                                        bitmapState?.let { ImageCache.releaseNode(it) }
                                        bitmapState = newState
                                    } else {
                                        ImageCache.releaseNode(newState)
                                    }
                                }
                            } else {
                                if (error != null) {
                                    println("PageNode decode error: ${error.message}")
                                }
                            }
                            isDecoding = false
                        }

                        override fun shouldRender(pageNumber: Int, isFullPage: Boolean): Boolean {
                            if (pageViewState.isShutdown()) {
                                return false
                            }

                            // 检查页面是否在当前可见页面列表中
                            val isPageVisible =
                                pageViewState.pageToRender.any { it.aPage.index == pageNumber }
                            if (!isPageVisible) {
                                return false
                            }

                            // 对于节点任务，还需要检查具体的tile是否在预加载区域
                            return pageViewState.isTileVisible(tileSpec, strictMode = false)
                        }
                    }
                )

                pageViewState.decodeService?.submitTask(decodeTask)
            }
        }
    }

    private fun isScopeActive(): Boolean {
        if (pageViewState.isShutdown()) {
            println("[PageNode.decodeScope] page=PageViewState已关闭")
            isDecoding = false
            return false
        }
        return true
    }
}