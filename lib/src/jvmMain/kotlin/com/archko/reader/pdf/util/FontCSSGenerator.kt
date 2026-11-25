package com.archko.reader.pdf.util

import com.archko.reader.pdf.component.ReflowStyle
import java.io.File
import java.util.prefs.Preferences

/**
 * @author: archko 2025/9/18 :20:09
 */
public object FontCSSGenerator {

    private val prefs: Preferences by lazy {
        Preferences.userRoot().node("KReader/epub")
    }

    public fun getDefFontSize(): Float {
        val fontSize = 25f
        return fontSize
    }

    public fun getFontSize(): Float {
        val size = prefs.getFloat("font_size", 0f)
        return if (size > 0f) size else getDefFontSize()
    }

    public fun setFontSize(size: Float) {
        prefs.putFloat("font_size", size)
    }

    public fun getFontWeight(): Int {
        val weight = prefs.getInt("font_weight", 400)
        return if (weight in 100..900) weight else 400
    }

    public fun setFontWeight(weight: Int) {
        prefs.putInt("font_weight", weight.coerceIn(100, 900))
    }

    public fun getMarginPx(): Int {
        val margin = prefs.getInt("margin_px", 0)
        return if (margin > 0) 30 else 0
    }

    public fun setMarginPx(margin: Int) {
        prefs.putInt("margin_px", if (margin > 0) 30 else 0)
    }

    public fun getReflowStyle(): ReflowStyle {
        return ReflowStyle(
            fontPath = null,
            fontSize = getFontSize(),
            fontWeight = getFontWeight(),
            marginPx = getMarginPx(),
        )
    }

    public fun saveReflowStyle(style: ReflowStyle) {
        setFontSize(style.fontSize)
        setFontWeight(style.fontWeight)
        setMarginPx(style.marginPx)
    }

    public fun getStyleFingerprint(style: ReflowStyle = getReflowStyle()): String {
        return "${style.fontSize}|${style.fontWeight}|${style.marginPx}"
    }

    /**
     * 桌面端不支持自定义字体文件，字体项固定使用 MuPDF 内置字体。
     * 页边距使用 @page margin，字号通过 Document.layout 应用。
     */
    public fun generateReflowCSS(style: ReflowStyle): String {
        val pageMargin = style.marginPx.coerceAtLeast(0)
        val buffer = StringBuilder()
        buffer.apply {
            appendLine("@page { margin: ${pageMargin}px !important; }")
            appendLine("p { margin: 20px !important; padding: 0 !important; }")
            appendLine("blockquote { margin: 0 !important; padding: 0 !important; }")
            appendLine("* {")
            appendLine("    margin: 0 !important;")
            appendLine("    padding: 0 !important;")
            appendLine("    line-height: 1.5 !important;")
            appendLine("}")

            val weight = style.fontWeight.coerceIn(100, 900)
            if (weight != 400) {
                appendLine("html, body, p, div, span { font-weight: $weight !important; }")
            }
        }
        return buffer.toString()
    }

    public fun generateFontCSS(fontPath: String?, margin: String): String {
        val buffer = StringBuilder()

        /*if (!fontPath.isNullOrEmpty()) {
            val fontFile = File(fontPath)
            if (fontFile.exists()) {
                val fontName = getFontNameFromPath(fontPath)
                buffer.apply {
                    appendLine("@font-face {")
                    appendLine("    font-family: '$fontName' !important;")
                    appendLine("    src: url('file://$fontPath');")
                    appendLine("}")

                    appendLine("* {")
                    appendLine("    font-family: '$fontName', serif !important;")
                    appendLine("}")
                }
            }
        }*/

        buffer.apply {
            // 忽略mupdf的边距
            appendLine("    @page { margin:$margin $margin !important; }")
            appendLine("    p { margin: 20px !important; padding: 0 !important; }")
            appendLine("    blockquote { margin: 0 !important; padding: 0 !important; }")

            // 强制所有元素的边距和内边距
            appendLine("* {")
            appendLine("    margin: 0 !important;")
            appendLine("    padding: 0 !important;")
            appendLine("    line-height: 1.5 !important;")
            appendLine("}")
        }
        return buffer.toString()
    }

    private fun getFontNameFromPath(fontPath: String): String {
        val fileName = File(fontPath).name
        val dotIndex = fileName.lastIndexOf('.')
        return if (dotIndex > 0) fileName.take(dotIndex) else fileName
    }
}