package com.example.util

import android.net.Uri
import android.util.Log
import com.example.data.model.DownloadPlatform
import com.example.data.model.DownloadableVideoInfo
import com.example.data.model.VideoFormatOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * ⚡ UniversalVideoExtractor
 * কানেক্টেড: PlayDramaFlix নিজস্ব লাইভ ব্যাকএন্ড সার্ভার (FastAPI + yt-dlp)
 */
object UniversalVideoExtractor {
    private const val TAG = "VideoExtractor"

    // 🚀 আপনার নিজস্ব লাইভ সার্ভারের API এন্ডপয়েন্ট
    private const val BACKEND_API_URL = "https://playdramaflix.com/extractor/extract"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * লিঙ্কের ডোমেইন দেখে প্ল্যাটফর্ম শনাক্ত করা
     */
    fun detectPlatform(rawUrl: String): DownloadPlatform {
        val lower = rawUrl.lowercase().trim()
        return when {
            lower.contains("youtube.com") || lower.contains("youtu.be") -> DownloadPlatform.YOUTUBE
            lower.contains("facebook.com") || lower.contains("fb.watch") || lower.contains("fb.com") -> DownloadPlatform.FACEBOOK
            lower.contains("instagram.com") || lower.contains("instagr.am") -> DownloadPlatform.INSTAGRAM
            lower.contains("tiktok.com") || lower.contains("douyin.com") -> DownloadPlatform.TIKTOK
            lower.contains("twitter.com") || lower.contains("x.com") -> DownloadPlatform.TWITTER
            else -> DownloadPlatform.OTHER
        }
    }

    /**
     * মূল এক্সট্র্যাকশন মেথড
     */
    suspend fun extractVideoInfo(rawUrl: String): Result<DownloadableVideoInfo> = withContext(Dispatchers.IO) {
        val cleanUrl = rawUrl.trim()
        val platform = detectPlatform(cleanUrl)

        // ১. নিজস্ব PlayDramaFlix ব্যাকএন্ড সার্ভার দিয়ে এক্সট্র্যাক্ট করা
        val serverResult = extractFromSelfHostedServer(cleanUrl, platform)
        if (serverResult.isSuccess) {
            return@withContext serverResult
        }

        // ২. ফলব্যাক: সার্ভারে কোনো সমস্যা হলে টিকটকের লাইভ ডিরেক্ট API
        if (platform == DownloadPlatform.TIKTOK) {
            val tiktokFallback = extractTikTokDirect(cleanUrl)
            if (tiktokFallback.isSuccess) {
                return@withContext tiktokFallback
            }
        }

        return@withContext serverResult
    }

    /**
     * 🌐 নিজস্ব PlayDramaFlix সার্ভার থেকে এক্সট্র্যাক্ট করার মেথড
     */
    private fun extractFromSelfHostedServer(url: String, platform: DownloadPlatform): Result<DownloadableVideoInfo> {
        return try {
            val requestUrl = "$BACKEND_API_URL?url=${Uri.encode(url)}"
            val request = Request.Builder()
                .url(requestUrl)
                .header("Accept", "application/json")
                .header("User-Agent", "PlayDramaFlix-App/2.1")
                .build()

            val response = httpClient.newCall(request).execute()
            val bodyString = response.body?.string() ?: throw IllegalStateException("Empty response from server")

            if (!response.isSuccessful) {
                val errorMsg = runCatching {
                    JSONObject(bodyString).optString("detail", "Server returned HTTP ${response.code}")
                }.getOrDefault("Server returned HTTP ${response.code}")
                return Result.failure(Exception(errorMsg))
            }

            val json = JSONObject(bodyString)
            val title = json.optString("title", "Video_${System.currentTimeMillis() % 10000}")
            val author = json.optString("author")
            val duration = json.optLong("duration_seconds", 0L)
            val thumbnail = json.optString("thumbnail_url").takeIf { it.isNotBlank() }
            val formatsArray = json.getJSONArray("formats")

            val formatsList = mutableListOf<VideoFormatOption>()
            for (i in 0 until formatsArray.length()) {
                val f = formatsArray.getJSONObject(i)
                formatsList.add(
                    VideoFormatOption(
                        formatId = f.getString("format_id"),
                        qualityLabel = f.getString("quality_label"),
                        resolutionText = f.getString("resolution"),
                        extension = f.getString("ext"),
                        downloadUrl = f.getString("download_url"),
                        approximateSizeBytes = f.optLong("filesize_approx", 0L),
                        isAudioOnly = f.optBoolean("is_audio_only", false)
                    )
                )
            }

            if (formatsList.isEmpty()) {
                return Result.failure(Exception("No downloadable formats found for this video."))
            }

            Result.success(
                DownloadableVideoInfo(
                    sourceUrl = url,
                    title = title,
                    author = author,
                    durationSeconds = duration,
                    thumbnailUrl = thumbnail,
                    platform = platform,
                    availableFormats = formatsList
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Self-hosted server extraction error: ${e.message}", e)
            Result.failure(Exception(e.message ?: "Failed to connect to extraction server."))
        }
    }

    /**
     * 🎯 ব্যাকআপ টিকটক এক্সট্র্যাক্টর
     */
    private fun extractTikTokDirect(url: String): Result<DownloadableVideoInfo> {
        return try {
            val apiUrl = "https://www.tikwm.com/api/?url=${Uri.encode(url)}"
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val response = httpClient.newCall(request).execute()
            val bodyString = response.body?.string() ?: throw IllegalStateException("Empty response")
            val json = JSONObject(bodyString)
            val data = json.optJSONObject("data") ?: return Result.failure(Exception("TikTok video unavailable"))

            val title = data.optString("title").ifBlank { "TikTok Video" }
            val cover = data.optString("cover")
            val duration = data.optLong("duration", 0L)
            val author = data.optJSONObject("author")?.optString("nickname") ?: "TikTok Creator"
            val noWatermarkPlayUrl = data.optString("play").takeIf { it.isNotBlank() }
            val musicUrl = data.optString("music").takeIf { it.isNotBlank() }

            val formats = mutableListOf<VideoFormatOption>()
            if (!noWatermarkPlayUrl.isNullOrBlank()) {
                formats.add(
                    VideoFormatOption(
                        formatId = "tiktok_nowm",
                        qualityLabel = "Fast (No Watermark)",
                        resolutionText = "720p Clean",
                        downloadUrl = if (noWatermarkPlayUrl.startsWith("http")) noWatermarkPlayUrl else "https://www.tikwm.com$noWatermarkPlayUrl"
                    )
                )
            }
            if (!musicUrl.isNullOrBlank()) {
                formats.add(
                    VideoFormatOption(
                        formatId = "tiktok_audio",
                        qualityLabel = "Audio (MP3 Music)",
                        resolutionText = "Original Audio",
                        extension = "mp3",
                        downloadUrl = if (musicUrl.startsWith("http")) musicUrl else "https://www.tikwm.com$musicUrl",
                        isAudioOnly = true
                    )
                )
            }

            Result.success(
                DownloadableVideoInfo(
                    sourceUrl = url,
                    title = title,
                    author = author,
                    durationSeconds = duration,
                    thumbnailUrl = cover,
                    platform = DownloadPlatform.TIKTOK,
                    availableFormats = formats
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
