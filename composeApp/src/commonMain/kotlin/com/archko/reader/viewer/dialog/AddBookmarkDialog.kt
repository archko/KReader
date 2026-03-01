package com.archko.reader.viewer.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.archko.reader.pdf.entity.Bookmark
import kreader.composeapp.generated.resources.Res
import kreader.composeapp.generated.resources.add_bookmark
import kreader.composeapp.generated.resources.bookmark_color
import kreader.composeapp.generated.resources.bookmark_note
import kreader.composeapp.generated.resources.bookmark_title
import kreader.composeapp.generated.resources.cancel
import kreader.composeapp.generated.resources.edit_bookmark
import kreader.composeapp.generated.resources.save
import org.jetbrains.compose.resources.stringResource

/**
 * 添加/编辑书签对话框
 * @author: archko 2026/3/1
 */
@Composable
fun AddBookmarkDialog(
    pageIndex: Int,
    existingBookmark: Bookmark? = null,
    onSave: (title: String?, note: String?, color: Long?) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(existingBookmark?.title ?: "") }
    var note by remember { mutableStateOf(existingBookmark?.note ?: "") }
    var selectedColor by remember { mutableStateOf(existingBookmark?.color) }

    val bookmarkColors = listOf(
        null to Color.Gray,           // 默认
        0xFFFF5252L to Color(0xFFFF5252),  // 红色
        0xFFFFD740L to Color(0xFFFFD740),  // 黄色
        0xFF69F0AEL to Color(0xFF69F0AE),  // 绿色
        0xFF40C4FFL to Color(0xFF40C4FF),  // 蓝色
        0xFFE040FBL to Color(0xFFE040FB),  // 紫色
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = if (existingBookmark == null) 
                        stringResource(Res.string.add_bookmark) 
                    else 
                        stringResource(Res.string.edit_bookmark),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "第 ${pageIndex + 1} 页",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(Res.string.bookmark_title)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(Res.string.bookmark_note)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(Res.string.bookmark_color),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    bookmarkColors.forEach { (colorValue, displayColor) ->
                        val isSelected = selectedColor == colorValue
                        ColorCircle(
                            color = displayColor,
                            isSelected = isSelected,
                            onClick = { selectedColor = colorValue }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.cancel))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            onSave(
                                title.ifBlank { null },
                                note.ifBlank { null },
                                selectedColor
                            )
                            onDismiss()
                        }
                    ) {
                        Text(stringResource(Res.string.save))
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorCircle(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) 
        MaterialTheme.colorScheme.primary 
    else 
        Color.Transparent

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(40.dp)
            .background(borderColor, CircleShape)
            .padding(4.dp)
            .clickable(onClick = onClick)
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(32.dp)
                .background(color, CircleShape)
        )
    }
}
