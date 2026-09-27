package com.example.data.model

import androidx.compose.ui.graphics.Color
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 🎬 একক কাস্টম ভিডিও বিজ্ঞাপনের ডেটা মডেল
 */
@JsonClass(generateAdapter = true)
data class CustomVideoAdDto(
    @Json(name = "id") val id: Int = 1,
    @Json(name = "title") val title: String = "Sponsored Ad",
    @Json(name = "video_url") val videoUrl: String = "",
    @Json(name = "skip_after_seconds") val skipAfterSeconds: Int = 5, // ০ হলে নন-স্কিপেবল
    @Json(name = "cta_text") val ctaText: String = "Learn More",      // বাটনে যা লেখা থাকবে
    @Json(name = "cta_color") val ctaColor: String = "#00E676",      // বাটনের হেক্স কালার
    @Json(name = "destination_type") val destinationType: String = "external_url", // 'internal_app', 'play_store', 'external_url'
    @Json(name = "destination_target") val destinationTarget: String = "",
    @Json(name = "placement") val placement: String = "all"           // 'shorts', 'long_video', 'all'
) {
    // 🎯 হেল্পার প্রোপার্টিজ
    val isSkippable: Boolean get() = skipAfterSeconds > 0
    val isInternalApp: Boolean get() = destinationType.equals("internal_app", ignoreCase = true)
    val isPlayStore: Boolean get() = destinationType.equals("play_store", ignoreCase = true)
    val isExternalWeb: Boolean get() = destinationType.equals("external_url", ignoreCase = true)

    // হেক্স কালার থেকে Compose Color রূপান্তরকারী
    val parsedCtaColor: Color
        get() = try {
            val cleanHex = ctaColor.trim().removePrefix("#")
            val colorLong = cleanHex.toLong(16)
            if (cleanHex.length == 6) Color(0xFF000000 or colorLong)
            else Color(colorLong)
        } catch (_: Exception) {
            Color(0xFF00E676)
        }
}

/**
 * 📱 শর্ট ভিডিওর জন্য ইন্টারভাল রুলস
 */
@JsonClass(generateAdapter = true)
data class ShortsAdRulesDto(
    @Json(name = "enabled") val enabled: Boolean = true,
    @Json(name = "interval_episodes") val intervalEpisodes: Int = 3 // প্রতি কত পর্ব পর পর বিজ্ঞাপন আসবে
)

/**
 * 🎥 লং ভিডিওর জন্য মিড-রোল অ্যালগরিদম রুলস
 */
@JsonClass(generateAdapter = true)
data class LongVideoAdRulesDto(
    @Json(name = "enabled") val enabled: Boolean = true,
    @Json(name = "midroll_interval_seconds") val midrollIntervalSeconds: Int = 600, // প্রতি ১০ মিনিট পর পর
    @Json(name = "cue_point_timestamps") val cuePointTimestamps: List<Int> = listOf(300, 900, 1800) // ৫মি, ১৫মি, ৩০মি
)

/**
 * 📡 সম্পূর্ণ কাস্টম অ্যাড সার্ভার API রেসপন্স মডেল
 */
@JsonClass(generateAdapter = true)
data class CustomAdsConfigResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: Int? = 200,
    @Json(name = "custom_ads_enabled") val customAdsEnabled: Boolean = true,
    @Json(name = "shorts_rules") val shortsRules: ShortsAdRulesDto? = ShortsAdRulesDto(),
    @Json(name = "long_video_rules") val longVideoRules: LongVideoAdRulesDto? = LongVideoAdRulesDto(),
    @Json(name = "total_ads") val totalAds: Int? = 0,
    @Json(name = "ads") val ads: List<CustomVideoAdDto> = emptyList()
)
