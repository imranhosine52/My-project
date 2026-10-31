package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class StartIoConfig(
    @Json(name = "enabled") val enabled: Boolean = true,
    @Json(name = "app_id") val appId: String = "207238360",
    @Json(name = "publisher_id") val publisherId: String? = "113502454",
    @Json(name = "test_mode") val testMode: Boolean = false
)

@JsonClass(generateAdapter = true)
data class AdsterraConfig(
    @Json(name = "enabled") val enabled: Boolean = true,
    @Json(name = "direct_link") val directLink: String? = null,
    @Json(name = "smartlink_url") val smartlinkUrl: String? = null,
    @Json(name = "popunder_url") val popunderUrl: String? = null,
    @Json(name = "popunder_frequency") val popunderFrequency: Int = 3,
    @Json(name = "popunder_min_interval_seconds") val popunderMinIntervalSeconds: Int = 30,
    @Json(name = "social_bar_enabled") val socialBarEnabled: Boolean = true,
    @Json(name = "social_bar_code") val socialBarCode: String? = null,
    @Json(name = "social_bar_script") val socialBarScript: String? = null,
    @Json(name = "social_bar_url") val socialBarUrl: String? = null
) {
    val effectiveDirectLink: String? get() = directLink?.takeIf { it.isNotBlank() } ?: smartlinkUrl?.takeIf { it.isNotBlank() }
    val effectiveSocialBarCode: String? get() = socialBarCode?.takeIf { it.isNotBlank() } ?: socialBarScript?.takeIf { it.isNotBlank() }
    val effectiveSocialBarUrl: String? get() = socialBarUrl?.takeIf { it.isNotBlank() }
}

@JsonClass(generateAdapter = true)
data class AdRulesConfig(
    @Json(name = "timer_seconds") val timerSeconds: Int = 5,
    @Json(name = "ad_wait_seconds") val adWaitSeconds: Int = 5,
    @Json(name = "unlock_clicks_required") val unlockClicksRequired: Int = 1,
    @Json(name = "rewarded_unlock_hours") val rewardedUnlockHours: Int = 2,
    @Json(name = "free_unlocked_episodes") val freeUnlockedEpisodes: Int = 1
)

@JsonClass(generateAdapter = true)
data class AdsConfigResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: Int? = 200,
    @Json(name = "ads_enabled") val adsEnabled: Boolean = true,
    @Json(name = "primary_network") val primaryNetwork: String = "startio",
    @Json(name = "fallback_network") val fallbackNetwork: String = "adsterra",
    @Json(name = "startio") val startio: StartIoConfig? = StartIoConfig(),
    @Json(name = "adsterra") val adsterra: AdsterraConfig? = AdsterraConfig(),
    @Json(name = "rules") val rules: AdRulesConfig? = AdRulesConfig()
)
