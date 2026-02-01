package com.archko.reader.viewer.utils

import com.tencent.mmkv.MMKV

actual class Utils {
    actual companion object {
        private val mmkv: MMKV by lazy {
            MMKV.mmkvWithID("ai_settings")
        }
        
        actual fun saveString(key: String, value: String) {
            mmkv.encode(key, value)
        }
        
        actual fun getString(key: String, defaultValue: String): String {
            return mmkv.decodeString(key, defaultValue) ?: defaultValue
        }
    }
}