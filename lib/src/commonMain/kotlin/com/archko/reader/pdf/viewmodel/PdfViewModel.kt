package com.archko.reader.pdf.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archko.reader.pdf.cache.AppDatabase
import com.archko.reader.pdf.entity.Recent
import kotlinx.coroutines.CompletableDeferred
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

    // 分页相关状态
    private val _hasMoreData = MutableStateFlow(true)
    public val hasMoreData: StateFlow<Boolean> = _hasMoreData

    private val _isLoading = MutableStateFlow(false)
    public val isLoading: StateFlow<Boolean> = _isLoading

    public val pageSize: Int = 15 // 每页15条记录

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
            println("PdfViewModel.insertOrUpdate:${progress}")

            // 增量更新：如果记录不在当前列表中且列表未满，添加到开头
            if (progress != null) {
                val currentList = _recentList.value.toMutableList()
                val existingIndex = currentList.indexOfFirst { it.path == path }
                if (existingIndex != -1) {
                    // 如果记录已存在，从原位置删除，然后添加到开头（按时间排序）
                    currentList.removeAt(existingIndex)
                    currentList.add(0, progress!!)
                    _recentList.value = currentList
                    println("PdfViewModel.insertOrUpdate - moved existing record to beginning from index: $existingIndex")
                } else if (currentList.size < pageSize) {
                    // 如果记录不存在且列表未满，添加到开头
                    currentList.add(0, progress!!)
                    _recentList.value = currentList
                    println("PdfViewModel.insertOrUpdate - added new record to beginning")
                } else {
                    // 如果记录不存在且列表已满，不添加到当前列表（保持分页状态）
                    println("PdfViewModel.insertOrUpdate - list is full, skipping add to current page")
                }
            }
        }
    }

    public suspend fun insertOrUpdateAndWait(path: String, pageCount: Long, reflow: Long) {
        this.path = path
        val deferred = CompletableDeferred<Unit>()
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
                        reflow,
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
            println("PdfViewModel.insertOrUpdateAndWait:${progress}")

            // 增量更新：如果记录不在当前列表中且列表未满，添加到开头
            if (progress != null) {
                val currentList = _recentList.value.toMutableList()
                val existingIndex = currentList.indexOfFirst { it.path == path }
                if (existingIndex != -1) {
                    // 如果记录已存在，从原位置删除，然后添加到开头（按时间排序）
                    currentList.removeAt(existingIndex)
                    currentList.add(0, progress!!)
                    _recentList.value = currentList
                    println("PdfViewModel.insertOrUpdateAndWait - moved existing record to beginning from index: $existingIndex")
                } else if (currentList.size < pageSize) {
                    // 如果记录不存在且列表未满，添加到开头
                    currentList.add(0, progress!!)
                    _recentList.value = currentList
                    println("PdfViewModel.insertOrUpdateAndWait - added new record to beginning")
                } else {
                    // 如果记录不存在且列表已满，不添加到当前列表（保持分页状态）
                    println("PdfViewModel.insertOrUpdateAndWait - list is full, skipping add to current page")
                }
            }

            deferred.complete(Unit)
        }
        deferred.await()
    }

    public fun updateProgress(
        page: Long,
        pageCount: Long,
        zoom: Double,
        crop: Long,
        scrollX: Long,
        scrollY: Long,
        scrollOri: Long,
        reflow: Long,
    ) {
        println("PdfViewModel.updateProgress:$page, count:$pageCount, zoom:$zoom, crop:$crop, scrollX:$scrollX, scrollY:$scrollY, scrollOri:$scrollOri, reflow:$reflow, old:$progress")
        if (path == null) {
            return
        }
        viewModelScope.launch {
            // 如果progress为空但path不为空，则新建一个记录
            if (progress == null && path != null) {
                println("PdfViewModel.get:$path, page:$page")

                progress = database?.recentDao()?.getRecent(path!!)
            }
            if (progress == null) {
                val newProgress = Recent(
                    path!!,
                    page,
                    pageCount,
                    System.currentTimeMillis(),
                    System.currentTimeMillis(),
                    crop,
                    reflow,
                    scrollOri,
                    zoom,
                    scrollX,
                    scrollY,
                )
                database?.recentDao()?.addRecent(newProgress)
                progress = database?.recentDao()?.getRecent(path!!)
                println("PdfViewModel.updateProgress-created:$progress")
            } else {
                // 更新现有记录
                progress?.run {
                    progress!!.apply {
                        this.page = page
                        this.pageCount = pageCount
                        updateAt = System.currentTimeMillis()
                        this.crop = crop
                        this.reflow = reflow
                        this.zoom = zoom
                        this.scrollX = scrollX
                        this.scrollY = scrollY
                        this.scrollOri = scrollOri
                    }
                    database?.recentDao()?.updateRecent(progress!!)
                    path?.run {
                        progress = database?.recentDao()?.getRecent(path!!)
                        println("PdfViewModel.updateProgress.after:$progress")
                    }
                }
            }

            // 如果progress不为空，更新列表
            progress?.let { currentProgress ->
                // 增量更新：从列表中删除旧记录，然后添加到开头（按updateAt排序）
                val currentList = _recentList.value.toMutableList()
                val updatedIndex = currentList.indexOfFirst { it.path == currentProgress.path }
                println("PdfViewModel.updateProgress - currentList size: ${currentList.size}, updatedIndex: $updatedIndex")

                if (updatedIndex != -1) {
                    // 从原位置删除旧记录
                    currentList.removeAt(updatedIndex)
                    println("PdfViewModel.updateProgress - removed old record from index: $updatedIndex")
                }

                // 将更新后的记录添加到列表开头（因为updateAt最新）
                currentList.add(0, currentProgress)
                _recentList.value = currentList
                println("PdfViewModel.updateProgress - added updated record to beginning, new list size: ${_recentList.value.size}")
            }
        }
    }

    public fun updateProgress(
        page: Long,
        path: String
    ) {
        viewModelScope.launch {
            val old = database?.recentDao()?.getRecent(path)
            println("PdfViewModel.updateProgress:$page, path:$path, old:$old")

            if (old != null) {
                old.apply {
                    this.page = page
                    updateAt = System.currentTimeMillis()
                }
                database?.recentDao()?.updateRecent(old)
                println("PdfViewModel.updateProgress.after:$old")
            }
        }
    }

    public fun loadRecents() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val progresses = database?.recentDao()?.getRecents(0, pageSize)
                println("PdfViewModel.loadRecents:${progresses?.size}")
                if (progresses != null) {
                    if (progresses.isNotEmpty()) {
                        _recentList.value = progresses
                        // 如果查询结果少于pageSize，说明没有更多数据了
                        _hasMoreData.value = progresses.size >= pageSize
                    } else {
                        _recentList.value = emptyList()
                        _hasMoreData.value = false
                    }
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    public fun loadMoreRecents() {
        if (_isLoading.value || !_hasMoreData.value) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val currentList = _recentList.value
                if (currentList.isEmpty()) {
                    _hasMoreData.value = false
                    return@launch
                }

                // 获取当前列表最后一条数据的updateAt时间
                val lastUpdateAt = currentList.last().updateAt ?: 0L

                // 查询updateAt小于最后一条数据的所有记录，按updateAt desc排序
                val progresses = database?.recentDao()?.getRecentsAfter(lastUpdateAt, pageSize)

                if (progresses != null && progresses.isNotEmpty()) {
                    val newList = currentList.toMutableList()
                    newList.addAll(progresses)
                    _recentList.value = newList

                    // 如果查询结果少于pageSize，说明没有更多数据了
                    _hasMoreData.value = progresses.size >= pageSize
                } else {
                    _hasMoreData.value = false
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    public fun clear() {
        viewModelScope.launch {
            database?.recentDao()?.deleteAllRecents()

            // 直接清空列表并重置分页状态
            _recentList.value = emptyList()
            _hasMoreData.value = false
        }
    }

    public fun deleteRecent(recent: Recent) {
        viewModelScope.launch {
            database?.recentDao()?.deleteRecent(recent)
            println("PdfViewModel.deleteRecent:${recent}")

            // 从列表中移除被删除的记录
            val currentList = _recentList.value.toMutableList()
            val removedIndex = currentList.indexOfFirst { it.path == recent.path }
            if (removedIndex != -1) {
                currentList.removeAt(removedIndex)
                _recentList.value = currentList

                // 如果删除后列表变空，重新检查是否有更多数据
                if (currentList.isEmpty()) {
                    val totalCount = database?.recentDao()?.recentCount() ?: 0
                    _hasMoreData.value = totalCount > 0
                }
            }
        }
    }

    public suspend fun getProgress(absolutePath: String) {
        this.path = absolutePath
        val deferred = CompletableDeferred<Unit>()
        viewModelScope.launch {
            progress = database?.recentDao()?.getRecent(absolutePath)
            println("PdfViewModel.getProgress:${progress}")
            deferred.complete(Unit)
        }
        deferred.await()
    }
}