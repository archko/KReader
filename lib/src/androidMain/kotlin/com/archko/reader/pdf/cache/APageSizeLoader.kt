package com.archko.reader.pdf.cache

import android.text.TextUtils
import android.util.Log
import androidx.compose.ui.geometry.Rect
import com.archko.reader.pdf.entity.APage
import com.archko.reader.pdf.util.FileUtils
import com.archko.reader.pdf.util.StreamUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File

/**
 * @author: archko 2020/1/1 :9:04 下午
 */
public object APageSizeLoader {

    public const val PAGE_COUNT: Int = 20

    public fun loadPageSizeFromFile(
        pageCount: Int,
        file: File
    ): PageSizeBean? {
        var pageSizeBean: PageSizeBean? = null
        try {
            val size = file.length()
            val saveFile = File(
                //FileUtils.getExternalCacheDir(App.instance).path
                FileUtils.getStorageDirPath() + "/amupdf"
                        + File.separator + "page" + File.separator + file.nameWithoutExtension + ".json"
            )
            val content = StreamUtils.readStringFromFile(saveFile)
            if (!TextUtils.isEmpty(content)) {
                pageSizeBean = fromJson(pageCount, size, JSONObject(content))
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
            val saveFile = File(
                //FileUtils.getExternalCacheDir(App.instance).path
                FileUtils.getStorageDirPath() + "/amupdf"
                        + File.separator + "page" + File.separator + file.nameWithoutExtension + ".json"
            )
            val content = StreamUtils.readStringFromFile(saveFile)
            if (!TextUtils.isEmpty(content)) {
                pageSizeBean = fromJson(pageCount, size, JSONObject(content))
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
            val saveFile = File(
                //FileUtils.getExternalCacheDir(App.instance).path
                FileUtils.getStorageDirPath() + "/amupdf"
                        + File.separator + "page" + File.separator + file.nameWithoutExtension + ".json"
            )
            val content = toJson(crop, file.length(), list)
            StreamUtils.saveStringToFile(content, saveFile)
        }
    }

    public fun savePageSizeToFile(
        crop: Boolean,
        path: String,
        list: MutableList<APage>?,
    ) {
        list?.run {
            val file = File(path)
            val saveFile = File(
                //FileUtils.getExternalCacheDir(App.instance).path
                FileUtils.getStorageDirPath() + "/amupdf"
                        + File.separator + "page" + File.separator + file.nameWithoutExtension + ".json"
            )
            val content = toJson(crop, file.length(), list)
            StreamUtils.saveStringToFile(content, saveFile)
        }
    }

    private fun fromJson(pageCount: Int, fileSize: Long, jo: JSONObject): PageSizeBean? {
        val ja = jo.optJSONArray("pagesize")
        if (null == ja || ja.length() != pageCount) {
            Log.d("TAG", "new pagecount:$pageCount")
            return null
        }
        if (fileSize != jo.optLong("filesize")) {
            Log.d("TAG", "new filesize:$fileSize")
            return null
        }
        val pageSizeBean = PageSizeBean()
        val list = fromJson(ja)
        pageSizeBean.list = list
        pageSizeBean.crop = jo.optBoolean("crop")
        return pageSizeBean
    }

    private fun fromJson(ja: JSONArray): MutableList<APage> {
        val list = mutableListOf<APage>()
        for (i in 0 until ja.length()) {
            list.add(fromJson(ja.optJSONObject(i)))
        }
        return list
    }

    private fun toJson(crop: Boolean, fileSize: Long, list: List<APage>): String {
        val jo = JSONObject()
        try {
            jo.put("crop", crop)
            jo.put("filesize", fileSize)
            jo.put("pagesize", toJsons(list))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return jo.toString()
    }

    private fun toJsons(list: List<APage>): JSONArray {
        val jsonArray = JSONArray()
        for (aPage in list) {
            jsonArray.put(toJson(aPage))
        }
        return jsonArray
    }

    public fun deletePageSizeFromFile(path: String?) {
        path?.run {
        val file = File(path)
        val saveFile = File(
            FileUtils.getStorageDirPath() + "/amupdf"
                    + File.separator + "page" + File.separator + file.nameWithoutExtension + ".json"
        )
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

    public fun toJson(aPage: APage): JSONObject {
        val jo = JSONObject()
        try {
            jo.put("index", aPage.index)
            jo.put("width", aPage.width.toDouble())
            jo.put("height", aPage.height.toDouble())
            jo.put("zoom", aPage.scale.toDouble())
            if (aPage.cropBounds != null) {
                jo.put("cbleft", aPage.cropBounds!!.left)
                jo.put("cbtop", aPage.cropBounds!!.top)
                jo.put("cbright", aPage.cropBounds!!.right)
                jo.put("cbbottom", aPage.cropBounds!!.bottom)
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return jo
    }

    public fun fromJson(jo: JSONObject): APage {
        val index = jo.optInt("index")
        val width = jo.optDouble("width")
        val height = jo.optDouble("height")
        val scale = jo.optDouble("zoom").toFloat()
        val cbleft = jo.optInt("cbleft")
        val cbtop = jo.optInt("cbtop")
        val cbright = jo.optInt("cbright")
        val cbbottom = jo.optInt("cbbottom")
        var cropBounds: Rect? = null
        if (cbright > 0 && cbbottom > 0) {
            cropBounds =
                Rect(cbleft.toFloat(), cbtop.toFloat(), cbright.toFloat(), cbbottom.toFloat())
        }
        val aPage = APage(index, width.toInt(), height.toInt(), scale, cropBounds)
        return aPage
    }
}