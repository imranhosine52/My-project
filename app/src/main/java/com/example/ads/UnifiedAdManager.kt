package com.example.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.*
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

/**
 * 🔍 Context থেকে Activity খুঁজে নেওয়ার সেফ হেলপার
 */
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
 * 📡 REMOTE DYNAMIC MULTI-NETWORK AD MEDIATION ENGINE
 * ============================================================
 * UnifiedAdManager
 * Centralized ad orchestration engine supporting dynamic remote config,
 * multi-network mediation (Unity Ads, Start.io, Adsterra, AdMob),
 * automated fallbacks, and zero-latency VIP member bypass.
 */
object UnifiedAdManager {
    private const val TAG = "UnifiedAdManager"

    // Default Fallback Configurations
    private const val DEFAULT_UNITY_GAME_ID = "800364838"
    private const val DEFAULT_STARTIO_APP_ID = "207238360"
    private const val DEFAULT_STARTIO_PUB_ID = "113502454"

    // 📡 লাইভ রিমোট কনফিগারেশন স্টেট
    private val _adConfigState = MutableStateFlow(
        AdsConfigResponse(
            success = true,
            status = 200,
            adsEnabled = true,
            primaryNetwork = "unity", // 👈 ডিফল্ট প্রাইমারি: Unity Ads
            fallbackNetwork = "startio", // 👈 ফলব্যাক: Start.io
            unity = UnityAdsConfig(
                enabled = true,
                gameId = DEFAULT_UNITY_GAME_ID,
                rewardedId = "Rewarded_Android",
                interstitialId = "Interstitial_Android",
                bannerId = "Banner_Android",
                testMode = false
            ),
            startio = StartIoConfig(
                enabled = true,
                appId = DEFAULT_STARTIO_APP_ID,
                publisherId = DEFAULT_STARTIO_PUB_ID
            ),
            adsterra = AdsterraConfig(enabled = true),
            admob = AdMobConfig(enabled = false),
            rules = AdRulesConfig(timerSeconds = 10, rewardedUnlockHours = 2, freeUnlockedEpisodes = 1)
        )
    )
    val adConfigState: StateFlow<AdsConfigResponse> = _adConfigState.asStateFlow()

    private var isStartIoInitialized = false
    private var isUnityInitialized = false

    // Start.io In-Memory Preloaded Ads
    private var startIoInterstitialAd: StartAppAd? = null
    private var startIoRewardedAd: StartAppAd? = null
    private var isStartIoInterstitialLoading = false
    private var isStartIoRewardedLoading = false

    // Rate Limiting
    private var pageTransitionCount = 0
    private var lastPopunderTimestamp = 0L

    // In-App Browser State (for Adsterra Direct Link / Smartlink)
    data class InAppBrowserRequest(
        val url: String,
        val title: String = "Sponsored Offer",
        val verificationSeconds: Int? = null,
        val onVerified: (() -> Unit)? = null
    )

    private val _inAppBrowserRequest = MutableStateFlow<InAppBrowserRequest?>(null)
    val inAppBrowserRequest: StateFlow<InAppBrowserRequest?> = _inAppBrowserRequest.asStateFlow()

    fun openInAppBrowser(
        url: String,
        title: String = "Sponsored Offer",
        verificationSeconds: Int? = null,
        onVerified: (() -> Unit)? = null
    ) {
        _inAppBrowserRequest.value = InAppBrowserRequest(
            url = url,
            title = title,
            verificationSeconds = verificationSeconds,
            onVerified = onVerified
        )
    }

    fun closeInAppBrowser() {
        _inAppBrowserRequest.value = null
    }

    /**
     * ১. অ্যাপ চালু হওয়ার সময় অ্যাড নেটওয়ার্ক ইনিশিয়ালাইজেশন
     */
    fun init(context: Context, initialConfig: AdsConfigResponse? = null, isVip: Boolean = false) {
        if (initialConfig != null) {
            _adConfigState.value = initialConfig
        }

        val config = _adConfigState.value

        // 🔴 মাস্টার সুইচ: যদি অ্যাড বন্ধ থাকে বা ইউজার VIP হয়
        if (!config.adsEnabled || isVip) {
            Log.i(TAG, "Ads globally disabled or user is VIP. Suppressing ad SDKs.")
            return
        }

        // Unity Ads ইনিশিয়ালাইজ
        val unityGameId = config.unity?.gameId?.takeIf { it.isNotBlank() } ?: DEFAULT_UNITY_GAME_ID
        val unityTestMode = config.unity?.testMode ?: false
        initUnityAds(context, unityGameId, unityTestMode)

        // Start.io ইনিশিয়ালাইজ
        val startIoAppId = config.startio?.appId?.takeIf { it.isNotBlank() } ?: DEFAULT_STARTIO_APP_ID
        initStartIo(context, startIoAppId)
    }

    /**
     * ২. সার্ভার থেকে পাওয়া নতুন কনফিগারেশন ডায়নামিকালি অ্যাপ্লাই
     */
    fun applyRemoteConfig(context: Context, newConfig: AdsConfigResponse, isVip: Boolean = false) {
        _adConfigState.value = newConfig
        Log.i(TAG, "📡 Applied Remote Ads Config: MasterEnabled=${newConfig.adsEnabled}, Primary=${newConfig.primaryNetwork}")

        if (!newConfig.adsEnabled || isVip) {
            return
        }

        val unityGameId = newConfig.unity?.gameId?.takeIf { it.isNotBlank() } ?: DEFAULT_UNITY_GAME_ID
        val unityTestMode = newConfig.unity?.testMode ?: false
        initUnityAds(context, unityGameId, unityTestMode)

        val startIoAppId = newConfig.startio?.appId?.takeIf { it.isNotBlank() } ?: DEFAULT_STARTIO_APP_ID
        initStartIo(context, startIoAppId)

        preloadInterstitial(context)
        preloadRewardedVideo(context)
    }

    private fun initUnityAds(context: Context, gameId: String, testMode: Boolean) {
        try {
            if (!isUnityInitialized && gameId.isNotBlank()) {
                UnityAds.initialize(
                    context.applicationContext,
                    gameId,
                    testMode,
                    object : IUnityAdsInitializationListener {
                        override fun onInitializationComplete() {
                            isUnityInitialized = true
                            Log.i(TAG, "✓ Unity Ads SDK Initialized Successfully (Game ID: $gameId, TestMode: $testMode)")
                        }

                        override fun onInitializationFailed(error: UnityAds.UnityAdsInitializationError?, message: String?) {
                            Log.e(TAG, "Unity Ads Init Failed: $message")
                        }
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unity Ads Init Exception: ${e.message}")
        }
    }

    private fun initStartIo(context: Context, appId: String) {
        try {
            if (!isStartIoInitialized && appId.isNotBlank()) {
                StartAppSDK.init(context.applicationContext, appId, false)
                StartAppSDK.setTestAdsEnabled(false)
                StartAppAd.disableSplash()
                StartAppSDK.enableReturnAds(false)
                isStartIoInitialized = true
                Log.i(TAG, "✓ Start.io SDK Initialized (App ID: $appId)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Start.io Init Exception: ${e.message}")
        }
    }

    // ============================================================
    // 🎁 REWARDED VIDEO ADS (UNITY -> START.IO -> ADSTERRA FALLBACK)
    // ============================================================

    /**
     * UI থেকে সরাসরি ১ লাইনে কল করার জন্য মেথড
     */
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

    /**
     * সম্পূর্ণ রিওয়ার্ডেড অ্যাড ও মাল্টি-নেটওয়ার্ক ফলব্যাক লজিক
     */
    fun showRewardedVideo(
        context: Context,
        isVip: Boolean,
        onRewardUnlocked: () -> Unit,
        onAdNotReadyOrFailed: ((reason: String) -> Unit)? = null,
        onAdClosed: ((rewardEarned: Boolean) -> Unit)? = null
    ) {
        val config = _adConfigState.value

        // 🔴 মাস্টার সুইচ চেক: অ্যাড বন্ধ থাকলে সরাসরি আনলক
        if (isVip || !config.adsEnabled) {
            Log.d(TAG, "VIP/No-Ad Bypass: Reward granted instantly without ads.")
            onRewardUnlocked()
            onAdClosed?.invoke(true)
            return
        }

        val activity = context.findActivity()
        if (activity == null) {
            Log.w(TAG, "No valid Activity context for Rewarded Ad.")
            onAdNotReadyOrFailed?.invoke("Screen context not ready. Please try again.")
            onAdClosed?.invoke(false)
            return
        }

        val primary = config.primaryNetwork.lowercase()

        // 🎯 ১. প্রাইমারি নেটওয়ার্ক অনুযায়ী অ্যাড প্লে করা
        when (primary) {
            "unity" -> {
                showUnityRewardedVideo(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
            }
            "startio" -> {
                showStartIoRewardedVideoWithFallback(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
            }
            "adsterra" -> {
                val opened = openAdsterraDirectLink(activity, isVip = false, verificationSeconds = 10, onVerified = {
                    onRewardUnlocked()
                    onAdClosed?.invoke(true)
                })
                if (!opened) {
                    onAdNotReadyOrFailed?.invoke("Adsterra link unavailable.")
                    onAdClosed?.invoke(false)
                }
            }
            else -> {
                showUnityRewardedVideo(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
            }
        }
    }

    // 🎮 Unity Rewarded Video
    private fun showUnityRewardedVideo(
        activity: Activity,
        onRewardUnlocked: () -> Unit,
        onAdNotReadyOrFailed: ((reason: String) -> Unit)?,
        onAdClosed: ((rewardEarned: Boolean) -> Unit)?
    ) {
        val config = _adConfigState.value
        val placementId = config.unity?.rewardedId?.takeIf { it.isNotBlank() } ?: "Rewarded_Android"

        Log.d(TAG, "Loading Unity Rewarded Video (Placement: $placementId)...")

        UnityAds.load(placementId, object : IUnityAdsLoadListener {
            override fun onUnityAdsAdLoaded(placementId: String?) {
                Log.i(TAG, "✓ Unity Rewarded Video Loaded. Displaying now...")
                UnityAds.show(activity, placementId, UnityAdsShowOptions(), object : IUnityAdsShowListener {
                    override fun onUnityAdsShowStart(placementId: String?) {}
                    override fun onUnityAdsShowClick(placementId: String?) {}

                    override fun onUnityAdsShowComplete(placementId: String?, state: UnityAds.UnityAdsShowCompletionState?) {
                        if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                            Log.i(TAG, "✓ Unity Video Completed! Reward confirmed.")
                            onRewardUnlocked()
                            onAdClosed?.invoke(true)
                        } else {
                            Log.w(TAG, "Unity Video was skipped before completion. No reward.")
                            onAdClosed?.invoke(false)
                        }
                    }

                    override fun onUnityAdsShowFailure(placementId: String?, error: UnityAds.UnityAdsShowError?, message: String?) {
                        Log.w(TAG, "Unity Show Failed ($message). Triggering Start.io Fallback...")
                        showStartIoRewardedVideoWithFallback(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
                    }
                })
            }

            override fun onUnityAdsFailedToLoad(placementId: String?, error: UnityAds.UnityAdsLoadError?, message: String?) {
                Log.w(TAG, "Unity Load Failed ($message). Triggering Start.io Fallback...")
                showStartIoRewardedVideoWithFallback(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
            }
        })
    }

    // 🎯 Start.io Rewarded Video
    private fun showStartIoRewardedVideoWithFallback(
        activity: Activity,
        onRewardUnlocked: () -> Unit,
        onAdNotReadyOrFailed: ((reason: String) -> Unit)?,
        onAdClosed: ((rewardEarned: Boolean) -> Unit)?
    ) {
        try {
            val onDemandAd = StartAppAd(activity)
            var userEarnedReward = false

            onDemandAd.setVideoListener(object : VideoListener {
                override fun onVideoCompleted() {
                    Log.i(TAG, "✓ Start.io Rewarded Video completed!")
                    userEarnedReward = true
                }
            })

            onDemandAd.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener {
                override fun onReceiveAd(loadedAd: Ad) {
                    onDemandAd.showAd(object : AdDisplayListener {
                        override fun adHidden(shownAd: Ad) {
                            if (userEarnedReward) {
                                onRewardUnlocked()
                            }
                            onAdClosed?.invoke(userEarnedReward)
                            preloadRewardedVideo(activity)
                        }
                        override fun adDisplayed(shownAd: Ad) {}
                        override fun adClicked(shownAd: Ad) {}
                        override fun adNotDisplayed(shownAd: Ad) {
                            onAdNotReadyOrFailed?.invoke("Start.io ad display failed.")
                            onAdClosed?.invoke(false)
                        }
                    })
                }

                override fun onFailedToReceiveAd(failedAd: Ad?) {
                    Log.w(TAG, "Start.io Rewarded returned NO FILL. Offering Adsterra 10s Timer Unlock Fallback...")

                    // 🌐 ৩য় ফলব্যাক: Adsterra Smartlink / 10s Timer Unlock
                    val opened = openAdsterraDirectLink(activity, isVip = false, verificationSeconds = 10, onVerified = {
                        onRewardUnlocked()
                        onAdClosed?.invoke(true)
                    })

                    if (!opened) {
                        onAdNotReadyOrFailed?.invoke("Ad servers are busy. Please try again in a moment.")
                        onAdClosed?.invoke(false)
                    }
                }
            })
        } catch (t: Throwable) {
            Log.e(TAG, "Start.io Rewarded Error: ${t.message}")
            onAdNotReadyOrFailed?.invoke("Ad error: ${t.localizedMessage}")
            onAdClosed?.invoke(false)
        }
    }

    // ============================================================
    // 🌐 ADSTERRA POPUNDER & SMARTLINK INTEGRATION
    // ============================================================

    fun showPopunderIfEligible(context: Context, isVip: Boolean) {
        val config = _adConfigState.value
        if (isVip || !config.adsEnabled) return

        val adsterra = config.adsterra ?: return
        if (!adsterra.enabled) return

        val popunderUrl = adsterra.popunderUrl?.trim()
        if (popunderUrl.isNullOrBlank() || (!popunderUrl.startsWith("http://") && !popunderUrl.startsWith("https://"))) {
            return
        }

        pageTransitionCount++
        val targetFreq = (adsterra.popunderFrequency).coerceAtLeast(1)
        val minIntervalMs = (adsterra.popunderMinIntervalSeconds).coerceAtLeast(5) * 1000L
        val currentTime = System.currentTimeMillis()

        if (pageTransitionCount % targetFreq == 0 && (currentTime - lastPopunderTimestamp) >= minIntervalMs) {
            lastPopunderTimestamp = currentTime
            openInAppBrowser(url = popunderUrl, title = "Sponsored Partner")
        }
    }

    fun openAdsterraDirectLink(
        context: Context,
        isVip: Boolean,
        fallbackUrl: String? = null,
        verificationSeconds: Int? = null,
        onVerified: (() -> Unit)? = null
    ): Boolean {
        val config = _adConfigState.value
        if (isVip || !config.adsEnabled) return false

        val adsterra = config.adsterra
        val targetUrl = adsterra?.effectiveDirectLink?.trim()?.takeIf { it.isNotBlank() } ?: fallbackUrl

        if (targetUrl.isNullOrBlank() || (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://"))) {
            return false
        }

        openInAppBrowser(url = targetUrl, title = "Sponsored Ad", verificationSeconds = verificationSeconds, onVerified = onVerified)
        return true
    }

    fun openSmartlink(
        context: Context,
        isVip: Boolean,
        fallbackUrl: String? = null,
        verificationSeconds: Int? = null,
        onVerified: (() -> Unit)? = null
    ): Boolean = openAdsterraDirectLink(context, isVip, fallbackUrl, verificationSeconds, onVerified)

    fun isDirectLinkAvailable(isVip: Boolean): Boolean {
        val config = _adConfigState.value
        if (isVip || !config.adsEnabled) return false
        val adsterra = config.adsterra ?: return false
        return adsterra.enabled && !adsterra.effectiveDirectLink.isNullOrBlank()
    }

    fun isSmartlinkAvailable(isVip: Boolean): Boolean = isDirectLinkAvailable(isVip)
    fun getEffectiveDirectLink(): String? = _adConfigState.value.adsterra?.effectiveDirectLink
    fun getSmartlinkUrl(): String? = getEffectiveDirectLink()
    fun getVerificationTimerSeconds(): Int = _adConfigState.value.rules?.timerSeconds ?: 10
    fun isAdsterraPrimary(): Boolean = _adConfigState.value.primaryNetwork.equals("adsterra", ignoreCase = true)
    fun isStartIoPrimary(): Boolean = _adConfigState.value.primaryNetwork.equals("startio", ignoreCase = true)
    fun isUnityPrimary(): Boolean = _adConfigState.value.primaryNetwork.equals("unity", ignoreCase = true)
    fun getUnlockDurationHours(): Int = _adConfigState.value.rules?.rewardedUnlockHours ?: 2
    fun getFreeUnlockedEpisodesCount(): Int = _adConfigState.value.rules?.freeUnlockedEpisodes ?: 1
    fun isAdsGloballyEnabled(): Boolean = _adConfigState.value.adsEnabled

    // ============================================================
    // 🎬 INTERSTITIAL ADS MEDIATION
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

        val activity = context.findActivity() ?: run { onComplete(); return }
        val primary = config.primaryNetwork.lowercase()

        if (primary == "unity") {
            val placementId = config.unity?.interstitialId?.takeIf { it.isNotBlank() } ?: "Interstitial_Android"
            UnityAds.load(placementId, object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(placementId: String?) {
                    UnityAds.show(activity, placementId, UnityAdsShowOptions(), object : IUnityAdsShowListener {
                        override fun onUnityAdsShowStart(placementId: String?) {}
                        override fun onUnityAdsShowClick(placementId: String?) {}
                        override fun onUnityAdsShowComplete(placementId: String?, state: UnityAds.UnityAdsShowCompletionState?) { onComplete() }
                        override fun onUnityAdsShowFailure(placementId: String?, error: UnityAds.UnityAdsShowError?, message: String?) { onComplete() }
                    })
                }
                override fun onUnityAdsFailedToLoad(placementId: String?, error: UnityAds.UnityAdsLoadError?, message: String?) {
                    // Fallback to Start.io Interstitial
                    showStartIoInterstitial(context, onComplete)
                }
            })
        } else {
            showStartIoInterstitial(context, onComplete)
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

    fun preloadInterstitial(context: Context) {
        val config = _adConfigState.value
        if (!config.adsEnabled) return

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

    fun preloadRewardedVideo(
        context: Context,
        onLoaded: (() -> Unit)? = null,
        onFailed: ((String) -> Unit)? = null
    ) {
        val config = _adConfigState.value
        if (!config.adsEnabled) return

        if (isStartIoRewardedLoading && startIoRewardedAd != null) return
        isStartIoRewardedLoading = true

        try {
            val act = context.findActivity() ?: context
            val ad = StartAppAd(act)
            ad.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener {
                override fun onReceiveAd(receivedAd: Ad) {
                    isStartIoRewardedLoading = false
                    startIoRewardedAd = ad
                    onLoaded?.invoke()
                }

                override fun onFailedToReceiveAd(failedAd: Ad?) {
                    isStartIoRewardedLoading = false
                    onFailed?.invoke(failedAd?.errorMessage ?: "Start.io load error")
                }
            })
        } catch (t: Throwable) {
            isStartIoRewardedLoading = false
            onFailed?.invoke(t.message ?: "Unknown error")
        }
    }
}

// ============================================================
// 📱 UNIFIED AD BANNER COMPOSABLE
// ============================================================
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            factory = { ctx ->
                try {
                    val activity = ctx.findActivity() ?: ctx
                    Banner(activity).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        setBannerListener(object : BannerListener {
                            override fun onReceiveAd(banner: View) {}
                            override fun onFailedToReceiveAd(banner: View) {}
                            override fun onClick(banner: View) {}
                            override fun onImpression(banner: View) {}
                        })
                    }
                } catch (t: Throwable) {
                    View(ctx)
                }
            },
            onRelease = { bannerView ->
                try {
                    if (bannerView is Banner) {
                        bannerView.hideBanner()
                    }
                } catch (_: Throwable) {}
            }
        )
    }
}
