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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusModifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.archko.reader.pdf.entity.CustomImageData
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
import kreader.composeapp.generated.resources.Res
import kreader.composeapp.generated.resources.error_read_pdf_info
import kreader.composeapp.generated.resources.file_label
import kreader.composeapp.generated.resources.ic_back
import kreader.composeapp.generated.resources.original_width_label
import kreader.composeapp.generated.resources.pages_label
import kreader.composeapp.generated.resources.pdf_info
import kreader.composeapp.generated.resources.please_select_pdf_first
import kreader.composeapp.generated.resources.select_pdf_file
import kreader.composeapp.generated.resources.split_doing
import kreader.composeapp.generated.resources.split_error
import kreader.composeapp.generated.resources.split_input_rule
import kreader.composeapp.generated.resources.split_out_name
import kreader.composeapp.generated.resources.split_out_name_tip
import kreader.composeapp.generated.resources.split_out_setting
import kreader.composeapp.generated.resources.split_pdf_to_split
import kreader.composeapp.generated.resources.split_rule
import kreader.composeapp.generated.resources.split_rule_desc
import kreader.composeapp.generated.resources.split_start
import kreader.composeapp.generated.resources.split_success
import kreader.composeapp.generated.resources.split_title
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.io.File
import kotlin.math.roundToInt

@Composable
fun PdfSplitDialog(
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val toaster = rememberToasterState()

    var selectedPdfPath by remember { mutableStateOf<String?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var originalWidth by remember { mutableIntStateOf(1080) }
    var splitRangeInput by remember { mutableStateOf("") }
    var outputFileName by remember { mutableStateOf("") }
    var isExporting by remember { mutableStateOf(false) }

    val pdfPickerLauncher = rememberFilePickerLauncher(
        type = FilePickerFileType.Custom(listOf("pdf")),
        selectionMode = FilePickerSelectionMode.Single
    ) { files ->
        scope.launch {
            files.singleOrNull()?.let { file ->
                val path = file.file.absolutePath
                selectedPdfPath = path
                outputFileName = File(path).nameWithoutExtension + "_split"

                try {
                    val doc = Document.openDocument(path)
                    pageCount = doc.countPages()

                    if (pageCount > 0) {
                        val page = doc.loadPage(0)
                        val bounds = page.bounds
                        originalWidth = bounds.x1.roundToInt()

                        // Generate cover image
                        withContext(Dispatchers.Default) {
                            try {
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        page.destroy()
                    }
                    doc.destroy()
                } catch (e: Exception) {
                    toaster.show(
                        message = getString(Res.string.error_read_pdf_info)
                            .format(e.message ?: ""),
                        type = ToastType.Error,
                    )
                }
            }
        }
    }

    fun selectPdf() {
        pdfPickerLauncher.launch()
    }

    fun splitToPdfs() {
        scope.launch {
            val pdfPath = selectedPdfPath
            if (pdfPath == null) {
                toaster.show(
                    message = getString(Res.string.please_select_pdf_first),
                    type = ToastType.Error,
                )
                return@launch
            }

            if (splitRangeInput.isBlank()) {
                toaster.show(
                    message = getString(Res.string.split_input_rule),
                    type = ToastType.Error,
                )
                return@launch
            }

            if (outputFileName.isBlank()) {
                toaster.show(
                    message = getString(Res.string.split_out_name_tip),
                    type = ToastType.Error,
                )
                return@launch
            }

            isExporting = true

            val result = withContext(Dispatchers.IO) {
                PDFCreaterHelper.splitPDF(
                    pdfPath,
                    outputFileName,
                    splitRangeInput
                )
            }

            isExporting = false

            if (result > 0) {
                val userHome = System.getProperty("user.home")
                toaster.show(
                    message = getString(Res.string.split_success)
                        .format(userHome),
                    type = ToastType.Success,
                )
            } else {
                toaster.show(
                    message = getString(Res.string.split_error),
                    type = ToastType.Error,
                )
            }
        }
    }

    Toaster(
        state = toaster,
        maxVisibleToasts = 1,
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
                        text = stringResource(Res.string.split_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(0.45f)
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { selectPdf() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isExporting
                        ) {
                            Text(stringResource(Res.string.select_pdf_file))
                        }

                        if (selectedPdfPath != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = stringResource(Res.string.pdf_info),
                                        style = TextStyle(
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )

                                    // 封面图片
                                    selectedPdfPath?.let { image ->
                                        Box(
                                            modifier = Modifier
                                                .width(300.dp)
                                                .height(390.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AsyncImage(
                                                model = CustomImageData(
                                                    image,
                                                    300,
                                                    390
                                                ),
                                                contentDescription = null,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier.fillMaxSize(),
                                                alignment = Alignment.Center
                                            )
                                        }
                                    }

                                    Text(
                                        stringResource(Res.string.file_label)
                                            .format(File(selectedPdfPath!!).name)
                                    )
                                    Text(
                                        stringResource(Res.string.pages_label)
                                            .format(pageCount)
                                    )
                                    Text(
                                        stringResource(Res.string.original_width_label)
                                            .format(originalWidth)
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(Res.string.split_pdf_to_split),
                                    style = TextStyle(
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(0.55f)
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(Res.string.split_rule),
                                style = TextStyle(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Text(
                                text = stringResource(Res.string.split_rule_desc),
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            OutlinedTextField(
                                value = splitRangeInput,
                                onValueChange = { splitRangeInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("1-10,11-20") },
                                enabled = !isExporting && selectedPdfPath != null
                            )
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(Res.string.split_out_setting),
                                style = TextStyle(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            OutlinedTextField(
                                value = outputFileName,
                                onValueChange = { outputFileName = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(Res.string.split_out_name)) },
                                enabled = !isExporting && selectedPdfPath != null
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Button(
                            onClick = { splitToPdfs() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isExporting && selectedPdfPath != null
                        ) {
                            if (isExporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                if (isExporting) stringResource(Res.string.split_doing)
                                else stringResource(Res.string.split_start)
                            )
                        }
                    }
                }
            }
        }
    }
}