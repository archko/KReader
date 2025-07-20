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
import ovh.plrapps.mapcompose.core.*
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
            /* Geometric transformations seem to be applied in reversed order of declaration */
            rotate(
                degrees = zoomPRState.rotation,
                pivot = Offset(
                    x = zoomPRState.pivotX.toFloat(),
                    y = zoomPRState.pivotY.toFloat()
                )
            )
            translate(left = -zoomPRState.scrollX, top = -zoomPRState.scrollY)
            scale(scale = zoomPRState.scale, Offset.Zero)
        }) {
            //paint.isFilterBitmap = isFilteringBitmap()

            //println("TileCanvas: rendering ${tilesToRender.size} tiles")
            for (tile in tilesToRender) {
                val bitmap = tile.bitmap
                if (bitmap == null) {
                    println("TileCanvas: tile has no bitmap: $tile")
                    continue
                }
                
                // tile.pageOffsetX 和 tile.pageOffsetY 已经是文档坐标系中的位置
                // 因为在VisibleTilesResolver中已经加上了pageSize.offsetHeight
                val tileX = tile.pageOffsetX
                val tileY = tile.pageOffsetY
                
                // 使用tile的实际尺寸，而不是bitmap的原始尺寸
                val l = tileX.toFloat()
                val t = tileY.toFloat()
                val r = l + tile.tileWidth.toFloat()
                val b = t + tile.tileHeight.toFloat()
                dest.set(l.toInt(), t.toInt(), r.toInt(), b.toInt())

                println("TileCanvas: drawing tile pageIndex=${tile.pageIndex} at ($l,$t,$r,$b), tileSize=${tile.tileWidth}x${tile.tileHeight}, bitmapSize=${bitmap.width}x${bitmap.height}, tile.pageOffsetX=${tile.pageOffsetX}, pageOffsetY=${tile.pageOffsetY}")

                drawIntoCanvas {
                    it.nativeCanvas.drawBitmap(bitmap, null, dest, null)
                }
            }
        }
    }
}