package com.archko.reader.viewer.dialog

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.archko.reader.pdf.cache.ImageCache
import com.archko.reader.pdf.component.DecoderAdapter
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.APage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kreader.composeapp.generated.resources.Res
import kreader.composeapp.generated.resources.ic_back
import org.jetbrains.compose.resources.painterResource

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
    val lazyListState = rememberLazyListState(
        initialFirstVisibleItemIndex = currentPage.coerceAtLeast(0)
    )

    val singleThreadScope = remember {
        CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    DisposableEffect(Unit) {
        onDispose {
            singleThreadScope.cancel()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(180.dp)
                .fillMaxHeight(),
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
                        text = "缩略图列表",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize(),
                ) {
                    itemsIndexed(
                        list, key = { index, _ -> index },
                    ) { index, page ->
                        val isCurrentPage = index == currentPage
                        ThumbnailItem(
                            index = index,
                            aPage = page,
                            decoder = decoder,
                            isSelected = isCurrentPage,
                            onClick = {
                                onPageClick(index)
                            },
                            scope = singleThreadScope
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
    decoder: ImageDecoder,
    isSelected: Boolean,
    onClick: () -> Unit,
    scope: CoroutineScope
) {
    val (thumbWidth, thumbHeight) = DecoderAdapter.calculateThumbnailSize(
        aPage.width,
        aPage.height,
        baseSize = 180
    )
    val thumbHeightDp = thumbHeight.dp

    val cacheKey = "thumb-${index}-${thumbWidth}x${thumbHeight}"
    val imageState = remember { mutableStateOf<Painter?>(null) }
    val isLoading = remember { mutableStateOf(true) }

    val itemModifier = Modifier
        .fillMaxWidth()
        .height(thumbHeightDp)
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
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(thumbHeightDp)
                    .background(Color.White)
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp)
            )
        }

        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(horizontal = 4.dp, vertical = 1.dp)
                .background(Color.Black.copy(alpha = 0.20f), RoundedCornerShape(4.dp))
        )
    }

    DisposableEffect(index, thumbWidth, thumbHeight) {
        val cachedState = ImageCache.acquirePage(cacheKey)
        if (cachedState != null) {
            imageState.value = BitmapPainter(cachedState.bitmap)
            isLoading.value = false
        } else {
            scope.launch {
                if (isActive) {
                    val bitmap = decoder.renderPage(
                        aPage,
                        IntSize.Zero,
                        thumbWidth,
                        thumbHeight,
                        false
                    )
                    val newState = ImageCache.putPage(cacheKey, bitmap)
                    imageState.value = BitmapPainter(newState.bitmap)
                    isLoading.value = false
                }
            }
        }

        onDispose {
            cachedState?.let { ImageCache.releasePage(it) }
            isLoading.value = false
        }
    }
}
