package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.WatchHistoryEntity
import com.example.data.local.WatchlistEntity
import com.example.data.model.*
import com.example.data.remote.ApiClient
import com.example.data.remote.PlayDramaFlixApiService
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
        // ✅ নন-নাল নিশ্চিত করা হলো (আগের 185 নম্বর লাইনের বাগ সমাধান)
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
    // 🎬 REMOTE CONTENT & WATCH DETAILS
    // =========================================================================

    suspend fun getContents(): Result<List<ContentItemDto>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getContents()
            if (response.isSuccessful && response.body()?.data?.isNotEmpty() == true) {
                Result.success(response.body()!!.data)
            } else {
                Result.success(getFallbackContents())
            }
        } catch (e: Exception) {
            Log.w("ContentRepository", "API call failed, using fallback contents: ${e.message}")
            Result.success(getFallbackContents())
        }
    }

    suspend fun getWatchDetails(slug: String, fallbackContent: ContentItemDto? = null): Result<WatchDetailResponse> = withContext(Dispatchers.IO) {
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

                Result.success(body.copy(servers = cleanedServers))
            } else {
                Result.success(getFallbackWatchDetails(slug, fallbackContent))
            }
        } catch (e: Exception) {
            Log.w("ContentRepository", "Watch details API failed for slug=$slug: ${e.message}")
            Result.success(getFallbackWatchDetails(slug, fallbackContent))
        }
    }

    // =========================================================================
    // ⚡ CLOUDFLARE R2 FALLBACK DATA GENERATOR
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
                description = "A world-defying warrior descends from sacred peaks to reclaim his family glory in fast-paced vertical mini episodes.",
                isFeatured = true,
                isRecent = true,
                isHot = true
            ),
            ContentItemDto(
                rawId = "s2",
                title = "Lost In Love Bangla Dubbed",
                slug = "lost-in-love-bangla-dubbed",
                type = "shorts",
                language = "Bangla Dubbed",
                customDubBadge = "Bangla",
                releaseYear = "2026",
                rawRating = "9.1",
                rawViews = "280K",
                rawCategories = "Shorts Drama, Bangla Dub",
                rawTotalEpisodes = 9,
                posterUrl = "https://playdramaflix.com/public/uploads/posters/1787446896_6a8a4670591ea.webp",
                bannerUrl = "https://playdramaflix.com/public/uploads/banner/1787446896_6a8a4670593c3.jpg",
                shareUrl = "https://playdramaflix.com/lost-in-love-bangla-dubbed",
                description = "Rediscovering feelings across time, heartbreak, and sweet unexpected twists in this romantic micro drama.",
                isFeatured = false,
                isRecent = true,
                isHot = true
            )
        )
    }

    fun getFallbackWatchDetails(slug: String, fallbackContent: ContentItemDto? = null): WatchDetailResponse {
        val content = fallbackContent
            ?: getFallbackContents().find { it.slug == slug || it.rawId == slug }
            ?: ContentItemDto(
                rawId = slug,
                title = slug.replace("-", " ").replaceFirstChar { it.uppercase() },
                slug = slug,
                type = "series",
                language = "Bangla Dubbed",
                customDubBadge = "Bangla",
                releaseYear = "2026",
                rawRating = "9.0",
                rawViews = "100K",
                rawCategories = "Drama Series",
                rawTotalEpisodes = 10,
                posterUrl = "https://playdramaflix.com/public/uploads/posters/1787413105_6a89c271df941.webp",
                bannerUrl = "https://playdramaflix.com/public/uploads/banner/1787413105_6a89c271dfab5.webp",
                shareUrl = "https://playdramaflix.com/$slug",
                description = "Enjoy high quality streaming with full episodes.",
                isFeatured = false,
                isRecent = false,
                isHot = false
            )

        val isMovie = content.type == "movie"
        val totalEp = if (isMovie) 1 else if (content.totalEpisodes > 0) content.totalEpisodes else 8

        val episodes = (1..totalEp).map { num ->
            val r2StreamUrl = "https://cdn.playdramaflix.com/streams/${content.slug}/ep_$num/download.mp4"
            val epTitle = if (isMovie) "Full Movie HD" else "${content.title} - Episode $num"
            EpisodeDto(
                rawEpisodeId = "ep_${content.slug}_$num",
                episodeNumber = num,
                rawTitle = epTitle,
                epTitle = epTitle,
                seasonNumber = 1,
                duration = if (isMovie) "1h 54m" else "${20 + (num % 5)}m",
                thumbnail = "https://cdn.playdramaflix.com/streams/${content.slug}/ep_$num/thumb.jpg",
                appStreamUrl = r2StreamUrl,
                videoUrl = r2StreamUrl,
                webPlayerUrl = "https://playdramaflix.com/player/?url=https%3A%2F%2Fcdn.playdramaflix.com%2Fstreams%2F${content.slug}%2Fep_$num%2Fmaster.m3u8",
                embedUrl = "https://byse.sx/e/${content.slug}_ep_$num",
                downloadUrl = r2StreamUrl,
                isLocked = num > 1 && !isMovie,
                adsCount = if (num > 1) 2 else 0
            )
        }

        val servers = listOf(
            ServerDto(
                rawId = "srv_1_${content.slug}",
                serverName = "Cloudflare R2 Ultra Fast HD",
                rawUrl = episodes.firstOrNull()?.resolveR2StreamUrl(content.slug),
                serverType = "mp4",
                rawEpisodeId = episodes.firstOrNull()?.episodeId
            )
        )

        return WatchDetailResponse(
            success = true,
            status = 200,
            content = content,
            forApp = ForAppDto(
                streamUrl = episodes.firstOrNull()?.resolveR2StreamUrl(content.slug),
                mimeType = "video/mp4"
            ),
            servers = servers,
            episodes = episodes
        )
    }
}
