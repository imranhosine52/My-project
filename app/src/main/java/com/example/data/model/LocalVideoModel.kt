package com.example.data.model

import android.net.Uri
import java.util.Locale

/**
 * 🎬 Local Video Item Model
 * ফোনের স্টোরেজে থাকা প্রতিটি একক ভিডিওর তথ্য ধারণ করে।
 */
data class LocalVideoItem(
    val id: Long,
    val title: String,
    val displayName: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val path: String,
    val contentUriString: String,
    val folderName: String,
    val bucketId: String,
    val dateAdded: Long,
    val mimeType: String? = "video/*",
    val resolution: String? = null
) {
    val contentUri: Uri
        get() = Uri.parse(contentUriString)

    // ⏱️ ভিডিওর মোট সময় সুন্দর ফরম্যাটে রূপান্তর (e.g. 05:24 অথবা 01:20:45)
    val formattedDuration: String
        get() {
            if (durationMs <= 0) return "00:00"
            val totalSeconds = durationMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60

            return if (hours > 0) {
                String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.US, "%02d:%02d", minutes, seconds)
            }
        }

    // 📦 ভিডিওর সাইজ ফরম্যাট (e.g. 45.2 MB অথবা 1.4 GB)
    val formattedSize: String
        get() {
            if (sizeBytes <= 0) return "0 B"
            val kb = sizeBytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0

            return when {
                gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
                mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
                kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
                else -> "$sizeBytes B"
            }
        }

    // 🏷️ রেজোলিউশন ট্যাগ (HD, Full HD, 4K ইত্যাদি)
    val qualityTag: String
        get() {
            val res = resolution?.lowercase() ?: ""
            return when {
                res.contains("3840") || res.contains("2160") || res.contains("4k") -> "4K UHD"
                res.contains("1920") || res.contains("1080") -> "1080p FHD"
                res.contains("1280") || res.contains("720") -> "720p HD"
                else -> "HD"
            }
        }
}

/**
 * 📁 Local Video Folder Model
 * ভিডিওগুলো যে যে ফোল্ডারে আছে (যেমন: Camera, WhatsApp, Download) সেগুলোর গ্রুপ তথ্য।
 */
data class LocalVideoFolder(
    val bucketId: String,
    val folderName: String,
    val folderPath: String,
    val videoCount: Int,
    val totalSizeBytes: Long,
    val thumbnailUriString: String?
) {
    val formattedTotalSize: String
        get() {
            val mb = totalSizeBytes / (1024.0 * 1024.0)
            val gb = mb / 1024.0
            return if (gb >= 1.0) {
                String.format(Locale.US, "%.2f GB", gb)
            } else {
                String.format(Locale.US, "%.1f MB", mb)
            }
        }
}
