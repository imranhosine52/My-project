@file:OptIn(UnstableApi::class)

package com.example.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.ads.StartAppBanner
import com.example.ads.StartIoAdManager
import com.example.ads.UnifiedAdManager
import com.example.data.model.ContentItemDto
import com.example.data.model.DramaApiComment
import com.example.ui.components.AuthBottomSheetDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.DramaFlixViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

// 🏷️ ট্যাব এনাম
enum class PlayerTab {
    FOR_YOU,
    COMMENTS
}

private fun findActivityFromContext(context: Context): Activity? {
    var current = context
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun cleanDramaTitle(title: String): String {
    return title.split("|", "-").firstOrNull()?.trim() ?: title
}

// 🌐 লিংকটি কি থার্ড-পার্টি Embed আইফ্রেম নাকি ডিরেক্ট ভিডিও?
private fun isWebEmbedUrl(url: String): Boolean {
    val lower = url.lowercase()
    return lower.contains("/e/") ||
            lower.contains("/embed") ||
            lower.contains("byse.sx") ||
            lower.contains("streamtape") ||
            lower.contains("streamwish") ||
            lower.contains("dood") ||
            lower.contains("vidhide") ||
            lower.contains("youtube.com/embed") ||
            lower.contains("playdramaflix.com/player")
}

private fun formatTime(millis: Long): String {
    if (millis <= 0) return "00:00"
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PlayerScreen(
    slug: String,
    viewModel: DramaFlixViewModel,
    onBackClick: () -> Unit,
    onNavigateToVip: () -> Unit,
    onRelatedDramaClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { findActivityFromContext(context) }
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val configuration = LocalConfiguration.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    val isDeviceLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isAnyFullscreen = isDeviceLandscape

    var currentActiveSlug by remember(slug) { mutableStateOf(slug) }
    val dramaHistoryStack = remember { mutableStateListOf<String>() }

    val playerState by viewModel.playerUiState.collectAsStateWithLifecycle()
    val authState by viewModel.authUiState.collectAsStateWithLifecycle()
    val homeState by viewModel.homeUiState.collectAsStateWithLifecycle()

    // 🎛️ প্লেয়ার স্টেটসমূহ
    var isControlsVisible by remember { mutableStateOf(true) }
    var isScreenLocked by rememberSaveable { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }

    var isUserSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableLongStateOf(0L) }

    var resizeModeIndex by rememberSaveable { mutableIntStateOf(0) }
    val speedOptions = remember { listOf(1.0f, 1.25f, 1.5f, 2.0f, 0.5f, 0.75f) }
    var currentSpeedIndex by rememberSaveable { mutableIntStateOf(0) }
    val playbackSpeed by remember { derivedStateOf { speedOptions[currentSpeedIndex] } }

    var brightnessLevel by remember {
        mutableFloatStateOf(activity?.window?.attributes?.screenBrightness?.takeIf { it > 0 } ?: 0.5f)
    }
    var showBrightnessOverlay by remember { mutableStateOf(false) }
    var volumeLevel by remember { mutableFloatStateOf(0.5f) }
    var showVolumeOverlay by remember { mutableStateOf(false) }

    var isRewindActive by remember { mutableStateOf(false) }
    var isForwardActive by remember { mutableStateOf(false) }
    val rewindRotation = remember { Animatable(0f) }
    val forwardRotation = remember { Animatable(0f) }
    val rewindAlpha by animateFloatAsState(targetValue = if (isRewindActive) 1f else 0f, label = "rewindAlpha")
    val forwardAlpha by animateFloatAsState(targetValue = if (isForwardActive) 1f else 0f, label = "forwardAlpha")

    // স্ট্রিম লিঙ্ক নির্ধারণ (Embed নাকি Native MP4)
    var useWebPlayerFallback by rememberSaveable { mutableStateOf(false) }
    var activeStreamUrl by rememberSaveable { mutableStateOf("") }
    var currentLoadedEpKey by rememberSaveable { mutableStateOf("") }

    var showAuthSheet by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(PlayerTab.FOR_YOU) }
    var inlineCommentText by remember { mutableStateOf("") }

    var shuffledRecommendations by remember { mutableStateOf<List<ContentItemDto>>(emptyList()) }
    var selectedThreadParentComment by remember { mutableStateOf<DramaApiComment?>(null) }
    var threadReplyText by remember { mutableStateOf("") }
    var isDescriptionExpanded by remember { mutableStateOf(false) }

    val adConfig by UnifiedAdManager.adConfigState.collectAsStateWithLifecycle()
    val shouldLockEpisodes = !playerState.isVip && adConfig.adsEnabled

    val currentUser = authState.userProfile
    val currentUserName = currentUser?.displayName ?: "User"
    val currentUserAvatar = currentUser?.avatar ?: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=120&q=80"
    val userInitials = remember(currentUserName) {
        val parts = currentUserName.trim().split(" ").filter { it.isNotBlank() }
        if (parts.size >= 2) "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
        else currentUserName.take(2).uppercase()
    }

    fun handleBackNavigation() {
        if (isScreenLocked) {
            isScreenLocked = false
            return
        }
        if (isDeviceLandscape) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else if (selectedThreadParentComment != null) {
            selectedThreadParentComment = null
        } else if (dramaHistoryStack.isNotEmpty()) {
            val prevSlug = dramaHistoryStack.removeAt(dramaHistoryStack.lastIndex)
            currentActiveSlug = prevSlug
            viewModel.loadDramaDetails(prevSlug, context)
        } else {
            onBackClick()
        }
    }

    BackHandler { handleBackNavigation() }

    // ⚡ ১. MP4-এর জন্য Media3 ExoPlayer ইঞ্জিন
    val exoPlayer = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setUserAgent("PlayDramaFlix Native Player")

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val fastStartLoadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(2500, 15000, 1000, 2000)
            .build()

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(fastStartLoadControl)
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

    // 🌐 ২. থার্ড-পার্টি Embed-এর জন্য WebView প্লেয়ার ইঞ্জিন
    val persistentWebView = remember {
        WebView(context).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = true
                allowContentAccess = true
                loadWithOverviewMode = true
                useWideViewPort = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/128.0.0.0 Mobile Safari/537.36"
            }
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                    view?.destroy()
                    return true
                }
            }
            webChromeClient = WebChromeClient()
        }
    }

    // ফুলস্ক্রিন ও স্ট্যাটাস বার হ্যান্ডলিং
    LaunchedEffect(isAnyFullscreen) {
        activity?.let { act ->
            val window = act.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (isAnyFullscreen) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            exoPlayer.release()
            persistentWebView.destroy()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
                } else if (state == Player.STATE_ENDED) {
                    viewModel.playNextEpisode()
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                if (isWebEmbedUrl(activeStreamUrl)) {
                    useWebPlayerFallback = true
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // টাইমলাইন আপডেট
    LaunchedEffect(isPlaying, isUserSeeking) {
        while (isPlaying && !isUserSeeking) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
            if (totalDurationMs > 0) {
                viewModel.updateWatchProgress(currentPositionMs, totalDurationMs)
            }
            delay(500L)
        }
    }

    LaunchedEffect(isControlsVisible, isPlaying, isScreenLocked) {
        if (isControlsVisible && isPlaying && !isScreenLocked) {
            delay(4000L)
            isControlsVisible = false
        }
    }

    fun handleSeek(seconds: Int) {
        val target = (exoPlayer.currentPosition + (seconds * 1000L)).coerceIn(0L, totalDurationMs.coerceAtLeast(1L))
        exoPlayer.seekTo(target)
        currentPositionMs = target

        coroutineScope.launch {
            if (seconds < 0) {
                isRewindActive = true
                rewindRotation.snapTo(0f)
                rewindRotation.animateTo(-360f, animationSpec = tween(380, easing = LinearEasing))
                delay(500)
                isRewindActive = false
            } else {
                isForwardActive = true
                forwardRotation.snapTo(0f)
                forwardRotation.animateTo(360f, animationSpec = tween(380, easing = LinearEasing))
                delay(500)
                isForwardActive = false
            }
        }
    }

    // 🎯 স্মার্ট ভিডিও প্লেয়ার সিলেকশন (Embed vs MP4)
    LaunchedEffect(playerState.currentEpisode?.episodeNumber, playerState.currentEpisode?.episodeId, currentActiveSlug) {
        val currentEp = playerState.currentEpisode
        if (currentEp != null) {
            if (shouldLockEpisodes && currentEp.isLocked) {
                exoPlayer.pause()
                viewModel.showEpisodeUnlockModal(currentEp)
                return@LaunchedEffect
            }

            // ১ম পছন্দ: সার্ভারের আসল ভিডিও বা এম্বেড লিংক
            val serverVideoUrl = currentEp.videoUrl?.takeIf { it.isNotBlank() }
                ?: currentEp.appStreamUrl?.takeIf { it.isNotBlank() }
                ?: currentEp.embedUrl?.takeIf { it.isNotBlank() }
                ?: currentEp.resolveR2StreamUrl(currentActiveSlug)

            val epUniqueKey = "${currentEp.episodeId}_${currentEp.episodeNumber}"
            if (epUniqueKey == currentLoadedEpKey && activeStreamUrl == serverVideoUrl) return@LaunchedEffect

            currentLoadedEpKey = epUniqueKey
            activeStreamUrl = serverVideoUrl

            // 🔍 যদি এটি কোনো থার্ড-পার্টি Embed আইফ্রেম হয়
            if (isWebEmbedUrl(serverVideoUrl)) {
                useWebPlayerFallback = true
                exoPlayer.pause()
                persistentWebView.loadUrl(serverVideoUrl)
            } else {
                // ⚡ অন্যথায় আমাদের নিজস্ব কাস্টম MP4 প্লেয়ার
                useWebPlayerFallback = false
                try {
                    exoPlayer.stop()
                    exoPlayer.clearMediaItems()
                    val mediaItem = MediaItem.Builder()
                        .setUri(Uri.parse(serverVideoUrl))
                        .setMimeType(if (serverVideoUrl.contains(".m3u8")) MimeTypes.APPLICATION_M3U8 else MimeTypes.APPLICATION_MP4)
                        .build()
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                } catch (_: Exception) {
                    useWebPlayerFallback = true
                    persistentWebView.loadUrl(serverVideoUrl)
                }
            }
        }
    }

    LaunchedEffect(playerState.recommendations, homeState.popularDramas, currentActiveSlug) {
        val combined = (playerState.recommendations + homeState.popularDramas)
            .distinctBy { it.slug }
            .filter { it.slug != currentActiveSlug }
        shuffledRecommendations = combined.shuffled()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "card_shine")
    val shineOffset by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shine_offset"
    )
    val shiningBorderBrush = Brush.linearGradient(
        colors = listOf(Color(0xFF1E293B), Color(0xFF00E5FF).copy(alpha = 0.7f), Color(0xFF1E293B)),
        start = Offset(shineOffset, shineOffset),
        end = Offset(shineOffset + 180f, shineOffset + 180f)
    )

    // ড্রামার মূল অবজেক্ট (কখনোই যেন খালি না থাকে)
    val content = playerState.content
        ?: homeState.popularDramas.find { it.slug == currentActiveSlug }
        ?: ContentItemDto(title = "Loading Drama...", slug = currentActiveSlug)

    val currentEp = playerState.currentEpisode ?: playerState.episodes.firstOrNull()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (!isAnyFullscreen) Modifier.statusBarsPadding() else Modifier)
        ) {
            // =========================================================================
            // 🎬 ১. শীর্ষের ১৬:৯ প্লেয়ার ফ্রেম (Embed হলে WebView, MP4 হলে Custom Player)
            // =========================================================================
            Box(
                modifier = if (isAnyFullscreen) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                }.background(Color.Black)
            ) {
                if (useWebPlayerFallback && activeStreamUrl.isNotBlank()) {
                    // 🌐 Player 1: আগের Web / Embed Player (Third-party Server)
                    AndroidView(
                        factory = {
                            (persistentWebView.parent as? ViewGroup)?.removeView(persistentWebView)
                            persistentWebView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // ⚡ Player 2: আমাদের নিজস্ব কাস্টম MP4 গ্যালারি প্লেয়ার
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false
                                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                resizeMode = when (resizeModeIndex) {
                                    1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    2 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                            }
                        },
                        update = { view ->
                            view.resizeMode = when (resizeModeIndex) {
                                1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                2 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(isScreenLocked) {
                                detectTapGestures(
                                    onTap = { isControlsVisible = !isControlsVisible },
                                    onDoubleTap = { offset ->
                                        if (!isScreenLocked) {
                                            if (offset.x < size.width / 2) handleSeek(-10) else handleSeek(10)
                                        }
                                    }
                                )
                            }
                            .pointerInput(isScreenLocked) {
                                if (!isScreenLocked) {
                                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
                                    detectVerticalDragGestures(
                                        onDragStart = { offset ->
                                            if (offset.x < size.width / 2) showBrightnessOverlay = true else showVolumeOverlay = true
                                        },
                                        onDragEnd = {
                                            showBrightnessOverlay = false
                                            showVolumeOverlay = false
                                        },
                                        onVerticalDrag = { change, dragAmount ->
                                            val isLeft = change.position.x < size.width / 2
                                            val delta = -dragAmount / 500f

                                            if (isLeft) {
                                                brightnessLevel = (brightnessLevel + delta).coerceIn(0.05f, 1.0f)
                                                activity?.window?.let { win ->
                                                    val lp = win.attributes
                                                    lp.screenBrightness = brightnessLevel
                                                    win.attributes = lp
                                                }
                                            } else {
                                                val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                                                val newVol = (currentVol + (delta * maxVol)).coerceIn(0f, maxVol)
                                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol.toInt(), 0)
                                                volumeLevel = newVol / maxVol
                                            }
                                        }
                                    )
                                }
                            }
                    )

                    // ব্রাইটনেস ও ভলিউম ইন্ডিকেটর
                    if (showBrightnessOverlay) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.75f),
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.BrightnessMedium, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Brightness ${(brightnessLevel * 100).toInt()}%", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (showVolumeOverlay) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.75f),
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Volume ${(volumeLevel * 100).toInt()}%", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // অন-স্ক্রিন কাস্টম কন্ট্রোলস
                    if (isControlsVisible) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))) {
                            if (!isScreenLocked) {
                                // Top Controls
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.TopCenter)
                                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        IconButton(onClick = { handleBackNavigation() }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${cleanDramaTitle(content.title)} • EP ${currentEp?.episodeNumber ?: 1}",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Surface(
                                            shape = CircleShape,
                                            color = Color.White.copy(alpha = 0.15f),
                                            modifier = Modifier.height(24.dp).clickable {
                                                currentSpeedIndex = (currentSpeedIndex + 1) % speedOptions.size
                                                exoPlayer.setPlaybackSpeed(speedOptions[currentSpeedIndex])
                                            }
                                        ) {
                                            Text(
                                                text = "${playbackSpeed}X",
                                                color = Color(0xFF00E5FF),
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { resizeModeIndex = (resizeModeIndex + 1) % 3 },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Outlined.CropFree, contentDescription = "Aspect Ratio", tint = Color.White, modifier = Modifier.size(17.dp))
                                        }

                                        IconButton(
                                            onClick = { isScreenLocked = true; isControlsVisible = false },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Outlined.Lock, contentDescription = "Lock", tint = Color.White, modifier = Modifier.size(17.dp))
                                        }

                                        IconButton(
                                            onClick = {
                                                activity?.requestedOrientation = if (isDeviceLandscape) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen", tint = Color.White, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }

                                // Center Controls (Skip -10s, Play/Pause, Skip +10s)
                                Row(
                                    modifier = Modifier.align(Alignment.Center),
                                    horizontalArrangement = Arrangement.spacedBy(44.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("-10s", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(y = (-30).dp).alpha(rewindAlpha))
                                        IconButton(onClick = { handleSeek(-10) }, modifier = Modifier.size(44.dp).rotate(rewindRotation.value)) {
                                            SleekSkipIconOnline(isForward = false, color = Color.White)
                                        }
                                    }

                                    IconButton(
                                        onClick = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() },
                                        modifier = Modifier.size(54.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "Play/Pause",
                                            tint = Color.White,
                                            modifier = Modifier.size(46.dp)
                                        )
                                    }

                                    Box(contentAlignment = Alignment.Center) {
                                        Text("+10s", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(y = (-30).dp).alpha(forwardAlpha))
                                        IconButton(onClick = { handleSeek(10) }, modifier = Modifier.size(44.dp).rotate(forwardRotation.value)) {
                                            SleekSkipIconOnline(isForward = true, color = Color.White)
                                        }
                                    }
                                }

                                // Bottom Timeline
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomCenter)
                                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(formatTime(if (isUserSeeking) seekPosition else currentPositionMs), color = Color.White, fontSize = 11.5.sp)

                                    SleekOnlineTimeline(
                                        currentPositionMs = if (isUserSeeking) seekPosition else currentPositionMs,
                                        totalDurationMs = totalDurationMs,
                                        onSeekStarted = { isUserSeeking = true },
                                        onSeeking = { seekPosition = it },
                                        onSeekFinished = { targetPos ->
                                            exoPlayer.seekTo(targetPos)
                                            currentPositionMs = targetPos
                                            isUserSeeking = false
                                        },
                                        modifier = Modifier.weight(1f)
                                    )

                                    Text(formatTime(totalDurationMs), color = Color.White.copy(alpha = 0.8f), fontSize = 11.5.sp)
                                }
                            }
                        }
                    }

                    if (isScreenLocked) {
                        IconButton(
                            onClick = { isScreenLocked = false; isControlsVisible = true },
                            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp).size(42.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.7f))
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "Unlock", tint = Color(0xFFFF5252), modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }

            // =========================================================================
            // 📑 ২. নিচের পেজ (আগের মতো অক্ষুণ্ণ: পর্ব তালিকা, ডেসক্রিপশন, কমেন্টস)
            // =========================================================================
            if (!isAnyFullscreen) {
                if (selectedThreadParentComment != null) {
                    CommentRepliesThreadView(
                        parentComment = selectedThreadParentComment!!,
                        dramaContent = content,
                        currentUserAvatar = currentUserAvatar,
                        userInitials = userInitials,
                        replyText = threadReplyText,
                        onReplyTextChange = { threadReplyText = it },
                        onBackClick = { selectedThreadParentComment = null },
                        onSendReply = {
                            val text = threadReplyText.trim()
                            if (text.isNotBlank()) {
                                viewModel.postComment(text, parentId = selectedThreadParentComment!!.id)
                                threadReplyText = ""
                                keyboardController?.hide()
                            }
                        },
                        onLikeComment = { commentId -> viewModel.toggleCommentLike(commentId) }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF0C0F15)),
                            contentPadding = PaddingValues(bottom = 32.dp)
                        ) {
                            val shortTitle = cleanDramaTitle(content.title)

                            // Title & Navigation Row
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = shortTitle,
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF161A23),
                                            border = BorderStroke(0.8.dp, Color(0xFF2B3346)),
                                            modifier = Modifier.clickable {
                                                StartIoAdManager.showInterstitial(context, isVip = playerState.isVip) {
                                                    viewModel.playPreviousEpisode()
                                                }
                                            }
                                        ) {
                                            Text("Pre", color = Color(0xFFB0B7C6), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF161A23),
                                            border = BorderStroke(0.8.dp, Color(0xFF2B3346)),
                                            modifier = Modifier.clickable {
                                                StartIoAdManager.showInterstitial(context, isVip = playerState.isVip) {
                                                    viewModel.playNextEpisode()
                                                }
                                            }
                                        ) {
                                            Text("Next", color = Color(0xFFB0B7C6), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
                                        }
                                    }
                                }
                            }

                            // Metadata Row (Year, Rating, Likes, Views, Bookmark)
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text(content.releaseYear.ifBlank { "2026" }, color = Color(0xFF8E95A5), fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                                        Text("•", color = Color(0xFF4C5466), fontSize = 11.sp)
                                        Icon(Icons.Default.Star, contentDescription = null, tint = GoldVip, modifier = Modifier.size(13.dp))
                                        Text(if (content.rating > 0) String.format(Locale.US, "%.1f", content.rating) else "8.9", color = GoldVip, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                        Text("•", color = Color(0xFF4C5466), fontSize = 11.sp)
                                        Text(
                                            text = if (isDescriptionExpanded) "less" else "...more",
                                            color = TealAccent,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clickable { isDescriptionExpanded = !isDescriptionExpanded }
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                                            modifier = Modifier.clickable {
                                                if (!authState.isLoggedIn) showAuthSheet = true else viewModel.toggleLikeDrama()
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (playerState.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                contentDescription = "Like",
                                                tint = if (playerState.isLiked) Color(0xFFFF4B72) else Color(0xFFADB3C2),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text("${playerState.likesCount.coerceAtLeast(1)}", color = Color(0xFFADB3C2), fontSize = 11.sp)
                                        }

                                        Icon(
                                            imageVector = if (playerState.isInWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                            contentDescription = "Bookmark",
                                            tint = if (playerState.isInWatchlist) TealAccent else Color(0xFFADB3C2),
                                            modifier = Modifier.size(15.dp).clickable { viewModel.toggleWatchlist() }
                                        )
                                    }
                                }
                            }

                            // Expandable Description
                            item {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    AnimatedVisibility(
                                        visible = isDescriptionExpanded,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut()
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
                                            Text(content.description?.takeIf { it.isNotBlank() } ?: content.synopsis, color = Color(0xFFCCD0DB), fontSize = 12.sp, lineHeight = 17.sp)
                                        }
                                    }
                                }
                            }

                            // 📺 EPISODE PILLS (EP 1, EP 2, EP 3...)
                            val displayEpisodes = playerState.episodes.ifEmpty {
                                (1..(content.totalEpisodes.coerceAtLeast(1))).map { num ->
                                    com.example.data.model.EpisodeDto(
                                        episodeNumber = num,
                                        rawTitle = "Episode $num",
                                        isLocked = num > 1
                                    )
                                }
                            }

                            item {
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(displayEpisodes.size) { index ->
                                        val ep = displayEpisodes[index]
                                        val isSelected = (currentEp?.episodeNumber ?: 1) == ep.episodeNumber
                                        val isEpLocked = shouldLockEpisodes && ep.isLocked

                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) Color(0xFF0F261C) else Color(0xFF131722),
                                            border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) Color(0xFF00D166) else Color(0xFF222838)),
                                            modifier = Modifier.widthIn(min = 84.dp).clickable {
                                                if (isEpLocked) viewModel.showEpisodeUnlockModal(ep)
                                                else viewModel.selectEpisode(ep)
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "EP ${ep.episodeNumber}",
                                                    color = if (isSelected) Color(0xFF00D166) else Color.White,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                if (isSelected) {
                                                    EqualizerBarsIcon(modifier = Modifier.size(12.dp, 14.dp), tint = Color(0xFF00D166))
                                                } else if (isEpLocked) {
                                                    Icon(Icons.Default.Lock, contentDescription = "Locked", tint = GoldVip, modifier = Modifier.size(13.dp))
                                                } else {
                                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF8E95A5), modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Ad Banner
                            item {
                                StartAppBanner(
                                    isVip = playerState.isVip,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
                                )
                            }

                            // 📑 Tabs Header (For you / Comments)
                            item {
                                Surface(color = Color(0xFF0C0F15), modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "For you",
                                            color = if (selectedTab == PlayerTab.FOR_YOU) Color.White else Color(0xFF8E95A5),
                                            fontSize = 13.5.sp,
                                            fontWeight = if (selectedTab == PlayerTab.FOR_YOU) FontWeight.Bold else FontWeight.Medium,
                                            modifier = Modifier.clickable { selectedTab = PlayerTab.FOR_YOU }
                                        )
                                        Text(
                                            text = "Comments (${playerState.comments.size})",
                                            color = if (selectedTab == PlayerTab.COMMENTS) Color.White else Color(0xFF8E95A5),
                                            fontSize = 13.5.sp,
                                            fontWeight = if (selectedTab == PlayerTab.COMMENTS) FontWeight.Bold else FontWeight.Medium,
                                            modifier = Modifier.clickable {
                                                selectedTab = PlayerTab.COMMENTS
                                                viewModel.refreshComments()
                                            }
                                        )
                                    }
                                }
                            }

                            // Tab 1: For You Grid
                            if (selectedTab == PlayerTab.FOR_YOU) {
                                val displayList = shuffledRecommendations.ifEmpty {
                                    (playerState.recommendations + homeState.popularDramas).filter { it.slug != currentActiveSlug }
                                }
                                val dramaRows = displayList.chunked(3)
                                items(dramaRows.size) { rowIndex ->
                                    val rowDramas = dramaRows[rowIndex]
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        for (drama in rowDramas) {
                                            val cardTitle = cleanDramaTitle(drama.title)
                                            Column(
                                                modifier = Modifier.weight(1f).clickable {
                                                    dramaHistoryStack.add(currentActiveSlug)
                                                    currentActiveSlug = drama.slug
                                                    viewModel.loadDramaDetails(drama.slug, context)
                                                    onRelatedDramaClick(drama.slug)
                                                }
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .aspectRatio(0.72f)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .border(1.dp, shiningBorderBrush, RoundedCornerShape(8.dp))
                                                        .background(Color(0xFF141A26))
                                                ) {
                                                    AsyncImage(
                                                        model = drama.posterUrl ?: drama.bannerUrl,
                                                        contentDescription = cardTitle,
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.BottomStart)
                                                            .padding(4.dp)
                                                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                                    ) {
                                                        Text("${drama.totalEpisodes} Episodes", color = Color(0xFFE2E8F0), fontSize = 9.sp)
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(cardTitle, color = Color(0xFFCCD0DB), fontSize = 11.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                        repeat(3 - rowDramas.size) { Spacer(modifier = Modifier.weight(1f)) }
                                    }
                                }
                            }

                            // Tab 2: Comments
                            if (selectedTab == PlayerTab.COMMENTS) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF161F30)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(userInitials, color = Color(0xFFFFC107), fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Box(
                                            modifier = Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(21.dp)).background(Color(0xFF131926)).padding(horizontal = 16.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            if (inlineCommentText.isEmpty()) Text("Add a comment...", color = Color(0xFF64748B), fontSize = 13.5.sp)
                                            BasicTextField(
                                                value = inlineCommentText,
                                                onValueChange = { inlineCommentText = it },
                                                textStyle = TextStyle(color = Color.White, fontSize = 13.5.sp),
                                                cursorBrush = SolidColor(Color(0xFFFFC107)),
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                                keyboardActions = KeyboardActions(onSend = {
                                                    if (inlineCommentText.isNotBlank()) {
                                                        viewModel.postComment(inlineCommentText.trim())
                                                        inlineCommentText = ""
                                                        keyboardController?.hide()
                                                    }
                                                }),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                if (inlineCommentText.isNotBlank()) {
                                                    viewModel.postComment(inlineCommentText.trim())
                                                    inlineCommentText = ""
                                                    keyboardController?.hide()
                                                }
                                            },
                                            modifier = Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFFFC107))
                                        ) {
                                            Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.Black, modifier = Modifier.size(19.dp))
                                        }
                                    }
                                }

                                items(playerState.comments.size) { index ->
                                    val comment = playerState.comments[index]
                                    ModernCommentRowItem(
                                        comment = comment,
                                        onLike = { viewModel.toggleCommentLike(comment.id) },
                                        onOpenReplies = { selectedThreadParentComment = comment },
                                        onShare = {}
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAuthSheet) {
            AuthBottomSheetDialog(viewModel = viewModel, onDismiss = { showAuthSheet = false })
        }

        if (shouldLockEpisodes && playerState.showEpisodeUnlockModal && playerState.lockedEpisodeTarget != null) {
            val lockedTarget = playerState.lockedEpisodeTarget!!
            CompactUnlockEpisodeDialog(
                episodeNumber = lockedTarget.episodeNumber,
                onDismiss = { viewModel.dismissEpisodeUnlockModal() },
                onWatchAd = {
                    val act = activity ?: findActivityFromContext(context)
                    if (act != null) {
                        StartIoAdManager.showRewardedAd(act) { isRewarded ->
                            if (isRewarded) {
                                viewModel.unlockEpisodeWithRewardAd(context, currentActiveSlug, lockedTarget)
                                Toast.makeText(context, "Episode ${lockedTarget.episodeNumber} unlocked for 2 hours!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        viewModel.unlockEpisodeWithRewardAd(context, currentActiveSlug, lockedTarget)
                    }
                },
                onUpgradeVip = {
                    viewModel.dismissEpisodeUnlockModal()
                    onNavigateToVip()
                }
            )
        }
    }
}
