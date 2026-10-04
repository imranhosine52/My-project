@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
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
private val YouTubeCardDark = Color(0xFF1E1E1E)
private val YouTubeAdYellow = Color(0xFFFFCC00)
private val YouTubeSkipButtonBg = Color(0xCC111111)
private val YouTubeSecondaryBtnBg = Color(0xFF272727)
private val YouTubeInstallPurple = Color(0xFFD0BCFF)

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
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    val buttonText = remember(ad.ctaText) {
        ad.ctaText.ifBlank { "Install" }
    }

    var isAdPlaying by remember { mutableStateOf(true) }
    var showPlayPauseControls by remember { mutableStateOf(false) }
    var currentAdPositionMs by remember { mutableLongStateOf(0L) }
    var totalAdDurationMs by remember { mutableLongStateOf(0L) }

    // স্কিপ ও কাউন্টডাউন স্টেট
    val skipThresholdSec = ad.skipAfterSeconds
    val canBeSkipped = ad.isSkippable
    var remainingSecondsToSkip by remember { mutableIntStateOf(skipThresholdSec) }
    val isSkipButtonUnlocked = (remainingSecondsToSkip <= 0 && canBeSkipped)

    // ↕️ প্লেয়ার বড়/ছোট করার স্মুথ জেসচার স্টেট
    var isPlayerExpanded by remember { mutableStateOf(false) }
    val animatedPlayerHeight by animateDpAsState(
        targetValue = if (isPlayerExpanded) (screenHeight * 0.58f) else 235.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "player_height_anim"
    )

    // 🎯 ফিক্সড: প্লে/পজ বাটনের স্মুথ অ্যানিমেটেড আলফা (AnimatedVisibility স্কোপ এরর সমাধান)
    val playPauseAlpha by animateFloatAsState(
        targetValue = if (showPlayPauseControls || !isAdPlaying) 1f else 0f,
        animationSpec = tween(180),
        label = "play_pause_alpha"
    )

    // 📊 ১. ভিউ ইভেন্ট ট্র্যাকার
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

    // প্লে/পজ বাটন ২.৫ সেকেন্ড পর অদৃশ্য হওয়া
    LaunchedEffect(showPlayPauseControls, isAdPlaying) {
        if (showPlayPauseControls && isAdPlaying) {
            delay(2500L)
            showPlayPauseControls = false
        }
    }

    // কাউন্টডাউন টাইমার
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

    // প্রগ্রেস বার ট্র্যাকার
    LaunchedEffect(isAdPlaying) {
        while (isAdPlaying) {
            currentAdPositionMs = adPlayer.currentPosition.coerceAtLeast(0L)
            val d = adPlayer.duration
            if (d > 0) totalAdDurationMs = d
            delay(100L)
        }
    }

    // 🎯 ক্লিক ট্র্যাকার এবং রিডাইরেকশন হ্যান্ডলার
    fun handleDestinationClick() {
        val target = ad.destinationTarget.trim()
        if (target.isBlank()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                ApiClient.apiService.trackCustomAdEvent(
                    TrackAdEventRequest(adId = ad.id, event = "click")
                )
            } catch (_: Exception) {}
        }

        when {
            ad.isInternalApp -> {
                onAdFinishedOrSkipped()
                onNavigateInternalScreen(target)
            }
            ad.isPlayStore -> {
                try {
                    val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse(target)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(playIntent)
                } catch (_: Exception) {
                    val cleanWeb = if (target.startsWith("market://details?id=")) {
                        "https://play.google.com/store/apps/details?id=" + target.removePrefix("market://details?id=")
                    } else target
                    onOpenExternalUrl(cleanWeb)
                }
                onAdFinishedOrSkipped()
            }
            else -> {
                onOpenExternalUrl(target)
                onAdFinishedOrSkipped()
            }
        }
    }

    // ব্যাক প্রেস লক
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

                // ⏯️ প্লেয়ারের সেন্ট্রাল প্লে/পজ বাটন (১০০% ক্র্যাশ-প্রুফ অ্যানিমেটেড আলফা)
                if (playPauseAlpha > 0.02f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .graphicsLayer {
                                alpha = playPauseAlpha
                                scaleX = 0.85f + (0.15f * playPauseAlpha)
                                scaleY = 0.85f + (0.15f * playPauseAlpha)
                            }
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f))
                            .clickable {
                                if (adPlayer.isPlaying) {
                                    adPlayer.pause()
                                } else {
                                    adPlayer.play()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isAdPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause Ad",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
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
                        .clickable { handleDestinationClick() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Visit advertiser",
                            color = Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                // 🏷️ নিচে বাঁয়ে: [ Sponsored ⓘ ]
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Sponsored",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(13.dp)
                    )
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
                                    Text(
                                        text = "Skip",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "Skip",
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = YouTubeSkipButtonBg
                            ) {
                                Text(
                                    text = "$remainingSecondsToSkip",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // 🟡 হলুদ প্রগ্রেস বার (ভিডিওর নিচে)
                val progressFraction = if (totalAdDurationMs > 0) {
                    (currentAdPositionMs.toFloat() / totalAdDurationMs.toFloat()).coerceIn(0f, 1f)
                } else 0f

                LinearProgressIndicator(
                    progress = { progressFraction },
                    color = YouTubeAdYellow,
                    trackColor = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .align(Alignment.BottomCenter)
                )
            }

            // =========================================================================
            // ↕️ ২. ড্র্যাগেবল হ্যান্ডেল বার (নিচে টানলে প্লেয়ার বড়, উপরে টানলে ছোট হবে)
            // =========================================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .background(Color(0xFF161616))
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount > 12f) {
                                isPlayerExpanded = true // নিচে টান দিলে বড় হবে
                            } else if (dragAmount < -12f) {
                                isPlayerExpanded = false // উপরে টান দিলে ছোট হবে
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF555555))
                )
            }

            // =========================================================================
            // 📑 ৩. নিচের অংশ: ক্লিন স্পনসর বার ও ফুল-স্ক্রিন ওয়েব পেজ
            // =========================================================================
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(YouTubeDarkBg)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ৩.১ স্পনসর হেডার ও ক্লোজ বাটন
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sponsored",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // ✕ বিজ্ঞাপন ক্লোজ বাটন
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable {
                                if (isSkipButtonUnlocked) onAdFinishedOrSkipped()
                                else Toast.makeText(context, "Please wait ${remainingSecondsToSkip}s", Toast.LENGTH_SHORT).show()
                            }
                    )
                }

                // ৩.২ ব্র্যান্ড লোগো, নাম ও প্ল্যাটফর্ম টাইটেল
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { handleDestinationClick() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF222B3D)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storefront,
                            contentDescription = null,
                            tint = ad.parsedCtaColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text(
                            text = ad.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = if (ad.isPlayStore) "Google Play · FREE" else "Official Sponsored Platform",
                            color = Color(0xFFAAAAAA),
                            fontSize = 11.5.sp
                        )
                    }
                }

                // ৩.৩ কাস্টমাইজড ডুয়াল অ্যাকশন বাটন [ Learn more ] [ buttonText ]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = YouTubeSecondaryBtnBg,
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clickable { handleDestinationClick() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Learn more",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = ad.parsedCtaColor.takeIf { it != Color(0xFF00E676) } ?: YouTubeInstallPurple,
                        modifier = Modifier
                            .weight(1.3f)
                            .height(38.dp)
                            .clickable { handleDestinationClick() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = buttonText,
                                color = Color.Black,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // =========================================================================
                // 🌐 ৩.৪ ফুল-উইন্ডো ওয়েব পেজ (বাকি সম্পূর্ণ জায়গা জুড়ে থাকবে)
                // =========================================================================
                val targetUrl = ad.destinationTarget.trim()
                val isHttpWeb = targetUrl.startsWith("http://") || targetUrl.startsWith("https://")

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .background(YouTubeCardDark)
                        .border(0.8.dp, Color(0xFF282828), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
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
                                            handleDestinationClick()
                                            return true
                                        }
                                    }
                                    loadUrl(targetUrl)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // 🎯 পেজের ওপর স্বচ্ছ টাচ লেয়ার: যেকোনো জায়গায় ক্লিক করলে ক্রোম ব্রাউজারে লিংক নিয়ে যাবে
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Transparent)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    handleDestinationClick()
                                }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable { handleDestinationClick() }
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = YouTubeAdYellow,
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    text = ad.title,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Tap to open official page in Google Chrome / Play Store",
                                    color = Color(0xFFAAAAAA),
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
