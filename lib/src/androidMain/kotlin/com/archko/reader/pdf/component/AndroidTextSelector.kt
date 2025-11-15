package com.archko.reader.pdf.component

import androidx.compose.ui.geometry.Offset

/**
 * Android平台的actual实现
 */
public actual fun createTextSelector(getStructuredTextCallback: (Int) -> StructuredText?): TextSelector {
    return AndroidTextSelector(getStructuredTextCallback)
}

public actual fun createStructuredTextImpl(nativeStructuredText: Any): StructuredText {
    return AndroidStructuredTextImpl(nativeStructuredText)
}

/**
 * Android平台的文本选择器实现
 */
public class AndroidTextSelector(
    private val getStructuredTextCallback: (Int) -> StructuredText?
) : TextSelector {

    override fun getStructuredText(pageIndex: Int): StructuredText? {
        return getStructuredTextCallback(pageIndex)
    }

    override fun quadToScreenQuad(
        quad: MuPdfQuad,
        pdfToScreenTransform: (Float, Float) -> Offset
    ): ScreenQuad {
        val ul = pdfToScreenTransform(quad.ul_x, quad.ul_y)
        val ur = pdfToScreenTransform(quad.ur_x, quad.ur_y)
        val ll = pdfToScreenTransform(quad.ll_x, quad.ll_y)
        val lr = pdfToScreenTransform(quad.lr_x, quad.lr_y)
        return ScreenQuad(ul, ur, ll, lr)
    }
}

/**
 * Android平台的StructuredText实现
 */
public class AndroidStructuredTextImpl(
    private val nativeStructuredText: Any // 实际的MuPDF StructuredText对象
) : StructuredText {

    override fun highlight(startPoint: PagePoint, endPoint: PagePoint): Array<MuPdfQuad> {
        return try {
            val structuredText = nativeStructuredText as com.artifex.mupdf.fitz.StructuredText

            // 完全绕过MuPDF的智能选择，直接基于坐标创建选择区域
            val left = minOf(startPoint.x, endPoint.x)
            val top = minOf(startPoint.y, endPoint.y)
            val right = maxOf(startPoint.x, endPoint.x)
            val bottom = maxOf(startPoint.y, endPoint.y)

            println("highlight: 使用坐标选择 start:($startPoint) end:($endPoint) -> rect:($left,$top,$right,$bottom)")

            // 只有当起始点和结束点不同时才创建选择区域
            if (left != right || top != bottom) {
                val quad = MuPdfQuad(
                    ul_x = left, ul_y = top,      // 左上角
                    ur_x = right, ur_y = top,     // 右上角
                    ll_x = left, ll_y = bottom,   // 左下角
                    lr_x = right, lr_y = bottom   // 右下角
                )
                arrayOf(quad)
            } else {
                emptyArray()
            }
        } catch (e: Exception) {
            println("MuPDF highlight error: ${e.message}")
            // 降级到简单实现
            val left = minOf(startPoint.x, endPoint.x)
            val top = minOf(startPoint.y, endPoint.y)
            val right = maxOf(startPoint.x, endPoint.x)
            val bottom = maxOf(startPoint.y, endPoint.y)

            // 只有当起始点和结束点不同时才创建选择区域
            if (left != right || top != bottom) {
                val quad = MuPdfQuad(
                    ul_x = left, ul_y = top,
                    ur_x = right, ur_y = top,
                    ll_x = left, ll_y = bottom,
                    lr_x = right, lr_y = bottom
                )
                arrayOf(quad)
            } else {
                emptyArray()
            }
        }
    }

    override fun copy(startPoint: PagePoint, endPoint: PagePoint): String {
        return try {
            val structuredText = nativeStructuredText as com.artifex.mupdf.fitz.StructuredText

            // 尝试使用MuPDF的文本提取，但如果结果包含整行，则使用坐标基础的方法
            val nativeStart = com.artifex.mupdf.fitz.Point(startPoint.x, startPoint.y)
            val nativeEnd = com.artifex.mupdf.fitz.Point(endPoint.x, endPoint.y)

            val left = minOf(startPoint.x, endPoint.x)
            val top = minOf(startPoint.y, endPoint.y)
            val right = maxOf(startPoint.x, endPoint.x)
            val bottom = maxOf(startPoint.y, endPoint.y)

            // 尝试多种策略来获取精确的文本选择

            // 策略1: 直接copy
            val directCopy = structuredText.copy(nativeStart, nativeEnd) ?: ""

            // 策略2: 使用稍微缩小的选择区域，避免触发MuPDF的智能扩展
            val margin = 2.0f // 2个点的边距
            val shrunkStart = com.artifex.mupdf.fitz.Point(
                startPoint.x + margin,
                startPoint.y + margin
            )
            val shrunkEnd = com.artifex.mupdf.fitz.Point(
                endPoint.x - margin,
                endPoint.y - margin
            )
            val shrunkCopy = try {
                if (shrunkEnd.x > shrunkStart.x && shrunkEnd.y > shrunkStart.y) {
                    structuredText.copy(shrunkStart, shrunkEnd) ?: ""
                } else {
                    ""
                }
            } catch (e: Exception) {
                ""
            }

            println("copy: 选择区域: ($left,$top,$right,$bottom)")
            println("copy: 直接复制长度: ${directCopy.length}, 内容: '$directCopy'")
            println("copy: 缩小复制长度: ${shrunkCopy.length}, 内容: '$shrunkCopy'")

            // 选择最合适的结果
            when {
                shrunkCopy.isNotBlank() && shrunkCopy.length < directCopy.length -> {
                    println("copy: 使用缩小选择结果")
                    shrunkCopy
                }

                directCopy.isNotBlank() -> {
                    println("copy: 使用直接选择结果")
                    directCopy
                }

                else -> {
                    "选中区域 (${(right - left).toInt()}x${(bottom - top).toInt()})"
                }
            }
        } catch (e: Exception) {
            println("MuPDF copy error: ${e.message}")
            // 降级到简单实现
            val width = kotlin.math.abs(endPoint.x - startPoint.x)
            val height = kotlin.math.abs(endPoint.y - startPoint.y)
            "选中文本 (${width.toInt()}x${height.toInt()})"
        }
    }

    override fun snapSelection(
        startPoint: PagePoint,
        endPoint: PagePoint,
        mode: Int
    ): MuPdfQuad? {
        return try {
            // 调用实际的MuPDF StructuredText.snapSelection方法
            val structuredText = nativeStructuredText as com.artifex.mupdf.fitz.StructuredText
            val nativeStart = com.artifex.mupdf.fitz.Point(startPoint.x, startPoint.y)
            val nativeEnd = com.artifex.mupdf.fitz.Point(endPoint.x, endPoint.y)
            val nativeQuad = structuredText.snapSelection(nativeStart, nativeEnd, mode)

            nativeQuad?.let { quad ->
                MuPdfQuad(
                    ul_x = quad.ul_x, ul_y = quad.ul_y,
                    ur_x = quad.ur_x, ur_y = quad.ur_y,
                    ll_x = quad.ll_x, ll_y = quad.ll_y,
                    lr_x = quad.lr_x, lr_y = quad.lr_y
                )
            }
        } catch (e: Exception) {
            println("MuPDF snapSelection error: ${e.message}")
            null
        }
    }

    override fun search(needle: String, flags: Int): Array<Array<MuPdfQuad>> {
        return try {
            // 调用实际的MuPDF StructuredText.search方法
            val structuredText = nativeStructuredText as com.artifex.mupdf.fitz.StructuredText
            val nativeResults = structuredText.search(needle)

            nativeResults.map { quadArray ->
                quadArray.map { quad ->
                    MuPdfQuad(
                        ul_x = quad.ul_x, ul_y = quad.ul_y,
                        ur_x = quad.ur_x, ur_y = quad.ur_y,
                        ll_x = quad.ll_x, ll_y = quad.ll_y,
                        lr_x = quad.lr_x, lr_y = quad.lr_y
                    )
                }.toTypedArray()
            }.toTypedArray()
        } catch (e: Exception) {
            println("MuPDF search error: ${e.message}")
            emptyArray()
        }
    }
}