package com.archko.reader.pdf.cache

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.archko.reader.pdf.entity.Bookmark

/**
 * 书签数据访问对象
 * @author: archko 2026/3/1
 */
@Dao
public interface BookmarkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun insertBookmark(bookmark: Bookmark): Long

    @Update
    public suspend fun updateBookmark(bookmark: Bookmark)

    @Delete
    public suspend fun deleteBookmark(bookmark: Bookmark)

    @Query("SELECT * FROM bookmark WHERE path = :path ORDER BY pageIndex ASC")
    public suspend fun getBookmarksByPath(path: String): List<Bookmark>

    @Query("SELECT * FROM bookmark ORDER BY updateAt DESC")
    public suspend fun getAllBookmarks(): List<Bookmark>

    @Query("SELECT * FROM bookmark WHERE path = :path AND pageIndex = :pageIndex LIMIT 1")
    public suspend fun getBookmarkByPageAndPath(path: String, pageIndex: Int): Bookmark?

    @Query("SELECT COUNT(*) FROM bookmark WHERE path = :path")
    public suspend fun getBookmarkCountByPath(path: String): Int

    @Query("DELETE FROM bookmark WHERE path = :path")
    public suspend fun deleteBookmarksByPath(path: String)

    @Query("DELETE FROM bookmark")
    public suspend fun deleteAllBookmarks()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun insertAllBookmarks(bookmarks: List<Bookmark>)
}
