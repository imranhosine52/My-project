package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Locale

/**
 * 🏷️ হ্যাশট্যাগ এক্সপ্লোরার রেসপন্স মডেল
 * Endpoint: GET /tiktok-manager.php?action=get_hashtag_reels&tag={tag_name}&page=1
 */
@JsonClass(generateAdapter = true)
data class HashtagDetailResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "hashtag") val rawHashtag: String? = "",
    @Json(name = "total_views") val rawTotalViews: Any? = 0L,
    @Json(name = "total_reels") val rawTotalReels: Any? = 0,
    @Json(name = "reels") val reels: List<UserReelDto> = emptyList(),
    @Json(name = "message") val message: String? = null
) {
    val displayTag: String
        get() {
            val tag = rawHashtag?.trim().orEmpty()
            return if (tag.startsWith("#")) tag else if (tag.isNotBlank()) "#$tag" else "#Trending"
        }

    val totalViews: Long
        get() = when (val v = rawTotalViews) {
            is Number -> v.toLong()
            is String -> v.toLongOrNull() ?: 0L
            else -> 0L
        }

    val totalReels: Int
        get() = when (val r = rawTotalReels) {
            is Number -> r.toInt()
            is String -> r.toIntOrNull() ?: reels.size
            else -> reels.size
        }

    val formattedViews: String
        get() = when {
            totalViews >= 1_000_000_000 -> String.format(Locale.US, "%.1fB", totalViews / 1_000_000_000.0)
            totalViews >= 1_000_000 -> String.format(Locale.US, "%.1fM", totalViews / 1_000_000.0)
            totalViews >= 1_000 -> String.format(Locale.US, "%.1fK", totalViews / 1_000.0)
            else -> totalViews.toString()
        }

    val formattedReelsCount: String
        get() = when {
            totalReels >= 1_000_000 -> String.format(Locale.US, "%.1fM", totalReels / 1_000_000.0)
            totalReels >= 1_000 -> String.format(Locale.US, "%.1fK", totalReels / 1_000.0)
            else -> totalReels.toString()
        }

    val displayStatsSubtitle: String
        get() = "👁️ $formattedViews Views • $formattedReelsCount Videos"
}

/**
 * 🔍 ক্যাটাগরি ও কি-ওয়ার্ড ভিত্তিক সার্চ রেসপন্স মডেল
 * Endpoint: GET /tiktok-manager.php?action=search_reels&q={search_text}&category={category_name}&page=1
 */
@JsonClass(generateAdapter = true)
data class SearchReelsResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "total") val total: Int = 0,
    @Json(name = "reels") val reels: List<UserReelDto> = emptyList(),
    @Json(name = "message") val message: String? = null
)

/**
 * 🌟 সার্চ স্ক্রিনের ট্রেন্ডিং হ্যাশট্যাগ চিপ মডেল
 */
data class TrendingHashtagChip(
    val tag: String,
    val viewsLabel: String
)
