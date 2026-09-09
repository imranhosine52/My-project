@file:OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)

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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.ads.StartIoAdManager
import com.example.ads.UnifiedAdManager
import com.example.data.model.ContentItemDto
import com.example.data.model.EpisodeDto
import com.example.ui.components.AuthBottomSheetDialog
import com.example.ui.components.CompactUnlockEpisodeDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.DramaFlixViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun findActivity(context: Context): Activity? {
    var current = context
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
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

    val playerState by viewModel.playerUiState.collectAsStateWithLifecycle()
    val authState by viewModel.authUiState.collectAsStateWithLifecycle()
    val homeState by viewModel.homeUiState.collectAsStateWithLifecycle()

    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }
    var isBuffering by remember { mutableStateOf(true) }

    // বটম শিট স্টেটসমূহ (ছবি ২ ও ছবি ৩)
    var showEpisodePickerSheet by remember { mutableStateOf(false) }
    var showDetailsSheet by remember { mutableStateOf(false) }
    var showAuthSheet by remember { mutableStateOf(false) }

    val adConfig by UnifiedAdManager.adConfigState.collectAsStateWithLifecycle()
    val shouldLockEpisodes = !playerState.isVip && adConfig.adsEnabled

    val content = playerState.content
        ?: homeState.popularDramas.find { it.slug == slug }
        ?: ContentItemDto(title = "Car God Returns", slug = slug, type = "shorts")

    val totalEpCount = playerState.episodes.size.coerceAtLeast(content.totalEpisodes.coerceAtLeast(1))
    val currentEp = playerState.currentEpisode ?: playerState.episodes.firstOrNull()
    val currentEpNum = currentEp?.episodeNumber ?: 1

    // ⚡ ফাস্ট-স্টার্ট 9:16 ExoPlayer
    val exoPlayer = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(12000)
            .setReadTimeoutMs(12000)
            .setUserAgent("PlayDramaFlix Shorts Player")

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(1500, 10000, 600, 1200)
            .build()

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
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

    // পোর্ট্রেট মোড লক এবং ফুলস্ক্রিন ইনসেটস
    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            exoPlayer.release()
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
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
            delay(300L)
        }
    }

    // 🎬 ভিডিও স্ট্রিম লোডার
    LaunchedEffect(currentEp?.episodeNumber, currentEp?.episodeId, slug) {
        if (currentEp != null) {
            if (shouldLockEpisodes && currentEp.isLocked) {
                exoPlayer.pause()
                viewModel.showEpisodeUnlockModal(currentEp)
                return@LaunchedEffect
            }

            val videoStreamUrl = currentEp.resolveR2StreamUrl(slug)
            try {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.parse(videoStreamUrl))
                    .setMimeType(if (videoStreamUrl.contains(".m3u8")) MimeTypes.APPLICATION_M3U8 else MimeTypes.APPLICATION_MP4)
                    .build()
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
                exoPlayer.play()
            } catch (_: Exception) {}
        }
    }

    BackHandler { onBackClick() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // =========================================================================
        // 📱 ১. ফুল-স্ক্রিন 9:16 ভার্টিক্যাল ভিডিও সারফেস
        // =========================================================================
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM // 9:16 ফুলস্ক্রিন ফিল
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        },
                        onDoubleTap = {
                            if (!authState.isLoggedIn) showAuthSheet = true else viewModel.toggleLikeDrama()
                        }
                    )
                }
        )

        // বাফারিং লোডার
        if (isBuffering) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF00E676), strokeWidth = 3.dp, modifier = Modifier.size(44.dp))
            }
        }

        // সেন্টার প্লে/পজ ইন্ডিকেটর
        if (!isPlaying && !isBuffering) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        // =========================================================================
        // 🔝 ২. ১ম ছবির মতো টপ বার (Back Arrow & Series Title)
        // =========================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconButton(onClick = onBackClick, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                text = content.title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        // =========================================================================
        // 📱 ৩. ১ম ছবির ডানপাশের ফ্লোটিং অ্যাকশন বার (Download, Bookmark, Share)
        // =========================================================================
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // ১. ডাউনলোড আইকন 📥 (ছবি ৩ খোলে)
            IconButton(
                onClick = { showDetailsSheet = true },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                Icon(Icons.Outlined.FileDownload, contentDescription = "Download", tint = Color.White, modifier = Modifier.size(24.dp))
            }

            // ২. বুকমার্ক/লাইক আইকন 🔖 (কাউন্টার সহ)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = {
                        if (!authState.isLoggedIn) showAuthSheet = true else viewModel.toggleWatchlist()
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                ) {
                    Icon(
                        imageVector = if (playerState.isInWatchlist) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "Bookmark",
                        tint = if (playerState.isInWatchlist) Color(0xFF00E676) else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text("1.0 k", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            // ৩. শেয়ার আইকন 📤
            IconButton(
                onClick = {
                    val shareUrl = "https://playdramaflix.com/watch/${content.slug}"
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Watch ${content.title} on PlayDramaFlix: $shareUrl")
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Drama"))
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }

        // =========================================================================
        // 📑 ৪. ১ম ছবির মতো বটম বার ([ EP01 / EP46 ⌃ ] ও থিন স্ক্রাবার)
        // =========================================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ১ম ছবির হুবহু সবুজ আইকনযুক্ত [ EP01 / EP46  ^ ] পিল বাটন
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF232832),
                border = BorderStroke(0.6.dp, Color(0xFF3B4354)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clickable { showEpisodePickerSheet = true } // 👈 ২য় ছবির পর্বের গ্রিড খোলে
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // সবুজ লেয়ার আইকন
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "EP${String.format(Locale.US, "%02d", currentEpNum)} / EP${String.format(Locale.US, "%02d", totalEpCount)}",
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Open Episodes",
                        tint = Color(0xFF9AA4B5),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // আল্ট্রা-থিন প্রোগ্রেস লাইন (১ম ছবির মতো)
            val progress = if (totalDurationMs > 0) (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f) else 0f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color.White.copy(alpha = 0.25f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = progress)
                        .fillMaxHeight()
                        .background(Color.White)
                )
            }
        }

        // =========================================================================
        // 🔲 ২য় ছবির পর্বের গ্রিড শিট (ShortsEpisodePickerSheet)
        // =========================================================================
        if (showEpisodePickerSheet) {
            ShortsEpisodePickerSheet(
                title = content.title,
                episodes = playerState.episodes,
                currentEpisodeNumber = currentEpNum,
                isVip = playerState.isVip,
                shouldLockEpisodes = shouldLockEpisodes,
                onDismiss = { showEpisodePickerSheet = false },
                onSelectEpisode = { selectedEp ->
                    showEpisodePickerSheet = false
                    if (shouldLockEpisodes && selectedEp.isLocked) {
                        viewModel.showEpisodeUnlockModal(selectedEp)
                    } else {
                        viewModel.selectEpisode(selectedEp)
                    }
                }
            )
        }

        // =========================================================================
        // 📋 ৩য় ছবির ডিটেইলস ও ব্যাচ ডাউনলোড শিট (ShortsDetailsDownloadSheet)
        // =========================================================================
        if (showDetailsSheet) {
            ShortsDetailsDownloadSheet(
                content = content,
                episodes = playerState.episodes,
                isBookmarked = playerState.isInWatchlist,
                onDismiss = { showDetailsSheet = false },
                onContinuePlay = { showDetailsSheet = false },
                onToggleBookmark = { viewModel.toggleWatchlist() },
                onStartBatchDownload = { selectedEpisodes ->
                    showDetailsSheet = false
                    selectedEpisodes.forEach { ep ->
                        startOneClickR2Download(context, ep.resolveR2StreamUrl(slug), content.title, ep.episodeNumber)
                    }
                    Toast.makeText(context, "📥 Downloading ${selectedEpisodes.size} episodes in background...", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // 🔐 ভিআইপি আনলক ডায়ালগ
        if (shouldLockEpisodes && playerState.showEpisodeUnlockModal && playerState.lockedEpisodeTarget != null) {
            val lockedTarget = playerState.lockedEpisodeTarget!!
            CompactUnlockEpisodeDialog(
                episodeNumber = lockedTarget.episodeNumber,
                onDismiss = { viewModel.dismissEpisodeUnlockModal() },
                onWatchAd = {
                    val act = activity ?: findActivity(context)
                    if (act != null) {
                        StartIoAdManager.showRewardedAd(act) { isRewarded ->
                            if (isRewarded) {
                                viewModel.unlockEpisodeWithRewardAd(context, slug, lockedTarget)
                                Toast.makeText(context, "Episode ${lockedTarget.episodeNumber} unlocked for 2 hours!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onUpgradeVip = {
                    viewModel.dismissEpisodeUnlockModal()
                    onNavigateToVip()
                }
            )
        }

        if (showAuthSheet) {
            AuthBottomSheetDialog(viewModel = viewModel, onDismiss = { showAuthSheet = false })
        }
    }
}
