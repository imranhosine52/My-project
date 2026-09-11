@file:OptIn(UnstableApi::class)

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
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.model.ContentItemDto
import com.example.data.model.EpisodeDto
import com.example.ui.screens.EqualizerBarsIcon
import com.example.ui.screens.SleekOnlineTimeline
import com.example.ui.screens.SleekSkipIconOnline
import com.example.ui.theme.GoldVip
import com.example.util.R2DownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs

private data class LiveDanmakuItem(
    val id: Long,
    val text: String,
    val lineIndex: Int,
    val startDelayMs: Long
)

private fun getDubLanguageBadge(title: String, categories: List<String>): String {
    val lowerTitle = title.lowercase()
    val lowerCats = categories.map { it.lowercase() }

    return when {
        lowerTitle.contains("bangla") || lowerCats.any { it.contains("bangla") || it.contains("bengali") } -> "Bangla"
        lowerTitle.contains("hindi") || lowerCats.any { it.contains("hindi") } -> "Hindi"
        lowerTitle.contains("english") || lowerCats.any { it.contains("english") } -> "English"
        lowerTitle.contains("dubbed") || lowerCats.any { it.contains("dub") } -> "Dubbed"
        else -> "HD"
    }
}

private suspend fun fetchRealFileSize(url: String): Long = withContext(Dispatchers.IO) {
    if (url.isBlank()) return@withContext 0L
    try {
        val connection = (URL(url).openConnection() as? HttpURLConnection)?.apply {
            requestMethod = "HEAD"
            connectTimeout = 4500
            readTimeout = 4500
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "PlayDramaFlix")
            setRequestProperty("Accept-Encoding", "identity")
        }
        val length = connection?.contentLengthLong ?: 0L
        connection?.disconnect()
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

private fun extractDanmakuText(comment: Any): String {
    if (comment is String) return comment
    val clazz = comment.javaClass
    val candidateNames = listOf("commentText", "text", "comment", "content", "message", "body")
    for (name in candidateNames) {
        try {
            val getterName = "get" + name.replaceFirstChar { it.uppercase() }
            val method = clazz.methods.find { it.name.equals(getterName, ignoreCase = true) || it.name.equals(name, ignoreCase = true) }
            if (method != null && method.parameterCount == 0) {
                val res = method.invoke(comment)
                if (res != null && res.toString().isNotBlank() && !res.toString().startsWith("DramaApiComment(")) {
                    return res.toString()
                }
            }
        } catch (_: Exception) {}
        try {
            val field = clazz.declaredFields.find { it.name.equals(name, ignoreCase = true) }
            if (field != null) {
                field.isAccessible = true
                val res = field.get(comment)
                if (res != null && res.toString().isNotBlank() && !res.toString().startsWith("DramaApiComment(")) {
                    return res.toString()
                }
            }
        } catch (_: Exception) {}
    }
    val str = comment.toString()
    val regex = Regex("""commentText=([^,\)]+)""")
    return regex.find(str)?.groupValues?.get(1) ?: str.take(40)
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

    var isDanmakuEnabled by rememberSaveable { mutableStateOf(true) }

    // 🎯 ১ নম্বর ছবি: ইমোজি প্যানেল শো/হাইড স্টেট
    var showEmojiPicker by remember { mutableStateOf(false) }

    // ১ নম্বর ছবির মতো পপুলার রিঅ্যাকশন ইমোজি তালিকা
    val popularEmojis = remember {
        listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "🥲", "🥹", "😊", "😇", "🙂", "🙃", "😉",
            "😍", "🥰", "😘", "😗", "😚", "😋", "😛", "😜", "🤪", "😝", "🤑", "🤗", "🤭", "🤫", "🤔",
            "🤐", "🤨", "😐", "😑", "😶", "😏", "😒", "🙄", "😬", "😮‍💨", "🤥", "😌", "😔", "😪", "🤤",
            "😴", "😷", "🤒", "🤕", "🤢", "🤮", "🤧", "🥵", "🥶", "🥴", "😵", "🤯", "🤠", "🥳", "🥸",
            "😎", "🤓", "🧐", "😕", "😟", "🙁", "😮", "😯", "😲", "😳", "🥺", "😦", "😧", "😨", "😰",
            "😥", "😢", "😭", "😱", "😖", "😣", "😞", "😓", "😩", "😫", "🥱", "😤", "😡", "😠", "🤬",
            "❤️", "💔", "💖", "🔥", "👍", "👎", "👏", "🙌", "🫶", "🤝", "🙏", "🎉", "✨", "☕", "🎂"
        )
    }

    var showSideDrawer by remember { mutableStateOf(false) }
    var sideDrawerType by remember { mutableStateOf("playlist") }

    val selectedDownloadEpisodes = remember { mutableStateListOf<EpisodeDto>() }
    val realFileSizes = remember { mutableStateMapOf<String, Long>() }
    var isFetchingSizes by remember { mutableStateOf(false) }

    var commentInputText by remember { mutableStateOf("") }

    val speedOptions = remember { listOf(4.0f, 3.0f, 2.0f, 1.5f, 1.25f, 1.0f, 0.75f, 0.5f) }
    var currentSpeed by rememberSaveable { mutableFloatStateOf(1.0f) }

    val danmakuList = remember { mutableStateListOf<LiveDanmakuItem>() }

    LaunchedEffect(comments) {
        if (comments.isNotEmpty() && danmakuList.isEmpty()) {
            comments.take(12).forEachIndexed { index, c ->
                val text = extractDanmakuText(c)
                if (text.isNotBlank()) {
                    danmakuList.add(
                        LiveDanmakuItem(
                            id = System.currentTimeMillis() + index,
                            text = text,
                            lineIndex = index % 3,
                            startDelayMs = (index * 2200L)
                        )
                    )
                }
            }
        }
    }

    LaunchedEffect(selectedDownloadEpisodes.toList()) {
        val uncalculated = selectedDownloadEpisodes.filter { ep ->
            val key = "${ep.episodeId}_${ep.episodeNumber}"
            !realFileSizes.containsKey(key) || (realFileSizes[key] ?: 0L) <= 0L
        }

        if (uncalculated.isNotEmpty()) {
            isFetchingSizes = true
            uncalculated.forEach { ep ->
                coroutineScope.launch {
                    val dlUrl = ep.resolveDownloadUrl(slug)
                    val size = fetchRealFileSize(dlUrl)
                    val key = "${ep.episodeId}_${ep.episodeNumber}"
                    if (size > 0) {
                        realFileSizes[key] = size
                    }
                }
            }
            isFetchingSizes = false
        }
    }

    val totalSelectedBytes = remember(selectedDownloadEpisodes.toList(), realFileSizes.toMap()) {
        selectedDownloadEpisodes.sumOf { ep ->
            val key = "${ep.episodeId}_${ep.episodeNumber}"
            realFileSizes[key] ?: 0L
        }
    }

    val isPiPActive = remember(activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
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

    var isUserSeeking by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableLongStateOf(0L) }

    var isBuffering by remember { mutableStateOf(exoPlayer.playbackState == Player.STATE_BUFFERING) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = (state == Player.STATE_BUFFERING)
            }
            override fun onIsPlayingChanged(playingState: Boolean) {
                if (playingState) isBuffering = false
            }
            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    var isRewindActive by remember { mutableStateOf(false) }
    var isForwardActive by remember { mutableStateOf(false) }
    val rewindRotation = remember { Animatable(0f) }
    val forwardRotation = remember { Animatable(0f) }
    val rewindAlpha by animateFloatAsState(targetValue = if (isRewindActive) 1f else 0f, label = "rewindAlpha")
    val forwardAlpha by animateFloatAsState(targetValue = if (isForwardActive) 1f else 0f, label = "forwardAlpha")

    fun triggerSkip(seconds: Int) {
        onSeek(seconds)
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

    LaunchedEffect(isControlsVisible, isPlaying, isScreenLocked, showSideDrawer, showEmojiPicker) {
        if (isControlsVisible && isPlaying && !isScreenLocked && !showSideDrawer && !showEmojiPicker) {
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
                showEmojiPicker = false
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
                        val isRightEdge = firstDown.position.x > size.width * 0.70f

                        var isTransforming = false
                        var isVerticalDragging = false
                        var totalDragY = 0f
                        var totalDragX = 0f
                        val touchSlop = viewConfiguration.touchSlop
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()

                        do {
                            val event = awaitPointerEvent()
                            val pointerCount = event.changes.size

                            if (pointerCount >= 2) {
                                isTransforming = true
                                showBrightnessOverlay = false
                                showVolumeOverlay = false

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

                                if (!isVerticalDragging && abs(totalDragY) > touchSlop && abs(totalDragY) > abs(totalDragX)) {
                                    isVerticalDragging = true
                                    if (isLeft) showBrightnessOverlay = true else showVolumeOverlay = true
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
                                } else if (isDeviceLandscape && isRightEdge && totalDragX < -touchSlop * 1.5f && abs(totalDragX) > abs(totalDragY)) {
                                    sideDrawerType = "for_you"
                                    showSideDrawer = true
                                    change.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        showBrightnessOverlay = false
                        showVolumeOverlay = false
                    }
                }
                .pointerInput(isScreenLocked, showSideDrawer, isPiPActive) {
                    detectTapGestures(
                        onTap = {
                            if (!isPiPActive) {
                                if (showSideDrawer) {
                                    showSideDrawer = false
                                } else if (showEmojiPicker) {
                                    showEmojiPicker = false
                                } else {
                                    isControlsVisible = !isControlsVisible
                                }
                            }
                        },
                        onDoubleTap = { offset ->
                            if (!isScreenLocked && !showSideDrawer && !isPiPActive && !showEmojiPicker) {
                                if (zoomScale > 1.05f) {
                                    zoomScale = 1.0f
                                    zoomOffset = Offset.Zero
                                } else {
                                    if (offset.x < size.width / 2) triggerSkip(-10) else triggerSkip(10)
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

            // ভাসমান লাইভ কমেন্ট লেয়ার
            if (isDanmakuEnabled && !isPiPActive && isDeviceLandscape) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .align(Alignment.TopStart)
                        .padding(top = 18.dp)
                ) {
                    danmakuList.forEach { item ->
                        key(item.id) {
                            FloatingDanmakuRow(
                                item = item,
                                screenWidthPx = configuration.screenWidthDp * 3f
                            )
                        }
                    }
                }
            }

            if (isBuffering && !isPiPActive) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF00E5FF), strokeWidth = 3.dp, modifier = Modifier.size(44.dp))
                }
            }

            // ব্রাইটনেস HUD
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

            // ভলিউম HUD
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

            // PiP মোড ডিজাইন
            if (isPiPActive) {
                Box(modifier = Modifier.fillMaxSize()) {
                    IconButton(
                        onClick = onPlayPauseClick,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(46.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            } else {
                // অন-স্ক্রিন প্লেয়ার কন্ট্রোলস
                androidx.compose.animation.AnimatedVisibility(
                    visible = isControlsVisible && !isScreenLocked && !showEmojiPicker,
                    enter = slideInVertically(initialOffsetY = { -it }, animationSpec = tween(240)) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(240)) + fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)))
                            .then(if (isDeviceLandscape) Modifier.statusBarsPadding() else Modifier)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = onBackClick, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(24.dp))
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

                androidx.compose.animation.AnimatedVisibility(
                    visible = isControlsVisible && !isScreenLocked && !showEmojiPicker,
                    enter = fadeIn(animationSpec = tween(200)) + scaleIn(initialScale = 0.85f),
                    exit = fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 0.85f),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(50.dp),
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
                            IconButton(onClick = { triggerSkip(-10) }, modifier = Modifier.size(46.dp).rotate(rewindRotation.value)) {
                                SleekSkipIconOnline(isForward = false, color = Color.White)
                            }
                        }

                        IconButton(onClick = onPlayPauseClick, modifier = Modifier.size(54.dp)) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.size(44.dp)
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
                            IconButton(onClick = { triggerSkip(10) }, modifier = Modifier.size(46.dp).rotate(forwardRotation.value)) {
                                SleekSkipIconOnline(isForward = true, color = Color.White)
                            }
                        }
                    }
                }

                if (isDeviceLandscape) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isControlsVisible && !isScreenLocked && !showEmojiPicker,
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
                                .padding(end = 12.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f))
                        ) {
                            Icon(Icons.Outlined.LockOpen, contentDescription = "Lock", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                // বটম বার (ইমোজি পিকার না থাকলে সাধারণ বার দেখাবে)
                androidx.compose.animation.AnimatedVisibility(
                    visible = isControlsVisible && !isScreenLocked && !showEmojiPicker,
                    enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(240)) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(240)) + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.90f))))
                            .then(if (isDeviceLandscape) Modifier.navigationBarsPadding() else Modifier)
                            .padding(start = 12.dp, end = 10.dp, bottom = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = formatTimeDisplay(if (isUserSeeking) scrubPosition else currentPositionMs),
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )

                            SleekOnlineTimeline(
                                currentPositionMs = if (isUserSeeking) scrubPosition else currentPositionMs,
                                totalDurationMs = totalDurationMs,
                                onSeekStarted = { isUserSeeking = true },
                                onSeeking = { scrubPosition = it },
                                onSeekFinished = { targetPos ->
                                    onSeekFinished(targetPos)
                                    isUserSeeking = false
                                },
                                modifier = Modifier.weight(1f)
                            )

                            Text(
                                text = formatTimeDisplay(totalDurationMs),
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )

                            if (!isDeviceLandscape) {
                                Text(
                                    text = if (currentSpeed == 1.0f) "1x" else "${currentSpeed}x",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.15f))
                                        .clickable {
                                            sideDrawerType = "speed"
                                            showSideDrawer = true
                                        }
                                        .padding(horizontal = 7.dp, vertical = 3.dp)
                                )

                                IconButton(
                                    onClick = { onDownloadClick?.invoke() },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Outlined.FileDownload, contentDescription = "Download", tint = Color.White, modifier = Modifier.size(20.dp))
                                }

                                IconButton(onClick = onToggleFullscreen, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Fullscreen, contentDescription = "Rotate", tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                        }

                        // ল্যান্ডস্কেপ বটম বার
                        if (isDeviceLandscape) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clickable { isDanmakuEnabled = !isDanmakuEnabled },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Subtitles,
                                            contentDescription = "Danmaku Toggle",
                                            tint = if (isDanmakuEnabled) Color.White else Color.White.copy(alpha = 0.4f),
                                            modifier = Modifier.size(20.dp)
                                        )

                                        if (isDanmakuEnabled) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .offset(x = 1.dp, y = 1.dp)
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF00E5FF)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Color.Black,
                                                    modifier = Modifier.size(7.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (isDanmakuEnabled) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth(0.88f)
                                                .height(34.dp)
                                                .clip(RoundedCornerShape(17.dp))
                                                .background(Color(0xFF181B24).copy(alpha = 0.85f))
                                                .padding(horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            // 🎯 ইমোজি আইকন (চাপ দিলে ১ নম্বর ছবির ইমোজি প্যানেল খুলবে)
                                            Icon(
                                                imageVector = Icons.Outlined.SentimentSatisfiedAlt,
                                                contentDescription = "Emoji",
                                                tint = Color.White.copy(alpha = 0.7f),
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clickable { showEmojiPicker = true }
                                            )

                                            Box(modifier = Modifier.weight(1f)) {
                                                if (commentInputText.isEmpty()) {
                                                    Text("Say something", color = Color.White.copy(alpha = 0.5f), fontSize = 11.5.sp)
                                                }
                                                BasicTextField(
                                                    value = commentInputText,
                                                    onValueChange = { if (it.length <= 60) commentInputText = it },
                                                    textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                                                    cursorBrush = SolidColor(Color(0xFF00E5FF)),
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }

                                            Text(
                                                text = "${60 - commentInputText.length}",
                                                color = Color.White.copy(alpha = 0.35f),
                                                fontSize = 9.5.sp
                                            )

                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Send,
                                                contentDescription = "Send",
                                                tint = if (commentInputText.isNotBlank()) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.4f),
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clickable(enabled = commentInputText.isNotBlank()) {
                                                        val text = commentInputText.trim()
                                                        onSendComment(text)
                                                        danmakuList.add(
                                                            LiveDanmakuItem(
                                                                id = System.currentTimeMillis(),
                                                                text = text,
                                                                lineIndex = (0..2).random(),
                                                                startDelayMs = 0L
                                                            )
                                                        )
                                                        commentInputText = ""
                                                    }
                                            )
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .clickable {
                                                sideDrawerType = "speed"
                                                showSideDrawer = true
                                            }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Speed,
                                            contentDescription = "Speed",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "${currentSpeed}x",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            sideDrawerType = "playlist"
                                            showSideDrawer = !showSideDrawer
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Layers,
                                            contentDescription = "Episodes Playlist",
                                            tint = if (showSideDrawer && sideDrawerType == "playlist") Color(0xFF00E5FF) else Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            sideDrawerType = "download"
                                            showSideDrawer = !showSideDrawer
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.FileDownload,
                                            contentDescription = "Download Episodes",
                                            tint = if (showSideDrawer && sideDrawerType == "download") Color(0xFF00E5FF) else Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    IconButton(onClick = onNextEpisode, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(22.dp))
                                    }

                                    IconButton(
                                        onClick = {
                                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                            onToggleFullscreen()
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FullscreenExit,
                                            contentDescription = "Exit Fullscreen",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // 🎯 ১ নম্বর ছবি: ফুলস্ক্রিন ল্যান্ডস্কেপ ইমোজি বোর্ড প্যানেল
            // =========================================================================
            androidx.compose.animation.AnimatedVisibility(
                visible = showEmojiPicker && isDeviceLandscape && !isPiPActive,
                enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(240)) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(240)) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .pointerInput(Unit) { detectTapGestures {} },
                    color = Color(0xFF12151E).copy(alpha = 0.95f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        // টপ ইনপুট বার
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SentimentSatisfiedAlt,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(20.dp)
                            )

                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (commentInputText.isEmpty()) {
                                    Text("Say something", color = Color.White.copy(alpha = 0.5f), fontSize = 11.5.sp)
                                }
                                BasicTextField(
                                    value = commentInputText,
                                    onValueChange = { if (it.length <= 60) commentInputText = it },
                                    textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                                    cursorBrush = SolidColor(Color(0xFF00E5FF)),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${60 - commentInputText.length}",
                                    color = Color.White.copy(alpha = 0.35f),
                                    fontSize = 9.sp
                                )
                            }

                            // সেন্ড বাটন
                            IconButton(
                                onClick = {
                                    if (commentInputText.isNotBlank()) {
                                        val text = commentInputText.trim()
                                        onSendComment(text)
                                        danmakuList.add(
                                            LiveDanmakuItem(
                                                id = System.currentTimeMillis(),
                                                text = text,
                                                lineIndex = (0..2).random(),
                                                startDelayMs = 0L
                                            )
                                        )
                                        commentInputText = ""
                                        showEmojiPicker = false
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color(0xFF00E5FF), modifier = Modifier.size(18.dp))
                            }

                            // ইমোজি বোর্ড ক্লোজ বাটন
                            IconButton(onClick = { showEmojiPicker = false }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF8E95A5), modifier = Modifier.size(18.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // ১ নম্বর ছবির মতো ইমোজি গ্রিড
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(15),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(popularEmojis) { emoji ->
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            if (commentInputText.length < 58) {
                                                commentInputText += emoji
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = emoji, fontSize = 18.sp)
                                }
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // 📑 সাইড ড্রয়ার: স্পিড, এপিসোড ও ডাউনলোড ড্রয়ার
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
                            if (sideDrawerType == "speed") {
                                if (isDeviceLandscape) 0.28f else 0.45f
                            } else {
                                if (isDeviceLandscape) 0.32f else 0.75f
                            }
                        )
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.50f),
                                    Color.Black.copy(alpha = 0.85f),
                                    Color.Black.copy(alpha = 0.96f)
                                )
                            )
                        )
                        .pointerInput(Unit) { detectTapGestures {} }
                ) {
                    if (sideDrawerType == "speed") {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 14.dp, horizontal = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            items(speedOptions) { spd ->
                                val isSelected = currentSpeed == spd
                                val label = if (spd == 1.0f) "1.0 X · Normal" else "${spd} x"

                                Text(
                                    text = label,
                                    color = if (isSelected) Color(0xFF00E5FF) else Color(0xFFCBD5E1),
                                    fontSize = 14.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier
                                        .clickable {
                                            currentSpeed = spd
                                            exoPlayer.setPlaybackSpeed(spd)
                                            showSideDrawer = false
                                        }
                                        .padding(vertical = 4.dp)
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (sideDrawerType == "download") "Download (${selectedDownloadEpisodes.size})" else "Episodes (${episodes.size})",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(onClick = { showSideDrawer = false }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF8E95A5), modifier = Modifier.size(16.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            val displayEps = episodes.ifEmpty {
                                (1..30).map { EpisodeDto(episodeNumber = it, isLocked = it > 1) }
                            }

                            LazyVerticalGrid(
                                columns = GridCells.Fixed(5),
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
                                            .aspectRatio(1.05f)
                                            .clip(RoundedCornerShape(5.dp))
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
                                                shape = RoundedCornerShape(5.dp)
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
                                                EqualizerBarsIcon(modifier = Modifier.size(11.dp, 8.dp), tint = Color(0xFF00E676))
                                            } else {
                                                Text(
                                                    text = ep.episodeNumber.toString(),
                                                    color = if ((sideDrawerType == "download" && isSelectedForDl) || (sideDrawerType == "playlist" && isSelected)) Color(0xFF00E676) else Color.White,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            if (isEpLocked && !isSelected && sideDrawerType == "playlist") {
                                                Text(text = "VIP", color = GoldVip, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            if (sideDrawerType == "download") {
                                Spacer(modifier = Modifier.height(6.dp))
                                val displaySize = formatSize(totalSelectedBytes, isFetchingSizes && totalSelectedBytes == 0L)
                                Button(
                                    onClick = {
                                        if (selectedDownloadEpisodes.isNotEmpty()) {
                                            selectedDownloadEpisodes.forEach { ep ->
                                                R2DownloadManager.startDownload(
                                                    context = context,
                                                    downloadUrl = ep.resolveDownloadUrl(slug),
                                                    title = title,
                                                    episodeNumber = ep.episodeNumber,
                                                    isMovie = false
                                                )
                                            }
                                            Toast.makeText(context, "Downloading ${selectedDownloadEpisodes.size} episodes ($displaySize)", Toast.LENGTH_SHORT).show()
                                            showSideDrawer = false
                                        }
                                    },
                                    enabled = selectedDownloadEpisodes.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D26A)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().height(36.dp)
                                ) {
                                    Text(
                                        text = "Download (${selectedDownloadEpisodes.size}) · $displaySize",
                                        color = Color.Black,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isScreenLocked && !isPiPActive) {
                IconButton(
                    onClick = {
                        isScreenLocked = false
                        isControlsVisible = true
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.75f))
                ) {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = "Unlock", tint = Color(0xFFFF5252), modifier = Modifier.size(22.dp))
                }
            }
        }

        // =========================================================================
        // 🎯 ২ নম্বর ছবি: For You সাইড প্যানেল (ক্লিক করলেই ইনস্ট্যান্ট ড্রামা সুইচ)
        // =========================================================================
        androidx.compose.animation.AnimatedVisibility(
            visible = isForYouDocked,
            enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(),
            exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(280.dp)
                    .pointerInput(Unit) { detectTapGestures {} },
                color = Color(0xFF10141E)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "For You",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showSideDrawer = false }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF8E95A5), modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val displayRecs = recommendations.filter { it.slug != slug }

                    if (displayRecs.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("Loading series...", color = Color(0xFF8E95A5), fontSize = 12.5.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(displayRecs) { rec ->
                                val dubBadge = getDubLanguageBadge(rec.title, rec.categories)
                                val isBangla = dubBadge == "Bangla"

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            // 🎯 চাপ দেওয়ার সাথে সাথে কোনো ল্যাগ বা ফ্ল্যাশ ছাড়াই ইনস্ট্যান্ট সিরিজ লোড
                                            showSideDrawer = false
                                            onRelatedDramaClick(rec.slug)
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(118.dp)
                                            .aspectRatio(16f / 9f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF1A1F2C))
                                    ) {
                                        val bannerImg = rec.bannerUrl?.takeIf { it.isNotBlank() } ?: rec.posterUrl
                                        AsyncImage(
                                            model = bannerImg,
                                            contentDescription = rec.title,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )

                                        // =============================================================
                                        // 🎯 ২ নম্বর ছবি: সুপার স্লিম ও কোনায় সেট করা ছোট ডাব ব্যাজ
                                        // =============================================================
                                        Surface(
                                            shape = RoundedCornerShape(bottomStart = 3.dp),
                                            color = if (isBangla) Color(0xFF00D26A) else GoldVip,
                                            modifier = Modifier.align(Alignment.TopEnd)
                                        ) {
                                            Text(
                                                text = dubBadge,
                                                color = Color.Black,
                                                fontSize = 6.5.sp, // 🎯 সুপার স্লিম ও ক্ষুদ্র সাইজ
                                                fontWeight = FontWeight.Black,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(horizontal = 3.5.dp, vertical = 0.5.dp) // 🎯 ব্যানার ১০০% ক্লিয়ার থাকবে
                                            )
                                        }
                                    }

                                    Text(
                                        text = rec.title,
                                        color = Color.White,
                                        fontSize = 12.5.sp,
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

@Composable
private fun FloatingDanmakuRow(
    item: LiveDanmakuItem,
    screenWidthPx: Float
) {
    val offsetX = remember { Animatable(screenWidthPx) }

    LaunchedEffect(item.id) {
        delay(item.startDelayMs)
        offsetX.animateTo(
            targetValue = -500f,
            animationSpec = tween(
                durationMillis = (7000..9500).random(),
                easing = LinearEasing
            )
        )
    }

    Box(
        modifier = Modifier
            .offset(y = (item.lineIndex * 34).dp)
            .graphicsLayer { translationX = offsetX.value }
            .padding(horizontal = 8.dp)
    ) {
        Text(
            text = item.text,
            color = Color.White.copy(alpha = 0.90f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            style = TextStyle(
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = Color.Black,
                    offset = Offset(1.5f, 1.5f),
                    blurRadius = 3f
                )
            )
        )
    }
}
