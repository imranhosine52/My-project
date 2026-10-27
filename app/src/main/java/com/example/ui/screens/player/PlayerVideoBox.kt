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
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.text.style.TextAlign
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
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
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

data class RealSubtitleTrack(
    val id: String,
    val language: String,
    val label: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val isSelected: Boolean = false
)

// -----------------------------------------------------------------------------
// 🌟 PD Flix প্রিমিয়াম ব্র্যান্ড লোগো (শুধুমাত্র বাঁকা সুইশ লাইন সহ)
// -----------------------------------------------------------------------------
@Composable
fun PdFlixBrandLogo(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // 'P' -> Electric Cyan-Blue Gradient
            Text(
                text = "P",
                style = TextStyle(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF00F0FF), Color(0xFF0072FF))
                    ),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black
                )
            )

            // 'D' -> Hot Pink to Magenta Gradient
            Text(
                text = "D",
                style = TextStyle(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFFF007A), Color(0xFFC000FF))
                    ),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black
                )
            )

            Spacer(modifier = Modifier.width(3.dp))

            // 'Flix' -> Glossy White with Speed Slash Bars
            Box(contentAlignment = Alignment.TopCenter) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .offset(x = 1.dp, y = (-4).dp)
                        .align(Alignment.TopCenter)
                ) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(5.dp)
                                .rotate(20f)
                                .background(Color(0xFF00D2FF), RoundedCornerShape(1.dp))
                        )
                    }
                }

                Text(
                    text = "Flix",
                    style = TextStyle(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFFFFFFFF), Color(0xFFD6E4FF), Color(0xFF9CB8FF))
                        ),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black
                    )
                )
            }
        }

        // নিচের বাঁকা স্পিড সুইশ লাইন (Swoosh Arc)
        Canvas(
            modifier = Modifier
                .width(175.dp)
                .height(14.dp)
                .offset(y = (-4).dp)
        ) {
            val w = size.width
            val h = size.height

            val swooshBrush = Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFF00E5FF),
                    Color(0xFF0072FF),
                    Color(0xFFFF007A),
                    Color(0xFFFF007A).copy(alpha = 0f)
                )
            )

            val path = Path().apply {
                moveTo(0f, h * 0.45f)
                quadraticBezierTo(w * 0.45f, h * 1.15f, w * 0.96f, 0f)
                quadraticBezierTo(w * 0.45f, h * 0.85f, 0f, h * 0.45f)
                close()
            }
            drawPath(path = path, brush = swooshBrush)
        }
    }
}

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

@Composable
fun MusicNoteDubbingIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF00E5FF)
) {
    Canvas(modifier = modifier.size(13.dp)) {
        val w = size.width
        val h = size.height

        val headRadius = w * 0.22f
        val headCenter = Offset(w * 0.30f, h * 0.72f)
        drawCircle(color = tint, radius = headRadius, center = headCenter)

        val stemStroke = 1.6.dp.toPx()
        val stemX = headCenter.x + headRadius * 0.75f
        drawLine(
            color = tint,
            start = Offset(stemX, headCenter.y),
            end = Offset(stemX, h * 0.16f),
            strokeWidth = stemStroke,
            cap = StrokeCap.Round
        )

        val flagPath = Path().apply {
            moveTo(stemX, h * 0.16f)
            cubicTo(
                w * 0.72f, h * 0.16f,
                w * 0.82f, h * 0.30f,
                w * 0.72f, h * 0.46f
            )
            lineTo(stemX, h * 0.38f)
            close()
        }
        drawPath(flagPath, color = tint)
    }
}

@Composable
fun DiagonalFullscreenIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    Canvas(modifier = modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.7.dp.toPx()

        drawLine(color, Offset(w * 0.14f, h * 0.44f), Offset(w * 0.14f, h * 0.14f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.14f, h * 0.14f), Offset(w * 0.44f, h * 0.14f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.14f, h * 0.14f), Offset(w * 0.40f, h * 0.40f), stroke, StrokeCap.Round)

        drawLine(color, Offset(w * 0.86f, h * 0.56f), Offset(w * 0.86f, h * 0.86f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.86f, h * 0.86f), Offset(w * 0.54f, h * 0.86f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.86f, h * 0.86f), Offset(w * 0.60f, h * 0.60f), stroke, StrokeCap.Round)
    }
}

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
            connectTimeout = 3000
            readTimeout = 3000
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
    onOpenServerSelector: (() -> Unit)? = null,
    comments: List<Any> = emptyList(),
    recommendations: List<ContentItemDto> = emptyList(),
    onRelatedDramaClick: (String) -> Unit = {},
    posterUrl: String? = null,
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

    var availableVideoTracks by remember { mutableStateOf<List<RealVideoTrack>>(emptyList()) }
    var currentSelectedHeight by rememberSaveable { mutableIntStateOf(0) }
    var currentQualityLabel by rememberSaveable { mutableStateOf("720P") }

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

    // 🛡️ সুরক্ষিত এরর স্টেট (কোনো গোপন লিংক প্রকাশ পাবে না)
    var activePlaybackErrorType by remember { mutableStateOf<String?>(null) }

    // =========================================================================
    // ⚡ ১. রিয়েল-টাইম লাইভ বাফার ও সর্বোচ্চ গতিতে ২MB প্রোগ্রেস ইঞ্জিন
    // =========================================================================
    val targetStartupBufferMs = 1800L // ১৮০০ মিলিসেকেন্ড ভিডিও ডাটা = প্রায় ২ মেগাবাইট
    var livePercentage by remember(episodeNumber, slug) { mutableIntStateOf(0) }
    var liveSpeedText by remember(episodeNumber, slug) { mutableStateOf("0.00MB/s") }
    var measuredBitrateBps by remember(episodeNumber, slug) { mutableLongStateOf(0L) }

    val isVideoActuallyPlaying = (isPlaying && currentPositionMs > 250L) || (exoPlayer.isPlaying && currentPositionMs > 100L)
    val isPlaybackReady = exoPlayer.playbackState == Player.STATE_READY

    LaunchedEffect(episodeNumber, slug) {
        livePercentage = 0
        liveSpeedText = "0.00MB/s"
        measuredBitrateBps = 0L
        isFirstFrameRendered = false
        isBuffering = true
        activePlaybackErrorType = null
    }

    // প্রতি ৫০ মিলিসেকেন্ডে স্মুথলি ০% থেকে ১০০% বৃদ্ধি পাবে (কোনো ফ্রিজ বা ল্যাগ ছাড়াই)
    LaunchedEffect(isFirstFrameRendered, isVideoActuallyPlaying, isPlaybackReady, episodeNumber, slug) {
        while (!isFirstFrameRendered && !isVideoActuallyPlaying && !isPlaybackReady && activePlaybackErrorType == null) {
            val bufferedMs = (exoPlayer.bufferedPosition - exoPlayer.currentPosition).coerceAtLeast(0L)
            val currentBufferedPct = ((bufferedMs.toFloat() / targetStartupBufferMs.toFloat()) * 100).toInt()

            if (currentBufferedPct > livePercentage) {
                livePercentage = currentBufferedPct.coerceIn(0, 99)
            } else if (livePercentage < 98) {
                livePercentage = (livePercentage + 1).coerceAtMost(98)
            }

            // ২ এমবি বা ১৮০০ms ডাটা বাফার হলেই ইনস্ট্যান্ট ১০০% এবং সরাসরি প্লে
            if (bufferedMs >= targetStartupBufferMs || exoPlayer.playbackState == Player.STATE_READY) {
                livePercentage = 100
                if (!exoPlayer.isPlaying) {
                    exoPlayer.play()
                }
                isFirstFrameRendered = true
                isBuffering = false
                break
            }

            delay(50L)
        }

        if (isVideoActuallyPlaying || isPlaybackReady) {
            livePercentage = 100
            isFirstFrameRendered = true
            isBuffering = false
        }
    }

    val shouldShowLoadingScreen = !isVideoActuallyPlaying && !isFirstFrameRendered &&
            (exoPlayer.playbackState == Player.STATE_BUFFERING || exoPlayer.playbackState == Player.STATE_IDLE) &&
            activePlaybackErrorType == null

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
    var volumeLevel by remember {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
        mutableFloatStateOf(if (maxVol > 0) curVol / maxVol else 0.5f)
    }
    var showVolumeOverlay by remember { mutableStateOf(false) }

    var isHorizontalScrubbing by remember { mutableStateOf(false) }
    var scrubTargetPositionMs by remember { mutableLongStateOf(0L) }

    var isUserSeeking by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableLongStateOf(0L) }

    var showLeftDoubleTapAnim by remember { mutableStateOf(false) }
    var showRightDoubleTapAnim by remember { mutableStateOf(false) }

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

    val eightMinutesMs = 8 * 60 * 1000L
    var pendingAdClicks by rememberSaveable(slug) { mutableIntStateOf(0) }
    val triggeredAdMarks = rememberSaveable(slug) { mutableSetOf<Int>() }

    val isServerAdActive = remember(isVip) {
        UnifiedAdManager.isDirectLinkAvailable(isVip)
    }

    LaunchedEffect(currentPositionMs, isVip, totalDurationMs, isServerAdActive) {
        if (!isVip && isServerAdActive && totalDurationMs > 0L) {
            val currentMark = (currentPositionMs / eightMinutesMs).toInt()
            if (currentMark > 0 && currentMark !in triggeredAdMarks) {
                triggeredAdMarks.add(currentMark)
                pendingAdClicks += 2
            }
        }
    }

    val adCueFractions = remember(totalDurationMs, isVip, isServerAdActive) {
        if (isVip || !isServerAdActive || totalDurationMs <= eightMinutesMs) {
            emptyList()
        } else {
            val count = (totalDurationMs / eightMinutesMs).toInt()
            (1..count).map { (it * eightMinutesMs).toFloat() / totalDurationMs.toFloat() }
        }
    }

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

        val standardResolutions = listOf(
            RealVideoTrack(0, 0, 0, "Auto", isAuto = true),
            RealVideoTrack(720, 1280, 0, "720p"),
            RealVideoTrack(480, 854, 0, "480p"),
            RealVideoTrack(360, 640, 0, "360p")
        )
        availableVideoTracks = if (foundVideoTracks.isNotEmpty()) {
            listOf(RealVideoTrack(0, 0, 0, "Auto", isAuto = true)) + foundVideoTracks.distinctBy { it.label }
        } else standardResolutions

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

    // =========================================================================
    // 🎧 প্লেয়ার ও নেটওয়ার্ক লিসেনার (কোনো সোর্স লিংক ফাঁস ছাড়াই এরর ক্যাচ)
    // =========================================================================
    DisposableEffect(exoPlayer) {
        val playerListener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                extractTracks(tracks)
            }

            override fun onRenderedFirstFrame() {
                isFirstFrameRendered = true
                isBuffering = false
                activePlaybackErrorType = null
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
                    activePlaybackErrorType = null
                }
                bufferedPositionMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)
            }

            override fun onIsPlayingChanged(playingState: Boolean) {
                if (playingState) {
                    isFirstFrameRendered = true
                    isBuffering = false
                    activePlaybackErrorType = null
                }
                bufferedPositionMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                val causeMsg = error.cause?.message?.lowercase() ?: error.message?.lowercase() ?: ""
                activePlaybackErrorType = when {
                    causeMsg.contains("unknownhost") || causeMsg.contains("name_not_resolved") || causeMsg.contains("dns") -> {
                        "Network DNS Error (সার্ভার ডোমেইন পাওয়া যায়নি)"
                    }
                    causeMsg.contains("404") || causeMsg.contains("not found") -> {
                        "Video Not Found (ভিডিও ফাইলটি সার্ভারে নেই)"
                    }
                    causeMsg.contains("timeout") || causeMsg.contains("connect") -> {
                        "Connection Timeout (সার্ভার কানেকশন সময়সীমা উত্তীর্ণ)"
                    }
                    causeMsg.contains("ssl") || causeMsg.contains("cert") -> {
                        "SSL Certificate Error (সুরক্ষা সার্টিফিকেট সমস্যা)"
                    }
                    else -> {
                        "Playback Error (কোড: ${error.errorCodeName})"
                    }
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
                measuredBitrateBps = bitrateEstimate
                if (bitrateEstimate > 0L) {
                    val speedMBs = (bitrateEstimate / 8f) / (1024f * 1024f)
                    liveSpeedText = String.format(Locale.US, "%.2fMB/s", speedMBs)
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
        if (seconds > 0 && pendingAdClicks > 0 && !isVip && UnifiedAdManager.isDirectLinkAvailable(isVip)) {
            pendingAdClicks--
            UnifiedAdManager.openAdsterraDirectLink(context, isVip = false, fallbackUrl = null)
            Toast.makeText(context, "⚠️ Please clear sponsor ad to skip forward!", Toast.LENGTH_SHORT).show()
            return
        }

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
                    // 🎯 ড্রয়ার বা এপিসোড শিট ওপেন থাকলে ব্যাকগ্রাউন্ডে ভলিউম জেসচার ১০০% নিষ্ক্রিয়
                    if (isScreenLocked || isPiPActive || showSideDrawer) return@pointerInput

                    awaitEachGesture {
                        val firstDown = awaitFirstDown(requireUnconsumed = false)
                        
                        // 🎯 নির্দেশনা অনুযায়ী: বাম পাশে ভলিউম এবং ডান পাশে ব্রাইটনেস
                        val isLeftSide = firstDown.position.x < (size.width * 0.45f)
                        val isRightSide = firstDown.position.x > (size.width * 0.55f)

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
                                    if (isLeftSide) showVolumeOverlay = true
                                    else if (isRightSide) showBrightnessOverlay = true
                                }

                                if (!isVerticalDragging && !isHorizontalDragging && abs(totalDragX) > touchSlop && abs(totalDragX) > abs(totalDragY)) {
                                    isHorizontalDragging = true
                                    isHorizontalScrubbing = true
                                }

                                if (isVerticalDragging) {
                                    val delta = -dragY / (size.height * 0.50f)
                                    
                                    // 🔊 ১. বাম পাশে স্মুথ ভলিউম কন্ট্রোল
                                    if (isLeftSide) {
                                        volumeLevel = (volumeLevel + delta).coerceIn(0f, 1f)
                                        val newVolumeInt = (volumeLevel * maxVol).toInt()
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolumeInt, 0)
                                    } 
                                    // ☀️ ২. ডান পাশে স্মুথ ব্রাইটনেস কন্ট্রোল
                                    else if (isRightSide) {
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
                .pointerInput(isScreenLocked, showSideDrawer, isPiPActive, isUserSeeking) {
                    if (isScreenLocked || isPiPActive || showSideDrawer) return@pointerInput

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
                            isControlsVisible = !isControlsVisible
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

            // =========================================================================
            // 🌟 লোডিং স্ক্রিন (মাঝের সোজা দাগ নেই + চারদিকের অ্যাম্বিয়েন্ট গ্লো)
            // =========================================================================
            androidx.compose.animation.AnimatedVisibility(
                visible = shouldShowLoadingScreen,
                enter = fadeIn(tween(100)),
                exit = fadeOut(tween(200)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF06080E))
                        .drawBehind {
                            val centerOffset = Offset(size.width / 2f, size.height / 2f)

                            // সেন্টারের ব্যাকড্রপ অরা গ্লো
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF00E5FF).copy(alpha = 0.18f),
                                        Color(0xFFFF007A).copy(alpha = 0.09f),
                                        Color.Transparent
                                    ),
                                    center = centerOffset,
                                    radius = size.minDimension * 0.72f
                                ),
                                radius = size.minDimension * 0.72f,
                                center = centerOffset
                            )

                            // চারদিকের নরম সারফেস গ্লো
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
                        // 🎬 PD Flix ব্র্যান্ড লোগো (শুধুমাত্র বাঁকা সুইশ লাইন সহ)
                        PdFlixBrandLogo()

                        Spacer(modifier = Modifier.height(18.dp))

                        // 🎯 আপনার চাওয়া ফরম্যাট: Starting soon... 0% 0.00MB/s
                        Text(
                            text = "Starting soon... $livePercentage%  $liveSpeedText",
                            color = Color.White.copy(alpha = 0.95f),
                            fontSize = 14.sp,
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

            // =========================================================================
            // 🛡️ সুরক্ষিত ওটিটি এরর পপআপ (কোনো সোর্স বা গোপন লিংক প্রকাশ পাবে না)
            // =========================================================================
            if (activePlaybackErrorType != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .widthIn(max = 380.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF141923)),
                        border = BorderStroke(1.dp, Color(0xFF263248))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF3B171E)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WarningAmber,
                                    contentDescription = "Error",
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Text(
                                text = "ভিডিও লোড করা যাচ্ছে না",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )

                            // 🎯 শুধুমাত্র এরর টাইপ দেখাবে, কোনো গোপন URL নয়
                            Text(
                                text = activePlaybackErrorType ?: "সার্ভার রেসপন্স করছে না",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.5.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 17.sp
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        activePlaybackErrorType = null
                                        if (onOpenServerSelector != null) {
                                            onOpenServerSelector()
                                        } else {
                                            sideDrawerType = "quality"
                                            showSideDrawer = true
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFF00E5FF))
                                ) {
                                    Text("সার্ভার পরিবর্তন", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        activePlaybackErrorType = null
                                        livePercentage = 0
                                        exoPlayer.prepare()
                                        exoPlayer.play()
                                    },
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                                ) {
                                    Text("পুনরায় চেষ্টা", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // ২X স্পিড ইন্ডিকেটর
            if (is2xActive) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(imageVector = Icons.Default.Bolt, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                    Text(text = "2X Speed", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }

            // স্ক্রিন ড্র্যাগ ফরোয়ার্ড/রিওয়াইন্ড স্ক্রাব
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
                        Text(text = formatTimeDisplay(scrubTargetPositionMs), color = Color(0xFF00E5FF), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(text = "/", color = Color.White.copy(alpha = 0.7f), fontSize = 18.sp)
                        Text(text = formatTimeDisplay(totalDurationMs), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }

                    val scrubFraction = if (totalDurationMs > 0) (scrubTargetPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f) else 0f
                    Box(
                        modifier = Modifier
                            .width(135.dp)
                            .height(2.8.dp)
                            .clip(RoundedCornerShape(1.4.dp))
                            .background(Color.White.copy(alpha = 0.35f))
                    ) {
                        Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(scrubFraction).background(Color(0xFF00E5FF)))
                    }
                }
            }

            // ডাবল-ট্যাপ অ্যানিমেশন
            val screenWidthDp = configuration.screenWidthDp.dp
            val doubleTapOffset = remember(screenWidthDp) { screenWidthDp * 0.25f }

            androidx.compose.animation.AnimatedVisibility(
                visible = showLeftDoubleTapAnim,
                enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.8f),
                exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.8f),
                modifier = Modifier.align(Alignment.Center).offset(x = -doubleTapOffset)
            ) {
                OttDoubleTapSkipBadge(isForward = false, skipSeconds = 10)
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showRightDoubleTapAnim,
                enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.8f),
                exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.8f),
                modifier = Modifier.align(Alignment.Center).offset(x = doubleTapOffset)
            ) {
                OttDoubleTapSkipBadge(isForward = true, skipSeconds = 10)
            }

            // ব্রাইটনেস ও ভলিউম HUD
            if (showBrightnessOverlay && !isPiPActive && !showSideDrawer) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 26.dp)
                ) {
                    Icon(Icons.Default.WbSunny, contentDescription = "Brightness", tint = Color.White, modifier = Modifier.size(16.dp))
                    Box(modifier = Modifier.width(85.dp).height(2.5.dp).clip(RoundedCornerShape(1.5.dp)).background(Color.White.copy(alpha = 0.35f))) {
                        Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(brightnessLevel.coerceIn(0f, 1f)).background(Color(0xFF00E5FF)))
                    }
                }
            }

            if (showVolumeOverlay && !isPiPActive && !showSideDrawer) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 26.dp)
                ) {
                    Icon(
                        imageVector = if (volumeLevel <= 0f) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Volume",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Box(modifier = Modifier.width(85.dp).height(2.5.dp).clip(RoundedCornerShape(1.5.dp)).background(Color.White.copy(alpha = 0.35f))) {
                        Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(volumeLevel.coerceIn(0f, 1f)).background(Color(0xFF00E5FF)))
                    }
                }
            }

            // ওপরের টপ বার
            androidx.compose.animation.AnimatedVisibility(
                visible = isControlsVisible && !isScreenLocked && activePlaybackErrorType == null,
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
                visible = isControlsVisible && !isScreenLocked && activePlaybackErrorType == null,
                enter = fadeIn(animationSpec = tween(200)) + scaleIn(initialScale = 0.85f),
                exit = fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 0.85f),
                modifier = Modifier.align(Alignment.Center)
            ) {
                IconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier.size(54.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.50f))
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            // বটম কন্ট্রোল বার
            androidx.compose.animation.AnimatedVisibility(
                visible = isControlsVisible && !isScreenLocked && activePlaybackErrorType == null,
                enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(200)) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(200)) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f), Color.Black.copy(alpha = 0.95f))))
                        .then(if (isDeviceLandscape) Modifier.navigationBarsPadding() else Modifier)
                        .padding(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    if (showLastSeenBanner && lastSeenPosMs > 5000L) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xF0131722),
                            border = BorderStroke(0.8.dp, Color(0xFF263248)),
                            modifier = Modifier.padding(start = 6.dp, bottom = 4.dp)
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
                                    modifier = Modifier.size(14.dp).clickable { showLastSeenBanner = false }
                                )
                                Text("Last Seen ${formatTimeDisplay(lastSeenPosMs)}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    text = "Jump Play",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable {
                                        exoPlayer.seekTo(lastSeenPosMs)
                                        onSeekFinished(lastSeenPosMs)
                                        showLastSeenBanner = false
                                    }
                                )
                            }
                        }
                    }

                    if (isUserSeeking && totalDurationMs > 0) {
                        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color.Black.copy(alpha = 0.88f),
                                modifier = Modifier.align(Alignment.Center)
                            ) {
                                Text(
                                    text = "${formatTimeDisplay(scrubPosition)} / ${formatTimeDisplay(totalDurationMs)}",
                                    color = Color.White,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                                )
                            }
                        }
                    }

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
                        modifier = Modifier.fillMaxWidth().height(10.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 1.dp, bottom = 3.dp, start = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.size(24.dp).clip(CircleShape).clickable { onPlayPauseClick() }, contentAlignment = Alignment.Center) {
                                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                            }
                            Box(modifier = Modifier.size(24.dp).clip(CircleShape).clickable {
                                isMuted = !isMuted
                                exoPlayer.volume = if (isMuted) 0f else 1f
                            }, contentAlignment = Alignment.Center) {
                                Icon(if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.5.dp)) {
                                Text(formatTimeDisplay(if (isUserSeeking) scrubPosition else currentPositionMs), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("/", color = Color.White.copy(alpha = 0.50f), fontSize = 10.5.sp)
                                Text(formatTimeDisplay(totalDurationMs), color = Color.White.copy(alpha = 0.70f), fontSize = 11.sp)
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            if (isDeviceLandscape) {
                                IconButton(onClick = onNextEpisode, modifier = Modifier.size(26.dp)) {
                                    Icon(Icons.Default.SkipNext, contentDescription = "Next Episode", tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable {
                                    sideDrawerType = "speed"
                                    showSideDrawer = true
                                }.padding(horizontal = 3.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Outlined.Speed, contentDescription = "Speed", tint = Color.White, modifier = Modifier.size(13.dp))
                                Text(if (currentSpeed == 1.0f) "1x" else "${currentSpeed}x", color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable {
                                    sideDrawerType = "quality"
                                    showSideDrawer = true
                                }.padding(horizontal = 3.dp, vertical = 2.dp)
                            ) {
                                Box(modifier = Modifier.border(0.8.dp, Color.White, RoundedCornerShape(2.dp)).padding(horizontal = 2.dp, vertical = 0.5.dp)) {
                                    Text("HD", color = Color.White, fontSize = 6.5.sp, fontWeight = FontWeight.Black)
                                }
                                Text(currentQualityLabel, color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }

                            if (availableAudioTracks.size > 1) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable {
                                        sideDrawerType = "audio"
                                        showSideDrawer = true
                                    }.padding(horizontal = 3.dp, vertical = 2.dp)
                                ) {
                                    MusicNoteDubbingIcon(tint = Color(0xFF00E5FF))
                                    Text(text = currentAudioLabel, color = Color(0xFF00E5FF), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (availableSubtitleTracks.isNotEmpty()) {
                                Box(
                                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable {
                                        sideDrawerType = "subtitles"
                                        showSideDrawer = true
                                    }.padding(horizontal = 3.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = currentSubtitleLabel, color = if (selectedSubtitleTrackId != "off") Color(0xFF00E5FF) else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                    Icon(Icons.Outlined.Layers, contentDescription = "Episodes", tint = if (showSideDrawer && sideDrawerType == "playlist") Color(0xFF00E5FF) else Color.White, modifier = Modifier.size(18.dp))
                                }
                            }

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
                                Icon(Icons.Outlined.FileDownload, contentDescription = "Download", tint = if (isDeviceLandscape && showSideDrawer && sideDrawerType == "download") Color(0xFF00E5FF) else Color.White, modifier = Modifier.size(17.dp))
                            }

                            Box(modifier = Modifier.size(24.dp).clip(CircleShape).clickable { onToggleFullscreen() }, contentAlignment = Alignment.Center) {
                                DiagonalFullscreenIcon(color = Color.White)
                            }
                        }
                    }
                }
            }

            // সাইড ড্রয়ার
            androidx.compose.animation.AnimatedVisibility(
                visible = showSideDrawer && sideDrawerType != "for_you" && !isPiPActive,
                enter = slideInHorizontally { it } + fadeIn(),
                exit = slideOutHorizontally { it } + fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(if (isDeviceLandscape) 0.24f else 0.50f)
                        .background(Color(0xFF10141E))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (sideDrawerType == "playlist") "Episodes (${episodes.size})" else sideDrawerType.uppercase(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { showSideDrawer = false }, modifier = Modifier.size(22.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        if (sideDrawerType == "playlist") {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(4),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(episodes, key = { it.episodeId }) { ep ->
                                    val isSelected = ep.episodeNumber == episodeNumber
                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(1.1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) Color(0xFF0F3B32) else Color(0xFF1E2433))
                                            .border(1.dp, if (isSelected) Color(0xFF00E676) else Color.Transparent, RoundedCornerShape(6.dp))
                                            .clickable {
                                                isFirstFrameRendered = false
                                                isBuffering = true
                                                onSelectEpisode(ep)
                                                showSideDrawer = false
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = ep.episodeNumber.toString(), color = if (isSelected) Color(0xFF00E676) else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else if (sideDrawerType == "speed") {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(speedOptions) { spd ->
                                    Text(
                                        text = "${spd}x",
                                        color = if (currentSpeed == spd) Color(0xFF00E5FF) else Color.White,
                                        fontSize = 13.5.sp,
                                        fontWeight = if (currentSpeed == spd) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            currentSpeed = spd
                                            exoPlayer.setPlaybackSpeed(spd)
                                            showSideDrawer = false
                                        }.padding(vertical = 4.dp)
                                    )
                                }
                            }
                        } else if (sideDrawerType == "quality") {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(availableVideoTracks) { track ->
                                    val isSelected = if (track.isAuto) currentSelectedHeight == 0 else currentSelectedHeight == track.height
                                    Text(
                                        text = track.label,
                                        color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                                        fontSize = 13.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            applyExoPlayerQuality(track.height, if (track.isAuto) "Auto" else "${track.height}P")
                                            showSideDrawer = false
                                        }.padding(vertical = 4.dp)
                                    )
                                }
                            }
                        } else if (sideDrawerType == "audio") {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(availableAudioTracks) { audioTrack ->
                                    val isSelected = (selectedAudioTrackId == audioTrack.id)
                                    Text(
                                        text = audioTrack.label,
                                        color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                                        fontSize = 13.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            applyExoPlayerAudioTrack(audioTrack)
                                            showSideDrawer = false
                                        }.padding(vertical = 4.dp)
                                    )
                                }
                            }
                        } else if (sideDrawerType == "subtitles") {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    Text(
                                        text = "Off",
                                        color = if (selectedSubtitleTrackId == "off") Color(0xFF00E5FF) else Color.White,
                                        fontSize = 13.5.sp,
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            applyExoPlayerSubtitleTrack(null)
                                            showSideDrawer = false
                                        }.padding(vertical = 4.dp)
                                    )
                                }
                                items(availableSubtitleTracks) { subTrack ->
                                    val isSelected = (selectedSubtitleTrackId == subTrack.id)
                                    Text(
                                        text = subTrack.label,
                                        color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                                        fontSize = 13.5.sp,
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            applyExoPlayerSubtitleTrack(subTrack)
                                            showSideDrawer = false
                                        }.padding(vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
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
                modifier = Modifier.fillMaxHeight().width(260.dp),
                color = Color(0xFF10141E)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("For You", color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showSideDrawer = false }, modifier = Modifier.size(22.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF8E95A5), modifier = Modifier.size(15.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val displayRecs = recommendations.filter { it.slug != slug }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(displayRecs) { rec ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable {
                                    onRelatedDramaClick(rec.slug)
                                    showSideDrawer = false
                                }.padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(modifier = Modifier.width(105.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(5.dp)).background(Color(0xFF1A1F2C))) {
                                    val bannerImg = rec.bannerUrl?.takeIf { it.isNotBlank() } ?: rec.posterUrl
                                    AsyncImage(model = bannerImg, contentDescription = rec.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                }
                                Text(rec.title, color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}
