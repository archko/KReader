package com.archko.reader.pdf.util

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.select.Elements
import kotlin.math.abs

public class MuPdfHtmlMerger public constructor() {
    public fun mergeParagraphs(html: String): String {
        val doc: Document = Jsoup.parse(html)
        val pages: Elements = doc.select("div[id^=page]")

        for (page in pages) {
            // 完全移除div的style属性
            page.removeAttr("style")
            mergePage(page)
        }

        return doc.html()
    }

    /**
     * 移除div元素的width和height样式，使其自适应
     */
    private fun removeSizeAttributes(page: Element) {
        val style: String? = page.attr("style")
        if (style == null || style.isEmpty()) {
            return
        }

        // 拆分样式属性
        val styleParts = style.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val newStyle = StringBuilder()

        for (part in styleParts) {
            var part = part
            part = part.trim { it <= ' ' }
            // 保留除width和height之外的其他样式
            if (!part.startsWith("width") && !part.startsWith("height")) {
                if (newStyle.length > 0) {
                    newStyle.append("; ")
                }
                newStyle.append(part)
            }
        }

        // 更新样式属性，如果没有样式则移除该属性
        if (newStyle.length > 0) {
            page.attr("style", newStyle.toString())
        } else {
            page.removeAttr("style")
        }
    }

    /* 扩展：统一判断“整段保留”规则，拆成两步检测 ---------- */
    private fun shouldKeepIntact(p: Element): Boolean {
        /* 规则1：图片段落 */
        if (p.children().size == 1 && "img" == p.child(0).tagName()) return true

        /* 规则2：代码段落（单<span>，含关键字或以代码结束符结尾） */
        if (p.children().size == 1 && "span" == p.child(0).tagName()) {
            val txt = p.child(0).text().trim()
            val isCode = CODE_KEYWORDS.stream()
                .anyMatch { s: String? ->
                    txt.contains(
                        s!!
                    )
                }
                    || txt.endsWith(";") || txt.endsWith("{")
                    || txt.endsWith("}") || txt.endsWith(")")
            if (isCode) {
                // 识别为代码后，立即把字体改为 monospace
                val span: Element = p.child(0)
                var style: String? = span.attr("style")
                style = appendFontFamily(
                    style,
                    "monospace"
                )
                span.attr("style", style)
                return true
            }
        }

        /* 规则3：字体为 Monaco/monospace 仅作为辅助条件，不再单独判定为代码 */

        /* 规则4：文本以 >>> 或 > 开头（HTML 转义后）视为终端输入代码 */
        for (span in p.select("span")) {
            val txt = span.text().trim()
            if (txt.startsWith(">>>") || txt.startsWith(">")) {
                // 识别为终端代码后，立即把字体改为 monospace
                var style: String? = span.attr("style")
                style = appendFontFamily(
                    style,
                    "monospace"
                )
                span.attr("style", style)
                return true
            }
        }

        /* 规则5：已移除——仅保留更精确的代码特征判断 */

        /* 规则6：以后想加任何规则，直接在这里写 if (...) return true; */
        return false // 默认参与合并
    }

    private fun mergePage(page: Element) {
        /* 0. 先移除空行：仅含空白或高度≤单行高的<p> ---------------- */
        for (p in page.select("p")) {
            val txt = p.text().trim()
            val h: Float = MuPdfParagraph(p).lineHeight
            if (txt.isEmpty() || h <= TYPICAL_LINE_SPACING) {
                p.remove()
            }
        }

        /* 1. 再把所有“整段保留”的段落克隆+标记 -------------------- */
        val toRemove: MutableList<Node> = ArrayList()
        val keepNodes: MutableList<Node> = ArrayList()
        for (p in page.select("p")) {
            if (shouldKeepIntact(p)) {
                toRemove.add(p)
                keepNodes.add(p.clone())
            }
        }

        /* 1. 对剩余段落做原来的合并逻辑 ---------------------------- */
        val paragraphs: Elements = page.select("p")
        if (paragraphs.size <= 1) return

        val paraList: MutableList<MuPdfParagraph> = ArrayList<MuPdfParagraph>()
        for (p in paragraphs) {
            if (toRemove.contains(p)) continue  // 跳过保留段

            paraList.add(MuPdfParagraph(p))
        }

        val currentGroup: MutableList<MuPdfParagraph> = ArrayList<MuPdfParagraph>()
        if (!paraList.isEmpty()) currentGroup.add(paraList.get(0))

        for (i in 1..<paraList.size) {
            val prev = paraList.get(i - 1)
            val curr = paraList.get(i)
            if (shouldMerge(prev, curr)) {
                currentGroup.add(curr)
            } else {
                val mergedP: Element = mergeGroup(currentGroup)
                currentGroup.get(0).element.replaceWith(mergedP)
                for (j in 1..<currentGroup.size) {
                    currentGroup.get(j).element.remove()
                }
                currentGroup.clear()
                currentGroup.add(curr)
            }
        }
        if (!currentGroup.isEmpty()) {
            val mergedP: Element = mergeGroup(currentGroup)
            currentGroup.get(0).element.replaceWith(mergedP)
            for (j in 1..<currentGroup.size) {
                currentGroup.get(j).element.remove()
            }
        }

        /* 2. 把保留段插回（顺序不变） ------------------------------ */
        for (old in toRemove) old.remove()
        for (keep in keepNodes) page.appendChild(keep)
    }

    /**
     * 判断两个相邻段落是否应该合并
     */
    private fun shouldMerge(prev: MuPdfParagraph, curr: MuPdfParagraph): Boolean {
        // 1. 缩进差异判断：缩进不同视为不同段落
        if (abs(prev.left - curr.left) > INDENT_THRESHOLD) {
            return false
        }

        // 2. 间距判断：超过行间距的1.2倍视为新段落
        val spacing = curr.top - prev.bottom
        if (spacing > TYPICAL_LINE_SPACING * PARAGRAPH_SPACING_RATIO) {
            return false
        }

        // 3. 样式判断：字体或字号不同则不合并
        if (prev.fontFamily != curr.fontFamily ||
            abs(prev.fontSize - curr.fontSize) > FONT_SIZE_THRESHOLD
        ) {
            return false
        }

        // 4. 特殊规则：以标点符号结尾的段落不与后续段落合并
        val prevText = prev.element.text().trim()
        if (!prevText.isEmpty()) {
            val lastChar = prevText.get(prevText.length - 1)
            if (lastChar == '。' || lastChar == '：' || lastChar == '；' || lastChar == '！' || lastChar == '？' || lastChar == '、') {
                return false
            }
        }

        // 5. 特殊规则：以序号开头的段落（如"一、"）作为新段落
        val currText = curr.element.text().trim()
        if (currText.matches("^[一二三四五六七八九十]+、.*".toRegex()) ||
            currText.matches("^[ABCDE]+、.*".toRegex()) ||
            currText.matches("^［.*］.*".toRegex())
        ) {
            return false
        }

        return true
    }

    /**
     * 合并一组段落为一个段落
     */
    private fun mergeGroup(group: MutableList<MuPdfParagraph>): Element {
        // 使用第一个段落的样式作为基础
        val first = group.get(0)
        val mergedPara = Element("p")

        // 仅保留左侧缩进，移除顶部和底部边距
        mergedPara.attr(
            "style", String.format(
                "margin-left:%.1fpt;",
                first.left
            )
        )

        // 合并所有span内容，并强制行高倍率为1
        val mergedSpan = Element("span")
        mergedSpan.attr(
            "style", String.format(
                "font-family:%s;font-size:%.1fpt;line-height:1.0;",
                first.fontFamily, first.fontSize
            )
        )

        val textBuilder = StringBuilder()
        for (para in group) {
            textBuilder.append(para.element.text())
        }

        mergedSpan.text(textBuilder.toString())
        mergedPara.appendChild(mergedSpan)

        return mergedPara
    }

    /**
     * MuPDF段落信息封装类
     * 提取缩进、位置、样式等关键信息
     */
    private class MuPdfParagraph(p: Element) {
        var top: Float // 顶部位置
        var left: Float // 左侧缩进
        var bottom: Float // 底部位置
        var lineHeight: Float // 行高
        var fontFamily: String? = null // 字体
        var fontSize: Float = 0f // 字号
        var element: Element // 原始元素

        init {
            this.element = p
            val style: String? = p.attr("style")

            // 提取位置和行高
            this.top = extractValue(style, "top")
            this.left = extractValue(style, "left")
            this.lineHeight = extractValue(style, "line-height")
            this.bottom = this.top + this.lineHeight

            // 提取字体样式（从span中）
            val span: Element? = p.selectFirst("span")
            if (span != null) {
                val spanStyle: String? = span.attr("style")
                this.fontFamily = extractFontFamily(spanStyle)
                this.fontSize = extractValue(spanStyle, "font-size")
            } else {
                this.fontFamily = "unknown"
                this.fontSize = 12.0f
            }
        }

        // 提取样式中的数值（如12.0pt中的12.0）
        fun extractValue(style: String?, property: String): Float {
            if (style == null || !style.contains(property)) return 0.0f

            val parts = style.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (part in parts) {
                var part = part
                part = part.trim { it <= ' ' }
                if (part.startsWith(property)) {
                    val valueStr = part.split(":".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()[1].trim { it <= ' ' }
                        .replace("pt", "").replace("px", "")
                    try {
                        return valueStr.toFloat()
                    } catch (_: NumberFormatException) {
                        return 0.0f
                    }
                }
            }
            return 0.0f
        }

        // 提取字体家族
        fun extractFontFamily(style: String?): String {
            if (style == null || !style.contains("font-family")) return "unknown"

            val parts = style.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (part in parts) {
                var part = part
                part = part.trim { it <= ' ' }
                if (part.startsWith("font-family")) {
                    return part.split(":".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()[1].trim { it <= ' ' }
                        .replace("'", "").replace("\"", "")
                }
            }
            return "unknown"
        }
    }

    public companion object {
        // 根据文档特征确定的典型行间距
        private const val TYPICAL_LINE_SPACING = 14.0f

        // 段落间距阈值（行间距的倍数）
        private const val PARAGRAPH_SPACING_RATIO = 1.0f

        // 缩进差异阈值（超过此值视为不同段落）
        private const val INDENT_THRESHOLD = 2.0f

        // 字号差异阈值
        private const val FONT_SIZE_THRESHOLD = 0.5f

        /* 新增：代码关键字白名单（涵盖 Python/Kotlin/C/Dart/Java/Swift/Objective-C 等常用保留字与类型） */
        private val CODE_KEYWORDS =
            mutableSetOf<String?>( // ===== Java / Kotlin / C / Objective-C 共用核心关键字 =====
                "public",
                "private",
                "protected",
                "class",
                "interface",
                "void",
                "if",
                "for",
                "while",
                "switch",
                "case",
                "default",
                "try",
                "catch",
                "return",
                "import",
                "package",
                "static",
                "final",
                "this",
                "extends",
                "implements",
                "throws",
                "new",
                "throw",
                "enum",
                "@Override",
                "const",
                "struct",
                "typedef",
                "union",
                "sizeof",
                "extern",
                "register",
                "volatile",
                "goto",
                "inline",
                "#include",
                "#define",
                "#ifdef",
                "#ifndef",
                "#endif",
                "@interface",
                "@implementation",
                "@end",
                "@property",
                "@synthesize",
                "@protocol",
                "@selector",  // ===== Kotlin 独有 =====
                "fun",
                "val",
                "var",
                "when",
                "object",
                "companion",
                "data",
                "sealed",
                "suspend",
                "reified",
                "crossinline",
                "noinline",  // ===== Python 独有 =====
                "def",
                "elif",
                "else",
                "except",
                "with",
                "as",
                "lambda",
                "yield",
                "from",
                "global",
                "nonlocal",
                "pass",
                "del",
                "raise",
                "is",
                "in",
                "not",
                "and",
                "or",
                "True",
                "False",
                "None",  // ===== Dart 独有 =====
                "Future",
                "async",
                "await",
                "mixin",
                "extension",
                "late",
                "required",
                "factory",
                "external",
                "operator",
                "covariant",
                "part",
                "show",
                "hide",
                "deferred",
                "assert",
                "library",
                "export",  // ===== Swift 独有 =====
                "let",
                "guard",
                "defer",
                "fallthrough",
                "associatedtype",
                "typealias",
                "where",
                "some",
                "any",
                "actor",
                "nonisolated",
                "convenience",
                "lazy",
                "weak",
                "unowned",
                "optional",
                "willSet",
                "didSet",
                "get",
                "set",
                "inout",
                "escaping",
                "autoclosure",  // ===== 常见基础类型 =====
                "String",
                "int",
                "long",
                "double",
                "float",
                "boolean",
                "bool",
                "char",
                "byte",
                "short",
                "Integer",
                "Long",
                "Double",
                "Float",
                "Boolean",
                "Character",
                "Byte",
                "Short",
                "StringBuilder",
                "StringBuffer",
                "List",
                "Map",
                "Set",
                "Array",
                "Dict",
                "Optional",
                "Any",
                "Void",
                "Self",
                "self",
                "super",
                "id",
                "instancetype",
                "NSObject",
                "NSString",
                "NSNumber",
                "NSArray",
                "NSDictionary",
                "NSMutableArray",
                "NSMutableDictionary",  // ===== 常见修饰符 / 注解 =====
                "@FunctionalInterface",
                "@Deprecated",
                "@SafeVarargs",
                "@SuppressWarnings",
                "@Nullable",
                "@NonNull",
                "@JvmStatic",
                "@JvmField",
                "@JvmOverloads",
                "@Composable",
                "@Preview"
            )

        /**
         * 工具：向现有 style 字符串追加 font-family，若已存在则替换
         */
        private fun appendFontFamily(style: String?, font: String): String {
            var style = style
            if (style == null) style = ""
            // 去掉已有 font-family 声明
            style = style.replace("\\s*font-family\\s*:[^;]+".toRegex(), "").trim { it <= ' ' }
            // 去掉首尾多余分号与空格
            style = style.replace("^;+|;+$".toRegex(), "").trim { it <= ' ' }
            // 追加新的 font-family
            return if (style.isEmpty())
                "font-family:$font"
            else
                "$style;font-family:$font"
        }
    }
}