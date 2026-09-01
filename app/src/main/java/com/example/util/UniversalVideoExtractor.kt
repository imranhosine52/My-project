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
import java.util.regex.Pattern

/**
 * ⚡ UniversalVideoExtractor
 * YouTube (Shorts & Full Video), TikTok (No-WM), Facebook & Instagram Downloader.
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
                DownloadPlatform.TIKTOK -> extractTikTokDirect(cleanUrl)
                DownloadPlatform.YOUTUBE -> extractYouTubeCobaltV10(cleanUrl)
                DownloadPlatform.INSTAGRAM -> extractInstagramDirect(cleanUrl)
                DownloadPlatform.FACEBOOK -> {
                    val fbRes = extractFacebookDirect(cleanUrl)
                    if (fbRes.isSuccess) fbRes else extractCobaltRaw(cleanUrl, platform)
                }
                else -> extractCobaltRaw(cleanUrl, platform)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Extraction failed for $cleanUrl: ${e.message}", e)
            Result.failure(Exception(e.message ?: "Failed to resolve video stream. Please check link."))
        }
    }

    // =========================================================================
    // 🔴 1. YOUTUBE SHORTS & WATCH (Cobalt v10 + oEmbed Title Engine)
    // =========================================================================
    private fun extractYouTubeCobaltV10(url: String): Result<DownloadableVideoInfo> {
        val videoId = extractYouTubeVideoId(url)
        if (videoId.isBlank() || videoId == "default") {
            return Result.failure(Exception("Invalid YouTube URL. Could not parse video ID."))
        }

        val standardWatchUrl = "https://www.youtube.com/watch?v=$videoId"
        var videoTitle = "YouTube Video $videoId"
        var authorName: String? = null
        val thumbUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"

        // 🎬 oEmbed দিয়ে ১ ক্লিকে আসল টাইটেল ও চ্যানেলের নাম বের করা
        try {
            val oembedUrl = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json"
            val oembedReq = Request.Builder().url(oembedUrl).build()
            val oembedRes = httpClient.newCall(oembedReq).execute()
            if (oembedRes.isSuccessful) {
                val oembedJson = JSONObject(oembedRes.body?.string() ?: "{}")
                videoTitle = oembedJson.optString("title", videoTitle)
                authorName = oembedJson.optString("author_name")
            }
        } catch (_: Exception) {}

        // 🌟 Cobalt v10 লাইভ ক্লাস্টার
        val cobaltNodes = listOf(
            "https://api.cobalt.tools/",
            "https://cobalt-api.kwiatekm.tokyo/",
            "https://co.wuk.sh/api/json",
            "https://cobalt.hyonsu.com/api/json"
        )

        for (endpoint in cobaltNodes) {
            try {
                val payload = JSONObject().apply {
                    put("url", standardWatchUrl)
                }

                val request = Request.Builder()
                    .url(endpoint)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = httpClient.newCall(request).execute()
                val body = response.body?.string() ?: ""

                if (response.isSuccessful && body.isNotBlank()) {
                    val resJson = JSONObject(body)
                    val streamUrl = resJson.optString("url").takeIf { it.isNotBlank() }
                        ?: resJson.optJSONArray("picker")?.optJSONObject(0)?.optString("url")

                    if (!streamUrl.isNullOrBlank()) {
                        val formats = listOf(
                            VideoFormatOption(
                                formatId = "yt_hd_720",
                                qualityLabel = "HD Video (Full MP4)",
                                resolutionText = "720p/1080p",
                                downloadUrl = streamUrl
                            ),
                            VideoFormatOption(
                                formatId = "yt_audio_mp3",
                                qualityLabel = "Audio Only (MP3 Music)",
                                resolutionText = "HQ Audio",
                                extension = "mp3",
                                downloadUrl = streamUrl,
                                isAudioOnly = true
                            )
                        )

                        return Result.success(
                            DownloadableVideoInfo(
                                sourceUrl = url,
                                title = videoTitle,
                                author = authorName,
                                thumbnailUrl = thumbUrl,
                                platform = DownloadPlatform.YOUTUBE,
                                availableFormats = formats
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cobalt node $endpoint notice: ${e.message}")
            }
        }

        return Result.failure(Exception("Could not extract YouTube stream. Please verify video is public."))
    }

    // =========================================================================
    // ⚫ 2. TIKTOK DIRECT (১০০% নো ওয়াটারমার্ক)
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
        return extractCobaltRaw(url, DownloadPlatform.INSTAGRAM)
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
                extractCobaltRaw(url, DownloadPlatform.FACEBOOK)
            }
        } catch (e: Exception) {
            extractCobaltRaw(url, DownloadPlatform.FACEBOOK)
        }
    }

    private fun extractCobaltRaw(url: String, platform: DownloadPlatform): Result<DownloadableVideoInfo> {
        val cobaltNodes = listOf(
            "https://api.cobalt.tools/",
            "https://cobalt-api.kwiatekm.tokyo/",
            "https://co.wuk.sh/api/json"
        )

        for (endpoint in cobaltNodes) {
            try {
                val payload = JSONObject().apply { put("url", url) }
                val request = Request.Builder()
                    .url(endpoint)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = httpClient.newCall(request).execute()
                val body = response.body?.string() ?: ""

                if (response.isSuccessful && body.isNotBlank()) {
                    val resJson = JSONObject(body)
                    val streamUrl = resJson.optString("url").takeIf { it.isNotBlank() }
                    if (!streamUrl.isNullOrBlank()) {
                        return Result.success(
                            DownloadableVideoInfo(
                                sourceUrl = url,
                                title = "${platform.label} Video",
                                platform = platform,
                                availableFormats = listOf(
                                    VideoFormatOption(
                                        formatId = "cobalt_hd",
                                        qualityLabel = "HD Video (MP4)",
                                        resolutionText = "1080p HD",
                                        downloadUrl = streamUrl
                                    )
                                )
                            )
                        )
                    }
                }
            } catch (_: Exception) {}
        }
        return Result.failure(Exception("Could not resolve video stream."))
    }

    private fun findRegex(text: String, patternString: String): String? {
        val pattern = Pattern.compile(patternString)
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
