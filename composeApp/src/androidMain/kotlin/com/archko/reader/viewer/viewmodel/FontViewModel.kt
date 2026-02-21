package com.archko.reader.viewer.viewmodel

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archko.reader.pdf.entity.FontOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * @author: archko 2025/2/14 :21:42
 */
class FontViewModel : ViewModel() {

    private val _fontFiles = MutableStateFlow<List<FontOption>>(emptyList())
    val fontFiles: StateFlow<List<FontOption>> = _fontFiles

    private val _isFontLoading = MutableStateFlow(false)
    val isFontLoading: StateFlow<Boolean> = _isFontLoading

    private val blackListPaths = setOf(
        "/system/fonts/HwFont.ttf",
        "/system/fonts/AndroidClock.ttf",
        "/system/fonts/EmojiIcon.otf",
        "/system/fonts/NotoColorEmoji.ttf",
        "/system/fonts/HW-digit-Bold.ttf",
        "/system/fonts/HW-digit-Medium.ttf",
        "/system/fonts/HW-digit-Regular.ttf",
        "/system/fonts/HWDigit-Regular.ttf"
    )

    fun loadFontsIfNeeded() {
        if (_fontFiles.value.isNotEmpty()) return

        if (_isFontLoading.value) return

        _isFontLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val sdcardFonts = File(Environment.getExternalStorageDirectory(), "fonts")
            val systemFonts = File("/system/fonts")
            val scanDirs = listOf(sdcardFonts, systemFonts)

            val allFiles = scanDirs.flatMap { dir ->
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles { file ->
                        println("font:${file.absolutePath}")
                        val ext = file.extension.lowercase()
                        ext == "ttf" || ext == "otf"// || ext == "ttc"
                    }?.toList() ?: emptyList()
                } else {
                    emptyList()
                }
            }.filter { file ->
                println("filter.font:${file.absolutePath}")
                // 1. 过滤掉黑名单中的绝对路径
                // 2. 也可以通过文件名关键词过滤，比如过滤掉阿拉伯文(Naskh)
                !blackListPaths.contains(file.absolutePath) &&
                        !file.name.contains("Naskh", ignoreCase = true)
            }//.sortedBy { it.name.lowercase() }

            val options = mutableListOf<FontOption>()
            options.add(FontOption.SystemDefault)
            options.addAll(allFiles.map { FontOption.CustomFile(it) })

            _fontFiles.value = options
            _isFontLoading.value = false

            println("Final font list size: ${allFiles.size}")
        }
    }
}