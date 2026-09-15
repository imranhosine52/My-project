@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ui.screens.chat.components

import android.app.Activity
import android.view.ViewGroup
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

private fun formatVideoTime(millis: Long): String {
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
 * 🎬 স্কিপ আইকন (-10s / +10s)
 */
@Composable
private fun SleekChatSkipIcon(
    isForward: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    Box(
        modifier = modifier.size(46.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(36.dp)) {
            val strokeWidth = 1.8.dp.toPx()
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
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * ⚡ ২ নম্বর ছবির হুবহু সায়ান ড্র্যাগেবল টাইমলাইন বার
 */
@Composable
private fun RealTimeInteractiveTimeline(
    currentPositionMs: Long,
    totalDurationMs: Long,
    onSeekStarted: () -> Unit,
    onSeeking: (Long) -> Unit,
    onSeekFinished: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (totalDurationMs > 0) {
        (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Box(
        modifier = modifier
            .height(32.dp)
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
        Canvas(modifier = Modifier.fillMaxWidth().height(16.dp)) {
            val centerY = size.height / 2f
            val trackHeight = 3.2.dp.toPx()
            val thumbRadius = 6.2.dp.toPx()
            val trackWidth = size.width

            // ব্যাকগ্রাউন্ড ধূসর ট্র্যাক
            drawLine(
                color = Color(0xFF6B7280).copy(alpha = 0.6f),
                start = Offset(0f, centerY),
                end = Offset(trackWidth, centerY),
                strokeWidth = trackHeight,
                cap = StrokeCap.Round
            )

            val activeEnd = trackWidth * progress
            if (activeEnd > 0) {
                // অ্যাক্টিভ সায়ান লাইন
                drawLine(
                    color = Color(0xFF00E5FF),
                    start = Offset(0f, centerY),
                    end = Offset(activeEnd, centerY),
                    strokeWidth = trackHeight,
                    cap = StrokeCap.Round
                )
            }

            // সায়ান রঙের গোল ডট (Thumb)
            drawCircle(
                color = Color(0xFF00E5FF),
                radius = thumbRadius,
                center = Offset(activeEnd.coerceIn(0f, trackWidth), centerY)
            )
        }
    }
}

/**
 * 🎬 সম্পূর্ণ ফুল-স্ক্রিন চ্যাট ভিডিও প্লেয়ার (২ নম্বর ছবির ডিজাইন)
 */
@Composable
fun ChatVideoPlayerDialog(
    videoUrl: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var seekPositionMs by remember { mutableLongStateOf(0L) }
    var areControlsVisible by remember { mutableStateOf(true) }
    var resizeModeIndex by remember { mutableIntStateOf(0) } // 0: FIT, 1: ZOOM

    val rewindRotation = remember { Animatable(0f) }
    val forwardRotation = remember { Animatable(0f) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            prepare()
            playWhenReady = true
        }
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
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // ⚡ রিয়েল-টাইম পজিশন ও টোটাল টাইম ট্র্যাকার (প্রতি ১০০ মিলিসেকেন্ডে আপডেট)
    LaunchedEffect(exoPlayer) {
        while (isActive) {
            if (!isUserSeeking) {
                currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                val duration = exoPlayer.duration
                if (duration > 0) {
                    totalDurationMs = duration
                }
            }
            delay(100L)
        }
    }

    // কন্ট্রোলস অটো-হাইড (৩.৫ সেকেন্ড পর)
    LaunchedEffect(areControlsVisible, isPlaying, isUserSeeking) {
        if (areControlsVisible && isPlaying && !isUserSeeking) {
            delay(3500L)
            areControlsVisible = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
                ?: (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                WindowCompat.setDecorFitsSystemWindows(window, false)
            }
            onDispose {}
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { areControlsVisible = !areControlsVisible }
                    )
                }
        ) {
            // 📺 ১. ভিডিও ফ্রেম
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = when (resizeModeIndex) {
                            1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }
                },
                update = { view ->
                    view.resizeMode = when (resizeModeIndex) {
                        1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // =========================================================================
            // 🔝 ২. টপ ক্লোজ বাটন (✕)
            // =========================================================================
            AnimatedVisibility(
                visible = areControlsVisible,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(180)),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(0.75f), Color.Transparent)))
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // =========================================================================
            // 🎯 ৩. সেন্ট্রাল স্কিপ ও প্লে কন্ট্রোলস
            // =========================================================================
            AnimatedVisibility(
                visible = areControlsVisible,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(180)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(46.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // -১০ সেকেন্ড
                    IconButton(
                        onClick = {
                            val target = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(target)
                            currentPositionMs = target
                            coroutineScope.launch {
                                rewindRotation.snapTo(0f)
                                rewindRotation.animateTo(-360f, animationSpec = tween(380, easing = LinearEasing))
                            }
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .rotate(rewindRotation.value)
                    ) {
                        SleekChatSkipIcon(isForward = false, color = Color.White)
                    }

                    // Play / Pause
                    IconButton(
                        onClick = {
                            if (exoPlayer.isPlaying) {
                                exoPlayer.pause()
                                isPlaying = false
                            } else {
                                exoPlayer.play()
                                isPlaying = true
                            }
                        },
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(52.dp)
                        )
                    }

                    // +১০ সেকেন্ড
                    IconButton(
                        onClick = {
                            val target = (exoPlayer.currentPosition + 10000L).coerceAtMost(exoPlayer.duration)
                            exoPlayer.seekTo(target)
                            currentPositionMs = target
                            coroutineScope.launch {
                                forwardRotation.snapTo(0f)
                                forwardRotation.animateTo(360f, animationSpec = tween(380, easing = LinearEasing))
                            }
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .rotate(forwardRotation.value)
                    ) {
                        SleekChatSkipIcon(isForward = true, color = Color.White)
                    }
                }
            }

            // =========================================================================
            // ⏳ ৪. ২ নম্বর ছবির হুবহু বটম টাইমলাইন বার (উপরে তোলা এবং এক সারিতে সাজানো)
            // =========================================================================
            AnimatedVisibility(
                visible = areControlsVisible,
                enter = fadeIn(tween(180)) + slideInVertically { it },
                exit = fadeOut(tween(180)) + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                val currentMs = if (isUserSeeking) seekPositionMs else currentPositionMs

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.70f),
                                    Color.Black.copy(alpha = 0.95f)
                                )
                            )
                        )
                        // 🎯 নিচের নেভিগেশন বার থেকে পর্যাপ্ত উপরে ওঠানো হয়েছে যাতে কোনোভাবেই কেটে না যায়
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 32.dp)
                ) {
                    // ২ নম্বর ছবির হুবহু একক অনুভূমিক সারি: [ 00:03 ] -----●----------------- [ 01:10 ] [ ⛶ ]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // ১. বর্তমান সময়
                        Text(
                            text = formatVideoTime(currentMs),
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // ২. মাঝের ইন্টারেক্টিভ ড্র্যাগেবল সায়ান টাইমলাইন
                        RealTimeInteractiveTimeline(
                            currentPositionMs = currentMs,
                            totalDurationMs = totalDurationMs,
                            onSeekStarted = { isUserSeeking = true },
                            onSeeking = { seekPositionMs = it },
                            onSeekFinished = { target ->
                                exoPlayer.seekTo(target)
                                currentPositionMs = target
                                isUserSeeking = false
                            },
                            modifier = Modifier.weight(1f)
                        )

                        // ৩. মোট সময়
                        Text(
                            text = formatVideoTime(totalDurationMs),
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // ৪. ফুলস্ক্রিন / অ্যাসপেক্ট রেশিও টগল বাটন (⛶)
                        IconButton(
                            onClick = {
                                resizeModeIndex = if (resizeModeIndex == 0) 1 else 0
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (resizeModeIndex == 1) Icons.Default.CropFree else Icons.Default.Fullscreen,
                                contentDescription = "Toggle Fullscreen",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // 🟢 ৫. কন্ট্রোলস হাইড থাকলেও নিচে সবসময় দৃশ্যমান হালকা প্রগ্রেস লাইন
            // =========================================================================
            if (!areControlsVisible) {
                val progressFraction = if (totalDurationMs > 0) {
                    (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                } else 0f

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(bottom = 16.dp, start = 14.dp, end = 14.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(Color.White.copy(alpha = 0.25f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progressFraction)
                            .background(Color(0xFF00E5FF))
                    )
                }
            }
        }
    }
}
