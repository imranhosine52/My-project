@file:OptIn(UnstableApi::class)

package com.example.ui.screens.shorts

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup          // 👈 এই লাইনটি যোগ করুন
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
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
import com.example.data.model.ContentItemDto
import com.example.data.model.EpisodeDto
import com.example.ui.components.YouTubeCommentsBottomSheet
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

    // 📺 বটম ড্রয়ার ও ফুলস্ক্রিন স্টেট
    var isHalfDrawerOpen by remember { mutableStateOf(false) }
    var isImmersiveFullscreen by remember { mutableStateOf(false) }
    var showBatchDownloadDialog by remember { mutableStateOf(false) }
    var showCommentsSheet by remember { mutableStateOf(false) }

    var useWebPlayerFallback by rememberSaveable { mutableStateOf(false) }
    var activeStreamUrl by rememberSaveable { mutableStateOf("") }
    var currentLoadedEpKey by rememberSaveable { mutableStateOf("") }

    // স্কিপ এনিমেশন
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
    val currentEp = playerState.currentEpisode ?: effectiveEpisodes.firstOrNull()
    val currentEpNum = currentEp?.episodeNumber ?: 1

    // 🎬 শুধুমাত্র Shorts Drama রিকমেন্ডেশন ফিল্টার
    val shortDramaRecommendations = remember(homeState.popularDramas, homeState.shortsContent, slug) {
        (homeState.shortsContent + homeState.popularDramas.filter { it.isShorts })
            .distinctBy { it.slug }
            .filter { it.slug != slug }
            .take(6)
    }

    // ⚡ ১. FastStart ExoPlayer
    val exoPlayer = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setUserAgent("PlayDramaFlix Shorts Player")

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(2000, 12000, 800, 1500)
            .build()

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build().apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_ALL
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

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = (state == Player.STATE_BUFFERING)
                if (state == Player.STATE_READY) {
                    totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
                    exoPlayer.play()
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

    // 🎬 ভিডিও স্ট্রিম সিলেক্টর
    LaunchedEffect(currentEp?.episodeNumber, currentEp?.episodeId, slug) {
        if (currentEp != null) {
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
                    val mediaItem = MediaItem.Builder()
                        .setUri(Uri.parse(serverVideoUrl))
                        .setMimeType(if (serverVideoUrl.contains(".m3u8")) MimeTypes.APPLICATION_M3U8 else MimeTypes.APPLICATION_MP4)
                        .build()
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                    exoPlayer.play()
                } catch (_: Exception) {
                    useWebPlayerFallback = true
                    persistentWebView.loadUrl(serverVideoUrl)
                }
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
            // 📱 সোয়াইপ করে পরবর্তী/পূর্ববর্তী পর্বে যাওয়ার জেসচার
            .pointerInput(Unit) {
                var totalDragY = 0f
                detectVerticalDragGestures(
                    onDragStart = { totalDragY = 0f },
                    onDragEnd = {
                        if (totalDragY < -150f) {
                            viewModel.playNextEpisode()
                        } else if (totalDragY > 150f) {
                            viewModel.playPreviousEpisode()
                        }
                    },
                    onVerticalDrag = { _, dragAmount -> totalDragY += dragAmount }
                )
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // =========================================================================
            // 📱 ১. ভিডিও প্লেয়ার ফ্রেম
            // =========================================================================
            ShortsVideoSurface(
                exoPlayer = exoPlayer,
                persistentWebView = persistentWebView,
                useWebPlayerFallback = useWebPlayerFallback,
                isBuffering = isBuffering,
                currentEpNum = currentEpNum,
                isPlaying = isPlaying,
                isControlsVisible = isControlsVisible && !isHalfDrawerOpen,
                isRewindActive = isRewindActive,
                isForwardActive = isForwardActive,
                rewindRotation = rewindRotation.value,
                forwardRotation = forwardRotation.value,
                rewindAlpha = rewindAlpha,
                forwardAlpha = forwardAlpha,
                onBackClick = { onBackClick() },
                onDownloadClick = { showBatchDownloadDialog = true },
                onTapSurface = {
                    if (isHalfDrawerOpen) isHalfDrawerOpen = false
                    else if (isImmersiveFullscreen) isImmersiveFullscreen = false
                    else isControlsVisible = !isControlsVisible
                },
                onDoubleTapSkip = { seconds -> triggerSkip(seconds) },
                onPlayPauseClick = {
                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(if (isHalfDrawerOpen) 0.85f else 1f)
            )

            // =========================================================================
            // 📑 ২. হাফ-স্ক্রিন বটম ড্রয়ার (Episodes ও Introduction)
            // =========================================================================
            if (isHalfDrawerOpen) {
                ShortsHalfDrawerSheet(
                    content = content,
                    episodes = effectiveEpisodes,
                    currentEpNum = currentEpNum,
                    isInWatchlist = playerState.isInWatchlist,
                    shortDramaRecommendations = shortDramaRecommendations,
                    onSelectEpisode = { ep -> viewModel.selectEpisode(ep) },
                    onSelectRecommendation = { newSlug ->
                        isHalfDrawerOpen = false
                        viewModel.loadDramaDetails(newSlug, context)
                    },
                    onToggleWatchlist = {
                        if (!authState.isLoggedIn) viewModel.showAuthDialog(true)
                        else viewModel.toggleWatchlist()
                    },
                    modifier = Modifier.weight(1.15f)
                )
            }
        }

        // =========================================================================
        // 📱 ৩. ডানপাশের অ্যাকশন কলাম (Like, Comment, Share)
        // =========================================================================
        if (!isHalfDrawerOpen && !isImmersiveFullscreen) {
            ShortsActionColumn(
                context = context,
                title = content.title,
                slug = slug,
                likesCount = playerState.likesCount.toLong(),
                commentsCount = playerState.comments.size,
                isLiked = playerState.isLiked,
                onLikeClick = {
                    if (!authState.isLoggedIn) viewModel.showAuthDialog(true)
                    else viewModel.toggleLikeDrama()
                },
                onCommentClick = {
                    viewModel.refreshComments()
                    showCommentsSheet = true
                },
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }

        // =========================================================================
        // 📑 ৪. নিচের টাইটেল ও ড্রয়ার ট্রিগার বার
        // =========================================================================
        if (!isHalfDrawerOpen && !isImmersiveFullscreen) {
            ShortsBottomOverlay(
                content = content,
                currentEpNum = currentEpNum,
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
                onOpenDrawer = { isHalfDrawerOpen = true },
                onToggleFullscreen = { isImmersiveFullscreen = !isImmersiveFullscreen },
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }

        // =========================================================================
        // 📥 ৫. ৩ নম্বর ছবির হুবহু ব্যাচ ডাউনলোড কার্ড
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
                    Toast.makeText(context, "📥 Download started for ${selectedList.size} episodes!", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // 💬 ৬. রিয়েল কমেন্ট বটম শিট
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
