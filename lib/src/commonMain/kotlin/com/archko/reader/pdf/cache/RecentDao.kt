package com.archko.reader.pdf.cache

import androidx.room.*
import com.archko.reader.pdf.entity.Recent

/**
 * @author: archko 2025/7/8 :21:27
 */
@Dao
public interface RecentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun addRecent(recent: Recent): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun addRecents(recent: List<Recent>)

    @Update(
        onConflict = OnConflictStrategy.REPLACE
    )
    public suspend fun updateRecent(recent: Recent)

    @Query("SELECT * FROM recent WHERE id = :id")
    public suspend fun getRecent(id: Long): Recent?

    @Query("SELECT * FROM recent WHERE path = :path")
    public suspend fun getRecent(path: String): Recent?

    @Query("SELECT * FROM recent order by updateAt desc limit :start, :count")
    public suspend fun getRecents(start: Int, count: Int): List<Recent>?

    @Query("SELECT * FROM recent WHERE updateAt < :lastUpdateAt order by updateAt desc limit :count")
    public suspend fun getRecentsAfter(lastUpdateAt: Long, count: Int): List<Recent>?

    @Query("SELECT count(id) FROM recent")
    public suspend fun recentCount(): Int

    //@Delete
    @Query("Delete FROM recent where id = :id")
    public suspend fun deleteRecent(id: Long)

    //@Delete
    @Query("Delete FROM recent")
    public suspend fun deleteAllRecents()

    @Delete
    public suspend fun deleteRecent(recent: Recent)
}