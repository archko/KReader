package com.archko.reader.viewer.dialog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun WidthPickerDialog(
    currentWidth: Float,
    onConfirm: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    var width by remember { mutableFloatStateOf(currentWidth) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("笔触粗细") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 实时预览线宽
                Box(Modifier.fillMaxWidth().height(40.dp).drawBehind {
                    drawLine(
                        Color.Black,
                        Offset(20f, size.height / 2),
                        Offset(size.width - 20f, size.height / 2),
                        strokeWidth = width
                    )
                })
                Slider(value = width, onValueChange = { width = it }, valueRange = 1f..10f)
                Text("${width.toInt()} px")
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(width) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}