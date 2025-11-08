package com.archko.reader.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.archko.reader.pdf.cache.APageSizeLoader
import com.archko.reader.pdf.cache.CustomImageFetcher
import com.archko.reader.pdf.cache.ReflowCacheLoader
import com.archko.reader.pdf.entity.CustomImageData
import com.archko.reader.pdf.entity.Recent
import com.archko.reader.pdf.util.FileTypeUtils
import com.archko.reader.pdf.util.inferName
import com.archko.reader.pdf.util.toIntPx
import com.archko.reader.pdf.viewmodel.PdfViewModel
import com.mohamedrejeb.calf.io.KmpFile
import com.mohamedrejeb.calf.picker.FilePickerFileType
import com.mohamedrejeb.calf.picker.FilePickerSelectionMode
import com.mohamedrejeb.calf.picker.rememberFilePickerLauncher
import kotlinx.coroutines.launch
import kreader.composeapp.generated.resources.Res
import kreader.composeapp.generated.resources.browse_directory_message
import kreader.composeapp.generated.resources.browse_directory_title
import kreader.composeapp.generated.resources.cancel
import kreader.composeapp.generated.resources.clear_history
import kreader.composeapp.generated.resources.components_thumbnail_corner
import kreader.composeapp.generated.resources.components_thumbnail_left
import kreader.composeapp.generated.resources.components_thumbnail_top
import kreader.composeapp.generated.resources.confirm
import kreader.composeapp.generated.resources.delete_cache
import kreader.composeapp.generated.resources.delete_history
import kreader.composeapp.generated.resources.load_more
import kreader.composeapp.generated.resources.select_pdf
import kreader.composeapp.generated.resources.setting
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.io.File

data class OpenDocRequest(val paths: List<String>, val page: Int?)

@Composable
fun FileScreen(
    viewModel: PdfViewModel,
    initialFilePath: String? = null,
    modifier: Modifier = Modifier,
    onShowBottomBarChanged: (Boolean) -> Unit = {}
) {
    Theme {
        val scope = rememberCoroutineScope()
        val recentList by viewModel.recentList.collectAsState()
        val hasMoreData by viewModel.hasMoreData.collectAsState()
        val isLoading by viewModel.isLoading.collectAsState()
        var openDocRequest by remember { mutableStateOf<OpenDocRequest?>(null) }
        var showSettingDialog by remember { mutableStateOf(false) }
        var showDirectoryDialog by remember { mutableStateOf(false) }
        var pendingImagePath by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            viewModel.loadRecents()
        }

        // 确认对话框
        if (showDirectoryDialog) {
            AlertDialog(
                onDismissRequest = {
                    showDirectoryDialog = false
                    pendingImagePath = null
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
                                            // 按修改日期倒序排列，最新修改的在最前面
                                            allImageFiles.sortByDescending { it.lastModified() }

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
                        }
                    ) {
                        Text(stringResource(Res.string.confirm))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showDirectoryDialog = false
                            pendingImagePath?.let { imagePath ->
                                // 只打开单个图片
                                openDocRequest = OpenDocRequest(listOf(imagePath), 0)
                            }
                            pendingImagePath = null
                        }
                    ) {
                        Text(stringResource(Res.string.cancel))
                    }
                }
            )
        }

        // 监听文件路径变化
        LaunchedEffect(initialFilePath) {
            initialFilePath?.let { filePath ->
                println("FileScreen: 处理文件路径: $filePath")
                val file = File(filePath)
                println("FileScreen: 文件是否存在: ${file.exists()}")

                val path = file.absolutePath
                if (file.exists() && (FileTypeUtils.isImageFile(path)
                            || FileTypeUtils.isDocumentFile(path)
                            || FileTypeUtils.isTiffFile(path))
                ) {

                    println("FileScreen: 文件类型支持，准备打开")
                    // 检查是否有历史记录，如果有则使用历史记录的页码，否则从第0页开始
                    viewModel.getProgress(path)
                    val startPage = viewModel.progress?.page?.toInt() ?: 0
                    println("FileScreen: 开始页码: $startPage")
                    openDocRequest = OpenDocRequest(listOf(path), startPage)
                    println("FileScreen: 已设置打开文档请求")
                } else {
                    println("FileScreen: 文件不存在或类型不支持")
                }
            }
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
                        selectionMode = FilePickerSelectionMode.Single
                    ) { files ->
                        scope.launch {
                            files.singleOrNull()?.let { file ->
                                val fileObj = file.file
                                val path = fileObj.absolutePath
                                if (!FileTypeUtils.isImageFile(path)
                                    && !FileTypeUtils.isDocumentFile(path)
                                    && !FileTypeUtils.isTiffFile(path)
                                ) {
                                    return@launch
                                }

                                if (FileTypeUtils.isImageFile(path)) {
                                    // 如果是图片文件，弹出对话框询问是否扫描目录
                                    pendingImagePath = path
                                    showDirectoryDialog = true
                                } else {
                                    // 如果是文档文件，直接打开
                                    val paths = listOf(path)
                                    if (FileTypeUtils.shouldSaveProgress(paths)) {
                                        viewModel.getProgress(path)
                                        val startPage = viewModel.progress?.page?.toInt() ?: 0
                                        openDocRequest = OpenDocRequest(paths, startPage)
                                    } else {
                                        openDocRequest = OpenDocRequest(paths, 0)
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp)
                    ) {
                        if (recentList.isNotEmpty()) {
                            Button(
                                onClick = { viewModel.clear() },
                                modifier = Modifier.padding(start = 8.dp),
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
                            modifier = Modifier.padding(start = 16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(stringResource(Res.string.select_pdf))
                        }
                        Button(
                            onClick = { showSettingDialog = true },
                            modifier = Modifier.padding(start = 16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(stringResource(Res.string.setting))
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
                            columns = GridCells.Adaptive(160.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                        val file = KmpFile(File(it.path))
                                        val page = it.page?.toInt()
                                        scope.launch {
                                            val paths = listOf(file.file.absolutePath)
                                            if (FileTypeUtils.shouldSaveProgress(paths)) {
                                                viewModel.getProgress(file.file.absolutePath)
                                                openDocRequest = OpenDocRequest(paths, page)
                                            } else {
                                                openDocRequest = OpenDocRequest(paths, 0)
                                            }
                                        }
                                    },
                                    onDelete = {
                                        viewModel.deleteRecent(recentList[i])
                                    },
                                    onDeleteCache = {
                                        // 异步删除缓存文件
                                        scope.launch {
                                            val path = recentList[i].path
                                            CustomImageFetcher.deleteCache(path)
                                            APageSizeLoader.deletePageSizeFromFile(path)
                                            ReflowCacheLoader.deleteReflowCache(File(path))
                                        }
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
                    onSaveDocument = { page, pageCount, zoom, scrollX, scrollY, scrollOri, reflow, crop ->
                        viewModel.updateProgress(
                            page = page.toLong(),
                            pageCount = pageCount.toLong(),
                            zoom = zoom,
                            crop = crop,
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
                    reflow = viewModel.progress?.reflow ?: 0L,
                    crop = 0L == viewModel.progress?.crop
                )
            }
        }
        if (showSettingDialog) {
            SettingScreen(
                onDismiss = { showSettingDialog = false }
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RecentItem(
    recent: Recent,
    onClick: (Recent) -> Unit,
    onDelete: (Recent) -> Unit,
    onDeleteCache: (Recent) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .onPointerEvent(PointerEventType.Press) { event ->
                val button = event.button
                if (button == PointerButton.Primary) {
                    onClick(recent)
                } else if (button == PointerButton.Secondary) {
                    showMenu = true
                }
            }
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
                        .width(itemWidth - leftBorder - 2.dp)
                        .height(itemHeight - topBorder)
                        .offset(x = leftBorder, y = topBorder),
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
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.delete_cache)) },
                onClick = {
                    showMenu = false
                    onDeleteCache(recent)
                }
            )
        }
    }
}