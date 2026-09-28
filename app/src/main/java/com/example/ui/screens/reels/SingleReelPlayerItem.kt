@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ui.screens.reels

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.ReelVideoQuality
import com.example.data.model.UserReelDto
import com.example.ui.screens.SleekOnlineTimeline
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val HeartPink = Color(0xFFFF2A4B)
private val SaveGold = Color(0xFFFFB300)

@Composable
fun SingleReelPlayerItem(
    reel: UserReelDto,
    selectedQuality: ReelVideoQuality,
    playbackSpeed: Float,
    isActiveVideoPlaying: Boolean,
    onDoubleTapLike: () -> Unit,
    onToggleLike: () -> Unit,
    onFollowClick: () -> Unit,
    onCommentClick: () -> Unit,
    onSaveClick: () -> Unit,
    onShareClick: () -> Unit,
    onOpenPageProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isBuffering by remember { mutableStateOf(true) }
    var showBigHeartAnimation by remember { mutableStateOf(false) }

    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableLongStateOf(0L) }

    // নির্বাচিত কোয়ালিটি অনুযায়ী ভিডিও ইউআরএল
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

    // কোয়ালিটি পরিবর্তন হলে পজিশন ঠিক রেখে প্লে
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

    // স্পিড পরিবর্তন সিঙ্ক
    LaunchedEffect(playbackSpeed) {
        exoPlayer.setPlaybackSpeed(playbackSpeed)
    }

    // স্ক্রিনে দৃশ্যমান থাকলে প্লে, সরে গেলে পজ
    LaunchedEffect(isActiveVideoPlaying) {
        if (isActiveVideoPlaying) {
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    // টাইমলাইন পজিশন ট্র্যাকার লুপ
    LaunchedEffect(isActiveVideoPlaying, isUserSeeking) {
        while (isActiveVideoPlaying && !isUserSeeking) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
            delay(100L)
        }
    }

    // প্লেয়ার স্ট্যাটাস লিসেনার
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = (state == Player.STATE_BUFFERING)
                if (state == Player.STATE_READY) {
                    totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
                }
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
            // 🎯 ডাবল-ট্যাপে বড় হার্ট অ্যানিমেশন ও সিঙ্গেল ট্যাপে প্লে/পজ
            .pointerInput(reel.id) {
                detectTapGestures(
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
        // =========================================================================
        // 📺 ১. ভিডিও সারফেস (RESIZE_MODE_FIT: বিভিন্ন রেশিওর ভিডিও ফ্রেমেই থাকবে)
        // =========================================================================
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    // 🎯 ফুলস্ক্রিন ক্রপ হবে না, আসল ভিডিও রেশিও বজায় রাখবে
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
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
                    color = Color.White,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(42.dp)
                )
            }
        }

        // ❤️ ডাবল-ট্যাপে বড় হার্ট পপ-আপ অ্যানিমেশন
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

        // নিচের টেক্সটের জন্য ডার্ক শ্যাডো
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
        // 👉 ২. ডানপাশের সাইড অ্যাকশন বাটনসমূহ (YouTube Shorts & TikTok Style)
        // [ Like | Comment | Save | Share ]
        // =========================================================================
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ১. লাইক বাটন
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onToggleLike, modifier = Modifier.size(38.dp)) {
                    Icon(
                        imageVector = if (reel.isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (reel.isLiked) HeartPink else Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    text = reel.formattedLikes,
                    color = if (reel.isLiked) HeartPink else Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // ২. কমেন্ট বাটন
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onCommentClick, modifier = Modifier.size(38.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "Comment",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Text(
                    text = if (reel.commentsCount > 0) reel.commentsCount.toString() else "0",
                    color = Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // ৩. সেভ / বুকমার্ক বাটন
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onSaveClick, modifier = Modifier.size(38.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.BookmarkBorder,
                        contentDescription = "Save",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Text(
                    text = "Save",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // ৪. শেয়ার বাটন
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onShareClick, modifier = Modifier.size(38.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Share",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Text(
                    text = if (reel.sharesCount > 0) reel.sharesCount.toString() else "Share",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // =========================================================================
        // 👤 ৩. নিচের ইনফো বার: ক্রিয়েটর অবতার + নাম/হ্যান্ডেল + ফলো বাটন + ক্যাপশন
        // =========================================================================
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 74.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // ক্রিয়েটর অবতার + ইউজারনেম + ফলো বাটন রো
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // গোল প্রোফাইল অবতার
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF222838))
                            .clickable { onOpenPageProfile() },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(reel.pageAvatar ?: "https://ui-avatars.com/api/?name=${reel.pageName}&background=00E676&color=000&bold=true")
                                .crossfade(true)
                                .build(),
                            contentDescription = reel.pageName,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }

                    // ইউজারনেম ও হ্যান্ডেল
                    Text(
                        text = reel.displayHandle,
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clickable { onOpenPageProfile() }
                            .weight(1f, fill = false)
                    )

                    // 🎯 YouTube Shorts স্টাইল [ Follow ] / [ Following ] বাটন
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (reel.isFollowing) Color(0x33FFFFFF) else Color.White,
                        modifier = Modifier.clickable { onFollowClick() }
                    ) {
                        Text(
                            text = if (reel.isFollowing) "Following" else "Follow",
                            color = if (reel.isFollowing) Color.White else Color.Black,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // ক্যাপশন ও হ্যাশট্যাগ
                if (!reel.title.isNullOrBlank()) {
                    Text(
                        text = reel.title,
                        color = Color.White,
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // =========================================================================
            // ⏳ ৪. নিচে ভিডিও টাইমলাইন ও প্রোগ্রেস বার
            // =========================================================================
            SleekOnlineTimeline(
                currentPositionMs = if (isUserSeeking) seekPosition else currentPositionMs,
                totalDurationMs = totalDurationMs,
                onSeekStarted = { isUserSeeking = true },
                onSeeking = { seekPosition = it },
                onSeekFinished = { targetPos ->
                    exoPlayer.seekTo(targetPos)
                    currentPositionMs = targetPos
                    isUserSeeking = false
                },
                activeColor = Color.White,
                inactiveColor = Color.White.copy(alpha = 0.25f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
            )
        }
    }
}
