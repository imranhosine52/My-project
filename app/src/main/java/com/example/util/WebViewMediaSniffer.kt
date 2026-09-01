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
 * 🕵️‍♂️ WebViewMediaSniffer (1DM Style)
 * আসল টাইটেল, HD থাম্বনেইল, সাইজ ক্যালকুলেশন এবং ডিরেক্ট মিডিয়া ইন্টারসেপ্টর।
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

        // 🎬 ১. ইউটিউব/ভিডিওর আসল মেটাডাটা ও থাম্বনেইল ০.১ সেকেন্ডে ব্যাকগ্রাউন্ডে আনা
        var videoTitle = "Video ${System.currentTimeMillis() % 10000}"
        var videoThumbnail: String? = null
        var videoAuthor: String? = null

        if (platform == DownloadPlatform.YOUTUBE) {
            val videoId = extractYtId(targetUrl)
            if (videoId.isNotBlank()) {
                videoThumbnail = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
                try {
                    val oembedUrl = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json"
                    val oembedReq = Request.Builder().url(oembedUrl).build()
                    val oembedRes = probeClient.newCall(oembedReq).execute()
                    if (oembedRes.isSuccessful) {
                        val oembedJson = JSONObject(oembedRes.body?.string() ?: "{}")
                        videoTitle = oembedJson.optString("title", videoTitle)
                        videoAuthor = oembedJson.optString("author_name")
                    }
                } catch (_: Exception) {}
            }
        }

        // ২. মেইন থ্রেডে WebView চালিয়ে মিডিয়া ছিনতাই করা
        suspendCancellableCoroutine { continuation ->
            val mainHandler = Handler(Looper.getMainLooper())

            mainHandler.post {
                var isResumed = false
                var webView: WebView? = null
                val capturedFormats = mutableListOf<VideoFormatOption>()
                val seenUrls = mutableSetOf<String>()

                fun addMediaUrl(rawUrl: String, isAudio: Boolean = false, contentLength: Long = 0L) {
                    if (seenUrls.contains(rawUrl) || rawUrl.isBlank()) return
                    seenUrls.add(rawUrl)

                    val label = when {
                        isAudio || rawUrl.contains("mime=audio") -> "Audio Only (MP3)"
                        rawUrl.contains("itag=22") || rawUrl.contains("720") -> "720p HD MP4"
                        rawUrl.contains("itag=18") || rawUrl.contains("360") -> "360p Standard MP4"
                        rawUrl.contains("1080") -> "1080p Full HD"
                        else -> if (capturedFormats.isEmpty()) "High Quality MP4 (Video + Audio)" else "Standard Quality MP4"
                    }

                    val formatOption = VideoFormatOption(
                        formatId = "sniff_${capturedFormats.size + 1}",
                        qualityLabel = label,
                        resolutionText = if (isAudio) "Audio" else "HD Video",
                        extension = if (isAudio) "mp3" else "mp4",
                        downloadUrl = rawUrl,
                        approximateSizeBytes = contentLength,
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

                        // ✅ সঠিক কুকিজ কনফিগারেশন (Error Fixed)
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        webChromeClient = object : WebChromeClient() {
                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                if (!title.isNullOrBlank() && !title.startsWith("http") && videoTitle.startsWith("Video ")) {
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
                                    Log.i(TAG, "🎯 Intercepted Stream: $reqUrl")
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
