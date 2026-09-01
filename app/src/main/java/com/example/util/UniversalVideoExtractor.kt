package com.example.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.model.DownloadPlatform
import com.example.data.model.DownloadableVideoInfo
import com.example.data.model.VideoFormatOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * ⚡ UniversalVideoExtractor
 * YouTube (Auto Audio+Video Merged MP4), TikTok (No-WM), Facebook & Instagram Downloader Engine.
 */
object UniversalVideoExtractor {
    private const val TAG = "VideoExtractor"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

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

    suspend fun extractVideoInfo(context: Context, rawUrl: String): Result<DownloadableVideoInfo> = withContext(Dispatchers.IO) {
        val cleanUrl = rawUrl.trim()
        val platform = detectPlatform(cleanUrl)

        try {
            when (platform) {
                DownloadPlatform.YOUTUBE -> extractYouTubeMerged(cleanUrl)
                DownloadPlatform.TIKTOK -> extractTikTokDirect(cleanUrl)
                DownloadPlatform.INSTAGRAM -> extractInstagramDirect(cleanUrl)
                DownloadPlatform.FACEBOOK -> extractFacebookDirect(cleanUrl)
                else -> extractYouTubeMerged(cleanUrl)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Extraction failed: ${e.message}", e)
            Result.failure(Exception(e.message ?: "Failed to resolve video stream. Please check link."))
        }
    }

    // =========================================================================
    // 🔴 1. YOUTUBE CONVERTER ENGINE (পূর্ণাঙ্গ ভিডিও + অডিও যুক্ত MP4)
    // =========================================================================
    private fun extractYouTubeMerged(url: String): Result<DownloadableVideoInfo> {
        val videoId = extractYouTubeVideoId(url)
        if (videoId.isBlank() || videoId == "default") {
            return Result.failure(Exception("Invalid YouTube URL."))
        }

        // মেথড ১: Y2Mate / MP4 Converter Engine
        try {
            val endpoint = "https://cdn59.savetube.me/info?url=https://www.youtube.com/watch?v=$videoId"
            val req = Request.Builder()
                .url(endpoint)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val res = httpClient.newCall(req).execute()
            val body = res.body?.string() ?: ""

            if (res.isSuccessful && body.isNotBlank()) {
                val json = JSONObject(body)
                val data = json.optJSONObject("data") ?: json

                val title = data.optString("title", "YouTube Video $videoId")
                val thumb = data.optString("thumbnail_url", "https://img.youtube.com/vi/$videoId/hqdefault.jpg")
                val author = data.optString("channel_title", "YouTube Creator")
                val duration = data.optLong("duration", 0L)

                val formats = mutableListOf<VideoFormatOption>()
                val videoFormats = data.optJSONArray("video_formats")

                if (videoFormats != null) {
                    for (i in 0 until videoFormats.length()) {
                        val v = videoFormats.getJSONObject(i)
                        val dlUrl = v.optString("url")
                        val quality = v.optString("quality", "720")
                        val size = v.optLong("filesize", 0L)

                        if (dlUrl.startsWith("http")) {
                            formats.add(
                                VideoFormatOption(
                                    formatId = "yt_$quality",
                                    qualityLabel = "${quality}p HD MP4 (Full Video)",
                                    resolutionText = "${quality}p",
                                    downloadUrl = dlUrl,
                                    approximateSizeBytes = size,
                                    isAudioOnly = false
                                )
                            )
                        }
                    }
                }

                // অডিও MP3
                val audioFormats = data.optJSONArray("audio_formats")
                if (audioFormats != null && audioFormats.length() > 0) {
                    val a = audioFormats.getJSONObject(0)
                    val dlUrl = a.optString("url")
                    if (dlUrl.startsWith("http")) {
                        formats.add(
                            VideoFormatOption(
                                formatId = "yt_audio",
                                qualityLabel = "Audio Only (MP3 Music)",
                                resolutionText = "HQ Audio",
                                extension = "mp3",
                                downloadUrl = dlUrl,
                                isAudioOnly = true
                            )
                        )
                    }
                }

                if (formats.isNotEmpty()) {
                    return Result.success(
                        DownloadableVideoInfo(
                            sourceUrl = url,
                            title = title,
                            author = author,
                            durationSeconds = duration,
                            thumbnailUrl = thumb,
                            platform = DownloadPlatform.YOUTUBE,
                            availableFormats = formats
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "SaveTube engine notice: ${e.message}")
        }

        // মেথড ২: Cobalt Live Audio+Video Muxer
        try {
            val jsonPayload = JSONObject().apply {
                put("url", "https://www.youtube.com/watch?v=$videoId")
                put("videoQuality", "720")
                put("downloadMode", "auto")
            }

            val req = Request.Builder()
                .url("https://co.wuk.sh/api/json")
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val res = httpClient.newCall(req).execute()
            val body = res.body?.string() ?: ""

            if (res.isSuccessful && body.isNotBlank()) {
                val resJson = JSONObject(body)
                val streamUrl = resJson.optString("url")
                if (streamUrl.isNotBlank()) {
                    val title = resJson.optString("filename").replace(".mp4", "").ifBlank { "YouTube Video $videoId" }

                    val formats = listOf(
                        VideoFormatOption(
                            formatId = "cobalt_720",
                            qualityLabel = "720p HD MP4 (Full Video + Audio)",
                            resolutionText = "720p HD",
                            downloadUrl = streamUrl
                        ),
                        VideoFormatOption(
                            formatId = "cobalt_audio",
                            qualityLabel = "Audio Only (MP3)",
                            resolutionText = "HQ Audio",
                            extension = "mp3",
                            downloadUrl = streamUrl,
                            isAudioOnly = true
                        )
                    )

                    return Result.success(
                        DownloadableVideoInfo(
                            sourceUrl = url,
                            title = title,
                            thumbnailUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg",
                            platform = DownloadPlatform.YOUTUBE,
                            availableFormats = formats
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cobalt muxer notice: ${e.message}")
        }

        return Result.failure(Exception("Could not extract complete YouTube stream. Video might be age-restricted."))
    }

    // =========================================================================
    // ⚫ 2. TIKTOK DIRECT (১০০% ওয়াটারমার্ক ছাড়া)
    // =========================================================================
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
            val hdPlayUrl = data.optString("hdplay").takeIf { it.isNotBlank() }
            val noWatermarkPlayUrl = data.optString("play").takeIf { it.isNotBlank() }
            val musicUrl = data.optString("music").takeIf { it.isNotBlank() }

            val formats = mutableListOf<VideoFormatOption>()
            if (!hdPlayUrl.isNullOrBlank()) {
                formats.add(
                    VideoFormatOption(
                        formatId = "tiktok_hd",
                        qualityLabel = "HD Video (No Watermark)",
                        resolutionText = "1080p HD",
                        downloadUrl = if (hdPlayUrl.startsWith("http")) hdPlayUrl else "https://www.tikwm.com$hdPlayUrl"
                    )
                )
            }
            if (!noWatermarkPlayUrl.isNullOrBlank()) {
                formats.add(
                    VideoFormatOption(
                        formatId = "tiktok_nowm",
                        qualityLabel = "Fast Video (No Watermark)",
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

    // =========================================================================
    // 🟣 3. INSTAGRAM REELS DIRECT
    // =========================================================================
    private fun extractInstagramDirect(url: String): Result<DownloadableVideoInfo> {
        return try {
            val apiUrl = "https://api.vkrdownloader.com/server?vkr=${Uri.encode(url)}"
            val req = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val res = httpClient.newCall(req).execute()
            val body = res.body?.string() ?: ""
            val json = JSONObject(body)
            val data = json.optJSONObject("data") ?: json
            val downloads = data.optJSONArray("downloads")

            val formats = mutableListOf<VideoFormatOption>()
            if (downloads != null) {
                for (i in 0 until downloads.length()) {
                    val item = downloads.getJSONObject(i)
                    val dlUrl = item.optString("url")
                    if (dlUrl.startsWith("http")) {
                        formats.add(
                            VideoFormatOption(
                                formatId = "ig_$i",
                                qualityLabel = "Instagram HD Video (MP4)",
                                resolutionText = "1080p HD",
                                downloadUrl = dlUrl
                            )
                        )
                        break
                    }
                }
            }

            if (formats.isNotEmpty()) {
                Result.success(
                    DownloadableVideoInfo(
                        sourceUrl = url,
                        title = data.optString("title", "Instagram Reel"),
                        thumbnailUrl = data.optString("thumbnail"),
                        platform = DownloadPlatform.INSTAGRAM,
                        availableFormats = formats
                    )
                )
            } else {
                Result.failure(Exception("No direct Instagram stream"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // 🔵 4. FACEBOOK DIRECT
    // =========================================================================
    private fun extractFacebookDirect(url: String): Result<DownloadableVideoInfo> {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val res = httpClient.newCall(req).execute()
            val html = res.body?.string() ?: ""

            val hdMatch = findRegex(html, "hd_src:\"(https:[^\"]+)\"")
                ?: findRegex(html, "\"playable_url_quality_hd\":\"(https:[^\"]+)\"")
                ?: findRegex(html, "property=\"og:video\" content=\"(https:[^\"]+)\"")

            val sdMatch = findRegex(html, "sd_src:\"(https:[^\"]+)\"")
                ?: findRegex(html, "\"playable_url\":\"(https:[^\"]+)\"")

            val formats = mutableListOf<VideoFormatOption>()
            if (!hdMatch.isNullOrBlank()) {
                formats.add(
                    VideoFormatOption(
                        formatId = "fb_hd",
                        qualityLabel = "High Quality (HD 1080p)",
                        resolutionText = "HD Video",
                        downloadUrl = hdMatch.replace("\\/", "/").replace("&amp;", "&")
                    )
                )
            }
            if (!sdMatch.isNullOrBlank()) {
                formats.add(
                    VideoFormatOption(
                        formatId = "fb_sd",
                        qualityLabel = "Standard Quality (SD)",
                        resolutionText = "SD Video",
                        downloadUrl = sdMatch.replace("\\/", "/").replace("&amp;", "&")
                    )
                )
            }

            if (formats.isNotEmpty()) {
                Result.success(
                    DownloadableVideoInfo(
                        sourceUrl = url,
                        title = findRegex(html, "<title>(.*?)</title>")?.replace("| Facebook", "")?.trim() ?: "Facebook Video",
                        platform = DownloadPlatform.FACEBOOK,
                        availableFormats = formats
                    )
                )
            } else {
                Result.failure(Exception("No direct Facebook stream"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun findRegex(text: String, patternString: String): String? {
        val pattern = java.util.regex.Pattern.compile(patternString)
        val matcher = pattern.matcher(text)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun extractYouTubeVideoId(ytUrl: String): String {
        val clean = ytUrl.trim()
        return try {
            val uri = Uri.parse(clean)
            val vParam = uri.getQueryParameter("v")
            if (!vParam.isNullOrBlank()) return vParam

            if (clean.contains("youtu.be/")) {
                clean.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
            } else if (clean.contains("/shorts/")) {
                clean.substringAfter("/shorts/").substringBefore("?").substringBefore("&")
            } else if (clean.contains("/embed/")) {
                clean.substringAfter("/embed/").substringBefore("?").substringBefore("&")
            } else {
                "default"
            }
        } catch (_: Exception) {
            "default"
        }
    }
}
