@file:OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    UnstableApi::class
)

package com.example.ui.screens.reels

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.example.data.model.CustomAdsConfigResponse
import com.example.data.model.CustomVideoAdDto
import com.example.data.model.UserReelDto
import com.example.data.model.UserStoryDto
import com.example.ui.components.CustomVideoAdDialog
import com.example.ui.viewmodel.DramaFlixViewModel
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale

@Composable
fun ReelsFeedScreen(
    viewModel: DramaFlixViewModel,
    onOpenCreateReel: () -> Unit,
    onOpenPageProfile: (pageId: Int) -> Unit,
    onNavigateToVip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val authState by viewModel.authUiState.collectAsStateWithLifecycle()
    val isUserVip = authState.isVip

    var activeTab by rememberSaveable { mutableStateOf("for_you") } // "for_you" | "following"
    var reelsList by remember { mutableStateOf<List<UserReelDto>>(emptyList()) }
    var storiesList by remember { mutableStateOf<List<UserStoryDto>>(emptyList()) }
    var isLoadingFeed by remember { mutableStateOf(true) }

    // কাস্টম বিজ্ঞাপন স্টেট
    var customAdsConfig by remember { mutableStateOf<CustomAdsConfigResponse?>(null) }
    var activeCustomAd by remember { mutableStateOf<CustomVideoAdDto?>(null) }
    var watchedReelsCounter by rememberSaveable { mutableIntStateOf(0) }

    // স্টোরি ভিউয়ার স্টেট
    var viewingStoryInitialIndex by remember { mutableStateOf<Int?>(null) }

    // এপিআই থেকে বিজ্ঞাপন কনফিগ লোড
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val req = Request.Builder().url("https://playdramaflix.com/api/v1/custom-ads").build()
                val res = client.newCall(req).execute()
                val body = res.body?.string().orEmpty()
                if (res.isSuccessful && body.isNotBlank()) {
                    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                    customAdsConfig = moshi.adapter(CustomAdsConfigResponse::class.java).fromJson(body)
                }
            } catch (_: Exception) {}
        }
    }

    val availableAds = remember(customAdsConfig) {
        customAdsConfig?.ads?.filter { it.placement == "shorts" || it.placement == "all" } ?: emptyList()
    }

    // রিলস ও স্টোরি ফেচিং
    fun loadFeedData() {
        coroutineScope.launch {
            isLoadingFeed = true
            val reelsRes = viewModel.repository.getReelsFeed(tab = activeTab, page = 1)
            val storiesRes = viewModel.repository.getActiveStories()

            reelsList = reelsRes.getOrDefault(emptyList())
            storiesList = storiesRes.getOrDefault(emptyList())
            isLoadingFeed = false
        }
    }

    LaunchedEffect(activeTab) {
        loadFeedData()
    }

    // গ্যালারি থেকে স্টোরি আপলোড পিকার
    val storyPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val isVideo = context.contentResolver.getType(uri)?.contains("video", true) == true
            coroutineScope.launch {
                Toast.makeText(context, "Uploading story...", Toast.LENGTH_SHORT).show()
                val result = viewModel.repository.uploadStory(
                    caption = "My Story",
                    mediaUri = uri,
                    isVideo = isVideo
                )
                if (result.isSuccess) {
                    Toast.makeText(context, "✓ Story shared successfully!", Toast.LENGTH_SHORT).show()
                    loadFeedData()
                } else {
                    Toast.makeText(context, "Failed to upload story", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { reelsList.size })

    // 🎯 প্রতি ৪টি রিলস পর পর বিজ্ঞাপন ট্রিগার চেকার
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage > 0) {
            watchedReelsCounter++
            if (!isUserVip && customAdsConfig?.customAdsEnabled == true && availableAds.isNotEmpty()) {
                val interval = customAdsConfig?.shortsRules?.intervalEpisodes ?: 4
                if (watchedReelsCounter % interval == 0) {
                    activeCustomAd = availableAds.random()
                }
            }
            // ভিউ কাউন্ট এপিআই কল
            reelsList.getOrNull(pagerState.currentPage)?.let { currentReel ->
                viewModel.repository.interactReel(currentReel.id, "view")
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isLoadingFeed && reelsList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF00E676), strokeWidth = 3.dp)
            }
        } else if (reelsList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Movie, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(54.dp))
                    Text(
                        text = if (activeTab == "following") "No reels from followed creators yet" else "No reels available right now",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = onOpenCreateReel,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Create First Reel +", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            // =========================================================================
            // 🎬 ১. মূল উল্লম্ব পেজার (TikTok/Instagram Reels)
            // =========================================================================
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                flingBehavior = PagerDefaults.flingBehavior(state = pagerState)
            ) { pageIndex ->
                val reel = reelsList[pageIndex]
                val isCurrentVideoPlaying = (pagerState.currentPage == pageIndex) && (activeCustomAd == null)

                SingleReelPlayerPage(
                    reel = reel,
                    isPlaying = isCurrentVideoPlaying,
                    onDoubleTapLike = {
                        coroutineScope.launch {
                            viewModel.repository.interactReel(reel.id, "like")
                        }
                    },
                    onToggleLike = {
                        coroutineScope.launch {
                            viewModel.repository.interactReel(reel.id, "like")
                        }
                    },
                    onOpenComments = {
                        Toast.makeText(context, "Comments on reels", Toast.LENGTH_SHORT).show()
                    },
                    onShareClick = {
                        coroutineScope.launch {
                            viewModel.repository.interactReel(reel.id, "share")
                        }
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Watch ${reel.title} on PlayDramaFlix Reels: ${reel.videoUrl}")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Reel"))
                    },
                    onOpenPageProfile = { onOpenPageProfile(reel.pageId) }
                )
            }

            // =========================================================================
            // 🔝 ২. ওপরে ভাসমান টপ বার: [ For You | Following ] + ক্যামেরা + স্টোরি বার
            // =========================================================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // ক্যামেরা / রিলস আপলোড বাটন
                    IconButton(
                        onClick = onOpenCreateReel,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = "Create Reel", tint = Color.White, modifier = Modifier.size(20.dp))
                    }

                    // [ Following | For You ] ট্যাব সুইচ
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Following",
                            color = if (activeTab == "following") Color.White else Color.White.copy(alpha = 0.6f),
                            fontSize = 16.sp,
                            fontWeight = if (activeTab == "following") FontWeight.Black else FontWeight.Bold,
                            modifier = Modifier.clickable { activeTab = "following" }
                        )

                        Text(
                            text = "|",
                            color = Color.White.copy(alpha = 0.3f),
                            fontSize = 14.sp
                        )

                        Text(
                            text = "For You",
                            color = if (activeTab == "for_you") Color.White else Color.White.copy(alpha = 0.6f),
                            fontSize = 16.sp,
                            fontWeight = if (activeTab == "for_you") FontWeight.Black else FontWeight.Bold,
                            modifier = Modifier.clickable { activeTab = "for_you" }
                        )
                    }

                    // সার্চ আইকন
                    IconButton(
                        onClick = { Toast.makeText(context, "Search Reels", Toast.LENGTH_SHORT).show() },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White, modifier = Modifier.size(19.dp))
                    }
                }

                // 🌟 ইনস্টাগ্রাম স্টাইল স্টোরি ক্যারোজেল বার
                StoryCarouselBar(
                    currentUserAvatar = authState.userProfile?.avatar,
                    currentUserName = authState.userProfile?.displayName ?: "User",
                    stories = storiesList,
                    onAddStoryClick = {
                        storyPickerLauncher.launch("image/*,video/*")
                    },
                    onStoryClick = { index ->
                        viewingStoryInitialIndex = index
                    }
                )
            }
        }

        // =========================================================================
        // ⏱️ ৩. ২৪ ঘণ্টার স্টোরি ভিউয়ার ডায়ালগ
        // =========================================================================
        viewingStoryInitialIndex?.let { startIdx ->
            StoryViewerDialog(
                stories = storiesList,
                initialStoryIndex = startIdx,
                onDismiss = { viewingStoryInitialIndex = null }
            )
        }

        // =========================================================================
        // 📢 ৪. ৪টি রিলস পর পর কাস্টম ভিডিও বিজ্ঞাপন
        // =========================================================================
        activeCustomAd?.let { ad ->
            CustomVideoAdDialog(
                ad = ad,
                onAdFinishedOrSkipped = {
                    activeCustomAd = null
                },
                onNavigateInternalScreen = { target ->
                    activeCustomAd = null
                    if (target.contains("vip", true)) onNavigateToVip()
                },
                onOpenExternalUrl = { url ->
                    activeCustomAd = null
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                }
            )
        }
    }
}

// =============================================================================
// 🎬 একক রিলস প্লেয়ার কম্পোনেন্ট
// =============================================================================
@Composable
private fun SingleReelPlayerPage(
    reel: UserReelDto,
    isPlaying: Boolean,
    onDoubleTapLike: () -> Unit,
    onToggleLike: () -> Unit,
    onOpenComments: () -> Unit,
    onShareClick: () -> Unit,
    onOpenPageProfile: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isLikedState by remember(reel.id) { mutableStateOf(reel.isLiked) }
    var likesCountState by remember(reel.id) { mutableLongStateOf(reel.likesCount) }
    var showHeartAnimation by remember { mutableStateOf(false) }

    // ⚡ ২X স্পিড চাপ দিয়ে ধরে রাখার স্টেট
    var is2xHoldActive by remember { mutableStateOf(false) }

    val exoPlayer = remember(reel.id, reel.videoUrl) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(1500, 15000, 800, 1200)
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_ALL
                playWhenReady = true
                setAudioAttributes(
                    AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).setUsage(C.USAGE_MEDIA).build(),
                    true
                )
                setMediaItem(MediaItem.fromUri(reel.videoUrl))
                prepare()
            }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) exoPlayer.play() else exoPlayer.pause()
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // 🎯 স্ক্রিনের ওপর চাপ দিয়ে ধরে রাখলে 2X গতিতে চলা ও ডাবল-ট্যাপ লাইক
            .pointerInput(reel.id) {
                detectTapGestures(
                    onPress = {
                        var holdJob: Job? = null
                        try {
                            holdJob = coroutineScope.launch {
                                delay(350)
                                is2xHoldActive = true
                                exoPlayer.setPlaybackSpeed(2.0f)
                            }
                            tryAwaitRelease()
                        } finally {
                            holdJob?.cancel()
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
                        if (!isLikedState) {
                            isLikedState = true
                            likesCountState++
                        }
                        showHeartAnimation = true
                        onDoubleTapLike()
                        coroutineScope.launch {
                            delay(800)
                            showHeartAnimation = false
                        }
                    }
                )
            }
    ) {
        // ভিডিও সারফেস
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

        // ⚡ ২X স্পিড টেক্সট (কোনো ব্যাকগ্রাউন্ড ছাড়া)
        if (is2xHoldActive) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 100.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                Text("2X Speed", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }

        // ডাবল-ট্যাপ হার্ট পপ-আপ অ্যানিমেশন
        if (showHeartAnimation) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = Color(0xFFFF2A4B).copy(alpha = 0.9f),
                modifier = Modifier
                    .size(90.dp)
                    .align(Alignment.Center)
                    .scale(1.2f)
            )
        }

        // =========================================================================
        // 👉 ডানের অ্যাকশন বার (Like, Comment, Share, Creator Profile)
        // =========================================================================
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 12.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // পেজ অবতার ও ফলো বাটন
            Box(contentAlignment = Alignment.BottomCenter) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, Color.White, CircleShape)
                        .clickable { onOpenPageProfile() }
                ) {
                    AsyncImage(
                        model = reel.pageAvatar ?: "https://ui-avatars.com/api/?name=${reel.pageName}&background=00E676&color=000",
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // ❤️ লাইক বাটন
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = {
                        isLikedState = !isLikedState
                        if (isLikedState) likesCountState++ else likesCountState--
                        onToggleLike()
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isLikedState) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (isLikedState) Color(0xFFFF2A4B) else Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Text(
                    text = formatCountDisplay(likesCountState),
                    color = Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // 💬 কমেন্ট বাটন
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onOpenComments, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Comments", tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Text(
                    text = formatCountDisplay(reel.commentsCount.toLong()),
                    color = Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // ↗️ শেয়ার বাটন
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onShareClick, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Outlined.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Text(
                    text = formatCountDisplay(reel.sharesCount.toLong()),
                    color = Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // =========================================================================
        // 📝 নিচের টাইটেল ও পেজের বিবরণ
        // =========================================================================
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 14.dp, bottom = 24.dp, end = 80.dp),
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
                    text = reel.handle,
                    color = Color(0xFF00E676),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // ক্যাপশন
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

private fun formatCountDisplay(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
