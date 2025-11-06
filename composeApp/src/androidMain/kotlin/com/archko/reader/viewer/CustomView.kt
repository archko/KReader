package com.archko.reader.viewer

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.archko.reader.pdf.cache.ReflowCacheLoader
import com.archko.reader.pdf.component.DocumentView
import com.archko.reader.pdf.component.Horizontal
import com.archko.reader.pdf.component.Vertical
import com.archko.reader.pdf.decoder.ImagesDecoder
import com.archko.reader.pdf.decoder.PdfDecoder
import com.archko.reader.pdf.decoder.TiffDecoder
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.APage
import com.archko.reader.pdf.entity.ReflowBean
import com.archko.reader.pdf.util.FileTypeUtils
import com.archko.reader.pdf.util.FontCSSGenerator
import com.archko.reader.pdf.util.IntentFile
import com.archko.reader.viewer.dialog.FontDialog
import com.archko.reader.viewer.dialog.OutlineDialog
import com.archko.reader.viewer.dialog.PasswordDialog
import com.archko.reader.viewer.dialog.QueueDialog
import com.archko.reader.viewer.tts.TtsServiceBinder
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

        // 获取应用可用内存并设置缓存限制为1/4
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val cacheMemoryLimit = maxMemory / 4
        com.archko.reader.pdf.cache.ImageCache.setMaxMemory(cacheMemoryLimit)

        println("ImageCache: 设置内存限制为 ${cacheMemoryLimit / 1024 / 1024}MB (总内存: ${maxMemory / 1024 / 1024}MB)")
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
    var isCrop by remember { mutableStateOf(crop == true) }
    var isNeedPass by remember { mutableStateOf(false) }

    // 字体选择相关状态
    var showFontDialog by remember { mutableStateOf(false) }

    // 多文件支持
    val currentPath = paths.getOrNull(0) ?: paths.first()

    // TTS服务绑定器 - 只有文档文件才初始化
    var ttsServiceBinder by remember { mutableStateOf<TtsServiceBinder?>(null) }

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
                    if (paths.size > 1) {
                        isCrop = false
                        // 多文件模式：创建ImagesDecoder
                        val files = paths.map { File(it) }
                        ImagesDecoder(files)
                    } else {
                        if (FileTypeUtils.isDocumentFile(currentPath)) {
                            ttsServiceBinder = TtsServiceBinder(context)
                            ttsServiceBinder?.bindService()

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
            ttsServiceBinder?.unbindService()
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

        var showToolbar by remember { mutableStateOf(false) }
        var showOutlineDialog by remember { mutableStateOf(false) }

        var isVertical by remember { mutableStateOf(scrollOri.toInt() == Vertical) }
        var isReflow by remember { mutableStateOf(reflow == 1L) }
        var isTextSelectionMode by remember { mutableStateOf(false) }

        var showSleepDialog by remember { mutableStateOf(false) }
        var showQueueDialog by remember { mutableStateOf(false) }

        // 对于单图片文件，根据尺寸自动调整滚动方向
        LaunchedEffect(decoder) {
            decoder?.let { dec ->
                if (paths.size == 1 &&
                    (FileTypeUtils.isTiffFile(currentPath) || FileTypeUtils.isImageFile(currentPath))
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
        // 当前页与总页数
        var currentPage by remember { mutableIntStateOf(0) }
        // 添加标志位以跟踪是否为外部更改
        var isExternalChange by remember { mutableStateOf(false) }
        val pageCount: Int = list.size
        // 跳转页面状态
        var jumpToPage by remember { mutableIntStateOf(progressPage ?: -1) }

        val currentPageString = stringResource(Res.string.current_page)

        var isSpeaking by remember { mutableStateOf(false) }

        LaunchedEffect(ttsServiceBinder) {
            ttsServiceBinder?.isSpeakingFlow?.collect { speaking ->
                isSpeaking = speaking
            }
        }

        LaunchedEffect(ttsServiceBinder) {
            ttsServiceBinder?.setOnSpeechCompleteCallback { completedPage ->
                completedPage?.let { pageStr ->
                    val targetPage = pageStr.toIntOrNull()
                    println("SpeechComplete:targetPage:$targetPage, old:$jumpToPage")
                    if (null != targetPage && targetPage != jumpToPage) {
                        jumpToPage = targetPage
                    }
                }
            }
        }

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
                        // 点击非翻页区域时隐藏工具栏，但朗读时保持朗读工具条显示
                        if (showToolbar) {
                            showToolbar = false
                        }
                        val pageText = currentPageString.format(clickedPageIndex + 1, pageCount)
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
                        // 点击非翻页区域时隐藏工具栏，但朗读时保持朗读工具条显示
                        if (showToolbar) {
                            showToolbar = false
                        } else {
                            val pageText = currentPageString.format(clickedPageIndex + 1, pageCount)
                            Toast.makeText(context, pageText, Toast.LENGTH_SHORT).show()
                        }
                    },
                    initialScrollX = initialScrollX,
                    initialScrollY = initialScrollY,
                    initialZoom = initialZoom,
                    crop = isCrop,
                    isTextSelectionMode = isTextSelectionMode,
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
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { onCloseDocument?.invoke() }) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_back),
                                contentDescription = stringResource(Res.string.back),
                                tint = Color.White
                            )
                        }

                        LazyRow(
                            modifier = Modifier
                                .fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            item {
                                IconButton(onClick = {
                                    isVertical = !isVertical
                                    showToolbar = false
                                }) {
                                    Icon(
                                        painter = painterResource(if (isVertical) Res.drawable.ic_vertical else Res.drawable.ic_horizontal),
                                        contentDescription = if (isVertical) stringResource(Res.string.vertical) else stringResource(
                                            Res.string.horizontal
                                        ),
                                        tint = Color.White
                                    )
                                }
                            }

                            // 只有文档文件才显示其他按钮
                            if (FileTypeUtils.isDocumentFile(currentPath)) {
                                ttsServiceBinder?.let { binder ->
                                    item {
                                        val isConnected by binder.isConnected.collectAsState()

                                        IconButton(
                                            onClick = {
                                                if (isConnected && binder.isServiceInitialized()) {
                                                    if (isSpeaking) {
                                                        binder.pause()
                                                    } else {
                                                        scope.launch {
                                                            speakFromCurrentPage(
                                                                currentPage,
                                                                decoder!!,
                                                                binder
                                                            )
                                                        }
                                                    }
                                                }
                                            },
                                            enabled = true
                                        ) {
                                            Icon(
                                                painter = painterResource(Res.drawable.ic_tts),
                                                contentDescription = if (isSpeaking) "暂停朗读" else "开始朗读",
                                                tint = if (isSpeaking) Color.Green else Color.White
                                            )
                                        }
                                    }
                                }

                                item {
                                    IconButton(onClick = {
                                        isTextSelectionMode = !isTextSelectionMode
                                    }) {
                                        Icon(
                                            painter = painterResource(Res.drawable.ic_select),
                                            contentDescription = "文本选择",
                                            tint = if (isTextSelectionMode) Color.Green else Color.White
                                        )
                                    }
                                }

                                item {
                                    IconButton(onClick = { isCrop = !isCrop }) {
                                        Icon(
                                            painter = painterResource(if (isCrop) Res.drawable.ic_crop else Res.drawable.ic_no_crop),
                                            contentDescription = if (isCrop) stringResource(Res.string.crop) else stringResource(
                                                Res.string.no_crop
                                            ),
                                            tint = Color.White
                                        )
                                    }
                                }

                                if (FileTypeUtils.shouldShowOutline(paths)) {
                                    item {
                                        IconButton(onClick = { showOutlineDialog = true }) {
                                            Icon(
                                                painter = painterResource(Res.drawable.ic_toc),
                                                contentDescription = stringResource(Res.string.outline),
                                                tint = Color.White
                                            )
                                        }
                                    }
                                }

                                if (IntentFile.isReflowable(currentPath)) {
                                    item {
                                        IconButton(onClick = { showFontDialog = true }) {
                                            Icon(
                                                painter = painterResource(Res.drawable.ic_font),
                                                contentDescription = stringResource(Res.string.font),
                                                tint = Color.White
                                            )
                                        }
                                    }

                                    /*item {
                                        IconButton(onClick = { isReflow = !isReflow }) {
                                            Icon(
                                                painter = painterResource(Res.drawable.ic_reflow),
                                                contentDescription = stringResource(Res.string.reflow),
                                                tint = if (isReflow) Color.Green else Color.White
                                            )
                                        }
                                    }*/
                                }

                                item {
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
                }
            }

            // 朗读工具条 - 在主工具栏下方
            AnimatedVisibility(
                visible = isSpeaking,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (showToolbar) 48.dp else 0.dp)
            ) {
                Surface(
                    color = Color(0xCC333333),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ttsServiceBinder?.let { binder ->
                            val isConnected by binder.isConnected.collectAsState()

                            IconButton(
                                onClick = {
                                    if (isConnected && binder.isServiceInitialized()) {
                                        if (isSpeaking) {
                                            binder.pause()
                                        } else {
                                            scope.launch {
                                                binder.clearQueue()
                                                speakFromCurrentPage(currentPage, decoder!!, binder)
                                            }
                                        }
                                    }
                                },
                                enabled = true
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_tts),
                                    contentDescription = if (isSpeaking) "暂停" else "开始",
                                    tint = if (isSpeaking) Color.Green else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = { showSleepDialog = true }
                            ) {
                                val hasSleepTimer = binder.hasSleepTimer()
                                Text(
                                    text = "💤",
                                    color = if (hasSleepTimer) Color.Yellow else Color.White,
                                    fontSize = 16.sp
                                )
                            }

                            IconButton(
                                onClick = { showQueueDialog = true }
                            ) {
                                Text(
                                    text = "📋",
                                    color = Color.White,
                                    fontSize = 16.sp
                                )
                            }

                            IconButton(onClick = { binder.stop() }) {
                                Text(
                                    text = "X",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // 睡眠定时对话框
            if (showSleepDialog) {
                ttsServiceBinder?.let { binder ->
                    val sleepSetTimeText = stringResource(Res.string.tts_sleep_set_time)
                    val sleepCancelText = stringResource(Res.string.tts_sleep_set_cancel)

                    SleepTimerDialog(
                        onDismiss = { showSleepDialog = false },
                        onTimeSelected = { minutes ->
                            if (minutes > 0) {
                                binder.setSleepTimer(minutes)
                                val txt = sleepSetTimeText.format(minutes)
                                Toast.makeText(context, txt, Toast.LENGTH_SHORT).show()
                            } else {
                                binder.stop()
                                Toast.makeText(context, sleepCancelText, Toast.LENGTH_SHORT).show()
                            }
                        },
                        initialMinutes = binder.getSleepTimerMinutes().takeIf { it > 0 } ?: 20
                    )
                }
            }

            // 队列列表弹窗
            if (showQueueDialog) {
                ttsServiceBinder?.let { binder ->
                    val pdfDecoder = decoder as PdfDecoder

                    QueueDialog(
                        cacheBean = pdfDecoder.cacheBean,
                        currentSpeakingPage = binder.getCurrentSpeakingPage(),
                        onDismiss = { showQueueDialog = false },
                        onItemClick = { reflowBean ->
                            showQueueDialog = false

                            reflowBean.page?.let { pageStr ->
                                val targetPage = pageStr.toIntOrNull() ?: 0
                                jumpToPage = targetPage

                                scope.launch {
                                    binder.stop()

                                    kotlinx.coroutines.delay(50)

                                    speakFromCurrentPage(targetPage, decoder!!, binder)
                                }
                            }
                        }
                    )
                }
            }

            // 大纲弹窗（最上层）- 只有单文档文件才显示
            if (showOutlineDialog && FileTypeUtils.shouldShowOutline(paths)) {
                val outlineList = decoder?.outlineItems ?: emptyList()
                OutlineDialog(
                    currentPage,
                    outlineList,
                    onClick = { item ->
                        jumpToPage = item.page
                        showOutlineDialog = false
                        showToolbar = false
                    },
                    onDismiss = { showOutlineDialog = false },
                )
            }

            // 字体选择弹窗
            if (showFontDialog) {
                FontDialog(
                    onDismiss = { showFontDialog = false },
                    onFontSelected = { fontPath ->
                        println("选择了字体: ${File(fontPath).name}")
                        FontCSSGenerator.setFontFace(fontPath)
                        showFontDialog = false
                    }
                )
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

suspend fun speakFromCurrentPage(
    startPage: Int,
    imageDecoder: ImageDecoder,
    speechService: TtsServiceBinder
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

                val totalPages = imageDecoder.originalPageSizes.size
                var cacheBean = imageDecoder.cacheBean
                if (cacheBean == null) {
                    cacheBean = ReflowCacheLoader.loadReflowFromFile(
                        totalPages,
                        imageDecoder.file
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
                            imageDecoder.file,
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
}