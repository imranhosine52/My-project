@file:OptIn(UnstableApi::class)

package com.example.ui.screens.shorts

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
import com.example.data.model.ContentItemDto
import com.example.data.model.EpisodeDto
import com.example.ui.components.YouTubeCommentsBottomSheet
import com.example.ui.screens.SleekSkipIconOnline
import com.example.ui.viewmodel.DramaFlixViewModel
import com.example.util.R2DownloadManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun findActivity(context: Context): Activity? {
    var current = context
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

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

@kotlin.OptIn(ExperimentalFoundationApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ShortsPlayerScreen(
    slug: String,
    viewModel: DramaFlixViewModel,
    onBackClick: () -> Unit,
    onNavigateToVip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { findActivity(context) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(slug) {
        viewModel.loadDramaDetails(slug, context)
    }

    val playerState by viewModel.playerUiState.collectAsStateWithLifecycle()
    val authState by viewModel.authUiState.collectAsStateWithLifecycle()
    val homeState by viewModel.homeUiState.collectAsStateWithLifecycle()

    var isPlaying by remember { mutableStateOf(true) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }
    var isBuffering by remember { mutableStateOf(true) }

    var isUserSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableLongStateOf(0L) }

    // 📺 বটম ড্রয়ার, ফুলস্ক্রিন ও পপ-আপ স্টেট
    var isHalfDrawerOpen by remember { mutableStateOf(false) }
    var drawerInitialTab by remember { mutableIntStateOf(1) } // 0 = Introduction, 1 = Episodes
    var isImmersiveFullscreen by rememberSaveable { mutableStateOf(false) }
    var showBatchDownloadDialog by remember { mutableStateOf(false) }
    var showCommentsSheet by remember { mutableStateOf(false) }

    var useWebPlayerFallback by rememberSaveable { mutableStateOf(false) }
    var activeStreamUrl by rememberSaveable { mutableStateOf("") }
    var currentLoadedEpKey by rememberSaveable { mutableStateOf("") }

    // স্কিপ অ্যানিমেশন ট্র্যাকার
    var isRewindActive by remember { mutableStateOf(false) }
    var isForwardActive by remember { mutableStateOf(false) }
    val rewindRotation = remember { Animatable(0f) }
    val forwardRotation = remember { Animatable(0f) }
    val rewindAlpha by animateFloatAsState(targetValue = if (isRewindActive) 1f else 0f, label = "rewindAlpha")
    val forwardAlpha by animateFloatAsState(targetValue = if (isForwardActive) 1f else 0f, label = "forwardAlpha")

    val content = playerState.content
        ?: homeState.popularDramas.find { it.slug == slug }
        ?: ContentItemDto(title = slug.replace("-", " "), slug = slug, type = "shorts")

    val effectiveEpisodes = remember(playerState.episodes, content.totalEpisodes) {
        if (playerState.episodes.isNotEmpty()) playerState.episodes else {
            (1..(content.totalEpisodes.coerceAtLeast(1))).map { num ->
                EpisodeDto(episodeNumber = num, isLocked = false)
            }
        }
    }

    val totalEpCount = effectiveEpisodes.size

    // 📱 টিকটক স্টাইল ভার্টিক্যাল পেজার
    val verticalPagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { totalEpCount }
    )

    val currentEp = effectiveEpisodes.getOrElse(verticalPagerState.currentPage) { effectiveEpisodes.first() }
    val currentEpNum = currentEp.episodeNumber

    LaunchedEffect(verticalPagerState.currentPage) {
        val target = effectiveEpisodes.getOrNull(verticalPagerState.currentPage)
        if (target != null) {
            viewModel.selectEpisode(target)
        }
    }

    val shortDramaRecommendations = remember(homeState.popularDramas, homeState.shortsContent, slug) {
        (homeState.shortsContent + homeState.popularDramas.filter { it.isShorts })
            .distinctBy { it.slug }
            .filter { it.slug != slug }
            .take(12)
    }

    // =========================================================================
    // ⚡ ১. FastStart ExoPlayer (সুপার ফাস্ট প্রি-বাফারিং ও প্রি-লোডিং ইঞ্জিন)
    // =========================================================================
    val exoPlayer = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setUserAgent("PlayDramaFlix Shorts Player")

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        // 🎯 ব্যাকগ্রাউন্ডে পরবর্তী পর্ব দ্রুত বাফার করে রাখার কনফিগারেশন
        val fastPreloadLoadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                2000,   // Min buffer: ২ সেকেন্ড হলেই ইনস্ট্যান্ট প্লে শুরু
                30000,  // Max buffer: ৩০ সেকেন্ড পর্যন্ত ব্যাকগ্রাউন্ডে বাফার রাখবে
                500,    // Buffer for playback: ৫০০ms হলেই সাথে সাথে চলবে
                1000    // Rebuffer
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(fastPreloadLoadControl)
            .build().apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_OFF // 👈 লুপ বন্ধ করে অটো নেক্সট করা হলো
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true
                )
            }
    }

    // 🌐 ২. Web Embed Player
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

    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            exoPlayer.release()
            persistentWebView.destroy()
        }
    }

    // =========================================================================
    // 🚀 ১টি পর্ব শেষ হলে স্বয়ংক্রিয়ভাবে পরবর্তী পর্ব প্লে হওয়ার লিসেনার
    // =========================================================================
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = (state == Player.STATE_BUFFERING)
                if (state == Player.STATE_READY) {
                    totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
                    exoPlayer.play()
                } else if (state == Player.STATE_ENDED) {
                    // 🎯 ১টি পর্ব শেষ হলে স্বয়ংক্রিয়ভাবে পরবর্তী পর্বে চলে যাবে (কোনো বাফারিং ছাড়া)
                    if (verticalPagerState.currentPage < totalEpCount - 1) {
                        coroutineScope.launch {
                            verticalPagerState.animateScrollToPage(
                                page = verticalPagerState.currentPage + 1,
                                animationSpec = tween(durationMillis = 400, easing = LinearEasing)
                            )
                        }
                    }
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

    LaunchedEffect(isPlaying, isUserSeeking) {
        while (isPlaying && !isUserSeeking) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
            delay(350L)
        }
    }

    LaunchedEffect(isControlsVisible, isPlaying) {
        if (isControlsVisible && isPlaying && !isHalfDrawerOpen) {
            delay(4000L)
            isControlsVisible = false
        }
    }

    fun triggerSkip(seconds: Int) {
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

    // =========================================================================
    // 🎯 বর্তমান ও পরবর্তী পর্বগুলো প্রি-লোড করার লজিক (Pre-buffering Engine)
    // =========================================================================
    LaunchedEffect(currentEp.episodeNumber, currentEp.episodeId, slug) {
        val serverVideoUrl = currentEp.appStreamUrl?.takeIf { it.isNotBlank() }
            ?: currentEp.videoUrl?.takeIf { it.isNotBlank() }
            ?: currentEp.embedUrl?.takeIf { it.isNotBlank() }
            ?: currentEp.resolveR2StreamUrl(slug)

        val epUniqueKey = "${currentEp.episodeId}_${currentEp.episodeNumber}"
        if (epUniqueKey == currentLoadedEpKey && activeStreamUrl == serverVideoUrl) return@LaunchedEffect

        currentLoadedEpKey = epUniqueKey
        activeStreamUrl = serverVideoUrl

        if (isWebEmbedUrl(serverVideoUrl)) {
            useWebPlayerFallback = true
            exoPlayer.pause()
            persistentWebView.loadUrl(serverVideoUrl)
        } else {
            useWebPlayerFallback = false
            try {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()

                // বর্তমান পর্বের মিডিয়া আইটেম
                val currentMediaItem = MediaItem.Builder()
                    .setUri(Uri.parse(serverVideoUrl))
                    .setMimeType(if (serverVideoUrl.contains(".m3u8")) MimeTypes.APPLICATION_M3U8 else MimeTypes.APPLICATION_MP4)
                    .build()

                val playlist = mutableListOf(currentMediaItem)

                // 🚀 পরবর্তী পর্বগুলো প্লেলিস্টে যুক্ত করা যাতে ব্যাকগ্রাউন্ডে প্রি-লোড হয়ে থাকে
                val nextIdx = verticalPagerState.currentPage + 1
                if (nextIdx < totalEpCount) {
                    val nextEp = effectiveEpisodes[nextIdx]
                    val nextUrl = nextEp.resolveR2StreamUrl(slug)
                    if (!isWebEmbedUrl(nextUrl)) {
                        val nextMediaItem = MediaItem.Builder()
                            .setUri(Uri.parse(nextUrl))
                            .setMimeType(if (nextUrl.contains(".m3u8")) MimeTypes.APPLICATION_M3U8 else MimeTypes.APPLICATION_MP4)
                            .build()
                        playlist.add(nextMediaItem)
                    }
                }

                exoPlayer.setMediaItems(playlist, 0, 0L)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
                exoPlayer.play()
            } catch (_: Exception) {
                useWebPlayerFallback = true
                persistentWebView.loadUrl(serverVideoUrl)
            }
        }
    }

    BackHandler {
        if (showBatchDownloadDialog) {
            showBatchDownloadDialog = false
        } else if (showCommentsSheet) {
            showCommentsSheet = false
        } else if (isHalfDrawerOpen) {
            isHalfDrawerOpen = false
        } else if (isImmersiveFullscreen) {
            isImmersiveFullscreen = false
        } else {
            onBackClick()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            // 🎯 সিঙ্গেল ও ডাবল ট্যাপ লজিক
            .pointerInput(isImmersiveFullscreen, isControlsVisible, isHalfDrawerOpen) {
                detectTapGestures(
                    onTap = {
                        if (!isImmersiveFullscreen && !isHalfDrawerOpen) {
                            isControlsVisible = !isControlsVisible
                        }
                    },
                    onDoubleTap = {
                        if (!isHalfDrawerOpen) {
                            // 👈 ডাবল ট্যাপে ফুলস্ক্রিন মোড অন/অফ হবে
                            isImmersiveFullscreen = !isImmersiveFullscreen
                            if (isImmersiveFullscreen) {
                                isControlsVisible = false
                            }
                        }
                    }
                )
            }
    ) {
        // =========================================================================
        // 🎬 ১. একক ভিডিও প্লেয়ার সারফেস (কখনোই ব্ল্যাক স্ক্রিন হবে না)
        // =========================================================================
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(if (isHalfDrawerOpen) 0.44f else 1f)
                    .background(Color.Black)
            ) {
                if (useWebPlayerFallback) {
                    AndroidView(
                        factory = {
                            (persistentWebView.parent as? ViewGroup)?.removeView(persistentWebView)
                            persistentWebView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            }
                        },
                        update = { view ->
                            view.player = exoPlayer
                            view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (isBuffering && !useWebPlayerFallback) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = Color(0xFF00E676),
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
            }

            // =========================================================================
            // 📑 ২. হাফ-স্ক্রিন বটম ড্রয়ার (Episodes ও Introduction)
            // =========================================================================
            if (isHalfDrawerOpen) {
                ShortsHalfDrawerSheet(
                    content = content,
                    episodes = effectiveEpisodes,
                    currentEpNum = currentEpNum,
                    initialTab = drawerInitialTab,
                    isInWatchlist = playerState.isInWatchlist,
                    shortDramaRecommendations = shortDramaRecommendations,
                    onSelectEpisode = { ep: EpisodeDto ->
                        coroutineScope.launch {
                            val targetIndex = effectiveEpisodes.indexOfFirst { it.episodeNumber == ep.episodeNumber }
                            if (targetIndex != -1) {
                                verticalPagerState.scrollToPage(targetIndex)
                            }
                        }
                    },
                    onSelectRecommendation = { newSlug: String ->
                        isHalfDrawerOpen = false
                        viewModel.loadDramaDetails(newSlug, context)
                    },
                    onToggleWatchlist = {
                        if (!authState.isLoggedIn) viewModel.showAuthDialog(true)
                        else viewModel.toggleWatchlist()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // =========================================================================
        // 📱 ৩. টিকটক পেজার ওভারলে
        // =========================================================================
        if (!isHalfDrawerOpen) {
            VerticalPager(
                state = verticalPagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !isUserSeeking
            ) { page ->
                val pageEp = effectiveEpisodes.getOrElse(page) { effectiveEpisodes.first() }

                Box(modifier = Modifier.fillMaxSize()) {
                    // =========================================================================
                    // 🔝 টপ বার: [< EpX] ও [ডাউনলোড আইকন ↓] (ফুলস্ক্রিন থাকলেও সব সময় দৃশ্যমান থাকবে)
                    // =========================================================================
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                            .statusBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.clickable { onBackClick() }
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                "Ep${pageEp.episodeNumber}",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(
                            onClick = { showBatchDownloadDialog = true },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f))
                        ) {
                            Icon(
                                Icons.Outlined.FileDownload,
                                contentDescription = "Download",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // =========================================================================
                    // ⏯️ অন-স্ক্রিন স্কিপ ও প্লে/পজ বাটন (সিঙ্গেল ট্যাপে হাইড/শো হবে)
                    // =========================================================================
                    if (isControlsVisible && !isImmersiveFullscreen) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(48.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // ⏪ -10s বাটন
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "-10s",
                                        color = Color(0xFF00E5FF),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.offset(y = (-30).dp).alpha(rewindAlpha)
                                    )
                                    IconButton(
                                        onClick = { triggerSkip(-10) },
                                        modifier = Modifier.size(48.dp).rotate(rewindRotation.value)
                                    ) {
                                        SleekSkipIconOnline(isForward = false, color = Color.White)
                                    }
                                }

                                // ⏯️ Play/Pause বাটন
                                IconButton(
                                    onClick = {
                                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                    },
                                    modifier = Modifier.size(64.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = Color.White,
                                        modifier = Modifier.size(44.dp)
                                    )
                                }

                                // ⏩ +10s বাটন
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "+10s",
                                        color = Color(0xFF00E5FF),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.offset(y = (-30).dp).alpha(forwardAlpha)
                                    )
                                    IconButton(
                                        onClick = { triggerSkip(10) },
                                        modifier = Modifier.size(48.dp).rotate(forwardRotation.value)
                                    ) {
                                        SleekSkipIconOnline(isForward = true, color = Color.White)
                                    }
                                }
                            }
                        }
                    }

                    // 📱 অ্যাকশন কলাম ও নিচের ইনফো (ফুলস্ক্রিন মোডে হাইড থাকবে)
                    if (!isImmersiveFullscreen) {
                        ShortsActionColumn(
                            context = context,
                            title = content.title,
                            slug = slug,
                            likesCount = playerState.likesCount.toLong(),
                            commentsCount = playerState.comments.size,
                            isLiked = playerState.isLiked,
                            isInWatchlist = playerState.isInWatchlist,
                            onLikeClick = {
                                if (!authState.isLoggedIn) viewModel.showAuthDialog(true)
                                else viewModel.toggleLikeDrama()
                            },
                            onCommentClick = {
                                viewModel.refreshComments()
                                showCommentsSheet = true
                            },
                            onSaveClick = {
                                if (!authState.isLoggedIn) viewModel.showAuthDialog(true)
                                else viewModel.toggleWatchlist()
                            },
                            modifier = Modifier.align(Alignment.BottomEnd)
                        )

                        ShortsBottomOverlay(
                            content = content,
                            currentEpNum = pageEp.episodeNumber,
                            totalEpCount = totalEpCount,
                            currentPositionMs = currentPositionMs,
                            totalDurationMs = totalDurationMs,
                            isUserSeeking = isUserSeeking,
                            seekPosition = seekPosition,
                            onSeekStarted = { isUserSeeking = true },
                            onSeeking = { seekPosition = it },
                            onSeekFinished = {
                                exoPlayer.seekTo(it)
                                currentPositionMs = it
                                isUserSeeking = false
                            },
                            onOpenIntroductionTab = {
                                drawerInitialTab = 0
                                isHalfDrawerOpen = true
                            },
                            onOpenEpisodesTab = {
                                drawerInitialTab = 1
                                isHalfDrawerOpen = true
                            },
                            modifier = Modifier.align(Alignment.BottomStart)
                        )
                    }
                }
            }
        }

        // =========================================================================
        // 📥 ৪. ৩ নম্বর ছবির হুবহু ব্যাচ ডাউনলোড কার্ড
        // =========================================================================
        if (showBatchDownloadDialog) {
            ShortsBatchDownloadSheet(
                title = content.title,
                episodes = effectiveEpisodes,
                onDismiss = { showBatchDownloadDialog = false },
                onDownloadSelected = { selectedList ->
                    showBatchDownloadDialog = false
                    selectedList.forEach { ep ->
                        R2DownloadManager.startDownload(
                            context = context,
                            downloadUrl = ep.resolveDownloadUrl(slug),
                            title = content.title,
                            episodeNumber = ep.episodeNumber,
                            isMovie = false
                        )
                    }
                    Toast.makeText(
                        context,
                        "📥 Download started for ${selectedList.size} episodes!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }

        // 💬 ৫. রিয়েল কমেন্ট বটম শিট
        if (showCommentsSheet) {
            YouTubeCommentsBottomSheet(
                comments = playerState.comments,
                totalCommentsCount = playerState.comments.size,
                isLoading = playerState.isCommentsLoading,
                currentUserName = authState.userProfile?.displayName ?: "User",
                onDismiss = { showCommentsSheet = false },
                onAddComment = { commentText, parentId ->
                    viewModel.postComment(commentText, parentId)
                },
                onLikeComment = { commentId ->
                    viewModel.toggleCommentLike(commentId)
                },
                onShareComment = { commentId ->
                    viewModel.recordCommentShare(commentId)
                }
            )
        }
    }
}
