@file:OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.model.LocalVideoItem
import com.example.util.LocalMediaScanner
import kotlinx.coroutines.delay
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

@Composable
fun LocalPlayerScreen(
    videoItem: LocalVideoItem,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { findActivity(context) }
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var isControlsVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }

    // 0: FIT, 1: ZOOM / CROP, 2: STRETCH
    var resizeModeIndex by rememberSaveable { mutableIntStateOf(0) }

    // Gesture HUD Overlays
    var brightnessLevel by remember { mutableFloatStateOf(0.5f) }
    var showBrightnessOverlay by remember { mutableStateOf(false) }
    var volumeLevel by remember { mutableFloatStateOf(0.5f) }
    var showVolumeOverlay by remember { mutableStateOf(false) }

    // ১০ সেকেন্ড স্কিপ অ্যানিমেশন স্টেট
    var skipFeedbackNotice by remember { mutableStateOf<String?>(null) }
    var skipTriggerCount by remember { mutableIntStateOf(0) }

    // ExoPlayer ইঞ্জিন
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
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

    DisposableEffect(Unit) {
        activity?.let { act ->
            val window = act.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            WindowCompat.setDecorFitsSystemWindows(window, false)
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        onDispose {
            if (currentPositionMs > 1000) {
                LocalMediaScanner.saveLastPlaybackPosition(context, videoItem.id, currentPositionMs)
            }
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

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
            delay(300L)
        }
    }

    LaunchedEffect(isControlsVisible, isPlaying) {
        if (isControlsVisible && isPlaying) {
            delay(4000L)
            isControlsVisible = false
        }
    }

    // ১০ সেকেন্ড এগিয়ে বা পিছিয়ে নেওয়ার হেলপার
    fun performSeekBy(seconds: Int) {
        val target = (exoPlayer.currentPosition + (seconds * 1000L)).coerceIn(0L, totalDurationMs.coerceAtLeast(1L))
        exoPlayer.seekTo(target)
        currentPositionMs = target
        skipFeedbackNotice = if (seconds > 0) "+${seconds}s" else "${seconds}s"
        skipTriggerCount++
    }

    LaunchedEffect(skipTriggerCount) {
        if (skipTriggerCount > 0) {
            delay(900L)
            skipFeedbackNotice = null
        }
    }

    BackHandler {
        onBackClick()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { isControlsVisible = !isControlsVisible },
                    onDoubleTap = { offset ->
                        val screenWidth = size.width
                        if (offset.x < screenWidth / 2) {
                            performSeekBy(-10)
                        } else {
                            performSeekBy(10)
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        val screenWidth = size.width
                        if (offset.x < screenWidth / 2) {
                            showBrightnessOverlay = true
                        } else {
                            showVolumeOverlay = true
                        }
                    },
                    onDragEnd = {
                        showBrightnessOverlay = false
                        showVolumeOverlay = false
                    },
                    onVerticalDrag = { change, dragAmount ->
                        val screenWidth = size.width
                        val isLeft = change.position.x < screenWidth / 2
                        val delta = -dragAmount / 600f

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
    ) {
        // 🎬 ExoPlayer সারফেস ভিউ
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

        // ☀️ ব্রাইটনেস HUD
        AnimatedVisibility(
            visible = showBrightnessOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 48.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier.padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.BrightnessMedium, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(28.dp))
                    Text("${(brightnessLevel * 100).toInt()}%", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 🔊 ভলিউম HUD
        AnimatedVisibility(
            visible = showVolumeOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 48.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier.padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (volumeLevel == 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(28.dp)
                    )
                    Text("${(volumeLevel * 100).toInt()}%", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 🔟 ১০ সেকেন্ড স্কিপিং ফ্ল্যাটিং ব্যাজ (অ্যানিমেশন সহ)
        AnimatedVisibility(
            visible = skipFeedbackNotice != null,
            enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = 0.85f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (skipFeedbackNotice?.startsWith("+") == true) Icons.Default.Forward10 else Icons.Default.Replay10,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(26.dp)
                    )
                    Text(
                        text = skipFeedbackNotice ?: "",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        // =========================================================================
        // 🎮 স্ক্রিনশটের হুবহু প্লেয়ার কন্ট্রোল ওভারলে (Controls Overlay)
        // =========================================================================
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(250)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
            ) {
                // 🔝 Top Bar: [< Back] ────────────────────────── [Cast Icon]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = { resizeModeIndex = (resizeModeIndex + 1) % 3 },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cast,
                            contentDescription = "Cast",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // 🎯 Center Controls: [ ↺ 10 ]     [ ⏸ / ▶ ]     [ ↻ 10 ]
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.65f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // ↺ ১০ সেকেন্ড রিওয়াইন্ড বাটন (স্ক্রিনশটের স্টাইল)
                    IconButton(
                        onClick = { performSeekBy(-10) },
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "Rewind 10 seconds",
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }

                    // ⏸️ / ▶️ বড় প্লে-পজ বাটন (স্ক্রিনশটের মতো বড় ও স্পষ্ট)
                    IconButton(
                        onClick = {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(56.dp)
                        )
                    }

                    // ↻ ১০ সেকেন্ড ফরোয়ার্ড বাটন (স্ক্রিনশটের স্টাইল)
                    IconButton(
                        onClick = { performSeekBy(10) },
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "Forward 10 seconds",
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                // 🔻 Bottom Bar (স্ক্রিনশটের হুবহু লেআউট):
                // [ ⏸ / ▶ ] [ 00:14 ] ──────●────── [ 00:41 ] [ ⛶ / Aspect ]
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // বামে ছোট Play/Pause টগল
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .size(24.dp)
                                .clickable {
                                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                }
                        )

                        // বর্তমান সময় (00:14)
                        Text(
                            text = formatTime(currentPositionMs),
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        )

                        // ──────●────── সায়ান/ব্লু কালারের স্মুথ স্লাইডার বার (স্ক্রিনশটের মতো)
                        Slider(
                            value = if (totalDurationMs > 0) currentPositionMs.toFloat() else 0f,
                            onValueChange = { newPos ->
                                currentPositionMs = newPos.toLong()
                                exoPlayer.seekTo(currentPositionMs)
                            },
                            valueRange = 0f..(totalDurationMs.toFloat().coerceAtLeast(1f)),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00E5FF),
                                activeTrackColor = Color(0xFF00E5FF),
                                inactiveTrackColor = Color.White.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(20.dp)
                        )

                        // মোট সময় (00:41)
                        Text(
                            text = formatTime(totalDurationMs),
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        )

                        // অ্যাসপেক্ট রেশিও বাটন (Crop / Fit)
                        Icon(
                            imageVector = Icons.Outlined.AspectRatio,
                            contentDescription = "Aspect Ratio",
                            tint = Color.White,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable {
                                    resizeModeIndex = (resizeModeIndex + 1) % 3
                                }
                        )

                        // ফুলস্ক্রিন / সাইজ বাটন
                        Icon(
                            imageVector = when (resizeModeIndex) {
                                1 -> Icons.Default.CropFree
                                2 -> Icons.Default.FitScreen
                                else -> Icons.Default.Fullscreen
                            },
                            contentDescription = "Fullscreen",
                            tint = Color.White,
                            modifier = Modifier
                                .size(22.dp)
                                .clickable {
                                    resizeModeIndex = (resizeModeIndex + 1) % 3
                                }
                        )
                    }
                }
            }
        }
    }
}
