package com.archko.reader.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.archko.reader.pdf.subsampling.SamplingImageSource
import com.archko.reader.pdf.subsampling.SubSamplingImage
import com.archko.reader.pdf.subsampling.rememberSubSamplingImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import okio.Path.Companion.toPath

/**
 * @author: archko 2025/4/9 :16:25
 */
@Composable
fun TestSubSampling(path: String) {
    val imageSource = remember { SamplingImageSource(path.toPath(true), null) }
    val zoomableState = rememberZoomableState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        SubSamplingImage(
            modifier = Modifier
                .fillMaxSize()
                .zoomable(zoomableState),
            state = rememberSubSamplingImageState(imageSource, zoomableState),
            contentDescription = "",
        )
    }
}