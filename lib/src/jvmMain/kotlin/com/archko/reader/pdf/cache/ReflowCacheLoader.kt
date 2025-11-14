package com.archko.reader.pdf.cache

import com.archko.reader.pdf.cache.FileUtils.Companion.getCacheDirectory
import com.archko.reader.pdf.entity.ReflowBean
import com.archko.reader.pdf.entity.ReflowCacheBean
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Reflow文本缓存加载器，类似于APageSizeLoader的逻辑
 * 用于缓存PDF文档的文本内容，避免重复解析
 *
 * @author: archko 2025/11/2
 */
public object ReflowCacheLoader {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    /**
     * 从文件加载Reflow缓存
     * @param pageCount 文档总页数
     * @param file 原始PDF文件
     * @return ReflowCacheBean 如果缓存有效，否则返回null
     */
    public fun loadReflowFromFile(pageCount: Int, path: String?): ReflowCacheBean? {
        return try {
            val file = File(path)
            val fileSize = file.length()
            val cacheFile = getCacheFile(file)

            if (!cacheFile.exists()) {
                println("ReflowCache: 缓存文件不存在: ${cacheFile.absolutePath}")
                return null
            }

            val content = cacheFile.readText(Charsets.UTF_8)
            if (content.isEmpty()) {
                println("ReflowCache: 缓存文件为空")
                return null
            }

            val cacheBean = json.decodeFromString<ReflowCacheBean>(content)

            // 验证缓存有效性
            if (cacheBean.pageCount != pageCount) {
                println("ReflowCache: 页数不匹配，缓存文档总页数=${cacheBean.pageCount}, 实际页数=$pageCount")
                return null
            }

            if (cacheBean.fileSize != fileSize) {
                println("ReflowCache: 文件大小不匹配，缓存大小=${cacheBean.fileSize}, 实际大小=$fileSize")
                return null
            }

            println("ReflowCache: 成功加载缓存，页数=$pageCount")
            cacheBean
        } catch (e: Exception) {
            println("ReflowCache: 加载缓存失败: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * 保存Reflow缓存到文件
     * @param file 原始PDF文件
     * @param reflowTexts 每页的文本内容列表
     */
    public fun saveReflowToFile(
        totalPages: Int,
        path: String?,
        reflowTexts: List<ReflowBean>
    ): ReflowCacheBean? {
        try {
            val file = File(path)
            val cacheFile = getCacheFile(file)
            val cacheDir = cacheFile.parentFile

            if (cacheDir != null) {
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs()
                }
            }

            val cacheBean = ReflowCacheBean(
                pageCount = totalPages,
                fileSize = file.length(),
                reflow = reflowTexts
            )

            val content = json.encodeToString(cacheBean)
            cacheFile.writeText(content, Charsets.UTF_8)

            println("ReflowCache: 成功保存缓存，页数=${reflowTexts.size}, 文件=${cacheFile.absolutePath}")
            return cacheBean
        } catch (e: Exception) {
            println("ReflowCache: 保存缓存失败: ${e.message}")
            e.printStackTrace()
        }
        return null
    }

    /**
     * 删除指定文件的Reflow缓存
     * @param file 原始PDF文件
     */
    public fun deleteReflowCache(file: File) {
        try {
            val cacheFile = getCacheFile(file)
            if (cacheFile.exists()) {
                cacheFile.delete()
                println("ReflowCache: 删除缓存文件: ${cacheFile.absolutePath}")
            }
        } catch (e: Exception) {
            println("ReflowCache: 删除缓存失败: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 获取指定页码开始的文本内容
     * @param cacheBean 缓存数据
     * @param startPage 起始页码（从0开始）
     * @return 从指定页码开始的文本列表
     */
    public fun getTextsFromPage(cacheBean: ReflowCacheBean, startPage: Int): List<ReflowBean> {
        if (startPage < 0 || cacheBean.reflow.isEmpty()) {
            return emptyList()
        }

        // 找到第一个页码大于等于startPage的ReflowBean的索引位置
        val startIndex = cacheBean.reflow.indexOfFirst { reflowBean ->
            try {
                val pageNumber = reflowBean.page?.toIntOrNull() ?: -1
                pageNumber >= startPage
            } catch (_: Exception) {
                false
            }
        }

        // 如果没有找到匹配的页码，返回空列表
        if (startIndex == -1) {
            println("ReflowCache: 未找到页码 >= $startPage 的内容")
            return emptyList()
        }

        println("ReflowCache: 从索引 $startIndex 开始返回内容，对应页码 >= $startPage")
        return cacheBean.reflow.subList(startIndex, cacheBean.reflow.size)
    }

    /**
     * 获取缓存文件路径
     * @param file 原始PDF文件
     * @return 缓存文件
     */
    public fun getCacheFile(file: File): File {
        val cacheDir = getCacheDirectory("reflow")
        val fileName = "${file.nameWithoutExtension}_reflow.json"
        return File(cacheDir, fileName)
    }
}