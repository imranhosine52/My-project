@file:OptIn(
    UnstableApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.example.ui.screens.shorts

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.graphicsLayer
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
import com.example.data.model.CustomAdsConfigResponse
import com.example.data.model.CustomVideoAdDto
import com.example.data.model.EpisodeDto
import com.example.ui.components.CustomVideoAdDialog
import com.example.ui.screens.SleekSkipIconOnline
import com.example.ui.viewmodel.DramaFlixViewModel
import com.example.util.AppAnalyticsTracker
import com.example.util.R2DownloadManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import kotlin.math.abs

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

    // 🎯 নোটিফিকেশন বার হাইড
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

    var customAdsConfig by remember { mutableStateOf<CustomAdsConfigResponse?>(null) }
    var activeCustomVideoAd by remember { mutableStateOf<CustomVideoAdDto?>(null) }
    var watchedEpisodesCounter by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder().url("https://playdramaflix.com/api/v1/custom-ads").build()
                val response = client.newCall(request).execute()
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful && body.isNotBlank()) {
                    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                    val adapter = moshi.adapter(CustomAdsConfigResponse::class.java)
                    customAdsConfig = adapter.fromJson(body)
                }
            } catch (_: Exception) {}
        }
    }

    val customShortsAds = remember(customAdsConfig) {
        customAdsConfig?.ads?.filter { it.placement == "shorts" || it.placement == "all" } ?: emptyList()
    }
    val shortsAdInterval = customAdsConfig?.shortsRules?.intervalEpisodes ?: 3

    val isCurrentDramaLoaded = (playerState.content?.slug == slug)

    val content = if (isCurrentDramaLoaded) {
        playerState.content!!
    } else {
        homeState.popularDramas.find { it.slug == slug }
            ?: homeState.shortsContent.find { it.slug == slug }
            ?: ContentItemDto(title = slug.replace("-", " "), slug = slug, type = "shorts")
    }

    // 🎯 ছোট ও পরিচ্ছন্ন নাম (Display Name)
    val cleanShortTitle = remember(content) {
        content.displayName
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

    val activePageIndex by remember { derivedStateOf { verticalPagerState.currentPage } }
    val currentEp: EpisodeDto = effectiveEpisodes.getOrElse(activePageIndex) { effectiveEpisodes.first() }
    val currentEpNum = currentEp.episodeNumber

    // ৩ পর্ব পর পর অ্যাড চেকার
    LaunchedEffect(activePageIndex) {
        if (activePageIndex > 0) {
            watchedEpisodesCounter++
            if (!isUserVip && customAdsConfig?.customAdsEnabled == true && customShortsAds.isNotEmpty()) {
                if (watchedEpisodesCounter % shortsAdInterval == 0) {
                    activeCustomVideoAd = customShortsAds.random()
                }
            }
        }
    }

    val singleEpisodeFlingBehavior = PagerDefaults.flingBehavior(
        state = verticalPagerState,
        pagerSnapDistance = PagerSnapDistance.atMost(1),
        snapAnimationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
    )

    val currentVideoUrl = remember(currentEp, slug) { resolveBestEpisodeUrl(currentEp, slug) }

    LaunchedEffect(activePageIndex, slug, cleanShortTitle) {
        val target = effectiveEpisodes.getOrNull(activePageIndex)
        if (target != null) {
            viewModel.selectEpisode(target)
            val numericUid = authState.userProfile?.id?.filter { it.isDigit() }?.toIntOrNull()
            AppAnalyticsTracker.trackScreen(context, "Watching Short: $cleanShortTitle - Ep ${target.episodeNumber}", numericUid)
        }
    }

    val shortDramaRecommendations = remember(homeState.popularDramas, homeState.shortsContent, slug) {
        (homeState.shortsContent + homeState.popularDramas.filter { it.isShorts })
            .distinctBy { it.slug }
            .filter { it.slug != slug }
            .take(12)
    }

    // 🚀 ExoPlayer
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
            .setBufferDurationsMs(2000, 45000, 1000, 1500)
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

    fun exitPlayerCleanly() {
        try {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        } catch (_: Exception) {}
        onBackClick()
    }

    fun advanceToNextEpisode() {
        val nextIdx = verticalPagerState.currentPage + 1
        if (nextIdx < totalEpCount) {
            coroutineScope.launch {
                verticalPagerState.animateScrollToPage(
                    page = nextIdx,
                    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    BackHandler {
        when {
            activeCustomVideoAd != null -> {}
            showBatchDownloadDialog -> showBatchDownloadDialog = false
            showQualitySelectionSheet -> showQualitySelectionSheet = false
            showSpeedSelectionSheet -> showSpeedSelectionSheet = false
            isHalfDrawerOpen -> isHalfDrawerOpen = false
            isImmersiveFullscreen -> isImmersiveFullscreen = false
            else -> exitPlayerCleanly()
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
                Lifecycle.Event.ON_STOP -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> {
                    if (isPlaying && activeCustomVideoAd == null) {
                        exoPlayer.playWhenReady = true
                        exoPlayer.play()
                    }
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
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.release()
        }
    }

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
                    if (activeCustomVideoAd == null) exoPlayer.play()
                } else if (state == Player.STATE_ENDED) {
                    advanceToNextEpisode()
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
            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            val d = exoPlayer.duration
            currentPositionMs = pos
            if (d > 0) totalDurationMs = d

            if (totalDurationMs > 2000L && pos >= (totalDurationMs - 300L)) {
                advanceToNextEpisode()
            }
            delay(100L)
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

            exoPlayer.setMediaItem(currentItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            if (activeCustomVideoAd == null) exoPlayer.play()
        } catch (_: Exception) {}
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // 🔝 ১. ওপরের কালো ব্যাকগ্রাউন্ড বার (Ep নম্বর ও ডাউনলোড বাটন সহ)
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
                                .clickable { exitPlayerCleanly() }
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
                    isBuffering = isBuffering,
                    currentEpNum = currentEpNum,
                    isPlaying = isPlaying,
                    isControlsVisible = isControlsVisible,
                    isImmersiveFullscreen = isImmersiveFullscreen,
                    resizeMode = if (isImmersiveFullscreen) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else videoResizeMode,
                    onBackClick = { exitPlayerCleanly() },
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

                // =============================================================
                // 🎯 ৩. পেজারের ভেতরের কন্টেন্ট (স্ক্রল করার সময় স্মুথভাবে উপরে যাবে)
                // =============================================================
                VerticalPager(
                    state = verticalPagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = !isUserSeeking && !is2xActive,
                    flingBehavior = singleEpisodeFlingBehavior
                ) { page ->
                    val pageOffset = ((verticalPagerState.currentPage - page) + verticalPagerState.currentPageOffsetFraction)
                    val absOffset = abs(pageOffset)
                    val smoothAlpha = (1f - absOffset * 1.4f).coerceIn(0f, 1f)

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = smoothAlpha
                            }
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
                    ) {
                        // 🎯 সাইডের অ্যাকশন আইকনগুলো (Like, Share, Save) পেজের সাথে স্ক্রল হবে
                        if (!isImmersiveFullscreen && !isHalfDrawerOpen && page == activePageIndex) {
                            ShortsActionColumn(
                                context = context,
                                title = cleanShortTitle,
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

                        // 🎯 টাইটেল, ডেসক্রিপশন ও টাইমলাইন পেজের সাথে স্মুথভাবে স্ক্রল হবে
                        if (!isImmersiveFullscreen && !isHalfDrawerOpen && page == activePageIndex) {
                            ShortsVideoFloatingOverlay(
                                content = content.copy(
                                    title = cleanShortTitle,
                                    rawDisplayName = cleanShortTitle
                                ),
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
                    }
                }

                // ⚡ ২X স্পিড
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

                // প্লে/পজ ও ১০ সেকেন্ড স্কিপ কন্ট্রোলস
                if (isControlsVisible) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(52.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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

            // ⬛ ৪. সলিড কালো ব্যাকগ্রাউন্ড বার (Ep নম্বর, স্পিড ও কোয়ালিটি)
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
        // 📢 ৫. কাস্টম ভিডিও অ্যাড ডায়ালগ
        // =========================================================================
        activeCustomVideoAd?.let { ad ->
            exoPlayer.pause()
            CustomVideoAdDialog(
                ad = ad,
                onAdFinishedOrSkipped = {
                    activeCustomVideoAd = null
                    exoPlayer.play()
                },
                onNavigateInternalScreen = { target ->
                    activeCustomVideoAd = null
                    when {
                        target.contains("vip", true) -> onNavigateToVip()
                        target.startsWith("drama:") -> {
                            val newSlug = target.removePrefix("drama:").trim()
                            viewModel.loadDramaDetails(newSlug, context)
                        }
                    }
                },
                onOpenExternalUrl = { url ->
                    activeCustomVideoAd = null
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                }
            )
        }

        // =========================================================================
        // 📑 ৬. সমস্ত বটম শিটসমূহ
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
                        content = content.copy(
                            title = cleanShortTitle,
                            rawDisplayName = cleanShortTitle
                        ),
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
                title = cleanShortTitle,
                slug = slug,
                episodes = effectiveEpisodes,
                isVip = isUserVip,
                onDismiss = { showBatchDownloadDialog = false },
                onNavigateToVip = onNavigateToVip,
                onDownloadSelected = { selectedList, chosenQualityKey ->
                    showBatchDownloadDialog = false
                    selectedList.forEach { ep ->
                        val pad = String.format(Locale.US, "%02d", ep.episodeNumber)
                        val customBatchTitle = "$cleanShortTitle - Ep $pad (${chosenQualityKey.uppercase()}) - By PdFlix"
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
