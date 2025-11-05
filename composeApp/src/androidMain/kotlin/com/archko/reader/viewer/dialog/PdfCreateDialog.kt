package com.archko.reader.viewer.dialog

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.archko.reader.pdf.util.FileUtils
import com.archko.reader.pdf.util.IntentFile
import com.archko.reader.viewer.utils.PDFCreaterHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kreader.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.io.File

@Composable
fun PdfCreateDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var pdfName by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val files = mutableListOf<String>()
            try {
                val oneUri = result.data?.data
                if (oneUri != null) {
                    val path = IntentFile.getPath(context, oneUri)
                    if (path != null) {
                        files.add(path)
                    }
                } else {
                    // 多选图片
                    for (index in 0 until (result.data?.clipData?.itemCount ?: 0)) {
                        val uri = result.data?.clipData?.getItemAt(index)?.uri
                        if (uri != null) {
                            val path = IntentFile.getPath(context, uri)
                            if (path != null) {
                                files.add(path)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            // 按修改时间倒序排序
            files.sortByDescending { File(it).lastModified() }
            selectedImages = selectedImages + files
        }
    }

    fun selectImages() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            type = "image/*"
        }
        imagePickerLauncher.launch(intent)
    }

    fun createPdf() {
        scope.launch {
            if (selectedImages.isEmpty()) {
                Toast.makeText(context, getString(Res.string.please_select_images_first), Toast.LENGTH_SHORT).show()
                return@launch
            }

            var name = pdfName.trim()
            if (name.isEmpty()) {
                name = "new.pdf"
            }
            if (!name.endsWith(".pdf")) {
                name = "$name.pdf"
            }

            val path = FileUtils.getStorageDir("book").absolutePath + File.separator + name

            isCreating = true
            val result = withContext(Dispatchers.IO) {
                PDFCreaterHelper.createPdfFromImages(path, selectedImages)
            }
            isCreating = false
            if (result) {
                Toast.makeText(context, getString(Res.string.pdf_created_successfully), Toast.LENGTH_SHORT).show()
                onDismiss()
            } else {
                Toast.makeText(context, getString(Res.string.pdf_creation_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
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
                        text = stringResource(Res.string.create_pdf),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    OutlinedTextField(
                        value = pdfName,
                        onValueChange = { pdfName = it },
                        label = { Text(stringResource(Res.string.pdf_filename)) },
                        placeholder = { Text(stringResource(Res.string.enter_pdf_filename)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { selectImages() },
                            modifier = Modifier.weight(1f),
                            enabled = !isCreating
                        ) {
                            Text(stringResource(Res.string.select_images))
                        }

                        Button(
                            onClick = { createPdf() },
                            modifier = Modifier.weight(1f),
                            enabled = selectedImages.isNotEmpty() && !isCreating
                        ) {
                            if (isCreating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(Res.string.creating))
                            } else {
                                Text(stringResource(Res.string.create_pdf_button))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (selectedImages.isNotEmpty()) {
                        Text(
                            text = stringResource(Res.string.selected_images_count).format(selectedImages.size),
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(selectedImages) { imagePath ->
                                ImageItem(
                                    imagePath = imagePath,
                                    onRemove = {
                                        selectedImages = selectedImages.filter { it != imagePath }
                                    }
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
                                text = stringResource(Res.string.select_images_to_create_pdf),
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

@Composable
private fun ImageItem(
    imagePath: String,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = File(imagePath),
            contentDescription = null,
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = File(imagePath).name,
            modifier = Modifier.weight(1f),
            style = TextStyle(fontSize = 14.sp),
            maxLines = 2
        )

        IconButton(onClick = onRemove) {
            Icon(
                painter = painterResource(Res.drawable.ic_delete),
                contentDescription = stringResource(Res.string.delete),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}