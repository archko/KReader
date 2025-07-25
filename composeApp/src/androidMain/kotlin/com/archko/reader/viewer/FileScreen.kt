package com.archko.reader.viewer

import android.net.Uri
import android.text.TextUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
                        .padding(horizontal = 10.dp, vertical = 20.dp)
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
                                loadProgress(viewModel, file, pdf)
                                openDocRequest = OpenDocRequest(path, page)
                            }
                        }
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)
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
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 56.dp)
                        ) {
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
                                            loadProgress(viewModel, file, pdf)
                                            openDocRequest = OpenDocRequest(path, page)
                                        }
                                    },
                                    onDelete = {
                                        viewModel.deleteRecent(recentList[i])
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                onShowBottomBarChanged(false)
                CustomView(
                    path = openDocRequest!!.path,
                    progressPage = openDocRequest!!.page,
                    onDocumentClosed = { page, pageCount, zoom ->
                        viewModel.updateProgress(
                            page = page.toLong(),
                            pageCount = pageCount.toLong(),
                            zoom = zoom,
                            crop = 0L
                        )
                    }
                )
            }
        }
    }
}

private fun loadProgress(
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
            viewModel.insertOrUpdate(path, pdf.pageCount.toLong())
        }
    }
}

@Composable
fun Dp.toIntPx(): Int {
    return with(LocalDensity.current) { this@toIntPx.roundToPx() }
}

@Composable
fun Float.toIntPx(): Int {
    return with(LocalDensity.current) { this@toIntPx.toInt() }
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

            val spineWidth = 12.dp
            val thicknessHeight = 12.dp // 使底部和左侧一致

            Box(
                modifier = Modifier
                    .width(itemWidth)
                    .height(itemHeight)
                    .border(BorderStroke(1.dp, Color.LightGray))
            ) {
                // 左上角三角形高光
                Canvas(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .width(spineWidth)
                        .height(spineWidth)
                ) {
                    drawPath(
                        path = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width, 0f)
                            lineTo(0f, size.height)
                            close()
                        },
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFFEFEFEF), Color(0xFFCCCCCC)),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height)
                        )
                    )
                }
                // 左侧高光（细长矩形，模拟书脊高光）
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.White.copy(alpha = 0.7f), Color.Transparent),
                                startY = 0f,
                                endY = itemHeight.value
                            )
                        )
                )
                // 右下角三角形阴影
                Canvas(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .width(thicknessHeight)
                        .height(thicknessHeight)
                ) {
                    drawPath(
                        path = Path().apply {
                            moveTo(size.width, size.height)
                            lineTo(0f, size.height)
                            lineTo(size.width, 0f)
                            close()
                        },
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFFB0B0B0), Color(0xFFEEEEEE)),
                            start = Offset(size.width, size.height),
                            end = Offset(0f, 0f)
                        )
                    )
                }
                // 封面图片（右移、上移，尺寸缩小）
                AsyncImage(
                    model = recent.path?.let {
                        CustomImageData(
                            it,
                            (itemWidth - spineWidth).toIntPx(),
                            (itemHeight - thicknessHeight).toIntPx()
                        )
                    },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = spineWidth, bottom = thicknessHeight)
                )
                // 页码进度
                Text(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    maxLines = 1,
                    text = "${recent.page?.plus(1)}/${recent.pageCount}",
                    fontSize = 11.sp,
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