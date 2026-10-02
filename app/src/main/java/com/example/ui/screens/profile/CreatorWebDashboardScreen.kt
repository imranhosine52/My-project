@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.profile

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
 * (ইমার্সিভ স্ট্যাটাস বার, টান দিলে ব্রাউজারের মতো রিফ্রেশ ও ফাইল পিকার সহ)
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
    
    // 🔄 ব্রাউজারের মতো Pull-to-Refresh স্টেট
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    // 🎯 গ্যালারি ফাইল আপলোড লঞ্চার
    var fileUploadCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            fileUploadCallback?.onReceiveValue(arrayOf(uri))
        } else {
            fileUploadCallback?.onReceiveValue(null)
        }
        fileUploadCallback = null
    }

    BackHandler {
        if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        } else {
            onBackClick()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // =========================================================================
        // 🔝 ১. স্লিক হেডার বার
        // =========================================================================
        Surface(
            color = DarkBg,
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Creator Studio",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Live Analytics & Pipeline",
                            color = ActionGreen,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // ৩-ডট (⋮) মেনু
                Box {
                    IconButton(
                        onClick = { showTopMenu = true },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showTopMenu,
                        onDismissRequest = { showTopMenu = false },
                        modifier = Modifier
                            .background(CardBg)
                            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Refresh Dashboard", color = Color.White, fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = ActionGreen) },
                            onClick = {
                                showTopMenu = false
                                isRefreshing = true
                                webViewInstance?.reload()
                            }
                        )

                        HorizontalDivider(color = BorderColor, thickness = 0.6.dp)

                        DropdownMenuItem(
                            text = { 
                                Text(
                                    text = "Switch to Personal Profile", 
                                    color = Color(0xFFFF5252), 
                                    fontSize = 13.sp, 
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

        // =========================================================================
        // 🔄 ২. ব্রাউজারের মতো Pull-To-Refresh ও ফুলস্ক্রিন WebView কন্টেইনার
        // =========================================================================
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                webViewInstance?.reload()
            },
            state = pullRefreshState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
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
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isRefreshing = false
                                CookieManager.getInstance().flush()
                            }

                            override fun onReceivedHttpError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                errorResponse: WebResourceResponse?
                            ) {
                                super.onReceivedHttpError(view, request, errorResponse)
                                isRefreshing = false
                            }
                        }

                        // গ্যালারি ওপেনিং লজিক
                        webChromeClient = object : WebChromeClient() {
                            override fun onShowFileChooser(
                                webView: WebView?,
                                filePathCallback: ValueCallback<Array<Uri>>?,
                                fileChooserParams: FileChooserParams?
                            ): Boolean {
                                fileUploadCallback?.onReceiveValue(null)
                                fileUploadCallback = filePathCallback
                                filePickerLauncher.launch("image/*")
                                return true
                            }
                        }

                        loadUrl(studioUrl)
                    }
                }
            )
        }
    }
}

/**
 * 🛠️ ফিক্সড: overScrollMode এখন সরাসরি webView-তে কল করা হয়েছে
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
        allowFileAccess = true
        allowContentAccess = true
        cacheMode = WebSettings.LOAD_DEFAULT
        useWideViewPort = true
        loadWithOverviewMode = true
        setSupportZoom(false)
    }

    // 🎯 ফিক্সড লাইন
    webView.overScrollMode = View.OVER_SCROLL_NEVER
    webView.setBackgroundColor(android.graphics.Color.parseColor("#090C13"))
}
