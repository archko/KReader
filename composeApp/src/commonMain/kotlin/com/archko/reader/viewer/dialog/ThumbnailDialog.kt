package com.archko.reader.viewer.dialog

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.archko.reader.pdf.cache.ImageCache
import com.archko.reader.pdf.component.DecodeCallback
import com.archko.reader.pdf.component.DecodeService
import com.archko.reader.pdf.component.DecodeTask
import com.archko.reader.pdf.component.DecoderAdapter
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.APage
import kreader.composeapp.generated.resources.Res
import kreader.composeapp.generated.resources.ic_back
import kreader.composeapp.generated.resources.thumb_dialog_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * @author: archko 2026/2/6 :6:17
 */
@Composable
fun ThumbnailDialog(
    currentPage: Int,
    list: List<APage>,
    decoder: ImageDecoder,
    onPageClick: (Int) -> Unit,
    onDismiss: () -> Unit
) {

    val decodeService = remember(decoder) {
        DecodeService(DecoderAdapter(decoder, IntSize.Zero) { false })
    }

    val decodeScope: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ThumbDecoder-Dispatcher").apply { isDaemon = true }
    }

    DisposableEffect(decodeService) {
        onDispose {
            decodeService.shutdown()
        }
    }

    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = currentPage.coerceAtLeast(0)
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxSize(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, top = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_back),
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = stringResource(Res.string.thumb_dialog_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxSize(),
                    state = gridState,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(
                        list, key = { index, _ -> index },
                    ) { index, page ->
                        ThumbnailItem(
                            index = index,
                            aPage = page,
                            isSelected = index == currentPage,
                            onClick = { onPageClick(index) },
                            decodeService = decodeService,
                            decodeScope = decodeScope
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThumbnailItem(
    index: Int,
    aPage: APage,
    isSelected: Boolean,
    onClick: () -> Unit,
    decodeService: DecodeService,
    decodeScope: ExecutorService
) {
    val (thumbWidth, thumbHeight) = DecoderAdapter.calculateThumbnailSize(
        aPage.width,
        aPage.height,
        baseSize = 240
    )

    val cacheKey = "thumb-${index}-${thumbWidth}x${thumbHeight}"
    val imageState = remember { mutableStateOf<Painter?>(null) }
    val isLoading = remember { mutableStateOf(true) }
    val job = remember { mutableStateOf<Future<*>?>(null) }
    val isDisposed = remember { mutableStateOf(false) }

    val itemModifier = Modifier
        .fillMaxWidth()
        .height(120.dp)
        .clickable(onClick = onClick)
        .then(
            if (isSelected) {
                Modifier.border(width = 2.dp, Color.Red)
            } else {
                Modifier
            }
        )

    Box(
        modifier = itemModifier,
    ) {
        if (imageState.value != null) {
            Image(
                painter = imageState.value!!,
                contentDescription = "页面 ${index + 1}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(32.dp)
                    .align(Alignment.Center)
            )
        }

        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .background(Color.Black.copy(alpha = 0.20f), RoundedCornerShape(2.dp))
                .padding(horizontal = 4.dp)
                .wrapContentSize()
        )
    }

    DisposableEffect(index, thumbWidth, thumbHeight) {
        job.value?.cancel(true)
        job.value = null

        val cachedState = ImageCache.acquirePage(cacheKey)
        if (cachedState != null) {
            imageState.value = BitmapPainter(cachedState.bitmap)
            isLoading.value = false
            return@DisposableEffect onDispose {
                cachedState.let { ImageCache.releasePage(it) }
                isLoading.value = false
            }
        }

        val loadJob = decodeScope.submit {
            val decodeTask = DecodeTask(
                type = DecodeTask.TaskType.PAGE,
                pageIndex = index,
                decodeKey = cacheKey,
                aPage = aPage,
                zoom = 1f,
                Rect(0f, 0f, 1f, 1f),
                width = thumbWidth,
                height = thumbHeight,
                crop = false,
                callback = object : DecodeCallback {
                    override fun onDecodeComplete(
                        bitmap: ImageBitmap?,
                        isThumb: Boolean,
                        error: Throwable?
                    ) {
                        if (bitmap != null) {
                            val newState = ImageCache.putPage(cacheKey, bitmap)
                            imageState.value = BitmapPainter(newState.bitmap)
                            isLoading.value = false
                        } else {
                            if (error != null) {
                                println("Thumbnail decode error: ${error.message}")
                            }
                            isLoading.value = false
                        }
                    }

                    override fun shouldRender(pageNumber: Int, isFullPage: Boolean): Boolean {
                        return !isDisposed.value
                    }

                    override fun onFinish(pageNumber: Int) {
                        isLoading.value = false
                    }
                }
            )

            decodeService.submitTask(decodeTask)
        }

        job.value = loadJob

        onDispose {
            job.value?.cancel(true)
            job.value = null
            isDisposed.value = true

            cachedState?.let { ImageCache.releasePage(it) }
            isLoading.value = false
        }
    }
}
