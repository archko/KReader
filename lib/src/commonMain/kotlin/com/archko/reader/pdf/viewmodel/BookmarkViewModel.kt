package com.archko.reader.pdf.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archko.reader.pdf.cache.AppDatabase
import com.archko.reader.pdf.component.SearchState
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.Bookmark
import com.archko.reader.pdf.util.getFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 书签管理ViewModel
 * @author: archko 2026/3/1
 */
public class BookmarkViewModel : ViewModel() {
    public var database: AppDatabase? = null

    private val _bookmarkList = MutableStateFlow<List<Bookmark>>(emptyList())
    public val bookmarkList: StateFlow<List<Bookmark>> = _bookmarkList

    private val _currentPathBookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    public val currentPathBookmarks: StateFlow<List<Bookmark>> = _currentPathBookmarks

    // 搜索相关状态
    private val _searchState = MutableStateFlow(SearchState())
    public val searchState: StateFlow<SearchState> = _searchState

    /**
     * 加载指定文档的书签
     */
    public fun loadBookmarks(path: String) {
        viewModelScope.launch {
            val name = path.getFileName()
            val bookmarks = database?.bookmarkDao()?.getBookmarksByPath(name) ?: emptyList()
            _currentPathBookmarks.value = bookmarks
            println("BookmarkViewModel.loadBookmarks: name=$name, count=${bookmarks.size}")
        }
    }

    /**
     * 添加书签
     */
    public fun addBookmark(
        path: String,
        pageIndex: Int,
        title: String? = null,
        note: String? = null,
        color: Long? = null,
        scrollY: Long? = null
    ) {
        viewModelScope.launch {
            val name = path.getFileName()
            val bookmark = Bookmark(
                path = name,
                pageIndex = pageIndex,
                title = title,
                note = note,
                color = color,
                scrollY = scrollY
            )
            database?.bookmarkDao()?.insertBookmark(bookmark)
            println("BookmarkViewModel.addBookmark: $bookmark")
            
            // 重新加载当前文档的书签
            loadBookmarks(name)
        }
    }

    /**
     * 更新书签
     */
    public fun updateBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            bookmark.updateAt = System.currentTimeMillis()
            database?.bookmarkDao()?.updateBookmark(bookmark)
            println("BookmarkViewModel.updateBookmark: $bookmark")
            
            // 重新加载当前文档的书签
            loadBookmarks(bookmark.path)
        }
    }

    /**
     * 删除书签
     */
    public fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            database?.bookmarkDao()?.deleteBookmark(bookmark)
            println("BookmarkViewModel.deleteBookmark: $bookmark")
            
            // 重新加载当前文档的书签
            loadBookmarks(bookmark.path)
        }
    }

    /**
     * 检查指定页面是否有书签
     */
    public suspend fun hasBookmarkAtPage(path: String, pageIndex: Int): Boolean {
        val name = path.getFileName()
        return database?.bookmarkDao()?.getBookmarkByPageAndPath(name, pageIndex) != null
    }

    /**
     * 获取指定页面的书签
     */
    public suspend fun getBookmarkAtPage(path: String, pageIndex: Int): Bookmark? {
        val name = path.getFileName()
        return database?.bookmarkDao()?.getBookmarkByPageAndPath(name, pageIndex)
    }

    /**
     * 加载所有书签
     */
    public fun loadAllBookmarks() {
        viewModelScope.launch {
            val bookmarks = database?.bookmarkDao()?.getAllBookmarks() ?: emptyList()
            _bookmarkList.value = bookmarks
            println("BookmarkViewModel.loadAllBookmarks: count=${bookmarks.size}")
        }
    }

    /**
     * 执行搜索
     */
    public fun performSearch(query: String, decoder: ImageDecoder, caseSensitive: Boolean = false) {
        if (query.isBlank()) {
            return
        }

        _searchState.value = _searchState.value.copy(isSearching = true, query = query)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val results = decoder.search(query, caseSensitive)
                withContext(Dispatchers.Main) {
                    _searchState.value = SearchState(
                        query = query,
                        results = results,
                        currentIndex = if (results.isNotEmpty()) 0 else -1,
                        isSearching = false,
                        totalCount = results.size
                    )
                }
            } catch (e: Exception) {
                println("搜索失败: ${e.message}")
                withContext(Dispatchers.Main) {
                    _searchState.value = _searchState.value.copy(isSearching = false)
                }
            }
        }
    }

    /**
     * 更新搜索查询
     */
    public fun updateSearchQuery(query: String) {
        _searchState.value = _searchState.value.copy(query = query)
    }

    /**
     * 跳转到指定搜索结果
     */
    public fun goToSearchResult(index: Int) {
        if (index < 0 || index >= _searchState.value.results.size) return
        _searchState.value = _searchState.value.copy(currentIndex = index)
    }

    /**
     * 下一个搜索结果
     */
    public fun goToNextResult() {
        val state = _searchState.value
        if (state.results.isEmpty()) return
        val nextIndex = (state.currentIndex + 1) % state.results.size
        goToSearchResult(nextIndex)
    }

    /**
     * 上一个搜索结果
     */
    public fun goToPreviousResult() {
        val state = _searchState.value
        if (state.results.isEmpty()) return
        val prevIndex = if (state.currentIndex <= 0) {
            state.results.size - 1
        } else {
            state.currentIndex - 1
        }
        goToSearchResult(prevIndex)
    }

    /**
     * 重置搜索状态
     */
    public fun resetSearch() {
        _searchState.value = SearchState()
        println("BookmarkViewModel.resetSearch: 搜索状态已重置")
    }

    /**
     * 重置所有数据（文档关闭时调用）
     */
    public fun resetAll() {
        _currentPathBookmarks.value = emptyList()
        resetSearch()
        println("BookmarkViewModel.resetAll: 所有数据已重置")
    }
}
