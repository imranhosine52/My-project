package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 📺 ১. ওয়াচ হিস্ট্রি ডাও (অপরিবর্তিত)
 */
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

/**
 * 🔖 ২. ওয়াচলিস্ট ডাও (অপরিবর্তিত)
 */
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

/**
 * 📊 ৩. লাইক ও ভিউজ ডাও (অপরিবর্তিত)
 */
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

// =============================================================================
// 🚀 নতুন: অফলাইন-ফার্স্ট ক্যাশ ডাও (ContentCacheDao)
// =============================================================================

@Dao
interface ContentCacheDao {

    // --- ক) হোম ফিড ক্যাশ কুয়েরি ---

    @Query("SELECT * FROM cached_home_feed WHERE feedKey = :key LIMIT 1")
    suspend fun getCachedHomeFeed(key: String = "primary_home_feed"): CachedFeedEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCachedHomeFeed(feed: CachedFeedEntity)

    @Query("DELETE FROM cached_home_feed WHERE feedKey = :key")
    suspend fun clearCachedHomeFeed(key: String = "primary_home_feed")


    // --- খ) নির্দিষ্ট ড্রামার সমস্ত পর্ব ও ভিডিও ইউআরএল ক্যাশ কুয়েরি ---

    @Query("SELECT * FROM cached_watch_details WHERE slug = :slug LIMIT 1")
    suspend fun getCachedWatchDetail(slug: String): CachedWatchDetailEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCachedWatchDetail(detail: CachedWatchDetailEntity)

    @Query("DELETE FROM cached_watch_details WHERE slug = :slug")
    suspend fun deleteCachedWatchDetail(slug: String)

    @Query("DELETE FROM cached_watch_details")
    suspend fun clearAllWatchDetailsCache()
}
