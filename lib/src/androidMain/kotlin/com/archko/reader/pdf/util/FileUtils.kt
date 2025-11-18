package com.archko.reader.pdf.util

import android.content.Context
import android.content.res.AssetManager
import android.os.Environment
import android.text.TextUtils
import com.archko.reader.pdf.PdfApp
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.text.SimpleDateFormat
import java.util.Objects
import kotlin.math.max
import kotlin.math.min

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

    public companion object {
        private val mounts = ArrayList<String?>()
        private val mountsPR = ArrayList<String>()
        private val aliases = ArrayList<String?>()
        private val aliasesPR = ArrayList<String>()

        init {
            val files = Environment.getRootDirectory().listFiles()
            if (null != files) {
                for (f in files) {
                    if (f.isDirectory()) {
                        try {
                            val cp = f.getCanonicalPath()
                            val ap = f.getAbsolutePath()
                            if (cp != ap) {
                                aliases.add(ap)
                                aliasesPR.add(ap + "/")
                                mounts.add(cp)
                                mountsPR.add("/")
                            }
                        } catch (ex: IOException) {
                            System.err.println(ex.message)
                        }
                    }
                }
            }
        }

        public fun getRealPath(absolutePath: String): String {
            val sdcard = Environment.getExternalStorageDirectory().getPath()
            var filepath = absolutePath
            if (absolutePath.contains(sdcard)) {
                filepath = absolutePath.substring(sdcard.length)
            }
            return filepath
        }

        public fun getStoragePath(path: String?): String {
            return Environment.getExternalStorageDirectory().getPath() + "/" + (path)
        }

        public fun getStorageDir(dir: String?): File {
            //String path = Environment.getExternalStorageDirectory().getPath() + "/" + dir;
            val sdcardRoot: String = getStorageDirPath()
            val file = File(sdcardRoot + "/" + dir)
            if (!file.exists()) {
                file.mkdirs()
            }
            return file
        }

        public fun getStorageDirPath(): String {
            var externalFileRootDir: File? = PdfApp.Companion.app!!.getExternalFilesDir(null)
            do {
                externalFileRootDir =
                    Objects.requireNonNull<File?>(externalFileRootDir).getParentFile()
            } while (Objects.requireNonNull<File?>(externalFileRootDir).getAbsolutePath()
                    .contains("/Android")
            )
            var sdcardRoot: String? = null
            if (null != externalFileRootDir) {
                sdcardRoot = externalFileRootDir.getPath()
            }
            return sdcardRoot!!
        }

        public fun getDir(file: File?): String {
            if (file == null) {
                return ""
            }
            val name = file.getName()
            return file.getAbsolutePath().substring(0, file.getAbsolutePath().length - name.length)
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
                return String.format("%.2f", size / 1073741824.0) + " GB"
            } else if (size > 1048576) {
                return String.format("%.2f", size / 1048576.0) + " MB"
            } else if (size > 1024) {
                return String.format("%.2f", size / 1024.0) + " KB"
            } else {
                return size.toString() + " B"
            }
        }

        public fun getFileDate(time: Long): String? {
            return SimpleDateFormat("dd MMM yyyy").format(time)
        }

        public fun getAbsolutePath(file: File?): String? {
            return if (file != null) file.getAbsolutePath() else null
        }

        public fun getCanonicalPath(file: File?): String? {
            try {
                return if (file != null) file.getCanonicalPath() else null
            } catch (ex: IOException) {
                return null
            }
        }

        public fun invertMountPrefix(fileName: String): String? {
            run {
                var i = 0
                val n: Int = min(Companion.aliases.size, Companion.mounts.size)
                while (i < n) {
                    val alias: String? = Companion.aliases.get(i)
                    val mount: String? = Companion.mounts.get(i)
                    if (fileName == alias) {
                        return mount
                    }
                    if (fileName == mount) {
                        return alias
                    }
                    i++
                }
            }
            var i = 0
            val n: Int = min(aliasesPR.size, mountsPR.size)
            while (i < n) {
                val alias: String = aliasesPR.get(i)
                val mount: String = mountsPR.get(i)
                if (fileName.startsWith(alias)) {
                    return mount + fileName.substring(alias.length)
                }
                if (fileName.startsWith(mount)) {
                    return alias + fileName.substring(mount.length)
                }
                i++
            }
            return null
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
            val updates = max(1, fileNames.size / 20)

            var renamed = true

            val buf = ByteArray(128 * 1024)
            var length = 0
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
                            } catch (ignored: IOException) {
                            }
                        }
                        if (ins != null) {
                            try {
                                ins.close()
                            } catch (ignored: IOException) {
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
                val assetManager: AssetManager = PdfApp.Companion.app!!.getAssets()
                val `is`: InputStream = assetManager.open(assetName)
                return StreamUtils.readStringFromInputStream(`is`)
            } catch (_: IOException) {
                return null
            }
        }

        public fun getDiskCacheDir(context: Context, uniqueName: String?): File {
            val cachePath = if (Environment.MEDIA_MOUNTED == Environment
                    .getExternalStorageState()
            ) {
                getExternalCacheDir(context).path
            } else {
                context.cacheDir.path
            }

            return File(cachePath + File.separator + uniqueName)
        }

        /**
         * Get the external app cache directory
         *
         * @param context The [Context] to use
         * @return The external cache directory
         */
        public fun getExternalCacheDir(context: Context): File {
            val mCacheDir = context.getExternalCacheDir()
            if (mCacheDir != null) {
                return mCacheDir
            }

            /* Before Froyo we need to construct the external cache dir ourselves */
            val dir = "/Android/data/" + context.getPackageName() + "/cache/"
            return File(Environment.getExternalStorageDirectory().getPath() + dir)
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
            val file = File(path)
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
            } catch (e: NoSuchAlgorithmException) {
            }
            return data
        }

        private fun bytesToHexString(src: ByteArray?): String? {
            val stringBuilder = StringBuilder("")
            if (src == null || src.size <= 0) {
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
