package com.archko.reader.pdf.cache

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.archko.reader.pdf.entity.AICache
import com.archko.reader.pdf.entity.AIConversation
import com.archko.reader.pdf.entity.AIPageConversation
import com.archko.reader.pdf.entity.AIProvider
import com.archko.reader.pdf.entity.Bookmark
import com.archko.reader.pdf.entity.ReadingStats
import com.archko.reader.pdf.entity.Recent

@Database(
    entities = [
        Recent::class,
        Bookmark::class,
        ReadingStats::class,
        AIProvider::class,
        AICache::class,
        AIConversation::class,
        AIPageConversation::class,
    ],
    version = 5,
    exportSchema = false
)
public abstract class AppDatabase : RoomDatabase() {
    public abstract fun recentDao(): RecentDao
    public abstract fun bookmarkDao(): BookmarkDao
    public abstract fun readingStatsDao(): ReadingStatsDao
    public abstract fun aiProviderDao(): AIProviderDao
    public abstract fun aiCacheDao(): AICacheDao
    public abstract fun aiConversationDao(): AIConversationDao
    public abstract fun aiPageConversationDao(): AIPageConversationDao
}

// The Room compiler generates the `actual` implementations.
@Suppress("NO_ACTUAL_FOR_EXPECT")
public expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

internal const val DB_FILE_NAME = "book.db"
