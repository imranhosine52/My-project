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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.CreatorPlaylistDto
import com.example.data.model.ReelVideoQuality
import com.example.data.model.UserReelDto
import com.example.data.repository.ReelsRepository
import com.example.ui.screens.reels.actions.HorizontalBottomBar
import com.example.ui.screens.reels.actions.InstagramActionColumn
import com.example.ui.screens.reels.components.PlaylistEpisodesBottomSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// 🎨 নীল ও গ্রিন কালারের নিখুঁত কম্বিনেশন (লাল রঙ সম্পূর্ণ বর্জন করা হয়েছে)
private val CyanBlue = Color(0xFF00E5FF)
private val ActionGreen = Color(0xFF00E676)
private val DarkBarBg = Color(0xFF121620)

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
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }

    var isBuffering by remember { mutableStateOf(true) }
    var isPlayingState by remember { mutableStateOf(true) }

    var showBigHeartAnimation by remember { mutableStateOf(false) }
    var showPlayPauseIconState by remember { mutableStateOf<Boolean?>(null) }

    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }

    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }

    // ৩-সেকেন্ড জেনুইন ভিউ ফিল্টার
    var hasRecorded24hViewForThisPlayback by remember(reel.id) { mutableStateOf(false) }
    var hasRetriedFallback by remember(reel.id) { mutableStateOf(false) }

    // সিরিজ পর্ব তালিকা
    var showSeriesEpisodesDrawer by remember { mutableStateOf(false) }
    var seriesEpisodesList by remember { mutableStateOf<List<UserReelDto>>(emptyList()) }
    var otherPlaylistsList by remember { mutableStateOf<List<CreatorPlaylistDto>>(emptyList()) }
    var isSeriesLoading by remember { mutableStateOf(false) }

    // =========================================================================
    // 🎯 ২ নম্বর ছবি: স্মুথ অ্যানিমেটেড স্লাইডিং অফসেট স্টেট
    // =========================================================================
    val horizontalSlideOffset = remember { Animatable(0f) }

    LaunchedEffect(reel.playlistId) {
        val pId = reel.playlistId
        if (pId != null && pId > 0) {
            isSeriesLoading = true
            val res = repository.getPlaylistReels(pId)
            seriesEpisodesList = res.getOrDefault(emptyList())

            val targetPageId = if (reel.pageId > 0) reel.pageId.toLong() else reel.userId.toLong()
            val plRes = repository.getPlaylists(targetPageId)
            otherPlaylistsList = plRes.getOrDefault(emptyList())
            isSeriesLoading = false
        } else {
            seriesEpisodesList = emptyList()
        }
    }

    val currentEpisodeIndex = remember(seriesEpisodesList, reel.id) {
        seriesEpisodesList.indexOfFirst { it.id == reel.id }
    }

    val isVerticalTikTokRatio = remember(videoWidth, videoHeight) {
        if (videoWidth > 0 && videoHeight > 0) {
            (videoWidth.toFloat() / videoHeight.toFloat()) < 0.72f
        } else {
            true
        }
    }

    val currentIsSidebarOpen by rememberUpdatedState(isSidebarOpenState)
    val currentIsCommentsOpen by rememberUpdatedState(isCommentsOpen)

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

    BackHandler(enabled = isSidebarOpenState || showSeriesEpisodesDrawer) {
        if (showSeriesEpisodesDrawer) {
            showSeriesEpisodesDrawer = false
        } else {
            onSidebarStateChange(false)
        }
    }

    var isSaved by remember(reel.id, reel.isSaved) { mutableStateOf(reel.isSaved) }
    var saveCount by remember(reel.id, reel.isSaved) { mutableIntStateOf(if (reel.isSaved) 1 else 0) }

    var watchStartTimeMs by remember { mutableLongStateOf(0L) }
    var totalWatchDurationMs by remember { mutableLongStateOf(0L) }
    var hasCompleted100Percent by remember { mutableStateOf(false) }
    var loopCount by remember { mutableIntStateOf(0) }
    var isAlgorithmPingSent by remember { mutableStateOf(false) }

    val creatorReels = remember(reel, allReels) {
        val targetCreatorId = if (reel.pageId > 0) reel.pageId else reel.userId
        val list = allReels.filter {
            (it.pageId > 0 && it.pageId == targetCreatorId) || (it.userId > 0 && it.userId == targetCreatorId)
        }
        if (list.isNotEmpty()) list else listOf(reel)
    }

    val videoUrlToPlay = remember(reel.id, selectedQuality) {
        val customQuality = reel.getVideoUrlForQuality(selectedQuality)
        if (customQuality.isNotBlank()) customQuality else reel.videoUrl
    }

    val exoPlayer = remember(reel.id) {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(12000)
            .setReadTimeoutMs(15000)
            .setUserAgent("Mozilla/5.0 (Linux; Android 14) Chrome/120.0.0.0 Mobile Safari/537.36 PlayDramaFlix")

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(2000, 12000, 800, 1200)
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
                val mediaItem = MediaItem.fromUri(Uri.parse(videoUrlToPlay))
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                if (isActiveVideoPlaying) {
                    exoPlayer.play()
                }
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

    LaunchedEffect(isActiveVideoPlaying, isPlayingState) {
        while (isActiveVideoPlaying) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)

            if (!hasRecorded24hViewForThisPlayback && currentPositionMs >= 3000L) {
                hasRecorded24hViewForThisPlayback = true
                coroutineScope.launch {
                    repository.reelFeedRepository.recordReelViewLocal(reel.id)
                }
            }

            if (totalDurationMs > 2000L && currentPositionMs >= (totalDurationMs - 400L)) {
                hasCompleted100Percent = true
            }

            delay(150L)
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
                when (state) {
                    Player.STATE_BUFFERING -> isBuffering = true
                    Player.STATE_READY -> {
                        isBuffering = false
                        totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
                        if (isActiveVideoPlaying) exoPlayer.play()
                    }
                    Player.STATE_ENDED -> {
                        isBuffering = false
                        fireAlgorithmWatchTracking()

                        // পর্ব শেষ হওয়ামাত্রই পরের পর্বে যাওয়া
                        if (seriesEpisodesList.isNotEmpty() && currentEpisodeIndex != -1 && currentEpisodeIndex < seriesEpisodesList.size - 1) {
                            val nextEpisode = seriesEpisodesList[currentEpisodeIndex + 1]
                            onSelectReel(nextEpisode)
                            return
                        }

                        runCatching { onVideoCompleteAutoPlayNext() }
                    }
                    Player.STATE_IDLE -> {}
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoWidth = videoSize.width
                videoHeight = videoSize.height
                if (videoWidth > 0 && videoHeight > 0) isBuffering = false
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlayingState = playing
                if (playing) isBuffering = false
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                if (!hasRetriedFallback && reel.videoUrl.isNotBlank()) {
                    hasRetriedFallback = true
                    runCatching {
                        val fallbackItem = MediaItem.fromUri(Uri.parse(reel.videoUrl))
                        exoPlayer.setMediaItem(fallbackItem)
                        exoPlayer.prepare()
                        exoPlayer.play()
                    }
                }
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
                            color = CyanBlue,
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

        Toast.makeText(context, if (newSaveState) "Saved" else "Removed from saved", Toast.LENGTH_SHORT).show()

        coroutineScope.launch {
            val res = repository.toggleSaveReel(reel.id)
            if (res.isFailure) {
                isSaved = !newSaveState
                saveCount += if (newSaveState) -1 else 1
            }
        }
    }

    val progressFraction = if (totalDurationMs > 0) {
        (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    // =========================================================================
    // 🎯 ২ নম্বর ছবির স্মুথ অ্যানিমেটেড ড্র্যাগ হ্যান্ডলার (Gesture Physics)
    // =========================================================================
    val horizontalDragState = rememberDraggableState { delta ->
        if (!currentIsCommentsOpen) {
            coroutineScope.launch {
                val newOffset = (horizontalSlideOffset.value + delta).coerceIn(-screenWidthPx * 0.45f, screenWidthPx * 0.45f)
                horizontalSlideOffset.snapTo(newOffset)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            // ২ নম্বর ছবি: মসৃণ হরিজন্টাল স্লাইড অ্যানিমেশন
            .offset { IntOffset(horizontalSlideOffset.value.roundToInt(), 0) }
            .draggable(
                state = horizontalDragState,
                orientation = Orientation.Horizontal,
                onDragStopped = { velocity ->
                    coroutineScope.launch {
                        val currentX = horizontalSlideOffset.value
                        val hasSeries = seriesEpisodesList.isNotEmpty() && currentEpisodeIndex != -1

                        // বামে ড্র্যাগ (পরের পর্ব)
                        if ((currentX < -80f || velocity < -500f) && hasSeries && currentEpisodeIndex < seriesEpisodesList.size - 1) {
                            horizontalSlideOffset.animateTo(-screenWidthPx * 0.5f, tween(160, easing = FastOutLinearInEasing))
                            onSelectReel(seriesEpisodesList[currentEpisodeIndex + 1])
                            horizontalSlideOffset.snapTo(0f)
                        }
                        // ডানে ড্র্যাগ (পূর্বের পর্ব)
                        else if ((currentX > 80f || velocity > 500f) && hasSeries && currentEpisodeIndex > 0) {
                            horizontalSlideOffset.animateTo(screenWidthPx * 0.5f, tween(160, easing = FastOutLinearInEasing))
                            onSelectReel(seriesEpisodesList[currentEpisodeIndex - 1])
                            horizontalSlideOffset.snapTo(0f)
                        }
                        // সাইডবার টগল অথবা বাউন্স ব্যাক
                        else if (currentX < -100f && !currentIsSidebarOpen) {
                            horizontalSlideOffset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                            onSidebarStateChange(true)
                        } else {
                            horizontalSlideOffset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                        }
                    }
                }
            )
    ) {
        // ৩. ভিডিও প্লেয়ার
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
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = if (isVerticalTikTokRatio) {
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        } else {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    view.resizeMode = if (isVerticalTikTokRatio) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isBuffering) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CyanBlue, strokeWidth = 2.dp, modifier = Modifier.size(36.dp))
            }
        }

        // ৪. প্লে/পজ ও হার্ট অ্যানিমেশন
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
                    tint = ActionGreen.copy(alpha = 0.95f), // 🎯 লাল কালার বাদ দিয়ে গ্রিন
                    modifier = Modifier.size(96.dp).align(Alignment.Center).scale(1.25f)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.96f))))
            )

            AnimatedContent(
                targetState = isSidebarOpenState,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                label = "ReelsLayoutTransition",
                modifier = Modifier.fillMaxSize()
            ) { sidebarVisible ->
                if (!sidebarVisible) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // ডানপাশের অ্যাকশন কলাম
                        InstagramActionColumn(
                            reel = reel,
                            isSaved = isSaved,
                            saveCount = saveCount,
                            onLikeClick = { if (!isLoggedIn) onRequireLogin() else onToggleLike() },
                            onCommentClick = onCommentClick,
                            onSaveClick = { handleToggleSave() },
                            onShareClick = onShareClick,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .navigationBarsPadding()
                                .padding(end = 12.dp, bottom = 82.dp)
                        )

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(bottom = 50.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            // প্রোফাইল ইনফো ও ক্যাপশন
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 74.dp, bottom = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
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
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.clickable { onOpenPageProfile() }.weight(1f, fill = false)
                                    )

                                    // ফলো বাটন (নীল ও গ্রিন থিমে)
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (reel.isFollowing) Color(0x33FFFFFF) else CyanBlue,
                                        modifier = Modifier.clickable { if (!isLoggedIn) onRequireLogin() else onFollowClick() }
                                    ) {
                                        Text(
                                            text = if (reel.isFollowing) "Following" else "Follow",
                                            color = if (reel.isFollowing) Color.White else Color.Black,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                if (annotatedCaption.text.isNotBlank()) {
                                    ClickableText(
                                        text = annotatedCaption,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp
                                        ),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        onClick = { offset ->
                                            annotatedCaption.getStringAnnotations(tag = "HASHTAG", start = offset, end = offset)
                                                .firstOrNull()?.let { annotation -> onHashtagClick(annotation.item) }
                                        }
                                    )
                                }
                            }

                            // =========================================================================
                            // 🌟 ১ নম্বর ছবি: হুবহু ফুল-উইডথ ডকড প্লেলিস্ট বার (Playlist Bar at Bottom)
                            // =========================================================================
                            if (reel.playlistId != null && reel.playlistId > 0) {
                                Surface(
                                    color = DarkBarBg.copy(alpha = 0.95f),
                                    border = BorderStroke(0.6.dp, Color(0xFF1E2838)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showSeriesEpisodesDrawer = true }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.VideoLibrary,
                                                contentDescription = null,
                                                tint = CyanBlue,
                                                modifier = Modifier.size(17.dp)
                                            )
                                            Text(
                                                text = "Playlist • ${(reel.playlistTitle?.takeIf { it.isNotBlank() } ?: "SERIES").uppercase()} • Ep ${reel.episodeNum}",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                            contentDescription = "Open Drawer",
                                            tint = Color.White.copy(alpha = 0.8f),
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }

                            // টাইমলাইন প্রগ্রেস বার (নীল/গ্রিন গ্রেডিয়েন্ট)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.5.dp)
                                    .background(Color.White.copy(alpha = 0.25f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(fraction = progressFraction.coerceAtLeast(0.01f))
                                        .background(Brush.horizontalGradient(listOf(CyanBlue, ActionGreen)))
                                )
                            }
                        }
                    }
                } else {
                    // সাইডবার মোড
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 56.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .navigationBarsPadding(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
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
                                modifier = Modifier.fillMaxWidth()
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.5.dp)
                                    .background(Color.White.copy(alpha = 0.25f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(fraction = progressFraction.coerceAtLeast(0.01f))
                                        .background(Brush.horizontalGradient(listOf(CyanBlue, ActionGreen)))
                                )
                            }
                        }
                    }
                }
            }

            ReelsPlaylistSidebar(
                isOpen = isSidebarOpenState,
                currentReel = reel,
                creatorReels = creatorReels,
                isPlaying = isPlayingState,
                onTogglePlayPause = {
                    runCatching {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                    }
                },
                onSelectReel = { selectedReelItem ->
                    runCatching { onSelectReel(selectedReelItem) }
                },
                onCloseSidebar = { onSidebarStateChange(false) },
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }

        // =========================================================================
        // 📺 ৩ নম্বর ছবি: বটম শিট ড্রয়ার
        // =========================================================================
        if (showSeriesEpisodesDrawer && reel.playlistId != null && reel.playlistId > 0) {
            PlaylistEpisodesBottomSheet(
                seriesTitle = reel.playlistTitle?.ifBlank { "Mini-Drama" } ?: "Mini-Drama",
                currentReelId = reel.id,
                episodes = seriesEpisodesList,
                creatorPlaylists = otherPlaylistsList,
                isLoading = isSeriesLoading,
                isFavorite = isSaved,
                onToggleFavorite = {
                    if (!isLoggedIn) onRequireLogin()
                    else {
                        val n = !isSaved
                        isSaved = n
                        saveCount += if (n) 1 else -1
                        coroutineScope.launch { repository.toggleSaveReel(reel.id) }
                    }
                },
                onEpisodeClick = { targetEpisode -> onSelectReel(targetEpisode) },
                onSelectOtherPlaylist = { otherPl ->
                    coroutineScope.launch {
                        val res = repository.getPlaylistReels(otherPl.effectiveId)
                        val otherEps = res.getOrDefault(emptyList())
                        if (otherEps.isNotEmpty()) onSelectReel(otherEps.first())
                    }
                },
                onDismiss = { showSeriesEpisodesDrawer = false }
            )
        }
    }
}
