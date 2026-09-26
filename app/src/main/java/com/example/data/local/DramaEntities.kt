package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 📺 ১. ওয়াচ হিস্ট্রি টেবিল (আগের মতোই অপরিবর্তিত)
 */
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

/**
 * 🔖 ২. বুকমার্ক ও ওয়াচলিস্ট টেবিল (অপরিবর্তিত)
 */
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

/**
 * 📊 ৩. লাইক ও ভিউজ স্ট্যাটাস টেবিল (অপরিবর্তিত)
 */
@Entity(tableName = "drama_stats")
data class DramaStatsEntity(
    @PrimaryKey val slug: String,
    val likesCount: Int = 0,
    val isLiked: Boolean = false,
    val viewsCount: Long = 0L,
    val sharesCount: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)

// =============================================================================
// 🚀 নতুন ক্যাশিং টেবিল (জিরো-সার্ভার রিকোয়েস্ট ও অফলাইন-ফার্স্ট আর্কিটেকচার)
// =============================================================================

/**
 * 🗄️ ৪. হোম ফিড ক্যাশ টেবিল:
 * অ্যাপের হোম স্ক্রিনের সমস্ত ড্রামা ও ক্যাটাগরি ফোনে সেভ রাখবে।
 * দিনে মাত্র কয়েকবার সিঙ্ক হবে, বারবার সার্ভারে কল যাবে না।
 */
@Entity(tableName = "cached_home_feed")
data class CachedFeedEntity(
    @PrimaryKey val feedKey: String = "primary_home_feed",
    val jsonPayload: String, // সম্পূর্ণ ড্রামা লিস্টের JSON ডাটা
    val cachedTimestamp: Long = System.currentTimeMillis()
)

/**
 * 🎬 ৫. প্লেয়ার ও সমস্ত পর্বের স্ট্রিমিং লিঙ্ক ক্যাশ টেবিল:
 * প্রতিটি ড্রামার সমস্ত পর্ব, স্ট্রিমিং ইউআরএল (.m3u8), ডাউনলোড অপশন এবং সাইজ 
 * পার্মানেন্টলি সেভ থাকবে। ইউজার ক্লিক করলেই ০ সেকেন্ডে ভিডিও চালু হবে।
 */
@Entity(tableName = "cached_watch_details")
data class CachedWatchDetailEntity(
    @PrimaryKey val slug: String, // ড্রামার ইউনিক স্লাগ
    val jsonPayload: String,      // সমস্ত পর্ব ও ভিডিও ইউআরএল সমৃদ্ধ সম্পূর্ণ JSON
    val cachedTimestamp: Long = System.currentTimeMillis()
)
