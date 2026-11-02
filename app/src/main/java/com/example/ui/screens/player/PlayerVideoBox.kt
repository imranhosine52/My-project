@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalFoundationApi::class,
    UnstableApi::class
)

package com.example.ui.screens.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TextStyle
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.ads.UnifiedAdManager
import com.example.data.model.ContentItemDto
import com.example.data.model.CustomAdsConfigResponse
import com.example.data.model.CustomVideoAdDto
import com.example.data.model.EpisodeDto
import com.example.ui.components.InPlayerAdCountdownNotice
import com.example.ui.components.InPlayerCustomVideoAdView
import com.example.ui.components.InPlayerDirectLinkRequirementOverlay
import com.example.ui.screens.player.components.*
import com.example.ui.screens.shorts.RealVideoTrack
import com.example.util.DownloadQuotaManager
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
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private suspend fun fetchRealFileSize(url: String): Long = withContext(Dispatchers.IO) {
    if (url.isBlank()) return@withContext 0L
    try {
        val target = if (url.contains(".m3u8")) url.substringBeforeLast("/") + "/download_720p.mp4" else url
        val connection = (URL(target).openConnection() as? HttpURLConnection)?.apply {
            requestMethod = "HEAD"
            connectTimeout = 3000
            readTimeout = 3000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "PlayDramaFlix")
        }
        var length = connection?.contentLengthLong ?: 0L
        connection?.disconnect()

        if (length <= 0L && url.contains(".m3u8")) {
            val fallback = url.substringBeforeLast("/") + "/download.mp4"
            val conn2 = (URL(fallback).openConnection() as? HttpURLConnection)?.apply {
                requestMethod = "HEAD"
                connectTimeout = 2500
                readTimeout = 2500
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "PlayDramaFlix")
            }
            length = conn2?.contentLengthLong ?: 0L
            conn2?.disconnect()
        }

        if (length > 0) length else 0L
    } catch (_: Exception) {
        0L
    }
}

private fun findActivity(context: Context): Activity? {
    var current = context
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@Composable
fun PlayerVideoBox(
    exoPlayer: ExoPlayer,
    title: String,
    slug: String = "",
    episodeNumber: Int,
    downloadUrl: String,
    isDeviceLandscape: Boolean,
    currentPositionMs: Long,
    totalDurationMs: Long,
    isPlaying: Boolean,
    isVip: Boolean = false,
    onBackClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSeek: (seconds: Int) -> Unit,
    onSeekFinished: (positionMs: Long) -> Unit,
    onToggleFullscreen: () -> Unit,
    onShareClick: () -> Unit = {},
    onDownloadClick: (() -> Unit)? = null,
    episodes: List<EpisodeDto> = emptyList(),
    shouldLockEpisodes: Boolean = false,
    onSelectEpisode: (EpisodeDto) -> Unit = {},
    onNextEpisode: () -> Unit = {},
    onSendComment: (String) -> Unit = {},
    onNavigateToVip: () -> Unit = {},
    comments: List<Any> = emptyList(),
    recommendations: List<ContentItemDto> = emptyList(),
    onRelatedDramaClick: (String) -> Unit = {},
    posterUrl: String? = null,
    onRetryPlayback: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { findActivity(context) }
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val configuration = LocalConfiguration.current
    val coroutineScope = rememberCoroutineScope()

    var isControlsVisible by remember { mutableStateOf(true) }
    var isScreenLocked by rememberSaveable { mutableStateOf(false) }

    var is2xActive by remember { mutableStateOf(false) }
    var previousSpeedBefore2x by remember { mutableFloatStateOf(1.0f) }

    // 🎯 কোয়ালিটি ও ট্র্যাক ট্র্যাকার
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

    var isMuted by remember { mutableStateOf(false) }

    var bufferedPositionMs by remember { mutableLongStateOf(0L) }
    var isBuffering by remember { mutableStateOf(exoPlayer.playbackState == Player.STATE_BUFFERING) }
    var isFirstFrameRendered by remember { mutableStateOf(false) }
    var playbackErrorMessage by remember { mutableStateOf<String?>(null) }

    // =========================================================================
    // 📡 কাস্টম বিজ্ঞাপন ও সার্ভার কিউ-পয়েন্ট কনফিগারেশন
    // =========================================================================
    var customAdsConfig by remember { mutableStateOf<CustomAdsConfigResponse?>(null) }
    var activeInPlayerCustomAd by remember { mutableStateOf<CustomVideoAdDto?>(null) }
    var isDirectLinkOverlayActive by remember { mutableStateOf(false) }
    var pendingAdClicks by rememberSaveable(slug, episodeNumber) { mutableIntStateOf(0) }

    // ৩-সেকেন্ড কাউন্টডাউন ট্র্যাকার
    var activeCountdownSeconds by remember { mutableIntStateOf(0) }
    var isCountdownForDirectLink by remember { mutableStateOf(false) }
    val triggeredCueMarks = rememberSaveable(slug, episodeNumber) { mutableSetOf<Long>() }

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

    val longVideoAds = remember(customAdsConfig) {
        customAdsConfig?.ads?.filter { it.placement == "long_video" || it.placement == "all" } ?: emptyList()
    }

    val longVideoRules = remember(customAdsConfig) {
        customAdsConfig?.longVideoRules
    }

    // 🎯 সার্ভার নিয়ন্ত্রিত কিউ-পয়েন্টস (৮ মিনিট ফিক্সড নয়)
    val serverCuePointsMs = remember(totalDurationMs, longVideoRules, isVip) {
        if (isVip || longVideoRules?.enabled != true || totalDurationMs <= 0L) {
            emptyList<Long>()
        } else {
            longVideoRules.calculateCuePointsMs(totalDurationMs)
        }
    }

    val adCueFractions = remember(totalDurationMs, serverCuePointsMs) {
        if (totalDurationMs <= 0L || serverCuePointsMs.isEmpty()) {
            emptyList()
        } else {
            serverCuePointsMs.map { (it.toFloat() / totalDurationMs.toFloat()).coerceIn(0.01f, 0.99f) }
        }
    }

    // =========================================================================
    // ⏱️ বিজ্ঞাপন আসার ৩ সেকেন্ড আগে কাউন্টডাউন ও বিজ্ঞাপন প্রদর্শন লজিক
    // =========================================================================
    LaunchedEffect(currentPositionMs, serverCuePointsMs, isPlaying, isVip) {
        if (isVip || serverCuePointsMs.isEmpty() || !isPlaying || totalDurationMs <= 0L) {
            activeCountdownSeconds = 0
            return@LaunchedEffect
        }

        val upcomingCue = serverCuePointsMs.firstOrNull { cueMs ->
            cueMs !in triggeredCueMarks && currentPositionMs in (cueMs - 3200L)..<cueMs
        }

        if (upcomingCue != null) {
            val diffSec = ((upcomingCue - currentPositionMs) / 1000L).toInt().coerceIn(1, 3)
            activeCountdownSeconds = diffSec
            val hasVideoAds = longVideoAds.isNotEmpty()
            isCountdownForDirectLink = !hasVideoAds
        } else {
            activeCountdownSeconds = 0
        }

        val reachedCue = serverCuePointsMs.firstOrNull { cueMs ->
            cueMs !in triggeredCueMarks && currentPositionMs >= cueMs && currentPositionMs <= (cueMs + 2000L)
        }

        if (reachedCue != null) {
            triggeredCueMarks.add(reachedCue)
            activeCountdownSeconds = 0

            if (longVideoAds.isNotEmpty()) {
                activeInPlayerCustomAd = longVideoAds.random()
                exoPlayer.pause()
            } else if (UnifiedAdManager.isDirectLinkAvailable(isVip)) {
                pendingAdClicks = longVideoRules?.directLinkClicksPerAd ?: 1
                isDirectLinkOverlayActive = true
                exoPlayer.pause()
            }
        }
    }

    val targetStartupBufferBytes = 2 * 1024 * 1024L
    var realDownloadedBytes by remember(episodeNumber, slug) { mutableLongStateOf(0L) }
    var realBitrateBps by remember(episodeNumber, slug) { mutableLongStateOf(0L) }
    var smoothProgressPct by remember(episodeNumber, slug) { mutableFloatStateOf(1f) }
    var hasCompletedStartup by remember(episodeNumber, slug) { mutableStateOf(false) }

    val isVideoActuallyPlaying = (isPlaying && currentPositionMs > 300L) || (exoPlayer.isPlaying && currentPositionMs > 100L)

    LaunchedEffect(episodeNumber, slug) {
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
                    if (!exoPlayer.isPlaying && activeInPlayerCustomAd == null && !isDirectLinkOverlayActive) {
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

    val watchPrefs = remember(slug) {
        context.getSharedPreferences("drama_last_seen_prefs", Context.MODE_PRIVATE)
    }

    var showLastSeenBanner by remember(slug, episodeNumber) { mutableStateOf(false) }
    var lastSeenPosMs by remember(slug, episodeNumber) { mutableLongStateOf(0L) }

    LaunchedEffect(slug, episodeNumber) {
        val saved = watchPrefs.getLong("seen_${slug}_ep$episodeNumber", 0L)
        if (saved > 6000L) {
            lastSeenPosMs = saved
            showLastSeenBanner = true
            delay(7000L)
            showLastSeenBanner = false
        }
    }

    LaunchedEffect(currentPositionMs, isPlaying) {
        if (isPlaying && currentPositionMs > 4000L && totalDurationMs > 0L && currentPositionMs < (totalDurationMs - 10000L)) {
            watchPrefs.edit().putLong("seen_${slug}_ep$episodeNumber", currentPositionMs).apply()
        }
    }

    val lastWatchedEpPrefs = remember(slug) {
        context.getSharedPreferences("drama_last_watched_ep_prefs", Context.MODE_PRIVATE)
    }

    LaunchedEffect(slug, episodeNumber) {
        if (slug.isNotBlank() && episodeNumber > 0) {
            lastWatchedEpPrefs.edit().putInt("last_ep_$slug", episodeNumber).apply()
        }
    }

    var showSideDrawer by remember { mutableStateOf(false) }
    var sideDrawerType by remember { mutableStateOf("playlist") }

    val downloadQualityOptions = remember(episodes) {
        val detected = mutableListOf<SideDrawerQualityItem>()
        val seen = mutableSetOf<String>()

        episodes.forEach { ep ->
            ep.downloadOptions?.forEach { opt ->
                val qLower = opt.quality.lowercase()
                val (key, label) = when {
                    qLower.contains("1080") -> Pair("1080p", "1080P")
                    qLower.contains("720")  -> Pair("720p", "720P")
                    qLower.contains("480")  -> Pair("480p", "480P")
                    qLower.contains("360")  -> Pair("360p", "360P")
                    else                   -> Pair("single", "HD")
                }
                if (seen.add(key)) detected.add(SideDrawerQualityItem(key, label, isVipOnly = true))
            }
        }

        if (detected.isEmpty()) {
            listOf(
                SideDrawerQualityItem("1080p", "1080P", isVipOnly = true),
                SideDrawerQualityItem("720p", "720P", isVipOnly = true),
                SideDrawerQualityItem("480p", "480P", isVipOnly = true),
                SideDrawerQualityItem("360p", "360P", isVipOnly = true)
            )
        } else {
            detected.sortedByDescending { it.key }
        }
    }

    var selectedDownloadQualityKey by remember(downloadQualityOptions) {
        mutableStateOf(downloadQualityOptions.first().key)
    }

    val selectedDownloadEpisodes = remember { mutableStateListOf<EpisodeDto>() }
    val realFileSizes = remember { mutableStateMapOf<String, Long>() }
    var isFetchingSizes by remember { mutableStateOf(false) }

    val speedOptions = remember { listOf(2.0f, 1.5f, 1.25f, 1.0f, 0.75f, 0.5f) }
    var currentSpeed by rememberSaveable { mutableFloatStateOf(1.0f) }

    LaunchedEffect(selectedDownloadEpisodes.toList(), selectedDownloadQualityKey) {
        val uncalculated = selectedDownloadEpisodes.filter { ep ->
            val key = "${ep.episodeId}_$selectedDownloadQualityKey"
            !realFileSizes.containsKey(key) || (realFileSizes[key] ?: 0L) <= 0L
        }

        if (uncalculated.isNotEmpty()) {
            isFetchingSizes = true
            uncalculated.forEach { ep ->
                coroutineScope.launch {
                    val matchingOpt = ep.downloadOptions?.firstOrNull {
                        it.quality.contains(selectedDownloadQualityKey, true) || it.url.contains(selectedDownloadQualityKey, true)
                    }
                    val dlUrl = matchingOpt?.url?.takeIf { it.isNotBlank() } ?: ep.resolveDownloadUrl(slug)
                    val size = fetchRealFileSize(dlUrl)
                    val key = "${ep.episodeId}_$selectedDownloadQualityKey"
                    if (size > 0) {
                        realFileSizes[key] = size
                    }
                }
            }
            isFetchingSizes = false
        }
    }

    val totalSelectedBytes = remember(selectedDownloadEpisodes.toList(), selectedDownloadQualityKey, realFileSizes.toMap()) {
        selectedDownloadEpisodes.sumOf { ep ->
            val key = "${ep.episodeId}_$selectedDownloadQualityKey"
            realFileSizes[key] ?: 0L
        }
    }

    val isPiPActive = remember(activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity?.isInPictureInPictureMode == true
        } else false
    }

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var zoomOffset by remember { mutableStateOf(Offset.Zero) }

    var brightnessLevel by remember {
        mutableFloatStateOf(activity?.window?.attributes?.screenBrightness?.takeIf { it > 0 } ?: 0.5f)
    }
    var showBrightnessOverlay by remember { mutableStateOf(false) }

    var volumeFloat by remember {
        val maxV = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
        val curV = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
        mutableFloatStateOf(if (maxV > 0) curV / maxV else 0.5f)
    }
    var showVolumeOverlay by remember { mutableStateOf(false) }

    var isHorizontalScrubbing by remember { mutableStateOf(false) }
    var scrubTargetPositionMs by remember { mutableLongStateOf(0L) }

    var isUserSeeking by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableLongStateOf(0L) }

    var showLeftDoubleTapAnim by remember { mutableStateOf(false) }
    var showRightDoubleTapAnim by remember { mutableStateOf(false) }

    fun extractTracks(tracks: Tracks) {
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
            availableVideoTracks = listOf(RealVideoTrack(0, 0, 0, "Auto", isAuto = true)) + foundVideoTracks.distinctBy { it.label }

            if (!isVip && currentSelectedHeight == 0) {
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .setMaxVideoSize(854, 480)
                    .build()
            }
        } else if (foundVideoTracks.size == 1) {
            hasMultipleQualities = false
            availableVideoTracks = foundVideoTracks
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
                .build()
        } else {
            hasMultipleQualities = false
            availableVideoTracks = listOf(RealVideoTrack(0, 0, 0, "Auto", isAuto = true))
        }

        if (foundAudioTracks.isEmpty()) {
            val sampleLanguages = listOf("Hindi", "Tamil", "Telugu", "Malayalam", "Bengali", "English", "Japanese")
            availableAudioTracks = sampleLanguages.mapIndexed { idx, langName ->
                RealAudioTrack(
                    id = "audio_$idx",
                    language = langName.lowercase(),
                    label = langName,
                    groupIndex = 0,
                    trackIndex = idx,
                    isSelected = idx == 0
                )
            }
        } else {
            availableAudioTracks = foundAudioTracks.distinctBy { it.label }
        }

        availableSubtitleTracks = foundSubtitleTracks.distinctBy { it.label }
    }

    fun applyExoPlayerQuality(targetResolution: Int, label: String) {
        currentSelectedHeight = targetResolution
        currentQualityLabel = if (targetResolution == 0) "Auto" else "${targetResolution}P"

        if (targetResolution == 0) {
            val builder = exoPlayer.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)

            if (!isVip && hasMultipleQualities) {
                builder.setMaxVideoSize(854, 480)
            } else {
                builder.setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
            }

            exoPlayer.trackSelectionParameters = builder.build()
        } else {
            val currentTracks = exoPlayer.currentTracks
            var overrideApplied = false

            for (group in currentTracks.groups) {
                if (group.type == C.TRACK_TYPE_VIDEO) {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val res = if (format.width > 0 && format.height > 0) minOf(format.width, format.height) else format.height

                        if (res == targetResolution) {
                            val builder = exoPlayer.trackSelectionParameters
                                .buildUpon()
                                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))

                            builder.setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
                            exoPlayer.trackSelectionParameters = builder.build()
                            overrideApplied = true
                            break
                        }
                    }
                }
                if (overrideApplied) break
            }
        }
        Toast.makeText(context, "Quality: $label", Toast.LENGTH_SHORT).show()
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

    DisposableEffect(exoPlayer) {
        val playerListener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                extractTracks(tracks)
            }

            override fun onRenderedFirstFrame() {
                isFirstFrameRendered = true
                isBuffering = false
                hasCompletedStartup = true
                playbackErrorMessage = null
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (currentSelectedHeight == 0 && videoSize.height > 0) {
                    val res = minOf(videoSize.width, videoSize.height)
                    currentQualityLabel = "${res}P"
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = (state == Player.STATE_BUFFERING)
                if (state == Player.STATE_READY) {
                    isFirstFrameRendered = true
                    isBuffering = false
                    hasCompletedStartup = true
                    playbackErrorMessage = null
                }
                bufferedPositionMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)
            }

            override fun onIsPlayingChanged(playingState: Boolean) {
                if (playingState) {
                    isFirstFrameRendered = true
                    isBuffering = false
                    hasCompletedStartup = true
                    playbackErrorMessage = null
                }
                bufferedPositionMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)
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

        exoPlayer.addListener(playerListener)
        exoPlayer.addAnalyticsListener(analyticsListener)

        onDispose {
            exoPlayer.removeListener(playerListener)
            exoPlayer.removeAnalyticsListener(analyticsListener)
        }
    }

    LaunchedEffect(isPlaying, isUserSeeking) {
        while (isPlaying) {
            bufferedPositionMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)
            delay(500L)
        }
    }

    fun triggerSkipWithFeedback(seconds: Int) {
        onSeek(seconds)
        coroutineScope.launch {
            if (seconds < 0) {
                showLeftDoubleTapAnim = true
                delay(650)
                showLeftDoubleTapAnim = false
            } else {
                showRightDoubleTapAnim = true
                delay(650)
                showRightDoubleTapAnim = false
            }
        }
    }

    LaunchedEffect(isControlsVisible, isPlaying, isScreenLocked, showSideDrawer) {
        if (isControlsVisible && isPlaying && !isScreenLocked && !showSideDrawer) {
            delay(5000L)
            isControlsVisible = false
        }
    }

    fun enterPiPMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                isControlsVisible = false
                showSideDrawer = false
                activity?.enterPictureInPictureMode(params)
            } catch (_: Exception) {
                activity?.enterPictureInPictureMode()
            }
        } else {
            Toast.makeText(context, "PiP not supported", Toast.LENGTH_SHORT).show()
        }
    }

    fun openCastSettings() {
        try {
            val castIntent = Intent(Settings.ACTION_CAST_SETTINGS)
            context.startActivity(castIntent)
        } catch (_: Exception) {
            Toast.makeText(context, "Searching for Wireless Display / TV...", Toast.LENGTH_SHORT).show()
        }
    }

    val isForYouDocked = showSideDrawer && sideDrawerType == "for_you" && isDeviceLandscape && !isPiPActive

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(isScreenLocked, isPiPActive, isDeviceLandscape, showSideDrawer) {
                    if (isScreenLocked || isPiPActive || showSideDrawer) return@pointerInput

                    awaitEachGesture {
                        val firstDown = awaitFirstDown(requireUnconsumed = false)
                        val isLeft = firstDown.position.x < size.width / 2f
                        val isRightEdge = firstDown.position.x > size.width * 0.78f

                        var isTransforming = false
                        var isVerticalDragging = false
                        var isHorizontalDragging = false
                        var totalDragY = 0f
                        var totalDragX = 0f
                        val touchSlop = viewConfiguration.touchSlop
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
                        val startScrubPos = exoPlayer.currentPosition

                        if (isLeft && maxVol > 0) {
                            val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                            volumeFloat = cur / maxVol
                        }

                        do {
                            val event = awaitPointerEvent()
                            val pointerCount = event.changes.size

                            if (pointerCount >= 2) {
                                isTransforming = true
                                showBrightnessOverlay = false
                                showVolumeOverlay = false
                                isHorizontalScrubbing = false

                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()

                                zoomScale = (zoomScale * zoomChange).coerceIn(1.0f, 3.0f)
                                if (zoomScale > 1.0f) {
                                    val maxOffsetX = (size.width * (zoomScale - 1f)) / 2f
                                    val maxOffsetY = (size.height * (zoomScale - 1f)) / 2f
                                    zoomOffset = Offset(
                                        x = (zoomOffset.x + panChange.x).coerceIn(-maxOffsetX, maxOffsetX),
                                        y = (zoomOffset.y + panChange.y).coerceIn(-maxOffsetY, maxOffsetY)
                                    )
                                } else {
                                    zoomOffset = Offset.Zero
                                }
                                event.changes.forEach { it.consume() }
                            } else if (pointerCount == 1 && !isTransforming) {
                                val change = event.changes.first()
                                val dragY = change.position.y - change.previousPosition.y
                                val dragX = change.position.x - change.previousPosition.x

                                totalDragY += dragY
                                totalDragX += dragX

                                if (!isVerticalDragging && !isHorizontalDragging && abs(totalDragY) > touchSlop && abs(totalDragY) > abs(totalDragX)) {
                                    isVerticalDragging = true
                                    if (isLeft) showVolumeOverlay = true else showBrightnessOverlay = true
                                }

                                if (!isVerticalDragging && !isHorizontalDragging && abs(totalDragX) > touchSlop && abs(totalDragX) > abs(totalDragY)) {
                                    if (!(isDeviceLandscape && isRightEdge && totalDragX < 0)) {
                                        isHorizontalDragging = true
                                        isHorizontalScrubbing = true
                                    }
                                }

                                if (isVerticalDragging) {
                                    val delta = -dragY / (size.height * 0.70f)
                                    if (isLeft) {
                                        volumeFloat = (volumeFloat + delta).coerceIn(0f, 1f)
                                        val targetVolInt = (volumeFloat * maxVol).roundToInt()
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolInt, 0)
                                    } else {
                                        brightnessLevel = (brightnessLevel + delta).coerceIn(0.01f, 1.0f)
                                        activity?.window?.let { win ->
                                            val lp = win.attributes
                                            lp.screenBrightness = brightnessLevel
                                            win.attributes = lp
                                        }
                                    }
                                    change.consume()
                                } else if (isHorizontalDragging) {
                                    val deltaSec = (totalDragX / 5.5f).toInt()
                                    val targetPos = (startScrubPos + (deltaSec * 1000L)).coerceIn(0L, totalDurationMs.coerceAtLeast(1L))
                                    scrubTargetPositionMs = targetPos
                                    change.consume()
                                } else if (isDeviceLandscape && isRightEdge && totalDragX < -touchSlop * 1.5f && abs(totalDragX) > abs(totalDragY)) {
                                    sideDrawerType = "for_you"
                                    showSideDrawer = true
                                    change.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        if (isHorizontalDragging) {
                            onSeekFinished(scrubTargetPositionMs)
                            exoPlayer.seekTo(scrubTargetPositionMs)
                        }

                        showBrightnessOverlay = false
                        showVolumeOverlay = false
                        isHorizontalScrubbing = false
                    }
                }
                .pointerInput(isScreenLocked, showSideDrawer, isPiPActive, isUserSeeking, sideDrawerType) {
                    if (isScreenLocked || isPiPActive) return@pointerInput

                    detectTapGestures(
                        onPress = {
                            var holdJob: Job? = null
                            try {
                                holdJob = coroutineScope.launch {
                                    delay(400)
                                    if (!isUserSeeking && !isHorizontalScrubbing && !showSideDrawer) {
                                        is2xActive = true
                                        previousSpeedBefore2x = currentSpeed
                                        exoPlayer.setPlaybackSpeed(2.0f)
                                    }
                                }
                                tryAwaitRelease()
                            } finally {
                                holdJob?.cancel()
                                if (is2xActive) {
                                    is2xActive = false
                                    exoPlayer.setPlaybackSpeed(previousSpeedBefore2x)
                                }
                            }
                        },
                        onTap = {
                            if (showSideDrawer && sideDrawerType == "for_you") {
                                showSideDrawer = false
                                return@detectTapGestures
                            }

                            if (showSideDrawer) {
                                showSideDrawer = false
                                isControlsVisible = false
                            } else {
                                isControlsVisible = !isControlsVisible
                            }
                        },
                        onDoubleTap = { offset ->
                            if (!isScreenLocked && !showSideDrawer && !isPiPActive) {
                                if (zoomScale > 1.05f) {
                                    zoomScale = 1.0f
                                    zoomOffset = Offset.Zero
                                } else {
                                    if (offset.x < size.width / 2) {
                                        triggerSkipWithFeedback(-10)
                                    } else {
                                        triggerSkipWithFeedback(10)
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
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        keepScreenOn = true
                    }
                },
                update = { view ->
                    if (view.player != exoPlayer) {
                        view.player = exoPlayer
                    }
                    view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = zoomScale,
                        scaleY = zoomScale,
                        translationX = zoomOffset.x,
                        translationY = zoomOffset.y
                    )
            )

            // Starting soon overlay
            androidx.compose.animation.AnimatedVisibility(
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

            // Error Dialog
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
                            .fillMaxWidth(if (isDeviceLandscape) 0.50f else 0.86f)
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
                                    onClick = onBackClick,
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
                                        onRetryPlayback?.invoke() ?: run {
                                            exoPlayer.prepare()
                                            exoPlayer.play()
                                        }
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

            // =========================================================================
            // 🖐️ ৯. জেসচার ওভারলে (ভলিউম, ব্রাইটনেস, ২X, স্ক্রাবিং ও ডাবল-ট্যাপ স্কিপ)
            // =========================================================================
            PlayerGesturesOverlay(
                is2xActive = is2xActive,
                isHorizontalScrubbing = isHorizontalScrubbing,
                scrubTargetPositionMs = scrubTargetPositionMs,
                totalDurationMs = totalDurationMs,
                showLeftDoubleTapAnim = showLeftDoubleTapAnim,
                showRightDoubleTapAnim = showRightDoubleTapAnim,
                showVolumeOverlay = showVolumeOverlay && !isPiPActive && !showSideDrawer,
                volumeFloat = volumeFloat,
                showBrightnessOverlay = showBrightnessOverlay && !isPiPActive && !showSideDrawer,
                brightnessLevel = brightnessLevel,
                screenWidthDp = configuration.screenWidthDp.dp,
                modifier = Modifier.fillMaxSize()
            )

            // Buffering Spinner
            if (isBuffering && isFirstFrameRendered && !isPiPActive) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF00E5FF), strokeWidth = 3.dp, modifier = Modifier.size(42.dp))
                }
            }

            // =========================================================================
            // ⏱️ ৩-সেকেন্ড কাউন্টডাউন ব্যানার (নিচে নামানো ও হলুদ বর্ডার/ঘড়ি রিমুভড)
            // =========================================================================
            if (activeCountdownSeconds in 1..3 && activeInPlayerCustomAd == null && !isDirectLinkOverlayActive) {
                InPlayerAdCountdownNotice(
                    secondsLeft = activeCountdownSeconds,
                    isDirectLink = isCountdownForDirectLink,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 14.dp)
                )
            }

            // =========================================================================
            // 🎬 ইন-প্লেয়ার কাস্টম ভিডিও অ্যাড (YouTube Style Embedded)
            // =========================================================================
            if (activeInPlayerCustomAd != null) {
                InPlayerCustomVideoAdView(
                    ad = activeInPlayerCustomAd!!,
                    onAdFinishedOrSkipped = {
                        activeInPlayerCustomAd = null
                        exoPlayer.play()
                    },
                    onOpenAdTarget = { targetUrl ->
                        activeInPlayerCustomAd = null
                        if (targetUrl.contains("vip", ignoreCase = true)) {
                            onNavigateToVip()
                        } else {
                            val opened = UnifiedAdManager.openChromeCustomTab(context, targetUrl)
                            if (!opened) {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                        }
                        exoPlayer.play()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // =========================================================================
            // 🔒 ডাইরেক্ট লিংক ক্লিক অ্যাড ওভারলে (নন-স্কিপেবল)
            // =========================================================================
            if (isDirectLinkOverlayActive && pendingAdClicks > 0 && activeInPlayerCustomAd == null) {
                InPlayerDirectLinkRequirementOverlay(
                    remainingClicks = pendingAdClicks,
                    onVisitSponsor = {
                        pendingAdClicks--
                        UnifiedAdManager.openAdsterraDirectLink(context, isVip = false, fallbackUrl = null)
                        if (pendingAdClicks <= 0) {
                            isDirectLinkOverlayActive = false
                            exoPlayer.play()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // =========================================================================
            // 🎮 ১০. টপ, সেন্টার ও বটম প্লেয়ার কন্ট্রোল বার
            // =========================================================================
            if (!isPiPActive && activeInPlayerCustomAd == null) {
                PlayerControlsOverlay(
                    isControlsVisible = isControlsVisible,
                    isScreenLocked = isScreenLocked,
                    isPlaying = isPlaying,
                    isMuted = isMuted,
                    isDeviceLandscape = isDeviceLandscape,
                    currentPositionMs = currentPositionMs,
                    totalDurationMs = totalDurationMs,
                    bufferedPositionMs = bufferedPositionMs,
                    adCueFractions = adCueFractions,
                    isUserSeeking = isUserSeeking,
                    scrubPosition = scrubPosition,
                    showLastSeenBanner = showLastSeenBanner,
                    lastSeenPosMs = lastSeenPosMs,
                    currentSpeed = currentSpeed,
                    currentQualityLabel = currentQualityLabel,
                    currentAudioLabel = currentAudioLabel,
                    currentSubtitleLabel = currentSubtitleLabel,
                    hasMultipleAudio = availableAudioTracks.size > 1,
                    hasSubtitles = availableSubtitleTracks.isNotEmpty(),
                    isSubtitleActive = selectedSubtitleTrackId != "off",
                    onBackClick = onBackClick,
                    onShareClick = onShareClick,
                    onPiPClick = { enterPiPMode() },
                    onCastClick = { openCastSettings() },
                    onPlayPauseClick = onPlayPauseClick,
                    onMuteToggle = {
                        isMuted = !isMuted
                        exoPlayer.volume = if (isMuted) 0f else 1f
                    },
                    onLockToggle = {
                        isScreenLocked = true
                        isControlsVisible = false
                    },
                    onSeekStarted = { isUserSeeking = true },
                    onSeeking = { scrubPosition = it },
                    onSeekFinished = { targetPos ->
                        onSeekFinished(targetPos)
                        isUserSeeking = false
                    },
                    onLastSeenDismiss = { showLastSeenBanner = false },
                    onLastSeenJump = { targetPos ->
                        exoPlayer.seekTo(targetPos)
                        onSeekFinished(targetPos)
                        showLastSeenBanner = false
                    },
                    onNextEpisode = onNextEpisode,
                    onSpeedClick = {
                        sideDrawerType = "speed"
                        showSideDrawer = true
                    },
                    onQualityClick = {
                        sideDrawerType = "quality"
                        showSideDrawer = true
                    },
                    onAudioClick = {
                        sideDrawerType = "audio"
                        showSideDrawer = true
                    },
                    onSubtitleClick = {
                        sideDrawerType = "subtitles"
                        showSideDrawer = true
                    },
                    onEpisodesClick = {
                        sideDrawerType = "playlist"
                        showSideDrawer = !showSideDrawer
                    },
                    onDownloadClick = {
                        if (isDeviceLandscape) {
                            sideDrawerType = "download"
                            showSideDrawer = true
                        } else {
                            onDownloadClick?.invoke()
                        }
                    },
                    onToggleFullscreen = onToggleFullscreen,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // =========================================================================
            // 📑 সাইড ড্রয়ার প্যানেল (আলাদা ফাইলে বিভক্ত)
            // =========================================================================
            androidx.compose.animation.AnimatedVisibility(
                visible = showSideDrawer && sideDrawerType != "for_you" && !isPiPActive,
                enter = slideInHorizontally { it } + fadeIn(),
                exit = slideOutHorizontally { it } + fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(if (isDeviceLandscape) 0.24f else 0.52f)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.85f),
                                    Color.Black.copy(alpha = 0.98f)
                                )
                            )
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {}
                ) {
                    when (sideDrawerType) {
                        "speed" -> {
                            PlayerSpeedDrawer(
                                currentSpeed = currentSpeed,
                                speedOptions = speedOptions,
                                onSelectSpeed = { spd ->
                                    currentSpeed = spd
                                    exoPlayer.setPlaybackSpeed(spd)
                                    showSideDrawer = false
                                }
                            )
                        }
                        "quality" -> {
                            PlayerQualityDrawer(
                                availableVideoTracks = availableVideoTracks,
                                currentSelectedHeight = currentSelectedHeight,
                                hasMultipleQualities = hasMultipleQualities,
                                isVip = isVip,
                                onSelectQuality = { targetRes, label ->
                                    applyExoPlayerQuality(targetRes, label)
                                    showSideDrawer = false
                                },
                                onNavigateToVip = {
                                    onNavigateToVip()
                                    showSideDrawer = false
                                }
                            )
                        }
                        "audio" -> {
                            PlayerAudioDrawer(
                                availableAudioTracks = availableAudioTracks,
                                selectedAudioTrackId = selectedAudioTrackId,
                                onSelectAudioTrack = { audioTrack ->
                                    applyExoPlayerAudioTrack(audioTrack)
                                    showSideDrawer = false
                                }
                            )
                        }
                        "subtitles" -> {
                            PlayerSubtitleDrawer(
                                availableSubtitleTracks = availableSubtitleTracks,
                                selectedSubtitleTrackId = selectedSubtitleTrackId,
                                onSelectSubtitleTrack = { subTrack ->
                                    applyExoPlayerSubtitleTrack(subTrack)
                                    showSideDrawer = false
                                }
                            )
                        }
                        else -> {
                            PlayerPlaylistDrawer(
                                episodes = episodes,
                                currentEpNumber = episodeNumber,
                                sideDrawerType = sideDrawerType,
                                isVip = isVip,
                                shouldLockEpisodes = shouldLockEpisodes,
                                downloadQualityOptions = downloadQualityOptions,
                                selectedDownloadQualityKey = selectedDownloadQualityKey,
                                onSelectQualityKey = { selectedDownloadQualityKey = it },
                                selectedDownloadEpisodes = selectedDownloadEpisodes,
                                totalSelectedBytes = totalSelectedBytes,
                                isFetchingSizes = isFetchingSizes,
                                onSelectEpisode = { ep ->
                                    isFirstFrameRendered = false
                                    smoothProgressPct = 1f
                                    hasCompletedStartup = false
                                    isBuffering = true
                                    onSelectEpisode(ep)
                                    showSideDrawer = false
                                },
                                onStartDownload = {
                                    if (selectedDownloadEpisodes.isNotEmpty()) {
                                        val checkResult = DownloadQuotaManager.checkCanDownload(
                                            context = context,
                                            bytesToDownload = totalSelectedBytes,
                                            isVip = true
                                        )

                                        if (checkResult.canDownload) {
                                            selectedDownloadEpisodes.forEach { ep ->
                                                val matchingOpt = ep.downloadOptions?.firstOrNull {
                                                    it.quality.contains(selectedDownloadQualityKey, true) || it.url.contains(selectedDownloadQualityKey, true)
                                                }
                                                val targetUrl = matchingOpt?.url?.takeIf { it.isNotBlank() } ?: ep.resolveDownloadUrl(slug)

                                                R2DownloadManager.startDownload(
                                                    context = context,
                                                    downloadUrl = targetUrl,
                                                    title = "$title (${selectedDownloadQualityKey.uppercase()})",
                                                    episodeNumber = ep.episodeNumber,
                                                    isMovie = false
                                                )
                                            }
                                            Toast.makeText(context, "Downloading ${selectedDownloadEpisodes.size} episodes", Toast.LENGTH_SHORT).show()
                                            showSideDrawer = false
                                        } else {
                                            Toast.makeText(context, checkResult.message, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                onNavigateToVip = {
                                    onNavigateToVip()
                                    showSideDrawer = false
                                },
                                onClose = { showSideDrawer = false }
                            )
                        }
                    }
                }
            }

            // Unlock Lock Screen Button
            if (isScreenLocked && !isPiPActive) {
                IconButton(
                    onClick = {
                        isScreenLocked = false
                        isControlsVisible = true
                    },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Unlock",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // =========================================================================
        // 🎬 For You Side Panel (আলাদা ফাইলে বিভক্ত)
        // =========================================================================
        androidx.compose.animation.AnimatedVisibility(
            visible = isForYouDocked,
            enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(),
            exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut()
        ) {
            PlayerForYouPanel(
                recommendations = recommendations,
                currentSlug = slug,
                onRelatedDramaClick = { newSlug ->
                    onRelatedDramaClick(newSlug)
                    showSideDrawer = false
                },
                onClose = { showSideDrawer = false }
            )
        }
    }
}
