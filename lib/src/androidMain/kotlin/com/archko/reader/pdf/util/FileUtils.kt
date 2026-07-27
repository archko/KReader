package com.archko.reader.pdf.util

import android.content.Context
import android.content.res.AssetManager
import android.os.Environment
import android.text.TextUtils
import com.archko.reader.pdf.PdfApp
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.text.SimpleDateFormat
import java.util.Locale

public class FileUtils private constructor() {

    public companion object {

        public fun getRealPath(absolutePath: String): String {
            val sdcard = Environment.getExternalStorageDirectory().getPath()
            var filepath = absolutePath
            if (absolutePath.contains(sdcard)) {
                filepath = absolutePath.substring(sdcard.length)
            }
            return filepath
        }

        public fun getStoragePath(path: String?): String {
            if (TextUtils.isEmpty(path)) {
                return Environment.getExternalStorageDirectory().path
            }
            return Environment.getExternalStorageDirectory().path + "/" + (path)
        }

        public fun getStorageDir(dir: String?): File {
            val sdcardRoot: String = getStorageDirPath()
            val file = File("$sdcardRoot/$dir")
            if (!file.exists()) {
                file.mkdirs()
            }
            return file
        }

        public fun getStorageDirPath(): String {
            if (Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()) {
                val externalFileRootDir: File? = PdfApp.app!!.getExternalFilesDir(null)
                if (externalFileRootDir != null) {
                    return externalFileRootDir.absolutePath
                }
            }
            return PdfApp.app!!.filesDir.absolutePath
        }

        public fun getDir(file: File?): String {
            if (file == null) {
                return ""
            }
            val name = file.getName()
            return file.absolutePath.substring(0, file.absolutePath.length - name.length)
        }

        public fun getDir(absPath: String?): String {
            if (absPath == null) {
                return ""
            }
            val index = absPath.lastIndexOf("/")
            if (index == -1) {
                return ""
            }
            return absPath.substring(0, index + 1)
        }

        public fun getCacheDir(context: Context, uniqueName: String): File {
            val cachePath = context.cacheDir.path
            return File(cachePath, uniqueName)
        }

        public fun getFileSize(size: Long): String {
            if (size > 1073741824) {
                return String.format(Locale.getDefault(), "%.2f", size / 1073741824.0) + " GB"
            } else if (size > 1048576) {
                return String.format(Locale.getDefault(), "%.2f", size / 1048576.0) + " MB"
            } else if (size > 1024) {
                return String.format(Locale.getDefault(), "%.2f", size / 1024.0) + " KB"
            } else {
                return "$size B"
            }
        }

        public fun getFileDate(time: Long): String? {
            return SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(time)
        }

        public fun getName(absPath: String?): String {
            if (absPath == null) {
                return ""
            }
            val index = absPath.lastIndexOf("/")
            if (index == -1) {
                return ""
            }
            return absPath.substring(index + 1)
        }

        public fun getNameWithoutExt(absPath: String?): String {
            if (absPath == null) {
                return ""
            }
            val index = absPath.lastIndexOf("/")
            if (index == -1) {
                return ""
            }
            val end = absPath.lastIndexOf(".")
            if (end == -1) {
                return ""
            }
            return absPath.substring(index + 1, end)
        }

        public fun getExtension(file: File?): String {
            if (file == null) {
                return ""
            }
            val name = file.getName()
            val index = name.lastIndexOf(".")
            if (index == -1) {
                return ""
            }
            return name.substring(index + 1)
        }

        public fun getExtension(name: String?): String? {
            if (TextUtils.isEmpty(name)) {
                return name
            }
            val index = name!!.lastIndexOf(".")
            if (index == -1) {
                return ""
            }
            return name.substring(index + 1)
        }

        public fun move(sourceDir: File?, targetDir: File?, fileNames: Array<String>): Int {
            var count = 0
            var processed = 0

            var renamed = true

            val buf = ByteArray(128 * 1024)
            var length: Int
            for (file in fileNames) {
                val source = File(sourceDir, file)
                val target = File(targetDir, file)
                processed++

                renamed = renamed && source.renameTo(target)
                if (renamed) {
                    count++
                    continue
                }

                try {
                    var ins: InputStream? = null
                    var outs: OutputStream? = null
                    try {
                        ins = FileInputStream(source)
                        outs = FileOutputStream(target)
                        length = ins.read(buf)
                        while (length > -1) {
                            outs.write(buf, 0, length)
                            length = ins.read(buf)
                        }
                    } finally {
                        if (outs != null) {
                            try {
                                outs.close()
                            } catch (_: IOException) {
                            }
                        }
                        if (ins != null) {
                            try {
                                ins.close()
                            } catch (_: IOException) {
                            }
                        }
                    }
                    source.delete()
                    count++
                } catch (ex: IOException) {
                    System.err.println(ex.message)
                }
            }
            return count
        }

        public fun readAssetAsString(assetName: String): String? {
            try {
                val assetManager: AssetManager = PdfApp.app!!.getAssets()
                val `is`: InputStream = assetManager.open(assetName)
                return StreamUtils.readStringFromInputStream(`is`)
            } catch (_: IOException) {
                return null
            }
        }

        public fun getExternalCacheDir(context: Context): File {
            val mCacheDir = context.externalCacheDir
            if (mCacheDir != null) {
                return mCacheDir
            }

            val dir = "/Android/data/" + context.packageName + "/cache/"
            return File(Environment.getExternalStorageDirectory().path + dir)
        }

        public fun cleanDir(dir: File) {
            if (dir.exists()) {
                dir.delete()
            }

            dir.mkdirs()
        }

        public fun deleteDir(dir: File) {
            if (dir.exists()) {
                val files = dir.listFiles()
                for (file in files!!) {
                    if (file.isDirectory()) {
                        deleteDir(file)
                    } else {
                        deleteFile(file)
                    }
                }
            }
        }

        public fun deleteFile(file: File) {
            if (file.exists()) {
                if (file.isDirectory()) {
                    deleteDir(file)
                } else {
                    file.delete()
                }
            }
        }

        public fun deleteFile(path: String?) {
            if (TextUtils.isEmpty(path)) {
                return
            }
            val file = File(path!!)
            if (file.exists()) {
                if (file.isDirectory()) {
                    deleteDir(file)
                } else {
                    file.delete()
                }
            }
        }

        //---------------------------
        public fun MD5(data: String): String? {
            try {
                val md = MessageDigest.getInstance("MD5")
                val bytes = md.digest(data.toByteArray())
                return bytesToHexString(bytes)
            } catch (_: NoSuchAlgorithmException) {
            }
            return data
        }

        private fun bytesToHexString(src: ByteArray?): String? {
            val stringBuilder = StringBuilder("")
            if (src == null || src.isEmpty()) {
                return null
            }
            for (i in src.indices) {
                val v = src[i].toInt() and 0xFF
                val hv = Integer.toHexString(v)
                if (hv.length < 2) {
                    stringBuilder.append(0)
                }
                stringBuilder.append(hv)
            }
            return stringBuilder.toString()
        }
    }
}

public actual fun isSupportedImageFile(path: String): Boolean {
    return path.lowercase().let { filePath ->
        filePath.endsWith(".jpg") || filePath.endsWith(".jpeg")
                || filePath.endsWith(".png") || filePath.endsWith(".gif")
                || filePath.endsWith(".bmp") || filePath.endsWith(".webp")
                || filePath.endsWith(".heif") || filePath.endsWith(".heic")
                //raw images
                || filePath.endsWith(".dng") || filePath.endsWith(".arw")
                || filePath.endsWith(".nef") || filePath.endsWith(".cr2")
                || filePath.endsWith(".cr3") || filePath.endsWith(".arw")
                || filePath.endsWith(".raf") || filePath.endsWith(".orf")
                || filePath.endsWith(".sr2") //|| filePath.endsWith(".srf")
                || filePath.endsWith(".srw") || filePath.endsWith(".x3f")
                || filePath.endsWith(".pef") || filePath.endsWith(".3fr")
                || filePath.endsWith(".rw2") || filePath.endsWith(".nrw")
                || filePath.endsWith(".crw")
    }
}