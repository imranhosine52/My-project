@file:OptIn(
    UnstableApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

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
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip // 👈 ফিক্সড: মিসিং ইমপোর্ট যোগ করা হয়েছে
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.*
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
import com.example.util.AppAnalyticsTracker
import com.example.util.R2DownloadManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private fun findActivity(context: Context): Activity? {
    var current = context
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun resolveBestEpisodeUrl(ep: EpisodeDto, slug: String): String {
    val padNum = String.format(Locale.US, "%02d", ep.episodeNumber)
    val fallbackR2 = "https://cdn.playdramaflix.com/streams/$slug/ep_$padNum/master.m3u8"

    val rawUrl = ep.directStreamUrl?.takeIf { it.isNotBlank() }
        ?: ep.appStreamUrl?.takeIf { it.isNotBlank() }
        ?: ep.videoUrl?.takeIf { it.isNotBlank() }
        ?: fallbackR2

    return if (rawUrl.contains("/player/?url=", ignoreCase = true)) {
        Uri.decode(rawUrl.substringAfter("/player/?url=").substringBefore("&"))
    } else {
        rawUrl
    }
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

    // 🎯 নোটিফিকেশন বার সম্পূর্ণ হাইড
    DisposableEffect(Unit) {
        activity?.let { act ->
            val window = act.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.hide(WindowInsetsCompat.Type.statusBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            activity?.let { act ->
                val window = act.window
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.statusBars())
            }
        }
    }

    val playerState by viewModel.playerUiState.collectAsStateWithLifecycle()
    val authState by viewModel.authUiState.collectAsStateWithLifecycle()
    val homeState by viewModel.homeUiState.collectAsStateWithLifecycle()

    val isUserLoggedIn = authState.isLoggedIn
    val isUserVip = playerState.isVip || authState.isVip

    val isCurrentDramaLoaded = (playerState.content?.slug == slug)

    val content = if (isCurrentDramaLoaded) {
        playerState.content!!
    } else {
        homeState.popularDramas.find { it.slug == slug }
            ?: homeState.shortsContent.find { it.slug == slug }
            ?: ContentItemDto(title = slug.replace("-", " "), slug = slug, type = "shorts")
    }

    LaunchedEffect(slug) {
        viewModel.loadDramaDetails(slug, context)
    }

    var isPlaying by remember { mutableStateOf(true) }
    var isControlsVisible by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }
    var isBuffering by remember { mutableStateOf(false) }
    var videoResizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    var isUserSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableLongStateOf(0L) }

    var isHalfDrawerOpen by remember { mutableStateOf(false) }
    var drawerInitialTab by remember { mutableIntStateOf(1) }

    var isImmersiveFullscreen by rememberSaveable { mutableStateOf(false) }

    var showBatchDownloadDialog by remember { mutableStateOf(false) }
    var showQualitySelectionSheet by remember { mutableStateOf(false) }
    var showSpeedSelectionSheet by remember { mutableStateOf(false) }

    var availableVideoTracks by remember { mutableStateOf<List<RealVideoTrack>>(emptyList()) }
    var currentSelectedHeight by rememberSaveable { mutableIntStateOf(0) }
    var currentQualityLabel by rememberSaveable { mutableStateOf("Auto") }
    var currentSpeedFloat by rememberSaveable { mutableFloatStateOf(1.0f) }
    var currentSpeedLabel by rememberSaveable { mutableStateOf("1x") }

    var isRewindActive by remember { mutableStateOf(false) }
    var isForwardActive by remember { mutableStateOf(false) }
    val rewindRotation = remember { Animatable(0f) }
    val forwardRotation = remember { Animatable(0f) }
    val rewindAlpha by animateFloatAsState(targetValue = if (isRewindActive) 1f else 0f, label = "rewindAlpha")
    val forwardAlpha by animateFloatAsState(targetValue = if (isForwardActive) 1f else 0f, label = "forwardAlpha")

    val effectiveEpisodes = remember(playerState.episodes, content.totalEpisodes, isCurrentDramaLoaded) {
        if (isCurrentDramaLoaded && playerState.episodes.isNotEmpty()) {
            playerState.episodes
        } else {
            (1..(content.totalEpisodes.coerceAtLeast(1))).map { num ->
                EpisodeDto(episodeNumber = num, isLocked = false)
            }
        }
    }

    val totalEpCount = effectiveEpisodes.size
    val verticalPagerState = rememberPagerState(initialPage = 0, pageCount = { totalEpCount })

    val singleEpisodeFlingBehavior = PagerDefaults.flingBehavior(
        state = verticalPagerState,
        pagerSnapDistance = PagerSnapDistance.atMost(1),
        snapPositionalThreshold = 0.22f,
        snapAnimationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
    )

    val currentEp: EpisodeDto = effectiveEpisodes.getOrElse(verticalPagerState.currentPage) { effectiveEpisodes.first() }
    val currentEpNum = currentEp.episodeNumber
    val currentVideoUrl = remember(currentEp, slug) { resolveBestEpisodeUrl(currentEp, slug) }

    LaunchedEffect(verticalPagerState.currentPage, slug, content.title) {
        val target = effectiveEpisodes.getOrNull(verticalPagerState.currentPage)
        if (target != null) {
            viewModel.selectEpisode(target)
            val shortTitle = content.title.ifBlank { slug }
            val numericUid = authState.userProfile?.id?.filter { it.isDigit() }?.toIntOrNull()
            AppAnalyticsTracker.trackScreen(context, "Watching Short: $shortTitle - Ep ${target.episodeNumber}", numericUid)
        }
    }

    val shortDramaRecommendations = remember(homeState.popularDramas, homeState.shortsContent, slug) {
        (homeState.shortsContent + homeState.popularDramas.filter { it.isShorts })
            .distinctBy { it.slug }
            .filter { it.slug != slug }
            .take(12)
    }

    val exoPlayer = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
            .setUserAgent("Mozilla/5.0 PlayDramaFlix Mobile")

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val safeLoadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(2500, 35000, 1000, 2000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(safeLoadControl)
            .build().apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_OFF
                setAudioAttributes(
                    AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).setUsage(C.USAGE_MEDIA).build(),
                    true
                )
            }
    }

    fun extractRealTracks(tracks: Tracks) {
        val foundTracks = mutableListOf<RealVideoTrack>()
        val seenResolutions = mutableSetOf<Int>()

        for (group in tracks.groups) {
            if (group.type == C.TRACK_TYPE_VIDEO) {
                for (i in 0 until group.length) {
                    if (group.isTrackSupported(i)) {
                        val format = group.getTrackFormat(i)
                        val resolution = if (format.width > 0 && format.height > 0) minOf(format.width, format.height) else format.height

                        if (resolution > 0 && seenResolutions.add(resolution)) {
                            val label = when {
                                resolution >= 1080 -> "${resolution}p Full HD"
                                resolution >= 720 -> "${resolution}p HD"
                                resolution >= 480 -> "${resolution}p Standard"
                                else -> "${resolution}p Data Saver"
                            }
                            foundTracks.add(
                                RealVideoTrack(
                                    height = resolution,
                                    width = maxOf(format.width, format.height),
                                    bitrate = format.bitrate,
                                    label = label
                                )
                            )
                        }
                    }
                }
            }
        }

        if (foundTracks.size > 1) {
            foundTracks.sortByDescending { it.height }
            availableVideoTracks = listOf(
                RealVideoTrack(height = 0, width = 0, bitrate = 0, label = "Auto (Adaptive)", isAuto = true)
            ) + foundTracks
        } else {
            availableVideoTracks = foundTracks
        }
    }

    fun applyExoPlayerQuality(targetResolution: Int, label: String) {
        currentSelectedHeight = targetResolution
        currentQualityLabel = label

        if (targetResolution == 0) {
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .build()
            Toast.makeText(context, "Quality: Auto (Adaptive)", Toast.LENGTH_SHORT).show()
        } else {
            val currentTracks = exoPlayer.currentTracks
            var overrideApplied = false

            for (group in currentTracks.groups) {
                if (group.type == C.TRACK_TYPE_VIDEO) {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val res = if (format.width > 0 && format.height > 0) minOf(format.width, format.height) else format.height

                        if (res == targetResolution) {
                            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                .buildUpon()
                                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                .build()
                            overrideApplied = true
                            break
                        }
                    }
                }
                if (overrideApplied) break
            }
            Toast.makeText(context, "Quality set to: $label", Toast.LENGTH_SHORT).show()
        }
    }

    fun applyExoPlayerSpeed(speed: Float) {
        currentSpeedFloat = speed
        currentSpeedLabel = if (speed == 1.0f) "1x" else "${speed}x"
        exoPlayer.setPlaybackSpeed(speed)
        Toast.makeText(context, "Speed: $currentSpeedLabel", Toast.LENGTH_SHORT).show()
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

    DisposableEffect(exoPlayer, verticalPagerState) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                extractRealTracks(tracks)
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                val width = videoSize.width
                val height = videoSize.height
                if (width > 0 && height > 0) {
                    val ratio = width.toFloat() / height.toFloat()
                    videoResizeMode = if (isImmersiveFullscreen) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    } else if (ratio <= 0.75f) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }

                    val currentRes = minOf(width, height)
                    if (currentSelectedHeight == 0) {
                        currentQualityLabel = "${currentRes}P"
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
                            verticalPagerState.animateScrollToPage(
                                page = nextIndex,
                                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
                            )
                        }
                    }
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(isPlaying, isUserSeeking) {
        while (isPlaying && !isUserSeeking) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            val d = exoPlayer.duration
            if (d > 0) totalDurationMs = d
            delay(200L)
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

    LaunchedEffect(currentVideoUrl, verticalPagerState.currentPage, slug) {
        if (currentVideoUrl.isBlank()) return@LaunchedEffect
        try {
            availableVideoTracks = emptyList()
            exoPlayer.stop()
            exoPlayer.clearMediaItems()

            val isHls = currentVideoUrl.contains(".m3u8", ignoreCase = true)
            val item = MediaItem.Builder()
                .setUri(Uri.parse(currentVideoUrl))
                .apply {
                    if (isHls) setMimeType(MimeTypes.APPLICATION_M3U8)
                    else setMimeType(MimeTypes.APPLICATION_MP4)
                }
                .build()
            exoPlayer.addMediaItem(item)

            val nextIdx = verticalPagerState.currentPage + 1
            if (nextIdx < effectiveEpisodes.size) {
                val nextEp = effectiveEpisodes[nextIdx]
                val nextUrl = resolveBestEpisodeUrl(nextEp, slug)
                if (nextUrl.isNotBlank()) {
                    val nextIsHls = nextUrl.contains(".m3u8", ignoreCase = true)
                    val nextItem = MediaItem.Builder()
                        .setUri(Uri.parse(nextUrl))
                        .apply {
                            if (nextIsHls) setMimeType(MimeTypes.APPLICATION_M3U8)
                            else setMimeType(MimeTypes.APPLICATION_MP4)
                        }
                        .build()
                    exoPlayer.addMediaItem(nextItem)
                }
            }

            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            exoPlayer.play()
        } catch (_: Exception) {}
    }

    BackHandler {
        when {
            isImmersiveFullscreen -> isImmersiveFullscreen = false
            showBatchDownloadDialog -> showBatchDownloadDialog = false
            showQualitySelectionSheet -> showQualitySelectionSheet = false
            showSpeedSelectionSheet -> showSpeedSelectionSheet = false
            isHalfDrawerOpen -> isHalfDrawerOpen = false
            else -> onBackClick()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // =========================================================================
        // 📺 প্রধান উল্লম্ব লেআউট (ওপরের কালো বার ➔ ভিডিও ফ্রেম ➔ নিচের কালো বার)
        // =========================================================================
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // 🔝 ১. ওপরের সলিড কালো ব্যাকগ্রাউন্ড বার (ব্যাক বাটন ও ডাউনলোড বাটন সহ)
            if (!isImmersiveFullscreen) {
                Surface(
                    color = Color.Black,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onBackClick() }
                                .padding(vertical = 4.dp, horizontal = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Ep $currentEpNum",
                                color = Color.White,
                                fontSize = 16.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(
                            onClick = { showBatchDownloadDialog = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FileDownload,
                                contentDescription = "Download",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // 🎬 ২. মাঝখানের ভিডিও এরিয়া (টাইটেল এবং ডেসক্রিপশনের নিচে ভিডিও দেখা যাবে)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isImmersiveFullscreen) Modifier.fillMaxHeight(1f)
                        else Modifier.weight(1f)
                    )
                    .background(Color.Black)
            ) {
                ShortsVideoSurface(
                    exoPlayer = exoPlayer,
                    isBuffering = isBuffering || currentVideoUrl.isBlank(),
                    currentEpNum = currentEpNum,
                    isPlaying = isPlaying,
                    isControlsVisible = isControlsVisible,
                    isImmersiveFullscreen = isImmersiveFullscreen,
                    resizeMode = if (isImmersiveFullscreen) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else videoResizeMode,
                    onBackClick = onBackClick,
                    onDownloadClick = { showBatchDownloadDialog = true },
                    onTapSurface = {
                        if (!isHalfDrawerOpen) {
                            isControlsVisible = !isControlsVisible
                        }
                    },
                    onDoubleTapFullscreen = {
                        isImmersiveFullscreen = !isImmersiveFullscreen
                        isControlsVisible = false
                    },
                    onPlayPauseClick = {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                    },
                    onSeekSkip = { seconds -> triggerSkip(seconds) },
                    modifier = Modifier.fillMaxSize()
                )

                // পেজার (সোয়াইপ ও ডাবল-ট্যাপ)
                VerticalPager(
                    state = verticalPagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isImmersiveFullscreen) Modifier.fillMaxHeight(1f) else Modifier.fillMaxHeight(0.70f)),
                    userScrollEnabled = !isUserSeeking,
                    flingBehavior = singleEpisodeFlingBehavior
                ) { _ ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(isImmersiveFullscreen, isHalfDrawerOpen) {
                                detectTapGestures(
                                    onTap = {
                                        if (!isHalfDrawerOpen) {
                                            isControlsVisible = !isControlsVisible
                                        }
                                    },
                                    onDoubleTap = {
                                        isImmersiveFullscreen = !isImmersiveFullscreen
                                        isControlsVisible = false
                                    }
                                )
                            }
                    )
                }

                // 🎯 সাইডের আইকনগুলো একদম নিচে নামানো হলো (Like, Share, Save)
                if (!isImmersiveFullscreen && !isHalfDrawerOpen) {
                    ShortsActionColumn(
                        context = context,
                        title = content.title,
                        slug = slug,
                        likesCount = playerState.likesCount.toLong(),
                        isLiked = playerState.isLiked,
                        isInWatchlist = playerState.isInWatchlist,
                        onLikeClick = {
                            if (!isUserLoggedIn) viewModel.showAuthDialog(true)
                            else viewModel.toggleLikeDrama()
                        },
                        onSaveClick = {
                            if (!isUserLoggedIn) viewModel.showAuthDialog(true)
                            else viewModel.toggleWatchlist()
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 12.dp, bottom = 12.dp)
                    )
                }

                // প্লে/পজ ও স্কিপ কন্ট্রোলস
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

            // ⬛ ৩. বটম ওভারলে: শুধুমাত্র দাগের নিচে কালো ব্যাকগ্রাউন্ড (ভিডিওর ওপর টাইটেল থাকবে)
            if (!isImmersiveFullscreen && !isHalfDrawerOpen) {
                ShortsBottomOverlay(
                    content = content,
                    currentEpNum = currentEpNum,
                    totalEpCount = totalEpCount,
                    currentPositionMs = currentPositionMs,
                    totalDurationMs = totalDurationMs,
                    isUserSeeking = isUserSeeking,
                    seekPosition = seekPosition,
                    currentSpeedText = currentSpeedLabel,
                    currentQualityText = currentQualityLabel,
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
                    onSpeedClick = { showSpeedSelectionSheet = true },
                    onQualityClick = { showQualitySelectionSheet = true }
                )
            }
        }

        // =========================================================================
        // 📑 ৪. সমস্ত বটম শিট (অর্ধেক স্ক্রিন)
        // =========================================================================

        if (isHalfDrawerOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { isHalfDrawerOpen = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.55f),
                    color = Color(0xFF181D29),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                ) {
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
                            if (!isUserLoggedIn) viewModel.showAuthDialog(true)
                            else viewModel.toggleWatchlist()
                        },
                        onDismiss = { isHalfDrawerOpen = false },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        if (showQualitySelectionSheet) {
            ShortsQualitySelectionSheet(
                availableTracks = availableVideoTracks,
                currentSelectedHeight = currentSelectedHeight,
                onSelectQuality = { targetResolution, label ->
                    applyExoPlayerQuality(targetResolution, label)
                },
                onDismiss = { showQualitySelectionSheet = false }
            )
        }

        if (showSpeedSelectionSheet) {
            ShortsSpeedSelectionSheet(
                currentSpeed = currentSpeedFloat,
                onSelectSpeed = { speed ->
                    applyExoPlayerSpeed(speed)
                },
                onDismiss = { showSpeedSelectionSheet = false }
            )
        }

        if (showBatchDownloadDialog) {
            ShortsBatchDownloadSheet(
                title = content.title,
                slug = slug,
                episodes = effectiveEpisodes,
                isVip = isUserVip,
                onDismiss = { showBatchDownloadDialog = false },
                onNavigateToVip = onNavigateToVip,
                onDownloadSelected = { selectedList, chosenQualityKey ->
                    showBatchDownloadDialog = false
                    selectedList.forEach { ep ->
                        val pad = String.format(Locale.US, "%02d", ep.episodeNumber)
                        val customBatchTitle = "${content.title} - Ep $pad (${chosenQualityKey.uppercase()}) - By PdFlix"
                        val targetUrl = ep.downloadOptions?.firstOrNull {
                            it.quality.contains(chosenQualityKey, true)
                        }?.url ?: ep.resolveDownloadUrl(slug)

                        R2DownloadManager.startDownload(
                            context = context,
                            downloadUrl = targetUrl,
                            title = customBatchTitle,
                            episodeNumber = ep.episodeNumber,
                            isMovie = false
                        )
                    }
                    Toast.makeText(context, "📥 Download started for ${selectedList.size} episodes!", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}
