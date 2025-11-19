package com.archko.reader.pdf.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archko.reader.pdf.cache.AppDatabase
import com.archko.reader.pdf.cache.getStoragePath
import com.archko.reader.pdf.entity.Recent
import com.archko.reader.pdf.util.getAbsolutePath
import com.archko.reader.pdf.util.getExtension
import com.archko.reader.pdf.util.getFileName
import com.archko.reader.pdf.util.normalizePath
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

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

    public var recent: Recent? = null
    public var path: String? = null

    public fun insertOrUpdate(path: String, pageCount: Long) {
        this.path = path
        viewModelScope.launch {
            val normalizedPath = normalizePath(path)
            val absolutePath = getAbsolutePath(normalizedPath)
            val file = File(absolutePath)
            val selRecent = database?.recentDao()?.getRecent(normalizedPath)
            if (selRecent == null) {
                database?.recentDao()?.addRecent(
                    Recent(
                        path = normalizedPath,
                        page = 0L,
                        pageCount = pageCount,
                        createAt = System.currentTimeMillis(),
                        updateAt = System.currentTimeMillis(),
                        crop = 1L,
                        reflow = 0L,
                        scrollOri = 1L,
                        zoom = 1.0,
                        scrollX = 0L,
                        scrollY = 0L,
                        name = absolutePath.getFileName(),
                        ext = absolutePath.getExtension(),
                        size = file.length(),
                        readTimes = 1L,
                        progress = 0L,
                        isFavorited = 0L,
                        inRecent = 1L
                    )
                )
            } else {
                selRecent.apply {
                    this.pageCount = pageCount
                    this.updateAt = System.currentTimeMillis()
                    this.readTimes = (this.readTimes ?: 0) + 1
                    this.name = absolutePath.getFileName()
                    this.ext = absolutePath.getExtension()
                    this.size = file.length()
                }
                database?.recentDao()?.updateRecent(selRecent)
            }
            recent = database?.recentDao()?.getRecent(normalizedPath)
            println("PdfViewModel.insertOrUpdate:${recent}")

            // 增量更新：如果记录不在当前列表中且列表未满，添加到开头
            if (recent != null) {
                val currentList = _recentList.value.toMutableList()
                val existingIndex = currentList.indexOfFirst { it.path == normalizedPath }
                if (existingIndex != -1) {
                    // 如果记录已存在，从原位置删除，然后添加到开头（按时间排序）
                    currentList.removeAt(existingIndex)
                    currentList.add(0, recent!!)
                    _recentList.value = currentList
                    println("PdfViewModel.insertOrUpdate - moved existing record to beginning from index: $existingIndex")
                } else if (currentList.size < pageSize) {
                    // 如果记录不存在且列表未满，添加到开头
                    currentList.add(0, recent!!)
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
            val normalizedPath = normalizePath(path)
            val absolutePath = getAbsolutePath(normalizedPath)
            val file = File(absolutePath)
            val selRecent = database?.recentDao()?.getRecent(normalizedPath)
            if (selRecent == null) {
                database?.recentDao()?.addRecent(
                    Recent(
                        path = normalizedPath,
                        page = 0L,
                        pageCount = pageCount,
                        createAt = System.currentTimeMillis(),
                        updateAt = System.currentTimeMillis(),
                        crop = 1L,
                        reflow = reflow,
                        scrollOri = 1L,
                        zoom = 1.0,
                        scrollX = 0L,
                        scrollY = 0L,
                        name = absolutePath.getFileName(),
                        ext = absolutePath.getExtension(),
                        size = file.length(),
                        readTimes = 1L,
                        progress = 0L,
                        isFavorited = 0L,
                        inRecent = 1L
                    )
                )
            } else {
                selRecent.apply {
                    this.pageCount = pageCount
                    this.updateAt = System.currentTimeMillis()
                    this.reflow = reflow
                    this.readTimes = (this.readTimes ?: 0) + 1
                    this.name = absolutePath.getFileName()
                    this.ext = absolutePath.getExtension()
                    this.size = file.length()
                }
                database?.recentDao()?.updateRecent(selRecent)
            }
            this@PdfViewModel.recent = database?.recentDao()?.getRecent(normalizedPath)
            println("PdfViewModel.insertOrUpdateAndWait:${this@PdfViewModel.recent}")

            // 增量更新：如果记录不在当前列表中且列表未满，添加到开头
            if (this@PdfViewModel.recent != null) {
                val currentList = _recentList.value.toMutableList()
                val existingIndex = currentList.indexOfFirst { it.path == normalizedPath }
                if (existingIndex != -1) {
                    // 如果记录已存在，从原位置删除，然后添加到开头（按时间排序）
                    currentList.removeAt(existingIndex)
                    currentList.add(0, this@PdfViewModel.recent!!)
                    _recentList.value = currentList
                    println("PdfViewModel.insertOrUpdateAndWait - moved existing record to beginning from index: $existingIndex")
                } else if (currentList.size < pageSize) {
                    // 如果记录不存在且列表未满，添加到开头
                    currentList.add(0, this@PdfViewModel.recent!!)
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

    public fun updateRecent(
        page: Long,
        pageCount: Long,
        zoom: Double,
        crop: Long,
        scrollX: Long,
        scrollY: Long,
        scrollOri: Long,
        reflow: Long,
    ) {
        println("PdfViewModel.updateRecent:$page, count:$pageCount, zoom:$zoom, crop:$crop, scrollX:$scrollX, scrollY:$scrollY, scrollOri:$scrollOri, reflow:$reflow, old:$recent")
        if (path == null) {
            return
        }
        viewModelScope.launch {
            val normalizedPath = normalizePath(path!!)
            val absolutePath = getAbsolutePath(normalizedPath)
            val file = File(absolutePath)
            // 如果recent为空但path不为空，则新建一个记录
            if (recent == null && path != null) {
                println("PdfViewModel.get:$normalizedPath, path:$path, page:$page")

                recent = database?.recentDao()?.getRecent(normalizedPath)
            }
            if (recent == null) {
                val recent = Recent(
                    path = normalizedPath,
                    page = page,
                    pageCount = pageCount,
                    createAt = System.currentTimeMillis(),
                    updateAt = System.currentTimeMillis(),
                    crop = crop,
                    reflow = reflow,
                    scrollOri = scrollOri,
                    zoom = zoom,
                    scrollX = scrollX,
                    scrollY = scrollY,
                    name = absolutePath.getFileName(),
                    ext = absolutePath.getExtension(),
                    size = file.length(),
                    readTimes = 1L,
                    progress = if (pageCount > 0) (page * 100 / pageCount) else 0L,
                    isFavorited = 0L,
                    inRecent = 1L
                )
                database?.recentDao()?.addRecent(recent)
                this@PdfViewModel.recent = database?.recentDao()?.getRecent(normalizedPath)
                println("PdfViewModel.updateRecent-created:${this@PdfViewModel.recent}")
            } else {
                // 更新现有记录
                recent?.run {
                    recent!!.apply {
                        this.page = page
                        this.pageCount = pageCount
                        this.updateAt = System.currentTimeMillis()
                        this.crop = crop
                        this.reflow = reflow
                        this.zoom = zoom
                        this.scrollX = scrollX
                        this.scrollY = scrollY
                        this.scrollOri = scrollOri
                        this.progress = if (pageCount > 0) (page * 100 / pageCount) else 0L
                        this.name = absolutePath.getFileName()
                        this.ext = absolutePath.getExtension()
                        this.size = file.length()
                    }
                    database?.recentDao()?.updateRecent(recent!!)
                    recent = database?.recentDao()?.getRecent(normalizedPath)
                    println("PdfViewModel.updateRecent.after:$recent")
                }
            }

            // 如果recent不为空，更新列表
            recent?.let { currentRecent ->
                // 增量更新：从列表中删除旧记录，然后添加到开头（按updateAt排序）
                val currentList = _recentList.value.toMutableList()
                val updatedIndex = currentList.indexOfFirst { it.path == currentRecent.path }
                println("PdfViewModel.updateRecent - currentList size: ${currentList.size}, updatedIndex: $updatedIndex")

                if (updatedIndex != -1) {
                    // 从原位置删除旧记录
                    currentList.removeAt(updatedIndex)
                    println("PdfViewModel.updateRecent - removed old record from index: $updatedIndex")
                }

                // 将更新后的记录添加到列表开头（因为updateAt最新）
                currentList.add(0, currentRecent)
                _recentList.value = currentList
                println("PdfViewModel.updateRecent - added updated record to beginning, new list size: ${_recentList.value.size}")
            }
        }
    }

    public fun updateRecent(
        page: Long,
        path: String
    ) {
        viewModelScope.launch {
            val normalizedPath = normalizePath(path)
            val old = database?.recentDao()?.getRecent(normalizedPath)
            println("PdfViewModel.updateRecent:$page, path:$normalizedPath, old:$old")

            if (old != null) {
                old.apply {
                    this.page = page
                    updateAt = System.currentTimeMillis()
                }
                database?.recentDao()?.updateRecent(old)
                println("PdfViewModel.updateRecent.after:$old")
            }
        }
    }

    public fun loadRecents() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val recents = database?.recentDao()?.getRecents(0, pageSize)
                println("PdfViewModel.loadRecents:${recents?.size}")
                if (recents != null) {
                    if (recents.isNotEmpty()) {
                        _recentList.value = recents
                        // 如果查询结果少于pageSize，说明没有更多数据了
                        _hasMoreData.value = recents.size >= pageSize
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
                val recents = database?.recentDao()?.getRecentsAfter(lastUpdateAt, pageSize)

                if (recents != null && recents.isNotEmpty()) {
                    val newList = currentList.toMutableList()
                    newList.addAll(recents)
                    _recentList.value = newList

                    // 如果查询结果少于pageSize，说明没有更多数据了
                    _hasMoreData.value = recents.size >= pageSize
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

    public suspend fun getRecent(absolutePath: String) {
        this.path = absolutePath
        val deferred = CompletableDeferred<Unit>()
        viewModelScope.launch {
            val normalizedPath = normalizePath(absolutePath)
            recent = database?.recentDao()?.getRecent(normalizedPath)
            println("PdfViewModel.getRecent:normalizedPath:$normalizedPath, path:$absolutePath, $recent")
            deferred.complete(Unit)
        }
        deferred.await()
    }
}