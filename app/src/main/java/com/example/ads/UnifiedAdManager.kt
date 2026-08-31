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

    private var isStartIoInitialized = false
    private var isUnityInitialized = false
    private var isUnityAdLoaded = false
    private var currentStartIoAppId: String = DEFAULT_STARTIO_APP_ID

    private var startIoInterstitialAd: StartAppAd? = null
    private var startIoRewardedAd: StartAppAd? = null
    private var isStartIoInterstitialLoading = false
    private var isStartIoRewardedLoading = false

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

    fun init(context: Context, initialConfig: AdsConfigResponse? = null, isVip: Boolean = false) {
        if (initialConfig != null) {
            _adConfigState.value = initialConfig
        }

        val config = _adConfigState.value

        if (!config.adsEnabled || isVip) {
            Log.i(TAG, "Ads globally disabled or user is VIP.")
            return
        }

        val unityConfig = config.unity
        if (unityConfig?.enabled == true) {
            val unityGameId = unityConfig.gameId?.takeIf { it.isNotBlank() } ?: DEFAULT_UNITY_GAME_ID
            initUnityAds(context, unityGameId, unityConfig.testMode)
        }

        val startIoConfig = config.startio
        if (startIoConfig?.enabled == true) {
            val startIoAppId = startIoConfig.appId.takeIf { it.isNotBlank() } ?: DEFAULT_STARTIO_APP_ID
            initializeStartIo(context, startIoAppId, isVip)
        }
    }

    fun applyRemoteConfig(context: Context, newConfig: AdsConfigResponse, isVip: Boolean = false) {
        _adConfigState.value = newConfig

        if (!newConfig.adsEnabled || isVip) {
            return
        }

        val unityConfig = newConfig.unity
        if (unityConfig?.enabled == true) {
            val unityGameId = unityConfig.gameId?.takeIf { it.isNotBlank() } ?: DEFAULT_UNITY_GAME_ID
            initUnityAds(context, unityGameId, unityConfig.testMode)
        }

        val startIoConfig = newConfig.startio
        if (startIoConfig?.enabled == true) {
            val newAppId = startIoConfig.appId.takeIf { it.isNotBlank() } ?: DEFAULT_STARTIO_APP_ID
            if (newAppId != currentStartIoAppId || !isStartIoInitialized) {
                initializeStartIo(context, newAppId, isVip)
            } else {
                preloadInterstitial(context)
                preloadStartIoRewarded(context)
            }
        }
    }

    private fun initUnityAds(context: Context, gameId: String, testMode: Boolean) {
        try {
            if (!UnityAds.isInitialized && gameId.isNotBlank()) {
                Log.d(TAG, "Initializing Unity Ads SDK (Game ID: $gameId, TestMode: $testMode)...")
                UnityAds.initialize(
                    context.applicationContext,
                    gameId,
                    testMode,
                    object : IUnityAdsInitializationListener {
                        override fun onInitializationComplete() {
                            isUnityInitialized = true
                            Log.i(TAG, "✓ Unity Ads SDK Initialized. Preloading Rewarded Video...")
                            preloadUnityRewarded(context)
                        }

                        override fun onInitializationFailed(error: UnityAds.UnityAdsInitializationError, message: String) {
                            isUnityInitialized = false
                            Log.e(TAG, "Unity Ads Init Failed: [$error] $message")
                        }
                    }
                )
            } else if (UnityAds.isInitialized) {
                preloadUnityRewarded(context)
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
            Log.i(TAG, "✓ Start.io SDK Initialized (App ID: $appId)")

            if (!isVip) {
                preloadInterstitial(context)
                preloadStartIoRewarded(context)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to init Start.io SDK: ${t.message}")
        }
    }

    // ============================================================
    // 🎁 REWARDED VIDEO ADS (SMART MULTI-TIER REWARD ENGINE)
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
        val isUnityOn = config.unity?.enabled == true
        val isStartIoOn = config.startio?.enabled == true
        val isAdsterraOn = config.adsterra?.enabled == true

        when {
            (primary.contains("unity") && isUnityOn) -> {
                showUnityRewardedVideo(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
            }
            (primary.contains("start") && isStartIoOn) -> {
                showStartIoRewardedWithInterstitialFallback(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
            }
            (primary.contains("adsterra") && isAdsterraOn) -> {
                val opened = openAdsterraDirectLink(activity, isVip = false, verificationSeconds = 10, onVerified = {
                    onRewardUnlocked()
                    onAdClosed?.invoke(true)
                })
                if (!opened) {
                    onAdNotReadyOrFailed?.invoke("Ad server busy.")
                    onAdClosed?.invoke(false)
                }
            }
            else -> {
                if (isUnityOn) {
                    showUnityRewardedVideo(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
                } else if (isStartIoOn) {
                    showStartIoRewardedWithInterstitialFallback(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
                } else if (isAdsterraOn) {
                    openAdsterraDirectLink(activity, isVip = false, verificationSeconds = 10, onVerified = {
                        onRewardUnlocked()
                        onAdClosed?.invoke(true)
                    })
                } else {
                    onAdNotReadyOrFailed?.invoke("No ad networks available.")
                    onAdClosed?.invoke(false)
                }
            }
        }
    }

    private fun showUnityRewardedVideo(
        activity: Activity,
        onRewardUnlocked: () -> Unit,
        onAdNotReadyOrFailed: ((reason: String) -> Unit)?,
        onAdClosed: ((rewardEarned: Boolean) -> Unit)?
    ) {
        val config = _adConfigState.value
        val unityConfig = config.unity
        val placementId = unityConfig?.rewardedId?.takeIf { it.isNotBlank() } ?: "Rewarded_Android"
        val gameId = unityConfig?.gameId?.takeIf { it.isNotBlank() } ?: DEFAULT_UNITY_GAME_ID
        val testMode = unityConfig?.testMode ?: true

        if (!UnityAds.isInitialized) {
            UnityAds.initialize(
                activity.applicationContext,
                gameId,
                testMode,
                object : IUnityAdsInitializationListener {
                    override fun onInitializationComplete() {
                        isUnityInitialized = true
                        loadAndPlayUnityAd(activity, placementId, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
                    }

                    override fun onInitializationFailed(error: UnityAds.UnityAdsInitializationError, message: String) {
                        handleUnityRewardFallback(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
                    }
                }
            )
        } else {
            loadAndPlayUnityAd(activity, placementId, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
        }
    }

    private fun loadAndPlayUnityAd(
        activity: Activity,
        placementId: String,
        onRewardUnlocked: () -> Unit,
        onAdNotReadyOrFailed: ((reason: String) -> Unit)?,
        onAdClosed: ((rewardEarned: Boolean) -> Unit)?
    ) {
        val showListener = object : IUnityAdsShowListener {
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
        }

        if (isUnityAdLoaded) {
            Log.i(TAG, "Showing preloaded Unity Rewarded Ad instantly...")
            UnityAds.show(activity, placementId, UnityAdsShowOptions(), showListener)
        } else {
            Log.i(TAG, "Unity Ad not preloaded. Fetching now...")
            UnityAds.load(placementId, object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(placementId: String) {
                    isUnityAdLoaded = true
                    UnityAds.show(activity, placementId, UnityAdsShowOptions(), showListener)
                }

                override fun onUnityAdsFailedToLoad(placementId: String, error: UnityAds.UnityAdsLoadError, message: String) {
                    isUnityAdLoaded = false
                    Log.w(TAG, "Unity Load Failed: [$error] $message. Trying fallback...")
                    handleUnityRewardFallback(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
                }
            })
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
            Log.i(TAG, "Triggering Start.io Rewarded/Interstitial fallback...")
            showStartIoRewardedWithInterstitialFallback(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
        } else if (config.adsterra?.enabled == true) {
            val opened = openAdsterraDirectLink(activity, isVip = false, verificationSeconds = 10, onVerified = {
                onRewardUnlocked()
                onAdClosed?.invoke(true)
            })
            if (!opened) {
                onAdNotReadyOrFailed?.invoke("No ads available right now.")
                onAdClosed?.invoke(false)
            }
        } else {
            onAdNotReadyOrFailed?.invoke("Ad load failed. Please try again.")
            onAdClosed?.invoke(false)
        }
    }

    /**
     * 🎯 Start.io Rewarded Video ➔ Interstitial Fallback ➔ Unlock on Close
     */
    private fun showStartIoRewardedWithInterstitialFallback(
        activity: Activity,
        onRewardUnlocked: () -> Unit,
        onAdNotReadyOrFailed: ((reason: String) -> Unit)?,
        onAdClosed: ((rewardEarned: Boolean) -> Unit)?
    ) {
        val preloadedRewarded = startIoRewardedAd
        if (preloadedRewarded != null && preloadedRewarded.isReady) {
            var earnedReward = false
            preloadedRewarded.setVideoListener(object : VideoListener {
                override fun onVideoCompleted() {
                    earnedReward = true
                }
            })
            preloadedRewarded.showAd(object : AdDisplayListener {
                override fun adHidden(shownAd: Ad) {
                    startIoRewardedAd = null
                    preloadStartIoRewarded(activity)
                    if (earnedReward) {
                        onRewardUnlocked()
                    }
                    onAdClosed?.invoke(earnedReward)
                }

                override fun adDisplayed(shownAd: Ad) {}
                override fun adClicked(shownAd: Ad) {}
                override fun adNotDisplayed(shownAd: Ad) {
                    startIoRewardedAd = null
                    preloadStartIoRewarded(activity)
                    showStartIoInterstitialForReward(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
                }
            })
            return
        }

        val preloadedInterstitial = startIoInterstitialAd
        if (preloadedInterstitial != null && preloadedInterstitial.isReady) {
            Log.i(TAG, "Showing preloaded Start.io Interstitial for Unlock...")
            preloadedInterstitial.showAd(object : AdDisplayListener {
                override fun adHidden(shownAd: Ad) {
                    startIoInterstitialAd = null
                    preloadInterstitial(activity)
                    onRewardUnlocked()
                    onAdClosed?.invoke(true)
                }
                override fun adDisplayed(shownAd: Ad) {}
                override fun adClicked(shownAd: Ad) {}
                override fun adNotDisplayed(shownAd: Ad) {
                    startIoInterstitialAd = null
                    preloadInterstitial(activity)
                    showStartIoOnDemandRewardedOrInterstitial(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
                }
            })
            return
        }

        showStartIoOnDemandRewardedOrInterstitial(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
    }

    private fun showStartIoOnDemandRewardedOrInterstitial(
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
                            preloadStartIoRewarded(activity)
                        }

                        override fun adDisplayed(shownAd: Ad) {}
                        override fun adClicked(shownAd: Ad) {}

                        override fun adNotDisplayed(shownAd: Ad) {
                            preloadStartIoRewarded(activity)
                            showStartIoInterstitialForReward(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
                        }
                    })
                }

                override fun onFailedToReceiveAd(failedAd: Ad?) {
                    Log.w(TAG, "Start.io Rewarded Video failed. Immediately loading Start.io Interstitial Ad...")
                    showStartIoInterstitialForReward(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
                }
            })
        } catch (t: Throwable) {
            showStartIoInterstitialForReward(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
        }
    }

    private fun showStartIoInterstitialForReward(
        activity: Activity,
        onRewardUnlocked: () -> Unit,
        onAdNotReadyOrFailed: ((reason: String) -> Unit)?,
        onAdClosed: ((rewardEarned: Boolean) -> Unit)?
    ) {
        try {
            val interstitialAd = StartAppAd(activity)
            interstitialAd.loadAd(StartAppAd.AdMode.AUTOMATIC, object : AdEventListener {
                override fun onReceiveAd(loadedAd: Ad) {
                    Log.i(TAG, "✓ Start.io Interstitial loaded! Showing ad to unlock episode...")
                    interstitialAd.showAd(object : AdDisplayListener {
                        override fun adHidden(shownAd: Ad) {
                            Log.i(TAG, "✓ Start.io Interstitial closed. Episode unlocked successfully!")
                            onRewardUnlocked()
                            onAdClosed?.invoke(true)
                            preloadInterstitial(activity)
                        }

                        override fun adDisplayed(shownAd: Ad) {}
                        override fun adClicked(shownAd: Ad) {}

                        override fun adNotDisplayed(shownAd: Ad) {
                            fallbackToAdsterraDirectLink(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
                        }
                    })
                }

                override fun onFailedToReceiveAd(ad: Ad?) {
                    Log.w(TAG, "Start.io Interstitial also failed. Final fallback to Adsterra...")
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
        val smartlinkOpened = openSmartlink(activity, isVip = false, verificationSeconds = 10, onVerified = {
            onRewardUnlocked()
            onAdClosed?.invoke(true)
        })
        if (!smartlinkOpened) {
            onAdNotReadyOrFailed?.invoke("Ad is currently unavailable. Please try again.")
            onAdClosed?.invoke(false)
        }
    }

    // 🚀 প্রি-লোডিং ফাংশনসমূহ (Background Preloaders)
    private fun preloadUnityRewarded(context: Context) {
        val config = _adConfigState.value
        val placementId = config.unity?.rewardedId?.takeIf { it.isNotBlank() } ?: "Rewarded_Android"
        if (UnityAds.isInitialized) {
            UnityAds.load(placementId, object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(placementId: String) {
                    isUnityAdLoaded = true
                    Log.d(TAG, "✓ Unity Rewarded Video Preloaded & Ready in Memory!")
                }

                override fun onUnityAdsFailedToLoad(placementId: String, error: UnityAds.UnityAdsLoadError, message: String) {
                    isUnityAdLoaded = false
                    Log.w(TAG, "Unity Rewarded Video Preload notice: $message")
                }
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
                    Log.d(TAG, "✓ Start.io Rewarded Video Preloaded.")
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
                    Log.d(TAG, "✓ Start.io Interstitial Preloaded & Ready in Memory.")
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
        if (isVip || !config.adsEnabled || config.adsterra?.enabled != true) return false

        val adsterra = config.adsterra
        val targetUrl = adsterra?.effectiveDirectLink?.trim()?.takeIf { it.isNotBlank() } ?: fallbackUrl
        if (targetUrl.isNullOrBlank() || (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://"))) return false

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

    fun isAdsterraPrimary(): Boolean = _adConfigState.value.primaryNetwork.contains("adsterra", ignoreCase = true)
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
    fun isAdsGloballyEnabled(): Boolean = _adConfigState.value.adsEnabled

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

        when {
            (primary.contains("unity") && isUnityOn) -> {
                showUnityInterstitial(activity, context, onComplete)
            }
            (primary.contains("start") && isStartIoOn) -> {
                showStartIoInterstitial(context, onComplete)
            }
            else -> {
                if (isUnityOn) {
                    showUnityInterstitial(activity, context, onComplete)
                } else if (isStartIoOn) {
                    showStartIoInterstitial(context, onComplete)
                } else {
                    onComplete()
                }
            }
        }
    }

    private fun showUnityInterstitial(activity: Activity, context: Context, onComplete: () -> Unit) {
        val config = _adConfigState.value
        val placementId = config.unity?.interstitialId?.takeIf { it.isNotBlank() } ?: "Interstitial_Android"
        val isStartIoOn = config.startio?.enabled == true

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
