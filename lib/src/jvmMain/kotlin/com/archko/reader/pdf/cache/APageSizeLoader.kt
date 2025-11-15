package com.archko.reader.pdf.cache

import androidx.compose.ui.geometry.Rect
import com.archko.reader.pdf.entity.APage
import kotlinx.serialization.json.*
import java.io.File

/**
 * @author: archko 2025/11/1 :9:04 下午
 */
 public actual fun getCacheFile(file: File): File {
    val saveFile = File(
        FileUtils.getCacheDirectory("page").absolutePath
                + File.separator
                + file.nameWithoutExtension + ".json"
    )
    return saveFile
}