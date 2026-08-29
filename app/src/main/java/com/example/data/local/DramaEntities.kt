package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val id: String, // format: slug_epNumber
    val contentSlug: String,
    val contentTitle: String,
    val posterUrl: String?,
    val episodeNumber: Int,
    val episodeTitle: String,
    val seasonNumber: Int = 1,
    val progressMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val progressPercentage: Float = 0f,
    val dubBadge: String = "Bangla",
    val lastWatchedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "watchlist")
data class WatchlistEntity(
    @PrimaryKey val id: String, // content slug
    val title: String,
    val posterUrl: String?,
    val dubBadge: String,
    val rating: Double,
    val category: String,
    val totalEpisodes: Int,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "drama_stats")
data class DramaStatsEntity(
    @PrimaryKey val slug: String,
    val likesCount: Int = 0,
    val isLiked: Boolean = false,
    val viewsCount: Long = 0L,
    val sharesCount: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)
