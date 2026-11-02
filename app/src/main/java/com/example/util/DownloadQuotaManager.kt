package com.example.util

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 🎯 Download Quota & 100% VIP Exclusive Access Manager
object DownloadQuotaManager {
    private const val PREF_NAME = "playdramaflix_download_quota_pref"
    private const val KEY_LAST_DATE = "quota_last_date"
    private const val KEY_USED_BYTES = "quota_used_bytes_today"

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

    /**
     * 🎯 ডাউনলোড পারমিশন ভ্যালিডেশন ইঞ্জিন:
     * শর্ট এবং লং উভয় প্রকার ভিডিও ডাউনলোড এখন ১০০% VIP মেম্বারদের জন্য সংরক্ষিত।
     * ফ্রি ইউজারদের ডাউনলোড সরাসরি ব্লক করা হবে।
     */
    fun checkCanDownload(
        context: Context,
        bytesToDownload: Long,
        isVip: Boolean,
        isLongVideo: Boolean = false
    ): QuotaCheckResult {
        // 👑 ১. VIP ইউজারদের জন্য সমস্ত ডাউনলোড আনলিমিটেড
        if (isVip) {
            return QuotaCheckResult(
                canDownload = true,
                isVip = true,
                message = "VIP Unlimited Download"
            )
        }

        // 🚫 ২. ফ্রি ইউজারদের জন্য শর্ট ও লং উভয় ভিডিওর ডাউনলোড সম্পূর্ণ রেস্ট্রিক্টেড (ইংরেজিতে)
        return QuotaCheckResult(
            canDownload = false,
            isVip = false,
            message = "👑 Video downloading is exclusive to VIP members! Upgrade to VIP for unlimited high-speed downloads."
        )
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
