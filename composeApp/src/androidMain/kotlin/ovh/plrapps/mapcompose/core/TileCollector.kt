package ovh.plrapps.mapcompose.core

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.selects.select
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * The engine.The view-model uses two channels to communicate with the [TileCollector]:
 * * one to send [TileSpec]s (a [SendChannel])
 * * one to receive [TileSpec]s (a [ReceiveChannel])
 *
 * The [TileCollector] encapsulates all the complexity that transforms a [TileSpec] into a [Tile].
 * ```
 *                                              _____________________________________________________________________
 *                                             |                           TileCollector             ____________    |
 *                                  tiles      |                                                    |  ________  |   |
 *              ---------------- [*********] <----------------------------------------------------- | | worker | |   |
 *             |                               |                                                    |  --------  |   |
 *             ↓                               |                                                    |  ________  |   |
 *  _____________________                      |                                   tileSpecs        | | worker | |   |
 * | TileCanvasViewModel |                     |    _____________________  <---- [**********] <---- |  --------  |   |
 *  ---------------------  ----> [*********] ----> | tileCollectorKernel |                          |  ________  |   |
 *                                tileSpecs    |    ---------------------  ----> [**********] ----> | | worker | |   |
 *                                             |                                   tileSpecs        |  --------  |   |
 *                                             |                                                    |____________|   |
 *                                             |                                                      worker pool    |
 *                                             |                                                                     |
 *                                              ---------------------------------------------------------------------
 * ```
 * This architecture is an example of Communicating Sequential Processes (CSP).
 *
 * @author p-lr on 22/06/19
 */
internal class TileCollector(
    private val workerCount: Int,
    private val tileSize: Int,
    private val decoder: com.archko.reader.pdf.subsampling.PdfDecoder,
) {
    @Volatile
    var isIdle: Boolean = true

    /**
     * Sets up the tile collector machinery. The architecture is inspired from
     * [Kotlin Conf 2018](https://www.youtube.com/watch?v=a3agLJQ6vt8).
     * It support back-pressure, and avoids deadlock in CSP taking into account recommendations of
     * this [article](https://medium.com/@elizarov/deadlocks-in-non-hierarchical-csp-e5910d137cc),
     * which is from the same author.
     *
     * @param [tileSpecs] channel of [TileSpec], which capacity should be [Channel.RENDEZVOUS].
     * @param [tilesOutput] channel of [Tile], which should be set as [Channel.RENDEZVOUS].
     */
    suspend fun collectTiles(
        tileSpecs: ReceiveChannel<TileSpec>,
        tilesOutput: SendChannel<Tile>,
    ) = coroutineScope {
        val tilesToDownload = Channel<TileSpec>(capacity = Channel.RENDEZVOUS)
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
        tilesOutput: SendChannel<Tile>,
    ) = launch(dispatcher) {
        for (spec in tilesToDownload) {
            val bitmapForLayers = async {
                // 直接使用TileSpec中的页码和偏移量信息
                val pageIndex = spec.pageIndex
                val tileInPageX = spec.pageOffsetX
                val tileInPageY = spec.pageOffsetY
                
                // 确保tile在页面范围内
                val pageWidth = decoder.pageSizes[pageIndex].width
                val pageHeight = decoder.pageSizes[pageIndex].height
                
                if (tileInPageX >= pageWidth || tileInPageY >= pageHeight) {
                    println("TileCollector: tile out of page bounds: spec=$spec, pageIndex=$pageIndex, tileInPageX=$tileInPageX, tileInPageY=$tileInPageY, pageWidth=$pageWidth, pageHeight=$pageHeight")
                    return@async BitmapForLayer(null)
                }
                
                // 计算tile的实际大小
                // 使用固定的tileSize，但最后一个tile可能更小
                val currentTileWidth = min(tileSize, pageWidth - tileInPageX)
                val currentTileHeight = min(tileSize, pageHeight - tileInPageY)
                
                // 解码时使用1.0的scale，因为Canvas已经处理了缩放变换
                val decodeScale = 1.0f
                
                // 使用PdfDecoder解码指定区域
                val region = androidx.compose.ui.geometry.Rect(
                    left = tileInPageX.toFloat(),
                    top = tileInPageY.toFloat(),
                    right = (tileInPageX + currentTileWidth).toFloat(),
                    bottom = (tileInPageY + currentTileHeight).toFloat()
                )
                
                try {
                    println("TileCollector: decoding tile spec=$spec, region=$region, pageIndex=$pageIndex, decodeScale=$decodeScale, tileInPageX=$tileInPageX, tileInPageY=$tileInPageY")
                    val bitmap = decoder.renderPageRegion(
                        region = region,
                        index = pageIndex,
                        scale = decodeScale,
                        viewSize = androidx.compose.ui.unit.IntSize(currentTileWidth, currentTileHeight),
                        pageWidth = pageWidth,
                        pageHeight = pageHeight
                    )
                    BitmapForLayer(bitmap.asAndroidBitmap())
                } catch (e: Exception) {
                    println("TileCollector: decode error for tile $spec: ${e.message}")
                    e.printStackTrace()
                    BitmapForLayer(null)
                }
            }.await()

            val resultBitmap = bitmapForLayers.bitmap ?: run {
                tilesDownloaded.send(spec)
                /* When the decoding failed or if there's nothing to decode, then send back the Tile
                 * just as in normal processing, so that the actor which submits tiles specs to the
                 * collector knows that this tile has been processed and does not immediately
                 * re-sends the same spec. */
                tilesOutput.send(
                    Tile(
                        spec.zoom,
                        spec.level,
                        spec.pageIndex,
                        spec.pageOffsetX,
                        spec.pageOffsetY,
                    )
                )
                null
            } ?: continue // If the decoding failed, skip the rest

            //println("TileCollector: successfully decoded tile zoom:${spec.zoom}, level:${spec.level}, page:${spec.pageIndex}, subSample:${spec.subSample}")
            val tile = Tile(
                spec.zoom,
                spec.level,
                spec.pageIndex,
                spec.pageOffsetX,
                spec.pageOffsetY,
            ).apply {
                this.bitmap = resultBitmap
            }
            tilesOutput.send(tile)
            tilesDownloaded.send(spec)
        }
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
                        /* Add it to the list of specs being processed */
                        specsBeingProcessed.add(it)
                        isIdle = false

                        /* Now download the tile */
                        tilesToDownload.send(it)
                    }
                }
            }
        }
    }

    /**
     * Attempts to stop all actively executing tasks, halts the processing of waiting tasks.
     */
    fun shutdownNow() {
        executor.shutdownNow()
    }

    /**
     * When using a [LinkedBlockingQueue], the core pool size mustn't be 0, or the active thread
     * count won't be greater than 1. Previous versions used a [SynchronousQueue], which could have
     * a core pool size of 0 and a growing count of active threads. However, a [Runnable] could be
     * rejected when no thread were available. Starting from kotlinx.coroutines 1.4.0, this cause
     * the associated coroutine to be cancelled. By using a [LinkedBlockingQueue], we avoid rejections.
     */
    private val executor = ThreadPoolExecutor(
        workerCount, workerCount,
        60L, TimeUnit.SECONDS, LinkedBlockingQueue()
    ).apply {
        allowCoreThreadTimeOut(true)
    }
    private val dispatcher = executor.asCoroutineDispatcher()
}

private data class BitmapForLayer(val bitmap: Bitmap?)