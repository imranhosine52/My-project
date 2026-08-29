package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY lastWatchedAt DESC LIMIT 20")
    fun getContinueWatching(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE contentSlug = :slug ORDER BY lastWatchedAt DESC LIMIT 1")
    suspend fun getLastWatchedEpisode(slug: String): WatchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveWatchProgress(history: WatchHistoryEntity)

    @Query("DELETE FROM watch_history WHERE id = :id")
    suspend fun deleteProgress(id: String)

    @Query("DELETE FROM watch_history")
    suspend fun clearAllHistory()
}

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlist ORDER BY addedAt DESC")
    fun getAllWatchlist(): Flow<List<WatchlistEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE id = :slug)")
    fun isInWatchlistFlow(slug: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE id = :slug)")
    suspend fun isInWatchlist(slug: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToWatchlist(item: WatchlistEntity)

    @Query("DELETE FROM watchlist WHERE id = :slug")
    suspend fun removeFromWatchlist(slug: String)
}

@Dao
interface DramaStatsDao {
    @Query("SELECT * FROM drama_stats WHERE slug = :slug")
    fun getStatsFlow(slug: String): Flow<DramaStatsEntity?>

    @Query("SELECT * FROM drama_stats WHERE slug = :slug")
    suspend fun getStats(slug: String): DramaStatsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(stats: DramaStatsEntity)

    @Query("UPDATE drama_stats SET isLiked = :isLiked, likesCount = :likesCount, lastUpdated = :timestamp WHERE slug = :slug")
    suspend fun updateLike(slug: String, isLiked: Boolean, likesCount: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE drama_stats SET viewsCount = viewsCount + 1, lastUpdated = :timestamp WHERE slug = :slug")
    suspend fun incrementViews(slug: String, timestamp: Long = System.currentTimeMillis())
}
