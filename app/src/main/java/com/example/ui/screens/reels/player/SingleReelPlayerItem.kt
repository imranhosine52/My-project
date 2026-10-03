@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ui.screens.reels.player

import android.app.Activity
import android.net.Uri
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.model.CreatorPlaylistDto
import com.example.data.model.ReelVideoQuality
import com.example.data.model.UserReelDto
import com.example.data.repository.ReelsRepository
import com.example.ui.screens.reels.components.PlaylistEpisodesBottomSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// 🎨 নীল ও গ্রিন অ্যাকসেন্ট
private val CyanBlue = Color(0xFF00E5FF)
private val ActionGreen = Color(0xFF00E676)
private val DockedBarBg = Color(0xFF0A0D14)

/**
 * 🎬 ফ্রেমড ভিডিও প্লেয়ার কার্ড (২ নম্বর ছবির খাঁটি মিনি-ড্রামা ভিউ)
 * - ভিডিও ফ্রেমটি আলাদা রাউন্ডেড কার্ড আকারে রেন্ডার হয়
 * - নিচে ১ নম্বর ছবির মতো হুবহু ডকড প্লেলিস্ট বার থাকে
 * - ৩ সেকেন্ড জেনুইন ভিউ ফিল্টার ও বাফার-মুক্ত ক্যাশ প্লেব্যাক
 */
@Composable
fun SingleReelPlayerItem(
    reel: UserReelDto,
    allReels: List<UserReelDto> = emptyList(),
    selectedQuality: ReelVideoQuality,
    playbackSpeed: Float,
    isActiveVideoPlaying: Boolean,
    repository: ReelsRepository,
    isCommentsOpen: Boolean = false,
    isSidebarOpenState: Boolean = false,
    onSidebarStateChange: (Boolean) -> Unit = {},
    isLoggedIn: Boolean = true,
    isCreatorPageUser: Boolean = false,
    onRequireLogin: () -> Unit = {},
    onDoubleTapLike: () -> Unit,
    onToggleLike: () -> Unit,
    onFollowClick: () -> Unit,
    onCommentClick: () -> Unit,
    onShareClick: () -> Unit,
    onHashtagClick: (String) -> Unit,
    onOpenPageProfile: () -> Unit,
    onSelectReel: (UserReelDto) -> Unit = {},
    onVideoCompleteAutoPlayNext: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }

    var isBuffering by remember { mutableStateOf(true) }
    var isPlayingState by remember { mutableStateOf(true) }

    var showBigHeartAnimation by remember { mutableStateOf(false) }
    var showPlayPauseIconState by remember { mutableStateOf<Boolean?>(null) }

    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }

    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }

    // ৩-সেকেন্ড জেনুইন ভিউ ফিল্টার
    var hasRecorded24hViewForThisPlayback by remember(reel.id) { mutableStateOf(false) }
    var hasRetriedFallback by remember(reel.id) { mutableStateOf(false) }

    // সিরিজ পর্ব তালিকা
    var showSeriesEpisodesDrawer by remember { mutableStateOf(false) }
    var seriesEpisodesList by remember { mutableStateOf<List<UserReelDto>>(emptyList()) }
    var otherPlaylistsList by remember { mutableStateOf<List<CreatorPlaylistDto>>(emptyList()) }

    // ২ নম্বর ছবির মসৃণ স্লাইড অফসেট
    val horizontalSlideOffset = remember { Animatable(0f) }

    LaunchedEffect(reel.playlistId) {
        val pId = reel.playlistId
        if (pId != null && pId > 0) {
            val res = repository.getPlaylistReels(pId)
            seriesEpisodesList = res.getOrDefault(emptyList())

            val targetPageId = if (reel.pageId > 0) reel.pageId.toLong() else reel.userId.toLong()
            val plRes = repository.getPlaylists(targetPageId)
            otherPlaylistsList = plRes.getOrDefault(emptyList())
        } else {
            seriesEpisodesList = emptyList()
        }
    }

    val currentEpisodeIndex = remember(seriesEpisodesList, reel.id) {
        seriesEpisodesList.indexOfFirst { it.id == reel.id }
    }

    val isVerticalRatio = remember(videoWidth, videoHeight) {
        if (videoWidth > 0 && videoHeight > 0) {
            (videoWidth.toFloat() / videoHeight.toFloat()) < 0.72f
        } else {
            true
        }
    }

    DisposableEffect(isActiveVideoPlaying, isPlayingState) {
        if (isActiveVideoPlaying && isPlayingState) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    BackHandler(enabled = showSeriesEpisodesDrawer) {
        showSeriesEpisodesDrawer = false
    }

    val videoUrlToPlay = remember(reel.id, selectedQuality) {
        val customQuality = reel.getVideoUrlForQuality(selectedQuality)
        if (customQuality.isNotBlank()) customQuality else reel.videoUrl
    }

    val exoPlayer = remember(reel.id) {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(12000)
            .setReadTimeoutMs(15000)
            .setUserAgent("Mozilla/5.0 (Linux; Android 14) Chrome/120.0.0.0 Mobile Safari/537.36 PlayDramaFlix")

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(2000, 12000, 800, 1200)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = true
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true
                )
            }
    }

    LaunchedEffect(videoUrlToPlay) {
        if (videoUrlToPlay.isNotBlank()) {
            runCatching {
                val mediaItem = MediaItem.fromUri(Uri.parse(videoUrlToPlay))
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                if (isActiveVideoPlaying) {
                    exoPlayer.play()
                }
            }
        }
    }

    LaunchedEffect(playbackSpeed) {
        runCatching { exoPlayer.setPlaybackSpeed(playbackSpeed) }
    }

    LaunchedEffect(isActiveVideoPlaying) {
        if (isActiveVideoPlaying) {
            runCatching { exoPlayer.play() }
            isPlayingState = true
        } else {
            runCatching { exoPlayer.pause() }
            isPlayingState = false
        }
    }

    // ৩-সেকেন্ড জেনুইন ভিউ ফিল্টার
    LaunchedEffect(isActiveVideoPlaying, isPlayingState) {
        while (isActiveVideoPlaying) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)

            if (!hasRecorded24hViewForThisPlayback && currentPositionMs >= 3000L) {
                hasRecorded24hViewForThisPlayback = true
                coroutineScope.launch {
                    repository.reelFeedRepository.recordReelViewLocal(reel.id)
                }
            }

            delay(150L)
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> isBuffering = true
                    Player.STATE_READY -> {
                        isBuffering = false
                        totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
                        if (isActiveVideoPlaying) exoPlayer.play()
                    }
                    Player.STATE_ENDED -> {
                        isBuffering = false
                        if (seriesEpisodesList.isNotEmpty() && currentEpisodeIndex != -1 && currentEpisodeIndex < seriesEpisodesList.size - 1) {
                            onSelectReel(seriesEpisodesList[currentEpisodeIndex + 1])
                            return
                        }
                        runCatching { onVideoCompleteAutoPlayNext() }
                    }
                    Player.STATE_IDLE -> {}
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoWidth = videoSize.width
                videoHeight = videoSize.height
                if (videoWidth > 0 && videoHeight > 0) isBuffering = false
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlayingState = playing
                if (playing) isBuffering = false
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                if (!hasRetriedFallback && reel.videoUrl.isNotBlank()) {
                    hasRetriedFallback = true
                    runCatching {
                        val fallbackItem = MediaItem.fromUri(Uri.parse(reel.videoUrl))
                        exoPlayer.setMediaItem(fallbackItem)
                        exoPlayer.prepare()
                        exoPlayer.play()
                    }
                }
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    val progressFraction = if (totalDurationMs > 0) {
        (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    // স্মুথ হরিজন্টাল স্লাইড কন্ট্রোলার
    val horizontalDragState = rememberDraggableState { delta ->
        if (!isCommentsOpen) {
            coroutineScope.launch {
                val newOffset = (horizontalSlideOffset.value + delta).coerceIn(-screenWidthPx * 0.45f, screenWidthPx * 0.45f)
                horizontalSlideOffset.snapTo(newOffset)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .offset { IntOffset(horizontalSlideOffset.value.roundToInt(), 0) }
            .draggable(
                state = horizontalDragState,
                orientation = Orientation.Horizontal,
                onDragStopped = { velocity ->
                    coroutineScope.launch {
                        val currentX = horizontalSlideOffset.value
                        val hasSeries = seriesEpisodesList.isNotEmpty() && currentEpisodeIndex != -1

                        if ((currentX < -80f || velocity < -500f) && hasSeries && currentEpisodeIndex < seriesEpisodesList.size - 1) {
                            horizontalSlideOffset.animateTo(-screenWidthPx * 0.5f, tween(160, easing = FastOutLinearInEasing))
                            onSelectReel(seriesEpisodesList[currentEpisodeIndex + 1])
                            horizontalSlideOffset.snapTo(0f)
                        } else if ((currentX > 80f || velocity > 500f) && hasSeries && currentEpisodeIndex > 0) {
                            horizontalSlideOffset.animateTo(screenWidthPx * 0.5f, tween(160, easing = FastOutLinearInEasing))
                            onSelectReel(seriesEpisodesList[currentEpisodeIndex - 1])
                            horizontalSlideOffset.snapTo(0f)
                        } else if (currentX < -100f && !isSidebarOpenState) {
                            horizontalSlideOffset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                            onSidebarStateChange(true)
                        } else {
                            horizontalSlideOffset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                        }
                    }
                }
            )
    ) {
        // =========================================================================
        // 📺 ১. খাঁটি ভিডিও ফ্রেম রেন্ডারার
        // =========================================================================
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(reel.id) {
                    detectTapGestures(
                        onTap = {
                            if (!isCommentsOpen) {
                                if (isSidebarOpenState) {
                                    onSidebarStateChange(false)
                                } else {
                                    if (exoPlayer.isPlaying) {
                                        exoPlayer.pause()
                                        showPlayPauseIconState = false
                                    } else {
                                        exoPlayer.play()
                                        showPlayPauseIconState = true
                                    }
                                    coroutineScope.launch {
                                        delay(600)
                                        showPlayPauseIconState = null
                                    }
                                }
                            }
                        },
                        onDoubleTap = {
                            if (!isCommentsOpen && !isSidebarOpenState) {
                                if (!isLoggedIn) {
                                    onRequireLogin()
                                } else {
                                    showBigHeartAnimation = true
                                    onDoubleTapLike()
                                    coroutineScope.launch {
                                        delay(700)
                                        showBigHeartAnimation = false
                                    }
                                }
                            }
                        }
                    )
                }
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = if (isVerticalRatio) {
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        } else {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    view.resizeMode = if (isVerticalRatio) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isBuffering) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CyanBlue, strokeWidth = 2.dp, modifier = Modifier.size(36.dp))
            }
        }

        // প্লে/পজ আইকন অ্যানিমেশন
        AnimatedVisibility(
            visible = showPlayPauseIconState != null,
            enter = scaleIn(tween(140)) + fadeIn(tween(140)),
            exit = scaleOut(tween(140)) + fadeOut(tween(140)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (showPlayPauseIconState == true) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        // ডাবল ট্যাপ হার্ট অ্যানিমেশন (গ্রিন কালার)
        if (showBigHeartAnimation) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = ActionGreen.copy(alpha = 0.95f),
                modifier = Modifier.size(92.dp).align(Alignment.Center).scale(1.25f)
            )
        }

        // =========================================================================
        // 🌟 ২. ১ নম্বর ছবির হুবহু ডকড প্লেলিস্ট বার (ভিডিও ফ্রেমের নিচে স্থির)
        // =========================================================================
        if (reel.playlistId != null && reel.playlistId > 0) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                Surface(
                    color = DockedBarBg.copy(alpha = 0.96f),
                    border = BorderStroke(0.6.dp, Color(0xFF1E2838)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSeriesEpisodesDrawer = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = null,
                                tint = CyanBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Playlist • ${(reel.playlistTitle?.takeIf { it.isNotBlank() } ?: "SERIES").uppercase()} • Ep ${reel.episodeNum}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = "Open Drawer",
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                // টাইমলাইন প্রগ্রেস বার (ভিডিও কার্ডের একদম নিচের বর্ডার)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = progressFraction.coerceAtLeast(0.01f))
                            .background(Brush.horizontalGradient(listOf(CyanBlue, ActionGreen)))
                    )
                }
            }
        }

        // =========================================================================
        // 📺 ৩. সিরিজ এপিসোড সিলেকশন বটম শিট
        // =========================================================================
        if (showSeriesEpisodesDrawer && reel.playlistId != null && reel.playlistId > 0) {
            PlaylistEpisodesBottomSheet(
                seriesTitle = reel.playlistTitle?.ifBlank { "Mini-Drama" } ?: "Mini-Drama",
                currentReelId = reel.id,
                episodes = seriesEpisodesList,
                creatorPlaylists = otherPlaylistsList,
                isLoading = false,
                isFavorite = reel.isSaved,
                onToggleFavorite = {
                    if (!isLoggedIn) onRequireLogin()
                    else coroutineScope.launch { repository.toggleSaveReel(reel.id) }
                },
                onEpisodeClick = { targetEpisode -> onSelectReel(targetEpisode) },
                onSelectOtherPlaylist = { otherPl ->
                    coroutineScope.launch {
                        val res = repository.getPlaylistReels(otherPl.effectiveId)
                        val otherEps = res.getOrDefault(emptyList())
                        if (otherEps.isNotEmpty()) onSelectReel(otherEps.first())
                    }
                },
                onDismiss = { showSeriesEpisodesDrawer = false }
            )
        }
    }
}
