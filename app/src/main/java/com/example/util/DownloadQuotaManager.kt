package com.example.util

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 🎯 ফ্রি বনাম ভিআইপি ডাউনলোড কোটা ট্র্যাকার ইঞ্জিন
object DownloadQuotaManager {
    private const val PREF_NAME = "playdramaflix_download_quota_pref"
    private const val KEY_LAST_DATE = "quota_last_date"
    private const val KEY_USED_BYTES = "quota_used_bytes_today"

    // 🎯 প্রতিদিনের ফ্রি ডাউনলোড লিমিট: ঠিক ২ জিবি (Bytes)
    const val DAILY_FREE_LIMIT_BYTES: Long = 2L * 1024L * 1024L * 1024L // 2.0 GB

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    private fun getTodayDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    // আজকের দিনে ইতিমধ্যে কত বাইট ব্যবহার হয়েছে (রাত ১২টায় অটো রিসেট হয়)
    fun getTodayUsedBytes(context: Context): Long {
        val prefs = getPrefs(context)
        val savedDate = prefs.getString(KEY_LAST_DATE, "")
        val today = getTodayDate()

        return if (savedDate == today) {
            prefs.getLong(KEY_USED_BYTES, 0L)
        } else {
            // নতুন দিন শুরু হয়েছে, কোটা ০ এ রিসেট
            prefs.edit()
                .putString(KEY_LAST_DATE, today)
                .putLong(KEY_USED_BYTES, 0L)
                .apply()
            0L
        }
    }

    // ফ্রি কতটুকু বাকি আছে
    fun getRemainingFreeBytes(context: Context): Long {
        val used = getTodayUsedBytes(context)
        return (DAILY_FREE_LIMIT_BYTES - used).coerceAtLeast(0L)
    }

    // 🎯 ডাউনলোড করার আগে পারমিশন ও কোটা চেক
    fun checkCanDownload(
        context: Context,
        bytesToDownload: Long,
        isVip: Boolean
    ): QuotaCheckResult {
        // ১. VIP ইউজার হলে সরাসরি আনলিমিটেড এক্সেস
        if (isVip) {
            return QuotaCheckResult(
                canDownload = true,
                isVip = true,
                message = "VIP Unlimited Download"
            )
        }

        val used = getTodayUsedBytes(context)
        val remaining = (DAILY_FREE_LIMIT_BYTES - used).coerceAtLeast(0L)

        // ২. ফ্রি লিমিট ২ জিবি অতিক্রম করছে কিনা যাচাই
        return if (used + bytesToDownload <= DAILY_FREE_LIMIT_BYTES) {
            QuotaCheckResult(
                canDownload = true,
                isVip = false,
                message = "OK",
                remainingBytes = remaining - bytesToDownload
            )
        } else {
            val remainingFormatted = formatBytes(remaining)
            QuotaCheckResult(
                canDownload = false,
                isVip = false,
                message = "⚠️ আজকের ফ্রি ২ জিবি লিমিট শেষ! আর মাত্র $remainingFormatted বাকি আছে। আনলিমিটেড ডাউনলোড করতে VIP প্ল্যানে আপগ্রেড করুন।",
                remainingBytes = remaining
            )
        }
    }

    // ডাউনলোড শুরু হওয়া মাত্রই খরচ হওয়া কোটা সংরক্ষণ
    fun recordDownloadUsage(context: Context, bytes: Long) {
        if (bytes <= 0) return
        val prefs = getPrefs(context)
        val currentUsed = getTodayUsedBytes(context)
        val today = getTodayDate()

        prefs.edit()
            .putString(KEY_LAST_DATE, today)
            .putLong(KEY_USED_BYTES, currentUsed + bytes)
            .apply()
    }

    // সুন্দর MB / GB ফরম্যাটার
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            String.format(Locale.US, "%.2f GB", mb / 1024.0)
        } else {
            String.format(Locale.US, "%.1f MB", mb)
        }
    }
}

data class QuotaCheckResult(
    val canDownload: Boolean,
    val isVip: Boolean,
    val message: String,
    val remainingBytes: Long = 0L
)
