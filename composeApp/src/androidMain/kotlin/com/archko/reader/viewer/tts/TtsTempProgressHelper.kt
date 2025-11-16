package com.archko.reader.viewer.tts

import android.content.Context
import android.content.Context.MODE_PRIVATE
import androidx.core.content.edit
import org.json.JSONObject

/**
 * TTS临时进度辅助类
 * 用于在Activity被销毁但Service还在运行时保存和恢复朗读进度
 */
object TtsTempProgressHelper {

    private const val PREFS_NAME = "tts_temp_progress"
    private const val MAX_AGE_MILLIS = 24 * 60 * 60 * 1000L // 1小时

    data class TempProgress(
        val path: String,
        val page: Int,
        val timestamp: Long
    )

    /**
     * 保存临时进度到 SharedPreferences
     */
    fun saveTempProgress(page: String?, documentPath: String?, context: Context) {
        if (documentPath == null || page == null) {
            return
        }

        try {
            val pageInt = page.toIntOrNull() ?: return
            val prefs = context.getSharedPreferences("tts_temp_progress", MODE_PRIVATE)
            val timestamp = System.currentTimeMillis()

            // 使用文档路径的 hash 作为 key
            val key = "progress_${documentPath.hashCode()}"
            val json = """{"path":"$documentPath","page":$pageInt,"timestamp":$timestamp}"""

            prefs.edit { putString(key, json) }
            println("TTS: 保存临时进度: path=$documentPath, page=$pageInt")
        } catch (e: Exception) {
            println("TTS: 保存临时进度失败: ${e.message}")
        }
    }


    /**
     * 获取所有临时进度
     */
    fun getAllTempProgress(context: Context): List<TempProgress> {
        val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val allEntries = prefs.all
        val result = mutableListOf<TempProgress>()
        val currentTime = System.currentTimeMillis()

        for ((key, value) in allEntries) {
            if (key.startsWith("progress_") && value is String) {
                try {
                    val json = JSONObject(value)
                    val path = json.getString("path")
                    val page = json.getInt("page")
                    val timestamp = json.getLong("timestamp")

                    // 只返回最近1小时内的进度
                    if (currentTime - timestamp <= MAX_AGE_MILLIS) {
                        result.add(TempProgress(path, page, timestamp))
                    } else {
                        // 清除过期的进度
                        prefs.edit { remove(key) }
                        println("TtsTempProgress: 清除过期进度: $path")
                    }
                } catch (e: Exception) {
                    println("TtsTempProgress: 解析进度失败: ${e.message}")
                }
            }
        }

        return result
    }

    /**
     * 获取指定文档的临时进度
     */
    fun getTempProgress(context: Context, documentPath: String): TempProgress? {
        val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val key = "progress_${documentPath.hashCode()}"
        val json = prefs.getString(key, null) ?: return null

        return try {
            val jsonObj = JSONObject(json)
            val path = jsonObj.getString("path")
            val page = jsonObj.getInt("page")
            val timestamp = jsonObj.getLong("timestamp")

            val currentTime = System.currentTimeMillis()
            if (currentTime - timestamp <= MAX_AGE_MILLIS) {
                TempProgress(path, page, timestamp)
            } else {
                // 清除过期的进度
                prefs.edit { remove(key) }
                println("TtsTempProgress: 清除过期进度: $path")
                null
            }
        } catch (e: Exception) {
            println("TtsTempProgress: 解析进度失败: ${e.message}")
            null
        }
    }

    /**
     * 清除指定文档的临时进度
     */
    fun clearTempProgress(context: Context, documentPath: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val key = "progress_${documentPath.hashCode()}"
        prefs.edit { remove(key) }
        println("TtsTempProgress: 清除临时进度: $documentPath")
    }

    /**
     * 清除所有临时进度
     */
    fun clearAllTempProgress(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit { clear() }
        println("TtsTempProgress: 清除所有临时进度")
    }
}
