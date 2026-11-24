package com.archko.reader.pdf.component

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.APage
import com.archko.reader.pdf.util.ReflowCache
import com.archko.reader.pdf.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.math.sqrt

/**
 * Background decode service for the reflow mode.
 *
 * Responsibilities:
 *  - Accepts per-page requests from the UI (any thread / coroutine).
 *  - Runs all decode work on a dedicated single thread so the main thread and
 *    the tile-render thread are never blocked.
 *  - Deduplicates requests: if a new request arrives for the same page+params
 *    before the previous one finished, the old result is discarded.
 *  - Caches rendered tile bitmaps on disk so repeated visits or orientation
 *    changes don't trigger a full re-reflow.
 *  - Supports both PdfDecoder and DjvuDecoder (any ImageDecoder).
 *
 * Lifecycle: call [shutdown] when the document is closed.
 */
public class ReflowDecodeService(
    private val decoder: ImageDecoder,
    private val documentPath: String,
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val densityDpi: Int,
    private val orientation: Int = 0,
) {
    // ── Single-thread coroutine worker ────────────────────────────────────────
    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    private val dispatcher = newSingleThreadContext("ReflowDecode")

    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val isShutdown = AtomicBoolean(false)

    // ── Request channel (capacity = number of pages; DROP_OLDEST not needed
    //    because we deduplicate via the pending map below) ────────────────────
    private val requestChannel = Channel<DecodeRequest>(Channel.UNLIMITED)

    // ── Pending requests: pageIndex → latest request.
    //    When a newer request comes in for the same page the old one's
    //    continuation is cancelled via its `cancelled` flag. ──────────────────
    private val pending = ConcurrentHashMap<Int, DecodeRequest>()

    // ── Disk tile cache ───────────────────────────────────────────────────────
    private val tileCache = ReflowCache.create(documentPath)

    init {
        scope.launch {
            for (request in requestChannel) {
                if (isShutdown.get()) break
                // Skip stale requests (a newer one replaced this page)
                val current = pending[request.pageIndex]
                if (current !== request) {
                    request.cancelled.set(true)
                    continue
                }
                processRequest(request)
                pending.remove(request.pageIndex, request)
            }
        }
    }

    /**
     * Request tiles for [pageIndex] with the given style parameters.
     * Suspends until the result is available (or the coroutine is cancelled).
     * Returns an empty list on failure.
     *
     * If called again for the same page while a previous call is in-flight,
     * the previous call gets an empty result immediately and the new call
     * takes over.
     */
    public suspend fun requestPage(
        pageIndex: Int,
        fontSize: Float,
        wordSpacing: Float,
    ): List<ImageBitmap> = suspendCancellableCoroutine { cont ->
        // Cancel any in-flight request for this page
        pending[pageIndex]?.cancelled?.set(true)

        val request = DecodeRequest(
            pageIndex = pageIndex,
            fontSize = fontSize,
            wordSpacing = wordSpacing,
            onResult = { tiles ->
                if (cont.isActive) cont.resume(tiles)
            }
        )
        pending[pageIndex] = request
        requestChannel.trySend(request)

        cont.invokeOnCancellation {
            request.cancelled.set(true)
            pending.remove(pageIndex, request)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    public fun shutdown() {
        if (isShutdown.compareAndSet(false, true)) {
            requestChannel.close()
            scope.cancel()
            dispatcher.close()
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun processRequest(request: DecodeRequest) {
        if (request.cancelled.get()) {
            request.onResult(emptyList())
            return
        }

        val originalSizes = decoder.originalPageSizes
        if (request.pageIndex >= originalSizes.size) {
            request.onResult(emptyList())
            return
        }

        val originalSize = originalSizes[request.pageIndex]
        val pageWidthPts = originalSize.width.toFloat()
        val pageHeightPts = originalSize.height.toFloat()
        val renderDpi = renderDpiForPage(pageWidthPts, pageHeightPts)

        // ── 1. Try disk cache ─────────────────────────────────────────────────
        val cacheKey = tileCache.buildKey(
            pageNumber = request.pageIndex,
            targetWidth = screenWidth,
            targetHeight = screenHeight,
            densityDpi = densityDpi,
            renderDpi = renderDpi,
            fontSize = request.fontSize,
            wordSpacing = request.wordSpacing,
            crop = false,
            orientation = orientation,
            pageSliceBounds = null,
        )
        val cached = tileCache.load(cacheKey)
        if (cached != null) {
            Logger.d("ReflowDecodeService", "cache HIT page=${request.pageIndex}")
            request.onResult(cached)
            return
        }

        if (request.cancelled.get()) {
            request.onResult(emptyList())
            return
        }

        // ── 2. Render source page via ImageDecoder ────────────────────────────
        val scale = renderDpi / 72f
        val renderWidth = (pageWidthPts * scale).toInt().coerceAtLeast(1)
        val renderHeight = (pageHeightPts * scale).toInt().coerceAtLeast(1)

        val aPageList = decoder.aPageList
        val aPage: APage = if (!aPageList.isNullOrEmpty() && request.pageIndex < aPageList.size) {
            aPageList[request.pageIndex]
        } else {
            APage(request.pageIndex, pageWidthPts.toInt(), pageHeightPts.toInt(), 1f)
        }

        val sourceImage: ImageBitmap = try {
            decoder.renderPage(
                aPage = aPage,
                viewSize = IntSize(screenWidth, screenHeight),
                outWidth = renderWidth,
                outHeight = renderHeight,
                crop = false
            )
        } catch (e: Exception) {
            Logger.e(
                "ReflowDecodeService",
                "renderPage failed page=${request.pageIndex}: ${e.message}",
                e
            )
            request.onResult(emptyList())
            return
        }

        if (request.cancelled.get()) {
            request.onResult(emptyList())
            return
        }

        // sourceDpi = 72 × imageWidth / pageWidthPts
        val sourceDpi = if (sourceImage.width > 0 && pageWidthPts > 0f) {
            (72f * sourceImage.width / pageWidthPts).coerceAtLeast(1f)
        } else {
            renderDpi
        }

        // ── 3. Run k2pdfopt ───────────────────────────────────────────────────
        val tiles = k2pdf2bitmap(
            bitmap = sourceImage,
            fontSize = request.fontSize,
            viewWidth = screenWidth,
            viewHeight = screenHeight,
            densityDpi = densityDpi,
            sourceDpi = sourceDpi,
            wordSpacing = request.wordSpacing,
        )

        // ── 4. Persist to disk cache (best-effort, background) ───────────────
        if (tiles.isNotEmpty()) {
            tileCache.save(cacheKey, tiles)
        }

        Logger.d("ReflowDecodeService", "decoded page=${request.pageIndex}, tiles=${tiles.size}")
        request.onResult(tiles)
    }

    // ── Static helpers ────────────────────────────────────────────────────────

    private fun renderDpiForPage(pageWidthPts: Float, pageHeightPts: Float): Float {
        if (pageWidthPts <= 0f || pageHeightPts <= 0f) return 400f
        val areaInches = (pageWidthPts / 72f) * (pageHeightPts / 72f)
        if (areaInches <= 0f) return 400f
        return sqrt(8_000_000f / areaInches).coerceIn(120f, 400f)
    }

    private fun k2pdf2bitmap(
        bitmap: ImageBitmap,
        fontSize: Float,
        viewWidth: Int,
        viewHeight: Int,
        densityDpi: Int,
        sourceDpi: Float,
        wordSpacing: Float,
    ): List<ImageBitmap> {
        val devDpi = if (sourceDpi > 1f) {
            minOf(densityDpi.toFloat(), sourceDpi).toInt()
        } else {
            densityDpi
        }
        return renderReflowTiles(
            source = bitmap,
            fontSize = fontSize,
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            devDpi = devDpi,
            sourceDpi = sourceDpi,
            wordSpacing = wordSpacing,
        )
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    private class DecodeRequest(
        val pageIndex: Int,
        val fontSize: Float,
        val wordSpacing: Float,
        val onResult: (List<ImageBitmap>) -> Unit,
        val cancelled: AtomicBoolean = AtomicBoolean(false),
    )
}
