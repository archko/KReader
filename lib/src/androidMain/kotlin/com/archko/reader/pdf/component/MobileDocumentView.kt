package com.archko.reader.pdf.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.APage
import com.archko.reader.pdf.entity.DocQuad
import com.archko.reader.pdf.state.AnnotationManager

/**
 * 移动端文档视图（公共实现）
 */
@Composable
public fun MobileDocumentView(
    list: MutableList<APage>,
    decoder: ImageDecoder,
    jumpToPage: Int? = null,
    jumpMode: JumpMode = JumpMode.PageRestore,
    jumpOffsetY: Float? = null,
    initialOrientation: Int,
    columnCount: Int,
    onSaveDocument: ((page: Int, pageCount: Int, zoom: Double, scrollX: Long, scrollY: Long, scrollOri: Long, reflow: Long, crop: Long) -> Unit)? = null,
    onCloseDocument: (() -> Unit)? = null,
    onDoubleTapToolbar: (() -> Unit)? = null,
    onPageChanged: ((page: Int) -> Unit)? = null,
    onTapNonPageArea: ((pageIndex: Int) -> Unit)? = null,
    initialScrollX: Long = 0L,
    initialScrollY: Long = 0L,
    initialZoom: Double = 1.0,
    reflow: Long = 0,
    crop: Boolean = false,
    speakingPageIndex: Int? = null,
    gestureMode: GestureMode = GestureMode.VIEW,
    pathConfig: PathConfig,
    annotationManager: AnnotationManager,
    currentPath: String,
    searchHighlightQuads: Map<Int, List<DocQuad>> = emptyMap(),
    currentSearchPageIndex: Int? = null,
) {
    // 创建文档视图状态
    val stateHolder = rememberDocumentViewState(
        list = list,
        decoder = decoder,
        initialScrollX = initialScrollX,
        initialScrollY = initialScrollY,
        initialZoom = initialZoom,
        initialOrientation = initialOrientation,
        crop = crop,
        columnCount = columnCount,
        currentPath = currentPath,
        annotationManager = annotationManager,
        speakingPageIndex = speakingPageIndex,
    )

    // 创建文本选择器
    val textSelector = remember {
        createTextSelector(currentPath) { pageIndex ->
            val structuredText = decoder.getStructuredText(pageIndex)
            if (structuredText != null) {
                createStructuredTextImpl(currentPath, structuredText)
            } else {
                null
            }
        }
    }

    // 所有副作用效果
    DocumentViewEffects(
        state = stateHolder,
        list = list,
        jumpToPage = jumpToPage,
        jumpMode = jumpMode,
        jumpOffsetY = jumpOffsetY,
        initialOrientation = initialOrientation,
        initialScrollX = initialScrollX,
        initialScrollY = initialScrollY,
        initialZoom = initialZoom,
        reflow = reflow,
        crop = crop,
        speakingPageIndex = speakingPageIndex,
        columnCount = columnCount,
        searchHighlightQuads = searchHighlightQuads,
        currentSearchPageIndex = currentSearchPageIndex,
        onSaveDocument = onSaveDocument,
        onCloseDocument = onCloseDocument,
        onPageChanged = onPageChanged,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                if (stateHolder.viewSize.value == size) {
                    return@onSizeChanged
                }
                stateHolder.viewSize.value = size
                println("DocumentView: onSizeChanged:${stateHolder.viewSize.value}, vZoom:${stateHolder.vZoom.value}, list: ${list.size}, orientation: ${stateHolder.orientation.value}")
                stateHolder.pageViewState.updateViewSize(
                    stateHolder.viewSize.value,
                    stateHolder.vZoom.value,
                    stateHolder.orientation.value
                )
            },
        contentAlignment = androidx.compose.ui.Alignment.TopStart
    ) {
        // 统一的手势处理器
        CommonGestureHandler(
            state = stateHolder,
            gestureMode = gestureMode,
            pathConfig = pathConfig,
            viewSize = stateHolder.viewSize.value,
            onDoubleTapToolbar = { onDoubleTapToolbar?.invoke() },
            onTapNonPageArea = { onTapNonPageArea?.invoke(it) },
            onPageChanged = { onPageChanged?.invoke(it) },
        )

        // 文档视图绘制器
        DocumentViewCanvas(
            state = stateHolder,
            viewSize = stateHolder.viewSize.value,
        )

        // 文本操作工具栏
        TextActionToolbarWrapper(
            show = stateHolder.showTextActionToolbar.value,
            selectedPage = stateHolder.selectedPage.value,
            textSelector = textSelector,
            onCopy = { text ->
                println("复制文本: $text")
                stateHolder.showTextActionToolbar.value = false
                stateHolder.selectedPage.value?.clearTextSelection()
                stateHolder.selectedPage.value = null
                stateHolder.selectionStartPos.value = null
                stateHolder.selectionEndPos.value = null
            },
            onDismiss = {
                stateHolder.showTextActionToolbar.value = false
                stateHolder.selectedPage.value?.clearTextSelection()
                stateHolder.selectedPage.value = null
                stateHolder.selectionStartPos.value = null
                stateHolder.selectionEndPos.value = null
            }
        )
    }
}
