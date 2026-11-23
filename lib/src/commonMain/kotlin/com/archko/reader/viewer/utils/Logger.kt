package com.archko.reader.viewer.utils

/**
 * Expect declaration for platform-specific logging.
 * Each platform (Android, Desktop) will provide its own implementation.
 */
public expect object DebugConfig {
    public val isDebugMode: Boolean
}

/**
 * Unified logging interface for the entire project.
 * Logs are automatically disabled in release builds across all platforms.
 */
public object Logger {
    public fun d(tag: String, message: String, throwable: Throwable? = null) {
        if (DebugConfig.isDebugMode) {
            println("🔵 [$tag] $message")
            throwable?.printStackTrace()
        }
    }

    public fun i(tag: String, message: String) {
        if (DebugConfig.isDebugMode) {
            println("ℹ️ [$tag] $message")
        }
    }

    public fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (DebugConfig.isDebugMode) {
            println("⚠️ [$tag] $message")
            throwable?.printStackTrace()
        }
    }

    public fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (DebugConfig.isDebugMode) {
            println("❌ [$tag] $message")
            throwable?.printStackTrace()
        }
    }
}
