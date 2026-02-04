package com.archko.reader.viewer.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.archko.reader.pdf.component.DrawType
import kreader.composeapp.generated.resources.Res
import kreader.composeapp.generated.resources.cancel
import kreader.composeapp.generated.resources.confirm
import kreader.composeapp.generated.resources.draw_color_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun ColorPickerDialog(
    currentColor: Color,
    currentWidth: Float,
    drawType: DrawType,
    onConfirm: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    // 常用 PDF 批注颜色列表
    val colors = listOf(
        Color.Red, Color(0xFFE91E63), Color(0xFF9C27B0), // 红、粉、紫
        Color(0xFF673AB7), Color(0xFF3F51B5), Color.Blue, // 深紫、蓝
        Color(0xFF03A9F4), Color(0xFF00BCD4), Color(0xFF009688), // 天蓝、青、绿
        Color(0xFF4CAF50), Color(0xFF8BC34A), Color(0xFFCDDC39), // 浅绿、黄
        Color(0xFFFFEB3B), Color(0xFFFFC107), Color(0xFFFF9800), // 橙
        Color.Black, Color.Gray, Color.LightGray          // 黑、灰
    )

    var selectedColor by remember { mutableStateOf(currentColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.draw_color_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 顶部预览区域：展示当前的颜色、粗细和线条类型
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .padding(bottom = 16.dp)
                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                        .drawBehind {
                            val centerY = size.height / 2
                            if (drawType == DrawType.LINE) {
                                // 预览直线
                                drawLine(
                                    color = selectedColor,
                                    start = Offset(20f, centerY),
                                    end = Offset(size.width - 20f, centerY),
                                    strokeWidth = currentWidth
                                )
                            } else {
                                // 预览曲线 (波浪线)
                                val path = Path().apply {
                                    moveTo(20f, centerY)
                                    quadraticTo(
                                        size.width / 4,
                                        centerY - 20,
                                        size.width / 2,
                                        centerY
                                    )
                                    quadraticTo(
                                        size.width * 3 / 4,
                                        centerY + 20,
                                        size.width - 20,
                                        centerY
                                    )
                                }
                                drawPath(path, selectedColor, style = Stroke(width = currentWidth))
                            }
                        }
                )

                // 颜色网格
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(200.dp)
                ) {
                    items(colors) { color ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(color, CircleShape)
                                .border(
                                    width = if (selectedColor == color) 3.dp else 0.dp,
                                    color = if (selectedColor == color) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = color }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedColor) }) {
                Text(stringResource(Res.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        }
    )
}