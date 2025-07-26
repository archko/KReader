package com.archko.reader.viewer

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.FileOutputStream

object IconGenerator {

    @Composable
    fun KReaderIcon(size: Int) {
        Canvas(
            modifier = Modifier
                .size(size.dp)
                .clip(RoundedCornerShape(size.dp))
        ) {
            drawKReaderIcon(size.toFloat())
        }
    }

    private fun DrawScope.drawKReaderIcon(size: Float) {
        val centerX = size / 2
        val centerY = size / 2
        val iconSize = size * 0.7f

        // 绘制背景渐变
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF64B5F6),
                    Color(0xFF42A5F5),
                    Color(0xFF1976D2),
                    Color(0xFF1565C0),
                    Color(0xFF0D47A1)
                ),
                center = Offset(centerX - size * 0.1f, centerY - size * 0.1f),
                radius = size * 0.6f
            ),
            radius = size / 2,
            center = Offset(centerX, centerY)
        )

        // 添加光晕效果
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF90CAF9).copy(alpha = 0.3f),
                    Color(0xFF90CAF9).copy(alpha = 0.1f),
                    Color.Transparent
                ),
                center = Offset(centerX + size * 0.2f, centerY + size * 0.2f),
                radius = size * 0.4f
            ),
            radius = size * 0.4f,
            center = Offset(centerX + size * 0.2f, centerY + size * 0.2f)
        )

        // 绘制书本主体（横放）
        val bookWidth = iconSize * 0.85f
        val bookHeight = iconSize * 0.65f
        val bookLeft = centerX - bookWidth / 2
        val bookTop = centerY - bookHeight / 2

        // 书本阴影
        drawRoundRect(
            color = Color(0xFF000000).copy(alpha = 0.1f),
            topLeft = Offset(bookLeft + size * 0.02f, bookTop + size * 0.02f),
            size = Size(bookWidth, bookHeight),
            cornerRadius = CornerRadius(size * 0.06f)
        )

        // 书本主体
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(bookLeft, bookTop),
            size = Size(bookWidth, bookHeight),
            cornerRadius = CornerRadius(size * 0.06f)
        )

        // 绘制书本页面线条
        val lineSpacing = bookHeight / 9
        for (i in 1..7) {
            val lineY = bookTop + lineSpacing * i
            val lineLength = bookWidth * (0.6f + i * 0.03f)
            val lineStart = bookLeft + (bookWidth - lineLength) / 2

            drawLine(
                color = Color(0xFFE8EAF6).copy(alpha = 0.8f),
                start = Offset(lineStart, lineY),
                end = Offset(lineStart + lineLength, lineY),
                strokeWidth = size * 0.008f
            )
        }

        // 绘制字母"k"
        val kSize = bookWidth * 0.5f
        val kLeft = bookLeft + bookWidth - kSize - size * 0.02f
        val kTop = bookTop + (bookHeight - kSize) / 2 + size * 0.04f

        val kBrush = Brush.linearGradient(
            colors = listOf(
                Color(0xFFE53E3E),
                Color(0xFFC53030),
                Color(0xFF9B2C2C)
            ),
            start = Offset(kLeft, kTop),
            end = Offset(kLeft + kSize, kTop + kSize)
        )

        val strokeWidth = kSize * 0.15f
        val letterHeight = kSize * 0.8f

        // 字母"k"的竖线
        drawLine(
            brush = kBrush,
            start = Offset(kLeft + kSize * 0.2f, kTop - size * 0.01f),
            end = Offset(kLeft + kSize * 0.2f, kTop + letterHeight),
            strokeWidth = strokeWidth
        )

        // 上半部分斜线
        drawLine(
            brush = kBrush,
            start = Offset(kLeft + kSize * 0.2f, kTop + letterHeight * 0.45f),
            end = Offset(kLeft + kSize * 0.75f, kTop - size * 0.01f),
            strokeWidth = strokeWidth
        )

        // 下半部分斜线
        drawLine(
            brush = kBrush,
            start = Offset(kLeft + kSize * 0.2f, kTop + letterHeight * 0.45f),
            end = Offset(kLeft + kSize * 0.65f, kTop + letterHeight),
            strokeWidth = strokeWidth
        )

        // 连接点
        drawCircle(
            brush = kBrush,
            radius = strokeWidth * 1.0f,
            center = Offset(kLeft + kSize * 0.2f, kTop + letterHeight * 0.45f)
        )

        // 装饰球
        drawCircle(
            color = Color(0xFF2196F3),
            radius = size * 0.07f,
            center = Offset(bookLeft + size * 0.1f, bookTop + size * 0.1f)
        )

        // 底部装饰
        drawCircle(
            color = Color.White.copy(alpha = 0.3f),
            radius = size * 0.15f,
            center = Offset(centerX - size * 0.2f, centerY - size * 0.2f)
        )
    }

    fun generateIcons(context: Context) {
        // 在后台线程中生成图标
        Thread {
            // 生成PNG图标
            generateIcon(context, 1024, "ic_launcher_1024", isCircular = true)
            generateIcon(context, 512, "ic_launcher_512", isCircular = true)
            generateIcon(context, 192, "ic_launcher_192", isCircular = true)

            generateIcon(context, 1024, "ic_launcher_rect_1024", isCircular = false)
            generateIcon(context, 512, "ic_launcher_rect_512", isCircular = false)
            generateIcon(context, 192, "ic_launcher_rect_192", isCircular = false)

            // 生成自适应图标XML文件
            //generateAdaptiveIconFiles(context)
        }.start()
    }

    private fun generateIcon(context: Context, size: Int, fileName: String, isCircular: Boolean = true) {
        try {
            // 创建位图
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)

            // 直接绘制图标内容
            drawIconToCanvas(canvas, size, isCircular)

            // 保存文件到应用内部存储
            val file = File(context.getExternalFilesDir(null), "$fileName.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            // 显示成功消息
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "图标已生成: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            }
            println("图标已生成: ${file.absolutePath}")

            // 清理资源
            bitmap.recycle()

        } catch (e: Exception) {
            e.printStackTrace()
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "生成图标失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun generateAdaptiveIconFiles(context: Context) {
        try {
            // 生成自适应图标背景XML - 与Canvas完全一致
            val backgroundXml = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    
    <!-- 主背景径向渐变 - 与Canvas中的Brush.radialGradient完全一致 -->
    <defs>
        <radialGradient
            android:type="radial"
            android:gradientRadius="64.8"
            android:centerX="43.2"
            android:centerY="43.2">
            <item android:offset="0" android:color="#64B5F6" />
            <item android:offset="0.25" android:color="#42A5F5" />
            <item android:offset="0.5" android:color="#1976D2" />
            <item android:offset="0.75" android:color="#1565C0" />
            <item android:offset="1" android:color="#0D47A1" />
        </radialGradient>
    </defs>
    
    <!-- 主背景圆形 - 使用渐变填充 -->
    <path
        android:fillColor="#64B5F6"
        android:pathData="M54,54m-54,0a54,54 0,1 1,108 0a54,54 0,1 1,-108 0" />
    
    <!-- 光晕效果 - 右上角，与Canvas一致 -->
    <defs>
        <radialGradient
            android:type="radial"
            android:gradientRadius="43.2"
            android:centerX="75.6"
            android:centerY="75.6">
            <item android:offset="0" android:color="#4D90CAF9" />
            <item android:offset="0.5" android:color="#1A90CAF9" />
            <item android:offset="1" android:color="#00000000" />
        </radialGradient>
    </defs>
    <path
        android:fillColor="#4D90CAF9"
        android:pathData="M75.6,75.6m-43.2,0a43.2,43.2 0,1 1,86.4 0a43.2,43.2 0,1 1,-86.4 0" />
    
    <!-- 底部装饰 - 左下角，与Canvas一致 -->
    <path
        android:fillColor="#4DFFFFFF"
        android:pathData="M32.4,32.4m-16.2,0a16.2,16.2 0,1 1,32.4 0a16.2,16.2 0,1 1,-32.4 0" />
</vector>"""

            // 生成自适应图标前景XML - 与Canvas完全一致
            val foregroundXml = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    
    <!-- 书本阴影 - 与Canvas中的drawRoundRect阴影一致 -->
    <path
        android:fillColor="#1A000000"
        android:pathData="M20.16,35.28 L75.84,35.28 A6.48,6.48 0,0 1,82.32 41.76 L82.32,72 A6.48,6.48 0,0 1,75.84 78.48 L20.16,78.48 A6.48,6.48 0,0 1,13.68 72 L13.68,41.76 A6.48,6.48 0,0 1,20.16 35.28 Z" />
    
    <!-- 书本主体（白色，带圆角）- 与Canvas中的drawRoundRect一致 -->
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M19.08,33.48 L74.88,33.48 A6.48,6.48 0,0 1,81.36 39.96 L81.36,70.2 A6.48,6.48 0,0 1,74.88 76.68 L19.08,76.68 A6.48,6.48 0,0 1,12.6 70.2 L12.6,39.96 A6.48,6.48 0,0 1,19.08 33.48 Z" />
    
    <!-- 书本页面线条 - 与Canvas中的7条线条一致 -->
    <path
        android:fillColor="#CCE8EAF6"
        android:pathData="M26.5,42.5 L67.5,42.5 L67.5,43.3 L26.5,43.3 Z" />
    <path
        android:fillColor="#CCE8EAF6"
        android:pathData="M26.5,47.5 L68.5,47.5 L68.5,48.3 L26.5,48.3 Z" />
    <path
        android:fillColor="#CCE8EAF6"
        android:pathData="M26.5,52.5 L69.5,52.5 L69.5,53.3 L26.5,53.3 Z" />
    <path
        android:fillColor="#CCE8EAF6"
        android:pathData="M26.5,57.5 L70.5,57.5 L70.5,58.3 L26.5,58.3 Z" />
    <path
        android:fillColor="#CCE8EAF6"
        android:pathData="M26.5,62.5 L71.5,62.5 L71.5,63.3 L26.5,63.3 Z" />
    <path
        android:fillColor="#CCE8EAF6"
        android:pathData="M26.5,67.5 L72.5,67.5 L72.5,68.3 L26.5,68.3 Z" />
    <path
        android:fillColor="#CCE8EAF6"
        android:pathData="M26.5,72.5 L73.5,72.5 L73.5,73.3 L26.5,73.3 Z" />
    
    <!-- 字母"k"的竖线 - 与Canvas中的drawLine一致 -->
    <path
        android:fillColor="#E53E3E"
        android:pathData="M58.5,35.5 L58.5,71.5 L60.5,71.5 L60.5,35.5 Z" />
    
    <!-- 字母"k"的上半部分斜线 - 与Canvas中的drawLine一致 -->
    <path
        android:fillColor="#E53E3E"
        android:pathData="M60.5,47.5 L75.5,35.5 L77.5,37.5 L62.5,49.5 Z" />
    
    <!-- 字母"k"的下半部分斜线 - 与Canvas中的drawLine一致 -->
    <path
        android:fillColor="#E53E3E"
        android:pathData="M60.5,59.5 L73.5,71.5 L71.5,73.5 L58.5,61.5 Z" />
    
    <!-- 字母"k"的连接点 - 与Canvas中的drawCircle一致 -->
    <path
        android:fillColor="#E53E3E"
        android:pathData="M58.5,47.5 A2,2 0,0 1,62.5 47.5 A2,2 0,0 1,58.5 51.5 Z" />
    
    <!-- 装饰球（蓝色圆点）- 与Canvas中的drawCircle一致 -->
    <path
        android:fillColor="#2196F3"
        android:pathData="M29.5,43.5 A3.5,3.5 0,0 1,36.5 43.5 A3.5,3.5 0,0 1,29.5 50.5 Z" />
</vector>"""

            // 生成自适应图标配置XML
            val adaptiveIconXml = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background_gradient" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>"""

            // 保存文件
            val backgroundFile = File(context.getExternalFilesDir(null), "ic_launcher_background_gradient.xml")
            val foregroundFile = File(context.getExternalFilesDir(null), "ic_launcher_foreground.xml")
            val adaptiveIconFile = File(context.getExternalFilesDir(null), "ic_launcher_adaptive.xml")
            
            backgroundFile.writeText(backgroundXml)
            foregroundFile.writeText(foregroundXml)
            adaptiveIconFile.writeText(adaptiveIconXml)

            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "自适应图标XML文件已重新生成，与Canvas效果一致", Toast.LENGTH_LONG).show()
            }
            println("自适应图标XML文件已重新生成，与Canvas效果一致")

        } catch (e: Exception) {
            e.printStackTrace()
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "生成自适应图标失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun drawIconToCanvas(canvas: android.graphics.Canvas, size: Int, isCircular: Boolean = true) {
        val centerX = size / 2f
        val centerY = size / 2f
        val iconSize = size * 0.7f

        // 绘制背景
        val backgroundPaint = android.graphics.Paint().apply {
            shader = android.graphics.RadialGradient(
                centerX - size * 0.1f, centerY - size * 0.1f, size * 0.6f,
                intArrayOf(
                    0xFF64B5F6.toInt(),
                    0xFF42A5F5.toInt(),
                    0xFF1976D2.toInt(),
                    0xFF1565C0.toInt(),
                    0xFF0D47A1.toInt()
                ),
                floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f),
                android.graphics.Shader.TileMode.CLAMP
            )
        }

        if (isCircular) {
            // 圆形背景
            canvas.drawCircle(centerX, centerY, size / 2f, backgroundPaint)
        } else {
            // 矩形背景（带圆角）
            val cornerRadius = size * 0.12f // 12%的圆角
            canvas.drawRoundRect(
                0f, 0f, size.toFloat(), size.toFloat(),
                cornerRadius, cornerRadius, backgroundPaint
            )
        }

        // 绘制光晕效果
        val glowPaint = android.graphics.Paint().apply {
            shader = android.graphics.RadialGradient(
                centerX + size * 0.2f, centerY + size * 0.2f, size * 0.4f,
                intArrayOf(
                    0x4D90CAF9.toInt(), // 30% alpha
                    0x1A90CAF9.toInt(), // 10% alpha
                    0x00000000.toInt()  // transparent
                ),
                floatArrayOf(0f, 0.5f, 1f),
                android.graphics.Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(centerX + size * 0.2f, centerY + size * 0.2f, size * 0.4f, glowPaint)

        // 绘制书本主体
        val bookWidth = iconSize * 0.85f
        val bookHeight = iconSize * 0.65f
        val bookLeft = centerX - bookWidth / 2
        val bookTop = centerY - bookHeight / 2

        // 书本阴影
        val shadowPaint = android.graphics.Paint().apply {
            color = 0x1A000000.toInt() // 10% black
        }
        canvas.drawRoundRect(
            bookLeft + size * 0.02f, bookTop + size * 0.02f,
            bookLeft + bookWidth + size * 0.02f, bookTop + bookHeight + size * 0.02f,
            size * 0.06f, size * 0.06f, shadowPaint
        )

        // 书本主体
        val bookPaint = android.graphics.Paint().apply {
            color = 0xFFFFFFFF.toInt() // white
        }
        canvas.drawRoundRect(
            bookLeft, bookTop, bookLeft + bookWidth, bookTop + bookHeight,
            size * 0.06f, size * 0.06f, bookPaint
        )

        // 绘制书本页面线条
        val linePaint = android.graphics.Paint().apply {
            color = 0xCCE8EAF6.toInt() // 80% alpha
            strokeWidth = size * 0.008f
        }
        val lineSpacing = bookHeight / 9
        for (i in 1..7) {
            val lineY = bookTop + lineSpacing * i
            val lineLength = bookWidth * (0.6f + i * 0.03f)
            val lineStart = bookLeft + (bookWidth - lineLength) / 2
            canvas.drawLine(lineStart, lineY, lineStart + lineLength, lineY, linePaint)
        }

        // 绘制字母"k"
        val kSize = bookWidth * 0.5f
        val kLeft = bookLeft + bookWidth - kSize - size * 0.02f
        val kTop = bookTop + (bookHeight - kSize) / 2 + size * 0.04f

        val kPaint = android.graphics.Paint().apply {
            shader = android.graphics.LinearGradient(
                kLeft, kTop, kLeft + kSize, kTop + kSize,
                intArrayOf(
                    0xFFE53E3E.toInt(),
                    0xFFC53030.toInt(),
                    0xFF9B2C2C.toInt()
                ),
                floatArrayOf(0f, 0.5f, 1f),
                android.graphics.Shader.TileMode.CLAMP
            )
            strokeWidth = kSize * 0.15f
            strokeCap = android.graphics.Paint.Cap.ROUND
        }

        val strokeWidth = kSize * 0.15f
        val letterHeight = kSize * 0.8f

        // 字母"k"的竖线
        canvas.drawLine(
            kLeft + kSize * 0.2f, kTop - size * 0.01f,
            kLeft + kSize * 0.2f, kTop + letterHeight,
            kPaint
        )

        // 上半部分斜线
        canvas.drawLine(
            kLeft + kSize * 0.2f, kTop + letterHeight * 0.45f,
            kLeft + kSize * 0.75f, kTop - size * 0.01f,
            kPaint
        )

        // 下半部分斜线
        canvas.drawLine(
            kLeft + kSize * 0.2f, kTop + letterHeight * 0.45f,
            kLeft + kSize * 0.65f, kTop + letterHeight,
            kPaint
        )

        // 连接点
        kPaint.style = android.graphics.Paint.Style.FILL
        canvas.drawCircle(
            kLeft + kSize * 0.2f, kTop + letterHeight * 0.45f,
            strokeWidth * 1.0f, kPaint
        )

        // 装饰球
        val dotPaint = android.graphics.Paint().apply {
            color = 0xFF2196F3.toInt()
        }
        canvas.drawCircle(
            bookLeft + size * 0.1f, bookTop + size * 0.1f,
            size * 0.07f, dotPaint
        )

        // 底部装饰
        val bottomPaint = android.graphics.Paint().apply {
            color = 0x4DFFFFFF.toInt() // 30% white
        }
        canvas.drawCircle(
            centerX - size * 0.2f, centerY - size * 0.2f,
            size * 0.15f, bottomPaint
        )
    }
} 