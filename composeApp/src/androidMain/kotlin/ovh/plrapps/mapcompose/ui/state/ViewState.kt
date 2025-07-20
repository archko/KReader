package ovh.plrapps.mapcompose.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.archko.reader.pdf.subsampling.PdfDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import ovh.plrapps.mapcompose.core.GestureConfiguration
import ovh.plrapps.mapcompose.core.Viewport
import ovh.plrapps.mapcompose.core.VisibleTilesResolver
import ovh.plrapps.mapcompose.core.throttle
import ovh.plrapps.mapcompose.ui.layout.Fit
import ovh.plrapps.mapcompose.ui.layout.Forced
import ovh.plrapps.mapcompose.ui.layout.MinimumScaleMode
import ovh.plrapps.mapcompose.utils.AngleDegree

/**
 * The state of the map. All public APIs are extensions functions or extension properties of this
 * class.
 *
 * @param decoder PdfDecoder instance for PDF processing
 * @param levelCount The number of levels in the pyramid. Defaults to 4. Each level represents a different zoom level.
 * @param tileSize The size in pixels of tiles, which are expected to be squared. Defaults to 256.
 * @param workerCount The thread count used to fetch tiles. Defaults to the number of cores minus
 * one, which works well for tiles in the file system or in a local database. However, that number
 * should be increased to 16 or more for remote tiles (HTTP requests).
 * @param initialValuesBuilder A builder for [InitialValues] which are applied during [ViewState]
 * initialization. Note that the provided lambda should not start any coroutines.
 */
class ViewState(
    decoder: PdfDecoder,
    levelCount: Int = 6,
    fullWidth: Int,
    fullHeight: Int,
    tileSize: Int = 512,
    workerCount: Int = 1,
    initialValuesBuilder: InitialValues.() -> Unit = {}
) : ZoomPanRotateStateListener {
    private val initialValues = InitialValues().apply(initialValuesBuilder)
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // 计算初始缩放比例 - 根据文档尺寸和视图尺寸
    private val initialScale = if (initialValues.scale == 1f) {
        // 如果用户没有指定scale，则根据minimumScaleMode计算
        when (initialValues.minimumScaleMode) {
            is ovh.plrapps.mapcompose.ui.layout.Fit -> {
                // 默认让文档宽度填满视图宽度，这里先设为1，等layout完成后会重新计算
                1f
            }
            is ovh.plrapps.mapcompose.ui.layout.Fill -> {
                // 填满视图，这里先设为1，等layout完成后会重新计算
                1f
            }
            is ovh.plrapps.mapcompose.ui.layout.Forced -> {
                (initialValues.minimumScaleMode as Forced).scale
            }
        }
    } else {
        initialValues.scale
    }
    
    internal val zoomPanRotateState = ZoomPanRotateState(
        fullWidth = fullWidth,
        fullHeight = fullHeight,
        stateChangeListener = this,
        minimumScaleMode = initialValues.minimumScaleMode,
        maxScale = initialValues.maxScale,
        scale = initialScale,
        rotation = initialValues.rotation,
        gestureConfiguration = initialValues.gestureConfiguration
    )

    internal val visibleTilesResolver =
        VisibleTilesResolver(
            decoder = decoder,
            levelCount = levelCount,
            fullWidth = fullWidth,
            fullHeight = fullHeight,
            tileSize = tileSize,
            magnifyingFactor = initialValues.magnifyingFactor
        ) {
            zoomPanRotateState.scale
        }
    internal val tileCanvasState = TileCanvasState(
        scope,
        tileSize,
        visibleTilesResolver,
        workerCount,
        decoder
    )

    private val throttledTask = scope.throttle(wait = 18) {
        renderVisibleTiles()
    }
    private val viewport = Viewport()
    internal var preloadingPadding: Int = initialValues.preloadingPadding
    internal val tileSize by mutableIntStateOf(tileSize)
    internal var stateChangeListener: (ViewState.() -> Unit)? = null
    internal var touchDownCb: (() -> Unit)? = null
    internal var tapCb: LayoutTapCb? = null
    internal var longPressCb: LayoutTapCb? = null
    internal var mapBackground by mutableStateOf(Color.White)
    private var consumeLateInitialValues: () -> Unit = {
        consumeLateInitialValues = {}
        applyLateInitialValues(initialValues)
    }

    /**
     * Cancels all internal tasks.
     * After this call, this [ViewState] is unusable.
     */
    @Suppress("unused")
    fun shutdown() {
        scope.cancel()
        tileCanvasState.shutdown()
    }

    override fun onStateChanged() {
        consumeLateInitialValues()

        println("ViewState: onStateChanged: scrollX=${zoomPanRotateState.scrollX}, scrollY=${zoomPanRotateState.scrollY}")
        renderVisibleTilesThrottled()
        stateChangeListener?.invoke(this)
    }

    override fun onTouchDown() {
        touchDownCb?.invoke()
    }

    override fun onPress() {
    }

    override fun onLongPress(x: Double, y: Double) {
        longPressCb?.invoke(x, y)
    }

    override fun onTap(x: Double, y: Double) {
        tapCb?.invoke(x, y)
    }

    override fun detectsTap(): Boolean = tapCb != null

    override fun detectsLongPress(): Boolean = longPressCb != null

    override fun interceptsTap(x: Double, y: Double, xPx: Int, yPx: Int): Boolean {
        return false
    }

    override fun interceptsLongPress(x: Double, y: Double, xPx: Int, yPx: Int): Boolean {
        return false
    }

    internal fun renderVisibleTilesThrottled() {
        throttledTask.trySend(Unit)
    }

    private suspend fun renderVisibleTiles() {
        val viewport = updateViewport()
        tileCanvasState.setViewport(viewport)
    }

    private fun updateViewport(): Viewport {
        val padding = preloadingPadding
        val newViewport = viewport.apply {
            left = zoomPanRotateState.scrollX.toInt() - padding
            top = zoomPanRotateState.scrollY.toInt() - padding
            right = left + zoomPanRotateState.layoutSize.width + padding * 2
            bottom = top + zoomPanRotateState.layoutSize.height + padding * 2
        }
        println("ViewState: updateViewport: scrollX=${zoomPanRotateState.scrollX}, scrollY=${zoomPanRotateState.scrollY}, viewport=$newViewport")
        return newViewport
    }

    /**
     * Apply "late" initial values - e.g, those which depend on the layout size.
     * For the moment, the scroll is the only one.
     */
    private fun applyLateInitialValues(initialValues: InitialValues) {
        with(zoomPanRotateState) {
            val offsetX = initialValues.screenOffset.x * layoutSize.width
            val offsetY = initialValues.screenOffset.y * layoutSize.height

            val destScrollX = (initialValues.x * fullWidth * scale + offsetX).toFloat()
            val destScrollY = (initialValues.y * fullHeight * scale + offsetY).toFloat()

            setScroll(destScrollX, destScrollY)
        }
    }
}

/**
 * Builder for initial values.
 * Changes made after the `ViewState` instance creation take precedence over initial values.
 * In the following example, the init scale will be 4f since the max scale is later set to 4f.
 *
 * ```
 * ViewState(4, 4096, 4096,
 *   initialValues = InitialValues().scale(8f)
 * ).apply {
 *   maxScale = 4f
 * }
 * ```
 */
@Suppress("unused")
class InitialValues internal constructor() {
    internal var x = 0.0
    internal var y = 0.0
    internal var screenOffset: Offset = Offset(-0.0f, -0.0f)
    internal var scale: Float = 1f
    internal var minimumScaleMode: MinimumScaleMode = Fit
    internal var maxScale: Float = 8f  // 增加最大缩放值，支持更大的缩放
    internal var rotation: AngleDegree = 0f
    internal var magnifyingFactor = 0
    internal var preloadingPadding: Int = 0
    internal var isFilteringBitmap: (ViewState) -> Boolean = { true }
    internal var gestureConfiguration: GestureConfiguration = GestureConfiguration()

    /**
     * Init the scroll position. Defaults to centering on the provided scroll destination.
     *
     * @param x The normalized X position on the map, in range [0..1]
     * @param y The normalized Y position on the map, in range [0..1]
     * @param screenOffset Offset of the screen relatively to its dimension. Default is
     * Offset(-0.5f, -0.5f), so moving the screen by half the width left and by half the height top,
     * effectively centering on the scroll destination.
     */
    fun scroll(x: Double, y: Double, screenOffset: Offset = Offset(-0.5f, -0.5f)) = apply {
        this.screenOffset = screenOffset
        this.x = x
        this.y = y
    }

    /**
     * Set the initial scale. Defaults to 1f.
     */
    fun scale(scale: Float) = apply {
        this.scale = scale
    }

    /**
     * Set the [MinimumScaleMode]. Defaults to [Fit].
     */
    fun minimumScaleMode(minimumScaleMode: MinimumScaleMode) = apply {
        this.minimumScaleMode = minimumScaleMode
    }

    /**
     * Set the maximum allowed scale. Defaults to 2f.
     */
    fun maxScale(maxScale: Float) = apply {
        this.maxScale = maxScale
    }

    /**
     * Set the initial rotation. Defaults to 0° (no rotation).
     */
    /*fun rotation(rotation: AngleDegree) = apply {
        this.rotation = rotation
    }*/

    /**
     * Alters the level at which tiles are picked for a given scale. By default, the level
     * immediately higher (in index) is picked, to avoid sub-sampling. This corresponds to a
     * [magnifyingFactor] of 0. The value 1 will result in picking the current level at a given
     * scale, which will be at a relative scale between 1.0 and 2.0
     */
    fun magnifyingFactor(magnifyingFactor: Int) = apply {
        this.magnifyingFactor = magnifyingFactor.coerceAtLeast(0)
    }

    /**
     * By default, only visible tiles are loaded. By adding a preloadingPadding additional tiles
     * will be loaded, which can be used to produce a seamless tile loading effect.
     *
     * @param padding in pixels
     */
    fun preloadingPadding(padding: Int) = apply {
        this.preloadingPadding = padding.coerceAtLeast(0)
    }

    /**
     * Controls whether Bitmap filtering is enabled when drawing tiles. This is enabled by default.
     * Disabling it is useful to achieve nearest-neighbor scaling, for cases when the art style of
     * the displayed image benefits from it.
     * @see [android.graphics.Paint.setFilterBitmap]
     */
    fun bitmapFilteringEnabled(enabled: Boolean) = apply {
        bitmapFilteringEnabled { enabled }
    }

    /**
     * A version of [bitmapFilteringEnabled] which allows for dynamic control of bitmap filtering
     * depending on the current [ViewState].
     */
    fun bitmapFilteringEnabled(predicate: (state: ViewState) -> Boolean) = apply {
        isFilteringBitmap = predicate
    }

    /**
     * Customize gestures.
     */
    fun configureGestures(gestureConfigurationBlock: GestureConfiguration.() -> Unit) {
        this.gestureConfiguration.gestureConfigurationBlock()
    }
}

internal typealias LayoutTapCb = (x: Double, y: Double) -> Unit