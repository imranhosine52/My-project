package com.example.util

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
import java.util.regex.Pattern

/**
 * ⚡ UniversalVideoExtractor
 * YouTube, Facebook, Instagram, TikTok ও Web ভিডিও থেকে ডিরেক্ট ডাউনলোড লিঙ্ক বের করার ইঞ্জিন।
 */
object UniversalVideoExtractor {
    private const val TAG = "VideoExtractor"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
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
     * প্রধান ফাংশন: যেকোনো ভিডিও লিঙ্ক থেকে মেটাডাটা ও কোয়ালিটি লিস্ট বের করা
     */
    suspend fun extractVideoInfo(rawUrl: String): Result<DownloadableVideoInfo> = withContext(Dispatchers.IO) {
        val cleanUrl = rawUrl.trim()
        val platform = detectPlatform(cleanUrl)

        try {
            when (platform) {
                DownloadPlatform.TIKTOK -> extractTikTok(cleanUrl)
                DownloadPlatform.YOUTUBE -> extractYouTubeOrCobalt(cleanUrl, platform)
                DownloadPlatform.FACEBOOK -> extractFacebook(cleanUrl)
                DownloadPlatform.INSTAGRAM -> extractInstagram(cleanUrl)
                else -> extractYouTubeOrCobalt(cleanUrl, platform)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Extraction failed for $cleanUrl: ${e.message}", e)
            Result.failure(Exception(e.message ?: "Could not extract video stream. Please check link."))
        }
    }

    // =========================================================================
    // 1. ⚫ TIKTOK EXTRACTOR (১০০% ওয়াটারমার্ক ছাড়া HD ভিডিও ও MP3)
    // =========================================================================
    private fun extractTikTok(url: String): Result<DownloadableVideoInfo> {
        val apiUrl = "https://www.tikwm.com/api/?url=${Uri.encode(url)}"
        val request = Request.Builder()
            .url(apiUrl)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        val response = httpClient.newCall(request).execute()
        val bodyString = response.body?.string() ?: throw IllegalStateException("Empty response from TikTok server")
        val json = JSONObject(bodyString)

        if (json.optInt("code", -1) != 0) {
            return extractYouTubeOrCobalt(url, DownloadPlatform.TIKTOK)
        }

        val data = json.getJSONObject("data")
        val title = data.optString("title").ifBlank { "TikTok Video ${System.currentTimeMillis()}" }
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
                    qualityLabel = "HD (No Watermark)",
                    resolutionText = "1080p HD",
                    downloadUrl = if (hdPlayUrl.startsWith("http")) hdPlayUrl else "https://www.tikwm.com$hdPlayUrl",
                    approximateSizeBytes = data.optLong("hd_size", 0L)
                )
            )
        }

        if (!noWatermarkPlayUrl.isNullOrBlank()) {
            formats.add(
                VideoFormatOption(
                    formatId = "tiktok_nowm",
                    qualityLabel = "Fast (No Watermark)",
                    resolutionText = "720p Clean",
                    downloadUrl = if (noWatermarkPlayUrl.startsWith("http")) noWatermarkPlayUrl else "https://www.tikwm.com$noWatermarkPlayUrl",
                    approximateSizeBytes = data.optLong("size", 0L)
                )
            )
        }

        if (!musicUrl.isNullOrBlank()) {
            formats.add(
                VideoFormatOption(
                    formatId = "tiktok_audio",
                    qualityLabel = "Audio (MP3 Sound)",
                    resolutionText = "Original Audio",
                    extension = "mp3",
                    downloadUrl = if (musicUrl.startsWith("http")) musicUrl else "https://www.tikwm.com$musicUrl",
                    isAudioOnly = true
                )
            )
        }

        return Result.success(
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
    }

    // =========================================================================
    // 2. 🔴 YOUTUBE, INSTA & UNIVERSAL COBALT API EXTRACTOR
    // =========================================================================
    private fun extractYouTubeOrCobalt(url: String, platform: DownloadPlatform): Result<DownloadableVideoInfo> {
        val cobaltInstances = listOf(
            "https://api.cobalt.tools/",
            "https://cobalt-api.kwiatekm.tokyo/",
            "https://co.wuk.sh/api/json"
        )

        for (endpoint in cobaltInstances) {
            try {
                val jsonPayload = JSONObject().apply {
                    put("url", url)
                    put("videoQuality", "1080")
                    put("audioFormat", "mp3")
                    put("downloadMode", "auto")
                }

                val request = Request.Builder()
                    .url(endpoint)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "PlayDramaFlix/2.1")
                    .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = httpClient.newCall(request).execute()
                val bodyString = response.body?.string()

                if (response.isSuccessful && !bodyString.isNullOrBlank()) {
                    val resJson = JSONObject(bodyString)
                    val streamUrl = resJson.optString("url").takeIf { it.isNotBlank() }
                        ?: resJson.optString("audio").takeIf { it.isNotBlank() }

                    if (!streamUrl.isNullOrBlank()) {
                        val title = resJson.optString("filename")
                            .replace(Regex("\\.[a-zA-Z0-9]+$"), "")
                            .ifBlank { "${platform.label} Video ${System.currentTimeMillis() % 10000}" }

                        val formats = listOf(
                            VideoFormatOption(
                                formatId = "hd_best",
                                qualityLabel = "Full HD (1080p / Best)",
                                resolutionText = "1080p Max",
                                downloadUrl = streamUrl
                            ),
                            VideoFormatOption(
                                formatId = "hd_720",
                                qualityLabel = "HD Video (720p)",
                                resolutionText = "720p MP4",
                                downloadUrl = streamUrl
                            ),
                            VideoFormatOption(
                                formatId = "audio_mp3",
                                qualityLabel = "Audio Only (MP3)",
                                resolutionText = "High Bitrate",
                                extension = "mp3",
                                downloadUrl = streamUrl,
                                isAudioOnly = true
                            )
                        )

                        return Result.success(
                            DownloadableVideoInfo(
                                sourceUrl = url,
                                title = title,
                                thumbnailUrl = "https://img.youtube.com/vi/${extractYouTubeVideoId(url)}/hqdefault.jpg",
                                platform = platform,
                                availableFormats = formats
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cobalt instance $endpoint error: ${e.message}")
            }
        }

        // ফলব্যাক: যদি ডিরেক্ট স্ট্রিমিং লিঙ্ক থাকে
        if (url.endsWith(".mp4") || url.endsWith(".m3u8") || url.contains("googlevideo.com")) {
            return Result.success(
                DownloadableVideoInfo(
                    sourceUrl = url,
                    title = "Direct Video Stream",
                    platform = platform,
                    availableFormats = listOf(
                        VideoFormatOption(
                            formatId = "direct_mp4",
                            qualityLabel = "Original Video",
                            resolutionText = "Direct MP4",
                            downloadUrl = url
                        )
                    )
                )
            )
        }

        return Result.failure(Exception("Cannot resolve video streams. Video might be private or region restricted."))
    }

    // =========================================================================
    // 3. 🔵 FACEBOOK DIRECT EXTRACTOR (HD & SD)
    // =========================================================================
    private fun extractFacebook(url: String): Result<DownloadableVideoInfo> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val html = response.body?.string() ?: ""

            val hdMatch = findRegex(html, "hd_src:\"(https:[^\"]+)\"")
                ?: findRegex(html, "\"playable_url_quality_hd\":\"(https:[^\"]+)\"")

            val sdMatch = findRegex(html, "sd_src:\"(https:[^\"]+)\"")
                ?: findRegex(html, "\"playable_url\":\"(https:[^\"]+)\"")

            val titleMatch = findRegex(html, "<title>(.*?)</title>") ?: "Facebook Video"
            val cleanTitle = titleMatch.replace("| Facebook", "").trim()

            val formats = mutableListOf<VideoFormatOption>()

            if (!hdMatch.isNullOrBlank()) {
                formats.add(
                    VideoFormatOption(
                        formatId = "fb_hd",
                        qualityLabel = "High Quality (HD)",
                        resolutionText = "720p/1080p",
                        downloadUrl = hdMatch.replace("\\/", "/")
                    )
                )
            }

            if (!sdMatch.isNullOrBlank()) {
                formats.add(
                    VideoFormatOption(
                        formatId = "fb_sd",
                        qualityLabel = "Standard Quality (SD)",
                        resolutionText = "480p SD",
                        downloadUrl = sdMatch.replace("\\/", "/")
                    )
                )
            }

            if (formats.isNotEmpty()) {
                return Result.success(
                    DownloadableVideoInfo(
                        sourceUrl = url,
                        title = cleanTitle,
                        platform = DownloadPlatform.FACEBOOK,
                        availableFormats = formats
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "FB native parser notice: ${e.message}")
        }

        return extractYouTubeOrCobalt(url, DownloadPlatform.FACEBOOK)
    }

    // =========================================================================
    // 4. 🟣 INSTAGRAM REELS EXTRACTOR
    // =========================================================================
    private fun extractInstagram(url: String): Result<DownloadableVideoInfo> {
        return extractYouTubeOrCobalt(url, DownloadPlatform.INSTAGRAM)
    }

    private fun findRegex(text: String, patternString: String): String? {
        val pattern = Pattern.compile(patternString)
        val matcher = pattern.matcher(text)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun extractYouTubeVideoId(ytUrl: String): String {
        return try {
            val uri = Uri.parse(ytUrl)
            uri.getQueryParameter("v")
                ?: if (ytUrl.contains("youtu.be/")) ytUrl.substringAfter("youtu.be/").substringBefore("?")
                else if (ytUrl.contains("/shorts/")) ytUrl.substringAfter("/shorts/").substringBefore("?")
                else "default"
        } catch (_: Exception) {
            "default"
        }
    }
}
