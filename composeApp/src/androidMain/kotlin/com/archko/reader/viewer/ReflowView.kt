package com.archko.reader.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archko.reader.pdf.decoder.ParseTextMain
import com.archko.reader.pdf.decoder.PdfDecoder
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.ReflowBean

@Composable
fun ReflowView(
    decoder: ImageDecoder,
    pageCount: Int,
    onPageChanged: ((page: Int) -> Unit)? = null,
    jumpToPage: Int? = null,
) {
    val lazyListState = rememberLazyListState()

    // 页面内容缓存
    var pageContents by remember { mutableStateOf<Map<Int, List<ReflowBean>>>(emptyMap()) }

    // 处理页面跳转
    LaunchedEffect(jumpToPage) {
        jumpToPage?.let { page ->
            if (page >= 0 && page < pageCount) {
                lazyListState.animateScrollToItem(page)
            }
        }
    }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        itemsIndexed((0 until pageCount).toList()) { index, pageIndex ->
            ReflowPageItem(
                pageIndex = pageIndex,
                content = pageContents[pageIndex] ?: emptyList(),
                onContentLoaded = { content ->
                    pageContents = pageContents + (pageIndex to content)
                },
                decoder = decoder
            )
        }
    }
}

@Composable
private fun ReflowPageItem(
    pageIndex: Int,
    content: List<ReflowBean>,
    onContentLoaded: (List<ReflowBean>) -> Unit,
    decoder: ImageDecoder
) {
    val context = LocalContext.current

    // 单线程加载页面内容（MuPDF不支持多线程）
    LaunchedEffect(pageIndex) {
        if (content.isEmpty()) {
            try {
                // 这里需要根据实际的decoder类型调用decodeReflow方法
                // 由于ImageDecoder接口没有decodeReflow方法，我们需要类型转换
                val loadedContent = when (decoder) {
                    is PdfDecoder -> {
                        decoder.decodeReflow(pageIndex)
                    }

                    else -> {
                        // 对于其他类型的decoder，返回空内容
                        emptyList()
                    }
                }
                onContentLoaded(loadedContent)
            } catch (e: Exception) {
                println("Failed to decode reflow content for page $pageIndex: $e")
                onContentLoaded(emptyList())
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        // 页面内容
        if (content.isEmpty()) {
            // 加载中状态
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            // 显示内容
            content.forEach { item ->
                when (item.type) {
                    ReflowBean.TYPE_STRING -> {
                        Text(
                            text = item.data ?: "",
                            fontSize = 28.sp,
                        )
                    }

                    ReflowBean.TYPE_IMAGE -> {
                        // 使用BitmapBean处理图片
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
                                    .width(bitmapBean.width.dp)
                                    .height(bitmapBean.height.dp),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            // 图片解码失败时的占位符
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .background(Color.LightGray),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "[图片解码失败]",
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
} 