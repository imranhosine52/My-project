package com.example.ads

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * StartIoAdManager (Delegates to UnifiedAdManager)
 * ব্যাকওয়ার্ড কম্প্যাটিবিলিটি ও সহজ মেথড কলিংয়ের জন্য ক্লিন র্যাপার অবজেক্ট।
 * এটি স্বয়ংক্রিয়ভাবে Start.io এবং Adsterra ডিরেক্ট লিংকের সমন্বিত ইঞ্জিন পরিচালনা করে।
 */
object StartIoAdManager {
    const val PUBLISHER_ID = "113502454"
    const val STARTIO_APP_ID = "207238360"

    /**
     * Start.io অ্যাড নেটওয়ার্ক ইনিশিয়ালাইজেশন
     */
    fun init(context: Context, isVip: Boolean = false) {
        UnifiedAdManager.init(context, isVip = isVip)
    }

    /**
     * Start.io ইন্টারস্টিশিয়াল অ্যাড প্রি-লোডার
     */
    fun preloadInterstitial(context: Context) {
        UnifiedAdManager.preloadInterstitial(context)
    }

    /**
     * ইন্টারস্টিশিয়াল অ্যাড প্রদর্শন (Start.io On-Demand)
     */
    fun showInterstitial(
        context: Context,
        isVip: Boolean,
        onAdClosed: () -> Unit
    ) {
        UnifiedAdManager.showInterstitial(context, isVip, onAdClosed)
    }

    /**
     * Start.io রিওয়ার্ডেড ভিডিও প্রি-লোডার
     */
    fun preloadRewardedVideo(
        context: Context,
        onLoaded: (() -> Unit)? = null,
        onFailed: ((String) -> Unit)? = null
    ) {
        UnifiedAdManager.preloadRewardedVideo(context, onLoaded, onFailed)
    }

    fun preloadRewarded(context: Context) = preloadRewardedVideo(context)
    fun loadRewardedVideoAd(context: Context) = preloadRewardedVideo(context)

    /**
     * 🎁 সহজ রিওয়ার্ডেড অ্যাড কলিং (Start.io -> Adsterra Fallback)
     */
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

    /**
     * Adsterra পপ-আন্ডার প্রদর্শন
     */
    fun showPopunderIfEligible(context: Context, isVip: Boolean) {
        UnifiedAdManager.showPopunderIfEligible(context, isVip)
    }

    /**
     * Adsterra / স্মার্টলিংক ডিরেক্ট লিঙ্ক ওপেনার
     */
    fun openSmartlink(context: Context, isVip: Boolean): Boolean {
        return UnifiedAdManager.openSmartlink(context, isVip)
    }
}

/**
 * StartAppBanner backward-compatible wrapper
 * যেসকল স্ক্রিনে StartAppBanner কল করা আছে, সেখানে স্বয়ংক্রিয়ভাবে
 * Start.io ব্যানার বিজ্ঞাপন রেন্ডার হবে।
 */
@Composable
fun StartAppBanner(
    isVip: Boolean,
    modifier: Modifier = Modifier
) {
    UnifiedAdBanner(isVip = isVip, modifier = modifier)
}
