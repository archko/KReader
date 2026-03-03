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
import com.archko.reader.pdf.cache.ReflowCacheLoader
import com.archko.reader.pdf.component.DesktopDocumentView
import com.archko.reader.pdf.component.GestureMode
import com.archko.reader.pdf.component.Horizontal
import com.archko.reader.pdf.component.JumpIntent
import com.archko.reader.pdf.component.JumpMode
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
import com.archko.reader.pdf.tts.SpeechService
import com.archko.reader.pdf.tts.TtsProgressListener
import com.archko.reader.pdf.util.FileTypeUtils
import com.archko.reader.pdf.util.ReadingTimeTracker
import com.archko.reader.pdf.viewmodel.AIViewModel
import com.archko.reader.pdf.viewmodel.BookmarkViewModel
import com.archko.reader.pdf.viewmodel.ReadingStatsViewModel
import com.archko.reader.viewer.component.DrawingToolbar
import com.archko.reader.viewer.component.SearchBar
import com.archko.reader.viewer.dialog.AIPageDialog
import com.archko.reader.viewer.dialog.AddBookmarkDialog
import com.archko.reader.viewer.dialog.OutlineDialog
import com.archko.reader.viewer.dialog.PasswordDialog
import com.archko.reader.viewer.dialog.QueueDialog
import com.archko.reader.viewer.dialog.ThumbnailDialog
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
    paths: List<String>,
    currentPath: String,
    columnCount: Int,
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
    onAIDialogShow: () -> Unit,
    onBookmarkDialogShow: () -> Unit,
    onSearchBarShow: () -> Unit,
    onQueueDialogShow: () -> Unit,
    onThumbnailDialogShow: () -> Unit,
    onColumnCountChanged: (Int) -> Unit,
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
        var showColumn by remember {
            mutableStateOf(
                if (paths.size > 1 || FileTypeUtils.isImageFile(currentPath)) {
                    false
                } else {
                    true
                }
            )
        }
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
                modifier = Modifier.weight(1f),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
            )

            if (columnCount <= 1) {
                IconButton(onClick = { onOrientationChange() }) {
                    Icon(
                        painter = painterResource(if (isVertical) Res.drawable.ic_vertical else Res.drawable.ic_horizontal),
                        contentDescription = if (isVertical) stringResource(Res.string.vertical) else stringResource(
                            Res.string.horizontal
                        ),
                        tint = Color.White
                    )
                }
            }

            if (showColumn && isVertical) {
                if (columnCount == 1) {
                    IconButton(
                        onClick = { onColumnCountChanged(2) },
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_column_two),
                            contentDescription = "",
                            tint = Color.White
                        )
                    }
                } else {
                    IconButton(
                        onClick = { onColumnCountChanged(1) },
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_column_one),
                            contentDescription = "",
                            tint = Color.White
                        )
                    }
                }
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
                        Icon(
                            painter = painterResource(Res.drawable.ic_toc),
                            contentDescription = stringResource(Res.string.outline),
                            tint = Color.White
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
                        contentDescription = "批注画线",
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

                // AI按钮
                if (FileTypeUtils.isDocumentFile(currentPath)) {
                    IconButton(onClick = { onAIDialogShow() }) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_ai),
                            contentDescription = "AI助手",
                            tint = Color.White
                        )
                    }
                }

                // 书签按钮
                if (FileTypeUtils.isDocumentFile(currentPath)) {
                    IconButton(onClick = { onBookmarkDialogShow() }) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_bookmark),
                            contentDescription = stringResource(Res.string.bookmark),
                            tint = Color.White
                        )
                    }
                }

                // 搜索按钮
                IconButton(onClick = { onSearchBarShow() }) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_search),
                        contentDescription = stringResource(Res.string.search),
                        tint = Color.White
                    )
                }
            }

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
    bookmarkViewModel: BookmarkViewModel,
    readingStatsViewModel: ReadingStatsViewModel,
    aiViewModel: AIViewModel,
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

    // AI对话相关状态
    var showAIDialog by remember { mutableStateOf(false) }

    // 书签相关状态
    var showAddBookmarkDialog by remember { mutableStateOf(false) }
    var editingBookmark by remember { mutableStateOf<Bookmark?>(null) }

    // 用于保存当前页码的引用
    var currentPageRef = remember { mutableIntStateOf(progressPage ?: 0) }

    // 搜索相关状态
    var showSearchBar by remember { mutableStateOf(false) }
    var searchState by remember { mutableStateOf(SearchState()) }

    // 构建搜索高亮映射（按页面分组）
    // 当前结果的quads放在最前面，这样Page.kt绘制时第一个quad会被特殊高亮
    val searchHighlightQuads = remember(searchState.results, searchState.currentIndex) {
        val highlightMap = mutableMapOf<Int, MutableList<DocQuad>>()

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

    // 多文件支持
    val currentPath = paths.getOrNull(0) ?: paths.first()

    val speechService: SpeechService = remember { TtsQueueService() }

    LaunchedEffect(Unit) {
        // 加载书签
        if (paths.size == 1 && FileTypeUtils.isDocumentFile(paths[0])) {
            bookmarkViewModel.loadBookmarks(paths[0])
        }
    }

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
            var showThumbnailDialog by remember { mutableStateOf(false) }

            var isVertical by remember { mutableStateOf(scrollOri.toInt() == Vertical) }
            var isReflow by remember { mutableStateOf(reflow == 1L) }
            var gestureMode by remember { mutableStateOf(GestureMode.VIEW) }

            var showQueueDialog by remember { mutableStateOf(false) }

            // 阅读时长追踪
            val readingTimeTracker = remember { ReadingTimeTracker() }

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

            // 阅读时长追踪 - 启动
            LaunchedEffect(currentPath) {
                if (FileTypeUtils.isDocumentFile(currentPath)) {
                    readingTimeTracker.startSession()
                    // 初始化统计数据
                    decoder?.let { dec ->
                        readingStatsViewModel.startSession(currentPath, dec.originalPageSizes.size)
                    }
                }
            }

            // 使用 derivedStateOf 来避免 orientation 变化时重新组合 DocumentView
            val orientation by remember { derivedStateOf { if (isVertical) Vertical else Horizontal } }
            var currentPage by remember { mutableIntStateOf(0) }
            // 添加标志位以跟踪是否为外部更改
            var isExternalChange by remember { mutableStateOf(false) }
            val pageCount: Int = list.size

            // 正在朗读的页面索引
            var speakingPageIndex by remember { mutableStateOf<Int?>(null) }

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

            var columnCount by remember { mutableIntStateOf(1) }

            // 搜索辅助函数 - 必须在performSearch之前定义
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

                            // 如果有结果，跳转到第一个结果
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

            // 设置TTS进度监听器
            LaunchedEffect(speechService) {
                speechService.setProgressListener(object : TtsProgressListener {
                    override fun onStart(bean: ReflowBean) {
                        // Segment开始朗读，更新speakingPageIndex和跳转
                        val newPageStr = bean.page?.split("-")?.firstOrNull()
                        val newPage = newPageStr?.toIntOrNull()
                        println("TTS: onStart - page: ${bean.page}, newPage: $newPage, currentPage: $currentPage")
                        if (newPage != null) {
                            scope.launch {
                                speakingPageIndex = newPage
                                if (newPage != currentPage && newPage != jumpIntent.page) {
                                    jumpIntent = JumpIntent(newPage, JumpMode.PageNavigation)
                                }
                            }
                        }
                    }

                    override fun onDone(bean: ReflowBean) {
                        // Segment完成
                        val newPageStr = bean.page?.split("-")?.firstOrNull()
                        val newPage = newPageStr?.toIntOrNull()
                        println("TTS: onDone - page: ${bean.page}, newPage: $newPage, currentPage: $currentPage")
                    }

                    override fun onFinish() {
                        // 朗读完成
                        println("TTS: onFinish")
                        scope.launch {
                            speakingPageIndex = null
                        }
                    }
                })
            }

            // 监听朗读状态
            LaunchedEffect(speechService) {
                speechService.isSpeakingFlow.collect { speaking ->
                    if (!speaking) {
                        speakingPageIndex = null
                    }
                }
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

            // 清理资源
            DisposableEffect(currentPath) {
                onDispose {
                    println("CustomView.onDispose:$currentPath, $decoder")
                    decoder?.close()
                    if (speechService is TtsQueueService) {
                        speechService.setProgressListener(null)
                        speechService.destroy()
                    } else {
                        speechService.stop()
                    }

                    // 保存阅读统计
                    if (FileTypeUtils.isDocumentFile(currentPath)) {
                        val sessionDuration = readingTimeTracker.pauseSession()
                        val annotationCount = annotationManager.annotations.values.sumOf { it.size }
                        val bookmarkCount = bookmarkViewModel.currentPathBookmarks.value.size
                        readingStatsViewModel.endSession(
                            path = currentPath,
                            sessionDuration = sessionDuration,
                            currentPage = currentPageRef.intValue,
                            annotationCount = annotationCount,
                            bookmarkCount = bookmarkCount
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 顶部工具栏 - 始终显示
                ToolbarContent(
                    isVertical = isVertical,
                    onOrientationChange = { isVertical = !isVertical },
                    onCloseDocument = onCloseDocument,
                    paths = paths,
                    currentPath = currentPath,
                    speechService = speechService,
                    currentPage = currentPage,
                    columnCount = columnCount,
                    decoder = decoder!!,
                    onGestureModeChange = { mode -> gestureMode = mode },
                    gestureMode = gestureMode,
                    onCropChange = { isCrop = !isCrop },
                    isCrop = isCrop,
                    onOutlineDialogShow = { showOutlineDialog = true },
                    onAIDialogShow = { showAIDialog = true },
                    onBookmarkDialogShow = { showAddBookmarkDialog = true },
                    onSearchBarShow = { showSearchBar = !showSearchBar },
                    onQueueDialogShow = { showQueueDialog = true },
                    onThumbnailDialogShow = { showThumbnailDialog = true },
                    onColumnCountChanged = { columnCount = it },
                    scope = scope,
                    onStartSpeaking = { page, dec, binder ->
                        scope.launch {
                            speakingPageIndex = page
                            speakFromCurrentPage(page, decoder!!, speechService)
                            if (!speechService.isSpeaking()) {
                                showQueueDialog = false
                                speakingPageIndex = null
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
                        currentSpeakingPage = speechService.getCurrentReflowBean()?.page?.split("-")
                            ?.firstOrNull() ?: jumpIntent.page.toString(),
                        count = 30,
                        onDismiss = { showQueueDialog = false },
                        onItemClick = { reflowBean ->
                            showQueueDialog = false

                            // 从选中的页面重新开始朗读
                            reflowBean.page?.let { pageStr ->
                                val targetPageStr = pageStr.split("-").firstOrNull()
                                val targetPage = targetPageStr?.toIntOrNull() ?: 0
                                // 跳转到目标页面
                                jumpIntent = JumpIntent(targetPage, JumpMode.PageNavigation)

                                scope.launch {
                                    speechService.stop()

                                    // 等待一小段时间确保停止操作完成
                                    delay(500)

                                    speakingPageIndex = targetPage
                                    speakFromCurrentPage(targetPage, decoder!!, speechService)
                                    if (!speechService.isSpeaking()) {
                                        speakingPageIndex = null
                                    }
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
                            decoder = decoder!!,
                            jumpToPage = jumpIntent.page,
                            jumpMode = jumpIntent.mode,
                            jumpOffsetY = jumpIntent.offsetY,
                            initialOrientation = orientation,
                            columnCount = columnCount,
                            onSaveDocument = onSaveDocument,
                            onCloseDocument = {
                                println("onCloseDocument.isReflow:$isReflow")
                                if (!isReflow) {
                                    onCloseDocument?.invoke()
                                }
                            }, // 只在非重排模式下传递关闭回调
                            onDoubleTapToolbar = { showBottomToolbar = !showBottomToolbar },
                            onPageChanged = { page ->
                                currentPage = page
                                currentPageRef.intValue = page
                            },
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
                            zoom = vZoom,
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

                    androidx.compose.animation.AnimatedVisibility(
                        visible = gestureMode == GestureMode.DRAW,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                    ) {
                        DrawingToolbar(
                            annotationManager = annotationManager,
                            pathConfig = pathConfig,
                            onClose = { config ->
                                pathConfig = config
                            }
                        )
                    }

                    // 搜索栏 - 在画线工具条下方
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showSearchBar,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = if (gestureMode == GestureMode.DRAW) 48.dp else 0.dp)
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

                    // 大纲弹窗（最上层）- 只有单文档文件才显示
                    if (showOutlineDialog && FileTypeUtils.shouldShowOutline(listOf(currentPath))) {
                        val outlineList = decoder?.outlineItems ?: emptyList()
                        OutlineDialog(
                            currentPage = currentPage,
                            currentPath = currentPath,
                            outlineList = outlineList,
                            annotationManager = annotationManager,
                            bookmarkViewModel = bookmarkViewModel,
                            aiViewModel = aiViewModel,
                            onOutlineClick = { item ->
                                jumpIntent = JumpIntent(item.page, JumpMode.PageNavigation)
                                showOutlineDialog = false
                            },
                            onAnnotationClick = { pageIndex ->
                                jumpIntent = JumpIntent(pageIndex, JumpMode.PageNavigation)
                                showOutlineDialog = false
                            },
                            onBookmarkClick = { bookmark ->
                                jumpIntent = JumpIntent(bookmark.pageIndex, JumpMode.PageNavigation)
                                showOutlineDialog = false
                            },
                            onEditBookmark = { bookmark ->
                                editingBookmark = bookmark
                                showAddBookmarkDialog = true
                            },
                            onAIConversationClick = { pageIndex ->
                                jumpIntent = JumpIntent(pageIndex, JumpMode.PageNavigation)
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

                    if (showThumbnailDialog) {
                        ThumbnailDialog(
                            currentPage,
                            list,
                            decoder!!,
                            onPageClick = { page ->
                                jumpIntent = JumpIntent(page, JumpMode.PageNavigation)
                                showThumbnailDialog = false
                            },
                            onDismiss = { showThumbnailDialog = false },
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
                                scope.launch {
                                    toaster.show(message, type = ToastType.Info)
                                }
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
