package com.archko.reader.pdf.decoder

import android.graphics.Bitmap

/**
 * @author: archko 2019/11/30 :3:18 PM
 */
public class BitmapBean @JvmOverloads constructor(
    public var bitmap: Bitmap,
    public var index: Int,
    public var width: Float = bitmap.width.toFloat(),
    public var height: Float = bitmap.height.toFloat()
) {
    public constructor(bitmap: Bitmap, width: Float, height: Float) :
            this(bitmap, 0, width, height)

    override fun toString(): String {
        return "BitmapBean{" +
                "index:" + index +
                ",bitmap:" + bitmap +
                '}'
    }
}