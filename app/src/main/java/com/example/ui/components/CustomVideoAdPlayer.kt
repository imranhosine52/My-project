@file:kotlin.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ui.components

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.ads.UnifiedAdManager
import com.example.data.model.CustomVideoAdDto
import com.example.data.model.TrackAdEventRequest
import com.example.data.remote.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

// 🎨 ইউটিউবের হুবহু কালার প্যালেট
private val YouTubeDarkBg = Color(0xFF0F0F0F)
private val YouTubeCardDark = Color(0xFF181818)
private val YouTubeAdYellow = Color(0xFFFFCC00)
private val YouTubeSkipButtonBg = Color(0xCC111111)
private val YouTubeSecondaryBtnBg = Color(0xFF272727)
private val YouTubeInstallPurple = Color(0xFFD0BCFF)

private fun findActivity(context: Context): Activity? {
    var current = context
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun formatAdTime(millis: Long): String {
    if (millis <= 0L) return "00:00"
    val totalSeconds = millis / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CustomVideoAdDialog(
    ad: CustomVideoAdDto,
    onAdFinishedOrSkipped: () -> Unit,
    onNavigateInternalScreen: (target: String) -> Unit,
    onOpenExternalUrl: (url: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { findActivity(context) }
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    val buttonText = remember(ad.ctaText) {
        ad.ctaText.ifBlank { "Install" }
    }

    var isAdPlaying by remember { mutableStateOf(true) }
    var showPlayPauseControls by remember { mutableStateOf(false) }
    var currentAdPositionMs by remember { mutableLongStateOf(0L) }
    var totalAdDurationMs by remember { mutableLongStateOf(0L) }

    var isAdVideoVertical by remember { mutableStateOf(true) }

    val skipThresholdSec = ad.skipAfterSeconds
    val canBeSkipped = ad.isSkippable
    var remainingSecondsToSkip by remember { mutableIntStateOf(skipThresholdSec) }
    val isSkipButtonUnlocked = (remainingSecondsToSkip <= 0 && canBeSkipped)

    // =========================================================================
    // ↕️ ৩-স্টেপ প্লেয়ার রিসাইজিং (উপরে টানলে প্লেয়ার ছোট ও ওয়েব পেজ বিশাল বড় হবে)
    // =========================================================================
    var isPlayerShrunk by remember { mutableStateOf(false) }   // উপরে টানলে ছোট হবে
    var isPlayerExpanded by remember { mutableStateOf(false) } // নিচে টানলে বড় হবে

    val animatedPlayerHeight by animateDpAsState(
        targetValue = when {
            isPlayerShrunk -> 145.dp                             // 👈 উপরে টানলে প্লেয়ার ছোট, ওয়েব পেজ বড়
            isPlayerExpanded -> screenHeight * 0.62f             // 👈 নিচে টানলে প্লেয়ার বড়
            isAdVideoVertical -> screenHeight * 0.48f            // স্বাভাবিক টিকটক মোড
            else -> 210.dp                                       // স্বাভাবিক ১৬:৯ মোড
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "player_height_anim"
    )

    val playPauseAlpha by animateFloatAsState(
        targetValue = if (showPlayPauseControls || !isAdPlaying) 1f else 0f,
        animationSpec = tween(180),
        label = "play_pause_alpha"
    )

    // =========================================================================
    // 🎯 ফিক্সড: executeAdAction ফাংশনটি সবার শীর্ষে গ্লোবালি ডিক্লেয়ার করা হয়েছে
    // =========================================================================
    fun executeAdAction(actionType: String = "LEARN_MORE", customTarget: String? = null) {
        val rawTarget = (customTarget ?: ad.destinationTarget).trim()
        val target = if (rawTarget.isBlank()) "https://playdramaflix.com" else rawTarget

        // ১. সার্ভারে ক্লিক ট্র্যাকিং
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ApiClient.apiService.trackCustomAdEvent(
                    TrackAdEventRequest(adId = ad.id, event = "click")
                )
            } catch (_: Exception) {}
        }

        // ২. বিজ্ঞাপন বন্ধ করা
        onAdFinishedOrSkipped()

        // ৩. ওরিয়েন্টেশন রিসেট
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        // ৪. অ্যাকশন এক্সিকিউট
        when (actionType) {
            "CTA_PRIMARY" -> {
                if (target.contains("vip", ignoreCase = true) || ad.isInternalApp || buttonText.contains("VIP", ignoreCase = true)) {
                    onNavigateInternalScreen("screen:vip")
                } else if (target.startsWith("market://") || ad.isPlayStore) {
                    try {
                        val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse(target)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        (activity ?: context).startActivity(playIntent)
                    } catch (_: Exception) {
                        onOpenExternalUrl(target)
                    }
                } else {
                    val opened = UnifiedAdManager.openChromeCustomTab(activity ?: context, target)
                    if (!opened) onOpenExternalUrl(target)
                }
            }
            else -> {
                val webUrl = if (target.contains("vip", true)) "https://playdramaflix.com" else target
                val opened = UnifiedAdManager.openChromeCustomTab(activity ?: context, webUrl)
                if (!opened) {
                    try {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        (activity ?: context).startActivity(browserIntent)
                    } catch (_: Exception) {
                        onOpenExternalUrl(webUrl)
                    }
                }
            }
        }
    }

    // 📊 ভিউ ট্র্যাকার
    LaunchedEffect(ad.id) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ApiClient.apiService.trackCustomAdEvent(
                    TrackAdEventRequest(adId = ad.id, event = "view")
                )
            } catch (_: Exception) {}
        }
    }

    // 🚀 বিজ্ঞাপনের ExoPlayer
    val adPlayer = remember {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(1000, 15000, 500, 1000)
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build().apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_OFF
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true
                )
            }
    }

    LaunchedEffect(ad.videoUrl) {
        if (ad.videoUrl.isNotBlank()) {
            try {
                adPlayer.stop()
                adPlayer.clearMediaItems()
                val mediaItem = MediaItem.fromUri(Uri.parse(ad.videoUrl))
                adPlayer.setMediaItem(mediaItem)
                adPlayer.prepare()
                adPlayer.play()
            } catch (_: Exception) {
                onAdFinishedOrSkipped()
            }
        } else {
            onAdFinishedOrSkipped()
        }
    }

    DisposableEffect(adPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    isAdVideoVertical = videoSize.height > videoSize.width
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    totalAdDurationMs = adPlayer.duration.coerceAtLeast(0L)
                } else if (state == Player.STATE_ENDED) {
                    onAdFinishedOrSkipped()
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isAdPlaying = playing
            }
        }
        adPlayer.addListener(listener)

        onDispose {
            adPlayer.removeListener(listener)
            adPlayer.stop()
            adPlayer.clearMediaItems()
            adPlayer.release()
        }
    }

    LaunchedEffect(showPlayPauseControls, isAdPlaying) {
        if (showPlayPauseControls && isAdPlaying) {
            delay(2500L)
            showPlayPauseControls = false
        }
    }

    LaunchedEffect(isAdPlaying, remainingSecondsToSkip) {
        if (canBeSkipped && remainingSecondsToSkip > 0) {
            while (remainingSecondsToSkip > 0) {
                delay(1000L)
                if (isAdPlaying) {
                    remainingSecondsToSkip--
                }
            }
        }
    }

    LaunchedEffect(isAdPlaying) {
        while (isAdPlaying) {
            currentAdPositionMs = adPlayer.currentPosition.coerceAtLeast(0L)
            val d = adPlayer.duration
            if (d > 0) totalAdDurationMs = d
            delay(100L)
        }
    }

    BackHandler {
        if (isSkipButtonUnlocked) {
            onAdFinishedOrSkipped()
        } else if (canBeSkipped) {
            Toast.makeText(context, "Skip in ${remainingSecondsToSkip}s", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(
        onDismissRequest = {
            if (isSkipButtonUnlocked) onAdFinishedOrSkipped()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(YouTubeDarkBg)
        ) {
            // =========================================================================
            // 📺 ১. উপরের ভিডিও প্লেয়ার ফ্রেম
            // =========================================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(animatedPlayerHeight)
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures {
                            showPlayPauseControls = !showPlayPauseControls
                        }
                    }
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = adPlayer
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // ⏯️ প্লে/পজ বাটন
                if (playPauseAlpha > 0.02f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .graphicsLayer {
                                alpha = playPauseAlpha
                                scaleX = 0.85f + (0.15f * playPauseAlpha)
                                scaleY = 0.85f + (0.15f * playPauseAlpha)
                            }
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f))
                            .clickable {
                                if (adPlayer.isPlaying) adPlayer.pause() else adPlayer.play()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isAdPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // 🔝 উপরে ডানে: [ Visit advertiser ↗ ]
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 8.dp, end = 10.dp)
                        .clickable { executeAdAction("LEARN_MORE") }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Visit advertiser", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                    }
                }

                // 🏷️ নিচে বাঁয়ে: [ Ad ] এবং মিনিট টাইমলাইন (00:05 / 01:30)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(shape = RoundedCornerShape(3.dp), color = YouTubeAdYellow) {
                        Text("Ad", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp))
                    }

                    val currentFormatted = formatAdTime(currentAdPositionMs)
                    val totalFormatted = formatAdTime(totalAdDurationMs)
                    Text("$currentFormatted / $totalFormatted", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // ⏭️ নিচে ডানে: [ Skip ❯| ] বাটন
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 10.dp, bottom = 8.dp)
                ) {
                    if (canBeSkipped) {
                        if (isSkipButtonUnlocked) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = YouTubeSkipButtonBg,
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.8f)),
                                modifier = Modifier.clickable { onAdFinishedOrSkipped() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("Skip", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Icon(Icons.Default.SkipNext, contentDescription = "Skip", tint = Color.White, modifier = Modifier.size(15.dp))
                                }
                            }
                        } else {
                            Surface(shape = RoundedCornerShape(4.dp), color = YouTubeSkipButtonBg) {
                                Text("$remainingSecondsToSkip", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                            }
                        }
                    }
                }

                // 🟡 হলুদ প্রগ্রেস বার
                val progressFraction = if (totalAdDurationMs > 0) (currentAdPositionMs.toFloat() / totalAdDurationMs.toFloat()).coerceIn(0f, 1f) else 0f
                LinearProgressIndicator(
                    progress = { progressFraction },
                    color = YouTubeAdYellow,
                    trackColor = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.fillMaxWidth().height(2.5.dp).align(Alignment.BottomCenter)
                )
            }

            // =========================================================================
            // ↕️ ২. ড্র্যাগেবল হ্যান্ডেল বার (উপরে টানলে প্লেয়ার ছোট, নিচে টানলে বড়)
            // =========================================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .background(Color(0xFF161616))
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount < -8f) {
                                // 🎯 উপরে টানলে প্লেয়ার ছোট হবে এবং ওয়েব পেজ বড় হবে
                                isPlayerShrunk = true
                                isPlayerExpanded = false
                            } else if (dragAmount > 8f) {
                                // 🎯 নিচে টানলে প্লেয়ার আবার স্বাভাবিক বা বড় হবে
                                if (isPlayerShrunk) {
                                    isPlayerShrunk = false
                                } else {
                                    isPlayerExpanded = true
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF666666))
                )
            }

            // =========================================================================
            // 📑 ৩. নিচের অংশ: হেডার, বাটন ও দুই সাইডে ফুল-স্ক্রিন ওয়েব পেজ
            // =========================================================================
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(YouTubeDarkBg),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // ৩.১ স্পনসর হেডার ও ক্লোজ বাটন
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sponsored", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)

                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp).clickable {
                            if (isSkipButtonUnlocked) onAdFinishedOrSkipped()
                            else Toast.makeText(context, "Please wait ${remainingSecondsToSkip}s", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                // ৩.২ ব্র্যান্ড লোগো ও টাইটেল
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .clickable { executeAdAction("CTA_PRIMARY") },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF222B3D)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Storefront, contentDescription = null, tint = ad.parsedCtaColor, modifier = Modifier.size(22.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(ad.title, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (ad.isPlayStore) "Google Play · FREE" else "Official Sponsored Platform", color = Color(0xFFAAAAAA), fontSize = 11.sp)
                    }
                }

                // ৩.৩ কার্যকরী ডুয়াল অ্যাকশন বাটন
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // বাটন ১: [ Learn more ]
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = YouTubeSecondaryBtnBg,
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clickable { executeAdAction("LEARN_MORE") }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("Learn more", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // বাটন ২: [ Get VIP Now / Install ]
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = ad.parsedCtaColor.takeIf { it != Color(0xFF00E676) } ?: YouTubeInstallPurple,
                        modifier = Modifier
                            .weight(1.3f)
                            .height(38.dp)
                            .clickable { executeAdAction("CTA_PRIMARY") }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(buttonText, color = Color.Black, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // =========================================================================
                // 🌐 ৩.৪ দুই সাইডে ১০০% ফুল-স্ক্রিন ওয়েব পেজ (Edge-to-Edge)
                // =========================================================================
                val targetUrl = ad.destinationTarget.trim()
                val isHttpWeb = targetUrl.startsWith("http://") || targetUrl.startsWith("https://")

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth() // 👈 দুই সাইডে সম্পূর্ণ ফুল স্ক্রিন
                        .background(YouTubeCardDark)
                ) {
                    if (isHttpWeb) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        loadWithOverviewMode = true
                                        useWideViewPort = true
                                        cacheMode = WebSettings.LOAD_DEFAULT
                                    }
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                            executeAdAction("LEARN_MORE", url)
                                            return true
                                        }
                                    }
                                    loadUrl(targetUrl)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = YouTubeAdYellow, modifier = Modifier.size(44.dp))
                                Text(ad.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("Tap '$buttonText' or 'Learn more' to explore in Google Chrome / Play Store", color = Color(0xFFAAAAAA), fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}
