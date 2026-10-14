package com.example.util

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 🎯 Download Quota & Long Video VIP Access Manager
object DownloadQuotaManager {
    private const val PREF_NAME = "playdramaflix_download_quota_pref"
    private const val KEY_LAST_DATE = "quota_last_date"
    private const val KEY_USED_BYTES = "quota_used_bytes_today"

    // 🎯 শর্ট ভিডিওর জন্য দৈনিক ফ্রি ডাউনলোড লিমিট: ২.০ জিবি (Bytes)
    const val DAILY_FREE_LIMIT_BYTES: Long = 2L * 1024L * 1024L * 1024L // 2.0 GB

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    private fun getTodayDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    fun getTodayUsedBytes(context: Context): Long {
        val prefs = getPrefs(context)
        val savedDate = prefs.getString(KEY_LAST_DATE, "")
        val today = getTodayDate()

        return if (savedDate == today) {
            prefs.getLong(KEY_USED_BYTES, 0L)
        } else {
            prefs.edit()
                .putString(KEY_LAST_DATE, today)
                .putLong(KEY_USED_BYTES, 0L)
                .apply()
            0L
        }
    }

    fun getRemainingFreeBytes(context: Context): Long {
        val used = getTodayUsedBytes(context)
        return (DAILY_FREE_LIMIT_BYTES - used).coerceAtLeast(0L)
    }

    /**
     * 🎯 ডাউনলোড পারমিশন ভ্যালিডেশন ইঞ্জিন:
     * ১. VIP ইউজাররা লং ও শর্ট সব ধরনের ভিডিও আনলিমিটেড ডাউনলোড করতে পারবে।
     * ২. লং ভিডিও হলে ফ্রি ইউজারদের জন্য ডাউনলোড সম্পূর্ণ বন্ধ থাকবে (VIP বাধ্যতামূলক)।
     * ৩. শর্ট ভিডিও হলে ফ্রি ইউজারদের জন্য দৈনিক ২.০ জিবি লিমিট কার্যকর থাকবে।
     */
    fun checkCanDownload(
        context: Context,
        bytesToDownload: Long,
        isVip: Boolean,
        isLongVideo: Boolean = false // 👈 লং ভিডিও ফ্ল্যাগ
    ): QuotaCheckResult {
        // 👑 ১. ভিআইপি ইউজারদের জন্য সবকিছু আনলিমিটেড
        if (isVip) {
            return QuotaCheckResult(
                canDownload = true,
                isVip = true,
                message = "VIP Unlimited Download"
            )
        }

        // 🚫 ২. লং ভিডিও হলে ফ্রি ইউজারদের জন্য ডাউনলোড সম্পূর্ণ রেস্ট্রিক্টেড
        if (isLongVideo) {
            return QuotaCheckResult(
                canDownload = false,
                isVip = false,
                message = "👑 লং ভিডিও ডাউনলোড শুধুমাত্র VIP মেম্বারদের জন্য প্রযোজ্য! ফুল ড্রামা ও হাই-স্পিড ডাউনলোডের জন্য VIP পাস নিন।"
            )
        }

        // 📱 ৩. শর্ট ভিডিওর ক্ষেত্রে দৈনিক ২ জিবি ফ্রি কোটা চেক
        val used = getTodayUsedBytes(context)
        val remaining = (DAILY_FREE_LIMIT_BYTES - used).coerceAtLeast(0L)

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
                message = "⚠️ দৈনিক ২.০ জিবি ফ্রি শর্ট ভিডিও ডাউনলোড সীমা শেষ! আজকে আর মাত্র $remainingFormatted বাকি আছে। আনলিমিটেড ডাউনলোডের জন্য VIP পাস নিন।",
                remainingBytes = remaining
            )
        }
    }

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
