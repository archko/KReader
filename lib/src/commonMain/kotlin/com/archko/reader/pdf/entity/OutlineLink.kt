package com.archko.reader.pdf.entity

public class OutlineLink(
    public val title: String?,
    link: String?,
    public val level: Int
) {
    public var targetUrl: String? = null
    public var targetPage: Int = -1
    // var targetRect: RectF? = null

    init {
        if (link != null) {
            if (link.startsWith("#")) {
                try {   //djvu
                    targetPage = link.substring(1).replace(" ", "").toInt()
                } catch (e: Exception) {
                    //mupdf
                    targetUrl = link
                }
            } else if (link.startsWith("http:")) {
                targetUrl = link
            }
        }
    }
}