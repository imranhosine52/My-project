@file:OptIn(
    UnstableApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.example.ui.screens.shorts

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke // 👈 ফিক্সড: মিসিং ইমপোর্ট যোগ করা হয়েছে
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import com.example.data.model.ContentItemDto
import com.example.data.model.EpisodeDto
import com.example.ui.screens.SleekSkipIconOnline
import com.example.ui.viewmodel.DramaFlixViewModel
import com.example.util.AppAnalyticsTracker
import com.example.util.R2DownloadManager
import kotlinx.coroutines.Job
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

/**
 * 🎯 ২ নম্বর ছবির হুবহু ১০ সেকেন্ড স্কিপ বাটন:
 * কোনো ডার্ক ব্যাকগ্রাউন্ড সার্কেল নেই। ১০ সংখ্যাটি স্থির থাকবে, শুধু বাইরের সাদা দাগটি ঘুরবে।
 */
@Composable
private fun PureSkipButton(
    isForward: Boolean,
    rotation: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(52.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(40.dp)
                .rotate(rotation)
        ) {
            val strokeWidth = 2.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val arcSize = Size(diameter, diameter)
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

            if (isForward) {
                drawArc(
                    color = Color.White,
                    startAngle = -65f,
                    sweepAngle = 280f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            } else {
                drawArc(
                    color = Color.White,
                    startAngle = 245f,
                    sweepAngle = -280f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        Text(
            text = "10",
            color = Color.White,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold
        )
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

    // 🎯 নোটিফিকেশন ও স্ট্যাটাস বার হাইড
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

    // ⚡ 2X স্পিড প্রেস-এন্ড-হোল্ড স্টেট
    var is2xActive by remember { mutableStateOf(false) }
    var previousSpeedBefore2x by remember { mutableFloatStateOf(1.0f) }

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

    // স্কিপ অ্যানিমেশন
    var isRewindActive by remember { mutableStateOf(false) }
    var isForwardActive by remember { mutableStateOf(false) }
    val rewindRotation = remember { Animatable(0f) }
    val forwardRotation = remember { Animatable(0f) }
    val rewindPopupScale = remember { Animatable(0f) }
    val forwardPopupScale = remember { Animatable(0f) }

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

    // 🎯 সোয়াইপ করার সাথে সাথে রিয়েল-টাইমে বর্তমান পর্বের সংখ্যা আপডেট হওয়া
    val activePageIndex by remember { derivedStateOf { verticalPagerState.currentPage } }
    val currentEp: EpisodeDto = effectiveEpisodes.getOrElse(activePageIndex) { effectiveEpisodes.first() }
    val currentEpNum = currentEp.episodeNumber

    // মাখনের মতো মসৃণ সোয়াইপ ফ্লিং
    val singleEpisodeFlingBehavior = PagerDefaults.flingBehavior(
        state = verticalPagerState,
        pagerSnapDistance = PagerSnapDistance.atMost(1),
        snapAnimationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    )

    val currentVideoUrl = remember(currentEp, slug) { resolveBestEpisodeUrl(currentEp, slug) }

    LaunchedEffect(activePageIndex, slug, content.title) {
        val target = effectiveEpisodes.getOrNull(activePageIndex)
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

    // 🚀 ExoPlayer (স্মার্ট প্লেলিস্ট প্রি-বাফারিং - ০ms নেক্সট এপিসোড)
    val exoPlayer = remember {
        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(buildUponParameters().setAllowMultipleAdaptiveSelections(true))
        }

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
            .setUserAgent("Mozilla/5.0 PlayDramaFlix Mobile")

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val instantLoadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                2000,
                45000,
                1000,
                1500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(instantLoadControl)
            .build().apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_OFF
                setAudioAttributes(
                    AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).setUsage(C.USAGE_MEDIA).build(),
                    true
                )
            }
    }

    // PiP মোডে যাওয়ার হ্যান্ডলার
    fun triggerPictureInPicture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                isControlsVisible = false
                val pipParams = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(9, 16))
                    .build()
                activity?.enterPictureInPictureMode(pipParams)
            } catch (_: Exception) {
                onBackClick()
            }
        } else {
            onBackClick()
        }
    }

    // 🔙 ব্যাক বাটন হ্যান্ডলার: ভিডিও প্লে থাকলে ইউটিউবের মতো উপরে ফ্লোটিং হবে
    BackHandler {
        when {
            showBatchDownloadDialog -> showBatchDownloadDialog = false
            showQualitySelectionSheet -> showQualitySelectionSheet = false
            showSpeedSelectionSheet -> showSpeedSelectionSheet = false
            isHalfDrawerOpen -> isHalfDrawerOpen = false
            isImmersiveFullscreen -> isImmersiveFullscreen = false
            isPlaying -> triggerPictureInPicture()
            else -> onBackClick()
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
                Lifecycle.Event.ON_PAUSE -> {
                    if (isPlaying && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        try {
                            activity?.enterPictureInPictureMode(
                                PictureInPictureParams.Builder()
                                    .setAspectRatio(Rational(9, 16))
                                    .build()
                            )
                        } catch (_: Exception) {
                            exoPlayer.pause()
                        }
                    } else {
                        exoPlayer.pause()
                    }
                }
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

    // 🎯 অটোমেটিক পরবর্তী পর্বে জাম্প (০ লোডিং)
    DisposableEffect(exoPlayer, totalEpCount, verticalPagerState) {
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
                            verticalPagerState.animateScrollToPage(nextIndex)
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
            delay(150L)
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
                launch { rewindRotation.snapTo(0f); rewindRotation.animateTo(-360f, tween(360, easing = LinearEasing)) }
                launch { rewindPopupScale.snapTo(0.6f); rewindPopupScale.animateTo(1.2f, spring(Spring.DampingRatioMediumBouncy)); delay(300); rewindPopupScale.animateTo(0f, tween(180)) }
                delay(450)
                isRewindActive = false
            } else {
                isForwardActive = true
                launch { forwardRotation.snapTo(0f); forwardRotation.animateTo(360f, tween(360, easing = LinearEasing)) }
                launch { forwardPopupScale.snapTo(0.6f); forwardPopupScale.animateTo(1.2f, spring(Spring.DampingRatioMediumBouncy)); delay(300); forwardPopupScale.animateTo(0f, tween(180)) }
                delay(450)
                isForwardActive = false
            }
        }
    }

    // প্রি-লোডিং প্লেলিস্ট সেটআপ (০-সেকেন্ডে পরবর্তী পর্ব প্লে)
    LaunchedEffect(currentVideoUrl, activePageIndex, slug) {
        if (currentVideoUrl.isBlank()) return@LaunchedEffect
        try {
            availableVideoTracks = emptyList()
            exoPlayer.stop()
            exoPlayer.clearMediaItems()

            val isHls = currentVideoUrl.contains(".m3u8", ignoreCase = true)
            val currentItem = MediaItem.Builder()
                .setUri(Uri.parse(currentVideoUrl))
                .apply {
                    if (isHls) setMimeType(MimeTypes.APPLICATION_M3U8)
                    else setMimeType(MimeTypes.APPLICATION_MP4)
                }
                .build()
            exoPlayer.addMediaItem(currentItem)

            val nextIdx = activePageIndex + 1
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

    val isPiPActive = activity?.isInPictureInPictureMode == true

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isPiPActive) {
            ShortsVideoSurface(
                exoPlayer = exoPlayer,
                isBuffering = isBuffering,
                currentEpNum = currentEpNum,
                isPlaying = isPlaying,
                isControlsVisible = false,
                isImmersiveFullscreen = true,
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                // 🔝 ১. ওপরের কালো ব্যাকগ্রাউন্ড বার (ব্যাক বাটন ও ডাউনলোড বাটন সহ)
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

            // 🎬 ২. মাঝখানের সম্পূর্ণ ভিডিও এরিয়া
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isImmersiveFullscreen) Modifier.fillMaxHeight(1f)
                        else Modifier.weight(1f)
                    )
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
                    },
                    onPlayPauseClick = {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                    },
                    onSeekSkip = { seconds -> triggerSkip(seconds) },
                    modifier = Modifier.fillMaxSize()
                )

                // 🎯 পেজার ও স্ক্রিনে চেপে ধরলে ২X স্পিড চালানোর লজিক
                VerticalPager(
                    state = verticalPagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = !isUserSeeking && !is2xActive,
                    flingBehavior = singleEpisodeFlingBehavior
                ) { _ ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(isUserSeeking) {
                                detectTapGestures(
                                    onPress = {
                                        var speedJob: Job? = null
                                        try {
                                            speedJob = coroutineScope.launch {
                                                delay(400)
                                                if (!isUserSeeking) {
                                                    is2xActive = true
                                                    previousSpeedBefore2x = currentSpeedFloat
                                                    exoPlayer.setPlaybackSpeed(2.0f)
                                                }
                                            }
                                            tryAwaitRelease()
                                        } finally {
                                            speedJob?.cancel()
                                            if (is2xActive) {
                                                is2xActive = false
                                                exoPlayer.setPlaybackSpeed(previousSpeedBefore2x)
                                            }
                                        }
                                    },
                                    onTap = {
                                        if (!isHalfDrawerOpen) {
                                            isControlsVisible = !isControlsVisible
                                        }
                                    },
                                    onDoubleTap = {
                                        isImmersiveFullscreen = !isImmersiveFullscreen
                                    }
                                )
                            }
                    )
                }

                // ⚡ ২X স্পিড পপ-আপ ব্যাজ
                if (is2xActive) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.70f),
                        border = BorderStroke(1.dp, Color(0xFF00E5FF)),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                            Text("2X Speed", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // সাইডের অ্যাকশন আইকনগুলো (Like, Share, Save)
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
                            .padding(end = 12.dp, bottom = 80.dp)
                    )
                }

                // ভিডিও ফ্রেমের ওপর ভাসমান টাইটেল, ডেসক্রিপশন ও টাইমলাইন
                if (!isImmersiveFullscreen && !isHalfDrawerOpen) {
                    ShortsVideoFloatingOverlay(
                        content = content,
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
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }

                // =========================================================================
                // 🌟 ২ নম্বর ছবির হুবহু প্লে/পজ ও ১০ সেকেন্ড স্কিপ কন্ট্রোলস (নো ব্যাকগ্রাউন্ড)
                // =========================================================================
                if (isControlsVisible) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(52.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // -১০ সেকেন্ড স্কিপ বাটন
                            Box(contentAlignment = Alignment.Center) {
                                if (isRewindActive) {
                                    Text(
                                        text = "-10s",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .offset(y = (-36).dp)
                                            .scale(rewindPopupScale.value)
                                            .alpha(rewindPopupScale.value.coerceIn(0f, 1f))
                                    )
                                }
                                PureSkipButton(
                                    isForward = false,
                                    rotation = rewindRotation.value,
                                    onClick = { triggerSkip(-10) }
                                )
                            }

                            // 🎯 ২ নম্বর ছবির হুবহু প্লে / পজ বাটন (পিওর সাদা, নো ডার্ক বক্স)
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Crossfade(
                                    targetState = isPlaying,
                                    animationSpec = tween(150),
                                    label = "PlayPauseCrossfade"
                                ) { playing ->
                                    Icon(
                                        imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (playing) "Pause" else "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(56.dp)
                                    )
                                }
                            }

                            // +১০ সেকেন্ড স্কিপ বাটন
                            Box(contentAlignment = Alignment.Center) {
                                if (isForwardActive) {
                                    Text(
                                        text = "+10s",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .offset(y = (-36).dp)
                                            .scale(forwardPopupScale.value)
                                            .alpha(forwardPopupScale.value.coerceIn(0f, 1f))
                                    )
                                }
                                PureSkipButton(
                                    isForward = true,
                                    rotation = forwardRotation.value,
                                    onClick = { triggerSkip(10) }
                                )
                            }
                        }
                    }
                }
            }

            // ⬛ ৩. শুধুমাত্র নীল দাগের নিচে থাকা "সলিড কালো ব্যাকগ্রাউন্ড বার"
            if (!isImmersiveFullscreen && !isHalfDrawerOpen) {
                ShortsSolidBlackBottomBar(
                    currentEpNum = currentEpNum,
                    totalEpCount = totalEpCount,
                    currentSpeedText = currentSpeedLabel,
                    currentQualityText = currentQualityLabel,
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
        // 📑 ৪. সমস্ত বটম শিটসমূহ
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
