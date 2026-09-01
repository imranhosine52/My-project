package com.example.data.model

import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 🎬 LocalMediaItem Model
 * ভিডিও, অডিও, ইমেজ, ডকুমেন্ট ও APK ফাইলের তথ্য মডেল।
 */
data class LocalVideoItem(
    val id: Long,
    val title: String,
    val displayName: String,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val path: String,
    val contentUriString: String,
    val folderName: String = "Internal",
    val bucketId: String = "0",
    val dateAdded: Long = 0L,
    val mimeType: String? = "*/*",
    val resolution: String? = null,
    val isStarred: Boolean = false,
    val isSafe: Boolean = false,
    val isTrashed: Boolean = false
) {
    val contentUri: Uri
        get() = Uri.parse(contentUriString)

    val formattedDuration: String
        get() {
            if (durationMs <= 0) return ""
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

    val formattedSize: String
        get() {
            if (sizeBytes <= 0) return "0 B"
            val kb = sizeBytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0

            return when {
                gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
                mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
                kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
                else -> "$sizeBytes B"
            }
        }

    val formattedDate: String
        get() {
            if (dateAdded <= 0) return "Recent"
            return try {
                val sdf = SimpleDateFormat("MMM d", Locale.US)
                sdf.format(Date(dateAdded * 1000L))
            } catch (_: Exception) {
                "Recent"
            }
        }
}

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
            return if (gb >= 1.0) String.format(Locale.US, "%.1f GB", gb) else String.format(Locale.US, "%.1f MB", mb)
        }
}

data class StorageCategorySummary(
    val videosSizeText: String = "0 B",
    val imagesSizeText: String = "0 B",
    val audioSizeText: String = "0 B",
    val documentsSizeText: String = "0 B",
    val downloadsSizeText: String = "0 B",
    val appsSizeText: String = "0 B"
)
