package com.example.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * StartIoAdManager (Delegates to UnifiedAdManager)
 * Provided for backward compatibility across existing screens while powered by UnifiedAdManager.
 */

// Helper to safely find Activity from any Context
fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

object StartIoAdManager {
    const val PUBLISHER_ID = "113502454"
    const val STARTIO_APP_ID = "207238360"
    const val APP_ADS_TXT_URL = "https://playdramaflix.com/app-ads.txt"

    fun init(context: Context, isVip: Boolean = false) {
        UnifiedAdManager.init(context, isVip = isVip)
    }

    fun preloadInterstitial(context: Context) {
        UnifiedAdManager.preloadInterstitial(context)
    }

    fun showInterstitial(
        context: Context,
        isVip: Boolean,
        onAdClosed: () -> Unit
    ) {
        UnifiedAdManager.showInterstitial(context, isVip, onAdClosed)
    }

    fun preloadRewardedVideo(
        context: Context,
        onLoaded: (() -> Unit)? = null,
        onFailed: ((String) -> Unit)? = null
    ) {
        UnifiedAdManager.preloadRewardedVideo(context, onLoaded, onFailed)
    }

    fun preloadRewarded(context: Context) = preloadRewardedVideo(context)
    fun loadRewardedVideoAd(context: Context) = preloadRewardedVideo(context)

    fun showRewardedVideo(
        context: Context,
        isVip: Boolean,
        onRewardUnlocked: () -> Unit,
        onAdNotReadyOrFailed: ((reason: String) -> Unit)? = null,
        onAdClosed: ((rewardEarned: Boolean) -> Unit)? = null
    ) {
        UnifiedAdManager.showRewardedVideo(
            context = context,
            isVip = isVip,
            onRewardUnlocked = onRewardUnlocked,
            onAdNotReadyOrFailed = onAdNotReadyOrFailed,
            onAdClosed = onAdClosed
        )
    }

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
 * StartAppBanner backward-compatible wrapper
 */
@Composable
fun StartAppBanner(
    isVip: Boolean,
    modifier: Modifier = Modifier
) {
    UnifiedAdBanner(isVip = isVip, modifier = modifier)
}
