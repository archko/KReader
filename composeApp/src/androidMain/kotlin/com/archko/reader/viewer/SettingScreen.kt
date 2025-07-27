package com.archko.reader.viewer

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.*
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import kreader.composeapp.generated.resources.Res
import kreader.composeapp.generated.resources.about_content
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingScreen(
    modifier: Modifier = Modifier,
) {
    Theme {
        Surface(
            modifier = modifier
                .fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                        .padding(top = 40.dp) // 添加顶部间距，避免内容伸到状态栏
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Avatar()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "KReader",
                            style = TextStyle(
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 28.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Author:archko@gmail.com",
                            style = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                fontSize = 14.sp
                            ),
                            maxLines = 1
                        )
                    }

                    SettingCategory()
                }
            }
        }
    }
}

@Composable
fun Avatar() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 创建现代PDF阅读器图标
        Canvas(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(96.dp))
                .background(Color(0xFFE3F2FD))
        ) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val iconSize = size.minDimension * 0.7f
            
            // 绘制更丰富的背景渐变
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF64B5F6), // 更亮的蓝色
                        Color(0xFF42A5F5), // 中等蓝色
                        Color(0xFF1976D2), // 深蓝色
                        Color(0xFF1565C0), // 更深蓝色
                        Color(0xFF0D47A1)  // 最深的蓝色
                    ),
                    center = Offset(centerX - size.minDimension * 0.1f, centerY - size.minDimension * 0.1f),
                    radius = size.minDimension * 0.6f
                ),
                radius = size.minDimension / 2,
                center = Offset(centerX, centerY)
            )
            
            // 添加额外的光晕效果
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF90CAF9).copy(alpha = 0.3f),
                        Color(0xFF90CAF9).copy(alpha = 0.1f),
                        Color.Transparent
                    ),
                    center = Offset(centerX + size.minDimension * 0.2f, centerY + size.minDimension * 0.2f),
                    radius = size.minDimension * 0.4f
                ),
                radius = size.minDimension * 0.4f,
                center = Offset(centerX + size.minDimension * 0.2f, centerY + size.minDimension * 0.2f)
            )

            // 绘制书本主体（白色，带圆角）- 横着放，宽大于高
            val bookWidth = iconSize * 0.85f  // 宽度变大
            val bookHeight = iconSize * 0.65f // 高度变小
            val bookLeft = centerX - bookWidth / 2
            val bookTop = centerY - bookHeight / 2

            // 书本阴影
            drawRoundRect(
                color = Color(0xFF000000).copy(alpha = 0.1f),
                topLeft = Offset(bookLeft + 2.dp.toPx(), bookTop + 2.dp.toPx()),
                size = Size(bookWidth, bookHeight),
                cornerRadius = CornerRadius(6.dp.toPx())
            )

            // 书本主体
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(bookLeft, bookTop),
                size = Size(bookWidth, bookHeight),
                cornerRadius = CornerRadius(6.dp.toPx())
            )

            // 绘制书本页面线条（更细更优雅）
            val lineSpacing = bookHeight / 9
            for (i in 1..7) {
                val lineY = bookTop + lineSpacing * i
                val lineLength = bookWidth * (0.6f + i * 0.03f) // 渐变的线条长度
                val lineStart = bookLeft + (bookWidth - lineLength) / 2

                drawLine(
                    color = Color(0xFFE8EAF6).copy(alpha = 0.8f),
                    start = Offset(lineStart, lineY),
                    end = Offset(lineStart + lineLength, lineY),
                    strokeWidth = 0.8.dp.toPx()
                )
            }

            // 绘制字母"k"标识 - 放在横放书本的右下角，与左上装饰球形成对角平衡
            val kSize = bookWidth * 0.5f  // 放大字母"k"
            val kLeft = bookLeft + bookWidth - kSize - 6.dp.toPx()
            val kTop = bookTop + (bookHeight - kSize) / 2 + 4.dp.toPx()  // 向下移动

            // 绘制字母"k"（使用红色渐变）
            val kBrush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFE53E3E), // 红色
                    Color(0xFFC53030), // 深红色
                    Color(0xFF9B2C2C)  // 更深红色
                ),
                start = Offset(kLeft, kTop),
                end = Offset(kLeft + kSize, kTop + kSize)
            )
            
            val strokeWidth = kSize * 0.15f  // 线条粗细为整体大小的15%
            val letterHeight = kSize * 0.8f  // 字母高度

            // 绘制字母"k"的主体结构
            // 左边竖线（圆润的直线）- 向上延伸
            drawLine(
                brush = kBrush,
                start = Offset(kLeft + kSize * 0.2f, kTop - 1.dp.toPx()),  // 稍微向上延伸
                end = Offset(kLeft + kSize * 0.2f, kTop + letterHeight),
                strokeWidth = strokeWidth
            )
            
            // 上半部分斜线（从竖线中间向上）
            drawLine(
                brush = kBrush,
                start = Offset(kLeft + kSize * 0.2f, kTop + letterHeight * 0.45f),
                end = Offset(kLeft + kSize * 0.75f, kTop - 1.dp.toPx()),  // 与竖线顶部对齐
                strokeWidth = strokeWidth
            )
            
            // 下半部分斜线（从竖线中间向下）
            drawLine(
                brush = kBrush,
                start = Offset(kLeft + kSize * 0.2f, kTop + letterHeight * 0.45f),
                end = Offset(kLeft + kSize * 0.65f, kTop + letterHeight),
                strokeWidth = strokeWidth
            )
            
            // 添加圆润的连接点
            drawCircle(
                brush = kBrush,
                radius = strokeWidth * 1.0f,
                center = Offset(kLeft + kSize * 0.2f, kTop + letterHeight * 0.45f)
            )

            // 绘制装饰性元素（醒目的蓝色圆点）
            val leftTopRadius = 13.dp.toPx()  // 左上角装饰再放大一点
            drawCircle(
                color = Color(0xFF9C27B0).copy(alpha = 0.3f), // 紫色圆点，透明度与右下角一样
                radius = leftTopRadius,
                center = Offset(bookLeft + 10.dp.toPx(), bookTop + 11.dp.toPx())  // 更靠近左上角
            )
            
            // 右下角装饰圆形
            val dotRadius2 = 9.dp.toPx()  // 放大装饰点
            drawCircle(
                color = Color(0xFF4CAF50).copy(alpha = 0.3f), // 绿色圆点，进一步降低透明度
                radius = dotRadius2,
                center = Offset(bookLeft + bookWidth - 10.dp.toPx(), bookTop + bookHeight - 10.dp.toPx())  // 右下角
            )
            
            // 底部背景装饰圆形 - 形成正三角形（在书本之后绘制，显示在书本上面）
            val triangleRadius = size.minDimension * 0.18f  // 放大装饰圆形
            
            // 第一个圆形（左上角，带渐变效果，显示在最上层）
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.2f), // 上半部分浅色
                        Color.White.copy(alpha = 0.4f), // 中间过渡
                        Color.White.copy(alpha = 0.6f)  // 下半部分深色
                    ),
                    center = Offset(centerX - size.minDimension * 0.2f - 2.dp.toPx(), centerY - size.minDimension * 0.2f + 2.dp.toPx() - triangleRadius * 0.3f),
                    radius = triangleRadius * 1.2f
                ),
                radius = triangleRadius,
                center = Offset(centerX - size.minDimension * 0.2f - 2.dp.toPx(), centerY - size.minDimension * 0.2f + 2.dp.toPx())
            )
            
            // 第二个圆形（右边，带渐变效果，显示在最上层）
            /*drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.2f), // 上半部分浅色
                        Color.White.copy(alpha = 0.4f), // 中间过渡
                        Color.White.copy(alpha = 0.6f)  // 下半部分深色
                    ),
                    center = Offset(centerX + size.minDimension * 0.2f, centerY - size.minDimension * 0.2f - triangleRadius * 0.3f),
                    radius = triangleRadius * 1.2f
                ),
                radius = triangleRadius,
                center = Offset(centerX + size.minDimension * 0.2f, centerY - size.minDimension * 0.2f)
            )*/
            
            // 第三个圆形（底部，形成正三角形，确保不被书本遮挡）
            /*drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.6f), // 上半部分深色
                        Color.White.copy(alpha = 0.4f), // 中间过渡
                        Color.White.copy(alpha = 0.2f)  // 下半部分透明一些
                    ),
                    center = Offset(centerX, centerY + size.minDimension * 0.25f - triangleRadius * 0.3f),
                    radius = triangleRadius * 1.2f
                ),
                radius = triangleRadius,
                center = Offset(centerX, centerY + size.minDimension * 0.25f)
            )*/
        }
    }
}

@Composable
fun SettingCategory() {
    val context = LocalContext.current
    var showAboutDialog by remember { mutableStateOf(false) }

    val version by remember {
        var packageInfo: PackageInfo? = null
        try {
            packageInfo =
                context.let { context.packageManager?.getPackageInfo(it.packageName, 0) }
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        if (packageInfo != null) {
            mutableStateOf(packageInfo.versionName)
        } else {
            mutableStateOf("")
        }
    }
    Column(
        modifier = Modifier
            .padding(top = 20.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(44.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = "Version",
                style = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp
                ),
                maxLines = 1
            )

            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
            ) {
                Text(
                    text = version!!,
                    style = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp
                    ),
                    maxLines = 1
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // 生成图标按钮
    /*Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xffffffff))
            .clickable {
                IconGenerator.generateIcons(context)
            }
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(44.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = "生成应用图标",
                style = TextStyle(
                    Color(0xff333333),
                    fontSize = 15.sp
                ),
                maxLines = 1
            )

            Text(
                text = "圆形+矩形",
                style = TextStyle(
                    Color(0xff666666),
                    fontSize = 12.sp
                ),
                maxLines = 1
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))*/

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .clickable { showAboutDialog = true }
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(44.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = "About",
                style = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp
                ),
                maxLines = 1
            )
        }
    }

    // About Dialog
    if (showAboutDialog) {
        AboutDialog(
            onDismiss = { showAboutDialog = false }
        )
    }
}

@Composable
fun AboutDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "About KReader",
                style = TextStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 18.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(Res.string.about_content),
                    style = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    ),
                    textAlign = TextAlign.Start
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "确定",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 14.sp
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    )
}

