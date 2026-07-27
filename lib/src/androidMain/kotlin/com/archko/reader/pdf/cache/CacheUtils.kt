package com.archko.reader.pdf.cache

import com.archko.reader.pdf.PdfApp
import com.archko.reader.pdf.util.FileUtils
import java.io.File

/**
 * @author: archko 2020/11/1 :9:04 下午
 */
public actual fun getStoragePath(): String {
    return FileUtils.getStoragePath("")
}

public actual fun getPageCacheFile(path: String): File {
    val name = path.substringAfterLast("/").substringBeforeLast(".")
    val saveFile = File(
        FileUtils.getStorageDirPath() + "/amupdf"
                + File.separator + "page" + File.separator + name + ".json"
    )
    return saveFile
}

public actual fun getCacheDirectory(name: String): File {
    val file = File(
        FileUtils.getStorageDirPath() + "/amupdf" + File.separator + name
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
    val cacheDir = FileUtils.getStorageDir("amupdf/tts")
    val fileName = "${file.nameWithoutExtension}_tts.json"
    return File(cacheDir, fileName)
}

public actual fun getWebdavCacheDir(): File {
    val dir = FileUtils.getCacheDir(PdfApp.app!!, "webdav")
    if (!dir.exists()) {
        dir.mkdirs()
    }
    return dir
}

public actual fun saveWebdavCacheFile(name: String, content: String) {
    val file = File(getWebdavCacheDir(), name)
    println("name:$name, content:$content")
    file.writeText(content)
}