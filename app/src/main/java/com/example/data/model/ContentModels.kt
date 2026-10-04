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
    @Json(name = "type") val type: String = "series",
    
    // 🎯 ১. সার্ভারের ছোট নাম (Display Name)
    @Json(name = "display_name") val rawDisplayName: String? = null,
    @Json(name = "name") val rawName: String? = null,
    
    // 🎯 ২. সার্ভারের দেশ (Country)
    @Json(name = "country") val rawCountry: String? = null,
    
    // 🎯 ৩. সার্ভারের মূল টাইটেল ও অন্যান্য ফিল্ড
    @Json(name = "title") val title: String = "",
    @Json(name = "slug") val slug: String = "",
    @Json(name = "description") val description: String? = null,
    @Json(name = "meta_description") val metaDescription: String? = null,
    
    // 🎯 ৪. সার্ভারের ডাবিং ও ভাষা ফিল্ড
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

    // 📱 ছোট ও পরিচ্ছন্ন নাম (Display Name -> Name -> Title Fallback)
    val displayName: String
        get() = rawDisplayName?.takeIf { it.isNotBlank() }
            ?: rawName?.takeIf { it.isNotBlank() }
            ?: title.split("|", "-").firstOrNull()?.trim()
            ?: title

    // 🌍 দেশের নাম (সার্ভার থেকে সরাসরি পাওয়া যাবে)
    val country: String
        get() = rawCountry?.takeIf { it.isNotBlank() } ?: "China"

    // 🏷️ ডাবিং ব্যাজ (সার্ভার থেকে যে ভাষাই পাঠাবে হুবহু সেটাই থাকবে - বাংলা, হিন্দি, ইংলিশ, তামিল ইত্যাদি)
    val dubBadge: String
        get() {
            if (!customDubBadge.isNullOrBlank()) {
                return customDubBadge.trim()
            }
            if (language.isNotBlank()) {
                return language.trim()
            }
            return "Bangla Dub"
        }

    // রেটিং পার্সার
    val rating: Double
        get() = when (rawRating) {
            is Number -> rawRating.toDouble()
            is String -> rawRating.toDoubleOrNull() ?: 8.5
            else -> 8.5
        }

    // ভিউ কাউন্ট পার্সার
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

    // ক্যাটাগরি পার্সার
    val categories: List<String>
        get() = when (rawCategories) {
            is List<*> -> rawCategories.filterIsInstance<String>().flatMap { it.split(",") }.map { it.trim() }.filter { it.isNotEmpty() }
            is String -> if (rawCategories.isNotBlank()) rawCategories.split(",").map { it.trim() }.filter { it.isNotEmpty() } else emptyList()
            else -> emptyList()
        }

    // মোট এপিসোড সংখ্যা
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
    val isShorts: Boolean get() = type.equals("shorts", ignoreCase = true) || categories.any { it.contains("Shorts", true) }
    val isAnime: Boolean get() = type.equals("anime", ignoreCase = true) || categories.any { it.contains("Anime", true) }
    val isMovie: Boolean get() = !isShorts && !isAnime && (type.equals("movie", true) || categories.any { it.contains("Movie", true) })
    val isDramaSeries: Boolean get() = !isShorts && !isAnime && !isMovie && (type.equals("series", true) || totalEpisodes > 1)

    val isBanglaDub: Boolean get() = dubBadge.contains("Bangla", true) || dubBadge.contains("Bengali", true)
    val isHindiDub: Boolean get() = dubBadge.contains("Hindi", true)

    val synopsis: String
        get() = description?.takeIf { it.isNotBlank() } ?: customSynopsis ?: metaDescription ?: "Watch full episodes in HD on PlayDramaFlix."
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

// 📥 মাল্টি-কোয়ালিটি ডাউনলোড অপশন DTO
@JsonClass(generateAdapter = true)
data class DownloadOptionDto(
    @Json(name = "quality") val quality: String = "",
    @Json(name = "size") val size: String = "",
    @Json(name = "url") val url: String = ""
)

@JsonClass(generateAdapter = true)
data class WatchDetailResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: Any? = null,
    @Json(name = "stream_url") val streamUrl: String? = null,
    @Json(name = "download_options") val downloadOptions: List<DownloadOptionDto>? = null,
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
    @Json(name = "server_name") val serverName: String? = "Server 1",
    @Json(name = "raw_url") val rawUrl: String? = null,
    @Json(name = "embed_url") val embedUrl: String? = null,
    @Json(name = "server_type") val serverType: String? = "stream",
    @Json(name = "quality") val quality: String? = "Streaming"
) {
    val id: String get() = rawId?.toString() ?: ""
    val name: String get() = serverName ?: "Server 1"
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
    @Json(name = "stream_url") val directStreamUrl: String? = null,
    @Json(name = "app_stream_url") val appStreamUrl: String? = null,
    @Json(name = "video_url") val videoUrl: String? = null,
    @Json(name = "web_player_url") val webPlayerUrl: String? = null,
    @Json(name = "embed_url") val embedUrl: String? = null,
    @Json(name = "download_url") val downloadUrl: String? = null,
    @Json(name = "is_locked") val isLocked: Boolean = false,
    @Json(name = "ads_count") val adsCount: Int = 0,
    @Json(name = "download_options") val downloadOptions: List<DownloadOptionDto>? = null
) {
    val episodeId: String get() = rawEpisodeId?.toString() ?: episodeNumber.toString()
    val displayTitle: String get() = rawTitle?.takeIf { it.isNotBlank() } ?: epTitle?.takeIf { it.isNotBlank() } ?: "Episode $episodeNumber"

    fun resolveR2StreamUrl(dramaSlug: String): String {
        return directStreamUrl?.takeIf { it.isNotBlank() }
            ?: appStreamUrl?.takeIf { it.isNotBlank() }
            ?: videoUrl?.takeIf { it.isNotBlank() }
            ?: downloadUrl?.takeIf { it.isNotBlank() }
            ?: "https://cdn.playdramaflix.com/streams/$dramaSlug/ep_$episodeNumber/master.m3u8"
    }

    fun resolveDownloadUrl(dramaSlug: String): String {
        return downloadOptions?.firstOrNull()?.url?.takeIf { it.isNotBlank() }
            ?: downloadUrl?.takeIf { it.isNotBlank() }
            ?: resolveR2StreamUrl(dramaSlug)
    }
}
