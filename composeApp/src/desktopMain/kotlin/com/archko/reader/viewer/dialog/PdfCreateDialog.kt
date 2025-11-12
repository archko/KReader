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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.archko.reader.viewer.utils.PDFCreaterHelper
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
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.io.File

@Composable
fun PdfCreateDialog(
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val toaster = rememberToasterState()

    var selectedImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var pdfName by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberFilePickerLauncher(
        type = FilePickerFileType.Image,
        selectionMode = FilePickerSelectionMode.Multiple
    ) { files ->
        scope.launch {
            val imagePaths = files.map { it.file.absolutePath }
            // 按修改时间倒序排序
            val sortedPaths = imagePaths.sortedByDescending { File(it).lastModified() }
            selectedImages = selectedImages + sortedPaths
        }
    }

    fun selectImages() {
        imagePickerLauncher.launch()
    }

    fun createPdf() {
        scope.launch {
            if (selectedImages.isEmpty()) {
                toaster.show(
                    message = getString(Res.string.please_select_images_first),
                    type = ToastType.Error,
                )
                return@launch
            }

            var name = pdfName.trim()
            if (name.isEmpty()) {
                name = "new.pdf"
            }
            if (!name.endsWith(".pdf")) {
                name = "$name.pdf"
            }

            val path = System.getProperty("user.home") + File.separator + name

            isCreating = true
            val result = withContext(Dispatchers.IO) {
                PDFCreaterHelper.createPdfFromImages(path, selectedImages)
            }
            isCreating = false
            if (result) {
                toaster.show(
                    message = getString(Res.string.pdf_created_successfully),
                    type = ToastType.Success,
                )
                onDismiss()
            } else {
                toaster.show(
                    message = getString(Res.string.pdf_creation_failed),
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
            Surface(
                modifier = Modifier.wrapContentSize(),
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
                                text = stringResource(Res.string.selected_images_count).format(
                                    selectedImages.size
                                ),
                                style = TextStyle(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            val lazyListState = rememberLazyListState()
                            val reorderableLazyListState = rememberReorderableLazyListState(
                                lazyListState = lazyListState,
                                onMove = { from, to ->
                                    selectedImages = selectedImages.toMutableList().apply {
                                        add(to.index, removeAt(from.index))
                                    }
                                }
                            )

                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                state = lazyListState,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(
                                    selectedImages,
                                    key = { _, item -> item }) { index, imagePath ->
                                    ReorderableItem(
                                        state = reorderableLazyListState,
                                        key = imagePath
                                    ) { isDragging ->
                                        ImageItem(
                                            imagePath = imagePath,
                                            index = index + 1,
                                            isDragging = isDragging,
                                            dragModifier = Modifier.draggableHandle(),
                                            onRemove = {
                                                selectedImages =
                                                    selectedImages.filter { it != imagePath }
                                            }
                                        )
                                    }
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
}

@Composable
private fun ImageItem(
    imagePath: String,
    index: Int,
    isDragging: Boolean,
    dragModifier: Modifier,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDragging) 8.dp else 2.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_menu),
                contentDescription = "Drag to reorder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .then(dragModifier)
            )

            Spacer(modifier = Modifier.width(8.dp))

            AsyncImage(
                model = File(imagePath),
                contentDescription = null,
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "${index}. ${File(imagePath).name}",
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    maxLines = 2
                )
            }

            IconButton(onClick = onRemove) {
                Icon(
                    painter = painterResource(Res.drawable.ic_delete),
                    contentDescription = stringResource(Res.string.delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}