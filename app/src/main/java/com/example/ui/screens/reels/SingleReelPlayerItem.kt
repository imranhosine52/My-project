@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ui.screens.reels

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.ReelVideoQuality
import com.example.data.model.UserReelDto
import com.example.util.ReelsCachePreloadManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val HeartPink = Color(0xFFFF2A4B)
private val HashtagBlue = Color(0xFF00E5FF)

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
    onHashtagClick: (String) -> Unit,
    onOpenPageProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isBuffering by remember { mutableStateOf(true) }
    var isPlayingState by remember { mutableStateOf(true) }

    var showBigHeartAnimation by remember { mutableStateOf(false) }
    var showPlayPauseIconState by remember { mutableStateOf<Boolean?>(null) }

    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }

    // 🚀 প্রি-লোডার ক্যাশ বা অফলাইন ফাইল থেকে সরাসরি ভিডিও লিংক নেওয়া
    val playbackUri = remember(reel.id, selectedQuality) {
        ReelsCachePreloadManager.resolvePlaybackUri(reel)
    }

    // অপ্টিমাইজড ExoPlayer
    val exoPlayer = remember(reel.id) {
        val cacheDataSourceFactory = ReelsCachePreloadManager.getCacheDataSourceFactory(context)
        val mediaSourceFactory = DefaultMediaSourceFactory(cacheDataSourceFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(1200, 12000, 600, 1000)
            .build()

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
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

    LaunchedEffect(playbackUri) {
        exoPlayer.setMediaItem(MediaItem.fromUri(playbackUri))
        exoPlayer.prepare()
        if (isActiveVideoPlaying) exoPlayer.play()
    }

    LaunchedEffect(playbackSpeed) {
        exoPlayer.setPlaybackSpeed(playbackSpeed)
    }

    LaunchedEffect(isActiveVideoPlaying) {
        if (isActiveVideoPlaying) {
            exoPlayer.play()
            isPlayingState = true
        } else {
            exoPlayer.pause()
            isPlayingState = false
        }
    }

    // টাইমলাইন পজিশন ট্র্যাকার
    LaunchedEffect(isActiveVideoPlaying) {
        while (isActiveVideoPlaying) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
            delay(120L)
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = (state == Player.STATE_BUFFERING)
                if (state == Player.STATE_READY) {
                    totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlayingState = playing
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    // 🎯 ক্লিকেবল হ্যাশট্যাগ ও টেক্সট ফরম্যাটিং
    val annotatedCaption = remember(reel.title) {
        buildAnnotatedString {
            val fullText = reel.title.orEmpty()
            val words = fullText.split(" ")

            words.forEach { word ->
                if (word.startsWith("#") && word.length > 1) {
                    pushStringAnnotation(tag = "HASHTAG", annotation = word)
                    withStyle(
                        style = SpanStyle(
                            color = HashtagBlue,
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        append("$word ")
                    }
                    pop()
                } else {
                    append("$word ")
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            // 🎯 স্ক্রিন ট্যাপে প্লে/পজ ও ডাবল ট্যাপে লাইক
            .pointerInput(reel.id) {
                detectTapGestures(
                    onTap = {
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                            showPlayPauseIconState = false
                        } else {
                            exoPlayer.play()
                            showPlayPauseIconState = true
                        }
                        coroutineScope.launch {
                            delay(650)
                            showPlayPauseIconState = null
                        }
                    },
                    onDoubleTap = {
                        showBigHeartAnimation = true
                        onDoubleTapLike()
                        coroutineScope.launch {
                            delay(700)
                            showBigHeartAnimation = false
                        }
                    }
                )
            }
    ) {
        // =========================================================================
        // 📺 ১. ভিডিও সারফেস (RESIZE_MODE_FIT: কোন জোরপূর্বক ফুলস্ক্রিন ক্রপিং ছাড়া)
        // =========================================================================
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
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
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        // 🎯 স্ক্রিন ট্যাপে মাঝখানে অ্যানিমেটেড Play / Pause আইকন
        AnimatedVisibility(
            visible = showPlayPauseIconState != null,
            enter = scaleIn(tween(150)) + fadeIn(tween(150)),
            exit = scaleOut(tween(150)) + fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (showPlayPauseIconState == true) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        // ❤️ ডাবল-ট্যাপে বড় হার্ট অ্যানিমেশন
        if (showBigHeartAnimation) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = HeartPink.copy(alpha = 0.95f),
                modifier = Modifier
                    .size(96.dp)
                    .align(Alignment.Center)
                    .scale(1.25f)
            )
        }

        // নিচে টেক্সট পরিষ্কার দেখার হালকা ডার্ক শ্যাডো
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
        // 👉 ২. ডানপাশের সাইড অ্যাকশন বাটনসমূহ (বটম বারের উপরে সুরক্ষিত)
        // =========================================================================
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 60.dp), // 👈 ৪০dp বটম বারের উপরে
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // লাইক বাটন
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
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // কমেন্ট বাটন
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
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // সেভ বাটন
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
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // শেয়ার বাটন
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
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // =========================================================================
        // 👤 ৩. নিচের ইনফো বার ও টাইমলাইন (বটম বার যাতে ঢেকে না ফেলে: bottom = 44.dp)
        // =========================================================================
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(bottom = 44.dp), // 👈 ৪০dp বটম ন্যাভিগেশন বারের ঠিক উপরে
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 74.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // ক্রিয়েটর অবতার + ইউজারনেম + ফলো বাটন
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
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

                    // YouTube Shorts স্টাইল Follow / Following বাটন
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (reel.isFollowing) Color(0x33FFFFFF) else Color.White,
                        modifier = Modifier.clickable { onFollowClick() }
                    ) {
                        Text(
                            text = if (reel.isFollowing) "Following" else "Follow",
                            color = if (reel.isFollowing) Color.White else Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.5.dp)
                        )
                    }
                }

                // 🎯 ক্লিকেবল ক্যাপশন ও হ্যাশট্যাগ (#hashtag ক্লিক করলে সার্চ করবে)
                if (!reel.title.isNullOrBlank()) {
                    ClickableText(
                        text = annotatedCaption,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White,
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        onClick = { offset ->
                            annotatedCaption.getStringAnnotations(tag = "HASHTAG", start = offset, end = offset)
                                .firstOrNull()?.let { annotation ->
                                    onHashtagClick(annotation.item)
                                }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // =========================================================================
            // ⏳ ৪. ইউটিউব / টিকটক স্টাইল অতি সূক্ষ্ম ১.৫dp টাইমলাইন প্রোগ্রেস লাইন
            // =========================================================================
            val progressFraction = if (totalDurationMs > 0) {
                (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.5.dp) // 👈 ইউটিউবের মতো অতি সূক্ষ্ম লাইন
                    .background(Color.White.copy(alpha = 0.20f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = progressFraction)
                        .background(Color.White) // টিকটক/ইউটিউবের মতো সাদা বা লাল প্রোগ্রেস
                )
            }
        }
    }
}
