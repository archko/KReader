package com.archko.reader.pdf.decoder

import androidx.compose.runtime.snapshots.SnapshotStateMap
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
    private val isTileVisible: ((TileSpec) -> Boolean)? = null, // 新增
    private val stateImageCache: SnapshotStateMap<String, ImageBitmap?>? = null // 新增，可选Compose StateMap
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
            // 新增：解码前判断可见性
            if (isTileVisible != null && !isTileVisible.invoke(spec)) {
                println("DecoderService.not visible:$spec")
                tilesDownloaded.send(spec) // 仍然要通知已处理，避免队列阻塞
                continue
            }
            val imageBitmap = async {
                //testBitmap(spec)
                decodeBitmap(spec)
            }.await()
            spec.imageBitmap = imageBitmap
            // 删除所有 ImageCache.put、ImageCache.get、ImageCache.remove、ImageCache.clear 相关代码
            // 新增：同步到 Compose StateMap
            stateImageCache?.set(spec.cacheKey, imageBitmap)

            tilesOutput.send(spec)
            tilesDownloaded.send(spec)
        }
    }

    private fun decodeBitmap(spec: TileSpec): ImageBitmap {
        // 逻辑坐标转原始像素区域（原始宽高）
        val srcRect = Rect(
            left = spec.bounds.left * spec.pageWidth,
            top = spec.bounds.top * spec.pageHeight,
            right = spec.bounds.right * spec.pageWidth,
            bottom = spec.bounds.bottom * spec.pageHeight
        )
        val outWidth = ((srcRect.right - srcRect.left)).toInt()
        val outHeight = ((srcRect.bottom - srcRect.top)).toInt()
        //println("decodeBitmap.Tile:page:${spec.page}, rect:${spec.bounds}, scale:${spec.pageScale}, $outWidth-$outHeight, $srcRect")
        return decoder.renderPageRegion(
            srcRect,
            spec.page,
            spec.pageScale, // totalScale
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
    val bounds: Rect, // 0~1
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
        if (pageWidth != other.pageWidth) return false
        if (pageHeight != other.pageHeight) return false
        if (bounds != other.bounds) return false
        if (cacheKey != other.cacheKey) return false

        return true
    }

    override fun hashCode(): Int {
        var result = page
        result = 31 * result + pageScale.hashCode()
        result = 31 * result + pageWidth
        result = 31 * result + pageHeight
        result = 31 * result + bounds.hashCode()
        result = 31 * result + cacheKey.hashCode()
        return result
    }
}