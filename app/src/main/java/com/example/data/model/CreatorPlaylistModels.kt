package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 📺 ক্রিয়েটর প্লেলিস্ট / মিনি-ড্রামা সিরিজ DTO
 * (৯:১৬ ভার্টিক্যাল পোস্টার ও ১৬:৯ ব্যানার সাপোর্ট সহ)
 */
@JsonClass(generateAdapter = true)
data class CreatorPlaylistDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "playlist_id") val rawPlaylistId: Int? = null,
    @Json(name = "page_id") val pageId: Int = 0,
    @Json(name = "title") val title: String = "",
    @Json(name = "description") val description: String? = null,
    @Json(name = "poster_url") val posterUrl: String? = null, // 👈 ৯:১৬ ভার্টিক্যাল পোস্টার
    @Json(name = "banner_url") val bannerUrl: String? = null, // 👈 ১৬:৯ হরিজন্টাল ব্যানার
    @Json(name = "cover_url") val coverUrl: String? = null,
    @Json(name = "total_episodes") val rawTotalEpisodes: Any? = 0,
    @Json(name = "total_views") val rawTotalViews: Any? = 0L,
    @Json(name = "created_at") val createdAt: String? = null
) {
    val effectiveId: Int get() = if (id > 0) id else (rawPlaylistId ?: 0)
    val effectivePoster: String? get() = posterUrl?.takeIf { it.isNotBlank() } ?: coverUrl
    val effectiveBanner: String? get() = bannerUrl?.takeIf { it.isNotBlank() } ?: coverUrl ?: posterUrl
    
    val totalEpisodes: Int
        get() = when (val total = rawTotalEpisodes) {
            is Number -> total.toInt()
            is String -> total.toIntOrNull() ?: 0
            else -> 0
        }

    val totalViews: Long
        get() = when (val v = rawTotalViews) {
            is Number -> v.toLong()
            is String -> v.toLongOrNull() ?: 0L
            else -> 0L
        }
}

/**
 * 🎬 নতুন সিরিজ তৈরির রেসপন্স মডেল
 */
@JsonClass(generateAdapter = true)
data class CreatePlaylistResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "playlist_id") val playlistId: Int? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "poster_url") val posterUrl: String? = null,
    @Json(name = "banner_url") val bannerUrl: String? = null,
    @Json(name = "message") val message: String? = null
)

/**
 * 📂 পেজের সব প্লেলিস্টের তালিকা রেসপন্স
 */
@JsonClass(generateAdapter = true)
data class PlaylistListResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: Int? = 200,
    @Json(name = "total") val total: Int? = 0,
    @Json(name = "playlists") val playlists: List<CreatorPlaylistDto> = emptyList(),
    @Json(name = "data") val data: List<CreatorPlaylistDto>? = null,
    @Json(name = "message") val message: String? = null
) {
    val effectivePlaylists: List<CreatorPlaylistDto>
        get() = playlists.ifEmpty { data ?: emptyList() }
}

/**
 * ▶️ প্লেলিস্টের সব পর্বের তালিকা রেসপন্স
 * (সার্ভার থেকে 'episodes' অথবা 'reels' যেকোনো নামে আসলে তা ক্র্যাশ ছাড়া রিড করবে)
 */
@JsonClass(generateAdapter = true)
data class PlaylistReelsResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: Int? = 200,
    @Json(name = "playlist_id") val playlistId: Int = 0,
    @Json(name = "title") val title: String? = null,
    @Json(name = "total") val total: Int = 0,
    @Json(name = "reels") val reels: List<UserReelDto> = emptyList(),
    @Json(name = "episodes") val episodes: List<UserReelDto>? = null,
    @Json(name = "data") val data: List<UserReelDto>? = null
) {
    val effectiveReels: List<UserReelDto>
        get() = episodes?.ifEmpty { reels } ?: (reels.ifEmpty { data ?: emptyList() })
}
