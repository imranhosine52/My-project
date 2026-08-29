package com.example.util

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

sealed class UpdateDownloadState {
    object Idle : UpdateDownloadState()
    data class Downloading(val progressPercent: Int, val downloadedMb: Float, val totalMb: Float) : UpdateDownloadState()
    data class ReadyToInstall(val apkFile: File) : UpdateDownloadState()
    data class Error(val message: String) : UpdateDownloadState()
}

object InAppUpdateManager {
    private const val TAG = "InAppUpdateManager"

    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()

    private var downloadJob: Job? = null

    /**
     * Start In-App Direct APK Download with Coroutines & Streamed Buffer
     */
    fun startInAppDownload(context: Context, downloadUrl: String, targetVersion: String) {
        if (_downloadState.value is UpdateDownloadState.Downloading) {
            Log.d(TAG, "Download already in progress")
            return
        }

        downloadJob?.cancel()
        downloadJob = CoroutineScope(Dispatchers.IO).launch {
            _downloadState.value = UpdateDownloadState.Downloading(0, 0f, 0f)

            try {
                // Ensure update directory in external cache or internal files directory
                val updateDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "updates")
                if (!updateDir.exists()) {
                    updateDir.mkdirs()
                }

                val sanitizedVersion = targetVersion.replace(".", "_")
                val apkFile = File(updateDir, "PlayDramaFlix_v${sanitizedVersion}.apk")
                if (apkFile.exists()) {
                    apkFile.delete()
                }

                var currentUrl = downloadUrl
                var connection: HttpURLConnection? = null
                var redirects = 0

                while (redirects < 5) {
                    val url = URL(currentUrl)
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 15000
                        readTimeout = 30000
                        instanceFollowRedirects = true
                        setRequestProperty("Accept-Encoding", "identity")
                        setRequestProperty("User-Agent", "Mozilla/5.0 (PlayDramaFlix App)")
                        connect()
                    }

                    val code = conn.responseCode
                    if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP || code == HttpURLConnection.HTTP_SEE_OTHER || code == 307 || code == 308) {
                        val newLocation = conn.getHeaderField("Location")
                        conn.disconnect()
                        if (!newLocation.isNullOrBlank()) {
                            currentUrl = newLocation
                            redirects++
                            continue
                        }
                    }

                    connection = conn
                    break
                }

                val activeConnection = connection ?: throw IllegalStateException("Could not establish connection to $downloadUrl")
                if (activeConnection.responseCode !in 200..299) {
                    throw IllegalStateException("Server returned HTTP ${activeConnection.responseCode}: ${activeConnection.responseMessage}")
                }

                val contentLength = activeConnection.contentLengthLong
                val totalMb = if (contentLength > 0) contentLength / (1024f * 1024f) else 0f

                val inputStream = activeConnection.inputStream
                val outputStream = FileOutputStream(apkFile)
                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                var totalBytesRead = 0L
                var lastProgressUpdate = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (!isActive) {
                        outputStream.close()
                        inputStream.close()
                        apkFile.delete()
                        return@launch
                    }

                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    val now = System.currentTimeMillis()
                    if (now - lastProgressUpdate > 100 || totalBytesRead == contentLength) {
                        lastProgressUpdate = now
                        val progressPercent = if (contentLength > 0) {
                            ((totalBytesRead * 100) / contentLength).toInt().coerceIn(0, 100)
                        } else {
                            0
                        }
                        val downloadedMb = totalBytesRead / (1024f * 1024f)

                        _downloadState.value = UpdateDownloadState.Downloading(
                            progressPercent = progressPercent,
                            downloadedMb = downloadedMb,
                            totalMb = totalMb
                        )
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()
                connection.disconnect()

                Log.d(TAG, "Download finished. APK saved at: ${apkFile.absolutePath}")
                _downloadState.value = UpdateDownloadState.ReadyToInstall(apkFile)

                // Auto-trigger installation immediately
                withContext(Dispatchers.Main) {
                    installApk(context, apkFile)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Download failed: ${e.message}", e)
                _downloadState.value = UpdateDownloadState.Error(e.message ?: "Failed to download update APK")
            }
        }
    }

    /**
     * Launch Android Package Installer for the downloaded APK
     */
    fun installApk(context: Context, apkFile: File) {
        try {
            if (!apkFile.exists()) {
                Toast.makeText(context, "APK file not found. Please download again.", Toast.LENGTH_SHORT).show()
                return
            }

            // Check Unknown App Install Permission for Android 8.0 (API 26) and above
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    Toast.makeText(context, "Please allow PlayDramaFlix to install app updates", Toast.LENGTH_LONG).show()
                    val permissionIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(permissionIntent)
                    return
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            context.startActivity(installIntent)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer: ${e.message}", e)
            Toast.makeText(context, "Install error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun resetState() {
        downloadJob?.cancel()
        _downloadState.value = UpdateDownloadState.Idle
    }
}
