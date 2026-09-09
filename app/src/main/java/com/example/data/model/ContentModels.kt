package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ContentResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: Any? = null,
    @Json(name = "total") val total: Int? = 0,
    @Json(name = "data") val data: List<ContentItemDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ContentItemDto(
    @Json(name = "id") val rawId: Any? = null,
    @Json(name = "type") val type: String = "series", // "movie" | "series" | "shorts" | "anime"
    @Json(name = "title") val title: String = "",
    @Json(name = "slug") val slug: String = "",
    @Json(name = "description") val description: String? = null,
    @Json(name = "meta_description") val metaDescription: String? = null,
    @Json(name = "language") val language: String = "Bangla Dubbed",
    @Json(name = "dub_badge") val customDubBadge: String? = null,
    @Json(name = "release_year") val releaseYear: String = "2026",
    @Json(name = "rating") val rawRating: Any? = "8.5",
    @Json(name = "views") val rawViews: Any? = 0,
    @Json(name = "categories") val rawCategories: Any? = null,
    @Json(name = "total_episodes") val rawTotalEpisodes: Any? = null,
    @Json(name = "poster_url") val posterUrl: String? = null,
    @Json(name = "banner_url") val bannerUrl: String? = null,
    @Json(name = "share_url") val shareUrl: String? = null,
    @Json(name = "synopsis") val customSynopsis: String? = null,
    @Json(name = "is_featured") val isFeatured: Boolean = false,
    @Json(name = "is_recent") val isRecent: Boolean = false,
    @Json(name = "is_hot") val isHot: Boolean = false
) {
    val id: String get() = rawId?.toString() ?: slug

    val rating: Double
        get() = when (rawRating) {
            is Number -> rawRating.toDouble()
            is String -> rawRating.toDoubleOrNull() ?: 8.5
            else -> 8.5
        }

    val views: String
        get() = when (rawViews) {
            is Number -> "${rawViews} views"
            is String -> rawViews
            else -> "0 views"
        }

    val numericViews: Long
        get() = when (val v = rawViews) {
            is Number -> v.toLong()
            is String -> {
                val clean = v.trim().uppercase().replace("VIEWS", "").replace("VIEW", "").trim()
                when {
                    clean.endsWith("M") -> ((clean.dropLast(1).toDoubleOrNull() ?: 1.0) * 1_000_000).toLong()
                    clean.endsWith("K") -> ((clean.dropLast(1).toDoubleOrNull() ?: 1.0) * 1_000).toLong()
                    else -> clean.toLongOrNull() ?: 0L
                }
            }
            else -> 0L
        }

    val viewsDisplay: String
        get() {
            val n = numericViews
            return when {
                n >= 1_000_000 -> "${(n / 100_000) / 10.0}M"
                n >= 1_000 -> "${(n / 100) / 10.0}K"
                n > 0 -> "$n"
                rawViews is String && (rawViews as String).isNotBlank() -> rawViews as String
                else -> "100K"
            }
        }

    val categories: List<String>
        get() = when (rawCategories) {
            is List<*> -> rawCategories.filterIsInstance<String>().flatMap { it.split(",") }.map { it.trim() }.filter { it.isNotEmpty() }
            is String -> if (rawCategories.isNotBlank()) rawCategories.split(",").map { it.trim() }.filter { it.isNotEmpty() } else emptyList()
            else -> emptyList()
        }

    val totalEpisodes: Int
        get() {
            val num = when (rawTotalEpisodes) {
                is Number -> rawTotalEpisodes.toInt()
                is String -> rawTotalEpisodes.toIntOrNull() ?: 0
                else -> 0
            }
            return if (num > 0) num else 1
        }

    val isSpotlight: Boolean get() = isFeatured || isHot

    val isRecentlyAdded: Boolean
        get() = isFeatured || releaseYear == "2026" || releaseYear == "2025" || title.contains("Guess Who I Am", ignoreCase = true)

    val watchUrl: String get() = shareUrl ?: "https://playdramaflix.com/watch/$slug"

    val isShorts: Boolean
        get() = type.equals("shorts", ignoreCase = true) ||
                categories.any { it.contains("Shorts", ignoreCase = true) } ||
                title.contains("Shorts", ignoreCase = true) ||
                slug.contains("shorts", ignoreCase = true)

    val isAnime: Boolean
        get() = type.equals("anime", ignoreCase = true) ||
                categories.any { it.contains("Anime", ignoreCase = true) } ||
                title.contains("Anime", ignoreCase = true) ||
                slug.contains("anime", ignoreCase = true)

    val isMovie: Boolean
        get() = !isShorts && !isAnime && (
                type.equals("movie", ignoreCase = true) ||
                type.equals("film", ignoreCase = true) ||
                categories.any { it.contains("Movie", ignoreCase = true) || it.contains("Film", ignoreCase = true) } ||
                title.contains("Movie", ignoreCase = true)
        )

    val isDramaSeries: Boolean
        get() = !isShorts && !isAnime && !isMovie && (
                type.equals("series", ignoreCase = true) ||
                type.equals("drama", ignoreCase = true) ||
                totalEpisodes > 1
        )

    val isBanglaDub: Boolean
        get() = (language.contains("Bangla", ignoreCase = true) ||
                dubBadge.contains("Bangla", ignoreCase = true) ||
                title.contains("Bangla", ignoreCase = true)) &&
                !language.startsWith("Hindi", ignoreCase = true)

    val isHindiDub: Boolean
        get() = (language.contains("Hindi", ignoreCase = true) ||
                dubBadge.contains("Hindi", ignoreCase = true) ||
                title.contains("Hindi", ignoreCase = true)) &&
                !language.startsWith("Bangla", ignoreCase = true)

    val dubBadge: String
        get() {
            if (!customDubBadge.isNullOrBlank()) return customDubBadge
            val lower = language.lowercase()
            return when {
                lower.contains("bangla") -> "Bangla Dub"
                lower.contains("hindi") -> "Hindi Dub"
                lower.contains("dual") -> "Dual Audio"
                else -> "Bangla Dub"
            }
        }

    val synopsis: String
        get() = description?.takeIf { it.isNotBlank() } ?: customSynopsis ?: metaDescription ?: "Watch full episodes in HD on PlayDramaFlix."

    val trailerUrl: String get() = shareUrl ?: ""
    val quality: String get() = "1080p Full HD"
    val viewsCount: Long get() = numericViews

    val country: String
        get() = when {
            title.contains("Korea", ignoreCase = true) || categories.any { it.contains("k-drama", ignoreCase = true) || it.contains("korean", ignoreCase = true) } -> "South Korea"
            title.contains("China", ignoreCase = true) || categories.any { it.contains("c-drama", ignoreCase = true) || it.contains("chinese", ignoreCase = true) } -> "China"
            isAnime || categories.any { it.contains("Japan", ignoreCase = true) || it.contains("anime", ignoreCase = true) } -> "Japan"
            else -> "Asia"
        }
}

// ⚡ Cloudflare R2 Streaming Wrappers
@JsonClass(generateAdapter = true)
data class ForAppDto(
    @Json(name = "stream_url") val streamUrl: String? = null,
    @Json(name = "mime_type") val mimeType: String? = "video/mp4"
)

@JsonClass(generateAdapter = true)
data class ForWebDto(
    @Json(name = "player_url") val playerUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class WatchDetailResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: Any? = null,
    @Json(name = "content") val content: ContentItemDto? = null,
    @Json(name = "for_app") val forApp: ForAppDto? = null,
    @Json(name = "for_web") val forWeb: ForWebDto? = null,
    @Json(name = "servers") val servers: List<ServerDto> = emptyList(),
    @Json(name = "episodes") val episodes: List<EpisodeDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ServerDto(
    @Json(name = "id") val rawId: Any? = null,
    @Json(name = "content_id") val rawContentId: Any? = null,
    @Json(name = "episode_id") val rawEpisodeId: Any? = null,
    @Json(name = "server_name") val serverName: String? = "Server 1 (Byse.sx)",
    @Json(name = "raw_url") val rawUrl: String? = null,
    @Json(name = "embed_url") val embedUrl: String? = null,
    @Json(name = "server_type") val serverType: String? = "stream",
    @Json(name = "quality") val quality: String? = "Streaming"
) {
    val id: String get() = rawId?.toString() ?: ""
    val name: String get() = serverName ?: "Server 1 (Byse.sx)"
    val episodeId: String get() = rawEpisodeId?.toString() ?: ""
    val url: String get() = embedUrl?.takeIf { it.isNotBlank() } ?: rawUrl ?: ""
    val type: String get() = if (serverType == "hls" || url.endsWith(".m3u8")) "hls" else "embed"
}

@JsonClass(generateAdapter = true)
data class EpisodeDto(
    @Json(name = "episode_id") val rawEpisodeId: Any? = null,
    @Json(name = "episode_number") val episodeNumber: Int = 1,
    @Json(name = "title") val rawTitle: String? = null,
    @Json(name = "ep_title") val epTitle: String? = null,
    @Json(name = "season_number") val seasonNumber: Int = 1,
    @Json(name = "duration") val duration: String = "24m",
    @Json(name = "thumbnail") val thumbnail: String? = null,
    @Json(name = "app_stream_url") val appStreamUrl: String? = null,
    @Json(name = "video_url") val videoUrl: String? = null,
    @Json(name = "web_player_url") val webPlayerUrl: String? = null,
    @Json(name = "embed_url") val embedUrl: String? = null,
    @Json(name = "download_url") val downloadUrl: String? = null,
    @Json(name = "is_locked") val isLocked: Boolean = false,
    @Json(name = "ads_count") val adsCount: Int = 0
) {
    val episodeId: String get() = rawEpisodeId?.toString() ?: episodeNumber.toString()
    val displayTitle: String get() = rawTitle?.takeIf { it.isNotBlank() } ?: epTitle?.takeIf { it.isNotBlank() } ?: "Episode $episodeNumber"

    /**
     * ⚡ Cloudflare R2 Direct MP4 URL Resolver
     */
    fun resolveR2StreamUrl(dramaSlug: String): String {
        return appStreamUrl?.takeIf { it.isNotBlank() }
            ?: videoUrl?.takeIf { it.isNotBlank() }
            ?: downloadUrl?.takeIf { it.isNotBlank() }
            ?: "https://cdn.playdramaflix.com/streams/$dramaSlug/ep_$episodeNumber/download.mp4"
    }

    fun resolveDownloadUrl(dramaSlug: String): String {
        return downloadUrl?.takeIf { it.isNotBlank() }
            ?: resolveR2StreamUrl(dramaSlug)
    }
}
