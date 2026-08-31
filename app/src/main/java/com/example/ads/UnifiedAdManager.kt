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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.AdsConfigResponse
import com.example.data.model.AdsterraConfig
import com.example.data.model.AdMobConfig
import com.example.data.model.AdRulesConfig
import com.example.data.model.StartIoConfig
import com.example.data.model.UnityAdsConfig
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

object UnifiedAdManager {
    private const val TAG = "UnifiedAdManager"

    // ⚠️ আপনার Unity Dashboard (cloud.unity.com) থেকে ৭ ডিজিটের Android Game ID টি এখানে দিন
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
                testMode = true // 👈 টেস্ট অ্যাড আসার জন্য true রাখা হয়েছে
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
            initUnityAds(context, unityGameId, true) // Force testMode true
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
            initUnityAds(context, unityGameId, true)
        }

        val startIoConfig = newConfig.startio
        if (startIoConfig?.enabled == true) {
            val newAppId = startIoConfig.appId.takeIf { it.isNotBlank() } ?: DEFAULT_STARTIO_APP_ID
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

                        override fun onInitializationFailed(error: UnityAds.UnityAdsInitializationError?, message: String?) {
                            isUnityInitialized = false
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
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to init Start.io SDK: ${t.message}")
        }
    }

    // ============================================================
    // 🎁 REWARDED VIDEO ADS (STRICT NO-BYPASS REWARD SYSTEM)
    // ============================================================

    fun showRewardedAd(
        activity: Activity,
        onRewardEarned: (Boolean) -> Unit
    ) {
        showRewardedVideo(
            context = activity,
            isVip = false,
            onRewardUnlocked = { onRewardEarned(true) },
            onAdNotReadyOrFailed = { reason ->
                Toast.makeText(activity, reason, Toast.LENGTH_LONG).show()
                onRewardEarned(false)
            },
            onAdClosed = { rewarded -> 
                if (!rewarded) onRewardEarned(false) 
            }
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

        if (isVip) {
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

        val unityConfig = config.unity
        val isUnityOn = unityConfig?.enabled == true
        val isStartIoOn = config.startio?.enabled == true

        if (isUnityOn) {
            showUnityRewardedVideo(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
        } else if (isStartIoOn) {
            showStartIoRewardedVideoWithFallback(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
        } else {
            onAdNotReadyOrFailed?.invoke("No ad networks configured in admin panel.")
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
        val unityConfig = config.unity
        val placementId = unityConfig?.rewardedId?.takeIf { it.isNotBlank() } ?: "Rewarded_Android"
        val gameId = unityConfig?.gameId?.takeIf { it.isNotBlank() } ?: DEFAULT_UNITY_GAME_ID

        if (!UnityAds.isInitialized()) {
            Toast.makeText(activity, "Connecting to Unity Ads...", Toast.LENGTH_SHORT).show()
            UnityAds.initialize(
                activity.applicationContext,
                gameId,
                true,
                object : IUnityAdsInitializationListener {
                    override fun onInitializationComplete() {
                        isUnityInitialized = true
                        loadAndShowUnityAd(activity, placementId, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
                    }

                    override fun onInitializationFailed(error: UnityAds.UnityAdsInitializationError?, message: String?) {
                        Log.e(TAG, "Unity on-demand init failed: [$error] $message")
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
            override fun onUnityAdsAdLoaded(placement: String?) {
                Log.i(TAG, "✓ Unity Rewarded Video Loaded! Showing now...")
                UnityAds.show(activity, placement ?: "Rewarded_Android", UnityAdsShowOptions(), object : IUnityAdsShowListener {
                    override fun onUnityAdsShowStart(p0: String?) {}
                    override fun onUnityAdsShowClick(p0: String?) {}

                    override fun onUnityAdsShowComplete(p0: String?, state: UnityAds.UnityAdsShowCompletionState?) {
                        if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                            Log.i(TAG, "✓ Unity Video Completed! Unlocking episode.")
                            onRewardUnlocked()
                            onAdClosed?.invoke(true)
                        } else {
                            Toast.makeText(activity, "You must watch the full ad to unlock.", Toast.LENGTH_SHORT).show()
                            onAdClosed?.invoke(false)
                        }
                    }

                    override fun onUnityAdsShowFailure(p0: String?, error: UnityAds.UnityAdsShowError?, message: String?) {
                        Log.w(TAG, "Unity Show Failed: [$error] $message. Trying fallback...")
                        handleUnityRewardFallback(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
                    }
                })
            }

            override fun onUnityAdsFailedToLoad(p0: String?, error: UnityAds.UnityAdsLoadError?, message: String?) {
                Log.w(TAG, "Unity Load Failed: [$error] $message. Trying fallback...")
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
            Log.i(TAG, "Fallback: Showing Start.io Rewarded Video...")
            showStartIoRewardedVideoWithFallback(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
        } else {
            onAdNotReadyOrFailed?.invoke("Ad failed to load. Please check Unity Game ID in settings.")
            onAdClosed?.invoke(false)
        }
    }

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
                    userEarnedReward = true
                }
            })

            onDemandAd.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener {
                override fun onReceiveAd(p0: Ad?) {
                    onDemandAd.showAd(object : AdDisplayListener {
                        override fun adHidden(p0: Ad?) {
                            if (userEarnedReward) {
                                onRewardUnlocked()
                                onAdClosed?.invoke(true)
                            } else {
                                onAdClosed?.invoke(false)
                            }
                        }

                        override fun adDisplayed(p0: Ad?) {}
                        override fun adClicked(p0: Ad?) {}

                        override fun adNotDisplayed(p0: Ad?) {
                            onAdNotReadyOrFailed?.invoke("Ad could not be displayed.")
                            onAdClosed?.invoke(false)
                        }
                    })
                }

                override fun onFailedToReceiveAd(failedAd: Ad?) {
                    // ⛔ কোনো ফলব্যাক ফ্রি আনলক নয় — অ্যাড না আসলে আনলক হবে না!
                    onAdNotReadyOrFailed?.invoke("Ad is not ready yet. Please try again in a moment.")
                    onAdClosed?.invoke(false)
                }
            })
        } catch (t: Throwable) {
            onAdNotReadyOrFailed?.invoke("Ad error: ${t.message}")
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

    fun isAdsterraPrimary(): Boolean = _adConfigState.value.primaryNetwork.equals("adsterra", ignoreCase = true)
    fun isStartIoPrimary(): Boolean = _adConfigState.value.primaryNetwork.equals("startio", ignoreCase = true)
    fun isUnityPrimary(): Boolean = _adConfigState.value.primaryNetwork.equals("unity", ignoreCase = true)

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

        val isUnityOn = config.unity?.enabled == true
        val isStartIoOn = config.startio?.enabled == true

        if (isUnityOn) {
            val placementId = config.unity?.interstitialId?.takeIf { it.isNotBlank() } ?: "Interstitial_Android"
            UnityAds.load(placementId, object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(p0: String?) {
                    UnityAds.show(activity, p0 ?: "Interstitial_Android", UnityAdsShowOptions(), object : IUnityAdsShowListener {
                        override fun onUnityAdsShowStart(p0: String?) {}
                        override fun onUnityAdsShowClick(p0: String?) {}
                        override fun onUnityAdsShowComplete(p0: String?, state: UnityAds.UnityAdsShowCompletionState?) { onComplete() }
                        override fun onUnityAdsShowFailure(p0: String?, error: UnityAds.UnityAdsShowError?, message: String?) {
                            if (isStartIoOn) showStartIoInterstitial(context, onComplete) else onComplete()
                        }
                    })
                }

                override fun onUnityAdsFailedToLoad(p0: String?, error: UnityAds.UnityAdsLoadError?, message: String?) {
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
                    override fun adHidden(p0: Ad?) {
                        startIoInterstitialAd = null
                        preloadInterstitial(context)
                        onComplete()
                    }
                    override fun adDisplayed(p0: Ad?) {}
                    override fun adClicked(p0: Ad?) {}
                    override fun adNotDisplayed(p0: Ad?) {
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
                override fun onReceiveAd(p0: Ad?) {
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
                override fun onUnityAdsAdLoaded(p0: String?) {
                    onLoaded?.invoke()
                }
                override fun onUnityAdsFailedToLoad(p0: String?, p1: UnityAds.UnityAdsLoadError?, p2: String?) {
                    onFailed?.invoke(p2 ?: "Unity ad failed to load")
                }
            })
        }
    }
}

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
                            override fun onReceiveAd(p0: View?) {}
                            override fun onFailedToReceiveAd(p0: View?) {}
                            override fun onClick(p0: View?) {}
                            override fun onImpression(p0: View?) {}
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
