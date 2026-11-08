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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class ContentRepository(
    private val context: Context,
    private val apiService: PlayDramaFlixApiService = ApiClient.apiService,
    private val database: AppDatabase = AppDatabase.getInstance(context)
) {
    private val watchHistoryDao = database.watchHistoryDao()
    private val watchlistDao = database.watchlistDao()
    private val contentCacheDao = database.contentCacheDao()
    private val viewsCachePrefs = context.getSharedPreferences("play_drama_flix_views_24h_cache", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "ContentRepository"
        // ⏱️ হোম ফিড ক্যাশ মেয়াদ ৩ মিনিট (ডাটাবেজ লোড প্রটেকশন)
        private const val FEED_CACHE_TTL_MS = 3 * 60 * 1000L
        // ⏱️ পর্ব ও স্ট্রিমিং সার্ভারের ভারী ডাটার ক্যাশ মেয়াদ ১০ মিনিট
        private const val WATCH_CACHE_TTL_MS = 10 * 60 * 1000L
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
    // 🎬 ১. হোম ফিড কনটেন্ট (স্মার্ট ক্যাশিং - ডাটাবেজের ওপর কোনো চাপ ছাড়া)
    // =========================================================================

    suspend fun getContents(forceRefresh: Boolean = false): Result<List<ContentItemDto>> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        if (forceRefresh) {
            contentCacheDao.clearCachedHomeFeed("primary_home_feed")
            Log.d(TAG, "✓ Home feed cache cleared due to forceRefresh.")
        } else {
            val localFeed = contentCacheDao.getCachedHomeFeed("primary_home_feed")
            if (localFeed != null) {
                val isFresh = (now - localFeed.cachedTimestamp) < FEED_CACHE_TTL_MS
                val cachedList = runCatching { contentListAdapter.fromJson(localFeed.jsonPayload) }.getOrNull()

                if (!cachedList.isNullOrEmpty() && isFresh) {
                    Log.d(TAG, "✓ Home feed loaded from Local Room DB (Zero DB load).")
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
                Log.d(TAG, "✓ Fresh home feed cached.")
                Result.success(serverItems)
            } else {
                val fallbackList = runCatching {
                    contentCacheDao.getCachedHomeFeed("primary_home_feed")?.let { contentListAdapter.fromJson(it.jsonPayload) }
                }.getOrNull()
                Result.success(fallbackList ?: getFallbackContents())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Server connection notice, serving offline Room DB: ${e.message}")
            val fallbackList = runCatching {
                contentCacheDao.getCachedHomeFeed("primary_home_feed")?.let { contentListAdapter.fromJson(it.jsonPayload) }
            }.getOrNull()
            Result.success(fallbackList ?: getFallbackContents())
        }
    }

    // =========================================================================
    // ⚡ ২. ওয়াচ ডিটেইলস (জিরো-ডাটাবেজ লোড + রিয়েল-টাইম লাইভ ভিউজ সিঙ্ক)
    // =========================================================================

    suspend fun getWatchDetails(
        slug: String,
        fallbackContent: ContentItemDto? = null,
        forceRefresh: Boolean = false
    ): Result<WatchDetailResponse> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        if (forceRefresh) {
            contentCacheDao.deleteCachedWatchDetail(slug)
            try {
                File(context.cacheDir, "precache_streams/$slug").deleteRecursively()
            } catch (_: Exception) {}
            Log.d(TAG, "✓ Cache revalidated for '$slug'.")
        } else {
            val localCached = contentCacheDao.getCachedWatchDetail(slug)
            if (localCached != null) {
                val isFresh = (now - localCached.cachedTimestamp) < WATCH_CACHE_TTL_MS
                val cachedDetail = runCatching { watchDetailAdapter.fromJson(localCached.jsonPayload) }.getOrNull()

                if (cachedDetail?.content != null && cachedDetail.episodes.isNotEmpty() && isFresh) {
                    Log.d(TAG, "✓ Serving watch details from Local Room DB (Zero MySQL Load).")

                    // 🎯 মেমোরির তাজা ভিউ সংখ্যা ড্রামা ডিটেইলসে ইনজেক্ট করা
                    val contentIdStr = cachedDetail.content.id
                    val cachedLiveViews = viewsCachePrefs.getLong("cached_views_$contentIdStr", 0L)
                    val updatedContent = if (cachedLiveViews > cachedDetail.content.viewsCount) {
                        cachedDetail.content.copy(rawViews = cachedLiveViews)
                    } else {
                        cachedDetail.content
                    }

                    val finalResponse = cachedDetail.copy(content = updatedContent)

                    CoroutineScope(Dispatchers.IO).launch {
                        precacheFirst3SecondsOfEpisodes(slug, cachedDetail.episodes.take(3))
                    }

                    return@withContext Result.success(finalResponse)
                }
            }
        }

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

                if (finalBody.episodes.isNotEmpty()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        precacheFirst3SecondsOfEpisodes(slug, finalBody.episodes.take(3))
                    }
                }

                Result.success(finalBody)
            } else {
                val fallbackCached = runCatching {
                    contentCacheDao.getCachedWatchDetail(slug)?.let { watchDetailAdapter.fromJson(it.jsonPayload) }
                }.getOrNull()

                if (fallbackCached?.content != null && fallbackCached.episodes.isNotEmpty()) {
                    Result.success(fallbackCached)
                } else if (fallbackContent != null) {
                    Result.success(getFallbackWatchDetails(slug, fallbackContent))
                } else {
                    Result.failure(Exception("Content not found: $slug"))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Watch details offline fallback: ${e.message}")
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
    // ⚡ ৩. প্রথম ৩টি পর্বের ৩ সেকেন্ড প্রি-ডাউনলোড ইঞ্জিন (০-সেকেন্ড বাফারিং)
    // =========================================================================

    private suspend fun precacheFirst3SecondsOfEpisodes(
        slug: String,
        targetEpisodes: List<EpisodeDto>
    ) = withContext(Dispatchers.IO) {
        val precacheDir = File(context.cacheDir, "precache_streams/$slug").apply {
            if (!exists()) mkdirs()
        }

        targetEpisodes.forEach { ep ->
            try {
                val rawStreamUrl = ep.resolveR2StreamUrl(slug).trim()
                if (rawStreamUrl.isBlank() || (!rawStreamUrl.startsWith("http://") && !rawStreamUrl.startsWith("https://"))) {
                    return@forEach
                }

                val isHls = rawStreamUrl.contains(".m3u8", ignoreCase = true)

                if (isHls) {
                    precacheHlsFirstSegment(rawStreamUrl, precacheDir, ep.episodeNumber)
                } else {
                    precacheMp4FirstChunk(rawStreamUrl, precacheDir, ep.episodeNumber)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Pre-cache notice for ep ${ep.episodeNumber}: ${e.message}")
            }
        }
    }

    private fun precacheMp4FirstChunk(urlStr: String, precacheDir: File, epNum: Int) {
        var conn: HttpURLConnection? = null
        try {
            val targetFile = File(precacheDir, "ep_${epNum}_initial.part")
            if (targetFile.exists() && targetFile.length() >= 256 * 1024L) {
                return
            }

            val url = URL(urlStr)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 6000
                instanceFollowRedirects = true
                setRequestProperty("Range", "bytes=0-524287")
                setRequestProperty("User-Agent", "PlayDramaFlix Precache")
                connect()
            }

            if (conn.responseCode in 200..299 || conn.responseCode == 206) {
                val input = conn.inputStream
                val output = FileOutputStream(targetFile)
                input.use { inStream ->
                    output.use { outStream ->
                        inStream.copyTo(outStream)
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            conn?.disconnect()
        }
    }

    private fun precacheHlsFirstSegment(m3u8UrlStr: String, precacheDir: File, epNum: Int) {
        var conn: HttpURLConnection? = null
        try {
            val targetFile = File(precacheDir, "ep_${epNum}_seg0.ts")
            if (targetFile.exists() && targetFile.length() >= 1000L) {
                return
            }

            val url = URL(m3u8UrlStr)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 5000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "PlayDramaFlix Precache")
                connect()
            }

            if (conn.responseCode in 200..299) {
                val playlistContent = conn.inputStream.bufferedReader().use { it.readText() }
                val baseUrl = m3u8UrlStr.substringBeforeLast("/")

                val lines = playlistContent.lines().map { it.trim() }.filter { it.isNotBlank() }
                val firstSegmentLine = lines.firstOrNull {
                    !it.startsWith("#") && (it.endsWith(".ts") || it.endsWith(".m4s") || it.contains(".ts?") || it.contains(".m4s?"))
                }

                if (firstSegmentLine != null) {
                    val segmentUrl = if (firstSegmentLine.startsWith("http://") || firstSegmentLine.startsWith("https://")) {
                        firstSegmentLine
                    } else {
                        "$baseUrl/$firstSegmentLine"
                    }

                    val segConn = (URL(segmentUrl).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 4000
                        readTimeout = 5000
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "PlayDramaFlix Precache")
                    }
                    if (segConn.responseCode in 200..299) {
                        segConn.inputStream.use { input ->
                            FileOutputStream(targetFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    segConn.disconnect()
                }
            }
        } catch (_: Exception) {
        } finally {
            conn?.disconnect()
        }
    }

    // =========================================================================
    // 🔔 ৪. রিয়েল-টাইম পুশ সিঙ্ক মেথড
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
                true
            } else false
        } catch (e: Exception) {
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
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

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
        return WatchDetailResponse(
            success = fallbackContent != null,
            status = if (fallbackContent != null) 200 else 404,
            content = fallbackContent,
            episodes = emptyList()
        )
    }
}
