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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
import com.example.data.model.EpisodeDto
import com.example.ui.screens.EqualizerBarsIcon
import com.example.ui.screens.SleekOnlineTimeline
import com.example.ui.screens.SleekSkipIconOnline
import com.example.ui.theme.GoldVip
import com.example.util.R2DownloadManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

// 🎯 স্ক্রিনের ওপর দিয়ে ভেসে চলা কমেন্টের মডেল
private data class LiveDanmakuItem(
    val id: Long,
    val text: String,
    val lineIndex: Int,
    val startDelayMs: Long
)

// 🎯 কমেন্ট অবজেক্ট থেকে টেক্সট বের করার নিরাপদ স্বয়ংসম্পূর্ণ হেল্পার
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { findActivity(context) }
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val configuration = LocalConfiguration.current
    val coroutineScope = rememberCoroutineScope()

    var isControlsVisible by remember { mutableStateOf(true) }
    var isScreenLocked by rememberSaveable { mutableStateOf(false) }

    // ১ নম্বর ছবি: লাইভ কমেন্ট চালু/বন্ধ টগল স্টেট
    var isDanmakuEnabled by rememberSaveable { mutableStateOf(true) }

    // ৩ নম্বর ছবি: সাইডবার ড্রয়ার টাইপ ("playlist", "download", "speed")
    var showSideDrawer by remember { mutableStateOf(false) }
    var sideDrawerType by remember { mutableStateOf("playlist") }

    val selectedDownloadEpisodes = remember { mutableStateListOf<EpisodeDto>() }
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

    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(isScreenLocked, isPiPActive) {
                if (!isScreenLocked && !isPiPActive) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        zoomScale = (zoomScale * zoom).coerceIn(1.0f, 3.0f)
                        if (zoomScale > 1.0f) {
                            val maxOffsetX = (size.width * (zoomScale - 1f)) / 2f
                            val maxOffsetY = (size.height * (zoomScale - 1f)) / 2f
                            zoomOffset = Offset(
                                x = (zoomOffset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX),
                                y = (zoomOffset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                            )
                        } else {
                            zoomOffset = Offset.Zero
                        }
                    }
                }
            }
            .pointerInput(isScreenLocked, showSideDrawer, isPiPActive) {
                detectTapGestures(
                    onTap = {
                        if (!isPiPActive) {
                            if (showSideDrawer) {
                                showSideDrawer = false
                            } else {
                                isControlsVisible = !isControlsVisible
                            }
                        }
                    },
                    onDoubleTap = { offset ->
                        if (!isScreenLocked && !showSideDrawer && !isPiPActive) {
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
            .pointerInput(isScreenLocked, isPiPActive) {
                if (!isScreenLocked && isDeviceLandscape && !isPiPActive) {
                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            if (offset.x < size.width / 2) showBrightnessOverlay = true else showVolumeOverlay = true
                        },
                        onDragEnd = {
                            showBrightnessOverlay = false
                            showVolumeOverlay = false
                        },
                        onVerticalDrag = { change, dragAmount ->
                            val isLeft = change.position.x < size.width / 2
                            val delta = -dragAmount / 500f

                            if (isLeft) {
                                brightnessLevel = (brightnessLevel + delta).coerceIn(0.05f, 1.0f)
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
                        }
                    )
                }
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
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = zoomScale,
                    scaleY = zoomScale,
                    translationX = zoomOffset.x,
                    translationY = zoomOffset.y
                )
        )

        // ভাসমান লাইভ কমেন্ট লেয়ার (Danmaku)
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

        if (showBrightnessOverlay && !isPiPActive) {
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.75f), modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)) {
                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BrightnessMedium, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Brightness ${(brightnessLevel * 100).toInt()}%", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (showVolumeOverlay && !isPiPActive) {
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.75f), modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)) {
                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (volumeLevel == 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Volume ${(volumeLevel * 100).toInt()}%", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // =========================================================================
        // 🎬 অন-স্ক্রিন প্লেয়ার কন্ট্রোলস
        // =========================================================================
        if (!isPiPActive) {
            // 🔝 ১. টপ বার
            AnimatedVisibility(
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

            // ⏯️ ২. সেন্টার স্কিপ ও প্লে/পজ
            AnimatedVisibility(
                visible = isControlsVisible && !isScreenLocked,
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

            // 🔒 লক বাটন
            if (isDeviceLandscape) {
                AnimatedVisibility(
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
                            .padding(end = 12.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f))
                    ) {
                        Icon(Icons.Outlined.LockOpen, contentDescription = "Lock", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // 🔻 ৩. বটম বার
            AnimatedVisibility(
                visible = isControlsVisible && !isScreenLocked,
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
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isDanmakuEnabled) Color(0xFF00E5FF).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.12f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isDanmakuEnabled) Color(0xFF00E5FF) else Color.Transparent
                                    ),
                                    modifier = Modifier
                                        .size(width = 30.dp, height = 26.dp)
                                        .clickable { isDanmakuEnabled = !isDanmakuEnabled }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isDanmakuEnabled) Icons.Default.Subtitles else Icons.Default.SubtitlesOff,
                                            contentDescription = "Danmaku Toggle",
                                            tint = if (isDanmakuEnabled) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                if (isDanmakuEnabled) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth(0.85f)
                                            .height(34.dp)
                                            .clip(RoundedCornerShape(17.dp))
                                            .background(Color.White.copy(alpha = 0.15f))
                                            .padding(horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (commentInputText.isEmpty()) {
                                            Text("Say something...", color = Color.White.copy(alpha = 0.6f), fontSize = 11.5.sp)
                                        }
                                        BasicTextField(
                                            value = commentInputText,
                                            onValueChange = { commentInputText = it },
                                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                                            cursorBrush = SolidColor(Color(0xFF00E5FF)),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (commentInputText.isNotBlank()) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Send,
                                                contentDescription = "Send",
                                                tint = Color(0xFF00E5FF),
                                                modifier = Modifier.size(16.dp).clickable {
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
        // 📑 সাইডবার ড্রয়ার (Speed / Playlist / Download)
        // =========================================================================
        AnimatedVisibility(
            visible = showSideDrawer && !isPiPActive,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(
                        if (sideDrawerType == "speed") {
                            if (isDeviceLandscape) 0.24f else 0.45f
                        } else {
                            if (isDeviceLandscape) 0.40f else 0.75f
                        }
                    ),
                color = Color.Black.copy(alpha = 0.75f)
            ) {
                if (sideDrawerType == "speed") {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 14.dp, horizontal = 16.dp),
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
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (sideDrawerType == "download") "Download (${selectedDownloadEpisodes.size})" else "Episodes (${episodes.size})",
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { showSideDrawer = false }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF8E95A5), modifier = Modifier.size(16.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val displayEps = episodes.ifEmpty {
                            (1..30).map { EpisodeDto(episodeNumber = it, isLocked = it > 1) }
                        }

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(5),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(displayEps, key = { it.episodeId }) { ep ->
                                val isSelected = ep.episodeNumber == episodeNumber
                                val isEpLocked = shouldLockEpisodes && ep.isLocked
                                val isSelectedForDl = selectedDownloadEpisodes.contains(ep)

                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (sideDrawerType == "download") {
                                            if (isSelectedForDl) Color(0xFF0F3B32) else Color(0xFF1E2433).copy(alpha = 0.70f)
                                        } else {
                                            if (isSelected) Color(0xFF0F3B32) else Color(0xFF1E2433).copy(alpha = 0.70f)
                                        }
                                    )
                                    .border(
                                        width = 1.2.dp,
                                        color = if ((sideDrawerType == "download" && isSelectedForDl) || (sideDrawerType == "playlist" && isSelected)) Color(0xFF00E676) else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp)
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
                                        EqualizerBarsIcon(modifier = Modifier.size(12.dp, 9.dp), tint = Color(0xFF00E676))
                                    } else {
                                        Text(
                                            text = ep.episodeNumber.toString(),
                                            color = if ((sideDrawerType == "download" && isSelectedForDl) || (sideDrawerType == "playlist" && isSelected)) Color(0xFF00E676) else Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    if (isEpLocked && !isSelected && sideDrawerType == "playlist") {
                                        Text(text = "VIP", color = GoldVip, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    if (sideDrawerType == "download") {
                        Spacer(modifier = Modifier.height(6.dp))
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
                                    Toast.makeText(context, "Downloading ${selectedDownloadEpisodes.size} episodes", Toast.LENGTH_SHORT).show()
                                    showSideDrawer = false
                                }
                            },
                            enabled = selectedDownloadEpisodes.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D26A)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(38.dp)
                        ) {
                            Text(
                                text = "Download Selected (${selectedDownloadEpisodes.size})",
                                color = Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
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
}

// 🎯 স্ক্রিনের ওপর ডান থেকে বামে কমেন্ট মসৃণভাবে ভাসিয়ে নেওয়ার কম্পোনেন্ট
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
