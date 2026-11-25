package com.archko.reader.pdf.util
import android.content.Context
import com.archko.reader.pdf.PdfApp
import com.archko.reader.pdf.component.ReflowStyle
import com.tencent.mmkv.MMKV
import java.io.File

/**
 * @author: archko 2025/9/18 :20:09
 */
public object FontCSSGenerator {

    public fun getDefFontSize(): Float {
        val fontSize = (8.4f * Utils.getDensityDpi(PdfApp.app as Context) / 72)
        return fontSize
    }

    //"/sdcard/fonts/simsun.ttf"
    public fun getFontFace(): String? {
        val mmkv: MMKV = MMKV.mmkvWithID("epub")
        val fs = mmkv.decodeString("font_face", "")
        return fs
    }

    public fun setFontFace(name: String?) {
        val mmkv: MMKV = MMKV.mmkvWithID("epub")
        Logger.d("setFontFace", "$name")
        mmkv.encode("font_face", name)
    }

    public fun getFontSize(): Float {
        val mmkv: MMKV = MMKV.mmkvWithID("epub")
        val size = mmkv.decodeFloat("font_size", 0f)
        return if (size > 0f) size else getDefFontSize()
    }

    public fun setFontSize(size: Float) {
        MMKV.mmkvWithID("epub").encode("font_size", size)
    }

    public fun getFontWeight(): Int {
        val weight = MMKV.mmkvWithID("epub").decodeInt("font_weight", 400)
        return if (weight in 100..900) weight else 400
    }

    public fun setFontWeight(weight: Int) {
        MMKV.mmkvWithID("epub").encode("font_weight", weight.coerceIn(100, 900))
    }

    public fun getMarginPx(): Int {
        val margin = MMKV.mmkvWithID("epub").decodeInt("margin_px", 0)
        return if (margin > 0) 30 else 0
    }

    public fun setMarginPx(margin: Int) {
        MMKV.mmkvWithID("epub").encode("margin_px", if (margin > 0) 30 else 0)
    }

    public fun getReflowStyle(): ReflowStyle {
        return ReflowStyle(
            fontPath = getFontFace()?.takeIf { it.isNotEmpty() },
            fontSize = getFontSize(),
            fontWeight = getFontWeight(),
            marginPx = getMarginPx(),
        )
    }

    public fun saveReflowStyle(style: ReflowStyle) {
        setFontFace(style.fontPath)
        setFontSize(style.fontSize)
        setFontWeight(style.fontWeight)
        setMarginPx(style.marginPx)
    }

    /**
     * 页面尺寸缓存使用该指纹，样式变化后不再命中旧缓存。
     */
    public fun getStyleFingerprint(style: ReflowStyle = getReflowStyle()): String {
        return "${style.fontPath ?: ""}|${style.fontSize}|${style.fontWeight}|${style.marginPx}"
    }

    /**
     * 流式文档样式：font-size 交给 Document.layout，不写入 CSS。
     * 页边距使用 @page margin，MuPDF 用它计算 page_margin 和页面 bbox。
     */
    public fun generateReflowCSS(style: ReflowStyle): String {
        val buffer = StringBuilder()

        val fontPath = style.fontPath
        if (!fontPath.isNullOrEmpty()) {
            val fontFile = File(fontPath)
            if (fontFile.exists()) {
                val fontName = getFontNameFromPath(fontPath)
                buffer.apply {
                    appendLine("@font-face {")
                    appendLine("    font-family: '$fontName', 'Droid Sans Fallback', sans-serif !important;")
                    appendLine("    src: url('file://$fontPath');")
                    appendLine("}")

                    appendLine("html, body, p, div {")
                    appendLine("    font-family: '$fontName', 'Droid Sans Fallback', sans-serif !important;")
                    appendLine("}")
                }
            }
        }

        buffer.apply {
            // MuPDF 的页面边距来自 @page 的 margin（fz_layout_html 用 root->style->margin
            // 计算 page_margin），body padding 不参与页面尺寸计算。
            val pageMargin = style.marginPx.coerceAtLeast(0)
            appendLine("@page { margin: ${pageMargin}px !important; }")
            appendLine("p { margin: 20px !important; padding: 0 !important; }")
            appendLine("blockquote { margin: 0 !important; padding: 0 !important; }")

            // 强制所有元素的边距和内边距
            appendLine("* {")
            appendLine("    margin: 0 !important;")
            appendLine("    padding: 0 !important;")
            appendLine("}")

            val weight = style.fontWeight.coerceIn(100, 900)
            if (weight != 400) {
                // MuPDF 用 atoi(weight) > 400 判断 bold，数值本身只影响是否加粗
                appendLine("html, body, p, div, span { font-weight: $weight !important; }")
            }
        }
        return buffer.toString()
    }

    public fun generateFontCSS(fontPath: String?, margin: String): String {
        val buffer = StringBuilder()

        if (!fontPath.isNullOrEmpty()) {
            val fontFile = File(fontPath)
            if (fontFile.exists()) {
                val fontName = getFontNameFromPath(fontPath)
                buffer.apply {
                    appendLine("@font-face {")
                    appendLine("    font-family: '$fontName', 'Droid Sans Fallback', sans-serif !important;")
                    appendLine("    src: url('file://$fontPath');")
                    appendLine("}")

                    appendLine("html, body, p, div {")
                    appendLine("    font-family: '$fontName', 'Droid Sans Fallback', sans-serif !important;")
                    appendLine("}")
                }
            }
        }

        buffer.apply {
            // 忽略mupdf的边距
            appendLine("    @page { margin:$margin $margin !important; }")
            appendLine("    p { margin: 20px !important; padding: 0 !important; }")
            appendLine("    blockquote { margin: 0 !important; padding: 0 !important; }")

            // 强制所有元素的边距和内边距
            appendLine("* {")
            appendLine("    margin: 0 !important;")
            appendLine("    padding: 0 !important;")
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