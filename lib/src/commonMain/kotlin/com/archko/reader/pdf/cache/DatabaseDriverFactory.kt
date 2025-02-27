package com.archko.reader.pdf.cache

import app.cash.sqldelight.db.SqlDriver

/**
 * @author: archko 2025/2/14 :16:46
 */
public interface DatabaseDriverFactory {
    public fun createDriver(): SqlDriver
}