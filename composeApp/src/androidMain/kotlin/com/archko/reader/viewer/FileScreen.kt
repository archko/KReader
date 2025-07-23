package com.archko.reader.viewer

import android.net.Uri
import android.text.TextUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
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

@Composable
fun FileScreen(
    screenWidthInPixels: Int,
    screenHeightInPixels: Int,
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier,
) {
    Theme {
        var pdf: LocalPdfState? by remember {
            mutableStateOf(null, referentialEqualityPolicy())
        }
        val navController = LocalNavController.current
        BackHandler {
            val path = viewModel.path
            if (path.isNullOrEmpty()) {
                return@BackHandler
            }
            pdf = null
            viewModel.path = null
            navController.popBackStack()
        }

        val scope = rememberCoroutineScope()

        val recentList by viewModel.recentList.collectAsState()
        LaunchedEffect(Unit) {
            val recents = viewModel.loadRecents()
            println("recents:$recents")
        }

        if (pdf == null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .padding(paddingValues = PaddingValues(20.dp, 20.dp, 20.dp, 0.dp))
            ) {
                val pickerLauncher = rememberFilePickerLauncher(
                    type = FilePickerFileType.All,
                    selectionMode = FilePickerSelectionMode.Single
                ) { files ->
                    scope.launch {
                        files.singleOrNull()?.let { file ->
                            pdf = LocalPdfState(file)
                            loadProgress(viewModel, file, pdf)
                        }
                    }
                }

                Spacer(
                    modifier = Modifier.height(16.dp)
                )
                Button(
                    onClick = pickerLauncher::launch
                ) {
                    Text("Select PDF file")
                }

                Spacer(
                    modifier = Modifier.height(16.dp)
                )

                if (recentList.isNotEmpty()) {
                    Row(modifier = Modifier.align(Alignment.Start)) {
                        Button(
                            onClick = { viewModel.clear() }
                        ) {
                            Text("Clear")
                        }
                    }

                    Spacer(
                        modifier = Modifier.height(16.dp)
                    )

                    LazyVerticalGrid(
                        columns = GridCells.FixedSize(140.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            count = recentList.size,
                            key = { index -> "$index" }
                        ) { i ->
                            recentItem(recentList[i]) {
                                val file = KmpFile(Uri.parse(it.path))
                                pdf = LocalPdfState(file)
                                loadProgress(viewModel, file, pdf)
                            }
                        }
                    }
                }
            }
        } else {
            if (pdf!!.pageCount < 1) {
                Text(
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White,
                    text = "Error"
                )
                return@Theme
            }

            CustomView(viewModel.path.toString())
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
            viewModel.insertOrUpdate(path.toString(), pdf.pageCount.toLong())
        }
    }
}

@Composable
fun Dp.toIntPx(): Int {
    return with(LocalDensity.current) { this@toIntPx.roundToPx() }
}

@Composable
private fun recentItem(recent: Recent, click: (Recent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(1.dp)
            .clickable { click(recent) }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(8.dp),
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.2f),
                    spotColor = Color.Black.copy(alpha = 0.4f)
                )
                .border(BorderStroke(1.dp, Color.LightGray))
        ) {
            AsyncImage(
                model = recent.path?.let {
                    CustomImageData(
                        it,
                        135.dp.toIntPx(),
                        180.dp.toIntPx()
                    )
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Text(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp),
                color = Color.Blue,
                maxLines = 1,
                text = "${recent.page?.plus(1)}/${recent.pageCount}",
                fontSize = 11.sp,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            modifier = Modifier.padding(2.dp),
            color = Color.Black, maxLines = 2,
            text = "${recent.path?.inferName()}",
            fontSize = 13.sp,
            overflow = TextOverflow.Ellipsis
        )
    }
}