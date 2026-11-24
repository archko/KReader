package com.archko.reader.pdf.util

import androidx.compose.ui.graphics.ImageBitmap

internal const val TILE_FORMAT_PNG = 1
internal const val TILE_FORMAT_WEBP = 2

internal class EncodedTile(
    val format: Int,
    val bytes: ByteArray,
)

/**
 * 把一页重排结果中的横条图片编码成缓存文件里保存的字节。
 * Android 使用 WebP/PNG 压缩，JVM 使用 ImageIO。
 */
internal expect fun encodeReflowTile(image: ImageBitmap): EncodedTile?

internal expect fun decodeReflowTile(bytes: ByteArray): ImageBitmap?

internal expect fun recycleReflowTile(image: ImageBitmap)
