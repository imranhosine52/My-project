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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.vector.ImageVector
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

enum class SettingsTab {
    SPEED,
    LANGUAGE,
    QUALITY
}

data class AudioLanguageOption(
    val displayName: String,
    val languageCode: String
)

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
    onNextEpisodeClick: () -> Unit = {},
    onDownloadClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { findActivityFromContext(context) }
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val coroutineScope = rememberCoroutineScope()

    var isControlsVisible by remember { mutableStateOf(true) }
    var isScreenLocked by rememberSaveable { mutableStateOf(false) }

    // 🤏 YouTube Pinch-to-zoom ও Aspect Ratio (0: Fit, 1: Zoom to fill, 2: 100% Stretch)
    var resizeModeIndex by rememberSaveable { mutableIntStateOf(0) }

    // ⚙️ ১ম ছবির মতো সেটিংস মেনু স্টেট
    var showSettingsDialog by remember { mutableStateOf(false) }
    var activeSettingsTab by remember { mutableStateOf(SettingsTab.LANGUAGE) }

    val speedOptions = remember { listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f) }
    var currentSpeed by rememberSaveable { mutableFloatStateOf(1.0f) }

    val languageOptions = remember {
        listOf(
            AudioLanguageOption("Bangla (বাংলা ডাবিং)", "ben"),
            AudioLanguageOption("Hindi (हिन्दी)", "hin"),
            AudioLanguageOption("English", "eng"),
            AudioLanguageOption("Japanese (日本語)", "jpn")
        )
    }
    var selectedLanguage by rememberSaveable { mutableStateOf("Bangla (বাংলা ডাবিং)") }

    val qualityOptions = remember { listOf("Auto", "1080P", "720P", "480P", "360P") }
    var selectedQuality by rememberSaveable { mutableStateOf("1080P") }

    // ব্রাইটনেস ও ভলিউম
    var brightnessLevel by remember {
        mutableFloatStateOf(activity?.window?.attributes?.screenBrightness?.takeIf { it > 0 } ?: 0.5f)
    }
    var showBrightnessOverlay by remember { mutableStateOf(false) }
    var volumeLevel by remember { mutableFloatStateOf(0.5f) }
    var showVolumeOverlay by remember { mutableStateOf(false) }

    var isUserSeeking by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableLongStateOf(0L) }

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
                            Toast.makeText(context, "Zoom to Fill", Toast.LENGTH_SHORT).show()
                        } else if (zoom < 0.88f && resizeModeIndex != 0) {
                            resizeModeIndex = 0 // Fit to screen
                            Toast.makeText(context, "Fit to Screen", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            // 👆 ২. ডাবল ট্যাপে স্কিপ এবং সিঙ্গেল ট্যাপে কন্ট্রোল দেখানো
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
        // 🎬 ExoPlayer Surface
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

        // ব্রাইটনেস ওভারলে
        if (showBrightnessOverlay) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
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
        if (showVolumeOverlay) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
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

        // =========================================================================
        // 🌟 ২য় ছবির মতো কাস্টম কন্ট্রোলস (Landscape & Portrait Unified)
        // =========================================================================
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))) {
                if (!isScreenLocked) {
                    // 🔝 Top Bar (Back Arrow & Download Icon)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = onBackClick, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }

                        IconButton(onClick = onDownloadClick, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.FileDownload, contentDescription = "Download", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }

                    // 🔓 ২য় ছবির মতো বামে "Tap to Lock" বাটন
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

                    // ⏯️ ২য় ছবির মতো সেন্ট্রাল কন্ট্রোলস (-10s, Play/Pause, +10s)
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

                    // ⏳ ২য় ছবির হুবহু বটম বার লেআউট (Timeline + Crop + Language + Speed + Quality)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
                            .padding(start = 16.dp, end = 16.dp, bottom = 6.dp)
                    ) {
                        // ১. টাইমলাইন স্ক্রাবার ও সময়
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // ২. ২য় ছবির মতো বাটন রো: [Play] [Next] ... [Crop] [Language] [1x] [1080P]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // বামের প্লে ও নেক্সট আইকন
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp).clickable { onPlayPauseClick() }
                                )

                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Next Episode",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp).clickable { onNextEpisodeClick() }
                                )
                            }

                            // ডানের অপশন রো: Crop • Language • 1x • 1080P • Rotate
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Crop বাটন
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.clickable {
                                        resizeModeIndex = (resizeModeIndex + 1) % 3
                                        val name = when (resizeModeIndex) {
                                            1 -> "Zoom to Fill"
                                            2 -> "100% Stretch"
                                            else -> "Original Fit"
                                        }
                                        Toast.makeText(context, name, Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(Icons.Outlined.CropFree, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                    Text("Crop", color = Color.White, fontSize = 11.5.sp)
                                }

                                // Language বাটন (১ম ছবির ট্যাব খোলে)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.clickable {
                                        activeSettingsTab = SettingsTab.LANGUAGE
                                        showSettingsDialog = true
                                    }
                                ) {
                                    Icon(Icons.Default.Headphones, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                    Text("Language", color = Color.White, fontSize = 11.5.sp)
                                }

                                // 1x বাটন
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.clickable {
                                        activeSettingsTab = SettingsTab.SPEED
                                        showSettingsDialog = true
                                    }
                                ) {
                                    Icon(Icons.Default.Speed, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                    Text(if (currentSpeed == 1.0f) "1x" else "${currentSpeed}x", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }

                                // 1080P বাটন
                                Text(
                                    text = selectedQuality,
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable {
                                        activeSettingsTab = SettingsTab.QUALITY
                                        showSettingsDialog = true
                                    }
                                )

                                // স্ক্রিন রোটেট আইকন
                                Icon(
                                    imageVector = if (isDeviceLandscape) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = "Rotate",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp).clickable { onToggleFullscreen() }
                                )
                            }
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
                    .padding(20.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.75f))
            ) {
                Icon(Icons.Default.Lock, contentDescription = "Unlock", tint = Color(0xFFFF5252), modifier = Modifier.size(22.dp))
            }
        }

        // =========================================================================
        // ⚙️ ১ম ছবির হুবহু সেটিংস পপ-আপ মেনু (Speed • Headphones • Subtitles • Close)
        // =========================================================================
        if (showSettingsDialog) {
            Dialog(onDismissRequest = { showSettingsDialog = false }) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141720)),
                    border = BorderStroke(1.dp, Color(0xFF282E3E)),
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .wrapContentHeight()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // ১ম ছবির হুবহু টপ ট্যাব বার
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // ১. স্পিডোমিটার ট্যাব
                                SettingsTabItem(
                                    icon = Icons.Default.Speed,
                                    isSelected = activeSettingsTab == SettingsTab.SPEED,
                                    onClick = { activeSettingsTab = SettingsTab.SPEED }
                                )

                                // ২. হেডফোন/অডিও ট্যাব (১ম ছবির মতো সক্রিয় ট্যাব)
                                SettingsTabItem(
                                    icon = Icons.Default.Headphones,
                                    isSelected = activeSettingsTab == SettingsTab.LANGUAGE,
                                    onClick = { activeSettingsTab = SettingsTab.LANGUAGE }
                                )

                                // ৩. সাবটাইটেল/কোয়ালিটি ট্যাব
                                SettingsTabItem(
                                    icon = Icons.Default.Subtitles,
                                    isSelected = activeSettingsTab == SettingsTab.QUALITY,
                                    onClick = { activeSettingsTab = SettingsTab.QUALITY }
                                )
                            }

                            // 'X' ক্লোজ বাটন
                            IconButton(onClick = { showSettingsDialog = false }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // কনটেন্ট লিস্ট (১ম ছবির মতো বড় স্পষ্ট টেক্সট)
                        when (activeSettingsTab) {
                            SettingsTab.LANGUAGE -> {
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    languageOptions.forEach { langOpt ->
                                        val isSelected = selectedLanguage == langOpt.displayName
                                        Text(
                                            text = langOpt.displayName,
                                            color = if (isSelected) Color.White else Color(0xFF9AA4B5),
                                            fontSize = 15.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedLanguage = langOpt.displayName
                                                    // ExoPlayer আসল অডিও ট্র্যাক নির্বাচন
                                                    try {
                                                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                            .buildUpon()
                                                            .setPreferredAudioLanguage(langOpt.languageCode)
                                                            .build()
                                                    } catch (_: Exception) {}
                                                    showSettingsDialog = false
                                                    Toast.makeText(context, "Audio set to: ${langOpt.displayName}", Toast.LENGTH_SHORT).show()
                                                }
                                                .padding(vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            SettingsTab.SPEED -> {
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    speedOptions.forEach { speed ->
                                        val isSelected = currentSpeed == speed
                                        Text(
                                            text = if (speed == 1.0f) "1.0x (Normal)" else "${speed}x",
                                            color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    currentSpeed = speed
                                                    exoPlayer.setPlaybackSpeed(speed)
                                                    showSettingsDialog = false
                                                    Toast.makeText(context, "Speed: ${speed}x", Toast.LENGTH_SHORT).show()
                                                }
                                                .padding(vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            SettingsTab.QUALITY -> {
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    qualityOptions.forEach { quality ->
                                        val isSelected = selectedQuality == quality
                                        Text(
                                            text = quality,
                                            color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedQuality = quality
                                                    showSettingsDialog = false
                                                    Toast.makeText(context, "Quality: $quality", Toast.LENGTH_SHORT).show()
                                                }
                                                .padding(vertical = 4.dp)
                                        )
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

@Composable
private fun SettingsTabItem(
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (isSelected) Color(0xFF2C3242) else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) Color.White else Color(0xFF8892A2),
            modifier = Modifier.size(20.dp)
        )
    }
}
