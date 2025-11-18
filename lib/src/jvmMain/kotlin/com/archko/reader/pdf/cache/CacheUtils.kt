package com.archko.reader.pdf.cache

import com.archko.reader.pdf.cache.FileUtils.Companion.getCacheDirectory
import java.io.File

/**
 * @author: archko 2025/11/1 :9:04 下午
 */
public actual fun getPageCacheFile(file: File): File {
    val saveFile = File(
        getCacheDirectory("page").absolutePath
                + File.separator
                + file.nameWithoutExtension + ".json"
    )
    return saveFile
}

/**
 * 获取缓存文件路径
 * @param file 原始PDF文件
 * @return 缓存文件
 */
public actual fun getReflowCacheFile(file: File): File {
    val cacheDir = getCacheDirectory("reflow")
    val fileName = "${file.nameWithoutExtension}_reflow.json"
    return File(cacheDir, fileName)
}

public actual fun getWebdavCacheDir(): File {
    return getCacheDirectory("webdav")
}

public actual fun saveWebdavCacheFile(name: String, content: String) {}