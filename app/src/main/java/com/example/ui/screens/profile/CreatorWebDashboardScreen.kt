@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.profile

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

private val DarkBg = Color(0xFF090C13)
private val ActionGreen = Color(0xFF00E676)
private val CardBg = Color(0xFF131722)
private val BorderColor = Color(0xFF222838)

/**
 * 🌐 ক্রিয়েটর স্টুডিও ওয়েব অ্যানালিটিক্স ড্যাশবোর্ড স্ক্রিন
 * (পার্মানেন্ট লগইন কুকি ও ৩-ডট মেনু সহ)
 */
@Composable
fun CreatorWebDashboardScreen(
    pageId: Int,
    studioUrl: String,
    onSwitchToPersonalProfile: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showTopMenu by remember { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isLoadingPage by remember { mutableStateOf(true) }

    // ব্যাক প্রেস করলে ওয়েব হিস্টোরির পেছনে যাবে, না থাকলে অ্যাপে ব্যাক হবে
    BackHandler {
        if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        } else {
            onBackClick()
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = DarkBg,
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
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Column {
                            Text(
                                text = "Creator Studio",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Live Analytics & Pipeline",
                                color = ActionGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // 🎯 ৩-ডট (⋮) মেনু
                    Box {
                        IconButton(onClick = { showTopMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = Color.White
                            )
                        }

                        DropdownMenu(
                            expanded = showTopMenu,
                            onDismissRequest = { showTopMenu = false },
                            modifier = Modifier
                                .background(CardBg)
                                .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                        ) {
                            // ১. রিফ্রেশ অপশন
                            DropdownMenuItem(
                                text = { Text("Refresh Dashboard", color = Color.White, fontSize = 13.5.sp) },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = ActionGreen) },
                                onClick = {
                                    showTopMenu = false
                                    webViewInstance?.reload()
                                }
                            )

                            HorizontalDivider(color = BorderColor, thickness = 0.6.dp)

                            // ২. 🎯 পার্সোনাল প্রোফাইলে সুইচ অপশন
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        text = "Switch to Personal Profile", 
                                        color = Color(0xFFFF5252), 
                                        fontSize = 13.5.sp, 
                                        fontWeight = FontWeight.Bold 
                                    ) 
                                },
                                leadingIcon = { 
                                    Icon(
                                        imageVector = Icons.Default.SwitchAccount, 
                                        contentDescription = null, 
                                        tint = Color(0xFFFF5252) 
                                    ) 
                                },
                                onClick = {
                                    showTopMenu = false
                                    onSwitchToPersonalProfile()
                                }
                            )
                        }
                    }
                }
            }
        },
        containerColor = DarkBg
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DarkBg)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        webViewInstance = this
                        setupStudioWebView(this)

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoadingPage = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoadingPage = false
                                CookieManager.getInstance().flush() // 🍪 কুকি পার্মানেন্টলি হার্ড ড্রাইভে সেভ রাখা
                            }
                        }

                        webChromeClient = WebChromeClient()
                        loadUrl(studioUrl)
                    }
                }
            )

            // পেজ লোডিং প্রগ্রেস বার
            if (isLoadingPage) {
                LinearProgressIndicator(
                    color = ActionGreen,
                    trackColor = Color(0xFF1E2838),
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
 * 🛠️ ব্রাউজার সেশন ও কুকি আজীবন মনে রাখার সেটিংস
 */
@SuppressLint("SetJavaScriptEnabled")
private fun setupStudioWebView(webView: WebView) {
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
    }
    webView.setBackgroundColor(android.graphics.Color.parseColor("#090C13"))
}
