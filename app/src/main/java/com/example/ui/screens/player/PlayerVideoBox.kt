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
import android.os.Build
import android.provider.Settings
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.ads.UnifiedAdManager
import com.example.data.model.ContentItemDto
import com.example.data.model.EpisodeDto
import com.example.ui.screens.EqualizerBarsIcon
import com.example.ui.screens.SleekOnlineTimeline
import com.example.ui.screens.shorts.RealVideoTrack
import com.example.ui.theme.GoldVip
import com.example.util.DownloadQuotaManager
import com.example.util.R2DownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs

private data class SideDrawerQualityItem(
    val key: String,
    val label: String,
    val isVipOnly: Boolean
)

data class RealAudioTrack(
    val id: String,
    val language: String,
    val label: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val isSelected: Boolean = false
)

@Composable
private fun MiniCrownVector(modifier: Modifier = Modifier, tint: Color = Color(0xFFF6D38B)) {
    Canvas(modifier = modifier.size(11.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(0f, h * 0.30f)
            lineTo(w * 0.28f, h * 0.65f)
            lineTo(w * 0.50f, 0f)
            lineTo(w * 0.72f, h * 0.65f)
            lineTo(w, h * 0.30f)
            lineTo(w * 0.85f, h * 0.95f)
            lineTo(w * 0.15f, h * 0.95f)
            close()
        }
        drawPath(path = path, color = tint)
    }
}

// 🎯 ফুলস্ক্রিন এক্সপ্যান্ড ডায়াগনাল অ্যারো আইকন
@Composable
fun DiagonalFullscreenIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    Canvas(modifier = modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.7.dp.toPx()

        // ↖ Top-Left
        drawLine(color, Offset(w * 0.14f, h * 0.44f), Offset(w * 0.14f, h * 0.14f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.14f, h * 0.14f), Offset(w * 0.44f, h * 0.14f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.14f, h * 0.14f), Offset(w * 0.40f, h * 0.40f), stroke, StrokeCap.Round)

        // ↘ Bottom-Right
        drawLine(color, Offset(w * 0.86f, h * 0.56f), Offset(w * 0.86f, h * 0.86f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.86f, h * 0.86f), Offset(w * 0.54f, h * 0.86f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.86f, h * 0.86f), Offset(w * 0.60f, h * 0.60f), stroke, StrokeCap.Round)
    }
}

// 🎯 ডাবল ট্যাপ স্কিপ টেক্সট (+10 › / ‹ -10)
@Composable
fun OttDoubleTapSkipBadge(
    isForward: Boolean,
    skipSeconds: Int = 10,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        if (!isForward) {
            Canvas(modifier = Modifier.size(13.dp, 18.dp)) {
                val w = size.width
                val h = size.height
                val path = Path().apply {
                    moveTo(w * 0.85f, h * 0.15f)
                    lineTo(w * 0.20f, h * 0.50f)
                    lineTo(w * 0.85f, h * 0.85f)
                }
                drawPath(
                    path = path,
                    color = Color.White,
                    style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
        Text(
            text = if (isForward) "+$skipSeconds" else "-$skipSeconds",
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = Color.Black.copy(alpha = 0.85f),
                    blurRadius = 8f
                )
            )
        )
        if (isForward) {
            Canvas(modifier = Modifier.size(13.dp, 18.dp)) {
                val w = size.width
                val h = size.height
                val path = Path().apply {
                    moveTo(w * 0.15f, h * 0.15f)
                    lineTo(w * 0.80f, h * 0.50f)
                    lineTo(w * 0.15f, h * 0.85f)
                }
                drawPath(
                    path = path,
                    color = Color.White,
                    style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}

private suspend fun fetchRealFileSize(url: String): Long = withContext(Dispatchers.IO) {
    if (url.isBlank()) return@withContext 0L
    try {
        val target = if (url.contains(".m3u8")) url.substringBeforeLast("/") + "/download_720p.mp4" else url
        val connection = (URL(target).openConnection() as? HttpURLConnection)?.apply {
            requestMethod = "HEAD"
            connectTimeout = 4000
            readTimeout = 4000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "PlayDramaFlix")
            setRequestProperty("Accept-Encoding", "identity")
        }
        var length = connection?.contentLengthLong ?: 0L
        connection?.disconnect()

        if (length <= 0L && url.contains(".m3u8")) {
            val fallback = url.substringBeforeLast("/") + "/download.mp4"
            val conn2 = (URL(fallback).openConnection() as? HttpURLConnection)?.apply {
                requestMethod = "HEAD"
                connectTimeout = 3000
                readTimeout = 3000
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

private fun formatSize(bytes: Long, isCalculating: Boolean): String {
    if (bytes <= 0L) {
        return if (isCalculating) "Calculating..." else "0 MB"
    }
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) {
        String.format(Locale.US, "%.2f GB", mb / 1024.0)
    } else {
        String.format(Locale.US, "%.1f MB", mb)
    }
}

private fun formatTimeDisplay(millis: Long): String {
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

    // 📺 ভিডিও কোয়ালিটি ট্র্যাক
    var availableVideoTracks by remember { mutableStateOf<List<RealVideoTrack>>(emptyList()) }
    var currentSelectedHeight by rememberSaveable { mutableIntStateOf(0) }
    var currentQualityLabel by rememberSaveable { mutableStateOf("720P") }

    // 🎧 অডিও ট্র্যাক সিলেকশন
    var availableAudioTracks by remember { mutableStateOf<List<RealAudioTrack>>(emptyList()) }
    var selectedAudioTrackId by rememberSaveable { mutableStateOf("default") }
    var currentAudioLabel by rememberSaveable { mutableStateOf("Hindi") }

    // 🔊 সাউন্ড স্টেট
    var isMuted by remember { mutableStateOf(false) }

    // 🎯 সিডিএন বাফার প্রগ্রেস
    var bufferedPositionMs by remember { mutableLongStateOf(0L) }

    // =========================================================================
    // 🏷️ Last Seen 00:00 Jump Play ব্যানার স্টেট
    // =========================================================================
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

    // =========================================================================
    // 🔄 শেষ দেখা এপিসোড অটো-প্লে মেমোরি
    // =========================================================================
    val lastWatchedEpPrefs = remember(slug) {
        context.getSharedPreferences("drama_last_watched_ep_prefs", Context.MODE_PRIVATE)
    }

    LaunchedEffect(slug, episodeNumber) {
        if (slug.isNotBlank() && episodeNumber > 0) {
            lastWatchedEpPrefs.edit().putInt("last_ep_$slug", episodeNumber).apply()
        }
    }

    var hasAutoSelectedLastWatchedEp by rememberSaveable(slug) { mutableStateOf(false) }
    LaunchedEffect(slug, episodes) {
        if (!hasAutoSelectedLastWatchedEp && episodes.isNotEmpty()) {
            hasAutoSelectedLastWatchedEp = true
            val savedEpNum = lastWatchedEpPrefs.getInt("last_ep_$slug", 1)
            if (savedEpNum > 1 && savedEpNum != episodeNumber) {
                val targetEp = episodes.find { it.episodeNumber == savedEpNum }
                if (targetEp != null) {
                    onSelectEpisode(targetEp)
                }
            }
        }
    }

    // =========================================================================
    // 🎯 ১ নম্বর ছবি: ৮ মিনিট পর পর কিউমুলেটিভ ক্লিক-বেইট অ্যাড ইঞ্জিন (+২ ক্লিকের যোগফল)
    // =========================================================================
    val eightMinutesMs = 8 * 60 * 1000L // ৪৮০,০০০ মি.সে.
    var pendingAdClicks by rememberSaveable(slug) { mutableIntStateOf(0) }
    val triggeredAdMarks = rememberSaveable(slug) { mutableSetOf<Int>() }

    LaunchedEffect(currentPositionMs, isVip, totalDurationMs) {
        if (!isVip && totalDurationMs > 0L) {
            val currentMark = (currentPositionMs / eightMinutesMs).toInt()
            if (currentMark > 0 && currentMark !in triggeredAdMarks) {
                triggeredAdMarks.add(currentMark)
                // প্রত্যেকটা টাইমে ২টা করে ক্লিক অ্যাড হবে এবং পেছনের সাথে যোগ হবে
                pendingAdClicks += 2
            }
        }
    }

    val adCueFractions = remember(totalDurationMs, isVip) {
        if (isVip || totalDurationMs <= eightMinutesMs) {
            emptyList()
        } else {
            val count = (totalDurationMs / eightMinutesMs).toInt()
            (1..count).map { (it * eightMinutesMs).toFloat() / totalDurationMs.toFloat() }
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
                val (key, label, isVipTag) = when {
                    qLower.contains("1080") -> Triple("1080p", "1080P", true)
                    qLower.contains("720")  -> Triple("720p", "720P", true)
                    qLower.contains("480")  -> Triple("480p", "480P", false)
                    qLower.contains("360")  -> Triple("360p", "360P", false)
                    else                   -> Triple("single", "HD", false)
                }
                if (seen.add(key)) detected.add(SideDrawerQualityItem(key, label, isVipTag))
            }
        }

        if (detected.isEmpty()) {
            listOf(
                SideDrawerQualityItem("1080p", "1080P", true),
                SideDrawerQualityItem("720p", "720P", true),
                SideDrawerQualityItem("480p", "480P", false),
                SideDrawerQualityItem("360p", "360P", false)
            )
        } else {
            detected.sortedByDescending { it.key }
        }
    }

    var selectedDownloadQualityKey by remember(downloadQualityOptions) {
        mutableStateOf(downloadQualityOptions.firstOrNull { !it.isVipOnly }?.key ?: downloadQualityOptions.first().key)
    }

    val selectedDownloadEpisodes = remember { mutableStateListOf<EpisodeDto>() }
    val realFileSizes = remember { mutableStateMapOf<String, Long>() }
    var isFetchingSizes by remember { mutableStateOf(false) }
    var todayUsedBytes by remember { mutableLongStateOf(DownloadQuotaManager.getTodayUsedBytes(context)) }

    val speedOptions = remember { listOf(4.0f, 3.0f, 2.0f, 1.5f, 1.25f, 1.0f, 0.75f, 0.5f) }
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
    var volumeLevel by remember { mutableFloatStateOf(0.5f) }
    var showVolumeOverlay by remember { mutableStateOf(false) }

    // ওটিটি ড্র্যাগ স্ক্রাব স্টেট
    var isHorizontalScrubbing by remember { mutableStateOf(false) }
    var scrubTargetPositionMs by remember { mutableLongStateOf(0L) }

    var isUserSeeking by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableLongStateOf(0L) }
    var isBuffering by remember { mutableStateOf(exoPlayer.playbackState == Player.STATE_BUFFERING) }

    // ডাবল-ট্যাপ অ্যানিমেশন ট্র্যাকার
    var showLeftDoubleTapAnim by remember { mutableStateOf(false) }
    var showRightDoubleTapAnim by remember { mutableStateOf(false) }

    fun extractTracks(tracks: Tracks) {
        val foundVideoTracks = mutableListOf<RealVideoTrack>()
        val foundAudioTracks = mutableListOf<RealAudioTrack>()
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

                        // 🎯 ৪ নম্বর ছবির মতো একদম ক্লিন ভাষার নাম
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
            }
        }

        // ৩ নম্বর ছবির মতো কোয়ালিটি ট্র্যাক
        val standardResolutions = listOf(
            RealVideoTrack(0, 0, 0, "Auto", isAuto = true),
            RealVideoTrack(720, 1280, 0, "720p"),
            RealVideoTrack(480, 854, 0, "480p"),
            RealVideoTrack(360, 640, 0, "360p")
        )
        availableVideoTracks = if (foundVideoTracks.isNotEmpty()) {
            listOf(RealVideoTrack(0, 0, 0, "Auto", isAuto = true)) + foundVideoTracks.distinctBy { it.label }
        } else standardResolutions

        // ৪ নম্বর ছবির মতো ডিফল্ট তালিকা (যদি স্ট্রিমে মাল্টিপল অডিও ডিক্লেয়ার করা না থাকে)
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
    }

    fun applyExoPlayerQuality(targetResolution: Int, label: String) {
        currentSelectedHeight = targetResolution
        currentQualityLabel = if (targetResolution == 0) "Auto" else "${targetResolution}P"

        if (targetResolution == 0) {
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .build()
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

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                extractTracks(tracks)
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (currentSelectedHeight == 0 && videoSize.height > 0) {
                    val res = minOf(videoSize.width, videoSize.height)
                    currentQualityLabel = "${res}P"
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = (state == Player.STATE_BUFFERING)
                bufferedPositionMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)
            }
            override fun onIsPlayingChanged(playingState: Boolean) {
                if (playingState) isBuffering = false
                bufferedPositionMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)
            }
            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
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
                // =============================================================
                // 🖐️ মাল্টি-জেসচার ডিটেক্টর (OTT Horizontal Drag, Volume & Brightness)
                // =============================================================
                .pointerInput(isScreenLocked, isPiPActive, isDeviceLandscape, showSideDrawer) {
                    if (isScreenLocked || isPiPActive) return@pointerInput

                    awaitEachGesture {
                        val firstDown = awaitFirstDown(requireUnconsumed = false)
                        val isLeft = firstDown.position.x < size.width / 2f
                        val isRightEdge = firstDown.position.x > size.width * 0.72f

                        var isTransforming = false
                        var isVerticalDragging = false
                        var isHorizontalDragging = false
                        var totalDragY = 0f
                        var totalDragX = 0f
                        val touchSlop = viewConfiguration.touchSlop
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
                        val startScrubPos = exoPlayer.currentPosition

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
                                    if (isLeft) showBrightnessOverlay = true else showVolumeOverlay = true
                                }

                                if (!isVerticalDragging && !isHorizontalDragging && abs(totalDragX) > touchSlop && abs(totalDragX) > abs(totalDragY)) {
                                    if (!(isDeviceLandscape && isRightEdge && totalDragX < 0)) {
                                        isHorizontalDragging = true
                                        isHorizontalScrubbing = true
                                    }
                                }

                                if (isVerticalDragging) {
                                    val delta = -dragY / (size.height * 0.50f)
                                    if (isLeft) {
                                        brightnessLevel = (brightnessLevel + delta).coerceIn(0.01f, 1.0f)
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
                // =============================================================
                // 🎯 ট্যাপ হ্যান্ডলার (২ নম্বর ছবির সিঙ্গেল-ট্যাপ ফিক্স সহ)
                // =============================================================
                .pointerInput(isScreenLocked, showSideDrawer, isPiPActive, isUserSeeking, pendingAdClicks) {
                    if (isScreenLocked || isPiPActive) return@pointerInput

                    detectTapGestures(
                        onPress = {
                            var holdJob: Job? = null
                            try {
                                holdJob = coroutineScope.launch {
                                    delay(400)
                                    if (!isUserSeeking && !isHorizontalScrubbing) {
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
                            // 🎯 ১ নম্বর ছবি: যদি পেন্ডিং অ্যাড ক্লিক থাকে, তবে পুরো স্ক্রিন ক্লিক-বেইট হিসেবে অ্যাড ওপেন করবে
                            if (pendingAdClicks > 0 && !isVip) {
                                pendingAdClicks--
                                UnifiedAdManager.openAdsterraDirectLink(context, isVip = false)
                                return@detectTapGestures
                            }

                            // 🎯 ২ নম্বর ছবি: ফুলস্ক্রিনে For You বা অন্য কোনো ড্রয়ার থাকলে সিঙ্গেল ট্যাপে সাথে সাথে বন্ধ হবে
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

            // ২X স্পিড ইন্ডিকেটর
            if (is2xActive) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "2X Speed",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = Color.Black,
                                blurRadius = 6f
                            )
                        )
                    )
                }
            }

            // স্ক্রিন টেনে স্কিপ করার ডিজাইন
            if (isHorizontalScrubbing) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = formatTimeDisplay(scrubTargetPositionMs),
                            color = Color(0xFF00E5FF),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            style = TextStyle(
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = Color.Black,
                                    blurRadius = 8f
                                )
                            )
                        )
                        Text(
                            text = "/",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Normal,
                            style = TextStyle(
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = Color.Black,
                                    blurRadius = 8f
                                )
                            )
                        )
                        Text(
                            text = formatTimeDisplay(totalDurationMs),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            style = TextStyle(
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = Color.Black,
                                    blurRadius = 8f
                                )
                            )
                        )
                    }

                    val scrubFraction = if (totalDurationMs > 0) (scrubTargetPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f) else 0f
                    Box(
                        modifier = Modifier
                            .width(135.dp)
                            .height(2.8.dp)
                            .clip(RoundedCornerShape(1.4.dp))
                            .background(Color.White.copy(alpha = 0.35f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(scrubFraction)
                                .background(Color(0xFF00E5FF))
                        )
                    }
                }
            }

            // ডাবল-ট্যাপ অ্যানিমেশন (+10 › / ‹ -10)
            val screenWidthDp = configuration.screenWidthDp.dp
            val doubleTapOffset = remember(screenWidthDp) { screenWidthDp * 0.26f }

            androidx.compose.animation.AnimatedVisibility(
                visible = showLeftDoubleTapAnim,
                enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.8f),
                exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.8f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = -doubleTapOffset)
            ) {
                OttDoubleTapSkipBadge(isForward = false, skipSeconds = 10)
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showRightDoubleTapAnim,
                enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.8f),
                exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.8f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = doubleTapOffset)
            ) {
                OttDoubleTapSkipBadge(isForward = true, skipSeconds = 10)
            }

            // =============================================================
            // 🎯 ১ নম্বর ছবি: স্পনসর ব্যানার ও কিউমুলেটিভ পেন্ডিং ক্লিক কাউন্টার
            // =============================================================
            if (pendingAdClicks > 0 && !isVip) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.78f),
                    border = BorderStroke(1.2.dp, Color(0xFFFFCC00)),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = if (isDeviceLandscape) 16.dp else 46.dp, end = 14.dp)
                        .clickable {
                            pendingAdClicks--
                            UnifiedAdManager.openAdsterraDirectLink(context, isVip = false)
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFCC00))
                        )
                        Text(
                            text = "Sponsored • Tap to Visit (${pendingAdClicks} left)",
                            color = Color(0xFFFFCC00),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (isBuffering && !isPiPActive) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF00E5FF), strokeWidth = 3.dp, modifier = Modifier.size(42.dp))
                }
            }

            if (showBrightnessOverlay && !isPiPActive && !showSideDrawer) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WbSunny,
                        contentDescription = "Brightness",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Box(
                        modifier = Modifier
                            .width(85.dp)
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(Color.White.copy(alpha = 0.35f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(brightnessLevel.coerceIn(0f, 1f))
                                .background(Color(0xFF00E5FF))
                        )
                    }
                }
            }

            if (showVolumeOverlay && !isPiPActive && !showSideDrawer) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 26.dp)
                ) {
                    Icon(
                        imageVector = if (volumeLevel <= 0f) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Volume",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Box(
                        modifier = Modifier
                            .width(85.dp)
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(Color.White.copy(alpha = 0.35f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(volumeLevel.coerceIn(0f, 1f))
                                .background(Color(0xFF00E5FF))
                        )
                    }
                }
            }

            if (isPiPActive) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f))) {
                    IconButton(
                        onClick = { activity?.finish() },
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                    }

                    IconButton(
                        onClick = onPlayPauseClick,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.50f))
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
            } else {
                // ওপরের বার
                androidx.compose.animation.AnimatedVisibility(
                    visible = isControlsVisible && !isScreenLocked,
                    enter = slideInVertically(initialOffsetY = { -it }, animationSpec = tween(240)) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(240)) + fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)))
                            .then(if (isDeviceLandscape) Modifier.statusBarsPadding() else Modifier)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = onBackClick, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        if (isDeviceLandscape) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                IconButton(onClick = { enterPiPMode() }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.PictureInPictureAlt, contentDescription = "PiP", tint = Color.White, modifier = Modifier.size(19.dp))
                                }
                                IconButton(onClick = { openCastSettings() }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Cast, contentDescription = "Cast to TV", tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                        } else {
                            IconButton(onClick = onShareClick, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                // সেন্ট্রাল প্লে/পজ বাটন
                androidx.compose.animation.AnimatedVisibility(
                    visible = isControlsVisible && !isScreenLocked,
                    enter = fadeIn(animationSpec = tween(200)) + scaleIn(initialScale = 0.85f),
                    exit = fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 0.85f),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    IconButton(
                        onClick = onPlayPauseClick,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.50f))
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                // 🔓 লক বাটন (কোনো ব্যাকগ্রাউন্ড ছাড়া)
                if (isDeviceLandscape) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isControlsVisible && !isScreenLocked,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        IconButton(
                            onClick = {
                                isScreenLocked = true
                                isControlsVisible = false
                            },
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LockOpen,
                                contentDescription = "Lock",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // =========================================================================
                // 🔝 বটম কন্ট্রোল বার
                // =========================================================================
                androidx.compose.animation.AnimatedVisibility(
                    visible = isControlsVisible && !isScreenLocked,
                    enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(200)) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(200)) + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.65f),
                                        Color.Black.copy(alpha = 0.95f)
                                    )
                                )
                            )
                            .then(if (isDeviceLandscape) Modifier.navigationBarsPadding() else Modifier)
                            .padding(horizontal = 6.dp, vertical = 0.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        // Last Seen 00:00 Jump Play ব্যানার
                        if (showLastSeenBanner && lastSeenPosMs > 5000L) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xF0131722),
                                border = BorderStroke(0.8.dp, Color(0xFF263248)),
                                modifier = Modifier
                                    .padding(start = 6.dp, bottom = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { showLastSeenBanner = false }
                                    )

                                    Text(
                                        text = "Last Seen ${formatTimeDisplay(lastSeenPosMs)}",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )

                                    Text(
                                        text = "Jump Play",
                                        color = Color(0xFF00E5FF),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clickable {
                                                exoPlayer.seekTo(lastSeenPosMs)
                                                onSeekFinished(lastSeenPosMs)
                                                showLastSeenBanner = false
                                            }
                                    )
                                }
                            }
                        }

                        // আল্ট্রা-স্লিম টাইমলাইন
                        SleekOnlineTimeline(
                            currentPositionMs = if (isUserSeeking) scrubPosition else currentPositionMs,
                            totalDurationMs = totalDurationMs,
                            bufferedPositionMs = bufferedPositionMs,
                            adCueFractions = adCueFractions,
                            onSeekStarted = { isUserSeeking = true },
                            onSeeking = { scrubPosition = it },
                            onSeekFinished = { targetPos ->
                                onSeekFinished(targetPos)
                                isUserSeeking = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                        )

                        // বটম কন্ট্রোলস রো
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 1.dp, bottom = 3.dp, start = 4.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // 👈 বাঁ পাশের গ্রুপ: [▶ / ⏸] [🔊 / 🔇] 00:07 / 40:13
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .clickable { onPlayPauseClick() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = Color.White,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            isMuted = !isMuted
                                            exoPlayer.volume = if (isMuted) 0f else 1f
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "Mute/Unmute",
                                        tint = Color.White,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.5.dp)
                                ) {
                                    Text(
                                        text = formatTimeDisplay(if (isUserSeeking) scrubPosition else currentPositionMs),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "/",
                                        color = Color.White.copy(alpha = 0.50f),
                                        fontSize = 10.5.sp
                                    )
                                    Text(
                                        text = formatTimeDisplay(totalDurationMs),
                                        color = Color.White.copy(alpha = 0.70f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Normal
                                    )
                                }
                            }

                            // 👉 ডান পাশের গ্রুপ
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                if (isDeviceLandscape) {
                                    IconButton(
                                        onClick = onNextEpisode,
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SkipNext,
                                            contentDescription = "Next Episode",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                // ⏱️ স্পিড
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable {
                                            sideDrawerType = "speed"
                                            showSideDrawer = true
                                        }
                                        .padding(horizontal = 3.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Outlined.Speed, contentDescription = "Speed", tint = Color.White, modifier = Modifier.size(13.dp))
                                    Text(text = if (currentSpeed == 1.0f) "1x" else "${currentSpeed}x", color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                }

                                // 🎯 কোয়ালিটি
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable {
                                            sideDrawerType = "quality"
                                            showSideDrawer = true
                                        }
                                        .padding(horizontal = 3.dp, vertical = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .border(width = 0.8.dp, color = Color.White, shape = RoundedCornerShape(2.dp))
                                            .padding(horizontal = 2.dp, vertical = 0.5.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("HD", color = Color.White, fontSize = 6.5.sp, fontWeight = FontWeight.Black, lineHeight = 7.sp)
                                    }
                                    Text(text = currentQualityLabel, color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                }

                                // 🎧 অডিও
                                if (availableAudioTracks.size > 1) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable {
                                                sideDrawerType = "audio"
                                                showSideDrawer = true
                                            }
                                            .padding(horizontal = 3.dp, vertical = 2.dp)
                                    ) {
                                        Text(text = "♪", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text(text = currentAudioLabel, color = Color(0xFF00E5FF), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (isDeviceLandscape) {
                                    IconButton(
                                        onClick = {
                                            sideDrawerType = "playlist"
                                            showSideDrawer = !showSideDrawer
                                        },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Layers,
                                            contentDescription = "Episodes",
                                            tint = if (showSideDrawer && sideDrawerType == "playlist") Color(0xFF00E5FF) else Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                // ⬇ ডাউনলোড
                                IconButton(
                                    onClick = {
                                        if (isDeviceLandscape) {
                                            sideDrawerType = "download"
                                            showSideDrawer = !showSideDrawer
                                        } else {
                                            onDownloadClick?.invoke()
                                        }
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.FileDownload,
                                        contentDescription = "Download",
                                        tint = if (isDeviceLandscape && showSideDrawer && sideDrawerType == "download") Color(0xFF00E5FF) else Color.White,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }

                                // ⤢ ফুলস্ক্রিন আইকন
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .clickable { onToggleFullscreen() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    DiagonalFullscreenIcon(color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            // সাইড ড্রয়ার স্ক্রিম
            if (showSideDrawer && !isPiPActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            showSideDrawer = false
                            isControlsVisible = false
                        }
                )
            }

            // =========================================================================
            // 📑 ৩ ও ৪ নম্বর ছবির হুবহু "QUALITY" ও "AUDIO LANGUAGE" সাইড ড্রয়ার
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
                        .fillMaxWidth(
                            if (isDeviceLandscape) 0.22f else 0.46f // 👈 অতিরিক্ত কম জায়গা নিয়ে মিনিমালিস্ট ড্রয়ার
                        )
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.80f),
                                    Color.Black.copy(alpha = 0.96f)
                                )
                            )
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {}
                ) {
                    if (sideDrawerType == "speed") {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 18.dp, horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            item {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "SPEED",
                                        color = Color(0xFF00E5FF),
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Box(modifier = Modifier.width(36.dp).height(2.dp).background(Color(0xFF00E5FF)))
                                }
                            }

                            items(speedOptions) { spd ->
                                val isSelected = currentSpeed == spd
                                val label = if (spd == 1.0f) "1.0x" else "${spd}x"

                                Text(
                                    text = label,
                                    color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier
                                        .clickable {
                                            currentSpeed = spd
                                            exoPlayer.setPlaybackSpeed(spd)
                                            showSideDrawer = false
                                        }
                                        .padding(vertical = 3.dp)
                                )
                            }
                        }
                    } else if (sideDrawerType == "quality") {
                        // =============================================================
                        // 🎯 ৩ নম্বর ছবির হুবহু QUALITY ড্রয়ার ডিজাইন
                        // =============================================================
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 18.dp, horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            item {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "QUALITY",
                                        color = Color(0xFF00E5FF),
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Box(modifier = Modifier.width(42.dp).height(2.dp).background(Color(0xFF00E5FF)))
                                }
                            }

                            items(availableVideoTracks) { track ->
                                val isSelected = if (track.isAuto) currentSelectedHeight == 0 else currentSelectedHeight == track.height

                                Text(
                                    text = track.label,
                                    color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier
                                        .clickable {
                                            applyExoPlayerQuality(track.height, if (track.isAuto) "Auto" else "${track.height}P")
                                            showSideDrawer = false
                                        }
                                        .padding(vertical = 2.dp)
                                )
                            }
                        }
                    } else if (sideDrawerType == "audio") {
                        // =============================================================
                        // 🎯 ৪ নম্বর ছবির হুবহু AUDIO LANGUAGE ড্রয়ার ডিজাইন
                        // =============================================================
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 18.dp, horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            item {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "AUDIO LANGUAGE",
                                        color = Color(0xFF00E5FF),
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Box(modifier = Modifier.width(62.dp).height(2.dp).background(Color(0xFF00E5FF)))
                                }
                            }

                            items(availableAudioTracks) { audioTrack ->
                                val isSelected = (selectedAudioTrackId == audioTrack.id)

                                Text(
                                    text = audioTrack.label,
                                    color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier
                                        .clickable {
                                            applyExoPlayerAudioTrack(audioTrack)
                                            showSideDrawer = false
                                        }
                                        .padding(vertical = 2.dp)
                                )
                            }
                        }
                    } else {
                        // এপিসোড ও ডাউনলোড ড্রয়ার
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (sideDrawerType == "download") "Download (${selectedDownloadEpisodes.size})" else "Episodes (${episodes.size})",
                                    color = Color.White,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(onClick = { showSideDrawer = false }, modifier = Modifier.size(22.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF8E95A5), modifier = Modifier.size(15.dp))
                                }
                            }

                            if (sideDrawerType == "download") {
                                Spacer(modifier = Modifier.height(3.dp))
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    items(downloadQualityOptions) { qItem ->
                                        val isSelected = selectedDownloadQualityKey == qItem.key
                                        Surface(
                                            shape = RoundedCornerShape(5.dp),
                                            color = if (isSelected) Color(0xFF382B17) else Color(0xFF1E2433).copy(alpha = 0.8f),
                                            border = BorderStroke(
                                                width = 1.dp,
                                                color = if (isSelected) Color(0xFFE5B567) else Color(0xFF2D3545)
                                            ),
                                            modifier = Modifier.clickable {
                                                selectedDownloadQualityKey = qItem.key
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                if (qItem.isVipOnly) {
                                                    MiniCrownVector(tint = if (isSelected) Color(0xFFF6D38B) else Color(0xFFC49A52))
                                                }
                                                Text(
                                                    text = qItem.label,
                                                    color = if (isSelected) Color(0xFFF6D38B) else Color(0xFF94A3B8),
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(5.dp))

                            val displayEps = episodes.ifEmpty {
                                (1..30).map { EpisodeDto(episodeNumber = it, isLocked = it > 1) }
                            }

                            LazyVerticalGrid(
                                columns = GridCells.Fixed(4),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                items(displayEps, key = { it.episodeId }) { ep ->
                                    val isSelected = ep.episodeNumber == episodeNumber
                                    val isEpLocked = shouldLockEpisodes && ep.isLocked
                                    val isSelectedForDl = selectedDownloadEpisodes.contains(ep)

                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(1.1f)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                if (sideDrawerType == "download") {
                                                    if (isSelectedForDl) Color(0xFF0F3B32) else Color(0xFF1E2433).copy(alpha = 0.70f)
                                                } else {
                                                    if (isSelected) Color(0xFF0F3B32) else Color(0xFF1E2433).copy(alpha = 0.70f)
                                                }
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if ((sideDrawerType == "download" && isSelectedForDl) || (sideDrawerType == "playlist" && isSelected)) Color(0xFF00E676) else Color.Transparent,
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .clickable {
                                                if (sideDrawerType == "download") {
                                                    if (isSelectedForDl) selectedDownloadEpisodes.remove(ep)
                                                    else selectedDownloadEpisodes.add(ep)
                                                } else {
                                                    onSelectEpisode(ep)
                                                    showSideDrawer = false
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            if (sideDrawerType == "playlist" && isSelected) {
                                                EqualizerBarsIcon(modifier = Modifier.size(9.dp, 6.dp), tint = Color(0xFF00E676))
                                            } else {
                                                Text(
                                                    text = ep.episodeNumber.toString(),
                                                    color = if ((sideDrawerType == "download" && isSelectedForDl) || (sideDrawerType == "playlist" && isSelected)) Color(0xFF00E676) else Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            if (isEpLocked && !isSelected && sideDrawerType == "playlist") {
                                                Text(text = "VIP", color = GoldVip, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            if (sideDrawerType == "download") {
                                Spacer(modifier = Modifier.height(5.dp))
                                val displaySize = formatSize(totalSelectedBytes, isFetchingSizes && totalSelectedBytes == 0L)
                                val selectedQualityObj = downloadQualityOptions.firstOrNull { it.key == selectedDownloadQualityKey }
                                val isVipLocked = (selectedQualityObj?.isVipOnly == true) && !isVip

                                Button(
                                    onClick = {
                                        if (isVipLocked) {
                                            Toast.makeText(context, "${selectedQualityObj?.label} is exclusive for VIP members! Upgrade now.", Toast.LENGTH_SHORT).show()
                                            onNavigateToVip()
                                            return@Button
                                        }

                                        if (selectedDownloadEpisodes.isNotEmpty()) {
                                            val checkResult = DownloadQuotaManager.checkCanDownload(
                                                context = context,
                                                bytesToDownload = totalSelectedBytes,
                                                isVip = isVip
                                            )

                                            if (checkResult.canDownload) {
                                                if (!isVip) {
                                                    DownloadQuotaManager.recordDownloadUsage(context, totalSelectedBytes)
                                                    todayUsedBytes = DownloadQuotaManager.getTodayUsedBytes(context)
                                                }
                                                selectedDownloadEpisodes.forEach { ep ->
                                                    val matchingOpt = ep.downloadOptions?.firstOrNull {
                                                        it.quality.contains(selectedDownloadQualityKey, true) || it.url.contains(selectedDownloadQualityKey, true)
                                                    }
                                                    val targetUrl = matchingOpt?.url?.takeIf { it.isNotBlank() } ?: ep.resolveDownloadUrl(slug)

                                                    R2DownloadManager.startDownload(
                                                        context = context,
                                                        downloadUrl = targetUrl,
                                                        title = "$title (${selectedQualityObj?.label ?: "HD"})",
                                                        episodeNumber = ep.episodeNumber,
                                                        isMovie = false
                                                    )
                                                }
                                                Toast.makeText(context, "Downloading ${selectedDownloadEpisodes.size} episodes ($displaySize)", Toast.LENGTH_SHORT).show()
                                                showSideDrawer = false
                                            } else {
                                                Toast.makeText(context, checkResult.message, Toast.LENGTH_LONG).show()
                                                onNavigateToVip()
                                            }
                                        }
                                    },
                                    enabled = selectedDownloadEpisodes.isNotEmpty() || isVipLocked,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isVipLocked) Color(0xFFE5B567) else Color(0xFF00D26A)
                                    ),
                                    shape = RoundedCornerShape(7.dp),
                                    modifier = Modifier.fillMaxWidth().height(34.dp)
                                ) {
                                    Text(
                                        text = if (isVipLocked) "VIP 👑" else "Download · $displaySize",
                                        color = if (isVipLocked) Color(0xFF2B210E) else Color.Black,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 🔓 আনলক বাটন
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

        // For You সাইড প্যানেল
        androidx.compose.animation.AnimatedVisibility(
            visible = isForYouDocked,
            enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(),
            exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(260.dp)
                    .pointerInput(Unit) {
                        detectTapGestures {}
                    },
                color = Color(0xFF10141E)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "For You",
                            color = Color.White,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showSideDrawer = false }, modifier = Modifier.size(22.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF8E95A5), modifier = Modifier.size(15.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val displayRecs = recommendations.filter { it.slug != slug }

                    if (displayRecs.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("Loading series...", color = Color(0xFF8E95A5), fontSize = 12.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(displayRecs) { rec ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            onRelatedDramaClick(rec.slug)
                                            showSideDrawer = false
                                        }
                                        .padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(105.dp)
                                            .aspectRatio(16f / 9f)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(Color(0xFF1A1F2C))
                                    ) {
                                        val bannerImg = rec.bannerUrl?.takeIf { it.isNotBlank() } ?: rec.posterUrl
                                        AsyncImage(
                                            model = bannerImg,
                                            contentDescription = rec.title,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }

                                    Text(
                                        text = rec.title,
                                        color = Color.White,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
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
