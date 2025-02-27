package com.archko.reader.pdf.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archko.reader.pdf.state.PdfState
import com.archko.reader.pdf.util.Dispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
            println("PdfPage: image>height:$h, view.w-h:$width-$height, page:${size.width}-${size.height}")
        } else {
            h = height
        }
        val hdp = with(LocalDensity.current) {
            h.toDp()
        }
        Box(modifier = modifier.fillMaxWidth().height(hdp).background(loadingIconTint)) {
            LoadingView("Page $index / ${state.pageCount}")
            /*Text(
                modifier = Modifier.align(Alignment.Center),
                color = Color.Black,
                fontSize = 30.sp,
                text = "Page $index"
            )*/
        }
    },
    errorIndicator: @Composable () -> Unit = {
        Image(
            painter = state.renderPage(index, width, height),
            contentDescription = null,
            colorFilter = if (errorIconTint ==
                Color.Unspecified
            ) {
                null
            } else {
                ColorFilter.tint(errorIconTint)
            },
            modifier = Modifier.size(iconSize)
        )
    },
    contentScale: ContentScale = ContentScale.Fit
) {
    //val loadState = if (state is RemotePdfState) state.loadState else LoadState.Success
    val cacheKey = "$index-$width-$height"
    val imageState: MutableState<Painter?> = remember { mutableStateOf(ImageCache.get(cacheKey)) }
    if (imageState.value == null) {
        loadingIndicator()

        DisposableEffect(index, width, height) {
            val scope = CoroutineScope(SupervisorJob())
            scope.launch {
                snapshotFlow {
                    if (isActive) {
                        return@snapshotFlow state.renderPage(index, width, height)
                    } else {
                        return@snapshotFlow null
                    }
                }.flowOn(Dispatcher.DECODE)
                    .collectLatest {
                        if (it != null) {
                            ImageCache.put(cacheKey, it)
                        }
                        imageState.value = it
                    }
            }
            onDispose {
                scope.cancel()
                //ImageCache.remove(cacheKey)
            }
        }
    } else {
        Image(
            painter = imageState.value!!,
            contentDescription = null,
            contentScale = ContentScale.FillWidth
        )
    }

    /*when (loadState) {
        LoadState.Success -> Image(
            modifier = modifier.background(Color.White),
            painter = imageState.value!!,//state.renderPage(index, width, height),
            contentDescription = null,
            contentScale = contentScale
        )

        LoadState.Loading -> loadingIndicator()

        LoadState.Error -> loadingIndicator()
    }*/
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