package com.archko.reader.viewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.archko.reader.pdf.component.ImageCache
import com.archko.reader.pdf.flinger.FlingConfiguration
import com.archko.reader.pdf.flinger.SplineBasedFloatDecayAnimationSpec
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.FileInputStream
import androidx.compose.ui.graphics.drawscope.translate

/**
 * @author: archko 2025/3/10 :20:09
 */
@Composable
fun DocumentView(list: MutableList<APage>) {
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val pdfState = remember { PdfState(list) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var vZoom by remember { mutableFloatStateOf(1f) }
    val velocityTracker = remember { VelocityTracker() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var flingJob by remember { mutableStateOf<Job?>(null) }

    // 定义背景渐变
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(Color.Green, Color.Red)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged {
                viewSize = it
                pdfState.updateViewSize(it, vZoom)
            }
            .height(pdfState.totalHeight.dp)
            .pointerInput(Unit) {
                forEachGesture {
                    awaitPointerEventScope {
                        var initialZoom = vZoom
                        var startOffset = Offset.Zero
                        var initialDistance = 0f
                        var initialCenter = Offset.Zero

                        // 等待第一个触点
                        val firstDown = awaitFirstDown()
                        var pointerCount: Int

                        do {
                            flingJob?.cancel()
                            flingJob = null
                            val event = awaitPointerEvent()
                            pointerCount = event.changes.size

                            when {
                                // 单指滑动处理
                                pointerCount == 1 -> {
                                    event.changes[0].let { drag ->
                                        val delta = drag.position - drag.previousPosition
                                        velocityTracker.addPosition(
                                            drag.uptimeMillis,
                                            drag.position
                                        )

                                        val scaledWidth = viewSize.width * vZoom
                                        val scaledHeight = pdfState.totalHeight// * vZoom

                                        // 计算最大滚动范围
                                        val maxX =
                                            (scaledWidth - viewSize.width).coerceAtLeast(0f) / 2
                                        val maxY =
                                            (scaledHeight - viewSize.height).coerceAtLeast(0f)

                                        // 更新偏移量
                                        offset = Offset(
                                            (offset.x + delta.x).coerceIn(-maxX, maxX),
                                            (offset.y + delta.y).coerceIn(-maxY, 0f)
                                        )
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        // 计算最终速度
                        val velocity = velocityTracker.calculateVelocity()
                        velocityTracker.resetTracking()

                        // 创建优化后的decay动画spec
                        val decayAnimationSpec = SplineBasedFloatDecayAnimationSpec(
                            density = density,
                            scrollConfiguration = FlingConfiguration.Builder()
                                .scrollViewFriction(0.009f)  // 减小摩擦力，使滑动更流畅
                                // 减小这个值可以增加滚动速度，建议范围 0.01f - 0.02f
                                .numberOfSplinePoints(100)  // 提高采样率
                                // 增加这个值可以使滚动更平滑，但会略微增加计算量，建议范围 100 - 200
                                .splineInflection(0.25f)     // 控制曲线拐点位置
                                // 减小这个值可以使滚动更快减速，建议范围 0.1f - 0.3f
                                .splineStartTension(0.55f)   // 控制曲线起始张力
                                // 增加这个值可以使滚动初速度更快，建议范围 0.5f - 1.0f
                                .splineEndTension(0.7f)       // 控制曲线结束张力
                                // 增加这个值可以使滚动持续时间更长，建议范围 0.8f - 1.2f
                                .build()
                        )

                        flingJob = scope.launch {
                            // 同时处理水平和垂直方向的惯性滑动
                            val scaledWidth = viewSize.width * vZoom
                            val scaledHeight = pdfState.totalHeight// * vZoom
                            val maxX = (scaledWidth - viewSize.width).coerceAtLeast(0f) / 2
                            val maxY = (scaledHeight - viewSize.height).coerceAtLeast(0f)

                            // 创建两个协程同时处理x和y方向的动画
                            launch {
                                if (kotlin.math.abs(velocity.x) > 50f) {  // 添加最小速度阈值
                                    animateDecay(
                                        initialValue = offset.x,
                                        initialVelocity = velocity.x,
                                        animationSpec = decayAnimationSpec
                                    ) { value, _ ->
                                        offset = offset.copy(x = value.coerceIn(-maxX, maxX))
                                    }
                                }
                            }

                            launch {
                                if (kotlin.math.abs(velocity.y) > 50f) {  // 添加最小速度阈值
                                    animateDecay(
                                        initialValue = offset.y,
                                        initialVelocity = velocity.y,
                                        animationSpec = decayAnimationSpec
                                    ) { value, _ ->
                                        offset = offset.copy(y = value.coerceIn(-maxY, 0f))
                                    }
                                }
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.TopStart
    ) {
        Canvas(
            modifier = Modifier.matchParentSize()
        ) {
            translate(left = offset.x, top = offset.y) {
                //只绘制可见区域.
                val visibleRect = Rect(
                    left = -offset.x,
                    top = -offset.y,
                    right = size.width - offset.x,
                    bottom = size.height - offset.y
                )
                drawRect(
                    brush = gradientBrush,
                    topLeft = visibleRect.topLeft,
                    size = visibleRect.size
                )
            }
        }
        pdfState.pages.forEach { page ->
            PdfPage(
                page = page,
                offset = offset,
                size = viewSize
            )
        }
    }
}

private fun isPageVisible(index: Int, rect: Rect, offset: Offset, size: IntSize): Boolean {
    val visibleRect = Rect(
        left = -offset.x,
        top = -offset.y,
        right = size.width - offset.x,
        bottom = size.height - offset.y
    )

    // 检查页面是否与可视区域相交
    val flag = rect.intersectsWith(visibleRect)
    println("isVisible.index:$index, $flag, $visibleRect, $rect")
    return flag
}

@Composable
fun PdfPage(page: Page, offset: Offset, size: IntSize) {
    val (bitmap, setBitmap) = remember { mutableStateOf<ImageBitmap?>(null) }
    val (loading, setLoading) = remember { mutableStateOf(true) }
    val isVisible = isPageVisible(page.aPage.index, page.bounds, offset, size)

    LaunchedEffect(isVisible) {
        if (isVisible && bitmap == null) {
            setLoading(true)
            val loadedBitmap = loadPicture("/storage/emulated/0/DCIM/test.png")
            println("bitmap:$loadedBitmap")
            setBitmap(loadedBitmap)
            setLoading(false)
        } else if (!isVisible) {
            setBitmap(null)
            setLoading(true)
        }
    }

    val width = with(LocalDensity.current) { page.bounds.width.toDp() }
    val height = with(LocalDensity.current) { page.bounds.height.toDp() }
    Box(
        Modifier
            .width(width)
            .height(height)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            if (bitmap != null) {
                //drawImage(bitmap)
            } else if (loading) {
                //drawRect(color = Color.LightGray)
            } else {
                //drawRect(color = Color.Red)
            }

            val drawRect = Rect(
                page.bounds.left + offset.x,
                page.bounds.top + offset.y,
                page.bounds.right + offset.x,
                page.bounds.bottom + offset.y
            )
            drawContext.canvas.nativeCanvas.drawText(
                page.aPage.index.toString(),
                drawRect.topLeft.x + drawRect.size.width / 2,
                drawRect.topLeft.y + drawRect.size.height / 2,
                android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 60f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
            )
        }
    }
}

// 异步加载图片的方法
private suspend fun loadPicture(imageUrl: String): ImageBitmap? {
    return try {
        val imageBitmap = ImageCache.get(imageUrl)
        if (null != imageBitmap) {
            return imageBitmap
        }
        kotlinx.coroutines.delay(50L)
        val inputStream = FileInputStream(imageUrl)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        ImageCache.put(imageUrl, bitmap.asImageBitmap())
        bitmap.asImageBitmap()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}