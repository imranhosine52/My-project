package com.example.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.startapp.sdk.ads.banner.Banner
import com.startapp.sdk.ads.banner.BannerListener
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import com.startapp.sdk.adsbase.adlisteners.VideoListener

/**
 * Start.io (StartApp) Ads Manager for Play Drama Flix
 * Credentials:
 * - Ad Network: Start.io (StartApp Ads SDK)
 * - Publisher / Account ID: 113502454
 * - Start.io App ID: 207214769
 * - App-ads.txt: https://playdramaflix.com/app-ads.txt
 *
 * Strict VIP Member Ad-Bypass:
 * - If is_vip == true, all ads (Banners, Interstitials, Rewarded) are completely suppressed.
 */

// Helper to safely find Activity from any Context
internal fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

object StartIoAdManager {
    private const val TAG = "StartIoAdManager"

    const val PUBLISHER_ID = "113502454"
    const val STARTIO_APP_ID = "207214769"
    const val APP_ADS_TXT_URL = "https://playdramaflix.com/app-ads.txt"

    private var isInitialized = false
    private var interstitialAd: StartAppAd? = null
    private var rewardedAd: StartAppAd? = null
    private var isInterstitialLoading = false
    private var isRewardedLoading = false

    /**
     * 1. Initialize Start.io Ads SDK on application startup with App ID: 207214769
     */
    fun init(context: Context, isVip: Boolean = false) {
        if (isInitialized) {
            Log.d(TAG, "Start.io SDK already initialized.")
            return
        }
        try {
            // Configure and initialize StartApp SDK with App ID 207214769
            StartAppSDK.init(context, STARTIO_APP_ID, false)
            StartAppSDK.setTestAdsEnabled(false) // Disable test ads mode to deliver real live ads
            StartAppAd.disableSplash()
            StartAppSDK.enableReturnAds(false)
            isInitialized = true
            Log.i(TAG, "Start.io SDK initialized with LIVE ads. App ID: $STARTIO_APP_ID (Publisher: $PUBLISHER_ID)")

            // Preload ads if user is not VIP
            if (!isVip) {
                val act = context.findActivity() ?: context
                preloadInterstitial(act)
                preloadRewardedVideo(act)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize Start.io SDK: ${t.message}")
        }
    }

    /**
     * 2. Preload Interstitial Ad in background
     */
    fun preloadInterstitial(context: Context) {
        if (isInterstitialLoading && interstitialAd != null) return
        isInterstitialLoading = true
        try {
            val act = context.findActivity() ?: context
            val ad = StartAppAd(act)
            ad.loadAd(StartAppAd.AdMode.AUTOMATIC, object : AdEventListener {
                override fun onReceiveAd(receivedAd: Ad) {
                    isInterstitialLoading = false
                    interstitialAd = ad
                    Log.d(TAG, "Start.io Interstitial Ad preloaded successfully.")
                }

                override fun onFailedToReceiveAd(failedAd: Ad?) {
                    isInterstitialLoading = false
                    Log.w(TAG, "Start.io Interstitial Ad failed to load: ${failedAd?.errorMessage}")
                }
            })
        } catch (t: Throwable) {
            isInterstitialLoading = false
            Log.e(TAG, "Error preloading Interstitial Ad: ${t.message}")
        }
    }

    /**
     * 3. Show Interstitial Ad with Context & VIP Ad-Bypass
     * When free user clicks to play or switch an episode.
     * Always calls onAdClosed to guarantee uninterrupted playback.
     */
    fun showInterstitial(
        context: Context,
        isVip: Boolean,
        onAdClosed: () -> Unit
    ) {
        // Strict VIP Bypass: Free of all ads
        if (isVip) {
            Log.d(TAG, "VIP user: Interstitial ad bypassed.")
            onAdClosed()
            return
        }

        try {
            val activity = context.findActivity()
            val ad = interstitialAd
            if (activity != null && ad != null && ad.isReady) {
                ad.showAd(object : AdDisplayListener {
                    override fun adHidden(ad: Ad) {
                        Log.d(TAG, "Interstitial ad dismissed by user.")
                        interstitialAd = null
                        preloadInterstitial(context)
                        onAdClosed()
                    }

                    override fun adDisplayed(ad: Ad) {
                        Log.d(TAG, "Interstitial ad displayed on screen.")
                    }

                    override fun adClicked(ad: Ad) {
                        Log.d(TAG, "Interstitial ad clicked.")
                    }

                    override fun adNotDisplayed(ad: Ad) {
                        Log.w(TAG, "Interstitial ad could not be displayed.")
                        interstitialAd = null
                        preloadInterstitial(context)
                        onAdClosed()
                    }
                })
            } else {
                Log.d(TAG, "Interstitial ad not ready or no Activity; proceeding directly with video playback.")
                preloadInterstitial(context)
                onAdClosed()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error showing Interstitial ad: ${t.message}")
            preloadInterstitial(context)
            onAdClosed()
        }
    }

    /**
     * 4. Preload Rewarded Video Ad in background
     */
    fun preloadRewardedVideo(
        context: Context,
        onLoaded: (() -> Unit)? = null,
        onFailed: ((String) -> Unit)? = null
    ) {
        if (isRewardedLoading && rewardedAd != null) return
        isRewardedLoading = true
        try {
            val act = context.findActivity() ?: context
            val ad = StartAppAd(act)
            ad.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener {
                override fun onReceiveAd(receivedAd: Ad) {
                    isRewardedLoading = false
                    rewardedAd = ad
                    Log.d(TAG, "Start.io Rewarded Video Ad preloaded successfully.")
                    onLoaded?.invoke()
                }

                override fun onFailedToReceiveAd(failedAd: Ad?) {
                    isRewardedLoading = false
                    val error = failedAd?.errorMessage ?: "Ad failed to load"
                    Log.w(TAG, "Start.io Rewarded Video Ad failed to load: $error")
                    onFailed?.invoke(error)
                }
            })
        } catch (t: Throwable) {
            isRewardedLoading = false
            Log.e(TAG, "Error preloading Rewarded Video Ad: ${t.message}")
            onFailed?.invoke(t.message ?: "Unknown error")
        }
    }

    // Alias for compatibility
    fun preloadRewarded(context: Context) = preloadRewardedVideo(context)
    fun loadRewardedVideoAd(context: Context) = preloadRewardedVideo(context)
    fun isRewardedVideoReady(): Boolean = rewardedAd?.isReady == true

    /**
     * 5. Show Rewarded Video Ad (Episode 2-Click Unlock)
     * CRITICAL SECURITY RULE:
     * - ONLY grants reward if user completes watching the full rewarded video (`onVideoCompleted`).
     * - NEVER grants reward on ad failure, dismissal before completion, or missing ad.
     */
    fun showRewardedVideo(
        context: Context,
        isVip: Boolean,
        onRewardUnlocked: () -> Unit,
        onAdNotReadyOrFailed: ((reason: String) -> Unit)? = null,
        onAdClosed: ((rewardEarned: Boolean) -> Unit)? = null
    ) {
        // Strict VIP Bypass: Auto unlock without ads
        if (isVip) {
            Log.d(TAG, "VIP user: Rewarded ad bypassed, episode auto-unlocked.")
            onRewardUnlocked()
            onAdClosed?.invoke(true)
            return
        }

        val activity = context.findActivity()
        if (activity == null) {
            Log.w(TAG, "No Activity context found to display Rewarded Video Ad.")
            onAdNotReadyOrFailed?.invoke("Screen context not ready. Please try again.")
            onAdClosed?.invoke(false)
            return
        }

        try {
            val ad = rewardedAd
            if (ad != null && ad.isReady) {
                var userEarnedReward = false

                // Listen ONLY for complete video view
                ad.setVideoListener(object : VideoListener {
                    override fun onVideoCompleted() {
                        Log.i(TAG, "Start.io Rewarded Video completed! Setting reward flag to TRUE.")
                        userEarnedReward = true
                    }
                })

                ad.showAd(object : AdDisplayListener {
                    override fun adHidden(shownAd: Ad) {
                        Log.d(TAG, "Rewarded ad closed. userEarnedReward: $userEarnedReward")
                        if (userEarnedReward) {
                            onRewardUnlocked()
                        }
                        onAdClosed?.invoke(userEarnedReward)
                        // Reset and preload next ad
                        rewardedAd = null
                        preloadRewardedVideo(activity)
                    }

                    override fun adDisplayed(shownAd: Ad) {
                        Log.d(TAG, "Rewarded ad displayed on screen.")
                    }

                    override fun adClicked(shownAd: Ad) {
                        Log.d(TAG, "Rewarded ad clicked.")
                    }

                    override fun adNotDisplayed(shownAd: Ad) {
                        Log.w(TAG, "Rewarded ad could not be displayed. NO UNLOCK GRANTED.")
                        onAdNotReadyOrFailed?.invoke("Ad could not be displayed. Please try again.")
                        onAdClosed?.invoke(false)
                        rewardedAd = null
                        preloadRewardedVideo(activity)
                    }
                })
            } else {
                Log.d(TAG, "Preloaded Rewarded ad not ready; loading on-demand for immediate display...")
                val onDemandAd = StartAppAd(activity)
                var userEarnedReward = false

                onDemandAd.setVideoListener(object : VideoListener {
                    override fun onVideoCompleted() {
                        Log.i(TAG, "Start.io On-Demand Rewarded Video completed! Reward flag = TRUE.")
                        userEarnedReward = true
                    }
                })

                onDemandAd.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener {
                    override fun onReceiveAd(loadedAd: Ad) {
                        Log.d(TAG, "On-demand Rewarded Ad loaded successfully, displaying now.")
                        onDemandAd.showAd(object : AdDisplayListener {
                            override fun adHidden(shownAd: Ad) {
                                Log.d(TAG, "On-demand Rewarded ad closed. userEarnedReward: $userEarnedReward")
                                if (userEarnedReward) {
                                    onRewardUnlocked()
                                }
                                onAdClosed?.invoke(userEarnedReward)
                                preloadRewardedVideo(activity)
                            }

                            override fun adDisplayed(shownAd: Ad) {
                                Log.d(TAG, "On-demand Rewarded ad displayed.")
                            }

                            override fun adClicked(shownAd: Ad) {}

                            override fun adNotDisplayed(shownAd: Ad) {
                                Log.w(TAG, "On-demand Rewarded ad could not be displayed. NO UNLOCK.")
                                onAdNotReadyOrFailed?.invoke("Ad could not be displayed. Please try again.")
                                onAdClosed?.invoke(false)
                                preloadRewardedVideo(activity)
                            }
                        })
                    }

                    override fun onFailedToReceiveAd(failedAd: Ad?) {
                        val errMsg = failedAd?.errorMessage ?: "No ad fill"
                        Log.w(TAG, "On-demand Rewarded ad failed to load: $errMsg")
                        onAdNotReadyOrFailed?.invoke("Ad loading: $errMsg. Please try again.")
                        onAdClosed?.invoke(false)
                        preloadRewardedVideo(activity)
                    }
                })
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error showing Rewarded ad: ${t.message}. DO NOT UNLOCK.")
            onAdNotReadyOrFailed?.invoke("Error loading ad: ${t.localizedMessage ?: "Please try again"}")
            onAdClosed?.invoke(false)
            preloadRewardedVideo(activity)
        }
    }

    /**
     * Backward-compatible overload for showRewardedVideo
     */
    fun showRewardedVideo(
        context: Context,
        isVip: Boolean,
        onRewardUnlocked: () -> Unit,
        onAdClosed: () -> Unit
    ) {
        showRewardedVideo(
            context = context,
            isVip = isVip,
            onRewardUnlocked = onRewardUnlocked,
            onAdNotReadyOrFailed = null,
            onAdClosed = { onAdClosed() }
        )
    }
}

/**
 * 6. Adaptive Start.io Banner Ad Composable
 * Returns empty footprint (Modifier.size(0.dp)) if user is VIP.
 */
@Composable
fun StartAppBanner(
    isVip: Boolean,
    modifier: Modifier = Modifier
) {
    if (isVip) {
        // VIP bypass: zero layout space, no network ad requests
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
                                Log.d("StartAppBanner", "Banner ad loaded successfully.")
                            }

                            override fun onFailedToReceiveAd(banner: View) {
                                Log.w("StartAppBanner", "Banner ad failed to load or no fill.")
                            }

                            override fun onClick(banner: View) {
                                Log.d("StartAppBanner", "Banner ad clicked.")
                            }

                            override fun onImpression(banner: View) {
                                Log.d("StartAppBanner", "Banner ad impression logged.")
                            }
                        })
                    }
                } catch (t: Throwable) {
                    Log.w("StartAppBanner", "Banner creation fallback: ${t.message}")
                    View(ctx)
                }
            },
            onRelease = { bannerView ->
                try {
                    if (bannerView is Banner) {
                        bannerView.hideBanner()
                    }
                } catch (t: Throwable) {
                    Log.w("StartAppBanner", "Banner release notice: ${t.message}")
                }
            }
        )
    }
}
