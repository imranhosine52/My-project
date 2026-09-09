@file:OptIn(UnstableApi::class)

package com.example.ui.screens

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Environment
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private fun findActivityFromContext(context: Context): Activity? {
    var current = context
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
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

@Composable
fun PlayerVideoBox(
    exoPlayer: ExoPlayer,
    title: String,
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { findActivityFromContext(context) }
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val coroutineScope = rememberCoroutineScope()

    var isControlsVisible by remember { mutableStateOf(true) }
    var isScreenLocked by rememberSaveable { mutableStateOf(false) }

    // YouTube-স্টাইল অ্যাসপেক্ট রেশিও (0: Fit, 1: Zoom to fill)
    var resizeModeIndex by rememberSaveable { mutableIntStateOf(0) }

    // সেটিংস ডায়ালগ স্টেট
    var showSettingsDialog by remember { mutableStateOf(false) }

    val speedOptions = remember { listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f) }
    var currentSpeed by rememberSaveable { mutableFloatStateOf(1.0f) }

    val qualityOptions = remember { listOf("Auto", "1080p Full HD", "720p HD", "480p SD", "360p") }
    var selectedQuality by rememberSaveable { mutableStateOf("1080p Full HD") }

    // ব্রাইটনেস ও ভলিউম
    var brightnessLevel by remember {
        mutableFloatStateOf(activity?.window?.attributes?.screenBrightness?.takeIf { it > 0 } ?: 0.5f)
    }
    var showBrightnessOverlay by remember { mutableStateOf(false) }
    var volumeLevel by remember { mutableFloatStateOf(0.5f) }
    var showVolumeOverlay by remember { mutableStateOf(false) }

    // টাইমলাইন স্ক্রাবিং
    var isUserSeeking by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableLongStateOf(0L) }

    // ডাবল ট্যাপ ১০ সেকেন্ড স্কিপ এনিমেশন
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

    // 📥 ডাউনলোড মেথড
    fun startDownload() {
        if (downloadUrl.isBlank()) {
            Toast.makeText(context, "Download link unavailable", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val fileName = "${title.replace(" ", "_")}_EP$episodeNumber.mp4"
            val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                setTitle("$title - EP $episodeNumber")
                setDescription("Downloading high quality MP4...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(context, "📥 Download started in background...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Download error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ৪ সেকেন্ড পর অটো কন্ট্রোল লুকানো
    LaunchedEffect(isControlsVisible, isPlaying, isScreenLocked) {
        if (isControlsVisible && isPlaying && !isScreenLocked) {
            delay(4000L)
            isControlsVisible = false
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            // 🤏 ১. ইউটিউব-স্টাইল Pinch to Zoom (দুই আঙুলে টেনে বড় ও ছোট করা)
            .pointerInput(isScreenLocked) {
                if (!isScreenLocked) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom > 1.12f && resizeModeIndex != 1) {
                            resizeModeIndex = 1 // Zoom to fill
                            Toast.makeText(context, "Zoom to fill", Toast.LENGTH_SHORT).show()
                        } else if (zoom < 0.88f && resizeModeIndex != 0) {
                            resizeModeIndex = 0 // Original Fit
                            Toast.makeText(context, "Fit to screen", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            // 👆 ২. ডাবল ট্যাপে ১০ সেকেন্ড স্কিপ এবং সিঙ্গেল ট্যাপে কন্ট্রোল দেখানো
            .pointerInput(isScreenLocked) {
                detectTapGestures(
                    onTap = { isControlsVisible = !isControlsVisible },
                    onDoubleTap = { offset ->
                        if (!isScreenLocked) {
                            if (offset.x < size.width / 2) triggerSkip(-10) else triggerSkip(10)
                        }
                    }
                )
            }
            // 🔆 ৩. বামে ব্রাইটনেস ও ডানে ভলিউম সোয়াইপ
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
        // Media3 ExoPlayer সারফেস
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    resizeMode = if (resizeModeIndex == 1) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                }
            },
            update = { view ->
                view.resizeMode = if (resizeModeIndex == 1) {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // ব্রাইটনেস ওভারলে
        AnimatedVisibility(
            visible = showBrightnessOverlay,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.75f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.BrightnessMedium, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text("Brightness ${(brightnessLevel * 100).toInt()}%", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ভলিউম ওভারলে
        AnimatedVisibility(
            visible = showVolumeOverlay,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.75f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(if (volumeLevel == 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text("Volume ${(volumeLevel * 100).toInt()}%", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // অন-স্ক্রিন কাস্টম কন্ট্রোলস
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))) {
                if (!isScreenLocked) {
                    // 🔝 Top Action Bar (টাইটেল রিমুভড, ডান পাশে ডাউনলোড ও লক বাটন)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // বাম পাশে শুধু ব্যাক বাটন
                        IconButton(onClick = onBackClick, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }

                        // ডান পাশে ডাউনলোড ও লক বাটন
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // 📥 ডাউনলোড বাটন
                            IconButton(
                                onClick = { startDownload() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Outlined.FileDownload, contentDescription = "Download", tint = Color.White, modifier = Modifier.size(22.dp))
                            }

                            // 🔒 স্ক্রিন লক বাটন
                            IconButton(
                                onClick = { isScreenLocked = true; isControlsVisible = false },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Outlined.Lock, contentDescription = "Lock", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // ⏯️ সেন্ট্রাল কন্ট্রোলস (Skip -10s, Play/Pause, Skip +10s)
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(44.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("-10s", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(y = (-30).dp).alpha(rewindAlpha))
                            IconButton(onClick = { triggerSkip(-10) }, modifier = Modifier.size(44.dp).rotate(rewindRotation.value)) {
                                SleekSkipIconOnline(isForward = false, color = Color.White)
                            }
                        }

                        IconButton(onClick = onPlayPauseClick, modifier = Modifier.size(54.dp)) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.size(46.dp)
                            )
                        }

                        Box(contentAlignment = Alignment.Center) {
                            Text("+10s", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(y = (-30).dp).alpha(forwardAlpha))
                            IconButton(onClick = { triggerSkip(10) }, modifier = Modifier.size(44.dp).rotate(forwardRotation.value)) {
                                SleekSkipIconOnline(isForward = true, color = Color.White)
                            }
                        }
                    }

                    // ⏳ বটম লাইন: [00:00] --টাইমলাইন-- [24:00] [⚙️ Settings] [🔄 Rotate]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
                            .padding(start = 12.dp, end = 6.dp, bottom = 4.dp, top = 4.dp),
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

                        // ⚙️ সেটিংস বাটন
                        IconButton(
                            onClick = { showSettingsDialog = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White, modifier = Modifier.size(19.dp))
                        }

                        // 🔄 রোটেট / ফুলস্ক্রিন বাটন
                        IconButton(
                            onClick = onToggleFullscreen,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (isDeviceLandscape) Icons.Default.ScreenRotation else Icons.Default.Fullscreen,
                                contentDescription = "Rotate Screen",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // স্ক্রিন লক অবস্থায় আনলক বাটন
        if (isScreenLocked) {
            IconButton(
                onClick = { isScreenLocked = false; isControlsVisible = true },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.7f))
            ) {
                Icon(Icons.Default.Lock, contentDescription = "Unlock", tint = Color(0xFFFF5252), modifier = Modifier.size(22.dp))
            }
        }

        // =========================================================================
        // ⚙️ সেটিংস ডায়ালগ (স্পিড ও কোয়ালিটি পরিবর্তনের পপআপ)
        // =========================================================================
        if (showSettingsDialog) {
            Dialog(onDismissRequest = { showSettingsDialog = false }) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131A26)),
                    border = BorderStroke(1.dp, Color(0xFF2B364A)),
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Video Settings", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF8E95A5), modifier = Modifier.size(20.dp).clickable { showSettingsDialog = false })
                        }

                        HorizontalDivider(color = Color(0xFF222B3D), thickness = 0.8.dp)

                        // ১. প্লেব্যাক স্পিড
                        Text("Playback Speed", color = Color(0xFF00E5FF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            speedOptions.forEach { speed ->
                                val isSelected = (currentSpeed == speed)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF1E2638),
                                    modifier = Modifier.clickable {
                                        currentSpeed = speed
                                        exoPlayer.setPlaybackSpeed(speed)
                                        showSettingsDialog = false
                                        Toast.makeText(context, "Speed: ${speed}x", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Text(
                                        text = if (speed == 1.0f) "1x" else "${speed}x",
                                        color = if (isSelected) Color.Black else Color.White,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        // ২. ভিডিও কোয়ালিটি
                        Text("Streaming Quality", color = Color(0xFF00E5FF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            qualityOptions.forEach { quality ->
                                val isSelected = (selectedQuality == quality)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Color(0xFF1D283A) else Color.Transparent)
                                        .clickable {
                                            selectedQuality = quality
                                            showSettingsDialog = false
                                            Toast.makeText(context, "Quality set to $quality", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(quality, color = if (isSelected) Color(0xFF00E5FF) else Color.White, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
