package com.archko.reader.pdf.util

import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.math.BigInteger
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

public class FileUtils private constructor() {
    /**
     * 文件MD5值
     *
     * @param filepath
     */
    public fun md5File(filepath: String): String? {
        try {
            val file = File(filepath)
            val fis = FileInputStream(file)
            val md = MessageDigest.getInstance("MD5")
            val buffer = ByteArray(1024)
            var length = -1
            while (fis.read(buffer, 0, 1024) != -1) {
                length = fis.read(buffer, 0, 1024)
                md.update(buffer, 0, length)
            }
            val bigInt = BigInteger(1, md.digest())
            return bigInt.toString(16)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return null
    }
}

public actual fun isSupportedImageFile(path: String): Boolean {
    return path.lowercase().let { filePath ->
        filePath.endsWith(".jpg") || filePath.endsWith(".jpeg")
                || filePath.endsWith(".png") || filePath.endsWith(".gif")
                || filePath.endsWith(".bmp") || filePath.endsWith(".webp")
                || filePath.endsWith(".heif") || filePath.endsWith(".heic")
                //raw images
                //|| filePath.endsWith(".dng") || filePath.endsWith(".arw")
                //|| filePath.endsWith(".nef") || filePath.endsWith(".cr2")
                //|| filePath.endsWith(".cr3") || filePath.endsWith(".arw")
                //|| filePath.endsWith(".raf") || filePath.endsWith(".orf")
                //|| filePath.endsWith(".sr2") || filePath.endsWith(".srf")
                //|| filePath.endsWith(".srw") || filePath.endsWith(".x3f")
                //|| filePath.endsWith(".pef") || filePath.endsWith(".3fr")
                //|| filePath.endsWith(".rw2") || filePath.endsWith(".nrw")
                //|| filePath.endsWith(".crw")
    }
}