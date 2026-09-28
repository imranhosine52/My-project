@file:OptIn(UnstableApi::class)

package com.example.ui.screens.reels

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.model.ReelVideoQuality
import com.example.data.model.UserReelDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import androidx.compose.foundation.clickable
import kotlinx.coroutines.launch

private val ActionGreen = Color(0xFF00E676)
private val HeartPink = Color(0xFFFF2A4B)

@Composable
fun SingleReelPlayerItem(
    reel: UserReelDto,
    selectedQuality: ReelVideoQuality,
    isActiveVideoPlaying: Boolean,
    onDoubleTapLike: () -> Unit,
    onToggleLike: () -> Unit,
    onFollowClick: () -> Unit,
    onShareClick: () -> Unit,
    onQualityClick: () -> Unit,
    onOpenPageProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isBuffering by remember { mutableStateOf(true) }
    var showBigHeartAnimation by remember { mutableStateOf(false) }
    var is2xHoldActive by remember { mutableStateOf(false) }

    // নির্বাচিত কোয়ালিটি অনুযায়ী ভিডিও ইউআরএল বের করা
    val videoUrlToPlay = remember(reel.id, selectedQuality) {
        reel.getVideoUrlForQuality(selectedQuality)
    }

    // 🚀 ExoPlayer অপ্টিমাইজড ইন্সট্যান্স
    val exoPlayer = remember(reel.id) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(1500, 15000, 800, 1200)
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_ALL
                playWhenReady = true
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true
                )
            }
    }

    // কোয়ালিটি পরিবর্তিত হলে প্লেব্যাক পজিশন ধরে রেখে নতুন ভিডিও লোড করা
    LaunchedEffect(videoUrlToPlay) {
        if (videoUrlToPlay.isNotBlank()) {
            val currentPos = exoPlayer.currentPosition
            exoPlayer.setMediaItem(MediaItem.fromUri(videoUrlToPlay))
            exoPlayer.prepare()
            if (currentPos > 0) {
                exoPlayer.seekTo(currentPos)
            }
        }
    }

    // শুধুমাত্র স্ক্রিনে দৃশ্যমান থাকলে প্লে হবে, সরে গেলে পজ হবে
    LaunchedEffect(isActiveVideoPlaying) {
        if (isActiveVideoPlaying) {
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    // প্লেয়ার স্ট্যাটাস লিসেনার
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = (state == Player.STATE_BUFFERING)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            // 🎯 ডাবল-ট্যাপ লাইক, ট্যাপে প্লে/পজ এবং প্রেস-অ্যান্ড-হোল্ডে 2X স্পিড
            .pointerInput(reel.id) {
                detectTapGestures(
                    onPress = {
                        var speedJob: Job? = null
                        try {
                            speedJob = coroutineScope.launch {
                                delay(350)
                                is2xHoldActive = true
                                exoPlayer.setPlaybackSpeed(2.0f)
                            }
                            tryAwaitRelease()
                        } finally {
                            speedJob?.cancel()
                            if (is2xHoldActive) {
                                is2xHoldActive = false
                                exoPlayer.setPlaybackSpeed(1.0f)
                            }
                        }
                    },
                    onTap = {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                    },
                    onDoubleTap = {
                        showBigHeartAnimation = true
                        onDoubleTapLike()
                        coroutineScope.launch {
                            delay(800)
                            showBigHeartAnimation = false
                        }
                    }
                )
            }
    ) {
        // ১. মূল ভিডিও সারফেস
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // বাফারিং ইন্ডিকেটর
        if (isBuffering) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = ActionGreen,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(46.dp)
                )
            }
        }

        // ⚡ ২X স্পিড ইন্ডিকেটর
        if (is2xHoldActive) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 90.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "2X Speed",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ❤️ ডাবল-ট্যাপ পপ-আপ হার্ট অ্যানিমেশন
        if (showBigHeartAnimation) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = HeartPink.copy(alpha = 0.95f),
                modifier = Modifier
                    .size(96.dp)
                    .align(Alignment.Center)
                    .scale(1.2f)
            )
        }

        // ২. নিচের টেক্সট পড়ার সুবিধার জন্য ডার্ক শ্যাডো ওভারলে
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
        )

        // =========================================================================
        // 👉 ডানপাশের অ্যাকশন বাটনসমূহ
        // =========================================================================
        ReelsActionColumn(
            reel = reel,
            currentQuality = selectedQuality,
            onAvatarClick = onOpenPageProfile,
            onFollowClick = onFollowClick,
            onLikeClick = onToggleLike,
            onShareClick = onShareClick,
            onQualityClick = onQualityClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 24.dp)
        )

        // =========================================================================
        // 📝 নিচের টাইটেল ও পেজ ইনফো
        // =========================================================================
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 14.dp, bottom = 24.dp, end = 85.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // পেজের নাম ও হ্যান্ডেল
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.clickable { onOpenPageProfile() }
            ) {
                Text(
                    text = reel.pageName,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = reel.displayHandle,
                    color = ActionGreen,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // ক্যাপশন ও ডেসক্রিপশন
            if (!reel.title.isNullOrBlank()) {
                Text(
                    text = reel.title,
                    color = Color(0xFFEDEDED),
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
