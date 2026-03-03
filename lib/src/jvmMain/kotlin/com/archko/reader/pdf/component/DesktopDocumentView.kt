package com.archko.reader.pdf.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.APage
import com.archko.reader.pdf.entity.DocQuad
import com.archko.reader.pdf.state.AnnotationManager
import kotlin.math.max
import kotlin.math.min

/**
 * 桌面端文档视图
 * 使用公共的手势处理逻辑，额外添加键盘和滚轮事件支持
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
public fun DesktopDocumentView(
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
    zoom: Double = 1.0,
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
    // 平台判断
    val isMacOs by remember {
        mutableStateOf(
            System.getProperty("os.name", "").lowercase().contains("mac")
        )
    }

    // 焦点请求器
    val focusRequester = remember { FocusRequester() }

    // 创建文档视图状态
    val stateHolder = rememberDocumentViewState(
        list = list,
        decoder = decoder,
        initialScrollX = initialScrollX,
        initialScrollY = initialScrollY,
        initialZoom = zoom,
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
        initialZoom = zoom,
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

    // 监听外部zoom参数的变化，并更新内部vZoom
    LaunchedEffect(zoom) {
        println("DesktopDocumentView: 新的zoom: $zoom, 旧的vZoom=${stateHolder.vZoom.value}")
        stateHolder.vZoom.value = zoom.toFloat()

        stateHolder.pageViewState.updateViewSize(
            stateHolder.viewSize.value,
            stateHolder.vZoom.value,
            stateHolder.orientation.value
        )
        stateHolder.pageViewState.updateVisiblePages(
            stateHolder.offset.value,
            stateHolder.viewSize.value,
            stateHolder.vZoom.value
        )
    }

    // 桌面端特有的键盘事件处理器
    val handleKeyboardEvent = { event: KeyEvent ->
        if (event.type == KeyEventType.KeyDown) {
            when (event.key) {
                Key.Spacebar -> {
                    if (stateHolder.orientation.value == Vertical) {
                        val maxY =
                            (stateHolder.pageViewState.totalHeight - stateHolder.viewSize.value.height).coerceAtLeast(
                                0f
                            )
                        val keepPx = 6f
                        val newY =
                            (stateHolder.offset.value.y - stateHolder.viewSize.value.height + keepPx).coerceAtLeast(
                                -maxY
                            )
                        stateHolder.offset.value = Offset(stateHolder.offset.value.x, newY)
                    } else {
                        val maxX =
                            (stateHolder.pageViewState.totalWidth - stateHolder.viewSize.value.width).coerceAtLeast(
                                0f
                            )
                        val keepPx = 6f
                        val newX =
                            (stateHolder.offset.value.x - stateHolder.viewSize.value.width + keepPx).coerceAtLeast(
                                -maxX
                            )
                        stateHolder.offset.value = Offset(newX, stateHolder.offset.value.y)
                    }
                    stateHolder.pageViewState.updateOffset(stateHolder.offset.value)
                    true
                }

                Key.PageUp -> {
                    val keepPx = 6f
                    if (stateHolder.orientation.value == Vertical) {
                        val newY =
                            (stateHolder.offset.value.y + stateHolder.viewSize.value.height - keepPx).coerceAtMost(
                                0f
                            )
                        stateHolder.offset.value = Offset(stateHolder.offset.value.x, newY)
                    } else {
                        val newX =
                            (stateHolder.offset.value.x + stateHolder.viewSize.value.width - keepPx).coerceAtMost(
                                0f
                            )
                        stateHolder.offset.value = Offset(newX, stateHolder.offset.value.y)
                    }
                    stateHolder.pageViewState.updateOffset(stateHolder.offset.value)
                    true
                }

                Key.PageDown -> {
                    val keepPx = 6f
                    if (stateHolder.orientation.value == Vertical) {
                        val maxY =
                            (stateHolder.pageViewState.totalHeight - stateHolder.viewSize.value.height).coerceAtLeast(
                                0f
                            )
                        val newY =
                            (stateHolder.offset.value.y - stateHolder.viewSize.value.height + keepPx).coerceAtLeast(
                                -maxY
                            )
                        stateHolder.offset.value = Offset(stateHolder.offset.value.x, newY)
                    } else {
                        val maxX =
                            (stateHolder.pageViewState.totalWidth - stateHolder.viewSize.value.width).coerceAtLeast(
                                0f
                            )
                        val newX =
                            (stateHolder.offset.value.x - stateHolder.viewSize.value.width + keepPx).coerceAtLeast(
                                -maxX
                            )
                        stateHolder.offset.value = Offset(newX, stateHolder.offset.value.y)
                    }
                    stateHolder.pageViewState.updateOffset(stateHolder.offset.value)
                    true
                }

                Key.DirectionUp -> {
                    if (stateHolder.orientation.value == Vertical) {
                        val newY = (stateHolder.offset.value.y + 120f).coerceAtMost(0f)
                        stateHolder.offset.value = Offset(stateHolder.offset.value.x, newY)
                    } else {
                        val newX = (stateHolder.offset.value.x + 120f).coerceAtMost(0f)
                        stateHolder.offset.value = Offset(newX, stateHolder.offset.value.y)
                    }
                    stateHolder.pageViewState.updateOffset(stateHolder.offset.value)
                    true
                }

                Key.DirectionDown -> {
                    if (stateHolder.orientation.value == Vertical) {
                        val maxY =
                            (stateHolder.pageViewState.totalHeight - stateHolder.viewSize.value.height).coerceAtLeast(
                                0f
                            )
                        val newY = (stateHolder.offset.value.y - 120f).coerceAtLeast(-maxY)
                        stateHolder.offset.value = Offset(stateHolder.offset.value.x, newY)
                    } else {
                        val maxX =
                            (stateHolder.pageViewState.totalWidth - stateHolder.viewSize.value.width).coerceAtLeast(
                                0f
                            )
                        val newX = (stateHolder.offset.value.x - 120f).coerceAtLeast(-maxX)
                        stateHolder.offset.value = Offset(newX, stateHolder.offset.value.y)
                    }
                    stateHolder.pageViewState.updateOffset(stateHolder.offset.value)
                    true
                }

                Key.DirectionLeft -> {
                    val newX = (stateHolder.offset.value.x + 120f).coerceAtMost(0f)
                    stateHolder.offset.value = Offset(newX, stateHolder.offset.value.y)
                    stateHolder.pageViewState.updateOffset(stateHolder.offset.value)
                    true
                }

                Key.DirectionRight -> {
                    val maxX =
                        (stateHolder.pageViewState.totalWidth - stateHolder.viewSize.value.width).coerceAtLeast(
                            0f
                        )
                    val newX = (stateHolder.offset.value.x - 120f).coerceAtLeast(-maxX)
                    stateHolder.offset.value = Offset(newX, stateHolder.offset.value.y)
                    stateHolder.pageViewState.updateOffset(stateHolder.offset.value)
                    true
                }

                Key.NumPad0 -> {
                    if (stateHolder.orientation.value == Vertical) {
                        stateHolder.offset.value = Offset(stateHolder.offset.value.x, 0f)
                    } else {
                        stateHolder.offset.value = Offset(0f, stateHolder.offset.value.y)
                    }
                    stateHolder.pageViewState.updateOffset(stateHolder.offset.value)
                    true
                }

                Key.NumPad1 -> {
                    if (stateHolder.orientation.value == Vertical) {
                        val maxY =
                            (stateHolder.pageViewState.totalHeight - stateHolder.viewSize.value.height).coerceAtLeast(
                                0f
                            )
                        stateHolder.offset.value = Offset(stateHolder.offset.value.x, -maxY)
                    } else {
                        val maxX =
                            (stateHolder.pageViewState.totalWidth - stateHolder.viewSize.value.width).coerceAtLeast(
                                0f
                            )
                        stateHolder.offset.value = Offset(-maxX, stateHolder.offset.value.y)
                    }
                    stateHolder.pageViewState.updateOffset(stateHolder.offset.value)
                    true
                }

                Key.Equals -> {
                    if (event.isCtrlPressed || event.isMetaPressed) {
                        val minZoom = 0.5f
                        val maxZoom = 5.0f
                        val newZoom = min(stateHolder.vZoom.value * 1.2f, maxZoom)
                        if (newZoom != stateHolder.vZoom.value) {
                            val centerX = stateHolder.viewSize.value.width / 2f
                            val centerY = stateHolder.viewSize.value.height / 2f
                            handleZoom(
                                newZoom,
                                centerX,
                                centerY,
                                stateHolder.vZoom.value,
                                stateHolder.offset.value,
                                stateHolder.pageViewState,
                                stateHolder.viewSize.value,
                                stateHolder.orientation.value
                            ) { newOffset, newVZoom ->
                                stateHolder.offset.value = newOffset
                                stateHolder.vZoom.value = newVZoom
                                stateHolder.pageViewState.updateViewSize(
                                    stateHolder.viewSize.value,
                                    stateHolder.vZoom.value,
                                    stateHolder.orientation.value
                                )
                                stateHolder.pageViewState.updateOffset(newOffset)
                            }
                        }
                        true
                    } else false
                }

                Key.Minus -> {
                    if (event.isCtrlPressed || event.isMetaPressed) {
                        val minZoom = 0.5f
                        val maxZoom = 5.0f
                        val newZoom = max(stateHolder.vZoom.value / 1.2f, minZoom)
                        if (newZoom != stateHolder.vZoom.value) {
                            val centerX = stateHolder.viewSize.value.width / 2f
                            val centerY = stateHolder.viewSize.value.height / 2f
                            handleZoom(
                                newZoom,
                                centerX,
                                centerY,
                                stateHolder.vZoom.value,
                                stateHolder.offset.value,
                                stateHolder.pageViewState,
                                stateHolder.viewSize.value,
                                stateHolder.orientation.value
                            ) { newOffset, newVZoom ->
                                stateHolder.offset.value = newOffset
                                stateHolder.vZoom.value = newVZoom
                                stateHolder.pageViewState.updateViewSize(
                                    stateHolder.viewSize.value,
                                    stateHolder.vZoom.value,
                                    stateHolder.orientation.value
                                )
                                stateHolder.pageViewState.updateOffset(newOffset)
                            }
                        }
                        true
                    } else false
                }

                Key.Zero -> {
                    if (event.isCtrlPressed || event.isMetaPressed) {
                        if (stateHolder.vZoom.value != 1.0f) {
                            val centerX = stateHolder.viewSize.value.width / 2f
                            val centerY = stateHolder.viewSize.value.height / 2f
                            handleZoom(
                                1.0f,
                                centerX,
                                centerY,
                                stateHolder.vZoom.value,
                                stateHolder.offset.value,
                                stateHolder.pageViewState,
                                stateHolder.viewSize.value,
                                stateHolder.orientation.value
                            ) { newOffset, newVZoom ->
                                stateHolder.offset.value = newOffset
                                stateHolder.vZoom.value = newVZoom
                                stateHolder.pageViewState.updateViewSize(
                                    stateHolder.viewSize.value,
                                    stateHolder.vZoom.value,
                                    stateHolder.orientation.value
                                )
                                stateHolder.pageViewState.updateOffset(newOffset)
                            }
                        }
                        true
                    } else false
                }

                else -> false
            }
        } else {
            false
        }
    }

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
        contentAlignment = Alignment.TopStart
    ) {
        // 统一的手势处理器 + 桌面端扩展修饰符
        CommonGestureHandler(
            state = stateHolder,
            gestureMode = gestureMode,
            pathConfig = pathConfig,
            viewSize = stateHolder.viewSize.value,
            onDoubleTapToolbar = { onDoubleTapToolbar?.invoke() },
            onTapNonPageArea = { onTapNonPageArea?.invoke(it) },
            onPageChanged = { onPageChanged?.invoke(it) },
            modifier = Modifier
                .focusRequester(focusRequester)
                .focusable()
                .onPointerEvent(PointerEventType.Scroll) { event ->
                    val rawScrollDelta =
                        event.changes.firstOrNull()?.scrollDelta ?: return@onPointerEvent
                    val scrollAmount = Offset(
                        x = if (isMacOs) -rawScrollDelta.x else rawScrollDelta.x,
                        y = if (isMacOs) -rawScrollDelta.y else rawScrollDelta.y
                    )

                    val scrollMultiplier = 30f
                    val maxX =
                        (stateHolder.pageViewState.totalWidth - stateHolder.viewSize.value.width).coerceAtLeast(
                            0f
                        )
                    val maxY =
                        (stateHolder.pageViewState.totalHeight - stateHolder.viewSize.value.height).coerceAtLeast(
                            0f
                        )

                    val newX =
                        (stateHolder.offset.value.x + scrollAmount.x * scrollMultiplier).coerceIn(
                            -maxX,
                            0f
                        )
                    val newY =
                        (stateHolder.offset.value.y + scrollAmount.y * scrollMultiplier).coerceIn(
                            -maxY,
                            0f
                        )

                    stateHolder.offset.value = Offset(newX, newY)
                    stateHolder.pageViewState.updateOffset(stateHolder.offset.value)
                }
                .onKeyEvent(handleKeyboardEvent),
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

    // 自动请求焦点，确保键盘事件能被捕获
    // 延迟请求焦点，确保 pointerInput 已经初始化且 viewSize 已确定
    LaunchedEffect(stateHolder.viewSize.value) {
        if (stateHolder.viewSize.value != IntSize.Zero) {
            focusRequester.requestFocus()
        }
    }
}

/**
 * 处理缩放操作的公共方法
 */
private fun handleZoom(
    newZoom: Float,
    centerX: Float,
    centerY: Float,
    currentZoom: Float,
    currentOffset: Offset,
    pageViewState: PageViewState,
    viewSize: IntSize,
    orientation: Int,
    onZoomChanged: (Offset, Float) -> Unit
) {
    val contentX = centerX - currentOffset.x
    val contentY = centerY - currentOffset.y

    val zoomRatio = newZoom / currentZoom

    val newOffsetX = centerX - contentX * zoomRatio
    val newOffsetY = centerY - contentY * zoomRatio

    val maxX = (pageViewState.totalWidth - viewSize.width).coerceAtLeast(0f)
    val maxY = (pageViewState.totalHeight - viewSize.height).coerceAtLeast(0f)

    val clampedOffsetX = newOffsetX.coerceIn(-maxX, 0f)
    val clampedOffsetY = newOffsetY.coerceIn(-maxY, 0f)

    val newOffset = Offset(clampedOffsetX, clampedOffsetY)

    onZoomChanged(newOffset, newZoom)
}
