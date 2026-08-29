package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.*
import kotlinx.coroutines.delay

/**
 * 🌐 In-App Browser Dialog
 * Opens sponsored links (Adsterra Direct Link, Smartlink, etc.) strictly INSIDE the application
 * without redirecting or kicking the user out to external browser apps.
 * 
 * Supports:
 * - 10-Second Live Verification countdown timer for rewarded episode unlocking
 * - Interactive in-app browsing with progress indicators
 * - Safe internal navigation with back stack support
 * - Clean close and verification callbacks
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InAppBrowserDialog(
    url: String,
    title: String = "Sponsored Offer",
    verificationSeconds: Int? = null,
    onVerificationComplete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var pageTitle by remember { mutableStateOf(title) }
    var currentUrl by remember { mutableStateOf(url) }
    var loadingProgress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(true) }

    // Verification timer state
    val totalSeconds = verificationSeconds ?: 0
    var remainingSeconds by remember { mutableIntStateOf(totalSeconds) }
    var isVerified by remember { mutableStateOf(verificationSeconds == null || verificationSeconds <= 0) }

    // Timer countdown loop
    LaunchedEffect(verificationSeconds) {
        if (verificationSeconds != null && verificationSeconds > 0) {
            remainingSeconds = verificationSeconds
            while (remainingSeconds > 0) {
                delay(1000L)
                remainingSeconds--
            }
            isVerified = true
            Toast.makeText(context, "✓ Verification Complete! Unlocking...", Toast.LENGTH_SHORT).show()
            delay(600L)
            onVerificationComplete?.invoke()
            onDismiss()
        }
    }

    // Handle Android system back press to go back inside webview first
    BackHandler {
        if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        } else {
            if (verificationSeconds != null && !isVerified) {
                Toast.makeText(
                    context,
                    "Stay $remainingSeconds seconds to unlock episode for free.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = {
            if (verificationSeconds != null && !isVerified) {
                Toast.makeText(
                    context,
                    "Visit cancelled. Complete $remainingSeconds seconds to unlock for free.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                // Top Header Toolbar
                Surface(
                    color = SurfaceDark,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Close Button
                            IconButton(
                                onClick = {
                                    if (verificationSeconds != null && !isVerified) {
                                        Toast.makeText(
                                            context,
                                            "Verification stopped. You can try again anytime.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceVariantDark)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close In-App Browser",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Center Info & Domain Bar
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = TealAccent,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = pageTitle.ifBlank { "In-App Browser" },
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                val displayHost = try {
                                    Uri.parse(currentUrl).host ?: "In-App Sponsor"
                                } catch (e: Exception) {
                                    "In-App Sponsor"
                                }

                                Text(
                                    text = displayHost,
                                    color = TextSecondary,
                                    fontSize = 10.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Verification Timer Badge or Refresh Action
                            if (verificationSeconds != null && verificationSeconds > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(
                                            if (isVerified) Color(0xFF00E676).copy(alpha = 0.2f)
                                            else TealAccent.copy(alpha = 0.15f)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isVerified) Color(0xFF00E676) else TealAccent,
                                            shape = RoundedCornerShape(20.dp)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        if (!isVerified) {
                                            CircularProgressIndicator(
                                                progress = { 1f - (remainingSeconds.toFloat() / totalSeconds.toFloat()) },
                                                modifier = Modifier.size(12.dp),
                                                color = TealAccent,
                                                strokeWidth = 2.dp,
                                                trackColor = BorderDark
                                            )
                                            Text(
                                                text = "${remainingSeconds}s",
                                                color = TealAccent,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFF00E676),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "Verified",
                                                color = Color(0xFF00E676),
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            } else {
                                IconButton(
                                    onClick = { webViewInstance?.reload() },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(SurfaceVariantDark)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Reload Page",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Web Page Loading Progress Bar
                        if (isLoading && loadingProgress < 1f) {
                            LinearProgressIndicator(
                                progress = { loadingProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.5.dp),
                                color = TealAccent,
                                trackColor = Color.Transparent
                            )
                        }
                    }
                }

                // Verification Hint Banner (If timer is running)
                if (verificationSeconds != null && !isVerified) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF0A2E28), Color(0xFF131A26))
                                )
                            )
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.HourglassTop,
                                contentDescription = null,
                                tint = TealAccent,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Browse this sponsor page for $remainingSeconds seconds to unlock Episode.",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // In-App WebView (Renders sponsor / direct link strictly in-app)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.White)
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                webViewInstance = this

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    setSupportZoom(true)
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    mediaPlaybackRequiresUserGesture = false
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    }
                                }

                                // Enable CookieManager for ad session continuity
                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                    cookieManager.setAcceptThirdPartyCookies(this, true)
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        loadingProgress = newProgress / 100f
                                        isLoading = newProgress < 100
                                    }

                                    override fun onReceivedTitle(view: WebView?, title: String?) {
                                        if (!title.isNullOrBlank() && !title.startsWith("http")) {
                                            pageTitle = title
                                        }
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        isLoading = true
                                        url?.let { currentUrl = it }
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        isLoading = false
                                        url?.let { currentUrl = it }
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val targetUri = request?.url ?: return false
                                        val scheme = targetUri.scheme?.lowercase() ?: ""
                                        val uriString = targetUri.toString()

                                        return if (scheme == "http" || scheme == "https") {
                                            // Keep navigation strictly inside this In-App WebView
                                            currentUrl = uriString
                                            false
                                        } else {
                                            // Handle custom schemes like tel, mailto, intent gracefully
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, targetUri).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                ctx.startActivity(intent)
                                            } catch (e: Exception) {
                                                Log.w("InAppBrowser", "Cannot handle intent scheme: $uriString")
                                            }
                                            true
                                        }
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        errorCode: Int,
                                        description: String?,
                                        failingUrl: String?
                                    ) {
                                        super.onReceivedError(view, errorCode, description, failingUrl)
                                        isLoading = false
                                    }
                                }

                                loadUrl(url)
                            }
                        },
                        update = { webView ->
                            // Update if url changes
                        }
                    )
                }
            }
        }
    }
}
