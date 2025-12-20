package com.archko.reader.pdf.component

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import com.archko.reader.pdf.entity.APage

/**
 * 解码任务
 * @author: archko 2025/1/10
 */
public data class DecodeTask(
    public val type: TaskType,
    public val pageIndex: Int,
    public val decodeKey: String,
    public val aPage: APage,
    public val zoom: Float = 1f,
    public val pageSliceBounds: Rect,
    public val width: Int,
    public val height: Int,
    public val crop: Boolean = false,
    public val callback: DecodeCallback? = null
) {
    public enum class TaskType {
        PAGE,
        NODE,
        CROP
    }
}

/**
 * 解码回调接口
 */
public interface DecodeCallback {
    public fun onDecodeComplete(bitmap: ImageBitmap?, isThumb: Boolean, error: Throwable?)
    public fun shouldRender(pageNumber: Int, isFullPage: Boolean): Boolean
    public fun onFinish(pageNumber: Int)
}

/**
 * 解码器接口
 */
public interface Decoder {
    public suspend fun decodePage(task: DecodeTask): ImageBitmap?
    public suspend fun decodeNode(task: DecodeTask): ImageBitmap?
    public suspend fun processCrop(task: DecodeTask): CropResult?
    public fun generateCropTasks(): List<DecodeTask>
}

/**
 * 切边处理结果
 */
public data class CropResult(
    public val pageIndex: Int,
    public val cropBounds: Rect?
)
