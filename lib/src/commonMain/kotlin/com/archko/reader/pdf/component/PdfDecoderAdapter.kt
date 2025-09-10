package com.archko.reader.pdf.component

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.util.SmartCropUtils

/**
 * 将现有的ImageDecoder适配到新的Decoder接口
 * @author: archko 2025/1/10
 */
public class PdfDecoderAdapter(
    private val imageDecoder: ImageDecoder,
    private val viewSize: IntSize,
    private val isCropEnabled: () -> Boolean
) : Decoder {

    override suspend fun decodePage(task: DecodeTask): ImageBitmap? {
        return try {
            val aPage = task.aPage
            val ratio: Float = 1f * aPage.width / (aPage.getWidth(task.crop) * task.zoom)
            val thumbWidth = (300 * task.zoom).toInt()
            val thumbHeight = (aPage.height / ratio).toInt()

            imageDecoder.renderPage(
                aPage = aPage,
                viewSize = viewSize,
                outWidth = thumbWidth,
                outHeight = thumbHeight,
                crop = task.crop
            )
        } catch (e: Exception) {
            println("PdfDecoderAdapter.decodePage error: ${e.message}")
            null
        }
    }

    override suspend fun decodeNode(task: DecodeTask): ImageBitmap? {
        return imageDecoder.renderPageRegion(
            task.pageSliceBounds,
            task.pageIndex,
            task.zoom,
            IntSize.Zero,
            task.width,
            task.height
        )
        /*return try {
            val aPage = task.aPage
            val pageSliceBounds = task.pageSliceBounds ?: Rect(0f, 0f, 1f, 1f)
            
            // 计算切边区域
            val cropBounds = if (task.crop && aPage.cropBounds != null) {
                aPage.cropBounds!!
            } else {
                Rect(0f, 0f, aPage.width.toFloat(), aPage.height.toFloat())
            }
            
            // 计算实际渲染区域
            val srcRect = Rect(
                left = pageSliceBounds.left * cropBounds.width + cropBounds.left,
                top = pageSliceBounds.top * cropBounds.height + cropBounds.top,
                right = pageSliceBounds.right * cropBounds.width + cropBounds.left,
                bottom = pageSliceBounds.bottom * cropBounds.height + cropBounds.top
            )
            
            val outWidth = ((srcRect.right - srcRect.left) * task.zoom).toInt()
            val outHeight = ((srcRect.bottom - srcRect.top) * task.zoom).toInt()
            
            imageDecoder.renderPageRegion(
                region = srcRect,
                index = task.pageIndex,
                scale = task.zoom,
                viewSize = viewSize,
                outWidth = outWidth,
                outHeight = outHeight
            )
        } catch (e: Exception) {
            println("PdfDecoderAdapter.decodeNode error: ${e.message}")
            null
        }*/
    }

    override suspend fun processCrop(task: DecodeTask): CropResult? {
        return try {
            val aPage = task.aPage

            // 如果已经有切边信息，直接返回
            if (aPage.cropBounds != null) {
                return CropResult(task.pageIndex, aPage.cropBounds)
            }

            // 渲染缩略图用于切边检测
            val thumbWidth = 300
            val ratio: Float = 1f * aPage.width / thumbWidth
            val thumbHeight = (aPage.height / ratio).toInt()

            val thumbBitmap = imageDecoder.renderPage(
                aPage = aPage,
                viewSize = viewSize,
                outWidth = thumbWidth,
                outHeight = thumbHeight,
                crop = false // 切边检测时不使用已有的切边信息
            )

            // 检测切边区域
            val cropBounds = SmartCropUtils.detectSmartCropBounds(thumbBitmap)

            val finalCropBounds = if (cropBounds != null) {
                // 将缩略图坐标转换为原始PDF坐标
                Rect(
                    left = cropBounds.left * ratio,
                    top = cropBounds.top * ratio,
                    right = cropBounds.right * ratio,
                    bottom = cropBounds.bottom * ratio
                )
            } else {
                // 如果检测失败，使用整个页面
                Rect(0f, 0f, aPage.width.toFloat(), aPage.height.toFloat())
            }

            println("PdfDecoderAdapter.processCrop: page=${task.pageIndex}, cropBounds=$finalCropBounds")

            CropResult(task.pageIndex, finalCropBounds)
        } catch (e: Exception) {
            println("PdfDecoderAdapter.processCrop error: ${e.message}")
            CropResult(task.pageIndex, null)
        }
    }

    override fun generateCropTasks(): List<DecodeTask> {
        val tasks = mutableListOf<DecodeTask>()

        if (!isCropEnabled()) {
            return tasks
        }

        // 为所有没有切边信息的页面生成切边任务
        imageDecoder.aPageList?.forEachIndexed { index, aPage ->
            if (aPage.cropBounds == null) {
                val task = DecodeTask(
                    type = DecodeTask.TaskType.CROP,
                    pageIndex = index,
                    decodeKey = "crop-$index",
                    aPage = aPage,
                    1f,
                    Rect(0f,0f,1f,1f),
                    1,
                    1,
                    crop = true
                )
                tasks.add(task)
            }
        }

        println("PdfDecoderAdapter.generateCropTasks: generated ${tasks.size} crop tasks")
        return tasks
    }
}