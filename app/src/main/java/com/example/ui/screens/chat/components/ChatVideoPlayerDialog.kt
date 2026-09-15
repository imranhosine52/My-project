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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
 * ⚡ প্রিমিয়াম ইন্টারেক্টিভ ড্র্যাগেবল টাইমলাইন (বাস্তব সময়ে প্লে ও মোট সময়ের সাথে নিখুঁত সিঙ্ক)
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
            .fillMaxWidth()
            .height(34.dp)
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
            val trackHeight = 3.5.dp.toPx()
            val thumbRadius = 6.5.dp.toPx()
            val trackWidth = size.width

            // ব্যাকগ্রাউন্ড ট্র্যাক
            drawLine(
                color = Color.White.copy(alpha = 0.30f),
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

            // সায়ান রঙের কন্ট্রোল থাম্ব
            drawCircle(
                color = Color(0xFF00E5FF),
                radius = thumbRadius,
                center = Offset(activeEnd.coerceIn(0f, trackWidth), centerY)
            )
        }
    }
}

/**
 * 🎬 সম্পূর্ণ ফুল-স্ক্রিন চ্যাট ভিডিও প্লেয়ার
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

    // ⚡ রিয়েল-টাইম পজিশন ও টোটাল টাইম ট্র্যাকার (প্রতি ১৫০ মিলিসেকেন্ডে স্মুথ আপডেট)
    LaunchedEffect(exoPlayer) {
        while (isActive) {
            if (!isUserSeeking) {
                currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                val duration = exoPlayer.duration
                if (duration > 0) {
                    totalDurationMs = duration
                }
            }
            delay(150L)
        }
    }

    // কন্ট্রোলস অটো-হাইড (৪ সেকেন্ড পর)
    LaunchedEffect(areControlsVisible, isPlaying, isUserSeeking) {
        if (areControlsVisible && isPlaying && !isUserSeeking) {
            delay(4000L)
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
        // স্ট্যাটাস বার ও নেভিগেশন বার ফুলস্ক্রিন ট্রান্সপারেন্ট করা
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
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
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
            // ⏳ ৪. ফিক্সড বটম টাইমলাইন ও রিয়েল-টাইম টাইমার বার (স্ক্রিনশটের সমাধান)
            // =========================================================================
            AnimatedVisibility(
                visible = areControlsVisible,
                enter = fadeIn(tween(180)) + slideInVertically { it / 2 },
                exit = fadeOut(tween(180)) + slideOutVertically { it / 2 },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                val currentMs = if (isUserSeeking) seekPositionMs else currentPositionMs

                Column(
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
                        // 🎯 ফোনের নিচের জেসচার লাইনের ঠিক উপরে নিরাপদ স্থানে রাখা হয়েছে
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // রিয়েল-টাইম টাইমার ডিসপ্লে: [ 00:04 ]        [ 00:32 ]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatVideoTime(currentMs),
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = formatVideoTime(totalDurationMs),
                            color = Color(0xFF00E5FF),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 🎯 ইন্টারেক্টিভ ড্র্যাগেবল টাইমলাইন (Seekbar)
                    RealTimeInteractiveTimeline(
                        currentPositionMs = currentMs,
                        totalDurationMs = totalDurationMs,
                        onSeekStarted = { isUserSeeking = true },
                        onSeeking = { seekPositionMs = it },
                        onSeekFinished = { target ->
                            exoPlayer.seekTo(target)
                            currentPositionMs = target
                            isUserSeeking = false
                        }
                    )
                }
            }

            // =========================================================================
            // 🟢 ৫. কন্ট্রোলস হাইড হলেও নিচে সবসময় দৃশ্যমান স্লিম প্রগ্রেস লাইন
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
                        .padding(bottom = 8.dp)
                        .height(3.dp)
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
