@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.viewmodel.DramaFlixViewModel

private val PureBlackBg = Color(0xFF06080E)
private val GoldAccent = Color(0xFFFFB300)

/**
 * 👑 অল-ইন-ওয়ান ভিআইপি সাবস্ক্রিপশন, চেকআউট ও ইনভয়েস ওয়েব স্ক্রিন
 * (অটো-লগইন, ডিপ-লিংক হ্যান্ডলার ও ব্যাক-প্রেস নেভিগেশন সহ)
 */
@Composable
fun VipScreen(
    viewModel: DramaFlixViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToProfile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val authState by viewModel.authUiState.collectAsStateWithLifecycle()

    val currentUserId = remember(authState.userProfile) {
        authState.userProfile?.id?.filter { it.isDigit() } ?: "0"
    }

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isLoadingPage by remember { mutableStateOf(true) }

    // 🎯 ওয়েবসাইটের নতুন পাথ (User ID সহ)
    val vipWebUrl = remember(currentUserId) {
        "https://playdramaflix.com/app/vip/index.php?user_id=$currentUserId"
    }

    // ব্যাক প্রেস করলে ওয়েব হিস্টোরির আগের পেজে যাবে (যেমন: Checkout থেকে Plans-এ)
    BackHandler {
        if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        } else {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = PureBlackBg,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = {
                            if (webViewInstance?.canGoBack() == true) {
                                webViewInstance?.goBack()
                            } else {
                                onNavigateBack()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Text(
                            text = "VIP Membership",
                            color = Color.White,
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = { webViewInstance?.reload() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = GoldAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        },
        containerColor = PureBlackBg
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(PureBlackBg)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewInstance = this
                        setupVipWebView(this)

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoadingPage = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoadingPage = false
                                CookieManager.getInstance().flush()
                            }

                            // 🎯 ওয়েব থেকে পেমেন্ট সফল ডিপ-লিংক আসলে অ্যাপে ব্যাক নেওয়া
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val url = request?.url?.toString() ?: ""

                                if (url.startsWith("playdramaflix://vip-activated") || 
                                    url.startsWith("playdramaflix://payment-success")) {
                                    
                                    viewModel.refreshVipStatusAndProfile()
                                    Toast.makeText(context, "🎉 Congratulations! VIP Activated!", Toast.LENGTH_LONG).show()
                                    onNavigateBack()
                                    return true
                                }

                                if (url.startsWith("tel:") || url.startsWith("mailto:")) {
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    } catch (_: Exception) {}
                                    return true
                                }

                                return false
                            }
                        }

                        webChromeClient = WebChromeClient()
                        loadUrl(vipWebUrl)
                    }
                }
            )

            // লোডিং প্রগ্রেস বার
            if (isLoadingPage) {
                LinearProgressIndicator(
                    color = GoldAccent,
                    trackColor = Color(0xFF1E2536),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.TopCenter)
                )
            }
        }
    }
}

/**
 * 🛠️ ওয়েবভিউ কনফিগারেশন
 */
@SuppressLint("SetJavaScriptEnabled")
private fun setupVipWebView(webView: WebView) {
    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)
    cookieManager.setAcceptThirdPartyCookies(webView, true)

    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        cacheMode = WebSettings.LOAD_DEFAULT
        useWideViewPort = true
        loadWithOverviewMode = true
        setSupportZoom(false)
        mediaPlaybackRequiresUserGesture = false // ভিডিও অটো-প্লে সাপোর্ট
    }
    webView.setBackgroundColor(android.graphics.Color.parseColor("#06080E"))
}
