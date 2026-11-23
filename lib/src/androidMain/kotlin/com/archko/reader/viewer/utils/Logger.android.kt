package com.archko.reader.viewer.utils

import android.content.pm.ApplicationInfo
import com.archko.reader.pdf.PdfApp

/**
 * Android platform implementation of DebugConfig.
 * Uses ApplicationInfo.FLAG_DEBUGGABLE which is set by Gradle based on build type.
 */
public actual object DebugConfig {
    public actual val isDebugMode: Boolean
        get() {
            val flags = PdfApp.app?.applicationInfo?.flags ?: return false
            return (flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        }
}
