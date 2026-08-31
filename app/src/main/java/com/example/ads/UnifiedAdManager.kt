package com.example.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
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

// Helper to safely extract Activity from Context
internal fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) return currentContext
        currentContext = currentContext.baseContext
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
                testMode = true
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

    private var startIoInterstitialAd: StartAppAd? = null
    private var isStartIoInterstitialLoading = false

    private var pageTransitionCount = 0
    private var lastPopunderTimestamp = 0L

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
     * 1. Initialize Ad Networks dynamically
     */
    fun init(context: Context, initialConfig: AdsConfigResponse? = null, isVip: Boolean = false) {
        if (initialConfig != null) {
            _adConfigState.value = initialConfig
        }

        val config = _adConfigState.value

        if (!config.adsEnabled || isVip) {
            Log.i(TAG, "Ads globally disabled or user is VIP.")
            return
        }

        if (config.unity?.enabled == true) {
            val unityGameId = config.unity.gameId?.takeIf { it.isNotBlank() } ?: DEFAULT_UNITY_GAME_ID
            val unityTestMode = config.unity.testMode
            initUnityAds(context, unityGameId, unityTestMode)
        }

        if (config.startio?.enabled == true) {
            val startIoAppId = config.startio.appId?.takeIf { it.isNotBlank() } ?: DEFAULT_STARTIO_APP_ID
            initializeStartIo(context, startIoAppId, isVip)
        }
    }

    /**
     * 2. Apply Dynamic Remote Config updates
     */
    fun applyRemoteConfig(context: Context, newConfig: AdsConfigResponse, isVip: Boolean = false) {
        _adConfigState.value = newConfig
        Log.i(TAG, "📡 Applied Remote Ads Config: Primary=${newConfig.primaryNetwork}, AdsEnabled=${newConfig.adsEnabled}")

        if (!newConfig.adsEnabled || isVip) {
            return
        }

        if (newConfig.unity?.enabled == true) {
            val unityGameId = newConfig.unity.gameId?.takeIf { it.isNotBlank() } ?: DEFAULT_UNITY_GAME_ID
            val unityTestMode = newConfig.unity.testMode
            initUnityAds(context, unityGameId, unityTestMode)
        }

        val newAppId = newConfig.startio?.appId?.takeIf { it.isNotBlank() } ?: DEFAULT_STARTIO_APP_ID
        if (newConfig.startio?.enabled == true) {
            if (newAppId != currentStartIoAppId || !isStartIoInitialized) {
                initializeStartIo(context, newAppId, isVip)
            }
        }
    }

    private fun initUnityAds(context: Context, gameId: String, testMode: Boolean) {
        try {
            if (!UnityAds.isInitialized() && gameId.isNotBlank()) {
                Log.i(TAG, "Initializing Unity Ads SDK (Game ID: $gameId, TestMode: $testMode)...")
                UnityAds.initialize(
                    context.applicationContext,
                    gameId,
                    testMode,
                    object : IUnityAdsInitializationListener {
                        override fun onInitializationComplete() {
                            isUnityInitialized = true
                            Log.i(TAG, "✓ Unity Ads SDK initialized successfully with Game ID: $gameId")
                        }

                        override fun onInitializationFailed(error: UnityAds.UnityAdsInitializationError, message: String) {
                            Log.e(TAG, "Failed to init Unity Ads SDK: [$error] $message")
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
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to init Start.io SDK: ${t.message}")
        }
    }

    // ============================================================
    // 🎁 REWARDED VIDEO ADS (ON-DEMAND AUTO-HEALING ENGINE)
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
        val isUnityOn = config.unity?.enabled == true
        val isStartIoOn = config.startio?.enabled == true
        val isAdsterraOn = config.adsterra?.enabled == true

        if (primary == "unity" || isUnityOn) {
            showUnityRewardedVideo(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
        } else if (primary == "startio" && isStartIoOn) {
            showStartIoRewardedVideoWithFallback(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
        } else if (primary == "adsterra" && isAdsterraOn) {
            val opened = openAdsterraDirectLink(activity, isVip = false, verificationSeconds = 10, onVerified = {
                onRewardUnlocked()
                onAdClosed?.invoke(true)
            })
            if (!opened) {
                onAdNotReadyOrFailed?.invoke("Adsterra Direct Link is unavailable.")
                onAdClosed?.invoke(false)
            }
        } else {
            Log.d(TAG, "No ad network enabled in admin panel. Granting instant access.")
            onRewardUnlocked()
            onAdClosed?.invoke(true)
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
        val testMode = config.unity?.testMode ?: true

        if (!UnityAds.isInitialized()) {
            Log.i(TAG, "Unity Ads not initialized yet. Initializing on-demand with Game ID: $gameId (TestMode: $testMode)...")
            UnityAds.initialize(
                activity.applicationContext,
                gameId,
                testMode,
                object : IUnityAdsInitializationListener {
                    override fun onInitializationComplete() {
                        isUnityInitialized = true
                        Log.i(TAG, "✓ Unity Ads Initialized on-demand! Loading ad...")
                        loadAndShowUnityAd(activity, placementId, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
                    }

                    override fun onInitializationFailed(error: UnityAds.UnityAdsInitializationError, message: String) {
                        Log.e(TAG, "Unity on-demand initialization failed: [$error] $message. Trying fallback...")
                        handleUnityRewardFallback(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
                    }
                }
            )
        } else {
            loadAndShowUnityAd(activity, placementId, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
        }
    }

    private fun loadAndShowUnityAd(
        activity: Activity,
        placementId: String,
        onRewardUnlocked: () -> Unit,
        onAdNotReadyOrFailed: ((reason: String) -> Unit)?,
        onAdClosed: ((rewardEarned: Boolean) -> Unit)?
    ) {
        Log.i(TAG, "Loading Unity Rewarded Ad for placement: '$placementId'...")

        UnityAds.load(placementId, object : IUnityAdsLoadListener {
            override fun onUnityAdsAdLoaded(placementId: String) {
                Log.i(TAG, "✓ Unity Rewarded Video Loaded! Showing now...")
                UnityAds.show(activity, placementId, UnityAdsShowOptions(), object : IUnityAdsShowListener {
                    override fun onUnityAdsShowStart(placementId: String) {
                        Log.d(TAG, "Unity Video Started playing.")
                    }
                    override fun onUnityAdsShowClick(placementId: String) {}

                    override fun onUnityAdsShowComplete(placementId: String, state: UnityAds.UnityAdsShowCompletionState) {
                        if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                            Log.i(TAG, "✓ Unity Video Completed! Unlocking episode.")
                            onRewardUnlocked()
                            onAdClosed?.invoke(true)
                        } else {
                            Log.w(TAG, "Unity Video was skipped.")
                            onAdClosed?.invoke(false)
                        }
                    }

                    override fun onUnityAdsShowFailure(placementId: String, error: UnityAds.UnityAdsShowError, message: String) {
                        Log.w(TAG, "Unity Show Failed: [$error] $message. Fallback...")
                        handleUnityRewardFallback(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
                    }
                })
            }

            override fun onUnityAdsFailedToLoad(placementId: String, error: UnityAds.UnityAdsLoadError, message: String) {
                Log.w(TAG, "Unity Load Failed: [$error] $message. Fallback...")
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
            Log.i(TAG, "Executing fallback: Showing Start.io Rewarded Video...")
            showStartIoRewardedVideoWithFallback(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
        } else if (config.adsterra?.enabled == true) {
            Log.i(TAG, "Executing fallback: Opening Adsterra Direct Link...")
            val opened = openAdsterraDirectLink(activity, isVip = false, verificationSeconds = 10, onVerified = {
                onRewardUnlocked()
                onAdClosed?.invoke(true)
            })
            if (!opened) {
                onAdNotReadyOrFailed?.invoke("Ad servers are busy. Please try again.")
                onAdClosed?.invoke(false)
            }
        } else {
            onAdNotReadyOrFailed?.invoke("No video fill available at the moment.")
            onAdClosed?.invoke(false)
        }
    }

    // 🎯 Start.io Rewarded Video
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
                        }

                        override fun adDisplayed(shownAd: Ad) {}
                        override fun adClicked(shownAd: Ad) {}

                        override fun adNotDisplayed(shownAd: Ad) {
                            onAdNotReadyOrFailed?.invoke("Ad could not be displayed.")
                            onAdClosed?.invoke(false)
                        }
                    })
                }

                override fun onFailedToReceiveAd(failedAd: Ad?) {
                    Log.w(TAG, "Start.io ad failed to load. Fallback to Adsterra Smartlink...")
                    val smartlinkOpened = openSmartlink(activity, isVip = false, verificationSeconds = 10, onVerified = {
                        onRewardUnlocked()
                        onAdClosed?.invoke(true)
                    })
                    if (!smartlinkOpened) {
                        onAdNotReadyOrFailed?.invoke("Ad server is busy.")
                        onAdClosed?.invoke(false)
                    }
                }
            })
        } catch (t: Throwable) {
            Log.e(TAG, "Error in Start.io Rewarded Ad: ${t.message}")
            onAdNotReadyOrFailed?.invoke("Ad error")
            onAdClosed?.invoke(false)
        }
    }

    // ============================================================
    // 🌐 ADSTERRA POPUNDER & DIRECT LINK
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
            return false
        }

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

    // Compatibility Properties (For Dialogs & Screens)
    val isAdsterraPrimary: Boolean
        get() = _adConfigState.value.primaryNetwork.equals("adsterra", ignoreCase = true)

    val isStartIoPrimary: Boolean
        get() = _adConfigState.value.primaryNetwork.equals("startio", ignoreCase = true)

    val isUnityPrimary: Boolean
        get() = _adConfigState.value.primaryNetwork.equals("unity", ignoreCase = true)

    @JvmOverloads
    fun isDirectLinkAvailable(isVip: Boolean = false): Boolean {
        val config = _adConfigState.value
        if (isVip || !config.adsEnabled) return false
        val adsterra = config.adsterra ?: return false
        return adsterra.enabled && !adsterra.effectiveDirectLink.isNullOrBlank()
    }

    @JvmOverloads
    fun isSmartlinkAvailable(isVip: Boolean = false): Boolean = isDirectLinkAvailable(isVip)

    fun getEffectiveDirectLink(): String? {
        val config = _adConfigState.value
        if (!config.adsEnabled) return null
        val adsterra = config.adsterra ?: return null
        return if (adsterra.enabled) adsterra.effectiveDirectLink else null
    }

    fun getSmartlinkUrl(): String? = getEffectiveDirectLink()

    fun getVerificationTimerSeconds(): Int = _adConfigState.value.rules?.timerSeconds ?: 10

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

        val activity = context.findActivity() ?: run {
            onComplete()
            return
        }

        val primary = config.primaryNetwork.lowercase()
        val isUnityOn = config.unity?.enabled == true
        val isStartIoOn = config.startio?.enabled == true

        if (primary == "unity" && isUnityOn) {
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
        if (startIoInterstitialAd == null && !isStartIoInterstitialLoading) {
            isStartIoInterstitialLoading = true
            val ad = StartAppAd(context.applicationContext)
            ad.loadAd(StartAppAd.AdMode.AUTOMATIC, object : AdEventListener {
                override fun onReceiveAd(p0: Ad) {
                    startIoInterstitialAd = ad
                    isStartIoInterstitialLoading = false
                }
                override fun onFailedToReceiveAd(p0: Ad?) {
                    startIoInterstitialAd = null
                    isStartIoInterstitialLoading = false
                }
            })
        }
    }

    fun preloadRewardedVideo(context: Context, onLoaded: (() -> Unit)? = null, onFailed: ((String) -> Unit)? = null) {
        val config = _adConfigState.value
        val placementId = config.unity?.rewardedId?.takeIf { it.isNotBlank() } ?: "Rewarded_Android"
        if (UnityAds.isInitialized()) {
            UnityAds.load(placementId, object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(p0: String) {
                    onLoaded?.invoke()
                }
                override fun onUnityAdsFailedToLoad(p0: String, p1: UnityAds.UnityAdsLoadError, p2: String) {
                    onFailed?.invoke(p2)
                }
            })
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
