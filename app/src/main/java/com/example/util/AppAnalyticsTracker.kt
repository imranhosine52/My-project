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

/**
 * ⚡ হাই-পারফরম্যান্স রিয়েল-টাইম অ্যানালিটিক্স ও হার্টবিট ট্র্যাকার
 * ক্লাউডফ্লেয়ার ও Redis এর সাথে সমন্বিত হয়ে কোটি ভিজিটর সাপোর্ট করে।
 */
object AppAnalyticsTracker {
    private const val TAG = "ANALYTICS_TRACKER"
    private const val PING_URL = "https://playdramaflix.com/api/v1/analytics/ping"
    private const val HEARTBEAT_INTERVAL_MS = 40_000L // ৪০ সেকেন্ড পর পর লাইভ হার্টবিট
    private const val MIN_SCREEN_INTERVAL_MS = 2_500L // পরপর দ্রুত স্ক্রিন চেঞ্জের স্প্যাম প্রটেকশন

    private val trackerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var heartbeatJob: Job? = null

    @Volatile
    private var currentScreenName: String = "Home Screen"

    @Volatile
    private var currentPostTitle: String = ""

    @Volatile
    private var currentPostSlug: String = ""

    @Volatile
    private var currentUserId: Int? = null

    @Volatile
    private var lastPingTimestamp: Long = 0L

    private var deviceIdCache: String? = null

    @SuppressLint("HardwareIds")
    fun getDeviceId(context: Context): String {
        return deviceIdCache ?: run {
            val id = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )?.takeIf { it.isNotBlank() && it != "9774d56d682e549c" }
                ?: "dev_${Build.MANUFACTURER}_${Build.MODEL}_${System.currentTimeMillis() % 1000000}"
            deviceIdCache = id
            id
        }
    }

    /**
     * 🚀 অ্যাপ চালু হওয়ার সাথে সাথে প্রাথমিক লাইভ সেশন রেজিস্টার করা
     */
    fun init(context: Context, userId: Int? = null) {
        currentUserId = userId
        Log.d(TAG, "✓ Initializing AppAnalyticsTracker for device: ${getDeviceId(context)}")

        trackerScope.launch {
            sendPing(
                context = context.applicationContext,
                screen = "Home Screen",
                postTitle = "",
                postSlug = "",
                userId = currentUserId
            )
        }

        startHeartbeatLoop(context.applicationContext)
    }

    /**
     * 📱 সাধারণ স্ক্রিন পরিবর্তন ট্র্যাকিং (ব্যাকওয়ার্ড কম্প্যাটিবল)
     */
    fun trackScreen(context: Context, screenName: String, userId: Int? = null) {
        trackScreen(
            context = context,
            screenName = screenName,
            userId = userId,
            postTitle = "",
            postSlug = ""
        )
    }

    /**
     * 🎬 ড্রামা / শর্টস ও নির্দিষ্ট পোস্ট দেখার সময় পূর্ণাঙ্গ লাইভ ট্র্যাকিং
     */
    fun trackScreen(
        context: Context,
        screenName: String,
        userId: Int? = null,
        postTitle: String = "",
        postSlug: String = ""
    ) {
        val now = System.currentTimeMillis()
        if (screenName == currentScreenName && postSlug == currentPostSlug && (now - lastPingTimestamp) < MIN_SCREEN_INTERVAL_MS) {
            return // অপ্রয়োজনীয় ডুপ্লিকেট পিং ফিল্টার
        }

        currentScreenName = screenName
        currentPostTitle = postTitle
        currentPostSlug = postSlug

        if (userId != null && userId > 0) {
            currentUserId = userId
        }

        trackerScope.launch {
            sendPing(
                context = context.applicationContext,
                screen = currentScreenName,
                postTitle = currentPostTitle,
                postSlug = currentPostSlug,
                userId = currentUserId
            )
        }

        startHeartbeatLoop(context.applicationContext)
    }

    private fun startHeartbeatLoop(appContext: Context) {
        if (heartbeatJob?.isActive == true) return

        heartbeatJob = trackerScope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                sendPing(
                    context = appContext,
                    screen = currentScreenName,
                    postTitle = currentPostTitle,
                    postSlug = currentPostSlug,
                    userId = currentUserId
                )
            }
        }
    }

    /**
     * 🌐 ক্লাউডফ্লেয়ার ও Redis এর সাথে আল্ট্রা-লাইটওয়েট পিং মেথড
     */
    private fun sendPing(
        context: Context,
        screen: String,
        postTitle: String,
        postSlug: String,
        userId: Int?
    ) {
        var conn: HttpURLConnection? = null
        try {
            lastPingTimestamp = System.currentTimeMillis()
            val deviceId = getDeviceId(context)

            // টাইটেল থেকে ড্রামার নাম এক্সট্র্যাক্ট করা (যদি আলাদাভাবে পাঠানো না হয়ে থাকে)
            val resolvedPostTitle = if (postTitle.isNotBlank()) {
                postTitle
            } else if (screen.startsWith("Watching:") || screen.startsWith("Watching Short:")) {
                screen.replace("Watching Short:", "")
                    .replace("Watching:", "")
                    .split(" - Ep")[0]
                    .trim()
            } else {
                ""
            }

            val jsonPayload = JSONObject().apply {
                put("device_id", deviceId)
                put("screen_name", screen)
                put("post_title", resolvedPostTitle)
                put("post_slug", postSlug)
                put("user_id", userId ?: 0)
            }

            val url = URL(PING_URL)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Connection", "Keep-Alive")
                // ক্লাউডফ্লেয়ার ফায়ারওয়াল বাইপাসিং হেডার
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; ${Build.MODEL}) PlayDramaFlixApp/1.0"
                )
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
            if (responseCode in 200..299) {
                Log.d(TAG, "✓ Ping OK [$responseCode]: $screen | $resolvedPostTitle")
            } else {
                val stream = conn.errorStream ?: conn.inputStream
                val errText = BufferedReader(InputStreamReader(stream)).use { it.readText() }
                Log.w(TAG, "Ping notice [$responseCode]: $errText")
            }

        } catch (t: Throwable) {
            Log.w(TAG, "Ping failed (silent): ${t.message}")
        } finally {
            conn?.disconnect()
        }
    }
}
