@file:Suppress("unused")

package ovh.plrapps.mapcompose.api

import androidx.compose.ui.platform.ViewConfiguration
import ovh.plrapps.mapcompose.ui.state.ViewState


/**
 * Enable rotation by user gestures.
 */
/*fun ViewState.enableRotation() {
    zoomPanRotateState.isRotationEnabled = true
}*/

/**
 * Enable scrolling by user gestures. This is enabled by default.
 */
fun ViewState.enableScrolling() {
    zoomPanRotateState.isScrollingEnabled = true
}

/**
 * Enable zooming by user gestures. This is enabled by default.
 */
fun ViewState.enableZooming() {
    zoomPanRotateState.isZoomingEnabled = true
}

/**
 * Discard rotation gestures. The map can still be programmatically rotated using APIs such as
 * [rotateTo] or [rotation].
 */
/*fun ViewState.disableRotation() {
    zoomPanRotateState.isRotationEnabled = false
}*/

/**
 * Discard scrolling gestures. The map can still be programmatically scrolled using APIs such as
 * [scrollTo] or [snapScrollTo].
 */
fun ViewState.disableScrolling() {
    zoomPanRotateState.isScrollingEnabled = false
}

/**
 * Discard zooming gestures. The map can still be programmatically zoomed using [scale].
 */
fun ViewState.disableZooming() {
    zoomPanRotateState.isZoomingEnabled = false
}

/**
 * Disable gesture detection. The map view can still be transformed programmatically.
 */
fun ViewState.disableGestures() {
    with (zoomPanRotateState) {
        isRotationEnabled = false
        isScrollingEnabled = false
        isZoomingEnabled = false
    }
}

/**
 * Enables fling scale animation after a pinch to zoom gesture. Enabled by default.
 */
fun ViewState.enableFlingZoom() {
    zoomPanRotateState.isFlingZoomEnabled = true
}

/**
 * Disables fling scale animation after a pinch to zoom gesture.
 */
fun ViewState.disableFlingZoom() {
    zoomPanRotateState.isFlingZoomEnabled = false
}

/**
 * Registers a tap callback for tap gestures. The callback is invoked with the relative coordinates
 * of the tapped point on the map.
 * Note: the tap gesture is detected only after the [ViewConfiguration.doubleTapMinTimeMillis] has
 * passed, because the layout's gesture detector also detects double-tap gestures.
 */
fun ViewState.onTap(tapCb: (x: Double, y: Double) -> Unit) {
    this.tapCb = tapCb
}

/**
 * Registers a callback for long press gestures. The callback is invoked with the relative coordinates
 * of the pressed point on the map.
 */
fun ViewState.onLongPress(longPressCb: (x: Double, y: Double) -> Unit) {
    this.longPressCb = longPressCb
}

/**
 * Registers a callback for touch down event.
 */
fun ViewState.onTouchDown(cb: () -> Unit) {
    touchDownCb = cb
}