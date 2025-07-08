package com.archko.reader.pdf.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archko.reader.pdf.state.PdfState
import com.archko.reader.pdf.util.Dispatcher
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn

@Composable
private fun PdfSubPage(
    state: PdfState,
    index: Int,
    width: Int,
    height: Int,
    xOffset: Int,
    yOffset: Int,
    modifier: Modifier = Modifier,
    loadingIconTint: Color = Color.White,
    errorIconTint: Color = Color.Red,
    iconSize: Dp = 40.dp,
    loadingIndicator: @Composable () -> Unit = {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(loadingIconTint)
        ) {
        }
    },
) {
    val cacheKey = "$index-$width-$height-$xOffset-$yOffset"
    val imageState: MutableState<ImageBitmap?> = remember { mutableStateOf(ImageCache.get(cacheKey)) }

    if (imageState.value == null) {
        loadingIndicator()
        DisposableEffect(index, width, height, xOffset, yOffset) {
            val scope = CoroutineScope(SupervisorJob())
            scope.launch {
                snapshotFlow {
                    if (isActive) {
                        return@snapshotFlow state.renderPageRegion(index, width, height, xOffset, yOffset)
                    } else {
                        return@snapshotFlow null
                    }
                }.flowOn(Dispatcher.DECODE)
                    .collectLatest {
                        if (it != null) {
                            ImageCache.put(cacheKey, it)
                            imageState.value = it
                        }
                    }
            }
            onDispose {
                scope.cancel()
            }
        }
    } else {
        Image(
            painter = BitmapPainter(imageState.value!!),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = modifier
                .width(with(LocalDensity.current) { width.toDp() })
                .height(with(LocalDensity.current) { height.toDp() })
        )
    }
}

@Composable
public fun PdfPage(
    state: PdfState,
    index: Int,
    width: Int,
    height: Int,
    modifier: Modifier = Modifier,
    loadingIconTint: Color = Color.White,
    errorIconTint: Color = Color.Red,
    iconSize: Dp = 40.dp,
    loadingIndicator: @Composable () -> Unit = {
        val h: Int
        if (state.pageSizes.isNotEmpty()) {
            val size = state.pageSizes[index]
            val xscale = 1f * width / size.width
            h = (size.height * xscale).toInt()
        } else {
            h = height
        }
        val hdp = with(LocalDensity.current) {
            h.toDp()
        }
        Box(modifier = modifier.fillMaxWidth().height(hdp).background(loadingIconTint)) {
            LoadingView("Page $index / ${state.pageCount}")
        }
    },
    errorIndicator: @Composable () -> Unit = {
        Image(
            painter = BitmapPainter(state.renderPage(index, width, height)),
            contentDescription = null,
            colorFilter = if (errorIconTint == Color.Unspecified) null else ColorFilter.tint(errorIconTint),
            modifier = Modifier.size(iconSize)
        )
    },
    contentScale: ContentScale = ContentScale.Fit
) {
    val h: Int
    if (state.pageSizes.isNotEmpty()) {
        val size = state.pageSizes[index]
        val xscale = 1f * width / size.width
        h = (size.height * xscale).toInt()
    } else {
        h = height
    }

    if (width > 2048 || h > 2048) {
        val subWidth = width / 2
        val subHeight = h / 2

        Column(
            modifier = modifier
                .width(with(LocalDensity.current) { width.toDp() })
                .height(with(LocalDensity.current) { h.toDp() })
        ) {
            Row(
                modifier = Modifier
                    .width(with(LocalDensity.current) { width.toDp() })
                    .height(with(LocalDensity.current) { subHeight.toDp() })
            ) {
                // 左上角子页面
                PdfSubPage(
                    state = state,
                    index = index,
                    width = subWidth,
                    height = subHeight,
                    xOffset = 0,
                    yOffset = 0,
                )
                // 右上角子页面
                PdfSubPage(
                    state = state,
                    index = index,
                    width = subWidth,
                    height = subHeight,
                    xOffset = subWidth,
                    yOffset = 0,
                )
            }
            Row(
                modifier = Modifier
                    .width(with(LocalDensity.current) { width.toDp() })
                    .height(with(LocalDensity.current) { subHeight.toDp() })
            ) {
                // 左下角子页面
                PdfSubPage(
                    state = state,
                    index = index,
                    width = subWidth,
                    height = subHeight,
                    xOffset = 0,
                    yOffset = subHeight,
                )
                // 右下角子页面
                PdfSubPage(
                    state = state,
                    index = index,
                    width = subWidth,
                    height = subHeight,
                    xOffset = subWidth,
                    yOffset = subHeight,
                )
            }
        }
    } else {
        Column(
            modifier = modifier
                .width(with(LocalDensity.current) { width.toDp() })
                .height(with(LocalDensity.current) { h.toDp() })
        ) {
            PdfSubPage(
                state = state,
                index = index,
                width = width,
                height = h,
                xOffset = 0,
                yOffset = 0,
            )
        }
    }
}

@Composable
private fun LoadingView(
    text: String = "Decoding",
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        Text(
            text,
            style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(24.dp))
        LinearProgressIndicator(
            modifier = Modifier
                .height(10.dp)
                .align(alignment = Alignment.CenterHorizontally)
        )
    }
}