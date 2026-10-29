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
 * স্বয়ংক্রিয় ওয়াচ-টাইম (সেকেন্ড), সার্চ টার্ম, শর্টস সোয়াইপ ও ২৪-ঘণ্টা ভিউ ফিল্টারিং সহ
 */
object AppAnalyticsTracker {
    private const val TAG = "ANALYTICS_TRACKER"
    private const val PING_URL = "https://playdramaflix.com/api/v1/analytics/ping"
    private const val HEARTBEAT_INTERVAL_MS = 40_000L // ৪০ সেকেন্ড পর পর লাইভ হার্টবিট
    private const val MIN_SCREEN_INTERVAL_MS = 2_500L // পরপর দ্রুত স্ক্রিন চেঞ্জের স্প্যাম প্রটেকশন

    private const val PREFS_NAME = "drama_analytics_prefs"
    private const val KEY_FIRST_OPEN_TRACKED = "has_reported_first_install"
    private const val KEY_USER_AGE_GROUP = "saved_user_age_group"

    const val DEFAULT_AGE_GROUP = "18–24 years"

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
    private var currentAgeGroup: String? = null

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
     * 👥 ইউজারের বয়স গ্রুপ সেভ ও আপডেট করার মেথড
     */
    fun setAgeGroup(context: Context, ageGroup: String) {
        currentAgeGroup = ageGroup.trim()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USER_AGE_GROUP, currentAgeGroup)
            .apply()
    }

    /**
     * 👥 ইউজারের সংরক্ষিত বয়স গ্রুপ লোড করা (ডিফল্ট: 18–24 years)
     */
    fun getAgeGroup(context: Context): String {
        return currentAgeGroup ?: run {
            val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_USER_AGE_GROUP, DEFAULT_AGE_GROUP) ?: DEFAULT_AGE_GROUP
            currentAgeGroup = saved
            saved
        }
    }

    /**
     * 🎯 চেক করে যে এটি ইউজারের ফোনের প্রথম অ্যাপ ওপেন কি না (Install Tracker)
     */
    private fun checkIsFirstOpen(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val alreadyTracked = prefs.getBoolean(KEY_FIRST_OPEN_TRACKED, false)
        if (!alreadyTracked) {
            prefs.edit().putBoolean(KEY_FIRST_OPEN_TRACKED, true).apply()
            return true
        }
        return false
    }

    /**
     * 🚀 অ্যাপ চালু হওয়ার সাথে সাথে প্রাথমিক সেশন ও ইন্সটল সিগন্যাল রেজিস্টার করা
     */
    fun init(context: Context, userId: Int? = null) {
        currentUserId = userId
        val isFirstOpen = checkIsFirstOpen(context)
        val age = getAgeGroup(context)
        Log.d(TAG, "✓ Initializing AppAnalyticsTracker (First Open: $isFirstOpen, Age: $age) for device: ${getDeviceId(context)}")

        trackerScope.launch {
            sendPing(
                context = context.applicationContext,
                screen = "Home Screen",
                postTitle = "",
                postSlug = "",
                userId = currentUserId,
                isFirstOpen = isFirstOpen,
                ageGroup = age
            )
        }

        startHeartbeatLoop(context.applicationContext)
    }

    /**
     * 📱 সাধারণ স্ক্রিন পরিবর্তন ট্র্যাকিং
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
            return
        }

        currentScreenName = screenName
        currentPostTitle = postTitle
        currentPostSlug = postSlug

        if (userId != null && userId > 0) {
            currentUserId = userId
        }

        val age = getAgeGroup(context)

        trackerScope.launch {
            sendPing(
                context = context.applicationContext,
                screen = currentScreenName,
                postTitle = currentPostTitle,
                postSlug = currentPostSlug,
                userId = currentUserId,
                isFirstOpen = false,
                ageGroup = age
            )
        }

        startHeartbeatLoop(context.applicationContext)
    }

    /**
     * 🔍 ইউজার সার্চ করলে সার্চ কি-ওয়ার্ড ট্র্যাকিং
     */
    fun trackSearch(context: Context, query: String) {
        if (query.trim().length < 3) return
        val age = getAgeGroup(context)
        trackerScope.launch {
            sendPing(
                context = context.applicationContext,
                screen = "Search",
                postTitle = "",
                postSlug = "",
                userId = currentUserId,
                isFirstOpen = false,
                searchQuery = query.trim(),
                ageGroup = age
            )
        }
    }

    /**
     * 🎬 শর্টস সোয়াইপ বনাম সম্পূর্ণ দেখার ট্র্যাকিং (Stayed vs Swiped)
     */
    fun trackShortAction(context: Context, isStayed: Boolean) {
        val action = if (isStayed) "stayed" else "swiped"
        val age = getAgeGroup(context)
        trackerScope.launch {
            sendPing(
                context = context.applicationContext,
                screen = "Short TV",
                postTitle = currentPostTitle,
                postSlug = currentPostSlug,
                userId = currentUserId,
                isFirstOpen = false,
                shortAction = action,
                ageGroup = age
            )
        }
    }

    private fun startHeartbeatLoop(appContext: Context) {
        if (heartbeatJob?.isActive == true) return

        heartbeatJob = trackerScope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                val age = getAgeGroup(appContext)
                sendPing(
                    context = appContext,
                    screen = currentScreenName,
                    postTitle = currentPostTitle,
                    postSlug = currentPostSlug,
                    userId = currentUserId,
                    isFirstOpen = false,
                    ageGroup = age
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
        userId: Int?,
        isFirstOpen: Boolean,
        searchQuery: String = "",
        shortAction: String = "",
        ageGroup: String = DEFAULT_AGE_GROUP
    ) {
        var conn: HttpURLConnection? = null
        try {
            val now = System.currentTimeMillis()

            // ⏱️ ওয়াচ টাইম (সেকেন্ড) হিসাব
            val elapsedSec = if (lastPingTimestamp > 0L) {
                ((now - lastPingTimestamp) / 1000L).coerceIn(5L, 120L).toInt()
            } else {
                30
            }
            lastPingTimestamp = now

            val deviceId = getDeviceId(context)

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

            val brand = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
            val model = Build.MODEL
            val osVersion = "Android ${Build.VERSION.RELEASE}"

            val jsonPayload = JSONObject().apply {
                put("device_id", deviceId)
                put("screen_name", screen)
                put("post_title", resolvedPostTitle)
                put("post_slug", postSlug)
                put("user_id", userId ?: 0)
                put("is_first_open", isFirstOpen)
                put("device_brand", brand)
                put("device_model", model)
                put("os_version", osVersion)
                put("age_group", ageGroup) // 👈 বয়স গ্রুপ পাঠানো হচ্ছে

                // ⏱️ ওয়াচ-টাইম সেকেন্ড
                put("watch_seconds", elapsedSec)

                // 🔍 সার্চ টার্ম ও শর্টস সোয়াইপ
                if (searchQuery.isNotBlank()) put("search_query", searchQuery)
                if (shortAction.isNotBlank()) put("short_action", shortAction)
            }

            val url = URL(PING_URL)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Connection", "Keep-Alive")
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
                Log.d(TAG, "✓ Ping OK [$responseCode]: $screen | ${elapsedSec}s | Age: $ageGroup")
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
