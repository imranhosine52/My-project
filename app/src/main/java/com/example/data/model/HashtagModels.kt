package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Locale

/**
 * 🏷️ সার্ভার থেকে রিয়েল ট্রেন্ডিং হ্যাশট্যাগ আইটেম DTO
 * (ডামি সংখ্যা রোধে সরাসরি সার্ভার থেকে লাইভ ভিউজ ও রিলস কাউন্ট রিড করে)
 */
@JsonClass(generateAdapter = true)
data class TrendingHashtagDto(
    @Json(name = "id") val rawId: Any? = null,
    @Json(name = "tag") val rawTag: String? = null,
    @Json(name = "name") val rawName: String? = null,
    @Json(name = "views") val rawViews: Any? = null,
    @Json(name = "total_views") val rawTotalViews: Any? = null,
    @Json(name = "formatted_views") val rawFormattedViews: String? = null,
    @Json(name = "total_reels") val rawTotalReels: Any? = null,
    @Json(name = "reels_count") val rawReelsCount: Any? = null
) {
    val displayTag: String
        get() {
            val t = (rawTag ?: rawName)?.trim().orEmpty()
            return if (t.startsWith("#")) t else if (t.isNotBlank()) "#$t" else "#dramaflix"
        }

    val totalViews: Long
        get() = when (val v = rawViews ?: rawTotalViews) {
            is Number -> v.toLong()
            is String -> {
                val clean = v.trim().uppercase().replace("VIEWS", "").replace("VIEW", "").trim()
                when {
                    clean.endsWith("B") -> ((clean.dropLast(1).toDoubleOrNull() ?: 0.0) * 1_000_000_000).toLong()
                    clean.endsWith("M") -> ((clean.dropLast(1).toDoubleOrNull() ?: 0.0) * 1_000_000).toLong()
                    clean.endsWith("K") -> ((clean.dropLast(1).toDoubleOrNull() ?: 0.0) * 1_000).toLong()
                    else -> clean.toLongOrNull() ?: 0L
                }
            }
            else -> 0L
        }

    val totalReels: Int
        get() = when (val r = rawTotalReels ?: rawReelsCount) {
            is Number -> r.toInt()
            is String -> r.toIntOrNull() ?: 0
            else -> 0
        }

    // 🎯 ১০০% সত্য সার্ভার ফরম্যাটেড ভিউজ (যেমন: "2.4K views", "435 views")
    val displayViews: String
        get() {
            if (!rawFormattedViews.isNullOrBlank()) return rawFormattedViews.trim()
            val v = totalViews
            return when {
                v >= 1_000_000_000 -> String.format(Locale.US, "%.1fB views", v / 1_000_000_000.0)
                v >= 1_000_000 -> String.format(Locale.US, "%.1fM views", v / 1_000_000.0)
                v >= 1_000 -> String.format(Locale.US, "%.1fK views", v / 1_000.0)
                v > 0 -> "$v views"
                else -> "0 views"
            }
        }

    val displayReelsBadge: String
        get() = if (totalReels > 0) "$totalReels videos" else ""
}

/**
 * 📡 ট্রেন্ডিং হ্যাশট্যাগ লিস্ট এপিআই রেসপন্স
 * Endpoint: GET /tiktok-manager.php?action=get_trending_hashtags
 */
@JsonClass(generateAdapter = true)
data class TrendingHashtagsResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "total") val total: Int? = 0,
    @Json(name = "hashtags") val hashtags: List<TrendingHashtagDto> = emptyList(),
    @Json(name = "data") val data: List<TrendingHashtagDto>? = null,
    @Json(name = "message") val message: String? = null
) {
    val effectiveHashtags: List<TrendingHashtagDto>
        get() = hashtags.ifEmpty { data ?: emptyList() }
}

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
