package com.archko.reader.viewer

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.archko.reader.pdf.component.DocumentView
import com.archko.reader.pdf.component.Horizontal
import com.archko.reader.pdf.component.Vertical
import com.archko.reader.pdf.decoder.PdfDecoder
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.APage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kreader.composeapp.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kreader.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.getString
import java.io.File
import androidx.compose.ui.text.style.TextAlign

/**
 * @author: archko 2025/7/23 :09:09
 */
@Composable
fun CustomView(
    path: String,
    progressPage: Int? = null,
    onDocumentClosed: ((Int, Int, Double, Long, Long, Long) -> Unit)? = null,
    onCloseDocument: (() -> Unit)? = null,
    initialScrollX: Long = 0L,
    initialScrollY: Long = 0L,
    initialZoom: Double = 1.0,
) {
    // 在打开文档时隐藏状态栏
    val context = LocalContext.current
    val isDarkTheme = isSystemInDarkTheme()
    
    LaunchedEffect(Unit) {
        val activity = context as? ComponentActivity
        activity?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.statusBars())
                hide(WindowInsetsCompat.Type.navigationBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    // 在组件销毁时恢复状态栏
    DisposableEffect(Unit) {
        onDispose {
            val activity = context as? ComponentActivity
            activity?.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, true)
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    show(WindowInsetsCompat.Type.statusBars())
                    show(WindowInsetsCompat.Type.navigationBars())
                    // 根据主题设置状态栏文字颜色
                    isAppearanceLightStatusBars = !isDarkTheme
                }
            }
        }
    }

    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var decoder: ImageDecoder? by remember { mutableStateOf(null) }
    var loadingError by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(path) {
        withContext(Dispatchers.IO) {
            println("init:$viewportSize, $path")
            try {
                val pdfDecoder = if (viewportSize == IntSize.Zero) {
                    null
                } else {
                    PdfDecoder(File(path))
                }
                if (pdfDecoder != null) {
                    pdfDecoder.getSize(viewportSize)
                    println("init.size:${pdfDecoder.imageSize.width}-${pdfDecoder.imageSize.height}")
                    decoder = pdfDecoder
                    loadingError = null // 清除之前的错误
                }
            } catch (e: Exception) {
                println("文档加载失败: $path, 错误: ${e.message}")
                loadingError = "document_open_failed"
                decoder = null
            }
        }
    }
    DisposableEffect(path) {
        onDispose {
            println("onDispose:$path, $decoder")
            decoder?.close()
        }
    }

    if (null == decoder) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewportSize = it }) {
            if (loadingError != null) {
                // 显示错误信息
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = when (loadingError) {
                            "document_open_failed" -> stringResource(Res.string.document_open_failed)
                            else -> stringResource(Res.string.document_open_failed)
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = path,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { onCloseDocument?.invoke() }
                    ) {
                        Text(stringResource(Res.string.close))
                    }
                }
            } else {
                // 显示加载中
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        stringResource(Res.string.loading),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    } else {
        fun createList(decoder: ImageDecoder): MutableList<APage> {
            val list = mutableListOf<APage>()
            for (i in 0 until decoder.originalPageSizes.size) {
                val page = decoder.originalPageSizes[i]
                val aPage = APage(i, page.width, page.height, 1f)
                list.add(aPage)
            }
            return list
        }

        val list: MutableList<APage> = remember {
            createList(decoder!!)
        }
        // 工具栏显示状态
        var showToolbar by remember { mutableStateOf(false) }
        // 大纲弹窗状态
        var showOutlineDialog by remember { mutableStateOf(false) }
        // 大纲列表滚动位置状态
        var outlineScrollPosition by remember { mutableIntStateOf(0) }
        // 横竖切换、重排等按钮内部状态
        var isVertical by remember { mutableStateOf(true) }
        var isReflow by remember { mutableStateOf(false) }
        // 当前页与总页数
        var currentPage by remember { mutableIntStateOf(0) }
        val pageCount: Int = list.size
        // 跳转页面状态
        var jumpToPage by remember { mutableIntStateOf(progressPage ?: -1) }
        // 标记是否为用户主动跳转
        var isUserJump by remember { mutableStateOf(false) }
        // 大纲列表
        val outlineList = decoder?.outlineItems ?: emptyList()
        
        // 获取字符串资源
        val currentPageString = stringResource(Res.string.current_page)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewportSize = it }
        ) {
            val context = LocalContext.current
            // 文档视图（最底层）
            DocumentView(
                list = list,
                state = decoder!!,
                jumpToPage = if (jumpToPage >= 0) jumpToPage else null,
                orientation = if (isVertical) Vertical else Horizontal,
                onDocumentClosed = if (decoder != null && list.isNotEmpty()) onDocumentClosed else null,
                onDoubleTapToolbar = { showToolbar = !showToolbar },
                onPageChanged = { page -> currentPage = page },
                onTapNonPageArea = { clickedPageIndex ->
                    // 点击非翻页区域时隐藏工具栏
                    if (showToolbar) {
                        showToolbar = false
                    }
                    val pageText = currentPageString.format(clickedPageIndex + 1)
                    Toast.makeText(context, pageText, Toast.LENGTH_SHORT).show()
                },
                initialScrollX = initialScrollX,
                initialScrollY = initialScrollY,
                initialZoom = initialZoom,
                isUserJump = isUserJump // 使用内部状态
            )

            // 重置跳转页面状态 - 延迟重置，确保DocumentView能接收到跳转指令
            LaunchedEffect(jumpToPage) {
                if (jumpToPage >= 0) {
                    println("CustomView: 设置跳转到第 $jumpToPage 页, isUserJump: $isUserJump")
                    jumpToPage = -1
                    // 重置用户跳转标志
                    isUserJump = false
                }
            }

            AnimatedVisibility(
                visible = showToolbar,
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Surface(
                    color = Color(0xCC222222),
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            // 只有在文档加载成功时才保存进度
                            if (decoder != null && list.isNotEmpty()) {
                                onDocumentClosed?.invoke(currentPage, pageCount, 1.0, 0, 0, 0)
                            }
                            // 然后关闭文档
                            onCloseDocument?.invoke()
                        }) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_back),
                                contentDescription = stringResource(Res.string.back),
                                tint = Color.White
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { isVertical = !isVertical }) {
                            Icon(
                                painter = painterResource(if (isVertical) Res.drawable.ic_vertical else Res.drawable.ic_horizontal),
                                contentDescription = if (isVertical) stringResource(Res.string.vertical) else stringResource(Res.string.horizontal),
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = { showOutlineDialog = true }) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_toc),
                                contentDescription = stringResource(Res.string.outline),
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = { isReflow = !isReflow }) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_reflow),
                                contentDescription = stringResource(Res.string.reflow),
                                tint = if (isReflow) Color.Green else Color.White
                            )
                        }
                        IconButton(onClick = { /* TODO: 搜索功能 */ }) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_search),
                                contentDescription = stringResource(Res.string.search),
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            // 大纲弹窗（最上层）
            if (showOutlineDialog) {
                Dialog(onDismissRequest = { showOutlineDialog = false }) {
                    val hasOutline = outlineList.isNotEmpty()
                    Surface(
                        modifier = if (hasOutline) Modifier.fillMaxSize() else Modifier.wrapContentSize(),
                        color = Color.White.copy(alpha = 0.8f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            modifier = if (hasOutline) Modifier.fillMaxSize() else Modifier.wrapContentSize()
                        ) {
                            Box(
                                Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = { showOutlineDialog = false }) {
                                        Icon(
                                            painter = painterResource(Res.drawable.ic_back),
                                            contentDescription = stringResource(Res.string.back),
                                            tint = Color.Black
                                        )
                                    }
                                    Spacer(Modifier.weight(1f))
                                }
                                Text(
                                    stringResource(Res.string.document_outline),
                                    color = Color.Black,
                                    modifier = Modifier.align(Alignment.Center),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            // 内容区
                            if (!hasOutline) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(250.dp)
                                        .padding(bottom = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(stringResource(Res.string.no_outline), color = Color.Gray)
                                }
                            } else {
                                val lazyListState =
                                    rememberLazyListState(initialFirstVisibleItemIndex = outlineScrollPosition)

                                // 监听滚动位置变化并保存
                                LaunchedEffect(lazyListState.firstVisibleItemIndex) {
                                    outlineScrollPosition = lazyListState.firstVisibleItemIndex
                                }

                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    state = lazyListState
                                ) {
                                    itemsIndexed(outlineList) { index, item ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    jumpToPage = item.page
                                                    isUserJump = true // 大纲点击是用户主动跳转
                                                    showOutlineDialog = false
                                                }
                                                .padding(vertical = 6.dp, horizontal = 16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = item.title ?: "",
                                                color = Color.Black,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                fontSize = 15.sp
                                            )
                                            Spacer(Modifier.weight(1f))
                                            Text(
                                                text = stringResource(Res.string.page_number).format(item.page + 1),
                                                color = Color.Gray,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 底部SeekBar - 考虑导航栏（上层）
            AnimatedVisibility(
                visible = showToolbar,
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    color = Color(0xCC222222),
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    var sliderValue by remember { mutableFloatStateOf((currentPage + 1).toFloat()) }
                    // 当currentPage变化时更新sliderValue
                    LaunchedEffect(currentPage) {
                        sliderValue = (currentPage + 1).toFloat()
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${sliderValue.toInt()} / $pageCount",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Slider(
                            value = sliderValue,
                            onValueChange = { sliderValue = it },
                            valueRange = 1f..pageCount.toFloat(),
                            steps = (pageCount - 2).coerceAtLeast(0),
                            onValueChangeFinished = {
                                val targetPage = sliderValue.toInt() - 1
                                if (targetPage != currentPage && targetPage >= 0 && targetPage < pageCount) {
                                    jumpToPage = targetPage
                                    isUserJump = true // 进度条拖动是用户主动跳转
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.Gray
                            )
                        )
                    }
                }
            }
        }
    }
}