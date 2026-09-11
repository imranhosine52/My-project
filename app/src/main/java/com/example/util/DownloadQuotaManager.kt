package com.example.util

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 🎯 Download Quota & Bandwidth Manager
object DownloadQuotaManager {
    private const val PREF_NAME = "playdramaflix_download_quota_pref"
    private const val KEY_LAST_DATE = "quota_last_date"
    private const val KEY_USED_BYTES = "quota_used_bytes_today"

    // 🎯 Daily Free Limit: 2.0 GB in Bytes
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

    // 🎯 আন্তর্জাতিক স্ট্যান্ডার্ডে ডাউনলোড কোটা চেক
    fun checkCanDownload(
        context: Context,
        bytesToDownload: Long,
        isVip: Boolean
    ): QuotaCheckResult {
        if (isVip) {
            return QuotaCheckResult(
                canDownload = true,
                isVip = true,
                message = "VIP Unlimited Download"
            )
        }

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
                message = "⚠️ Daily 2.0 GB free download limit reached! Only $remainingFormatted remaining today. Upgrade to VIP for unlimited downloads.",
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
