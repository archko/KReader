package com.archko.reader.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.subsampling.SamplingImageSource
import com.archko.reader.pdf.subsampling.SubSamplingImage
import com.archko.reader.pdf.subsampling.rememberSubSamplingImageState
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import okio.Path.Companion.toPath

/**
 * @author: archko 2025/4/9 :16:25
 */
@Composable
fun TestSubSampling(path: String) {
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val imageSource = remember { SamplingImageSource(path) }
    val zoomableState = rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 2f))
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        SubSamplingImage(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged {
                    viewportSize = IntSize(it.width, it.height)
                    println("viewportSize:$viewportSize")
                }
                .zoomable(zoomableState),
            state = rememberSubSamplingImageState(imageSource, zoomableState, viewportSize),
            contentDescription = "",
        )
    }
}