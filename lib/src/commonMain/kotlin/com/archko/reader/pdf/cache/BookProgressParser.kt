package com.archko.reader.pdf.cache

import com.archko.reader.pdf.entity.Recent
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

/**
 * 存储最近阅读的记录
 *
 * @author: archko 2025/11/17 :15:05
 */
public object BookProgressParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    public fun addRecentToJson(recent: Recent, storagePath: String): JsonObject {
        return buildJsonObject {
            // Remove storage path prefix and URL encode the path
            val encodedPath = recent.path?.let { fullPath ->
                try {
                    // 如果路径包含 storagePath 前缀，去除它
                    val relativePath = if (fullPath.startsWith(storagePath)) {
                        fullPath.removePrefix(storagePath).removePrefix("/")
                    } else {
                        fullPath
                    }
                    java.net.URLEncoder.encode(relativePath, "UTF-8")
                } catch (e: Exception) {
                    fullPath // 如果编码失败，使用原始值
                }
            } ?: ""
            put("path", encodedPath)
            put("name", recent.name ?: "")
            put("ext", recent.ext ?: "")
            put("pageCount", recent.pageCount ?: 0)
            put("size", recent.size ?: 0)
            put("firstTimestampe", recent.createAt ?: 0)
            put("lastTimestampe", recent.updateAt ?: 0)
            put("readTimes", recent.readTimes ?: 0)
            put("progress", recent.progress ?: 0)
            put("page", recent.page ?: 0)
            // zoomLevel = zoom * 1000
            put("zoomLevel", ((recent.zoom ?: 1.0) * 1000).toInt())
            put("offsetX", recent.scrollX ?: 0)
            put("offsetY", recent.scrollY ?: 0)
            put("autoCrop", recent.crop ?: 1)
            put("reflow", recent.reflow ?: 0)
            put("isFavorited", recent.favorited ?: 0)
            put("inRecent", recent.inRecent ?: 0)
            put("scrollOrientation", recent.scrollOri ?: 1)
        }
    }

    public fun parseRecent(jsonObject: JsonObject?, storagePath: String): Recent? {
        if (jsonObject == null) {
            return null
        }

        return try {
            val recent = Recent()
            // Decode URL-encoded path and add storage path prefix
            val encodedPath = jsonObject["path"]?.jsonPrimitive?.contentOrNull
            recent.path = encodedPath?.let {
                try {
                    val decodedPath = java.net.URLDecoder.decode(it, "UTF-8")
                    "$storagePath/$decodedPath"
                } catch (e: Exception) {
                    it // 如果解码失败，使用原始值
                }
            }
            recent.name = jsonObject["name"]?.jsonPrimitive?.contentOrNull
            recent.ext = jsonObject["ext"]?.jsonPrimitive?.contentOrNull
            recent.pageCount = jsonObject["pageCount"]?.jsonPrimitive?.longOrNull ?: 0
            recent.size = jsonObject["size"]?.jsonPrimitive?.longOrNull ?: 0
            recent.createAt = jsonObject["firstTimestampe"]?.jsonPrimitive?.longOrNull ?: 0
            recent.updateAt = jsonObject["lastTimestampe"]?.jsonPrimitive?.longOrNull ?: 0
            recent.readTimes = jsonObject["readTimes"]?.jsonPrimitive?.longOrNull ?: 0
            recent.progress = jsonObject["progress"]?.jsonPrimitive?.longOrNull ?: 0
            recent.page = jsonObject["page"]?.jsonPrimitive?.longOrNull ?: 0

            // zoomLevel / 1000 = zoom
            val zoomLevel = jsonObject["zoomLevel"]?.jsonPrimitive?.intOrNull ?: 1000
            recent.zoom = zoomLevel / 1000.0

            recent.scrollX = jsonObject["offsetX"]?.jsonPrimitive?.longOrNull ?: 0
            recent.scrollY = jsonObject["offsetY"]?.jsonPrimitive?.longOrNull ?: 0
            recent.crop = jsonObject["autoCrop"]?.jsonPrimitive?.longOrNull ?: 1
            recent.reflow = jsonObject["reflow"]?.jsonPrimitive?.longOrNull ?: 0
            recent.favorited = jsonObject["isFavorited"]?.jsonPrimitive?.longOrNull ?: 0
            recent.inRecent = jsonObject["inRecent"]?.jsonPrimitive?.longOrNull ?: 0
            recent.scrollOri = jsonObject["scrollOrientation"]?.jsonPrimitive?.longOrNull ?: 1

            recent
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Parse JSON string to list of Recent objects
     */
    public fun parseRecents(jsonString: String): List<Recent> {
        val recentList = mutableListOf<Recent>()

        try {
            val jsonElement = json.parseToJsonElement(jsonString)
            val rootArray = jsonElement.jsonObject["root"]?.jsonArray

            // 在循环外获取一次 storagePath
            val storagePath = getStoragePath()

            rootArray?.forEach { element ->
                val recent = parseRecent(element.jsonObject, storagePath)
                if (recent != null) {
                    println("recent:$recent")
                    recentList.add(recent)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return recentList
    }

    /**
     * Convert list of Recent objects to JSON string
     */
    public fun recentsToJson(recents: List<Recent>): String {
        // 在循环外获取一次 storagePath
        val storagePath = getStoragePath()
        
        val rootArray = buildJsonArray {
            recents.forEach { recent ->
                add(addRecentToJson(recent, storagePath))
            }
        }

        val jsonObject = buildJsonObject {
            put("root", rootArray)
        }

        return json.encodeToString(JsonObject.serializer(), jsonObject)
    }
}