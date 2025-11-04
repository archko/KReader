package com.archko.reader.pdf.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

public fun String.inferName(): String {
    val separatorIndex = this.lastIndexOf('/').takeIf { it != -1 } ?: this.lastIndexOf('\\')
    val fileName = if (separatorIndex != -1) {
        this.substring(separatorIndex + 1)
    } else {
        this
    }
    return fileName
}

@Composable
public fun Dp.toIntPx(): Int {
    return with(LocalDensity.current) { this@toIntPx.roundToPx() }
}