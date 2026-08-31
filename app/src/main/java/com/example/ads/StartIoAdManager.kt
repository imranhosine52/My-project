package com.example.ads

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * StartIoAdManager (Delegates to UnifiedAdManager)
 * ব্যাকওয়ার্ড কম্প্যাটিবিলিটি ও সহজ মেথড কলিংয়ের জন্য র্যাপার অবজেক্ট।
 */
object StartIoAdManager {
    const val PUBLISHER_ID = "113502454"
    const val STARTIO_APP_ID = "207238360"

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

    // 👈 সহজ রিওয়ার্ডেড অ্যাড কলিং
    fun showRewardedAd(
        activity: Activity,
        onRewardEarned: (Boolean) -> Unit
    ) {
        UnifiedAdManager.showRewardedAd(activity, onRewardEarned)
    }

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
