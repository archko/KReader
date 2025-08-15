package com.archko.reader.image

import java.awt.image.BufferedImage

public class TiffLoader {
    private var nativeHandle: Long = 0
    public var tiffInfo: TiffInfo? = null

    /**
     * 打开TIFF文件
     *
     * @param path TIFF文件路径
     * @return 是否成功打开
     */
    public fun openTiff(path: String?): Long {
        if (path == null || path.isEmpty()) {
            println("Invalid path: $path")
            return -1
        }

        // 关闭之前打开的文件
        if (nativeHandle != 0L) {
            close()
        }

        try {
            nativeHandle = nativeOpenTiff(path)
            if (nativeHandle != 0L) {
                tiffInfo = nativeGetTiffInfo(nativeHandle)
                println("Opened TIFF file: $path")
                return nativeHandle
            } else {
                println("Failed to open TIFF file: $path")
                return -1
            }
        } catch (e: Exception) {
            println("Exception opening TIFF file: $path,$e")
            return -1
        }
    }

    /**
     * 关闭TIFF文件并清理资源
     */
    public fun close() {
        if (nativeHandle != 0L) {
            try {
                nativeClose(nativeHandle)
                println("Closed TIFF file")
            } catch (e: Exception) {
                println("Exception closing TIFF file:$e")
            } finally {
                nativeHandle = 0
                tiffInfo = null
            }
        }
    }

    /**
     * 解码指定区域
     *
     * @param x      起始X坐标
     * @param y      起始Y坐标
     * @param width  区域宽度
     * @param height 区域高度
     * @param scale  缩放比例 (1.0f = 原始大小)
     * @return 解码的区域数据，失败时返回null
     */
    public fun decodeRegion(x: Int, y: Int, width: Int, height: Int, scale: Float): DecodedRegion? {
        if (nativeHandle == 0L) {
            println("No TIFF file opened")
            return null
        }

        if (width <= 0 || height <= 0 || scale <= 0) {
            println(
                "Invalid parameters: width=$width, height=$height, scale=$scale"
            )
            return null
        }

        try {
            val region: DecodedRegion? = nativeDecodeRegion(
                nativeHandle,
                x,
                y,
                width,
                height,
                scale
            )
            if (region != null && region.isValid()) {
                println("Decoded region: " + region)
                return region
            } else {
                println("Failed to decode region")
                return null
            }
        } catch (e: Exception) {
            println("Exception decoding region:$e")
            return null
        }
    }

    /**
     * 将解码的区域转换为Bitmap（兼容版本）
     *
     * @param x      起始X坐标
     * @param y      起始Y坐标
     * @param width  区域宽度
     * @param height 区域高度
     * @param scale  缩放比例
     * @return Bitmap对象，失败时返回null
     */
    public fun decodeRegionToBitmap(x: Int, y: Int, width: Int, height: Int, scale: Float): BufferedImage? {
        val region: DecodedRegion? = decodeRegion(x, y, width, height, scale)
        if (region == null || !region.isValid()) {
            return null
        }

        try {
            var bitmap: BufferedImage

            if (region.bytesPerPixel == 4) {
                // 4字节ARGB数据，直接使用
                bitmap = BufferedImage(region.width, region.height, BufferedImage.TYPE_INT_ARGB)
                val raster = bitmap.raster
                val dataBuffer = raster.dataBuffer as java.awt.image.DataBufferInt
                val pixels = dataBuffer.data
                
                val buffer = java.nio.ByteBuffer.wrap(region.data)
                buffer.asIntBuffer().get(pixels)
            } else if (region.bytesPerPixel == 3) {
                bitmap = BufferedImage(region.width, region.height, BufferedImage.TYPE_INT_ARGB)

                val pixelCount = region.width * region.height
                val argbPixels = IntArray(pixelCount)

                for (i in 0 until pixelCount) {
                    val rgbIndex = i * 3
                    val r = region.data!![rgbIndex].toInt() and 0xFF
                    val g = region.data!![rgbIndex + 1].toInt() and 0xFF
                    val b = region.data!![rgbIndex + 2].toInt() and 0xFF

                    argbPixels[i] = 0xFF shl 24 or (r shl 16) or (g shl 8) or b
                }

                bitmap.setRGB(0, 0, region.width, region.height, argbPixels, 0, region.width)
            } else if (region.bytesPerPixel == 1) {
                // 1字节灰度数据
                bitmap = BufferedImage(region.width, region.height, BufferedImage.TYPE_BYTE_GRAY)
                val raster = bitmap.raster
                val dataBuffer = raster.dataBuffer as java.awt.image.DataBufferByte
                val pixels = dataBuffer.data
                
                System.arraycopy(region.data, 0, pixels, 0, region.data!!.size)
            } else {
                println(
                    "Unsupported bytes per pixel: " + region.bytesPerPixel
                )
                return null
            }

            return bitmap
        } catch (e: Exception) {
            println("Exception creating bitmap:$e")
            return null
        }
    }

    public val lastError: String?
        /**
         * 获取最后的错误信息
         *
         * @return 错误信息字符串
         */
        get() {
            if (nativeHandle != 0L) {
                return nativeGetLastError(nativeHandle)
            }
            return "No TIFF file opened"
        }

    public val isOpened: Boolean
        /**
         * 检查是否已打开文件
         *
         * @return 是否已打开文件
         */
        get() = nativeHandle != 0L

    private val TAG = "TiffLoader"

    // 加载本地库
    init {
        try {
            System.loadLibrary("tiff_lazy")
            println("Native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            println("Failed to load native library:$e")
        }
    }

    // 本地方法声明
    private external fun nativeOpenTiff(path: String?): Long
    private external fun nativeGetTiffInfo(handle: Long): TiffInfo?
    private external fun nativeDecodeRegion(
        handle: Long,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        scale: Float
    ): DecodedRegion?

    private external fun nativeClose(handle: Long)
    private external fun nativeGetLastError(handle: Long): String?

}