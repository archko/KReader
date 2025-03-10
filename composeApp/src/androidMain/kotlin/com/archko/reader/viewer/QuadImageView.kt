package com.archko.reader.viewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream

@Composable
fun QuadImageView(imageUrls: List<String>) {
    // 确保我们有四个URLs来分别加载四个图片
    require(imageUrls.size == 4) { "Exactly four image URLs are required." }

    // 使用remember保存图片状态
    val bitmaps = remember { mutableStateListOf<ImageBitmap?>(null, null, null, null) }

    // 对每个URL启动协程进行异步加载
    imageUrls.forEachIndexed { index, url ->
        LaunchedEffect(url) {
            val bitmap = loadPicture(url)
            bitmaps[index] = bitmap?.asImageBitmap()
        }
    }

    // Canvas 绘制逻辑
    Canvas(modifier = Modifier.size(400.dp)) { // 设置Canvas大小
        val canvasWidth = size.width
        val canvasHeight = size.height

        // 分割成四部分
        val rects = listOf(
            Rect(offset = Offset(0f, 0f), size = size / 2F),
            Rect(offset = Offset(canvasWidth / 2, 0f), size = size / 2F),
            Rect(offset = Offset(0f, canvasHeight / 2), size = size / 2F),
            Rect(offset = Offset(canvasWidth / 2, canvasHeight / 2), size = size / 2F)
        )

        // 绘制每个bitmap到对应的rect或者占位符
        bitmaps.forEachIndexed { index, imageBitmap ->
            if (imageBitmap != null) {
                drawImage(
                    imageBitmap,
                    dstSize = IntSize(rects[index].width.toInt(), rects[index].height.toInt()),
                    dstOffset = IntOffset(rects[index].left.toInt(), rects[index].top.toInt())
                )
            } else {
                // 加载中显示的占位框
                drawRect(
                    color = Color.LightGray,
                    topLeft = rects[index].topLeft,
                    size = rects[index].size
                )
            }
        }
    }
}

// 异步加载图片的方法
private suspend fun loadPicture(imageUrl: String): Bitmap? = withContext(Dispatchers.IO) {
    return@withContext try {
        Thread.sleep(1000L)
        val inputStream = FileInputStream(imageUrl)
        BitmapFactory.decodeStream(inputStream)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}