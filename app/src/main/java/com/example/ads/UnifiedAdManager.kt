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
import com.startapp.sdk.ads.banner.Banner
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import com.startapp.sdk.adsbase.adlisteners.VideoListener
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
 * 📡 CLEAN AD MEDIATION ENGINE: Start.io + Adsterra Smartlink
 * (Strict Server Switch • 0s Instant Launch • Dynamic Rules)
 * ============================================================
 */
object UnifiedAdManager {
    private const val TAG = "UnifiedAdManager"

    private const val DEFAULT_STARTIO_APP_ID = "207238360"
    private const val INTERSTITIAL_PAGE_INTERVAL = 3
    private var interstitialNavCount = 0

    // লাইভ অ্যাড কনফিগারেশন স্টেট
    private val _adConfigState = MutableStateFlow(
        AdsConfigResponse(
            success = true,
            status = 200,
            adsEnabled = true,
            primaryNetwork = "startio",
            fallbackNetwork = "adsterra",
            startio = StartIoConfig(enabled = true, appId = DEFAULT_STARTIO_APP_ID),
            adsterra = AdsterraConfig(enabled = true),
            rules = AdRulesConfig(
                timerSeconds = 5,
                adWaitSeconds = 5,
                unlockClicksRequired = 1,
                rewardedUnlockHours = 2,
                freeUnlockedEpisodes = 1
            )
        )
    )
    val adConfigState: StateFlow<AdsConfigResponse> = _adConfigState.asStateFlow()

    private var isStartIoInitialized = false
    private var currentStartIoAppId: String = DEFAULT_STARTIO_APP_ID

    // Start.io অবজেক্ট
    private var startIoInterstitialAd: StartAppAd? = null
    private var startIoRewardedAd: StartAppAd? = null
    private var isStartIoInterstitialLoading = false
    private var isStartIoRewardedLoading = false

    private var pageTransitionCount = 0
    private var lastPopunderTimestamp = 0L

    // ============================================================
    // 🌐 ০ সেকেন্ডে CHROME CUSTOM TABS ওপেন
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
            Log.e(TAG, "Chrome Custom Tab launch failed: ${t.message}")
            openUrlSafely(context, url)
        }
    }

    // ============================================================
    // 🚀 INITIALIZATION
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
        if (!config.adsEnabled || isVip) return

        // 🎯 সার্ভার সুইচ চেক: Start.io অন থাকলেই শুধু ইনিশিয়ালাইজ হবে
        if (config.startio?.enabled == true) {
            val startIoAppId = config.startio.appId.takeIf { it.isNotBlank() } ?: DEFAULT_STARTIO_APP_ID
            val isTestMode = config.startio.testMode
            initializeStartIo(context, startIoAppId, isVip, isTestMode)
        }
    }

    fun applyRemoteConfig(context: Context, newConfig: AdsConfigResponse, isVip: Boolean = false) {
        _adConfigState.value = newConfig
        if (!newConfig.adsEnabled || isVip) return

        if (newConfig.startio?.enabled == true) {
            val newAppId = newConfig.startio.appId.takeIf { it.isNotBlank() } ?: DEFAULT_STARTIO_APP_ID
            val isTestMode = newConfig.startio.testMode
            if (newAppId != currentStartIoAppId || !isStartIoInitialized) {
                initializeStartIo(context, newAppId, isVip, isTestMode)
            } else {
                preloadInterstitial(context)
                preloadStartIoRewarded(context)
            }
        }
    }

    private fun initializeStartIo(context: Context, appId: String, isVip: Boolean, testMode: Boolean) {
        try {
            currentStartIoAppId = appId
            val appContext = context.applicationContext

            StartAppSDK.init(appContext, appId, false)
            StartAppSDK.setTestAdsEnabled(testMode)
            StartAppAd.disableSplash()
            StartAppSDK.enableReturnAds(false)
            
            isStartIoInitialized = true
            Log.i(TAG, "✓ Start.io Initialized (TestMode: $testMode).")

            if (!isVip) {
                preloadInterstitial(context)
                preloadStartIoRewarded(context)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Start.io init error: ${t.message}")
        }
    }

    // ============================================================
    // 🎁 REWARDED VIDEO ADS (সার্ভার সুইচ অনুযায়ী)
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

        // ১. ভিআইপি ইউজার অথবা অ্যাড বন্ধ থাকলে সরাসরি আনলক
        if (isVip || !config.adsEnabled) {
            onRewardUnlocked()
            onAdClosed?.invoke(true)
            return
        }

        val isStartIoOn = config.startio?.enabled == true
        val isAdsterraOn = config.adsterra?.enabled == true

        // ২. সার্ভার থেকে দুটিই বন্ধ থাকলে কোনো বিজ্ঞাপন ছাড়াই সরাসরি আনলক
        if (!isStartIoOn && !isAdsterraOn) {
            onRewardUnlocked()
            onAdClosed?.invoke(true)
            return
        }

        val activity = context.findActivity() ?: run {
            if (isAdsterraOn) {
                fallbackToAdsterraDirectLink(context, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
            } else {
                onRewardUnlocked()
                onAdClosed?.invoke(true)
            }
            return
        }

        // ৩. সার্ভার সুইচ অনুযায়ী পরিচালনা
        if (isStartIoOn) {
            showStartIoRewardedWithFallback(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
        } else if (isAdsterraOn) {
            // Start.io অফ থাকলে সরাসরি ০ সেকেন্ডে Adsterra ওপেন
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
                            handleStartIoFailed(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
                        }
                    })
                }
                override fun onFailedToReceiveAd(failedAd: Ad?) {
                    handleStartIoFailed(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
                }
            })
        } catch (t: Throwable) {
            handleStartIoFailed(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
        }
    }

    private fun handleStartIoFailed(
        activity: Activity,
        onRewardUnlocked: () -> Unit,
        onAdNotReadyOrFailed: ((reason: String) -> Unit)?,
        onAdClosed: ((rewardEarned: Boolean) -> Unit)?
    ) {
        val config = _adConfigState.value
        if (config.adsterra?.enabled == true) {
            fallbackToAdsterraDirectLink(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
        } else {
            onRewardUnlocked()
            onAdClosed?.invoke(true)
        }
    }

    private fun fallbackToAdsterraDirectLink(
        context: Context,
        onRewardUnlocked: () -> Unit,
        onAdNotReadyOrFailed: ((reason: String) -> Unit)?,
        onAdClosed: ((rewardEarned: Boolean) -> Unit)?
    ) {
        val backupLink = "https://playdramaflix.com"
        val smartlinkOpened = openSmartlink(context, isVip = false, fallbackUrl = backupLink)
        if (smartlinkOpened) {
            onRewardUnlocked()
            onAdClosed?.invoke(true)
        } else {
            onRewardUnlocked()
            onAdClosed?.invoke(true)
        }
    }

    // ============================================================
    // 🎬 INTERSTITIAL ADS (৩ পেজ পরপর • শুধুমাত্র অন থাকলে)
    // ============================================================
    fun showInterstitial(
        context: Context,
        isVip: Boolean,
        onComplete: () -> Unit
    ) {
        val config = _adConfigState.value
        if (isVip || !config.adsEnabled || config.startio?.enabled != true) {
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

        showStartIoInterstitial(activity, onComplete)
    }

    private fun showStartIoInterstitial(activity: Activity, onComplete: () -> Unit) {
        val ad = startIoInterstitialAd
        if (ad != null && ad.isReady) {
            ad.showAd(object : AdDisplayListener {
                override fun adHidden(ad: Ad) {
                    startIoInterstitialAd = null
                    preloadInterstitial(activity)
                    onComplete()
                }
                override fun adDisplayed(ad: Ad) {}
                override fun adClicked(ad: Ad) {}
                override fun adNotDisplayed(ad: Ad) {
                    startIoInterstitialAd = null
                    preloadInterstitial(activity)
                    onComplete()
                }
            })
        } else {
            try {
                val onDemandAd = StartAppAd(activity)
                onDemandAd.loadAd(StartAppAd.AdMode.AUTOMATIC, object : AdEventListener {
                    override fun onReceiveAd(receivedAd: Ad) {
                        onDemandAd.showAd(object : AdDisplayListener {
                            override fun adHidden(shownAd: Ad) {
                                preloadInterstitial(activity)
                                onComplete()
                            }
                            override fun adDisplayed(shownAd: Ad) {}
                            override fun adClicked(shownAd: Ad) {}
                            override fun adNotDisplayed(shownAd: Ad) {
                                preloadInterstitial(activity)
                                onComplete()
                            }
                        })
                    }
                    override fun onFailedToReceiveAd(failedAd: Ad?) {
                        preloadInterstitial(activity)
                        onComplete()
                    }
                })
            } catch (t: Throwable) {
                preloadInterstitial(activity)
                onComplete()
            }
        }
    }

    // ============================================================
    // ⚡ PRELOADERS
    // ============================================================
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
                }
                override fun onFailedToReceiveAd(failedAd: Ad?) {
                    isStartIoInterstitialLoading = false
                }
            })
        } catch (t: Throwable) {
            isStartIoInterstitialLoading = false
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

    fun preloadRewardedVideo(context: Context, onLoaded: (() -> Unit)? = null, onFailed: ((String) -> Unit)? = null) {
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

        val targetUrl = config.adsterra?.effectiveDirectLink?.trim()?.takeIf { it.isNotBlank() }
            ?: fallbackUrl
            ?: "https://playdramaflix.com"

        return openChromeCustomTab(context, targetUrl)
    }

    fun openSmartlink(
        context: Context,
        isVip: Boolean,
        fallbackUrl: String? = null
    ): Boolean = openAdsterraDirectLink(context, isVip, fallbackUrl)

    // 🎯 ডাইনামিক রুলস ও হেল্পার মেথড
    fun isAdsterraPrimary(): Boolean = _adConfigState.value.primaryNetwork.contains("adsterra", ignoreCase = true) fun isStartIoPrimary(): Boolean = _adConfigState.value.primaryNetwork.contains("start", ignoreCase = true)

    fun isDirectLinkAvailable(isVip: Boolean = false): Boolean {
        val config = _adConfigState.value
        if (isVip || !config.adsEnabled) return false
        val adsterra = config.adsterra ?: return false
        return adsterra.enabled && !adsterra.effectiveDirectLink.isNullOrBlank()
    }

    fun isSmartlinkAvailable(isVip: Boolean = false): Boolean = isDirectLinkAvailable(isVip)
    fun getEffectiveDirectLink(): String? = if (_adConfigState.value.adsEnabled) _adConfigState.value.adsterra?.effectiveDirectLink else null
    fun getSmartlinkUrl(): String? = getEffectiveDirectLink()

    // ⏱️ ডায়নামিক সেটিংস (সার্ভার থেকে আসে)
    fun getUnlockClicksRequired(): Int = _adConfigState.value.rules?.unlockClicksRequired?.coerceAtLeast(1) ?: 1
    fun getVerificationTimerSeconds(): Int = (_adConfigState.value.rules?.adWaitSeconds ?: _adConfigState.value.rules?.timerSeconds ?: 5).coerceAtLeast(1)
    fun getUnlockDurationHours(): Int = _adConfigState.value.rules?.rewardedUnlockHours?.coerceAtLeast(1) ?: 2
    fun getFreeUnlockedEpisodesCount(): Int = _adConfigState.value.rules?.freeUnlockedEpisodes?.coerceAtLeast(1) ?: 1

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
 * 📱 ব্যানার (শুধুমাত্র Start.io অন থাকলেই রেন্ডার হবে)
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
                    }
                } catch (t: Throwable) {
                    View(ctx)
                }
            }
        )
    }
}
