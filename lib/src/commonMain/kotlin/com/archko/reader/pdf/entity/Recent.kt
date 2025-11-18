package com.archko.reader.pdf.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

/**
 * @author: archko 2024/2/14 :14:32
 */
@Entity(
    tableName = "recent",
)
public class Recent {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    public var id: Long = 0

    @ColumnInfo(name = "path")
    public var path: String? = null

    @ColumnInfo(name = "updateAt")
    public var updateAt: Long? = 0

    @ColumnInfo(name = "page")
    public var page: Long? = 0

    @ColumnInfo(name = "pageCount")
    public var pageCount: Long? = 0

    @ColumnInfo(name = "createAt")
    public var createAt: Long? = 0

    @ColumnInfo(name = "crop")
    //0:autocrop,1:no crop, 2:manunal crop
    public var crop: Long? = 1

    @ColumnInfo(name = "reflow")
    //0,no reflow mode,1,reflow mode
    public var reflow: Long? = 0

    @ColumnInfo(name = "scrollOri")
    //0水平,1垂直
    public var scrollOri: Long? = 1

    @ColumnInfo(name = "zoom")
    public var zoom: Double? = 1.0

    @ColumnInfo(name = "scrollX")
    public var scrollX: Long? = 0

    @ColumnInfo(name = "scrollY")
    public var scrollY: Long? = 0

    @ColumnInfo(name = "name")
    public var name: String? = null

    @ColumnInfo(name = "ext")
    public var ext: String? = null

    @ColumnInfo(name = "size")
    public var size: Long? = 0

    @ColumnInfo(name = "readTimes")
    public var readTimes: Long? = 0

    @ColumnInfo(name = "progress")
    public var progress: Long? = 0

    @ColumnInfo(name = "isFavorited")
    public var favorited: Long? = 0

    @ColumnInfo(name = "inRecent")
    public var inRecent: Long? = 0

    public constructor()

    @Ignore
    public constructor(path: String?) {
        this.path = path
        createAt = System.currentTimeMillis()
        updateAt = System.currentTimeMillis()
    }

    @Ignore
    public constructor(
        //id: Long,
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
        scrollY: Long?,
        name: String?,
        ext: String?,
        size: Long?,
        readTimes: Long?,
        progress: Long?,
        isFavorited: Long?,
        inRecent: Long?
    ) {
        //this.id = id
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
        this.name = name
        this.ext = ext
        this.size = size
        this.readTimes = readTimes
        this.progress = progress
        this.favorited = isFavorited
        this.inRecent = inRecent
    }

    override fun toString(): String {
        return "Recent{" +
                "updateAt=" + updateAt +
                ", page=" + page +
                ", pageCount=" + pageCount +
                ", createAt=" + createAt +
                ", crop=" + crop +
                ", reflow=" + reflow +
                ", scrollOri=" + scrollOri +
                ", zoom=" + zoom +
                ", scrollX=" + scrollX +
                ", scrollY=" + scrollY +
                ", uri='" + path + '\'' +
                ", name='" + name + '\'' +
                ", ext='" + ext + '\'' +
                ", size=" + size +
                ", readTimes=" + readTimes +
                ", progress=" + progress +
                ", isFavorited=" + favorited +
                ", inRecent=" + inRecent +
                '}'
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Recent) return false

        return path == other.path &&
                page == other.page &&
                pageCount == other.pageCount &&
                updateAt == other.updateAt &&
                createAt == other.createAt &&
                crop == other.crop &&
                reflow == other.reflow &&
                scrollOri == other.scrollOri &&
                zoom == other.zoom &&
                scrollX == other.scrollX &&
                scrollY == other.scrollY &&
                name == other.name &&
                ext == other.ext &&
                size == other.size &&
                readTimes == other.readTimes &&
                progress == other.progress &&
                favorited == other.favorited &&
                inRecent == other.inRecent
    }

    override fun hashCode(): Int {
        var result = path?.hashCode() ?: 0
        result = 31 * result + (page?.hashCode() ?: 0)
        result = 31 * result + (pageCount?.hashCode() ?: 0)
        result = 31 * result + (updateAt?.hashCode() ?: 0)
        result = 31 * result + (createAt?.hashCode() ?: 0)
        result = 31 * result + (crop?.hashCode() ?: 0)
        result = 31 * result + (reflow?.hashCode() ?: 0)
        result = 31 * result + (scrollOri?.hashCode() ?: 0)
        result = 31 * result + (zoom?.hashCode() ?: 0)
        result = 31 * result + (scrollX?.hashCode() ?: 0)
        result = 31 * result + (scrollY?.hashCode() ?: 0)
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + (ext?.hashCode() ?: 0)
        result = 31 * result + (size?.hashCode() ?: 0)
        result = 31 * result + (readTimes?.hashCode() ?: 0)
        result = 31 * result + (progress?.hashCode() ?: 0)
        result = 31 * result + (favorited?.hashCode() ?: 0)
        result = 31 * result + (inRecent?.hashCode() ?: 0)
        return result
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
            scrollY: Long,
            name: String? = null,
            ext: String? = null,
            size: Long? = null,
            readTimes: Long? = null,
            progress: Long? = null,
            isFavorited: Long? = null,
            inRecent: Long? = null
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
            recent.name = name
            recent.ext = ext
            recent.size = size
            recent.readTimes = readTimes
            recent.progress = progress
            recent.favorited = isFavorited
            recent.inRecent = inRecent
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