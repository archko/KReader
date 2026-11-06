package com.archko.reader.pdf.entity

/**
 * 文档级切边模式。
 */
public object CropMode {
    public const val AUTO: Int = 0
    public const val NONE: Int = 1
    public const val MANUAL: Int = 2

    public fun isEnabled(mode: Int): Boolean = mode != NONE
}
