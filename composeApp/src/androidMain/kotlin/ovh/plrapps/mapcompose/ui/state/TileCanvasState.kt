package ovh.plrapps.mapcompose.ui.state

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import ovh.plrapps.mapcompose.core.*
import java.util.concurrent.Executors
import kotlin.math.pow

/**
 * This class contains all the logic related to [Tile] management.
 * It defers [Tile] loading to the [TileCollector].
 * All internal data manipulation are thread-confined to a single background thread. This is
 * guarantied by the [scope] and its custom dispatcher.
 * Ultimately, it exposes the list of tiles to render ([tilesToRender]) which is backed by a
 * [MutableState]. A composable using [tilesToRender] will be automatically recomposed when this
 * list changes.
 *
 * @author P.Laurence on 04/06/2019
 */
internal class TileCanvasState(
    parentScope: CoroutineScope,
    tileSize: Int,
    private val visibleTilesResolver: VisibleTilesResolver,
    workerCount: Int,
    private val decoder: com.archko.reader.pdf.subsampling.PdfDecoder
) {

    private val singleThreadDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(
        parentScope.coroutineContext + singleThreadDispatcher
    )
    internal var tilesToRender: List<Tile> by mutableStateOf(listOf())

    private val visibleTileLocationsChannel = Channel<TileSpec>(capacity = Channel.RENDEZVOUS)
    private val tilesOutput = Channel<Tile>(capacity = Channel.RENDEZVOUS)
    private val visibleStateFlow = MutableStateFlow<VisibleState?>(null)
    internal var alphaTick = 0.07f
        set(value) {
            field = value.coerceIn(0.01f, 1f)
        }

    private val lastVisible: VisibleTiles?
        get() = visibleStateFlow.value?.visibleTiles

    private val recycleChannel = Channel<Tile>(Channel.UNLIMITED)

    /**
     * So long as this debounced channel is offered a message, the lambda isn't called.
     */
    private val idleDebounced = scope.debounce<Unit>(400) {
        visibleStateFlow.value?.also { (visibleTiles) ->
            println("state:idleDebounced:$visibleTiles")
            evictTiles(visibleTiles, aggressiveAttempt = true)
            renderTiles(visibleTiles)
        }
    }

    private val renderTask = scope.throttle(wait = 34) {
        /* Evict, then render */
        val (lastVisible) = visibleStateFlow.value ?: return@throttle
        evictTiles(lastVisible)

        renderTiles(lastVisible)
    }

    private fun renderTiles(visibleTiles: VisibleTiles) {
        /* Right before sending tiles to the view, reorder them so that tiles from current level are
         * above others. */
        val tilesToRenderCopy = tilesCollected.sortedBy {
            val priority = if (it.zoom == visibleTiles.scale && it.level == visibleTiles.level) 100 else 0
            priority
        }
        println("state:renderTiles: tiles details:${tilesToRenderCopy.size}")
        tilesToRenderCopy.forEach { tile ->
            println("  - tile=$tile, bitmap=${tile.bitmap != null}")
        }

        tilesToRender = tilesToRenderCopy
    }

    private val tilesCollected = mutableListOf<Tile>()

    private val tileCollector: TileCollector

    init {
        /* Collect visible tiles and send specs to the TileCollector */
        scope.launch {
            collectNewTiles()
        }

        /* Launch the TileCollector */
        tileCollector = TileCollector(workerCount.coerceAtLeast(1), tileSize, decoder)
        scope.launch {
            tileCollector.collectTiles(
                tileSpecs = visibleTileLocationsChannel,
                tilesOutput = tilesOutput,
            )
        }

        /* Launch a coroutine to consume the produced tiles */
        scope.launch {
            consumeTiles(tilesOutput)
        }

        scope.launch(Dispatchers.Main) {
            for (t in recycleChannel) {
                val b = t.bitmap
                t.bitmap = null
                b?.recycle()
            }
        }
    }

    /**
     * Forgets visible state and previously collected tiles.
     * To clear the canvas, call [forgetTiles], then [renderThrottled].
     */
    suspend fun forgetTiles() {
        scope.launch {
            visibleStateFlow.value = null
            tilesCollected.clear()
        }.join()
    }

    fun shutdown() {
        singleThreadDispatcher.close()
        tileCollector.shutdownNow()
    }

    suspend fun setViewport(viewport: Viewport) {
        /* Thread-confine the tileResolver to the main thread */
        val visibleTiles = withContext(Dispatchers.Main) {
            visibleTilesResolver.getVisibleTiles(viewport)
        }

        withContext(scope.coroutineContext) {
            if (visibleTilesResolver.getScale() == visibleTiles.scale) {
                setVisibleTiles(visibleTiles)
            }
        }
    }

    private fun setVisibleTiles(visibleTiles: VisibleTiles) {
        /* Feed the tile processing machinery */
        val visibleState = VisibleState(visibleTiles)
        visibleStateFlow.value = visibleState

        println("state:setVisibleTiles:$visibleTiles")
        renderThrottled()
    }

    /**
     * Consumes incoming visible tiles from [visibleStateFlow] and sends [TileSpec] instances to the
     * [TileCollector].
     *
     * Leverage built-in back pressure, as this function will suspend when the tile collector is busy
     * to the point it can't handshake the [visibleTileLocationsChannel] channel.
     *
     * Using [Flow.collectLatest], we cancel any ongoing previous tile list processing. It's
     * particularly useful when the [TileCollector] is too slow, so when a new [VisibleTiles] element
     * is received from [visibleStateFlow], no new [TileSpec] elements from the previous [VisibleTiles]
     * element are sent to the [TileCollector]. When the [TileCollector] is ready to resume processing,
     * the latest [VisibleTiles] element is processed right away.
     */
    private suspend fun collectNewTiles() {
        visibleStateFlow.collectLatest { visibleState ->
            val visibleTiles = visibleState?.visibleTiles
            if (visibleTiles != null) {
                val viewScale = visibleTilesResolver.getScale()
                println("state:collectNewTiles: viewScale=$viewScale, visibleTiles.scale=${visibleTiles.scale}, visibleTiles.count=${visibleTiles.visibleTiles.size}")
                if (visibleTiles.scale == viewScale) {
                    for (tileSpec in visibleTiles.visibleTiles) {
                        // 检查是否已经有相同spec的tile，并且该tile有bitmap
                        val existingTile = tilesCollected.find { tile ->
                            tile.sameSpecAs(
                                tileSpec.zoom,
                                tileSpec.level,
                                tileSpec.pageIndex,
                                tileSpec.pageOffsetX,
                                tileSpec.pageOffsetY,
                            )
                        }

                        /* Only emit specs which haven't already been processed by the collector
                         * or if the existing tile doesn't have a bitmap */
                        if (existingTile == null || existingTile.bitmap == null) {
                            //println("TileCanvasState: sending tileSpec=$tileSpec")
                            visibleTileLocationsChannel.send(tileSpec)
                        } else {
                            println("TileCanvasState: tileSpec already processed with bitmap=$tileSpec")
                        }
                    }
                }
            }
        }
    }

    /**
     * For each [Tile] received, add it to the list of collected tiles if it's visible. Otherwise,
     * recycle the tile.
     */
    private suspend fun consumeTiles(tileChannel: ReceiveChannel<Tile>) {
        for (tile in tileChannel) {
            val lastVisible = lastVisible
            val existingTile = tilesCollected.find { existing ->
                existing.sameSpecAs(
                    tile.zoom,
                    tile.level,
                    tile.pageIndex,
                    tile.pageOffsetX,
                    tile.pageOffsetY
                )
            }
            
            if (
                (lastVisible == null || lastVisible.contains(tile))
                && existingTile == null
            ) {
                tile.prepare()
                tilesCollected.add(tile)
                println("state:consumeTiles: added tile=$tile, total=${tilesCollected.size}")
                renderThrottled()
            } else {
                println("state:consumeTiles: recycling tile=$tile (not visible or already exists)")
                tile.recycle()
            }
            //fullEvictionDebounced()
        }
    }

    private fun fullEvictionDebounced() {
        idleDebounced.trySend(Unit)
    }

    /**
     * The the alpha needs to be set to [alphaTick], to produce a fade-in effect. If [alphaTick] is
     * 1f, the alpha won't be updated and there won't be any fade-in effect.
     */
    private fun Tile.prepare() {
    }

    private fun VisibleTiles.contains(tile: Tile): Boolean {
        // 检查tile是否在当前可见tile列表中
        // 需要匹配pageIndex、位置和level
        val found = visibleTiles.any { spec ->
            spec.pageIndex == tile.pageIndex
                    && spec.pageOffsetX == tile.pageOffsetX
                    && spec.pageOffsetY == tile.pageOffsetY
                    && spec.level == tile.level
            //&& spec.zoom == tile.zoom //这个如果加了,缩放时页面会空白.
        }

        if (!found) {
            println("VisibleTiles.contains: tile not found in visible list - tile=$tile, visibleLevel=$level, tileLevel=${tile.level}")
            println("VisibleTiles.contains: visible tiles count=${visibleTiles.size}")
            // Add more detailed debugging
            visibleTiles.take(3).forEach { spec ->
                println("  - visible spec: pageIndex=${spec.pageIndex}, offsetX=${spec.pageOffsetX}, offsetY=${spec.pageOffsetY}, level=${spec.level}")
            }
        }

        return found
    }

    private fun VisibleTiles.intersects(tile: Tile): Boolean {
        return if (level == tile.level) {
            // 如果level相同，检查是否有重叠的tile
            visibleTiles.any { spec ->
                spec.pageIndex == tile.pageIndex
                        && spec.pageOffsetX == tile.pageOffsetX
                        && spec.pageOffsetY == tile.pageOffsetY
                        && spec.level == tile.level
            }
        } else {
            // 如果level不同，检查是否有重叠的区域
            // 这里可以根据需要实现更复杂的重叠检测逻辑
            false
        }
    }

    /**
     * Each time we get a new [VisibleTiles], remove all [Tile] from [tilesCollected] which aren't
     * visible or that aren't needed anymore and put their bitmap into the pool.
     */
    private fun evictTiles(
        visibleTiles: VisibleTiles,
        aggressiveAttempt: Boolean = false
    ) {
        val currentLevel = visibleTiles.level

        /* Always perform partial eviction */
        partialEviction(visibleTiles)

        /* Only perform aggressive eviction when tile collector is idle */
        if (aggressiveAttempt && tileCollector.isIdle) {
            aggressiveEviction(currentLevel)
        }
    }

    /**
     * Evict:
     * * tiles that are not in the current visible tiles list
     * * tiles of different levels that are not needed
     */
    private fun partialEviction(
        visibleTiles: VisibleTiles,
    ) {
        val currentLevel = visibleTiles.level
        val initialSize = tilesCollected.size
        //println("state:partialEviction:level:$currentLevel, size:$initialSize")

        val iterator = tilesCollected.iterator()
        var removedCount = 0
        while (iterator.hasNext()) {
            val tile = iterator.next()

            // Remove tiles that are not in the current visible tiles list
            if (!visibleTiles.contains(tile)) {
                println("state:partialEviction: removing tile=$tile (not in visible list)")
                iterator.remove()
                tile.recycle()
                removedCount++
                continue
            }

            // Remove tiles of different levels (keep only current level tiles)
            if (tile.level != currentLevel) {
                println("state:partialEviction: removing tile=$tile (different level: ${tile.level} vs $currentLevel)")
                iterator.remove()
                tile.recycle()
                removedCount++
            }
        }
        
        println("state:partialEviction: removed $removedCount tiles, final size:${tilesCollected.size}")
    }

    private fun shouldKeepTile(
        tile: Tile,
    ): Boolean {
        return true
    }

    /**
     * Removes tiles of other levels, even if they are visible (although they should be drawn beneath
     * currently visible tiles).
     * Only triggered after the [idleDebounced] fires.
     */
    private fun aggressiveEviction(
        currentLevel: Int,
    ) {
        val iterator = tilesCollected.iterator()
        while (iterator.hasNext()) {
            val tile = iterator.next()

            if (tile.level != currentLevel) {
                println("state:aggressiveEviction:level:$currentLevel, $tile")
                iterator.remove()
                tile.recycle()
            }
        }
    }

    private fun evictAll() = scope.launch {
        val iterator = tilesCollected.iterator()
        while (iterator.hasNext()) {
            val tile = iterator.next()
            iterator.remove()
            tile.recycle()
        }
    }

    /**
     * Post a new value to the observable. The view should update its UI.
     */
    private fun renderThrottled() {
        renderTask.trySend(Unit)
    }

    /**
     * After a [Tile] is no longer visible, depending on the bitmap mutability:
     * - If the Bitmap is mutable, put it into the pool for later use.
     * - If the bitmap isn't mutable, we don't use bitmap pooling. That means the associated graphic
     * memory can be reclaimed asap.
     * The Compose framework draws tiles on the main thread and checks whether or not [Tile.bitmap]
     * is null. So, prior to calling recycle() we set [Tile.bitmap] to null on the main thread. This
     * is done inside the coroutine which consumes [recycleChannel].
     */
    private fun Tile.recycle() {
        val b = bitmap ?: return
        if (b.isMutable) {
        } else {
            recycleChannel.trySend(this)
        }
    }

    private fun Int.minAtGreaterLevel(n: Int): Int {
        return this * 2.0.pow(n).toInt()
    }

    private fun Int.maxAtGreaterLevel(n: Int): Int {
        return (this + 1) * 2.0.pow(n).toInt() - 1
    }

    private data class VisibleState(
        val visibleTiles: VisibleTiles,
    )
}
