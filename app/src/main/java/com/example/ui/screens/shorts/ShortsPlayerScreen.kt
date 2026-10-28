@file:OptIn(
    androidx.media3.common.util.UnstableApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
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
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import com.example.data.model.ContentItemDto
import com.example.data.model.CustomAdsConfigResponse
import com.example.data.model.CustomVideoAdDto
import com.example.data.model.EpisodeDto
import com.example.ui.components.CustomVideoAdDialog
import com.example.ui.screens.player.OttDoubleTapSkipBadge
import com.example.ui.screens.player.PdFlixBrandLogo
import com.example.ui.screens.player.RealAudioTrack
import com.example.ui.screens.player.RealSubtitleTrack
import com.example.ui.viewmodel.DramaFlixViewModel
import com.example.util.AppAnalyticsTracker
import com.example.util.R2DownloadManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    UnstableApi::class
)
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

    // 🚀 ExoPlayer Engine
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

    // 📢 Custom Ads Config
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

    val shortsAdInterval = remember(customAdsConfig) {
        customAdsConfig?.shortsRules?.intervalEpisodes?.coerceAtLeast(1) ?: 3
    }

    val isCurrentDramaLoaded = (playerState.content?.slug == slug)

    val content = if (isCurrentDramaLoaded) {
        playerState.content!!
    } else {
        homeState.popularDramas.find { it.slug == slug }
            ?: homeState.shortsContent.find { it.slug == slug }
            ?: ContentItemDto(title = slug.replace("-", " "), slug = slug, type = "shorts")
    }

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
    var isFirstFrameRendered by remember { mutableStateOf(false) }
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
    var showAudioSelectionSheet by remember { mutableStateOf(false) }
    var showSubtitleSelectionSheet by remember { mutableStateOf(false) }

    // 🎯 কোয়ালিটি ও মাল্টি-অডিও/সাবটাইটেল ট্র্যাকার স্টেট
    var availableVideoTracks by remember { mutableStateOf<List<RealVideoTrack>>(emptyList()) }
    var hasMultipleQualities by rememberSaveable { mutableStateOf(false) }
    var currentSelectedHeight by rememberSaveable { mutableIntStateOf(0) }
    var currentQualityLabel by rememberSaveable { mutableStateOf("Auto") }

    var availableAudioTracks by remember { mutableStateOf<List<RealAudioTrack>>(emptyList()) }
    var selectedAudioTrackId by rememberSaveable { mutableStateOf("default") }
    var currentAudioLabel by rememberSaveable { mutableStateOf("Hindi") }

    var availableSubtitleTracks by remember { mutableStateOf<List<RealSubtitleTrack>>(emptyList()) }
    var selectedSubtitleTrackId by rememberSaveable { mutableStateOf("off") }
    var currentSubtitleLabel by rememberSaveable { mutableStateOf("CC") }

    var currentSpeedFloat by rememberSaveable { mutableFloatStateOf(1.0f) }
    var currentSpeedLabel by rememberSaveable { mutableStateOf("1x") }

    var playbackErrorMessage by remember { mutableStateOf<String?>(null) }

    // =========================================================================
    // ⚡ লাইভ বাফারিং স্পিড ও ১% থেকে ১০০% প্রোগ্রেস কাউন্টার
    // =========================================================================
    val targetStartupBufferBytes = 2 * 1024 * 1024L
    var realDownloadedBytes by remember(cleanShortTitle) { mutableLongStateOf(0L) }
    var realBitrateBps by remember(cleanShortTitle) { mutableLongStateOf(0L) }

    var smoothProgressPct by remember(cleanShortTitle) { mutableFloatStateOf(1f) }
    var hasCompletedStartup by remember(cleanShortTitle) { mutableStateOf(false) }

    val isVideoActuallyPlaying = (isPlaying && currentPositionMs > 300L) || (exoPlayer.isPlaying && currentPositionMs > 100L)

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

    LaunchedEffect(currentEpNum, slug) {
        smoothProgressPct = 1f
        hasCompletedStartup = false
        realDownloadedBytes = 0L
        realBitrateBps = 0L
        isFirstFrameRendered = false
        isBuffering = true
        playbackErrorMessage = null

        while (!hasCompletedStartup && playbackErrorMessage == null) {
            val isReady = exoPlayer.playbackState == Player.STATE_READY || isFirstFrameRendered || isVideoActuallyPlaying

            if (isReady) {
                if (smoothProgressPct < 100f) {
                    smoothProgressPct = (smoothProgressPct + 12f).coerceAtMost(100f)
                } else {
                    delay(80L)
                    hasCompletedStartup = true
                    isFirstFrameRendered = true
                    isBuffering = false
                    if (!exoPlayer.isPlaying) {
                        exoPlayer.play()
                    }
                    break
                }
            } else {
                if (realDownloadedBytes > 0L) {
                    val realPct = ((realDownloadedBytes.toFloat() / targetStartupBufferBytes.toFloat()) * 100f).coerceIn(1f, 98f)
                    smoothProgressPct = maxOf(smoothProgressPct + 0.8f, smoothProgressPct + (realPct - smoothProgressPct) * 0.25f).coerceIn(1f, 98f)
                } else {
                    val step = when {
                        smoothProgressPct < 30f -> 1.8f
                        smoothProgressPct < 70f -> 1.2f
                        smoothProgressPct < 90f -> 0.8f
                        else -> 0.3f
                    }
                    smoothProgressPct = (smoothProgressPct + step).coerceIn(1f, 98f)
                }
            }
            delay(45L)
        }
    }

    val displaySpeedText by remember(realBitrateBps, smoothProgressPct) {
        derivedStateOf {
            if (realBitrateBps > 0L) {
                val speedMBs = (realBitrateBps / 8f) / (1024f * 1024f)
                String.format(Locale.US, "%.2fMB/s", speedMBs)
            } else {
                val simulatedSpeed = 2.15f + ((smoothProgressPct % 7) * 0.16f)
                String.format(Locale.US, "%.2fMB/s", simulatedSpeed)
            }
        }
    }

    val shouldShowLoadingScreen = !hasCompletedStartup && playbackErrorMessage == null

    // Scroll counter for Ads
    LaunchedEffect(activePageIndex) {
        if (activePageIndex > 0) {
            watchedEpisodesCounter++
            if (!isUserVip && customAdsConfig?.customAdsEnabled == true && customShortsAds.isNotEmpty()) {
                if (watchedEpisodesCounter % shortsAdInterval == 0) {
                    val candidateAd = customShortsAds.random()
                    exoPlayer.pause()
                    activeCustomVideoAd = candidateAd
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
            showAudioSelectionSheet -> showAudioSelectionSheet = false
            showSubtitleSelectionSheet -> showSubtitleSelectionSheet = false
            isHalfDrawerOpen -> isHalfDrawerOpen = false
            isImmersiveFullscreen -> isImmersiveFullscreen = false
            else -> exitPlayerCleanly()
        }
    }

    // =========================================================================
    // 🎯 ট্র্যাক এক্সট্র্যাকশন (ভিডিও, অডিও ও সাবটাইটেল)
    // =========================================================================
    fun extractRealTracks(tracks: Tracks) {
        val foundVideoTracks = mutableListOf<RealVideoTrack>()
        val foundAudioTracks = mutableListOf<RealAudioTrack>()
        val foundSubtitleTracks = mutableListOf<RealSubtitleTrack>()
        val seenResolutions = mutableSetOf<Int>()

        for ((groupIndex, group) in tracks.groups.withIndex()) {
            if (group.type == C.TRACK_TYPE_VIDEO) {
                for (i in 0 until group.length) {
                    if (group.isTrackSupported(i)) {
                        val format = group.getTrackFormat(i)
                        val resolution = if (format.width > 0 && format.height > 0) minOf(format.width, format.height) else format.height

                        if (resolution > 0 && seenResolutions.add(resolution)) {
                            val label = when {
                                resolution >= 1080 -> "1080p"
                                resolution >= 720 -> "720p"
                                resolution >= 480 -> "480p"
                                else -> "360p"
                            }
                            foundVideoTracks.add(
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
            } else if (group.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.length) {
                    if (group.isTrackSupported(i)) {
                        val format = group.getTrackFormat(i)
                        val lang = format.language?.lowercase() ?: "und"
                        val isSelected = group.isTrackSelected(i)

                        val readableLabel = when {
                            lang.contains("hi") || lang.contains("hin") || format.label?.contains("hindi", true) == true -> "Hindi"
                            lang.contains("ta") || lang.contains("tam") || format.label?.contains("tamil", true) == true -> "Tamil"
                            lang.contains("te") || lang.contains("tel") || format.label?.contains("telugu", true) == true -> "Telugu"
                            lang.contains("ml") || lang.contains("mal") || format.label?.contains("malayalam", true) == true -> "Malayalam"
                            lang.contains("bn") || lang.contains("ben") || format.label?.contains("bengali", true) == true || format.label?.contains("bangla", true) == true -> "Bengali"
                            lang.contains("en") || lang.contains("eng") || format.label?.contains("english", true) == true -> "English"
                            lang.contains("ja") || lang.contains("jpn") || format.label?.contains("japanese", true) == true -> "Japanese"
                            lang.contains("ko") || lang.contains("kor") || format.label?.contains("korean", true) == true -> "Korean"
                            lang.contains("zh") || lang.contains("chi") || format.label?.contains("chinese", true) == true -> "Chinese"
                            else -> format.label?.takeIf { it.isNotBlank() } ?: "Audio ${foundAudioTracks.size + 1}"
                        }

                        val trackId = "${groupIndex}_$i"
                        if (isSelected) {
                            selectedAudioTrackId = trackId
                            currentAudioLabel = readableLabel
                        }

                        foundAudioTracks.add(
                            RealAudioTrack(
                                id = trackId,
                                language = lang,
                                label = readableLabel,
                                groupIndex = groupIndex,
                                trackIndex = i,
                                isSelected = isSelected
                            )
                        )
                    }
                }
            } else if (group.type == C.TRACK_TYPE_TEXT) {
                for (i in 0 until group.length) {
                    if (group.isTrackSupported(i)) {
                        val format = group.getTrackFormat(i)
                        val mime = format.sampleMimeType ?: ""
                        val lang = format.language?.lowercase() ?: ""
                        val label = format.label ?: ""

                        val isCeaDummy = mime.contains("cea", true) || mime.contains("eia", true)
                        val hasValidLanguageOrLabel = (lang.isNotBlank() && lang != "und") || label.isNotBlank()

                        if (!isCeaDummy && hasValidLanguageOrLabel) {
                            val readableLabel = when {
                                lang.contains("bn") || lang.contains("ben") || label.contains("bangla", true) -> "Bangla Subtitle"
                                lang.contains("en") || lang.contains("eng") || label.contains("english", true) -> "English Subtitle"
                                lang.contains("hi") || lang.contains("hin") || label.contains("hindi", true) -> "Hindi Subtitle"
                                else -> label.ifBlank { "Subtitle ${foundSubtitleTracks.size + 1}" }
                            }

                            val trackId = "sub_${groupIndex}_$i"
                            val isSelected = group.isTrackSelected(i)
                            if (isSelected) {
                                selectedSubtitleTrackId = trackId
                                currentSubtitleLabel = "CC"
                            }

                            foundSubtitleTracks.add(
                                RealSubtitleTrack(
                                    id = trackId,
                                    language = lang,
                                    label = readableLabel,
                                    groupIndex = groupIndex,
                                    trackIndex = i,
                                    isSelected = isSelected
                                )
                            )
                        }
                    }
                }
            }
        }

        if (foundVideoTracks.size > 1) {
            hasMultipleQualities = true
            foundVideoTracks.sortByDescending { it.height }
            availableVideoTracks = listOf(
                RealVideoTrack(height = 0, width = 0, bitrate = 0, label = "Auto", isAuto = true)
            ) + foundVideoTracks.distinctBy { it.label }
        } else if (foundVideoTracks.size == 1) {
            hasMultipleQualities = false
            availableVideoTracks = foundVideoTracks
        } else {
            hasMultipleQualities = false
            availableVideoTracks = listOf(RealVideoTrack(height = 0, width = 0, bitrate = 0, label = "Auto", isAuto = true))
        }

        availableAudioTracks = foundAudioTracks.distinctBy { it.label }
        availableSubtitleTracks = foundSubtitleTracks.distinctBy { it.label }
    }

    fun applyExoPlayerQuality(targetResolution: Int, label: String) {
        currentSelectedHeight = targetResolution
        currentQualityLabel = if (targetResolution == 0) "Auto" else "${targetResolution}P"

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

    fun applyExoPlayerAudioTrack(audioTrack: RealAudioTrack) {
        selectedAudioTrackId = audioTrack.id
        currentAudioLabel = audioTrack.label

        val currentTracks = exoPlayer.currentTracks
        val targetGroup = currentTracks.groups.getOrNull(audioTrack.groupIndex)

        if (targetGroup != null && targetGroup.type == C.TRACK_TYPE_AUDIO) {
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setOverrideForType(TrackSelectionOverride(targetGroup.mediaTrackGroup, audioTrack.trackIndex))
                .build()
        }
        Toast.makeText(context, "Audio: ${audioTrack.label}", Toast.LENGTH_SHORT).show()
    }

    fun applyExoPlayerSubtitleTrack(subTrack: RealSubtitleTrack?) {
        if (subTrack == null) {
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .build()
            selectedSubtitleTrackId = "off"
            currentSubtitleLabel = "CC"
            Toast.makeText(context, "Subtitles: Off", Toast.LENGTH_SHORT).show()
        } else {
            val currentTracks = exoPlayer.currentTracks
            val targetGroup = currentTracks.groups.getOrNull(subTrack.groupIndex)
            if (targetGroup != null && targetGroup.type == C.TRACK_TYPE_TEXT) {
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(TrackSelectionOverride(targetGroup.mediaTrackGroup, subTrack.trackIndex))
                    .build()
                selectedSubtitleTrackId = subTrack.id
                currentSubtitleLabel = "CC"
                Toast.makeText(context, "Subtitle: ${subTrack.label}", Toast.LENGTH_SHORT).show()
            }
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

            override fun onRenderedFirstFrame() {
                isFirstFrameRendered = true
                isBuffering = false
                hasCompletedStartup = true
                playbackErrorMessage = null
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
                    isFirstFrameRendered = true
                    hasCompletedStartup = true
                    playbackErrorMessage = null
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
                isFirstFrameRendered = true
                hasCompletedStartup = true
                playbackErrorMessage = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                        "Network connection failed! Please check your internet connection."
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                        "Video server is currently down or file unavailable."
                    else -> "Playback error occurred. Please try again."
                }
            }
        }

        val analyticsListener = object : AnalyticsListener {
            override fun onBandwidthEstimate(
                eventTime: AnalyticsListener.EventTime,
                totalDurationMs: Int,
                totalBytesTransferred: Long,
                bitrateEstimate: Long
            ) {
                if (totalBytesTransferred > realDownloadedBytes) {
                    realDownloadedBytes = totalBytesTransferred
                }
                realBitrateBps = bitrateEstimate
            }

            override fun onLoadCompleted(
                eventTime: AnalyticsListener.EventTime,
                loadEventInfo: LoadEventInfo,
                mediaLoadData: MediaLoadData
            ) {
                if (loadEventInfo.bytesLoaded > 0L) {
                    realDownloadedBytes += loadEventInfo.bytesLoaded
                }
            }
        }

        exoPlayer.addListener(listener)
        exoPlayer.addAnalyticsListener(analyticsListener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.removeAnalyticsListener(analyticsListener)
        }
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
            // 🔝 Top Action Bar
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

            // 🎬 Video Playback Area
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
                // 🌟 Live Buffering Progress Screen with Speed
                // =============================================================
                AnimatedVisibility(
                    visible = shouldShowLoadingScreen,
                    enter = fadeIn(tween(120)),
                    exit = fadeOut(tween(200)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF06080E))
                            .drawBehind {
                                val centerOffset = Offset(size.width / 2f, size.height / 2f)

                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Color(0xFF00E5FF).copy(alpha = 0.16f),
                                            Color(0xFFFF007A).copy(alpha = 0.08f),
                                            Color.Transparent
                                        ),
                                        center = centerOffset,
                                        radius = size.minDimension * 0.72f
                                    ),
                                    radius = size.minDimension * 0.72f,
                                    center = centerOffset
                                )

                                drawRect(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color(0xFF00F0FF).copy(alpha = 0.04f),
                                            Color(0xFFFF007A).copy(alpha = 0.08f)
                                        ),
                                        center = centerOffset,
                                        radius = size.maxDimension * 0.75f
                                    )
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            PdFlixBrandLogo()

                            Spacer(modifier = Modifier.height(18.dp))

                            val statsText = String.format(
                                Locale.US,
                                "Starting soon... %02d%%  %s",
                                smoothProgressPct.toInt().coerceIn(1, 100),
                                displaySpeedText
                            )

                            Text(
                                text = statsText,
                                color = Color.White.copy(alpha = 0.95f),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.4.sp,
                                style = TextStyle(
                                    shadow = androidx.compose.ui.graphics.Shadow(
                                        color = Color.Black.copy(alpha = 0.85f),
                                        blurRadius = 6f
                                    )
                                )
                            )
                        }
                    }
                }

                // =============================================================
                // 🛡️ Error Popup Dialog (100% English)
                // =============================================================
                if (playbackErrorMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.92f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF131722),
                            border = BorderStroke(1.dp, Color(0xFF2E384D)),
                            modifier = Modifier
                                .fillMaxWidth(0.86f)
                                .padding(16.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(22.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFF334B).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudOff,
                                        contentDescription = "Error",
                                        tint = Color(0xFFFF334B),
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                Text(
                                    text = "Playback Problem",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = playbackErrorMessage ?: "Video server is currently unavailable.",
                                    color = Color(0xFF9EABC0),
                                    fontSize = 12.5.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { exitPlayerCleanly() },
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, Color(0xFF3B4861)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Go Back", color = Color.White, fontSize = 12.sp)
                                    }

                                    Button(
                                        onClick = {
                                            playbackErrorMessage = null
                                            isFirstFrameRendered = false
                                            smoothProgressPct = 1f
                                            hasCompletedStartup = false
                                            exoPlayer.prepare()
                                            exoPlayer.play()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Try Again", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

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

                // 2X Speed Indicator
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

                // Play/Pause and Skip Controls
                if (isControlsVisible) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(52.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PureSkipButton(
                                isForward = false,
                                rotation = 0f,
                                onClick = { triggerSkip(-10) }
                            )

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

                            PureSkipButton(
                                isForward = true,
                                rotation = 0f,
                                onClick = { triggerSkip(10) }
                            )
                        }
                    }
                }
            }

            // =============================================================
            // ⬛ Solid Black Bottom Bar (Speed, Quality, Audio & Subtitles)
            // =============================================================
            if (!isImmersiveFullscreen && !isHalfDrawerOpen) {
                ShortsSolidBlackBottomBar(
                    currentEpNum = currentEpNum,
                    totalEpCount = totalEpCount,
                    currentSpeedText = currentSpeedLabel,
                    currentQualityText = currentQualityLabel,
                    currentAudioText = currentAudioLabel,
                    currentSubtitleText = currentSubtitleLabel,
                    hasMultipleAudio = availableAudioTracks.size > 1,
                    hasSubtitles = availableSubtitleTracks.isNotEmpty(),
                    isSubtitleActive = selectedSubtitleTrackId != "off",
                    onOpenEpisodesTab = {
                        drawerInitialTab = 1
                        isHalfDrawerOpen = true
                    },
                    onSpeedClick = { showSpeedSelectionSheet = true },
                    onQualityClick = { showQualitySelectionSheet = true },
                    onAudioClick = { showAudioSelectionSheet = true },
                    onSubtitleClick = { showSubtitleSelectionSheet = true }
                )
            }
        }

        // Custom Video Ads
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

        // Half Drawer
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

        // Quality Sheet
        if (showQualitySelectionSheet) {
            ShortsQualitySelectionSheet(
                availableTracks = availableVideoTracks,
                currentSelectedHeight = currentSelectedHeight,
                hasMultipleQualities = hasMultipleQualities,
                isVip = isUserVip,
                onNavigateToVip = onNavigateToVip,
                onSelectQuality = { targetResolution, label ->
                    applyExoPlayerQuality(targetResolution, label)
                },
                onDismiss = { showQualitySelectionSheet = false }
            )
        }

        // Multi-Audio Selection Sheet
        if (showAudioSelectionSheet) {
            ShortsAudioSelectionSheet(
                audioTracks = availableAudioTracks,
                selectedAudioTrackId = selectedAudioTrackId,
                onSelectAudioTrack = { track ->
                    applyExoPlayerAudioTrack(track)
                },
                onDismiss = { showAudioSelectionSheet = false }
            )
        }

        // Subtitle Selection Sheet
        if (showSubtitleSelectionSheet) {
            ShortsSubtitleSelectionSheet(
                subtitleTracks = availableSubtitleTracks,
                selectedSubtitleTrackId = selectedSubtitleTrackId,
                onSelectSubtitleTrack = { track ->
                    applyExoPlayerSubtitleTrack(track)
                },
                onDismiss = { showSubtitleSelectionSheet = false }
            )
        }

        // Playback Speed Sheet
        if (showSpeedSelectionSheet) {
            ShortsSpeedSelectionSheet(
                currentSpeed = currentSpeedFloat,
                onSelectSpeed = { speed ->
                    applyExoPlayerSpeed(speed)
                },
                onDismiss = { showSpeedSelectionSheet = false }
            )
        }

        // Batch Download Sheet
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
                    Toast.makeText(context, "Download started for ${selectedList.size} episodes!", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}
