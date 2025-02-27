package com.archko.reader.pdf.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archko.reader.pdf.cache.AppDatabase
import com.archko.reader.pdf.cache.Progress
import com.archko.reader.pdf.entity.Recent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * @author: archko 2025/2/14 :21:42
 */
public class PdfViewModel : ViewModel() {
    public var database: AppDatabase? = null
    private val _recentList = MutableStateFlow<List<Recent>>(mutableListOf())
    public val recentList: StateFlow<List<Recent>> = _recentList
    public var progress: Progress? = null

    public fun insertOrUpdate(path: String, pageCount: Long) {
        viewModelScope.launch {
            val selProgress =
                database?.appDatabaseQueries?.selectProgress(path)
                    ?.executeAsOneOrNull()
            if (selProgress == null) {
                database?.appDatabaseQueries?.insertProgress(
                    path,
                    0,
                    pageCount,
                    System.currentTimeMillis(),
                    System.currentTimeMillis(),
                    1,
                    0,
                    0,
                    1.0,
                    0,
                    0
                )
            } else {
                database?.appDatabaseQueries?.updateProgress(
                    selProgress.page,
                    pageCount,
                    System.currentTimeMillis(),
                    selProgress.crop,
                    selProgress.reflow,
                    selProgress.zoom,
                    selProgress.scrollOri,
                    selProgress.scrollX,
                    selProgress.scrollY,
                    selProgress.id
                )
            }
            progress = database?.appDatabaseQueries?.selectProgress(path)?.executeAsOneOrNull()
            println("insertOrUpdate:${progress}")
        }
    }

    public fun updateProgress(page: Long, pageCount: Long, zoom: Double, crop: Long) {
        println("updateProgress:$page, pc:$pageCount, old:$progress")
        progress?.run {
            viewModelScope.launch {
                database?.appDatabaseQueries?.updateProgress(
                    page,
                    pageCount,
                    System.currentTimeMillis(),
                    crop,
                    0,
                    zoom,
                    0,
                    0,
                    0,
                    progress!!.id
                )
                progress = database?.appDatabaseQueries?.selectProgress(path)?.executeAsOneOrNull()
                loadRecents()
            }
        }
    }

    public fun loadRecents() {
        viewModelScope.launch {
            val progresses = database?.appDatabaseQueries?.selectProgresses()?.executeAsList()
            if (progresses != null) {
                if (progresses.isNotEmpty()) {
                    val list = mutableListOf<Recent>()
                    progresses.forEach {
                        list.add(
                            Recent(
                                it.id,
                                it.path,
                                it.page,
                                it.pageCount,
                                it.createAt,
                                it.updateAt,
                                it.crop,
                                it.reflow,
                                it.scrollOri,
                                it.zoom,
                                it.scrollX,
                                it.scrollY
                            )
                        )
                    }
                    _recentList.value = list
                }
            }
        }
    }

    public fun clear() {
        viewModelScope.launch {
            database?.appDatabaseQueries?.removeAllProgresses()
            loadRecents()
        }
    }
}