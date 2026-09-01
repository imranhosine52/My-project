package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.*
import com.example.data.model.DownloadPlatform
import com.example.data.model.DownloadableVideoInfo
import com.example.data.model.VideoFormatOption
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.regex.Pattern
import kotlin.coroutines.resume

/**
 * 🕵️‍♂️ WebViewMediaSniffer (1DM / IDM Style Media Stream Interceptor)
 * ব্যাকগ্রাউন্ডে ব্রাউজার চালিয়ে নেটওয়ার্ক ট্র্যাফিক থেকে সরাসরি অরিজিনাল ভিডিও ও অডিও স্ট্রিম ক্যাপচার করে।
 */
object WebViewMediaSniffer {
    private const val TAG = "WebViewSniffer"
    private const val SNIFF_TIMEOUT_MS = 10000L // সর্বোচ্চ ১০ সেকেন্ড অপেক্ষা করবে

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun sniffMedia(
        context: Context,
        targetUrl: String,
        platform: DownloadPlatform
    ): Result<DownloadableVideoInfo> = suspendCancellableCoroutine { continuation ->

        val mainHandler = Handler(Looper.getMainLooper())

        mainHandler.post {
            var isResumed = false
            var webView: WebView? = null

            val capturedFormats = mutableListOf<VideoFormatOption>()
            val seenUrls = mutableSetOf<String>()
            var pageTitle = "Captured Video"
            var thumbnailUrl: String? = null

            // রেজোলিউশন বা কোয়ালিটি লেবেল নির্ধারণের হেলপার
            fun addMediaUrl(rawUrl: String, isAudio: Boolean = false) {
                if (seenUrls.contains(rawUrl) || rawUrl.isBlank()) return
                seenUrls.add(rawUrl)

                val label = when {
                    isAudio || rawUrl.contains("mime=audio") -> "Audio Only (MP3)"
                    rawUrl.contains("1080") -> "1080p Full HD"
                    rawUrl.contains("720") -> "720p HD MP4"
                    rawUrl.contains("480") -> "480p SD MP4"
                    rawUrl.contains("360") -> "360p MP4"
                    else -> if (capturedFormats.isEmpty()) "High Quality MP4" else "Standard MP4"
                }

                val formatOption = VideoFormatOption(
                    formatId = "sniff_${capturedFormats.size + 1}",
                    qualityLabel = label,
                    resolutionText = if (isAudio) "Audio" else "HD",
                    ext = if (isAudio) "mp3" else "mp4",
                    downloadUrl = rawUrl,
                    isAudioOnly = isAudio
                )

                capturedFormats.add(formatOption)
            }

            fun finishSuccess() {
                if (isResumed) return
                isResumed = true

                val resultInfo = DownloadableVideoInfo(
                    sourceUrl = targetUrl,
                    title = pageTitle,
                    thumbnailUrl = thumbnailUrl,
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

            // টাইমআউট হ্যান্ডলার
            val timeoutRunnable = Runnable {
                if (!isResumed) {
                    if (capturedFormats.isNotEmpty()) {
                        finishSuccess()
                    } else {
                        finishError("Could not detect playable video streams on this page.")
                    }
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
                            if (!title.isNullOrBlank() && !title.startsWith("http")) {
                                pageTitle = title.replace(" - YouTube", "").replace(" | Facebook", "").trim()
                            }
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)

                            // 🔍 DOM থেকে HTML5 Video/Audio সোর্স এবং মেটাডাটা এক্সট্র্যাক্ট করার JS
                            val jsExtractor = """
                                (function() {
                                    var urls = [];
                                    document.querySelectorAll('video, audio, source').forEach(function(el) {
                                        if (el.src && el.src.startsWith('http')) urls.push(el.src);
                                        if (el.currentSrc && el.currentSrc.startsWith('http')) urls.push(el.currentSrc);
                                    });
                                    var ogImg = document.querySelector('meta[property="og:image"]');
                                    var thumb = ogImg ? ogImg.content : '';
                                    return JSON.stringify({urls: Array.from(new Set(urls)), thumb: thumb});
                                })();
                            """.trimIndent()

                            view?.evaluateJavascript(jsExtractor) { result ->
                                try {
                                    val clean = result?.removeSurrounding("\"")?.replace("\\\"", "\"") ?: ""
                                    val pattern = Pattern.compile("https?://[^\\s\",]+")
                                    val matcher = pattern.matcher(clean)
                                    while (matcher.find()) {
                                        val mediaUrl = matcher.group()
                                        if (isMediaUrl(mediaUrl)) {
                                            addMediaUrl(mediaUrl)
                                        }
                                    }
                                } catch (_: Exception) {}

                                // যদি ভিডিও লিঙ্ক পাওয়া যায়, সাথে সাথে রিটার্ন করা
                                if (capturedFormats.isNotEmpty() && !isResumed) {
                                    mainHandler.removeCallbacks(timeoutRunnable)
                                    finishSuccess()
                                }
                            }
                        }

                        // 🎯 1DM মূল ট্রিক: নেটওয়ার্ক রিকোয়েস্টের ভেতর থেকে ডিরেক্ট মিডিয়া লিঙ্ক ছিনতাই করা
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val reqUrl = request?.url?.toString() ?: return null

                            if (isMediaUrl(reqUrl)) {
                                Log.i(TAG, "🎯 1DM Intercepted Media Stream: $reqUrl")
                                val isAudio = reqUrl.contains("mime=audio") || reqUrl.contains(".mp3")
                                mainHandler.post {
                                    addMediaUrl(reqUrl, isAudio)
                                    if (capturedFormats.isNotEmpty()) {
                                        // ২ সেকেন্ড অপেক্ষা করে অতিরিক্ত কোয়ালিটি থাকলে নিয়ে নেবে
                                        mainHandler.postDelayed({
                                            if (!isResumed) {
                                                mainHandler.removeCallbacks(timeoutRunnable)
                                                finishSuccess()
                                            }
                                        }, 1500L)
                                    }
                                }
                            }
                            return super.shouldInterceptRequest(view, request)
                        }
                    }

                    loadUrl(targetUrl)
                }
            } catch (e: Exception) {
                finishError("Failed to initialize background sniffer: ${e.message}")
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

    /**
     * লিঙ্কটি অডিও/ভিডিও মিডিয়া স্ট্রিম কি না তা যাচাই করার ফিল্টার
     */
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
}
