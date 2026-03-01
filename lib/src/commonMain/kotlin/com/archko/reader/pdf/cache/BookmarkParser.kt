package com.archko.reader.pdf.cache

import com.archko.reader.pdf.entity.Bookmark
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
 * Parser for Bookmark backup and restore
 * Uses filename-only identifiers for cross-device compatibility
 * Groups bookmarks by filename
 *
 * @author: archko 2026/3/1
 */
public object BookmarkParser {
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
     * Convert single Bookmark to JsonObject
     */
    private fun addBookmarkToJson(bookmark: Bookmark): JsonObject {
        return buildJsonObject {
            put("pageIndex", bookmark.pageIndex)
            put("title", bookmark.title)
            put("note", bookmark.note)
            put("createAt", bookmark.createAt)
            put("updateAt", bookmark.updateAt)
            put("color", bookmark.color)
            put("scrollY", bookmark.scrollY)
        }
    }

    /**
     * Convert list of Bookmarks to JSON string
     * Groups bookmarks by filename
     */
    public fun bookmarksToJson(bookmarks: List<Bookmark>): String {
        // Group bookmarks by filename
        val groupedByFile = bookmarks.groupBy { extractFilename(it.path) }

        val bookmarksArray = buildJsonArray {
            groupedByFile.forEach { (filename, fileBookmarks) ->
                val fileObject = buildJsonObject {
                    put("filename", filename)
                    put("items", buildJsonArray {
                        fileBookmarks.forEach { bookmark ->
                            add(addBookmarkToJson(bookmark))
                        }
                    })
                }
                add(fileObject)
            }
        }

        val jsonObject = buildJsonObject {
            put("version", "1.0")
            put("lastBackupTime", System.currentTimeMillis())
            put("bookmarks", bookmarksArray)
        }

        return json.encodeToString(JsonObject.serializer(), jsonObject)
    }

    /**
     * Parse single JsonObject to Bookmark
     * Returns null if parsing fails
     */
    private fun parseBookmarkItem(jsonObject: JsonObject?, filename: String): Bookmark? {
        if (jsonObject == null) {
            return null
        }

        return try {
            val bookmark = Bookmark()
            // Store filename as path - will be matched to local path during restore
            bookmark.path = filename
            bookmark.pageIndex = jsonObject["pageIndex"]?.jsonPrimitive?.intOrNull ?: 0
            bookmark.title = jsonObject["title"]?.jsonPrimitive?.contentOrNull
            bookmark.note = jsonObject["note"]?.jsonPrimitive?.contentOrNull
            bookmark.createAt = jsonObject["createAt"]?.jsonPrimitive?.longOrNull ?: 0
            bookmark.updateAt = jsonObject["updateAt"]?.jsonPrimitive?.longOrNull ?: 0
            bookmark.color = jsonObject["color"]?.jsonPrimitive?.longOrNull
            bookmark.scrollY = jsonObject["scrollY"]?.jsonPrimitive?.longOrNull
            bookmark
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Parse JSON string to list of Bookmarks
     * Matches by filename only
     */
    public fun parseBookmarks(jsonString: String): List<Bookmark> {
        val bookmarkList = mutableListOf<Bookmark>()

        try {
            val jsonElement = json.parseToJsonElement(jsonString)
            val bookmarksArray = jsonElement.jsonObject["bookmarks"]?.jsonArray

            bookmarksArray?.forEach { fileElement ->
                val fileObject = fileElement.jsonObject
                val filename = fileObject["filename"]?.jsonPrimitive?.contentOrNull ?: ""
                val items = fileObject["items"]?.jsonArray

                items?.forEach { itemElement ->
                    val bookmark = parseBookmarkItem(itemElement.jsonObject, filename)
                    if (bookmark != null) {
                        println("Parsed Bookmark: $bookmark")
                        bookmarkList.add(bookmark)
                    }
                }
            }
        } catch (e: Exception) {
            println("Error parsing Bookmarks JSON: ${e.message}")
            e.printStackTrace()
        }

        return bookmarkList
    }
}
