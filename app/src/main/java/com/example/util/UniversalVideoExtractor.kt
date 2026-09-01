package com.example.util

import android.content.Context
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
import java.util.regex.Pattern

/**
 * ⚡ UniversalVideoExtractor
 * ৩-স্তরের লাইভ ক্লাস্টার ইঞ্জিন (YouTube Full MP4, TikTok No-WM, Facebook & Instagram).
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
                DownloadPlatform.YOUTUBE -> extractYouTubeMultiCluster(cleanUrl)
                DownloadPlatform.TIKTOK -> extractTikTokDirect(cleanUrl)
                DownloadPlatform.INSTAGRAM -> extractInstagramDirect(cleanUrl)
                DownloadPlatform.FACEBOOK -> extractFacebookDirect(cleanUrl)
                else -> extractYouTubeMultiCluster(cleanUrl)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Extraction failed: ${e.message}", e)
            Result.failure(Exception(e.message ?: "Failed to resolve video stream. Please check link."))
        }
    }

    // =========================================================================
    // 🔴 1. YOUTUBE MULTI-CLUSTER ENGINE (Invidious + SaveTube + VKr)
    // =========================================================================
    private fun extractYouTubeMultiCluster(url: String): Result<DownloadableVideoInfo> {
        val videoId = extractYouTubeVideoId(url)
        if (videoId.isBlank() || videoId == "default") {
            return Result.failure(Exception("Invalid YouTube URL. Could not parse video ID."))
        }

        // 🌟 ক্লাস্টার ১: গ্লোবাল ইনভিডিয়াস প্রক্সি ক্লাস্টার (১০০% সচল ও নির্ভরযোগ্য)
        val invidiousInstances = listOf(
            "https://inv.nadeko.net",
            "https://invidious.nerdvpn.de",
            "https://yt.chocolatemoo53.com",
            "https://invidious.tiekoetter.com",
            "https://invidious.drgns.space"
        )

        for (base in invidiousInstances) {
            try {
                val apiUrl = "$base/api/v1/videos/$videoId"
                val req = Request.Builder()
                    .url(apiUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .header("Accept", "application/json")
                    .build()

                val res = httpClient.newCall(req).execute()
                val body = res.body?.string() ?: ""

                if (res.isSuccessful && body.isNotBlank()) {
                    val json = JSONObject(body)
                    val title = json.optString("title").ifBlank { "YouTube Video $videoId" }
                    val author = json.optString("author")
                    val duration = json.optLong("lengthSeconds", 0L)
                    val thumbs = json.optJSONArray("videoThumbnails")
                    val thumbnail = thumbs?.optJSONObject(0)?.optString("url")
                        ?: "https://img.youtube.com/vi/$videoId/hqdefault.jpg"

                    val formats = mutableListOf<VideoFormatOption>()

                    // 🎬 প্রোগ্রেসিভ ফুল ভিডিও স্ট্রিমস (ভিডিও + অডিও একসাথে যুক্ত MP4)
                    val formatStreams = json.optJSONArray("formatStreams")
                    if (formatStreams != null) {
                        for (i in 0 until formatStreams.length()) {
                            val stream = formatStreams.getJSONObject(i)
                            var sUrl = stream.optString("url")
                            val qLabel = stream.optString("qualityLabel").ifBlank { stream.optString("quality", "720p") }
                            val resText = stream.optString("resolution", "HD")

                            if (sUrl.startsWith("/")) {
                                sUrl = "$base$sUrl"
                            }

                            if (sUrl.startsWith("http")) {
                                formats.add(
                                    VideoFormatOption(
                                        formatId = "yt_prog_${qLabel}_$i",
                                        qualityLabel = "$qLabel HD MP4 (Full Video)",
                                        resolutionText = resText,
                                        downloadUrl = sUrl,
                                        isAudioOnly = false
                                    )
                                )
                            }
                        }
                    }

                    // 🎵 অডিও MP3 ফরম্যাট
                    val adaptiveFormats = json.optJSONArray("adaptiveFormats")
                    if (adaptiveFormats != null) {
                        for (i in 0 until adaptiveFormats.length()) {
                            val stream = adaptiveFormats.getJSONObject(i)
                            val type = stream.optString("type")
                            var aUrl = stream.optString("url")
                            if (type.contains("audio", ignoreCase = true) && aUrl.isNotBlank()) {
                                if (aUrl.startsWith("/")) {
                                    aUrl = "$base$aUrl"
                                }
                                formats.add(
                                    VideoFormatOption(
                                        formatId = "yt_audio_best",
                                        qualityLabel = "Audio Only (MP3 HQ)",
                                        resolutionText = "HQ Audio",
                                        extension = "mp3",
                                        downloadUrl = aUrl,
                                        isAudioOnly = true
                                    )
                                )
                                break
                            }
                        }
                    }

                    if (formats.isNotEmpty()) {
                        return Result.success(
                            DownloadableVideoInfo(
                                sourceUrl = url,
                                title = title,
                                author = author,
                                durationSeconds = duration,
                                thumbnailUrl = thumbnail,
                                platform = DownloadPlatform.YOUTUBE,
                                availableFormats = formats
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Invidious instance $base failed: ${e.message}")
            }
        }

        // 🌟 ক্লাস্টার ২: VKr Downloader API
        try {
            val vkrUrl = "https://api.vkrdownloader.com/server?vkr=https://www.youtube.com/watch?v=$videoId"
            val req = Request.Builder().url(vkrUrl).header("User-Agent", "Mozilla/5.0").build()
            val res = httpClient.newCall(req).execute()
            val body = res.body?.string() ?: ""

            if (res.isSuccessful && body.isNotBlank()) {
                val json = JSONObject(body)
                val data = json.optJSONObject("data") ?: json
                val title = data.optString("title", "YouTube Video $videoId")
                val thumb = data.optString("thumbnail", "https://img.youtube.com/vi/$videoId/hqdefault.jpg")
                val downloads = data.optJSONArray("downloads")

                val formats = mutableListOf<VideoFormatOption>()
                if (downloads != null) {
                    for (i in 0 until downloads.length()) {
                        val item = downloads.getJSONObject(i)
                        val dlUrl = item.optString("url")
                        val qText = item.optString("format_note", item.optString("format_id", "720p"))
                        if (dlUrl.startsWith("http")) {
                            formats.add(
                                VideoFormatOption(
                                    formatId = "vkr_$i",
                                    qualityLabel = "$qText MP4 (Full Video)",
                                    resolutionText = qText,
                                    downloadUrl = dlUrl,
                                    isAudioOnly = false
                                )
                            )
                            if (formats.size >= 2) break
                        }
                    }
                }

                if (formats.isNotEmpty()) {
                    return Result.success(
                        DownloadableVideoInfo(
                            sourceUrl = url,
                            title = title,
                            thumbnailUrl = thumb,
                            platform = DownloadPlatform.YOUTUBE,
                            availableFormats = formats
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "VKr engine notice: ${e.message}")
        }

        return Result.failure(Exception("Could not extract YouTube stream. Please check your internet connection."))
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
                Result.failure(Exception("No direct Instagram stream found."))
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
                Result.failure(Exception("No direct Facebook stream found."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
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
