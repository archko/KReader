package com.archko.reader.viewer.utils

expect class Utils {
    companion object {
        fun saveString(key: String, value: String)
        fun getString(key: String, defaultValue: String = ""): String
    }
}