package com.archko.reader.viewer

import android.content.pm.ActivityInfo
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.archko.reader.pdf.cache.ImageCache
import com.archko.reader.pdf.cache.ReflowCacheLoader
import com.archko.reader.pdf.component.GestureMode
import com.archko.reader.pdf.component.Horizontal
import com.archko.reader.pdf.component.JumpIntent
import com.archko.reader.pdf.component.JumpMode
import com.archko.reader.pdf.component.MobileDocumentView
import com.archko.reader.pdf.component.PathConfig
import com.archko.reader.pdf.component.SearchState
import com.archko.reader.pdf.component.Vertical
import com.archko.reader.pdf.decoder.DjvuDecoder
import com.archko.reader.pdf.decoder.ImagesDecoder
import com.archko.reader.pdf.decoder.PdfDecoder
import com.archko.reader.pdf.decoder.TiffDecoder
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.APage
import com.archko.reader.pdf.entity.Bookmark
import com.archko.reader.pdf.entity.DocQuad
import com.archko.reader.pdf.entity.ReflowBean
import com.archko.reader.pdf.state.AnnotationManager
import com.archko.reader.pdf.tts.TtsProgressListener
import com.archko.reader.pdf.util.FileTypeUtils
import com.archko.reader.pdf.util.FontCSSGenerator
import com.archko.reader.pdf.util.ReadingTimeTracker
import com.archko.reader.pdf.viewmodel.AIViewModel
import com.archko.reader.pdf.viewmodel.BookmarkViewModel
import com.archko.reader.pdf.viewmodel.ReadingStatsViewModel
import com.archko.reader.viewer.component.DrawingToolbar
import com.archko.reader.viewer.component.ErrorContent
import com.archko.reader.viewer.component.SearchBar
import com.archko.reader.viewer.dialog.AIPageDialog
import com.archko.reader.viewer.dialog.AddBookmarkDialog
import com.archko.reader.viewer.dialog.FontDialog
import com.archko.reader.viewer.dialog.OutlineDialog
import com.archko.reader.viewer.dialog.PasswordDialog
import com.archko.reader.viewer.dialog.QueueDialog
import com.archko.reader.viewer.dialog.SleepTimerDialog
import com.archko.reader.viewer.dialog.ThumbnailDialog
import com.archko.reader.viewer.tts.TtsServiceBinder
import com.archko.reader.viewer.tts.TtsTempProgressHelper
import com.archko.reader.viewer.viewmodel.FontViewModel
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

/**
 * 工具栏内容组件
 */
@Composable
private fun ToolbarContent(
    onCloseDocument: (() -> Unit)?,
    currentPath: String,
    ttsServiceBinder: TtsServiceBinder?,
    isSpeaking: Boolean,
    currentPage: Int,
    decoder: ImageDecoder,
    onGestureModeChange: (gestureMode: GestureMode) -> Unit,
    gestureMode: GestureMode,
    onCropChange: () -> Unit,
    isCrop: Boolean,
    onOutlineDialogShow: () -> Unit,
    onAIDialogShow: () -> Unit,
    onBookmarkDialogShow: () -> Unit,
    onSearchBarShow: () -> Unit,
    onFontDialogShow: () -> Unit,
    onThumbnailDialogShow: () -> Unit,
    scope: CoroutineScope,
    onStartSpeaking: (Int, ImageDecoder, TtsServiceBinder) -> Unit,
    isReflow: Boolean,
    paths: List<String>
) {
    Surface(
        color = Color(0xCC222222),
        modifier = Modifier.fillMaxWidth()
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
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
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
                                                onStartSpeaking(currentPage, decoder, binder)
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
                    }

                    item {
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
                                contentDescription = "批注画线",
                                tint = if (gestureMode == GestureMode.DRAW) Color.Green else Color.White
                            )
                        }
                    }

                    item {
                        IconButton(onClick = { onCropChange() }) {
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
                            IconButton(onClick = { onOutlineDialogShow() }) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_toc),
                                    contentDescription = stringResource(Res.string.outline),
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    // AI按钮
                    if (FileTypeUtils.isDocumentFile(currentPath)) {
                        item {
                            IconButton(onClick = { onAIDialogShow() }) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_ai),
                                    contentDescription = "AI",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    // 书签按钮
                    if (FileTypeUtils.isDocumentFile(currentPath)) {
                        item {
                            IconButton(onClick = { onBookmarkDialogShow() }) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_bookmark),
                                    contentDescription = stringResource(Res.string.bookmark),
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    // 搜索按钮
                    if (FileTypeUtils.isDocumentFile(currentPath)) {
                        item {
                            IconButton(onClick = { onSearchBarShow() }) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_search),
                                    contentDescription = stringResource(Res.string.search),
                                    tint = Color.White
                                )
                            }
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

                    if (FileTypeUtils.isReflowable(currentPath)) {
                        item {
                            IconButton(onClick = { onFontDialogShow() }) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_font),
                                    contentDescription = stringResource(Res.string.font),
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }

                item {
                    IconButton(onClick = { onThumbnailDialogShow() }) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_thumb),
                            contentDescription = "缩略图",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * TTS控制栏内容组件
 */
@Composable
private fun TtsControlBarContent(
    ttsServiceBinder: TtsServiceBinder?,
    onPauseResume: () -> Unit,
    onSleepTimer: () -> Unit,
    onQueue: () -> Unit,
    onStop: () -> Unit,
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
        ) {
            ttsServiceBinder?.let { binder ->
                IconButton(
                    onClick = { onPauseResume() },
                    enabled = true
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_tts),
                        contentDescription = "TTS",
                        tint = Color.Green,
                    )
                }

                IconButton(onClick = { onSleepTimer() }) {
                    Text(
                        text = "💤",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }

                IconButton(onClick = { onQueue() }) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_toc),
                        contentDescription = stringResource(Res.string.outline),
                        tint = Color.White
                    )
                }

                IconButton(onClick = { onStop() }) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_close),
                        contentDescription = "close",
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
    fontViewModel: FontViewModel,
    bookmarkViewModel: BookmarkViewModel,
    readingStatsViewModel: ReadingStatsViewModel,
    aiViewModel: AIViewModel,
) {
    val context = LocalContext.current
    val isDarkTheme = isSystemInDarkTheme()

    // 全屏设置函数
    fun applyFullScreen() {
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

    LaunchedEffect(Unit) {
        applyFullScreen()

        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val cacheMemoryLimit = maxMemory / 2
        ImageCache.setMaxMemory(cacheMemoryLimit)

        println("ImageCache: 设置内存限制为 ${cacheMemoryLimit / 1024 / 1024}MB (总内存: ${maxMemory / 1024 / 1024}MB)")

        // 加载书签
        if (paths.size == 1 && FileTypeUtils.isDocumentFile(paths[0])) {
            bookmarkViewModel.loadBookmarks(paths[0])
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
    var isCrop by remember { mutableStateOf(crop == true) }
    var isNeedPass by remember { mutableStateOf(false) }

    // 字体选择相关状态
    var showFontDialog by remember { mutableStateOf(false) }

    // AI对话相关状态
    var showAIDialog by remember { mutableStateOf(false) }

    // 书签相关状态
    var showAddBookmarkDialog by remember { mutableStateOf(false) }
    var editingBookmark by remember { mutableStateOf<Bookmark?>(null) }

    // 阅读时长追踪
    val readingTimeTracker = remember { ReadingTimeTracker() }

    // 用于保存当前页码的引用（在DisposableEffect中使用）
    var currentPageRef = remember { mutableIntStateOf(progressPage ?: 0) }

    // 多文件支持
    val currentPath = paths.getOrNull(0) ?: paths.first()

    // TTS服务绑定器 - 只有文档文件才初始化
    var ttsServiceBinder by remember { mutableStateOf<TtsServiceBinder?>(null) }
    var pathConfig by remember { mutableStateOf(PathConfig()) }

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
                                ttsServiceBinder = TtsServiceBinder(context, currentPath)
                                ttsServiceBinder?.bindService()

                                val djvuDecoder = DjvuDecoder(File(currentPath))
                                djvuDecoder
                            } else if (FileTypeUtils.isDocumentFile(currentPath)) {
                                ttsServiceBinder = TtsServiceBinder(context, currentPath)
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
                val activity = context as? ComponentActivity
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
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
            ErrorContent(
                loadingError = loadingError,
                currentPath = currentPath,
                onCloseDocument = onCloseDocument
            )
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
            var showThumbnailDialog by remember { mutableStateOf(false) }

            var isVertical by remember { mutableStateOf(scrollOri.toInt() == Vertical) }
            var isReflow by remember { mutableStateOf(reflow == 1L) }
            var gestureMode by remember { mutableStateOf(GestureMode.VIEW) }

            var showSleepDialog by remember { mutableStateOf(false) }
            var showQueueDialog by remember { mutableStateOf(false) }

            // 搜索相关状态
            var showSearchBar by remember { mutableStateOf(false) }
            var searchState by remember { mutableStateOf(SearchState()) }

            // 构建搜索高亮映射（按页面分组）
            // 当前结果的quads放在最前面，这样Page.kt绘制时第一个quad会被特殊高亮
            val searchHighlightQuads = remember(searchState.results, searchState.currentIndex) {
                val highlightMap =
                    mutableMapOf<Int, MutableList<DocQuad>>()

                // 先添加当前结果的quads
                val currentResult = searchState.currentResult
                if (currentResult != null) {
                    highlightMap.getOrPut(currentResult.pageIndex) { mutableListOf() }
                        .addAll(currentResult.quads)
                }

                // 再添加其他结果的quads
                searchState.results.forEachIndexed { index, r ->
                    if (index != searchState.currentIndex) {
                        highlightMap.getOrPut(r.pageIndex) { mutableListOf() }.addAll(r.quads)
                    }
                }

                highlightMap
            }

            val currentSearchPageIndex = searchState.currentResult?.pageIndex

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
            // 当前页与总页数
            var currentPage by remember { mutableIntStateOf(progressPage ?: 0) }
            // 添加标志位以跟踪是否为外部更改
            var isExternalChange by remember { mutableStateOf(false) }
            val pageCount: Int = list.size
            // 跳转页面状态
            var jumpIntent by remember {
                mutableStateOf(
                    when {
                        progressPage != null && (initialScrollX != 0L || initialScrollY != 0L) ->
                            JumpIntent(progressPage, JumpMode.PageRestore)

                        progressPage != null ->
                            JumpIntent(progressPage, JumpMode.PageNavigation)

                        else -> JumpIntent(0, JumpMode.PageRestore)
                    }
                )
            }

            var isSpeaking by remember { mutableStateOf(false) }
            var speakingPageIndex by remember { mutableStateOf<Int?>(null) }
            var columnCount by remember { mutableIntStateOf(1) }
            var showColumn by remember {
                mutableStateOf(
                    if (paths.size > 1 || FileTypeUtils.isImageFile(currentPath)) {
                        false
                    } else {
                        true
                    }
                )
            }

            val annotationManager = remember(paths) {
                var absolutePath = ""
                if (paths.size == 1) {
                    val first = paths[0]
                    if (FileTypeUtils.isDocumentFile(first)) {
                        absolutePath = first
                    }
                }
                AnnotationManager(absolutePath)
            }

            // 搜索辅助函数 - 在jumpIntent定义之后
            fun goToSearchResult(index: Int) {
                if (index < 0 || index >= searchState.results.size) return

                val result = searchState.results[index]
                searchState = searchState.copy(currentIndex = index)

                // 计算搜索结果在页面中的Y坐标（取第一个quad的顶部）
                // 减去80dp避免被工具栏覆盖
                val offsetY = result.quads.firstOrNull()?.ul?.y?.let { y ->
                    (y - 80f).coerceAtLeast(0f)
                }

                // 跳转到结果所在页面，带上精确偏移
                jumpIntent = JumpIntent(
                    page = result.pageIndex,
                    mode = JumpMode.PageNavigation,
                    offsetY = offsetY
                )
            }

            fun goToNextResult() {
                if (searchState.results.isEmpty()) return
                val nextIndex = (searchState.currentIndex + 1) % searchState.results.size
                goToSearchResult(nextIndex)
            }

            fun goToPreviousResult() {
                if (searchState.results.isEmpty()) return
                val prevIndex = if (searchState.currentIndex <= 0) {
                    searchState.results.size - 1
                } else {
                    searchState.currentIndex - 1
                }
                goToSearchResult(prevIndex)
            }

            // 搜索功能函数
            fun performSearch(state: SearchState) {
                val query: String = state.query
                if (query.isBlank()) {
                    return
                }

                searchState = state.copy(isSearching = true)

                scope.launch(Dispatchers.IO) {
                    try {
                        val results = decoder!!.search(query, caseSensitive = false)
                        withContext(Dispatchers.Main) {
                            searchState = SearchState(
                                query = query,
                                results = results,
                                currentIndex = if (results.isNotEmpty()) 0 else -1,
                                isSearching = false,
                                totalCount = results.size
                            )

                            // 如果有结果，跳转到第一个结果并高亮
                            if (results.isNotEmpty()) {
                                goToSearchResult(0)
                            }
                        }
                    } catch (e: Exception) {
                        println("搜索失败: ${e.message}")
                        withContext(Dispatchers.Main) {
                            searchState = searchState.copy(isSearching = false)
                        }
                    }
                }
            }

            // 阅读时长追踪 - 启动会话
            LaunchedEffect(currentPath) {
                if (FileTypeUtils.isDocumentFile(currentPath)) {
                    readingTimeTracker.startSession()
                    // 初始化统计数据
                    decoder?.let { dec ->
                        readingStatsViewModel.startSession(currentPath, dec.originalPageSizes.size)
                    }
                }
            }

            // 监听朗读状态
            LaunchedEffect(ttsServiceBinder) {
                ttsServiceBinder?.isSpeakingFlow?.collect { speaking ->
                    isSpeaking = speaking
                    if (!speaking) {
                        speakingPageIndex = null
                    }
                }
            }

            // 设置TTS进度监听器 - 用于页面跳转和进度保存
            LaunchedEffect(ttsServiceBinder) {
                ttsServiceBinder?.setProgressListener(object : TtsProgressListener {
                    override fun onStart(bean: ReflowBean) {
                        // Segment开始朗读
                    }

                    override fun onDone(bean: ReflowBean) {
                        // Segment完成，检查是否页面切换
                        val newPageStr = bean.page?.split("-")?.firstOrNull()
                        val newPage = newPageStr?.toIntOrNull()
                        if (newPage != null && newPage != currentPage) {
                            scope.launch {
                                speakingPageIndex = newPage
                                if (newPage != jumpIntent.page) {
                                    jumpIntent = JumpIntent(newPage, JumpMode.PageNavigation)
                                }
                            }
                        }
                    }

                    override fun onFinish() {
                        // 朗读完成，保存最后页面进度
                        scope.launch {
                            speakingPageIndex = null
                            val lastPageStr =
                                ttsServiceBinder?.getCurrentReflowBean()?.page?.split("-")
                                    ?.firstOrNull()
                            val lastPage = lastPageStr?.toIntOrNull()
                            lastPage?.let { page ->
                                onSaveDocument?.invoke(
                                    page,
                                    pageCount,
                                    initialZoom,
                                    initialScrollX,
                                    initialScrollY,
                                    if (isVertical) Vertical.toLong() else Horizontal.toLong(),
                                    if (isReflow) 1L else 0L,
                                    if (isCrop) 1L else 0L
                                )
                            }
                            TtsTempProgressHelper.clearTempProgress(context, currentPath)
                        }
                    }
                })
            }

            // 监听生命周期，当从后台返回前台时同步到正在朗读的页面
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, ttsServiceBinder) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        ttsServiceBinder?.let { binder ->
                            if (binder.isSpeaking()) {
                                val speakingPage = binder.getCurrentSpeakingPage()
                                speakingPage?.let { pageStr ->
                                    val targetPageStr = pageStr.split("-").firstOrNull()
                                    val targetPage = targetPageStr?.toIntOrNull()
                                    println("OnResume: 正在朗读第${targetPage}页，当前显示第${currentPage}页")
                                    if (targetPage != null && targetPage != currentPage) {
                                        jumpIntent = JumpIntent(targetPage, JumpMode.PageNavigation)

                                    }
                                }
                            }
                        }
                        if (FileTypeUtils.isDocumentFile(currentPath)) {
                            readingTimeTracker.resumeSession()
                        }
                    }
                    if (event == Lifecycle.Event.ON_PAUSE) {
                        if (FileTypeUtils.isDocumentFile(currentPath)) {
                            readingTimeTracker.pauseSession()
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            // 根据reflow状态选择显示模式
            if (isReflow && FileTypeUtils.isDocumentFile(currentPath)) {
                // Reflow视图
                ReflowView(
                    decoder = decoder!!,
                    pageCount = pageCount,
                    onSaveDocument = if (list.isNotEmpty() && FileTypeUtils.shouldSaveProgress(paths)) onSaveDocument else null,
                    onCloseDocument = {
                        println("onCloseDocument.isReflow:$isReflow")
                        if (!isReflow) {
                            onCloseDocument?.invoke()
                        }
                    }, // 只在非重排模式下传递关闭回调
                    onDoubleTapToolbar = { showToolbar = !showToolbar },
                    onPageChanged = { page ->
                        currentPage = page
                    },
                    onTapNonPageArea = { clickedPageIndex ->
                        // 点击非翻页区域时隐藏工具栏，但朗读时保持朗读工具条显示
                        if (showToolbar) {
                            showToolbar = false
                        }
                        scope.launch {
                            val pageText = getString(Res.string.current_page)
                                .format(clickedPageIndex + 1, pageCount)
                            Toast.makeText(context, pageText, Toast.LENGTH_SHORT).show()
                        }
                    },
                    jumpToPage = jumpIntent.page,
                    initialScrollX = initialScrollX,
                    initialScrollY = initialScrollY,
                    initialZoom = initialZoom,
                    initialOrientation = orientation,
                    reflow = 1L,
                )
            } else {
                // 文档视图（最底层）
                MobileDocumentView(
                    list = list,
                    decoder = decoder!!,
                    jumpToPage = jumpIntent.page,
                    jumpMode = jumpIntent.mode,
                    jumpOffsetY = jumpIntent.offsetY,
                    initialOrientation = orientation,
                    columnCount = columnCount,
                    onSaveDocument = if (list.isNotEmpty() && FileTypeUtils.shouldSaveProgress(paths)) onSaveDocument else null,
                    onCloseDocument = {
                        println("onCloseDocument.isReflow:$isReflow")
                        // 保存阅读统计
                        if (FileTypeUtils.isDocumentFile(currentPath)) {
                            val sessionDuration = readingTimeTracker.pauseSession()
                            val annotationCount =
                                annotationManager.annotations.values.sumOf { it.size }
                            val bookmarkCount = bookmarkViewModel.currentPathBookmarks.value.size
                            readingStatsViewModel.endSession(
                                path = currentPath,
                                sessionDuration = sessionDuration,
                                currentPage = currentPageRef.intValue,
                                annotationCount = annotationCount,
                                bookmarkCount = bookmarkCount
                            )
                        }
                        if (!isReflow) {
                            onCloseDocument?.invoke()
                        }
                    }, // 只在非重排模式下传递关闭回调
                    onDoubleTapToolbar = { showToolbar = !showToolbar },
                    onPageChanged = { page ->
                        currentPage = page
                        currentPageRef.intValue = page
                    },
                    onTapNonPageArea = { clickedPageIndex ->
                        // 点击非翻页区域时隐藏工具栏，但朗读时保持朗读工具条显示
                        if (showToolbar) {
                            showToolbar = false
                        } else {
                            scope.launch {
                                val pageText = getString(Res.string.current_page)
                                    .format(clickedPageIndex + 1, pageCount)
                                Toast.makeText(context, pageText, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    initialScrollX = initialScrollX,
                    initialScrollY = initialScrollY,
                    initialZoom = initialZoom,
                    crop = isCrop,
                    speakingPageIndex = speakingPageIndex,
                    gestureMode = gestureMode,
                    pathConfig = pathConfig,
                    annotationManager = annotationManager,
                    currentPath = currentPath,
                    searchHighlightQuads = searchHighlightQuads,
                    currentSearchPageIndex = currentSearchPageIndex,
                )
            }

            // 阅读器工具栏
            AnimatedVisibility(
                visible = showToolbar,
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                ToolbarContent(
                    onCloseDocument = onCloseDocument,
                    currentPath = currentPath,
                    ttsServiceBinder = ttsServiceBinder,
                    isSpeaking = isSpeaking,
                    currentPage = currentPage,
                    decoder = decoder!!,
                    onGestureModeChange = { mode -> gestureMode = mode },
                    gestureMode = gestureMode,
                    onCropChange = { isCrop = !isCrop },
                    isCrop = isCrop,
                    onOutlineDialogShow = { showOutlineDialog = true },
                    onAIDialogShow = { showAIDialog = true },
                    onBookmarkDialogShow = { showAddBookmarkDialog = true },
                    onSearchBarShow = { showSearchBar = !showSearchBar },
                    onFontDialogShow = { showFontDialog = true },
                    onThumbnailDialogShow = { showThumbnailDialog = true },
                    scope = scope,
                    onStartSpeaking = { page, dec, binder ->
                        scope.launch {
                            speakingPageIndex = page
                            speakFromCurrentPage(page, dec, binder)
                        }
                    },
                    isReflow = isReflow,
                    paths = paths
                )
            }

            AnimatedVisibility(
                visible = (gestureMode == GestureMode.DRAW && showToolbar) || isSpeaking || showSearchBar,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (showToolbar) 48.dp else 0.dp)
            ) {
                Column() {
                    AnimatedVisibility(
                        visible = gestureMode == GestureMode.DRAW && showToolbar,
                        modifier = Modifier
                    ) {
                        DrawingToolbar(
                            annotationManager = annotationManager,
                            pathConfig = pathConfig,
                            onClose = { config ->
                                pathConfig = config
                                println("onClose.pathConfig:$pathConfig")
                            }
                        )
                    }

                    // TTS控制栏
                    AnimatedVisibility(
                        visible = isSpeaking,
                        modifier = Modifier
                    ) {
                        TtsControlBarContent(
                            ttsServiceBinder = ttsServiceBinder,
                            onPauseResume = {
                                ttsServiceBinder?.let { binder ->
                                    if (binder.isServiceInitialized()) {
                                        if (isSpeaking) {
                                            binder.pause()
                                        } else {
                                            scope.launch {
                                                speakFromCurrentPage(currentPage, decoder!!, binder)
                                            }
                                        }
                                    }
                                }
                            },
                            onSleepTimer = { showSleepDialog = true },
                            onQueue = { showQueueDialog = true },
                            onStop = { ttsServiceBinder?.stop() }
                        )
                    }

                    // 搜索栏
                    AnimatedVisibility(
                        visible = showSearchBar,
                        modifier = Modifier
                    ) {
                        SearchBar(
                            searchState = searchState,
                            onQueryChange = { query ->
                                searchState = searchState.copy(query = query)
                            },
                            onSearch = {
                                performSearch(searchState)
                            },
                            onPrevious = {
                                goToPreviousResult()
                            },
                            onNext = {
                                goToNextResult()
                            },
                            onClose = {
                                showSearchBar = false
                                searchState = SearchState()
                            }
                        )
                    }
                }
            }

            // 底部SeekBar - 考虑导航栏（上层）
            AnimatedVisibility(
                visible = showToolbar,
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Surface(
                        color = Color(0xCC222222),
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        // Activity屏幕方向状态
                        var isActivityPortrait by remember { mutableStateOf(true) }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp, end = 8.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 方向按钮（仅在单列且文档文件时显示）
                                if (columnCount <= 1) {
                                    IconButton(
                                        onClick = { isVertical = !isVertical },
                                    ) {
                                        Icon(
                                            painter = painterResource(if (isVertical) Res.drawable.ic_vertical else Res.drawable.ic_horizontal),
                                            contentDescription = if (isVertical) stringResource(Res.string.vertical) else stringResource(
                                                Res.string.horizontal
                                            ),
                                            tint = Color.White
                                        )
                                    }

                                    VerticalDivider(modifier = Modifier.height(20.dp))
                                }

                                IconButton(
                                    onClick = {
                                        isActivityPortrait = !isActivityPortrait
                                        val activity = context as? ComponentActivity
                                        activity?.requestedOrientation = if (isActivityPortrait) {
                                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                        } else {
                                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                        }

                                        scope.launch {
                                            // 等待方向改变完成
                                            delay(50)
                                            applyFullScreen()
                                        }
                                    },
                                ) {
                                    Icon(
                                        painter = painterResource(if (isActivityPortrait) Res.drawable.ic_portrait else Res.drawable.ic_landscape),
                                        contentDescription = if (isActivityPortrait) "竖屏" else "横屏",
                                        tint = Color.White
                                    )
                                }

                                if (showColumn && isVertical) {
                                    VerticalDivider(modifier = Modifier.height(20.dp))
                                    if (columnCount == 1) {
                                        IconButton(
                                            onClick = { columnCount = 2 },
                                        ) {
                                            Icon(
                                                painter = painterResource(Res.drawable.ic_column_two),
                                                contentDescription = "",
                                                tint = Color.White
                                            )
                                        }
                                    } else {
                                        IconButton(
                                            onClick = { columnCount = 1 },
                                        ) {
                                            Icon(
                                                painter = painterResource(Res.drawable.ic_column_one),
                                                contentDescription = "",
                                                tint = Color.White
                                            )
                                        }
                                    }
                                }
                            }

                            var sliderValue by remember { mutableFloatStateOf((currentPage + 1).toFloat()) }
                            // 当currentPage变化时更新sliderValue
                            LaunchedEffect(currentPage) {
                                isExternalChange = true
                                sliderValue = (currentPage + 1).toFloat()
                                isExternalChange = false
                            }

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
                                            jumpIntent =
                                                JumpIntent(targetPage, JumpMode.PageNavigation)
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
                                Toast.makeText(context, sleepCancelText, Toast.LENGTH_SHORT)
                                    .show()
                            }
                        },
                        initialMinutes = binder.getSleepTimerMinutes().takeIf { it > 0 } ?: 20
                    )
                }
            }

            // 队列列表弹窗
            if (showQueueDialog) {
                ttsServiceBinder?.let { binder ->
                    QueueDialog(
                        cacheBean = decoder!!.cacheBean,
                        currentSpeakingPage = binder.getCurrentSpeakingPage()?.split("-")
                            ?.firstOrNull(),
                        onDismiss = { showQueueDialog = false },
                        onItemClick = { reflowBean ->
                            showQueueDialog = false

                            reflowBean.page?.let { pageStr ->
                                val targetPageStr = pageStr.split("-").firstOrNull()
                                val targetPage = targetPageStr?.toIntOrNull() ?: 0

                                scope.launch {
                                    jumpIntent = JumpIntent(targetPage, JumpMode.PageNavigation)
                                    currentPage = targetPage

                                    // 停止当前朗读
                                    binder.stop()
                                    delay(50)

                                    // 开始新的朗读
                                    speakFromCurrentPage(targetPage, decoder!!, binder)
                                    if (binder.isSpeaking()) {
                                        speakingPageIndex = targetPage
                                    } else {
                                        speakingPageIndex = null
                                    }
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
                    currentPage = currentPage,
                    currentPath = paths.firstOrNull() ?: "",
                    outlineList = outlineList,
                    annotationManager = annotationManager,
                    bookmarkViewModel = bookmarkViewModel,
                    aiViewModel = aiViewModel,
                    onOutlineClick = { item ->
                        jumpIntent = JumpIntent(item.page, JumpMode.PageNavigation)
                        showOutlineDialog = false
                        showToolbar = false
                    },
                    onAnnotationClick = { pageIndex ->
                        jumpIntent = JumpIntent(pageIndex, JumpMode.PageNavigation)
                        showOutlineDialog = false
                        showToolbar = false
                    },
                    onBookmarkClick = { bookmark ->
                        jumpIntent = JumpIntent(bookmark.pageIndex, JumpMode.PageNavigation)
                        showOutlineDialog = false
                        showToolbar = false
                    },
                    onEditBookmark = { bookmark ->
                        editingBookmark = bookmark
                        showAddBookmarkDialog = true
                    },
                    onAIConversationClick = { pageIndex ->
                        jumpIntent = JumpIntent(pageIndex, JumpMode.PageNavigation)
                        showOutlineDialog = false
                        showToolbar = false
                    },
                    onDismiss = { showOutlineDialog = false },
                )
            }

            if (showThumbnailDialog) {
                ThumbnailDialog(
                    currentPage,
                    list,
                    decoder!!,
                    onPageClick = { page ->
                        jumpIntent = JumpIntent(page, JumpMode.PageNavigation)
                        showThumbnailDialog = false
                        showToolbar = false
                    },
                    onDismiss = { showThumbnailDialog = false },
                )
            }

            // 字体选择弹窗
            if (showFontDialog) {
                FontDialog(
                    viewModel = fontViewModel,
                    onDismiss = { showFontDialog = false },
                    onFontSelected = { fontPath ->
                        println("选择了字体: ${File(fontPath).name}")
                        FontCSSGenerator.setFontFace(fontPath)
                        showFontDialog = false
                    }
                )
            }

            // AI对话框
            if (showAIDialog) {
                AIPageDialog(
                    currentPath = currentPath,
                    pageIndex = currentPage,
                    decoder = decoder!!,
                    aiViewModel = aiViewModel,
                    onDismiss = { showAIDialog = false },
                    onShowToast = { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // 添加/编辑书签对话框
            if (showAddBookmarkDialog) {
                AddBookmarkDialog(
                    pageIndex = currentPage,
                    existingBookmark = editingBookmark,
                    onSave = { title, note, color ->
                        if (editingBookmark != null) {
                            // 编辑现有书签
                            editingBookmark!!.apply {
                                this.title = title
                                this.note = note
                                this.color = color
                            }
                            bookmarkViewModel.updateBookmark(editingBookmark!!)
                        } else {
                            // 添加新书签
                            bookmarkViewModel.addBookmark(
                                path = currentPath,
                                pageIndex = currentPage,
                                title = title,
                                note = note,
                                color = color
                            )
                        }
                        editingBookmark = null
                    },
                    onDismiss = {
                        showAddBookmarkDialog = false
                        editingBookmark = null
                    }
                )
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
