package com.archko.reader.pdf.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * 定义单条线段
 * @author: archko 2026/2/2 :16:35
 */
public data class AnnotationPath(
    val points: List<Offset>,
    val config: PathConfig,
)

public data class PathConfig(
    val color: Color = Color.Red,
    val strokeWidth: Float = 4f,
    val drawType: DrawType = DrawType.CURVE
)

public enum class DrawType { CURVE, LINE }