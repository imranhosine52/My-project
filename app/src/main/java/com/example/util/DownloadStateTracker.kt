package com.example.util

import com.example.data.model.LocalVideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ActiveDownloadTask(
    val id: Int,
    val title: String,
    val episodeNumber: Int,
    val posterUrl: String? = null,
    val progressPercent: Int = 0,
    val downloadedMb: Float = 0f,
    val totalMb: Float = 0f,
    val isCompleted: Boolean = false,
    val isFailed: Boolean = false,
    val localFilePath: String? = null,
    val contentUriString: String? = null
)

object DownloadStateTracker {
    private val _activeDownloads = MutableStateFlow<Map<Int, ActiveDownloadTask>>(emptyMap())
    val activeDownloads: StateFlow<Map<Int, ActiveDownloadTask>> = _activeDownloads.asStateFlow()

    fun startTask(id: Int, title: String, episodeNumber: Int, posterUrl: String?) {
        _activeDownloads.update { current ->
            current + (id to ActiveDownloadTask(
                id = id,
                title = title,
                episodeNumber = episodeNumber,
                posterUrl = posterUrl
            ))
        }
    }

    fun updateProgress(id: Int, progressPercent: Int, downloadedMb: Float, totalMb: Float) {
        _activeDownloads.update { current ->
            val task = current[id] ?: return@update current
            current + (id to task.copy(
                progressPercent = progressPercent,
                downloadedMb = downloadedMb,
                totalMb = totalMb
            ))
        }
    }

    fun completeTask(id: Int, filePath: String?, contentUri: String?) {
        _activeDownloads.update { current ->
            val task = current[id] ?: return@update current
            current + (id to task.copy(
                progressPercent = 100,
                isCompleted = true,
                localFilePath = filePath,
                contentUriString = contentUri
            ))
        }
    }

    fun removeTask(id: Int) {
        _activeDownloads.update { current ->
            current - id
        }
    }
}
