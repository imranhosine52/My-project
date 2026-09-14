package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object AppAnalyticsTracker {
    private const val TAG = "ANALYTICS_PING"
    private const val PING_URL = "https://playdramaflix.com/api/v1/analytics/ping"
    private const val HEARTBEAT_INTERVAL_MS = 45_000L // ৪৫ সেকেন্ড পর পর

    private val trackerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var heartbeatJob: Job? = null

    @Volatile
    private var currentScreenName: String = "Home Screen"

    @Volatile
    private var currentUserId: Int? = null

    private var deviceIdCache: String? = null

    @SuppressLint("HardwareIds")
    fun getDeviceId(context: Context): String {
        return deviceIdCache ?: run {
            val id = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "android_${Build.BOARD}_${System.currentTimeMillis()}"
            deviceIdCache = id
            id
        }
    }

    /**
     * 🚀 অ্যাপ ওপেন হওয়ার সাথে সাথেই ইনস্ট্যান্ট পিং পাঠাবে (No delay)
     */
    fun init(context: Context, userId: Int? = null) {
        currentUserId = userId
        Log.d(TAG, "Initializing AppAnalyticsTracker for device: ${getDeviceId(context)}")
        
        // ১. অবিলম্বে প্রথম পিং সার্ভারে পাঠানো
        trackerScope.launch {
            sendPing(context.applicationContext, "App Launched", currentUserId)
        }

        // ২. ব্যাকগ্রাউন্ড লুপ চালু করা
        startHeartbeatLoop(context.applicationContext)
    }

    /**
     * স্ক্রিন পরিবর্তন বা ভিডিও প্লে হলে তাৎক্ষণিক পিং পাঠানো
     */
    fun trackScreen(context: Context, screenName: String, userId: Int? = null) {
        currentScreenName = screenName
        if (userId != null && userId > 0) {
            currentUserId = userId
        }

        trackerScope.launch {
            sendPing(context.applicationContext, currentScreenName, currentUserId)
        }

        startHeartbeatLoop(context.applicationContext)
    }

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
     * ক্লাউডফ্লেয়ার ও ফায়ারওয়াল সুরক্ষিত HTTP POST রিকোয়েস্ট
     */
    private fun sendPing(context: Context, screen: String, userId: Int?) {
        var conn: HttpURLConnection? = null
        try {
            val deviceId = getDeviceId(context)

            val jsonPayload = JSONObject().apply {
                put("device_id", deviceId)
                put("screen_name", screen)
                if (userId != null && userId > 0) {
                    put("user_id", userId)
                } else {
                    put("user_id", 0) // অধিকাংশ ব্যাকএন্ডে 0 বা null চায়
                }
            }

            val url = URL(PING_URL)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                // 🛡️ ক্লাউডফ্লেয়ার ব্লকিং এড়াতে স্ট্যান্ডার্ড মোবাইল হেডার
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; ${Build.MODEL}) PlayDramaFlixApp/1.0")
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                instanceFollowRedirects = true
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                writer.write(jsonPayload.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            val responseText = try {
                val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                BufferedReader(InputStreamReader(stream)).readText()
            } catch (_: Exception) { "" }

            Log.i(TAG, "🟢 Ping Status [$responseCode]: $screen | Server Response: $responseText")

        } catch (t: Throwable) {
            Log.e(TAG, "🔴 Ping failed: ${t.message}")
        } finally {
            conn?.disconnect()
        }
    }
}
