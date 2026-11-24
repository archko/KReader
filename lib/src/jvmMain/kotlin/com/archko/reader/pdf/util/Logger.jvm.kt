package com.archko.reader.pdf.util

/**
 * Desktop platform implementation of DebugConfig.
 * On desktop, we check for IDE environment or use a system property.
 * You can also set an environment variable DEBUG_MODE=true to enable logging.
 */
public actual object DebugConfig {
    public actual val isDebugMode: Boolean
        get() {
            // Check system properties first
            val sysProp = System.getProperty("debug.mode", "").lowercase()
            if (sysProp.isNotEmpty()) {
                return sysProp == "true"
            }

            // Check environment variables
            val envVar = System.getenv("DEBUG_MODE")?.lowercase()
            if (envVar != null) {
                return envVar == "true"
            }

            // Check if running in IDE (IntelliJ IDEA)
            val ideaVersion = System.getProperty("idea.version")
            if (ideaVersion != null) {
                return true  // Enable logging when running in IDE
            }

            // Default: disable logging in release desktop builds
            return false
        }
}
