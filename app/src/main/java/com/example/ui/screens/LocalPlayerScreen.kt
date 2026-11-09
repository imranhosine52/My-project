@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.model.LocalVideoItem
import com.example.ui.screens.localplayer.components.LocalPlayerFolderPlaylist
import com.example.ui.screens.localplayer.components.LocalPlayerVideoBox
import com.example.util.LocalMediaScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private fun findActivity(context: Context): Activity? {
    var ctx = context
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun LocalPlayerScreen(
    videoItem: LocalVideoItem,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { findActivity(context) }
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // 🎬 বর্তমান চলমান ভিডিও আইটেম
    var activeVideoItem by remember(videoItem.id) { mutableStateOf(videoItem) }

    // 📂 একই ফোল্ডারের অন্যান্য ভিডিওগুলোর প্লেলিস্ট
    var folderVideos by remember { mutableStateOf<List<LocalVideoItem>>(emptyList()) }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }

    // =========================================================================
    // 🚀 হাই-পারফরম্যান্স ExoPlayer ইঞ্জিন (Audio Becoming Noisy ও বাফারিং অপ্টিমাইজড)
    // =========================================================================
    val exoPlayer = remember {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(30_000, 120_000, 250, 600)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setHandleAudioBecomingNoisy(true) // 🎧 হেডফোন খুলে গেলে স্পিকার ব্লাস্টিং রোধে অটো-পজ
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

    // একই ফোল্ডারের ভিডিওগুলো ব্যাকগ্রাউন্ড থ্রেডে স্ক্যান করা
    LaunchedEffect(activeVideoItem.bucketId) {
        withContext(Dispatchers.IO) {
            val allVideos = LocalMediaScanner.getAllVideos(context)
            val filtered = allVideos.filter { it.bucketId == activeVideoItem.bucketId }
            folderVideos = if (filtered.isNotEmpty()) filtered else allVideos
        }
    }

    // ⏭️ পরবর্তী ভিডিও প্লে করার স্মার্ট লজিক
    fun playNextVideo() {
        val currentIndex = folderVideos.indexOfFirst { it.id == activeVideoItem.id }
        if (currentIndex != -1 && currentIndex < folderVideos.size - 1) {
            activeVideoItem = folderVideos[currentIndex + 1]
        } else if (folderVideos.isNotEmpty()) {
            activeVideoItem = folderVideos.first()
        }
    }

    // ভিডিও পরিবর্তন হলে ExoPlayer-এ লোড করা ও লাস্ট প্লেব্যাক পজিশন উদ্ধার
    LaunchedEffect(activeVideoItem.id) {
        val lastPos = LocalMediaScanner.getLastPlaybackPosition(context, activeVideoItem.id)
        val mediaItem = MediaItem.fromUri(activeVideoItem.contentUri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        if (lastPos > 0) {
            exoPlayer.seekTo(lastPos)
        }
        exoPlayer.play()
    }

    // প্লেয়ার স্টেট ও শেষ হওয়া হ্যান্ডলার
    DisposableEffect(exoPlayer, folderVideos, activeVideoItem) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
                } else if (state == Player.STATE_ENDED) {
                    // ভিডিও শেষ হলে স্বয়ংক্রিয়ভাবে পরের ভিডিও চালু হবে
                    playNextVideo()
                }
            }

            override fun onIsPlayingChanged(playingState: Boolean) {
                isPlaying = playingState
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // টাইম ট্র্যাকার ও পজিশন সেভার
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
            delay(400L)
        }
    }

    // ফুলস্ক্রিন ও সিস্টেম বার কন্ট্রোল
    DisposableEffect(isLandscape) {
        activity?.let { act ->
            val window = act.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)

            if (isLandscape) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { }
    }

    // লাইফসাইকেল ও রিসোর্স রিলিজ
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> {
                    if (exoPlayer.playbackState == Player.STATE_READY) exoPlayer.play()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (currentPositionMs > 1000) {
                LocalMediaScanner.saveLastPlaybackPosition(context, activeVideoItem.id, currentPositionMs)
            }
            exoPlayer.release()

            activity?.let { act ->
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                val window = act.window
                WindowCompat.setDecorFitsSystemWindows(window, true)
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    // ব্যাক বাটন লজিক (ল্যান্ডস্কেপে থাকলে প্রথমে পোর্ট্রেটে ফিরবে)
    BackHandler {
        if (isLandscape) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            onBackClick()
        }
    }

    // =========================================================================
    // 📱 মূল ইউআই লেআউট (পোর্ট্রেট বনাম ল্যান্ডস্কেপ)
    // =========================================================================
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isLandscape) {
            // =============================================================
            // 🔄 ১. ল্যান্ডস্কেপ মোড (সম্পূর্ণ ফুলস্ক্রিন ওটিটি প্লেয়ার)
            // =============================================================
            LocalPlayerVideoBox(
                exoPlayer = exoPlayer,
                currentVideo = activeVideoItem,
                folderVideos = folderVideos,
                isLandscape = true,
                isPlaying = isPlaying,
                currentPositionMs = currentPositionMs,
                totalDurationMs = totalDurationMs,
                onBackClick = {
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                },
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
                onSelectVideo = { selected ->
                    activeVideoItem = selected
                },
                onNextVideoClick = { playNextVideo() },
                onToggleFullscreen = {
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // =============================================================
            // 📱 ২. পোর্ট্রেট মোড (উপরে প্লেয়ার + নিচে ফোল্ডার ভিডিও তালিকা)
            // =============================================================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // ২.১) উপরের ১৬:৯ ভিডিও প্লেয়ার ফ্রেম
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                ) {
                    LocalPlayerVideoBox(
                        exoPlayer = exoPlayer,
                        currentVideo = activeVideoItem,
                        folderVideos = folderVideos,
                        isLandscape = false,
                        isPlaying = isPlaying,
                        currentPositionMs = currentPositionMs,
                        totalDurationMs = totalDurationMs,
                        onBackClick = onBackClick,
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
                        onSelectVideo = { selected ->
                            activeVideoItem = selected
                        },
                        onNextVideoClick = { playNextVideo() },
                        onToggleFullscreen = {
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // ২.২) নিচের স্ক্রোলেবল ফোল্ডার ভিডিও প্লেলিস্ট
                LocalPlayerFolderPlaylist(
                    folderName = activeVideoItem.folderName,
                    videos = folderVideos,
                    currentVideoId = activeVideoItem.id,
                    onSelectVideo = { selected ->
                        activeVideoItem = selected
                    },
                    onNextVideoClick = { playNextVideo() },
                    isLandscapeDrawer = false,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
        }
    }
}
