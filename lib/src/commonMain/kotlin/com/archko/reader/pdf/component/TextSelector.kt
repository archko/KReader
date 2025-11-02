package com.archko.reader.pdf.component

import androidx.compose.ui.geometry.Offset

/**
 * MuPDF Point类的抽象表示
 */
public data class MuPdfPoint(
    val x: Float,
    val y: Float
)

/**
 * MuPDF Quad类的抽象表示
 */
public data class MuPdfQuad(
    val ul_x: Float, val ul_y: Float,  // 左上角
    val ur_x: Float, val ur_y: Float,  // 右上角
    val ll_x: Float, val ll_y: Float,  // 左下角
    val lr_x: Float, val lr_y: Float   // 右下角
)

/**
 * 结构化文本接口，对应MuPDF的StructuredText
 */
public interface StructuredText {
    /**
     * 获取文本高亮区域
     */
    public fun highlight(startPoint: MuPdfPoint, endPoint: MuPdfPoint): Array<MuPdfQuad>

    /**
     * 复制选中的文本
     */
    public fun copy(startPoint: MuPdfPoint, endPoint: MuPdfPoint): String

    /**
     * 智能选择，自动调整选择边界
     */
    public fun snapSelection(startPoint: MuPdfPoint, endPoint: MuPdfPoint, mode: Int): MuPdfQuad?

    /**
     * 搜索文本
     */
    public fun search(needle: String, flags: Int = 0): Array<Array<MuPdfQuad>>
}

/**
 * 文本选择接口，由不同平台实现
 */
public interface TextSelector {
    /**
     * 获取页面的结构化文本
     */
    public fun getStructuredText(pageIndex: Int): StructuredText?

    /**
     * 将MuPDF Quad转换为屏幕坐标
     */
    public fun quadToScreenQuad(quad: MuPdfQuad, pdfToScreenTransform: (Float, Float) -> Offset): ScreenQuad
}

/**
 * 屏幕坐标系的四边形
 */
public data class ScreenQuad(
    val ul: Offset = Offset.Zero,  // 左上角
    val ur: Offset = Offset.Zero,  // 右上角
    val ll: Offset = Offset.Zero,  // 左下角
    val lr: Offset = Offset.Zero   // 右下角
)

/**
 * 文本选择数据类
 */
public data class TextSelection(
    val startPoint: MuPdfPoint,        // 起始点
    val endPoint: MuPdfPoint,          // 结束点
    val text: String,                  // 选中的文本
    val quads: Array<MuPdfQuad>        // 高亮区域
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as TextSelection

        if (startPoint != other.startPoint) return false
        if (endPoint != other.endPoint) return false
        if (text != other.text) return false
        if (!quads.contentEquals(other.quads)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = startPoint.hashCode()
        result = 31 * result + endPoint.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + quads.contentHashCode()
        return result
    }
}

/**
 * 文本选择常量
 */
public object TextSelectionConstants {
    public const val SELECT_CHARS: Int = 0
    public const val SELECT_WORDS: Int = 1
    public const val SELECT_LINES: Int = 2
}