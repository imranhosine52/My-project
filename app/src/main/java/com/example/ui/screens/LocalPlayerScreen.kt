@file:OptIn(UnstableApi::class)

package com.example.ui.screens

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.model.LocalVideoItem
import com.example.util.LocalMediaScanner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private fun findActivity(context: Context): Activity? {
    var ctx = context
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun formatTime(millis: Long): String {
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

/**
 * ⚡ প্রিমিয়াম চিকন স্কিপ আইকন (১০ সেকেন্ড)
 */
@Composable
fun SleekSkipIcon(
    isForward: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    Box(
        modifier = modifier.size(42.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(34.dp)) {
            val strokeWidth = 1.6.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val arcSize = Size(diameter, diameter)
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

            if (isForward) {
                drawArc(
                    color = color,
                    startAngle = -60f,
                    sweepAngle = 290f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            } else {
                drawArc(
                    color = color,
                    startAngle = 240f,
                    sweepAngle = -290f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }
        Text(
            text = "10",
            color = color,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * ⚡ আল্ট্রা-স্লিম টাইমলাইন বার (৩ নং ছবির মতো)
 */
@Composable
fun SleekVideoTimeline(
    currentPositionMs: Long,
    totalDurationMs: Long,
    onSeekStarted: () -> Unit,
    onSeeking: (Long) -> Unit,
    onSeekFinished: (Long) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Color(0xFF00E5FF),
    inactiveColor: Color = Color.White.copy(alpha = 0.28f)
) {
    val progress = if (totalDurationMs > 0) {
        (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(totalDurationMs) {
                detectTapGestures { offset ->
                    val newProgress = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    val newTarget = (newProgress * totalDurationMs).toLong()
                    onSeekFinished(newTarget)
                }
            }
            .pointerInput(totalDurationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { onSeekStarted() },
                    onDragEnd = {
                        val currentTarget = (progress * totalDurationMs).toLong()
                        onSeekFinished(currentTarget)
                    },
                    onDragCancel = {
                        val currentTarget = (progress * totalDurationMs).toLong()
                        onSeekFinished(currentTarget)
                    },
                    onHorizontalDrag = { change, _ ->
                        val newProgress = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        val newTarget = (newProgress * totalDurationMs).toLong()
                        onSeeking(newTarget)
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(14.dp)) {
            val centerY = size.height / 2f
            val trackHeight = 2.8.dp.toPx()
            val thumbRadius = 5.2.dp.toPx()
            val trackWidth = size.width

            // ব্যাকগ্রাউন্ড লাইন
            drawLine(
                color = inactiveColor,
                start = Offset(0f, centerY),
                end = Offset(trackWidth, centerY),
                strokeWidth = trackHeight,
                cap = StrokeCap.Round
            )

            // অ্যাক্টিভ লাইন
            val activeEnd = trackWidth * progress
            if (activeEnd > 0) {
                drawLine(
                    color = activeColor,
                    start = Offset(0f, centerY),
                    end = Offset(activeEnd, centerY),
                    strokeWidth = trackHeight,
                    cap = StrokeCap.Round
                )
            }

            // থাম্ব
            drawCircle(
                color = activeColor,
                radius = thumbRadius,
                center = Offset(activeEnd.coerceIn(0f, trackWidth), centerY)
            )
        }
    }
}

// 🔔 নোটিফিকেশন বার হেল্পার
private const val NOTIFICATION_CHANNEL_ID = "media_playback_channel"
private const val NOTIFICATION_ID = 1001

private fun showAudioNotification(
    context: Context,
    title: String,
    isPlaying: Boolean
) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Audio Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Controls for background media playback"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    val pendingIntent = PendingIntent.getActivity(
        context, 0, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setContentTitle(title)
        .setContentText(if (isPlaying) "Playing Audio" else "Paused")
        .setContentIntent(pendingIntent)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setOnlyAlertOnce(true)
        .setOngoing(isPlaying)
        .build()

    try {
        notificationManager.notify(NOTIFICATION_ID, notification)
    } catch (_: Exception) {}
}

private fun hideAudioNotification(context: Context) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    try {
        notificationManager.cancel(NOTIFICATION_ID)
    } catch (_: Exception) {}
}

@Composable
fun LocalPlayerScreen(
    videoItem: LocalVideoItem,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { findActivity(context) }
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val scope = rememberCoroutineScope()

    val isInitiallyAudio = remember(videoItem) {
        videoItem.mimeType?.startsWith("audio") == true || videoItem.path.endsWith(".mp3", ignoreCase = true)
    }
    var isAudioMode by rememberSaveable { mutableStateOf(isInitiallyAudio) }

    var isControlsVisible by remember { mutableStateOf(true) }
    var isScreenLocked by rememberSaveable { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }

    var isUserSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableLongStateOf(0L) }

    var resizeModeIndex by rememberSaveable { mutableIntStateOf(1) }

    val speedOptions = remember { listOf(1.0f, 1.25f, 1.5f, 2.0f, 0.5f, 0.75f) }
    var currentSpeedIndex by rememberSaveable { mutableIntStateOf(0) }
    val playbackSpeed by remember { derivedStateOf { speedOptions[currentSpeedIndex] } }

    var brightnessLevel by remember {
        mutableFloatStateOf(activity?.window?.attributes?.screenBrightness?.takeIf { it > 0 } ?: 0.5f)
    }
    var showBrightnessOverlay by remember { mutableStateOf(false) }
    var volumeLevel by remember { mutableFloatStateOf(0.5f) }
    var showVolumeOverlay by remember { mutableStateOf(false) }

    // 💫 রিওয়াইন্ড ও ফরোয়ার্ড অ্যানিমেশন স্টেট
    var isRewindActive by remember { mutableStateOf(false) }
    var isForwardActive by remember { mutableStateOf(false) }
    val rewindRotation = remember { Animatable(0f) }
    val forwardRotation = remember { Animatable(0f) }

    val rewindAlpha by animateFloatAsState(
        targetValue = if (isRewindActive) 1f else 0f,
        animationSpec = tween(150),
        label = "rewindAlpha"
    )
    val forwardAlpha by animateFloatAsState(
        targetValue = if (isForwardActive) 1f else 0f,
        animationSpec = tween(150),
        label = "forwardAlpha"
    )

    // 🎵 ExoPlayer Engine
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
        }
    }

    LaunchedEffect(videoItem.id) {
        val lastPos = LocalMediaScanner.getLastPlaybackPosition(context, videoItem.id)
        val mediaItem = MediaItem.fromUri(videoItem.contentUri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        if (lastPos > 0) {
            exoPlayer.seekTo(lastPos)
        }
        exoPlayer.play()
    }

    LaunchedEffect(isAudioMode, isPlaying, videoItem.title) {
        if (isAudioMode) {
            showAudioNotification(context, videoItem.title, isPlaying)
        } else {
            hideAudioNotification(context)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (currentPositionMs > 1000) {
                LocalMediaScanner.saveLastPlaybackPosition(context, videoItem.id, currentPositionMs)
            }
            hideAudioNotification(context)
            exoPlayer.release()

            activity?.let { act ->
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                val window = act.window
                WindowCompat.setDecorFitsSystemWindows(window, true)
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    DisposableEffect(isAudioMode) {
        activity?.let { act ->
            val window = act.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)

            if (!isAudioMode) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
                WindowCompat.setDecorFitsSystemWindows(window, false)
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                WindowCompat.setDecorFitsSystemWindows(window, true)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(isPlaying, isUserSeeking) {
        while (isPlaying && !isUserSeeking) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
            delay(300L)
        }
    }

    LaunchedEffect(isControlsVisible, isPlaying, isScreenLocked) {
        if (isControlsVisible && isPlaying && !isScreenLocked && !isAudioMode) {
            delay(4000L)
            isControlsVisible = false
        }
    }

    fun handleSeek(seconds: Int) {
        val target = (exoPlayer.currentPosition + (seconds * 1000L)).coerceIn(0L, totalDurationMs.coerceAtLeast(1L))
        exoPlayer.seekTo(target)
        currentPositionMs = target

        scope.launch {
            if (seconds < 0) {
                isRewindActive = true
                rewindRotation.snapTo(0f)
                rewindRotation.animateTo(-360f, animationSpec = tween(400, easing = LinearEasing))
                delay(600)
                isRewindActive = false
            } else {
                isForwardActive = true
                forwardRotation.snapTo(0f)
                forwardRotation.animateTo(360f, animationSpec = tween(400, easing = LinearEasing))
                delay(600)
                isForwardActive = false
            }
        }
    }

    fun enterPiPMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                activity?.let { act ->
                    try {
                        isControlsVisible = false
                        val aspectRatio = when (resizeModeIndex) {
                            1 -> Rational(9, 16)
                            else -> Rational(16, 9)
                        }
                        val pipParams = PictureInPictureParams.Builder()
                            .setAspectRatio(aspectRatio)
                            .build()
                        act.enterPictureInPictureMode(pipParams)
                    } catch (_: Exception) {
                        Toast.makeText(context, "PiP not available on this device", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(context, "PiP is not supported", Toast.LENGTH_SHORT).show()
            }
        }
    }

    BackHandler {
        if (isScreenLocked) {
            isScreenLocked = false
        } else {
            onBackClick()
        }
    }

    // =========================================================================
    // 🎵 ১. ক্লিন অডিও প্লেয়ার মোড
    // =========================================================================
    if (isAudioMode) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF140F1D))
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Button(
                        onClick = { isAudioMode = false },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF281C38))
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Video Mode", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color(0xFF241930)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "Music Art",
                        tint = Color(0xFF5A4470),
                        modifier = Modifier.size(110.dp)
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = videoItem.title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    SleekVideoTimeline(
                        currentPositionMs = if (isUserSeeking) seekPosition else currentPositionMs,
                        totalDurationMs = totalDurationMs,
                        onSeekStarted = { isUserSeeking = true },
                        onSeeking = { seekPosition = it },
                        onSeekFinished = {
                            exoPlayer.seekTo(it)
                            currentPositionMs = it
                            isUserSeeking = false
                        },
                        activeColor = Color.White,
                        inactiveColor = Color.White.copy(alpha = 0.2f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatTime(if (isUserSeeking) seekPosition else currentPositionMs), color = Color.White.copy(alpha = 0.5f), fontSize = 11.5.sp)
                        Text(formatTime(totalDurationMs), color = Color.White.copy(alpha = 0.5f), fontSize = 11.5.sp)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { handleSeek(-10) }, modifier = Modifier.size(48.dp)) {
                        SleekSkipIcon(isForward = false, color = Color.White)
                    }

                    IconButton(
                        onClick = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() },
                        modifier = Modifier.size(68.dp).clip(CircleShape).background(Color.White)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color(0xFF16101E),
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    IconButton(onClick = { handleSeek(10) }, modifier = Modifier.size(48.dp)) {
                        SleekSkipIcon(isForward = true, color = Color.White)
                    }
                }
            }
        }
    } else {
        // =========================================================================
        // 🎬 ২. ভিডিও প্লেয়ার মোড
        // =========================================================================
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(isScreenLocked) {
                    detectTapGestures(
                        onTap = { isControlsVisible = !isControlsVisible },
                        onDoubleTap = { offset ->
                            if (!isScreenLocked) {
                                if (offset.x < size.width / 2) handleSeek(-10) else handleSeek(10)
                            }
                        }
                    )
                }
                .pointerInput(isScreenLocked) {
                    if (!isScreenLocked) {
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
                                val delta = -dragAmount / 550f

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
                        resizeMode = when (resizeModeIndex) {
                            1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            2 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    }
                },
                update = { view ->
                    view.resizeMode = when (resizeModeIndex) {
                        1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        2 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // ব্রাইটনেস HUD
            AnimatedVisibility(
                visible = showBrightnessOverlay,
                enter = fadeIn(animationSpec = tween(150)),
                exit = fadeOut(animationSpec = tween(200)),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 70.dp)
            ) {
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.BrightnessMedium, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Box(
                        modifier = Modifier.width(120.dp).height(2.5.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.35f))
                    ) {
                        Box(modifier = Modifier.fillMaxWidth(fraction = brightnessLevel.coerceIn(0f, 1f)).fillMaxHeight().background(Color(0xFF00E5FF)))
                    }
                }
            }

            // ভলিউম HUD
            AnimatedVisibility(
                visible = showVolumeOverlay,
                enter = fadeIn(animationSpec = tween(150)),
                exit = fadeOut(animationSpec = tween(200)),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 70.dp)
            ) {
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(if (volumeLevel == 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Box(
                        modifier = Modifier.width(120.dp).height(2.5.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.35f))
                    ) {
                        Box(modifier = Modifier.fillMaxWidth(fraction = volumeLevel.coerceIn(0f, 1f)).fillMaxHeight().background(Color(0xFF00E5FF)))
                    }
                }
            }

            // 🎮 কন্ট্রোলস ওভারলে
            AnimatedVisibility(
                visible = isControlsVisible,
                enter = fadeIn(animationSpec = tween(150)),
                exit = fadeOut(animationSpec = tween(200)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))) {
                    if (!isScreenLocked) {
                        // 🔝 ১. টপ বার
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)))
                                .statusBarsPadding()
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            // লাইন ১: Back + Title
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = onBackClick, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = videoItem.title,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // লাইন ২: ফাংশনাল আইকনসমূহ
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // ⚡ Speed
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.White.copy(alpha = 0.15f),
                                        modifier = Modifier
                                            .height(26.dp)
                                            .clickable {
                                                currentSpeedIndex = (currentSpeedIndex + 1) % speedOptions.size
                                                val newSpeed = speedOptions[currentSpeedIndex]
                                                exoPlayer.setPlaybackSpeed(newSpeed)
                                                Toast.makeText(context, "${newSpeed}X", Toast.LENGTH_SHORT).show()
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                            Text(
                                                text = if (playbackSpeed == 1.0f) "1X" else "${playbackSpeed}X",
                                                color = Color(0xFF00E5FF),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    // 📐 Crop
                                    IconButton(
                                        onClick = {
                                            resizeModeIndex = (resizeModeIndex + 1) % 3
                                            val modeName = when (resizeModeIndex) {
                                                1 -> "TikTok 9:16 Fullscreen"
                                                2 -> "100% Stretch"
                                                else -> "16:9 Fit"
                                            }
                                            Toast.makeText(context, modeName, Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(Icons.Outlined.CropFree, contentDescription = "Aspect Ratio", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }

                                    // 🎧 Audio Mode
                                    IconButton(onClick = { isAudioMode = true }, modifier = Modifier.size(30.dp)) {
                                        Icon(Icons.Default.Headphones, contentDescription = "Audio Mode", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }

                                    // 🔄 Rotate Screen
                                    IconButton(
                                        onClick = {
                                            activity?.let { act ->
                                                act.requestedOrientation = if (act.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                                                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                                } else {
                                                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(Icons.Default.ScreenRotation, contentDescription = "Rotate", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // 📺 TV Cast
                                    IconButton(
                                        onClick = {
                                            try {
                                                val intent = Intent(Settings.ACTION_CAST_SETTINGS).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(intent)
                                            } catch (_: Exception) {
                                                Toast.makeText(context, "Make sure TV and phone are on same Wi-Fi", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(Icons.Default.Cast, contentDescription = "Cast", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }

                                    // 🖼️ PiP Minimize বাটন
                                    IconButton(onClick = { enterPiPMode() }, modifier = Modifier.size(30.dp)) {
                                        Icon(Icons.Outlined.PictureInPictureAlt, contentDescription = "PiP", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }

                                    // 🔒 Lock Screen
                                    IconButton(onClick = { isScreenLocked = true; isControlsVisible = false }, modifier = Modifier.size(30.dp)) {
                                        Icon(Icons.Outlined.Lock, contentDescription = "Lock", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        // 🎯 ২. সেন্টার কন্ট্রোলস
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(48.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // -10s রিওয়াইন্ড বাটন
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "-10s",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .offset(y = (-32).dp)
                                        .alpha(rewindAlpha)
                                )

                                IconButton(
                                    onClick = { handleSeek(-10) },
                                    modifier = Modifier.size(46.dp).rotate(rewindRotation.value)
                                ) {
                                    SleekSkipIcon(isForward = false, color = Color.White)
                                }
                            }

                            // Play/Pause বাটন
                            IconButton(
                                onClick = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() },
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(48.dp)
                                )
                            }

                            // +10s ফরোয়ার্ড বাটন
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "+10s",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .offset(y = (-32).dp)
                                        .alpha(forwardAlpha)
                                )

                                IconButton(
                                    onClick = { handleSeek(10) },
                                    modifier = Modifier.size(46.dp).rotate(forwardRotation.value)
                                ) {
                                    SleekSkipIcon(isForward = true, color = Color.White)
                                }
                            }
                        }

                        // 🔻 ৩. আল্ট্রা-স্লিম টাইমলাইন
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
                                .navigationBarsPadding()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = formatTime(if (isUserSeeking) seekPosition else currentPositionMs),
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )

                            SleekVideoTimeline(
                                currentPositionMs = if (isUserSeeking) seekPosition else currentPositionMs,
                                totalDurationMs = totalDurationMs,
                                onSeekStarted = { isUserSeeking = true },
                                onSeeking = { seekPosition = it },
                                onSeekFinished = {
                                    exoPlayer.seekTo(it)
                                    currentPositionMs = it
                                    isUserSeeking = false
                                },
                                modifier = Modifier.weight(1f)
                            )

                            Text(
                                text = formatTime(totalDurationMs),
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )

                            IconButton(
                                onClick = { resizeModeIndex = (resizeModeIndex + 1) % 3 },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(Icons.Default.Fullscreen, contentDescription = "Resize", tint = Color.White, modifier = Modifier.size(19.dp))
                            }
                        }
                    }
                }
            }

            // 🔓 স্ক্রিন লক আনলক বাটন
            if (isScreenLocked) {
                IconButton(
                    onClick = { isScreenLocked = false; isControlsVisible = true },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.7f))
                ) {
                    Icon(Icons.Default.Lock, contentDescription = "Unlock", tint = Color(0xFFFF5252), modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}
