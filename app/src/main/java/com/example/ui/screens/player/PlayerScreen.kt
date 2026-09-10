package com.example.ui.screens.player

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
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.ads.StartAppBanner
import com.example.ads.StartIoAdManager
import com.example.ads.UnifiedAdManager
import com.example.data.model.ContentItemDto
import com.example.data.model.DramaApiComment
import com.example.data.model.EpisodeDto
import com.example.ui.components.AuthBottomSheetDialog
import com.example.ui.components.DownloadResourceSheet
import com.example.ui.screens.*
import com.example.ui.screens.player.components.*
import com.example.ui.viewmodel.DramaFlixViewModel
import com.example.util.R2DownloadManager
import kotlinx.coroutines.delay

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

// 🎯 এটি কি আসলেই সরাসরি এমপি৪/এইচএলএস স্ট্রিম কি না তা শতভাগ নিশ্চিত করার ফাংশন
private fun isDirectMediaUrl(rawUrl: String?): Boolean {
    if (rawUrl.isNullOrBlank()) return false
    val url = rawUrl.trim().lowercase()

    // ডাটাবেজের খালি বা টেক্সট নাল ভ্যালু ফিল্টার
    if (url == "null" || url == "none" || url == "n/a" || url == "undefined") return false
    if (!url.startsWith("http://") && !url.startsWith("https://")) return false

    // কোনো প্রকার এম্বেড/আইফ্রেম বা থার্ড পার্টি ডোমেইন থাকলে এটি সার্ভার ১ হতে পারবে না
    if (url.contains("/e/") || url.contains("/embed") || url.contains("byse") ||
        url.contains("streamtape") || url.contains("streamwish") || url.contains("dood") ||
        url.contains("vidhide") || url.contains("youtube") || url.contains("iframe")
    ) {
        return false
    }

    // সরাসরি ভিডিও এক্সটেনশন থাকা বাধ্যতামূলক
    val cleanUrl = url.substringBefore("?").substringBefore("#")
    return cleanUrl.endsWith(".mp4") ||
            cleanUrl.endsWith(".m3u8") ||
            cleanUrl.endsWith(".mpd") ||
            cleanUrl.endsWith(".m4v")
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = remember(context) { findActivityFromContext(context) }
    val configuration = LocalConfiguration.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val isDeviceLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isAnyFullscreen = isDeviceLandscape

    var currentActiveSlug by remember(slug) { mutableStateOf(slug) }
    val dramaHistoryStack = remember { mutableStateListOf<String>() }

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

    var selectedGlobalServerId by rememberSaveable { mutableStateOf("server_1") }

    var showAuthSheet by remember { mutableStateOf(false) }
    var showDownloadSheet by remember { mutableStateOf(false) }

    var showServerSelectorSheet by remember { mutableStateOf(false) }
    var showAllEpisodesSheet by remember { mutableStateOf(false) }

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var inlineCommentText by remember { mutableStateOf("") }

    var shuffledRecommendations by remember { mutableStateOf<List<ContentItemDto>>(emptyList()) }
    var selectedThreadParentComment by remember { mutableStateOf<DramaApiComment?>(null) }
    var threadReplyText by remember { mutableStateOf("") }
    var isDescriptionExpanded by remember { mutableStateOf(false) }

    var embedCustomView by remember { mutableStateOf<View?>(null) }
    var embedCustomViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

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
        if (embedCustomView != null) {
            embedCustomViewCallback?.onCustomViewHidden()
            embedCustomView = null
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else if (showAllEpisodesSheet) {
            showAllEpisodesSheet = false
        } else if (showServerSelectorSheet) {
            showServerSelectorSheet = false
        } else if (isDeviceLandscape) {
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
                setSupportZoom(false)
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/128.0.0.0 Mobile Safari/537.36"
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

                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    embedCustomView = view
                    embedCustomViewCallback = callback
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }

                override fun onHideCustomView() {
                    embedCustomViewCallback?.onCustomViewHidden()
                    embedCustomView = null
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            }
        }
    }

    LaunchedEffect(isAnyFullscreen, embedCustomView) {
        activity?.let { act ->
            val window = act.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (isAnyFullscreen || embedCustomView != null) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    exoPlayer.pause()
                    persistentWebView.onPause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    persistentWebView.onResume()
                    if (!useWebPlayerFallback) exoPlayer.play()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
                if (activeStreamUrl.isNotBlank()) {
                    useWebPlayerFallback = true
                    persistentWebView.loadUrl(activeStreamUrl)
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

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

    // =========================================================================
    // 🎯 ১০০% নির্ভুল সার্ভার ফিল্টারিং লজিক
    // =========================================================================
    val currentEp = playerState.currentEpisode ?: playerState.episodes.firstOrNull()

    // 🎯 সার্ভার ১ শুধুমাত্র তখনই আসবে যদি ডাটাবেজে বাস্তব ডিরেক্ট MP4/M3U8 ফাইল থাকে
    val hasServer1Available = remember(currentEp) {
        if (currentEp == null) false
        else {
            val appStream = currentEp.appStreamUrl
            val dlUrl = currentEp.downloadUrl
            val vUrl = currentEp.videoUrl

            isDirectMediaUrl(appStream) || isDirectMediaUrl(dlUrl) || isDirectMediaUrl(vUrl)
        }
    }

    // 🎯 সার্ভার ২ (Embed / Byse / Web Player লিংক থাকলে ট্রু হবে)
    val hasServer2Available = remember(currentEp, playerState.servers) {
        if (currentEp == null) false
        else {
            val em = currentEp.embedUrl?.trim() ?: ""
            val wp = currentEp.webPlayerUrl?.trim() ?: ""
            val vu = currentEp.videoUrl?.trim() ?: ""

            val hasValidEmbed = em.isNotBlank() && em != "null"
            val hasValidWebPlayer = wp.isNotBlank() && wp != "null"
            // যদি videoUrl-এ লিংক থাকে কিন্তু সেটি ডিরেক্ট MP4 না হয়, তবে সেটি সার্ভার ২
            val hasWebPlayerInVideoUrl = vu.isNotBlank() && vu != "null" && !isDirectMediaUrl(vu)

            val hasGlobalServers = playerState.servers.any { srv ->
                val raw = srv.rawUrl?.trim() ?: ""
                val embed = srv.embedUrl?.trim() ?: ""
                (raw.isNotBlank() && raw != "null") || (embed.isNotBlank() && embed != "null")
            }

            hasValidEmbed || hasValidWebPlayer || hasWebPlayerInVideoUrl || hasGlobalServers
        }
    }

    // 🎯 শুধুমাত্র ডাটাবেজে উপস্থিত সার্ভারগুলোই তালিকায় যোগ হবে
    val availableGlobalServers = remember(hasServer1Available, hasServer2Available) {
        buildList {
            if (hasServer1Available) {
                add(
                    GlobalStreamServer(
                        id = "server_1",
                        displayName = "Server 1",
                        providerInfo = "Ultra Fast HD Node (1080p)",
                        isEmbed = false
                    )
                )
            }
            if (hasServer2Available) {
                add(
                    GlobalStreamServer(
                        id = "server_2",
                        displayName = "Server 2",
                        providerInfo = "High-Speed Stream Node",
                        isEmbed = true
                    )
                )
            }
        }
    }

    // 🎯 অটো-কানেক্ট লজিক: সার্ভার ১ না থাকলে সরাসরি সার্ভার ২ সিলেক্ট ও চালু হবে
    LaunchedEffect(hasServer1Available, hasServer2Available) {
        if (hasServer2Available && !hasServer1Available) {
            selectedGlobalServerId = "server_2"
        } else if (hasServer1Available && !hasServer2Available) {
            selectedGlobalServerId = "server_1"
        } else if (hasServer1Available && hasServer2Available) {
            if (selectedGlobalServerId != "server_1" && selectedGlobalServerId != "server_2") {
                selectedGlobalServerId = "server_1"
            }
        }
    }

    // 🎬 ভিডিও লিংক লোডিং লজিক
    LaunchedEffect(
        currentEp?.episodeNumber,
        currentEp?.episodeId,
        selectedGlobalServerId,
        currentActiveSlug
    ) {
        if (currentEp != null) {
            if (shouldLockEpisodes && currentEp.isLocked) {
                exoPlayer.pause()
                viewModel.showEpisodeUnlockModal(currentEp)
                return@LaunchedEffect
            }

            val (resolvedUrl, isEmbed) = if (selectedGlobalServerId == "server_2") {
                val byseCandidate = currentEp.embedUrl?.takeIf { it.isNotBlank() && it != "null" }
                    ?: currentEp.webPlayerUrl?.takeIf { it.isNotBlank() && it != "null" }
                    ?: currentEp.videoUrl?.takeIf { it.isNotBlank() && it != "null" && !isDirectMediaUrl(it) }
                    ?: ""
                Pair(byseCandidate, true)
            } else {
                val directCandidate = currentEp.appStreamUrl?.takeIf { isDirectMediaUrl(it) }
                    ?: currentEp.videoUrl?.takeIf { isDirectMediaUrl(it) }
                    ?: currentEp.downloadUrl?.takeIf { isDirectMediaUrl(it) }
                    ?: ""
                Pair(directCandidate, false)
            }

            if (resolvedUrl.isBlank()) return@LaunchedEffect

            val epUniqueKey = "${currentEp.episodeId}_${currentEp.episodeNumber}_${selectedGlobalServerId}"
            if (epUniqueKey == currentLoadedEpKey && activeStreamUrl == resolvedUrl) return@LaunchedEffect

            currentLoadedEpKey = epUniqueKey
            activeStreamUrl = resolvedUrl

            if (isEmbed) {
                useWebPlayerFallback = true
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                persistentWebView.loadUrl(resolvedUrl)
            } else {
                useWebPlayerFallback = false
                persistentWebView.loadUrl("about:blank")
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
        if (embedCustomView != null) {
            AndroidView(
                factory = { embedCustomView!! },
                modifier = Modifier.fillMaxSize().background(Color.Black)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (!isAnyFullscreen) Modifier.statusBarsPadding() else Modifier)
            ) {
                Box(
                    modifier = if (isAnyFullscreen) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier.fillMaxWidth().aspectRatio(16f / 9f)
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
                            onLikeComment = { commentId: String -> viewModel.toggleCommentLike(commentId) }
                        )
                    } else {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().background(Color(0xFF0C0F15)),
                                contentPadding = PaddingValues(bottom = 32.dp)
                            ) {
                                val shortTitle = cleanDramaTitle(content.title)

                                item {
                                    PlayerHeaderSection(
                                        content = content,
                                        shortTitle = shortTitle,
                                        viewsCount = playerState.viewsCount,
                                        likesCount = playerState.likesCount.toLong(),
                                        isLiked = playerState.isLiked,
                                        isInWatchlist = playerState.isInWatchlist,
                                        isDescriptionExpanded = isDescriptionExpanded,
                                        onPreviousClick = {
                                            StartIoAdManager.showInterstitial(context, isVip = playerState.isVip) {
                                                viewModel.playPreviousEpisode()
                                            }
                                        },
                                        onNextClick = {
                                            StartIoAdManager.showInterstitial(context, isVip = playerState.isVip) {
                                                viewModel.playNextEpisode()
                                            }
                                        },
                                        onToggleDescription = { isDescriptionExpanded = !isDescriptionExpanded },
                                        onLikeClick = {
                                            if (!authState.isLoggedIn) showAuthSheet = true
                                            else viewModel.toggleLikeDrama()
                                        },
                                        onWatchlistClick = { viewModel.toggleWatchlist() },
                                        onServerIconClick = { showServerSelectorSheet = true }
                                    )
                                }

                                val displayEpisodes = playerState.episodes.ifEmpty {
                                    (1..(content.totalEpisodes.coerceAtLeast(1))).map { num ->
                                        EpisodeDto(episodeNumber = num, rawTitle = "Episode $num", isLocked = num > 1)
                                    }
                                }

                                item {
                                    PlayerEpisodesRow(
                                        episodes = displayEpisodes,
                                        currentEpNumber = currentEp?.episodeNumber ?: 1,
                                        shouldLockEpisodes = shouldLockEpisodes,
                                        onAllClick = { showAllEpisodesSheet = true },
                                        onEpisodeSelect = { ep ->
                                            if (shouldLockEpisodes && ep.isLocked) viewModel.showEpisodeUnlockModal(ep)
                                            else viewModel.selectEpisode(ep)
                                        }
                                    )
                                }

                                item {
                                    StartAppBanner(
                                        isVip = playerState.isVip,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
                                    )
                                }

                                item {
                                    PlayerTabsHeader(
                                        selectedTabIndex = selectedTabIndex,
                                        commentsCount = playerState.comments.size,
                                        onTabSelected = { idx ->
                                            selectedTabIndex = idx
                                            if (idx == 1) viewModel.refreshComments()
                                        }
                                    )
                                }

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
                                                PlayerRecommendationCard(
                                                    drama = drama,
                                                    cardTitle = cleanDramaTitle(drama.title),
                                                    shiningBorderBrush = shiningBorderBrush,
                                                    onClick = {
                                                        dramaHistoryStack.add(currentActiveSlug)
                                                        currentActiveSlug = drama.slug
                                                        viewModel.loadDramaDetails(drama.slug, context)
                                                        onRelatedDramaClick(drama.slug)
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            repeat(3 - rowDramas.size) { Spacer(modifier = Modifier.weight(1f)) }
                                        }
                                    }
                                }

                                if (selectedTabIndex == 1) {
                                    item {
                                        PlayerInlineCommentInput(
                                            userInitials = userInitials,
                                            text = inlineCommentText,
                                            onTextChange = { inlineCommentText = it },
                                            onSend = {
                                                if (inlineCommentText.isNotBlank()) {
                                                    viewModel.postComment(inlineCommentText.trim())
                                                    inlineCommentText = ""
                                                    keyboardController?.hide()
                                                }
                                            }
                                        )
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

                            if (showAllEpisodesSheet) {
                                val allEps = playerState.episodes.ifEmpty {
                                    (1..(content.totalEpisodes.coerceAtLeast(1))).map { num ->
                                        EpisodeDto(episodeNumber = num, rawTitle = "Episode $num", isLocked = num > 1)
                                    }
                                }
                                PlayerAllEpisodesSheet(
                                    episodes = allEps,
                                    currentEpNumber = currentEp?.episodeNumber ?: 1,
                                    shouldLockEpisodes = shouldLockEpisodes,
                                    onClose = { showAllEpisodesSheet = false },
                                    onSelectEpisode = { ep ->
                                        showAllEpisodesSheet = false
                                        if (shouldLockEpisodes && ep.isLocked) viewModel.showEpisodeUnlockModal(ep)
                                        else viewModel.selectEpisode(ep)
                                    }
                                )
                            }

                            // 🎯 শুধুমাত্র ডাটাবেজে উপস্থিত সার্ভারগুলোর তালিকা
                            if (showServerSelectorSheet) {
                                PlayerServerSelectorSheet(
                                    servers = availableGlobalServers,
                                    selectedServerId = selectedGlobalServerId,
                                    onClose = { showServerSelectorSheet = false },
                                    onSelectServer = { srv: GlobalStreamServer ->
                                        selectedGlobalServerId = srv.id
                                        showServerSelectorSheet = false
                                        Toast.makeText(context, "Switched to ${srv.displayName}", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

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
