package com.archko.reader.pdf.cache

import android.content.Context
import androidx.room.Room
//import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/**
 * @author: archko 2025/2/14 :16:54
 */
public actual class DriverFactory(
    private val app: Context,
) {
    public actual fun createRoomDatabase(): AppDatabase {
        val dbFile = app.getDatabasePath(DB_FILE_NAME)
        return Room
            .databaseBuilder<AppDatabase>(
                context = app,
                name = dbFile.absolutePath,
            )
            //.setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration(dropAllTables = false)
            .build()
    }
}
