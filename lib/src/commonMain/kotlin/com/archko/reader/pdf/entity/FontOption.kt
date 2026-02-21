package com.archko.reader.pdf.entity

import java.io.File

public sealed class FontOption {
    public object SystemDefault : FontOption()
    public data class CustomFile(val file: File) : FontOption()
    
    // 用于显示的名称
    public val displayName: String
        get() = when (this) {
            is SystemDefault -> "系统默认 (Default)"
            is CustomFile -> file.name
        }

    // 用于传递给 CSS 的路径
    public val path: String
        get() = when (this) {
            is SystemDefault -> ""
            is CustomFile -> file.absolutePath
        }
    public val fontFile: File?
        get() = when (this) {
            is SystemDefault -> null
            is CustomFile -> file
        }
}