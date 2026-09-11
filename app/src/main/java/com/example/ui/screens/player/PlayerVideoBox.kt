@file:OptIn(UnstableApi::class)

package com.example.ui.screens.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.model.EpisodeDto
import com.example.ui.screens.EqualizerBarsIcon
import com.example.ui.screens.SleekOnlineTimeline
import com.example.ui.screens.SleekSkipIconOnline
import com.example.ui.theme.GoldVip
import com.example.util.R2DownloadManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

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

private fun findActivity(context: Context): Activity? {
    var current = context
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
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
    onShareClick: () -> Unit = {},
    onDownloadClick: (() -> Unit)? = null,
    // 🎯 নতুন রিকোয়ারমেন্ট প্যারামিটার
    episodes: List<EpisodeDto> = emptyList(),
    shouldLockEpisodes: Boolean = false,
    onSelectEpisode: (EpisodeDto) -> Unit = {},
    onNextEpisode: () -> Unit = {},
    onSendComment: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { findActivity(context) }
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val coroutineScope = rememberCoroutineScope()

    var isControlsVisible by remember { mutableStateOf(true) }
    var isScreenLocked by rememberSaveable { mutableStateOf(false) }

    // 🎯 ২ নম্বর ছবির মতো ডানপাশের এপিসোড ড্রয়ারের স্টেট
    var showRightEpisodeDrawer by remember { mutableStateOf(false) }
    var commentInputText by remember { mutableStateOf("") }

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var zoomOffset by remember { mutableStateOf(Offset.Zero) }

    val speedOptions = remember { listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f) }
    var currentSpeedIndex by rememberSaveable { mutableIntStateOf(2) }
    val currentSpeed = speedOptions[currentSpeedIndex]

    var brightnessLevel by remember {
        mutableFloatStateOf(activity?.window?.attributes?.screenBrightness?.takeIf { it > 0 } ?: 0.5f)
    }
    var showBrightnessOverlay by remember { mutableStateOf(false) }
    var volumeLevel by remember { mutableFloatStateOf(0.5f) }
    var showVolumeOverlay by remember { mutableStateOf(false) }

    var isUserSeeking by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableLongStateOf(0L) }

    var isBuffering by remember { mutableStateOf(exoPlayer.playbackState == Player.STATE_BUFFERING) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = (state == Player.STATE_BUFFERING)
            }
            override fun onIsPlayingChanged(playingState: Boolean) {
                if (playingState) isBuffering = false
            }
            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

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

    // ৪ সেকেন্ড পর কন্ট্রোলস অটো হাইড
    LaunchedEffect(isControlsVisible, isPlaying, isScreenLocked, showRightEpisodeDrawer) {
        if (isControlsVisible && isPlaying && !isScreenLocked && !showRightEpisodeDrawer) {
            delay(5000L)
            isControlsVisible = false
        }
    }

    // PiP মোড চালু করার মেথড
    fun enterPiPMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                activity?.enterPictureInPictureMode(params)
            } catch (_: Exception) {
                activity?.enterPictureInPictureMode()
            }
        } else {
            Toast.makeText(context, "PiP not supported on this Android version", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
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
            .pointerInput(isScreenLocked, showRightEpisodeDrawer) {
                detectTapGestures(
                    onTap = {
                        if (showRightEpisodeDrawer) {
                            showRightEpisodeDrawer = false
                        } else {
                            isControlsVisible = !isControlsVisible
                        }
                    },
                    onDoubleTap = { offset ->
                        if (!isScreenLocked && !showRightEpisodeDrawer) {
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
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    keepScreenOn = true
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

        // বাফারিং লোডার
        if (isBuffering) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color(0xFF00E5FF),
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        // ব্রাইটনেস ওভারলে
        if (showBrightnessOverlay) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.BrightnessMedium, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(if (volumeLevel == 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Volume ${(volumeLevel * 100).toInt()}%", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // =========================================================================
        // 🎬 ১ নম্বর ছবির মতো মূল অন-স্ক্রিন প্লেয়ার কন্ট্রোলস
        // =========================================================================
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.40f))
            ) {
                if (!isScreenLocked) {
                    // 🔝 ১. টপ বার: [< Title Ep]               [PiP] [Cast] [Download]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)))
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // বাম সাইড: ব্যাক বাটন ও কত নম্বর পর্ব
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).padding(end = 12.dp)
                        ) {
                            IconButton(onClick = onBackClick, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            val epFormatted = String.format(Locale.US, "%02d", episodeNumber)
                            Text(
                                text = "$title $epFormatted",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // ডান সাইড (১ নম্বর ছবির মতো): PiP, TV Cast, Download
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // ১. ছোট স্ক্রিন / PiP বাটন
                            IconButton(onClick = { enterPiPMode() }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.PictureInPictureAlt, contentDescription = "PiP", tint = Color.White, modifier = Modifier.size(19.dp))
                            }

                            // ২. টিভি কানেক্ট / Cast বাটন
                            IconButton(
                                onClick = { Toast.makeText(context, "Searching for Cast devices...", Toast.LENGTH_SHORT).show() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Cast, contentDescription = "Cast", tint = Color.White, modifier = Modifier.size(19.dp))
                            }

                            // ৩. ডাউনলোড বাটন
                            IconButton(
                                onClick = {
                                    if (onDownloadClick != null) onDownloadClick()
                                    else {
                                        val resolvedUrl = R2DownloadManager.resolveDirectMp4Url(downloadUrl)
                                        if (resolvedUrl.isNotBlank()) {
                                            R2DownloadManager.startDownload(
                                                context = context,
                                                downloadUrl = resolvedUrl,
                                                title = title,
                                                episodeNumber = episodeNumber,
                                                isMovie = (episodeNumber <= 1 && totalDurationMs > 3600000L)
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Outlined.FileDownload, contentDescription = "Download", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    // ⏯️ ২. সেন্টারের কন্ট্রোলস (-10s, Play/Pause, +10s)
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(50.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "-10s",
                                color = Color(0xFF00E5FF),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.offset(y = (-30).dp).alpha(rewindAlpha)
                            )
                            IconButton(onClick = { triggerSkip(-10) }, modifier = Modifier.size(46.dp).rotate(rewindRotation.value)) {
                                SleekSkipIconOnline(isForward = false, color = Color.White)
                            }
                        }

                        IconButton(onClick = onPlayPauseClick, modifier = Modifier.size(54.dp)) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.size(44.dp)
                            )
                        }

                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "+10s",
                                color = Color(0xFF00E5FF),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.offset(y = (-30).dp).alpha(forwardAlpha)
                            )
                            IconButton(onClick = { triggerSkip(10) }, modifier = Modifier.size(46.dp).rotate(forwardRotation.value)) {
                                SleekSkipIconOnline(isForward = true, color = Color.White)
                            }
                        }
                    }

                    // 🔒 ৩. ডানপাশের স্ক্রিন লক বাটন (১ নম্বর ছবির মতো মাঝবরাবর)
                    IconButton(
                        onClick = {
                            isScreenLocked = true
                            isControlsVisible = false
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f))
                    ) {
                        Icon(Icons.Outlined.LockOpen, contentDescription = "Lock", tint = Color.White, modifier = Modifier.size(20.dp))
                    }

                    // 🔻 ৪. বটম বার: টাইমলাইন + "Say something" + Speed + Playlist + Next
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.90f))))
                            .navigationBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // টাইমলাইন ও সময়
                        Row(
                            modifier = Modifier.fillMaxWidth(),
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
                        }

                        // বটম অ্যাকশন কন্ট্রোলস (১ নম্বর ছবির হুবহু ডিজাইন)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // 💬 "Say something" কমেন্ট ইনপুট বক্স
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(17.dp))
                                    .background(Color.White.copy(alpha = 0.15f))
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (commentInputText.isEmpty()) {
                                    Text("Say something...", color = Color.White.copy(alpha = 0.6f), fontSize = 11.5.sp)
                                }
                                BasicTextField(
                                    value = commentInputText,
                                    onValueChange = { commentInputText = it },
                                    textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                                    cursorBrush = SolidColor(Color(0xFF00E5FF)),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                if (commentInputText.isNotBlank()) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send",
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable {
                                                onSendComment(commentInputText.trim())
                                                commentInputText = ""
                                            }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            // ডানদিকের অ্যাকশনস: [Speed] [Playlist] [Next] [Rotate]
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // ১. স্পিড সিলেক্টর (1.0x)
                                Text(
                                    text = if (currentSpeed == 1.0f) "1.0x" else "${currentSpeed}x",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable {
                                            currentSpeedIndex = (currentSpeedIndex + 1) % speedOptions.size
                                            val newSpd = speedOptions[currentSpeedIndex]
                                            exoPlayer.setPlaybackSpeed(newSpd)
                                            Toast.makeText(context, "${newSpd}x Speed", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(vertical = 4.dp)
                                )

                                // ২. 🎯 এপিসোড প্লে-লিস্ট বাটন (চাপ দিলে ২ নম্বর ছবির মতো ড্রয়ার আসবে)
                                IconButton(
                                    onClick = { showRightEpisodeDrawer = !showRightEpisodeDrawer },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FeaturedPlayList,
                                        contentDescription = "Episodes Playlist",
                                        tint = if (showRightEpisodeDrawer) Color(0xFF00E5FF) else Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // ৩. ⏭️ Next Episode বাটন
                                IconButton(onClick = onNextEpisode, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.SkipNext, contentDescription = "Next Episode", tint = Color.White, modifier = Modifier.size(22.dp))
                                }

                                // ৪. ফুলস্ক্রিন / রোটেট বাটন
                                IconButton(onClick = onToggleFullscreen, modifier = Modifier.size(28.dp)) {
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
            }
        }

        // =========================================================================
        // 📑 ২ নম্বর ছবির হুবহু ডানপাশের এপিসোড ড্রয়ার (Right-Side Drawer)
        // =========================================================================
        AnimatedVisibility(
            visible = showRightEpisodeDrawer,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(if (isDeviceLandscape) 0.38f else 0.72f),
                color = Color(0xFF10141E).copy(alpha = 0.94f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Episodes (${episodes.size})",
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showRightEpisodeDrawer = false }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF8E95A5), modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 🔲 ২ নম্বর ছবির মতো ৫-কলাম গ্রিড
                    val displayEps = episodes.ifEmpty {
                        (1..30).map { EpisodeDto(episodeNumber = it, isLocked = it > 1) }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(displayEps, key = { it.episodeId }) { ep ->
                            val isSelected = ep.episodeNumber == episodeNumber
                            val isEpLocked = shouldLockEpisodes && ep.isLocked

                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isSelected) Color(0xFF0F3B32) else Color(0xFF1E2433).copy(alpha = 0.8f)
                                    )
                                    .border(
                                        width = if (isSelected) 1.2.dp else 0.dp,
                                        color = if (isSelected) Color(0xFF00E676) else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        onSelectEpisode(ep)
                                        showRightEpisodeDrawer = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    // চলমান পর্বে অ্যানিমেটেড ইকুয়ালাইজার
                                    if (isSelected) {
                                        EqualizerBarsIcon(
                                            modifier = Modifier.size(12.dp, 9.dp),
                                            tint = Color(0xFF00E676)
                                        )
                                    } else {
                                        Text(
                                            text = ep.episodeNumber.toString(),
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // ২ নম্বর ছবির মতো লক থাকা পর্বে ছোট "VIP" ট্যাগ
                                    if (isEpLocked && !isSelected) {
                                        Text(
                                            text = "VIP",
                                            color = GoldVip,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // আনলক বাটন (স্ক্রিন লক থাকলে ডানপাশে ভেসে থাকবে)
        if (isScreenLocked) {
            IconButton(
                onClick = {
                    isScreenLocked = false
                    isControlsVisible = true
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.75f))
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
