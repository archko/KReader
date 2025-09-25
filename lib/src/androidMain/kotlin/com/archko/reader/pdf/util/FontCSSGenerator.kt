package com.archko.reader.pdf.util

import android.content.Context
import com.archko.reader.pdf.PdfApp
import com.tencent.mmkv.MMKV
import java.io.File

/**
 * @author: archko 2025/9/18 :20:09
 */
public object FontCSSGenerator {

    public fun getDefFontSize(): Float {
        val fontSize = (9f * Utils.getDensityDpi(PdfApp.Companion.app as Context) / 72)
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
        println("setFontFace:$name")
        mmkv.encode("font_face", name)
    }

    public fun generateFontCSS(fontPath: String?, margin: String): String {
        val buffer = StringBuilder()

        if (!fontPath.isNullOrEmpty()) {
            val fontFile = File(fontPath)
            if (fontFile.exists()) {
                val fontName = getFontNameFromPath(fontPath)
                val fontFamily = fontName
                buffer.apply {
                    appendLine("@font-face {")
                    appendLine("    font-family: '$fontFamily' !important;")
                    appendLine("    src: url('file://$fontPath');")
                    appendLine("}")

                    appendLine("* {")
                    appendLine("    font-family: '$fontFamily', serif !important;")
                    appendLine("}")
                }
            }
        }

        buffer.apply {
            // 忽略mupdf的边距
            appendLine("    @page { margin:$margin $margin !important; }")
            appendLine("    p { margin: 30px !important; padding: 0 !important; }")
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
        return if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
    }
}