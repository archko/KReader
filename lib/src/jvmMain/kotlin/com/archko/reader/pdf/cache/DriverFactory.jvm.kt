package com.archko.reader.pdf.cache

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * @author: archko 2025/2/14 :16:54
 */
public actual class DriverFactory {

    public actual fun createRoomDatabase(): AppDatabase {
        return getDatabaseBuilder()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }

    public fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
        val dbFile = File(System.getProperty("java.io.tmpdir"), DB_FILE_NAME)
        return Room.databaseBuilder<AppDatabase>(
            name = dbFile.absolutePath,
        )
    }

    public fun getAppCacheDirectory(): File {
        val os = System.getProperty("os.name").lowercase()
        val appName = "com.archko.reader.viewer"

        return when {
            os.contains("win") -> {
                val localAppData = System.getenv("LOCALAPPDATA")
                File("$localAppData/$appName/Cache")
            }

            os.contains("mac") -> {
                val userHome = System.getProperty("user.home")
                File("$userHome/Library/Caches/$appName")
            }

            os.contains("nix") || os.contains("nux") -> {
                val userHome = System.getProperty("user.home")
                File("$userHome/.cache/$appName")
            }

            else -> throw UnsupportedOperationException("Unsupported operating system: $os")
        }.also {
            if (!it.exists()) {
                it.mkdirs()
            }
        }
    }
}