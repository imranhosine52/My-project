@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ui.screens

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@Composable
fun VideoSplashScreen(
    onVideoFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasFinished by remember { mutableStateOf(false) }

    val safeFinish = remember(onVideoFinished) {
        {
            if (!hasFinished) {
                hasFinished = true
                onVideoFinished()
            }
        }
    }

    // 🎯 ডায়নামিক রিসোর্স চেকার (ভিডিও ফাইল পাওয়া না গেলে সাথে সাথে হোম স্ক্রিনে চলে যাবে)
    val rawResId = remember(context) {
        context.resources.getIdentifier("splash_video", "raw", context.packageName)
    }

    if (rawResId == 0) {
        LaunchedEffect(Unit) {
            safeFinish()
        }
        Box(modifier = Modifier.fillMaxSize().background(Color.Black))
        return
    }

    // 🚀 এক্সোপ্লেয়ার ইনস্ট্যান্স তৈরি (জিরো ল্যাগ ও স্মুথ অডিও সিঙ্ক সহ)
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val uri = Uri.parse("android.resource://${context.packageName}/$rawResId")
            setMediaItem(MediaItem.fromUri(uri))
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                false
            )
            repeatMode = Player.REPEAT_MODE_OFF
            prepare()
            playWhenReady = true
        }
    }

    // লাইফসাইকেল হ্যান্ডলার (অ্যাপ মিনিমাইজ হলে পজ ও ব্যাক করলে প্লে)
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> {
                    if (!hasFinished) exoPlayer.play()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // প্লেব্যাক লিসেনার (ভিডিও শেষ হওয়ার সাথে সাথে স্মুথ ট্রানজিশন ট্রিগার)
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    exoPlayer.pause()
                    safeFinish()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                safeFinish()
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.release()
        }
    }

    // সর্বোচ্চ সময়সীমা নিরাপত্তা ওয়াচডগ (যাতে কোনো ডিভাইসে ভিডিও আটকে না থাকে)
    LaunchedEffect(Unit) {
        delay(6000L)
        safeFinish()
    }

    // 🎯 পিওর ব্ল্যাক ব্যাকগ্রাউন্ড (ভিডিও শুরুর আগে কোনো আইকন ফ্লিকার করবে না)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    keepScreenOn = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
