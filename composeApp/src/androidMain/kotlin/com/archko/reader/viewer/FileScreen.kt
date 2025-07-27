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

data class OpenDocRequest(val path: String, val page: Int?)

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
        var openDocRequest by remember { mutableStateOf<OpenDocRequest?>(null) }

        LaunchedEffect(Unit) {
            viewModel.loadRecents()
        }

        // 处理外部路径
        LaunchedEffect(externalPath) {
            externalPath?.let { path ->
                scope.launch {
                    val file = File(path)
                    if (file.exists()) {
                        viewModel.getProgress(file.absolutePath)
                        val startPage = viewModel.progress?.page?.toInt() ?: 0
                        openDocRequest = OpenDocRequest(file.absolutePath, startPage)
                    }
                }
            }
        }

        val context = LocalContext.current
        val isDarkTheme = isSystemInDarkTheme()

        BackHandler(enabled = openDocRequest != null) {
            openDocRequest = null
            viewModel.path = null
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
                            files.singleOrNull()?.let { kmpFile ->
                                val path = IntentFile.getPath(PdfApp.app!!, kmpFile.uri) ?: kmpFile.uri.toString()
                                val file = File(path)
                                if (file.exists()) {
                                    viewModel.getProgress(file.absolutePath)
                                    val startPage = viewModel.progress?.page?.toInt() ?: 0
                                    openDocRequest = OpenDocRequest(file.absolutePath, startPage)
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp)
                    ) {
                        if (recentList.isNotEmpty()) {
                            Button(
                                onClick = { viewModel.clear() },
                                modifier = Modifier.align(Alignment.CenterStart)
                            ) {
                                Text(stringResource(Res.string.clear), color = MaterialTheme.colorScheme.onBackground)
                            }
                        }
                        Button(
                            onClick = pickerLauncher::launch,
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            Text(stringResource(Res.string.select_pdf), color = MaterialTheme.colorScheme.onBackground)
                        }
                    }

                    if (recentList.isNotEmpty()) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(100.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 56.dp)
                        ) {
                            // 顶部间距 - 占满一行
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            items(
                                count = recentList.size,
                                key = { index -> "$index" }
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
                                                viewModel.getProgress(file.absolutePath)
                                                val startPage = viewModel.progress?.page?.toInt() ?: 0
                                                openDocRequest = OpenDocRequest(file.absolutePath, startPage)
                                            }
                                        }
                                    },
                                    onDelete = {
                                        viewModel.deleteRecent(recentList[i])
                                    }
                                )
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
                    path = openDocRequest!!.path,
                    progressPage = openDocRequest!!.page,
                    onDocumentClosed = { page, pageCount, zoom, scrollX, scrollY, scrollOri ->
                        viewModel.updateProgress(
                            page = page.toLong(),
                            pageCount = pageCount.toLong(),
                            zoom = zoom,
                            crop = 0L,
                            scrollX,
                            scrollY,
                            scrollOri,
                        )
                    },
                    onCloseDocument = {
                        openDocRequest = null
                    },
                    initialScrollX = viewModel.progress?.scrollX ?: 0L,
                    initialScrollY = viewModel.progress?.scrollY ?: 0L,
                    initialZoom = viewModel.progress?.zoom ?: 1.0
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
                        modifier = Modifier
                            .width(itemWidth - leftBorder)
                            .height(topBorder)
                    )
                }
                // 左侧装饰条图片
                Image(
                    painter = painterResource(Res.drawable.components_thumbnail_left),
                    contentDescription = null,
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