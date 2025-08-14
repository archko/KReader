package com.archko.reader.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.archko.reader.pdf.decoder.ParseTextMain
import com.archko.reader.pdf.decoder.PdfDecoder
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.ReflowBean
import com.archko.reader.pdf.util.Dispatcher
import kotlinx.coroutines.withContext

@Composable
fun ReflowView(
    decoder: ImageDecoder,
    pageCount: Int,
    onPageChanged: ((page: Int) -> Unit)? = null,
    jumpToPage: Int? = null,
    onSaveDocument: ((page: Int, pageCount: Int, zoom: Double, scrollX: Long, scrollY: Long, scrollOri: Long, reflow: Long, crop: Long) -> Unit)? = null,
    onCloseDocument: (() -> Unit)? = null,
    onDoubleTapToolbar: (() -> Unit)? = null,
    onTapNonPageArea: ((pageIndex: Int) -> Unit)? = null,
    initialScrollX: Long = 0L,
    initialScrollY: Long = 0L,
    initialZoom: Double = 1.0,
    initialOrientation: Int,
    reflow: Long = 0,
) {
    val lazyListState = rememberLazyListState()

    // 使用独立的状态来跟踪当前页面，避免频繁访问 firstVisibleItemIndex
    var currentPage by remember { mutableIntStateOf(0) }

    var toPage by remember { mutableIntStateOf(-1) }
    var isJumping by remember { mutableStateOf(false) } // 添加跳转标志

    LaunchedEffect(jumpToPage) {
        println("DocumentView: jumpToPage:$jumpToPage,")

        isJumping = true // 设置跳转标志
        // 1. 有明确的跳转页码
        // 2. 已初始化
        // 3. 是用户主动跳转（如进度条拖动）或者没有初始偏移量
        if (null != jumpToPage && toPage != jumpToPage) {
            isJumping = true // 设置跳转标志
            toPage = jumpToPage
            println("DocumentView: 执行跳转到第${jumpToPage}页, page:$toPage")

            if (toPage >= 0 && toPage < pageCount) {
                lazyListState.animateScrollToItem(toPage)
            }
            isJumping = false // 清除跳转标志
        }
    }

    // 监听页面变化并回调 - 使用 derivedStateOf 优化性能
    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.firstVisibleItemIndex }
            .collect { newPage ->
                if (!isJumping && newPage != currentPage) {
                    currentPage = newPage
                    onPageChanged?.invoke(newPage)
                }
            }
    }

    // 保存文档状态的公共方法
    fun saveDocumentState() {
        println("ReflowView: 保存记录:page:$currentPage, pc:$pageCount, reflow:$reflow")

        onSaveDocument?.invoke(
            currentPage,
            pageCount,
            initialZoom,
            initialScrollX,
            initialScrollY,
            initialOrientation.toLong(),
            reflow,
            1L
        )
    }

    // 监听生命周期事件，在onPause时保存记录
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                saveDocumentState()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // 在组件销毁时也保存一次状态
            saveDocumentState()
            onCloseDocument?.invoke()
        }
    }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offsetTap ->
                        // 计算点击的页面
                        val clickedPage = calculateClickedPage(
                            offsetTap,
                            currentPage,
                            pageCount
                        )
                        onTapNonPageArea?.invoke(clickedPage)
                    },
                    onDoubleTap = { offsetTap ->
                        val y = offsetTap.y
                        val height = size.height.toFloat()
                        if (y >= height / 4 && y <= height * 3 / 4) {
                            onDoubleTapToolbar?.invoke()
                        }
                    }
                )
            },
    ) {
        itemsIndexed((0 until pageCount).toList()) { index, pageIndex ->
            ReflowPageItem(
                pageIndex = pageIndex,
                decoder = decoder
            )
        }
    }
}

@Composable
private fun ReflowPageItem(
    pageIndex: Int,
    decoder: ImageDecoder
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var pageContents by remember { mutableStateOf<List<ReflowBean>>(emptyList()) }

    LaunchedEffect(pageIndex) {
        withContext(Dispatcher.DECODE) {
            pageContents = when (decoder) {
                is PdfDecoder -> {
                    decoder.decodeReflow(pageIndex)
                }

                else -> {
                    // 对于其他类型的decoder，返回空内容
                    emptyList()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        if (pageContents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Loading...",
                    fontSize = 18.sp,
                )
            }
        } else {
            pageContents.forEach { item ->
                when (item.type) {
                    ReflowBean.TYPE_STRING -> {
                        Text(
                            text = item.data ?: "",
                            fontSize = 18.sp,
                        )
                    }

                    ReflowBean.TYPE_IMAGE -> {
                        val bitmapBean = ParseTextMain.decodeBitmap(
                            item.data ?: "",
                            1.0f, // systemScale
                            context.resources.displayMetrics.heightPixels,
                            context.resources.displayMetrics.widthPixels,
                            context
                        )

                        if (bitmapBean != null) {
                            Image(
                                bitmap = bitmapBean.bitmap.asImageBitmap(),
                                contentDescription = "图片",
                                modifier = Modifier
                                    .width(with(density) { bitmapBean.width.toDp() })
                                    .height(with(density) { bitmapBean.height.toDp() })
                                    .padding(vertical = 2.dp),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .background(Color.LightGray),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "[Error]",
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider(
            color = Color(0xff666666),
            modifier = Modifier
                .padding(top = 2.dp)
                .width(100.dp)
        )
    }
}

/**
 * 根据点击坐标计算点击的页面索引
 */
private fun calculateClickedPage(
    tapOffset: Offset,
    currentPage: Int,
    pageCount: Int
): Int {
    // 对于 LazyColumn，直接返回当前页面索引
    // 避免频繁访问 lazyListState.firstVisibleItemIndex
    return currentPage.coerceIn(0, pageCount - 1)
}