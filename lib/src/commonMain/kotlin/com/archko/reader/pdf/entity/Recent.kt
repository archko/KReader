package com.archko.reader.pdf.entity

/**
 * @author: archko 2024/2/14 :14:32
 */
public class Recent {
    public var id: Long? = 0
    public var path: String? = null
    public var updateAt: Long? = 0
    public var page: Long? = 0
    public var pageCount: Long? = 0
    public var createAt: Long? = 0

    //0:autocrop,1:no crop, 2:manunal crop
    public var crop: Long? = 0

    //0,no reflow mode,1,reflow mode
    public var reflow: Long? = 0

    //0水平,1垂直
    public var scrollOri: Long? = 1
    public var zoom: Double? = 1.0
    public var scrollX: Long? = 0
    public var scrollY: Long? = 0

    public constructor()

    public constructor(path: String?) {
        this.path = path
        createAt = System.currentTimeMillis()
        updateAt = System.currentTimeMillis()
    }

    public constructor(
        id: Long,
        path: String,
        page: Long?,
        pageCount: Long?,
        createAt: Long?,
        updateAt: Long?,
        crop: Long?,
        reflow: Long?,
        scrollOri: Long?,
        zoom: Double?,
        scrollX: Long?,
        scrollY: Long?
    ) {
        this.id = id
        this.updateAt = updateAt
        this.page = page
        this.pageCount = pageCount
        this.createAt = createAt
        this.path = path
        this.crop = crop
        this.reflow = reflow
        this.scrollOri = scrollOri
        this.zoom = zoom
        this.scrollX = scrollX
        this.scrollY = scrollY
    }

    override fun toString(): String {
        return "Recent{" +
                "updateAt=" + updateAt +
                ", page=" + page +
                ", pageCount=" + pageCount +
                ", createAt=" + createAt +
                ", crop=" + crop +
                ", scrollOri=" + scrollOri +
                ", zoom=" + zoom +
                ", scrollX=" + scrollX +
                ", scrollY=" + scrollY +
                ", uri='" + path + '\'' +
                '}'
    }

    public companion object {
        public fun encode(
            path: String?,
            page: Long,
            pageCount: Long,
            crop: Long,
            scrollOri: Long,
            reflow: Long,
            zoom: Double,
            scrollX: Long,
            scrollY: Long
        ): Recent {
            val recent = Recent()
            recent.updateAt = System.currentTimeMillis()
            recent.page = page
            recent.pageCount = pageCount
            recent.crop = crop
            recent.scrollOri = scrollOri
            recent.reflow = reflow
            recent.createAt = System.currentTimeMillis()
            recent.path = path
            recent.zoom = zoom
            recent.scrollX = scrollX
            recent.scrollY = scrollY
            return recent
        }

        /*public fun decode(jsonObject: JSONObject): Recent {
            val recent = Recent()
            recent.updateAt = jsonObject.optLong("updateAt")
            recent.page = jsonObject.optInt("page")
            recent.pageCount = jsonObject.optInt("pageCount")
            recent.createAt = jsonObject.optLong("createAt")
            recent.path = jsonObject.optString("uri")
            recent.crop = jsonObject.optInt("crop", 0)
            recent.scrollOri = jsonObject.optInt("scrollOri", 1)
            recent.reflow = jsonObject.optInt("reflow", 0)
            recent.zoom = jsonObject.optDouble("zoom", 1.0).toFloat()
            recent.scrollX = jsonObject.optInt("scrollX", 0)
            recent.scrollY = jsonObject.optInt("scrollY", 0)
            return recent
        }

        public fun encode(recent: Recent): JSONObject {
            val jsonObject = JSONObject()
            try {
                jsonObject.put("updateAt", recent.updateAt)
                jsonObject.put("page", recent.page)
                jsonObject.put("pageCount", recent.pageCount)
                jsonObject.put("createAt", recent.createAt)
                jsonObject.put("uri", recent.path)
                jsonObject.put("crop", recent.crop)
                jsonObject.put("scrollOri", recent.scrollOri)
                jsonObject.put("reflow", recent.reflow)
                jsonObject.put("zoom", recent.zoom.toDouble())
                jsonObject.put("scrollX", recent.scrollX)
                jsonObject.put("scrollY", recent.scrollY)
            } catch (e: JSONException) {
            }
            return jsonObject
        }*/
    }
}