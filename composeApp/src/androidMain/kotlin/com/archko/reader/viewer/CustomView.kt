package com.archko.reader.viewer

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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

    // 初始化TTS服务（仅文档文件）
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
                        // 单文件模式：检查文件类型
                        if (FileTypeUtils.isDocumentFile(currentPath)) {
                            ttsServiceBinder = TtsServiceBinder(context)
                            ttsServiceBinder?.bindService()

                            // 文档文件：创建PdfDecoder
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
                        } else {
                            isCrop = false
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
        // 工具栏显示状态
        var showToolbar by remember { mutableStateOf(false) }
        // 大纲弹窗状态
        var showOutlineDialog by remember { mutableStateOf(false) }

        // 横竖切换、重排等按钮内部状态
        var isVertical by remember { mutableStateOf(scrollOri.toInt() == Vertical) }
        var isReflow by remember { mutableStateOf(reflow == 1L) }
        // 文本选择模式状态
        var isTextSelectionMode by remember { mutableStateOf(false) }

        var showTtsToolbar by remember { mutableStateOf(false) }
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
        // 大纲列表
        val outlineList = decoder?.outlineItems ?: emptyList()

        // 获取字符串资源
        val currentPageString = stringResource(Res.string.current_page)

        // 监听TTS状态，自动显示/隐藏朗读工具条
        ttsServiceBinder?.let { binder ->
            val isSpeaking by binder.isSpeakingFlow.collectAsState()
            LaunchedEffect(isSpeaking) {
                if (isSpeaking) {
                    showTtsToolbar = true
                } else {
                    // 朗读停止时，延迟3秒后自动隐藏工具条（如果用户没有手动操作）
                    kotlinx.coroutines.delay(1000)
                    if (!isSpeaking) {
                        showTtsToolbar = false
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
                        // 固定的返回按钮
                        IconButton(onClick = {
                            onCloseDocument?.invoke()
                        }) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_back),
                                contentDescription = stringResource(Res.string.back),
                                tint = Color.White
                            )
                        }

                        // 可滚动的按钮区域
                        LazyRow(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            verticalAlignment = Alignment.CenterVertically,
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            if (FileTypeUtils.isDocumentFile(currentPath)) {
                                // TTS控制按钮
                                ttsServiceBinder?.let { binder ->
                                    item {
                                        val isSpeaking by binder.isSpeakingFlow.collectAsState()
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
                                                        showTtsToolbar = true
                                                    }
                                                }
                                            },
                                            enabled = isConnected && binder.isServiceInitialized()
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
                            }

                            // 方向按钮 - 文档和图片都显示
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

                                // 只有单文档文件才显示大纲按钮
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

                                if (!IntentFile.isReflowable(currentPath)) {
                                    item {
                                        IconButton(onClick = { isReflow = !isReflow }) {
                                            Icon(
                                                painter = painterResource(Res.drawable.ic_reflow),
                                                contentDescription = stringResource(Res.string.reflow),
                                                tint = if (isReflow) Color.Green else Color.White
                                            )
                                        }
                                    }
                                } else {
                                    item {
                                        IconButton(onClick = { showFontDialog = true }) {
                                            Icon(
                                                painter = painterResource(Res.drawable.ic_font),
                                                contentDescription = stringResource(Res.string.font),
                                                tint = Color.White
                                            )
                                        }
                                    }
                                }

                                item {
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
                }
            }

            // 朗读工具条 - 在主工具栏下方
            AnimatedVisibility(
                visible = showTtsToolbar && FileTypeUtils.isDocumentFile(currentPath),
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
                            .height(40.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ttsServiceBinder?.let { binder ->
                            val isSpeaking by binder.isSpeakingFlow.collectAsState()
                            val isConnected by binder.isConnected.collectAsState()

                            IconButton(
                                onClick = {
                                    if (isConnected && binder.isServiceInitialized()) {
                                        if (isSpeaking) {
                                            binder.pause()
                                        } else {
                                            scope.launch {
                                                speakFromCurrentPage(currentPage, decoder!!, binder)
                                            }
                                        }
                                    }
                                },
                                enabled = isConnected && binder.isServiceInitialized()
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_tts),
                                    contentDescription = if (isSpeaking) "暂停" else "开始",
                                    tint = if (isSpeaking) Color.Red else Color.Green,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = { showSleepDialog = true }
                            ) {
                                Text(
                                    text = "💤",
                                    color = Color.White,
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

                            IconButton(
                                onClick = {
                                    binder.stop()
                                    showTtsToolbar = false
                                }
                            ) {
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

            // 队列列表弹窗
            if (showQueueDialog) {
                Dialog(onDismissRequest = { showQueueDialog = false }) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 600.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { showQueueDialog = false }) {
                                    Icon(
                                        painter = painterResource(Res.drawable.ic_back),
                                        contentDescription = "返回",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = stringResource(Res.string.tts_queue_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            ttsServiceBinder?.let { binder ->
                                val decoder = decoder as PdfDecoder
                                if (decoder.cacheBean != null) {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(
                                            horizontal = 16.dp,
                                            vertical = 8.dp
                                        )
                                    ) {
                                        itemsIndexed(
                                            decoder.cacheBean!!.reflowTexts,
                                            key = { index, item -> index }) { index, item ->
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 2.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                                        alpha = 0.5f
                                                    )
                                                )
                                            ) {
                                                Text(
                                                    text = stringResource(Res.string.tts_page_item)
                                                        .format(
                                                            item.page,
                                                            item.data?.take(10)
                                                        ),
                                                    modifier = Modifier.padding(16.dp),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stringResource(Res.string.tts_empty_queue),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
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
                    // 根据当前页码找到最接近的大纲项位置
                    val initialOutlineIndex = outlineList.indexOfFirst { it.page >= currentPage }
                        .takeIf { it != -1 } ?: outlineList.indexOfLast { it.page <= currentPage }
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
                                    Text(stringResource(Res.string.no_outline), color = Color.Gray)
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
                                                    showToolbar = false
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

/**
 * 字体选择对话框
 */
@Composable
fun FontDialog(
    onDismiss: () -> Unit,
    onFontSelected: (String) -> Unit
) {
    var fontFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // 扫描字体文件
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val fontsDir = File("/sdcard/fonts")
            val files = if (fontsDir.exists() && fontsDir.isDirectory) {
                fontsDir.listFiles { file ->
                    file.extension.equals("ttf", ignoreCase = true) ||
                            file.extension.equals("otf", ignoreCase = true)
                }?.toList() ?: emptyList()
            } else {
                emptyList()
            }
            fontFiles = files
            isLoading = false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.wrapContentSize(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.wrapContentSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_back),
                            contentDescription = stringResource(Res.string.back),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(Res.string.font_select),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (isLoading) {
                    Box(
                        modifier = Modifier.wrapContentSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(Res.string.font_scaning),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else if (fontFiles.isEmpty()) {
                    Box(
                        modifier = Modifier.wrapContentSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(Res.string.font_no),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(Res.string.font_storage),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .wrapContentSize()
                            .heightIn(max = 800.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        items(fontFiles.size) { index ->
                            val fontFile = fontFiles[index]
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        onFontSelected(fontFile.absolutePath)
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                        alpha = 0.5f
                                    )
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(Res.drawable.ic_font),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = fontFile.nameWithoutExtension,
                                            style = MaterialTheme.typography.headlineMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontFamily = FontFamily(
                                                Font(
                                                    fontFile,
                                                    FontWeight.Normal,
                                                    FontStyle.Normal
                                                )
                                            )
                                        )
                                        Text(
                                            text = "${fontFile.extension.uppercase()} • ${
                                                String.format(
                                                    "%.1f KB",
                                                    fontFile.length() / 1024.0
                                                )
                                            }",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontFamily = FontFamily(
                                                Font(
                                                    fontFile,
                                                    FontWeight.Normal,
                                                    FontStyle.Normal
                                                )
                                            )
                                        )
                                    }
                                }
                            }
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