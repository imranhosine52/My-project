@file:OptIn(UnstableApi::class)

package com.example.ui.screens.shorts

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import com.example.data.model.ContentItemDto
import com.example.data.model.EpisodeDto
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

private fun resolveBestEpisodeUrl(ep: EpisodeDto, slug: String): String {
    return ep.appStreamUrl?.takeIf { it.isNotBlank() }
        ?: ep.videoUrl?.takeIf { it.isNotBlank() }
        ?: ep.resolveR2StreamUrl(slug)
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = remember(context) { findActivity(context) }
    val coroutineScope = rememberCoroutineScope()

    val playerState by viewModel.playerUiState.collectAsStateWithLifecycle()
    val authState by viewModel.authUiState.collectAsStateWithLifecycle()
    val homeState by viewModel.homeUiState.collectAsStateWithLifecycle()

    // 🎯 ইউজারের VIP স্ট্যাটাস পূর্ণাঙ্গভাবে চেক করা
    val isUserVip = playerState.isVip || 
                    authState.isVip || 
                    (authState.userProfile?.isVip == true) || 
                    (authState.userProfile?.plan?.lowercase() == "vip") || 
                    (authState.userProfile?.plan?.lowercase() == "premium")

    // 🎯 ড্রামাভিত্তিক কমেন্ট মেমরি (ড্রামা পরিবর্তন হলে আগের কমেন্ট মুছে যাবে)
    val persistentDramaComments = remember(slug) { mutableStateListOf<Any>() }

    LaunchedEffect(slug) {
        persistentDramaComments.clear()
        viewModel.loadDramaDetails(slug, context)
    }

    LaunchedEffect(playerState.comments, slug) {
        persistentDramaComments.clear()
        playerState.comments.forEach { newComment ->
            persistentDramaComments.add(newComment)
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var isControlsVisible by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }
    var isBuffering by remember { mutableStateOf(false) }

    // 🎯 ভিডিওর আসল অনুপাত অনুযায়ী রেশিও মোড
    var videoResizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    var isUserSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableLongStateOf(0L) }

    var isHalfDrawerOpen by remember { mutableStateOf(false) }
    var drawerInitialTab by remember { mutableIntStateOf(1) }
    var isImmersiveFullscreen by rememberSaveable { mutableStateOf(false) }
    var showBatchDownloadDialog by remember { mutableStateOf(false) }
    var showCommentsSheet by remember { mutableStateOf(false) }

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

    val verticalPagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { totalEpCount }
    )

    val currentEp: EpisodeDto = effectiveEpisodes.getOrElse(verticalPagerState.currentPage) { effectiveEpisodes.first() }
    val currentEpNum = currentEp.episodeNumber

    val currentVideoUrl = remember(currentEp, slug) {
        resolveBestEpisodeUrl(currentEp, slug)
    }

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
    // ⚡ ১. FastStart ExoPlayer ইঞ্জিন
    // =========================================================================
    val exoPlayer = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(8000)
            .setUserAgent("PlayDramaFlix MP4 Engine/1.0")

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val turboLoadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(500, 35000, 100, 250)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(turboLoadControl)
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

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> {
                    exoPlayer.playWhenReady = true
                    exoPlayer.play()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            exoPlayer.release()
        }
    }

    // =========================================================================
    // 🚀 ভিডিও সাইজ ও অনুপাত ডিটেকশন লিসেনার
    // =========================================================================
    DisposableEffect(exoPlayer, totalEpCount) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                val width = videoSize.width
                val height = videoSize.height
                if (width > 0 && height > 0) {
                    val ratio = width.toFloat() / height.toFloat()
                    videoResizeMode = if (ratio <= 0.75f) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = (state == Player.STATE_BUFFERING)
                if (state == Player.STATE_READY) {
                    totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
                    exoPlayer.play()
                } else if (state == Player.STATE_ENDED) {
                    val nextIndex = verticalPagerState.currentPage + 1
                    if (nextIndex < totalEpCount) {
                        coroutineScope.launch {
                            verticalPagerState.scrollToPage(nextIndex)
                        }
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                    val nextIndex = verticalPagerState.currentPage + 1
                    if (nextIndex < totalEpCount) {
                        coroutineScope.launch {
                            verticalPagerState.scrollToPage(nextIndex)
                        }
                    }
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                exoPlayer.prepare()
                exoPlayer.play()
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

    // ব্যাকগ্রাউন্ড প্রিলোডিং ইঞ্জিন
    LaunchedEffect(currentVideoUrl, verticalPagerState.currentPage) {
        if (currentVideoUrl.isBlank()) return@LaunchedEffect

        try {
            val currentExoUri = exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString()

            if (currentExoUri == currentVideoUrl && exoPlayer.playbackState != Player.STATE_IDLE) {
                exoPlayer.play()
            } else {
                val hasNextInQueue = exoPlayer.mediaItemCount > 1
                val queueItemUri = if (hasNextInQueue) exoPlayer.getMediaItemAt(1).localConfiguration?.uri?.toString() else null

                if (queueItemUri == currentVideoUrl) {
                    exoPlayer.seekToNextMediaItem()
                    exoPlayer.play()
                } else {
                    exoPlayer.stop()
                    exoPlayer.clearMediaItems()

                    val currentMediaItem = MediaItem.Builder()
                        .setUri(Uri.parse(currentVideoUrl))
                        .setMimeType(if (currentVideoUrl.contains(".m3u8")) MimeTypes.APPLICATION_M3U8 else MimeTypes.APPLICATION_MP4)
                        .build()
                    exoPlayer.addMediaItem(currentMediaItem)

                    val nextIdx = verticalPagerState.currentPage + 1
                    if (nextIdx < effectiveEpisodes.size) {
                        val nextEp = effectiveEpisodes[nextIdx]
                        val nextUrl = resolveBestEpisodeUrl(nextEp, slug)
                        if (nextUrl.isNotBlank()) {
                            val nextItem = MediaItem.Builder()
                                .setUri(Uri.parse(nextUrl))
                                .setMimeType(if (nextUrl.contains(".m3u8")) MimeTypes.APPLICATION_M3U8 else MimeTypes.APPLICATION_MP4)
                                .build()
                            exoPlayer.addMediaItem(nextItem)
                        }
                    }

                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                    exoPlayer.play()
                }
            }
        } catch (_: Exception) {}
    }

    BackHandler {
        when {
            showBatchDownloadDialog -> showBatchDownloadDialog = false
            showCommentsSheet -> showCommentsSheet = false
            isHalfDrawerOpen -> isHalfDrawerOpen = false
            isImmersiveFullscreen -> isImmersiveFullscreen = false
            else -> onBackClick()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(if (isHalfDrawerOpen) 0.44f else 1f)
            ) {
                ShortsVideoSurface(
                    exoPlayer = exoPlayer,
                    isBuffering = isBuffering || currentVideoUrl.isBlank(),
                    currentEpNum = currentEpNum,
                    isPlaying = isPlaying,
                    isControlsVisible = isControlsVisible,
                    isImmersiveFullscreen = isImmersiveFullscreen,
                    resizeMode = videoResizeMode,
                    onBackClick = onBackClick,
                    onDownloadClick = { showBatchDownloadDialog = true },
                    onTapSurface = {
                        if (!isImmersiveFullscreen && !isHalfDrawerOpen) {
                            isControlsVisible = !isControlsVisible
                        }
                    },
                    onDoubleTapFullscreen = {
                        if (!isHalfDrawerOpen) {
                            isImmersiveFullscreen = !isImmersiveFullscreen
                            if (isImmersiveFullscreen) isControlsVisible = false
                        }
                    },
                    onPlayPauseClick = {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                    },
                    onSeekSkip = { seconds -> triggerSkip(seconds) },
                    modifier = Modifier.fillMaxSize()
                )
            }

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
                    onDismiss = { isHalfDrawerOpen = false },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (!isHalfDrawerOpen) {
            VerticalPager(
                state = verticalPagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !isUserSeeking
            ) { page ->
                val pageEp = effectiveEpisodes.getOrElse(page) { effectiveEpisodes.first() }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(isImmersiveFullscreen, isControlsVisible, isHalfDrawerOpen) {
                            detectTapGestures(
                                onTap = {
                                    if (!isImmersiveFullscreen && !isHalfDrawerOpen) {
                                        isControlsVisible = !isControlsVisible
                                    }
                                },
                                onDoubleTap = {
                                    if (!isHalfDrawerOpen) {
                                        isImmersiveFullscreen = !isImmersiveFullscreen
                                        if (isImmersiveFullscreen) {
                                            isControlsVisible = false
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    if (!isImmersiveFullscreen) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.70f), Color.Transparent)))
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
                                    "Ep ${pageEp.episodeNumber}",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            IconButton(
                                onClick = { showBatchDownloadDialog = true },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.FileDownload,
                                    contentDescription = "Download",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        ShortsActionColumn(
                            context = context,
                            title = content.title,
                            slug = slug,
                            likesCount = playerState.likesCount.toLong(),
                            commentsCount = persistentDramaComments.size,
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

                    if (isControlsVisible && !isImmersiveFullscreen) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.20f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(48.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
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

                                IconButton(
                                    onClick = {
                                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                    },
                                    modifier = Modifier.size(68.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = Color.White,
                                        modifier = Modifier.size(56.dp)
                                    )
                                }

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
                }
            }
        }

        // =========================================================================
        // 📥 🎯 শর্টস ব্যাচ ডাউনলোড পপ-আপ (VIP ও ২ জিবি লিমিট কানেক্টেড)
        // =========================================================================
        if (showBatchDownloadDialog) {
            ShortsBatchDownloadSheet(
                title = content.title,
                slug = slug,
                episodes = effectiveEpisodes,
                isVip = isUserVip, // 🎯 VIP স্ট্যাটাস পাঠানো হলো
                onDismiss = { showBatchDownloadDialog = false },
                onNavigateToVip = onNavigateToVip, // 🎯 লিমিট শেষ হলে VIP নেভিগেশন
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

        if (showCommentsSheet) {
            ShortsCommentsSheet(
                comments = persistentDramaComments,
                totalCommentsCount = persistentDramaComments.size,
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
