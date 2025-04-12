package com.archko.reader.pdf.subsampling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import com.archko.reader.pdf.subsampling.internal.ImageCache
import com.archko.reader.pdf.subsampling.internal.ImageRegionDecoder
import com.archko.reader.pdf.subsampling.internal.fastMapNotNull
import com.archko.reader.pdf.subsampling.internal.tile.ImageRegionTile
import com.archko.reader.pdf.subsampling.internal.tile.ImageRegionTileGrid
import com.archko.reader.pdf.subsampling.internal.tile.ImageSampleSize
import com.archko.reader.pdf.subsampling.internal.tile.ViewportImageTile
import com.archko.reader.pdf.subsampling.internal.tile.ViewportTile
import com.archko.reader.pdf.subsampling.internal.tile.calculateFor
import com.archko.reader.pdf.subsampling.internal.tile.contains2
import com.archko.reader.pdf.subsampling.internal.tile.generate
import com.archko.reader.pdf.subsampling.internal.tile.isNotEmpty
import com.archko.reader.pdf.subsampling.internal.tile.maxScale
import com.archko.reader.pdf.subsampling.internal.tile.overlaps2
import com.archko.reader.pdf.subsampling.internal.tile.scaledAndOffsetBy
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import me.saket.telephoto.zoomable.ZoomableContentTransformation
import com.archko.reader.pdf.subsampling.internal.ImageRegionDecoder.DecodeResult as ImageDecodeResult

/** State for [SubSamplingImage]. Created using [rememberSubSamplingImageState]. */
@Stable
internal class RealSubSamplingImageState(
    private val imageSource: SubSamplingImageSource,
    private val contentTransformation: () -> ZoomableContentTransformation,
) : SubSamplingImageState {

    override val imageSize: IntSize?
        get() = decoder?.imageSize

    // todo: it isn't great that the preview image remains in memory even after the full image is loaded.
    private val imagePreview: Painter? =
        imageSource.preview?.let(::BitmapPainter)

    internal val imageOrPreviewSize: IntSize?
        get() = imageSize ?: imageSource.preview?.size()

    override val isImageDisplayed: Boolean by derivedStateOf {
        isReadyToBeDisplayed && viewportImageTiles.isNotEmpty() &&
                (viewportImageTiles.fastAny { it.isBase } || viewportImageTiles.fastAll { it.painter != null })
    }

    override val isImageDisplayedInFullQuality: Boolean by derivedStateOf {
        isImageDisplayed && viewportImageTiles.fastAll { it.painter != null }
    }

    internal var decoder: ImageRegionDecoder? by mutableStateOf(null)
    internal var viewportSize: IntSize? by mutableStateOf(null)
    internal var showTileBounds = true  // Only used by tests.

    /**
     * Images collected from [ImageCache].
     *
     * Loaded images are kept in a separate state instead of being combined with viewport tiles
     * because images are collected asynchronously, whereas viewport tiles are updated synchronously
     * during the layout pass.
     *
     * This separation enables layout changes to be rendered immediately. In previous
     * versions, layout changes caused image flickering because tile updates were asynchronous
     * and lagged by one frame.
     */
    private var loadedImages: ImmutableMap<ImageRegionTile, ImageDecodeResult> by mutableStateOf(
        persistentMapOf()
    )

    private val isReadyToBeDisplayed: Boolean by derivedStateOf {
        viewportSize?.isNotEmpty() == true && imageOrPreviewSize?.isNotEmpty() == true
    }

    // Note to self: This is not inlined in viewportTiles to
    // avoid creating a new grid on every transformation change.
    private val tileGrid by derivedStateOf {
        if (isReadyToBeDisplayed && decoder != null) {
            val transformation = contentTransformation()
            ImageRegionTileGrid.generate(
                transformation.scale,
                viewportSize = viewportSize!!,
                unscaledImageSize = imageOrPreviewSize!!,
            )
        } else null
    }

    private val viewportTiles: ImmutableList<ViewportTile> by derivedStateOf {
        val tileGrid = tileGrid ?: return@derivedStateOf persistentListOf()
        val transformation = contentTransformation()
        val baseSampleSize = tileGrid.base.sampleSize

        val currentSampleSize = ImageSampleSize
            .calculateFor(zoom = transformation.scale.maxScale)
            .coerceAtMost(baseSampleSize)

        val isBaseSampleSize = currentSampleSize == baseSampleSize
        val foregroundRegions =
            if (isBaseSampleSize) emptyList() else tileGrid.foreground[currentSampleSize]!!

        (listOf(tileGrid.base) + foregroundRegions)
            .sortedByDescending {
                it.bounds.contains2(transformation.centroid)
            }
            .fastMapNotNull { region ->
                val isBaseTile = region == tileGrid.base
                val drawBounds =
                    region.bounds.scaledAndOffsetBy(transformation.scale, transformation.offset)
                ViewportTile(
                    region = region,
                    bounds = drawBounds,
                    isBase = isBaseTile,
                    isVisible = drawBounds.overlaps2(viewportSize!!),
                )
            }
            .toImmutableList()
    }

    internal val viewportImageTiles: ImmutableList<ViewportImageTile> by derivedStateOf {
        // Fill any missing gaps in tiles by drawing the low-res base tile underneath as
        // a fallback. The base tile will hide again when all bitmaps have been loaded.
        //
        // A drawback of doing this is that the base tile may get optimized out before the
        // foreground tiles complete their fade-in animation (run by ZoomableImage()).
        // This can only be solved by encoding their animation info in the tiles.
        val hasNoForeground = viewportTiles.fastAll { it.isBase }
        val hasGapsInForeground =
            { viewportTiles.fastAny { !it.isBase && it.isVisible && it.region !in loadedImages } }
        val canDrawBaseTile = hasNoForeground || hasGapsInForeground()

        viewportTiles.fastMapNotNull { tile ->
            if (tile.isVisible && (!tile.isBase || canDrawBaseTile)) {
                ViewportImageTile(
                    tile = tile,
                    painter = loadedImages[tile.region]?.painter
                        ?: if (tile.isBase) imagePreview else null,
                )
            } else null
        }.toImmutableList()
    }

    @Composable
    fun LoadImageTilesEffect() {
        val imageRegionDecoder = decoder ?: return
        val scope = rememberCoroutineScope()
        val imageCache = remember(this, imageRegionDecoder) {
            ImageCache(scope, imageRegionDecoder)
        }

        LaunchedEffect(imageCache) {
            snapshotFlow { viewportTiles }.collect { tiles ->
                imageCache.loadOrUnloadForTiles(
                    regions = tiles.fastMapNotNull { if (it.isVisible) it.region else null }
                )
            }
        }
        LaunchedEffect(imageCache) {
            imageCache.observeCachedImages().collect {
                loadedImages = it
            }
        }
    }
}

private fun ImageBitmap.size(): IntSize = IntSize(width, height)
