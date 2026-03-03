package com.archko.reader.pdf.entity

import androidx.compose.ui.geometry.Offset

/**
 * MuPDF Quad类的表示（使用Offset简化）
 * @author: archko 2026/3/1
 */
public data class DocQuad(
    val ul: Offset,  // 左上角
    val ur: Offset,  // 右上角
    val ll: Offset,  // 左下角
    val lr: Offset   // 右下角
)
