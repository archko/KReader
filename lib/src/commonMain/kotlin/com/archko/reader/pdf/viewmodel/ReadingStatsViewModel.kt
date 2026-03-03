package com.archko.reader.pdf.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archko.reader.pdf.cache.AppDatabase
import com.archko.reader.pdf.entity.ReadingStats
import com.archko.reader.pdf.util.getFileName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 阅读统计ViewModel,根据书名,不是路径取数据
 * @author: archko 2026/3/1
 */
public class ReadingStatsViewModel : ViewModel() {
    public var database: AppDatabase? = null

    private val _currentStats = MutableStateFlow<ReadingStats?>(null)
    public val currentStats: StateFlow<ReadingStats?> = _currentStats

    /**
     * 加载指定文档的统计数据
     */
    public fun loadStats(path: String) {
        viewModelScope.launch {
            val name = path.getFileName()
            val stats = database?.readingStatsDao()?.getStatsByPath(name)
            _currentStats.value = stats
            println("ReadingStatsViewModel.loadStats: name=$name, stats=$stats")
        }
    }

    /**
     * 实时更新统计数据（用于在文档打开期间查看统计）
     */
    public fun updateCurrentStats(
        path: String,
        sessionDuration: Long,
        currentPage: Int,
        annotationCount: Int,
        bookmarkCount: Int
    ) {
        viewModelScope.launch {
            val name = path.getFileName()
            val stats = database?.readingStatsDao()?.getStatsByPath(name)
            if (stats != null) {
                // 临时更新当前显示的统计数据（不保存到数据库）
                val updatedStats = stats.copy().apply {
                    val tempTotalTime = stats.totalReadingTime + sessionDuration
                    val tempSessionCount = stats.sessionCount + 1

                    totalReadingTime = tempTotalTime
                    lastSessionTime = sessionDuration
                    averageSessionTime =
                        if (tempSessionCount > 0) tempTotalTime / tempSessionCount else 0

                    if (currentPage > completedPages) {
                        completedPages = currentPage
                    }

                    this.annotationCount = annotationCount
                    this.bookmarkCount = bookmarkCount
                }
                _currentStats.value = updatedStats
                println("ReadingStatsViewModel.updateCurrentStats: 临时更新统计 $updatedStats")
            }
        }
    }

    /**
     * 开始新的阅读会话
     */
    public suspend fun startSession(path: String, totalPages: Int) {
        val name = path.getFileName()
        val stats = database?.readingStatsDao()?.getStatsByPath(name)
        if (stats == null) {
            // 首次阅读，创建新记录
            val newStats = ReadingStats(name, totalPages)
            database?.readingStatsDao()?.insertStats(newStats)
            _currentStats.value = newStats
            println("ReadingStatsViewModel.startSession: 创建新统计记录 $newStats")
        } else {
            _currentStats.value = stats
            println("ReadingStatsViewModel.startSession: 加载已有统计记录 $stats")
        }
    }

    /**
     * 结束阅读会话，更新统计数据
     */
    public fun endSession(
        path: String,
        sessionDuration: Long,  // 本次阅读时长（秒）
        currentPage: Int,
        annotationCount: Int,
        bookmarkCount: Int
    ) {
        viewModelScope.launch {
            val name = path.getFileName()
            val stats = database?.readingStatsDao()?.getStatsByPath(name) ?: return@launch

            val currentDate = getCurrentDate()
            val isNewDay = stats.lastSessionDate != currentDate

            stats.apply {
                // 更新时长统计
                totalReadingTime += sessionDuration
                lastSessionTime = sessionDuration
                sessionCount += 1
                averageSessionTime = if (sessionCount > 0) totalReadingTime / sessionCount else 0

                // 更新时间
                lastReadAt = System.currentTimeMillis()
                lastSessionDate = currentDate

                // 更新连续阅读天数
                if (isNewDay) {
                    val daysDiff = calculateDaysDifference(stats.lastSessionDate, currentDate)
                    consecutiveDays = if (daysDiff == 1) consecutiveDays + 1 else 1
                }

                // 更新页面进度
                if (currentPage > completedPages) {
                    completedPages = currentPage
                }

                // 更新计数
                this.annotationCount = annotationCount
                this.bookmarkCount = bookmarkCount
            }

            database?.readingStatsDao()?.updateStats(stats)
            _currentStats.value = stats
            println("ReadingStatsViewModel.endSession: 更新统计 $stats")
        }
    }

    /**
     * 获取总阅读时长
     */
    public suspend fun getTotalReadingTime(): Long {
        return database?.readingStatsDao()?.getTotalReadingTime() ?: 0L
    }

    private fun getCurrentDate(): String {
        val calendar = Calendar.getInstance()
        return String.format(
            "%04d-%02d-%02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    private fun calculateDaysDifference(date1: String, date2: String): Int {
        try {
            val parts1 = date1.split("-")
            val parts2 = date2.split("-")

            val cal1 = Calendar.getInstance().apply {
                set(parts1[0].toInt(), parts1[1].toInt() - 1, parts1[2].toInt(), 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val cal2 = Calendar.getInstance().apply {
                set(parts2[0].toInt(), parts2[1].toInt() - 1, parts2[2].toInt(), 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val diffMillis = cal2.timeInMillis - cal1.timeInMillis
            return (diffMillis / (1000 * 60 * 60 * 24)).toInt()
        } catch (e: Exception) {
            return 0
        }
    }
}
