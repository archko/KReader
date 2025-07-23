package com.archko.reader.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.component.DocumentView
import com.archko.reader.pdf.decoder.PdfDecoder
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.APage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * @author: archko 2025/7/23 :09:09
 */
@Composable
fun CustomView(path: String) {
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var decoder: ImageDecoder? by remember { mutableStateOf(null) }
    LaunchedEffect(path) {
        withContext(Dispatchers.IO) {
            println("init:$viewportSize, $path")
            val pdfDecoder = if (viewportSize == IntSize.Zero) {
                null
            } else {
                PdfDecoder(File(path))
            }
            if (pdfDecoder != null) {
                pdfDecoder.getSize(viewportSize)
                println("init.size:${pdfDecoder.imageSize.width}-${pdfDecoder.imageSize.height}")
                decoder = pdfDecoder
            }
        }
    }
    DisposableEffect(path) {
        onDispose {
            println("onDispose:$path, $decoder")
            decoder?.close()
        }
    }

    if (null == decoder) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewportSize = it }) {
            Text(
                "Loading",
                modifier = Modifier
            )
        }
    } else {
        fun createList(decoder: ImageDecoder): MutableList<APage> {
            var list = mutableListOf<APage>()
            for (i in 0 until decoder.originalPageSizes.size) {
                val page = decoder.originalPageSizes[i]
                val aPage = APage(i, page.width, page.height, 1f)
                list.add(aPage)
            }
            return list
        }

        var list: MutableList<APage> = remember {
            createList(decoder!!)
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewportSize = it }) {
            DocumentView(
                list,
                decoder!!
            )
        }
    }
}