package com.archko.reader.pdf.cache

import com.archko.reader.pdf.util.FileUtils
import java.io.File

/**
 * @author: archko 2020/11/1 :9:04 下午
 */
public actual fun getCacheFile(file: File): File {
    val saveFile = File(
        //FileUtils.getExternalCacheDir(App.instance).path
        FileUtils.getStorageDirPath() + "/amupdf"
                + File.separator + "page" + File.separator + file.nameWithoutExtension + ".json"
    )
    return saveFile
}