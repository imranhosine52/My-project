package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.local.*
import com.example.data.model.*
import com.example.data.remote.ApiClient
import com.example.data.remote.PlayDramaFlixApiService
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ContentRepository(
    private val context: Context,
    private val apiService: PlayDramaFlixApiService = ApiClient.apiService,
    private val database: AppDatabase = AppDatabase.getInstance(context)
) {
    private val watchHistoryDao = database.watchHistoryDao()
    private val watchlistDao = database.watchlistDao()
    private val contentCacheDao = database.contentCacheDao()

    companion object {
        private const val TAG = "ContentRepository"
        // ⏱️ ক্যাশ মেয়াদ ৩ মিনিট
        private const val FEED_CACHE_TTL_MS = 3 * 60 * 1000L
        // ⏱️ ওয়াচ ক্যাশ ২০ মিনিট
        private const val WATCH_CACHE_TTL_MS = 20 * 60 * 1000L
    }

    // ⚡ Moshi JSON অ্যাডাপ্টার
    private val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    private val contentListAdapter: JsonAdapter<List<ContentItemDto>> = moshi.adapter(
        Types.newParameterizedType(List::class.java, ContentItemDto::class.java)
    )

    private val watchDetailAdapter: JsonAdapter<WatchDetailResponse> =
        moshi.adapter(WatchDetailResponse::class.java)

    // 📺 Room Database Observables
    val continueWatchingFlow: Flow<List<WatchHistoryEntity>> = watchHistoryDao.getContinueWatching()
    val watchlistFlow: Flow<List<WatchlistEntity>> = watchlistDao.getAllWatchlist()

    fun isItemInWatchlist(slug: String): Flow<Boolean> = watchlistDao.isInWatchlistFlow(slug)

    suspend fun toggleWatchlist(item: ContentItemDto, isInList: Boolean) = withContext(Dispatchers.IO) {
        if (isInList) {
            watchlistDao.removeFromWatchlist(item.slug)
        } else {
            watchlistDao.addToWatchlist(
                WatchlistEntity(
                    id = item.slug,
                    title = item.title,
                    posterUrl = item.posterUrl,
                    dubBadge = item.dubBadge,
                    rating = item.rating,
                    category = item.categories.firstOrNull() ?: item.type,
                    totalEpisodes = item.totalEpisodes
                )
            )
        }
    }

    suspend fun saveWatchProgress(
        content: ContentItemDto,
        episode: EpisodeDto,
        progressMs: Long,
        totalDurationMs: Long
    ) = withContext(Dispatchers.IO) {
        val pct = if (totalDurationMs > 0) (progressMs.toFloat() / totalDurationMs.toFloat()) else 0f
        val resolvedTitle = episode.displayTitle.ifBlank { "Episode ${episode.episodeNumber}" }

        watchHistoryDao.saveWatchProgress(
            WatchHistoryEntity(
                id = "${content.slug}_ep_${episode.episodeNumber}",
                contentSlug = content.slug,
                contentTitle = content.title,
                posterUrl = content.posterUrl,
                episodeNumber = episode.episodeNumber,
                episodeTitle = resolvedTitle,
                seasonNumber = episode.seasonNumber,
                progressMs = progressMs,
                totalDurationMs = totalDurationMs,
                progressPercentage = pct,
                dubBadge = content.dubBadge,
                lastWatchedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        watchHistoryDao.clearAllHistory()
    }

    // =========================================================================
    // 🎬 ১. হোম ফিড কনটেন্ট
    // =========================================================================

    suspend fun getContents(forceRefresh: Boolean = false): Result<List<ContentItemDto>> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        if (!forceRefresh) {
            val localFeed = contentCacheDao.getCachedHomeFeed("primary_home_feed")
            if (localFeed != null) {
                val isFresh = (now - localFeed.cachedTimestamp) < FEED_CACHE_TTL_MS
                val cachedList = runCatching { contentListAdapter.fromJson(localFeed.jsonPayload) }.getOrNull()

                if (!cachedList.isNullOrEmpty() && isFresh) {
                    Log.d(TAG, "✓ Home feed loaded from Local Room DB (Fresh cache).")
                    return@withContext Result.success(cachedList)
                }
            }
        }

        try {
            val response = apiService.getContents()
            if (response.isSuccessful && response.body()?.data?.isNotEmpty() == true) {
                val serverItems = response.body()!!.data
                
                val jsonStr = contentListAdapter.toJson(serverItems)
                contentCacheDao.saveCachedHomeFeed(
                    CachedFeedEntity(
                        feedKey = "primary_home_feed",
                        jsonPayload = jsonStr,
                        cachedTimestamp = now
                    )
                )
                Log.d(TAG, "✓ Fresh home feed fetched directly from server and cached.")
                Result.success(serverItems)
            } else {
                val fallbackList = runCatching {
                    contentCacheDao.getCachedHomeFeed("primary_home_feed")?.let { contentListAdapter.fromJson(it.jsonPayload) }
                }.getOrNull()
                Result.success(fallbackList ?: getFallbackContents())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Server connection failed, serving from offline Room DB: ${e.message}")
            val fallbackList = runCatching {
                contentCacheDao.getCachedHomeFeed("primary_home_feed")?.let { contentListAdapter.fromJson(it.jsonPayload) }
            }.getOrNull()
            Result.success(fallbackList ?: getFallbackContents())
        }
    }

    // =========================================================================
    // ⚡ ২. ওয়াচ ডিটেইলস ও পর্বের লিঙ্ক (নিখুঁত 404 হ্যান্ডলিং)
    // =========================================================================

    suspend fun getWatchDetails(
        slug: String,
        fallbackContent: ContentItemDto? = null,
        forceRefresh: Boolean = false
    ): Result<WatchDetailResponse> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        // ক) ক্যাশ চেক (যদি ইতিপূর্বে লোড করা থাকে এবং অফলাইন থাকে)
        if (!forceRefresh) {
            val localCached = contentCacheDao.getCachedWatchDetail(slug)
            if (localCached != null) {
                val isFresh = (now - localCached.cachedTimestamp) < WATCH_CACHE_TTL_MS
                val cachedDetail = runCatching { watchDetailAdapter.fromJson(localCached.jsonPayload) }.getOrNull()

                if (cachedDetail?.content != null && cachedDetail.episodes.isNotEmpty() && isFresh) {
                    Log.d(TAG, "✓ Watch details for '$slug' loaded from Room DB.")
                    return@withContext Result.success(cachedDetail)
                }
            }
        }

        // খ) সার্ভার থেকে ডাটা লোড
        try {
            val response = apiService.getWatchDetails(slug)
            if (response.isSuccessful && response.body()?.content != null) {
                val body = response.body()!!
                val cleanedServers = if (body.servers.isNotEmpty()) {
                    body.servers.mapIndexed { index, srv ->
                        val cleanName = if (srv.serverName.isNullOrBlank() || srv.serverName == "Server 1 (Byse.sx)") {
                            "Server ${index + 1} (${if (srv.type == "hls") "VIP HLS" else if (srv.type == "mp4") "Fast HD" else "Byse.sx"})"
                        } else {
                            srv.serverName
                        }
                        srv.copy(serverName = cleanName)
                    }
                } else body.servers

                val finalBody = body.copy(servers = cleanedServers)

                val jsonStr = watchDetailAdapter.toJson(finalBody)
                contentCacheDao.saveCachedWatchDetail(
                    CachedWatchDetailEntity(
                        slug = slug,
                        jsonPayload = jsonStr,
                        cachedTimestamp = now
                    )
                )
                Log.d(TAG, "✓ Fresh watch details for '$slug' fetched and saved.")
                Result.success(finalBody)
            } else {
                // 🛑 সার্ভারে পোস্ট না থাকলে চেক করা
                val fallbackCached = runCatching {
                    contentCacheDao.getCachedWatchDetail(slug)?.let { watchDetailAdapter.fromJson(it.jsonPayload) }
                }.getOrNull()

                if (fallbackCached?.content != null && fallbackCached.episodes.isNotEmpty()) {
                    Result.success(fallbackCached)
                } else if (fallbackContent != null) {
                    Result.success(getFallbackWatchDetails(slug, fallbackContent))
                } else {
                    // কোনো অস্তিত্ব নেই -> সরাসরি ফেইলিউর রিটার্ন যাতে 404 স্ক্রিন আসে
                    Result.failure(Exception("Content not found on server: $slug"))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Watch details API failed: ${e.message}")
            val fallbackCached = runCatching {
                contentCacheDao.getCachedWatchDetail(slug)?.let { watchDetailAdapter.fromJson(it.jsonPayload) }
            }.getOrNull()

            if (fallbackCached?.content != null && fallbackCached.episodes.isNotEmpty()) {
                Result.success(fallbackCached)
            } else if (fallbackContent != null) {
                Result.success(getFallbackWatchDetails(slug, fallbackContent))
            } else {
                Result.failure(e)
            }
        }
    }

    // =========================================================================
    // 🔔 ৩. রিয়েল-টাইম পুশ সিঙ্ক মেথড
    // =========================================================================

    suspend fun syncSpecificDramaCache(slug: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getWatchDetails(slug)
            if (response.isSuccessful && response.body()?.content != null) {
                val jsonStr = watchDetailAdapter.toJson(response.body()!!)
                contentCacheDao.saveCachedWatchDetail(
                    CachedWatchDetailEntity(
                        slug = slug,
                        jsonPayload = jsonStr,
                        cachedTimestamp = System.currentTimeMillis()
                    )
                )
                Log.i(TAG, "✓ Real-time sync complete: '$slug' pre-downloaded to Room DB.")
                true
            } else false
        } catch (e: Exception) {
            Log.e(TAG, "Real-time sync error for '$slug': ${e.message}")
            false
        }
    }

    suspend fun syncHomeFeedCache(): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getContents()
            if (response.isSuccessful && response.body()?.data?.isNotEmpty() == true) {
                val jsonStr = contentListAdapter.toJson(response.body()!!.data)
                contentCacheDao.saveCachedHomeFeed(
                    CachedFeedEntity(
                        feedKey = "primary_home_feed",
                        jsonPayload = jsonStr,
                        cachedTimestamp = System.currentTimeMillis()
                    )
                )
                Log.i(TAG, "✓ Home feed synced to Room DB via background sync.")
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    // =========================================================================
    // ⚡ অফলাইন ফলব্যাক কনটেন্ট
    // =========================================================================

    fun getFallbackContents(): List<ContentItemDto> {
        return listOf(
            ContentItemDto(
                rawId = "s1",
                title = "The Proud Dragon God Bangla Dubbed",
                slug = "the-proud-dragon-god-bangla-dubbed",
                type = "shorts",
                language = "Bangla Dubbed",
                customDubBadge = "Bangla",
                releaseYear = "2026",
                rawRating = "9.4",
                rawViews = "415K",
                rawCategories = "Shorts Drama, Bangla Dub",
                rawTotalEpisodes = 7,
                posterUrl = "https://playdramaflix.com/public/uploads/posters/1787413105_6a89c271df941.webp",
                bannerUrl = "https://playdramaflix.com/public/uploads/banner/1787413105_6a89c271dfab5.webp",
                shareUrl = "https://playdramaflix.com/the-proud-dragon-god-bangla-dubbed",
                description = "A world-defying warrior descends from sacred peaks to reclaim his family glory.",
                isFeatured = true,
                isRecent = true,
                isHot = true
            )
        )
    }

    /**
     * 🎯 যদি ড্রামাটি পূর্বে পরিচিত না থাকে তবে কোনো ভুয়া অবজেক্ট তৈরি করবে না।
     */
    fun getFallbackWatchDetails(slug: String, fallbackContent: ContentItemDto? = null): WatchDetailResponse {
        return WatchDetailResponse(
            success = fallbackContent != null,
            status = if (fallbackContent != null) 200 else 404,
            content = fallbackContent,
            episodes = emptyList()
        )
    }
}
