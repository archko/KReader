package com.archko.reader.viewer.dialog

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.archko.reader.pdf.util.FileUtils
import com.archko.reader.pdf.util.IntentFile
import com.archko.reader.viewer.utils.PDFCreaterHelper
import com.artifex.mupdf.fitz.Document
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
fun PdfSplitDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedPdfPath by remember { mutableStateOf<String?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var originalWidth by remember { mutableIntStateOf(1080) }
    var splitRangeInput by remember { mutableStateOf("") }
    var outputFileName by remember { mutableStateOf("") }
    var isExporting by remember { mutableStateOf(false) }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val path = IntentFile.getPath(context, result.data?.data)
            if (path != null) {
                selectedPdfPath = path
                scope.launch {
                    try {
                        val doc = Document.openDocument(path)
                        pageCount = doc.countPages()

                        if (pageCount > 0) {
                            val page = doc.loadPage(0)
                            val bounds = page.bounds
                            originalWidth = bounds.x1.roundToInt()

                            page.destroy()
                        }
                        doc.destroy()
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            getString(Res.string.error_read_pdf_info)
                                .format(e.message ?: ""),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    fun selectPdf() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
        }
        pdfPickerLauncher.launch(intent)
    }

    fun splitToPdfs() {
        scope.launch {
            val pdfPath = selectedPdfPath
            if (pdfPath == null) {
                Toast.makeText(
                    context,
                    getString(Res.string.please_select_pdf_first),
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            if (splitRangeInput.isBlank()) {
                Toast.makeText(
                    context, getString(Res.string.split_input_rule),
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            if (outputFileName.isBlank()) {
                Toast.makeText(
                    context, getString(Res.string.split_out_name_tip),
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            isExporting = true

            val userHome = FileUtils.getStorageDir("book")
            val result = withContext(Dispatchers.IO) {
                PDFCreaterHelper.splitPDF(
                    userHome,
                    pdfPath,
                    outputFileName,
                    splitRangeInput
                )
            }

            isExporting = false

            if (result > 0) {
                Toast.makeText(
                    context,
                    getString(Res.string.split_success)
                        .format(userHome),
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    context, getString(Res.string.split_error),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .width(screenWidth * 0.95f)
                    .height(screenHeight * 0.8f),
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

                        Button(
                            onClick = { splitToPdfs() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isExporting && selectedPdfPath != null
                        ) {
                            if (isExporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text(
                                    stringResource(Res.string.split_start)
                                )
                            }
                        }

                        if (selectedPdfPath != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                ) {
                                    Text(
                                        text = stringResource(Res.string.pdf_info),
                                        style = TextStyle(
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    // 封面图片
                                    /*selectedPdfPath?.let { image ->
                                        Box(
                                            modifier = Modifier
                                                .width(150.dp)
                                                .height(195.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AsyncImage(
                                                model = CustomImageData(
                                                    image,
                                                    150,
                                                    195
                                                ),
                                                contentDescription = null,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier.fillMaxSize(),
                                                alignment = Alignment.Center
                                            )
                                        }
                                    }*/

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
                }
            }
        }
    }
}