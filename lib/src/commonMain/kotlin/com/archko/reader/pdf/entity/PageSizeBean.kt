package com.archko.reader.pdf.entity

/**
 * @author: archko 2025/11/16 :06:04
 */
public class PageSizeBean {
    public var list: MutableList<APage>? = null
    public var crop: Boolean = false
    public var fileSize: Long = 0L

    override fun toString(): String {
        return "PageSizeBean(crop=$crop, fileSize=$fileSize, list=${list?.size})"
    }
}