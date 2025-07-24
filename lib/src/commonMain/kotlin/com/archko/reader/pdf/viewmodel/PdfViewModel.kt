package com.archko.reader.pdf.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archko.reader.pdf.cache.AppDatabase
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
    public var progress: Recent? = null
    public var path: String? = null

    public fun insertOrUpdate(path: String, pageCount: Long) {
        this.path = path
        viewModelScope.launch {
            val selProgress = database?.recentDao()?.getRecent(path)
            if (selProgress == null) {
                database?.recentDao()?.addRecent(
                    Recent(
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
                )
            } else {
                selProgress.apply {
                    this.pageCount = pageCount
                    createAt = System.currentTimeMillis()
                }
                database?.recentDao()?.updateRecent(selProgress)
            }
            progress = database?.recentDao()?.getRecent(path)
            println("insertOrUpdate:${progress}")
        }
    }

    public fun updateProgress(page: Long, pageCount: Long, zoom: Double, crop: Long) {
        println("updateProgress:$page, pc:$pageCount, old:$progress")
        progress?.run {
            viewModelScope.launch {
                progress!!.apply {
                    this.page = page
                    this.pageCount = pageCount
                    createAt = System.currentTimeMillis()
                    this.crop = crop
                    this.zoom = zoom
                }
                database?.recentDao()?.updateRecent(progress!!)
                path?.run {
                    progress = database?.recentDao()?.getRecent(path!!)
                }
                loadRecents()
            }
        }
    }

    public fun loadRecents() {
        viewModelScope.launch {
            val progresses = database?.recentDao()?.getRecents(0, 100)
            if (progresses != null) {
                if (progresses.isNotEmpty()) {
                    _recentList.value = progresses
                }
            }
        }
    }

    public fun clear() {
        viewModelScope.launch {
            database?.recentDao()?.deleteAllRecents()
            loadRecents()
        }
    }

    public fun deleteRecent(recent: Recent) {
        viewModelScope.launch {
            database?.recentDao()?.deleteRecent(recent)
            println("deleteRecent:${recent}")
            loadRecents()
        }
    }
}