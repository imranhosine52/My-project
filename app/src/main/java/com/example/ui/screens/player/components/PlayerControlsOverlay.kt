package com.example.ui.screens.player.components

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.SleekOnlineTimeline
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
 * 🎮 লং ভিডিও প্লেয়ারের টপ, সেন্টার ও বটম কন্ট্রোল ওভারলে
 */
@Composable
fun PlayerControlsOverlay(
    isControlsVisible: Boolean,
    isScreenLocked: Boolean,
    isPlaying: Boolean,
    isMuted: Boolean,
    isDeviceLandscape: Boolean,
    currentPositionMs: Long,
    totalDurationMs: Long,
    bufferedPositionMs: Long,
    adCueFractions: List<Float>,
    isUserSeeking: Boolean,
    scrubPosition: Long,
    showLastSeenBanner: Boolean,
    lastSeenPosMs: Long,
    currentSpeed: Float,
    currentQualityLabel: String,
    currentAudioLabel: String,
    currentSubtitleLabel: String,
    hasMultipleAudio: Boolean,
    hasSubtitles: Boolean,
    isSubtitleActive: Boolean,
    onBackClick: () -> Unit,
    onShareClick: () -> Unit,
    onPiPClick: () -> Unit,
    onCastClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onMuteToggle: () -> Unit,
    onLockToggle: () -> Unit,
    onSeekStarted: () -> Unit,
    onSeeking: (Long) -> Unit,
    onSeekFinished: (Long) -> Unit,
    onLastSeenDismiss: () -> Unit,
    onLastSeenJump: (Long) -> Unit,
    onNextEpisode: () -> Unit,
    onSpeedClick: () -> Unit,
    onQualityClick: () -> Unit,
    onAudioClick: () -> Unit,
    onSubtitleClick: () -> Unit,
    onEpisodesClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {

        // =============================================================
        // 🔝 ১. টপ অ্যাকশন বার (কোনো এপিসোড সংখ্যা ছাড়া ক্লিন ব্যাক বাটন)
        // =============================================================
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconButton(onClick = onPiPClick, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.PictureInPictureAlt, contentDescription = "PiP", tint = Color.White, modifier = Modifier.size(19.dp))
                        }
                        IconButton(onClick = onCastClick, modifier = Modifier.size(32.dp)) {
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

        // =============================================================
        // ⏯️ ২. সেন্টার প্লে/পজ বাটন
        // =============================================================
        AnimatedVisibility(
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

        // =============================================================
        // 🎯 ৩. ডান পাশের ফ্লোটিং আইকন (কালো ব্যাকগ্রাউন্ড সম্পূর্ণ রিমুভড)
        // =============================================================
        if (isDeviceLandscape) {
            AnimatedVisibility(
                visible = isControlsVisible && !isScreenLocked,
                enter = slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(240)),
                exit = slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(240, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(200)),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(end = 14.dp)
                ) {
                    // ১. লক আইকন (কোনো ব্যাকগ্রাউন্ড ছাড়া ক্লিন ফ্লোটিং)
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

                    // ২. নেক্সট এপিসোড আইকন (কোনো ব্যাকগ্রাউন্ড ছাড়া ক্লিন ফ্লোটিং)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onNextEpisode() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next Episode",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }

        // =============================================================
        // 📺 ৪. বটম কন্ট্রোল বার (গ্লাস Jump Play ব্যানার ও টাইমলাইন)
        // =============================================================
        AnimatedVisibility(
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
                // =============================================================
                // 🌟 আধুনিক ফ্রস্টেড গ্লাস "Last Seen / Jump Play" ব্যানার
                // =============================================================
                if (showLastSeenBanner && lastSeenPosMs > 5000L) {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp, bottom = 6.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0x38FFFFFF),
                                        Color(0x22FFFFFF),
                                        Color(0x18000000)
                                    )
                                )
                            )
                            .border(
                                width = 0.8.dp,
                                color = Color(0x55FFFFFF),
                                shape = RoundedCornerShape(18.dp)
                            )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier
                                    .size(13.dp)
                                    .clickable { onLastSeenDismiss() }
                            )

                            Text(
                                text = "Last Seen ${formatTime(lastSeenPosMs)}",
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Text(
                                text = "Jump Play",
                                color = Color(0xFF00E5FF),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { onLastSeenJump(lastSeenPosMs) }
                            )
                        }
                    }
                }

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

                // স্লিক অনলাইন টাইমলাইন
                SleekOnlineTimeline(
                    currentPositionMs = if (isUserSeeking) scrubPosition else currentPositionMs,
                    totalDurationMs = totalDurationMs,
                    bufferedPositionMs = bufferedPositionMs,
                    adCueFractions = adCueFractions,
                    onSeekStarted = onSeekStarted,
                    onSeeking = onSeeking,
                    onSeekFinished = onSeekFinished,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                )

                // কন্ট্রোল আইকন ও ইনফো রো
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 1.dp, bottom = 3.dp, start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
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
                                .clickable { onMuteToggle() },
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
                                text = formatTime(if (isUserSeeking) scrubPosition else currentPositionMs),
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
                                text = formatTime(totalDurationMs),
                                color = Color.White.copy(alpha = 0.70f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        // স্পিড চিপ
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onSpeedClick() }
                                .padding(horizontal = 3.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Outlined.Speed, contentDescription = "Speed", tint = Color.White, modifier = Modifier.size(13.dp))
                            Text(text = if (currentSpeed == 1.0f) "1x" else "${currentSpeed}x", color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }

                        // কোয়ালিটি চিপ
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onQualityClick() }
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

                        // অডিও ল্যাঙ্গুয়েজ চিপ
                        if (hasMultipleAudio) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { onAudioClick() }
                                    .padding(horizontal = 3.dp, vertical = 2.dp)
                            ) {
                                MusicNoteDubbingIcon(tint = Color(0xFF00E5FF))
                                Text(text = currentAudioLabel, color = Color(0xFF00E5FF), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // সাবটাইটেল চিপ
                        if (hasSubtitles) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { onSubtitleClick() }
                                    .padding(horizontal = 3.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = currentSubtitleLabel,
                                    color = if (isSubtitleActive) Color(0xFF00E5FF) else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // এপিসোড ড্রয়ার আইকন
                        if (isDeviceLandscape) {
                            IconButton(
                                onClick = onEpisodesClick,
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Layers,
                                    contentDescription = "Episodes",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // ডাউনলোড বাটন
                        IconButton(
                            onClick = onDownloadClick,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FileDownload,
                                contentDescription = "Download",
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                        }

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
    }
}
