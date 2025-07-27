package com.archko.reader.viewer

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
                .statusBarsPadding()
                .fillMaxSize(),
            color = Color(0xFFF5F5F5) // 设置为浅灰色背景
        ) {
            Box {
                Column(modifier = Modifier.padding(20.dp)) {
                    Spacer(modifier = Modifier.width(32.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar()
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "KReader",
                            style = TextStyle(
                                color = Color(0xFF1976D2),
                                fontSize = 28.sp
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .align(alignment = Alignment.CenterVertically)
        ) {
            // 创建现代PDF阅读器图标
            Canvas(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(64.dp))
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
                val kLeft = bookLeft + bookWidth - kSize - 2.dp.toPx()  // 更靠右
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
                val dotRadius2 = 7.dp.toPx()  // 再增大装饰点
                drawCircle(
                    color = Color(0xFF2196F3), // 蓝色圆点
                    radius = dotRadius2,
                    center = Offset(bookLeft + 10.dp.toPx(), bookTop + 10.dp.toPx())  // 更靠近左上角
                )
                
                // 恢复底部的圆形装饰
                drawCircle(
                    color = Color.White.copy(alpha = 0.3f),
                    radius = size.minDimension * 0.15f,
                    center = Offset(centerX - size.minDimension * 0.2f, centerY - size.minDimension * 0.2f)
                )
            }
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
            .background(Color(0xffffffff))
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
                    Color(0xff333333),
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
                        Color(0xff333333),
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
            .background(Color(0xffffffff))
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
                    Color(0xff333333),
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
                    color = Color(0xFF1976D2),
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
                        color = Color(0xFF333333),
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
                    containerColor = Color(0xFF1976D2)
                )
            ) {
                Text(
                    text = "确定",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp),
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    )
}

