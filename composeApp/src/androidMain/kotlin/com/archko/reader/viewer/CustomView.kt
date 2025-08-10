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
import androidx.compose.ui.text.style.TextAlign
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
import com.archko.reader.pdf.decoder.ImagesDecoder
import com.archko.reader.pdf.decoder.PdfDecoder
import com.archko.reader.pdf.decoder.TiffDecoder
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.APage
import com.archko.reader.pdf.util.FileTypeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kreader.composeapp.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.io.File

/**
 * @author: archko 2025/7/23 :09:09
 */
@Composable
fun CustomView(
    paths: List<String>,
    progressPage: Int? = null,
    onSaveDocument: ((page: Int, pageCount: Int, zoom: Double, scrollX: Long, scrollY: Long, scrollOri: Long, reflow: Long) -> Unit)? = null,
    onCloseDocument: (() -> Unit)? = null,
    initialScrollX: Long = 0L,
    initialScrollY: Long = 0L,
    initialZoom: Double = 1.0,
    scrollOri: Long = 0,
    reflow: Long = 0,
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
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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

    // 密码相关状态
    var showPasswordDialog by remember { mutableStateOf(false) }
    var isPasswordError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 多文件支持
    val currentPath = paths.getOrNull(0) ?: paths.first()

    LaunchedEffect(currentPath) {
        withContext(Dispatchers.IO) {
            println("init:$viewportSize, reflow:$reflow, $currentPath")
            if (!FileTypeUtils.isDocumentFile(currentPath)
                && !FileTypeUtils.isImageFile(currentPath)
                && !FileTypeUtils.isTiffFile(currentPath)) {
                loadingError = "document_open_failed"
                decoder = null
                return@withContext
            }
            try {
                val newDecoder: ImageDecoder? = if (viewportSize == IntSize.Zero) {
                    null
                } else {
                    if (paths.size > 1) {
                        // 多文件模式：创建ImagesDecoder
                        val files = paths.map { File(it) }
                        ImagesDecoder(files)
                    } else {
                        // 单文件模式：检查文件类型
                        if (FileTypeUtils.isDocumentFile(currentPath)) {
                            // 文档文件：创建PdfDecoder
                            val pdfDecoder = PdfDecoder(File(currentPath))

                            // 检查是否需要密码
                            if (pdfDecoder.needsPassword) {
                                showPasswordDialog = true
                                isPasswordError = false
                                return@withContext
                            }

                            pdfDecoder
                        } else if (FileTypeUtils.isTiffFile(currentPath)) {
                            val tiffDecoder = TiffDecoder(File(currentPath))
                            tiffDecoder
                        } else {
                            // 图片文件：创建ImagesDecoder（单文件图片也使用ImagesDecoder）
                            ImagesDecoder(listOf(File(currentPath)))
                        }
                    }
                }
                if (newDecoder != null) {
                    newDecoder.size(viewportSize)
                    println("init.size:${newDecoder.imageSize.width}-${newDecoder.imageSize.height}")
                    decoder = newDecoder
                    loadingError = null // 清除之前的错误
                }
            } catch (e: Exception) {
                println("文档加载失败: $currentPath, 错误: ${e.message}")
                loadingError = "document_open_failed"
                decoder = null
            }
        }
    }
    DisposableEffect(currentPath) {
        onDispose {
            println("CustomView.onDispose:$currentPath, $decoder")
            decoder?.close()
        }
    }

    // 处理密码输入
    fun handlePasswordEntered(password: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                decoder?.let { pdfDecoder ->
                    val success = (pdfDecoder as PdfDecoder).authenticatePassword(password)
                    if (success) {
                        // 密码正确，初始化文档
                        pdfDecoder.size(viewportSize)
                        decoder = pdfDecoder
                        loadingError = null
                        showPasswordDialog = false
                        isPasswordError = false
                    } else {
                        // 密码错误，重新显示对话框并显示错误信息
                        showPasswordDialog = true
                        isPasswordError = true
                    }
                }
            }
        }
    }

    // 处理密码对话框取消
    fun handlePasswordDialogDismiss() {
        showPasswordDialog = false
        isPasswordError = false
        onCloseDocument?.invoke()
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
                        text = currentPath,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(Res.string.support_format),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
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

        // 显示密码输入对话框
        if (showPasswordDialog) {
            PasswordDialog(
                fileName = File(currentPath).name,
                onPasswordEntered = { password ->
                    handlePasswordEntered(password)
                },
                onDismiss = {
                    handlePasswordDialogDismiss()
                },
                isPasswordError = isPasswordError
            )
        }
    } else {
        fun createList(decoder: ImageDecoder): MutableList<APage> {
            if (!decoder.aPageList.isNullOrEmpty()) {
                return decoder.aPageList!!
            }
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
        var isVertical by remember { mutableStateOf(scrollOri.toInt() == Vertical) }
        var isReflow by remember { mutableStateOf(reflow == 1L) }

        // 使用 derivedStateOf 来避免 orientation 变化时重新组合 DocumentView
        val orientation by remember { derivedStateOf { if (isVertical) Vertical else Horizontal } }
        // 当前页与总页数
        var currentPage by remember { mutableIntStateOf(0) }
        // 添加标志位以跟踪是否为外部更改
        var isExternalChange by remember { mutableStateOf(false) }
        val pageCount: Int = list.size
        // 跳转页面状态
        var jumpToPage by remember { mutableIntStateOf(progressPage ?: -1) }
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

            // 根据reflow状态选择显示模式
            if (isReflow && FileTypeUtils.isDocumentFile(currentPath)) {
                // Reflow视图
                ReflowView(
                    decoder = decoder!!,
                    pageCount = pageCount,
                    onSaveDocument = if (list.isNotEmpty() && decoder is PdfDecoder) onSaveDocument else null,
                    onCloseDocument = {
                        println("onCloseDocument.isReflow:$isReflow")
                        if (!isReflow) {
                            onCloseDocument?.invoke()
                        }
                    }, // 只在非重排模式下传递关闭回调
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
                    jumpToPage = jumpToPage,
                    initialScrollX = initialScrollX,
                    initialScrollY = initialScrollY,
                    initialZoom = initialZoom,
                    initialOrientation = orientation,
                    reflow = 1L,
                )
            } else {
                // 文档视图（最底层）
                DocumentView(
                    list = list,
                    state = decoder!!,
                    jumpToPage = jumpToPage,
                    initialOrientation = orientation,
                    onSaveDocument = if (list.isNotEmpty() && decoder is PdfDecoder) onSaveDocument else null,
                    onCloseDocument = {
                        println("onCloseDocument.isReflow:$isReflow")
                        if (!isReflow) {
                            onCloseDocument?.invoke()
                        }
                    }, // 只在非重排模式下传递关闭回调
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
                )
            }

            AnimatedVisibility(
                visible = showToolbar,
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Surface(
                    color = Color(0xCC222222),
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            onCloseDocument?.invoke()
                        }) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_back),
                                contentDescription = stringResource(Res.string.back),
                                tint = Color.White
                            )
                        }
                        Spacer(Modifier.weight(1f))

                        // 方向按钮 - 文档和图片都显示
                        IconButton(onClick = { isVertical = !isVertical }) {
                            Icon(
                                painter = painterResource(if (isVertical) Res.drawable.ic_vertical else Res.drawable.ic_horizontal),
                                contentDescription = if (isVertical) stringResource(Res.string.vertical) else stringResource(
                                    Res.string.horizontal
                                ),
                                tint = Color.White
                            )
                        }

                        // 只有文档文件才显示其他按钮
                        if (FileTypeUtils.isDocumentFile(currentPath)) {
                            // 只有单文档文件才显示大纲按钮
                            if (FileTypeUtils.shouldShowOutline(paths)) {
                                IconButton(onClick = { showOutlineDialog = true }) {
                                    Icon(
                                        painter = painterResource(Res.drawable.ic_toc),
                                        contentDescription = stringResource(Res.string.outline),
                                        tint = Color.White
                                    )
                                }
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
            }

            // 大纲弹窗（最上层）- 只有单文档文件才显示
            if (showOutlineDialog && FileTypeUtils.shouldShowOutline(paths)) {
                Dialog(onDismissRequest = {
                    showOutlineDialog = false
                }) {
                    val hasOutline = outlineList.isNotEmpty()
                    val lazyListState =
                        rememberLazyListState(initialFirstVisibleItemIndex = outlineScrollPosition)
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
                                    IconButton(onClick = {
                                        // 关闭弹窗时记录当前滚动位置
                                        outlineScrollPosition = lazyListState.firstVisibleItemIndex
                                        showOutlineDialog = false
                                    }) {
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
                                // 在弹窗关闭时记录滚动位置
                                LaunchedEffect(showOutlineDialog) {
                                    if (!showOutlineDialog) {
                                        // 弹窗关闭时记录当前滚动位置
                                        outlineScrollPosition = lazyListState.firstVisibleItemIndex
                                    }
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
                                                    // 点击项目时记录当前滚动位置
                                                    outlineScrollPosition =
                                                        lazyListState.firstVisibleItemIndex
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
                                                text = stringResource(Res.string.page_number).format(
                                                    item.page + 1
                                                ),
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
                ) {
                    var sliderValue by remember { mutableFloatStateOf((currentPage + 1).toFloat()) }
                    // 当currentPage变化时更新sliderValue
                    LaunchedEffect(currentPage) {
                        isExternalChange = true
                        sliderValue = (currentPage + 1).toFloat()
                        isExternalChange = false
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
                                if (!isExternalChange) {
                                    val targetPage = sliderValue.toInt() - 1
                                    if (targetPage != currentPage && targetPage >= 0 && targetPage < pageCount) {
                                        jumpToPage = targetPage
                                    }
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