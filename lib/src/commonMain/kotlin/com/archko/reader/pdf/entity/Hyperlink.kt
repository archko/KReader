package com.archko.reader.pdf.entity

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

public class Hyperlink {
    public var linkType: Int = LINKTYPE_PAGE
    public var url: String? = null
    public var page: Int = 0
    public var bbox: Rect? = null
    override fun toString(): String {
        return "Hyperlink{" +
                "linkType=" + linkType +
                ", page=" + page +
                ", bbox=" + bbox +
                ", url='" + url + '\'' +
                '}'
    }

    public companion object {

        public const val LINKTYPE_PAGE: Int = 0
        public const val LINKTYPE_URL: Int = 1

        /**
         * 检查点是否在链接区域内
         */
        public fun contains(hyperlink: Hyperlink, x: Float, y: Float): Boolean {
            val bbox = hyperlink.bbox ?: return false
            return bbox.contains(Offset(x, y))
        }

        /**
         * 在链接列表中查找包含指定点的链接
         */
        public fun findLinkAtPoint(links: List<Hyperlink>, x: Float, y: Float): Hyperlink? {
            for (link in links) {
                if (contains(link, x, y)) {
                    return link
                }
            }
            return null
        }
    }
}