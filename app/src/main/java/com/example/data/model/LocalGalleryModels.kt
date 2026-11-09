package com.example.data.model

import android.net.Uri
import java.util.Locale

/**
 * 🎬 Gallery Primary Sections
 * বাম পাশে Folders (ডিফল্ট) এবং ডান পাশে Videos, Music, Images, Files
 */
enum class GallerySection(val displayName: String, val id: String) {
    FOLDERS(displayName = "Folders", id = "folders"),
    VIDEOS(displayName = "Videos", id = "videos"),
    MUSIC(displayName = "Music", id = "music"),
    IMAGES(displayName = "Images", id = "images"),
    FILES(displayName = "Files", id = "files")
}

/**
 * 📁 মিডিয়া ক্যাটাগরি ফিল্টার (Folders ভিউ চলাকালীন কোন টাইপের ফোল্ডার দেখানো হবে)
 */
enum class GalleryCategoryType(val label: String, val id: String) {
    VIDEOS("Videos", "videos"),
    MUSIC("Music", "music"),
    IMAGES("Images", "images"),
    FILES("Files", "files")
}

/**
 * 🔀 Video Sorting Options
 */
enum class VideoSortField(val label: String) {
    DATE_ADDED("Date Added"),
    TITLE("Name (A-Z)"),
    SIZE("File Size"),
    DURATION("Duration")
}

enum class SortDirection {
    ASCENDING,
    DESCENDING
}

data class VideoSortOrder(
    val field: VideoSortField = VideoSortField.DATE_ADDED,
    val direction: SortDirection = SortDirection.DESCENDING
) {
    private fun resolveDisplayLabel(): String = when (field) {
        VideoSortField.DATE_ADDED -> if (direction == SortDirection.DESCENDING) "Newest First" else "Oldest First"
        VideoSortField.TITLE -> if (direction == SortDirection.ASCENDING) "Name (A to Z)" else "Name (Z to A)"
        VideoSortField.SIZE -> if (direction == SortDirection.DESCENDING) "Largest First" else "Smallest First"
        VideoSortField.DURATION -> if (direction == SortDirection.DESCENDING) "Longest First" else "Shortest First"
    }

    val displayLabel: String
        get() = resolveDisplayLabel()
}

/**
 * 🔍 Video Filter Options
 */
enum class VideoResolutionFilter(val label: String) {
    ALL("All Resolutions"),
    UHD_4K("4K Ultra HD"),
    FHD_1080P("1080p Full HD"),
    HD_720P("720p HD"),
    SD("Standard Definition")
}

enum class VideoDurationFilter(val label: String) {
    ALL("Any Duration"),
    SHORT_UNDER_5M("< 5 Minutes"),
    MEDIUM_5_TO_20M("5 – 20 Minutes"),
    LONG_OVER_20M("> 20 Minutes")
}

data class GalleryFilterConfig(
    val resolutionFilter: VideoResolutionFilter = VideoResolutionFilter.ALL,
    val durationFilter: VideoDurationFilter = VideoDurationFilter.ALL,
    val selectedFolderBucketId: String? = null
) {
    val isFilterActive: Boolean
        get() = resolutionFilter != VideoResolutionFilter.ALL ||
                durationFilter != VideoDurationFilter.ALL ||
                selectedFolderBucketId != null
}

/**
 * 🗑️ Dual Deletion Options
 */
enum class DeleteOption {
    MOVE_TO_RECYCLE_BIN,
    PERMANENTLY_DELETE
}

/**
 * 📊 Real-time Live Deletion Progress State
 */
data class DeleteProgressState(
    val isRunning: Boolean = false,
    val operationType: DeleteOption = DeleteOption.MOVE_TO_RECYCLE_BIN,
    val totalFiles: Int = 0,
    val successfullyDeletedCount: Int = 0,
    val remainingFilesCount: Int = 0,
    val failedCount: Int = 0,
    val currentProcessingFileName: String = "",
    val progressPercent: Int = 0,
    val isFinished: Boolean = false,
    val isCancelled: Boolean = false,
    val failedFilesList: List<String> = emptyList(),
    val statusMessage: String = ""
) {
    val progressFraction: Float
        get() = if (totalFiles > 0) (successfullyDeletedCount.toFloat() / totalFiles.toFloat()).coerceIn(0f, 1f) else 0f
}

/**
 * 📋 Deletion Operation Final Summary
 */
data class DeleteSummaryReport(
    val operationType: DeleteOption,
    val totalFilesProcessed: Int,
    val successCount: Int,
    val failedCount: Int,
    val failedFileNames: List<String> = emptyList(),
    val totalFreedBytes: Long = 0L
) {
    val formattedFreedSize: String
        get() {
            if (totalFreedBytes <= 0) return "0 MB"
            val mb = totalFreedBytes / (1024.0 * 1024.0)
            val gb = mb / 1024.0
            return if (gb >= 1.0) String.format(Locale.US, "%.2f GB", gb) else String.format(Locale.US, "%.1f MB", mb)
        }
}

/**
 * ♻️ Recycle Bin Trashed Media Metadata
 */
data class TrashedVideoMetadata(
    val id: Long,
    val originalPath: String,
    val trashLocationPath: String,
    val originalFileName: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val trashedTimestamp: Long = System.currentTimeMillis(),
    val mimeType: String? = "video/mp4",
    val contentUriString: String? = null
) {
    val formattedSize: String
        get() {
            val mb = sizeBytes / (1024.0 * 1024.0)
            val gb = mb / 1024.0
            return if (gb >= 1.0) String.format(Locale.US, "%.2f GB", gb) else String.format(Locale.US, "%.1f MB", mb)
        }

    val daysRemainingBeforePurge: Int
        get() {
            val elapsedDays = ((System.currentTimeMillis() - trashedTimestamp) / (1000 * 60 * 60 * 24)).toInt()
            return (30 - elapsedDays).coerceAtLeast(0)
        }
}

/**
 * 📺 Player Aspect Ratio Modes (MX Player Style)
 */
enum class AspectRatioMode(val label: String, val iconResName: String) {
    FIT_TO_SCREEN("Fit to Screen", "fit_screen"),
    STRETCH("Stretch", "open_in_full"),
    CROP_FILL("Crop / Zoom", "crop"),
    RATIO_16_9("16:9", "aspect_ratio"),
    RATIO_4_3("4:3", "aspect_ratio"),
    ORIGINAL("100% Original", "zoom_in")
}

/**
 * 🎧 Multi-Audio Track Information
 */
data class AdvancedAudioTrackInfo(
    val id: String,
    val languageCode: String,
    val displayName: String,
    val channelInfo: String, // e.g. "Stereo (2.0)", "Surround 5.1"
    val codec: String,       // e.g. "AAC", "AC3", "EAC3", "MP3"
    val bitRate: Int = 0,
    val groupIndex: Int,
    val trackIndex: Int,
    val isSelected: Boolean = false
)

/**
 * 💬 Multi-Subtitle Track Information
 */
data class AdvancedSubtitleTrackInfo(
    val id: String,
    val languageCode: String,
    val displayName: String,
    val isExternal: Boolean = false,
    val fileUri: Uri? = null,
    val groupIndex: Int = -1,
    val trackIndex: Int = -1,
    val isSelected: Boolean = false
)

/**
 * ℹ️ Video Detailed Media Information
 */
data class VideoMediaDetails(
    val title: String,
    val fileName: String,
    val path: String,
    val sizeText: String,
    val durationText: String,
    val resolutionText: String,
    val videoCodec: String,
    val audioCodec: String,
    val audioChannels: String,
    val containerFormat: String
)

/**
 * 🏷️ UI Video Badge Helper
 */
object VideoBadgeResolver {
    fun resolveResolutionBadge(width: Int, height: Int, resolutionString: String?): String? {
        val maxDim = maxOf(width, height)
        val minDim = minOf(width, height)

        if (resolutionString != null && resolutionString.isNotBlank()) {
            val parts = resolutionString.split("x", "X")
            if (parts.size == 2) {
                val parsedWidth = parts[0].trim().toIntOrNull() ?: 0
                val parsedHeight = parts[1].trim().toIntOrNull() ?: 0
                val parsedMax = maxOf(parsedWidth, parsedHeight)
                return when {
                    parsedMax >= 3840 || parsedHeight >= 2160 -> "4K"
                    parsedMax >= 1920 || parsedHeight >= 1080 -> "1080p"
                    parsedMax >= 1280 || parsedHeight >= 720 -> "720p"
                    parsedMax > 0 -> "SD"
                    else -> null
                }
            }
        }

        return when {
            maxDim >= 3840 || minDim >= 2160 -> "4K"
            maxDim >= 1920 || minDim >= 1080 -> "1080p"
            maxDim >= 1280 || minDim >= 720 -> "720p"
            maxDim > 0 -> "SD"
            else -> null
        }
    }
}
