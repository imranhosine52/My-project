@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ui.screens.reels.player

import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
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
import com.example.data.model.ReelVideoQuality
import com.example.data.model.UserReelDto
import com.example.data.repository.ReelsRepository
import com.example.ui.screens.reels.actions.HorizontalBottomBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val HeartPink = Color(0xFFFF2A4B)
private val HashtagCyan = Color(0xFF00E5FF)

/**
 * 🎬 একক ভিডিও রিলস প্লেয়ার কম্পোনেন্ট:
 * - স্ক্রিনের ডান প্রান্ত থেকে টানলে সব ভিডিওর প্লেলিস্ট সাইডবার ওপেন
 * - নিচে অনুভূমিক ক্যাপশন, ক্রিয়েটর ও লাইক/কমেন্ট/স্টার/শেয়ার অ্যাকশন বার
 * - কমেন্ট ওপেন থাকলে সমস্ত ওভারলে অদৃশ্য হয়ে শুধু ভিডিও ফ্রেম দেখা
 */
@Composable
fun SingleReelPlayerItem(
    reel: UserReelDto,
    allReels: List<UserReelDto> = emptyList(), // 🎯 ক্রিয়েটরের সব ভিডিও সাইডবারে দেখানোর জন্য
    selectedQuality: ReelVideoQuality,
    playbackSpeed: Float,
    isActiveVideoPlaying: Boolean,
    repository: ReelsRepository,
    isCommentsOpen: Boolean = false,
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
    onSelectReel: (UserReelDto) -> Unit = {}, // 🎯 সাইডবার থেকে অন্য ভিডিও চাপলে স্যুইচ করার কলব্যাক
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

    // 🎯 ডান সাইডবার ড্রয়ার ওপেন/ক্লোজ স্টেট
    var isSidebarOpen by remember { mutableStateOf(false) }

    // সাইডবার ওপেন থাকলে ব্যাক বাটনে চাপলে সাইডবার বন্ধ হবে
    BackHandler(enabled = isSidebarOpen) {
        isSidebarOpen = false
    }

    // রিয়েল সেভ স্টেট
    var isSaved by remember(reel.id, reel.isSaved) { mutableStateOf(reel.isSaved) }
    var saveCount by remember(reel.id, reel.isSaved) { mutableIntStateOf(if (reel.isSaved) 1 else 0) }

    // অ্যালগরিদম ওয়াচ ট্র্যাকার
    var watchStartTimeMs by remember { mutableLongStateOf(0L) }
    var totalWatchDurationMs by remember { mutableLongStateOf(0L) }
    var hasCompleted100Percent by remember { mutableStateOf(false) }
    var loopCount by remember { mutableIntStateOf(0) }
    var isAlgorithmPingSent by remember { mutableStateOf(false) }

    val videoUrlToPlay = remember(reel.id, selectedQuality) {
        reel.getVideoUrlForQuality(selectedQuality)
    }

    // একই ক্রিয়েটরের সব ভিডিও ফিল্টার করা
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

    LaunchedEffect(videoUrlToPlay) {
        if (videoUrlToPlay.isNotBlank()) {
            val curPos = exoPlayer.currentPosition
            val mediaItem = MediaItem.fromUri(Uri.parse(videoUrlToPlay))
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            if (curPos > 0) exoPlayer.seekTo(curPos)
            if (isActiveVideoPlaying) exoPlayer.play()
        }
    }

    LaunchedEffect(playbackSpeed) {
        exoPlayer.setPlaybackSpeed(playbackSpeed)
    }

    LaunchedEffect(isActiveVideoPlaying) {
        if (isActiveVideoPlaying) {
            exoPlayer.play()
            isPlayingState = true
            watchStartTimeMs = System.currentTimeMillis()
        } else {
            exoPlayer.pause()
            isPlayingState = false
            if (watchStartTimeMs > 0L) {
                totalWatchDurationMs += (System.currentTimeMillis() - watchStartTimeMs)
                watchStartTimeMs = 0L
            }
        }
    }

    LaunchedEffect(isActiveVideoPlaying) {
        while (isActiveVideoPlaying) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)

            if (totalDurationMs > 2000L && currentPositionMs >= (totalDurationMs - 400L)) {
                hasCompleted100Percent = true
            }
            delay(100L)
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
            repository.trackReelWatch(
                reelId = reel.id,
                watchTimeSec = elapsedSec,
                isCompleted = isCompleted,
                isSkipped = isSkipped,
                isRewatch = isRewatch
            )
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
                exoPlayer.prepare()
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

    // হ্যাশট্যাগ ও ক্যাপশন
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(reel.id, isCommentsOpen) {
                detectTapGestures(
                    onTap = {
                        if (!isCommentsOpen) {
                            if (isSidebarOpen) {
                                isSidebarOpen = false
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
                        if (!isCommentsOpen && !isSidebarOpen) {
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
    ) {
        // ১. মূল ভিডিও প্লেয়ার ফ্রেম
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

        if (isBuffering) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // =========================================================================
        // 🛑 কমেন্ট ওপেন থাকলে সমস্ত ওভারলে অদৃশ্য থাকবে (শুধু পিওর ফ্রেম চলবে)
        // =========================================================================
        if (!isCommentsOpen) {
            // প্লে / পজ ইন্ডিকেটর
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

            // ডাবল ট্যাপ বিগ হার্ট
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

            // টেক্সট স্পষ্ট করার নিচের শ্যাডো গ্রেডিয়েন্ট
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))
                        )
                    )
            )

            // =========================================================================
            // 🎬 নতুন স্ক্রিনশটের হুবহু নিচের অনুভূমিক কন্ট্রোল বার:
            // (ক্যাপশন + ক্রিয়েটর প্রোফাইল + লাইক, কমেন্ট, স্টার, শেয়ার)
            // =========================================================================
            HorizontalBottomBar(
                reel = reel,
                annotatedCaption = annotatedCaption,
                isSaved = isSaved,
                saveCount = saveCount,
                isLoggedIn = isLoggedIn,
                onRequireLogin = onRequireLogin,
                onToggleLike = onToggleLike,
                onCommentClick = onCommentClick,
                onSaveClick = {
                    if (!isLoggedIn) {
                        onRequireLogin()
                        return@HorizontalBottomBar
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
                },
                onShareClick = onShareClick,
                onFollowClick = onFollowClick,
                onOpenPageProfile = onOpenPageProfile,
                onHashtagClick = onHashtagClick,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
            )

            // =========================================================================
            // 📱 ডান প্রান্তের ড্র্যাগ জোন: ডান দিক থেকে বামে টানলে সাইডবার ড্রয়ার ওপেন
            // =========================================================================
            if (!isSidebarOpen) {
                Box(
                    modifier = Modifier
                        .width(34.dp)
                        .fillMaxHeight()
                        .align(Alignment.CenterEnd)
                        .draggable(
                            state = rememberDraggableState { delta ->
                                // 👈 ডান থেকে বামে টানলে সাইডবার ওপেন
                                if (delta < -12) {
                                    isSidebarOpen = true
                                }
                            },
                            orientation = Orientation.Horizontal
                        )
                )
            }

            // =========================================================================
            // 🔲 স্ক্রিনশটের হুবহু ডানপাশের ভিডিও প্লেলিস্ট সাইডবার
            // =========================================================================
            ReelsPlaylistSidebar(
                isOpen = isSidebarOpen,
                currentReel = reel,
                creatorReels = creatorReels,
                onSelectReel = { selectedReelItem ->
                    isSidebarOpen = false
                    onSelectReel(selectedReelItem)
                },
                onCloseSidebar = { isSidebarOpen = false },
                modifier = Modifier.align(Alignment.CenterEnd)
            )

            // নিচের টাইমলাইন প্রগ্রেস বার
            val progressFraction = if (totalDurationMs > 0) {
                (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.8.dp)
                    .align(Alignment.BottomCenter)
                    .background(Color.White.copy(alpha = 0.20f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = progressFraction)
                        .background(Color.White)
                )
            }
        }
    }
}
