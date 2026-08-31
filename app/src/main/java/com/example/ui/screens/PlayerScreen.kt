@file:OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.ads.StartAppBanner
import com.example.ads.StartIoAdManager
import com.example.ads.UnifiedAdManager
import com.example.data.model.ContentItemDto
import com.example.data.model.DramaApiComment
import com.example.data.model.EpisodeDto
import com.example.ui.*
import com.example.ui.components.AuthBottomSheetDialog
import com.example.ui.components.UnlockEpisodeDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.DramaFlixViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class PlayerTab {
    FOR_YOU,
    COMMENTS
}

private fun isWebEmbedUrl(url: String): Boolean {
    val lower = url.lowercase()
    return lower.contains("/e/") ||
            lower.contains("/embed") ||
            lower.contains("streamtape") ||
            lower.contains("byse.sx") ||
            lower.contains("youtube.com/embed") ||
            lower.contains("youtu.be") ||
            lower.contains("drive.google.com") ||
            lower.endsWith(".html") ||
            lower.endsWith(".htm") ||
            lower.contains("playdramaflix.com/player")
}

private fun buildMediaItemForUrl(url: String): MediaItem {
    val uri = Uri.parse(url)
    val lower = url.lowercase()
    val builder = MediaItem.Builder().setUri(uri)
    if (lower.contains(".m3u8") || lower.contains("hls")) {
        builder.setMimeType(MimeTypes.APPLICATION_M3U8)
    } else if (lower.contains(".mpd") || lower.contains("dash")) {
        builder.setMimeType(MimeTypes.APPLICATION_MPD)
    } else if (lower.contains(".mp4")) {
        builder.setMimeType(MimeTypes.APPLICATION_MP4)
    }
    return builder.build()
}

@Composable
fun EqualizerBarsIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF00D166)
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(modifier = Modifier.width(2.2.dp).height(6.dp).background(tint, RoundedCornerShape(1.dp)))
        Box(modifier = Modifier.width(2.2.dp).height(12.dp).background(tint, RoundedCornerShape(1.dp)))
        Box(modifier = Modifier.width(2.2.dp).height(4.dp).background(tint, RoundedCornerShape(1.dp)))
    }
}

@Composable
fun PlayerScreen(
    slug: String,
    viewModel: DramaFlixViewModel,
    onBackClick: () -> Unit,
    onNavigateToVip: () -> Unit,
    onRelatedDramaClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val playerState by viewModel.playerUiState.collectAsStateWithLifecycle()
    val authState by viewModel.authUiState.collectAsStateWithLifecycle()
    val homeState by viewModel.homeUiState.collectAsStateWithLifecycle()

    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }
    var showAuthSheet by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var useWebPlayerFallback by remember { mutableStateOf(false) }
    var activeStreamUrl by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(PlayerTab.FOR_YOU) }
    var inlineCommentText by remember { mutableStateOf("") }

    // 💬 থ্রেডেড রিপ্লাই কমেন্ট মোড স্টেট
    var selectedThreadParentComment by remember { mutableStateOf<DramaApiComment?>(null) }
    var threadReplyText by remember { mutableStateOf("") }

    // 📜 ইনলাইন ডেসক্রিপশন এক্সপ্যান্ড স্টেট
    var isDescriptionExpanded by remember { mutableStateOf(false) }

    // 🔄 Pull-To-Refresh State
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    val isAdsGloballyActive = remember { UnifiedAdManager.isAdsGloballyEnabled() }
    val shouldLockEpisodes = !playerState.isVip && isAdsGloballyActive

    val currentUser = authState.userProfile
    val currentUserName = currentUser?.displayName ?: "User"
    val currentUserAvatar = currentUser?.avatar ?: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=120&q=80"
    val userInitials = remember(currentUserName) {
        val parts = currentUserName.trim().split(" ").filter { it.isNotBlank() }
        if (parts.size >= 2) "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
        else currentUserName.take(2).uppercase()
    }

    // ↗️ Android Native Share Bottom Sheet
    fun shareDramaLink() {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Watch ${playerState.content?.title ?: "Asian Drama"} on PlayDramaFlix: https://playdramaflix.com/watch/$slug")
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share with friends via"))
        } catch (_: Exception) {
            Toast.makeText(context, "Cannot open share options", Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler {
        if (selectedThreadParentComment != null) {
            selectedThreadParentComment = null
        } else {
            onBackClick()
        }
    }

    LaunchedEffect(slug) {
        viewModel.loadDramaDetails(slug, context)
    }

    val exoPlayer = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setUserAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 PlayDramaFlix/2.0")

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_OFF
            }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    playbackError = null
                    totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
                } else if (state == Player.STATE_ENDED) {
                    viewModel.playNextEpisode()
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                if (isWebEmbedUrl(activeStreamUrl)) {
                    useWebPlayerFallback = true
                    playbackError = null
                } else {
                    playbackError = "Stream notice: Tap to retry or try Web Player."
                }
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
            val activity = context as? Activity
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // 🎯 নির্দিষ্ট এপিসোড লিঙ্ক পরিবর্তন ও প্লেব্যাক লজিক (১০০% ফিক্সড)
    LaunchedEffect(playerState.currentEpisode?.episodeNumber, playerState.currentEpisode?.episodeId, playerState.selectedServer) {
        val currentEp = playerState.currentEpisode
        val content = playerState.content
        if (currentEp != null && content != null) {
            playbackError = null
            if (shouldLockEpisodes && currentEp.isLocked) {
                exoPlayer.pause()
                viewModel.showEpisodeUnlockModal(currentEp)
                return@LaunchedEffect
            }

            // প্রতিটি এপিসোডের নির্দিষ্ট লিঙ্ক নির্ধারণ
            val videoUrl = currentEp.videoUrl?.takeIf { it.isNotBlank() }
                ?: currentEp.embedUrl?.takeIf { it.isNotBlank() }
                ?: "https://byse.sx/e/${content.slug}_ep_${currentEp.episodeNumber}"

            activeStreamUrl = videoUrl

            if (isWebEmbedUrl(videoUrl) || playerState.selectedServer?.type.equals("embed", ignoreCase = true)) {
                useWebPlayerFallback = true
                exoPlayer.pause()
            } else {
                useWebPlayerFallback = false
                try {
                    exoPlayer.stop()
                    exoPlayer.clearMediaItems()
                    val mediaItem = buildMediaItemForUrl(videoUrl)
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                } catch (_: Exception) {
                    useWebPlayerFallback = true
                }
            }
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
            if (totalDurationMs > 0) {
                viewModel.updateWatchProgress(currentPositionMs, totalDurationMs)
            }
            delay(1000L)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (playerState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = TealAccent, strokeWidth = 3.dp, modifier = Modifier.size(44.dp))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // 1. 🎬 Video Player Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                ) {
                    if (useWebPlayerFallback && activeStreamUrl.isNotBlank()) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        mediaPlaybackRequiresUserGesture = false
                                        allowFileAccess = false
                                    }
                                    webViewClient = object : WebViewClient() {
                                        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                                            view?.destroy()
                                            return true
                                        }
                                    }
                                    webChromeClient = WebChromeClient()
                                    loadUrl(activeStreamUrl)
                                }
                            },
                            update = { webView ->
                                if (webView.url != activeStreamUrl) {
                                    webView.loadUrl(activeStreamUrl)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = exoPlayer
                                    useController = true
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // ✨ Clean Transparent Top Icons (কোনো ডার্ক ব্যাকগ্রাউন্ড ছাড়া)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(24.dp))
                        }

                        IconButton(onClick = { shareDramaLink() }) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    }
                }

                // 2. 🔄 Details & Comments View / Dedicated Thread View
                if (selectedThreadParentComment != null) {
                    CommentRepliesThreadView(
                        parentComment = selectedThreadParentComment!!,
                        dramaContent = playerState.content,
                        currentUserAvatar = currentUserAvatar,
                        userInitials = userInitials,
                        replyText = threadReplyText,
                        onReplyTextChange = { threadReplyText = it },
                        onBackClick = { selectedThreadParentComment = null },
                        onSendReply = {
                            val text = threadReplyText.trim()
                            if (text.isNotBlank()) {
                                viewModel.postComment(text, parentId = selectedThreadParentComment!!.id)
                                threadReplyText = ""
                                keyboardController?.hide()
                            }
                        },
                        onLikeComment = { commentId -> viewModel.toggleCommentLike(commentId) }
                    )
                } else {
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = {
                            coroutineScope.launch {
                                isRefreshing = true
                                viewModel.loadDramaDetails(slug, context)
                                viewModel.refreshComments()
                                delay(500)
                                isRefreshing = false
                            }
                        },
                        state = pullRefreshState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF0C0F15)),
                            contentPadding = PaddingValues(bottom = 32.dp)
                        ) {
                            val content = playerState.content
                            val currentEp = playerState.currentEpisode

                            if (content != null) {
                                // Title & Pre/Next Buttons Row
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = content.title,
                                            color = TextPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 8.dp)
                                        )

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Pre Button
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFF161A23),
                                                border = BorderStroke(0.8.dp, Color(0xFF2B3346)),
                                                modifier = Modifier.clickable {
                                                    StartIoAdManager.showInterstitial(context, isVip = playerState.isVip) {
                                                        viewModel.playPreviousEpisode()
                                                    }
                                                }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Icon(Icons.Default.SkipPrevious, contentDescription = "Pre", tint = Color(0xFFB0B7C6), modifier = Modifier.size(13.dp))
                                                    Text("Pre", color = Color(0xFFB0B7C6), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            // Next Button
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFF161A23),
                                                border = BorderStroke(0.8.dp, Color(0xFF2B3346)),
                                                modifier = Modifier.clickable {
                                                    StartIoAdManager.showInterstitial(context, isVip = playerState.isVip) {
                                                        viewModel.playNextEpisode()
                                                    }
                                                }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Text("Next", color = Color(0xFFB0B7C6), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color(0xFFB0B7C6), modifier = Modifier.size(13.dp))
                                                }
                                            }
                                        }
                                    }
                                }

                                // Metadata Row (📅 2020 • ⭐ 8.9 • less/...more)
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Text("📅", fontSize = 11.sp)
                                            Text(content.releaseYear.ifBlank { "2020" }, color = Color(0xFF8E95A5), fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                                            Text("•", color = Color(0xFF4C5466), fontSize = 11.sp)
                                            Icon(Icons.Default.Star, contentDescription = null, tint = GoldVip, modifier = Modifier.size(13.dp))
                                            Text(if (content.rating > 0) String.format("%.1f", content.rating) else "8.9", color = GoldVip, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                            Text("•", color = Color(0xFF4C5466), fontSize = 11.sp)

                                            Text(
                                                text = if (isDescriptionExpanded) "less" else "...more",
                                                color = TealAccent,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                                                    .padding(2.dp)
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            // Like
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                modifier = Modifier.clickable {
                                                    if (!authState.isLoggedIn) showAuthSheet = true else viewModel.toggleLikeDrama()
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = if (playerState.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                    contentDescription = "Like",
                                                    tint = if (playerState.isLiked) Color(0xFFFF4B72) else Color(0xFFADB3C2),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text("${playerState.likesCount.coerceAtLeast(1)}", color = Color(0xFFADB3C2), fontSize = 11.sp)
                                            }

                                            // Views
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Text("👁", fontSize = 11.sp)
                                                Text("${playerState.viewsCount.coerceAtLeast(48L)}", color = Color(0xFFADB3C2), fontSize = 11.sp)
                                            }

                                            // Comments
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                modifier = Modifier.clickable {
                                                    selectedTab = PlayerTab.COMMENTS
                                                    viewModel.refreshComments()
                                                }
                                            ) {
                                                Text("💬", fontSize = 11.sp)
                                                Text("${playerState.comments.size}", color = Color(0xFFADB3C2), fontSize = 11.sp)
                                            }

                                            // Bookmark
                                            Icon(
                                                imageVector = if (playerState.isInWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                                contentDescription = "Bookmark",
                                                tint = if (playerState.isInWatchlist) TealAccent else Color(0xFFADB3C2),
                                                modifier = Modifier
                                                    .size(15.dp)
                                                    .clickable { viewModel.toggleWatchlist() }
                                            )
                                        }
                                    }
                                }

                                // 📜 ইনলাইন এক্সপ্যান্ডেবল ডেসক্রিপশন সেকশন
                                item {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        AnimatedVisibility(
                                            visible = isDescriptionExpanded,
                                            enter = expandVertically() + fadeIn(),
                                            exit = shrinkVertically() + fadeOut()
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = content.description?.takeIf { it.isNotBlank() } ?: content.synopsis,
                                                    color = Color(0xFFCCD0DB),
                                                    fontSize = 12.sp,
                                                    lineHeight = 17.sp
                                                )
                                                Text(
                                                    text = "📌 Language: ${content.dubBadge.ifBlank { content.language }}\n📌 Quality: ${content.quality}\n📌 Episodes: ${content.totalEpisodes}",
                                                    color = Color(0xFF94A3B8),
                                                    fontSize = 11.sp,
                                                    lineHeight = 16.sp,
                                                    modifier = Modifier.padding(top = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // 🔘 ২ নম্বর স্ক্রিনশটের মতো লম্বাটে সাইজের প্রিমিয়াম এপিসোড বাটন
                                if (playerState.episodes.isNotEmpty()) {
                                    item {
                                        LazyRow(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            items(playerState.episodes) { ep ->
                                                val isSelected = currentEp?.episodeNumber == ep.episodeNumber
                                                val isEpLocked = shouldLockEpisodes && ep.isLocked

                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (isSelected) Color(0xFF0F261C) else Color(0xFF131722),
                                                    border = BorderStroke(
                                                        width = if (isSelected) 1.5.dp else 1.dp,
                                                        color = if (isSelected) Color(0xFF00D166) else Color(0xFF222838)
                                                    ),
                                                    modifier = Modifier
                                                        .widthIn(min = 84.dp) // 👈 লম্বা সাইজ
                                                        .clickable {
                                                            if (isEpLocked) {
                                                                viewModel.showEpisodeUnlockModal(ep)
                                                            } else {
                                                                viewModel.selectEpisode(ep)
                                                            }
                                                        }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            text = "EP ${ep.episodeNumber}",
                                                            color = if (isSelected) Color(0xFF00D166) else Color.White,
                                                            fontSize = 12.5.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )

                                                        Spacer(modifier = Modifier.width(8.dp))

                                                        if (isSelected) {
                                                            EqualizerBarsIcon(modifier = Modifier.size(11.dp, 13.dp), tint = Color(0xFF00D166))
                                                        } else if (isEpLocked) {
                                                            Icon(Icons.Default.Lock, contentDescription = "Locked", tint = GoldVip, modifier = Modifier.size(13.dp))
                                                        } else {
                                                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF8E95A5), modifier = Modifier.size(14.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Start.io Banner Ad
                                item {
                                    StartAppBanner(
                                        isVip = playerState.isVip,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 4.dp)
                                    )
                                }

                                // Tabs: For you & Comments
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.clickable { selectedTab = PlayerTab.FOR_YOU }) {
                                            Text(
                                                text = "For you",
                                                color = if (selectedTab == PlayerTab.FOR_YOU) Color.White else Color(0xFF8E95A5),
                                                fontSize = 13.5.sp,
                                                fontWeight = if (selectedTab == PlayerTab.FOR_YOU) FontWeight.Bold else FontWeight.Medium
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .height(2.5.dp)
                                                    .width(42.dp)
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(if (selectedTab == PlayerTab.FOR_YOU) Color.White else Color.Transparent)
                                            )
                                        }

                                        Column(modifier = Modifier.clickable {
                                            selectedTab = PlayerTab.COMMENTS
                                            viewModel.refreshComments()
                                        }) {
                                            Text(
                                                text = "Comments(${playerState.comments.size})",
                                                color = if (selectedTab == PlayerTab.COMMENTS) Color.White else Color(0xFF8E95A5),
                                                fontSize = 13.5.sp,
                                                fontWeight = if (selectedTab == PlayerTab.COMMENTS) FontWeight.Bold else FontWeight.Medium
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .height(2.5.dp)
                                                    .width(55.dp)
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(if (selectedTab == PlayerTab.COMMENTS) Color.White else Color.Transparent)
                                            )
                                        }
                                    }
                                }

                                // Tab 1: For You
                                if (selectedTab == PlayerTab.FOR_YOU) {
                                    val combinedList = (playerState.recommendations + homeState.popularDramas)
                                        .distinctBy { it.slug }
                                        .filter { it.slug != slug }
                                    val dramaRows = combinedList.chunked(3)

                                    items(dramaRows) { rowDramas ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 5.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            rowDramas.forEach { drama ->
                                                Box(modifier = Modifier.weight(1f)) {
                                                    DramaPosterCardHorizontal(
                                                        drama = drama,
                                                        onClick = { onRelatedDramaClick(drama.slug) },
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                            }
                                            repeat(3 - rowDramas.size) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }

                                // Tab 2: Comments (Screenshot 2 Style Feed)
                                if (selectedTab == PlayerTab.COMMENTS) {
                                    item {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF161F30)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(userInitials, color = Color(0xFFFFC107), fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(42.dp)
                                                    .clip(RoundedCornerShape(21.dp))
                                                    .background(Color(0xFF131926))
                                                    .padding(horizontal = 16.dp),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                if (inlineCommentText.isEmpty()) {
                                                    Text("Add a comment...", color = Color(0xFF64748B), fontSize = 13.5.sp)
                                                }
                                                BasicTextField(
                                                    value = inlineCommentText,
                                                    onValueChange = { inlineCommentText = it },
                                                    textStyle = TextStyle(color = Color.White, fontSize = 13.5.sp),
                                                    cursorBrush = SolidColor(Color(0xFFFFC107)),
                                                    singleLine = true,
                                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                                    keyboardActions = KeyboardActions(onSend = {
                                                        val text = inlineCommentText.trim()
                                                        if (text.isNotBlank()) {
                                                            viewModel.postComment(text)
                                                            inlineCommentText = ""
                                                            keyboardController?.hide()
                                                        }
                                                    }),
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    val text = inlineCommentText.trim()
                                                    if (text.isNotBlank()) {
                                                        viewModel.postComment(text)
                                                        inlineCommentText = ""
                                                        keyboardController?.hide()
                                                    }
                                                },
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFFFC107))
                                            ) {
                                                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.Black, modifier = Modifier.size(19.dp))
                                            }
                                        }
                                    }

                                    if (playerState.comments.isEmpty()) {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 36.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("No comments yet. Be the first to comment!", color = Color(0xFF64748B), fontSize = 13.sp)
                                            }
                                        }
                                    } else {
                                        items(playerState.comments, key = { it.id }) { comment ->
                                            ModernCommentRowItem(
                                                comment = comment,
                                                onLike = { viewModel.toggleCommentLike(comment.id) },
                                                onOpenReplies = { selectedThreadParentComment = comment },
                                                onShare = {
                                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(Intent.EXTRA_TEXT, "${comment.displayName}: ${comment.commentText}")
                                                    }
                                                    context.startActivity(Intent.createChooser(shareIntent, "Share comment"))
                                                }
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

        if (showAuthSheet) {
            AuthBottomSheetDialog(
                viewModel = viewModel,
                onDismiss = { showAuthSheet = false }
            )
        }

        if (playerState.showEpisodeUnlockModal && playerState.lockedEpisodeTarget != null) {
            val lockedTarget = playerState.lockedEpisodeTarget!!
            UnlockEpisodeDialog(
                episode = lockedTarget,
                dramaSlug = slug,
                isVip = playerState.isVip,
                onDismiss = { viewModel.dismissEpisodeUnlockModal() },
                onWatchAdSuccess = {
                    viewModel.unlockEpisodeWithRewardAd(context, slug, lockedTarget)
                    Toast.makeText(context, "Episode ${lockedTarget.episodeNumber} unlocked for 2 hours!", Toast.LENGTH_LONG).show()
                },
                onUpgradeVipClick = {
                    viewModel.dismissEpisodeUnlockModal()
                    onNavigateToVip()
                }
            )
        }
    }
}

// -------------------------------------------------------------
// 💬 Comment Row Item (Screenshot 2 Style)
// -------------------------------------------------------------
@Composable
private fun ModernCommentRowItem(
    comment: DramaApiComment,
    onLike: () -> Unit,
    onOpenReplies: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    val name = comment.displayName

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenReplies() }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF334155)),
                contentAlignment = Alignment.Center
            ) {
                if (!comment.userAvatar.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(comment.userAvatar)
                            .crossfade(true)
                            .build(),
                        contentDescription = name,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = name.take(2).uppercase(),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(name, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                    Text(comment.displayDate, color = Color(0xFF64748B), fontSize = 11.5.sp)
                }

                Text(comment.commentText, color = Color(0xFFE2E8F0), fontSize = 13.5.sp, lineHeight = 18.sp)

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Like
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.clickable { onLike() }
                    ) {
                        Icon(
                            imageVector = if (comment.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (comment.isLiked) Color(0xFFFF4B72) else Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                        if (comment.likesCount > 0) {
                            Text("${comment.likesCount}", color = Color(0xFF94A3B8), fontSize = 11.5.sp)
                        }
                    }

                    // Open Reply Thread
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.clickable { onOpenReplies() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = "Replies",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                        if (comment.repliesCount > 0) {
                            Text("${comment.repliesCount}", color = Color(0xFF94A3B8), fontSize = 11.5.sp)
                        }
                    }

                    // Share
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onShare() }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = Color(0xFF161F2E), thickness = 0.5.dp)
    }
}

// -------------------------------------------------------------
// 💬 DEDICATED REPLIES THREAD VIEW (Screenshot 3 Style)
// -------------------------------------------------------------
@Composable
private fun CommentRepliesThreadView(
    parentComment: DramaApiComment,
    dramaContent: ContentItemDto?,
    currentUserAvatar: String,
    userInitials: String,
    replyText: String,
    onReplyTextChange: (String) -> Unit,
    onBackClick: () -> Unit,
    onSendReply: () -> Unit,
    onLikeComment: (String) -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0D14))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF161F30))
                    .clickable { onBackClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF334155)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!parentComment.userAvatar.isNullOrBlank()) {
                                AsyncImage(
                                    model = parentComment.userAvatar,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(parentComment.displayName.take(2).uppercase(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Column {
                            Text(parentComment.displayName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(parentComment.displayDate, color = Color(0xFF64748B), fontSize = 11.sp)
                        }
                    }

                    Text(parentComment.commentText, color = Color.White, fontSize = 14.sp, lineHeight = 19.sp)

                    if (dramaContent != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF141C2B))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black)
                            ) {
                                AsyncImage(
                                    model = dramaContent.posterUrl ?: dramaContent.bannerUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(dramaContent.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("📀 ${dramaContent.releaseYear} • Streaming", color = Color(0xFFFFC107), fontSize = 10.5.sp)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFFF4B72), modifier = Modifier.size(15.dp))
                            Text("${parentComment.likesCount.coerceAtLeast(1)}", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(15.dp))
                            Text("${parentComment.repliesList.size}", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        }
                    }
                }
            }

            item {
                Text("${parentComment.repliesList.size} Comments", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            }

            if (parentComment.repliesList.isEmpty()) {
                item {
                    Text("No replies yet. Be the first to reply!", color = Color(0xFF64748B), fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
                }
            } else {
                items(parentComment.repliesList, key = { it.id }) { reply ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF8E24AA)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(reply.displayName.take(2).uppercase(), color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }

                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(reply.displayName, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                Text(reply.displayDate, color = Color(0xFF64748B), fontSize = 10.5.sp)
                            }
                            Text(reply.commentText, color = Color(0xFFE2E8F0), fontSize = 12.5.sp)
                        }
                    }
                }
            }
        }

        // Bottom Reply Input Box
        Surface(
            color = Color(0xFF080C14),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF161F30)),
                    contentAlignment = Alignment.Center
                ) {
                    if (currentUserAvatar.isNotBlank()) {
                        AsyncImage(model = currentUserAvatar, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                    } else {
                        Text(userInitials, color = Color(0xFFFFC107), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(21.dp))
                        .background(Color(0xFF131926))
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (replyText.isEmpty()) {
                        Text("Add a reply...", color = Color(0xFF64748B), fontSize = 13.sp)
                    }
                    BasicTextField(
                        value = replyText,
                        onValueChange = onReplyTextChange,
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                        cursorBrush = SolidColor(Color(0xFFFFC107)),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { onSendReply() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                IconButton(
                    onClick = onSendReply,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFC107))
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.Black, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
