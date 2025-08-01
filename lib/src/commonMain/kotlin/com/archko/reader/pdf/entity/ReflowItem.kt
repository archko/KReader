package com.archko.reader.pdf.entity

/**
 * Reflow页面中的内容项
 */
public data class ReflowBean(
    var data: String?,
    var type: Int = TYPE_STRING,
    val page: String? = null
) {
    override fun toString(): String {
        return "ReflowBean(page=$page, data=$data)"
    }

    public companion object {
        public const val TYPE_STRING: Int = 0

        public const val TYPE_IMAGE: Int = 1
    }
}

/**
 * Reflow页面数据
 */
public data class ReflowPage(
    val pageIndex: Int,
    val content: List<ReflowBean>,
    val hasBookmark: Boolean = false
) 