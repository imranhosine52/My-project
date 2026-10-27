@file:kotlin.OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.media3.common.util.UnstableApi::class
)

package com.example.ui.screens.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.ads.UnifiedAdManager
import com.example.data.model.ContentItemDto
import com.example.data.model.CustomAdsConfigResponse
import com.example.data.model.CustomVideoAdDto
import com.example.data.model.DramaApiComment
import com.example.data.model.EpisodeDto
import com.example.ui.components.AuthBottomSheetDialog
import com.example.ui.components.CustomVideoAdDialog
import com.example.ui.components.DownloadResourceSheet
import com.example.ui.screens.*
import com.example.ui.screens.chat.components.TelegramMediaPickerSheet
import com.example.ui.screens.player.components.*
import com.example.ui.viewmodel.DramaFlixViewModel
import com.example.util.AppAnalyticsTracker
import com.example.util.R2DownloadManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private fun findActivityFromContext(context: Context): Activity? {
    var current = context
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun isDirectStreamableUrl(rawUrl: String?): Boolean {
    if (rawUrl.isNullOrBlank()) return false
    val url = rawUrl.trim().lowercase()

    if (url == "null" || url == "none" || url == "n/a" || url == "undefined") return false
    if (!url.startsWith("http://") && !url.startsWith("https://")) return false

    if (url.contains("/e/") || url.contains("/embed") || url.contains("byse") ||
        url.contains("streamtape") || url.contains("streamwish") || url.contains("dood") ||
        url.contains("vidhide") || url.contains("youtube") || url.contains("iframe")
    ) {
        return false
    }

    val cleanUrl = url.substringBefore("?").substringBefore("#")
    return cleanUrl.endsWith(".mp4") ||
            cleanUrl.endsWith(".m3u8") ||
            cleanUrl.endsWith(".mkv") ||
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
    val coroutineScope = rememberCoroutineScope()

    val isDeviceLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isAnyFullscreen = isDeviceLandscape

    val playerState by viewModel.playerUiState.collectAsStateWithLifecycle()
    val authState by viewModel.authUiState.collectAsStateWithLifecycle()
    val homeState by viewModel.homeUiState.collectAsStateWithLifecycle()

    val isUserLoggedIn = authState.isLoggedIn

    val isUserVip = playerState.isVip ||
            authState.isVip ||
            (authState.userProfile?.isVip == true) ||
            (authState.userProfile?.plan?.lowercase() == "vip") ||
            (authState.userProfile?.plan?.lowercase() == "premium")

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedThreadParentComment by remember { mutableStateOf<DramaApiComment?>(null) }
    var threadReplyText by remember { mutableStateOf("") }

    // =========================================================================
    // 🔙 পারফেক্ট ব্যাক বাটন হ্যান্ডলার
    // =========================================================================
    fun handleBackNavigation() {
        when {
            isAnyFullscreen || isDeviceLandscape -> {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            selectedThreadParentComment != null -> {
                selectedThreadParentComment = null
            }
            selectedTabIndex == 1 -> {
                selectedTabIndex = 0
            }
            else -> {
                onBackClick()
            }
        }
    }

    BackHandler { handleBackNavigation() }

    if (playerState.isNotFound) {
        NotFoundScreen(
            onNavigateHome = { onBackClick() },
            onNavigateSearch = { onBackClick() },
            onBackClick = { handleBackNavigation() },
            customMessage = "আপনি যে ড্রামা বা ভিডিওটি দেখতে চেয়েছেন তা সার্ভারে পাওয়া যায়নি অথবা মুছে ফেলা হয়েছে।"
        )
        return
    }

    val content = playerState.content
        ?: homeState.popularDramas.find { it.slug == slug }
        ?: ContentItemDto(title = "Loading...", slug = slug)

    val cleanShortTitle = remember(content) {
        content.displayName
    }

    val currentContentId = remember(content.id, slug) {
        content.id.ifBlank { slug }
    }

    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    val deletedCommentPrefs = remember { context.getSharedPreferences("drama_deleted_comments_prefs", Context.MODE_PRIVATE) }
    val deletedCommentIds = remember {
        mutableStateListOf<String>().apply {
            val saved = deletedCommentPrefs.getStringSet("deleted_comment_ids", emptySet()) ?: emptySet()
            addAll(saved)
        }
    }

    val persistentDramaComments = remember(slug) { mutableStateListOf<DramaApiComment>() }
    val mainScrollListState = rememberLazyListState()

    var isMediaSendingLock by remember { mutableStateOf(false) }

    var customAdsConfig by remember { mutableStateOf<CustomAdsConfigResponse?>(null) }
    var activeCustomVideoAd by remember { mutableStateOf<CustomVideoAdDto?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder().url("https://playdramaflix.com/api/v1/custom-ads").build()
                val response = client.newCall(request).execute()
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful && body.isNotBlank()) {
                    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                    val adapter = moshi.adapter(CustomAdsConfigResponse::class.java)
                    customAdsConfig = adapter.fromJson(body)
                }
            } catch (_: Exception) {}
        }
    }

    val adConfig by UnifiedAdManager.adConfigState.collectAsStateWithLifecycle()
    val shouldLockEpisodes = !isUserVip && adConfig.adsEnabled

    val requiredAdClicks = remember(adConfig) {
        adConfig.rules?.timerSeconds?.let { (it / 5).coerceIn(1, 3) } ?: 2
    }

    var completedAdClicks by remember(slug, playerState.lockedEpisodeTarget?.episodeNumber) {
        mutableIntStateOf(0)
    }

    var showFireworksCelebration by remember { mutableStateOf(false) }

    fun unlockEpisodeForOneMinute(ep: EpisodeDto) {
        val prefs = context.getSharedPreferences("drama_flix_unlocked_episodes_prefs", Context.MODE_PRIVATE)
        val oneMinuteExpiry = System.currentTimeMillis() + (60 * 1000L)
        val storageKey = "unlock_expiry_${slug}_ep${ep.episodeNumber}"

        prefs.edit().putLong(storageKey, oneMinuteExpiry).apply()
        viewModel.selectEpisode(ep.copy(isLocked = false))

        coroutineScope.launch {
            delay(60_000L)
            if (!isUserVip) {
                prefs.edit().remove(storageKey).apply()
            }
        }
    }

    fun handleMultiClickAdUnlock(ep: EpisodeDto) {
        UnifiedAdManager.openAdsterraDirectLink(context, isVip = false)
        completedAdClicks++

        if (completedAdClicks >= requiredAdClicks) {
            completedAdClicks = 0
            viewModel.dismissEpisodeUnlockModal()
            showFireworksCelebration = true
            unlockEpisodeForOneMinute(ep)
        } else {
            val remainingClicks = requiredAdClicks - completedAdClicks
            Toast.makeText(
                context,
                "✓ Ad $completedAdClicks completed! Complete $remainingClicks more to unlock.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    LaunchedEffect(slug) {
        persistentDramaComments.clear()
        completedAdClicks = 0

        val initialTitle = homeState.popularDramas.find { it.slug == slug }?.displayName
            ?: homeState.recentlyAdded.find { it.slug == slug }?.displayName
            ?: homeState.shortsContent.find { it.slug == slug }?.displayName
            ?: slug.replace("-", " ").replaceFirstChar { it.uppercase() }

        val numericUid = authState.userProfile?.id?.filter { it.isDigit() }?.toIntOrNull()
        AppAnalyticsTracker.trackScreen(
            context,
            "Watching: $initialTitle",
            numericUid
        )

        viewModel.loadDramaDetails(slug, context)
    }

    // =========================================================================
    // 🎯 ফিক্সড: কমেন্টের আইডি ও ড্রামা স্লাগ মিসম্যাচে কমেন্ট হাইড হওয়া বন্ধ করা হলো
    // =========================================================================
    LaunchedEffect(playerState.comments, slug, currentContentId) {
        val serverComments = playerState.comments.filter { comment ->
            comment.id !in deletedCommentIds // শুধুমাত্র ডিলিট হওয়া কমেন্টগুলো বাদ যাবে
        }

        val serverTexts = serverComments.map { it.commentText.trim() }.toSet()
        val uniquePendingOptimistic = persistentDramaComments.filter {
            (it.id.startsWith("temp_sticker_") || it.id.startsWith("temp_gif_") || it.id.startsWith("temp_text_")) &&
            it.id !in deletedCommentIds &&
            it.commentText.trim() !in serverTexts
        }

        persistentDramaComments.clear()
        persistentDramaComments.addAll(uniquePendingOptimistic)
        persistentDramaComments.addAll(serverComments)
    }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }

    var useWebPlayerFallback by rememberSaveable { mutableStateOf(false) }
    var activeStreamUrl by rememberSaveable { mutableStateOf("") }
    var currentLoadedEpKey by rememberSaveable { mutableStateOf("") }

    var showAuthSheet by remember { mutableStateOf(false) }
    var showDownloadSheet by remember { mutableStateOf(false) }
    var showBatchDownloadDialog by remember { mutableStateOf(false) }
    var showAllEpisodesSheet by remember { mutableStateOf(false) }

    var inlineCommentText by remember { mutableStateOf("") }
    var showCommentMediaPicker by remember { mutableStateOf(false) }

    val currentUser = authState.userProfile
    val currentUserName = currentUser?.displayName ?: "User"

    val savedPrefsAvatar = remember {
        context.getSharedPreferences("play_drama_flix_auth_prefs", Context.MODE_PRIVATE)
            .getString("user_avatar", null)?.takeIf { it.isNotBlank() }
    }

    val currentUserAvatar = remember(currentUser?.avatar, savedPrefsAvatar) {
        currentUser?.avatar?.takeIf { it.isNotBlank() }
            ?: currentUser?.effectiveAvatar?.takeIf { it.isNotBlank() }
            ?: savedPrefsAvatar
            ?: ""
    }

    val userInitials = remember(currentUserName) {
        val parts = currentUserName.trim().split(" ").filter { it.isNotBlank() }
        if (parts.size >= 2) "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
        else currentUserName.take(2).uppercase()
    }

    var shuffledRecommendations by remember { mutableStateOf<List<ContentItemDto>>(emptyList()) }
    var isDescriptionExpanded by remember { mutableStateOf(false) }

    var embedCustomView by remember { mutableStateOf<View?>(null) }
    var embedCustomViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    // =========================================================================
    // 💾 শেষ দেখা ১০ নম্বর এপিসোড মেমোরি
    // =========================================================================
    val lastWatchedEpPrefs = remember {
        context.getSharedPreferences("drama_last_watched_ep_prefs", Context.MODE_PRIVATE)
    }

    val savedEpisodeNumber = remember(slug) {
        lastWatchedEpPrefs.getInt("last_ep_$slug", 1)
    }

    val effectiveEpisodes = remember(playerState.episodes, content.totalEpisodes) {
        if (playerState.episodes.isNotEmpty()) playerState.episodes else {
            (1..(content.totalEpisodes.coerceAtLeast(1))).map { num ->
                EpisodeDto(episodeNumber = num, isLocked = num > 1)
            }
        }
    }

    val currentEp = playerState.currentEpisode?.takeIf { it.episodeNumber > 0 }
        ?: effectiveEpisodes.find { it.episodeNumber == savedEpisodeNumber }
        ?: effectiveEpisodes.firstOrNull()

    LaunchedEffect(slug, currentEp?.episodeNumber) {
        val epNum = currentEp?.episodeNumber ?: 1
        if (slug.isNotBlank() && epNum > 0) {
            lastWatchedEpPrefs.edit().putInt("last_ep_$slug", epNum).apply()
        }
    }

    // =========================================================================
    // 🚀 ExoPlayer ইঞ্জিন
    // =========================================================================
    val exoPlayer = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(6000)
            .setReadTimeoutMs(10000)
            .setDefaultRequestProperties(
                mapOf(
                    "Connection" to "keep-alive",
                    "Keep-Alive" to "timeout=60"
                )
            )
            .setUserAgent("Mozilla/5.0 (Linux; Android 14) PlayDramaFlix Native Player")

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val highSpeedLoadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(60_000, 180_000, 300, 800)
            .setBackBuffer(60_000, true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(highSpeedLoadControl)
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
                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    isWebLoading = false
                    view?.loadDataWithBaseURL(null, "<html><body style='background:#000;'></body></html>", "text/html", "UTF-8", null)
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
                    val isInPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        activity?.isInPictureInPictureMode == true
                    } else false

                    if (!isInPip) {
                        exoPlayer.pause()
                        persistentWebView.onPause()
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    persistentWebView.onResume()
                    if (!useWebPlayerFallback && activeCustomVideoAd == null) {
                        exoPlayer.playWhenReady = true
                        exoPlayer.play()
                    }
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

    DisposableEffect(exoPlayer, currentEp, effectiveEpisodes) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
                    if (activeCustomVideoAd == null) exoPlayer.play()
                } else if (state == Player.STATE_ENDED) {
                    val currentNum = currentEp?.episodeNumber ?: 1
                    val nextEpisode = effectiveEpisodes.find { it.episodeNumber == currentNum + 1 }
                    if (nextEpisode != null) {
                        viewModel.selectEpisode(nextEpisode)
                    } else {
                        viewModel.playNextEpisode()
                    }
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                if (useWebPlayerFallback && activeStreamUrl.isNotBlank()) {
                    persistentWebView.loadUrl(activeStreamUrl)
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(isPlaying, isUserVip) {
        while (isPlaying) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
            if (totalDurationMs > 0) {
                viewModel.updateWatchProgress(currentPositionMs, totalDurationMs)
            }
            delay(500L)
        }
    }

    LaunchedEffect(
        currentEp?.episodeNumber,
        currentEp?.episodeId,
        slug
    ) {
        if (currentEp != null) {
            if (shouldLockEpisodes && currentEp.isLocked) {
                exoPlayer.pause()
                viewModel.showEpisodeUnlockModal(currentEp)
                return@LaunchedEffect
            }

            val appStream = currentEp.appStreamUrl?.trim() ?: ""
            val streamUrl = currentEp.directStreamUrl?.trim() ?: ""
            val videoUrl = currentEp.videoUrl?.trim() ?: ""
            val embedUrl = currentEp.embedUrl?.trim() ?: ""
            val webPlayerUrl = currentEp.webPlayerUrl?.trim() ?: ""

            val directCandidate = when {
                isDirectStreamableUrl(appStream) -> appStream
                isDirectStreamableUrl(streamUrl) -> streamUrl
                isDirectStreamableUrl(videoUrl) -> videoUrl
                else -> ""
            }

            val embedCandidate = when {
                embedUrl.isNotBlank() && embedUrl != "null" -> embedUrl
                webPlayerUrl.isNotBlank() && webPlayerUrl != "null" -> webPlayerUrl
                videoUrl.isNotBlank() && videoUrl != "null" && !isDirectStreamableUrl(videoUrl) -> videoUrl
                else -> ""
            }

            val (resolvedUrl, isEmbed) = when {
                directCandidate.isNotBlank() -> Pair(directCandidate, false)
                embedCandidate.isNotBlank() -> Pair(embedCandidate, true)
                else -> {
                    val fallback = currentEp.resolveR2StreamUrl(slug)
                    Pair(fallback, !isDirectStreamableUrl(fallback))
                }
            }

            if (resolvedUrl.isBlank()) return@LaunchedEffect

            val epUniqueKey = "${currentEp.episodeId}_${currentEp.episodeNumber}"
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
                    val isHls = resolvedUrl.contains(".m3u8", ignoreCase = true)
                    val mediaItem = MediaItem.Builder()
                        .setUri(Uri.parse(resolvedUrl))
                        .setMimeType(if (isHls) MimeTypes.APPLICATION_M3U8 else MimeTypes.APPLICATION_MP4)
                        .build()

                    exoPlayer.setMediaItem(mediaItem, 0L)
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                    if (activeCustomVideoAd == null) {
                        exoPlayer.play()
                    }
                } catch (_: Exception) {
                    if (embedCandidate.isNotBlank()) {
                        useWebPlayerFallback = true
                        persistentWebView.loadUrl(embedCandidate)
                    }
                }
            }
        }
    }

    LaunchedEffect(
        playerState.recommendations,
        homeState.popularDramas,
        homeState.recentlyAdded,
        homeState.dramaSeriesContent,
        homeState.trendingDramas,
        homeState.banglaDubbed,
        homeState.hindiDubbed,
        slug
    ) {
        val currentCategories = content.categories.map { it.lowercase() }
        val currentDub = content.dubBadge.lowercase()

        val allPool = (
            playerState.recommendations +
            homeState.popularDramas +
            homeState.recentlyAdded +
            homeState.dramaSeriesContent +
            homeState.trendingDramas +
            homeState.banglaDubbed +
            homeState.hindiDubbed
        ).distinctBy { it.slug }
            .filter { drama ->
                drama.slug != slug &&
                !drama.isShorts &&
                !drama.slug.contains("shorts", ignoreCase = true)
            }

        val sortedSimilar = allPool.sortedByDescending { drama ->
            var score = 0
            if (drama.categories.any { it.lowercase() in currentCategories }) score += 3
            if (drama.dubBadge.lowercase() == currentDub) score += 2
            score
        }

        val targetCards = if (sortedSimilar.size >= 32) {
            sortedSimilar.take(32)
        } else {
            val remaining = (allPool - sortedSimilar.toSet())
            (sortedSimilar + remaining).take(32)
        }

        shuffledRecommendations = targetCards.shuffled()
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

    val rawDownloadCandidate = remember(currentEp, slug, activeStreamUrl) {
        currentEp?.downloadUrl?.takeIf { it.isNotBlank() }
            ?: currentEp?.appStreamUrl?.takeIf { it.isNotBlank() }
            ?: currentEp?.resolveDownloadUrl(slug)
            ?: activeStreamUrl
    }
    val downloadUrl = remember(rawDownloadCandidate) {
        R2DownloadManager.resolveDirectMp4Url(rawDownloadCandidate)
    }

    fun shareCurrentDrama() {
        try {
            val shareUrl = "https://playdramaflix.com/watch/$slug"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Watch $cleanShortTitle on PlayDramaFlix: $shareUrl")
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
                // =============================================================
                // 📺 ১. ভিডিও প্লেয়ার ফ্রেম
                // =============================================================
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
                            title = cleanShortTitle,
                            slug = slug,
                            episodeNumber = currentEp?.episodeNumber ?: 1,
                            downloadUrl = downloadUrl,
                            isDeviceLandscape = isDeviceLandscape,
                            currentPositionMs = currentPositionMs,
                            totalDurationMs = totalDurationMs,
                            isPlaying = isPlaying,
                            isVip = isUserVip,
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
                            onDownloadClick = { showBatchDownloadDialog = true },
                            episodes = effectiveEpisodes,
                            shouldLockEpisodes = shouldLockEpisodes,
                            onSelectEpisode = { ep ->
                                if (shouldLockEpisodes && ep.isLocked) {
                                    viewModel.showEpisodeUnlockModal(ep)
                                } else {
                                    viewModel.selectEpisode(ep)
                                }
                            },
                            onNextEpisode = {
                                val currentNum = currentEp?.episodeNumber ?: 1
                                val nextEp = effectiveEpisodes.find { it.episodeNumber == currentNum + 1 }
                                if (nextEp != null) {
                                    viewModel.selectEpisode(nextEp)
                                } else {
                                    viewModel.playNextEpisode()
                                }
                            },
                            onSendComment = { text ->
                                if (!isUserLoggedIn) {
                                    Toast.makeText(context, "Please log in to post a comment", Toast.LENGTH_SHORT).show()
                                    showAuthSheet = true
                                    return@PlayerVideoBox
                                }
                                if (text.isNotBlank()) {
                                    viewModel.postComment(text)
                                }
                            },
                            onNavigateToVip = onNavigateToVip,
                            comments = persistentDramaComments,
                            recommendations = shuffledRecommendations,
                            onRelatedDramaClick = { newSlug ->
                                onRelatedDramaClick(newSlug)
                            },
                            posterUrl = content.posterUrl ?: content.bannerUrl,
                            onRetryPlayback = {
                                if (activeStreamUrl.isNotBlank()) {
                                    try {
                                        val isHls = activeStreamUrl.contains(".m3u8", ignoreCase = true)
                                        val mediaItem = MediaItem.Builder()
                                            .setUri(Uri.parse(activeStreamUrl))
                                            .setMimeType(if (isHls) MimeTypes.APPLICATION_M3U8 else MimeTypes.APPLICATION_MP4)
                                            .build()
                                        exoPlayer.setMediaItem(mediaItem, 0L)
                                        exoPlayer.prepare()
                                        exoPlayer.playWhenReady = true
                                        exoPlayer.play()
                                    } catch (_: Exception) {}
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // =============================================================
                // 🔄 ২. ভিডিও ফ্রেমের নিচের অংশ (পোর্ট্রেট মোড)
                // =============================================================
                if (!isAnyFullscreen) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color(0xFF0C0F15))
                    ) {
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
                                    if (!isUserLoggedIn) {
                                        Toast.makeText(context, "Please log in to reply", Toast.LENGTH_SHORT).show()
                                        showAuthSheet = true
                                        return@CommentRepliesThreadView
                                    }
                                    val text = threadReplyText.trim()
                                    if (text.isNotBlank()) {
                                        viewModel.postComment(text, parentId = selectedThreadParentComment!!.id)
                                        threadReplyText = ""
                                        keyboardController?.hide()
                                    }
                                },
                                onLikeComment = { commentId: String -> viewModel.toggleCommentLike(commentId) },
                                onDeleteComment = { commentId ->
                                    deletedCommentIds.add(commentId)
                                    val savedSet = deletedCommentPrefs.getStringSet("deleted_comment_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
                                    savedSet.add(commentId)
                                    deletedCommentPrefs.edit().putStringSet("deleted_comment_ids", savedSet).apply()

                                    persistentDramaComments.removeAll { it.id == commentId }
                                    Toast.makeText(context, "Comment deleted", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        // =========================================================================
                        // 💬 ১. কমেন্ট বক্স মোড (প্লেয়ারের নিচ থেকে ফুলস্ক্রিন, টাইপিং বক্স নিচে পিন)
                        // =========================================================================
                        else if (selectedTabIndex == 1) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF0C0F15))
                                    .pointerInput(selectedTabIndex) {
                                        var totalDragX = 0f
                                        detectHorizontalDragGestures(
                                            onDragStart = { totalDragX = 0f },
                                            onDragEnd = {
                                                if (totalDragX > 45f) {
                                                    selectedTabIndex = 0
                                                }
                                            },
                                            onHorizontalDrag = { change, dragAmount ->
                                                totalDragX += dragAmount
                                                if (kotlin.math.abs(totalDragX) > 20f) {
                                                    change.consume()
                                                }
                                            }
                                        )
                                    }
                            ) {
                                PlayerTabsHeader(
                                    selectedTabIndex = selectedTabIndex,
                                    commentsCount = persistentDramaComments.size,
                                    onTabSelected = { idx -> selectedTabIndex = idx },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF0C0F15))
                                        .padding(vertical = 4.dp)
                                )

                                HorizontalDivider(color = Color(0xFF1E2638), thickness = 0.8.dp)

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                ) {
                                    if (persistentDramaComments.isEmpty()) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "No comments yet. Be the first to comment!",
                                                color = Color(0xFF8E95A5),
                                                fontSize = 13.sp
                                            )
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(vertical = 8.dp)
                                        ) {
                                            items(
                                                count = persistentDramaComments.size,
                                                key = { index -> persistentDramaComments[index].id }
                                            ) { index ->
                                                val comment = persistentDramaComments[index]
                                                ModernCommentRowItem(
                                                    comment = comment,
                                                    currentUserAvatar = currentUserAvatar,
                                                    currentUserName = currentUserName,
                                                    currentUserId = currentUser?.id,
                                                    onLike = {
                                                        val cIdx = persistentDramaComments.indexOfFirst { it.id == comment.id }
                                                        if (cIdx != -1) {
                                                            val c = persistentDramaComments[cIdx]
                                                            val newLiked = !c.isLiked
                                                            val newCount = if (newLiked) c.likesCount + 1 else (c.likesCount - 1).coerceAtLeast(0)
                                                            persistentDramaComments[cIdx] = c.copy(isLikedVal = newLiked, rawLikesCount = newCount)
                                                        }
                                                        viewModel.toggleCommentLike(comment.id)
                                                    },
                                                    onOpenReplies = {
                                                        selectedThreadParentComment = comment
                                                    },
                                                    onShare = {
                                                        try {
                                                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                                type = "text/plain"
                                                                putExtra(Intent.EXTRA_TEXT, "${comment.displayName}: ${comment.commentText}")
                                                            }
                                                            context.startActivity(Intent.createChooser(sendIntent, "Share Comment"))
                                                        } catch (_: Exception) {}
                                                    },
                                                    onDeleteComment = { commentId ->
                                                        deletedCommentIds.add(commentId)
                                                        val savedSet = deletedCommentPrefs.getStringSet("deleted_comment_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
                                                        savedSet.add(commentId)
                                                        deletedCommentPrefs.edit().putStringSet("deleted_comment_ids", savedSet).apply()

                                                        persistentDramaComments.removeAll { it.id == commentId }
                                                        Toast.makeText(context, "Comment deleted", Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // 🎯 টাইপিং বক্স: একদম নিচে পিন এবং কিবোর্ডের সাথে উপরে উঠবে
                                PlayerInlineCommentInput(
                                    userInitials = userInitials,
                                    currentUserAvatar = currentUserAvatar,
                                    isLoggedIn = isUserLoggedIn,
                                    text = inlineCommentText,
                                    onTextChange = { inlineCommentText = it },
                                    onOpenMediaPicker = { showCommentMediaPicker = true },
                                    onRequireLogin = {
                                        Toast.makeText(context, "Please log in to post a comment", Toast.LENGTH_SHORT).show()
                                        showAuthSheet = true
                                    },
                                    onSend = {
                                        if (!isUserLoggedIn) {
                                            Toast.makeText(context, "Please log in to post a comment", Toast.LENGTH_SHORT).show()
                                            showAuthSheet = true
                                            return@PlayerInlineCommentInput
                                        }
                                        val textToSend = inlineCommentText.trim()
                                        if (textToSend.isNotBlank()) {
                                            // 🎯 অপটিমিস্টিক আপডেট: সেন্ড বাটনে চাপামাত্রই সাথে সাথে যেন কমেন্ট লিস্টে শো করে
                                            val tempCommentId = "temp_text_${System.currentTimeMillis()}"
                                            val optimisticComment = DramaApiComment(
                                                rawId = tempCommentId,
                                                rawContentId = content.id,
                                                userName = currentUserName,
                                                userAvatar = currentUserAvatar,
                                                commentText = textToSend,
                                                dateDisplay = "Just now"
                                            )
                                            persistentDramaComments.add(0, optimisticComment)
                                            viewModel.postComment(textToSend)
                                            inlineCommentText = ""
                                            keyboardController?.hide()
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF10141D))
                                        .navigationBarsPadding()
                                        .imePadding()
                                )
                            }
                        }
                        // =========================================================================
                        // 🎬 ২. For you মোড (টাইটেল, এপিসোড রো, স্টিকি ট্যাব ও রিকমেন্ডেশন)
                        // =========================================================================
                        else {
                            PullToRefreshBox(
                                isRefreshing = isRefreshing,
                                onRefresh = {
                                    coroutineScope.launch {
                                        isRefreshing = true
                                        viewModel.loadDramaDetails(slug, context, forceRefresh = true)
                                        viewModel.refreshComments()
                                        delay(600)
                                        isRefreshing = false
                                        Toast.makeText(context, "✓ Updated to latest episodes & links!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                state = pullRefreshState,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                LazyColumn(
                                    state = mainScrollListState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFF0C0F15))
                                        .pointerInput(selectedTabIndex) {
                                            var totalDragX = 0f
                                            detectHorizontalDragGestures(
                                                onDragStart = { totalDragX = 0f },
                                                onDragEnd = {
                                                    if (totalDragX < -45f) {
                                                        selectedTabIndex = 1
                                                    }
                                                },
                                                onHorizontalDrag = { change, dragAmount ->
                                                    totalDragX += dragAmount
                                                    if (kotlin.math.abs(totalDragX) > 20f) {
                                                        change.consume()
                                                    }
                                                }
                                            )
                                        },
                                    contentPadding = PaddingValues(bottom = 80.dp)
                                ) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .pointerInput(Unit) {
                                                    var totalDragY = 0f
                                                    detectVerticalDragGestures(
                                                        onDragStart = { totalDragY = 0f },
                                                        onDragEnd = {
                                                            if (totalDragY > 40f) {
                                                                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                                            }
                                                        },
                                                        onVerticalDrag = { change, dragAmount ->
                                                            totalDragY += dragAmount
                                                            if (totalDragY > 20f) {
                                                                change.consume()
                                                            }
                                                        }
                                                    )
                                                }
                                        ) {
                                            PlayerHeaderSection(
                                                content = content,
                                                shortTitle = cleanShortTitle,
                                                viewsCount = playerState.viewsCount,
                                                likesCount = playerState.likesCount.toLong(),
                                                isLiked = playerState.isLiked,
                                                isInWatchlist = playerState.isInWatchlist,
                                                isDescriptionExpanded = isDescriptionExpanded,
                                                myReaction = playerState.myReaction,
                                                reactions = playerState.reactions,
                                                onPreviousClick = { viewModel.playPreviousEpisode() },
                                                onNextClick = { viewModel.playNextEpisode() },
                                                onToggleDescription = { isDescriptionExpanded = !isDescriptionExpanded },
                                                onLikeClick = {
                                                    if (!isUserLoggedIn) showAuthSheet = true
                                                    else viewModel.toggleLikeDrama()
                                                },
                                                onWatchlistClick = {
                                                    if (!isUserLoggedIn) showAuthSheet = true
                                                    else viewModel.toggleWatchlist()
                                                },
                                                onToggleReaction = { reactionKey ->
                                                    if (!isUserLoggedIn) showAuthSheet = true
                                                    else viewModel.toggleReaction(reactionKey)
                                                },
                                                onReplyClick = {
                                                    selectedTabIndex = 1
                                                },
                                                onForwardClick = { shareCurrentDrama() },
                                                onSwipeDownFullscreen = {
                                                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                                }
                                            )
                                        }
                                    }

                                    item {
                                        PlayerEpisodesRow(
                                            episodes = effectiveEpisodes,
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
                                            isVip = isUserVip,
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
                                        )
                                    }

                                    stickyHeader {
                                        PlayerTabsHeader(
                                            selectedTabIndex = selectedTabIndex,
                                            commentsCount = persistentDramaComments.size,
                                            onTabSelected = { idx ->
                                                selectedTabIndex = idx
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFF0C0F15))
                                                .padding(vertical = 4.dp)
                                        )
                                    }

                                    val dramaRows = shuffledRecommendations.chunked(4)
                                    items(dramaRows.size) { rowIndex ->
                                        val rowDramas = dramaRows[rowIndex]
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            for (drama in rowDramas) {
                                                PlayerRecommendationCard(
                                                    drama = drama,
                                                    cardTitle = drama.displayName,
                                                    shiningBorderBrush = shiningBorderBrush,
                                                    onClick = {
                                                        onRelatedDramaClick(drama.slug)
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            repeat(4 - rowDramas.size) { Spacer(modifier = Modifier.weight(1f)) }
                                        }
                                    }
                                }
                            }
                        }

                        if (showAllEpisodesSheet) {
                            PlayerAllEpisodesSheet(
                                episodes = effectiveEpisodes,
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

                        if (showBatchDownloadDialog) {
                            PlayerBatchDownloadSheet(
                                title = cleanShortTitle,
                                slug = slug,
                                episodes = effectiveEpisodes,
                                isVip = isUserVip,
                                onClose = { showBatchDownloadDialog = false },
                                onNavigateToVip = onNavigateToVip,
                                onDownloadSelected = { selectedList ->
                                    showBatchDownloadDialog = false
                                    selectedList.forEach { ep ->
                                        R2DownloadManager.startDownload(
                                            context = context,
                                            downloadUrl = ep.resolveDownloadUrl(slug),
                                            title = cleanShortTitle,
                                            episodeNumber = ep.episodeNumber,
                                            isMovie = (ep.episodeNumber <= 1 && totalDurationMs > 3600000L)
                                        )
                                    }
                                    Toast.makeText(
                                        context,
                                        "📥 Download started for ${selectedList.size} episodes!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                    }
                }
            }
        }

        activeCustomVideoAd?.let { ad ->
            exoPlayer.pause()
            CustomVideoAdDialog(
                ad = ad,
                onAdFinishedOrSkipped = {
                    activeCustomVideoAd = null
                    exoPlayer.play()
                },
                onNavigateInternalScreen = { target ->
                    activeCustomVideoAd = null
                    when {
                        target.contains("vip", true) -> onNavigateToVip()
                        target.startsWith("drama:") -> {
                            val newSlug = target.removePrefix("drama:").trim()
                            viewModel.loadDramaDetails(newSlug, context)
                        }
                    }
                },
                onOpenExternalUrl = { url ->
                    activeCustomVideoAd = null
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                }
            )
        }

        if (showCommentMediaPicker) {
            ModalBottomSheet(
                onDismissRequest = { showCommentMediaPicker = false },
                containerColor = Color(0xFF17212B),
                scrimColor = Color.Black.copy(alpha = 0.65f),
                dragHandle = null,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            ) {
                TelegramMediaPickerSheet(
                    onSendSticker = { stickerUrl ->
                        if (!isMediaSendingLock) {
                            isMediaSendingLock = true
                            showCommentMediaPicker = false
                            val tempId = "temp_sticker_${System.currentTimeMillis()}"
                            val optimisticSticker = DramaApiComment(
                                rawId = tempId,
                                rawContentId = content.id,
                                userName = currentUserName,
                                userAvatar = currentUserAvatar,
                                commentText = stickerUrl,
                                dateDisplay = "Just now"
                            )
                            persistentDramaComments.add(0, optimisticSticker)
                            viewModel.postComment(stickerUrl)

                            coroutineScope.launch {
                                delay(800L)
                                isMediaSendingLock = false
                            }
                        }
                    },
                    onSendGif = { gifUrl ->
                        if (!isMediaSendingLock) {
                            isMediaSendingLock = true
                            showCommentMediaPicker = false
                            val tempId = "temp_gif_${System.currentTimeMillis()}"
                            val optimisticGif = DramaApiComment(
                                rawId = tempId,
                                rawContentId = content.id,
                                userName = currentUserName,
                                userAvatar = currentUserAvatar,
                                commentText = gifUrl,
                                dateDisplay = "Just now"
                            )
                            persistentDramaComments.add(0, optimisticGif)
                            viewModel.postComment(gifUrl)

                            coroutineScope.launch {
                                delay(800L)
                                isMediaSendingLock = false
                            }
                        }
                    },
                    onSelectEmoji = { emoji ->
                        inlineCommentText += emoji
                    },
                    onClose = { showCommentMediaPicker = false },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (showDownloadSheet) {
            DownloadResourceSheet(
                title = cleanShortTitle,
                downloadUrl = downloadUrl,
                isVip = isUserVip,
                isLongVideo = true,
                onDismiss = { showDownloadSheet = false },
                onDownloadNow = {
                    showDownloadSheet = false
                    R2DownloadManager.startDownload(
                        context = context,
                        downloadUrl = downloadUrl,
                        title = cleanShortTitle,
                        episodeNumber = currentEp?.episodeNumber ?: 1,
                        isMovie = (currentEp?.episodeNumber ?: 1) <= 1 && totalDurationMs > 3600000L
                    )
                },
                onOpenDownloadsPage = {
                    showDownloadSheet = false
                    onNavigateToDownloads()
                },
                onNavigateToVip = onNavigateToVip
            )
        }

        if (showAuthSheet) {
            AuthBottomSheetDialog(viewModel = viewModel, onDismiss = { showAuthSheet = false })
        }

        if (shouldLockEpisodes && playerState.showEpisodeUnlockModal && playerState.lockedEpisodeTarget != null) {
            val lockedTarget = playerState.lockedEpisodeTarget!!
            CompactUnlockEpisodeDialog(
                episodeNumber = lockedTarget.episodeNumber,
                completedSteps = completedAdClicks,
                totalSteps = requiredAdClicks,
                onDismiss = {
                    completedAdClicks = 0
                    viewModel.dismissEpisodeUnlockModal()
                },
                onWatchAd = {
                    handleMultiClickAdUnlock(lockedTarget)
                },
                onUpgradeVip = {
                    viewModel.dismissEpisodeUnlockModal()
                    onNavigateToVip()
                }
            )
        }

        if (showFireworksCelebration) {
            CelebrationFireworksOverlay(
                onDismiss = { showFireworksCelebration = false },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
