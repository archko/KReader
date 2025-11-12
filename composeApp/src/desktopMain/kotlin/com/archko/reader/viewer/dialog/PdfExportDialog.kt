package com.archko.reader.viewer.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.archko.reader.viewer.utils.PDFCreaterHelper
import com.artifex.mupdf.fitz.Document
import com.dokar.sonner.ToastType
import com.dokar.sonner.Toaster
import com.dokar.sonner.rememberToasterState
import com.mohamedrejeb.calf.picker.FilePickerFileType
import com.mohamedrejeb.calf.picker.FilePickerSelectionMode
import com.mohamedrejeb.calf.picker.rememberFilePickerLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kreader.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.io.File
import kotlin.math.roundToInt

@Composable
fun PdfExportDialog(
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val toaster = rememberToasterState()

    var selectedPdfPath by remember { mutableStateOf<String?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var originalWidth by remember { mutableIntStateOf(1080) }
    var startPage by remember { mutableIntStateOf(1) }
    var endPage by remember { mutableIntStateOf(1) }
    var exportWidth by remember { mutableFloatStateOf(1080f) }
    var isExporting by remember { mutableStateOf(false) }

    val pdfPickerLauncher = rememberFilePickerLauncher(
        type = FilePickerFileType.Custom(listOf("pdf")),
        selectionMode = FilePickerSelectionMode.Single
    ) { files ->
        scope.launch {
            files.singleOrNull()?.let { file ->
                val path = file.file.absolutePath
                selectedPdfPath = path
                try {
                    val doc = Document.openDocument(path)
                    pageCount = doc.countPages()
                    endPage = pageCount

                    if (pageCount > 0) {
                        val page = doc.loadPage(0)
                        val bounds = page.bounds
                        originalWidth = bounds.x1.roundToInt()
                        page.destroy()
                    }
                    doc.destroy()
                } catch (e: Exception) {
                    toaster.show(
                        message = getString(Res.string.error_read_pdf_info).format(e.message ?: ""),
                        type = ToastType.Error,
                    )
                }
            }
        }
    }

    fun selectPdf() {
        pdfPickerLauncher.launch()
    }

    fun exportToImages() {
        scope.launch {
            val pdfPath = selectedPdfPath
            if (pdfPath == null) {
                toaster.show(
                    message = getString(Res.string.please_select_pdf_first),
                    type = ToastType.Error,
                )
                return@launch
            }

            if (startPage > endPage || startPage < 1 || endPage > pageCount) {
                toaster.show(
                    message = getString(Res.string.invalid_page_range),
                    type = ToastType.Error,
                )
                return@launch
            }

            val fileName = File(pdfPath).nameWithoutExtension
            val outputDir = System.getProperty("user.home") + File.separator + fileName

            isExporting = true
            PDFCreaterHelper.canExtract = true

            val result = withContext(Dispatchers.IO) {
                PDFCreaterHelper.extractToImages(
                    exportWidth.roundToInt(), // 分辨率宽度
                    outputDir,                // dir (输出目录)
                    pdfPath,                  // pdfPath (PDF文件路径)
                    startPage - 1,            // start (起始页，转换为0基索引)
                    endPage               // end (结束页)
                )
            }
            isExporting = false

            when (result) {
                0 -> {
                    toaster.show(
                        message = getString(Res.string.images_export_success).format(outputDir),
                        type = ToastType.Success,
                    )
                    onDismiss()
                }

                -2 -> {
                    toaster.show(
                        message = getString(Res.string.images_export_failed),
                        type = ToastType.Error,
                    )
                }

                else -> {
                    toaster.show(
                        message = getString(Res.string.export_cancelled_partial).format(result),
                        type = ToastType.Error,
                    )
                }
            }
        }
    }

    fun exportToHtml() {
        scope.launch {
            val pdfPath = selectedPdfPath
            if (pdfPath == null) {
                toaster.show(
                    message = getString(Res.string.please_select_pdf_first),
                    type = ToastType.Error,
                )
                return@launch
            }

            if (startPage > endPage || startPage < 1 || endPage > pageCount) {
                toaster.show(
                    message = getString(Res.string.invalid_page_range),
                    type = ToastType.Error,
                )
                return@launch
            }

            val fileName = File(pdfPath).nameWithoutExtension
            val outputPath = System.getProperty("user.home") + File.separator + "${fileName}.html"

            isExporting = true

            val result = withContext(Dispatchers.IO) {
                PDFCreaterHelper.extractToHtml(
                    startPage - 1,            // start (起始页，转换为0基索引)
                    endPage - 1,              // end (结束页)
                    outputPath,               // path (输出HTML文件路径)
                    pdfPath                   // pdfPath (PDF文件路径)
                )
            }
            isExporting = false

            if (result) {
                toaster.show(
                    message = getString(Res.string.html_export_success).format(outputPath),
                    type = ToastType.Success,
                )
                onDismiss()
            } else {
                toaster.show(
                    message = getString(Res.string.html_export_failed),
                    type = ToastType.Error,
                )
            }
        }
    }

    Toaster(
        state = toaster,
        alignment = Alignment.Center,
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 1000.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
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
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(Res.string.export_pdf),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 20.dp, end = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { selectPdf() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isExporting
                    ) {
                        Text(stringResource(Res.string.select_pdf_file))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { exportToImages() },
                            modifier = Modifier.weight(1f),
                            enabled = !isExporting
                        ) {
                            if (isExporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text(stringResource(Res.string.export_images))
                            }
                        }

                        Button(
                            onClick = { exportToHtml() },
                            modifier = Modifier.weight(1f),
                            enabled = !isExporting
                        ) {
                            if (isExporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text(stringResource(Res.string.export_html))
                            }
                        }
                    }

                    if (selectedPdfPath != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp)
                            ) {
                                Text(
                                    text = stringResource(Res.string.pdf_info),
                                    style = TextStyle(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    stringResource(Res.string.file_label).format(
                                        File(
                                            selectedPdfPath!!
                                        ).name
                                    )
                                )
                                Text(stringResource(Res.string.pages_label).format(pageCount))
                                Text(
                                    stringResource(Res.string.original_width_label).format(
                                        originalWidth
                                    )
                                )
                            }
                        }

                        Text(
                            text = stringResource(Res.string.page_range_label).format(
                                startPage,
                                endPage
                            ),
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )

                        if (pageCount > 1) {
                            RangeSlider(
                                value = startPage.toFloat()..endPage.toFloat(),
                                onValueChange = { range ->
                                    startPage = range.start.roundToInt()
                                    endPage = range.endInclusive.roundToInt()
                                },
                                valueRange = 1f..pageCount.toFloat(),
                                steps = if (pageCount > 2) pageCount - 2 else 0,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(Res.string.page_label).format(1))
                                Text(stringResource(Res.string.page_label).format(pageCount))
                            }
                        } else {
                            Text(stringResource(Res.string.single_page_document))
                        }

                        Text(
                            text = stringResource(Res.string.export_width_label).format(
                                exportWidth.roundToInt()
                            ),
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )

                        Slider(
                            value = exportWidth,
                            onValueChange = { exportWidth = it },
                            valueRange = 1080f..4000f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("1080px")
                            Text("4000px")
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(Res.string.select_pdf_to_export),
                                style = TextStyle(
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}