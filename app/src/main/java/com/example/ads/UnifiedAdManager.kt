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
 * 📡 CLEAN AD ENGINE: Start.io + Adsterra Smartlink
 * (AdMob ও Unity Ads সম্পূর্ণ অপসারিত)
 * ============================================================
 */
object UnifiedAdManager {
    private const val TAG = "UnifiedAdManager"

    private const val DEFAULT_STARTIO_APP_ID = "207238360"
    private const val INTERSTITIAL_PAGE_INTERVAL = 3
    private var interstitialNavCount = 0

    // ডিফল্ট কনফিগারেশন
    private val _adConfigState = MutableStateFlow(
        AdsConfigResponse(
            success = true,
            status = 200,
            adsEnabled = true,
            primaryNetwork = "startio",
            fallbackNetwork = "adsterra",
            startio = StartIoConfig(enabled = true, appId = DEFAULT_STARTIO_APP_ID),
            adsterra = AdsterraConfig(enabled = true),
            rules = AdRulesConfig(timerSeconds = 10, rewardedUnlockHours = 2, freeUnlockedEpisodes = 1)
        )
    )
    val adConfigState: StateFlow<AdsConfigResponse> = _adConfigState.asStateFlow()

    private var isStartIoInitialized = false
    private var currentStartIoAppId: String = DEFAULT_STARTIO_APP_ID

    // Start.io প্রি-লোডেড অবজেক্ট
    private var startIoInterstitialAd: StartAppAd? = null
    private var startIoRewardedAd: StartAppAd? = null
    private var isStartIoInterstitialLoading = false
    private var isStartIoRewardedLoading = false

    private var pageTransitionCount = 0
    private var lastPopunderTimestamp = 0L

    // ============================================================
    // 🌐 CHROME CUSTOM TABS (Adsterra Direct Link)
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
            openUrlSafely(context, url)
        }
    }

    // ============================================================
    // 🚀 INITIALIZATION
    // ============================================================
    fun init(context: Context, initialConfig: AdsConfigResponse? = null, isVip: Boolean = false) {
        if (initialConfig != null) {
            _adConfigState.value = initialConfig
        }

        val config = _adConfigState.value
        if (!config.adsEnabled || isVip) return

        val startIoAppId = config.startio?.appId?.takeIf { it.isNotBlank() } ?: DEFAULT_STARTIO_APP_ID
        initializeStartIo(context, startIoAppId, isVip)
    }

    fun applyRemoteConfig(context: Context, newConfig: AdsConfigResponse, isVip: Boolean = false) {
        _adConfigState.value = newConfig
        if (!newConfig.adsEnabled || isVip) return

        val newAppId = newConfig.startio?.appId?.takeIf { it.isNotBlank() } ?: DEFAULT_STARTIO_APP_ID
        if (newAppId != currentStartIoAppId || !isStartIoInitialized) {
            initializeStartIo(context, newAppId, isVip)
        } else {
            preloadInterstitial(context)
            preloadStartIoRewarded(context)
        }
    }

    private fun initializeStartIo(context: Context, appId: String, isVip: Boolean) {
        try {
            currentStartIoAppId = appId
            val appContext = context.applicationContext

            val now = System.currentTimeMillis()
            StartAppSDK.setUserConsent(appContext, "pas", now, true)
            StartAppSDK.setUserConsent(appContext, "gdpr", now, true)
            StartAppSDK.setUserConsent(appContext, "ccpa", now, true)

            // Start.io ইনিট
            StartAppSDK.init(appContext, appId, false)
            StartAppAd.disableSplash()
            StartAppSDK.enableReturnAds(false)
            
            // 🎯 টেস্টিং অবস্থায় বিজ্ঞাপন নিশ্চিত করতে Test Ads চালু
            StartAppSDK.setTestAdsEnabled(true)

            isStartIoInitialized = true
            Log.i(TAG, "✓ Start.io SDK Initialized successfully.")

            if (!isVip) {
                preloadInterstitial(context)
                preloadStartIoRewarded(context)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Start.io init error: ${t.message}")
        }
    }

    // ============================================================
    // 🎁 REWARDED VIDEO ADS (Start.io -> Adsterra Fallback)
    // ============================================================
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
            fallbackToAdsterraDirectLink(context, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
            return
        }

        // Start.io রিওয়ার্ডেড দেখানোর চেষ্টা
        showStartIoRewardedWithFallback(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
    }

    private fun showStartIoRewardedWithFallback(
        activity: Activity,
        onRewardUnlocked: () -> Unit,
        onAdNotReadyOrFailed: ((reason: String) -> Unit)?,
        onAdClosed: ((rewardEarned: Boolean) -> Unit)?
    ) {
        val preloaded = startIoRewardedAd
        if (preloaded != null && preloaded.isReady) {
            var earnedReward = false
            preloaded.setVideoListener(object : VideoListener {
                override fun onVideoCompleted() { earnedReward = true }
            })
            preloaded.showAd(object : AdDisplayListener {
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
            var userEarned = false

            onDemandAd.setVideoListener(object : VideoListener {
                override fun onVideoCompleted() { userEarned = true }
            })

            onDemandAd.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener {
                override fun onReceiveAd(loadedAd: Ad) {
                    onDemandAd.showAd(object : AdDisplayListener {
                        override fun adHidden(shownAd: Ad) {
                            if (userEarned) onRewardUnlocked()
                            onAdClosed?.invoke(userEarned)
                            preloadStartIoRewarded(activity)
                        }
                        override fun adDisplayed(shownAd: Ad) {}
                        override fun adClicked(shownAd: Ad) {}
                        override fun adNotDisplayed(shownAd: Ad) {
                            fallbackToAdsterraDirectLink(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
                        }
                    })
                }
                override fun onFailedToReceiveAd(failedAd: Ad?) {
                    // Start.io না পেলে Adsterra খুলবে
                    fallbackToAdsterraDirectLink(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
                }
            })
        } catch (t: Throwable) {
            fallbackToAdsterraDirectLink(activity, onRewardUnlocked, onAdNotReadyOrFailed, onAdClosed)
        }
    }

    private fun fallbackToAdsterraDirectLink(
        context: Context,
        onRewardUnlocked: () -> Unit,
        onAdNotReadyOrFailed: ((reason: String) -> Unit)?,
        onAdClosed: ((rewardEarned: Boolean) -> Unit)?
    ) {
        // 🎯 ব্যাকআপ সেফ লিংক যাতে কখনোই null না হয়ে ফেইল না করে
        val backupLink = "https://playdramaflix.com"
        val smartlinkOpened = openSmartlink(context, isVip = false, fallbackUrl = backupLink)
        
        if (smartlinkOpened) {
            onRewardUnlocked()
            onAdClosed?.invoke(true)
        } else {
            // লিংক ওপেন করতে না পারলেও পর্ব আনলক করে দেওয়া (যাতে ইউজার আটকে না থাকে)
            onRewardUnlocked()
            onAdClosed?.invoke(true)
        }
    }

    // ============================================================
    // 🎬 INTERSTITIAL ADS (Start.io On-Demand)
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
            // 🎯 লাইভ অন-ডিমান্ড লোড
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
        if (isVip || !config.adsEnabled) return false

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

    // হেল্পার মেথড
    fun isAdsGloballyEnabled(): Boolean = _adConfigState.value.adsEnabled
    fun isDirectLinkAvailable(isVip: Boolean = false): Boolean = !isVip && _adConfigState.value.adsEnabled
    fun getVerificationTimerSeconds(): Int = _adConfigState.value.rules?.timerSeconds ?: 10
    fun getUnlockDurationHours(): Int = _adConfigState.value.rules?.rewardedUnlockHours ?: 2

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
 * 📱 ব্যানার (শুধুমাত্র Start.io ব্যানার)
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
