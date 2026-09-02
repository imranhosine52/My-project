package com.example.util

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * 📊 Firebase Analytics Event Logger
 */
object AnalyticsHelper {
    private var firebaseAnalytics: FirebaseAnalytics? = null

    fun init(context: Context) {
        if (firebaseAnalytics == null) {
            firebaseAnalytics = FirebaseAnalytics.getInstance(context.applicationContext)
        }
    }

    // যেকোনো কাস্টম ইভেন্ট লগ করা
    fun logEvent(eventName: String, params: Bundle? = null) {
        firebaseAnalytics?.logEvent(eventName, params)
    }

    // 🎬 ইউজার কোনো ড্রামা ওপেন করলে ট্র্যাক করা
    fun logDramaView(slug: String, title: String) {
        val bundle = Bundle().apply {
            putString("drama_slug", slug)
            putString("drama_title", title)
        }
        logEvent("drama_watch", bundle)
    }

    // 👑 ইউজার ভিআইপি প্ল্যান সাবমিট করলে ট্র্যাক করা
    fun logVipPurchase(planName: String, price: Double) {
        val bundle = Bundle().apply {
            putString("plan_name", planName)
            putDouble("amount", price)
            putString("currency", "BDT")
        }
        logEvent("vip_purchase_submit", bundle)
    }

    // 🌐 স্ক্রিন ভিউ ট্র্যাক করা
    fun logScreenView(screenName: String) {
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
            putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenName)
        }
        logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
    }
}
