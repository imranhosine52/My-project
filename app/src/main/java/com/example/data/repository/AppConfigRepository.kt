package com.example.data.repository

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.data.model.*
import com.example.data.remote.ApiClient
import com.example.data.remote.PlayDramaFlixApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppConfigRepository(
    private val context: Context,
    private val apiService: PlayDramaFlixApiService = ApiClient.apiService
) {
    private val notifPrefs = context.getSharedPreferences("drama_notif_prefs", Context.MODE_PRIVATE)
    private val adConfigPrefs = context.getSharedPreferences("play_drama_flix_ad_config_prefs", Context.MODE_PRIVATE)

    // =========================================================================
    // 🔔 PERSISTENT NOTIFICATION STATE (READ & DELETED IDS)
    // =========================================================================

    fun getDeletedNotificationIds(): Set<String> {
        return notifPrefs.getStringSet("deleted_notif_ids", emptySet()) ?: emptySet()
    }

    fun saveDeletedNotificationId(id: String) {
        val current = getDeletedNotificationIds().toMutableSet()
        current.add(id)
        notifPrefs.edit().putStringSet("deleted_notif_ids", current).apply()
    }

    fun saveAllDeletedNotificationIds(ids: Collection<String>) {
        val current = getDeletedNotificationIds().toMutableSet()
        current.addAll(ids)
        notifPrefs.edit().putStringSet("deleted_notif_ids", current).apply()
    }

    fun getReadNotificationIds(): Set<String> {
        return notifPrefs.getStringSet("read_notif_ids", emptySet()) ?: emptySet()
    }

    fun saveReadNotificationId(id: String) {
        val current = getReadNotificationIds().toMutableSet()
        current.add(id)
        notifPrefs.edit().putStringSet("read_notif_ids", current).apply()
    }

    // =========================================================================
    // 📱 DEVICE REGISTRATION & APP VERSION
    // =========================================================================

    fun getInstalledAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    suspend fun registerDevice(token: String, oneSignalId: String? = null) = withContext(Dispatchers.IO) {
        try {
            val req = DeviceRegisterRequest(
                deviceToken = token,
                onesignalPlayerId = oneSignalId ?: "387a2baa-3299-46ba-8fff-df4eec199077",
                platform = "android",
                appVersion = getInstalledAppVersion(),
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                osVersion = Build.VERSION.RELEASE
            )
            apiService.registerDevice(req)
            Log.d("AppConfigRepo", "✓ Device token registered to server successfully.")
        } catch (e: Exception) {
            Log.e("AppConfigRepo", "Device registration error: ${e.message}")
        }
    }

    suspend fun checkAppVersion(currentVersion: String = getInstalledAppVersion()): Result<AppVersionCheckResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.checkAppVersion(currentVersion = currentVersion)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.success(
                    AppVersionCheckResponse(
                        success = true,
                        updateAvailable = false,
                        forceUpdate = false,
                        latestVersion = currentVersion
                    )
                )
            }
        } catch (e: Exception) {
            Result.success(
                AppVersionCheckResponse(
                    success = true,
                    updateAvailable = false,
                    forceUpdate = false,
                    latestVersion = currentVersion
                )
            )
        }
    }

    // =========================================================================
    // 📡 REMOTE & CACHED ADS MEDIATION CONFIGURATION
    // =========================================================================

    fun getCachedAdsConfig(): AdsConfigResponse {
        val enabled = adConfigPrefs.getBoolean("ads_enabled", true)
        val primary = adConfigPrefs.getString("primary_network", "unity") ?: "unity"
        val fallback = adConfigPrefs.getString("fallback_network", "startio") ?: "startio"

        val unityEnabled = adConfigPrefs.getBoolean("unity_enabled", true)
        val unityGameId = adConfigPrefs.getString("unity_game_id", "800364838") ?: "800364838"
        val unityRewarded = adConfigPrefs.getString("unity_rewarded_id", "Rewarded_Android") ?: "Rewarded_Android"
        val unityInterstitial = adConfigPrefs.getString("unity_interstitial_id", "Interstitial_Android") ?: "Interstitial_Android"
        val unityBanner = adConfigPrefs.getString("unity_banner_id", "Banner_Android") ?: "Banner_Android"
        val unityTestMode = adConfigPrefs.getBoolean("unity_test_mode", true)

        val startioEnabled = adConfigPrefs.getBoolean("startio_enabled", true)
        val startioAppId = adConfigPrefs.getString("startio_app_id", "207238360") ?: "207238360"
        val startioPubId = adConfigPrefs.getString("startio_pub_id", "113502454") ?: "113502454"

        val admobEnabled = adConfigPrefs.getBoolean("admob_enabled", false)
        val admobAppId = adConfigPrefs.getString("admob_app_id", null)
        val admobBanner = adConfigPrefs.getString("admob_banner_id", null)
        val admobInter = adConfigPrefs.getString("admob_interstitial_id", null)
        val admobReward = adConfigPrefs.getString("admob_rewarded_id", null)

        val adsterraEnabled = adConfigPrefs.getBoolean("adsterra_enabled", true)
        val adsterraDirectLink = adConfigPrefs.getString("adsterra_direct_link", null)
        val adsterraSmartlink = adConfigPrefs.getString("adsterra_smartlink_url", null)
        val adsterraPopunder = adConfigPrefs.getString("adsterra_popunder_url", null)
        val adsterraFreq = adConfigPrefs.getInt("adsterra_popunder_frequency", 3)
        val adsterraMinInterval = adConfigPrefs.getInt("adsterra_popunder_min_interval_seconds", 30)
        val adsterraSocialBarEnabled = adConfigPrefs.getBoolean("adsterra_social_bar_enabled", true)
        val adsterraSocialBarCode = adConfigPrefs.getString("adsterra_social_bar_code", null)
        val adsterraSocialBarScript = adConfigPrefs.getString("adsterra_social_bar_script", null)
        val adsterraSocialBarUrl = adConfigPrefs.getString("adsterra_social_bar_url", null)

        val timerSeconds = adConfigPrefs.getInt("timer_seconds", 10)
        val unlockHours = adConfigPrefs.getInt("rewarded_unlock_hours", 2)
        val freeEpisodes = adConfigPrefs.getInt("free_unlocked_episodes", 1)

        return AdsConfigResponse(
            success = true,
            status = 200,
            adsEnabled = enabled,
            primaryNetwork = primary,
            fallbackNetwork = fallback,
            unity = UnityAdsConfig(
                enabled = unityEnabled,
                gameId = unityGameId,
                rewardedId = unityRewarded,
                interstitialId = unityInterstitial,
                bannerId = unityBanner,
                testMode = unityTestMode
            ),
            startio = StartIoConfig(
                enabled = startioEnabled,
                appId = startioAppId,
                publisherId = startioPubId
            ),
            admob = AdMobConfig(
                enabled = admobEnabled,
                appId = admobAppId,
                bannerId = admobBanner,
                interstitialId = admobInter,
                rewardedId = admobReward
            ),
            adsterra = AdsterraConfig(
                enabled = adsterraEnabled,
                directLink = adsterraDirectLink,
                smartlinkUrl = adsterraSmartlink,
                popunderUrl = adsterraPopunder,
                popunderFrequency = adsterraFreq,
                popunderMinIntervalSeconds = adsterraMinInterval,
                socialBarEnabled = adsterraSocialBarEnabled,
                socialBarCode = adsterraSocialBarCode,
                socialBarScript = adsterraSocialBarScript,
                socialBarUrl = adsterraSocialBarUrl
            ),
            rules = AdRulesConfig(
                timerSeconds = timerSeconds,
                rewardedUnlockHours = unlockHours,
                freeUnlockedEpisodes = freeEpisodes
            )
        )
    }

    suspend fun fetchRemoteAdsConfig(): Result<AdsConfigResponse> = withContext(Dispatchers.IO) {
        try {
            val response = try {
                apiService.getAdsConfig()
            } catch (e: Exception) {
                apiService.getAdsConfigDirect()
            }
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                adConfigPrefs.edit().apply {
                    putBoolean("ads_enabled", body.adsEnabled)
                    putString("primary_network", body.primaryNetwork)
                    putString("fallback_network", body.fallbackNetwork)

                    putBoolean("unity_enabled", body.unity?.enabled ?: true)
                    putString("unity_game_id", body.unity?.gameId ?: "800364838")
                    putString("unity_rewarded_id", body.unity?.rewardedId ?: "Rewarded_Android")
                    putString("unity_interstitial_id", body.unity?.interstitialId ?: "Interstitial_Android")
                    putString("unity_banner_id", body.unity?.bannerId ?: "Banner_Android")
                    putBoolean("unity_test_mode", body.unity?.testMode ?: true)

                    putBoolean("startio_enabled", body.startio?.enabled ?: true)
                    putString("startio_app_id", body.startio?.appId ?: "207238360")
                    putString("startio_pub_id", body.startio?.publisherId ?: "113502454")

                    putBoolean("admob_enabled", body.admob?.enabled ?: false)
                    putString("admob_app_id", body.admob?.appId)
                    putString("admob_banner_id", body.admob?.bannerId)
                    putString("admob_interstitial_id", body.admob?.interstitialId)
                    putString("admob_rewarded_id", body.admob?.rewardedId)

                    putBoolean("adsterra_enabled", body.adsterra?.enabled ?: true)
                    putString("adsterra_direct_link", body.adsterra?.directLink)
                    putString("adsterra_smartlink_url", body.adsterra?.smartlinkUrl)
                    putString("adsterra_popunder_url", body.adsterra?.popunderUrl)
                    putInt("adsterra_popunder_frequency", body.adsterra?.popunderFrequency ?: 3)
                    putInt("adsterra_popunder_min_interval_seconds", body.adsterra?.popunderMinIntervalSeconds ?: 30)
                    putBoolean("adsterra_social_bar_enabled", body.adsterra?.socialBarEnabled ?: true)
                    putString("adsterra_social_bar_code", body.adsterra?.socialBarCode)
                    putString("adsterra_social_bar_script", body.adsterra?.socialBarScript)
                    putString("adsterra_social_bar_url", body.adsterra?.socialBarUrl)

                    putInt("timer_seconds", body.rules?.timerSeconds ?: 10)
                    putInt("rewarded_unlock_hours", body.rules?.rewardedUnlockHours ?: 2)
                    putInt("free_unlocked_episodes", body.rules?.freeUnlockedEpisodes ?: 1)
                    apply()
                }
                Result.success(body)
            } else {
                Result.success(getCachedAdsConfig())
            }
        } catch (e: Exception) {
            Result.success(getCachedAdsConfig())
        }
    }
}
