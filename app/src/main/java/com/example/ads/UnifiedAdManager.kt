package com.example.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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

// 🔍 Context থেকে Activity খুঁজে নেওয়ার সেফ গ্লোবাল হেলপার
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
 * 📡 REMOTE DYNAMIC MULTI-NETWORK AD MEDIATION ARCHITECTURE
 * ============================================================
 */
object UnifiedAdManager {
    private const val TAG = "UnifiedAdManager"

    // Default Fallback Configurations
    private const val DEFAULT_UNITY_GAME_ID = "800364838"
    private const val DEFAULT_STARTIO_APP_ID = "207238360"
    private const val DEFAULT_STARTIO_PUB_ID = "113502454"

    // Observable Live Ad Configuration State
    private val _adConfigState = MutableStateFlow(
        AdsConfigResponse(
            success = true,
            status = 200,
            adsEnabled = true,
            primaryNetwork = "unity",
            fallbackNetwork = "startio",
            unity = UnityAdsConfig(
                enabled = true,
                gameId = DEFAULT_UNITY_GAME_ID,
                rewardedId = "Rewarded_Android",
                interstitialId = "Interstitial_Android",
                bannerId = "Banner_Android",
                testMode = true // 👈 টেস্ট অ্যাড পাওয়ার জন্য true করা হয়েছে
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

    // Current State flags
    private var isStartIoInitialized = false
    private var isUnityInitialized = false
    private var currentStartIoAppId: String = DEFAULT_STARTIO_APP_ID

    // Start.io In-Memory Preloaded Ads
    private var startIoInterstitialAd: StartAppAd? = null
    private var startIoRewardedAd: StartAppAd? = null
    private var isStartIoInterstitialLoading = false
    private var isStartIoRewardedLoading = false

    // State flags for Adsterra Popunder & Smartlink Rate Limiting
    private var pageTransitionCount = 0
    private var lastPopunderTimestamp = 0L

    // In-App Browser State
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
     * 1. Initialize Ad Networks dynamically based on configuration
     */
    fun init(context: Context, initialConfig: AdsConfigResponse? = null, isVip: Boolean = false) {
        if (initialConfig != null) {
            _adConfigState.value = initialConfig
        }

        val config = _adConfigState.value

        // Master switch: if ads disabled completely or user is VIP, suppress initialization
        if (!config.adsEnabled || isVip) {
            Log.i(TAG, "Ads are globally disabled or user is VIP. Suppressing ad initialization.")
            return
        }

        // Initialize Unity Ads
        if (config.unity?.enabled == true) {
            val unityGameId = config.unity.gameId?.takeIf { it.isNotBlank() } ?: DEFAULT_UNITY_GAME_ID
            initUnityAds(context, unityGameId, true) // Force testMode true for guaranteed fill
        }

        // Initialize Start.io
        if (config.startio?.enabled == true) {
            val startIoAppId = config.startio.appId.takeIf { it.isNotBlank() } ?: DEFAULT_STARTIO_APP_ID
            initializeStartIo(context, startIoAppId, isVip)
        }
    }

    /**
     * 2. Apply Dynamic Remote Config updates fetched from REST API
     */
    fun applyRemoteConfig(context: Context, newConfig: AdsConfigResponse, isVip: Boolean = false) {
        _adConfigState.value = newConfig
        Log.i(TAG, "📡 Applied Remote Ads Config: Primary=${newConfig.primaryNetwork}, Fallback=${newConfig.fallbackNetwork}, AdsEnabled=${newConfig.adsEnabled}")

        if (!newConfig.adsEnabled || isVip) {
            Log.d(TAG, "Ads disabled in updated config or VIP active.")
            return
        }

        // Apply Unity Config
        if (newConfig.unity?.enabled == true) {
            val unityGameId = newConfig.unity.gameId?.takeIf { it.isNotBlank() } ?: DEFAULT_UNITY_GAME_ID
            initUnityAds(context, unityGameId, true)
        }

        // Apply Start.io Config
        val newAppId = newConfig.startio?.appId?.takeIf { it.isNotBlank() } ?: DEFAULT_STARTIO_APP_ID
        if (newConfig.startio?.enabled == true) {
            if (newAppId != currentStartIoAppId || !isStartIoInitialized) {
                initializeStartIo(context, newAppId, isVip)
            } else {
                preloadInterstitial(context)
                preloadRewardedVideo(context)
            }
        }
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
                            Log.i(TAG, "✓ Unity Ads SDK initialized with Game ID: $gameId (TestMode: $testMode)")
                        }

                        override fun onInitializationFailed(error: UnityAds.UnityAdsInitializationError, message: String) {
                            isUnityInitialized = false
                            Log.e(TAG, "Failed to init Unity Ads SDK: $message")
                        }
                    }
                )
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error initializing Unity Ads: ${t.message}")
        }
    }

    private fun initializeStartIo(context: Context, appId: String, isVip: Boolean) {
        try {
            currentStartIoAppId = appId
            StartAppSDK.init(context.applicationContext, appId, false)
            StartAppSDK.setTestAdsEnabled(false)
            StartAppAd.disableSplash()
            StartAppSDK.enableReturnAds(false)
            isStartIoInitialized = true
            Log.i(TAG, "✓ Start.io SDK initialized with App ID: $appId")

            if (!isVip && _adConfigState.value.adsEnabled) {
                val act = context.findActivity() ?: context
                preloadInterstitial(act)
                preloadRewardedVideo(act)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to init Start.io SDK: ${t.message}")
        }
    }

    // ============================================================
    // 🌐 ADSTERRA POPUNDER & SMARTLINK INTEGRATION
    // ============================================================

    fun showPopunderIfEligible(context: Context, isVip: Boolean) {
        val config = _adConfigState.value

        if (isVip || !config.adsEnabled || config.adsterra?.enabled != true) {
            return
        }

        val adsterra = config.adsterra ?: return
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
            Log.i(TAG, "🌐 Triggering Adsterra Popunder in In-App Browser (Transition #$pageTransitionCount): $popunderUrl")
            openInAppBrowser(
                url = popunderUrl,
                title = "Sponsored Partner"
            )
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

        if (isVip || !config.adsEnabled || config.adsterra?.enabled != true) {
            return false
        }

        val adsterra = config.adsterra
        val targetUrl = adsterra?.effectiveDirectLink?.trim()?.takeIf { it.isNotBlank() } ?: fallbackUrl

        if (targetUrl.isNullOrBlank() || (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://"))) {
            Log.w(TAG, "No valid Adsterra Direct Link configured.")
            return false
        }

        Log.i(TAG, "🌐 Opening Adsterra Direct Link (In-App Browser): $targetUrl")
        openInAppBrowser(
            url = targetUrl,
            title = "Sponsored Ad",
            verificationSeconds = verificationSeconds,
            onVerified = onVerified
        )
        return true
    }

    fun openSmartlink(
        context: Context,
        isVip: Boolean,
        fallbackUrl: String? = null,
        verificationSeconds: Int? = null,
        onVerified: (() -> Unit)? = null
    ): Boolean {
        return openAdsterraDirectLink(context, isVip, fallbackUrl, verificationSeconds, onVerified)
    }

    // 🌟 Compatibility Methods
    fun isAdsterraPrimary(): Boolean {
        return _adConfigState.value.primaryNetwork.equals("adsterra", ignoreCase = true)
    }

    fun isStartIoPrimary(): Boolean {
        return _adConfigState.value.primaryNetwork.equals("startio", ignoreCase = true)
    }

    fun isUnityPrimary(): Boolean {
        return _adConfigState.value.primaryNetwork.equals("unity", ignoreCase = true)
    }

    fun isDirectLinkAvailable(isVip: Boolean = false): Boolean {
        val config = _adConfigState.value
        if (isVip || !config.adsEnabled) return false
        val adsterra = config.adsterra ?: return false
        return adsterra.enabled && !adsterra.effectiveDirectLink.isNullOrBlank()
    }

    fun isSmartlinkAvailable(isVip: Boolean = false): Boolean {
        return isDirectLinkAvailable(isVip)
    }

    fun getEffectiveDirectLink(): String? {
        val config = _adConfigState.value
        if (!config.adsEnabled) return null
        val adsterra = config.adsterra ?: return null
        return if (adsterra.enabled) adsterra.effectiveDirectLink else null
    }

    fun getSmartlinkUrl(): String? {
        return getEffectiveDirectLink()
    }

    fun getVerificationTimerSeconds(): Int {
        return _adConfigState.value.rules?.timerSeconds ?: 10
    }

    fun openUrlSafely(context: Context, url: String): Boolean {
        return try {
            val uri = Uri.parse(url)
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to open link safely: ${t.message}")
            false
        }
    }

    fun getUnlockDurationHours(): Int {
        return _adConfigState.value.rules?.rewardedUnlockHours ?: 2
    }

    fun getFreeUnlockedEpisodesCount(): Int {
        return _adConfigState.value.rules?.freeUnlockedEpisodes ?: 1
    }

    fun isAdsGloballyEnabled(): Boolean {
        return _adConfigState.value.adsEnabled
    }

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

        val activity = context.findActivity() ?: run {
            onComplete()
            return
        }

        val isUnityOn = config.unity?.enabled == true
        val isStartIoOn = config.startio?.enabled == true

        if (isUnityOn) {
            val placementId = config.unity?.interstitialId?.takeIf { it.isNotBlank() } ?: "Interstitial_Android"
            UnityAds.load(placementId, object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(placementId: String) {
                    UnityAds.show(activity, placementId, UnityAdsShowOptions(), object : IUnityAdsShowListener {
                        override fun onUnityAdsShowStart(placementId: String) {}
                        override fun onUnityAdsShowClick(placementId: String) {}
                        override fun onUnityAdsShowComplete(placementId: String, state: UnityAds.UnityAdsShowCompletionState) {
                            onComplete()
                        }
                        override fun onUnityAdsShowFailure(placementId: String, error: UnityAds.UnityAdsShowError, message: String) {
                            if (isStartIoOn) showStartIoInterstitial(context, onComplete) else onComplete()
                        }
                    })
                }

                override fun onUnityAdsFailedToLoad(placementId: String, error: UnityAds.UnityAdsLoadError, message: String) {
                    if (isStartIoOn) showStartIoInterstitial(context, onComplete) else onComplete()
                }
            })
        } else if (isStartIoOn) {
            showStartIoInterstitial(context, onComplete)
        } else {
            onComplete()
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
                    Log.d(TAG, "✓ Start.io Interstitial preloaded successfully.")
                }

                override fun onFailedToReceiveAd(failedAd: Ad?) {
                    isStartIoInterstitialLoading = false
                }
            })
        } catch (t: Throwable) {
            isStartIoInterstitialLoading = false
        }
    }

    // ============================================================
    // 🎁 REWARDED VIDEO ADS MEDIATION
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
            onAdNotReadyOrFailed?.invoke("Screen context not ready. Please try again.")
            onAdClosed?.invoke(false)
            return
        }

        val isUnityOn = config.unity?.enabled == true
        val isStartIoOn = config.startio?.enabled == true

        if (isUnityOn) {
            showUnityRewardedVideo(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
        } else if (isStartIoOn) {
            showStartIoRewardedVideoWithFallback(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
        } else {
            onAdNotReadyOrFailed?.invoke("No ad networks available.")
            onAdClosed?.invoke(false)
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
        val gameId = config.unity?.gameId?.takeIf { it.isNotBlank() } ?: DEFAULT_UNITY_GAME_ID

        // যদি Unity Ads ইনিশিয়ালাইজ না হয়ে থাকে, তাৎক্ষণিক ইনিশিয়ালাইজ করে অ্যাড শো করা
        if (!UnityAds.isInitialized()) {
            Toast.makeText(activity, "Loading Ad...", Toast.LENGTH_SHORT).show()
            UnityAds.initialize(
                activity.applicationContext,
                gameId,
                true, // Force testMode true
                object : IUnityAdsInitializationListener {
                    override fun onInitializationComplete() {
                        isUnityInitialized = true
                        loadAndShowUnityAdDirect(activity, placementId, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
                    }

                    override fun onInitializationFailed(error: UnityAds.UnityAdsInitializationError, message: String) {
                        Log.e(TAG, "Unity Init Failed: [$error] $message")
                        activity.runOnUiThread {
                            Toast.makeText(activity, "Unity Error: $message", Toast.LENGTH_LONG).show()
                        }
                        handleUnityRewardFallback(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
                    }
                }
            )
        } else {
            loadAndShowUnityAdDirect(activity, placementId, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
        }
    }

    private fun loadAndShowUnityAdDirect(
        activity: Activity,
        placementId: String,
        onRewardUnlocked: () -> Unit,
        onAdNotReadyOrFailed: ((reason: String) -> Unit)?,
        onAdClosed: ((rewardEarned: Boolean) -> Unit)?
    ) {
        UnityAds.load(placementId, object : IUnityAdsLoadListener {
            override fun onUnityAdsAdLoaded(placementId: String) {
                UnityAds.show(activity, placementId, UnityAdsShowOptions(), object : IUnityAdsShowListener {
                    override fun onUnityAdsShowStart(placementId: String) {}
                    override fun onUnityAdsShowClick(placementId: String) {}

                    override fun onUnityAdsShowComplete(placementId: String, state: UnityAds.UnityAdsShowCompletionState) {
                        if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                            onRewardUnlocked()
                            onAdClosed?.invoke(true)
                        } else {
                            activity.runOnUiThread {
                                Toast.makeText(activity, "You must watch the full ad to unlock.", Toast.LENGTH_SHORT).show()
                            }
                            onAdClosed?.invoke(false)
                        }
                    }

                    override fun onUnityAdsShowFailure(placementId: String, error: UnityAds.UnityAdsShowError, message: String) {
                        Log.w(TAG, "Unity Show Failed ($message)")
                        handleUnityRewardFallback(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
                    }
                })
            }

            override fun onUnityAdsFailedToLoad(placementId: String, error: UnityAds.UnityAdsLoadError, message: String) {
                Log.w(TAG, "Unity Load Failed: [$error] $message")
                activity.runOnUiThread {
                    Toast.makeText(activity, "Unity Load Notice: $message", Toast.LENGTH_SHORT).show()
                }
                handleUnityRewardFallback(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
            }
        })
    }

    private fun handleUnityRewardFallback(
        activity: Activity,
        onRewardUnlocked: () -> Unit,
        onAdNotReadyOrFailed: ((reason: String) -> Unit)?,
        onAdClosed: ((rewardEarned: Boolean) -> Unit)?
    ) {
        val config = _adConfigState.value
        if (config.startio?.enabled == true) {
            showStartIoRewardedVideoWithFallback(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
        } else {
            onAdNotReadyOrFailed?.invoke("No ads available right now. Please try again.")
            onAdClosed?.invoke(false)
        }
    }

    private fun showStartIoRewardedVideoWithFallback(
        activity: Activity,
        onRewardUnlocked: () -> Unit,
        onAdNotReadyOrFailed: ((reason: String) -> Unit)?,
        onAdClosed: ((rewardEarned: Boolean) -> Unit)?
    ) {
        val config = _adConfigState.value
        if (config.startio?.enabled != true) {
            onAdNotReadyOrFailed?.invoke("Start.io is disabled.")
            return
        }

        try {
            val onDemandAd = StartAppAd(activity)
            var userEarnedReward = false

            onDemandAd.setVideoListener(object : VideoListener {
                override fun onVideoCompleted() {
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
                            onAdNotReadyOrFailed?.invoke("Ad could not be displayed.")
                            onAdClosed?.invoke(false)
                            preloadRewardedVideo(activity)
                        }
                    })
                }

                override fun onFailedToReceiveAd(failedAd: Ad?) {
                    onAdNotReadyOrFailed?.invoke("Ad failed to load. Please check internet connection.")
                    onAdClosed?.invoke(false)
                    preloadRewardedVideo(activity)
                }
            })
        } catch (t: Throwable) {
            onAdNotReadyOrFailed?.invoke("Ad error: ${t.localizedMessage ?: "Please try again"}")
            onAdClosed?.invoke(false)
            preloadRewardedVideo(activity)
        }
    }

    fun preloadRewardedVideo(
        context: Context,
        onLoaded: (() -> Unit)? = null,
        onFailed: ((String) -> Unit)? = null
    ) {
        val config = _adConfigState.value
        val placementId = config.unity?.rewardedId?.takeIf { it.isNotBlank() } ?: "Rewarded_Android"
        if (UnityAds.isInitialized()) {
            UnityAds.load(placementId, object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(placementId: String) {
                    onLoaded?.invoke()
                }

                override fun onUnityAdsFailedToLoad(placementId: String, error: UnityAds.UnityAdsLoadError, message: String) {
                    onFailed?.invoke(message)
                }
            })
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

    if (isVip || !adConfig.adsEnabled || adConfig.startio?.enabled != true) {
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
                        setBannerListener(object : BannerListener {
                            override fun onReceiveAd(banner: View) {
                                Log.d("UnifiedAdBanner", "Start.io Banner loaded.")
                            }

                            override fun onFailedToReceiveAd(banner: View) {
                                Log.w("UnifiedAdBanner", "Start.io Banner no fill.")
                            }

                            override fun onClick(banner: View) {}
                            override fun onImpression(banner: View) {}
                        })
                    }
                } catch (t: Throwable) {
                    Log.w("UnifiedAdBanner", "Banner create fallback: ${t.message}")
                    View(ctx)
                }
            },
            onRelease = { bannerView ->
                try {
                    if (bannerView is Banner) {
                        bannerView.hideBanner()
                    }
                } catch (t: Throwable) {
                    Log.w("UnifiedAdBanner", "Banner release note: ${t.message}")
                }
            }
        )
    }
}
