package com.archko.reader.image

/**
 * TIFF文件信息类
 */
public class TiffInfo {
    public var width: Int = 0
    public var height: Int = 0
    public var samplesPerPixel: Int = 0
    public var bitsPerSample: Int = 0
    public var photometricInterpretation: Int = 0
    public var compression: Int = 0
    public var orientation: Int = 0
    public var tileWidth: Int = 0
    public var tileHeight: Int = 0
    public var isTiled: Boolean = false
    public var isStripped: Boolean = false
    public var pages: Int = 0

    public constructor()

    public constructor(
        width: Int, height: Int, samplesPerPixel: Int, bitsPerSample: Int,
        photometricInterpretation: Int, compression: Int, orientation: Int,
        tileWidth: Int, tileHeight: Int, isTiled: Boolean, isStripped: Boolean, pages: Int
    ) {
        this.width = width
        this.height = height
        this.samplesPerPixel = samplesPerPixel
        this.bitsPerSample = bitsPerSample
        this.photometricInterpretation = photometricInterpretation
        this.compression = compression
        this.orientation = orientation
        this.tileWidth = tileWidth
        this.tileHeight = tileHeight
        this.isTiled = isTiled
        this.isStripped = isStripped
        this.pages = pages
    }

    override fun toString(): String {
        return "TiffInfo{" +
                "width=" + width +
                ", height=" + height +
                ", samplesPerPixel=" + samplesPerPixel +
                ", bitsPerSample=" + bitsPerSample +
                ", photometricInterpretation=" + photometricInterpretation +
                ", compression=" + compression +
                ", orientation=" + orientation +
                ", tileWidth=" + tileWidth +
                ", tileHeight=" + tileHeight +
                ", isTiled=" + isTiled +
                ", isStripped=" + isStripped +
                ", pages=" + pages +
                '}'
    }
}