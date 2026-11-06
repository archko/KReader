package com.archko.reader.viewer.dialog

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.archko.reader.pdf.component.DecoderAdapter
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.APage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kreader.composeapp.generated.resources.Res
import kreader.composeapp.generated.resources.cancel
import kreader.composeapp.generated.resources.confirm
import kreader.composeapp.generated.resources.crop
import org.jetbrains.compose.resources.stringResource

@Composable
fun ManualCropDialog(
    page: APage,
    decoder: ImageDecoder,
    initialCrop: Rect?,
    onConfirm: (Rect) -> Unit,
    onDismiss: () -> Unit,
) {
    var preview by remember(page.index) { mutableStateOf<ImageBitmap?>(null) }
    var cropRect by remember(page.index) {
        mutableStateOf(normalizeCrop(initialCrop, page))
    }

    LaunchedEffect(page.index, page.width, page.height) {
        preview = withContext(Dispatchers.IO) {
            try {
                val (width, height) = DecoderAdapter.calculateThumbnailSize(
                    page.width,
                    page.height,
                    1200
                )
                synchronized(decoder) {
                    decoder.renderPage(
                        aPage = page,
                        viewSize = IntSize.Zero,
                        outWidth = width,
                        outHeight = height,
                        crop = false
                    )
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.9f)
                .padding(vertical = 24.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = stringResource(Res.string.crop),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val aspect = page.width.toFloat() / page.height.toFloat()
                    val width = if (maxWidth / aspect <= maxHeight) {
                        maxWidth
                    } else {
                        maxHeight * aspect
                    }
                    val height = width / aspect
                    Box(
                        modifier = Modifier
                            .size(width, height)
                            .background(Color.Black), contentAlignment = Alignment.Center
                    ) {
                        CropPreview(
                            modifier = Modifier.fillMaxSize(),
                            image = preview,
                            cropRect = cropRect,
                            onRectChange = { cropRect = it }
                        )
                        if (preview == null) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                }

                val left = (cropRect.left * page.width).toInt()
                val top = (cropRect.top * page.height).toInt()
                val right = (page.width - cropRect.right * page.width).toInt()
                val bottom = (page.height - cropRect.bottom * page.height).toInt()
                Text(
                    text = "左 $left  上 $top  右 $right  下 $bottom",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.cancel))
                    }
                    TextButton(
                        onClick = {
                            onConfirm(
                                Rect(
                                    cropRect.left * page.width,
                                    cropRect.top * page.height,
                                    cropRect.right * page.width,
                                    cropRect.bottom * page.height
                                )
                            )
                        }
                    ) {
                        Text(stringResource(Res.string.confirm))
                    }
                }
            }
        }
    }
}

@Composable
private fun CropPreview(
    modifier: Modifier,
    image: ImageBitmap?,
    cropRect: Rect,
    onRectChange: (Rect) -> Unit,
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val currentRect by rememberUpdatedState(cropRect)
    val currentOnRectChange by rememberUpdatedState(onRectChange)
    var dragMode by remember { mutableStateOf(DRAG_NONE) }

    Box(
        modifier = modifier
            .background(Color(0xFF303030))
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                val handle = 28.dp.toPx()
                detectDragGestures(
                    onDragStart = { position ->
                        if (canvasSize.width > 0 && canvasSize.height > 0) {
                            val displayed = currentRect.toDisplay(canvasSize)
                            dragMode = when {
                                isNear(position, displayed.left, displayed.top, handle) -> DRAG_TOP_LEFT
                                isNear(position, displayed.right, displayed.top, handle) -> DRAG_TOP_RIGHT
                                isNear(position, displayed.left, displayed.bottom, handle) -> DRAG_BOTTOM_LEFT
                                isNear(position, displayed.right, displayed.bottom, handle) -> DRAG_BOTTOM_RIGHT
                                isNearEdge(position, displayed, handle, EDGE_TOP) -> DRAG_TOP
                                isNearEdge(position, displayed, handle, EDGE_BOTTOM) -> DRAG_BOTTOM
                                isNearEdge(position, displayed, handle, EDGE_LEFT) -> DRAG_LEFT
                                isNearEdge(position, displayed, handle, EDGE_RIGHT) -> DRAG_RIGHT
                                displayed.contains(position) -> DRAG_MOVE
                                else -> DRAG_NONE
                            }
                        }
                    },
                    onDragEnd = { dragMode = DRAG_NONE },
                    onDragCancel = { dragMode = DRAG_NONE },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (dragMode == DRAG_NONE ||
                            canvasSize.width <= 0 || canvasSize.height <= 0
                        ) {
                            return@detectDragGestures
                        }
                        val rect = currentRect
                        val dx = dragAmount.x / canvasSize.width
                        val dy = dragAmount.y / canvasSize.height
                        val minSize = 0.05f
                        val updated = when (dragMode) {
                            DRAG_MOVE -> {
                                val moveX = dx.coerceIn(-rect.left, 1f - rect.right)
                                val moveY = dy.coerceIn(-rect.top, 1f - rect.bottom)
                                Rect(
                                    rect.left + moveX,
                                    rect.top + moveY,
                                    rect.right + moveX,
                                    rect.bottom + moveY
                                )
                            }

                            DRAG_TOP -> Rect(
                                rect.left,
                                (rect.top + dy).coerceIn(0f, rect.bottom - minSize),
                                rect.right,
                                rect.bottom
                            )

                            DRAG_BOTTOM -> Rect(
                                rect.left,
                                rect.top,
                                rect.right,
                                (rect.bottom + dy).coerceIn(rect.top + minSize, 1f)
                            )

                            DRAG_LEFT -> Rect(
                                (rect.left + dx).coerceIn(0f, rect.right - minSize),
                                rect.top,
                                rect.right,
                                rect.bottom
                            )

                            DRAG_RIGHT -> Rect(
                                rect.left,
                                rect.top,
                                (rect.right + dx).coerceIn(rect.left + minSize, 1f),
                                rect.bottom
                            )

                            DRAG_TOP_LEFT -> Rect(
                                (rect.left + dx).coerceIn(0f, rect.right - minSize),
                                (rect.top + dy).coerceIn(0f, rect.bottom - minSize),
                                rect.right,
                                rect.bottom
                            )

                            DRAG_TOP_RIGHT -> Rect(
                                rect.left,
                                (rect.top + dy).coerceIn(0f, rect.bottom - minSize),
                                (rect.right + dx).coerceIn(rect.left + minSize, 1f),
                                rect.bottom
                            )

                            DRAG_BOTTOM_LEFT -> Rect(
                                (rect.left + dx).coerceIn(0f, rect.right - minSize),
                                rect.top,
                                rect.right,
                                (rect.bottom + dy).coerceIn(rect.top + minSize, 1f)
                            )

                            else -> Rect(
                                rect.left,
                                rect.top,
                                (rect.right + dx).coerceIn(rect.left + minSize, 1f),
                                (rect.bottom + dy).coerceIn(rect.top + minSize, 1f)
                            )
                        }
                        currentOnRectChange(updated)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (image != null) {
                drawImage(
                    image = image,
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt())
                )
            }

            val rect = cropRect.toDisplay(IntSize(size.width.toInt(), size.height.toInt()))
            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset.Zero,
                size = Size(size.width, rect.top)
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(0f, rect.bottom),
                size = Size(size.width, size.height - rect.bottom)
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(0f, rect.top),
                size = Size(rect.left, rect.height)
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(rect.right, rect.top),
                size = Size(size.width - rect.right, rect.height)
            )
            drawRect(
                color = Color.Red,
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width, rect.height),
                style = Stroke(width = 3.dp.toPx())
            )

            val cornerRadius = 7.dp.toPx()
            val edgeRadius = 5.dp.toPx()
            listOf(
                Offset(rect.left, rect.top),
                Offset(rect.right, rect.top),
                Offset(rect.left, rect.bottom),
                Offset(rect.right, rect.bottom)
            ).forEach { point ->
                drawCircle(Color.Red, radius = cornerRadius, center = point)
            }
            listOf(
                Offset(rect.center.x, rect.top),
                Offset(rect.center.x, rect.bottom),
                Offset(rect.left, rect.center.y),
                Offset(rect.right, rect.center.y)
            ).forEach { point ->
                drawCircle(Color.Red, radius = edgeRadius, center = point)
            }
        }
    }
}

private fun isNearEdge(
    position: Offset,
    rect: Rect,
    radius: Float,
    edge: Int,
): Boolean {
    val x = position.x
    val y = position.y
    return when (edge) {
        EDGE_TOP -> {
            x in (rect.left - radius)..(rect.right + radius) && kotlin.math.abs(y - rect.top) <= radius
        }

        EDGE_BOTTOM -> {
            x in (rect.left - radius)..(rect.right + radius) && kotlin.math.abs(y - rect.bottom) <= radius
        }

        EDGE_LEFT -> {
            y in (rect.top - radius)..(rect.bottom + radius) && kotlin.math.abs(x - rect.left) <= radius
        }

        else -> {
            y in (rect.top - radius)..(rect.bottom + radius) && kotlin.math.abs(x - rect.right) <= radius
        }
    }
}

private fun normalizeCrop(rect: Rect?, page: APage): Rect {
    val width = page.width.toFloat()
    val height = page.height.toFloat()
    if (rect == null || width <= 0f || height <= 0f) {
        return Rect(0f, 0f, 1f, 1f)
    }
    val left = (rect.left / width).coerceIn(0f, 1f)
    val top = (rect.top / height).coerceIn(0f, 1f)
    val right = (rect.right / width).coerceIn(left, 1f)
    val bottom = (rect.bottom / height).coerceIn(top, 1f)
    return Rect(left, top, right, bottom)
}

private fun Rect.toDisplay(size: IntSize): Rect {
    return Rect(
        left * size.width.toFloat(),
        top * size.height.toFloat(),
        right * size.width.toFloat(),
        bottom * size.height.toFloat()
    )
}

private fun isNear(position: Offset, x: Float, y: Float, radius: Float): Boolean {
    return (position.x - x) * (position.x - x) + (position.y - y) * (position.y - y) <= radius * radius
}

private const val DRAG_NONE = 0
private const val DRAG_MOVE = 1
private const val DRAG_TOP = 2
private const val DRAG_BOTTOM = 3
private const val DRAG_LEFT = 4
private const val DRAG_RIGHT = 5
private const val DRAG_TOP_LEFT = 6
private const val DRAG_TOP_RIGHT = 7
private const val DRAG_BOTTOM_LEFT = 8
private const val DRAG_BOTTOM_RIGHT = 9

private const val EDGE_TOP = 0
private const val EDGE_BOTTOM = 1
private const val EDGE_LEFT = 2
private const val EDGE_RIGHT = 3
