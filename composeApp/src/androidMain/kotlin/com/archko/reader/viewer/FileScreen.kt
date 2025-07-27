package com.archko.reader.viewer

import android.net.Uri
import android.text.TextUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.archko.reader.pdf.PdfApp
import com.archko.reader.pdf.entity.CustomImageData
import com.archko.reader.pdf.entity.Recent
import com.archko.reader.pdf.state.LocalPdfState
import com.archko.reader.pdf.util.IntentFile
import com.archko.reader.pdf.util.inferName
import com.archko.reader.pdf.viewmodel.PdfViewModel
import com.mohamedrejeb.calf.io.KmpFile
import com.mohamedrejeb.calf.picker.FilePickerFileType
import com.mohamedrejeb.calf.picker.FilePickerSelectionMode
import com.mohamedrejeb.calf.picker.rememberFilePickerLauncher
import kotlinx.coroutines.launch
import kreader.composeapp.generated.resources.Res
import kreader.composeapp.generated.resources.components_thumbnail_corner
import kreader.composeapp.generated.resources.components_thumbnail_left
import kreader.composeapp.generated.resources.components_thumbnail_top
import org.jetbrains.compose.resources.painterResource

data class OpenDocRequest(val path: String, val page: Int?)

@Composable
fun FileScreen(
    screenWidthInPixels: Int,
    screenHeightInPixels: Int,
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier,
    onShowBottomBarChanged: (Boolean) -> Unit = {}
) {
    Theme {
        val scope = rememberCoroutineScope()
        val recentList by viewModel.recentList.collectAsState()
        var openDocRequest by remember { mutableStateOf<OpenDocRequest?>(null) }

        LaunchedEffect(Unit) {
            viewModel.loadRecents()
        }

        BackHandler(enabled = openDocRequest != null) {
            openDocRequest = null
            viewModel.path = null
        }

        Surface(
            modifier = modifier
                .statusBarsPadding()
                .fillMaxSize(),
            color = Color(0xFFF5F5F5)
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
                                val path = IntentFile.getPath(PdfApp.app!!, file.uri) ?: file.uri.toString()
                                // 查询历史进度
                                val recent = viewModel.recentList.value.find { it.path == path }
                                val page = recent?.page?.toInt()
                                val pdf = LocalPdfState(file)
                                // 先加载进度
                                loadProgress(viewModel, file, pdf)
                                // 等待进度加载完成后再设置 openDocRequest
                                openDocRequest = OpenDocRequest(path, page)
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
                                Text("Clear")
                            }
                        }
                        Button(
                            onClick = pickerLauncher::launch,
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            Text("Select PDF")
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
                                        val file = KmpFile(Uri.parse(it.path))
                                        val path = IntentFile.getPath(PdfApp.app!!, file.uri) ?: file.uri.toString()
                                        val page = it.page?.toInt()
                                        val pdf = LocalPdfState(file)
                                        scope.launch {
                                            // 先加载进度
                                            loadProgress(viewModel, file, pdf)
                                            // 等待进度加载完成后再设置 openDocRequest
                                            openDocRequest = OpenDocRequest(path, page)
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

private suspend fun loadProgress(
    viewModel: PdfViewModel,
    file: KmpFile,
    pdf: LocalPdfState?
) {
    if (pdf != null && file.uri.lastPathSegment != null) {
        var path = IntentFile.getPath(PdfApp.app!!, file.uri)
        if (TextUtils.isEmpty(path)) {
            path = file.uri.toString()
        }
        path?.run {
            // 等待 insertOrUpdate 完成
            viewModel.insertOrUpdateAndWait(path, pdf.pageCount.toLong())
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
                    color = Color(0xFF444444).copy(alpha = 0.85f),
                    maxLines = 1,
                    text = "${recent.page?.plus(1)}/${recent.pageCount}",
                    fontSize = 12.sp,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Text(
            modifier = Modifier.padding(2.dp),
            color = Color.Black,
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
                text = { Text("删除历史记录") },
                onClick = {
                    showMenu = false
                    onDelete(recent)
                }
            )
        }
    }
}