package com.archko.reader.pdf.entity

import kotlinx.serialization.Serializable

/**
 * @author: archko 2025/11/4 :09:26
 */
/**
 * Reflow缓存数据类
 */
@Serializable
public data class ReflowCacheBean(
    val pageCount: Int,
    val fileSize: Long,
    val reflow: List<ReflowBean>
) {
    override fun toString(): String {
        return "ReflowCacheBean(pageCount=$pageCount, fileSize=$fileSize, textsSize=${reflow.size})"
    }
}