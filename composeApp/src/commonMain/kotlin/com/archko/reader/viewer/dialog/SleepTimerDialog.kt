package com.archko.reader.viewer.dialog

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kreader.composeapp.generated.resources.Res
import kreader.composeapp.generated.resources.tts_sleep_dialog_cancel
import kreader.composeapp.generated.resources.tts_sleep_dialog_minutes
import kreader.composeapp.generated.resources.tts_sleep_dialog_ok
import kreader.composeapp.generated.resources.tts_sleep_dialog_reset
import kreader.composeapp.generated.resources.tts_sleep_dialog_title
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * 睡眠定时对话框 - Compose版本
 */
@Composable
fun SleepTimerDialog(
    onDismiss: () -> Unit,
    onTimeSelected: (minutes: Int) -> Unit,
    initialMinutes: Int = 20
) {
    var selectedMinutes by remember { mutableIntStateOf(initialMinutes) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .wrapContentSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(Res.string.tts_sleep_dialog_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 圆形进度条和时间显示
                Box(
                    modifier = Modifier.size(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularSeekBar(
                        progress = selectedMinutes,
                        maxProgress = 120,
                        onProgressChanged = { selectedMinutes = it },
                        modifier = Modifier.fillMaxSize()
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = selectedMinutes.toString(),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(Res.string.tts_sleep_dialog_minutes),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 快捷时间选择按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(10, 30, 60).forEach { minutes ->
                        FilterChip(
                            onClick = { selectedMinutes = minutes },
                            label = { Text("$minutes") },
                            selected = selectedMinutes == minutes,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { selectedMinutes = 0 },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(Res.string.tts_sleep_dialog_reset))
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(Res.string.tts_sleep_dialog_cancel))
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Button(
                        onClick = {
                            onTimeSelected(selectedMinutes)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(Res.string.tts_sleep_dialog_ok))
                    }
                }
            }
        }
    }
}

/**
 * 圆形拖拽进度条
 */
@Composable
fun CircularSeekBar(
    progress: Int,
    maxProgress: Int,
    onProgressChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectDragGestures { change, _ ->
                val center = Offset(size.width / 2f, size.height / 2f)
                //val radius = minOf(size.width, size.height) / 2f - 40.dp.toPx()

                val angle = atan2(
                    change.position.y - center.y,
                    change.position.x - center.x
                )

                // 将角度转换为进度值 (0度在右侧，顺时针增加)
                var normalizedAngle = (angle + PI / 2).toFloat()
                if (normalizedAngle < 0) normalizedAngle += (2 * PI).toFloat()

                val progressValue = ((normalizedAngle / (2 * PI)) * maxProgress).toInt()
                    .coerceIn(1, maxProgress)

                onProgressChanged(progressValue)
            }
        }
    ) {
        val center = size.center
        val radius = minOf(size.width, size.height) / 2f - 40.dp.toPx()
        val strokeWidth = 12.dp.toPx()

        // 绘制背景圆环
        drawCircle(
            color = Color.Gray.copy(alpha = 0.3f),
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // 绘制进度圆弧
        val sweepAngle = (progress.toFloat() / maxProgress) * 360f
        drawArc(
            color = Color(0xFF2196F3),
            startAngle = -90f, // 从顶部开始
            sweepAngle = sweepAngle,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // 绘制拖拽点
        val thumbAngle = (-90f + sweepAngle) * PI / 180f
        val thumbX = center.x + radius * cos(thumbAngle).toFloat()
        val thumbY = center.y + radius * sin(thumbAngle).toFloat()

        drawCircle(
            color = Color(0xFF2196F3),
            radius = 16.dp.toPx(),
            center = Offset(thumbX, thumbY)
        )

        drawCircle(
            color = Color.White,
            radius = 12.dp.toPx(),
            center = Offset(thumbX, thumbY)
        )
    }
}