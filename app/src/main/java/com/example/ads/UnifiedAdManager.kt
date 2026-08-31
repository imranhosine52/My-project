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

// 🔍 Context থেকে Activity খুঁজে নেওয়ার সেফ হেলপার
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
 */
object UnifiedAdManager {
    private const val TAG = "UnifiedAdManager"

    private const val DEFAULT_UNITY_GAME_ID = "800364838"
    private const val DEFAULT_STARTIO_APP_ID = "207238360"
    private const val DEFAULT_STARTIO_PUB_ID = "113502454"

    private val _adConfigState = MutableStateFlow(
        AdsConfigResponse(
            success = true,
            status = 200,
            adsEnabled = true,
            primaryNetwork = "unity",
            fallbackNetwork = "unity",
            unity = UnityAdsConfig(enabled = true, gameId = DEFAULT_UNITY_GAME_ID),
            startio = StartIoConfig(enabled = false, appId = DEFAULT_STARTIO_APP_ID, publisherId = DEFAULT_STARTIO_PUB_ID),
            adsterra = AdsterraConfig(enabled = false),
            rules = AdRulesConfig(timerSeconds = 10, rewardedUnlockHours = 2, freeUnlockedEpisodes = 1)
        )
    )
    val adConfigState: StateFlow<AdsConfigResponse> = _adConfigState.asStateFlow()

    private var isStartIoInitialized = false
    private var isUnityInitialized = false

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

    fun openInAppBrowser(url: String, title: String = "Sponsored Offer", verificationSeconds: Int? = null, onVerified: (() -> Unit)? = null) {
        _inAppBrowserRequest.value = InAppBrowserRequest(url = url, title = title, verificationSeconds = verificationSeconds, onVerified = onVerified)
    }

    fun closeInAppBrowser() {
        _inAppBrowserRequest.value = null
    }

    /**
     * ১. অ্যাপ ইনিশিয়ালাইজেশন
     */
    fun init(context: Context, initialConfig: AdsConfigResponse? = null, isVip: Boolean = false) {
        if (initialConfig != null) {
            _adConfigState.value = initialConfig
        }

        val config = _adConfigState.value
        if (!config.adsEnabled || isVip) {
            Log.i(TAG, "Ads globally disabled or VIP active.")
            return
        }

        if (config.unity?.enabled == true) {
            val unityGameId = config.unity.gameId?.takeIf { it.isNotBlank() } ?: DEFAULT_UNITY_GAME_ID
            initUnityAds(context, unityGameId, config.unity.testMode)
        }

        if (config.startio?.enabled == true) {
            val startIoAppId = config.startio.appId.takeIf { it.isNotBlank() } ?: DEFAULT_STARTIO_APP_ID
            initStartIo(context, startIoAppId)
        }
    }

    /**
     * ২. রিমোট কনফিগ লাইভ অ্যাপ্লাই
     */
    fun applyRemoteConfig(context: Context, newConfig: AdsConfigResponse, isVip: Boolean = false) {
        _adConfigState.value = newConfig
        Log.i(TAG, "📡 Remote Ads Config Applied: MasterEnabled=${newConfig.adsEnabled}, Primary=${newConfig.primaryNetwork}")

        if (!newConfig.adsEnabled || isVip) return

        if (newConfig.unity?.enabled == true) {
            val unityGameId = newConfig.unity.gameId?.takeIf { it.isNotBlank() } ?: DEFAULT_UNITY_GAME_ID
            initUnityAds(context, unityGameId, newConfig.unity.testMode)
        }

        if (newConfig.startio?.enabled == true) {
            val startIoAppId = newConfig.startio.appId.takeIf { it.isNotBlank() } ?: DEFAULT_STARTIO_APP_ID
            initStartIo(context, startIoAppId)
            preloadInterstitial(context)
            preloadRewardedVideo(context)
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
                            Log.i(TAG, "✓ Unity Ads SDK Initialized Successfully (Game ID: $gameId)")
                        }
                        override fun onInitializationFailed(error: UnityAds.UnityAdsInitializationError, message: String) {
                            Log.e(TAG, "Unity Ads Init Failed: $message")
                        }
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unity Init Error: ${e.message}")
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
                Log.i(TAG, "✓ Start.io Initialized (App ID: $appId)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Start.io Init Error: ${e.message}")
        }
    }

    // ============================================================
    // 🎁 REWARDED VIDEO ADS
    // ============================================================

    fun showRewardedAd(activity: Activity, onRewardEarned: (Boolean) -> Unit) {
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
            onAdNotReadyOrFailed?.invoke("Context Error")
            onAdClosed?.invoke(false)
            return
        }

        val primary = config.primaryNetwork.lowercase()
        val isUnityOn = config.unity?.enabled == true
        val isStartIoOn = config.startio?.enabled == true
        val isAdsterraOn = config.adsterra?.enabled == true

        if (primary == "unity" && isUnityOn) {
            showUnityRewardedVideo(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
        } else if (primary == "startio" && isStartIoOn) {
            showStartIoRewardedVideoWithFallback(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
        } else if (primary == "adsterra" && isAdsterraOn) {
            openAdsterraDirectLink(activity, isVip = false, verificationSeconds = 10, onVerified = {
                onRewardUnlocked()
                onAdClosed?.invoke(true)
            })
        } else if (isUnityOn) {
            showUnityRewardedVideo(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
        } else if (isStartIoOn) {
            showStartIoRewardedVideoWithFallback(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
        } else {
            Log.d(TAG, "No Ad Networks enabled in Admin Panel. Instant Unlock.")
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
                            onAdClosed?.invoke(false)
                        }
                    }

                    override fun onUnityAdsShowFailure(placementId: String, error: UnityAds.UnityAdsShowError, message: String) {
                        handleUnityFallback(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
                    }
                })
            }

            override fun onUnityAdsFailedToLoad(placementId: String, error: UnityAds.UnityAdsLoadError, message: String) {
                handleUnityFallback(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
            }
        })
    }

    private fun handleUnityFallback(
        activity: Activity,
        onRewardUnlocked: () -> Unit,
        onAdNotReadyOrFailed: ((reason: String) -> Unit)?,
        onAdClosed: ((rewardEarned: Boolean) -> Unit)?
    ) {
        val config = _adConfigState.value
        if (config.startio?.enabled == true) {
            showStartIoRewardedVideoWithFallback(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
        } else if (config.adsterra?.enabled == true) {
            openAdsterraDirectLink(activity, isVip = false, verificationSeconds = 10, onVerified = {
                onRewardUnlocked()
                onAdClosed?.invoke(true)
            })
        } else {
            onAdNotReadyOrFailed?.invoke("No ads available right now.")
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
                override fun onVideoCompleted() { userEarnedReward = true }
            })

            onDemandAd.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener {
                override fun onReceiveAd(loadedAd: Ad) {
                    onDemandAd.showAd(object : AdDisplayListener {
                        override fun adHidden(shownAd: Ad) {
                            if (userEarnedReward) onRewardUnlocked()
                            onAdClosed?.invoke(userEarnedReward)
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
                    onAdNotReadyOrFailed?.invoke("No video fill available.")
                    onAdClosed?.invoke(false)
                }
            })
        } catch (t: Throwable) {
            onAdNotReadyOrFailed?.invoke("Ad error")
            onAdClosed?.invoke(false)
        }
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

    fun openAdsterraDirectLink(context: Context, isVip: Boolean, fallbackUrl: String? = null, verificationSeconds: Int? = null, onVerified: (() -> Unit)? = null): Boolean {
        val config = _adConfigState.value
        if (isVip || !config.adsEnabled || config.adsterra?.enabled != true) return false

        val adsterra = config.adsterra
        val targetUrl = adsterra?.effectiveDirectLink?.trim()?.takeIf { it.isNotBlank() } ?: fallbackUrl

        if (targetUrl.isNullOrBlank() || (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://"))) return false

        openInAppBrowser(url = targetUrl, title = "Sponsored Ad", verificationSeconds = verificationSeconds, onVerified = onVerified)
        return true
    }

    fun openSmartlink(context: Context, isVip: Boolean, fallbackUrl: String? = null, verificationSeconds: Int? = null, onVerified: (() -> Unit)? = null): Boolean = openAdsterraDirectLink(context, isVip, fallbackUrl, verificationSeconds, onVerified)

    // 🌟 UnlockEpisodeDialog এর জন্য কম্প্যাটিবিলিটি প্রোপার্টিজ ও মেথডস
    val isAdsterraPrimary: Boolean
        get() = _adConfigState.value.primaryNetwork.equals("adsterra", ignoreCase = true)

    fun isAdsterraPrimary(): Boolean = isAdsterraPrimary

    val isStartIoPrimary: Boolean
        get() = _adConfigState.value.primaryNetwork.equals("startio", ignoreCase = true)

    fun isStartIoPrimary(): Boolean = isStartIoPrimary

    val isUnityPrimary: Boolean
        get() = _adConfigState.value.primaryNetwork.equals("unity", ignoreCase = true)

    fun isUnityPrimary(): Boolean = isUnityPrimary

    val isDirectLinkAvailable: Boolean
        get() = _adConfigState.value.adsEnabled && _adConfigState.value.adsterra?.enabled == true && !_adConfigState.value.adsterra?.effectiveDirectLink.isNullOrBlank()

    fun isDirectLinkAvailable(isVip: Boolean = false): Boolean {
        return !isVip && isDirectLinkAvailable
    }

    val isSmartlinkAvailable: Boolean get() = isDirectLinkAvailable
    fun isSmartlinkAvailable(isVip: Boolean = false): Boolean = isDirectLinkAvailable(isVip)

    val effectiveDirectLink: String? get() = if (_adConfigState.value.adsterra?.enabled == true) _adConfigState.value.adsterra?.effectiveDirectLink else null
    fun getEffectiveDirectLink(): String? = effectiveDirectLink

    val smartlinkUrl: String? get() = effectiveDirectLink
    fun getSmartlinkUrl(): String? = smartlinkUrl

    val verificationTimerSeconds: Int get() = _adConfigState.value.rules?.timerSeconds ?: 10
    fun getVerificationTimerSeconds(): Int = verificationTimerSeconds

    val unlockDurationHours: Int get() = _adConfigState.value.rules?.rewardedUnlockHours ?: 2
    fun getUnlockDurationHours(): Int = unlockDurationHours

    val freeUnlockedEpisodesCount: Int get() = _adConfigState.value.rules?.freeUnlockedEpisodes ?: 1
    fun getFreeUnlockedEpisodesCount(): Int = freeUnlockedEpisodesCount

    val isAdsGloballyEnabled: Boolean get() = _adConfigState.value.adsEnabled
    fun isAdsGloballyEnabled(): Boolean = isAdsGloballyEnabled

    // ============================================================
    // 🎬 INTERSTITIAL ADS
    // ============================================================

    fun showInterstitial(context: Context, isVip: Boolean, onComplete: () -> Unit) {
        val config = _adConfigState.value
        if (isVip || !config.adsEnabled) {
            onComplete()
            return
        }

        val activity = context.findActivity() ?: run { onComplete(); return }
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
                        override fun onUnityAdsShowComplete(placementId: String, state: UnityAds.UnityAdsShowCompletionState) { onComplete() }
                        override fun onUnityAdsShowFailure(placementId: String, error: UnityAds.UnityAdsShowError, message: String) { onComplete() }
                    })
                }
                override fun onUnityAdsFailedToLoad(placementId: String, error: UnityAds.UnityAdsLoadError, message: String) { onComplete() }
            })
        } else if (isStartIoOn) {
            val ad = StartAppAd(activity)
            ad.loadAd(StartAppAd.AdMode.AUTOMATIC, object : AdEventListener {
                override fun onReceiveAd(adItem: Ad) {
                    ad.showAd(object : AdDisplayListener {
                        override fun adHidden(shownAd: Ad) { onComplete() }
                        override fun adDisplayed(shownAd: Ad) {}
                        override fun adClicked(shownAd: Ad) {}
                        override fun adNotDisplayed(shownAd: Ad) { onComplete() }
                    })
                }
                override fun onFailedToReceiveAd(adItem: Ad?) { onComplete() }
            })
        } else {
            onComplete()
        }
    }

    fun preloadInterstitial(context: Context) {}
    fun preloadRewardedVideo(context: Context, onLoaded: (() -> Unit)? = null, onFailed: ((String) -> Unit)? = null) {}
}

// ============================================================
// 📱 UNIFIED AD BANNER
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
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
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
