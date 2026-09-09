package com.example.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.service.VideoDownloadService
import java.net.URLDecoder

object R2DownloadManager {

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun showToast(context: Context, message: String) {
        mainHandler.post {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 🎯 যেকোনো URL থেকে সরাসরি আসল Cloudflare R2 MP4 লিংক তৈরি
     */
    fun resolveDirectMp4Url(rawUrl: String): String {
        var url = rawUrl.trim()
        if (url.isBlank()) return ""

        try {
            url = URLDecoder.decode(url, "UTF-8")
        } catch (_: Exception) {}

        if (url.contains("master.m3u8")) {
            url = url.replace("master.m3u8", "download.mp4")
        } else if (url.endsWith(".m3u8") || url.contains(".m3u8")) {
            url = url.substringBeforeLast("/") + "/download.mp4"
        }
        return url
    }

    /**
     * ⚡ ১-ক্লিকে নিশ্চিত ডাউনলোড ইঞ্জিন
     * (অ্যাপ থেকে বের হয়ে গেলেও ব্যাকগ্রাউন্ডে ডাউনলোড চালু থাকবে এবং MovieBox স্টাইল নোটিফিকেশন দেখাবে)
     */
    fun startDownload(
        context: Context,
        downloadUrl: String,
        title: String,
        episodeNumber: Int = 1,
        isMovie: Boolean = false,
        posterUrl: String? = null,
        onDownloadStarted: (downloadId: Long) -> Unit = {}
    ) {
        val cleanUrl = resolveDirectMp4Url(downloadUrl)

        if (cleanUrl.isBlank() || (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://"))) {
            showToast(context, "Direct download link not available")
            return
        }

        // 🌐 Byse এম্বেড লিংক হলে ইন-অ্যাপ ব্রাউজারে রিডাইরেক্ট (কোনো ক্র্যাশ বা এরর ছাড়া)
        if (cleanUrl.contains("/e/") || cleanUrl.contains("/embed") || cleanUrl.contains("byse")) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                showToast(context, "Opening player page for download...")
            } catch (_: Exception) {
                showToast(context, "Cannot open download link")
            }
            return
        }

        // 🔔 Android 13+ নোটিফিকেশন পারমিশন রিমাইন্ডার
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                showToast(context, "⚠️ Please allow notifications to see download progress")
            }
        }

        try {
            val downloadId = System.currentTimeMillis() % 100000
            onDownloadStarted(downloadId)

            // 🚀 ব্যাকগ্রাউন্ড ফোরগ্রাউন্ড সার্ভিস চালু (অ্যাপ সম্পূর্ণ বন্ধ করলেও ডাউনলোড চলবে)
            val serviceIntent = Intent(context, VideoDownloadService::class.java).apply {
                putExtra(VideoDownloadService.EXTRA_URL, cleanUrl)
                putExtra(VideoDownloadService.EXTRA_TITLE, title)
                putExtra(VideoDownloadService.EXTRA_EPISODE, episodeNumber)
                putExtra(VideoDownloadService.EXTRA_IS_MOVIE, isMovie)
                putExtra(VideoDownloadService.EXTRA_POSTER_URL, posterUrl)
            }

            ContextCompat.startForegroundService(context, serviceIntent)
            showToast(context, "📥 Download started! Check notification panel.")

        } catch (e: Exception) {
            // ডিভাইস রেস্ট্রিকশন থাকলে ব্রাউজার ফলব্যাক
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                showToast(context, "Starting download via browser...")
            } catch (_: Exception) {
                showToast(context, "Download error: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }
}
