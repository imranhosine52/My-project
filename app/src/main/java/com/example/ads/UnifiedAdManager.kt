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
import com.example.data.model.AdRulesConfig
import com.example.data.model.AdsConfigResponse
import com.example.data.model.AdsterraConfig
import com.example.data.model.StartIoConfig
import com.startapp.sdk.ads.banner.Banner
import com.startapp.sdk.ads.banner.BannerListener
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import com.startapp.sdk.adsbase.adlisteners.VideoListener
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
 * 📡 REMOTE DYNAMIC MULTI-NETWORK AD MEDIATION ARCHITECTURE
 * ============================================================
 * UnifiedAdManager
 * Centralized ad orchestration engine supporting dynamic remote config,
 * multi-network mediation (Start.io, Adsterra, AdMob), automatic fallbacks,
 * and zero-latency VIP member bypass.
 */
object UnifiedAdManager {
    private const val TAG = "UnifiedAdManager"

    // Default Fallback Configurations
    private const val DEFAULT_STARTIO_APP_ID = "207238360"
    private const val DEFAULT_STARTIO_PUB_ID = "113502454"

    // Observable Live Ad Configuration State
    private val _adConfigState = MutableStateFlow(
        AdsConfigResponse(
            success = true,
            status = 200,
            adsEnabled = true,
            primaryNetwork = "startio",
            fallbackNetwork = "adsterra",
            startio = StartIoConfig(enabled = true, appId = DEFAULT_STARTIO_APP_ID, publisherId = DEFAULT_STARTIO_PUB_ID),
            adsterra = AdsterraConfig(enabled = true),
            rules = AdRulesConfig(timerSeconds = 10, rewardedUnlockHours = 2, freeUnlockedEpisodes = 1)
        )
    )
    val adConfigState: StateFlow<AdsConfigResponse> = _adConfigState.asStateFlow()

    // Current State flags
    private var isInitialized = false
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

        // Master switch: if ads disabled completely or user is VIP, do not initialize SDK background tasks
        if (!config.adsEnabled || isVip) {
            Log.i(TAG, "Ads are globally disabled (ads_enabled=${config.adsEnabled}) or user is VIP (isVip=$isVip). Suppressing ad initialization.")
            return
        }

        val startIoAppId = config.startio?.appId?.takeIf { it.isNotBlank() } ?: DEFAULT_STARTIO_APP_ID
        initializeStartIo(context, startIoAppId, isVip)
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

        val newAppId = newConfig.startio?.appId?.takeIf { it.isNotBlank() } ?: DEFAULT_STARTIO_APP_ID
        if (newAppId != currentStartIoAppId || !isInitialized) {
            initializeStartIo(context, newAppId, isVip)
        } else {
            preloadInterstitial(context)
            preloadRewardedVideo(context)
        }
    }

    private fun initializeStartIo(context: Context, appId: String, isVip: Boolean) {
        try {
            currentStartIoAppId = appId
            StartAppSDK.init(context.applicationContext, appId, false)
            StartAppSDK.setTestAdsEnabled(false)
            StartAppAd.disableSplash()
            StartAppSDK.enableReturnAds(false)
            isInitialized = true
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

    /**
     * Triggers Adsterra Popunder ad during page navigation if enabled in Remote Config.
     */
    fun showPopunderIfEligible(
        context: Context,
        isVip: Boolean
    ) {
        val config = _adConfigState.value

        if (isVip || !config.adsEnabled) {
            return
        }

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
            Log.i(TAG, "🌐 Triggering Adsterra Popunder in In-App Browser (Transition #$pageTransitionCount): $popunderUrl")
            openInAppBrowser(
                url = popunderUrl,
                title = "Sponsored Partner"
            )
        }
    }

    /**
     * Opens Adsterra Direct Link in the In-App Browser.
     */
    fun openAdsterraDirectLink(
        context: Context,
        isVip: Boolean,
        fallbackUrl: String? = null,
        verificationSeconds: Int? = null,
        onVerified: (() -> Unit)? = null
    ): Boolean {
        val config = _adConfigState.value

        if (isVip || !config.adsEnabled) {
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

    fun isDirectLinkAvailable(isVip: Boolean): Boolean {
        val config = _adConfigState.value
        if (isVip || !config.adsEnabled) return false
        val adsterra = config.adsterra ?: return false
        return adsterra.enabled && !adsterra.effectiveDirectLink.isNullOrBlank()
    }

    fun isSmartlinkAvailable(isVip: Boolean): Boolean = isDirectLinkAvailable(isVip)

    fun getEffectiveDirectLink(): String? {
        val config = _adConfigState.value
        if (!config.adsEnabled) return null
        val adsterra = config.adsterra ?: return null
        return if (adsterra.enabled) adsterra.effectiveDirectLink else null
    }

    fun getSmartlinkUrl(): String? = getEffectiveDirectLink()

    fun getVerificationTimerSeconds(): Int = _adConfigState.value.rules?.timerSeconds ?: 10

    fun isAdsterraPrimary(): Boolean = _adConfigState.value.primaryNetwork.equals("adsterra", ignoreCase = true)

    fun isStartIoPrimary(): Boolean = _adConfigState.value.primaryNetwork.equals("startio", ignoreCase = true)

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
            Log.d(TAG, "Zero-delay bypass: isVip=$isVip, adsEnabled=${config.adsEnabled}")
            onComplete()
            return
        }

        val primary = config.primaryNetwork.lowercase()
        when (primary) {
            "startio" -> showStartIoInterstitial(context, onComplete)
            "admob" -> showAdMobInterstitialFallback(context, onComplete)
            else -> {
                if (config.startio?.enabled != false) {
                    showStartIoInterstitial(context, onComplete)
                } else {
                    onComplete()
                }
            }
        }
    }

    private fun showStartIoInterstitial(context: Context, onComplete: () -> Unit) {
        try {
            val activity = context.findActivity()
            val ad = startIoInterstitialAd
            if (activity != null && ad != null && ad.isReady) {
                ad.showAd(object : AdDisplayListener {
                    override fun adHidden(ad: Ad) {
                        Log.d(TAG, "Start.io Interstitial dismissed by user.")
                        startIoInterstitialAd = null
                        preloadInterstitial(context)
                        onComplete()
                    }

                    override fun adDisplayed(ad: Ad) {
                        Log.d(TAG, "Start.io Interstitial displayed.")
                    }

                    override fun adClicked(ad: Ad) {
                        Log.d(TAG, "Start.io Interstitial clicked.")
                    }

                    override fun adNotDisplayed(ad: Ad) {
                        Log.w(TAG, "Start.io Interstitial ad not displayed.")
                        startIoInterstitialAd = null
                        preloadInterstitial(context)
                        onComplete()
                    }
                })
            } else {
                Log.d(TAG, "Start.io Interstitial not ready; proceeding directly.")
                preloadInterstitial(context)
                onComplete()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error displaying Start.io Interstitial: ${t.message}")
            preloadInterstitial(context)
            onComplete()
        }
    }

    private fun showAdMobInterstitialFallback(context: Context, onComplete: () -> Unit) {
        val config = _adConfigState.value
        if (config.startio?.enabled != false) {
            showStartIoInterstitial(context, onComplete)
        } else {
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
                    Log.d(TAG, "✓ Start.io Interstitial preloaded successfully.")
                }

                override fun onFailedToReceiveAd(failedAd: Ad?) {
                    isStartIoInterstitialLoading = false
                    Log.w(TAG, "Start.io Interstitial preload failed: ${failedAd?.errorMessage}")
                }
            })
        } catch (t: Throwable) {
            isStartIoInterstitialLoading = false
            Log.e(TAG, "Error preloading Start.io Interstitial: ${t.message}")
        }
    }

    // ============================================================
    // 🎁 REWARDED VIDEO ADS MEDIATION
    // ============================================================

    /**
     * ⚡ UI থেকে সরাসরি কল করার জন্য সহজ ওভারলোডেড মেথড
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
     * Plays rewarded video ad to unlock locked episodes.
     * ভিডিও সম্পূর্ণ দেখা হলেই কেবল onRewardUnlocked ট্রিগার হবে।
     */
    fun showRewardedVideo(
        context: Context,
        isVip: Boolean,
        onRewardUnlocked: () -> Unit,
        onAdNotReadyOrFailed: ((reason: String) -> Unit)? = null,
        onAdClosed: ((rewardEarned: Boolean) -> Unit)? = null
    ) {
        val config = _adConfigState.value

        // 👑 Strict VIP Bypass
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

        showStartIoRewardedVideoWithFallback(
            activity = activity,
            onRewardUnlocked = onRewardUnlocked,
            onAdNotReadyOrFailed = onAdNotReadyOrFailed,
            onAdClosed = onAdClosed
        )
    }

    private fun showStartIoRewardedVideoWithFallback(
        activity: Activity,
        onRewardUnlocked: () -> Unit,
        onAdNotReadyOrFailed: ((reason: String) -> Unit)?,
        onAdClosed: ((rewardEarned: Boolean) -> Unit)?
    ) {
        try {
            val ad = startIoRewardedAd
            if (ad != null && ad.isReady) {
                var userEarnedReward = false

                ad.setVideoListener(object : VideoListener {
                    override fun onVideoCompleted() {
                        Log.i(TAG, "✓ Start.io Rewarded Video completed! Reward confirmed.")
                        userEarnedReward = true
                    }
                })

                ad.showAd(object : AdDisplayListener {
                    override fun adHidden(shownAd: Ad) {
                        Log.d(TAG, "Rewarded ad closed. Earned: $userEarnedReward")
                        if (userEarnedReward) {
                            onRewardUnlocked()
                        }
                        onAdClosed?.invoke(userEarnedReward)
                        startIoRewardedAd = null
                        preloadRewardedVideo(activity)
                    }

                    override fun adDisplayed(shownAd: Ad) {
                        Log.d(TAG, "Rewarded ad displayed on screen.")
                    }

                    override fun adClicked(shownAd: Ad) {}

                    override fun adNotDisplayed(shownAd: Ad) {
                        Log.w(TAG, "Rewarded ad could not be displayed.")
                        onAdNotReadyOrFailed?.invoke("Ad could not be displayed. Please try again.")
                        onAdClosed?.invoke(false)
                        startIoRewardedAd = null
                        preloadRewardedVideo(activity)
                    }
                })
            } else {
                Log.d(TAG, "Preloaded rewarded ad not ready. Loading on-demand...")
                val onDemandAd = StartAppAd(activity)
                var userEarnedReward = false

                onDemandAd.setVideoListener(object : VideoListener {
                    override fun onVideoCompleted() {
                        Log.i(TAG, "✓ Start.io On-Demand Rewarded Video completed! Reward confirmed.")
                        userEarnedReward = true
                    }
                })

                onDemandAd.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener {
                    override fun onReceiveAd(loadedAd: Ad) {
                        Log.d(TAG, "On-demand Rewarded Ad loaded successfully. Showing now.")
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
                                onAdNotReadyOrFailed?.invoke("Ad could not be displayed. Please try again.")
                                onAdClosed?.invoke(false)
                                preloadRewardedVideo(activity)
                            }
                        })
                    }

                    override fun onFailedToReceiveAd(failedAd: Ad?) {
                        Log.w(TAG, "Rewarded video returned NO FILL. Trying Automatic Interstitial fallback...")

                        val fallbackAd = StartAppAd(activity)
                        fallbackAd.loadAd(StartAppAd.AdMode.AUTOMATIC, object : AdEventListener {
                            override fun onReceiveAd(loadedAd: Ad) {
                                Log.d(TAG, "Fallback ad loaded successfully. Displaying now.")
                                fallbackAd.showAd(object : AdDisplayListener {
                                    override fun adHidden(shownAd: Ad) {
                                        Log.d(TAG, "Fallback ad completed. Unlocking episode.")
                                        onRewardUnlocked()
                                        onAdClosed?.invoke(true)
                                        preloadRewardedVideo(activity)
                                    }

                                    override fun adDisplayed(shownAd: Ad) {}
                                    override fun adClicked(shownAd: Ad) {}

                                    override fun adNotDisplayed(shownAd: Ad) {
                                        onAdNotReadyOrFailed?.invoke("Ad could not be displayed. Please try again.")
                                        onAdClosed?.invoke(false)
                                        preloadRewardedVideo(activity)
                                    }
                                })
                            }

                            override fun onFailedToReceiveAd(ad: Ad?) {
                                Log.w(TAG, "Both Rewarded Video and Interstitial returned NO FILL.")
                                val friendlyMsg = "Ad server is currently busy. Please try again in a few moments."
                                onAdNotReadyOrFailed?.invoke(friendlyMsg)
                                onAdClosed?.invoke(false)
                                preloadRewardedVideo(activity)
                            }
                        })
                    }
                })
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error in Rewarded Video flow: ${t.message}")
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
                    Log.d(TAG, "✓ Start.io Rewarded Video preloaded successfully.")
                    onLoaded?.invoke()
                }

                override fun onFailedToReceiveAd(failedAd: Ad?) {
                    isStartIoRewardedLoading = false
                    val error = failedAd?.errorMessage ?: "Ad failed to load"
                    Log.w(TAG, "Start.io Rewarded Video preload error: $error")
                    onFailed?.invoke(error)
                }
            })
        } catch (t: Throwable) {
            isStartIoRewardedLoading = false
            Log.e(TAG, "Error preloading Rewarded Ad: ${t.message}")
            onFailed?.invoke(t.message ?: "Unknown error")
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
