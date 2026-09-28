package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Locale

/**
 * রিলস ভিডিওর কোয়ালিটি এনাম
 */
enum class ReelVideoQuality(val label: String, val fileSuffix: String) {
    QUALITY_720P("720p HD", "video_720p.mp4"),
    QUALITY_480P("480p SD", "video_480p.mp4"),
    QUALITY_360P("360p Data Saver", "video_360p.mp4");

    companion object {
        fun fromSuffix(url: String): ReelVideoQuality {
            return when {
                url.contains("video_360p.mp4") -> QUALITY_360P
                url.contains("video_480p.mp4") -> QUALITY_480P
                else -> QUALITY_720P
            }
        }
    }
}

/**
 * একক রিলস ভিডিও মডেল
 */
@JsonClass(generateAdapter = true)
data class UserReelDto(
    @Json(name = "id") val id: Int,
    @Json(name = "page_id") val pageId: Int,
    @Json(name = "page_name") val pageName: String = "Creator",
    @Json(name = "handle") val handle: String = "@creator",
    @Json(name = "page_avatar") val pageAvatar: String? = null,
    @Json(name = "title") val title: String? = "",
    @Json(name = "description") val description: String? = null,
    @Json(name = "video_url") val rawVideoUrl: String = "",
    @Json(name = "thumb_url") val thumbUrl: String? = null,
    @Json(name = "duration_sec") val durationSec: Int = 15,
    @Json(name = "views_count") val rawViewsCount: Long? = 0L,
    @Json(name = "likes_count") val rawLikesCount: Long? = 0L,
    @Json(name = "shares_count") val rawSharesCount: Int? = 0,
    @Json(name = "is_liked") val isLiked: Boolean = false,
    @Json(name = "is_following") val isFollowing: Boolean = false
) {
    val viewsCount: Long get() = rawViewsCount ?: 0L
    val likesCount: Long get() = rawLikesCount ?: 0L
    val sharesCount: Int get() = rawSharesCount ?: 0

    val displayHandle: String 
        get() = if (handle.startsWith("@")) handle else "@$handle"

    /**
     * 🎯 MULTI-QUALITY SWITCHER LOGIC
     * সার্ভার URL থেকে ভিডিও কোয়ালিটি ডাইনামিক রূপান্তর
     */
    fun getVideoUrlForQuality(quality: ReelVideoQuality): String {
        if (rawVideoUrl.isBlank()) return ""
        return when (quality) {
            ReelVideoQuality.QUALITY_720P -> {
                rawVideoUrl.replace("video_480p.mp4", "video_720p.mp4")
                    .replace("video_360p.mp4", "video_720p.mp4")
            }
            ReelVideoQuality.QUALITY_480P -> {
                rawVideoUrl.replace("video_720p.mp4", "video_480p.mp4")
                    .replace("video_360p.mp4", "video_480p.mp4")
            }
            ReelVideoQuality.QUALITY_360P -> {
                rawVideoUrl.replace("video_720p.mp4", "video_360p.mp4")
                    .replace("video_480p.mp4", "video_360p.mp4")
            }
        }
    }

    val formattedLikes: String
        get() = when {
            likesCount >= 1_000_000 -> String.format(Locale.US, "%.1fM", likesCount / 1_000_000.0)
            likesCount >= 1_000 -> String.format(Locale.US, "%.1fK", likesCount / 1_000.0)
            else -> likesCount.toString()
        }

    val formattedViews: String
        get() = when {
            viewsCount >= 1_000_000 -> String.format(Locale.US, "%.1fM", viewsCount / 1_000_000.0)
            viewsCount >= 1_000 -> String.format(Locale.US, "%.1fK", viewsCount / 1_000.0)
            else -> viewsCount.toString()
        }
}

/**
 * রিলস ফিড রেসপন্স (VPS 1)
 */
@JsonClass(generateAdapter = true)
data class ReelsFeedResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "total") val total: Int = 0,
    @Json(name = "reels") val reels: List<UserReelDto> = emptyList()
)

/**
 * রিলস আপলোড রেসপন্স (VPS 2)
 */
@JsonClass(generateAdapter = true)
data class ReelUploadResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: Int = 200,
    @Json(name = "reel_id") val reelId: Int? = null,
    @Json(name = "message") val message: String? = null
)

/**
 * লাইক/ভিউ/শেয়ার ইন্টারঅ্যাকশন রেসপন্স
 */
@JsonClass(generateAdapter = true)
data class ReelInteractionResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: Int = 200,
    @Json(name = "is_liked") val isLiked: Boolean? = null,
    @Json(name = "message") val message: String? = null
)
