package com.archko.reader.pdf.subsampling.internal

import androidx.compose.ui.util.fastForEach
import com.archko.reader.pdf.cache.ImageBitmapCache
import com.archko.reader.pdf.subsampling.internal.ImageCache.LoadingState.InFlight
import com.archko.reader.pdf.subsampling.internal.ImageCache.LoadingState.Loaded
import com.archko.reader.pdf.subsampling.tile.ImageTile
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal class ImageCache(
    scope: CoroutineScope,
    private val decoder: ImageRegionDecoder,
    private val throttleEvery: Duration = 100.milliseconds,
) {
    private val visibleRegions = Channel<List<ImageTile>>(capacity = 10)
    private val cachedImages = MutableStateFlow(emptyMap<ImageTile, LoadingState>())

    private sealed interface LoadingState {
        data class Loaded(val painter: ImageRegionDecoder.DecodeResult) : LoadingState
        data class InFlight(val job: Job) : LoadingState
    }

    init {
        scope.launch {
            visibleRegions.consumeAsFlow()
                .distinctUntilChanged()
                .throttleLatest(throttleEvery)  // In case the image is animating its zoom.
                .collect { tiles ->
                    val tilesToLoad = tiles.fastFilter { it !in cachedImages.value }
                    tilesToLoad.fastForEach { tile ->
                        // CoroutineStart.UNDISPATCHED is used to ensure that the coroutines are executed
                        // in the same order they were launched. Otherwise, the tiles may load in a different
                        // order than what was requested. SubSamplingImageTest#draw_tile_under_centroid_first()
                        // test will also become flaky.
                        launch(start = CoroutineStart.UNDISPATCHED) {
                            cachedImages.update {
                                check(tile !in it)
                                it + (tile to InFlight(currentCoroutineContext().job))
                            }
                            val key = "${tile.index}-${tile.bounds}"
                            val painter = ImageBitmapCache.getInstance().getBitmap(key)
                            if (null != painter) {
                                cachedImages.update {
                                    it + (tile to Loaded(
                                        ImageRegionDecoder.DecodeResult(painter)
                                    ))
                                }
                            } else {
                                val painter = decoder.decodeRegion(tile)
                                ImageBitmapCache.getInstance().addBitmap(key, painter.painter)
                                cachedImages.update {
                                    it + (tile to Loaded(painter))
                                }
                            }
                        }
                    }

                    val tilesToUnload = cachedImages.value.keys.filter { it !in tiles }
                    tilesToUnload.fastForEach { region ->
                        val inFlight = cachedImages.value[region] as? InFlight
                        inFlight?.job?.cancel()
                    }
                    cachedImages.update { it - tilesToUnload.toSet() }
                }
        }
    }

    fun observeCachedImages(): Flow<ImmutableMap<ImageTile, ImageRegionDecoder.DecodeResult>> {
        return cachedImages.map { map ->
            buildMap(capacity = map.size) {
                map.forEach { (region, state) ->
                    if (state is Loaded) {
                        put(region, state.painter)
                    }
                }
            }.toImmutableMap()
        }.distinctUntilChanged()
    }

    fun loadOrUnloadForTiles(regions: List<ImageTile>) {
        visibleRegions.trySend(regions)
    }

    // Copied from https://github.com/Kotlin/kotlinx.coroutines/issues/1446#issuecomment-1198103541.
    private fun <T> Flow<T>.throttleLatest(delay: Duration): Flow<T> {
        return conflate().transform {
            emit(it)
            delay(delay)
        }
    }
}
