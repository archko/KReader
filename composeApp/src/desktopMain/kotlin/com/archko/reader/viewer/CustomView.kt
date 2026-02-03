package com.archko.reader.viewer

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import com.archko.reader.pdf.cache.ReflowCacheLoader
import com.archko.reader.pdf.component.DesktopDocumentView
import com.archko.reader.pdf.component.GestureMode
import com.archko.reader.pdf.component.Horizontal
import com.archko.reader.pdf.component.PathConfig
import com.archko.reader.pdf.component.Vertical
import com.archko.reader.pdf.decoder.DjvuDecoder
import com.archko.reader.pdf.decoder.ImagesDecoder
import com.archko.reader.pdf.decoder.PdfDecoder
import com.archko.reader.pdf.decoder.TiffDecoder
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.APage
import com.archko.reader.pdf.entity.ReflowBean
import com.archko.reader.pdf.state.AnnotationManager
import com.archko.reader.pdf.tts.SpeechService
import com.archko.reader.pdf.util.FileTypeUtils
import com.archko.reader.viewer.component.DrawingToolbar
import com.archko.reader.viewer.dialog.OutlineDialog
import com.archko.reader.viewer.dialog.PasswordDialog
import com.archko.reader.viewer.dialog.QueueDialog
import com.archko.reader.viewer.tts.TtsQueueService
import com.dokar.sonner.ToastType
import com.dokar.sonner.Toaster
import com.dokar.sonner.rememberToasterState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kreader.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.io.File

/**
 * @author: archko 2025/7/23 :09:09
 */
@Composable
private fun ToolbarContent(
    isVertical: Boolean,
    onOrientationChange: () -> Unit,
    onCloseDocument: (() -> Unit)?,
    currentPath: String,
    speechService: SpeechService,
    currentPage: Int,
    decoder: ImageDecoder,
    onGestureModeChange: (gestureMode: GestureMode) -> Unit,
    gestureMode: GestureMode,
    onCropChange: () -> Unit,
    isCrop: Boolean,
    onZoomChange: (zoom: Double) -> Unit,
    vZoom: Double,
    onOutlineDialogShow: () -> Unit,
    onQueueDialogShow: () -> Unit,
    scope: CoroutineScope,
    onStartSpeaking: (Int, ImageDecoder, SpeechService) -> Unit,
    isReflow: Boolean,
) {
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
            IconButton(onClick = { onCloseDocument?.invoke() }) {
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

            IconButton(onClick = { onOrientationChange() }) {
                Icon(
                    painter = painterResource(if (isVertical) Res.drawable.ic_vertical else Res.drawable.ic_horizontal),
                    contentDescription = if (isVertical) stringResource(Res.string.vertical) else stringResource(
                        Res.string.horizontal
                    ),
                    tint = Color.White
                )
            }

            if (FileTypeUtils.isDocumentFile(currentPath)) {
                val isSpeaking by speechService.isSpeakingFlow.collectAsState()

                IconButton(onClick = {
                    scope.launch {
                        onStartSpeaking(currentPage, decoder, speechService)
                    }
                }) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_tts),
                        contentDescription = stringResource(Res.string.tts),
                        tint = if (isSpeaking) Color.Green else Color.White
                    )
                }
                if (isSpeaking) {
                    IconButton(
                        onClick = { onQueueDialogShow() }
                    ) {
                        Text(
                            text = "📋",
                            color = Color.White,
                            fontSize = 16.sp
                        )
                    }
                }
                IconButton(onClick = {
                    var nMode = gestureMode
                    if (nMode == GestureMode.SELECTION) {
                        nMode = GestureMode.VIEW
                    } else {
                        nMode = GestureMode.SELECTION
                    }
                    onGestureModeChange(nMode)
                }) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_select),
                        contentDescription = "文本选择",
                        tint = if (gestureMode == GestureMode.SELECTION) Color.Green else Color.White
                    )
                }
                IconButton(onClick = {
                    var nMode = gestureMode
                    if (nMode == GestureMode.DRAW) {
                        nMode = GestureMode.VIEW
                    } else {
                        nMode = GestureMode.DRAW
                    }
                    onGestureModeChange(nMode)
                }) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_draw_pen),
                        contentDescription = "标注画线",
                        tint = if (gestureMode == GestureMode.DRAW) Color.Green else Color.White
                    )
                }
            }

            IconButton(onClick = {
                val newZoom = vZoom + 0.1
                if (newZoom <= 5f) {
                    onZoomChange(newZoom)
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
                    onZoomChange(newZoom)
                }
            }) {
                Icon(
                    painter = painterResource(Res.drawable.ic_zoom_out),
                    contentDescription = "",
                    tint = Color.White
                )
            }

            IconButton(onClick = {
                onZoomChange(1.0)
            }) {
                Icon(
                    painter = painterResource(Res.drawable.ic_zoom_reset),
                    contentDescription = "",
                    tint = Color.White
                )
            }

            // 只有文档文件才显示其他按钮
            if (FileTypeUtils.isDocumentFile(currentPath)) {
                IconButton(onClick = { onCropChange() }) {
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
                    IconButton(onClick = { onOutlineDialogShow() }) {
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
                IconButton(onClick = { }) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomView(
    paths: List<String>,
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
    val toaster = rememberToasterState()
    var isCrop by remember { mutableStateOf(crop == true) }
    var isNeedPass by remember { mutableStateOf(false) }
    var pathConfig by remember { mutableStateOf(PathConfig()) }

    // 多文件支持
    val currentPath = paths.getOrNull(0) ?: paths.first()

    val speechService: SpeechService = remember { TtsQueueService() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewportSize = it }
    ) {
        LaunchedEffect(currentPath) {
            withContext(Dispatchers.IO) {
                delay(20)
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
                        if (paths.size > 1) {
                            isCrop = false
                            // 多文件模式：创建ImagesDecoder
                            val files = paths.map { File(it) }
                            ImagesDecoder(files)
                        } else {
                            if (FileTypeUtils.isDjvuFile(currentPath)) {
                                val djvuDecoder = DjvuDecoder(File(currentPath))
                                djvuDecoder
                            } else if (FileTypeUtils.isDocumentFile(currentPath)) {
                                val pdfDecoder = PdfDecoder(File(currentPath))

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
                            } else {
                                isCrop = false
                                ImagesDecoder(listOf(File(currentPath)))
                            }
                        }
                    }
                    if (newDecoder != null) {
                        newDecoder.size(viewportSize)
                        println("init.size:${newDecoder.imageSize.width}-${newDecoder.imageSize.height}")
                        decoder = newDecoder
                        loadingError = null
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
            var showOutlineDialog by remember { mutableStateOf(false) }

            var isVertical by remember { mutableStateOf(scrollOri.toInt() == Vertical) }
            var isReflow by remember { mutableStateOf(reflow == 1L) }
            var gestureMode by remember { mutableStateOf(GestureMode.VIEW) }

            var showQueueDialog by remember { mutableStateOf(false) }

            // 对于单图片文件，根据尺寸自动调整滚动方向
            LaunchedEffect(decoder) {
                decoder?.let { dec ->
                    if (paths.size == 1 &&
                        (FileTypeUtils.isTiffFile(currentPath) || FileTypeUtils.isImageFile(
                            currentPath
                        ))
                    ) {
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
            var currentPage by remember { mutableIntStateOf(0) }
            // 添加标志位以跟踪是否为外部更改
            var isExternalChange by remember { mutableStateOf(false) }
            val pageCount: Int = list.size
            // 跳转页面状态
            var jumpToPage by remember { mutableIntStateOf(progressPage ?: -1) }
            val annotationManager = remember(paths) {
                var fileHash = ""
                if (paths.size == 1) {
                    val first = paths[0]
                    if (FileTypeUtils.isDocumentFile(first)) {
                        fileHash = first.hashCode().toString()
                    }
                }
                AnnotationManager(fileHash)
            }

            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 顶部工具栏 - 始终显示
                ToolbarContent(
                    isVertical = isVertical,
                    onOrientationChange = { isVertical = !isVertical },
                    onCloseDocument = onCloseDocument,
                    currentPath = currentPath,
                    speechService = speechService,
                    currentPage = currentPage,
                    decoder = decoder!!,
                    onGestureModeChange = { mode -> gestureMode = mode },
                    gestureMode = gestureMode,
                    onCropChange = { isCrop = !isCrop },
                    isCrop = isCrop,
                    onOutlineDialogShow = { showOutlineDialog = true },
                    onQueueDialogShow = { showQueueDialog = true },
                    scope = scope,
                    onStartSpeaking = { page, dec, binder ->
                        scope.launch {
                            speakFromCurrentPage(currentPage, decoder!!, speechService)
                            if (!speechService.isSpeaking()) {
                                showQueueDialog = false
                            }
                        }
                    },
                    vZoom = vZoom,
                    onZoomChange = { zoom ->
                        vZoom = zoom
                    },
                    isReflow = isReflow,
                )

                // 队列列表弹窗
                if (showQueueDialog) {
                    QueueDialog(
                        decoder!!.cacheBean,
                        currentSpeakingPage = jumpToPage.toString(),
                        count = 30,
                        onDismiss = { showQueueDialog = false },
                        onItemClick = { reflowBean ->
                            showQueueDialog = false

                            // 从选中的页面重新开始朗读
                            reflowBean.page?.let { pageStr ->
                                val targetPage = pageStr.toIntOrNull() ?: 0
                                // 跳转到目标页面
                                jumpToPage = targetPage

                                scope.launch {
                                    speechService.stop()

                                    // 等待一小段时间确保停止操作完成
                                    delay(500)

                                    speakFromCurrentPage(targetPage, decoder!!, speechService)
                                }
                            }
                        }
                    )
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
                                } else {
                                    // 如果底部工具栏已经隐藏，则显示toast
                                    scope.launch {
                                        toaster.show(
                                            message = getString(Res.string.current_page)
                                                .format(clickedPageIndex + 1, pageCount),
                                            type = ToastType.Error,
                                        )
                                    }
                                }
                            },
                            initialScrollX = initialScrollX,
                            initialScrollY = initialScrollY,
                            initialZoom = vZoom,
                            crop = isCrop,
                            gestureMode = gestureMode,
                            pathConfig = pathConfig,
                            annotationManager = annotationManager,
                        )
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = gestureMode == GestureMode.DRAW,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 0.dp)
                    ) {
                        DrawingToolbar(
                            annotationManager = annotationManager,
                            pathConfig = pathConfig,
                            onClose = { config ->
                                pathConfig = config
                            }
                        )
                    }

                    // 大纲弹窗（最上层）- 只有单文档文件才显示
                    if (showOutlineDialog && FileTypeUtils.shouldShowOutline(listOf(currentPath))) {
                        val outlineList = decoder?.outlineItems ?: emptyList()
                        OutlineDialog(
                            currentPage,
                            outlineList,
                            onClick = { item ->
                                jumpToPage = item.page
                                showOutlineDialog = false
                            },
                            onDismiss = { showOutlineDialog = false },
                        )
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

        Toaster(
            state = toaster,
            maxVisibleToasts = 1,
            alignment = Alignment.Center,
        )
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

    withContext(Dispatchers.IO) {
        try {
            speechService.clearQueue()

            val totalPages = imageDecoder.originalPageSizes.size
            var cacheBean = imageDecoder.cacheBean
            if (cacheBean == null) {
                cacheBean = ReflowCacheLoader.loadReflowFromFile(
                    totalPages,
                    imageDecoder.filePath
                )
            }

            if (cacheBean != null) {
                imageDecoder.cacheBean = cacheBean
                println("TTS: 从缓存获取文本，从第${startPage + 1}页开始")
                val cachedTexts = ReflowCacheLoader.getTextsFromPage(
                    cacheBean,
                    startPage
                )

                for (pageText in cachedTexts) {
                    speechService.addToQueue(pageText)
                }

                val queueSize = speechService.getQueueSize()
                println("TTS: 从缓存添加完成,队列中共有$queueSize 个文本段落")
            } else {
                try {
                    val reflowBean = imageDecoder.decodeReflowSinglePage(startPage)
                    if (reflowBean != null) {
                        speechService.addToQueue(reflowBean)
                        println("TTS: 当前页解析完成，立即开始朗读")
                    }
                } catch (e: Exception) {
                    println("TTS: 当前页解析失败: ${e.message}")
                    speechService.addToQueue(
                        ReflowBean(
                            data = "当前页解析失败",
                            type = ReflowBean.TYPE_STRING,
                            page = startPage.toString()
                        )
                    )
                }

                try {
                    println("TTS: 开始后台解析整个文档，共${totalPages}页")
                    val allTexts = imageDecoder.decodeReflowAllPages()

                    cacheBean = ReflowCacheLoader.saveReflowToFile(
                        totalPages,
                        imageDecoder.filePath,
                        allTexts
                    )
                    imageDecoder.cacheBean = cacheBean

                    for (pageIndex in (startPage + 1) until allTexts.size) {
                        val pageText = allTexts[pageIndex]
                        speechService.addToQueue(pageText)
                    }

                    val queueSize = speechService.getQueueSize()
                    println("TTS: 解析完成，队列中共有$queueSize 个文本段落")
                } catch (e: Exception) {
                    println("TTS: 解析失败: ${e.message}")
                }
            }
        } catch (e: Exception) {
            println("TTS: 朗读初始化失败: ${e.message}")
            speechService.addToQueue(
                ReflowBean(
                    data = "文本解码失败，无法朗读",
                    type = ReflowBean.TYPE_STRING,
                    page = startPage.toString()
                )
            )
        }
    }
}
