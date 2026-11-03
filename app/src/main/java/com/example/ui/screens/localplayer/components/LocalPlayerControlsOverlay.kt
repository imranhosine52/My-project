package com.example.ui.screens.localplayer.components

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

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
 * ⤢ ফুলস্ক্রিন ডায়াগোনাল আইকন
 */
@Composable
private fun DiagonalFullscreenIcon(color: Color = Color.White) {
    Canvas(modifier = Modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()

        drawLine(color, Offset(w * 0.14f, h * 0.44f), Offset(w * 0.14f, h * 0.14f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.14f, h * 0.14f), Offset(w * 0.44f, h * 0.14f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.14f, h * 0.14f), Offset(w * 0.40f, h * 0.40f), stroke, StrokeCap.Round)

        drawLine(color, Offset(w * 0.86f, h * 0.56f), Offset(w * 0.86f, h * 0.86f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.86f, h * 0.86f), Offset(w * 0.54f, h * 0.86f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.86f, h * 0.86f), Offset(w * 0.60f, h * 0.60f), stroke, StrokeCap.Round)
    }
}

/**
 * ⚡ আল্ট্রা-স্লিম ল্যাগ-মুক্ত লোকাল টাইমলাইন বার
 */
@Composable
fun SleekLocalTimeline(
    currentPositionMs: Long,
    totalDurationMs: Long,
    bufferedPositionMs: Long = 0L,
    onSeekStarted: () -> Unit,
    onSeeking: (Long) -> Unit,
    onSeekFinished: (Long) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Color(0xFF00E5FF),
    bufferedColor: Color = Color.White.copy(alpha = 0.35f),
    inactiveColor: Color = Color.White.copy(alpha = 0.16f)
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(-1f) }

    val currentFraction = if (totalDurationMs > 0) {
        (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val effectiveProgress = if (isDragging && dragFraction in 0f..1f) dragFraction else currentFraction

    val bufferedProgress = if (totalDurationMs > 0) {
        (bufferedPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(26.dp)
            .pointerInput(totalDurationMs) {
                detectTapGestures { offset ->
                    if (totalDurationMs > 0) {
                        val tapFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        val targetMs = (tapFraction * totalDurationMs).toLong()
                        onSeeking(targetMs)
                        onSeekFinished(targetMs)
                    }
                }
            }
            .pointerInput(totalDurationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        onSeekStarted()
                        if (totalDurationMs > 0) {
                            val startFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            dragFraction = startFraction
                            onSeeking((startFraction * totalDurationMs).toLong())
                        }
                    },
                    onDragEnd = {
                        if (isDragging && dragFraction in 0f..1f && totalDurationMs > 0) {
                            val finalTarget = (dragFraction * totalDurationMs).toLong()
                            onSeeking(finalTarget)
                            onSeekFinished(finalTarget)
                        }
                        isDragging = false
                        dragFraction = -1f
                    },
                    onDragCancel = {
                        if (isDragging && dragFraction in 0f..1f && totalDurationMs > 0) {
                            val finalTarget = (dragFraction * totalDurationMs).toLong()
                            onSeekFinished(finalTarget)
                        }
                        isDragging = false
                        dragFraction = -1f
                    },
                    onHorizontalDrag = { change, _ ->
                        if (totalDurationMs > 0) {
                            val newFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            dragFraction = newFraction
                            val newTarget = (newFraction * totalDurationMs).toLong()
                            onSeeking(newTarget)
                        }
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(10.dp)) {
            val centerY = size.height / 2f
            val trackHeight = 2.0.dp.toPx()
            val thumbRadius = if (isDragging) 5.dp.toPx() else 3.8.dp.toPx()
            val trackWidth = size.width

            // ব্যাকগ্রাউন্ড ট্র্যাক
            drawLine(
                color = inactiveColor,
                start = Offset(0f, centerY),
                end = Offset(trackWidth, centerY),
                strokeWidth = trackHeight,
                cap = StrokeCap.Round
            )

            // বাফার্ড ট্র্যাক
            val bufferedEnd = trackWidth * bufferedProgress
            if (bufferedEnd > 0) {
                drawLine(
                    color = bufferedColor,
                    start = Offset(0f, centerY),
                    end = Offset(bufferedEnd.coerceIn(0f, trackWidth), centerY),
                    strokeWidth = trackHeight,
                    cap = StrokeCap.Round
                )
            }

            // প্রোগ্রেস ট্র্যাক
            val activeEnd = trackWidth * effectiveProgress
            if (activeEnd > 0) {
                drawLine(
                    color = activeColor,
                    start = Offset(0f, centerY),
                    end = Offset(activeEnd.coerceIn(0f, trackWidth), centerY),
                    strokeWidth = trackHeight,
                    cap = StrokeCap.Round
                )
            }

            // মুভিং থাম্ব
            drawCircle(
                color = activeColor,
                radius = thumbRadius,
                center = Offset(activeEnd.coerceIn(0f, trackWidth), centerY)
            )
        }
    }
}

/**
 * 🎮 লোকাল ভিডিও প্লেয়ারের সম্পূর্ণ কন্ট্রোল ওভারলে
 */
@Composable
fun LocalPlayerControlsOverlay(
    isControlsVisible: Boolean,
    isScreenLocked: Boolean,
    isPlaying: Boolean,
    isMuted: Boolean,
    isLandscape: Boolean,
    videoTitle: String,
    currentPositionMs: Long,
    totalDurationMs: Long,
    bufferedPositionMs: Long,
    isUserSeeking: Boolean,
    scrubPosition: Long,
    currentSpeed: Float,
    currentAudioLabel: String,
    currentSubtitleLabel: String,
    hasMultipleAudio: Boolean,
    hasSubtitles: Boolean,
    isSubtitleActive: Boolean,
    onBackClick: () -> Unit,
    onPiPClick: () -> Unit,
    onCastClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onMuteToggle: () -> Unit,
    onLockToggle: () -> Unit,
    onSeekStarted: () -> Unit,
    onSeeking: (Long) -> Unit,
    onSeekFinished: (Long) -> Unit,
    onNextVideoClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onAudioClick: () -> Unit,
    onSubtitleClick: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {

        // =============================================================
        // 🔝 ১. টপ বার (পোর্ট্রেটে টাইটেল হাইড থাকবে, ফুলস্ক্রিনে দেখাবে)
        // =============================================================
        AnimatedVisibility(
            visible = isControlsVisible && !isScreenLocked,
            enter = slideInVertically(initialOffsetY = { -it }, animationSpec = tween(220)) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(220)) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)))
                    .then(if (isLandscape) Modifier.statusBarsPadding() else Modifier)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(onClick = onBackClick, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    // 🔥 লম্বা প্লেয়ারে টাইটেল থাকবে না, শুধু ফুলস্ক্রিন (Landscape)-এ দেখাবে
                    if (isLandscape) {
                        Text(
                            text = videoTitle,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onPiPClick, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.PictureInPictureAlt, contentDescription = "PiP", tint = Color.White, modifier = Modifier.size(19.dp))
                    }
                    IconButton(onClick = onCastClick, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Cast, contentDescription = "Cast to TV", tint = Color.White, modifier = Modifier.size(19.dp))
                    }
                }
            }
        }

        // =============================================================
        // ⏯️ ২. সেন্টার প্লে/পজ বাটন
        // =============================================================
        AnimatedVisibility(
            visible = isControlsVisible && !isScreenLocked,
            enter = fadeIn(animationSpec = tween(180)) + scaleIn(initialScale = 0.85f),
            exit = fadeOut(animationSpec = tween(180)) + scaleOut(targetScale = 0.85f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            IconButton(
                onClick = onPlayPauseClick,
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        // =============================================================
        // 🎯 ৩. ডান পাশের ফ্লোটিং বাটন (স্ক্রিন লক ও পরবর্তী ভিডিও ⏭️ বাটন)
        // 🔥 লম্বা প্লেয়ারে সম্পূর্ণ বন্ধ থাকবে, শুধু ফুলস্ক্রিন (Landscape)-এ দেখাবে
        // =============================================================
        AnimatedVisibility(
            visible = isControlsVisible && !isScreenLocked && isLandscape,
            enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(240, easing = FastOutSlowInEasing)) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(220, easing = FastOutSlowInEasing)) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(end = 14.dp)
            ) {
                // স্ক্রিন লক বাটন
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onLockToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LockOpen,
                        contentDescription = "Lock Screen",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // পরবর্তী ভিডিও বাটন (Next Video Button)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onNextVideoClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next Video",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        // =============================================================
        // 📺 ৪. বটম কন্ট্রোল বার (৩ নম্বর ছবির স্টাইলে স্পিডোমিটার ও অডিও আইকন)
        // =============================================================
        AnimatedVisibility(
            visible = isControlsVisible && !isScreenLocked,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(220)) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(220)) + fadeOut(),
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
                    .then(if (isLandscape) Modifier.navigationBarsPadding() else Modifier)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                // সিকিং টাইম প্রিভিউ ব্যাজ
                if (isUserSeeking && totalDurationMs > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 2.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.Black.copy(alpha = 0.88f),
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            Text(
                                text = "${formatTime(scrubPosition)} / ${formatTime(totalDurationMs)}",
                                color = Color.White,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                            )
                        }
                    }
                }

                // টাইমলাইন
                SleekLocalTimeline(
                    currentPositionMs = if (isUserSeeking) scrubPosition else currentPositionMs,
                    totalDurationMs = totalDurationMs,
                    bufferedPositionMs = bufferedPositionMs,
                    onSeekStarted = onSeekStarted,
                    onSeeking = onSeeking,
                    onSeekFinished = onSeekFinished,
                    modifier = Modifier.fillMaxWidth().height(10.dp)
                )

                // কন্ট্রোল বার রো
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 4.dp, start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // বাম পাশে: প্লে/পজ, মিউট, টাইম
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = onPlayPauseClick, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        IconButton(onClick = onMuteToggle, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        Text(
                            text = "${formatTime(if (isUserSeeking) scrubPosition else currentPositionMs)} / ${formatTime(totalDurationMs)}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // ডান পাশে: স্পিড, অডিও চিপ, সাবটাইটেল এবং ফুলস্ক্রিন
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // ⏱️ ৩ নম্বর ছবির মতো স্পিডোমিটার আইকন সহ স্পিড চিপ
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onSpeedClick() }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Playback Speed",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (currentSpeed == 1.0f) "1x" else "${currentSpeed}x",
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // 🎵 ৩ নম্বর ছবির মতো সায়ান মিউজিক নোট সহ অডিও ট্র্যাক চিপ
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onAudioClick() }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = "Audio Track",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = currentAudioLabel,
                                color = Color(0xFF00E5FF),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // 💬 সাবটাইটেল (CC) চিপ
                        Text(
                            text = currentSubtitleLabel,
                            color = if (isSubtitleActive) Color(0xFF00E5FF) else Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onSubtitleClick() }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )

                        // ❌ বটম বারের প্লেলিস্ট আইকন সম্পূর্ণরূপে অপসারিত

                        // ফুলস্ক্রিন টগল
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

        // =============================================================
        // 🔒 ৫. স্ক্রিন লক অবস্থায় আনলক বাটন
        // =============================================================
        if (isScreenLocked) {
            IconButton(
                onClick = onLockToggle,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.70f))
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Unlock",
                    tint = Color(0xFFFF5252),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
