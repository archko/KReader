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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.archko.reader.pdf.PdfApp
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
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.io.File

@Composable
fun ConvertToEpubDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedFiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var outName by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val paths = mutableListOf<String>()
            try {
                val oneUri = result.data?.data
                if (oneUri != null) {
                    val path = IntentFile.getPath(PdfApp.app!!, oneUri)
                        ?: oneUri.toString()
                    paths.add(path)
                } else {
                    // 多选
                    for (index in 0 until (result.data?.clipData?.itemCount ?: 0)) {
                        val uri = result.data?.clipData?.getItemAt(index)?.uri
                        if (uri != null) {
                            val path = IntentFile.getPath(PdfApp.app!!, uri)
                                ?: uri.toString()
                            paths.add(path)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            scope.launch {
                val filePaths = paths.filter { path ->
                    // 手动过滤 mobi 和 azw3 文件
                    path.endsWith(".mobi", ignoreCase = true) ||
                            path.endsWith(".azw3", ignoreCase = true)
                }

                if (filePaths.isNotEmpty()) {
                    selectedFiles = selectedFiles + filePaths
                } else {
                    Toast.makeText(
                        context,
                        getString(Res.string.convert_select_to_create_epub),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun selectImages() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            type = "*/*"
        }
        filePickerLauncher.launch(intent)
    }

    fun convert() {
        scope.launch {
            if (selectedFiles.isEmpty()) {
                Toast.makeText(
                    context,
                    getString(Res.string.please_select_images_first),
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            var name = outName.trim()
            if (name.isEmpty()) {
                name = "new.epub"
            }
            if (!name.endsWith(".epub")) {
                name = "$name.epub"
            }

            val dir = FileUtils.getStorageDir("book").absolutePath
            val path = dir + File.separator + name

            isCreating = true
            val result = withContext(Dispatchers.IO) {
                PDFCreaterHelper.convertToEpub(path, selectedFiles)
            }
            isCreating = false
            if (result > 0) {
                Toast.makeText(
                    context,
                    getString(Res.string.convert_successfully)
                        .format(result, dir),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    context,
                    getString(Res.string.convert_error),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 800.dp),
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
                        text = stringResource(Res.string.convert_title),
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
                        value = outName,
                        onValueChange = { outName = it },
                        label = { Text(stringResource(Res.string.convert_to_filename)) },
                        placeholder = { Text(stringResource(Res.string.convert_enter_epub_filename)) },
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
                            Text(stringResource(Res.string.convert_select_file))
                        }

                        Button(
                            onClick = { convert() },
                            modifier = Modifier.weight(1f),
                            enabled = selectedFiles.isNotEmpty() && !isCreating
                        ) {
                            if (isCreating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(Res.string.convert_doing))
                            } else {
                                Text(stringResource(Res.string.convert_btn))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (selectedFiles.isNotEmpty()) {
                        Text(
                            text = stringResource(Res.string.convert_tip),
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
                                selectedFiles = selectedFiles.toMutableList().apply {
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
                                selectedFiles,
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
                                            selectedFiles =
                                                selectedFiles.filter { it != imagePath }
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
                                text = stringResource(Res.string.convert_select_to_create_epub),
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
