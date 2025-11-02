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
        val dbFile = File(FileUtils.getCacheDirectory(), DB_FILE_NAME)
        return Room.databaseBuilder<AppDatabase>(
            name = dbFile.absolutePath,
        )
    }
}