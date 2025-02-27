package com.archko.reader.pdf.cache

import com.archko.reader.pdf.entity.Recent

/**
 * @author: archko 2025/2/14 :17:02
 */
internal class Database(databaseDriverFactory: DatabaseDriverFactory) {
    private val database = AppDatabase(databaseDriverFactory.createDriver())
    private val dbQuery = database.appDatabaseQueries

    internal fun getAllProgresses(): List<Recent> {
        return dbQuery.selectAllProgresses(::mapProgress).executeAsList()
    }

    private fun mapProgress(
        id: Long,
        path: String,
        page: Long?,
        pageCount: Long?,
        createAt: Long?,
        updateAt: Long?,
        crop: Long?,
        reflow: Long?,
        scrollOri: Long?,
        zoom: Double?,
        scrollX: Long?,
        scrollY: Long?,
    ): Recent {
        return Recent(
            id = id,
            path = path,
            page = page,
            pageCount = pageCount,
            createAt = createAt,
            updateAt = updateAt,
            crop = crop,
            reflow = reflow,
            scrollOri = scrollOri,
            zoom = zoom,
            scrollX = scrollX,
            scrollY = scrollY,
        )
    }

    internal fun clearAndCreateProgress(recents: List<Recent>) {
        dbQuery.transaction {
            dbQuery.removeAllProgresses()
            recents.forEach { recent ->
                dbQuery.insertProgress(
                    path = recent.path.toString(),
                    page = recent.page,
                    pageCount = recent.pageCount,
                    createAt = recent.createAt,
                    updateAt = recent.updateAt,
                    crop = recent.crop,
                    reflow = recent.reflow,
                    scrollOri = recent.scrollOri,
                    zoom = recent.zoom,
                    scrollX = recent.scrollX,
                    scrollY = recent.scrollY,
                )
            }
        }
    }
}