package com.archko.reader.image

/**
 * 解码的区域数据类
 */
public class DecodedRegion {
    public var data: ByteArray? = null
    public var width: Int = 0
    public var height: Int = 0
    public var bytesPerPixel: Int = 0
    public var originalWidth: Int = 0
    public var originalHeight: Int = 0
    public var scale: Float = 0f

    public constructor()

    public constructor(
        data: ByteArray?, width: Int, height: Int, bytesPerPixel: Int,
        originalWidth: Int, originalHeight: Int, scale: Float
    ) {
        this.data = data
        this.width = width
        this.height = height
        this.bytesPerPixel = bytesPerPixel
        this.originalWidth = originalWidth
        this.originalHeight = originalHeight
        this.scale = scale
    }

    public val dataSize: Int
        /**
         * 获取数据大小
         */
        get() {
            return if (data != null) data!!.size else 0
        }

    /**
     * 检查数据是否有效
     */
    public fun isValid(): Boolean {
        return data != null && data!!.size > 0 && width > 0 && height > 0
    }

    override fun toString(): String {
        return "DecodedRegion{" +
                "dataSize=" + this.dataSize +
                ", width=" + width +
                ", height=" + height +
                ", bytesPerPixel=" + bytesPerPixel +
                ", originalWidth=" + originalWidth +
                ", originalHeight=" + originalHeight +
                ", scale=" + scale +
                '}'
    }
}