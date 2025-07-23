package com.archko.reader.pdf.decoder

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

public class DecoderService(
    private val workerCount: Int,
    private val decoder: ImageDecoder,
) {
    @Volatile
    public var isIdle: Boolean = true
    public suspend fun collectTiles(
        tileSpecs: ReceiveChannel<TileSpec>,
        tilesOutput: SendChannel<TileSpec>,
    ): Job = coroutineScope {
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

            tilesOutput.send(spec)
            tilesDownloaded.send(spec)
        }
    }

    private fun decodeBitmap(spec: TileSpec): ImageBitmap {
        val totalScale = spec.pageScale * spec.vZoom
        // 逻辑坐标转原始像素区域
        val srcRect = Rect(
            left = spec.logicalRect.left * spec.pageWidth*totalScale,
            top = spec.logicalRect.top * spec.pageHeight*totalScale,
            right = spec.logicalRect.right * spec.pageWidth*totalScale,
            bottom = spec.logicalRect.bottom * spec.pageHeight*totalScale
        )
        val outWidth = ((srcRect.right - srcRect.left)).toInt()
        val outHeight = ((srcRect.bottom - srcRect.top)).toInt()
        println("decodeBitmap.Tile:page:${spec.page}, rect:${spec.logicalRect}, scale:${spec.pageScale},${spec.vZoom}, $outWidth-$outHeight, $srcRect")
        return decoder.renderPageRegion(
            srcRect,
            spec.page,
            totalScale,
            spec.viewSize,
            outWidth,
            outHeight
        )
    }

    /*private fun testBitmap(spec: TileSpec): ImageBitmap {
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
    }*/

    private fun CoroutineScope.tileCollectorKernel(
        tileSpecs: ReceiveChannel<TileSpec>,
        tilesToDownload: SendChannel<TileSpec>,
        tilesDownloadedFromWorker: ReceiveChannel<TileSpec>,
    ) = launch(Dispatchers.Default) {

        val specsBeingProcessed = mutableListOf<TileSpec>()

        while (true) {
            select {
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

    public fun shutdownNow() {
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

public data class TileSpec(
    val page: Int,
    val pageScale: Float,
    val vZoom: Float,
    val logicalRect: Rect, // 0~1
    val pageWidth: Int,
    val pageHeight: Int,
    val viewSize: IntSize,
    val cacheKey: String,
    var imageBitmap: ImageBitmap?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TileSpec

        if (page != other.page) return false
        if (pageScale != other.pageScale) return false
        if (vZoom != other.vZoom) return false
        if (pageWidth != other.pageWidth) return false
        if (pageHeight != other.pageHeight) return false
        if (logicalRect != other.logicalRect) return false
        if (cacheKey != other.cacheKey) return false

        return true
    }

    override fun hashCode(): Int {
        var result = page
        result = 31 * result + pageScale.hashCode()
        result = 31 * result + vZoom.hashCode()
        result = 31 * result + pageWidth
        result = 31 * result + pageHeight
        result = 31 * result + logicalRect.hashCode()
        result = 31 * result + cacheKey.hashCode()
        return result
    }
}