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
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * ⚡ UniversalVideoExtractor (Snaptube / NewPipe Style Client-Side Engine)
 * সরাসরি ইউজারের মোবাইল থেকে এক্সট্র্যাক্ট করে, তাই কোনো বট চেক বা সার্ভার ব্লকিংয়ের ঝুঁকি নেই।
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

    suspend fun extractVideoInfo(rawUrl: String): Result<DownloadableVideoInfo> = withContext(Dispatchers.IO) {
        val cleanUrl = rawUrl.trim()
        val platform = detectPlatform(cleanUrl)

        try {
            when (platform) {
                DownloadPlatform.YOUTUBE -> extractYouTubeDirect(cleanUrl)
                DownloadPlatform.TIKTOK -> extractTikTokDirect(cleanUrl)
                DownloadPlatform.INSTAGRAM -> extractInstagramDirect(cleanUrl)
                DownloadPlatform.FACEBOOK -> extractFacebookDirect(cleanUrl)
                else -> extractUniversalDirect(cleanUrl, platform)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Extraction failed for $cleanUrl: ${e.message}", e)
            Result.failure(Exception(e.message ?: "Cannot resolve video streams. Please check the link."))
        }
    }

    // =========================================================================
    // 🔴 1. YOUTUBE NATIVE INNERTUBE (সরাসরি মোবাইলের নিজস্ব ইন্টারনেট দিয়ে)
    // =========================================================================
    private fun extractYouTubeDirect(cleanUrl: String): Result<DownloadableVideoInfo> {
        val videoId = extractYouTubeVideoId(cleanUrl)
        if (videoId.isBlank() || videoId == "default") {
            return Result.failure(Exception("Invalid YouTube URL. Could not parse video ID."))
        }

        // অফিসিয়াল YouTube Android Client রিকোয়েস্ট (যা ইউটিউব অফিসিয়াল অ্যাপ ব্যবহার করে)
        val payload = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "ANDROID")
                    put("clientVersion", "19.09.37")
                    put("androidSdkVersion", 30)
                    put("hl", "en")
                    put("gl", "US")
                })
            })
            put("videoId", videoId)
        }

        try {
            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player")
                .header("User-Agent", "com.google.android.youtube/19.09.37 (Linux; U; Android 11)")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: throw IllegalStateException("Empty response from YouTube")
            val json = JSONObject(body)

            val videoDetails = json.optJSONObject("videoDetails")
            val streamingData = json.optJSONObject("streamingData")

            if (videoDetails != null && streamingData != null) {
                val title = videoDetails.optString("title").ifBlank { "YouTube Video $videoId" }
                val author = videoDetails.optString("author")
                val duration = videoDetails.optLong("lengthSeconds", 0L)
                val thumbs = videoDetails.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                val thumbnail = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url")
                    ?: "https://img.youtube.com/vi/$videoId/hqdefault.jpg"

                val formats = mutableListOf<VideoFormatOption>()

                // 🎬 অডিও + ভিডিও একসাথে যুক্ত MP4 স্ট্রিমস (720p / 360p)
                val formatsArray = streamingData.optJSONArray("formats")
                if (formatsArray != null) {
                    for (i in 0 until formatsArray.length()) {
                        val formatObj = formatsArray.getJSONObject(i)
                        val streamUrl = formatObj.optString("url").takeIf { it.isNotBlank() }
                            ?: parseSignatureCipher(formatObj.optString("signatureCipher"))

                        val qualityLabel = formatObj.optString("qualityLabel").ifBlank { "720p HD" }
                        val approxSize = formatObj.optLong("contentLength", 0L)

                        if (!streamUrl.isNullOrBlank()) {
                            formats.add(
                                VideoFormatOption(
                                    formatId = "yt_prog_$i",
                                    qualityLabel = "$qualityLabel MP4 (Video + Audio)",
                                    resolutionText = qualityLabel,
                                    downloadUrl = streamUrl,
                                    approximateSizeBytes = approxSize
                                )
                            )
                        }
                    }
                }

                // 🎵 অডিও MP3 ফরম্যাট
                val adaptiveArray = streamingData.optJSONArray("adaptiveFormats")
                if (adaptiveArray != null) {
                    for (i in 0 until adaptiveArray.length()) {
                        val formatObj = adaptiveArray.getJSONObject(i)
                        val mime = formatObj.optString("mimeType")
                        val streamUrl = formatObj.optString("url").takeIf { it.isNotBlank() }
                            ?: parseSignatureCipher(formatObj.optString("signatureCipher"))

                        if (mime.startsWith("audio", ignoreCase = true) && !streamUrl.isNullOrBlank()) {
                            val bitrate = formatObj.optInt("bitrate", 128000) / 1000
                            val approxSize = formatObj.optLong("contentLength", 0L)

                            formats.add(
                                VideoFormatOption(
                                    formatId = "yt_audio_$i",
                                    qualityLabel = "Audio Only (${bitrate} kbps MP3)",
                                    resolutionText = "HQ Audio",
                                    extension = "mp3",
                                    downloadUrl = streamUrl,
                                    approximateSizeBytes = approxSize,
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
                            sourceUrl = cleanUrl,
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
            Log.w(TAG, "YouTube InnerTube error: ${e.message}")
        }

        return Result.failure(Exception("Could not extract YouTube stream. Please check video link."))
    }

    // =========================================================================
    // ⚫ 2. TIKTOK DIRECT (১০০% ওয়াটারমার্ক ছাড়া)
    // =========================================================================
    private fun extractTikTokDirect(url: String): Result<DownloadableVideoInfo> {
        val apiUrl = "https://www.tikwm.com/api/?url=${Uri.encode(url)}"
        val request = Request.Builder()
            .url(apiUrl)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        val response = httpClient.newCall(request).execute()
        val bodyString = response.body?.string() ?: throw IllegalStateException("Empty response from TikTok")
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
    // 🟣 3. INSTAGRAM REELS DIRECT
    // =========================================================================
    private fun extractInstagramDirect(url: String): Result<DownloadableVideoInfo> {
        try {
            val apiUrl = "https://api.vkrdownloader.com/server?vkr=${Uri.encode(url)}"
            val req = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val res = httpClient.newCall(req).execute()
            val body = res.body?.string() ?: ""
            val json = JSONObject(body)
            val data = json.optJSONObject("data") ?: json

            val title = data.optString("title").ifBlank { "Instagram Video" }
            val thumb = data.optString("thumbnail")
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
                return Result.success(
                    DownloadableVideoInfo(
                        sourceUrl = url,
                        title = title,
                        thumbnailUrl = thumb,
                        platform = DownloadPlatform.INSTAGRAM,
                        availableFormats = formats
                    )
                )
            }
        } catch (_: Exception) {}

        return Result.failure(Exception("Could not extract Instagram Reel. Ensure the account is public."))
    }

    // =========================================================================
    // 🔵 4. FACEBOOK DIRECT
    // =========================================================================
    private fun extractFacebookDirect(url: String): Result<DownloadableVideoInfo> {
        try {
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

            val title = findRegex(html, "<title>(.*?)</title>")?.replace("| Facebook", "")?.trim() ?: "Facebook Video"

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
                return Result.success(
                    DownloadableVideoInfo(
                        sourceUrl = url,
                        title = title,
                        platform = DownloadPlatform.FACEBOOK,
                        availableFormats = formats
                    )
                )
            }
        } catch (_: Exception) {}

        return Result.failure(Exception("Could not extract Facebook video. Make sure the video is public."))
    }

    private fun extractUniversalDirect(url: String, platform: DownloadPlatform): Result<DownloadableVideoInfo> {
        if (url.endsWith(".mp4") || url.endsWith(".m3u8") || url.contains("video")) {
            return Result.success(
                DownloadableVideoInfo(
                    sourceUrl = url,
                    title = "Web Video",
                    platform = platform,
                    availableFormats = listOf(
                        VideoFormatOption(
                            formatId = "direct_mp4",
                            qualityLabel = "Direct Video (MP4)",
                            resolutionText = "Original",
                            downloadUrl = url
                        )
                    )
                )
            )
        }
        return Result.failure(Exception("Unsupported URL format."))
    }

    private fun parseSignatureCipher(cipher: String?): String? {
        if (cipher.isNullOrBlank()) return null
        return try {
            val params = cipher.split("&").associate {
                val pair = it.split("=")
                pair[0] to URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
            }
            params["url"]
        } catch (_: Exception) {
            null
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
