package com.archko.reader.pdf.cache

import com.archko.reader.pdf.util.FileUtils
import java.io.File

/**
 * @author: archko 2020/11/1 :9:04 下午
 */
public actual fun getPageCacheFile(file: File): File {
    val saveFile = File(
        //FileUtils.getExternalCacheDir(App.instance).path
        FileUtils.getStorageDirPath() + "/amupdf"
                + File.separator + "page" + File.separator + file.nameWithoutExtension + ".json"
    )
    return saveFile
}

/**
 * 获取缓存文件路径
 * @param file 原始PDF文件
 * @return 缓存文件
 */
public actual fun getReflowCacheFile(file: File): File {
    val cacheDir = FileUtils.getStorageDir("amupdf/reflow")
    val fileName = "${file.nameWithoutExtension}_reflow.json"
    return File(cacheDir, fileName)
}

public actual fun getWebdavCacheFile(): File {
    return File(FileUtils.getDir("webdav"))
}

public actual fun saveWebdavCacheFile(name: String, content: String) {
    val file = File(getWebdavCacheFile(), name)
    println("name:$name, content:$content")
    file.writeText(content)
}