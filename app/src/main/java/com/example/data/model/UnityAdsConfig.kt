package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UnityAdsConfig(
    @Json(name = "enabled") val enabled: Boolean = true,
    @Json(name = "game_id") val gameId: String? = "800364838",
    @Json(name = "rewarded_id") val rewardedId: String? = "Rewarded_Android",
    @Json(name = "interstitial_id") val interstitialId: String? = "Interstitial_Android",
    @Json(name = "banner_id") val bannerId: String? = "Banner_Android",
    @Json(name = "test_mode") val testMode: Boolean = false
)

@JsonClass(generateAdapter = true)
data class AdsConfigResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: Int = 200,
    @Json(name = "ads_enabled") val adsEnabled: Boolean = true,
    @Json(name = "primary_network") val primaryNetwork: String = "unity",
    @Json(name = "fallback_network") val fallbackNetwork: String = "startio",
    @Json(name = "unity") val unity: UnityAdsConfig? = UnityAdsConfig(),
    @Json(name = "startio") val startio: StartIoConfig? = null,
    @Json(name = "admob") val admob: AdMobConfig? = null,
    @Json(name = "adsterra") val adsterra: AdsterraConfig? = null,
    @Json(name = "rules") val rules: AdRulesConfig? = null
)
