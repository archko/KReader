package com.archko.reader.pdf.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.archko.reader.pdf.cache.getStoragePath

public fun String.inferName(): String {
    val separatorIndex = this.lastIndexOf('/').takeIf { it != -1 } ?: this.lastIndexOf('\\')
    val fileName = if (separatorIndex != -1) {
        this.substring(separatorIndex + 1)
    } else {
        this
    }
    return fileName
}

/**
 * 从文件全路径中获取文件名（包含扩展名）
 * 例如: "/path/to/file.pdf" -> "file.pdf"
 */
public fun String.getFileName(): String {
    if (this.isEmpty()) return ""

    val separatorIndex = this.lastIndexOf('/').coerceAtLeast(this.lastIndexOf('\\'))
    return if (separatorIndex >= 0 && separatorIndex < this.length - 1) {
        this.substring(separatorIndex + 1)
    } else {
        this
    }
}

/**
 * 从文件路径中获取扩展名（不包含点号）
 * 例如: "/path/to/file.pdf" -> "pdf"
 *       "document.tar.gz" -> "gz"
 */
public fun String.getExtension(): String {
    if (this.isEmpty()) return ""

    val fileName = this.getFileName()
    val dotIndex = fileName.lastIndexOf('.')

    return if (dotIndex > 0 && dotIndex < fileName.length - 1) {
        fileName.substring(dotIndex + 1).lowercase()
    } else {
        ""
    }
}

@Composable
public fun Dp.toIntPx(): Int {
    return with(LocalDensity.current) { this@toIntPx.roundToPx() }
}

/**
 * Normalize path by removing storage path prefix if present
 * This ensures consistent path format for database queries
 */
public fun normalizePath(path: String?): String {
    if (path.isNullOrEmpty()) {
        return ""
    }
    val storagePath = getStoragePath()
    return if (path.startsWith(storagePath)) {
        path.removePrefix(storagePath)
    } else {
        path
    }
}

/**
 * Get full absolute path by adding storage path prefix if needed
 */
public fun getAbsolutePath(path: String?): String {
    if (path.isNullOrEmpty()) {
        return ""
    }
    val storagePath = getStoragePath()
    return if (path.startsWith(storagePath)) {
        path
    } else {
        "$storagePath/$path"
    }
}