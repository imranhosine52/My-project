package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object AppAnalyticsTracker {
    private const val TAG = "AppAnalyticsTracker"
    private const val PING_URL = "https://playdramaflix.com/api/v1/analytics/ping"
    private const val HEARTBEAT_INTERVAL_MS = 45_000L // ৪৫ সেকেন্ড

    private val trackerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var heartbeatJob: Job? = null

    @Volatile
    private var currentScreenName: String = "App Launched"

    @Volatile
    private var currentUserId: Int? = null

    private var deviceIdCache: String? = null

    /**
     * ডিভাইসের ইউনিক অ্যান্ড্রয়েড আইডি সংগ্রহ করা
     */
    @SuppressLint("HardwareIds")
    private fun getDeviceId(context: Context): String {
        return deviceIdCache ?: run {
            val id = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "device_${System.currentTimeMillis()}"
            deviceIdCache = id
            id
        }
    }

    /**
     * অ্যাপ ওপেন হওয়ার পর ট্র্যাকার চালু করা
     */
    fun init(context: Context, userId: Int? = null) {
        currentUserId = userId
        startHeartbeatLoop(context.applicationContext)
    }

    /**
     * স্ক্রিন পরিবর্তন বা ভিডিও চালু হলে কল করার ফাংশন
     */
    fun trackScreen(context: Context, screenName: String, userId: Int? = null) {
        currentScreenName = screenName
        if (userId != null) {
            currentUserId = userId
        }

        // ইনস্ট্যান্ট পিং পাঠানো
        trackerScope.launch {
            sendPing(context.applicationContext, currentScreenName, currentUserId)
        }

        // নিশ্চিত করা যে ৪৫ সেকেন্ডের ব্যাকগ্রাউন্ড লুপ চালু আছে
        startHeartbeatLoop(context.applicationContext)
    }

    /**
     * প্রতি ৪৫ সেকেন্ডে সাইলেন্ট হার্টবিট পাঠানো
     */
    private fun startHeartbeatLoop(appContext: Context) {
        if (heartbeatJob?.isActive == true) return

        heartbeatJob = trackerScope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                sendPing(appContext, currentScreenName, currentUserId)
            }
        }
    }

    /**
     * মূল HTTP POST রিকোয়েস্ট (সম্পূর্ণ সাইলেন্ট ও ক্র্যাশ-প্রুফ)
     */
    private fun sendPing(context: Context, screen: String, userId: Int?) {
        try {
            val deviceId = getDeviceId(context)
            val jsonPayload = JSONObject().apply {
                put("device_id", deviceId)
                put("screen_name", screen)
                if (userId != null && userId > 0) {
                    put("user_id", userId)
                } else {
                    put("user_id", JSONObject.NULL)
                }
            }

            val url = URL(PING_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 5000
                readTimeout = 5000
                doOutput = true
                instanceFollowRedirects = true
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                writer.write(jsonPayload.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            conn.disconnect()
            Log.d(TAG, "✓ Ping Sent [$responseCode]: $screen (Device: $deviceId, User: $userId)")
        } catch (t: Throwable) {
            // নেটওয়ার্ক ফেইল বা সার্ভার ডাউন থাকলেও অ্যাপের কোনো সমস্যা হবে না
            Log.w(TAG, "Ping notice: ${t.message}")
        }
    }
}
