package com.archko.reader.pdf.cache

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Database migrations for AppDatabase
 * 
 * @author: archko 2025/11/18
 */
public val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        // Add new columns to recent table
        connection.execSQL("ALTER TABLE recent ADD COLUMN name TEXT")
        connection.execSQL("ALTER TABLE recent ADD COLUMN ext TEXT")
        connection.execSQL("ALTER TABLE recent ADD COLUMN size INTEGER DEFAULT 0")
        connection.execSQL("ALTER TABLE recent ADD COLUMN readTimes INTEGER DEFAULT 0")
        connection.execSQL("ALTER TABLE recent ADD COLUMN progress INTEGER DEFAULT 0")
        connection.execSQL("ALTER TABLE recent ADD COLUMN isFavorited INTEGER DEFAULT 0")
        connection.execSQL("ALTER TABLE recent ADD COLUMN inRecent INTEGER DEFAULT 0")
    }
}

public val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        // Create bookmark table
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS bookmark (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                path TEXT NOT NULL,
                pageIndex INTEGER NOT NULL,
                title TEXT,
                note TEXT,
                color INTEGER,
                scrollY INTEGER,
                createAt INTEGER NOT NULL,
                updateAt INTEGER NOT NULL
            )
        """.trimIndent())
        
        // Create index for bookmark table
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_bookmark_path ON bookmark(path)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_bookmark_path_page ON bookmark(path, pageIndex)")
        
        // Create reading_stats table
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS reading_stats (
                path TEXT PRIMARY KEY NOT NULL,
                totalReadingTime INTEGER NOT NULL,
                lastSessionTime INTEGER NOT NULL,
                averageSessionTime INTEGER NOT NULL,
                firstReadAt INTEGER NOT NULL,
                lastReadAt INTEGER NOT NULL,
                completedPages INTEGER NOT NULL,
                totalPages INTEGER NOT NULL,
                sessionCount INTEGER NOT NULL,
                lastSessionDate TEXT NOT NULL,
                consecutiveDays INTEGER NOT NULL,
                annotationCount INTEGER NOT NULL,
                bookmarkCount INTEGER NOT NULL
            )
        """.trimIndent())
        
        // 不需要为path创建索引，因为它已经是PRIMARY KEY
    }
}
