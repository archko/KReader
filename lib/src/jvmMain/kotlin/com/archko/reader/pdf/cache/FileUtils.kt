package com.archko.reader.pdf.cache

import java.io.File

/**
 *
 * @author: archko 2025/11/2
 */
public class FileUtils {

    public companion object {

        public fun getImageCacheDirectory(): File {
            val cacheDir = File(getCacheDirectory(), "image")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            return cacheDir
        }

        public fun getCacheDirectory(dir: String): File {
            val cacheDir = File(getCacheDirectory(), dir)
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            return cacheDir
        }

        public fun getCacheDirectory(): File {
            val osName = System.getProperty("os.name").lowercase()
            val isWindows = osName.contains("win")
            val userHome = System.getProperty("user.home")
            val cacheDirPath = if (isWindows) {
                val appData = System.getenv("APPDATA") ?: "$userHome\\AppData\\Roaming"
                "$appData\\KReader\\"
            } else if (osName.contains("mac")) {
                // macOS
                "$userHome/Library/Application Support/KReader/"
            } else {
                // 其他系统：使用用户主目录下的隐藏应用目录
                val userHome = System.getProperty("user.home")
                "$userHome/.kreader/cache"
            }
            val cacheDir = File(cacheDirPath)
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            return cacheDir
        }
    }
}