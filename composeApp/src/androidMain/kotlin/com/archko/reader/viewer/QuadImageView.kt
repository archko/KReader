package com.archko.reader.viewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.util.Dispatcher
import kotlinx.coroutines.withContext
import java.io.FileInputStream

@Composable
fun QuadImageView(imageUrls: List<String>) {
    // 确保我们有四个URLs来分别加载四个图片
    require(imageUrls.size == 4) { "Exactly four image URLs are required." }

    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged {
                viewSize = it
            },
        contentAlignment = Alignment.TopStart
    ) {
        val canvasWidth = viewSize.width
        val canvasHeight = viewSize.width
        // 计算四个区域
        val canvasSize = Size(viewSize.width / 2F, viewSize.height / 2F)
        val rects = listOf(
            Rect(offset = Offset(0f, 0f), size = canvasSize),
            Rect(offset = Offset(canvasWidth / 2f, 0f), size = canvasSize),
            Rect(offset = Offset(0f, canvasHeight / 2f), size = canvasSize),
            Rect(offset = Offset(canvasWidth / 2f, canvasHeight / 2f), size = canvasSize)
        )
        // 分别绘制四个页面
        rects.forEachIndexed { index, rect ->
            Page(
                imageUrl = imageUrls[index],
                rect = rect,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

@Composable
fun Page(imageUrl: String, rect: Rect, modifier: Modifier = Modifier) {
    // 状态：bitmap和加载状态
    val (bitmap, setBitmap) = remember { mutableStateOf<ImageBitmap?>(null) }
    val (loading, setLoading) = remember { mutableStateOf(true) }

    // 加载图片
    LaunchedEffect(key1 = imageUrl) {
        setLoading(true)
        val loadedBitmap = loadPicture(imageUrl)
        setBitmap(loadedBitmap?.asImageBitmap())
        setLoading(false)
    }

    Box(modifier = Modifier) {
        if (bitmap != null) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawImage(
                    bitmap,
                    dstSize = IntSize(rect.width.toInt(), rect.height.toInt()),
                    dstOffset = IntOffset(rect.left.toInt(), rect.top.toInt())
                )
            }
        } else if (!loading) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawRect(
                    color = Color.Red,
                    topLeft = rect.topLeft,
                    size = rect.size
                )
            }
        } else {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawRect(
                    color = Color.LightGray,
                    topLeft = rect.topLeft,
                    size = rect.size
                )
            }
        }
    }
}

// 异步加载图片的方法
private suspend fun loadPicture(imageUrl: String): Bitmap? = withContext(Dispatcher.DECODE) {
    return@withContext try {
        Thread.sleep(50L)
        val inputStream = FileInputStream(imageUrl)
        BitmapFactory.decodeStream(inputStream)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}