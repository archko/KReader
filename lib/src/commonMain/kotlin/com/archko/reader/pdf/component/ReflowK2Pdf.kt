package com.archko.reader.viewer

import androidx.compose.ui.graphics.ImageBitmap

/**
 * 调用平台 k2pdfopt 对单页图片做重排，并按视口高度切成横条。
 *
 * Android 与 JVM 的 k2pdfopt 原生接口不同（Bitmap / BufferedImage），
 * 因此在这里用 expect/actual 隔离。
 */
internal expect fun renderReflowTiles(
    source: ImageBitmap,
    fontSize: Float,
    viewWidth: Int,
    viewHeight: Int,
    devDpi: Int,
    sourceDpi: Float,
    wordSpacing: Float,
): List<ImageBitmap>
