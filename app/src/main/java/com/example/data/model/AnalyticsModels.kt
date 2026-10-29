package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Locale

/**
 * 📊 সম্পূর্ণ পাবলিক লাইভ ও হিস্টোরিক্যাল অ্যানালিটিক্স রেসপন্স মডেল
 */
@JsonClass(generateAdapter = true)
data class PublicLiveAnalyticsResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: Int? = 200,
    @Json(name = "timestamp") val timestamp: Long? = 0L,
    @Json(name = "live_online_count") val liveOnlineCount: Int = 1,
    @Json(name = "active_screens") val activeScreens: List<LiveScreenStat> = emptyList(),
    @Json(name = "overview") val overview: OverviewAnalyticsDto? = null,
    @Json(name = "content_analytics") val contentAnalytics: ContentAnalyticsDto? = null,
    @Json(name = "audience_analytics") val audienceAnalytics: AudienceAnalyticsDto? = null,
    @Json(name = "growth") val growth: GrowthAnalyticsStats? = null
) {
    val formattedLiveCount: String
        get() = formatMetric(liveOnlineCount.toLong())

    // 🎯 ফলব্যাক হেল্পার প্রোপার্টিজ
    val countries: List<LiveCountryStat> get() = audienceAnalytics?.countries ?: emptyList()
    val topPosts: List<LivePostStat> get() = contentAnalytics?.topDramas ?: emptyList()
    val topBrands: List<LiveBrandStat> get() = audienceAnalytics?.topBrands ?: emptyList()

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
 * ⏱️ Overview Tab: Traffic Graph, Watch Time & Traffic Summary Table
 */
@JsonClass(generateAdapter = true)
data class OverviewAnalyticsDto(
    @Json(name = "views_count") val viewsCount: Long = 0L,
    @Json(name = "watch_time_hours") val watchTimeHours: Double = 0.0,
    @Json(name = "last_7_days_visitors") val last7DaysVisitors: Long = 0L,
    @Json(name = "views_sparkline") val viewsSparkline: List<Float> = emptyList(),
    @Json(name = "hours_sparkline") val hoursSparkline: List<Float> = emptyList(),
    @Json(name = "traffic_summary") val trafficSummary: List<TrafficSummaryItem> = emptyList()
) {
    val formattedViews: String get() = PublicLiveAnalyticsResponse.formatMetric(viewsCount)
    val formatted7DaysVisitors: String get() = PublicLiveAnalyticsResponse.formatMetric(last7DaysVisitors)
    val formattedWatchTime: String
        get() = if (watchTimeHours >= 1_000) String.format(Locale.US, "%.1fK", watchTimeHours / 1000.0)
                else String.format(Locale.US, "%.1f", watchTimeHours)
}

/**
 * 📋 Traffic Summary টেবিল আইটেম
 */
@JsonClass(generateAdapter = true)
data class TrafficSummaryItem(
    @Json(name = "timeframe") val timeframe: String = "",
    @Json(name = "visitors") val visitors: Long = 0L,
    @Json(name = "views") val views: Long = 0L
) {
    val formattedVisitors: String get() = String.format(Locale.US, "%,d", visitors)
    val formattedViews: String get() = String.format(Locale.US, "%,d", views)
}

/**
 * 🎬 Content Tab: রিয়েল-টাইম পোস্ট মডেল
 */
@JsonClass(generateAdapter = true)
data class ContentAnalyticsDto(
    @Json(name = "realtime_posts") val realtimePosts: List<RealtimePostItem> = emptyList(),
    @Json(name = "top_dramas") val topDramas: List<LivePostStat> = emptyList()
)

/**
 * 🎥 রিয়েল-টাইম একক পোস্ট আইটেম
 */
@JsonClass(generateAdapter = true)
data class RealtimePostItem(
    @Json(name = "id") val id: String = "",
    @Json(name = "title") val title: String = "",
    @Json(name = "slug") val slug: String = "",
    @Json(name = "poster_url") val posterUrl: String? = null,
    @Json(name = "views") val views: Long = 0L,
    @Json(name = "progress_ratio") val progressRatio: Float = 0.5f
) {
    val formattedViews: String get() = PublicLiveAnalyticsResponse.formatMetric(views)
}

/**
 * 👥 Audience Tab: ডেমোগ্রাফিক্স
 */
@JsonClass(generateAdapter = true)
data class AudienceAnalyticsDto(
    @Json(name = "active_heatmap_matrix") val activeHeatmapMatrix: List<List<Float>> = emptyList(),
    @Json(name = "top_brands") val topBrands: List<LiveBrandStat> = emptyList(),
    @Json(name = "countries") val countries: List<LiveCountryStat> = emptyList()
)

/**
 * 📈 Growth Tab: অ্যাপ ইন্সটল, আনইন্সটল ও সামারি টেবিল
 */
@JsonClass(generateAdapter = true)
data class GrowthAnalyticsStats(
    @Json(name = "lifetime_installs") val lifetimeInstalls: Long = 0L,
    @Json(name = "today_installs") val todayInstalls: Long = 0L,
    @Json(name = "lifetime_uninstalls") val lifetimeUninstalls: Long = 0L,
    @Json(name = "today_uninstalls") val todayUninstalls: Long = 0L,
    @Json(name = "active_users_base") val activeUsersBase: Long = 0L,
    @Json(name = "retention_rate") val retentionRate: Double = 100.0,
    @Json(name = "growth_summary") val growthSummary: List<GrowthSummaryItem> = emptyList() // 👈 নতুন সামারি বোর্ড
) {
    val formattedLifetimeInstalls: String get() = PublicLiveAnalyticsResponse.formatMetric(lifetimeInstalls)
    val formattedTodayInstalls: String get() = PublicLiveAnalyticsResponse.formatMetric(todayInstalls)
    val formattedLifetimeUninstalls: String get() = PublicLiveAnalyticsResponse.formatMetric(lifetimeUninstalls)
    val formattedTodayUninstalls: String get() = PublicLiveAnalyticsResponse.formatMetric(todayUninstalls)
    val formattedActiveBase: String get() = PublicLiveAnalyticsResponse.formatMetric(activeUsersBase)
}

/**
 * 📋 নতুন Growth Summary টেবিল আইটেম (Today, Yesterday, 1M, 3M, 1Y, Total)
 */
@JsonClass(generateAdapter = true)
data class GrowthSummaryItem(
    @Json(name = "timeframe") val timeframe: String = "",
    @Json(name = "installs") val installs: Long = 0L,
    @Json(name = "uninstalls") val uninstalls: Long = 0L
) {
    val formattedInstalls: String get() = String.format(Locale.US, "%,d", installs)
    val formattedUninstalls: String get() = String.format(Locale.US, "%,d", uninstalls)
}

@JsonClass(generateAdapter = true)
data class LiveBrandStat(
    @Json(name = "name") val name: String = "Unknown",
    @Json(name = "count") val count: Int = 0,
    @Json(name = "percentage") val percentage: Double = 0.0
)

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

@JsonClass(generateAdapter = true)
data class LivePostStat(
    @Json(name = "title") val title: String = "",
    @Json(name = "slug") val slug: String = "",
    @Json(name = "watching_count") val watchingCount: Int = 0
) {
    val formattedWatchingCount: String get() = PublicLiveAnalyticsResponse.formatMetric(watchingCount.toLong())
}

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
