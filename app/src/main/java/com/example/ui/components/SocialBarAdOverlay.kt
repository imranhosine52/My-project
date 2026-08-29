package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ads.UnifiedAdManager

/**
 * 🚀 Adsterra Social Bar Overlay
 * Displays dynamic, high-engagement Adsterra Social Bar notifications & widgets in-app.
 * 
 * Key Features:
 * - Transparent overlay integration with hardware acceleration.
 * - In-App Browsing: Any link tapped inside the Social Bar opens strictly in the In-App Browser.
 * - Strict VIP Exemption: Completely unmounted and suppressed if isVip == true or ads_enabled == false.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SocialBarAdOverlay(
    isVip: Boolean,
    modifier: Modifier = Modifier
) {
    val adConfig by UnifiedAdManager.adConfigState.collectAsState()

    // 👑 Strict VIP & Master Switch Bypass: Zero footprint
    if (isVip || !adConfig.adsEnabled) {
        Spacer(modifier = Modifier.size(0.dp))
        return
    }

    val adsterra = adConfig.adsterra
    if (adsterra == null || !adsterra.enabled || !adsterra.socialBarEnabled) {
        Spacer(modifier = Modifier.size(0.dp))
        return
    }

    val socialBarCode = adsterra.effectiveSocialBarCode
    val socialBarUrl = adsterra.effectiveSocialBarUrl

    // If neither script code nor url is provided in remote config, provide smart template
    val htmlPayload = remember(socialBarCode, socialBarUrl) {
        when {
            !socialBarCode.isNullOrBlank() -> {
                buildHtmlForSocialBar(socialBarCode)
            }
            !socialBarUrl.isNullOrBlank() -> {
                val scriptTag = "<script type=\"text/javascript\" src=\"$socialBarUrl\"></script>"
                buildHtmlForSocialBar(scriptTag)
            }
            else -> {
                // If Adsterra is active but no custom script snippet added yet, use direct smartlink anchor
                val directLink = adsterra.effectiveDirectLink
                if (!directLink.isNullOrBlank()) {
                    buildHtmlForSocialBar("""
                        <script type="text/javascript">
                            // Adsterra Social Bar fallback trigger
                            console.log("Adsterra Social Bar active with Direct Link: $directLink");
                        </script>
                    """.trimIndent())
                } else {
                    null
                }
            }
        }
    }

    if (htmlPayload == null) {
        Spacer(modifier = Modifier.size(0.dp))
        return
    }

    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    setBackgroundColor(0x00000000) // Transparent
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        mediaPlaybackRequiresUserGesture = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val uri = request?.url ?: return false
                            val urlString = uri.toString()
                            val scheme = uri.scheme?.lowercase() ?: ""

                            if (scheme == "http" || scheme == "https") {
                                Log.i("SocialBar", "🌐 Social Bar ad clicked! Opening In-App Browser: $urlString")
                                UnifiedAdManager.openInAppBrowser(
                                    url = urlString,
                                    title = "Sponsored Offer"
                                )
                                return true
                            }
                            return false
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            errorCode: Int,
                            description: String?,
                            failingUrl: String?
                        ) {
                            Log.w("SocialBar", "Social Bar load notice: $description")
                        }
                    }

                    loadDataWithBaseURL(
                        "https://playdramaflix.com/",
                        htmlPayload,
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            },
            update = { webView ->
                webView.loadDataWithBaseURL(
                    "https://playdramaflix.com/",
                    htmlPayload,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        )
    }
}

/**
 * Builds responsive, transparent HTML container for Adsterra Social Bar scripts.
 */
private fun buildHtmlForSocialBar(scriptSnippet: String): String {
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
                * { box-sizing: border-box; }
                html, body {
                    margin: 0;
                    padding: 0;
                    background-color: transparent !important;
                    background: transparent !important;
                    overflow: visible;
                }
            </style>
        </head>
        <body>
            $scriptSnippet
        </body>
        </html>
    """.trimIndent()
}
