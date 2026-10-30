package com.archko.reader.pdf.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

/**
 * 定义单条线段
 * @author: archko 2026/2/2 :16:35
 */
public data class AnnotationPath(
    val points: List<Offset>,
    val config: PathConfig,
) {
    internal var cachedPath: Path? = null
    internal var cachedPathBase: Rect? = null
    internal var cachedPathPoints: List<Offset>? = null
}

public data class PathConfig(
    val color: Color = Color.Red,
    val strokeWidth: Float = 4f,
    val drawType: DrawType = DrawType.LINE
) {
    override fun toString(): String {
        return "PathConfig(color=$color, strokeWidth=$strokeWidth, drawType=$drawType)"
    }
}

public enum class DrawType { CURVE, LINE }