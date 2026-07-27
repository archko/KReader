package com.archko.reader.pdf.cache

import com.archko.reader.pdf.cache.FileUtils.Companion.getCacheDirectory
import java.io.File

/**
 * @author: archko 2025/11/1 :9:04 下午
 */
public actual fun getStoragePath(): String {
    val userHome = System.getProperty("user.home")
    return userHome
}

public actual fun getPageCacheFile(path: String): File {
    val name = path.substringAfterLast("/").substringBeforeLast(".")
    val saveFile = File(
        getCacheDirectory("page").absolutePath
                + File.separator
                + name + ".json"
    )
    return saveFile
}

public actual fun getCacheDirectory(name: String): File {
    val file = File(
        getCacheDirectory().absolutePath
                + File.separator + name
    )
    if(!file.exists()) {
        file.mkdirs()
    }
    return file
}

/**
 * 获取缓存文件路径
 * @param file 原始PDF文件
 * @return 缓存文件
 */
public actual fun getReflowCacheFile(file: File): File {
    val cacheDir = getCacheDirectory("tts")
    val fileName = "${file.nameWithoutExtension}_tts.json"
    return File(cacheDir, fileName)
}

public actual fun getWebdavCacheDir(): File {
    return getCacheDirectory("webdav")
}

public actual fun saveWebdavCacheFile(name: String, content: String) {}