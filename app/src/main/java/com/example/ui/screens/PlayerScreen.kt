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
import androidx.annotation.OptIn
import androidx.compose.animation.*
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
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
import androidx.compose.ui.platform.testTag
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
import com.example.data.model.ContentItemDto
import com.example.data.model.DramaApiComment
import com.example.data.model.EpisodeDto
import com.example.ui.*
import com.example.ui.components.AuthBottomSheetDialog
import com.example.ui.components.UnlockEpisodeDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.DramaFlixViewModel

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

@OptIn(UnstableApi::class)
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
    var showDescriptionDialog by remember { mutableStateOf(false) }

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

    // Initialize & load drama details
    LaunchedEffect(slug) {
        viewModel.loadDramaDetails(slug, context)
    }

    // Media3 ExoPlayer Instance with custom HTTP Data Source
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

    // Setup player listener with error recovery
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

    // Set media source when episode changes
    LaunchedEffect(playerState.currentEpisode, playerState.selectedServer) {
        val currentEp = playerState.currentEpisode
        if (currentEp != null) {
            playbackError = null
            // Check if episode is locked and user is not VIP
            if (currentEp.isLocked && !playerState.isVip) {
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

    // Periodic progress saver
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
            if (totalDurationMs > 0) {
                viewModel.updateWatchProgress(currentPositionMs, totalDurationMs)
            }
            kotlinx.coroutines.delay(1000L)
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
                CircularProgressIndicator(
                    color = TealAccent,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(44.dp)
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. Video Player View Frame (Media3 or Web Player Fallback)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                        .testTag("video_player_container")
                ) {
                    if (useWebPlayerFallback && activeStreamUrl.isNotBlank()) {
                        // Safe Embedded Web Player
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    layoutParams = FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        mediaPlaybackRequiresUserGesture = false
                                        allowFileAccess = false
                                        builtInZoomControls = false
                                        displayZoomControls = false
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
                        // Media3 ExoPlayer View
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = exoPlayer
                                    useController = true
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    layoutParams = FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Playback Error Recovery UI
                    if (playbackError != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.88f))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = GoldVip,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "Stream Playback Notice",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = playbackError ?: "",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            useWebPlayerFallback = true
                                            playbackError = null
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Play in Web Mode", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            playbackError = null
                                            try {
                                                val fallbackItem = MediaItem.fromUri("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
                                                exoPlayer.setMediaItem(fallbackItem)
                                                exoPlayer.prepare()
                                                exoPlayer.play()
                                            } catch (_: Exception) {}
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Direct Stream", color = Color.White, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Top Overlaid Controls: Back Arrow (Left), Share Icon (Right)
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
                                .testTag("player_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
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
                                .testTag("player_top_share_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = "Share",
                                tint = Color.White
                            )
                        }
                    }
                }

                // 2. Scrollable Body: Title + Controls, Metadata, Episodes, Tabs ("For you", "Comments")
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF0C0F15))
                        .testTag("player_details_scroll"),
                    contentPadding = PaddingValues(bottom = 28.dp)
                ) {
                    val content = playerState.content
                    val currentEp = playerState.currentEpisode

                    if (content != null) {
                        // Title Row + "Pre" / "Next" Buttons
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = content.title,
                                    color = TextPrimary,
                                    fontSize = 14.5.sp,
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
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF161A23))
                                            .border(0.8.dp, Color(0xFF2B3346), RoundedCornerShape(6.dp))
                                            .clickable {
                                                StartIoAdManager.showInterstitial(context, isVip = playerState.isVip) {
                                                    viewModel.playPreviousEpisode()
                                                }
                                            }
                                            .padding(horizontal = 9.dp, vertical = 5.dp)
                                            .testTag("player_btn_previous_ep"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.SkipPrevious,
                                                contentDescription = "Pre",
                                                tint = Color(0xFFB0B7C6),
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = "Pre",
                                                color = Color(0xFFB0B7C6),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    // Next Button
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF161A23))
                                            .border(0.8.dp, Color(0xFF2B3346), RoundedCornerShape(6.dp))
                                            .clickable {
                                                StartIoAdManager.showInterstitial(context, isVip = playerState.isVip) {
                                                    viewModel.playNextEpisode()
                                                }
                                            }
                                            .padding(horizontal = 9.dp, vertical = 5.dp)
                                            .testTag("player_btn_next_ep"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = "Next",
                                                color = Color(0xFFB0B7C6),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Icon(
                                                imageVector = Icons.Default.SkipNext,
                                                contentDescription = "Next",
                                                tint = Color(0xFFB0B7C6),
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Metadata (Year, Rating, ...more) & Action Counts Row (Like, Views, Comments, Share, Bookmark)
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left side: Year, Star rating, ...more
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CalendarMonth,
                                        contentDescription = null,
                                        tint = Color(0xFF8E95A5),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = content.releaseYear.ifBlank { "2023" },
                                        color = Color(0xFF8E95A5),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "•",
                                        color = Color(0xFF4C5466),
                                        fontSize = 11.sp
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = GoldVip,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = if (content.rating > 0) String.format("%.1f", content.rating) else "6.9",
                                        color = GoldVip,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "•",
                                        color = Color(0xFF4C5466),
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = "...more",
                                        color = TealAccent,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clickable { showDescriptionDialog = true }
                                            .testTag("player_action_more_info")
                                    )
                                }

                                // Right side: Heart, Views, Comments, Share, Bookmark
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(13.dp)
                                ) {
                                    // Heart / Like
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        modifier = Modifier
                                            .clickable {
                                                if (!authState.isLoggedIn) {
                                                    showAuthSheet = true
                                                } else {
                                                    viewModel.toggleLikeDrama()
                                                }
                                            }
                                            .testTag("player_action_like")
                                    ) {
                                        Icon(
                                            imageVector = if (playerState.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                            contentDescription = "Like",
                                            tint = if (playerState.isLiked) Color(0xFFFF4B72) else Color(0xFFADB3C2),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "${playerState.likesCount.coerceAtLeast(1)}",
                                            color = Color(0xFFADB3C2),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    // Views (Eye)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Visibility,
                                            contentDescription = "Views",
                                            tint = Color(0xFFADB3C2),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "${playerState.viewsCount.coerceAtLeast(48L)}",
                                            color = Color(0xFFADB3C2),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    // Comments (Chat)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        modifier = Modifier
                                            .clickable {
                                                selectedTab = PlayerTab.COMMENTS
                                                viewModel.refreshComments()
                                            }
                                            .testTag("player_action_comments_icon")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ChatBubbleOutline,
                                            contentDescription = "Comments",
                                            tint = Color(0xFFFFC107),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "${playerState.comments.size}",
                                            color = Color(0xFFADB3C2),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    // Share
                                    Icon(
                                        imageVector = Icons.Outlined.Share,
                                        contentDescription = "Share",
                                        tint = Color(0xFFADB3C2),
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable {
                                                val shareLink = "https://playdramaflix.com/watch/${content.slug}"
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("PlayDramaFlix Link", shareLink))
                                                Toast.makeText(context, "Link copied to clipboard!", Toast.LENGTH_SHORT).show()
                                            }
                                            .testTag("player_action_share")
                                    )

                                    // Save / Bookmark
                                    Icon(
                                        imageVector = if (playerState.isInWatchlist) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                        contentDescription = "Bookmark",
                                        tint = if (playerState.isInWatchlist) TealAccent else Color(0xFFADB3C2),
                                        modifier = Modifier
                                            .size(15.dp)
                                            .clickable { viewModel.toggleWatchlist() }
                                            .testTag("player_action_bookmark")
                                    )
                                }
                            }
                        }

                        // Horizontal Episode Pills Row (Active: Teal EP 1 📊, Other: Dark EP 2 ▶)
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
                                        val isEpLocked = ep.isLocked && !playerState.isVip

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (isSelected) TealAccent else Color(0xFF161A23)
                                                )
                                                .border(
                                                    width = 0.9.dp,
                                                    color = if (isSelected) TealAccent else Color(0xFF2B3346),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable {
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
                                                .padding(horizontal = 12.dp, vertical = 7.dp)
                                                .testTag("player_ep_chip_${ep.episodeNumber}"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(
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
                                                    EqualizerBarsIcon(
                                                        modifier = Modifier.size(10.dp, 12.dp),
                                                        tint = Color.Black
                                                    )
                                                } else if (isEpLocked) {
                                                    Icon(
                                                        imageVector = Icons.Default.Lock,
                                                        contentDescription = "Locked",
                                                        tint = GoldVip,
                                                        modifier = Modifier.size(11.dp)
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = null,
                                                        tint = Color(0xFF8E95A5),
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Adaptive Banner Ad below player/episodes for free users (Suppressed if VIP)
                        item {
                            StartAppBanner(
                                isVip = playerState.isVip,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 4.dp)
                            )
                        }

                        // Tab Bar: "For you" and "Comments(7)"
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // "For you" Tab
                                Column(
                                    modifier = Modifier
                                        .clickable { selectedTab = PlayerTab.FOR_YOU }
                                        .testTag("player_tab_for_you"),
                                    horizontalAlignment = Alignment.Start
                                ) {
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

                                // "Comments(X)" Tab
                                Column(
                                    modifier = Modifier
                                        .clickable {
                                            selectedTab = PlayerTab.COMMENTS
                                            viewModel.refreshComments()
                                        }
                                        .testTag("player_tab_comments"),
                                    horizontalAlignment = Alignment.Start
                                ) {
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

                        // Tab 1: "For you" -> 3-Column Poster Grid
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
                                            PlayerPosterCard(
                                                drama = drama,
                                                onClick = { onRelatedDramaClick(drama.slug) }
                                            )
                                        }
                                    }
                                    repeat(3 - rowDramas.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        // Tab 2: "Comments" -> Pure Server-Loaded Comments Matching Screenshot
                        if (selectedTab == PlayerTab.COMMENTS) {
                            // Section Header matching screenshot (Yellow Chat Icon + Comments + Count Badge + Close Button)
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.ChatBubble,
                                            contentDescription = null,
                                            tint = Color(0xFFFFC107),
                                            modifier = Modifier.size(19.dp)
                                        )
                                        Text(
                                            text = "Comments",
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(Color(0xFF1E293B))
                                                .padding(horizontal = 8.dp, vertical = 2.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${playerState.comments.size}",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    // Close button (returns to For You tab)
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF1E293B))
                                            .clickable { selectedTab = PlayerTab.FOR_YOU },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Close",
                                            tint = Color(0xFFCBD5E1),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }

                            // Bottom Input Bar Matching Screenshot
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // User Avatar Badge (Gold initials in Navy circle)
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF161F30)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = userInitials,
                                            color = Color(0xFFFFC107),
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // Rounded Capsule Input Box
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
                                            Text(
                                                text = "Add a comment...",
                                                color = Color(0xFF64748B),
                                                fontSize = 13.5.sp
                                            )
                                        }
                                        BasicTextField(
                                            value = inlineCommentText,
                                            onValueChange = { inlineCommentText = it },
                                            textStyle = TextStyle(
                                                color = Color.White,
                                                fontSize = 13.5.sp
                                            ),
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
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("inline_comment_input")
                                        )
                                    }

                                    // Golden Circular Send Button
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFFC107))
                                            .clickable {
                                                val text = inlineCommentText.trim()
                                                if (text.isNotBlank()) {
                                                    viewModel.postComment(text)
                                                    inlineCommentText = ""
                                                    keyboardController?.hide()
                                                }
                                            }
                                            .testTag("inline_comment_send_btn"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Send",
                                            tint = Color.Black,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }
                            }

                            // Comments Loading & Empty States (Strictly Server Driven - No Fake Comments)
                            if (playerState.isCommentsLoading && playerState.comments.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = Color(0xFFFFC107),
                                            modifier = Modifier.size(30.dp),
                                            strokeWidth = 2.5.dp
                                        )
                                    }
                                }
                            } else if (playerState.comments.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 36.dp, horizontal = 24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.ChatBubble,
                                                contentDescription = null,
                                                tint = Color(0xFF334155),
                                                modifier = Modifier.size(38.dp)
                                            )
                                            Text(
                                                text = "No comments yet",
                                                color = Color.White,
                                                fontSize = 14.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Be the first to share your thoughts on this drama!",
                                                color = Color(0xFF64748B),
                                                fontSize = 12.sp,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(playerState.comments, key = { it.id }) { comment ->
                                    InlineCommentItem(
                                        comment = comment,
                                        onLike = { viewModel.toggleCommentLike(comment.id) },
                                        onReply = {
                                            inlineCommentText = "@${comment.displayName} "
                                        },
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

        // Description Dialog (When user taps "...more")
        if (showDescriptionDialog && playerState.content != null) {
            val drama = playerState.content!!
            AlertDialog(
                onDismissRequest = { showDescriptionDialog = false },
                containerColor = Color(0xFF141923),
                shape = RoundedCornerShape(16.dp),
                title = {
                    Text(
                        text = drama.title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (drama.dubBadge.isNotBlank()) {
                                LanguageDubBadge(dubText = drama.dubBadge)
                            }
                            Text(
                                text = "Year: ${drama.releaseYear.ifBlank { "2023" }}",
                                color = Color(0xFF8E95A5),
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Rating: ★ ${if (drama.rating > 0) drama.rating else "6.9"}",
                                color = GoldVip,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = if (!drama.description.isNullOrBlank()) drama.description!! else "Watch the full story with all episodes in high definition audio and video on DramaFlix.",
                            color = Color(0xFFCCD0DB),
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDescriptionDialog = false }) {
                        Text("Close", color = TealAccent, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        if (showAuthSheet) {
            AuthBottomSheetDialog(
                viewModel = viewModel,
                onDismiss = { showAuthSheet = false }
            )
        }

        // Rewarded Video Ad Episode Unlock Dialog
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
fun PlayerPosterCard(
    drama: ContentItemDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val episodesLabel = when {
        drama.type.equals("movie", ignoreCase = true) -> "Movie"
        drama.totalEpisodes > 0 -> "${drama.totalEpisodes} Episodes"
        else -> "24 Episodes"
    }
    val dubTag = drama.dubBadge.ifBlank { drama.language.ifBlank { "Hindi" } }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("player_recommended_item_${drama.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(8.dp))
                .background(CardBackgroundDark)
                .border(0.6.dp, Color(0xFF263042), RoundedCornerShape(8.dp))
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(drama.posterUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = drama.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Gradient Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )

            // Top-Right Language Badge (Hindi / Bangla)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(topEnd = 8.dp, bottomStart = 6.dp))
                    .background(Color(0xFFEAA61A))
                    .padding(horizontal = 4.5.dp, vertical = 1.dp)
            ) {
                Text(
                    text = when {
                        dubTag.contains("Bangla", ignoreCase = true) -> "Bangla"
                        else -> "Hindi"
                    },
                    color = Color.Black,
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Optional "RECENTLY ADDED" badge
            if (drama.isHot || drama.isFeatured) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(3.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF1E88E5).copy(alpha = 0.9f))
                        .padding(horizontal = 3.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "RECENTLY ADDED",
                        color = Color.White,
                        fontSize = 5.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Bottom-Left Episodes Label
            Text(
                text = episodesLabel,
                color = Color.White,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 4.dp, bottom = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = drama.title,
            color = Color(0xFFDCE0E8),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .testTag("comment_item_${comment.id}"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Avatar Circle (40dp with image or slate background initial)
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
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
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
            // User Name & Timestamp Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = name,
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = comment.displayDate,
                    color = Color(0xFF64748B),
                    fontSize = 12.sp
                )
            }

            // Comment Text Body
            Text(
                text = comment.commentText,
                color = Color(0xFFE2E8F0),
                fontSize = 13.5.sp,
                lineHeight = 19.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Action Icons Row: Heart/Like, Comment/Reply, Share
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Like Button + Count
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.clickable { onLike() }
                ) {
                    Icon(
                        imageVector = if (comment.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (comment.isLiked) Color(0xFFFF4B72) else Color(0xFF94A3B8),
                        modifier = Modifier.size(17.dp)
                    )
                    if (comment.likesCount > 0) {
                        Text(
                            text = comment.likesCount.toString(),
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                }

                // Reply Button + Count
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.clickable { onReply() }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "Reply",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(17.dp)
                    )
                    if (comment.repliesCount > 0) {
                        Text(
                            text = comment.repliesCount.toString(),
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                }

                // Share Button + Count
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.clickable { onShare() }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Reply,
                        contentDescription = "Share",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(17.dp)
                    )
                    if (comment.sharesCount > 0) {
                        Text(
                            text = comment.sharesCount.toString(),
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
