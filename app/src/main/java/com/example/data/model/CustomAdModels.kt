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
    @Json(name = "start_after_minutes") val startAfterMinutes: Int = 5,
    @Json(name = "start_after_seconds") val startAfterSeconds: Int = 300,
    @Json(name = "scroll_interval_episodes") val scrollIntervalEpisodes: Int = 3, // 👈 সার্ভার নির্ধারিত রিলস স্ক্রোল কাউন্ট
    @Json(name = "cta_text") val ctaText: String = "Learn More",                  // বাটনে যা লেখা থাকবে
    @Json(name = "cta_color") val ctaColor: String = "#00E676",                  // বাটনের হেক্স কালার
    @Json(name = "destination_type") val destinationType: String = "external_url", // 'internal_app', 'play_store', 'external_url'
    @Json(name = "destination_target") val destinationTarget: String = "",
    @Json(name = "placement") val placement: String = "all"                       // 'shorts', 'long_video', 'all'
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
 * 🎥 লং ভিডিওর জন্য অ্যাডমিন প্যানেল থেকে পাঠানো ডায়নামিক মিনিট ইন্টারভাল রুলস
 */
@JsonClass(generateAdapter = true)
data class LongVideoAdRulesDto(
    @Json(name = "enabled") val enabled: Boolean = true,
    @Json(name = "repeat_interval_minutes") val repeatIntervalMinutes: Int = 5,   // অ্যাডমিন যত মিনিট পরপর বলবে
    @Json(name = "repeat_interval_seconds") val repeatIntervalSeconds: Int = 300, // প্লেয়ারের জন্য সেকেন্ড
    @Json(name = "first_ad_delay_minutes") val firstAdDelayMinutes: Int = 2,      // প্রথম অ্যাড আসার মিনিট
    @Json(name = "first_ad_delay_seconds") val firstAdDelaySeconds: Int = 120      // প্রথম অ্যাড আসার সেকেন্ড
)

/**
 * 📱 রিলস / শর্ট ভিডিওর জন্য সার্ভার নিয়ন্ত্রিত স্ক্রোল ইন্টারভাল রুলস
 */
@JsonClass(generateAdapter = true)
data class ShortsAdRulesDto(
    @Json(name = "enabled") val enabled: Boolean = true,
    @Json(name = "interval_episodes") val intervalEpisodes: Int = 3 // 👈 সার্ভার নির্ধারিত সার্বজনীন রিলস স্ক্রোল কাউন্ট
)

/**
 * 📡 সম্পূর্ণ কাস্টম অ্যাড সার্ভার API রেসপন্স মডেল
 */
@JsonClass(generateAdapter = true)
data class CustomAdsConfigResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: Int? = 200,
    @Json(name = "custom_ads_enabled") val customAdsEnabled: Boolean = true,
    @Json(name = "long_video_rules") val longVideoRules: LongVideoAdRulesDto? = LongVideoAdRulesDto(),
    @Json(name = "shorts_rules") val shortsRules: ShortsAdRulesDto? = ShortsAdRulesDto(),
    @Json(name = "total_ads") val totalAds: Int? = 0,
    @Json(name = "ads") val ads: List<CustomVideoAdDto> = emptyList()
)

/**
 * 📊 বিজ্ঞাপনের ভিউ ও ক্লিক ট্র্যাক করার রিকোয়েস্ট মডেল
 */
@JsonClass(generateAdapter = true)
data class TrackAdEventRequest(
    @Json(name = "ad_id") val adId: Int,
    @Json(name = "event") val event: String // 'view' অথবা 'click'
)
