package com.example.ads

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * StartIoAdManager (Delegates to UnifiedAdManager)
 * ব্যাকওয়ার্ড কম্প্যাটিবিলিটি ও সহজ মেথড কলিংয়ের জন্য র্যাপার অবজেক্ট।
 * এটি স্বয়ংক্রিয়ভাবে AdMob, Unity Ads, Start.io এবং Adsterra-এর সমন্বিত ইঞ্জিন পরিচালনা করে।
 */
object StartIoAdManager {
    const val PUBLISHER_ID = "113502454"
    const val STARTIO_APP_ID = "207238360"

    /**
     * সমস্ত অ্যাড নেটওয়ার্ক (AdMob, Unity, Start.io) ইনিশিয়ালাইজেশন
     */
    fun init(context: Context, isVip: Boolean = false) {
        UnifiedAdManager.init(context, isVip = isVip)
    }

    /**
     * ইন্টারস্টিশিয়াল অ্যাড প্রি-লোডার (AdMob ও Start.io ব্যাকগ্রাউন্ডে প্রি-লোড হবে)
     */
    fun preloadInterstitial(context: Context) {
        UnifiedAdManager.preloadInterstitial(context)
    }

    /**
     * ইন্টারস্টিশিয়াল অ্যাড প্রদর্শন (AdMob -> Unity -> Start.io ওয়াটারফল)
     */
    fun showInterstitial(
        context: Context,
        isVip: Boolean,
        onAdClosed: () -> Unit
    ) {
        UnifiedAdManager.showInterstitial(context, isVip, onAdClosed)
    }

    /**
     * রিওয়ার্ডেড ভিডিও প্রি-লোডার (AdMob, Unity ও Start.io প্রি-লোড হবে)
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
     * 🎁 সহজ রিওয়ার্ডেড অ্যাড কলিং (ভিডিও দেখে নাটক/এপিসোড আনলক)
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
 * পুরোনো যেসকল স্ক্রিনে StartAppBanner কল করা আছে, সেখানে স্বয়ংক্রিয়ভাবে
 * AdMob ব্যানার এবং না পেলে ফলব্যাক হিসেবে Start.io ব্যানার রেন্ডার হবে।
 */
@Composable
fun StartAppBanner(
    isVip: Boolean,
    modifier: Modifier = Modifier
) {
    UnifiedAdBanner(isVip = isVip, modifier = modifier)
}
