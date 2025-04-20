package com.archko.reader.viewer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.createBitmap
import com.archko.reader.pdf.subsampling.PdfDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal class DecoderService(
    private val workerCount: Int,
    private val decoder: PdfDecoder,
) {
    @Volatile
    var isIdle: Boolean = true
    suspend fun collectTiles(
        tileSpecs: ReceiveChannel<TileSpec>,
        tilesOutput: SendChannel<TileSpec>,
    ) = coroutineScope {
        val tilesToDownload = Channel<TileSpec>(capacity = Channel.Factory.RENDEZVOUS)
        val tilesDownloadedFromWorker = Channel<TileSpec>(capacity = 1)

        repeat(workerCount) {
            worker(
                tilesToDownload,
                tilesDownloadedFromWorker,
                tilesOutput,
            )
        }
        tileCollectorKernel(tileSpecs, tilesToDownload, tilesDownloadedFromWorker)
    }

    private fun CoroutineScope.worker(
        tilesToDownload: ReceiveChannel<TileSpec>,
        tilesDownloaded: SendChannel<TileSpec>,
        tilesOutput: SendChannel<TileSpec>,
    ) = launch(dispatcher) {
        for (spec in tilesToDownload) {
            val imageBitmap = async {
                //testBitmap(spec)
                decodeBitmap(spec)
            }.await()
            spec.imageBitmap = imageBitmap

            println("getBitmap.Tile:page:${spec.page}, w-h:${spec.width}-${spec.height}, zoom:${spec.zoom}, ${spec.rect}")
            tilesOutput.send(spec)
            tilesDownloaded.send(spec)
        }
    }

    private fun decodeBitmap(spec: TileSpec): ImageBitmap {
        return decoder.renderPageRegion(
            spec.rect,
            spec.page,
            spec.zoom,
            spec.viewSize,
            spec.width,
            spec.height
        )
    }

    private fun testBitmap(spec: TileSpec): ImageBitmap {
        val bitmap = createBitmap(spec.width, spec.height, Bitmap.Config.ARGB_8888)
        val paint = Paint()
        val canvas = Canvas(bitmap)
        paint.textSize = 32f
        paint.strokeWidth = 4f
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        canvas.drawARGB(
            255,
            255 * (spec.rect.left.toInt() % 150),
            255 * (spec.rect.top.toInt() % 150),
            0
        )
        val rect = Rect(0, 0, bitmap.getWidth(), bitmap.getHeight())
        paint.setColor(Color.GREEN)
        canvas.drawRect(rect, paint)
        paint.setColor(Color.RED)
        canvas.drawText(
            "page:${spec.page}, ${spec.width}-${spec.height}, scale:${spec.zoom}",
            20f,
            140f,
            paint
        )
        canvas.drawText(
            "(${spec.rect.left}, ${spec.rect.top}, ${spec.rect.right}, ${spec.rect.bottom})",
            20f,
            220f,
            paint
        )
        return bitmap.asImageBitmap()
    }

    private fun CoroutineScope.tileCollectorKernel(
        tileSpecs: ReceiveChannel<TileSpec>,
        tilesToDownload: SendChannel<TileSpec>,
        tilesDownloadedFromWorker: ReceiveChannel<TileSpec>,
    ) = launch(Dispatchers.Default) {

        val specsBeingProcessed = mutableListOf<TileSpec>()

        while (true) {
            select<Unit> {
                tilesDownloadedFromWorker.onReceive {
                    specsBeingProcessed.remove(it)
                    isIdle = specsBeingProcessed.isEmpty()
                }
                tileSpecs.onReceive {
                    if (it !in specsBeingProcessed) {
                        specsBeingProcessed.add(it)
                        isIdle = false

                        tilesToDownload.send(it)
                    }
                }
            }
        }
    }

    fun shutdownNow() {
        executor.shutdownNow()
        decoder.close()
    }

    private val executor = ThreadPoolExecutor(
        workerCount, workerCount,
        60L, TimeUnit.SECONDS, LinkedBlockingQueue()
    ).apply {
        allowCoreThreadTimeOut(true)
    }
    private val dispatcher = executor.asCoroutineDispatcher()
}

internal data class TileSpec(
    val page: Int,
    val zoom: Float,
    val rect: androidx.compose.ui.geometry.Rect,
    val width: Int = 512,
    val height: Int = 512,
    val viewSize: IntSize,
    val cacheKey: String,
    var imageBitmap: ImageBitmap?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TileSpec

        if (page != other.page) return false
        if (zoom != other.zoom) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (rect != other.rect) return false
        if (cacheKey != other.cacheKey) return false

        return true
    }

    override fun hashCode(): Int {
        var result = page
        result = 31 * result + zoom.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + rect.hashCode()
        result = 31 * result + cacheKey.hashCode()
        return result
    }
}