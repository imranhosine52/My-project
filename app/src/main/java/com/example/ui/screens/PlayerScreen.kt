package com.example.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.view.View
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
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import coil.compose.AsyncImage
import com.example.ads.StartAppBanner
import com.example.ads.StartIoAdManager
import com.example.ads.UnifiedAdManager
import com.example.data.model.ContentItemDto
import com.example.data.model.DramaApiComment
import com.example.data.model.EpisodeDto
import com.example.ui.components.AuthBottomSheetDialog
import com.example.ui.components.DownloadResourceSheet
import com.example.ui.theme.*
import com.example.ui.viewmodel.DramaFlixViewModel
import com.example.util.R2DownloadManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

// 🔀 গ্লোবাল সার্ভার ডাটা মডেল
data class GlobalStreamServer(
    val id: String,           // "server_1" বা "server_2"
    val displayName: String,  // "Server 1" বা "Server 2"
    val providerInfo: String, // "Cloudflare R2 (Fast HD 1080p)" বা "Byse.sx (Stream)"
    val isEmbed: Boolean      // false = MP4, true = WebView Embed
)

private fun findActivityFromContext(context: Context): Activity? {
    var current = context
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun cleanDramaTitle(title: String): String {
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

private fun formatCountDisplay(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
        count > 0 -> "$count"
        else -> "0"
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PlayerScreen(
    slug: String,
    viewModel: DramaFlixViewModel,
    onBackClick: () -> Unit,
    onNavigateToVip: () -> Unit,
    onRelatedDramaClick: (String) -> Unit,
    onNavigateToDownloads: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { findActivityFromContext(context) }
    val configuration = LocalConfiguration.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val isDeviceLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isAnyFullscreen = isDeviceLandscape

    var currentActiveSlug by remember(slug) { mutableStateOf(slug) }
    val dramaHistoryStack = remember { mutableStateListOf<String>() }

    // 🚀 প্রথমবার লোডের গ্লিচ ফিক্স
    LaunchedEffect(currentActiveSlug) {
        viewModel.loadDramaDetails(currentActiveSlug, context)
    }

    val playerState by viewModel.playerUiState.collectAsStateWithLifecycle()
    val authState by viewModel.authUiState.collectAsStateWithLifecycle()
    val homeState by viewModel.homeUiState.collectAsStateWithLifecycle()

    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }

    var useWebPlayerFallback by rememberSaveable { mutableStateOf(false) }
    var activeStreamUrl by rememberSaveable { mutableStateOf("") }
    var currentLoadedEpKey by rememberSaveable { mutableStateOf("") }

    // 🔀 সার্ভার নির্বাচন স্টেট ("server_1" = R2, "server_2" = Byse)
    var selectedGlobalServerId by rememberSaveable { mutableStateOf("server_1") }

    var showAuthSheet by remember { mutableStateOf(false) }
    var showDownloadSheet by remember { mutableStateOf(false) }
    var showServerSelectorSheet by remember { mutableStateOf(false) }

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var inlineCommentText by remember { mutableStateOf("") }

    var shuffledRecommendations by remember { mutableStateOf<List<ContentItemDto>>(emptyList()) }
    var selectedThreadParentComment by remember { mutableStateOf<DramaApiComment?>(null) }
    var threadReplyText by remember { mutableStateOf("") }
    var isDescriptionExpanded by remember { mutableStateOf(false) }

    val adConfig by UnifiedAdManager.adConfigState.collectAsStateWithLifecycle()
    val shouldLockEpisodes = !playerState.isVip && adConfig.adsEnabled

    val currentUser = authState.userProfile
    val currentUserName = currentUser?.displayName ?: "User"
    val currentUserAvatar = currentUser?.avatar ?: ""
    val userInitials = remember(currentUserName) {
        val parts = currentUserName.trim().split(" ").filter { it.isNotBlank() }
        if (parts.size >= 2) "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
        else currentUserName.take(2).uppercase()
    }

    fun handleBackNavigation() {
        if (isDeviceLandscape) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else if (selectedThreadParentComment != null) {
            selectedThreadParentComment = null
        } else if (dramaHistoryStack.isNotEmpty()) {
            val prevSlug = dramaHistoryStack.removeAt(dramaHistoryStack.lastIndex)
            currentActiveSlug = prevSlug
            viewModel.loadDramaDetails(prevSlug, context)
        } else {
            onBackClick()
        }
    }

    BackHandler { handleBackNavigation() }

    // ⚡ ১. ExoPlayer Engine
    val exoPlayer = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setUserAgent("PlayDramaFlix Native Player")

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val fastStartLoadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(2000, 15000, 1000, 2000)
            .build()

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(fastStartLoadControl)
            .build().apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_OFF
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true
                )
            }
    }

    // 🌐 ২. Web Embed Player (Fallback)
    var isWebLoading by remember { mutableStateOf(false) }

    val persistentWebView = remember {
        WebView(context).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = true
                allowContentAccess = true
                loadWithOverviewMode = true
                useWideViewPort = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/128.0.0.0 Safari/537.36"
            }
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    isWebLoading = true
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    isWebLoading = false
                }
                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                    view?.destroy()
                    return true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (newProgress >= 80) isWebLoading = false
                }
            }
        }
    }

    // ফুলস্ক্রিন ও স্ট্যাটাস বার হ্যান্ডলিং
    LaunchedEffect(isAnyFullscreen) {
        activity?.let { act ->
            val window = act.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (isAnyFullscreen) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            exoPlayer.release()
            persistentWebView.destroy()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
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

    // টাইমলাইন পোলিং
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
            if (totalDurationMs > 0) {
                viewModel.updateWatchProgress(currentPositionMs, totalDurationMs)
            }
            delay(500L)
        }
    }

    // 🎯 ৩. স্মার্ট গ্লোবাল সার্ভার অনুযায়ী এপিসোডের লিংক নির্ধারণ
    LaunchedEffect(
        playerState.currentEpisode?.episodeNumber,
        playerState.currentEpisode?.episodeId,
        selectedGlobalServerId,
        currentActiveSlug
    ) {
        val currentEp = playerState.currentEpisode
        if (currentEp != null) {
            if (shouldLockEpisodes && currentEp.isLocked) {
                exoPlayer.pause()
                viewModel.showEpisodeUnlockModal(currentEp)
                return@LaunchedEffect
            }

            // 🎯 গ্লোবাল সার্ভার নির্বাচন লজিক
            val (resolvedUrl, isEmbed) = if (selectedGlobalServerId == "server_2") {
                // Byse Stream (Server 2)
                val byseCandidate = currentEp.embedUrl?.takeIf { it.isNotBlank() && (it.contains("byse") || it.contains("/e/") || it.contains("embed")) }
                    ?: currentEp.webPlayerUrl?.takeIf { it.isNotBlank() && (it.contains("byse") || it.contains("/e/") || it.contains("embed")) }
                    ?: currentEp.videoUrl?.takeIf { it.isNotBlank() && (it.contains("byse") || it.contains("/e/") || it.contains("embed")) }
                    ?: "https://byse.sx/e/${currentActiveSlug}_ep_${currentEp.episodeNumber}"
                Pair(byseCandidate, true)
            } else {
                // Cloudflare R2 Direct MP4 (Server 1)
                val r2Candidate = if (!currentEp.appStreamUrl.isNullOrBlank() && !currentEp.appStreamUrl.contains("byse") && !currentEp.appStreamUrl.contains("/e/")) {
                    currentEp.appStreamUrl
                } else if (!currentEp.downloadUrl.isNullOrBlank() && !currentEp.downloadUrl.contains("byse") && !currentEp.downloadUrl.contains("/e/")) {
                    currentEp.downloadUrl
                } else if (!currentEp.videoUrl.isNullOrBlank() && !currentEp.videoUrl.contains("byse") && !currentEp.videoUrl.contains("/e/")) {
                    currentEp.videoUrl
                } else {
                    currentEp.resolveR2StreamUrl(currentActiveSlug)
                }
                Pair(r2Candidate, isWebEmbedUrl(r2Candidate))
            }

            if (resolvedUrl.isBlank()) return@LaunchedEffect

            val epUniqueKey = "${currentEp.episodeId}_${currentEp.episodeNumber}_${selectedGlobalServerId}"
            if (epUniqueKey == currentLoadedEpKey && activeStreamUrl == resolvedUrl) return@LaunchedEffect

            currentLoadedEpKey = epUniqueKey
            activeStreamUrl = resolvedUrl

            if (isEmbed) {
                useWebPlayerFallback = true
                exoPlayer.pause()
                persistentWebView.loadUrl(resolvedUrl)
            } else {
                useWebPlayerFallback = false
                try {
                    exoPlayer.stop()
                    exoPlayer.clearMediaItems()
                    val mediaItem = MediaItem.Builder()
                        .setUri(Uri.parse(resolvedUrl))
                        .setMimeType(if (resolvedUrl.contains(".m3u8")) MimeTypes.APPLICATION_M3U8 else MimeTypes.APPLICATION_MP4)
                        .build()
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                    exoPlayer.play()
                } catch (_: Exception) {
                    useWebPlayerFallback = true
                    persistentWebView.loadUrl(resolvedUrl)
                }
            }
        }
    }

    // 🔀 শুধুমাত্র সক্রিয় ও উপলব্ধ সার্ভার ডিটেকশন
    val availableGlobalServers = remember(playerState.episodes, playerState.servers, currentActiveSlug) {
        val hasByseAvailable = playerState.episodes.any { ep ->
            val em = ep.embedUrl ?: ""
            val vu = ep.videoUrl ?: ""
            val wp = ep.webPlayerUrl ?: ""
            em.contains("byse") || em.contains("/e/") || vu.contains("byse") || wp.contains("byse")
        } || playerState.servers.any { srv ->
            (srv.rawUrl ?: "").contains("byse") || (srv.embedUrl ?: "").contains("byse")
        }

        buildList {
            add(
                GlobalStreamServer(
                    id = "server_1",
                    displayName = "Server 1",
                    providerInfo = "Cloudflare R2 (Ultra Fast HD 1080p)",
                    isEmbed = false
                )
            )
            if (hasByseAvailable) {
                add(
                    GlobalStreamServer(
                        id = "server_2",
                        displayName = "Server 2",
                        providerInfo = "Byse.sx (Web Stream Embed)",
                        isEmbed = true
                    )
                )
            }
        }
    }

    LaunchedEffect(playerState.recommendations, homeState.popularDramas, currentActiveSlug) {
        val combined = (playerState.recommendations + homeState.popularDramas)
            .distinctBy { it.slug }
            .filter { it.slug != currentActiveSlug }
        shuffledRecommendations = combined.shuffled()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "card_shine")
    val shineOffset by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shine_offset"
    )
    val shiningBorderBrush = Brush.linearGradient(
        colors = listOf(Color(0xFF1E293B), Color(0xFF00E5FF).copy(alpha = 0.7f), Color(0xFF1E293B)),
        start = Offset(shineOffset, shineOffset),
        end = Offset(shineOffset + 180f, shineOffset + 180f)
    )

    val content = playerState.content
        ?: homeState.popularDramas.find { it.slug == currentActiveSlug }
        ?: ContentItemDto(title = "Loading...", slug = currentActiveSlug)

    val currentEp = playerState.currentEpisode ?: playerState.episodes.firstOrNull()

    val rawDownloadCandidate = remember(currentEp, currentActiveSlug, activeStreamUrl) {
        currentEp?.downloadUrl?.takeIf { it.isNotBlank() }
            ?: currentEp?.appStreamUrl?.takeIf { it.isNotBlank() }
            ?: currentEp?.resolveDownloadUrl(currentActiveSlug)
            ?: activeStreamUrl
    }
    val downloadUrl = remember(rawDownloadCandidate) {
        R2DownloadManager.resolveDirectMp4Url(rawDownloadCandidate)
    }

    fun shareCurrentDrama() {
        try {
            val shareUrl = "https://playdramaflix.com/watch/$currentActiveSlug"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Watch ${cleanDramaTitle(content.title)} on PlayDramaFlix: $shareUrl")
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share with friends"))
        } catch (_: Exception) {
            Toast.makeText(context, "Cannot open share options", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (!isAnyFullscreen) Modifier.statusBarsPadding() else Modifier)
        ) {
            // =========================================================================
            // 🎬 ১. শীর্ষের ১৬:৯ প্লেয়ার ফ্রেম
            // =========================================================================
            Box(
                modifier = if (isAnyFullscreen) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                }.background(Color.Black)
            ) {
                if (useWebPlayerFallback && activeStreamUrl.isNotBlank()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AndroidView(
                            factory = {
                                (persistentWebView.parent as? ViewGroup)?.removeView(persistentWebView)
                                persistentWebView
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        if (isWebLoading) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color(0xFF00E5FF), strokeWidth = 3.dp, modifier = Modifier.size(42.dp))
                            }
                        }
                        IconButton(
                            onClick = { handleBackNavigation() },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    }
                } else {
                    PlayerVideoBox(
                        exoPlayer = exoPlayer,
                        title = cleanDramaTitle(content.title),
                        episodeNumber = currentEp?.episodeNumber ?: 1,
                        downloadUrl = downloadUrl,
                        isDeviceLandscape = isDeviceLandscape,
                        currentPositionMs = currentPositionMs,
                        totalDurationMs = totalDurationMs,
                        isPlaying = isPlaying,
                        onBackClick = { handleBackNavigation() },
                        onPlayPauseClick = {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        },
                        onSeek = { seconds ->
                            val target = (exoPlayer.currentPosition + (seconds * 1000L)).coerceIn(0L, totalDurationMs.coerceAtLeast(1L))
                            exoPlayer.seekTo(target)
                            currentPositionMs = target
                        },
                        onSeekFinished = { pos ->
                            exoPlayer.seekTo(pos)
                            currentPositionMs = pos
                        },
                        onToggleFullscreen = {
                            activity?.let { act ->
                                act.requestedOrientation = if (isDeviceLandscape) {
                                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                } else {
                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                }
                            }
                        },
                        onShareClick = { shareCurrentDrama() },
                        onDownloadClick = { showDownloadSheet = true },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // =========================================================================
            // 📑 ২. নিচের অংশ: মেটাডাটা, সার্ভার সুইচ ও লাইভ ভিউজ/লাইকস
            // =========================================================================
            if (!isAnyFullscreen) {
                if (selectedThreadParentComment != null) {
                    CommentRepliesThreadView(
                        parentComment = selectedThreadParentComment!!,
                        dramaContent = content,
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
                    Box(
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
                            val shortTitle = cleanDramaTitle(content.title)

                            // Title & Pre/Next Controls
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = shortTitle,
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
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
                                            Text("Pre", color = Color(0xFFB0B7C6), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
                                        }

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
                                            Text("Next", color = Color(0xFFB0B7C6), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
                                        }
                                    }
                                }
                            }

                            // 📊 ২. মেটাডাটা রো: ভিউজ, লাইকস, বুকমার্ক এবং সার্ভার সুইচ অপশন
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text(content.releaseYear.ifBlank { "2026" }, color = Color(0xFF8E95A5), fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                                        Text("•", color = Color(0xFF4C5466), fontSize = 11.sp)
                                        Icon(Icons.Default.Star, contentDescription = null, tint = GoldVip, modifier = Modifier.size(13.dp))
                                        Text(if (content.rating > 0) String.format(Locale.US, "%.1f", content.rating) else "8.9", color = GoldVip, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                        Text("•", color = Color(0xFF4C5466), fontSize = 11.sp)
                                        Text(
                                            text = if (isDescriptionExpanded) "less" else "...more",
                                            color = TealAccent,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clickable { isDescriptionExpanded = !isDescriptionExpanded }
                                        )
                                    }

                                    // ডান পাশ: [ 👁️ Views ]  [ ❤️ Likes ]  [ 🔖 Bookmark ]  [ 🔀 Server Switch ]
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // 👁️ ১. ভিউজ কাউন্টার
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Visibility,
                                                contentDescription = "Views",
                                                tint = Color(0xFF00E5FF),
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                text = formatCountDisplay(playerState.viewsCount),
                                                color = Color(0xFFCCD0DB),
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        // ❤️ ২. লাইক বাটন
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.clickable {
                                                if (!authState.isLoggedIn) {
                                                    showAuthSheet = true
                                                } else {
                                                    viewModel.toggleLikeDrama()
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (playerState.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                contentDescription = "Like",
                                                tint = if (playerState.isLiked) Color(0xFFFF4B72) else Color(0xFFADB3C2),
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                text = formatCountDisplay(playerState.likesCount.toLong()),
                                                color = if (playerState.isLiked) Color(0xFFFF4B72) else Color(0xFFADB3C2),
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        // 🔖 ৩. বুকমার্ক
                                        Icon(
                                            imageVector = if (playerState.isInWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                            contentDescription = "Bookmark",
                                            tint = if (playerState.isInWatchlist) TealAccent else Color(0xFFADB3C2),
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable { viewModel.toggleWatchlist() }
                                        )

                                        // 🔀 ৪. সার্ভার চেঞ্জ বাটন
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF1B2333),
                                            border = BorderStroke(0.8.dp, Color(0xFF00E5FF).copy(alpha = 0.6f)),
                                            modifier = Modifier.clickable { showServerSelectorSheet = true }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Dns,
                                                    contentDescription = "Change Server",
                                                    tint = Color(0xFF00E5FF),
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                val activeServerLabel = if (selectedGlobalServerId == "server_2") "Server 2" else "Server 1"
                                                Text(
                                                    text = activeServerLabel,
                                                    color = Color(0xFF00E5FF),
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Expandable Description
                            item {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    AnimatedVisibility(
                                        visible = isDescriptionExpanded,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut()
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
                                            Text(content.description?.takeIf { it.isNotBlank() } ?: content.synopsis, color = Color(0xFFCCD0DB), fontSize = 12.sp, lineHeight = 17.sp)
                                        }
                                    }
                                }
                            }

                            // 📺 EPISODE PILLS (EP 1, EP 2, EP 3...)
                            val displayEpisodes = playerState.episodes.ifEmpty {
                                (1..(content.totalEpisodes.coerceAtLeast(1))).map { num ->
                                    EpisodeDto(
                                        episodeNumber = num,
                                        rawTitle = "Episode $num",
                                        isLocked = num > 1
                                    )
                                }
                            }

                            item {
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(displayEpisodes.size) { index ->
                                        val ep = displayEpisodes[index]
                                        val isSelected = (currentEp?.episodeNumber ?: 1) == ep.episodeNumber
                                        val isEpLocked = shouldLockEpisodes && ep.isLocked

                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) Color(0xFF0F261C) else Color(0xFF131722),
                                            border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) Color(0xFF00D166) else Color(0xFF222838)),
                                            modifier = Modifier.widthIn(min = 84.dp).clickable {
                                                if (isEpLocked) viewModel.showEpisodeUnlockModal(ep)
                                                else viewModel.selectEpisode(ep)
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
                                                    EqualizerBarsIcon(modifier = Modifier.size(12.dp, 14.dp), tint = Color(0xFF00D166))
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

                            // Ad Banner
                            item {
                                StartAppBanner(
                                    isVip = playerState.isVip,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
                                )
                            }

                            // 📑 Tabs Header (0 = For you, 1 = Comments)
                            item {
                                Surface(color = Color(0xFF0C0F15), modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "For you",
                                            color = if (selectedTabIndex == 0) Color.White else Color(0xFF8E95A5),
                                            fontSize = 13.5.sp,
                                            fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Medium,
                                            modifier = Modifier.clickable { selectedTabIndex = 0 }
                                        )
                                        Text(
                                            text = "Comments (${playerState.comments.size})",
                                            color = if (selectedTabIndex == 1) Color.White else Color(0xFF8E95A5),
                                            fontSize = 13.5.sp,
                                            fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Medium,
                                            modifier = Modifier.clickable {
                                                selectedTabIndex = 1
                                                viewModel.refreshComments()
                                            }
                                        )
                                    }
                                }
                            }

                            // Tab 0: For You Grid
                            if (selectedTabIndex == 0) {
                                val displayList = shuffledRecommendations.ifEmpty {
                                    (playerState.recommendations + homeState.popularDramas).filter { it.slug != currentActiveSlug }
                                }
                                val dramaRows = displayList.chunked(3)
                                items(dramaRows.size) { rowIndex ->
                                    val rowDramas = dramaRows[rowIndex]
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        for (drama in rowDramas) {
                                            val cardTitle = cleanDramaTitle(drama.title)
                                            Column(
                                                modifier = Modifier.weight(1f).clickable {
                                                    dramaHistoryStack.add(currentActiveSlug)
                                                    currentActiveSlug = drama.slug
                                                    viewModel.loadDramaDetails(drama.slug, context)
                                                    onRelatedDramaClick(drama.slug)
                                                }
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .aspectRatio(0.72f)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .border(1.dp, shiningBorderBrush, RoundedCornerShape(8.dp))
                                                        .background(Color(0xFF141A26))
                                                ) {
                                                    AsyncImage(
                                                        model = drama.posterUrl ?: drama.bannerUrl,
                                                        contentDescription = cardTitle,
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.BottomStart)
                                                            .padding(4.dp)
                                                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                                    ) {
                                                        Text("${drama.totalEpisodes} Episodes", color = Color(0xFFE2E8F0), fontSize = 9.sp)
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(cardTitle, color = Color(0xFFCCD0DB), fontSize = 11.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                        repeat(3 - rowDramas.size) { Spacer(modifier = Modifier.weight(1f)) }
                                    }
                                }
                            }

                            // Tab 1: Comments
                            if (selectedTabIndex == 1) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF161F30)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(userInitials, color = Color(0xFFFFC107), fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Box(
                                            modifier = Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(21.dp)).background(Color(0xFF131926)).padding(horizontal = 16.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            if (inlineCommentText.isEmpty()) Text("Add a comment...", color = Color(0xFF64748B), fontSize = 13.5.sp)
                                            BasicTextField(
                                                value = inlineCommentText,
                                                onValueChange = { inlineCommentText = it },
                                                textStyle = TextStyle(color = Color.White, fontSize = 13.5.sp),
                                                cursorBrush = SolidColor(Color(0xFFFFC107)),
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                                keyboardActions = KeyboardActions(onSend = {
                                                    if (inlineCommentText.isNotBlank()) {
                                                        viewModel.postComment(inlineCommentText.trim())
                                                        inlineCommentText = ""
                                                        keyboardController?.hide()
                                                    }
                                                }),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                if (inlineCommentText.isNotBlank()) {
                                                    viewModel.postComment(inlineCommentText.trim())
                                                    inlineCommentText = ""
                                                    keyboardController?.hide()
                                                }
                                            },
                                            modifier = Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFFFC107))
                                        ) {
                                            Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.Black, modifier = Modifier.size(19.dp))
                                        }
                                    }
                                }

                                items(playerState.comments.size) { index ->
                                    val comment = playerState.comments[index]
                                    ModernCommentRowItem(
                                        comment = comment,
                                        onLike = { viewModel.toggleCommentLike(comment.id) },
                                        onOpenReplies = { selectedThreadParentComment = comment },
                                        onShare = {}
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 🔀 ৩. সার্ভার সিলেক্টর ডায়ালগ (Server 1 = R2, Server 2 = Byse)
        // =========================================================================
        if (showServerSelectorSheet) {
            Dialog(
                onDismissRequest = { showServerSelectorSheet = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f))
                        .clickable { showServerSelectorSheet = false },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = false) {},
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF10141E))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Dns, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                                    Text("Select Video Server", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }

                                IconButton(onClick = { showServerSelectorSheet = false }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF8E95A5))
                                }
                            }

                            Text("If current stream buffers or does not load, please switch server below:", color = Color(0xFF8E95A5), fontSize = 12.sp)

                            availableGlobalServers.forEach { srv ->
                                val isSelected = (selectedGlobalServerId == srv.id)

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) Color(0xFF0E272C) else Color(0xFF181D2A),
                                    border = BorderStroke(if (isSelected) 1.2.dp else 0.6.dp, if (isSelected) Color(0xFF00E5FF) else Color(0xFF283144)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedGlobalServerId = srv.id
                                            showServerSelectorSheet = false
                                            Toast.makeText(context, "Switched to ${srv.displayName}", Toast.LENGTH_SHORT).show()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Icon(
                                                imageVector = if (!srv.isEmbed) Icons.Default.FlashOn else Icons.Default.PlayCircle,
                                                contentDescription = null,
                                                tint = if (isSelected) Color(0xFF00E5FF) else Color(0xFF8E95A5),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = srv.displayName,
                                                    color = if (isSelected) Color.White else Color(0xFFDCE0E8),
                                                    fontSize = 14.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                )
                                                Text(
                                                    text = srv.providerInfo,
                                                    color = Color(0xFF7E869E),
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }

                                        if (isSelected) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = "Active", tint = Color(0xFF00E5FF), modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }

        // 📥 ৪. Resources Detector BottomSheet
        if (showDownloadSheet) {
            DownloadResourceSheet(
                title = cleanDramaTitle(content.title),
                downloadUrl = downloadUrl,
                onDismiss = { showDownloadSheet = false },
                onDownloadNow = {
                    showDownloadSheet = false
                    R2DownloadManager.startDownload(
                        context = context,
                        downloadUrl = downloadUrl,
                        title = cleanDramaTitle(content.title),
                        episodeNumber = currentEp?.episodeNumber ?: 1,
                        isMovie = (currentEp?.episodeNumber ?: 1) <= 1 && totalDurationMs > 3600000L
                    )
                },
                onOpenDownloadsPage = {
                    showDownloadSheet = false
                    onNavigateToDownloads()
                }
            )
        }

        if (showAuthSheet) {
            AuthBottomSheetDialog(viewModel = viewModel, onDismiss = { showAuthSheet = false })
        }

        if (shouldLockEpisodes && playerState.showEpisodeUnlockModal && playerState.lockedEpisodeTarget != null) {
            val lockedTarget = playerState.lockedEpisodeTarget!!
            CompactUnlockEpisodeDialog(
                episodeNumber = lockedTarget.episodeNumber,
                onDismiss = { viewModel.dismissEpisodeUnlockModal() },
                onWatchAd = {
                    val act = activity ?: findActivityFromContext(context)
                    if (act != null) {
                        StartIoAdManager.showRewardedAd(act) { isRewarded ->
                            if (isRewarded) {
                                viewModel.unlockEpisodeWithRewardAd(context, currentActiveSlug, lockedTarget)
                                Toast.makeText(context, "Episode ${lockedTarget.episodeNumber} unlocked for 2 hours!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        viewModel.unlockEpisodeWithRewardAd(context, currentActiveSlug, lockedTarget)
                    }
                },
                onUpgradeVip = {
                    viewModel.dismissEpisodeUnlockModal()
                    onNavigateToVip()
                }
            )
        }
    }
}
