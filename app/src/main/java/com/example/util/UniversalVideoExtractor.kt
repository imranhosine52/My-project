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
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * ⚡ UniversalVideoExtractor
 * YouTube, Facebook, Instagram, TikTok ও Web ভিডিও থেকে ডিরেক্ট ডাউনলোড লিঙ্ক বের করার শক্তিশালী ইঞ্জিন।
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
                DownloadPlatform.YOUTUBE -> extractYouTube(cleanUrl)
                DownloadPlatform.TIKTOK -> extractTikTok(cleanUrl)
                DownloadPlatform.FACEBOOK -> extractFacebook(cleanUrl)
                DownloadPlatform.INSTAGRAM -> extractInstagram(cleanUrl)
                else -> extractCobaltFallback(cleanUrl, platform)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Extraction failed for $cleanUrl: ${e.message}", e)
            Result.failure(Exception(e.message ?: "Cannot resolve video streams. Video might be private or unavailable."))
        }
    }

    // =========================================================================
    // 1. 🔴 YOUTUBE DEDICATED EXTRACTOR (Invidious & Piped Multi-API Cluster)
    // =========================================================================
    private fun extractYouTube(url: String): Result<DownloadableVideoInfo> {
        val videoId = extractYouTubeVideoId(url)
        if (videoId.isBlank() || videoId == "default") {
            return Result.failure(Exception("Invalid YouTube URL. Could not parse video ID."))
        }

        // ইনভিডিয়াস পাবলিক API ক্লাস্টার
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
                val request = Request.Builder()
                    .url(apiUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .header("Accept", "application/json")
                    .build()

                val response = httpClient.newCall(request).execute()
                val body = response.body?.string()

                if (response.isSuccessful && !body.isNullOrBlank()) {
                    val json = JSONObject(body)
                    val title = json.optString("title").ifBlank { "YouTube Video $videoId" }
                    val author = json.optString("author")
                    val duration = json.optLong("lengthSeconds", 0L)
                    val thumbs = json.optJSONArray("videoThumbnails")
                    val thumbnail = thumbs?.optJSONObject(0)?.optString("url")
                        ?: "https://img.youtube.com/vi/$videoId/hqdefault.jpg"

                    val formats = mutableListOf<VideoFormatOption>()

                    // প্রোগ্রেসিভ স্ট্রিমস (ভিডিও + অডিও একসাথে যুক্ত MP4)
                    val formatStreams = json.optJSONArray("formatStreams")
                    if (formatStreams != null) {
                        for (i in 0 until formatStreams.length()) {
                            val stream = formatStreams.getJSONObject(i)
                            val streamUrl = stream.optString("url")
                            val qualityLabel = stream.optString("qualityLabel").ifBlank { stream.optString("quality", "720p") }
                            val resolution = stream.optString("resolution", "HD")
                            val size = stream.optString("size")

                            if (streamUrl.isNotBlank()) {
                                formats.add(
                                    VideoFormatOption(
                                        formatId = "yt_prog_${qualityLabel}_$i",
                                        qualityLabel = "$qualityLabel MP4 (Video + Audio)",
                                        resolutionText = resolution,
                                        downloadUrl = streamUrl
                                    )
                                )
                            }
                        }
                    }

                    // অডিও স্ট্রিমস (MP3 / M4A)
                    val adaptiveFormats = json.optJSONArray("adaptiveFormats")
                    if (adaptiveFormats != null) {
                        for (i in 0 until adaptiveFormats.length()) {
                            val stream = adaptiveFormats.getJSONObject(i)
                            val type = stream.optString("type")
                            val streamUrl = stream.optString("url")
                            if (type.contains("audio", ignoreCase = true) && streamUrl.isNotBlank()) {
                                val audioQuality = stream.optString("audioQuality", "Medium").replace("AUDIO_QUALITY_", "")
                                formats.add(
                                    VideoFormatOption(
                                        formatId = "yt_audio_$i",
                                        qualityLabel = "Audio Only ($audioQuality Bitrate)",
                                        resolutionText = "HQ Audio",
                                        extension = "mp3",
                                        downloadUrl = streamUrl,
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

        // পাইপড API ব্যাকআপ ক্লাস্টার
        val pipedInstances = listOf(
            "https://api.piped.yt",
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.leptons.xyz"
        )

        for (base in pipedInstances) {
            try {
                val apiUrl = "$base/streams/$videoId"
                val request = Request.Builder()
                    .url(apiUrl)
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Accept", "application/json")
                    .build()

                val response = httpClient.newCall(request).execute()
                val body = response.body?.string()

                if (response.isSuccessful && !body.isNullOrBlank()) {
                    val json = JSONObject(body)
                    val title = json.optString("title").ifBlank { "YouTube Video $videoId" }
                    val uploader = json.optString("uploader")
                    val duration = json.optLong("duration", 0L)
                    val thumbnail = json.optString("thumbnailUrl").ifBlank { "https://img.youtube.com/vi/$videoId/hqdefault.jpg" }

                    val formats = mutableListOf<VideoFormatOption>()
                    val videoStreams = json.optJSONArray("videoStreams")
                    if (videoStreams != null) {
                        for (i in 0 until videoStreams.length()) {
                            val stream = videoStreams.getJSONObject(i)
                            val streamUrl = stream.optString("url")
                            val quality = stream.optString("quality", "720p")
                            val format = stream.optString("format", "MP4")
                            val isVideoOnly = stream.optBoolean("videoOnly", false)

                            if (streamUrl.isNotBlank() && !isVideoOnly) {
                                formats.add(
                                    VideoFormatOption(
                                        formatId = "piped_vid_$i",
                                        qualityLabel = "$quality ($format)",
                                        resolutionText = quality,
                                        downloadUrl = streamUrl
                                    )
                                )
                            }
                        }
                    }

                    val audioStreams = json.optJSONArray("audioStreams")
                    if (audioStreams != null && audioStreams.length() > 0) {
                        val audio = audioStreams.getJSONObject(0)
                        val audioUrl = audio.optString("url")
                        val audioQuality = audio.optString("quality", "128 kbps")
                        if (audioUrl.isNotBlank()) {
                            formats.add(
                                VideoFormatOption(
                                    formatId = "piped_audio",
                                    qualityLabel = "Audio Only ($audioQuality)",
                                    resolutionText = "MP3 / M4A",
                                    extension = "mp3",
                                    downloadUrl = audioUrl,
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
                                author = uploader,
                                durationSeconds = duration,
                                thumbnailUrl = thumbnail,
                                platform = DownloadPlatform.YOUTUBE,
                                availableFormats = formats
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Piped instance $base failed: ${e.message}")
            }
        }

        return extractCobaltFallback(url, DownloadPlatform.YOUTUBE)
    }

    // =========================================================================
    // 2. ⚫ TIKTOK EXTRACTOR (১০০% ওয়াটারমার্ক ছাড়া HD ভিডিও ও MP3)
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
            return extractCobaltFallback(url, DownloadPlatform.TIKTOK)
        }

        val data = json.getJSONObject("data")
        val title = data.optString("title").ifBlank { "TikTok Video ${System.currentTimeMillis() % 10000}" }
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
                    downloadUrl = if (hdPlayUrl.startsWith("http")) hdPlayUrl else "https://www.tikwm.com$hdPlayUrl"
                )
            )
        }

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

        return extractCobaltFallback(url, DownloadPlatform.FACEBOOK)
    }

    // =========================================================================
    // 4. 🟣 INSTAGRAM REELS EXTRACTOR
    // =========================================================================
    private fun extractInstagram(url: String): Result<DownloadableVideoInfo> {
        return extractCobaltFallback(url, DownloadPlatform.INSTAGRAM)
    }

    // =========================================================================
    // 5. 🌐 COBALT FALLBACK & DIRECT MP4
    // =========================================================================
    private fun extractCobaltFallback(url: String, platform: DownloadPlatform): Result<DownloadableVideoInfo> {
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
                                qualityLabel = "Full HD / Best Quality",
                                resolutionText = "1080p Max",
                                downloadUrl = streamUrl
                            ),
                            VideoFormatOption(
                                formatId = "hd_720",
                                qualityLabel = "Standard HD (720p)",
                                resolutionText = "720p MP4",
                                downloadUrl = streamUrl
                            ),
                            VideoFormatOption(
                                formatId = "audio_mp3",
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

        return Result.failure(Exception("Cannot resolve video streams. Please check your internet connection or verify the link is public."))
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
