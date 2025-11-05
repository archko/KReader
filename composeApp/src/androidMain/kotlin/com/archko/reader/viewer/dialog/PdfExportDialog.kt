package com.archko.reader.viewer.dialog

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.archko.reader.pdf.util.FileUtils
import com.archko.reader.pdf.util.IntentFile
import com.archko.reader.viewer.utils.PDFCreaterHelper
import com.artifex.mupdf.fitz.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kreader.composeapp.generated.resources.Res
import kreader.composeapp.generated.resources.export_pdf
import kreader.composeapp.generated.resources.ic_back
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.io.File
import kotlin.math.roundToInt

@Composable
fun PdfExportDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedPdfPath by remember { mutableStateOf<String?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var originalWidth by remember { mutableIntStateOf(1080) }
    var startPage by remember { mutableIntStateOf(1) }
    var endPage by remember { mutableIntStateOf(1) }
    var exportWidth by remember { mutableFloatStateOf(1080f) }
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
                        endPage = pageCount

                        if (pageCount > 0) {
                            val page = doc.loadPage(0)
                            val bounds = page.bounds
                            originalWidth = bounds.x1.roundToInt()
                            page.destroy()
                        }
                        doc.destroy()
                    } catch (e: Exception) {
                        Toast.makeText(context, "无法读取PDF信息: ${e.message}", Toast.LENGTH_SHORT)
                            .show()
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

    fun exportToImages() {
        val pdfPath = selectedPdfPath
        if (pdfPath == null) {
            Toast.makeText(context, "请先选择PDF文件", Toast.LENGTH_SHORT).show()
            return
        }

        if (startPage > endPage || startPage < 1 || endPage > pageCount) {
            Toast.makeText(context, "页面范围无效", Toast.LENGTH_SHORT).show()
            return
        }

        val fileName = File(pdfPath).nameWithoutExtension
        val outputDir = FileUtils.getStorageDir(fileName).absolutePath

        isExporting = true
        PDFCreaterHelper.canExtract = true

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                PDFCreaterHelper.extractToImages(
                    exportWidth.roundToInt(), // 分辨率宽度
                    outputDir,                // dir (输出目录)
                    pdfPath,                  // pdfPath (PDF文件路径)
                    startPage - 1,            // start (起始页，转换为0基索引)
                    endPage - 1               // end (结束页)
                )
            }
            isExporting = false

            when (result) {
                0 -> {
                    Toast.makeText(context, "图片导出成功！文件保存在: $outputDir", Toast.LENGTH_LONG)
                        .show()
                    onDismiss()
                }

                -2 -> {
                    Toast.makeText(context, "图片导出失败", Toast.LENGTH_SHORT).show()
                }

                else -> {
                    Toast.makeText(context, "导出被取消，已导出: ${result}张", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    fun exportToHtml() {
        val pdfPath = selectedPdfPath
        if (pdfPath == null) {
            Toast.makeText(context, "请先选择PDF文件", Toast.LENGTH_SHORT).show()
            return
        }

        if (startPage > endPage || startPage < 1 || endPage > pageCount) {
            Toast.makeText(context, "页面范围无效", Toast.LENGTH_SHORT).show()
            return
        }

        val fileName = File(pdfPath).nameWithoutExtension
        val outputPath = FileUtils.getStorageDir(fileName).absolutePath + File.separator + "${fileName}.html"

        isExporting = true

        scope.launch {
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
                Toast.makeText(context, "HTML导出成功！文件保存在: $outputPath", Toast.LENGTH_LONG)
                    .show()
                onDismiss()
            } else {
                Toast.makeText(context, "HTML导出失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .widthIn(min = 400.dp, max = 600.dp)
                .heightIn(min = 500.dp, max = 700.dp)
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.8f),
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
                        Text("选择PDF文件")
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
                                Text("导出图片")
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
                                Text("导出HTML")
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
                                    text = "PDF信息",
                                    style = TextStyle(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("文件: ${File(selectedPdfPath!!).name}")
                                Text("页数: $pageCount")
                                Text("原始宽度: ${originalWidth}px")
                            }
                        }

                        Text(
                            text = "页面范围: $startPage - $endPage",
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
                                Text("第1页")
                                Text("第${pageCount}页")
                            }
                        } else {
                            Text("单页文档")
                        }

                        Text(
                            text = "导出宽度: ${exportWidth.roundToInt()}px",
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
                                text = "请选择要导出的PDF文件",
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