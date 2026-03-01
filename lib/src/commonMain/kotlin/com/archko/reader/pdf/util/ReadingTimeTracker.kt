package com.archko.reader.pdf.util

/**
 * 阅读时长追踪器
 * @author: archko 2026/3/1
 */
public class ReadingTimeTracker {
    private var sessionStartTime: Long = 0
    private var accumulatedTime: Long = 0  // 累计时长（毫秒）
    private var isActive: Boolean = false

    /**
     * 开始会话
     */
    public fun startSession() {
        if (isActive) return
        sessionStartTime = System.currentTimeMillis()
        accumulatedTime = 0
        isActive = true
        println("ReadingTimeTracker.startSession: ${sessionStartTime}")
    }

    /**
     * 暂停会话（返回本次累计时长，秒）
     */
    public fun pauseSession(): Long {
        if (!isActive) return 0
        val duration = System.currentTimeMillis() - sessionStartTime
        accumulatedTime += duration
        isActive = false
        println("ReadingTimeTracker.pauseSession: duration=${duration}ms, accumulated=${accumulatedTime}ms")
        return accumulatedTime / 1000  // 返回秒数
    }

    /**
     * 恢复会话
     */
    public fun resumeSession() {
        if (isActive) return
        sessionStartTime = System.currentTimeMillis()
        isActive = true
        println("ReadingTimeTracker.resumeSession: ${sessionStartTime}")
    }

    /**
     * 获取当前会话时长（秒）
     */
    public fun getSessionDuration(): Long {
        val currentDuration = if (isActive) {
            System.currentTimeMillis() - sessionStartTime
        } else {
            0
        }
        return (accumulatedTime + currentDuration) / 1000
    }

    /**
     * 重置追踪器
     */
    public fun reset() {
        sessionStartTime = 0
        accumulatedTime = 0
        isActive = false
    }
}
