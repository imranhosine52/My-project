package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Locale

/**
 * 📊 সম্পূর্ণ পাবলিক লাইভ ও হিস্টোরিক্যাল অ্যানালিটিক্স মডেল
 */
@JsonClass(generateAdapter = true)
data class PublicLiveAnalyticsResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: Int? = 200,
    @Json(name = "timestamp") val timestamp: Long? = 0L,
    @Json(name = "live_online_count") val liveOnlineCount: Int = 1,
    @Json(name = "countries") val countries: List<LiveCountryStat> = emptyList(),
    @Json(name = "top_posts") val topPosts: List<LivePostStat> = emptyList(),
    @Json(name = "active_screens") val activeScreens: List<LiveScreenStat> = emptyList(),
    @Json(name = "top_brands") val topBrands: List<LiveBrandStat> = emptyList(),
    @Json(name = "growth") val growth: GrowthAnalyticsStats? = null,
    @Json(name = "traffic") val traffic: TrafficAnalyticsStats? = null,
    @Json(name = "engagement") val engagement: EngagementAnalyticsStats? = null
) {
    val formattedLiveCount: String
        get() = formatMetric(liveOnlineCount.toLong())

    companion object {
        fun formatMetric(value: Long): String {
            return when {
                value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
                value >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1_000.0)
                else -> value.toString()
            }
        }
    }
}

/**
 * 📈 অ্যাপ গ্রোথ ও রিটেনশন মেট্রিক্স (ইন্সটল ও আনইন্সটল)
 */
@JsonClass(generateAdapter = true)
data class GrowthAnalyticsStats(
    @Json(name = "lifetime_installs") val lifetimeInstalls: Long = 0L,
    @Json(name = "today_installs") val todayInstalls: Long = 0L,
    @Json(name = "lifetime_uninstalls") val lifetimeUninstalls: Long = 0L,
    @Json(name = "today_uninstalls") val todayUninstalls: Long = 0L,
    @Json(name = "active_users_base") val activeUsersBase: Long = 0L,
    @Json(name = "retention_rate") val retentionRate: Double = 100.0
) {
    val formattedLifetimeInstalls: String get() = PublicLiveAnalyticsResponse.formatMetric(lifetimeInstalls)
    val formattedTodayInstalls: String get() = PublicLiveAnalyticsResponse.formatMetric(todayInstalls)
    val formattedLifetimeUninstalls: String get() = PublicLiveAnalyticsResponse.formatMetric(lifetimeUninstalls)
    val formattedTodayUninstalls: String get() = PublicLiveAnalyticsResponse.formatMetric(todayUninstalls)
    val formattedActiveBase: String get() = PublicLiveAnalyticsResponse.formatMetric(activeUsersBase)
}

/**
 * 🚦 সামগ্রিক ট্র্যাফিক মেট্রিক্স
 */
@JsonClass(generateAdapter = true)
data class TrafficAnalyticsStats(
    @Json(name = "today_unique_visitors") val todayUniqueVisitors: Long = 0L,
    @Json(name = "today_page_views") val todayPageViews: Long = 0L,
    @Json(name = "today_video_views") val todayVideoViews: Long = 0L,
    @Json(name = "lifetime_visitors") val lifetimeVisitors: Long = 0L,
    @Json(name = "lifetime_page_views") val lifetimePageViews: Long = 0L,
    @Json(name = "lifetime_video_views") val lifetimeVideoViews: Long = 0L
) {
    val formattedTodayVisitors: String get() = PublicLiveAnalyticsResponse.formatMetric(todayUniqueVisitors)
    val formattedTodayPageViews: String get() = PublicLiveAnalyticsResponse.formatMetric(todayPageViews)
    val formattedTodayVideoViews: String get() = PublicLiveAnalyticsResponse.formatMetric(todayVideoViews)
    val formattedLifetimeVisitors: String get() = PublicLiveAnalyticsResponse.formatMetric(lifetimeVisitors)
    val formattedLifetimeViews: String get() = PublicLiveAnalyticsResponse.formatMetric(lifetimePageViews)
    val formattedLifetimeVideoViews: String get() = PublicLiveAnalyticsResponse.formatMetric(lifetimeVideoViews)
}

/**
 * 💬 মোট এনগেজমেন্ট মেট্রিক্স
 */
@JsonClass(generateAdapter = true)
data class EngagementAnalyticsStats(
    @Json(name = "total_likes") val totalLikes: Long = 0L,
    @Json(name = "total_comments") val totalComments: Long = 0L,
    @Json(name = "total_shares") val totalShares: Long = 0L
) {
    val formattedLikes: String get() = PublicLiveAnalyticsResponse.formatMetric(totalLikes)
    val formattedComments: String get() = PublicLiveAnalyticsResponse.formatMetric(totalComments)
    val formattedShares: String get() = PublicLiveAnalyticsResponse.formatMetric(totalShares)
}

/**
 * 📱 ডিভাইস ব্র্যান্ড মডেল
 */
@JsonClass(generateAdapter = true)
data class LiveBrandStat(
    @Json(name = "name") val name: String = "Unknown",
    @Json(name = "count") val count: Int = 0,
    @Json(name = "percentage") val percentage: Double = 0.0
)

/**
 * 🌍 দেশভিত্তিক লাইভ ভিজিটর
 */
@JsonClass(generateAdapter = true)
data class LiveCountryStat(
    @Json(name = "code") val code: String = "BD",
    @Json(name = "name") val name: String = "Bangladesh",
    @Json(name = "flag") val flag: String = "🇧🇩",
    @Json(name = "count") val count: Int = 0,
    @Json(name = "percentage") val percentage: Double = 0.0
) {
    val formattedCount: String get() = PublicLiveAnalyticsResponse.formatMetric(count.toLong())
}

/**
 * 🔥 টপ ওয়াচিং ড্রামা
 */
@JsonClass(generateAdapter = true)
data class LivePostStat(
    @Json(name = "title") val title: String = "",
    @Json(name = "slug") val slug: String = "",
    @Json(name = "watching_count") val watchingCount: Int = 0
) {
    val formattedWatchingCount: String get() = PublicLiveAnalyticsResponse.formatMetric(watchingCount.toLong())
}

/**
 * 📍 স্ক্রিন ট্র্যাকিং
 */
@JsonClass(generateAdapter = true)
data class LiveScreenStat(
    @Json(name = "screen") val screen: String = "",
    @Json(name = "count") val count: Int = 0,
    @Json(name = "percentage") val percentage: Double = 0.0
) {
    val cleanScreenName: String
        get() = when {
            screen.contains("Short", ignoreCase = true) -> "Short TV / Reels"
            screen.contains("Watching", ignoreCase = true) -> "Watching Drama Series"
            screen.contains("Chat", ignoreCase = true) -> "Community Live Chat"
            screen.contains("Home", ignoreCase = true) -> "Home Feed"
            screen.contains("Search", ignoreCase = true) -> "Searching Dramas"
            else -> screen
        }
}
