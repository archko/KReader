package com.archko.reader.pdf.cache

import coil3.key.Keyer
import coil3.request.Options
import com.archko.reader.pdf.entity.CustomImageData

/**
 * @author: archko 2025/11/16 :06:36
 * Keyer for CustomImageData to enable Coil memory caching
 */
public class CustomImageDataKeyer : Keyer<CustomImageData> {
    override fun key(data: CustomImageData, options: Options): String {
        // 生成唯一的缓存键，包含路径和尺寸信息
        return "${data.path}_${data.width}x${data.height}"
    }
}