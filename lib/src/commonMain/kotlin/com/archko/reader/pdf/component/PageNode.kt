package com.archko.reader.pdf.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import com.archko.reader.pdf.cache.BitmapState
import com.archko.reader.pdf.cache.ImageCache
import java.util.concurrent.atomic.AtomicBoolean

internal const val TILE_CONTENT_PX: Float = 1024f
internal const val TILE_LONG_CONTENT_PX: Float = 1536f

/**
 * level 以 1/8 档为步进，textureScale = 2^(level/8)。
 * 2 的幂档位会让纹理最多 2 倍于屏幕分辨率，tile 在屏幕上缩到 512px，
 * 可见 tile 数量随之翻几倍（放大后尤其明显），所以这里用细档位。
 */
internal const val LEVEL_STEPS_PER_OCTAVE: Int = 8
internal const val MIN_TILE_LEVEL: Int = -6 * LEVEL_STEPS_PER_OCTAVE
internal const val MAX_TILE_LEVEL: Int = 8 * LEVEL_STEPS_PER_OCTAVE

private const val TILE_COL_BITS = 24
private const val TILE_ROW_BITS = 24
private const val TILE_COL_MASK = (1L shl TILE_COL_BITS) - 1
private const val TILE_ROW_MASK = (1L shl TILE_ROW_BITS) - 1

/**
 * 页内 tile 的唯一 key：level/row/col 打包成 Long。
 * 查找时不再创建对象，也没有装箱/哈希分配。
 */
internal fun tileKey(level: Int, row: Int, col: Int): Long {
    return ((level + 128).toLong() shl (TILE_ROW_BITS + TILE_COL_BITS)) or
            ((row.toLong() and TILE_ROW_MASK) shl TILE_COL_BITS) or
            (col.toLong() and TILE_COL_MASK)
}

/**
 * 一个 LOD tile。
 * fraction 是页内归一化矩形；srcRect 是解码器需要的 level 纹理像素区域。
 */
public class PageNode internal constructor(
    internal val key: Long,
    internal val cacheKey: String,
    internal val level: Int,
    internal val row: Int,
    internal val col: Int,
    internal val fraction: Rect,
    internal val srcRect: Rect,
    internal val outWidth: Int,
    internal val outHeight: Int,
    internal val textureScale: Float,
    internal val crop: Boolean
) {
    internal var bitmap: BitmapState? by mutableStateOf(null)
    internal val queued: AtomicBoolean = AtomicBoolean(false)
    internal val removed: AtomicBoolean = AtomicBoolean(false)
    internal var requestAt: Long = 0L

    internal fun docRect(base: Rect): Rect {
        return Rect(
            base.left + fraction.left * base.width,
            base.top + fraction.top * base.height,
            base.left + fraction.right * base.width,
            base.top + fraction.bottom * base.height
        )
    }

    internal fun release() {
        removed.set(true)
        bitmap?.let { ImageCache.releaseNode(it) }
        bitmap = null
        queued.set(false)
    }
}
