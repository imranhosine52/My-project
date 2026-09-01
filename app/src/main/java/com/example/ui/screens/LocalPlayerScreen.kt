@file:OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.app.Activity
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
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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

    val isInitiallyAudio = remember(videoItem) {
        videoItem.mimeType?.startsWith("audio") == true || videoItem.path.endsWith(".mp3", ignoreCase = true)
    }
    var isAudioMode by rememberSaveable { mutableStateOf(isInitiallyAudio) }

    var isControlsVisible by remember { mutableStateOf(true) }
    var isScreenLocked by rememberSaveable { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }

    // স্লাইডার ড্র্যাগিং স্মুথ করার জন্য স্টেট
    var isUserSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableFloatStateOf(0f) }

    // 0: FIT (16:9), 1: ZOOM (TikTok 9:16 Crop), 2: STRETCH
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

    // 🎵 ExoPlayer ইঞ্জিন (অডিও ফোকাস সহ)
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

    // ⚡ গ্লোবাল লাইফসাইকেল (স্ক্রিন বন্ধ হলেই কেবল প্লেয়ার রিলিজ হবে)
    DisposableEffect(Unit) {
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

    // 🔄 ওরিয়েন্টেশন ও স্ট্যাটাস বার নিয়ন্ত্রণ
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

    fun performSeekBy(seconds: Int) {
        val target = (exoPlayer.currentPosition + (seconds * 1000L)).coerceIn(0L, totalDurationMs.coerceAtLeast(1L))
        exoPlayer.seekTo(target)
        currentPositionMs = target
    }

    fun enterPiPMode() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                    activity?.let { act ->
                        val aspectRatio = if (resizeModeIndex == 1) Rational(9, 16) else Rational(16, 9)
                        val params = PictureInPictureParams.Builder().setAspectRatio(aspectRatio).build()
                        isControlsVisible = false
                        act.enterPictureInPictureMode(params)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    BackHandler {
        if (isScreenLocked) {
            isScreenLocked = false
        } else {
            onBackClick()
        }
    }

    // =========================================================================
    // 🎵 ১. অডিও প্লেয়ার মোড (স্ক্রিনশট ১)
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
                // Top Action Bar
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

                // Center Music Art Box
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

                // Track Title + Sleek Slider
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = videoItem.title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Slider(
                        value = if (isUserSeeking) seekPosition else if (totalDurationMs > 0) currentPositionMs.toFloat() else 0f,
                        onValueChange = { newPos ->
                            isUserSeeking = true
                            seekPosition = newPos
                        },
                        onValueChangeFinished = {
                            exoPlayer.seekTo(seekPosition.toLong())
                            currentPositionMs = seekPosition.toLong()
                            isUserSeeking = false
                        },
                        valueRange = 0f..(totalDurationMs.toFloat().coerceAtLeast(1f)),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.fillMaxWidth().height(16.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatTime(if (isUserSeeking) seekPosition.toLong() else currentPositionMs), color = Color.White.copy(alpha = 0.5f), fontSize = 11.5.sp)
                        Text(formatTime(totalDurationMs), color = Color.White.copy(alpha = 0.5f), fontSize = 11.5.sp)
                    }
                }

                // Controls: [ -10s ]   [ ▶ / ⏸ ]   [ +10s ]
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { performSeekBy(-10) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Replay10, contentDescription = "Prev 10s", tint = Color.White, modifier = Modifier.size(34.dp))
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

                    IconButton(onClick = { performSeekBy(10) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Forward10, contentDescription = "Next 10s", tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                }
            }
        }
    } else {
        // =========================================================================
        // 🎬 ২. ভিডিও প্লেয়ার মোড (স্ক্রিনশট ২ ও ৩ এর রিকোয়ারমেন্ট অনুযায়ী)
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
                                if (offset.x < size.width / 2) performSeekBy(-10) else performSeekBy(10)
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

            // ☀️ ব্রাইটনেস লেভেল ওভারলে
            AnimatedVisibility(
                visible = showBrightnessOverlay,
                enter = fadeIn(animationSpec = tween(150)),
                exit = fadeOut(animationSpec = tween(200)),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp)
            ) {
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.BrightnessMedium, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Box(
                        modifier = Modifier.width(120.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.35f))
                    ) {
                        Box(modifier = Modifier.fillMaxWidth(fraction = brightnessLevel.coerceIn(0f, 1f)).fillMaxHeight().background(Color(0xFF00E5FF)))
                    }
                }
            }

            // 🔊 ভলিউম লেভেল ওভারলে
            AnimatedVisibility(
                visible = showVolumeOverlay,
                enter = fadeIn(animationSpec = tween(150)),
                exit = fadeOut(animationSpec = tween(200)),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp)
            ) {
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(if (volumeLevel == 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Box(
                        modifier = Modifier.width(120.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.35f))
                    ) {
                        Box(modifier = Modifier.fillMaxWidth(fraction = volumeLevel.coerceIn(0f, 1f)).fillMaxHeight().background(Color(0xFF00E5FF)))
                    }
                }
            }

            // 🎮 প্লেয়ার কন্ট্রোলস ওভারলে
            AnimatedVisibility(
                visible = isControlsVisible,
                enter = fadeIn(animationSpec = tween(150)),
                exit = fadeOut(animationSpec = tween(200)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))) {
                    if (!isScreenLocked) {
                        // 🔝 ১. একক সরলরেখায় টপ বার (সব অপশন একই লাইনে)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)))
                                .statusBarsPadding()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Back Button
                            IconButton(onClick = onBackClick, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }

                            // Video Title
                            Text(
                                text = videoItem.title,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                            )

                            // 🔘 ডানদিকের অ্যাকশন বাটনসমূহ (এক সারিতে)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // ⚡ স্পিড কন্ট্রোল
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.15f),
                                    modifier = Modifier
                                        .height(28.dp)
                                        .clickable {
                                            currentSpeedIndex = (currentSpeedIndex + 1) % speedOptions.size
                                            val newSpeed = speedOptions[currentSpeedIndex]
                                            exoPlayer.setPlaybackSpeed(newSpeed)
                                            Toast.makeText(context, "${newSpeed}X", Toast.LENGTH_SHORT).show()
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 7.dp)) {
                                        Text(
                                            text = if (playbackSpeed == 1.0f) "1X" else "${playbackSpeed}X",
                                            color = Color(0xFF00E5FF),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // 📐 ক্রপ মোড (Aspect Ratio)
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
                                    Icon(Icons.Outlined.CropFree, contentDescription = "Aspect Ratio", tint = Color.White, modifier = Modifier.size(17.dp))
                                }

                                // 🎧 অডিও মোড
                                IconButton(onClick = { isAudioMode = true }, modifier = Modifier.size(30.dp)) {
                                    Icon(Icons.Default.Headphones, contentDescription = "Audio Mode", tint = Color.White, modifier = Modifier.size(17.dp))
                                }

                                // 🔄 রোটেট স্ক্রিন
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
                                    Icon(Icons.Default.ScreenRotation, contentDescription = "Rotate", tint = Color.White, modifier = Modifier.size(17.dp))
                                }

                                // 📺 কাস্ট অপশন
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
                                    Icon(Icons.Default.Cast, contentDescription = "Cast", tint = Color.White, modifier = Modifier.size(17.dp))
                                }

                                // 🖼️ PiP বাটন (উপরে স্থানান্তরিত)
                                IconButton(onClick = { enterPiPMode() }, modifier = Modifier.size(30.dp)) {
                                    Icon(Icons.Outlined.PictureInPictureAlt, contentDescription = "PiP", tint = Color.White, modifier = Modifier.size(17.dp))
                                }

                                // 🔒 স্ক্রিন লক বাটন (উপরে স্থানান্তরিত)
                                IconButton(onClick = { isScreenLocked = true; isControlsVisible = false }, modifier = Modifier.size(30.dp)) {
                                    Icon(Icons.Outlined.Lock, contentDescription = "Lock", tint = Color.White, modifier = Modifier.size(17.dp))
                                }
                            }
                        }

                        // 🎯 ২. স্ক্রিনের সেন্টারে কন্ট্রোলস (ছবি ৩ এর মতো)
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(46.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // -10s রিওয়াইন্ড বাটন
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { performSeekBy(-10) }
                            ) {
                                Text("-10s", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(38.dp))
                            }

                            // বড় Play/Pause বাটন
                            IconButton(
                                onClick = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() },
                                modifier = Modifier.size(54.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(46.dp)
                                )
                            }

                            // +10s ফরোয়ার্ড বাটন
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { performSeekBy(10) }
                            ) {
                                Text("+10s", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(38.dp))
                            }
                        }

                        // 🔻 ৩. বটম টাইমলাইন বার (ছবি ৩ এর মতো স্লিম ও প্রফেশনাল)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                                .navigationBarsPadding()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Current Time
                            Text(
                                text = formatTime(if (isUserSeeking) seekPosition.toLong() else currentPositionMs),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )

                            // চিকন ও স্মুথ স্লাইডার
                            Slider(
                                value = if (isUserSeeking) seekPosition else if (totalDurationMs > 0) currentPositionMs.toFloat() else 0f,
                                onValueChange = { newPos ->
                                    isUserSeeking = true
                                    seekPosition = newPos
                                },
                                onValueChangeFinished = {
                                    exoPlayer.seekTo(seekPosition.toLong())
                                    currentPositionMs = seekPosition.toLong()
                                    isUserSeeking = false
                                },
                                valueRange = 0f..(totalDurationMs.toFloat().coerceAtLeast(1f)),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF00E5FF),
                                    activeTrackColor = Color(0xFF00E5FF),
                                    inactiveTrackColor = Color.White.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier.weight(1f).height(12.dp)
                            )

                            // Total Duration
                            Text(
                                text = formatTime(totalDurationMs),
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )

                            // ফুলস্ক্রিন / রিসাইজ টগল আইকন
                            IconButton(
                                onClick = {
                                    resizeModeIndex = (resizeModeIndex + 1) % 3
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Fullscreen, contentDescription = "Resize", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            // 🔓 স্ক্রিন লক অবস্থায় শুধুমাত্র আনলক বাটন
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
