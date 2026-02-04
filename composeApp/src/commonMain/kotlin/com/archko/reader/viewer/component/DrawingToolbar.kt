package com.archko.reader.viewer.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.archko.reader.pdf.component.DrawType
import com.archko.reader.pdf.component.PathConfig
import com.archko.reader.pdf.state.AnnotationManager
import com.archko.reader.viewer.dialog.ColorPickerDialog
import com.archko.reader.viewer.dialog.DrawTypePickerDialog
import com.archko.reader.viewer.dialog.WidthPickerDialog
import kreader.composeapp.generated.resources.Res
import kreader.composeapp.generated.resources.ic_redo
import kreader.composeapp.generated.resources.ic_undo
import org.jetbrains.compose.resources.painterResource

/**
 * @author: archko 2026/2/3 :08:10
 */
@Composable
fun DrawingToolbar(
    annotationManager: AnnotationManager,
    pathConfig: PathConfig,
    onClose: (PathConfig) -> Unit,
) {
    var showWidthDialog by remember { mutableStateOf(false) }
    var showTypeDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }

    Surface(
        color = Color(0xCC333333),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 1. 粗细预览按钮
            IconButton(onClick = { showWidthDialog = true }) {
                Box(Modifier.size(20.dp).drawBehind {
                    drawLine(
                        Color.Red,
                        Offset(0f, size.height / 2),
                        Offset(size.width, size.height / 2),
                        strokeWidth = pathConfig.strokeWidth
                    )
                })
            }
            // 2. 类型按钮 (显示直线或曲线图标)
            IconButton(onClick = { showTypeDialog = true }) {
                Box(Modifier.size(20.dp).drawBehind {
                    if (pathConfig.drawType == DrawType.LINE) {
                        // 按钮上画一根倾斜的直线预览
                        drawLine(
                            Color.Red,
                            Offset(4f, size.height - 4f),
                            Offset(size.width - 4f, 4f),
                            strokeWidth = 4f
                        )
                    } else {
                        // 按钮上画一个 S 型曲线预览
                        val p = androidx.compose.ui.graphics.Path().apply {
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
                    }
                })
            }
            // 3. 颜色预览按钮
            IconButton(onClick = { showColorDialog = true }) {
                // 使用当前选中的颜色填充圆形预览
                Box(
                    Modifier
                        .size(20.dp)
                        .background(pathConfig.color, CircleShape)
                        .border(1.dp, Color.Gray.copy(alpha = 0.5f), CircleShape)
                )
            }
            VerticalDivider(modifier = Modifier.height(20.dp))
            IconButton(
                onClick = { annotationManager.undo() },
                enabled = annotationManager.canUndo
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_undo),
                    contentDescription = "Undo",
                    tint = if (annotationManager.canUndo) Color.White else Color.Gray
                )
            }

            IconButton(
                onClick = { annotationManager.redo() },
                enabled = annotationManager.canRedo
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_redo),
                    contentDescription = "Redo",
                    tint = if (annotationManager.canRedo) Color.White else Color.Gray
                )
            }
            /*VerticalDivider(modifier = Modifier.height(20.dp))
            IconButton(onClick = { }) {
                Text(
                    text = "X",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }*/
        }
    }

    if (showWidthDialog) {
        WidthPickerDialog(
            currentWidth = pathConfig.strokeWidth,
            onConfirm = { width ->
                showWidthDialog = false
                val config = PathConfig(
                    color = pathConfig.color,
                    strokeWidth = width,
                    drawType = pathConfig.drawType
                )
                onClose(config)
            },
            onDismiss = { showWidthDialog = false },
        )
    }

    if (showTypeDialog) {
        DrawTypePickerDialog(
            currentType = pathConfig.drawType,
            onConfirm = { drawType ->
                showTypeDialog = false
                val config = PathConfig(
                    color = pathConfig.color,
                    strokeWidth = pathConfig.strokeWidth,
                    drawType = drawType
                )
                onClose(config)
            },
            onDismiss = { showTypeDialog = false },
        )
    }

    if (showColorDialog) {
        ColorPickerDialog(
            currentWidth = pathConfig.strokeWidth,
            currentColor = pathConfig.color,
            drawType = pathConfig.drawType,
            onConfirm = { color ->
                showColorDialog = false
                val config = PathConfig(
                    color = color,
                    strokeWidth = pathConfig.strokeWidth,
                    drawType = pathConfig.drawType
                )
                onClose(config)
            },
            onDismiss = { showColorDialog = false },
        )
    }
}