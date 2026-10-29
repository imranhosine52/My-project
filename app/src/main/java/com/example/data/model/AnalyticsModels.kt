package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Locale

/**
 * 📊 সম্পূর্ণ পাবলিক লাইভ ও হিস্টোরিক্যাল অ্যানালিটিক্স রেসপন্স মডেল (YouTube Studio Architecture)
 */
@JsonClass(generateAdapter = true)
data class PublicLiveAnalyticsResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: Int? = 200,
    @Json(name = "timestamp") val timestamp: Long? = 0L,
    @Json(name = "live_online_count") val liveOnlineCount: Int = 1,
    @Json(name = "overview") val overview: OverviewAnalyticsDto? = null,
    @Json(name = "content_analytics") val contentAnalytics: ContentAnalyticsDto? = null,
    @Json(name = "audience_analytics") val audienceAnalytics: AudienceAnalyticsDto? = null,
    @Json(name = "growth") val growth: GrowthAnalyticsStats? = null
) {
    val formattedLiveCount: String
        get() = formatMetric(liveOnlineCount.toLong())

    // 🎯 ফলব্যাক হেল্পার প্রোপার্টিজ (যাতে অন্যান্য স্ক্রিনের সাথে কম্প্যাটিবিলিটি ঠিক থাকে)
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
 * ⏱️ Overview Tab: Views, Watch Time (Hours) ও Realtime Bar ডেটা
 */
@JsonClass(generateAdapter = true)
data class OverviewAnalyticsDto(
    @Json(name = "views_count") val viewsCount: Long = 0L,
    @Json(name = "watch_time_hours") val watchTimeHours: Double = 0.0,
    @Json(name = "lifetime_views") val lifetimeViews: Long = 0L,
    @Json(name = "lifetime_hours") val lifetimeHours: Double = 0.0,
    @Json(name = "views_sparkline") val viewsSparkline: List<Float> = emptyList(),
    @Json(name = "hours_sparkline") val hoursSparkline: List<Float> = emptyList(),
    @Json(name = "realtime_48h_bars") val realtime48hBars: List<Int> = emptyList()
) {
    val formattedViews: String get() = PublicLiveAnalyticsResponse.formatMetric(viewsCount)
    val formattedLifetimeViews: String get() = PublicLiveAnalyticsResponse.formatMetric(lifetimeViews)
    val formattedWatchTime: String
        get() = if (watchTimeHours >= 1_000) String.format(Locale.US, "%.1fK", watchTimeHours / 1000.0)
                else String.format(Locale.US, "%.1f", watchTimeHours)
}

/**
 * 🎬 Content Tab: শর্টস সোয়াইপ, সার্চ টার্ম ও টপ শোজ
 */
@JsonClass(generateAdapter = true)
data class ContentAnalyticsDto(
    @Json(name = "shorts_engagement") val shortsEngagement: ShortsEngagementDto? = ShortsEngagementDto(),
    @Json(name = "top_search_terms") val topSearchTerms: List<SearchTermStat> = emptyList(),
    @Json(name = "top_dramas") val topDramas: List<LivePostStat> = emptyList()
)

/**
 * 📱 শর্টস সোয়াইপ বনাম সম্পূর্ণ দেখা
 */
@JsonClass(generateAdapter = true)
data class ShortsEngagementDto(
    @Json(name = "stayed_percentage") val stayedPercentage: Double = 82.4,
    @Json(name = "swiped_percentage") val swipedPercentage: Double = 17.6
)

/**
 * 🔍 সার্চ কি-ওয়ার্ড মডেল
 */
@JsonClass(generateAdapter = true)
data class SearchTermStat(
    @Json(name = "term") val term: String = "",
    @Json(name = "count") val count: Int = 0
) {
    val formattedCount: String get() = PublicLiveAnalyticsResponse.formatMetric(count.toLong())
}

/**
 * 👥 Audience Tab: Weekly Heatmap, ডেমোগ্রাফিক্স ও কান্ট্রি
 */
@JsonClass(generateAdapter = true)
data class AudienceAnalyticsDto(
    @Json(name = "active_heatmap_matrix") val activeHeatmapMatrix: List<List<Float>> = emptyList(), // ৭টি কলাম (বার) এবং ২৪টি ঘণ্টা (রো)
    @Json(name = "new_viewers_pct") val newViewersPct: Double = 91.1,
    @Json(name = "casual_viewers_pct") val casualViewersPct: Double = 8.9,
    @Json(name = "vip_viewers_pct") val vipViewersPct: Double = 5.2,
    @Json(name = "device_mobile_pct") val deviceMobilePct: Double = 89.4,
    @Json(name = "device_tv_pct") val deviceTvPct: Double = 8.6,
    @Json(name = "device_tablet_pct") val deviceTabletPct: Double = 2.0,
    @Json(name = "top_brands") val topBrands: List<LiveBrandStat> = emptyList(),
    @Json(name = "countries") val countries: List<LiveCountryStat> = emptyList()
)

/**
 * 📈 Growth Tab: অ্যাপ ইন্সটল ও রিটেনশন
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
