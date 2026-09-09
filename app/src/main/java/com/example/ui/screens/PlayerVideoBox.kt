@file:OptIn(UnstableApi::class)

package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.util.R2DownloadManager
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

/**
 * 🎬 PlayerVideoBox — High Performance Native Video Player Engine
 */
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
    onShareClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { findActivityFromContext(context) }
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val coroutineScope = rememberCoroutineScope()

    var isControlsVisible by remember { mutableStateOf(true) }
    var isScreenLocked by rememberSaveable { mutableStateOf(false) }

    // 🤏 ১. ইউটিউবের মতো আল্ট্রা-স্মুথ পিঞ্চ-টু-জুম স্কেল (Smooth Zoom Scale)
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var zoomOffset by remember { mutableStateOf(Offset.Zero) }

    val speedOptions = remember { listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f) }
    var currentSpeedIndex by rememberSaveable { mutableIntStateOf(2) } // 1.0x ডিফল্ট
    val currentSpeed = speedOptions[currentSpeedIndex]

    // ব্রাইটনেস ও ভলিউম
    var brightnessLevel by remember {
        mutableFloatStateOf(activity?.window?.attributes?.screenBrightness?.takeIf { it > 0 } ?: 0.5f)
    }
    var showBrightnessOverlay by remember { mutableStateOf(false) }
    var volumeLevel by remember { mutableFloatStateOf(0.5f) }
    var showVolumeOverlay by remember { mutableStateOf(false) }

    var isUserSeeking by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableLongStateOf(0L) }

    // বাফারিং / লোডিং ট্র্যাকার (কালো স্ক্রিন দূর করতে)
    var isBuffering by remember { mutableStateOf(true) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = (state == Player.STATE_BUFFERING)
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // ১০ সেকেন্ড স্কিপ রোটেশন এনিমেশন
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

    // ৪ সেকেন্ড পর অটোমেটিক কন্ট্রোলস লুকানো
    LaunchedEffect(isControlsVisible, isPlaying, isScreenLocked) {
        if (isControlsVisible && isPlaying && !isScreenLocked) {
            delay(4000L)
            isControlsVisible = false
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            // 🤏 ১. ইউটিউবের মতো আল্ট্রা-স্মুথ পিঞ্চ-টু-জুম জেসচার (Smooth Pinch to Zoom)
            .pointerInput(isScreenLocked) {
                if (!isScreenLocked) {
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
            // 👆 ২. ডাবল ট্যাপে জুম রিসেট ও ১০ সেকেন্ড স্কিপ
            .pointerInput(isScreenLocked) {
                detectTapGestures(
                    onTap = { isControlsVisible = !isControlsVisible },
                    onDoubleTap = { offset ->
                        if (!isScreenLocked) {
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
            // 🔆 ৩. বামে ব্রাইটনেস ও ডানে ভলিউম সোয়াইপ
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
        // 🎬 ExoPlayer সারফেস (স্মুথ জুমিং ও প্যানিং সহ)
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

        // 🔄 বাফারিং / লোডিং স্পিনার (কালো স্ক্রিন দূরীকরণ)
        if (isBuffering) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF00E5FF),
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(46.dp)
                    )
                    Text(
                        text = "Loading HD Stream...",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ব্রাইটনেস ওভারলে
        if (showBrightnessOverlay) {
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.75f), modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)) {
                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BrightnessMedium, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Brightness ${(brightnessLevel * 100).toInt()}%", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ভলিউম ওভারলে
        if (showVolumeOverlay) {
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.75f), modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)) {
                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (volumeLevel == 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Volume ${(volumeLevel * 100).toInt()}%", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // =========================================================================
        // 🌟 কাস্টম অন-স্ক্রিন কন্ট্রোলস ওভারলে
        // =========================================================================
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))) {
                if (!isScreenLocked) {
                    // 🔝 Top Bar: [<- Back] ও [Share 📤] বাটন
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = onBackClick, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }

                        // 📤 ওপরে ডান পাশে শেয়ার বাটন
                        IconButton(onClick = onShareClick, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }

                    // 🔓 শুধুমাত্র রোটেট/ল্যান্ডস্কেপ মোডে "Tap to Lock" আসবে
                    if (isDeviceLandscape) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 24.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable { isScreenLocked = true; isControlsVisible = false }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Outlined.LockOpen, contentDescription = "Lock", tint = Color.White, modifier = Modifier.size(16.dp))
                            Text("Tap to Lock", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    // ⏯️ সেন্ট্রাল কন্ট্রোলস (-10s, Play/Pause, +10s)
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(50.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("-10s", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(y = (-30).dp).alpha(rewindAlpha))
                            IconButton(onClick = { triggerSkip(-10) }, modifier = Modifier.size(46.dp).rotate(rewindRotation.value)) {
                                SleekSkipIconOnline(isForward = false, color = Color.White)
                            }
                        }

                        IconButton(onClick = onPlayPauseClick, modifier = Modifier.size(56.dp)) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        Box(contentAlignment = Alignment.Center) {
                            Text("+10s", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(y = (-30).dp).alpha(forwardAlpha))
                            IconButton(onClick = { triggerSkip(10) }, modifier = Modifier.size(46.dp).rotate(forwardRotation.value)) {
                                SleekSkipIconOnline(isForward = true, color = Color.White)
                            }
                        }
                    }

                    // ⏳ নিচে: [00:02] --টাইমলাইন-- [24:00] [1x (Speed)] [📥 R2 Download] [⛶ Rotate]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
                            .padding(start = 12.dp, end = 10.dp, bottom = 6.dp),
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

                        // ⚡ ১x স্পিড বাটন
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.18f),
                            modifier = Modifier
                                .height(24.dp)
                                .clickable {
                                    currentSpeedIndex = (currentSpeedIndex + 1) % speedOptions.size
                                    val newSpd = speedOptions[currentSpeedIndex]
                                    exoPlayer.setPlaybackSpeed(newSpd)
                                    Toast.makeText(context, "${newSpd}x Speed", Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 7.dp)) {
                                Text(
                                    text = if (currentSpeed == 1.0f) "1x" else "${currentSpeed}x",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // 📥 ১-ক্লিকে Cloudflare R2 MP4 সরাসরি ডাউনলোড বাটন (R2DownloadManager কানেক্টেড)
                        IconButton(
                            onClick = {
                                R2DownloadManager.startDownload(
                                    context = context,
                                    downloadUrl = downloadUrl,
                                    title = title,
                                    episodeNumber = episodeNumber,
                                    isMovie = (episodeNumber <= 1 && totalDurationMs > 3600000L)
                                )
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FileDownload,
                                contentDescription = "Download Video",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // ⛶ রোটেট / ফুলস্ক্রিন বাটন
                        IconButton(
                            onClick = onToggleFullscreen,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (isDeviceLandscape) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = "Rotate",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // স্ক্রিন লক অবস্থায় আনলক বাটন
        if (isScreenLocked) {
            IconButton(
                onClick = { isScreenLocked = false; isControlsVisible = true },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(20.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.75f))
            ) {
                Icon(Icons.Default.Lock, contentDescription = "Unlock", tint = Color(0xFFFF5252), modifier = Modifier.size(22.dp))
            }
        }
    }
}
