package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 📺 একক প্লেলিস্ট বা সিরিজ ডেটা মডেল
 */
@JsonClass(generateAdapter = true)
data class CreatorPlaylistDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "playlist_id") val rawPlaylistId: Int? = null,
    @Json(name = "page_id") val pageId: Int = 0,
    @Json(name = "title") val title: String = "",
    @Json(name = "description") val description: String? = null,
    @Json(name = "cover_url") val coverUrl: String? = null,
    @Json(name = "total_episodes") val rawTotalEpisodes: Any? = 0,
    @Json(name = "created_at") val createdAt: String? = null
) {
    val effectiveId: Int get() = if (id > 0) id else (rawPlaylistId ?: 0)
    
    val totalEpisodes: Int
        get() = when (val total = rawTotalEpisodes) {
            is Number -> total.toInt()
            is String -> total.toIntOrNull() ?: 0
            else -> 0
        }
}

/**
 * ➕ নতুন সিরিজ তৈরি করার সার্ভার রেসপন্স মডেল
 */
@JsonClass(generateAdapter = true)
data class CreatePlaylistResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: Int? = 200,
    @Json(name = "playlist_id") val playlistId: Int? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "message") val message: String? = null
)

/**
 * 📋 ক্রিয়েটরের সব প্লেলিস্ট লিস্ট রেসপন্স মডেল
 */
@JsonClass(generateAdapter = true)
data class PlaylistListResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: Int? = 200,
    @Json(name = "total") val total: Int? = 0,
    @Json(name = "playlists") val playlists: List<CreatorPlaylistDto> = emptyList(),
    @Json(name = "message") val message: String? = null
)

/**
 * 🎬 একটি নির্দিষ্ট সিরিজের সমস্ত পর্ব (Reels) রেসপন্স মডেল
 */
@JsonClass(generateAdapter = true)
data class PlaylistReelsResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: Int? = 200,
    @Json(name = "playlist_id") val playlistId: Int = 0,
    @Json(name = "title") val title: String? = null,
    @Json(name = "total") val total: Int? = 0,
    @Json(name = "reels") val reels: List<UserReelDto> = emptyList()
)
