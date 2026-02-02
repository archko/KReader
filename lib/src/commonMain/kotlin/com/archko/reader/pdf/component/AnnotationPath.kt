package com.archko.reader.pdf.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * 定义单条线段
 * @author: archko 2026/2/2 :16:35
 */
public data class AnnotationPath(
    val points: List<Offset>, // 这里的 Offset 是相对于页面宽高的比例 (0~1)
    val color: Color = Color.Red,
    val strokeWidth: Float = 3f
)