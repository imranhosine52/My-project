package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CreatorPlaylistDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "playlist_id") val rawPlaylistId: Int? = null,
    @Json(name = "page_id") val pageId: Int = 0,
    @Json(name = "title") val title: String = "",
    @Json(name = "description") val description: String? = null,
    @Json(name = "poster_url") val posterUrl: String? = null, // 👈 9:16 Vertical Poster
    @Json(name = "banner_url") val bannerUrl: String? = null, // 👈 16:9 Horizontal Banner
    @Json(name = "cover_url") val coverUrl: String? = null,
    @Json(name = "total_episodes") val rawTotalEpisodes: Any? = 0,
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
}

@JsonClass(generateAdapter = true)
data class CreatePlaylistResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "playlist_id") val playlistId: Int? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "poster_url") val posterUrl: String? = null,
    @Json(name = "banner_url") val bannerUrl: String? = null,
    @Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class PlaylistListResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: Int? = 200,
    @Json(name = "total") val total: Int? = 0,
    @Json(name = "playlists") val playlists: List<CreatorPlaylistDto> = emptyList(),
    @Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class PlaylistReelsResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: Int? = 200,
    @Json(name = "playlist_id") val playlistId: Int = 0,
    @Json(name = "title") val title: String? = null,
    @Json(name = "total") val total: Int = 0,
    @Json(name = "reels") val reels: List<UserReelDto> = emptyList()
)
