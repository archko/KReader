package ovh.plrapps.mapcompose.ui

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import ovh.plrapps.mapcompose.ui.layout.ZoomPanRotate
import ovh.plrapps.mapcompose.ui.state.MapState
import ovh.plrapps.mapcompose.ui.view.TileCanvas

@Composable
fun TestUI(
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