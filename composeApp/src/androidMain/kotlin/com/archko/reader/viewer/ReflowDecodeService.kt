package com.archko.reader.viewer

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.IntSize
import com.archko.reader.k2pdfopt.K2PdfOpt
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.APage
import com.archko.reader.viewer.utils.Logger
import com.archko.reader.viewer.utils.ReflowCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.launch
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
    private val context: Context,
    private val decoder: ImageDecoder,
    private val documentPath: String,
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val densityDpi: Int,
    private val orientation: Int = 0,
) {
    // ── Single-thread coroutine worker ────────────────────────────────────────
    private val dispatcher = newSingleThreadContext("ReflowDecode")
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
    private val tileCache = ReflowCache.create(context, documentPath)

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
    ): List<Bitmap> = suspendCancellableCoroutine { cont ->
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

        val imageBitmap = try {
            decoder.renderPage(
                aPage = aPage,
                viewSize = IntSize(screenWidth, screenHeight),
                outWidth = renderWidth,
                outHeight = renderHeight,
                crop = false
            )
        } catch (e: Exception) {
            Logger.e("ReflowDecodeService", "renderPage failed page=${request.pageIndex}: ${e.message}", e)
            request.onResult(emptyList())
            return
        }

        if (request.cancelled.get()) {
            request.onResult(emptyList())
            return
        }

        val sourceBitmap: Bitmap = try {
            imageBitmap.asAndroidBitmap()
        } catch (e: Exception) {
            Logger.e("ReflowDecodeService", "asAndroidBitmap failed page=${request.pageIndex}: ${e.message}", e)
            request.onResult(emptyList())
            return
        }

        // sourceDpi = 72 × bitmapWidth / pageWidthPts
        val sourceDpi = if (sourceBitmap.width > 0 && pageWidthPts > 0f) {
            (72f * sourceBitmap.width / pageWidthPts).coerceAtLeast(1f)
        } else {
            renderDpi
        }

        // ── 3. Run k2pdfopt ───────────────────────────────────────────────────
        val tiles = k2pdf2bitmap(
            bitmap = sourceBitmap,
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
        bitmap: Bitmap,
        fontSize: Float,
        viewWidth: Int,
        viewHeight: Int,
        densityDpi: Int,
        sourceDpi: Float,
        wordSpacing: Float,
    ): List<Bitmap> {
        val list = mutableListOf<Bitmap>()
        val devDpi = if (sourceDpi > 1f) {
            minOf(densityDpi.toFloat(), sourceDpi).toInt()
        } else {
            densityDpi
        }
        val opt = K2PdfOpt()
        try {
            opt.create(viewWidth, viewHeight, devDpi)
            opt.setSourceDpi(sourceDpi)
            opt.setWordSpacing(wordSpacing)
            opt.fontSize = fontSize
            opt.load(bitmap)

            val pageWidth = opt.pageWidth
            val pageHeight = opt.pageHeight
            if (pageWidth <= 0 || pageHeight <= 0) {
                Logger.w("ReflowDecodeService", "k2pdfopt empty output: $pageWidth×$pageHeight")
                return list
            }

            val tileHeight = if (viewHeight > 0) viewHeight else 1024
            var y = 0
            while (y < pageHeight) {
                val h = minOf(tileHeight, pageHeight - y)
                val tile = opt.renderTile(0, y, pageWidth, h) ?: break
                list.add(tile)
                y += h
            }
        } catch (e: Exception) {
            Logger.e("ReflowDecodeService", "k2pdfopt error: ${e.message}", e)
        } finally {
            try { opt.releasePage() } catch (_: Exception) { }
        }
        return list
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    private class DecodeRequest(
        val pageIndex: Int,
        val fontSize: Float,
        val wordSpacing: Float,
        val onResult: (List<Bitmap>) -> Unit,
        val cancelled: AtomicBoolean = AtomicBoolean(false),
    )
}
