package com.archko.reader.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.archko.reader.pdf.entity.CustomImageData
import com.archko.reader.pdf.entity.Recent
import com.archko.reader.pdf.util.FileTypeUtils
import com.archko.reader.pdf.util.inferName
import com.archko.reader.pdf.viewmodel.PdfViewModel
import com.mohamedrejeb.calf.io.KmpFile
import com.mohamedrejeb.calf.picker.FilePickerFileType
import com.mohamedrejeb.calf.picker.FilePickerSelectionMode
import com.mohamedrejeb.calf.picker.rememberFilePickerLauncher
import kotlinx.coroutines.launch
import kreader.composeapp.generated.resources.Res
import kreader.composeapp.generated.resources.clear_history
import kreader.composeapp.generated.resources.components_thumbnail_corner
import kreader.composeapp.generated.resources.components_thumbnail_left
import kreader.composeapp.generated.resources.components_thumbnail_top
import kreader.composeapp.generated.resources.delete_history
import kreader.composeapp.generated.resources.load_more
import kreader.composeapp.generated.resources.select_pdf
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.io.File

data class OpenDocRequest(val path: String, val page: Int?)

@Composable
fun FileScreen(
    screenWidthInPixels: Int,
    screenHeightInPixels: Int,
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

        LaunchedEffect(Unit) {
            viewModel.loadRecents()
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
                    || FileTypeUtils.isTiffFile(path))) {
                    
                    println("FileScreen: 文件类型支持，准备打开")
                    // 检查是否有历史记录，如果有则使用历史记录的页码，否则从第0页开始
                    viewModel.getProgress(path)
                    val startPage = viewModel.progress?.page?.toInt() ?: 0
                    println("FileScreen: 开始页码: $startPage")
                    openDocRequest = OpenDocRequest(path, startPage)
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

                                // 检查是否有历史记录，如果有则使用历史记录的页码，否则从第0页开始
                                viewModel.getProgress(file.file.absolutePath)
                                val startPage = viewModel.progress?.page?.toInt() ?: 0
                                openDocRequest =
                                    OpenDocRequest(file.file.absolutePath, startPage)
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp)
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
                        //testTts()
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
                                            viewModel.getProgress(file.file.absolutePath)
                                            openDocRequest =
                                                OpenDocRequest(file.file.absolutePath, page)
                                        }
                                    },
                                    onDelete = {
                                        viewModel.deleteRecent(recentList[i])
                                    },
                                    onDeleteCache = {
                                        // 异步删除缓存文件
                                        scope.launch {
                                            //CustomImageFetcher.deleteCache(recentList[i].path)
                                            //APageSizeLoader.deletePageSizeFromFile(recentList[i].path)
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
                    currentPath = openDocRequest!!.path,
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
    }
}

/*@Composable
fun testTts() {
    val speechService: SpeechService = remember { PlatformSpeechService() } // 实例化平台特定的服务

    var textToSpeak by remember { mutableStateOf("Hello from Compose Multiplatform on macOS!") }
    var isSpeaking by remember { mutableStateOf(false) }
    var rate by remember { mutableStateOf(0.5f) }
    var volume by remember { mutableStateOf(0.8f) }
    var availableVoices by remember { mutableStateOf(emptyList<String>()) }
    var selectedVoice by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        availableVoices = speechService.getAvailableVoices()
        if (availableVoices.isNotEmpty()) {
            selectedVoice = availableVoices.first() // 默认选择第一个语音
            speechService.setVoice(selectedVoice)
        }
    }

    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            TextField(
                value = textToSpeak,
                onValueChange = { textToSpeak = it },
                label = { Text("Text to speak") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Button(onClick = {
                    speechService.setRate(rate)
                    speechService.setVolume(volume)
                    speechService.setVoice(selectedVoice) // 每次点击都设置一次
                    speechService.speak(textToSpeak)
                    isSpeaking = true
                }, enabled = !isSpeaking) {
                    Text("Speak")
                }

                Button(onClick = {
                    speechService.stop()
                    isSpeaking = false
                }, enabled = isSpeaking) {
                    Text("Stop")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Rate: ${"%.2f".format(rate)}")
            Slider(
                value = rate,
                onValueChange = {
                    rate = it
                    // speechService.setRate(it) // 可以实时设置，也可以在speak前设置
                },
                valueRange = 0.1f..1.0f,
                steps = 9,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Volume: ${"%.2f".format(volume)}")
            Slider(
                value = volume,
                onValueChange = {
                    volume = it
                    // speechService.setVolume(it) // 可以实时设置，也可以在speak前设置
                },
                valueRange = 0.0f..1.0f,
                steps = 10,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (availableVoices.isNotEmpty()) {
                Text("Selected Voice:")
                DropdownMenu(
                    expanded = false, // You'd need to manage dropdown expansion state
                    onDismissRequest = { *//* Handle dismiss *//* }
                ) {
                    availableVoices.forEach { voiceId ->
                        DropdownMenuItem(onClick = {
                            selectedVoice = voiceId
                            // speechService.setVoice(voiceId) // 实时设置语音
                            // You'll need to implement the dropdown UI correctly.
                            // This is a placeholder for basic voice selection logic.
                            println("Selected voice: $voiceId")
                        }) {
                            Text(voiceId.substringAfterLast(".").replace("_", " ")) // 简单显示名称
                        }
                    }
                }
                // For a simple dropdown, you might use a composable like:
                // https://stackoverflow.com/questions/68171630/jetpack-compose-desktop-how-to-implement-dropdown-menu
                // For brevity, I'm omitting the full DropdownMenu UI here.
                Text(selectedVoice.substringAfterLast(".").replace("_", " "))
            }
        }
    }

    // 监听说话状态 (Optional, NSSpeechSynthesizerDelegate would be more robust)
    LaunchedEffect(isSpeaking) {
        if (isSpeaking) {
            while (speechService.isSpeaking()) {
                kotlinx.coroutines.delay(100) // 检查状态
            }
            isSpeaking = false // 停止说话后更新状态
        }
    }
}*/

@Composable
private fun Dp.toIntPx(): Int {
    return with(LocalDensity.current) { this@toIntPx.roundToPx() }
}

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
            /*DropdownMenuItem(
                text = { Text(stringResource(Res.string.delete_cache)) },
                onClick = {
                    showMenu = false
                    onDeleteCache(recent)
                }
            )*/
        }
    }
}