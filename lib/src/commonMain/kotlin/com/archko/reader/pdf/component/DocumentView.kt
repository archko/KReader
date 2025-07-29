package com.archko.reader.pdf.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.cache.ImageCache
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.APage

public const val Vertical: Int = 0
public const val Horizontal: Int = 1

/**
 * 基础文档视图组件，提供核心的渲染功能
 * 状态管理由调用方提供，不包含任何输入处理
 */
@Composable
public fun DocumentView(
    list: MutableList<APage>,
    state: ImageDecoder,
    viewSize: IntSize,
    offset: Offset,
    vZoom: Float,
    orientation: Int,
    onDocumentClosed: ((page: Int, pageCount: Int, zoom: Double, scrollX: Long, scrollY: Long, scrollOri: Long) -> Unit)? = null,
    onPageChanged: ((page: Int) -> Unit)? = null,
    onViewSizeChanged: ((IntSize) -> Unit)? = null,
    // 额外的修饰符
    additionalModifier: Modifier = Modifier
) {
    val pdfViewState = remember(list, orientation) {
        PdfViewState(list, state, orientation)
    }

    // 更新视图大小
    LaunchedEffect(viewSize, orientation) {
        if (viewSize != IntSize.Zero) {
            pdfViewState.updateViewSize(viewSize, vZoom, orientation)
        }
    }

    // 更新偏移量
    LaunchedEffect(offset) {
        pdfViewState.updateOffset(offset)
    }

    // 监听页面变化并回调
    LaunchedEffect(offset, viewSize, orientation) {
        val pages = pdfViewState.pages
        if (pages.isNotEmpty()) {
            val offsetY = offset.y
            val offsetX = offset.x
            val firstVisible = pages.indexOfFirst { page ->
                if (orientation == Vertical) {
                    val top = -offsetY
                    val bottom = top + viewSize.height
                    page.bounds.bottom > top && page.bounds.top < bottom
                } else {
                    val left = -offsetX
                    val right = left + viewSize.width
                    page.bounds.right > left && page.bounds.left < right
                }
            }
            if (firstVisible != -1) {
                onPageChanged?.invoke(firstVisible)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val pages = pdfViewState.pages
            var currentPage = 0
            if (pages.isNotEmpty()) {
                val offsetY = offset.y
                val offsetX = offset.x
                val firstVisible = pages.indexOfFirst { page ->
                    if (orientation == Vertical) {
                        val top = -offsetY
                        val bottom = top + viewSize.height
                        page.bounds.bottom > top && page.bounds.top < bottom
                    } else {
                        val left = -offsetX
                        val right = left + viewSize.width
                        page.bounds.right > left && page.bounds.left < right
                    }
                }
                if (firstVisible != -1) {
                    currentPage = firstVisible
                }
            }
            val pageCount = list.size
            val zoom = vZoom.toDouble()
            
            if (!list.isEmpty()){
                onDocumentClosed?.invoke(
                    currentPage,
                    pageCount,
                    zoom,
                    offset.x.toLong(),
                    offset.y.toLong(),
                    orientation.toLong()
                )
            }

            pdfViewState.shutdown()
            ImageCache.clear()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged {
                onViewSizeChanged?.invoke(it)
            }
            .then(additionalModifier),
        contentAlignment = Alignment.TopStart
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            translate(left = offset.x, top = offset.y) {
                pdfViewState.drawVisiblePages(this, offset, vZoom, viewSize)
            }
        }
    }
}