@file:OptIn(UnstableApi::class)

package com.example.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ads.StartIoAdManager
import com.example.data.model.ContentItemDto
import com.example.data.model.EpisodeDto
import com.example.ui.theme.*
import com.example.ui.viewmodel.DramaFlixViewModel
import com.example.util.R2DownloadManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private const val CHUNK_SIZE_SHORTS = 50

private fun findActivity(context: Context): Activity? {
    var current = context
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun formatCount(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
        count > 0 -> "$count"
        else -> "23.3K"
    }
}

private fun cleanTitle(title: String): String {
    return title.split("|", "-").firstOrNull()?.trim() ?: title
}

private fun isWebEmbedUrl(url: String): Boolean {
    val lower = url.lowercase()
    return lower.contains("/e/") ||
            lower.contains("/embed") ||
            lower.contains("byse.sx") ||
            lower.contains("streamtape") ||
            lower.contains("streamwish") ||
            lower.contains("dood") ||
            lower.contains("vidhide") ||
            lower.contains("youtube.com/embed") ||
            lower.contains("playdramaflix.com/player")
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ShortsPlayerScreen(
    slug: String,
    viewModel: DramaFlixViewModel,
    onBackClick: () -> Unit,
    onNavigateToVip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { findActivity(context) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(slug) {
        viewModel.loadDramaDetails(slug, context)
    }

    val playerState by viewModel.playerUiState.collectAsStateWithLifecycle()
    val authState by viewModel.authUiState.collectAsStateWithLifecycle()
    val homeState by viewModel.homeUiState.collectAsStateWithLifecycle()

    var isPlaying by remember { mutableStateOf(true) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }
    var isBuffering by remember { mutableStateOf(true) }

    var isUserSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableLongStateOf(0L) }

    // 📺 বটম ড্রয়ার এবং ব্যাচ ডাউনলোড শিটের স্টেট
    var isHalfDrawerOpen by remember { mutableStateOf(false) }
    var drawerTab by remember { mutableIntStateOf(1) } // 0 = Introduction, 1 = Episodes
    var showBatchDownloadDialog by remember { mutableStateOf(false) }
    var selectedChunkIndex by remember { mutableIntStateOf(0) }

    // ব্যাচ ডাউনলোডের জন্য নির্বাচিত পর্বসমূহ
    val selectedDownloadEpisodes = remember { mutableStateListOf<EpisodeDto>() }

    // স্কিপ এনিমেশন
    var isRewindActive by remember { mutableStateOf(false) }
    var isForwardActive by remember { mutableStateOf(false) }
    val rewindRotation = remember { Animatable(0f) }
    val forwardRotation = remember { Animatable(0f) }
    val rewindAlpha by animateFloatAsState(targetValue = if (isRewindActive) 1f else 0f, label = "rewindAlpha")
    val forwardAlpha by animateFloatAsState(targetValue = if (isForwardActive) 1f else 0f, label = "forwardAlpha")

    // স্ট্রিম লিঙ্ক ও ফলব্যাক
    var useWebPlayerFallback by rememberSaveable { mutableStateOf(false) }
    var activeStreamUrl by rememberSaveable { mutableStateOf("") }
    var currentLoadedEpKey by rememberSaveable { mutableStateOf("") }

    val content = playerState.content
        ?: homeState.popularDramas.find { it.slug == slug }
        ?: ContentItemDto(title = cleanTitle(slug.replace("-", " ")), slug = slug, type = "shorts")

    val effectiveEpisodes = remember(playerState.episodes, content.totalEpisodes) {
        if (playerState.episodes.isNotEmpty()) playerState.episodes else {
            (1..(content.totalEpisodes.coerceAtLeast(1))).map { num ->
                EpisodeDto(episodeNumber = num, isLocked = false)
            }
        }
    }

    val totalEpCount = effectiveEpisodes.size
    val currentEp = playerState.currentEpisode ?: effectiveEpisodes.firstOrNull()
    val currentEpNum = currentEp?.episodeNumber ?: 1

    val episodeChunks = remember(effectiveEpisodes) {
        effectiveEpisodes.chunked(CHUNK_SIZE_SHORTS)
    }

    // ⚡ ১. FastStart ExoPlayer
    val exoPlayer = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setUserAgent("PlayDramaFlix Shorts Player")

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(2000, 12000, 800, 1500)
            .build()

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build().apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_ALL
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true
                )
            }
    }

    // 🌐 ২. Web Embed Player
    val persistentWebView = remember {
        WebView(context).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = true
                allowContentAccess = true
                loadWithOverviewMode = true
                useWideViewPort = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/128.0.0.0 Mobile Safari/537.36"
            }
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                    view?.destroy()
                    return true
                }
            }
            webChromeClient = WebChromeClient()
        }
    }

    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            exoPlayer.release()
            persistentWebView.destroy()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = (state == Player.STATE_BUFFERING)
                if (state == Player.STATE_READY) {
                    totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
                    exoPlayer.play()
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
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(isPlaying, isUserSeeking) {
        while (isPlaying && !isUserSeeking) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
            delay(350L)
        }
    }

    LaunchedEffect(isControlsVisible, isPlaying) {
        if (isControlsVisible && isPlaying && !isHalfDrawerOpen) {
            delay(4000L)
            isControlsVisible = false
        }
    }

    fun triggerSkip(seconds: Int) {
        val target = (exoPlayer.currentPosition + (seconds * 1000L)).coerceIn(0L, totalDurationMs.coerceAtLeast(1L))
        exoPlayer.seekTo(target)
        currentPositionMs = target

        coroutineScope.launch {
            if (seconds < 0) {
                isRewindActive = true
                rewindRotation.snapTo(0f)
                rewindRotation.animateTo(-360f, animationSpec = tween(380, easing = LinearEasing))
                delay(500)
                isRewindActive = false
            } else {
                isForwardActive = true
                forwardRotation.snapTo(0f)
                forwardRotation.animateTo(360f, animationSpec = tween(380, easing = LinearEasing))
                delay(500)
                isForwardActive = false
            }
        }
    }

    // 🎬 ভিডিও স্ট্রিম সিলেক্টর
    LaunchedEffect(currentEp?.episodeNumber, currentEp?.episodeId, slug) {
        if (currentEp != null) {
            val serverVideoUrl = currentEp.appStreamUrl?.takeIf { it.isNotBlank() }
                ?: currentEp.videoUrl?.takeIf { it.isNotBlank() }
                ?: currentEp.embedUrl?.takeIf { it.isNotBlank() }
                ?: currentEp.resolveR2StreamUrl(slug)

            val epUniqueKey = "${currentEp.episodeId}_${currentEp.episodeNumber}"
            if (epUniqueKey == currentLoadedEpKey && activeStreamUrl == serverVideoUrl) return@LaunchedEffect

            currentLoadedEpKey = epUniqueKey
            activeStreamUrl = serverVideoUrl

            if (isWebEmbedUrl(serverVideoUrl)) {
                useWebPlayerFallback = true
                exoPlayer.pause()
                persistentWebView.loadUrl(serverVideoUrl)
            } else {
                useWebPlayerFallback = false
                try {
                    exoPlayer.stop()
                    exoPlayer.clearMediaItems()
                    val mediaItem = MediaItem.Builder()
                        .setUri(Uri.parse(serverVideoUrl))
                        .setMimeType(if (serverVideoUrl.contains(".m3u8")) MimeTypes.APPLICATION_M3U8 else MimeTypes.APPLICATION_MP4)
                        .build()
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                    exoPlayer.play()
                } catch (_: Exception) {
                    useWebPlayerFallback = true
                    persistentWebView.loadUrl(serverVideoUrl)
                }
            }
        }
    }

    BackHandler {
        if (showBatchDownloadDialog) {
            showBatchDownloadDialog = false
        } else if (isHalfDrawerOpen) {
            isHalfDrawerOpen = false
        } else {
            onBackClick()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // =========================================================================
            // 📱 ১. ভিডিও প্লেয়ার ফ্রেম (ড্রয়ার খুললে উপরে সুন্দরভাবে চলতে থাকবে)
            // =========================================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(if (isHalfDrawerOpen) 0.85f else 1f)
                    .background(Color.Black)
            ) {
                if (useWebPlayerFallback && activeStreamUrl.isNotBlank()) {
                    AndroidView(
                        factory = {
                            (persistentWebView.parent as? ViewGroup)?.removeView(persistentWebView)
                            persistentWebView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false
                                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            }
                        },
                        update = { view ->
                            view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        if (isHalfDrawerOpen) isHalfDrawerOpen = false
                                        else isControlsVisible = !isControlsVisible
                                    },
                                    onDoubleTap = { offset ->
                                        if (offset.x < size.width / 2) triggerSkip(-10) else triggerSkip(10)
                                    }
                                )
                            }
                    )
                }

                // বাফারিং লোডার
                if (isBuffering && !useWebPlayerFallback) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF00E676), strokeWidth = 3.dp, modifier = Modifier.size(46.dp))
                    }
                }

                // 🔝 টপ বার: [< Ep1] ও [ডাউনলোড আইকন ↓]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.clickable { onBackClick() }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                        Text("Ep$currentEpNum", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    // 📥 ডাউনলোড আইকন (ট্যাপ করলে ব্যাচ ডাউনলোড পপ-আপ আসবে)
                    IconButton(
                        onClick = {
                            selectedDownloadEpisodes.clear()
                            selectedDownloadEpisodes.add(currentEp ?: effectiveEpisodes.first())
                            showBatchDownloadDialog = true
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f))
                    ) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = "Batch Download", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }

                // ⏯️ অন-স্ক্রিন স্কিপ কন্ট্রোলস (-10s, Play/Pause, +10s)
                AnimatedVisibility(
                    visible = isControlsVisible && !isHalfDrawerOpen,
                    enter = fadeIn(tween(150)),
                    exit = fadeOut(tween(200)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(48.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("-10s", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(y = (-30).dp).alpha(rewindAlpha))
                                IconButton(onClick = { triggerSkip(-10) }, modifier = Modifier.size(46.dp).rotate(rewindRotation.value)) {
                                    SleekSkipIconOnline(isForward = false, color = Color.White)
                                }
                            }

                            IconButton(
                                onClick = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() },
                                modifier = Modifier.size(60.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(42.dp)
                                )
                            }

                            Box(contentAlignment = Alignment.Center) {
                                Text("+10s", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(y = (-30).dp).alpha(forwardAlpha))
                                IconButton(onClick = { triggerSkip(10) }, modifier = Modifier.size(46.dp).rotate(forwardRotation.value)) {
                                    SleekSkipIconOnline(isForward = true, color = Color.White)
                                }
                            }
                        }
                    }
                }

                // 📱 ডানপাশের অ্যাকশন কলাম (Star/Like, Share, More)
                if (!isHalfDrawerOpen) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 12.dp, bottom = 86.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // ১. ⭐ Star / Favorite
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = {
                                    if (!authState.isLoggedIn) viewModel.showAuthDialog(true)
                                    else viewModel.toggleWatchlist()
                                },
                                modifier = Modifier.size(42.dp)
                            ) {
                                Icon(
                                    imageVector = if (playerState.isInWatchlist) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Favorite",
                                    tint = if (playerState.isInWatchlist) Color(0xFFFFD700) else Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Text(
                                text = formatCount(playerState.likesCount.toLong()),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // ২. ↗️ Share
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = {
                                    val shareUrl = "https://playdramaflix.com/watch/${content.slug}"
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "Watch ${content.title} on PlayDramaFlix: $shareUrl")
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Drama"))
                                },
                                modifier = Modifier.size(42.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                            Text("Share", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Normal)
                        }

                        // ৩. ⋯ More
                        IconButton(
                            onClick = {
                                isHalfDrawerOpen = true
                                drawerTab = 0 // Introduction ট্যাব খুলবে
                            },
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(Icons.Default.MoreHoriz, contentDescription = "More", tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                    }

                    // 📑 নিচের ইনফো ও বটম ড্রয়ার বার ([Episodes 1/60 ^ ⛶])
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomStart)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
                            .navigationBarsPadding()
                            .padding(start = 14.dp, end = 14.dp, bottom = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // টাইটেল + মোর
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.clickable {
                                isHalfDrawerOpen = true
                                drawerTab = 0
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.DarkGray)
                            ) {
                                AsyncImage(
                                    model = content.posterUrl ?: content.bannerUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Text(
                                text = "${content.title} >",
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // ডেসক্রিপশন সারাংশ
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .clickable {
                                    isHalfDrawerOpen = true
                                    drawerTab = 0
                                }
                        ) {
                            Text(
                                text = content.description?.takeIf { it.isNotBlank() } ?: content.synopsis,
                                color = Color(0xFFD1D5DB),
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("More", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }

                        // ⏳ স্লিম টাইমলাইন
                        SleekOnlineTimeline(
                            currentPositionMs = currentPositionMs,
                            totalDurationMs = totalDurationMs,
                            onSeekStarted = { isUserSeeking = true },
                            onSeeking = { seekPosition = it },
                            onSeekFinished = {
                                exoPlayer.seekTo(it)
                                currentPositionMs = it
                                isUserSeeking = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(16.dp)
                        )

                        // 🔲 বটম ড্রয়ার ট্রিগার বার ([Episodes 1/60  ^  ⛶])
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E222B).copy(alpha = 0.9f))
                                .clickable {
                                    isHalfDrawerOpen = true
                                    drawerTab = 1 // Episodes ট্যাব খুলবে
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Episodes  $currentEpNum/$totalEpCount",
                                color = Color.White,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Open Drawer", tint = Color(0xFF9AA4B5), modifier = Modifier.size(18.dp))
                                Icon(Icons.Default.CropFree, contentDescription = "Fullscreen", tint = Color(0xFF9AA4B5), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // 📑 ২. ২য় ও ৩য় স্ক্রিনের হাফ-স্ক্রিন বটম ড্রয়ার (Introduction ও Episodes)
            // =========================================================================
            AnimatedVisibility(
                visible = isHalfDrawerOpen,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.15f)
            ) {
                Surface(
                    color = Color(0xFF12151C),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        // ড্র্যাগ বার
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF3B4354))
                                .align(Alignment.CenterHorizontally)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // হেডার: পোস্টার + টাইটেল + সবুজ [ Add list ] বাটন
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 38.dp, height = 50.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.DarkGray)
                                ) {
                                    AsyncImage(
                                        model = content.posterUrl ?: content.bannerUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }

                                Column {
                                    Text(
                                        text = content.title,
                                        color = Color.White,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "$totalEpCount Episodes",
                                        color = Color(0xFF8E95A5),
                                        fontSize = 11.5.sp
                                    )
                                }
                            }

                            // 🟢 উজ্জ্বল সবুজ [ Add list ] বাটন
                            Button(
                                onClick = {
                                    if (!authState.isLoggedIn) viewModel.showAuthDialog(true)
                                    else viewModel.toggleWatchlist()
                                },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (playerState.isInWatchlist) Color(0xFF0F3B32) else Color(0xFF00D166)
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(
                                    text = if (playerState.isInWatchlist) "Added ✓" else "Add list",
                                    color = if (playerState.isInWatchlist) Color(0xFF00E676) else Color.Black,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 📑 [ Introduction ] এবং [ Episodes ] ট্যাব হেডার
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { drawerTab = 0 }
                            ) {
                                Text(
                                    text = "Introduction",
                                    color = if (drawerTab == 0) Color.White else Color(0xFF8E95A5),
                                    fontSize = 14.sp,
                                    fontWeight = if (drawerTab == 0) FontWeight.Bold else FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .width(32.dp)
                                        .height(2.5.dp)
                                        .background(if (drawerTab == 0) Color(0xFF00E676) else Color.Transparent)
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { drawerTab = 1 }
                            ) {
                                Text(
                                    text = "Episodes",
                                    color = if (drawerTab == 1) Color.White else Color(0xFF8E95A5),
                                    fontSize = 14.sp,
                                    fontWeight = if (drawerTab == 1) FontWeight.Bold else FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .width(32.dp)
                                        .height(2.5.dp)
                                        .background(if (drawerTab == 1) Color(0xFF00E676) else Color.Transparent)
                                )
                            }
                        }

                        HorizontalDivider(color = Color(0xFF222634), thickness = 0.8.dp)

                        Spacer(modifier = Modifier.height(8.dp))

                        // =============================================================
                        // 📖 ২য় স্ক্রিনের "Episodes" ট্যাব (৮-কলামের এপিসোড গ্রিড - VIP ব্যাজ ছাড়া)
                        // =============================================================
                        if (drawerTab == 1) {
                            // রেঞ্জ ফিল্টার: [ 1-50 ]  [ 51-60 ]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                episodeChunks.forEachIndexed { index, chunk ->
                                    val start = index * CHUNK_SIZE_SHORTS + 1
                                    val end = start + chunk.size - 1
                                    val isSelected = (index == selectedChunkIndex)

                                    Text(
                                        text = "$start-$end",
                                        color = if (isSelected) Color(0xFF00E676) else Color(0xFF8E95A5),
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier
                                            .clickable { selectedChunkIndex = index }
                                            .padding(vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text("$totalEpCount Episodes", color = Color(0xFF6B7280), fontSize = 11.5.sp)

                            Spacer(modifier = Modifier.height(8.dp))

                            val currentChunkEpisodes = episodeChunks.getOrElse(selectedChunkIndex) { emptyList() }

                            // 🔲 ৮-কলামের পর্ব গ্রিড (৮টি করে পর্ব প্রতি লাইনে)
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(8),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                items(currentChunkEpisodes, key = { it.episodeId }) { ep ->
                                    val isCurrent = (ep.episodeNumber == currentEpNum)

                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isCurrent) Color(0xFF0F3B32) else Color(0xFF1E222D))
                                            .border(
                                                width = if (isCurrent) 1.2.dp else 0.dp,
                                                color = if (isCurrent) Color(0xFF00E676) else Color.Transparent,
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .clickable {
                                                viewModel.selectEpisode(ep)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = ep.episodeNumber.toString(),
                                            color = if (isCurrent) Color(0xFF00E676) else Color(0xFFDCE0E8),
                                            fontSize = 12.5.sp,
                                            fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        } else {
                            // =============================================================
                            // 📖 ৩য় স্ক্রিনের "Introduction" ট্যাব (সিনপসিস, ট্যাগস ও রিকমেন্ডেশন)
                            // =============================================================
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                item {
                                    Text(
                                        text = content.description?.takeIf { it.isNotBlank() } ?: content.synopsis,
                                        color = Color(0xFFD1D5DB),
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp
                                    )
                                }

                                // ৩য় স্ক্রিনের ট্যাগস রো
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFF2E1C22),
                                            border = BorderStroke(0.6.dp, Color(0xFF702E3B))
                                        ) {
                                            Text(
                                                text = "🔥 Trending No.3 >",
                                                color = Color(0xFFFF5252),
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }

                                        content.categories.take(2).forEach { cat ->
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFF1E2330),
                                                border = BorderStroke(0.6.dp, Color(0xFF3B4354))
                                            ) {
                                                Text(
                                                    text = "$cat >",
                                                    color = Color(0xFF8E95A5),
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                item {
                                    HorizontalDivider(color = Color(0xFF222634), thickness = 0.6.dp)
                                }

                                // 🎬 ৩য় স্ক্রিনের Spin-off Program (৩-কলাম রিকমেন্ডেশন গ্রিড)
                                item {
                                    Text(
                                        text = "Spin-off Program",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                val recommendations = homeState.popularDramas.filter { it.slug != slug }.take(6).chunked(3)

                                items(recommendations) { rowDramas ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rowDramas.forEach { rec ->
                                            Column(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable {
                                                        isHalfDrawerOpen = false
                                                        viewModel.loadDramaDetails(rec.slug, context)
                                                    }
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .aspectRatio(0.72f)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(Color.DarkGray)
                                                ) {
                                                    AsyncImage(
                                                        model = rec.posterUrl ?: rec.bannerUrl,
                                                        contentDescription = null,
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop
                                                    )

                                                    // 🟢 সবুজ Short ব্যাজ (৩য় ছবির মতো)
                                                    Surface(
                                                        shape = RoundedCornerShape(bottomStart = 4.dp),
                                                        color = Color(0xFF00D166),
                                                        modifier = Modifier.align(Alignment.TopEnd)
                                                    ) {
                                                        Text(
                                                            text = "Short",
                                                            color = Color.Black,
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.Black,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }

                                                    Text(
                                                        text = "${rec.totalEpisodes} Episodes",
                                                        color = Color.White,
                                                        fontSize = 8.5.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        modifier = Modifier
                                                            .align(Alignment.BottomStart)
                                                            .padding(4.dp)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(3.dp))

                                                Text(
                                                    text = rec.title,
                                                    color = Color.White,
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        repeat(3 - rowDramas.size) { Spacer(modifier = Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 📥 ৩. একাধিক পর্ব নির্বাচন করে একসাথে ডাউনলোড করার পপ-আপ (Batch Download Card)
        // =========================================================================
        if (showBatchDownloadDialog) {
            val isAllSelected = (selectedDownloadEpisodes.size == effectiveEpisodes.size && effectiveEpisodes.isNotEmpty())

            Dialog(
                onDismissRequest = { showBatchDownloadDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .clickable { showBatchDownloadDialog = false },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = false) {},
                        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF141822))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // হেডার
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Download Episodes", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text("Select episodes to download in HD MP4", color = Color(0xFF8E95A5), fontSize = 11.5.sp)
                                }

                                IconButton(onClick = { showBatchDownloadDialog = false }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF8E95A5))
                                }
                            }

                            // রেঞ্জ ফিল্টার
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                itemsIndexed(episodeChunks) { index, chunk ->
                                    val start = index * CHUNK_SIZE_SHORTS + 1
                                    val end = start + chunk.size - 1
                                    val isSelected = (index == selectedChunkIndex)

                                    Text(
                                        text = "$start-$end",
                                        color = if (isSelected) Color(0xFF00E676) else Color(0xFF8E95A5),
                                        fontSize = 13.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier
                                            .clickable { selectedChunkIndex = index }
                                            .padding(vertical = 2.dp)
                                    )
                                }
                            }

                            // ৬-কলাম মাল্টি-সিলেকশন গ্রিড
                            val currentChunkEpisodes = episodeChunks.getOrElse(selectedChunkIndex) { emptyList() }

                            LazyVerticalGrid(
                                columns = GridCells.Fixed(6),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                            ) {
                                items(currentChunkEpisodes, key = { it.episodeId }) { ep ->
                                    val isSelectedForDl = selectedDownloadEpisodes.contains(ep)

                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(1.1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelectedForDl) Color(0xFF0F3B32) else Color(0xFF222634))
                                            .border(
                                                width = if (isSelectedForDl) 1.2.dp else 0.dp,
                                                color = if (isSelectedForDl) Color(0xFF00E676) else Color.Transparent,
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .clickable {
                                                if (isSelectedForDl) selectedDownloadEpisodes.remove(ep)
                                                else selectedDownloadEpisodes.add(ep)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = ep.episodeNumber.toString(),
                                            color = if (isSelectedForDl) Color(0xFF00E676) else Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )

                                        if (isSelectedForDl) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = Color(0xFF00E676),
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(2.dp)
                                                    .size(10.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = Color(0xFF222634), thickness = 0.8.dp)

                            // বটম বাটন রো: [ ◯ Select All ] ও [ 📥 Download (X) ]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.clickable {
                                        if (isAllSelected) {
                                            selectedDownloadEpisodes.clear()
                                        } else {
                                            selectedDownloadEpisodes.clear()
                                            selectedDownloadEpisodes.addAll(effectiveEpisodes)
                                        }
                                    }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .border(1.2.dp, if (isAllSelected) Color(0xFF00E676) else Color(0xFF8E95A5), CircleShape)
                                            .background(if (isAllSelected) Color(0xFF00E676) else Color.Transparent),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isAllSelected) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(12.dp))
                                        }
                                    }

                                    Text("Select All", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }

                                Button(
                                    onClick = {
                                        showBatchDownloadDialog = false
                                        val targets = if (selectedDownloadEpisodes.isNotEmpty()) selectedDownloadEpisodes.toList()
                                        else effectiveEpisodes.take(1)

                                        targets.forEach { ep ->
                                            R2DownloadManager.startDownload(
                                                context = context,
                                                downloadUrl = ep.resolveDownloadUrl(slug),
                                                title = content.title,
                                                episodeNumber = ep.episodeNumber,
                                                isMovie = false
                                            )
                                        }
                                        Toast.makeText(context, "📥 Download started for ${targets.size} episodes!", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D166)),
                                    modifier = Modifier
                                        .fillMaxWidth(0.68f)
                                        .height(42.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Outlined.FileDownload, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                        Text(
                                            text = if (selectedDownloadEpisodes.isNotEmpty()) "Download (${selectedDownloadEpisodes.size})" else "Download",
                                            color = Color.Black,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold
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
}
