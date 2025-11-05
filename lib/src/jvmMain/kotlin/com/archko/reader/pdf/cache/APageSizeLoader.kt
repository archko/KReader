package com.archko.reader.pdf.cache

import androidx.compose.ui.geometry.Rect
import com.archko.reader.pdf.entity.APage
import kotlinx.serialization.json.*
import java.io.File

/**
 * @author: archko 2020/1/1 :9:04 下午
 */
public object APageSizeLoader {

    public const val PAGE_COUNT: Int = 20

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private fun getCacheFile(file: File): File {
        val saveFile = File(
            FileUtils.getCacheDirectory("page").absolutePath
                    + File.separator
                    + file.nameWithoutExtension + ".json"
        )
        return saveFile
    }

    public fun loadPageSizeFromFile(
        pageCount: Int,
        file: File
    ): PageSizeBean? {
        var pageSizeBean: PageSizeBean? = null
        try {
            val size = file.length()
            val saveFile = getCacheFile(file)
            if(!saveFile.exists()){
                return null
            }
            val content = saveFile.readText(Charsets.UTF_8)
            if (!content.isEmpty()) {
                pageSizeBean = fromJson(pageCount, size, json.decodeFromString(content))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return pageSizeBean
    }

    public fun loadPageSizeFromFile(
        pageCount: Int,
        path: String
    ): PageSizeBean? {
        var pageSizeBean: PageSizeBean? = null
        try {
            val file = File(path)
            val size = file.length()
            val saveFile = getCacheFile(file)
            if(!saveFile.exists()){
                return null
            }
            val content = saveFile.readText(Charsets.UTF_8)
            if (!content.isEmpty()) {
                pageSizeBean = fromJson(pageCount, size, json.decodeFromString(content))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return pageSizeBean
    }

    public fun savePageSizeToFile(
        crop: Boolean,
        file: File,
        list: MutableList<APage>?,
    ) {
        list?.run {
            val saveFile = getCacheFile(file)
            val content = toJson(crop, file.length(), list)
            saveFile.writeText(content, Charsets.UTF_8)
        }
    }

    public fun savePageSizeToFile(
        crop: Boolean,
        path: String,
        list: MutableList<APage>?,
    ) {
        list?.run {
            val file = File(path)
            val saveFile = getCacheFile(file)
            val content = toJson(crop, file.length(), list)
            saveFile.writeText(content, Charsets.UTF_8)
        }
    }

    private fun fromJson(pageCount: Int, fileSize: Long, jo: JsonObject): PageSizeBean? {
        val ja = jo["pagesize"]?.jsonArray
        if (null == ja || ja.size != pageCount) {
            //Log.d("TAG", "new pagecount:$pageCount")
            return null
        }
        if (fileSize != jo["filesize"]?.jsonPrimitive?.longOrNull) {
            //Log.d("TAG", "new filesize:$fileSize")
            return null
        }
        val pageSizeBean = PageSizeBean()
        val list = fromJson(ja)
        pageSizeBean.list = list
        pageSizeBean.crop = jo["crop"]?.jsonPrimitive?.booleanOrNull ?: false
        return pageSizeBean
    }

    private fun fromJson(ja: JsonArray): MutableList<APage> {
        val list = mutableListOf<APage>()
        for (i in 0 until ja.size) {
            ja[i].jsonObject.let { pageObj ->
                list.add(fromJson(pageObj))
            }
        }
        return list
    }

    private fun toJson(crop: Boolean, fileSize: Long, list: List<APage>): String {
        return try {
            buildJsonObject {
                put("crop", crop)
                put("filesize", fileSize)
                put("pagesize", toJsons(list))
            }.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            "{}"
        }
    }

    private fun toJsons(list: List<APage>): JsonArray {
        return buildJsonArray {
            for (aPage in list) {
                add(toJson(aPage))
            }
        }
    }

    public fun deletePageSizeFromFile(path: String?) {
        path?.run {
            val file = File(path)
            val saveFile = getCacheFile(file)
            if (saveFile.exists()) {
                saveFile.delete()
            }
        }
    }

    public class PageSizeBean {
        public var list: MutableList<APage>? = null
        public var crop: Boolean = false
        public var fileSize: Long = 0L

        override fun toString(): String {
            return "PageSizeBean(crop=$crop, fileSize=$fileSize, list=${list?.size})"
        }
    }

    //=============

    public fun toJson(aPage: APage): JsonObject {
        return buildJsonObject {
            put("index", aPage.index)
            put("width", aPage.width.toDouble())
            put("height", aPage.height.toDouble())
            put("zoom", aPage.scale.toDouble())
            aPage.cropBounds?.let { bounds ->
                put("cbleft", bounds.left)
                put("cbtop", bounds.top)
                put("cbright", bounds.right)
                put("cbbottom", bounds.bottom)
            }
        }
    }

    public fun fromJson(jo: JsonObject): APage {
        val index = jo["index"]?.jsonPrimitive?.intOrNull ?: 0
        val width = jo["width"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val height = jo["height"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val scale = jo["zoom"]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: 1.0f
        val cbleft = jo["cbleft"]?.jsonPrimitive?.floatOrNull ?: 0f
        val cbtop = jo["cbtop"]?.jsonPrimitive?.floatOrNull ?: 0f
        val cbright = jo["cbright"]?.jsonPrimitive?.floatOrNull ?: 0f
        val cbbottom = jo["cbbottom"]?.jsonPrimitive?.floatOrNull ?: 0f
        var cropBounds: Rect? = null
        if (cbright > 0 && cbbottom > 0) {
            cropBounds = Rect(cbleft, cbtop, cbright, cbbottom)
        }
        val aPage = APage(index, width.toInt(), height.toInt(), scale, cropBounds)
        return aPage
    }
}