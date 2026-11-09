package com.example.data.model

import android.net.Uri
import java.util.Locale

/**
 * 🎬 Gallery Tab Sections
 * Only functional, fully implemented sections are included.
 */
enum class GallerySection(val displayName: String, val iconName: String) {
    ALL_VIDEOS(displayName = "All Videos", iconName = "video_library"),
    FOLDERS(displayName = "Folders", iconName = "folder"),
    RECENTLY_ADDED(displayName = "Recently Added", iconName = "new_releases"),
    RECENTLY_PLAYED(displayName = "Recently Played", iconName = "history"),
    DOWNLOADS(displayName = "Downloads", iconName = "download"),
    FAVORITES(displayName = "Favorites", iconName = "star")
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
