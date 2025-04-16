package ovh.plrapps.mapcompose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.subsampling.PdfDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ovh.plrapps.mapcompose.api.addLayer
import ovh.plrapps.mapcompose.api.shouldLoopScale
import ovh.plrapps.mapcompose.core.TileStreamProvider
import ovh.plrapps.mapcompose.ui.layout.ZoomPanRotate
import ovh.plrapps.mapcompose.ui.state.MapState
import ovh.plrapps.mapcompose.ui.view.TileCanvas
import java.io.File

@Composable
fun TestUI(path: String) {
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    var decoder: PdfDecoder? by remember { mutableStateOf(null) }
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
    /*val decoder by derivedStateOf {
        if (TextUtils.isEmpty(path) && viewportSize == IntSize.Zero) {
            PdfDecoder(File(path))
        } else null
    }*/

    DisposableEffect(decoder) {
        onDispose {
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
        val tileStreamProvider = remember {
            TileStreamProvider { row, col, zoomLvl ->
                null
            }
        }
        val state = remember {
            MapState(4, decoder!!.imageSize.width, decoder!!.imageSize.height) {
                scale(1.0f)
            }.apply {
                addLayer(tileStreamProvider)
                shouldLoopScale = true
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewportSize = it }) {
            TestUI(
                modifier = Modifier, state
            )
        }
    }
}

@Composable
private fun TestUI(
    modifier: Modifier = Modifier,
    state: MapState,
    content: @Composable () -> Unit = {}
) {
    val zoomPRState = state.zoomPanRotateState

    key(state) {
        ZoomPanRotate(
            modifier = modifier
                .clipToBounds()
                .background(state.mapBackground),
            gestureListener = zoomPRState,
            layoutSizeChangeListener = zoomPRState,
        ) {
            TileCanvas(
                modifier = Modifier,
                zoomPRState = zoomPRState,
                visibleTilesResolver = state.visibleTilesResolver,
                tileSize = state.tileSize,
                tilesToRender = state.tileCanvasState.tilesToRender,
            )

            content()
        }
    }
}