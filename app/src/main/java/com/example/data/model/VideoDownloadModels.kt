package com.example.data.model

import java.util.Locale

/**
 * 🌐 সাপোর্টেড প্ল্যাটফর্মসমূহ
 */
enum class DownloadPlatform(val label: String, val iconColorHex: Long) {
    YOUTUBE("YouTube", 0xFFFF0000),
    FACEBOOK("Facebook", 0xFF1877F2),
    INSTAGRAM("Instagram", 0xFFE1306C),
    TIKTOK("TikTok", 0xFF00F2FE),
    TWITTER("X (Twitter)", 0xFFFFFFFF),
    OTHER("Web Video", 0xFF00D166)
}

/**
 * 📦 প্রতিটি রেজোলিউশন / কোয়ালিটির তথ্য
 */
data class VideoFormatOption(
    val formatId: String,
    val qualityLabel: String,      // e.g. "1080p Full HD", "720p HD", "MP3 Audio"
    val resolutionText: String,    // e.g. "1920x1080"
    val extension: String = "mp4", // "mp4", "mp3", "m4a"
    val downloadUrl: String,
    val approximateSizeBytes: Long = 0L,
    val isAudioOnly: Boolean = false
) {
    val formattedSize: String
        get() {
            if (approximateSizeBytes <= 0) return "Fast Download"
            val mb = approximateSizeBytes / (1024.0 * 1024.0)
            val gb = mb / 1024.0
            return if (gb >= 1.0) {
                String.format(Locale.US, "%.2f GB", gb)
            } else {
                String.format(Locale.US, "%.1f MB", mb)
            }
        }
}

/**
 * 🎬 ভিডিওর সম্পূর্ণ মেটাডাটা ও কোয়ালিটি লিস্ট
 */
data class DownloadableVideoInfo(
    val sourceUrl: String,
    val title: String,
    val author: String? = null,
    val durationSeconds: Long = 0L,
    val thumbnailUrl: String? = null,
    val platform: DownloadPlatform = DownloadPlatform.OTHER,
    val availableFormats: List<VideoFormatOption> = emptyList()
) {
    val formattedDuration: String
        get() {
            if (durationSeconds <= 0) return ""
            val m = durationSeconds / 60
            val s = durationSeconds % 60
            return String.format(Locale.US, "%02d:%02d", m, s)
        }
}

/**
 * ⚡ ডাউনলোড টাস্কের লাইভ স্টেট
 */
data class ActiveDownloadTask(
    val downloadId: Long,
    val title: String,
    val platform: DownloadPlatform,
    val formatLabel: String,
    val progressPercent: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val status: DownloadStatus = DownloadStatus.DOWNLOADING,
    val localFilePath: String? = null
)

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    PAUSED
}
