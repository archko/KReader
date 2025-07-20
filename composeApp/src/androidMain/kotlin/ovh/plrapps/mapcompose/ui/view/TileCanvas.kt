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
                
                // 计算页面在文档中的位置 - 使用与VisibleTilesResolver相同的逻辑
                val pageStart = visibleTilesResolver.getPageStart(tile.pageIndex)
                //println("TileCanvas: tile.pageIndex=${tile.pageIndex}, calculated pageStart=$pageStart")
                
                // 计算tile在文档坐标系中的位置
                // tile.pageOffsetX 和 tile.pageOffsetY 是页面内的偏移
                // 需要加上页面在文档中的位置
                val tileX = tile.pageOffsetX  // 页面在文档中的x偏移是0，所以直接使用页面内偏移
                val tileY = pageStart + tile.pageOffsetY  // 需要加上页面在文档中的y位置
                
                // Canvas已经应用了缩放变换，所以这里使用文档坐标系
                // bitmap是按照1.0的scale解码的，所以直接使用bitmap的原始尺寸
                val l = tileX.toFloat()
                val t = tileY.toFloat()
                val r = l + bitmap.width.toFloat()  // 直接使用bitmap的原始宽度
                val b = t + bitmap.height.toFloat()  // 直接使用bitmap的原始高度
                dest.set(l.toInt(), t.toInt(), r.toInt(), b.toInt())

                //println("TileCanvas: drawing tile $tile at $l,$t,$r,$b with scale ${zoomPRState.scale}, pageIndex=${tile.pageIndex}, pageStart=$pageStart, tileSize=$tileSize, bitmapSize=${bitmap.width}x${bitmap.height}, tileX=$tileX, tileY=$tileY, pageOffsetX=${tile.pageOffsetX}, pageOffsetY=${tile.pageOffsetY}")
                //println("TileCanvas: calculated values: tileX=$tileX, tileY=$tileY, l=$l, t=$t, r=$r, b=$b, bitmapWidth=${bitmap.width}, bitmapHeight=${bitmap.height}")

                drawIntoCanvas {
                    it.nativeCanvas.drawBitmap(bitmap, null, dest, null)
                }
            }
        }
    }
}