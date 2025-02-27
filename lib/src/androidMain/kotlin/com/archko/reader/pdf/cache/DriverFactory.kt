package com.archko.reader.pdf.cache

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

/**
 * @author: archko 2025/2/14 :16:54
 */
public class DriverFactory(private val context: Context) : DatabaseDriverFactory {
    override fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(AppDatabase.Schema, context, "progress.db")
    }
}