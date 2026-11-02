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
    @Json(name = "skip_after_seconds") val skipAfterSeconds: Int = 5, // ০ হলে নন-স্কিপেবল, ৫ হলে ৫ সেকেন্ড পর স্কিপ
    @Json(name = "start_after_minutes") val startAfterMinutes: Int = 5,
    @Json(name = "start_after_seconds") val startAfterSeconds: Int = 300,
    @Json(name = "scroll_interval_episodes") val scrollIntervalEpisodes: Int = 3, // রিলস স্ক্রোল কাউন্ট
    @Json(name = "cta_text") val ctaText: String = "Learn More",                  // অ্যাকশন বাটন টেক্সট
    @Json(name = "cta_color") val ctaColor: String = "#00E676",                  // অ্যাকশন বাটন হেক্স কালার
    @Json(name = "destination_type") val destinationType: String = "external_url", // 'internal_app', 'play_store', 'external_url'
    @Json(name = "destination_target") val destinationTarget: String = "",
    @Json(name = "placement") val placement: String = "all"                       // 'shorts', 'long_video', 'all'
) {
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
 * 🎥 লং ভিডিওর জন্য অ্যাডমিন প্যানেল থেকে পাঠানো ডায়নামিক টাইমলাইন ও কিউ রুলস
 */
@JsonClass(generateAdapter = true)
data class LongVideoAdRulesDto(
    @Json(name = "enabled") val enabled: Boolean = true,
    @Json(name = "first_ad_delay_minutes") val firstAdDelayMinutes: Int = 2,      // প্রথম বিজ্ঞাপন আসার মিনিট
    @Json(name = "first_ad_delay_seconds") val firstAdDelaySeconds: Int = 120,    // প্রথম বিজ্ঞাপন আসার সেকেন্ড
    @Json(name = "repeat_interval_minutes") val repeatIntervalMinutes: Int = 5,   // অ্যাডমিন যত মিনিট পরপর বলবে
    @Json(name = "repeat_interval_seconds") val repeatIntervalSeconds: Int = 300, // রিপিট ইন্টারভাল সেকেন্ড
    @Json(name = "warning_countdown_seconds") val warningCountdownSeconds: Int = 3, // 👈 বিজ্ঞাপন আসার ৩ সেকেন্ড আগে কাউন্টডাউন
    @Json(name = "direct_link_clicks_per_ad") val directLinkClicksPerAd: Int = 1,   // 👈 ক্লিক অ্যাডে কয়টি ক্লিক করতে হবে (নন-স্কিপেবল)
    @Json(name = "custom_cue_minutes") val customCueMinutes: List<Int> = emptyList() // 👈 নির্দিষ্ট মিনিট তালিকা
) {
    /**
     * 🎯 ভিডিওর মোট দৈর্ঘ্য (ms) অনুযায়ী সমস্ত কিউ-পয়েন্ট (মিলি-সেকেন্ডে) বের করার ইঞ্জিন
     */
    fun calculateCuePointsMs(totalDurationMs: Long): List<Long> {
        if (!enabled || totalDurationMs <= 0L) return emptyList()

        val cuePoints = mutableListOf<Long>()

        if (customCueMinutes.isNotEmpty()) {
            // ১. অ্যাডমিনের নির্দিষ্ট করা মিনিট থাকলে
            for (min in customCueMinutes) {
                val ms = min * 60 * 1000L
                if (ms < totalDurationMs) {
                    cuePoints.add(ms)
                }
            }
        } else {
            // ২. সাধারণ প্রথম বিলম্ব ও রিপিট ইন্টারভাল অনুযায়ী হিসাব
            var nextCueMs = firstAdDelaySeconds * 1000L
            val stepMs = repeatIntervalSeconds * 1000L

            while (nextCueMs < totalDurationMs) {
                cuePoints.add(nextCueMs)
                nextCueMs += stepMs
            }
        }
        return cuePoints
    }

    /**
     * 🟡 টাইমলাইনের উপর ডট বসানোর জন্য ফ্র্যাকশন (0.0f - 1.0f) তালিকা
     */
    fun calculateCueFractions(totalDurationMs: Long): List<Float> {
        if (totalDurationMs <= 0L) return emptyList()
        val points = calculateCuePointsMs(totalDurationMs)
        return points.map { (it.toFloat() / totalDurationMs.toFloat()).coerceIn(0.01f, 0.99f) }
    }
}

/**
 * 📱 রিলস / শর্ট ভিডিওর জন্য সার্ভার নিয়ন্ত্রিত স্ক্রোল ইন্টারভাল রুলস
 */
@JsonClass(generateAdapter = true)
data class ShortsAdRulesDto(
    @Json(name = "enabled") val enabled: Boolean = true,
    @Json(name = "interval_episodes") val intervalEpisodes: Int = 3 // প্রতি কয়টি রিলস পর অ্যাড আসবে
)

/**
 * 📡 সম্পূর্ণ কাস্টম অ্যাড সার্ভার API রেসপন্স মডেল
 */
@JsonClass(generateAdapter = true)
data class CustomAdsConfigResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: Int? = 200,
    @Json(name = "custom_ads_enabled") val customAdsEnabled: Boolean = true,
    @Json(name = "long_video_rules") val longVideoRules: LongVideoAdRulesDto = LongVideoAdRulesDto(),
    @Json(name = "shorts_rules") val shortsRules: ShortsAdRulesDto = ShortsAdRulesDto(),
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
