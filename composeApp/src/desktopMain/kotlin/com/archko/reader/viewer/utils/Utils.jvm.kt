package com.archko.reader.viewer.utils

import java.io.File

actual class Utils {
    actual companion object {
        private val settingsDir = File(System.getProperty("user.home"), ".kreader/settings")
        
        init {
            settingsDir.mkdirs()
        }
        
        actual fun saveString(key: String, value: String) {
            val file = File(settingsDir, "${key}.txt")
            file.writeText(value)
        }
        
        actual fun getString(key: String, defaultValue: String): String {
            val file = File(settingsDir, "${key}.txt")
            return if (file.exists()) {
                try {
                    file.readText()
                } catch (e: Exception) {
                    defaultValue
                }
            } else {
                defaultValue
            }
        }
    }
}