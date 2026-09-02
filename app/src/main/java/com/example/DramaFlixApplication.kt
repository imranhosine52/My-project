package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import android.webkit.WebView
import com.example.ads.UnifiedAdManager
import com.example.data.repository.PlayDramaFlixRepository
import com.example.util.AnalyticsHelper
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging

class DramaFlixApplication : Application() {

    val repository: PlayDramaFlixRepository by lazy {
        PlayDramaFlixRepository(this)
    }

    override fun onCreate() {
        super.onCreate()

        // ১. মাল্টি-প্রসেস ক্র্যাশ প্রতিরোধে সেফ ওয়েবভিউ কনফিগারেশন
        initSafeWebView()

        // ২. ফায়ারবেজ অ্যাপ ইনিশিয়ালাইজেশন
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
                Log.d("DramaFlixApp", "FirebaseApp initialized successfully.")
            }
        } catch (e: Exception) {
            Log.w("DramaFlixApp", "Firebase initialization skipped or failed: ${e.message}")
        }

        // ৩. 📊 ফায়ারবেজ অ্যানালিটিক্স ইনিশিয়ালাইজেশন (লাইভ ইউজার ও ইভেন্ট ট্র্যাকিং)
        try {
            AnalyticsHelper.init(this)
            Log.d("DramaFlixApp", "Firebase Analytics initialized successfully.")
        } catch (e: Exception) {
            Log.w("DramaFlixApp", "Firebase Analytics init notice: ${e.message}")
        }

        // ৪. 🔔 সবার ফোনে এক ক্লিকে নোটিফিকেশন পাঠানোর জন্য 'all_users' টপিকে সাবস্ক্রাইব করা
        try {
            FirebaseMessaging.getInstance().subscribeToTopic("all_users")
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d("DramaFlixApp", "Subscribed to FCM topic: all_users")
                    } else {
                        Log.w("DramaFlixApp", "FCM topic subscription failed: ${task.exception?.message}")
                    }
                }
        } catch (e: Exception) {
            Log.w("DramaFlixApp", "FCM setup notice: ${e.message}")
        }

        // ৫. 📢 অ্যান্ড্রয়েড ৮.০+ এর জন্য নোটিফিকেশন চ্যানেল তৈরি
        createNotificationChannel()

        // ৬. 🎯 ইউনিফাইড অ্যাড মিডিয়েশন আর্কিটেকচার ইনিশিয়ালাইজেশন
        try {
            val isVip = repository.isUserVip()
            val initialConfig = repository.getCachedAdsConfig()
            UnifiedAdManager.init(this, initialConfig = initialConfig, isVip = isVip)
        } catch (e: Exception) {
            Log.w("DramaFlixApp", "Unified Ad Mediation initialization failed: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "high_importance_channel"
            val channelName = "New Post & App Alerts"
            val channelDescription = "Notifications for newly added drama series, movies, and app updates."
            val importance = NotificationManager.IMPORTANCE_HIGH

            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = channelDescription
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun initSafeWebView() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val processName = Application.getProcessName()
                if (packageName != processName) {
                    WebView.setDataDirectorySuffix(processName)
                }
            }
        } catch (e: Throwable) {
            Log.w("DramaFlixApp", "WebView setup notice: ${e.message}")
        }
    }
}
