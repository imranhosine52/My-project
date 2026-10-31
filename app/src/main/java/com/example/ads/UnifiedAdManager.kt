package com.example.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.*
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.startapp.sdk.ads.banner.Banner
import com.startapp.sdk.ads.banner.BannerListener
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import com.startapp.sdk.adsbase.adlisteners.VideoListener
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsShowOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Safe Activity Resolver from Context
fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * ============================================================
 * 📡 REMOTE DYNAMIC MULTI-NETWORK AD & CHROME CUSTOM TABS ENGINE
 * Networks: Google AdMob, Unity Ads, Start.io, Adsterra Direct Link
 * ============================================================
 */
object UnifiedAdManager {
    private const val TAG = "UnifiedAdManager"

    // Default Fallback Configurations
    private const val DEFAULT_UNITY_GAME_ID = "800364838"
    private const val DEFAULT_STARTIO_APP_ID = "207238360"
    
    // 🎯 Google AdMob Official Test Unit IDs (Test Mode-এর জন্য)
    private const val ADMOB_TEST_BANNER = "ca-app-pub-3940256099942544/6300978111"
    private const val ADMOB_TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
    private const val ADMOB_TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917"

    // ৩ পেজ পর পর ইন্টারস্টিশিয়াল অ্যাড দেখানোর কনফিগারেশন
    private const val INTERSTITIAL_PAGE_INTERVAL = 3
    private var interstitialNavCount = 0

    // Observable Live Ad Configuration State
    private val _adConfigState = MutableStateFlow(
        AdsConfigResponse(
            success = true,
            status = 200,
            adsEnabled = true,
            primaryNetwork = "admob",
            fallbackNetwork = "unity",
            admob = AdMobConfig(enabled = false),
            unity = UnityAdsConfig(enabled = true, testMode = false),
            startio = StartIoConfig(enabled = true),
            adsterra = AdsterraConfig(enabled = true),
            rules = AdRulesConfig(timerSeconds = 10, rewardedUnlockHours = 2, freeUnlockedEpisodes = 1)
        )
    )
    val adConfigState: StateFlow<AdsConfigResponse> = _adConfigState.asStateFlow()

    // Status Flags
    private var isAdMobInitialized = false
    private var isStartIoInitialized = false
    private var isUnityInitialized = false
    private var isUnityAdLoaded = false
    private var isUnityInterstitialLoaded = false
    private var currentStartIoAppId: String = DEFAULT_STARTIO_APP_ID

    // AdMob Preloaded Ads
    private var adMobInterstitialAd: InterstitialAd? = null
    private var isAdMobInterstitialLoading = false
    private var adMobRewardedAd: RewardedAd? = null
    private var isAdMobRewardedLoading = false

    // Start.io Preloaded Ads
    private var startIoInterstitialAd: StartAppAd? = null
    private var startIoRewardedAd: StartAppAd? = null
    private var isStartIoInterstitialLoading = false
    private var isStartIoRewardedLoading = false

    private var pageTransitionCount = 0
    private var lastPopunderTimestamp = 0L

    // ============================================================
    // 🌐 CHROME CUSTOM TABS LAUNCHER (Adsterra & High CPM Smartlink)
    // ============================================================
    fun openChromeCustomTab(context: Context, url: String): Boolean {
        return try {
            val cleanUrl = url.trim()
            if (cleanUrl.isBlank() || (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://"))) {
                return false
            }
            val uri = Uri.parse(cleanUrl)

            val defaultColors = CustomTabColorSchemeParams.Builder()
                .setToolbarColor(android.graphics.Color.parseColor("#06080E"))
                .setSecondaryToolbarColor(android.graphics.Color.parseColor("#10141E"))
                .setNavigationBarColor(android.graphics.Color.parseColor("#06080E"))
                .build()

            val customTabsIntent = CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(defaultColors)
                .setShowTitle(true)
                .setUrlBarHidingEnabled(false)
                .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
                .build()

            customTabsIntent.intent.setPackage("com.android.chrome")
            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            try {
                customTabsIntent.launchUrl(context, uri)
            } catch (e: Exception) {
                customTabsIntent.intent.setPackage(null)
                customTabsIntent.launchUrl(context, uri)
            }
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to launch Chrome Custom Tab: ${t.message}")
            openUrlSafely(context, url)
        }
    }

    // ============================================================
    // 🚀 INITIALIZATION ENGINE
    // ============================================================
    fun init(context: Context, initialConfig: AdsConfigResponse? = null, isVip: Boolean = false) {
        val appContext = context.applicationContext

        try {
            val now = System.currentTimeMillis()
            StartAppSDK.setUserConsent(appContext, "pas", now, true)
            StartAppSDK.setUserConsent(appContext, "gdpr", now, true)
            StartAppSDK.setUserConsent(appContext, "ccpa", now, true)
        } catch (_: Throwable) {}

        if (initialConfig != null) {
            _adConfigState.value = initialConfig
        }

        val config = _adConfigState.value

        if (!config.adsEnabled || isVip) {
            Log.i(TAG, "Ads globally disabled or user is VIP.")
            return
        }

        val isTestMode = config.unity?.testMode == true

        if (config.admob?.enabled == true) {
            initAdMob(appContext)
        }

        if (config.unity?.enabled == true) {
            val unityGameId = config.unity.gameId?.takeIf { it.isNotBlank() } ?: DEFAULT_UNITY_GAME_ID
            initUnityAds(context, unityGameId, isTestMode)
        }

        if (config.startio?.enabled == true) {
            val startIoAppId = config.startio.appId.takeIf { it.isNotBlank() } ?: DEFAULT_STARTIO_APP_ID
            initializeStartIo(context, startIoAppId, isVip, isTestMode)
        }
    }

    fun applyRemoteConfig(context: Context, newConfig: AdsConfigResponse, isVip: Boolean = false) {
        _adConfigState.value = newConfig

        if (!newConfig.adsEnabled || isVip) return

        val isTestMode = newConfig.unity?.testMode == true

        if (newConfig.admob?.enabled == true) {
            if (!isAdMobInitialized) initAdMob(context.applicationContext)
            else {
                preloadAdMobInterstitial(context)
                preloadAdMobRewarded(context)
            }
        }

        if (newConfig.unity?.enabled == true) {
            val unityGameId = newConfig.unity.gameId?.takeIf { it.isNotBlank() } ?: DEFAULT_UNITY_GAME_ID
            initUnityAds(context, unityGameId, isTestMode)
        }

        if (newConfig.startio?.enabled == true) {
            val newAppId = newConfig.startio.appId.takeIf { it.isNotBlank() } ?: DEFAULT_STARTIO_APP_ID
            if (newAppId != currentStartIoAppId || !isStartIoInitialized) {
                initializeStartIo(context, newAppId, isVip, isTestMode)
            } else {
                preloadInterstitial(context)
                preloadStartIoRewarded(context)
            }
        }
    }

    private fun initAdMob(context: Context) {
        try {
            if (!isAdMobInitialized) {
                MobileAds.initialize(context) { status ->
                    isAdMobInitialized = true
                    Log.i(TAG, "✓ AdMob SDK Initialized.")
                    preloadAdMobInterstitial(context)
                    preloadAdMobRewarded(context)
                }
            } else {
                preloadAdMobInterstitial(context)
                preloadAdMobRewarded(context)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to init AdMob SDK: ${t.message}")
        }
    }

    private fun initUnityAds(context: Context, gameId: String, testMode: Boolean) {
        try {
            if (!UnityAds.isInitialized && gameId.isNotBlank()) {
                UnityAds.initialize(
                    context.applicationContext,
                    gameId,
                    testMode,
                    object : IUnityAdsInitializationListener {
                        override fun onInitializationComplete() {
                            isUnityInitialized = true
                            Log.i(TAG, "✓ Unity Ads SDK Initialized (TestMode: $testMode).")
                            preloadUnityRewarded(context)
                            preloadUnityInterstitial(context)
                        }
                        override fun onInitializationFailed(error: UnityAds.UnityAdsInitializationError, message: String) {
                            isUnityInitialized = false
                        }
                    }
                )
            } else if (UnityAds.isInitialized) {
                preloadUnityRewarded(context)
                preloadUnityInterstitial(context)
            }
        } catch (t: Throwable) {}
    }

    private fun initializeStartIo(context: Context, appId: String, isVip: Boolean, testMode: Boolean) {
        try {
            currentStartIoAppId = appId
            val appContext = context.applicationContext

            val now = System.currentTimeMillis()
            StartAppSDK.setUserConsent(appContext, "pas", now, true)
            StartAppSDK.setUserConsent(appContext, "gdpr", now, true)
            StartAppSDK.setUserConsent(appContext, "ccpa", now, true)

            StartAppSDK.init(appContext, appId, false)
            StartAppSDK.setTestAdsEnabled(testMode)
            StartAppAd.disableSplash()
            StartAppSDK.enableReturnAds(false)
            
            isStartIoInitialized = true
            Log.i(TAG, "✓ Start.io SDK Initialized (TestMode: $testMode).")

            if (!isVip) {
                preloadInterstitial(context)
                preloadStartIoRewarded(context)
            }
        } catch (t: Throwable) {}
    }

    // ============================================================
    // 🎁 REWARDED VIDEO ADS (AdMob -> Unity -> Start.io -> Adsterra)
    // ============================================================
    fun showRewardedAd(
        activity: Activity,
        onRewardEarned: (Boolean) -> Unit
    ) {
        showRewardedVideo(
            context = activity,
            isVip = false,
            onRewardUnlocked = { onRewardEarned(true) },
            onAdNotReadyOrFailed = { onRewardEarned(false) },
            onAdClosed = { rewarded -> if (!rewarded) onRewardEarned(false) }
        )
    }

    fun showRewardedVideo(
        context: Context,
        isVip: Boolean,
        onRewardUnlocked: () -> Unit,
        onAdNotReadyOrFailed: ((reason: String) -> Unit)? = null,
        onAdClosed: ((rewardEarned: Boolean) -> Unit)? = null
    ) {
        val config = _adConfigState.value

        if (isVip || !config.adsEnabled) {
            onRewardUnlocked()
            onAdClosed?.invoke(true)
            return
        }

        val activity = context.findActivity()
        if (activity == null) {
            onAdNotReadyOrFailed?.invoke("Screen context not ready.")
            onAdClosed?.invoke(false)
            return
        }

        val primary = config.primaryNetwork.lowercase()
        val isAdMobOn = config.admob?.enabled == true
        val isUnityOn = config.unity?.enabled == true
        val isStartIoOn = config.startio?.enabled == true

        when {
            (primary.contains("admob") && isAdMobOn) -> showAdMobRewarded(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
            (primary.contains("unity") && isUnityOn) -> showUnityRewardedVideo(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
            (primary.contains("start") && isStartIoOn) -> showStartIoRewardedWithFallback(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
            else -> {
                if (isAdMobOn) showAdMobRewarded(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
                else if (isUnityOn) showUnityRewardedVideo(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
                else if (isStartIoOn) showStartIoRewardedWithFallback(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
                else fallbackToAdsterraDirectLink(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
            }
        }
    }

    private fun showAdMobRewarded(
        activity: Activity,
        onRewardUnlocked: () -> Unit,
        onAdNotReadyOrFailed: ((reason: String) -> Unit)?,
        onAdClosed: ((rewardEarned: Boolean) -> Unit)?
    ) {
        val preloaded = adMobRewardedAd
        if (preloaded != null) {
            var earnedReward = false
            preloaded.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    adMobRewardedAd = null
                    preloadAdMobRewarded(activity)
                    if (earnedReward) onRewardUnlocked()
                    onAdClosed?.invoke(earnedReward)
                }
                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    adMobRewardedAd = null
                    preloadAdMobRewarded(activity)
                    fallbackFromAdMobReward(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
                }
            }
            preloaded.show(activity) { earnedReward = true }
        } else {
            fallbackFromAdMobReward(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
            preloadAdMobRewarded(activity)
        }
    }

    private fun fallbackFromAdMobReward(
        activity: Activity,
        onRewardUnlocked: () -> Unit,
        onAdNotReadyOrFailed: ((reason: String) -> Unit)?,
        onAdClosed: ((rewardEarned: Boolean) -> Unit)?
    ) {
        val config = _adConfigState.value
        if (config.unity?.enabled == true) {
            showUnityRewardedVideo(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
        } else if (config.startio?.enabled == true) {
            showStartIoRewardedWithFallback(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
        } else {
            fallbackToAdsterraDirectLink(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
        }
    }

    private fun showUnityRewardedVideo(
        activity: Activity,
        onRewardUnlocked: () -> Unit,
        onAdNotReadyOrFailed: ((reason: String) -> Unit)?,
        onAdClosed: ((rewardEarned: Boolean) -> Unit)?
    ) {
        val config = _adConfigState.value
        val placementId = config.unity?.rewardedId?.takeIf { it.isNotBlank() } ?: "Rewarded_Android"

        if (isUnityInitialized && isUnityAdLoaded) {
            UnityAds.show(activity, placementId, UnityAdsShowOptions(), object : IUnityAdsShowListener {
                override fun onUnityAdsShowStart(placementId: String) {}
                override fun onUnityAdsShowClick(placementId: String) {}
                override fun onUnityAdsShowComplete(placementId: String, state: UnityAds.UnityAdsShowCompletionState) {
                    isUnityAdLoaded = false
                    preloadUnityRewarded(activity)
                    if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                        onRewardUnlocked()
                        onAdClosed?.invoke(true)
                    } else {
                        onAdClosed?.invoke(false)
                    }
                }
                override fun onUnityAdsShowFailure(placementId: String, error: UnityAds.UnityAdsShowError, message: String) {
                    isUnityAdLoaded = false
                    preloadUnityRewarded(activity)
                    handleUnityRewardFallback(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
                }
            })
        } else {
            handleUnityRewardFallback(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
            preloadUnityRewarded(activity)
        }
    }

    private fun handleUnityRewardFallback(
        activity: Activity,
        onRewardUnlocked: () -> Unit,
        onAdNotReadyOrFailed: ((reason: String) -> Unit)?,
        onAdClosed: ((rewardEarned: Boolean) -> Unit)?
    ) {
        val config = _adConfigState.value
        if (config.startio?.enabled == true) {
            showStartIoRewardedWithFallback(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
        } else {
            fallbackToAdsterraDirectLink(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
        }
    }

    private fun showStartIoRewardedWithFallback(
        activity: Activity,
        onRewardUnlocked: () -> Unit,
        onAdNotReadyOrFailed: ((reason: String) -> Unit)?,
        onAdClosed: ((rewardEarned: Boolean) -> Unit)?
    ) {
        val preloadedRewarded = startIoRewardedAd
        if (preloadedRewarded != null && preloadedRewarded.isReady) {
            var earnedReward = false
            preloadedRewarded.setVideoListener(object : VideoListener {
                override fun onVideoCompleted() { earnedReward = true }
            })
            preloadedRewarded.showAd(object : AdDisplayListener {
                override fun adHidden(shownAd: Ad) {
                    startIoRewardedAd = null
                    preloadStartIoRewarded(activity)
                    if (earnedReward) onRewardUnlocked()
                    onAdClosed?.invoke(earnedReward)
                }
                override fun adDisplayed(shownAd: Ad) {}
                override fun adClicked(shownAd: Ad) {}
                override fun adNotDisplayed(shownAd: Ad) {
                    startIoRewardedAd = null
                    preloadStartIoRewarded(activity)
                    showStartIoOnDemandRewarded(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
                }
            })
        } else {
            showStartIoOnDemandRewarded(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
        }
    }

    private fun showStartIoOnDemandRewarded(
        activity: Activity,
        onRewardUnlocked: () -> Unit,
        onAdNotReadyOrFailed: ((reason: String) -> Unit)?,
        onAdClosed: ((rewardEarned: Boolean) -> Unit)?
    ) {
        try {
            val onDemandAd = StartAppAd(activity)
            var userEarnedReward = false

            onDemandAd.setVideoListener(object : VideoListener {
                override fun onVideoCompleted() { userEarnedReward = true }
            })

            onDemandAd.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener {
                override fun onReceiveAd(loadedAd: Ad) {
                    onDemandAd.showAd(object : AdDisplayListener {
                        override fun adHidden(shownAd: Ad) {
                            if (userEarnedReward) onRewardUnlocked()
                            onAdClosed?.invoke(userEarnedReward)
                            preloadStartIoRewarded(activity)
                        }
                        override fun adDisplayed(shownAd: Ad) {}
                        override fun adClicked(shownAd: Ad) {}
                        override fun adNotDisplayed(shownAd: Ad) {
                            preloadStartIoRewarded(activity)
                            fallbackToAdsterraDirectLink(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
                        }
                    })
                }
                override fun onFailedToReceiveAd(failedAd: Ad?) {
                    fallbackToAdsterraDirectLink(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
                }
            })
        } catch (t: Throwable) {
            fallbackToAdsterraDirectLink(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
        }
    }

    private fun fallbackToAdsterraDirectLink(
        activity: Activity,
        onRewardUnlocked: () -> Unit,
        onAdNotReadyOrFailed: ((reason: String) -> Unit)?,
        onAdClosed: ((rewardEarned: Boolean) -> Unit)?
    ) {
        val smartlinkOpened = openSmartlink(activity, isVip = false)
        if (smartlinkOpened) {
            onRewardUnlocked()
            onAdClosed?.invoke(true)
        } else {
            onAdNotReadyOrFailed?.invoke("Ad is currently unavailable. Please try again.")
            onAdClosed?.invoke(false)
        }
    }

    // ============================================================
    // 🎬 INTERSTITIAL ADS MEDIATION (AdMob -> Unity -> Start.io)
    // ============================================================
    fun showInterstitial(
        context: Context,
        isVip: Boolean,
        onComplete: () -> Unit
    ) {
        val config = _adConfigState.value

        if (isVip || !config.adsEnabled) {
            onComplete()
            return
        }

        interstitialNavCount++
        if (interstitialNavCount % INTERSTITIAL_PAGE_INTERVAL != 0) {
            onComplete()
            return
        }

        val activity = context.findActivity() ?: run {
            onComplete()
            return
        }

        val primary = config.primaryNetwork.lowercase()
        val isAdMobOn = config.admob?.enabled == true
        val isUnityOn = config.unity?.enabled == true
        val isStartIoOn = config.startio?.enabled == true

        when {
            (primary.contains("admob") && isAdMobOn) -> showAdMobInterstitial(activity, context, onComplete)
            (primary.contains("unity") && isUnityOn) -> showUnityInterstitial(activity, context, onComplete)
            (primary.contains("start") && isStartIoOn) -> showStartIoInterstitial(context, onComplete)
            else -> {
                if (isAdMobOn) showAdMobInterstitial(activity, context, onComplete)
                else if (isUnityOn) showUnityInterstitial(activity, context, onComplete)
                else if (isStartIoOn) showStartIoInterstitial(context, onComplete)
                else onComplete()
            }
        }
    }

    private fun showAdMobInterstitial(activity: Activity, context: Context, onComplete: () -> Unit) {
        val ad = adMobInterstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    adMobInterstitialAd = null
                    preloadAdMobInterstitial(activity)
                    onComplete()
                }
                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    adMobInterstitialAd = null
                    preloadAdMobInterstitial(activity)
                    fallbackFromAdMobInterstitial(activity, context, onComplete)
                }
            }
            ad.show(activity)
        } else {
            fallbackFromAdMobInterstitial(activity, context, onComplete)
            preloadAdMobInterstitial(activity)
        }
    }

    private fun fallbackFromAdMobInterstitial(activity: Activity, context: Context, onComplete: () -> Unit) {
        val config = _adConfigState.value
        if (config.unity?.enabled == true) {
            showUnityInterstitial(activity, context, onComplete)
        } else if (config.startio?.enabled == true) {
            showStartIoInterstitial(context, onComplete)
        } else {
            onComplete()
        }
    }

    private fun showUnityInterstitial(activity: Activity, context: Context, onComplete: () -> Unit) {
        val config = _adConfigState.value
        val placementId = config.unity?.interstitialId?.takeIf { it.isNotBlank() } ?: "Interstitial_Android"
        val isStartIoOn = config.startio?.enabled == true

        if (isUnityInitialized && isUnityInterstitialLoaded) {
            UnityAds.show(activity, placementId, UnityAdsShowOptions(), object : IUnityAdsShowListener {
                override fun onUnityAdsShowStart(placementId: String) {}
                override fun onUnityAdsShowClick(placementId: String) {}
                override fun onUnityAdsShowComplete(placementId: String, state: UnityAds.UnityAdsShowCompletionState) {
                    isUnityInterstitialLoaded = false
                    preloadUnityInterstitial(context)
                    onComplete()
                }
                override fun onUnityAdsShowFailure(placementId: String, error: UnityAds.UnityAdsShowError, message: String) {
                    isUnityInterstitialLoaded = false
                    preloadUnityInterstitial(context)
                    if (isStartIoOn) showStartIoInterstitial(context, onComplete) else onComplete()
                }
            })
        } else {
            preloadUnityInterstitial(context)
            if (isStartIoOn) showStartIoInterstitial(context, onComplete) else onComplete()
        }
    }

    private fun showStartIoInterstitial(context: Context, onComplete: () -> Unit) {
        try {
            val activity = context.findActivity()
            val ad = startIoInterstitialAd
            if (activity != null && ad != null && ad.isReady) {
                ad.showAd(object : AdDisplayListener {
                    override fun adHidden(ad: Ad) {
                        startIoInterstitialAd = null
                        preloadInterstitial(context)
                        onComplete()
                    }
                    override fun adDisplayed(ad: Ad) {}
                    override fun adClicked(ad: Ad) {}
                    override fun adNotDisplayed(ad: Ad) {
                        startIoInterstitialAd = null
                        preloadInterstitial(context)
                        onComplete()
                    }
                })
            } else {
                preloadInterstitial(context)
                onComplete()
            }
        } catch (t: Throwable) {
            preloadInterstitial(context)
            onComplete()
        }
    }

    // ============================================================
    // ⚡ PRELOADERS
    // ============================================================
    private fun preloadAdMobInterstitial(context: Context) {
        val config = _adConfigState.value
        if (!config.adsEnabled || config.admob?.enabled != true) return
        if (isAdMobInterstitialLoading || adMobInterstitialAd != null) return

        isAdMobInterstitialLoading = true
        val isTestMode = config.unity?.testMode == true
        val adUnitId = if (isTestMode) ADMOB_TEST_INTERSTITIAL else config.admob.interstitialId?.takeIf { it.isNotBlank() } ?: ADMOB_TEST_INTERSTITIAL
        
        InterstitialAd.load(context, adUnitId, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                isAdMobInterstitialLoading = false
                adMobInterstitialAd = ad
            }
            override fun onAdFailedToLoad(error: LoadAdError) {
                isAdMobInterstitialLoading = false
                adMobInterstitialAd = null
            }
        })
    }

    private fun preloadAdMobRewarded(context: Context) {
        val config = _adConfigState.value
        if (!config.adsEnabled || config.admob?.enabled != true) return
        if (isAdMobRewardedLoading || adMobRewardedAd != null) return

        isAdMobRewardedLoading = true
        val isTestMode = config.unity?.testMode == true
        val adUnitId = if (isTestMode) ADMOB_TEST_REWARDED else config.admob.rewardedId?.takeIf { it.isNotBlank() } ?: ADMOB_TEST_REWARDED

        RewardedAd.load(context, adUnitId, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                isAdMobRewardedLoading = false
                adMobRewardedAd = ad
            }
            override fun onAdFailedToLoad(error: LoadAdError) {
                isAdMobRewardedLoading = false
                adMobRewardedAd = null
            }
        })
    }

    private fun preloadUnityRewarded(context: Context) {
        val config = _adConfigState.value
        val placementId = config.unity?.rewardedId?.takeIf { it.isNotBlank() } ?: "Rewarded_Android"
        if (UnityAds.isInitialized) {
            UnityAds.load(placementId, object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(placementId: String) { isUnityAdLoaded = true }
                override fun onUnityAdsFailedToLoad(placementId: String, error: UnityAds.UnityAdsLoadError, message: String) { isUnityAdLoaded = false }
            })
        }
    }

    private fun preloadUnityInterstitial(context: Context) {
        val config = _adConfigState.value
        val placementId = config.unity?.interstitialId?.takeIf { it.isNotBlank() } ?: "Interstitial_Android"
        if (UnityAds.isInitialized) {
            UnityAds.load(placementId, object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(placementId: String) { isUnityInterstitialLoaded = true }
                override fun onUnityAdsFailedToLoad(placementId: String, error: UnityAds.UnityAdsLoadError, message: String) { isUnityInterstitialLoaded = false }
            })
        }
    }

    private fun preloadStartIoRewarded(context: Context) {
        val config = _adConfigState.value
        if (!config.adsEnabled || config.startio?.enabled != true) return
        if (isStartIoRewardedLoading && startIoRewardedAd != null) return

        isStartIoRewardedLoading = true
        try {
            val act = context.findActivity() ?: context
            val ad = StartAppAd(act)
            ad.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener {
                override fun onReceiveAd(receivedAd: Ad) {
                    isStartIoRewardedLoading = false
                    startIoRewardedAd = ad
                }
                override fun onFailedToReceiveAd(failedAd: Ad?) {
                    isStartIoRewardedLoading = false
                }
            })
        } catch (t: Throwable) {
            isStartIoRewardedLoading = false
        }
    }

    fun preloadInterstitial(context: Context) {
        preloadAdMobInterstitial(context)
        preloadUnityInterstitial(context)

        val config = _adConfigState.value
        if (!config.adsEnabled || config.startio?.enabled != true) return
        if (isStartIoInterstitialLoading && startIoInterstitialAd != null) return

        isStartIoInterstitialLoading = true
        try {
            val act = context.findActivity() ?: context
            val ad = StartAppAd(act)
            ad.loadAd(StartAppAd.AdMode.AUTOMATIC, object : AdEventListener {
                override fun onReceiveAd(receivedAd: Ad) {
                    isStartIoInterstitialLoading = false
                    startIoInterstitialAd = ad
                }
                override fun onFailedToReceiveAd(failedAd: Ad?) {
                    isStartIoInterstitialLoading = false
                }
            })
        } catch (t: Throwable) {
            isStartIoInterstitialLoading = false
        }
    }

    fun preloadRewardedVideo(context: Context, onLoaded: (() -> Unit)? = null, onFailed: ((String) -> Unit)? = null) {
        preloadAdMobRewarded(context)
        preloadUnityRewarded(context)
        preloadStartIoRewarded(context)
    }

    // ============================================================
    // 🌐 ADSTERRA POPUNDER & DIRECT LINK
    // ============================================================
    fun showPopunderIfEligible(context: Context, isVip: Boolean) {
        val config = _adConfigState.value
        if (isVip || !config.adsEnabled || config.adsterra?.enabled != true) return

        val adsterra = config.adsterra ?: return
        val popunderUrl = adsterra.popunderUrl?.trim()
        if (popunderUrl.isNullOrBlank() || (!popunderUrl.startsWith("http://") && !popunderUrl.startsWith("https://"))) return

        pageTransitionCount++
        val targetFreq = (adsterra.popunderFrequency).coerceAtLeast(1)
        val minIntervalMs = (adsterra.popunderMinIntervalSeconds).coerceAtLeast(5) * 1000L
        val currentTime = System.currentTimeMillis()

        if (pageTransitionCount % targetFreq == 0 && (currentTime - lastPopunderTimestamp) >= minIntervalMs) {
            lastPopunderTimestamp = currentTime
            openChromeCustomTab(context, popunderUrl)
        }
    }

    fun openAdsterraDirectLink(
        context: Context,
        isVip: Boolean,
        fallbackUrl: String? = null
    ): Boolean {
        val config = _adConfigState.value
        if (isVip || !config.adsEnabled || config.adsterra?.enabled != true) return false

        val adsterra = config.adsterra
        val targetUrl = adsterra?.effectiveDirectLink?.trim()?.takeIf { it.isNotBlank() } ?: fallbackUrl
        if (targetUrl.isNullOrBlank() || (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://"))) return false

        return openChromeCustomTab(context, targetUrl)
    }

    fun openSmartlink(
        context: Context,
        isVip: Boolean,
        fallbackUrl: String? = null
    ): Boolean = openAdsterraDirectLink(context, isVip, fallbackUrl)

    // 🎯 HELPER FUNCTIONS (Restored for Missing Method Compilation Errors)
    fun isAdsGloballyEnabled(): Boolean = _adConfigState.value.adsEnabled
    fun isAdsterraPrimary(): Boolean = _adConfigState.value.primaryNetwork.contains("adsterra", ignoreCase = true)
    fun isAdMobPrimary(): Boolean = _adConfigState.value.primaryNetwork.contains("admob", ignoreCase = true)
    fun isStartIoPrimary(): Boolean = _adConfigState.value.primaryNetwork.contains("start", ignoreCase = true)
    fun isUnityPrimary(): Boolean = _adConfigState.value.primaryNetwork.contains("unity", ignoreCase = true)

    fun isDirectLinkAvailable(isVip: Boolean = false): Boolean {
        val config = _adConfigState.value
        if (isVip || !config.adsEnabled) return false
        val adsterra = config.adsterra ?: return false
        return adsterra.enabled && !adsterra.effectiveDirectLink.isNullOrBlank()
    }

    fun isSmartlinkAvailable(isVip: Boolean = false): Boolean = isDirectLinkAvailable(isVip)
    fun getEffectiveDirectLink(): String? = if (_adConfigState.value.adsEnabled) _adConfigState.value.adsterra?.effectiveDirectLink else null
    fun getSmartlinkUrl(): String? = getEffectiveDirectLink()
    fun getVerificationTimerSeconds(): Int = _adConfigState.value.rules?.timerSeconds ?: 10
    fun getUnlockDurationHours(): Int = _adConfigState.value.rules?.rewardedUnlockHours ?: 2
    fun getFreeUnlockedEpisodesCount(): Int = _adConfigState.value.rules?.freeUnlockedEpisodes ?: 1

    fun openUrlSafely(context: Context, url: String): Boolean {
        return try {
            val uri = Uri.parse(url)
            val intent = Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
            true
        } catch (t: Throwable) {
            false
        }
    }
}

/**
 * ============================================================
 * 📱 UNIFIED AD BANNER COMPOSABLE
 * ============================================================
 */
@Composable
fun UnifiedAdBanner(
    isVip: Boolean,
    modifier: Modifier = Modifier
) {
    val adConfig by UnifiedAdManager.adConfigState.collectAsState()

    if (isVip || !adConfig.adsEnabled) {
        Spacer(modifier = Modifier.size(0.dp))
        return
    }

    val isAdMobOn = adConfig.admob?.enabled == true
    val isStartIoOn = adConfig.startio?.enabled == true

    if (!isAdMobOn && !isStartIoOn) {
        Spacer(modifier = Modifier.size(0.dp))
        return
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isAdMobOn) {
            val isTestMode = adConfig.unity?.testMode == true
            val bannerId = if (isTestMode) "ca-app-pub-3940256099942544/6300978111" else adConfig.admob?.bannerId?.takeIf { it.isNotBlank() } ?: "ca-app-pub-3940256099942544/6300978111"

            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                factory = { ctx ->
                    AdView(ctx).apply {
                        setAdSize(AdSize.BANNER)
                        adUnitId = bannerId
                        loadAd(AdRequest.Builder().build())
                    }
                },
                onRelease = { adView ->
                    adView.destroy()
                }
            )
        } else if (isStartIoOn) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                factory = { ctx ->
                    try {
                        val activity = ctx.findActivity() ?: ctx
                        Banner(activity).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                        }
                    } catch (t: Throwable) {
                        View(ctx)
                    }
                }
            )
        }
    }
}
