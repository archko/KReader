package com.archko.reader.viewer

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import coil3.compose.AsyncImage
import com.archko.reader.pdf.PdfApp
import com.archko.reader.pdf.entity.CustomImageData
import com.archko.reader.pdf.entity.Recent
import com.archko.reader.pdf.util.FileTypeUtils
import com.archko.reader.pdf.util.IntentFile
import com.archko.reader.pdf.util.inferName
import com.archko.reader.pdf.viewmodel.PdfViewModel
import com.mohamedrejeb.calf.io.KmpFile
import com.mohamedrejeb.calf.picker.FilePickerFileType
import com.mohamedrejeb.calf.picker.FilePickerSelectionMode
import com.mohamedrejeb.calf.picker.rememberFilePickerLauncher
import kotlinx.coroutines.launch
import kreader.composeapp.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.io.File

data class OpenDocRequest(val paths: List<String>, val page: Int?)

@Composable
fun FileScreen(
    screenWidthInPixels: Int,
    screenHeightInPixels: Int,
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier,
    onShowBottomBarChanged: (Boolean) -> Unit = {},
    externalPath: String? = null
) {
    Theme {
        val scope = rememberCoroutineScope()
        val recentList by viewModel.recentList.collectAsState()
        val hasMoreData by viewModel.hasMoreData.collectAsState()
        val isLoading by viewModel.isLoading.collectAsState()
        var openDocRequest by remember { mutableStateOf<OpenDocRequest?>(null) }
        var showDirectoryDialog by remember { mutableStateOf(false) }
        var pendingImagePath by remember { mutableStateOf<String?>(null) }
        var pendingFiles by remember { mutableStateOf<List<File>?>(null) }

        LaunchedEffect(Unit) {
            viewModel.loadRecents()
        }

        // 处理外部路径
        LaunchedEffect(externalPath) {
            externalPath?.let { path ->
                scope.launch {
                    val file = File(path)
                    if (file.exists()) {
                        if (FileTypeUtils.isImageFile(path)) {
                            openDocRequest = OpenDocRequest(listOf(path), 0)
                        } else if (FileTypeUtils.isDocumentFile(path)) {
                            // 如果是支持的文档文件，直接打开
                            val paths = listOf(file.absolutePath)
                            if (FileTypeUtils.shouldSaveProgress(paths)) {
                                viewModel.getProgress(file.absolutePath)
                                val startPage = viewModel.progress?.page?.toInt() ?: 0
                                openDocRequest = OpenDocRequest(paths, startPage)
                            } else {
                                openDocRequest = OpenDocRequest(paths, 0)
                            }
                        }
                    }
                }
            }
        }

        val context = LocalContext.current
        val isDarkTheme = isSystemInDarkTheme()

        BackHandler(enabled = openDocRequest != null) {
            openDocRequest = null
            // 恢复状态栏显示
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

        // 确认对话框
        if (showDirectoryDialog) {
            AlertDialog(
                onDismissRequest = {
                    showDirectoryDialog = false
                    pendingImagePath = null
                    pendingFiles = null
                },
                title = { Text(stringResource(Res.string.browse_directory_title)) },
                text = { Text(stringResource(Res.string.browse_directory_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDirectoryDialog = false
                            pendingImagePath?.let { imagePath ->
                                scope.launch {
                                    // 扫描整个目录
                                    val imageFile = File(imagePath)
                                    val parentDir = imageFile.parentFile
                                    if (parentDir != null && parentDir.exists()) {
                                        val allImageFiles = mutableListOf<File>()

                                        // 只扫描当前目录层级，不递归子目录
                                        parentDir.listFiles()?.forEach { file ->
                                            if (FileTypeUtils.isValidImageFile(file)) {
                                                allImageFiles.add(file)
                                            }
                                        }

                                        if (allImageFiles.isNotEmpty()) {
                                            val paths = allImageFiles.map { it.absolutePath }
                                            openDocRequest = OpenDocRequest(paths, 0)
                                        }
                                    } else {
                                        // 如果父目录不存在，只打开当前图片
                                        openDocRequest = OpenDocRequest(listOf(imagePath), 0)
                                    }
                                }
                            }
                            pendingImagePath = null
                            pendingFiles = null
                        }
                    ) {
                        Text(stringResource(Res.string.confirm))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showDirectoryDialog = false
                            pendingFiles?.let { images ->
                                if (images.isNotEmpty()) {
                                    val paths = images.map { it.absolutePath }
                                    openDocRequest = OpenDocRequest(paths, 0)
                                }
                            }
                            pendingImagePath = null
                            pendingFiles = null
                        }
                    ) {
                        Text(stringResource(Res.string.cancel))
                    }
                }
            )
        }

        Surface(
            modifier = modifier
                .fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            if (openDocRequest == null) {
                onShowBottomBarChanged(true)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp)
                ) {
                    val pickerLauncher = rememberFilePickerLauncher(
                        type = FilePickerFileType.All,
                        selectionMode = FilePickerSelectionMode.Multiple
                    ) { files ->
                        scope.launch {
                            if (files.isNotEmpty()) {
                                // 先判断所有文件，得到图片列表和文档列表
                                val imageFiles = mutableListOf<File>()
                                val documentFiles = mutableListOf<File>()

                                files.forEach { file ->
                                    val path = IntentFile.getPath(PdfApp.app!!, file.uri) ?: file.uri.toString()
                                    val fileObj = File(path)
                                    if (FileTypeUtils.isValidImageFile(fileObj)) {
                                        imageFiles.add(fileObj)
                                    } else if (FileTypeUtils.isDocumentFile(path)) {
                                        documentFiles.add(fileObj)
                                    }
                                }

                                if (imageFiles.isNotEmpty()) {
                                    // 有图片，弹出对话框选择
                                    val firstImagePath = imageFiles.first().absolutePath
                                    pendingImagePath = firstImagePath
                                    pendingFiles = imageFiles // 直接设置为图片列表
                                    showDirectoryDialog = true
                                } else if (documentFiles.isNotEmpty()) {
                                    // 没有图片但有文档，直接打开第一个文档
                                    val firstDocumentPath = documentFiles.first().absolutePath
                                    val paths = listOf(firstDocumentPath)
                                    if (FileTypeUtils.shouldSaveProgress(paths)) {
                                        viewModel.getProgress(paths.first())
                                        val startPage = viewModel.progress?.page?.toInt() ?: 0
                                        openDocRequest = OpenDocRequest(paths, startPage)
                                    }
                                }
                                // 如果都没有，什么都不做
                            }
                        }
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp)
                    ) {
                        if (recentList.isNotEmpty()) {
                            Button(
                                onClick = { viewModel.clear() },
                                modifier = Modifier.align(Alignment.CenterStart),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text(stringResource(Res.string.clear_history))
                            }
                        }
                        Button(
                            onClick = pickerLauncher::launch,
                            modifier = Modifier.align(Alignment.Center),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(stringResource(Res.string.select_pdf))
                        }
                    }

                    if (recentList.isNotEmpty()) {
                        val gridState = rememberLazyGridState()

                        // 监听滚动到底部自动加载更多
                        LaunchedEffect(gridState) {
                            snapshotFlow { gridState.layoutInfo.visibleItemsInfo }
                                .collect { visibleItems ->
                                    if (visibleItems.isNotEmpty()) {
                                        val lastVisibleItem = visibleItems.last()
                                        val totalItems = recentList.size

                                        // 当滚动到最后几个项目时，自动加载更多
                                        if (lastVisibleItem.index >= totalItems - 3 && hasMoreData && !isLoading) {
                                            viewModel.loadMoreRecents()
                                        }
                                    }
                                }
                        }

                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Adaptive(100.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 56.dp)
                        ) {
                            // 顶部间距 - 占满一行
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Spacer(modifier = Modifier.height(0.dp))
                            }

                            items(
                                count = recentList.size,
                                key = { index -> recentList[index].path ?: "$index" }
                            ) { i ->
                                RecentItem(
                                    recent = recentList[i],
                                    onClick = {
                                        val kmpFile = KmpFile(Uri.parse(it.path))
                                        val path =
                                            IntentFile.getPath(PdfApp.app!!, kmpFile.uri) ?: kmpFile.uri.toString()
                                        val file = File(path)
                                        if (file.exists()) {
                                            scope.launch {
                                                val paths = listOf(file.absolutePath)
                                                if (FileTypeUtils.shouldSaveProgress(paths)) {
                                                    viewModel.getProgress(file.absolutePath)
                                                    val startPage = viewModel.progress?.page?.toInt() ?: 0
                                                    openDocRequest = OpenDocRequest(paths, startPage)
                                                } else {
                                                    openDocRequest = OpenDocRequest(paths, 0)
                                                }
                                            }
                                        }
                                    },
                                    onDelete = {
                                        viewModel.deleteRecent(recentList[i])
                                    }
                                )
                            }

                            // 加载更多按钮 - 占满一行
                            if (hasMoreData) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isLoading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Button(
                                                onClick = { viewModel.loadMoreRecents() },
                                                modifier = Modifier.padding(horizontal = 16.dp)
                                            ) {
                                                Text(
                                                    text = stringResource(Res.string.load_more),
                                                    color = MaterialTheme.colorScheme.onBackground
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 底部间距 - 占满一行
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            } else {
                onShowBottomBarChanged(false)
                CustomView(
                    paths = openDocRequest!!.paths,
                    progressPage = openDocRequest!!.page,
                    onSaveDocument = { page, pageCount, zoom, scrollX, scrollY, scrollOri, reflow ->
                        viewModel.updateProgress(
                            page = page.toLong(),
                            pageCount = pageCount.toLong(),
                            zoom = zoom,
                            crop = 0L,
                            scrollX,
                            scrollY,
                            scrollOri,
                            reflow,
                        )
                    },
                    onCloseDocument = {
                        openDocRequest = null
                    },
                    initialScrollX = viewModel.progress?.scrollX ?: 0L,
                    initialScrollY = viewModel.progress?.scrollY ?: 0L,
                    initialZoom = viewModel.progress?.zoom ?: 1.0,
                    scrollOri = viewModel.progress?.scrollOri ?: 0,
                    reflow = viewModel.progress?.reflow ?: 0L
                )
            }
        }
    }
}

@Composable
fun Dp.toIntPx(): Int {
    return with(LocalDensity.current) { this@toIntPx.roundToPx() }
}

@Composable
private fun RecentItem(
    recent: Recent,
    onClick: (Recent) -> Unit,
    onDelete: (Recent) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .combinedClickable(
                onClick = { onClick(recent) },
                onLongClick = { showMenu = true }
            )
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth()
        ) {
            val itemWidth = maxWidth
            val aspectRatio = 1.3f
            val itemHeight = itemWidth * aspectRatio

            val leftBorder = 15.dp
            val topBorder = 10.dp
            Box(
                modifier = Modifier
                    .width(itemWidth)
                    .height(itemHeight)
            ) {
                // 顶部区域：左上角图片 + 顶部装饰条图片
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .width(itemWidth)
                        .height(topBorder)
                ) {
                    Image(
                        painter = painterResource(Res.drawable.components_thumbnail_corner),
                        contentDescription = null,
                        modifier = Modifier
                            .width(leftBorder)
                            .height(topBorder)
                            .offset(x = 0.7.dp)
                    )
                    Image(
                        painter = painterResource(Res.drawable.components_thumbnail_top),
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .width(itemWidth - leftBorder)
                            .height(topBorder)
                    )
                }
                // 左侧装饰条图片
                Image(
                    painter = painterResource(Res.drawable.components_thumbnail_left),
                    contentDescription = null,
                    contentScale = ContentScale.FillHeight,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .width(leftBorder)
                        .height(itemHeight - topBorder)
                        .offset(y = topBorder)
                )
                // 封面图片
                AsyncImage(
                    model = recent.path?.let {
                        CustomImageData(
                            it,
                            (itemWidth - leftBorder).toIntPx(),
                            (itemHeight - topBorder).toIntPx()
                        )
                    },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(itemWidth - leftBorder)
                        .height(itemHeight - topBorder)
                        .offset(x = leftBorder - 1.dp, y = topBorder),
                    alignment = Alignment.Center
                )
                // 页码进度
                Text(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    maxLines = 1,
                    text = "${recent.page?.plus(1)}/${recent.pageCount}",
                    fontSize = 12.sp,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Text(
            modifier = Modifier.padding(2.dp),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            text = "${recent.path?.inferName()}",
            fontSize = 13.sp,
            lineHeight = 15.sp, // 行间距更紧凑
            overflow = TextOverflow.Ellipsis
        )

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.delete_history)) },
                onClick = {
                    showMenu = false
                    onDelete(recent)
                }
            )
        }
    }
}