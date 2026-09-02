@file:OptIn(UnstableApi::class, ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Rational
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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

private fun findActivityFromContext(context: Context): Activity? {
    var currentContext = context
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) return currentContext
        currentContext = currentContext.baseContext
    }
    return null
}

enum class PlayerTab {
    FOR_YOU,
    COMMENTS
}

private fun cleanDramaTitle(title: String): String {
    return title.split("|", "-").firstOrNull()?.trim() ?: title
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

private fun isWebEmbedUrl(url: String): Boolean {
    val lower = url.lowercase()
    return lower.contains("/e/") ||
            lower.contains("/embed") ||
            lower.contains("streamtape") ||
            lower.contains("streamwish") ||
            lower.contains("dood") ||
            lower.contains("vidhide") ||
            lower.contains("youtube.com/embed") ||
            lower.contains("youtu.be") ||
            lower.contains("drive.google.com") ||
            lower.endsWith(".html") ||
            lower.endsWith(".htm") ||
            lower.contains("playdramaflix.com/player")
}

private fun buildMediaItemForUrl(url: String): MediaItem {
    val uri = Uri.parse(url)
    val lower = url.lowercase()
    val builder = MediaItem.Builder().setUri(uri)
    if (lower.contains(".m3u8") || lower.contains("hls")) {
        builder.setMimeType(MimeTypes.APPLICATION_M3U8)
    } else if (lower.contains(".mpd") || lower.contains("dash")) {
        builder.setMimeType(MimeTypes.APPLICATION_MPD)
    } else if (lower.contains(".mp4")) {
        builder.setMimeType(MimeTypes.APPLICATION_MP4)
    }
    return builder.build()
}

// 🛡️ ওয়েবসাইটের সব ডিফল্ট বাটন ও কন্ট্রোল হাইড করার জন্য সিএসএস
private const val HIDE_WEB_CONTROLS_CSS = """
    javascript:(function() {
        var style = document.createElement('style');
        style.innerHTML = `
            .jw-controls, .vjs-control-bar, .plyr__controls, .clappr-controls,
            .jw-display-icon-container, .vjs-big-play-button, .plyr__control--overlaid,
            .jw-button-color, .jw-icon, [class*="control"], [class*="seekbar"], [class*="progress"],
            .jw-watermark, .vjs-watermark, .controls, #controls {
                display: none !important;
                opacity: 0 !important;
                visibility: hidden !important;
                pointer-events: none !important;
            }
            video {
                width: 100vw !important;
                height: 100vh !important;
                object-fit: contain !important;
            }
        `;
        document.head.appendChild(style);
    })()
"""

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
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
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    val playerState by viewModel.playerUiState.collectAsStateWithLifecycle()
    val authState by viewModel.authUiState.collectAsStateWithLifecycle()
    val homeState by viewModel.homeUiState.collectAsStateWithLifecycle()

    var isPlaying by remember { mutableStateOf(true) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var isScreenLocked by rememberSaveable { mutableStateOf(false) }

    var isWebMode by rememberSaveable { mutableStateOf(false) }
    var activeStreamUrl by rememberSaveable { mutableStateOf("") }
    var currentLoadedEpKey by rememberSaveable { mutableStateOf("") }

    // 0: FIT (16:9), 1: ZOOM (TikTok 9:16 Crop), 2: STRETCH
    var resizeModeIndex by rememberSaveable { mutableIntStateOf(0) }

    val speedOptions = remember { listOf(1.0f, 1.25f, 1.5f, 2.0f, 0.5f, 0.75f) }
    var currentSpeedIndex by rememberSaveable { mutableIntStateOf(0) }
    val playbackSpeed by remember { derivedStateOf { speedOptions[currentSpeedIndex] } }

    var isLandscapeMode by rememberSaveable { mutableStateOf(false) }

    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }

    var isUserSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableLongStateOf(0L) }

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

    var showAuthSheet by remember { mutableStateOf(false) }

    var selectedTab by remember { mutableStateOf(PlayerTab.FOR_YOU) }
    var inlineCommentText by remember { mutableStateOf("") }

    var shuffledRecommendations by remember { mutableStateOf<List<ContentItemDto>>(emptyList()) }
    var selectedThreadParentComment by remember { mutableStateOf<DramaApiComment?>(null) }
    var threadReplyText by remember { mutableStateOf("") }
    var isDescriptionExpanded by remember { mutableStateOf(false) }

    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

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

    LaunchedEffect(playerState.recommendations, homeState.popularDramas, slug) {
        val combined = (playerState.recommendations + homeState.popularDramas)
            .distinctBy { it.slug }
            .filter { it.slug != slug }
        shuffledRecommendations = combined.shuffled()
    }

    // 📺 ফুলস্ক্রিন ও ওরিয়েন্টেশন কন্ট্রোল
    LaunchedEffect(isLandscapeMode) {
        activity?.let { act ->
            val window = act.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)

            if (isLandscapeMode) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                WindowCompat.setDecorFitsSystemWindows(window, false)
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                WindowCompat.setDecorFitsSystemWindows(window, true)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.let { act ->
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                val window = act.window
                WindowCompat.setDecorFitsSystemWindows(window, true)
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
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
        colors = listOf(
            Color(0xFF1E293B),
            Color(0xFF00E5FF).copy(alpha = 0.7f),
            Color(0xFF1E293B)
        ),
        start = Offset(shineOffset, shineOffset),
        end = Offset(shineOffset + 180f, shineOffset + 180f)
    )

    fun shareDramaLink() {
        try {
            val shareUrl = "https://playdramaflix.com/watch/$slug"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Watch Drama")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Watch ${cleanDramaTitle(playerState.content?.title ?: "Asian Drama")} on PlayDramaFlix:\n$shareUrl"
                )
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share with friends via"))
        } catch (_: Exception) {
            Toast.makeText(context, "Cannot open share options", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(slug) {
        viewModel.loadDramaDetails(slug, context)
    }

    // 🎬 ExoPlayer ইঞ্জিন
    val exoPlayer = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setUserAgent("Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
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

    val persistentPlayerView = remember {
        PlayerView(context).apply {
            player = exoPlayer
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    // 🌐 স্মার্ট ওয়েবভিউ (CSS Injection সহ যাতে ওয়েবের বাটন সম্পূর্ণ ঢেকে যায়)
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
                userAgentString = "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
            }
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            // জাভাস্ক্রিপ্ট ইন্টারফেস দিয়ে ওয়েব ভিডিওর টাইম সিঙ্ক
            addJavascriptInterface(object {
                @JavascriptInterface
                fun onTimeUpdate(curr: Float, dur: Float) {
                    if (!isUserSeeking) {
                        currentPositionMs = (curr * 1000).toLong()
                        totalDurationMs = (dur * 1000).toLong()
                    }
                }

                @JavascriptInterface
                fun onPlayStateChange(playing: Boolean) {
                    isPlaying = playing
                }
            }, "AndroidBridge")

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // ✅ পেজ লোড হওয়া মাত্রই ভেতরের বাটন ও লাল দাগ হাইড করবে
                    view?.loadUrl(HIDE_WEB_CONTROLS_CSS)

                    // ভিডিও টাইমের সাথে সায়ান স্লাইডার সিঙ্ক করার স্ক্রিপ্ট
                    val syncScript = """
                        javascript:(function() {
                            var v = document.querySelector('video');
                            if (v) {
                                v.play();
                                v.ontimeupdate = function() {
                                    if (window.AndroidBridge) {
                                        window.AndroidBridge.onTimeUpdate(v.currentTime, v.duration);
                                    }
                                };
                                v.onplay = function() { if (window.AndroidBridge) window.AndroidBridge.onPlayStateChange(true); };
                                v.onpause = function() { if (window.AndroidBridge) window.AndroidBridge.onPlayStateChange(false); };
                            }
                        })()
                    """
                    view?.loadUrl(syncScript)
                }
            }
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
                if (!isWebMode) {
                    isPlaying = playing
                }
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
            persistentPlayerView.player = null
            persistentWebView.destroy()
        }
    }

    LaunchedEffect(playerState.currentEpisode?.episodeNumber, playerState.currentEpisode?.episodeId, playerState.selectedServer) {
        val currentEp = playerState.currentEpisode
        val content = playerState.content
        if (currentEp != null && content != null) {
            if (shouldLockEpisodes && currentEp.isLocked) {
                exoPlayer.pause()
                viewModel.showEpisodeUnlockModal(currentEp)
                return@LaunchedEffect
            }

            val matchedServer = playerState.servers.find {
                it.episodeId == currentEp.episodeId || it.episodeId == currentEp.episodeNumber.toString()
            } ?: playerState.selectedServer ?: playerState.servers.firstOrNull()

            val rawUrl = currentEp.videoUrl?.takeIf { it.isNotBlank() }
                ?: currentEp.embedUrl?.takeIf { it.isNotBlank() }
                ?: matchedServer?.url?.takeIf { it.isNotBlank() }
                ?: matchedServer?.rawUrl?.takeIf { it.isNotBlank() }
                ?: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"

            val epUniqueKey = "${currentEp.episodeId}_${currentEp.episodeNumber}_${matchedServer?.id ?: ""}"
            if (epUniqueKey == currentLoadedEpKey) {
                return@LaunchedEffect
            }

            currentLoadedEpKey = epUniqueKey
            activeStreamUrl = rawUrl

            if (isWebEmbedUrl(rawUrl) || matchedServer?.type.equals("embed", ignoreCase = true)) {
                isWebMode = true
                exoPlayer.pause()
                val headers = HashMap<String, String>().apply {
                    put("Referer", rawUrl)
                    put("Origin", rawUrl)
                }
                persistentWebView.loadUrl(rawUrl, headers)
            } else {
                isWebMode = false
                try {
                    exoPlayer.stop()
                    exoPlayer.clearMediaItems()
                    val mediaItem = buildMediaItemForUrl(rawUrl)
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                } catch (_: Exception) {
                    isWebMode = true
                    persistentWebView.loadUrl(rawUrl)
                }
            }
        }
    }

    // ExoPlayer এর সময় ট্র্যাকার
    LaunchedEffect(isPlaying, isUserSeeking, isWebMode) {
        while (isPlaying && !isUserSeeking && !isWebMode) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
            if (totalDurationMs > 0) {
                viewModel.updateWatchProgress(currentPositionMs, totalDurationMs)
            }
            delay(300L)
        }
    }

    LaunchedEffect(isControlsVisible, isPlaying, isScreenLocked) {
        if (isControlsVisible && isPlaying && !isScreenLocked) {
            delay(4000L)
            isControlsVisible = false
        }
    }

    // ⚡ স্কিপ হ্যান্ডলার (ExoPlayer ও Web Video উভয়ের জন্যই কাজ করবে)
    fun handleSeek(seconds: Int) {
        val target = (currentPositionMs + (seconds * 1000L)).coerceIn(0L, totalDurationMs.coerceAtLeast(1L))
        currentPositionMs = target

        if (isWebMode) {
            val targetSec = target / 1000f
            persistentWebView.evaluateJavascript("var v = document.querySelector('video'); if (v) v.currentTime = $targetSec;", null)
        } else {
            exoPlayer.seekTo(target)
        }

        coroutineScope.launch {
            if (seconds < 0) {
                isRewindActive = true
                rewindRotation.snapTo(0f)
                rewindRotation.animateTo(-360f, animationSpec = tween(400, easing = LinearEasing))
                delay(600)
                isRewindActive = false
            } else {
                isForwardActive = true
                forwardRotation.snapTo(0f)
                forwardRotation.animateTo(360f, animationSpec = tween(400, easing = LinearEasing))
                delay(600)
                isForwardActive = false
            }
        }
    }

    // ⚡ প্লে / পজ হ্যান্ডলার
    fun handlePlayPause() {
        if (isWebMode) {
            persistentWebView.evaluateJavascript("var v = document.querySelector('video'); if (v) { v.paused ? v.play() : v.pause(); }", null)
            isPlaying = !isPlaying
        } else {
            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
        }
    }

    fun enterPiPMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                activity?.let { act ->
                    try {
                        isControlsVisible = false
                        val aspectRatio = when (resizeModeIndex) {
                            1 -> Rational(9, 16)
                            else -> Rational(16, 9)
                        }
                        val pipParams = PictureInPictureParams.Builder()
                            .setAspectRatio(aspectRatio)
                            .build()
                        act.enterPictureInPictureMode(pipParams)
                    } catch (_: Exception) {
                        Toast.makeText(context, "PiP not available on this device", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    BackHandler {
        if (isLandscapeMode) {
            isLandscapeMode = false
        } else if (isScreenLocked) {
            isScreenLocked = false
        } else if (selectedThreadParentComment != null) {
            selectedThreadParentComment = null
        } else {
            onBackClick()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (playerState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = TealAccent, strokeWidth = 3.dp, modifier = Modifier.size(44.dp))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (!isLandscapeMode) Modifier.statusBarsPadding() else Modifier)
            ) {
                // =========================================================================
                // 🎬 ১. ১০০% কাস্টমাইজড প্লেয়ার বক্স (ওয়েবের বাটন লুকিয়ে আমাদের সুন্দর UI)
                // =========================================================================
                Box(
                    modifier = if (isLandscapeMode) {
                        Modifier
                            .fillMaxSize()
                            .weight(1f)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    }
                        .background(Color.Black)
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
                                        val delta = -dragAmount / 550f

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
                ) {
                    if (isWebMode) {
                        AndroidView(
                            factory = {
                                (persistentWebView.parent as? ViewGroup)?.removeView(persistentWebView)
                                persistentWebView
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        AndroidView(
                            factory = {
                                (persistentPlayerView.parent as? ViewGroup)?.removeView(persistentPlayerView)
                                persistentPlayerView.apply {
                                    resizeMode = when (resizeModeIndex) {
                                        1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        2 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                }
                            },
                            update = { view ->
                                view.player = exoPlayer
                                view.resizeMode = when (resizeModeIndex) {
                                    1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    2 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                                view.post { view.requestLayout() }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // ☀️ ব্রাইটনেস HUD
                    AnimatedVisibility(
                        visible = showBrightnessOverlay,
                        enter = fadeIn(animationSpec = tween(150)),
                        exit = fadeOut(animationSpec = tween(200)),
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp)
                    ) {
                        Row(
                            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.BrightnessMedium, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Box(
                                modifier = Modifier.width(120.dp).height(2.5.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.35f))
                            ) {
                                Box(modifier = Modifier.fillMaxWidth(fraction = brightnessLevel.coerceIn(0f, 1f)).fillMaxHeight().background(Color(0xFF00E5FF)))
                            }
                        }
                    }

                    // 🔊 ভলিউম HUD
                    AnimatedVisibility(
                        visible = showVolumeOverlay,
                        enter = fadeIn(animationSpec = tween(150)),
                        exit = fadeOut(animationSpec = tween(200)),
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp)
                    ) {
                        Row(
                            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(if (volumeLevel == 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Box(
                                modifier = Modifier.width(120.dp).height(2.5.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.35f))
                            ) {
                                Box(modifier = Modifier.fillMaxWidth(fraction = volumeLevel.coerceIn(0f, 1f)).fillMaxHeight().background(Color(0xFF00E5FF)))
                            }
                        }
                    }

                    // 🎮 কাস্টম প্লেয়ার ওভারলে কন্ট্রোলস
                    AnimatedVisibility(
                        visible = isControlsVisible,
                        enter = fadeIn(animationSpec = tween(150)),
                        exit = fadeOut(animationSpec = tween(200)),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))) {
                            if (!isScreenLocked) {
                                // 🔝 টপ বার
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.TopCenter)
                                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(onClick = onBackClick, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "${cleanDramaTitle(playerState.content?.title ?: "Drama")} - EP ${playerState.currentEpisode?.episodeNumber ?: 1}",
                                            color = Color.White,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = { shareDramaLink() }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(18.dp))
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // ⚡ Speed
                                            Surface(
                                                shape = CircleShape,
                                                color = Color.White.copy(alpha = 0.15f),
                                                modifier = Modifier
                                                    .height(26.dp)
                                                    .clickable {
                                                        currentSpeedIndex = (currentSpeedIndex + 1) % speedOptions.size
                                                        val newSpeed = speedOptions[currentSpeedIndex]
                                                        if (isWebMode) {
                                                            persistentWebView.evaluateJavascript("var v = document.querySelector('video'); if (v) v.playbackRate = $newSpeed;", null)
                                                        } else {
                                                            exoPlayer.setPlaybackSpeed(newSpeed)
                                                        }
                                                        Toast.makeText(context, "${newSpeed}X", Toast.LENGTH_SHORT).show()
                                                    }
                                            ) {
                                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                                    Text(
                                                        text = if (playbackSpeed == 1.0f) "1X" else "${playbackSpeed}X",
                                                        color = Color(0xFF00E5FF),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }

                                            // 📐 Crop / TikTok 9:16 Fullscreen
                                            IconButton(
                                                onClick = {
                                                    resizeModeIndex = (resizeModeIndex + 1) % 3
                                                    val modeName = when (resizeModeIndex) {
                                                        1 -> "TikTok 9:16 Fullscreen"
                                                        2 -> "100% Stretch"
                                                        else -> "16:9 Fit"
                                                    }
                                                    Toast.makeText(context, modeName, Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(Icons.Outlined.CropFree, contentDescription = "Aspect Ratio", tint = Color.White, modifier = Modifier.size(18.dp))
                                            }

                                            // 🔄 Rotate Screen Orientation
                                            IconButton(
                                                onClick = { isLandscapeMode = !isLandscapeMode },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(Icons.Default.ScreenRotation, contentDescription = "Rotate", tint = Color.White, modifier = Modifier.size(18.dp))
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // 📺 Cast
                                            IconButton(
                                                onClick = {
                                                    try {
                                                        val intent = Intent(Settings.ACTION_CAST_SETTINGS).apply {
                                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        }
                                                        context.startActivity(intent)
                                                    } catch (_: Exception) {
                                                        Toast.makeText(context, "Make sure TV and phone are on same Wi-Fi", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(Icons.Default.Cast, contentDescription = "Cast", tint = Color.White, modifier = Modifier.size(18.dp))
                                            }

                                            // 🖼️ PiP বাটন
                                            IconButton(onClick = { enterPiPMode() }, modifier = Modifier.size(30.dp)) {
                                                Icon(Icons.Outlined.PictureInPictureAlt, contentDescription = "PiP", tint = Color.White, modifier = Modifier.size(18.dp))
                                            }

                                            // 🔒 Lock Screen
                                            IconButton(onClick = { isScreenLocked = true; isControlsVisible = false }, modifier = Modifier.size(30.dp)) {
                                                Icon(Icons.Outlined.Lock, contentDescription = "Lock", tint = Color.White, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }

                                // 🎯 সেন্টার কন্ট্রোলস (-10s, Play/Pause, +10s)
                                Row(
                                    modifier = Modifier.align(Alignment.Center),
                                    horizontalArrangement = Arrangement.spacedBy(48.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "-10s",
                                            color = Color(0xFF00E5FF),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .offset(y = (-32).dp)
                                                .alpha(rewindAlpha)
                                        )

                                        IconButton(
                                            onClick = { handleSeek(-10) },
                                            modifier = Modifier.size(46.dp).rotate(rewindRotation.value)
                                        ) {
                                            SleekSkipIconOnline(isForward = false, color = Color.White)
                                        }
                                    }

                                    IconButton(
                                        onClick = { handlePlayPause() },
                                        modifier = Modifier.size(56.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "Play/Pause",
                                            tint = Color.White,
                                            modifier = Modifier.size(48.dp)
                                        )
                                    }

                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "+10s",
                                            color = Color(0xFF00E5FF),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .offset(y = (-32).dp)
                                                .alpha(forwardAlpha)
                                        )

                                        IconButton(
                                            onClick = { handleSeek(10) },
                                            modifier = Modifier.size(46.dp).rotate(forwardRotation.value)
                                        ) {
                                            SleekSkipIconOnline(isForward = true, color = Color.White)
                                        }
                                    }
                                }

                                // 🔻 বটম স্লিম টাইমলাইন
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomCenter)
                                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = formatTime(if (isUserSeeking) seekPosition else currentPositionMs),
                                        color = Color.White,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )

                                    SleekOnlineTimeline(
                                        currentPositionMs = if (isUserSeeking) seekPosition else currentPositionMs,
                                        totalDurationMs = totalDurationMs,
                                        onSeekStarted = { isUserSeeking = true },
                                        onSeeking = { seekPosition = it },
                                        onSeekFinished = {
                                            currentPositionMs = it
                                            if (isWebMode) {
                                                val targetSec = it / 1000f
                                                persistentWebView.evaluateJavascript("var v = document.querySelector('video'); if (v) v.currentTime = $targetSec;", null)
                                            } else {
                                                exoPlayer.seekTo(it)
                                            }
                                            isUserSeeking = false
                                        },
                                        modifier = Modifier.weight(1f)
                                    )

                                    Text(
                                        text = formatTime(totalDurationMs),
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )

                                    IconButton(
                                        onClick = { isLandscapeMode = !isLandscapeMode },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen", tint = Color.White, modifier = Modifier.size(19.dp))
                                    }
                                }
                            }
                        }
                    }

                    // 🔓 আনলক বাটন
                    if (isScreenLocked) {
                        IconButton(
                            onClick = { isScreenLocked = false; isControlsVisible = true },
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp)
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.7f))
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "Unlock", tint = Color(0xFFFF5252), modifier = Modifier.size(24.dp))
                        }
                    }
                }

                // =========================================================================
                // 📑 ২. ড্রামা ডিটেইলস, এপিসোড ও কমেন্টস সেকশন (নন-ফুলস্ক্রিন মোডে)
                // =========================================================================
                if (!isLandscapeMode) {
                    if (selectedThreadParentComment != null) {
                        CommentRepliesThreadView(
                            parentComment = selectedThreadParentComment!!,
                            dramaContent = playerState.content,
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
                        PullToRefreshBox(
                            isRefreshing = isRefreshing,
                            onRefresh = {
                                coroutineScope.launch {
                                    isRefreshing = true
                                    viewModel.loadDramaDetails(slug, context)
                                    viewModel.refreshComments()
                                    val combined = (playerState.recommendations + homeState.popularDramas)
                                        .distinctBy { it.slug }
                                        .filter { it.slug != slug }
                                    shuffledRecommendations = combined.shuffled()
                                    delay(500)
                                    isRefreshing = false
                                }
                            },
                            state = pullRefreshState,
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
                                val content = playerState.content
                                val currentEp = playerState.currentEpisode

                                if (content != null) {
                                    val shortCleanTitle = cleanDramaTitle(content.title)

                                    // Title Row
                                    item {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = shortCleanTitle,
                                                color = TextPrimary,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(end = 8.dp)
                                            )

                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
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
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                    ) {
                                                        Icon(Icons.Default.SkipPrevious, contentDescription = "Pre", tint = Color(0xFFB0B7C6), modifier = Modifier.size(13.dp))
                                                        Text("Pre", color = Color(0xFFB0B7C6), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
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
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                    ) {
                                                        Text("Next", color = Color(0xFFB0B7C6), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                        Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color(0xFFB0B7C6), modifier = Modifier.size(13.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Metadata Row
                                    item {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                                            ) {
                                                Text("📅", fontSize = 11.sp)
                                                Text(content.releaseYear.ifBlank { "2024" }, color = Color(0xFF8E95A5), fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                                                Text("•", color = Color(0xFF4C5466), fontSize = 11.sp)
                                                Icon(Icons.Default.Star, contentDescription = null, tint = GoldVip, modifier = Modifier.size(13.dp))
                                                Text(if (content.rating > 0) String.format(Locale.US, "%.1f", content.rating) else "8.9", color = GoldVip, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                                Text("•", color = Color(0xFF4C5466), fontSize = 11.sp)

                                                Text(
                                                    text = if (isDescriptionExpanded) "less" else "...more",
                                                    color = TealAccent,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier
                                                        .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                                                        .padding(2.dp)
                                                )
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
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

                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    Text("👁", fontSize = 11.sp)
                                                    Text("${playerState.viewsCount.coerceAtLeast(48L)}", color = Color(0xFFADB3C2), fontSize = 11.sp)
                                                }

                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                    modifier = Modifier.clickable {
                                                        selectedTab = PlayerTab.COMMENTS
                                                        viewModel.refreshComments()
                                                    }
                                                ) {
                                                    Text("💬", fontSize = 11.sp)
                                                    Text("${playerState.comments.size}", color = Color(0xFFADB3C2), fontSize = 11.sp)
                                                }

                                                Icon(
                                                    imageVector = if (playerState.isInWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                                    contentDescription = "Bookmark",
                                                    tint = if (playerState.isInWatchlist) TealAccent else Color(0xFFADB3C2),
                                                    modifier = Modifier
                                                        .size(15.dp)
                                                        .clickable { viewModel.toggleWatchlist() }
                                                )
                                            }
                                        }
                                    }

                                    // Description
                                    item {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            AnimatedVisibility(
                                                visible = isDescriptionExpanded,
                                                enter = expandVertically() + fadeIn(),
                                                exit = shrinkVertically() + fadeOut()
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = content.description?.takeIf { it.isNotBlank() } ?: content.synopsis,
                                                        color = Color(0xFFCCD0DB),
                                                        fontSize = 12.sp,
                                                        lineHeight = 17.sp
                                                    )
                                                    Text(
                                                        text = "📌 Language: ${content.dubBadge.ifBlank { content.language }}\n📌 Quality: ${content.quality}\n📌 Episodes: ${content.totalEpisodes}",
                                                        color = Color(0xFF94A3B8),
                                                        fontSize = 11.sp,
                                                        lineHeight = 16.sp,
                                                        modifier = Modifier.padding(top = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Episode Selector Pills
                                    if (playerState.episodes.isNotEmpty()) {
                                        item {
                                            LazyRow(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                items(
                                                    count = playerState.episodes.size,
                                                    key = { index -> playerState.episodes[index].episodeId }
                                                ) { index ->
                                                    val ep = playerState.episodes[index]
                                                    val isSelected = currentEp?.episodeNumber == ep.episodeNumber
                                                    val isEpLocked = shouldLockEpisodes && ep.isLocked

                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = if (isSelected) Color(0xFF0F261C) else Color(0xFF131722),
                                                        border = BorderStroke(
                                                            width = if (isSelected) 1.5.dp else 1.dp,
                                                            color = if (isSelected) Color(0xFF00D166) else Color(0xFF222838)
                                                        ),
                                                        modifier = Modifier
                                                            .widthIn(min = 84.dp)
                                                            .clickable {
                                                                if (isEpLocked) {
                                                                    viewModel.showEpisodeUnlockModal(ep)
                                                                } else {
                                                                    viewModel.selectEpisode(ep)
                                                                }
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
                                    }

                                    // Banner Ad
                                    item {
                                        StartAppBanner(
                                            isVip = playerState.isVip,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 4.dp)
                                        )
                                    }

                                    // Sticky Header Tabs
                                    stickyHeader {
                                        Surface(
                                            color = Color(0xFF0C0F15),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
                                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.clickable { selectedTab = PlayerTab.FOR_YOU }) {
                                                    Text(
                                                        text = "For you",
                                                        color = if (selectedTab == PlayerTab.FOR_YOU) Color.White else Color(0xFF8E95A5),
                                                        fontSize = 13.5.sp,
                                                        fontWeight = if (selectedTab == PlayerTab.FOR_YOU) FontWeight.Bold else FontWeight.Medium
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .height(2.5.dp)
                                                            .width(42.dp)
                                                            .clip(RoundedCornerShape(2.dp))
                                                            .background(if (selectedTab == PlayerTab.FOR_YOU) Color(0xFF00E5FF) else Color.Transparent)
                                                    )
                                                }

                                                Column(modifier = Modifier.clickable {
                                                    selectedTab = PlayerTab.COMMENTS
                                                    viewModel.refreshComments()
                                                }) {
                                                    Text(
                                                        text = "Comments(${playerState.comments.size})",
                                                        color = if (selectedTab == PlayerTab.COMMENTS) Color.White else Color(0xFF8E95A5),
                                                        fontSize = 13.5.sp,
                                                        fontWeight = if (selectedTab == PlayerTab.COMMENTS) FontWeight.Bold else FontWeight.Medium
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .height(2.5.dp)
                                                            .width(55.dp)
                                                            .clip(RoundedCornerShape(2.dp))
                                                            .background(if (selectedTab == PlayerTab.COMMENTS) Color(0xFF00E5FF) else Color.Transparent)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Tab 1: For You Grid
                                    if (selectedTab == PlayerTab.FOR_YOU) {
                                        val displayList = shuffledRecommendations.ifEmpty {
                                            (playerState.recommendations + homeState.popularDramas)
                                                .distinctBy { it.slug }
                                                .filter { it.slug != slug }
                                        }
                                        val dramaRows = displayList.chunked(3)

                                        items(
                                            count = dramaRows.size,
                                            key = { index -> dramaRows[index].firstOrNull()?.slug ?: index }
                                        ) { rowIndex ->
                                            val rowDramas = dramaRows[rowIndex]
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                for (drama in rowDramas) {
                                                    val cardTitle = cleanDramaTitle(drama.title)

                                                    Column(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .clickable { onRelatedDramaClick(drama.slug) }
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .aspectRatio(0.72f)
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .border(
                                                                    width = 1.dp,
                                                                    brush = shiningBorderBrush,
                                                                    shape = RoundedCornerShape(8.dp)
                                                                )
                                                                .background(Color(0xFF141A26))
                                                        ) {
                                                            AsyncImage(
                                                                model = drama.posterUrl ?: drama.bannerUrl,
                                                                contentDescription = cardTitle,
                                                                modifier = Modifier.fillMaxSize(),
                                                                contentScale = ContentScale.Crop
                                                            )

                                                            val rawBadge = drama.dubBadge.ifBlank { drama.language }
                                                            if (rawBadge.isNotBlank()) {
                                                                val isBangla = rawBadge.contains("bangla", ignoreCase = true) || rawBadge.contains("বাংলা", ignoreCase = true)
                                                                val badgeBgColor = if (isBangla) Color(0xFFFFC107) else Color(0xFF0080FF)
                                                                val badgeTextColor = if (isBangla) Color.Black else Color.White

                                                                Box(
                                                                    modifier = Modifier
                                                                        .align(Alignment.TopEnd)
                                                                        .clip(RoundedCornerShape(topEnd = 8.dp, bottomStart = 5.dp))
                                                                        .background(badgeBgColor)
                                                                        .padding(horizontal = 4.5.dp, vertical = 1.5.dp)
                                                                    ) {
                                                                        Text(
                                                                            text = rawBadge,
                                                                            color = badgeTextColor,
                                                                            fontSize = 8.sp,
                                                                            fontWeight = FontWeight.Bold,
                                                                            letterSpacing = (-0.2).sp
                                                                        )
                                                                    }
                                                            }

                                                            Box(
                                                                modifier = Modifier
                                                                    .align(Alignment.BottomStart)
                                                                    .padding(4.dp)
                                                                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                                                ) {
                                                                    Text(
                                                                        text = "${drama.totalEpisodes} Episodes",
                                                                        color = Color(0xFFE2E8F0),
                                                                        fontSize = 9.sp
                                                                    )
                                                                }
                                                        }

                                                        Spacer(modifier = Modifier.height(4.dp))

                                                        Text(
                                                            text = cardTitle,
                                                            color = Color(0xFFCCD0DB),
                                                            fontSize = 11.5.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                                val remaining = 3 - rowDramas.size
                                                if (remaining > 0) {
                                                    for (i in 0 until remaining) {
                                                        Spacer(modifier = Modifier.weight(1f))
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Tab 2: Comments
                                    if (selectedTab == PlayerTab.COMMENTS) {
                                        item {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(38.dp)
                                                        .clip(CircleShape)
                                                    .background(Color(0xFF161F30)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(userInitials, color = Color(0xFFFFC107), fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                                }

                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(42.dp)
                                                        .clip(RoundedCornerShape(21.dp))
                                                        .background(Color(0xFF131926))
                                                        .padding(horizontal = 16.dp),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    if (inlineCommentText.isEmpty()) {
                                                        Text("Add a comment...", color = Color(0xFF64748B), fontSize = 13.5.sp)
                                                    }
                                                    BasicTextField(
                                                        value = inlineCommentText,
                                                        onValueChange = { inlineCommentText = it },
                                                        textStyle = TextStyle(color = Color.White, fontSize = 13.5.sp),
                                                        cursorBrush = SolidColor(Color(0xFFFFC107)),
                                                        singleLine = true,
                                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                                        keyboardActions = KeyboardActions(onSend = {
                                                            val text = inlineCommentText.trim()
                                                            if (text.isNotBlank()) {
                                                                viewModel.postComment(text)
                                                                inlineCommentText = ""
                                                                keyboardController?.hide()
                                                            }
                                                        }),
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }

                                                IconButton(
                                                    onClick = {
                                                        val text = inlineCommentText.trim()
                                                        if (text.isNotBlank()) {
                                                            viewModel.postComment(text)
                                                            inlineCommentText = ""
                                                            keyboardController?.hide()
                                                        }
                                                    },
                                                    modifier = Modifier
                                                        .size(42.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFFFFC107))
                                                ) {
                                                    Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.Black, modifier = Modifier.size(19.dp))
                                                }
                                            }
                                        }

                                        if (playerState.comments.isEmpty()) {
                                            item {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 36.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("No comments yet. Be the first to comment!", color = Color(0xFF64748B), fontSize = 13.sp)
                                                }
                                            }
                                        } else {
                                            items(
                                                count = playerState.comments.size,
                                                key = { index -> playerState.comments[index].id }
                                            ) { index ->
                                                val comment = playerState.comments[index]
                                                ModernCommentRowItem(
                                                    comment = comment,
                                                    onLike = { viewModel.toggleCommentLike(comment.id) },
                                                    onOpenReplies = { selectedThreadParentComment = comment },
                                                    onShare = {
                                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                            type = "text/plain"
                                                            putExtra(Intent.EXTRA_TEXT, "${comment.displayName}: ${comment.commentText}")
                                                        }
                                                        context.startActivity(Intent.createChooser(shareIntent, "Share comment"))
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAuthSheet) {
            AuthBottomSheetDialog(
                viewModel = viewModel,
                onDismiss = { showAuthSheet = false }
            )
        }

        // 🌟 ছোট ও কমপ্যাক্ট আনলক এপিসোড ডায়ালগ
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
                                viewModel.unlockEpisodeWithRewardAd(context, slug, lockedTarget)
                                Toast.makeText(context, "Episode ${lockedTarget.episodeNumber} unlocked for 2 hours!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Ad was not completed or failed to load.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        viewModel.unlockEpisodeWithRewardAd(context, slug, lockedTarget)
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
