package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.*
import com.example.data.model.DownloadPlatform
import com.example.data.model.DownloadableVideoInfo
import com.example.data.model.VideoFormatOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.coroutines.resume

/**
 * 🕵️‍♂️ WebViewMediaSniffer (1DM Style Full-Stream Interceptor)
 * রেঞ্জ প্যারামিটার ক্লিন করে সম্পূর্ণ ভিডিও ফাইল ইন্টারসেপ্ট করে।
 */
object WebViewMediaSniffer {
    private const val TAG = "WebViewSniffer"
    private const val SNIFF_TIMEOUT_MS = 12000L

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun sniffMedia(
        context: Context,
        targetUrl: String,
        platform: DownloadPlatform
    ): Result<DownloadableVideoInfo> = withContext(Dispatchers.IO) {

        var videoTitle = "Video_${System.currentTimeMillis() % 10000}"
        var videoThumbnail: String? = null
        var videoAuthor: String? = null

        // 🎬 ইউটিউব মেটাডাটা ও থাম্বনেইল আনা
        if (platform == DownloadPlatform.YOUTUBE) {
            val videoId = extractYtId(targetUrl)
            if (videoId.isNotBlank()) {
                videoThumbnail = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
                try {
                    val oembedUrl = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json"
                    val oembedRes = probeClient.newCall(Request.Builder().url(oembedUrl).build()).execute()
                    if (oembedRes.isSuccessful) {
                        val oembedJson = JSONObject(oembedRes.body?.string() ?: "{}")
                        videoTitle = oembedJson.optString("title", videoTitle)
                        videoAuthor = oembedJson.optString("author_name")
                    }
                } catch (_: Exception) {}
            }
        }

        suspendCancellableCoroutine { continuation ->
            val mainHandler = Handler(Looper.getMainLooper())

            mainHandler.post {
                var isResumed = false
                var webView: WebView? = null
                val capturedFormats = mutableListOf<VideoFormatOption>()
                val seenUrls = mutableSetOf<String>()

                // 🎯 রেঞ্জ ও ফ্র্যাগমেন্ট ক্লিন করে ফুল স্ট্রিম তৈরি করা
                fun cleanStreamUrl(rawUrl: String): String {
                    var url = rawUrl.replace("\\u0026", "&").replace("\\/", "/")
                    if (url.contains("googlevideo.com/videoplayback")) {
                        url = url.replace(Regex("&range=[0-9]+-[0-9]+"), "")
                            .replace(Regex("&rn=[0-9]+"), "")
                            .replace(Regex("&rbuf=[0-9]+"), "")
                    }
                    return url
                }

                fun addMediaUrl(rawUrl: String, isAudio: Boolean = false) {
                    val cleanedUrl = cleanStreamUrl(rawUrl)
                    if (seenUrls.contains(cleanedUrl) || cleanedUrl.isBlank()) return
                    seenUrls.add(cleanedUrl)

                    val label = when {
                        isAudio || cleanedUrl.contains("mime=audio") -> "Audio Only (MP3)"
                        cleanedUrl.contains("itag=22") || cleanedUrl.contains("720") -> "720p HD MP4"
                        cleanedUrl.contains("itag=18") || cleanedUrl.contains("360") -> "360p Standard MP4"
                        cleanedUrl.contains("1080") -> "1080p Full HD"
                        else -> if (capturedFormats.isEmpty()) "High Quality MP4 (Full Video)" else "Standard MP4"
                    }

                    val formatOption = VideoFormatOption(
                        formatId = "sniff_${capturedFormats.size + 1}",
                        qualityLabel = label,
                        resolutionText = if (isAudio) "Audio" else "Full Video",
                        extension = if (isAudio) "mp3" else "mp4",
                        downloadUrl = cleanedUrl,
                        approximateSizeBytes = 0L,
                        isAudioOnly = isAudio
                    )

                    capturedFormats.add(formatOption)
                }

                fun finishSuccess() {
                    if (isResumed) return
                    isResumed = true

                    val resultInfo = DownloadableVideoInfo(
                        sourceUrl = targetUrl,
                        title = videoTitle,
                        author = videoAuthor,
                        thumbnailUrl = videoThumbnail,
                        platform = platform,
                        availableFormats = capturedFormats.toList()
                    )

                    try {
                        webView?.stopLoading()
                        webView?.destroy()
                    } catch (_: Exception) {}

                    continuation.resume(Result.success(resultInfo))
                }

                fun finishError(msg: String) {
                    if (isResumed) return
                    isResumed = true

                    try {
                        webView?.stopLoading()
                        webView?.destroy()
                    } catch (_: Exception) {}

                    continuation.resume(Result.failure(Exception(msg)))
                }

                val timeoutRunnable = Runnable {
                    if (!isResumed) {
                        if (capturedFormats.isNotEmpty()) finishSuccess()
                        else finishError("Could not detect playable video stream.")
                    }
                }
                mainHandler.postDelayed(timeoutRunnable, SNIFF_TIMEOUT_MS)

                try {
                    webView = WebView(context.applicationContext).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                        }

                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        webChromeClient = object : WebChromeClient() {
                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                if (!title.isNullOrBlank() && !title.startsWith("http") && videoTitle.startsWith("Video_")) {
                                    videoTitle = title.replace(" - YouTube", "").replace(" | Facebook", "").trim()
                                }
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                view?.evaluateJavascript("""
                                    (function() {
                                        var urls = [];
                                        document.querySelectorAll('video, audio, source').forEach(function(el) {
                                            if (el.src && el.src.startsWith('http')) urls.push(el.src);
                                            if (el.currentSrc && el.currentSrc.startsWith('http')) urls.push(el.currentSrc);
                                        });
                                        return JSON.stringify(Array.from(new Set(urls)));
                                    })();
                                """.trimIndent()) { result ->
                                    try {
                                        val clean = result?.removeSurrounding("\"")?.replace("\\\"", "\"") ?: ""
                                        val matcher = Pattern.compile("https?://[^\\s\",]+").matcher(clean)
                                        while (matcher.find()) {
                                            val mediaUrl = matcher.group()
                                            if (isMediaUrl(mediaUrl)) addMediaUrl(mediaUrl)
                                        }
                                    } catch (_: Exception) {}

                                    if (capturedFormats.isNotEmpty() && !isResumed) {
                                        mainHandler.removeCallbacks(timeoutRunnable)
                                        finishSuccess()
                                    }
                                }
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val reqUrl = request?.url?.toString() ?: return null

                                if (isMediaUrl(reqUrl)) {
                                    val isAudio = reqUrl.contains("mime=audio") || reqUrl.contains(".mp3")
                                    mainHandler.post {
                                        addMediaUrl(reqUrl, isAudio)
                                        if (capturedFormats.isNotEmpty()) {
                                            mainHandler.postDelayed({
                                                if (!isResumed) {
                                                    mainHandler.removeCallbacks(timeoutRunnable)
                                                    finishSuccess()
                                                }
                                            }, 1200L)
                                        }
                                    }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                        }

                        loadUrl(targetUrl)
                    }
                } catch (e: Exception) {
                    finishError("Sniffer error: ${e.message}")
                }

                continuation.invokeOnCancellation {
                    mainHandler.post {
                        try {
                            webView?.stopLoading()
                            webView?.destroy()
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    private fun isMediaUrl(url: String): Boolean {
        val lower = url.lowercase()
        return (lower.contains("googlevideo.com/videoplayback") ||
                lower.contains(".mp4") ||
                lower.contains(".m4a") ||
                lower.contains(".webm") ||
                lower.contains(".mp3") ||
                lower.contains("mime=video") ||
                lower.contains("mime=audio") ||
                lower.contains("fbcdn.net") ||
                lower.contains("cdninstagram.com") ||
                lower.contains("tiktokcdn.com")) &&
                !lower.contains(".js") &&
                !lower.contains(".css") &&
                !lower.contains(".html")
    }

    private fun extractYtId(url: String): String {
        return try {
            val uri = Uri.parse(url)
            val v = uri.getQueryParameter("v")
            if (!v.isNullOrBlank()) return v
            if (url.contains("youtu.be/")) url.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
            else if (url.contains("/shorts/")) url.substringAfter("/shorts/").substringBefore("?").substringBefore("&")
            else ""
        } catch (_: Exception) {
            ""
        }
    }
}
