@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ui.screens.reels.player

import android.app.Activity
import android.net.Uri
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.ReelVideoQuality
import com.example.data.model.UserReelDto
import com.example.data.repository.ReelsRepository
import com.example.ui.screens.reels.actions.HorizontalBottomBar
import com.example.ui.screens.reels.actions.InstagramActionColumn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val HeartPink = Color(0xFFFF2A4B)
private val HashtagCyan = Color(0xFF00E5FF)

/**
 * 🎬 একক ভিডিও রিলস প্লেয়ার:
 * - রোটেট অপশন সম্পূর্ণ রিমুভ
 * - ক্যাপশনের ঠিক নিচে ১০০% দৃশ্যমান টাইমলাইন প্রগ্রেস বার
 * - কোনো উপাদানই আর ন্যাভিগেশন বারের নিচে চাপা পড়বে না
 */
@Composable
fun SingleReelPlayerItem(
    reel: UserReelDto,
    allReels: List<UserReelDto> = emptyList(),
    selectedQuality: ReelVideoQuality,
    playbackSpeed: Float,
    isActiveVideoPlaying: Boolean,
    repository: ReelsRepository,
    isCommentsOpen: Boolean = false,
    isSidebarOpenState: Boolean = false,
    onSidebarStateChange: (Boolean) -> Unit = {},
    isLoggedIn: Boolean = true,
    isCreatorPageUser: Boolean = false,
    onRequireLogin: () -> Unit = {},
    onDoubleTapLike: () -> Unit,
    onToggleLike: () -> Unit,
    onFollowClick: () -> Unit,
    onCommentClick: () -> Unit,
    onShareClick: () -> Unit,
    onHashtagClick: (String) -> Unit,
    onOpenPageProfile: () -> Unit,
    onSelectReel: (UserReelDto) -> Unit = {},
    onVideoCompleteAutoPlayNext: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()

    var isBuffering by remember { mutableStateOf(true) }
    var isPlayingState by remember { mutableStateOf(true) }

    var showBigHeartAnimation by remember { mutableStateOf(false) }
    var showPlayPauseIconState by remember { mutableStateOf<Boolean?>(null) }

    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }

    val currentIsSidebarOpen by rememberUpdatedState(isSidebarOpenState)
    val currentIsCommentsOpen by rememberUpdatedState(isCommentsOpen)

    // ভিডিও চলাকালে স্ক্রিন চালু রাখার লজিক
    DisposableEffect(isActiveVideoPlaying, isPlayingState) {
        if (isActiveVideoPlaying && isPlayingState) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    BackHandler(enabled = isSidebarOpenState) {
        onSidebarStateChange(false)
    }

    var isSaved by remember(reel.id, reel.isSaved) { mutableStateOf(reel.isSaved) }
    var saveCount by remember(reel.id, reel.isSaved) { mutableIntStateOf(if (reel.isSaved) 1 else 0) }

    var watchStartTimeMs by remember { mutableLongStateOf(0L) }
    var totalWatchDurationMs by remember { mutableLongStateOf(0L) }
    var hasCompleted100Percent by remember { mutableStateOf(false) }
    var loopCount by remember { mutableIntStateOf(0) }
    var isAlgorithmPingSent by remember { mutableStateOf(false) }

    val videoUrlToPlay = remember(reel.id, selectedQuality) {
        reel.getVideoUrlForQuality(selectedQuality)
    }

    val creatorReels = remember(reel, allReels) {
        val targetCreatorId = if (reel.pageId > 0) reel.pageId else reel.userId
        val list = allReels.filter {
            (it.pageId > 0 && it.pageId == targetCreatorId) || (it.userId > 0 && it.userId == targetCreatorId)
        }
        if (list.isNotEmpty()) list else listOf(reel)
    }

    // ExoPlayer ইঞ্জিন
    val exoPlayer = remember(reel.id) {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setUserAgent("Mozilla/5.0 (Linux; Android 14) PlayDramaFlix-Reels")

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(1000, 15000, 500, 1000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
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

    LaunchedEffect(videoUrlToPlay) {
        if (videoUrlToPlay.isNotBlank()) {
            runCatching {
                val curPos = exoPlayer.currentPosition
                val mediaItem = MediaItem.fromUri(Uri.parse(videoUrlToPlay))
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                if (curPos > 0) exoPlayer.seekTo(curPos)
                if (isActiveVideoPlaying) exoPlayer.play()
            }
        }
    }

    LaunchedEffect(playbackSpeed) {
        runCatching { exoPlayer.setPlaybackSpeed(playbackSpeed) }
    }

    LaunchedEffect(isActiveVideoPlaying) {
        if (isActiveVideoPlaying) {
            runCatching { exoPlayer.play() }
            isPlayingState = true
            watchStartTimeMs = System.currentTimeMillis()
        } else {
            runCatching { exoPlayer.pause() }
            isPlayingState = false
            if (watchStartTimeMs > 0L) {
                totalWatchDurationMs += (System.currentTimeMillis() - watchStartTimeMs)
                watchStartTimeMs = 0L
            }
        }
    }

    // 🎯 টাইমলাইন প্রগ্রেস লাইভ ট্র্যাকিং লুপ
    LaunchedEffect(isActiveVideoPlaying, isPlayingState) {
        while (isActiveVideoPlaying) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)

            if (totalDurationMs > 2000L && currentPositionMs >= (totalDurationMs - 400L)) {
                hasCompleted100Percent = true
            }
            delay(50L) // ৬০ এফপিএস স্মুথ প্রগ্রেস বার
        }
    }

    fun fireAlgorithmWatchTracking() {
        if (isAlgorithmPingSent) return
        isAlgorithmPingSent = true

        val currentSessionTime = if (watchStartTimeMs > 0L) (System.currentTimeMillis() - watchStartTimeMs) else 0L
        val totalWatchedMs = totalWatchDurationMs + currentSessionTime
        val elapsedSec = (totalWatchedMs / 1000L).toInt()

        val isSkipped = elapsedSec < 2
        val isCompleted = hasCompleted100Percent || (totalDurationMs > 0 && totalWatchedMs >= (totalDurationMs - 1000L))
        val isRewatch = loopCount > 0 || (totalDurationMs > 0 && totalWatchedMs > (totalDurationMs * 1.5))

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                repository.trackReelWatch(
                    reelId = reel.id,
                    watchTimeSec = elapsedSec,
                    isCompleted = isCompleted,
                    isSkipped = isSkipped,
                    isRewatch = isRewatch
                )
            }
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = (state == Player.STATE_BUFFERING)
                if (state == Player.STATE_READY) {
                    totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
                    if (isActiveVideoPlaying) exoPlayer.play()
                }
                if (state == Player.STATE_ENDED) {
                    fireAlgorithmWatchTracking()
                    runCatching { onVideoCompleteAutoPlayNext() }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                    loopCount++
                    hasCompleted100Percent = true
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlayingState = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                runCatching { exoPlayer.prepare() }
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            fireAlgorithmWatchTracking()
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    val annotatedCaption = remember(reel.title, reel.description, reel.hashtags) {
        buildAnnotatedString {
            val fullText = buildString {
                if (!reel.title.isNullOrBlank()) append(reel.title)
                if (!reel.description.isNullOrBlank() && reel.description != reel.title) {
                    if (isNotEmpty()) append(" ")
                    append(reel.description)
                }
                if (!reel.hashtags.isNullOrBlank()) {
                    if (isNotEmpty()) append(" ")
                    append(reel.hashtags.replace(",", " "))
                }
            }

            val words = fullText.split(" ")
            words.forEach { word ->
                if (word.startsWith("#") && word.length > 1) {
                    pushStringAnnotation(tag = "HASHTAG", annotation = word)
                    withStyle(
                        style = SpanStyle(
                            color = HashtagCyan,
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

    fun handleToggleSave() {
        if (!isLoggedIn) {
            onRequireLogin()
            return
        }
        val newSaveState = !isSaved
        isSaved = newSaveState
        saveCount += if (newSaveState) 1 else -1

        Toast.makeText(
            context,
            if (newSaveState) "Saved" else "Removed from saved",
            Toast.LENGTH_SHORT
        ).show()

        coroutineScope.launch {
            val res = repository.toggleSaveReel(reel.id)
            if (res.isFailure) {
                isSaved = !newSaveState
                saveCount += if (newSaveState) -1 else 1
            }
        }
    }

    // 🎯 টাইমলাইন ফ্র্যাকশন হিসাব
    val progressFraction = if (totalDurationMs > 0) {
        (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // =========================================================================
        // 📺 ১. মূল ভিডিও প্লেয়ার সারফেস (RESIZE_MODE_FIT: অরিজিনাল সাইজ অক্ষুণ্ণ)
        // =========================================================================
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(reel.id) {
                    detectTapGestures(
                        onTap = {
                            if (!currentIsCommentsOpen) {
                                if (currentIsSidebarOpen) {
                                    onSidebarStateChange(false)
                                } else {
                                    if (exoPlayer.isPlaying) {
                                        exoPlayer.pause()
                                        showPlayPauseIconState = false
                                    } else {
                                        exoPlayer.play()
                                        showPlayPauseIconState = true
                                    }
                                    coroutineScope.launch {
                                        delay(600)
                                        showPlayPauseIconState = null
                                    }
                                }
                            }
                        },
                        onDoubleTap = {
                            if (!currentIsCommentsOpen && !currentIsSidebarOpen) {
                                if (!isLoggedIn) {
                                    onRequireLogin()
                                } else {
                                    showBigHeartAnimation = true
                                    onDoubleTapLike()
                                    coroutineScope.launch {
                                        delay(700)
                                        showBigHeartAnimation = false
                                    }
                                }
                            }
                        }
                    )
                }
                .pointerInput(reel.id) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        if (!currentIsCommentsOpen) {
                            if (!currentIsSidebarOpen && dragAmount < -15f) {
                                change.consume()
                                onSidebarStateChange(true)
                            } else if (currentIsSidebarOpen && dragAmount > 15f) {
                                change.consume()
                                onSidebarStateChange(false)
                            }
                        }
                    }
                }
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isBuffering) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // =========================================================================
        // 🛑 ওভারলে কনটেন্ট
        // =========================================================================
        if (!isCommentsOpen) {
            AnimatedVisibility(
                visible = showPlayPauseIconState != null,
                enter = scaleIn(tween(140)) + fadeIn(tween(140)),
                exit = scaleOut(tween(140)) + fadeOut(tween(140)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f)),
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

            // টেক্সটের পেছনের গ্রেডিয়েন্ট শ্যাডো
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f))
                        )
                    )
            )

            // =========================================================================
            // 🔄 মোড সুইচিং
            // =========================================================================
            AnimatedContent(
                targetState = isSidebarOpenState,
                transitionSpec = {
                    fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(140))
                },
                label = "ReelsLayoutTransition",
                modifier = Modifier.fillMaxSize()
            ) { sidebarVisible ->
                if (!sidebarVisible) {
                    // =============================================================
                    // 📱 ক) স্বাভাবিক মোড: ক্যাপশন, প্রোফাইল এবং নিশ্চিত টাইমলাইন বার
                    // =============================================================
                    Box(modifier = Modifier.fillMaxSize()) {
                        // ডানপাশের অ্যাকশন আইকনগুলো (বটম বারের উপরে পারফেক্ট উচ্চতায়)
                        InstagramActionColumn(
                            reel = reel,
                            isSaved = isSaved,
                            saveCount = saveCount,
                            onLikeClick = {
                                if (!isLoggedIn) onRequireLogin() else onToggleLike()
                            },
                            onCommentClick = onCommentClick,
                            onSaveClick = { handleToggleSave() },
                            onShareClick = onShareClick,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .navigationBarsPadding()
                                .padding(end = 12.dp, bottom = 78.dp) // 🎯 বটম ন্যাভ বারের ওপরে পারফেক্ট মার্জিন
                        )

                        // 🎯 নিচে বামে ক্রিয়েটর প্রোফাইল, ক্যাপশন ও সরাসরি সংযুক্ত টাইমলাইন বার
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(bottom = 60.dp), // 🎯 বটম ন্যাভিগেশন বারের ঠিক উপরে উচ্চতা
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 74.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // ১. প্রোফাইল ও ফলো বাটন
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

                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (reel.isFollowing) Color(0x33FFFFFF) else Color.White,
                                        modifier = Modifier.clickable {
                                            if (!isLoggedIn) onRequireLogin() else onFollowClick()
                                        }
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

                                // ২. ক্যাপশন ও হ্যাশট্যাগ
                                if (annotatedCaption.text.isNotBlank()) {
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

                            Spacer(modifier = Modifier.height(6.dp))

                            // =========================================================================
                            // 🎯 ৩. আপনার বহুল প্রতীক্ষিত টাইমলাইন প্রগ্রেস বার (ক্যাপশনের ঠিক নিচে স্পষ্ট)
                            // =========================================================================
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp) // 🎯 মোটা ও স্পষ্ট সাদা দাগ
                                    .background(Color.White.copy(alpha = 0.35f)) // দৃশ্যমান ব্যাকগ্রাউন্ড ট্র্যাক
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(fraction = progressFraction.coerceAtLeast(0.01f))
                                        .background(Color.White) // সক্রিয় রানিং সাদা প্রগ্রেস
                                )
                            }
                        }
                    }
                } else {
                    // =============================================================
                    // 🎬 খ) সাইডবার মোড
                    // =============================================================
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 56.dp)
                    ) {
                        HorizontalBottomBar(
                            reel = reel,
                            annotatedCaption = annotatedCaption,
                            isSaved = isSaved,
                            saveCount = saveCount,
                            isLoggedIn = isLoggedIn,
                            onRequireLogin = onRequireLogin,
                            onToggleLike = onToggleLike,
                            onCommentClick = onCommentClick,
                            onSaveClick = { handleToggleSave() },
                            onShareClick = onShareClick,
                            onFollowClick = onFollowClick,
                            onOpenPageProfile = onOpenPageProfile,
                            onHashtagClick = onHashtagClick,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 6.dp)
                        )
                    }
                }
            }

            // সাইডবার
            ReelsPlaylistSidebar(
                isOpen = isSidebarOpenState,
                currentReel = reel,
                creatorReels = creatorReels,
                isPlaying = isPlayingState,
                onTogglePlayPause = {
                    runCatching {
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                        } else {
                            exoPlayer.play()
                        }
                    }
                },
                onSelectReel = { selectedReelItem ->
                    runCatching { onSelectReel(selectedReelItem) }
                },
                onCloseSidebar = { onSidebarStateChange(false) },
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}
