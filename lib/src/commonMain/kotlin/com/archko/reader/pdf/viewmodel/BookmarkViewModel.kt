package com.archko.reader.pdf.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archko.reader.pdf.cache.AppDatabase
import com.archko.reader.pdf.entity.Bookmark
import com.archko.reader.pdf.util.getFileName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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
}
