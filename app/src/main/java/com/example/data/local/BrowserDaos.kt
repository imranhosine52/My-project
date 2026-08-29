package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BrowserHistoryDao {
    @Query("SELECT * FROM browser_history ORDER BY visitedAt DESC LIMIT 200")
    fun getHistory(): Flow<List<BrowserHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: BrowserHistoryEntity)

    @Query("DELETE FROM browser_history WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Query("DELETE FROM browser_history")
    suspend fun clearAll()
}

@Dao
interface BrowserBookmarkDao {
    @Query("SELECT * FROM browser_bookmarks ORDER BY addedAt DESC")
    fun getBookmarks(): Flow<List<BrowserBookmarkEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM browser_bookmarks WHERE url = :url)")
    fun isBookmarkedFlow(url: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addBookmark(bookmark: BrowserBookmarkEntity)

    @Query("DELETE FROM browser_bookmarks WHERE url = :url")
    suspend fun removeBookmark(url: String)
}
