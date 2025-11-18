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
