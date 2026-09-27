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
        // ⏱️ ক্যাশ মেয়াদ ৪ ঘণ্টার বদলে ৩ মিনিট করা হলো (যাতে নতুন পোস্ট করার সাথে সাথে চলে আসে)
        private const val FEED_CACHE_TTL_MS = 3 * 60 * 1000L
        // ⏱️ নতুন পর্বের আপডেট নিশ্চিত করতে ওয়াচ ক্যাশ ৮ ঘণ্টা থেকে কমিয়ে ২০ মিনিট করা হলো
        private const val WATCH_CACHE_TTL_MS = 20 * 60 * 1000L
    }

    // ⚡ Moshi JSON অ্যাডাপ্টার (লোকাল ডাটাবেজে দ্রুত ও নিরাপদ রূপান্তরের জন্য)
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
    // 🎬 ১. হোম ফিড কনটেন্ট (নতুন পোস্টের জন্য ইনস্ট্যান্ট লাইভ সিঙ্ক)
    // =========================================================================

    suspend fun getContents(forceRefresh: Boolean = false): Result<List<ContentItemDto>> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        // ক) forceRefresh false হলে এবং ক্যাশ ৩ মিনিটের কম পুরোনো হলে লোকাল মেমোরি থেকে রিটার্ন করবে
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

        // খ) forceRefresh true হলে অথবা ৩ মিনিট পার হলে সরাসরি লাইভ সার্ভার থেকে আনবে
        try {
            val response = apiService.getContents()
            if (response.isSuccessful && response.body()?.data?.isNotEmpty() == true) {
                val serverItems = response.body()!!.data
                
                // ফোনে ফ্রেশ ডাটা পার্মানেন্ট সেভ করা
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
                // সার্ভার এরর দিলে পুরোনো লোকাল ক্যাশ ফেরত দেওয়া
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
    // ⚡ ২. ওয়াচ ডিটেইলস ও পর্বের লিঙ্ক (ইনস্ট্যান্ট নতুন পর্ব আপডেট)
    // =========================================================================

    suspend fun getWatchDetails(
        slug: String,
        fallbackContent: ContentItemDto? = null,
        forceRefresh: Boolean = false
    ): Result<WatchDetailResponse> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        // ক) ক্যাশ চেক
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

        // খ) মেমোরিতে না থাকলে বা মেয়াদ শেষ হলে সার্ভার থেকে ফ্রেশ পর্ব লোড
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

                // 💾 নতুন পর্ব সহ ফোনে সেভ
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
                val fallbackCached = runCatching {
                    contentCacheDao.getCachedWatchDetail(slug)?.let { watchDetailAdapter.fromJson(it.jsonPayload) }
                }.getOrNull()
                Result.success(fallbackCached ?: getFallbackWatchDetails(slug, fallbackContent))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Watch details API failed, playing from offline Room DB: ${e.message}")
            val fallbackCached = runCatching {
                contentCacheDao.getCachedWatchDetail(slug)?.let { watchDetailAdapter.fromJson(it.jsonPayload) }
            }.getOrNull()
            Result.success(fallbackCached ?: getFallbackWatchDetails(slug, fallbackContent))
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

    fun getFallbackWatchDetails(slug: String, fallbackContent: ContentItemDto? = null): WatchDetailResponse {
        val content = fallbackContent ?: ContentItemDto(
            rawId = slug,
            title = slug.replace("-", " ").replaceFirstChar { it.uppercase() },
            slug = slug,
            type = "shorts"
        )
        return WatchDetailResponse(
            success = true,
            status = 200,
            content = content,
            episodes = emptyList()
        )
    }
}
