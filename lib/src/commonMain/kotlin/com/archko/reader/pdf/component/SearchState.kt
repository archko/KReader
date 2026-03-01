package com.archko.reader.pdf.component

import androidx.compose.ui.geometry.Offset
import com.archko.reader.pdf.entity.MuPdfQuad

/**
 * 搜索状态
 * @author: archko 2026/3/1
 */
public data class SearchState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val currentIndex: Int = -1,
    val isSearching: Boolean = false,
    val totalCount: Int = 0
) {
    val hasResults: Boolean get() = results.isNotEmpty()
    val currentResult: SearchResult? get() = if (currentIndex >= 0 && currentIndex < results.size) results[currentIndex] else null
}

/**
 * 搜索结果
 */
public data class SearchResult(
    val pageIndex: Int,
    val text: String,
    val quads: List<MuPdfQuad>,
    val context: String  // 上下文预览
)

/**
 * 搜索选项
 */
public data class SearchOptions(
    val caseSensitive: Boolean = false,
    val wholeWord: Boolean = false
)
