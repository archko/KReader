package com.archko.reader.pdf.cache

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.archko.reader.pdf.entity.Bookmark
import com.archko.reader.pdf.entity.ReadingStats
import com.archko.reader.pdf.entity.Recent

@Database(
    entities = [
        Recent::class,
        Bookmark::class,
        ReadingStats::class,
    ],
    version = 3,
    exportSchema = false
)
public abstract class AppDatabase : RoomDatabase() {
    public abstract fun recentDao(): RecentDao
    public abstract fun bookmarkDao(): BookmarkDao
    public abstract fun readingStatsDao(): ReadingStatsDao
}

// The Room compiler generates the `actual` implementations.
@Suppress("NO_ACTUAL_FOR_EXPECT")
public expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

internal const val DB_FILE_NAME = "book.db"
