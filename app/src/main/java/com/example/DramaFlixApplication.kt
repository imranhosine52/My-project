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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DramaFlixApplication : Application() {

    val repository: PlayDramaFlixRepository by lazy {
        PlayDramaFlixRepository(this)
    }

    override fun onCreate() {
        super.onCreate()

        // ১. মাল্টি-প্রসেস ক্র্যাশ প্রতিরোধে সেফ ওয়েবভিউ
        initSafeWebView()

        // ২. ফায়ারবেজ অ্যাপ ইনিশিয়ালাইজেশন
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
                Log.d("DramaFlixApp", "✓ FirebaseApp initialized.")
            }
        } catch (e: Exception) {
            Log.w("DramaFlixApp", "Firebase init notice: ${e.message}")
        }

        // ৩. ফায়ারবেজ অ্যানালিটিক্স
        try {
            AnalyticsHelper.init(this)
        } catch (e: Exception) {
            Log.w("DramaFlixApp", "Analytics notice: ${e.message}")
        }

        // ৪. নোটিফিকেশন চ্যানেল তৈরি
        createNotificationChannel()

        // ৫. 🔔 FCM টোকেন সংগ্রহ ও সার্ভারে রেজিস্টার করা + টপিক সাবস্ক্রিপশন
        setupFirebaseMessaging()

        // ৬. অ্যাড মিডিয়েশন আর্কিটেকচার ইনিশিয়ালাইজেশন
        try {
            val isVip = repository.isUserVip()
            val initialConfig = repository.getCachedAdsConfig()
            UnifiedAdManager.init(this, initialConfig = initialConfig, isVip = isVip)
        } catch (e: Exception) {
            Log.w("DramaFlixApp", "Ad Mediation init notice: ${e.message}")
        }
    }

    private fun setupFirebaseMessaging() {
        try {
            // অ্যাডমিন প্যানেলের কমন টপিকগুলোতে সাবস্ক্রাইব করা
            val topics = listOf("all_users", "all", "general", "dramaflix", "new_posts")
            for (topic in topics) {
                FirebaseMessaging.getInstance().subscribeToTopic(topic)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d("DramaFlixApp", "✓ Subscribed to topic: $topic")
                        }
                    }
            }

            // 🎯 সরাসরি ডিভাইস টোকেন নিয়ে সার্ভারে পাঠানো
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful && !task.result.isNullOrBlank()) {
                    val token = task.result
                    Log.d("DramaFlixApp", "✓ FCM Device Token: $token")
                    CoroutineScope(Dispatchers.IO).launch {
                        repository.registerDevice(token)
                    }
                } else {
                    Log.w("DramaFlixApp", "FCM token retrieval failed: ${task.exception?.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("DramaFlixApp", "FCM setup error: ${e.message}")
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
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
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
        } catch (_: Throwable) {}
    }
}
