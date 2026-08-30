@file:OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
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
    tint: Color = Color.Black
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(modifier = Modifier.width(2.2.dp).height(7.dp).background(tint, RoundedCornerShape(1.dp)))
        Box(modifier = Modifier.width(2.2.dp).height(12.dp).background(tint, RoundedCornerShape(1.dp)))
        Box(modifier = Modifier.width(2.2.dp).height(5.dp).background(tint, RoundedCornerShape(1.dp)))
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

    // 📜 ইনলাইন ডেসক্রিপশন এক্সপ্যান্ড স্টেট (...more / less)
    var isDescriptionExpanded by remember { mutableStateOf(false) }

    // 🔄 Pull-To-Refresh State
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    // 🔒 সার্ভার থেকে বিজ্ঞাপন চালু আছে কিনা তা চেক করা
    val isAdsGloballyActive = remember { UnifiedAdManager.isAdsGloballyEnabled() }
    val shouldLockEpisodes = !playerState.isVip && isAdsGloballyActive

    val currentUserName = authState.userProfile?.displayName ?: "User"
    val userInitials = remember(currentUserName) {
        val trimmed = currentUserName.trim()
        val parts = trimmed.split(" ").filter { it.isNotBlank() }
        if (parts.size >= 2) {
            "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
        } else if (trimmed.length >= 2) {
            trimmed.take(2).uppercase()
        } else if (trimmed.isNotEmpty()) {
            trimmed.take(1).uppercase()
        } else {
            "US"
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
                    playbackError = "Stream notice: Tap to try Web Player or retry."
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

    LaunchedEffect(playerState.currentEpisode, playerState.selectedServer) {
        val currentEp = playerState.currentEpisode
        if (currentEp != null) {
            playbackError = null
            if (shouldLockEpisodes && currentEp.isLocked) {
                exoPlayer.pause()
                viewModel.showEpisodeUnlockModal(currentEp)
                return@LaunchedEffect
            }

            val videoUrl = currentEp.videoUrl?.takeIf { it.isNotBlank() }
                ?: playerState.selectedServer?.url?.takeIf { it.isNotBlank() }
                ?: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"

            activeStreamUrl = videoUrl

            if (isWebEmbedUrl(videoUrl) || playerState.selectedServer?.type.equals("embed", ignoreCase = true)) {
                useWebPlayerFallback = true
                exoPlayer.pause()
            } else {
                useWebPlayerFallback = false
                try {
                    val mediaItem = buildMediaItemForUrl(videoUrl)
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                    exoPlayer.play()
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
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. 🎬 Video Player Frame
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

                    // Top Back & Share Overlay
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f))
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }

                        IconButton(
                            onClick = {
                                val shareLink = "https://playdramaflix.com/watch/$slug"
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("PlayDramaFlix Link", shareLink))
                                Toast.makeText(context, "Link copied to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f))
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                        }
                    }
                }

                // 2. 🔄 Pull-To-Refresh Scrollable Details Body
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        coroutineScope.launch {
                            isRefreshing = true
                            viewModel.loadDramaDetails(slug, context)
                            viewModel.refreshComments()
                            delay(600)
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

                            // Metadata & Actions Row (📅 2020 • ⭐ 8.9 • less/...more)
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

                            // 📜 ইনলাইন এক্সপ্যান্ডেবল ডেসক্রিপশন সেকশন (Fixed Column Wrapper)
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

                            // 🔘 Episode Selector Pills
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
                                                color = if (isSelected) Color(0xFF00D166) else Color(0xFF161A23),
                                                border = BorderStroke(0.9.dp, if (isSelected) Color(0xFF00D166) else Color(0xFF2B3346)),
                                                modifier = Modifier.clickable {
                                                    if (isEpLocked) {
                                                        viewModel.showEpisodeUnlockModal(ep)
                                                    } else {
                                                        if (currentEp?.episodeNumber != ep.episodeNumber) {
                                                            StartIoAdManager.showInterstitial(context, isVip = playerState.isVip) {
                                                                viewModel.selectEpisode(ep)
                                                            }
                                                        } else {
                                                            viewModel.selectEpisode(ep)
                                                        }
                                                    }
                                                }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                ) {
                                                    Text(
                                                        text = "EP ${ep.episodeNumber}",
                                                        color = if (isSelected) Color.Black else Color.White,
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )

                                                    if (isSelected) {
                                                        EqualizerBarsIcon(modifier = Modifier.size(10.dp, 12.dp), tint = Color.Black)
                                                    } else if (isEpLocked) {
                                                        Icon(Icons.Default.Lock, contentDescription = "Locked", tint = GoldVip, modifier = Modifier.size(11.dp))
                                                    } else {
                                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF8E95A5), modifier = Modifier.size(12.dp))
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

                            // Tab 1: For You (3-Column Poster Grid)
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

                            // Tab 2: Comments (Inline Feed)
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
                                        InlineCommentItem(
                                            comment = comment,
                                            onLike = { viewModel.toggleCommentLike(comment.id) },
                                            onReply = { inlineCommentText = "@${comment.displayName} " },
                                            onShare = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("Comment", "${comment.displayName}: ${comment.commentText}"))
                                                Toast.makeText(context, "Comment copied!", Toast.LENGTH_SHORT).show()
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

@Composable
fun InlineCommentItem(
    comment: DramaApiComment,
    onLike: () -> Unit,
    onReply: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val name = comment.displayName

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF475569)),
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
                    text = name.firstOrNull()?.uppercase() ?: "U",
                    color = Color.White,
                    fontSize = 16.sp,
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
                Text(comment.displayDate, color = Color(0xFF64748B), fontSize = 12.sp)
            }

            Text(comment.commentText, color = Color(0xFFE2E8F0), fontSize = 13.5.sp, lineHeight = 19.sp)

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.clickable { onLike() }
                ) {
                    Icon(
                        imageVector = if (comment.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (comment.isLiked) Color(0xFFFF4B72) else Color(0xFF94A3B8),
                        modifier = Modifier.size(17.dp)
                    )
                    if (comment.likesCount > 0) {
                        Text("${comment.likesCount}", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.clickable { onReply() }
                ) {
                    Text("Reply", color = Color(0xFF94A3B8), fontSize = 12.sp)
                }

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
}
