package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Locale

/**
 * 📊 পাবলিক লাইভ অ্যানালিটিক্স রেসপন্স মডেল
 * সার্ভারের Redis ইঞ্জিন থেকে প্রতি ২-৩ সেকেন্ড পরপর ক্যাশড লাইভ স্ন্যাপশট ধারণ করে।
 */
@JsonClass(generateAdapter = true)
data class PublicLiveAnalyticsResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: Int? = 200,
    @Json(name = "timestamp") val timestamp: Long? = 0L,
    @Json(name = "live_online_count") val liveOnlineCount: Int = 1,
    @Json(name = "countries") val countries: List<LiveCountryStat> = emptyList(),
    @Json(name = "top_posts") val topPosts: List<LivePostStat> = emptyList(),
    @Json(name = "active_screens") val activeScreens: List<LiveScreenStat> = emptyList()
) {
    /**
     * হেডারের জন্য সুন্দর ফরম্যাটেড লাইভ কাউন্ট
     * যেমন: 1250 হলে 1.2K, 1250000 হলে 1.2M, অন্যথায় সাধারণ সংখ্যা
     */
    val formattedLiveCount: String
        get() = when {
            liveOnlineCount >= 1_000_000 -> String.format(Locale.US, "%.1fM", liveOnlineCount / 1_000_000.0)
            liveOnlineCount >= 1_000 -> String.format(Locale.US, "%.1fK", liveOnlineCount / 1_000.0)
            else -> liveOnlineCount.toString()
        }
}

/**
 * 🌍 দেশভিত্তিক লাইভ ভিজিটর মডেল (ফ্ল্যাগ ও পারসেন্টেজ সহ)
 */
@JsonClass(generateAdapter = true)
data class LiveCountryStat(
    @Json(name = "code") val code: String = "BD",
    @Json(name = "name") val name: String = "Bangladesh",
    @Json(name = "flag") val flag: String = "🇧🇩",
    @Json(name = "count") val count: Int = 0,
    @Json(name = "percentage") val percentage: Double = 0.0
) {
    val formattedCount: String
        get() = when {
            count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
            count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
            else -> count.toString()
        }
}

/**
 * 🔥 বর্তমানে কোন ড্রামা/পোস্টে কতজন ইউজার লাইভ দেখছেন
 */
@JsonClass(generateAdapter = true)
data class LivePostStat(
    @Json(name = "title") val title: String = "",
    @Json(name = "slug") val slug: String = "",
    @Json(name = "watching_count") val watchingCount: Int = 0
) {
    val formattedWatchingCount: String
        get() = when {
            watchingCount >= 1_000_000 -> String.format(Locale.US, "%.1fM", watchingCount / 1_000_000.0)
            watchingCount >= 1_000 -> String.format(Locale.US, "%.1fK", watchingCount / 1_000.0)
            else -> watchingCount.toString()
        }
}

/**
 * 📍 ইউজাররা অ্যাপের কোন স্ক্রিনে বেশি সক্রিয় (যেমন: Shorts, Home, Chat)
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
