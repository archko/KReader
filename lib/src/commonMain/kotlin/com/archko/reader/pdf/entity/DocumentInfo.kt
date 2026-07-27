package com.archko.reader.pdf.entity

public data class DocumentInfo(
    val uri: String? = null,
    val path: String? = null,
    val fileSize: Long = 0L,
    val mimeType: String? = null,
    val ext: String? = null
) {
    public fun getDisplayPath(): String = path ?: uri ?: ""

    public fun hasUri(): Boolean = !uri.isNullOrEmpty()
}
