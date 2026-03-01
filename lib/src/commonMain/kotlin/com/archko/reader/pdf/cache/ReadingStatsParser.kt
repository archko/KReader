package com.archko.reader.pdf.cache

import com.archko.reader.pdf.entity.ReadingStats
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File

/**
 * Parser for ReadingStats backup and restore
 * Uses filename-only identifiers for cross-device compatibility
 *
 * @author: archko 2026/3/1
 */
public object ReadingStatsParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Extract filename from full path
     */
    private fun extractFilename(path: String): String {
        return File(path).name
    }

    /**
     * Convert single ReadingStats to JsonObject
     */
    private fun addStatsToJson(stats: ReadingStats): JsonObject {
        return buildJsonObject {
            put("filename", extractFilename(stats.path))
            put("totalReadingTime", stats.totalReadingTime)
            put("lastSessionTime", stats.lastSessionTime)
            put("averageSessionTime", stats.averageSessionTime)
            put("firstReadAt", stats.firstReadAt)
            put("lastReadAt", stats.lastReadAt)
            put("completedPages", stats.completedPages)
            put("totalPages", stats.totalPages)
            put("sessionCount", stats.sessionCount)
            put("lastSessionDate", stats.lastSessionDate)
            put("consecutiveDays", stats.consecutiveDays)
            put("annotationCount", stats.annotationCount)
            put("bookmarkCount", stats.bookmarkCount)
        }
    }

    /**
     * Convert list of ReadingStats to JSON string
     * Uses filename-only identifiers
     */
    public fun statsToJson(stats: List<ReadingStats>): String {
        val statsArray = buildJsonArray {
            stats.forEach { stat ->
                add(addStatsToJson(stat))
            }
        }

        val jsonObject = buildJsonObject {
            put("version", "1.0")
            put("lastBackupTime", System.currentTimeMillis())
            put("stats", statsArray)
        }

        return json.encodeToString(JsonObject.serializer(), jsonObject)
    }

    /**
     * Parse single JsonObject to ReadingStats
     * Returns null if parsing fails
     */
    private fun parseStatsItem(jsonObject: JsonObject?): ReadingStats? {
        if (jsonObject == null) {
            return null
        }

        return try {
            val stats = ReadingStats()
            // Store filename as path - will be matched to local path during restore
            stats.path = jsonObject["filename"]?.jsonPrimitive?.contentOrNull ?: ""
            stats.totalReadingTime = jsonObject["totalReadingTime"]?.jsonPrimitive?.longOrNull ?: 0
            stats.lastSessionTime = jsonObject["lastSessionTime"]?.jsonPrimitive?.longOrNull ?: 0
            stats.averageSessionTime = jsonObject["averageSessionTime"]?.jsonPrimitive?.longOrNull ?: 0
            stats.firstReadAt = jsonObject["firstReadAt"]?.jsonPrimitive?.longOrNull ?: 0
            stats.lastReadAt = jsonObject["lastReadAt"]?.jsonPrimitive?.longOrNull ?: 0
            stats.completedPages = jsonObject["completedPages"]?.jsonPrimitive?.intOrNull ?: 0
            stats.totalPages = jsonObject["totalPages"]?.jsonPrimitive?.intOrNull ?: 0
            stats.sessionCount = jsonObject["sessionCount"]?.jsonPrimitive?.intOrNull ?: 0
            stats.lastSessionDate = jsonObject["lastSessionDate"]?.jsonPrimitive?.contentOrNull ?: ""
            stats.consecutiveDays = jsonObject["consecutiveDays"]?.jsonPrimitive?.intOrNull ?: 0
            stats.annotationCount = jsonObject["annotationCount"]?.jsonPrimitive?.intOrNull ?: 0
            stats.bookmarkCount = jsonObject["bookmarkCount"]?.jsonPrimitive?.intOrNull ?: 0
            stats
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Parse JSON string to list of ReadingStats
     * Matches by filename only
     */
    public fun parseStats(jsonString: String): List<ReadingStats> {
        val statsList = mutableListOf<ReadingStats>()

        try {
            val jsonElement = json.parseToJsonElement(jsonString)
            val statsArray = jsonElement.jsonObject["stats"]?.jsonArray

            statsArray?.forEach { element ->
                val stats = parseStatsItem(element.jsonObject)
                if (stats != null) {
                    println("Parsed ReadingStats: $stats")
                    statsList.add(stats)
                }
            }
        } catch (e: Exception) {
            println("Error parsing ReadingStats JSON: ${e.message}")
            e.printStackTrace()
        }

        return statsList
    }
}
