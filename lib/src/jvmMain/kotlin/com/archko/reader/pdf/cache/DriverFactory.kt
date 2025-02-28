package com.archko.reader.pdf.cache

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.sql.DriverManager

/**
 * @author: archko 2025/2/14 :16:54
 */
public class DriverFactory : DatabaseDriverFactory {
    override fun createDriver(): SqlDriver {
        val cacheDirectory = getAppCacheDirectory()
        val databaseFile = File(cacheDirectory, "progress.db")
        val dbFilePath = databaseFile.absolutePath
        val dbUrl = "jdbc:sqlite:$dbFilePath"
        val driver = JdbcSqliteDriver(dbUrl)

        // 检查数据库中是否已经有表，如果没有则创建表结构
        if (!databaseFile.exists()) {
            AppDatabase.Schema.create(driver)
        }

        // val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        // AppDatabase.Schema.create(driver)

        return driver
    }

    private fun isDatabaseSchemaCreated(url: String): Boolean {
        val connection = DriverManager.getConnection(url)
        return try {
            val statement = connection.createStatement()
            val resultSet =
                statement.executeQuery("SELECT name FROM Progress WHERE type='table' AND name='Progress'")
            resultSet.next()
        } catch (e: Exception) {
            false
        } finally {
            connection.close()
        }
    }

    public fun getAppCacheDirectory(): File {
        val os = System.getProperty("os.name").lowercase()
        val appName = "com.archko.reader.viewer"

        return when {
            os.contains("win") -> {
                val localAppData = System.getenv("LOCALAPPDATA")
                File("$localAppData/$appName/Cache")
            }

            os.contains("mac") -> {
                val userHome = System.getProperty("user.home")
                File("$userHome/Library/Caches/$appName")
            }

            os.contains("nix") || os.contains("nux") -> {
                val userHome = System.getProperty("user.home")
                File("$userHome/.cache/$appName")
            }

            else -> throw UnsupportedOperationException("Unsupported operating system: $os")
        }.also {
            if (!it.exists()) {
                it.mkdirs()
            }
        }
    }
}