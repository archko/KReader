package com.archko.reader.viewer.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.archko.reader.pdf.component.DrawType

@Composable
fun DrawTypePickerDialog(
    currentType: DrawType,
    onConfirm: (DrawType) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("LINE") },
        text = {
            Column {
                Row(
                    Modifier.fillMaxWidth().clickable { onConfirm(DrawType.CURVE) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(20.dp).drawBehind {
                        // 按钮上画一个 S 型曲线预览
                        val p = Path().apply {
                            moveTo(4f, size.height - 4f)
                            cubicTo(
                                size.width / 2,
                                size.height,
                                size.width / 2,
                                0f,
                                size.width - 4f,
                                4f
                            )
                        }
                        drawPath(p, Color.Red, style = Stroke(width = 4f))
                    })
                    Spacer(Modifier.width(12.dp))
                    Text("自由曲线 (Curve)")
                    Spacer(Modifier.weight(1f))
                }
                Row(
                    Modifier.fillMaxWidth().clickable { onConfirm(DrawType.LINE) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(20.dp).drawBehind {
                        drawLine(
                            Color.Red,
                            Offset(4f, size.height - 4f),
                            Offset(size.width - 4f, 4f),
                            strokeWidth = 4f
                        )
                    })
                    Spacer(Modifier.width(12.dp))
                    Text("直线模式 (水平/垂直)")
                    Spacer(Modifier.weight(1f))
                }
            }
        },
        confirmButton = {}
    )
}