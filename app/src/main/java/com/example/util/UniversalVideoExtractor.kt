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
 * 1DM In-App WebView Sniffer এবং ডিরেক্ট ইঞ্জিনের সমন্বয়ে তৈরি হাইব্রিড এক্সট্র্যাক্টর।
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

    /**
     * প্রধান এক্সট্র্যাকশন মেথড (1DM Sniffer + Direct Fallback)
     */
    suspend fun extractVideoInfo(context: Context, rawUrl: String): Result<DownloadableVideoInfo> = withContext(Dispatchers.IO) {
        val cleanUrl = rawUrl.trim()
        val platform = detectPlatform(cleanUrl)

        try {
            // ⚫ ১. টিকটক হলে সরাসরি হাই-স্পিড নো-ওয়াটারমার্ক ইঞ্জিন
            if (platform == DownloadPlatform.TIKTOK) {
                val tikTokResult = extractTikTokDirect(cleanUrl)
                if (tikTokResult.isSuccess) return@withContext tikTokResult
            }

            // 🟣 ২. ইনস্টাগ্রাম হলে সরাসরি ডিরেক্ট CDN ট্রাই করবে
            if (platform == DownloadPlatform.INSTAGRAM) {
                val instaDirect = extractInstagramDirect(cleanUrl)
                if (instaDirect.isSuccess) return@withContext instaDirect
            }

            // 🔵 ৩. ফেসবুক হলে ডিরেক্ট মেটাডাটা ট্রাই করবে
            if (platform == DownloadPlatform.FACEBOOK) {
                val fbDirect = extractFacebookDirect(cleanUrl)
                if (fbDirect.isSuccess) return@withContext fbDirect
            }

            // 🕵️‍♂️ ৪. YouTube এবং অন্যান্য সব সাইটের জন্য 1DM In-App WebView Sniffer ইঞ্জিন
            Log.i(TAG, "🚀 Launching 1DM In-App WebView Sniffer for: $cleanUrl")
            val sniffResult = WebViewMediaSniffer.sniffMedia(context, cleanUrl, platform)
            if (sniffResult.isSuccess && sniffResult.getOrNull()?.availableFormats?.isNotEmpty() == true) {
                return@withContext sniffResult
            }

            return@withContext Result.failure(Exception("Could not detect video stream. Please verify the link is public."))

        } catch (e: Exception) {
            Log.e(TAG, "Extraction failed: ${e.message}", e)
            Result.failure(Exception(e.message ?: "Failed to resolve video stream."))
        }
    }

    // =========================================================================
    // ⚫ TIKTOK DIRECT (১০০% নো ওয়াটারমার্ক)
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
                        qualityLabel = "Audio Only (MP3 Music)",
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
    // 🟣 INSTAGRAM DIRECT
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
    // 🔵 FACEBOOK DIRECT
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
        val pattern = Pattern.compile(patternString)
        val matcher = pattern.matcher(text)
        return if (matcher.find()) matcher.group(1) else null
    }
}
