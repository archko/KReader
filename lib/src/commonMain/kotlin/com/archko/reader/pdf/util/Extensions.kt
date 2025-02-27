package com.archko.reader.pdf.util

public fun String.inferName(): String {
    val separatorIndex = this.lastIndexOf('/').takeIf { it != -1 } ?: this.lastIndexOf('\\')
    val fileName = if (separatorIndex != -1) {
        this.substring(separatorIndex + 1)
    } else {
        this
    }
    return fileName
}

