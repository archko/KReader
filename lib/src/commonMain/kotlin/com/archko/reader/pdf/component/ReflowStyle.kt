package com.archko.reader.pdf.component

import com.archko.reader.pdf.entity.APage

/**
 * 流式文档（epub/mobi/docx 等）的排版样式。
 * 注意：字号不写入 CSS，而是通过 Document.layout(w, h, fontSize) 应用。
 *
 * @author: archko 2026/11/25
 */
public data class ReflowStyle(
    public val fontPath: String? = null,
    public val fontSize: Float = 0f,
    public val fontWeight: Int = 400,
    public val marginPx: Int = 0,
)

/**
 * 样式变更请求。id 用于区分“相同样式的重复应用”。
 */
public data class ReflowStyleRequest(
    public val id: Int,
    public val style: ReflowStyle,
)

/**
 * 样式重排结果。pages 是一份独立的新页面列表，调用方在主线程替换旧列表。
 */
public data class ReflowStyleResult(
    public val pages: List<APage>,
    public val pageCount: Int,
)

/**
 * 可选的重排能力接口。不修改 ImageDecoder/Decoder 的现有方法。
 * applyReflowStyle 为阻塞调用，必须在后台线程执行，内部自行保证与渲染互斥。
 */
public interface ReflowStyleReceiver {
    public fun supportsReflowStyle(): Boolean

    /**
     * 应用新的 CSS / 字体 / 字号并重新排版。
     * @return null 表示当前文档不支持或重排失败；成功时返回新的页面列表。
     */
    public fun applyReflowStyle(style: ReflowStyle): ReflowStyleResult?
}
