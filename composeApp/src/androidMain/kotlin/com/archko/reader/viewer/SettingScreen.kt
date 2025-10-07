package com.archko.reader.viewer

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.archko.reader.pdf.PdfApp
import com.archko.reader.pdf.util.IntentFile
import com.archko.reader.viewer.utils.PDFCreaterHelper
import com.mohamedrejeb.calf.picker.FilePickerFileType
import com.mohamedrejeb.calf.picker.FilePickerSelectionMode
import com.mohamedrejeb.calf.picker.rememberFilePickerLauncher
import kotlinx.coroutines.launch
import kreader.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
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
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(Res.string.app_author),
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
                    center = Offset(
                        centerX - size.minDimension * 0.2f - 2.dp.toPx(),
                        centerY - size.minDimension * 0.2f + 2.dp.toPx() - triangleRadius * 0.3f
                    ),
                    radius = triangleRadius * 1.2f
                ),
                radius = triangleRadius,
                center = Offset(
                    centerX - size.minDimension * 0.2f - 2.dp.toPx(),
                    centerY - size.minDimension * 0.2f + 2.dp.toPx()
                )
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
                text = stringResource(Res.string.version),
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

    var showPdfEncryptDialog by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { showPdfEncryptDialog = true }
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
                text = "加密/解密PDF",
                style = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp
                ),
                maxLines = 1
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { showAboutDialog = true }
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
                text = stringResource(Res.string.about),
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

    // PDF加密/解密 Dialog
    if (showPdfEncryptDialog) {
        PdfEncryptDialog(
            onDismiss = { showPdfEncryptDialog = false }
        )
    }
}

@Composable
fun PdfEncryptDialog(
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var pdfFilePath by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_back),
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = stringResource(Res.string.encrypt_decrypt_title),
                    style = TextStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                val pickerLauncher = rememberFilePickerLauncher(
                    type = FilePickerFileType.Pdf,
                    selectionMode = FilePickerSelectionMode.Single
                ) { files ->
                    scope.launch {
                        if (files.isNotEmpty()) {
                            val file = files.first()
                            val path = IntentFile.getPath(PdfApp.app!!, file.uri) ?: file.uri.toString()
                            pdfFilePath = path
                        }
                    }
                }

                Button(
                    onClick = { pickerLauncher.launch() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(Res.string.encrypt_decrypt_add))
                }

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    if (pdfFilePath.isNotEmpty()) {
                        val fileObj = java.io.File(pdfFilePath)
                        val fileName = fileObj.name
                        val fileSize = if (fileObj.exists()) {
                            val sizeInBytes = fileObj.length()
                            val sizeInMB = sizeInBytes / (1024.0 * 1024.0)
                            String.format("%.1fMB", sizeInMB)
                        } else {
                            "0MB"
                        }
                        val lastModified = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                            .format(java.util.Date(fileObj.lastModified()))

                        Text(
                            text = stringResource(Res.string.encrypt_decrypt_file_name).format(fileName) + "\n" +
                                    stringResource(Res.string.encrypt_decrypt_file_size).format(fileSize) + "\n" +
                                    stringResource(Res.string.encrypt_decrypt_file_path).format(pdfFilePath) + "\n" +
                                    stringResource(Res.string.encrypt_decrypt_modification_time).format(lastModified),
                            modifier = Modifier.padding(8.dp),
                            style = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(Res.string.encrypt_decrypt_encrypt)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(stringResource(Res.string.encrypt_decrypt_decrypt)) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                val passwordLabel = stringResource(Res.string.encrypt_decrypt_input_pwd)

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(passwordLabel) },
                    visualTransformation = if (isPasswordVisible) {
                        androidx.compose.ui.text.input.VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(
                            onClick = { isPasswordVisible = !isPasswordVisible }
                        ) {
                            Icon(
                                painter = if (isPasswordVisible) {
                                    painterResource(Res.drawable.ic_visibility_off)
                                } else {
                                    painterResource(Res.drawable.ic_visibility)
                                },
                                contentDescription = if (isPasswordVisible) "隐藏密码" else "显示密码",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    if (selectedTab == 0) {
                        Button(
                            onClick = {
                                if (pdfFilePath.isNotEmpty() && (password.isNotEmpty())) {
                                    isLoading = true
                                    scope.launch {
                                        try {
                                            val outputPath = pdfFilePath.replace(".pdf", "_encrypted.pdf")
                                            val success = PDFCreaterHelper.encryptPDF(
                                                pdfFilePath,
                                                outputPath,
                                                password,
                                                password
                                            )

                                            if (success) {
                                                android.widget.Toast.makeText(
                                                    context,
                                                    getString(Res.string.encrypt_decrypt_encrypt_success)
                                                        .format(
                                                            outputPath
                                                        ),
                                                    android.widget.Toast.LENGTH_LONG
                                                ).show()
                                            } else {
                                                android.widget.Toast.makeText(
                                                    context,
                                                    getString(Res.string.encrypt_decrypt_encrypt_failed),
                                                    android.widget.Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(
                                                context,
                                                getString(Res.string.encrypt_decrypt_encrypt_failed),
                                                android.widget.Toast.LENGTH_LONG
                                            ).show()
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                } else {
                                    scope.launch {
                                        android.widget.Toast.makeText(
                                            context,
                                            getString(Res.string.encrypt_decrypt_input_pwd),
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = pdfFilePath.isNotEmpty() && password.isNotEmpty()
                        ) {
                            Text(stringResource(Res.string.encrypt_decrypt_encrypt))
                        }
                    } else {
                        Button(
                            onClick = {
                                if (pdfFilePath.isNotEmpty() && password.isNotEmpty()) {
                                    isLoading = true
                                    scope.launch {
                                        try {
                                            val outputPath = pdfFilePath.replace(".pdf", "_decrypted.pdf")
                                            val success = PDFCreaterHelper.decryptPDF(
                                                pdfFilePath,
                                                outputPath,
                                                password
                                            )

                                            if (success) {
                                                android.widget.Toast.makeText(
                                                    context,
                                                    getString(Res.string.encrypt_decrypt_decrypt_success)
                                                        .format(
                                                            outputPath
                                                        ),
                                                    android.widget.Toast.LENGTH_LONG
                                                ).show()
                                            } else {
                                                android.widget.Toast.makeText(
                                                    context,
                                                    getString(Res.string.encrypt_decrypt_decrypt_failed),
                                                    android.widget.Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(
                                                context,
                                                getString(Res.string.encrypt_decrypt_decrypt_failed),
                                                android.widget.Toast.LENGTH_LONG
                                            ).show()
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                } else {
                                    scope.launch {
                                        android.widget.Toast.makeText(
                                            context,
                                            getString(Res.string.encrypt_decrypt_input_pwd),
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = pdfFilePath.isNotEmpty() && password.isNotEmpty()
                        ) {
                            Text(stringResource(Res.string.encrypt_decrypt_decrypt))
                        }
                    }
                }
            }
        },
        confirmButton = {
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    )
}

@Composable
fun AboutDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_back),
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = stringResource(Res.string.about_kreader),
                    style = TextStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(Res.string.support_format),
                    style = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        lineHeight = 20.sp
                    ),
                    textAlign = TextAlign.Start
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.about_content),
                    style = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        lineHeight = 20.sp
                    ),
                    textAlign = TextAlign.Start
                )
            }
        },
        confirmButton = {
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    )
}