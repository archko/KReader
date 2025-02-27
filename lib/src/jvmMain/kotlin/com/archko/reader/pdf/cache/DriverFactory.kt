package com.archko.reader.pdf.cache

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

/**
 * @author: archko 2025/2/14 :16:54
 */
public class DriverFactory : DatabaseDriverFactory {
    override fun createDriver(): SqlDriver {
        val databaseFile = File("progress.db")
        val driver = JdbcSqliteDriver("jdbc:sqlite:progress.db")
        if (!databaseFile.exists()) {
            // 数据库文件不存在时，创建表，否则会出现未创建或者重复创建表的错误
            AppDatabase.Schema.create(driver)
        }

        // val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        // AppDatabase.Schema.create(driver)

        return driver
    }
}