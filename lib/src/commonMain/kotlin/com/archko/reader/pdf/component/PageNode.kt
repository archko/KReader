package com.archko.reader.pdf.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import com.archko.reader.pdf.cache.BitmapState
import com.archko.reader.pdf.cache.ImageCache

internal const val TILE_CONTENT_PX: Float = 512f
internal const val MIN_TILE_LEVEL: Int = -6
internal const val MAX_TILE_LEVEL: Int = 8

/**
 * tile 身份：页内局部，不含文档位置与缩放。
 * level 允许为负（大页在 fit 缩放下就需要较低的纹理分辨率）。
 */
internal data class TileId(
    val page: Int,
    val geometryId: Int,
    val level: Int,
    val row: Int,
    val col: Int
) {
    val key: String get() = "tile/$page/$geometryId/$level/$row/$col"
}

/**
 * 一个 LOD tile。
 * fraction 是页内归一化矩形；srcRect 是解码器需要的 level 纹理像素区域。
 */
public class PageNode internal constructor(
    internal val page: Page,
    internal val id: TileId,
    internal val fraction: Rect,
    internal val srcRect: Rect,
    internal val outWidth: Int,
    internal val outHeight: Int,
    internal val textureScale: Float,
    internal val crop: Boolean
) {
    internal var bitmap: BitmapState? by mutableStateOf(null)
    internal var queued: Boolean = false
    internal var removed: Boolean = false

    internal fun docRect(base: Rect): Rect {
        return Rect(
            base.left + fraction.left * base.width,
            base.top + fraction.top * base.height,
            base.left + fraction.right * base.width,
            base.top + fraction.bottom * base.height
        )
    }

    internal fun release() {
        removed = true
        bitmap?.let { ImageCache.releaseNode(it) }
        bitmap = null
        queued = false
    }
}
