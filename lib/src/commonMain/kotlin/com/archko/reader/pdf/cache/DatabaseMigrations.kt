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
        connection.execSQL(
            """
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
        """.trimIndent()
        )

        // Create index for bookmark table
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_bookmark_path ON bookmark(path)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_bookmark_path_page ON bookmark(path, pageIndex)")

        // Create reading_stats table
        connection.execSQL(
            """
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
        """.trimIndent()
        )

        // 不需要为path创建索引，因为它已经是PRIMARY KEY
    }
}

public val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        // Create ai_provider table
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ai_provider (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                api_key TEXT NOT NULL,
                base_url TEXT NOT NULL,
                model TEXT NOT NULL,
                max_tokens INTEGER NOT NULL DEFAULT 2000,
                temperature REAL NOT NULL DEFAULT 0.7,
                enabled INTEGER NOT NULL DEFAULT 1,
                is_default INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
        """.trimIndent()
        )

        // Create ai_cache table
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ai_cache (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                document_path TEXT NOT NULL,
                feature_type TEXT NOT NULL,
                input_hash TEXT NOT NULL,
                input_text TEXT NOT NULL,
                output_text TEXT NOT NULL,
                provider_id TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
        """.trimIndent()
        )

        // Create indexes for ai_cache
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_ai_cache_document_path ON ai_cache(document_path)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_ai_cache_input_hash ON ai_cache(input_hash)")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_ai_cache_document_path_feature_type_input_hash ON ai_cache(document_path, feature_type, input_hash)")

        // Create ai_conversation table
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ai_conversation (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                document_path TEXT NOT NULL,
                session_id TEXT NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                context_type TEXT,
                created_at INTEGER NOT NULL
            )
        """.trimIndent()
        )

        // Create indexes for ai_conversation
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_ai_conversation_session_id ON ai_conversation(session_id)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_ai_conversation_document_path ON ai_conversation(document_path)")
    }
}


public val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        // Create ai_page_conversation table
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ai_page_conversation (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                document_path TEXT NOT NULL,
                document_name TEXT NOT NULL,
                page_index INTEGER NOT NULL,
                question TEXT NOT NULL,
                answer TEXT NOT NULL,
                page_content TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
        """.trimIndent()
        )

        // Create indexes for ai_page_conversation
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_ai_page_conversation_document_path_page_index ON ai_page_conversation(document_path, page_index)")
    }
}
