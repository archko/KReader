package ovh.plrapps.mapcompose.ui.view

import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import ovh.plrapps.mapcompose.core.Tile
import ovh.plrapps.mapcompose.core.VisibleTilesResolver
import ovh.plrapps.mapcompose.ui.state.ZoomPanRotateState

@Composable
internal fun TileCanvas(
    modifier: Modifier,
    zoomPRState: ZoomPanRotateState,
    visibleTilesResolver: VisibleTilesResolver,
    tileSize: Int,
    tilesToRender: List<Tile>,
) {
    val dest = remember { Rect() }

    Canvas(
        modifier = modifier
            .fillMaxSize()
    ) {
        withTransform({
            /* Geometric transformations seem to be 一applied in reversed order of declaration */
            translate(left = -zoomPRState.scrollX, top = -zoomPRState.scrollY)
            scale(scale = zoomPRState.scale, Offset.Zero)
        }) {
            println("state:Canvas:${tilesToRender.size}")
            for (tile in tilesToRender) {
                val bitmap = tile.bitmap ?: continue
                val scaleForLevel = 1f
                val tileScaled = (tileSize / scaleForLevel).toInt()
                val l = tile.col * tileScaled
                val t = tile.row * tileScaled
                val r = l + tileScaled
                val b = t + tileScaled
                dest.set(l, t, r, b)

                drawIntoCanvas {
                    it.nativeCanvas.drawBitmap(bitmap, null, dest, null)
                }
            }
        }
    }
}