package com.archko.reader.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.archko.reader.pdf.component.DesktopDocumentView
import com.archko.reader.pdf.component.Horizontal
import com.archko.reader.pdf.component.Vertical
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomView(
    currentPath: String,
    progressPage: Int? = null,
    onSaveDocument: ((page: Int, pageCount: Int, zoom: Double, scrollX: Long, scrollY: Long, scrollOri: Long, reflow: Long, crop: Long) -> Unit)? = null,
    onCloseDocument: (() -> Unit)? = null,
    initialScrollX: Long = 0L,
    initialScrollY: Long = 0L,
    initialZoom: Double = 1.0,
    scrollOri: Long = 0,
    reflow: Long = 0,
    crop: Boolean? = null,
) {
    var vZoom by remember { mutableDoubleStateOf(initialZoom) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var decoder: ImageDecoder? by remember { mutableStateOf(null) }
    var loadingError by remember { mutableStateOf<String?>(null) }

    // 密码相关状态
    var showPasswordDialog by remember { mutableStateOf(false) }
    var isPasswordError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var isCrop by remember { mutableStateOf(crop == true) }
    var isNeedPass by remember { mutableStateOf(false) }

    val speechService: SpeechService = remember { TtsQueueService() }

    LaunchedEffect(currentPath) {
        withContext(Dispatchers.IO) {
            println("init:$viewportSize, reflow:$reflow, crop:$crop, $currentPath")
            if (!FileTypeUtils.isDocumentFile(currentPath)
                && !FileTypeUtils.isImageFile(currentPath)
                && !FileTypeUtils.isTiffFile(currentPath)
            ) {
                loadingError = "document_open_failed"
                decoder = null
                return@withContext
            }
            try {
                val newDecoder: ImageDecoder? = if (viewportSize == IntSize.Zero) {
                    null
                } else {
                    if (FileTypeUtils.isDocumentFile(currentPath)) {
                        val pdfDecoder = PdfDecoder(File(currentPath))

                        // 检查是否需要密码
                        if (pdfDecoder.needsPassword) {
                            showPasswordDialog = true
                            isPasswordError = false
                            decoder = pdfDecoder
                            isNeedPass = true
                            return@withContext
                        }

                        pdfDecoder
                    } else if (FileTypeUtils.isTiffFile(currentPath)) {
                        isCrop = false
                        val tiffDecoder = TiffDecoder(File(currentPath))
                        tiffDecoder
                    } else if (FileTypeUtils.isImageFile(currentPath)) {
                        isCrop = false
                        val pdfDecoder = PdfDecoder(File(currentPath))
                        pdfDecoder
                    } else {
                        null
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
            // 清理 TTS 服务
            if (speechService is TtsQueueService) {
                speechService.destroy()
            } else {
                speechService.stop()
            }
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
                        loadingError = null
                        showPasswordDialog = false
                        isPasswordError = false
                        isNeedPass = false
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

    if (isNeedPass) {
    } else if (null == decoder) {
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
        // 工具栏显示状态 - 顶部工具栏始终显示，底部可以隐藏
        var showBottomToolbar by remember { mutableStateOf(true) }
        // 大纲弹窗状态
        var showOutlineDialog by remember { mutableStateOf(false) }

        // 横竖切换、重排等按钮内部状态
        var isVertical by remember { mutableStateOf(scrollOri.toInt() == Vertical) }
        var isReflow by remember { mutableStateOf(reflow == 1L) }

        // 对于图片文件，根据尺寸自动调整滚动方向
        LaunchedEffect(decoder) {
            decoder?.let { dec ->
                if (FileTypeUtils.isTiffFile(currentPath) || FileTypeUtils.isImageFile(currentPath)) {
                    if (dec.originalPageSizes.isNotEmpty()) {
                        val firstPageSize = dec.originalPageSizes[0]
                        val width = firstPageSize.width
                        val height = firstPageSize.height
                        println("isVertical:$isVertical, width:$width-$height, $currentPath")
                        // 如果图片的高度小于宽度的1/3，则切换为横向滚动
                        if (height < width / 3) {
                            isVertical = false
                        }
                    }
                }
            }
        }

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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewportSize = it }
        ) {
            // 顶部工具栏 - 始终显示
            Surface(
                color = Color(0xff000000),
                shadowElevation = 8.dp, // 添加阴影确保层级
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .padding(horizontal = 8.dp, vertical = 0.dp),
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
                    Text(
                        text = currentPath,
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.weight(1f))

                    if (FileTypeUtils.isDocumentFile(currentPath)) {
                        IconButton(onClick = {
                            scope.launch {
                                speakFromCurrentPage(currentPage, decoder!!, speechService)
                            }
                        }) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_tts),
                                contentDescription = stringResource(Res.string.tts),
                                tint = if (speechService.isSpeaking()) Color.Green else Color.White
                            )
                        }
                    }

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

                    IconButton(onClick = {
                        val newZoom = vZoom + 0.1
                        if (newZoom <= 5f) {
                            vZoom = newZoom
                        }
                    }) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_zoom_in),
                            contentDescription = "",
                            tint = Color.White
                        )
                    }

                    IconButton(onClick = {
                        val newZoom = vZoom - 0.1
                        if (newZoom >= 0.51f) {
                            vZoom = newZoom
                        }
                    }) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_zoom_out),
                            contentDescription = "",
                            tint = Color.White
                        )
                    }

                    IconButton(onClick = {
                        vZoom = 1.0
                    }) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_zoom_reset),
                            contentDescription = "",
                            tint = Color.White
                        )
                    }

                    // 只有文档文件才显示其他按钮
                    if (FileTypeUtils.isDocumentFile(currentPath)) {
                        IconButton(onClick = { isCrop = !isCrop }) {
                            Icon(
                                painter = painterResource(if (isCrop) Res.drawable.ic_crop else Res.drawable.ic_no_crop),
                                contentDescription = if (isCrop) stringResource(Res.string.crop) else stringResource(
                                    Res.string.no_crop
                                ),
                                tint = Color.White
                            )
                        }
                        // 只有单文档文件才显示大纲按钮
                        if (FileTypeUtils.shouldShowOutline(listOf(currentPath))) {
                            IconButton(onClick = { showOutlineDialog = true }) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_toc),
                                    contentDescription = stringResource(Res.string.outline),
                                    tint = Color.White
                                )
                            }
                        }
                        //IconButton(onClick = { isReflow = !isReflow }) {
                        //    Icon(
                        //        painter = painterResource(Res.drawable.ic_reflow),
                        //        contentDescription = stringResource(Res.string.reflow),
                        //        tint = if (isReflow) Color.Green else Color.White
                        //    )
                        //}
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

            // 文档视图 - 占据剩余空间
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds() // 确保内容不会绘制到边界外
            ) {
                if (isReflow && FileTypeUtils.isDocumentFile(currentPath)) {
                    // Reflow视图
                } else {
                    DesktopDocumentView(
                        list = list,
                        state = decoder!!,
                        jumpToPage = jumpToPage,
                        initialOrientation = orientation,
                        onSaveDocument = onSaveDocument,
                        onCloseDocument = {
                            println("onCloseDocument.isReflow:$isReflow")
                            if (!isReflow) {
                                onCloseDocument?.invoke()
                            }
                        }, // 只在非重排模式下传递关闭回调
                        onDoubleTapToolbar = { showBottomToolbar = !showBottomToolbar },
                        onPageChanged = { page -> currentPage = page },
                        onTapNonPageArea = { clickedPageIndex ->
                            // 点击非翻页区域时隐藏底部工具栏
                            if (showBottomToolbar) {
                                showBottomToolbar = false
                            }
                            //val pageText = currentPageString.format(clickedPageIndex + 1)
                            //Toast.makeText(context, pageText, Toast.LENGTH_SHORT).show()
                        },
                        initialScrollX = initialScrollX,
                        initialScrollY = initialScrollY,
                        initialZoom = vZoom,
                        crop = isCrop,
                    )
                }

                // 大纲弹窗（最上层）- 只有单文档文件才显示
                if (showOutlineDialog && FileTypeUtils.shouldShowOutline(listOf(currentPath))) {
                    Dialog(onDismissRequest = {
                        showOutlineDialog = false
                    }) {
                        val hasOutline = outlineList.isNotEmpty()
                        // 根据当前页码找到最接近的大纲项位置
                        val initialOutlineIndex =
                            outlineList.indexOfFirst { it.page >= currentPage }
                                .takeIf { it != -1 }
                                ?: outlineList.indexOfLast { it.page <= currentPage }
                                    .takeIf { it != -1 } ?: 0
                        val lazyListState =
                            rememberLazyListState(
                                initialFirstVisibleItemIndex = initialOutlineIndex.coerceAtLeast(
                                    0
                                )
                            )
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
                                        Text(
                                            stringResource(Res.string.no_outline),
                                            color = Color.Gray
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        state = lazyListState
                                    ) {
                                        itemsIndexed(
                                            outlineList,
                                            key = { index, item -> index }) { index, item ->
                                            val isSelected = index == initialOutlineIndex
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.surfaceVariant
                                                        else Color.Transparent
                                                    )
                                                    .clickable {
                                                        jumpToPage = item.page
                                                        showOutlineDialog = false
                                                    }
                                                    .padding(vertical = 8.dp, horizontal = 16.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = item.title ?: "",
                                                    color = Color.Black,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    fontSize = 15.sp,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    text = stringResource(Res.string.page_number).format(
                                                        item.page + 1
                                                    ),
                                                    maxLines = 1,
                                                    softWrap = false,
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

                // 底部SeekBar - 覆盖在文档上方
                androidx.compose.animation.AnimatedVisibility(
                    visible = showBottomToolbar,
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Surface(
                        color = Color(0xCC222222),
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
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
                                .padding(horizontal = 16.dp, vertical = 4.dp) // 减小垂直padding
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(20.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color.White,
                                    inactiveTrackColor = Color.Gray
                                ),
                                track = { sliderState ->
                                    SliderDefaults.Track(
                                        sliderState = sliderState,
                                        modifier = Modifier.height(2.dp), // 设置轨道高度为2dp
                                        colors = SliderDefaults.colors(
                                            activeTrackColor = Color.White,
                                            inactiveTrackColor = Color.Gray
                                        )
                                    )
                                },
                                thumb = {
                                    SliderDefaults.Thumb(
                                        interactionSource = remember { MutableInteractionSource() },
                                        modifier = Modifier.size(16.dp), // 设置滑块大小为16dp
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color.White
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

suspend fun speakFromCurrentPage(
    startPage: Int,
    imageDecoder: ImageDecoder,
    speechService: SpeechService
) {
    if (speechService.isSpeaking()) {
        println("TTS: 正在朗读，停止当前朗读")
        speechService.stop()
        return
    }

    if (imageDecoder is PdfDecoder) {
        withContext(Dispatchers.IO) {
            try {
                speechService.clearQueue()

                val list = imageDecoder.decodeReflow(startPage)
                println("TTS: 解码完成共:${list.size} 页")

                for (pageText in list) {
                    speechService.addToQueue(pageText)
                }

                val queueSize = speechService.getQueueSize()
                println("TTS: 添加完成,队列中共有$queueSize 个文本段落")
            } catch (e: Exception) {
                println("TTS: 批量解码失败: ${e.message}")
                speechService.addToQueue("文本解码失败，无法朗读")
            }
        }
    }
}