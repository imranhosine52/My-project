@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.view.TextureView
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun VideoSplashScreen(
    onVideoFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var isFadingOut by remember { mutableStateOf(false) }
    var hasFinished by remember { mutableStateOf(false) }

    // 🌟 মাখনের মতো মিলিয়ে যাওয়ার জন্য আলফা অ্যানিমেশন
    val splashAlpha = remember { Animatable(1f) }

    // 🎯 ভিডিও শেষ হওয়ার আগেই মসৃণভাবে মিলিয়ে যাওয়ার ফাংশন
    fun triggerSmoothFinish() {
        if (!isFadingOut) {
            isFadingOut = true
            coroutineScope.launch {
                // ভিডিও চলা অবস্থাতেই ২৫০-৩০০ms এর মধ্যে মিলিয়ে যাবে (কোনো ফ্রেম ড্রপ ছাড়া)
                splashAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = 380,
                        easing = LinearOutSlowInEasing
                    )
                )
                if (!hasFinished) {
                    hasFinished = true
                    onVideoFinished()
                }
            }
        }
    }

    val rawResId = remember(context) {
        context.resources.getIdentifier("splash_video", "raw", context.packageName)
    }

    if (rawResId == 0) {
        LaunchedEffect(Unit) {
            onVideoFinished()
        }
        Box(modifier = Modifier.fillMaxSize().background(Color.Black))
        return
    }

    // 🚀 এক্সোপ্লেয়ার
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

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> {
                    if (!hasFinished && !isFadingOut) exoPlayer.play()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // ⏱️ ভিডিও শেষ হওয়ার ২৫০ মিলি-সেকেন্ড আগেই ফেড-আউট শুরু করার ট্র্যাকার
    LaunchedEffect(exoPlayer) {
        while (isActive && !isFadingOut) {
            val duration = exoPlayer.duration
            val position = exoPlayer.currentPosition
            // ভিডিও শেষ ফ্রেম স্পর্শ করার আগেই মিলিয়ে যাওয়া শুরু হবে, তাই স্ক্রিন কখনো আটকে থাকবে না
            if (duration > 1000L && position >= (duration - 280L)) {
                triggerSmoothFinish()
                break
            }
            delay(40L)
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    triggerSmoothFinish()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                triggerSmoothFinish()
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

    // ব্যাকআপ ওয়াচডগ টাইমার (সর্বোচ্চ ৫.৫ সেকেন্ড)
    LaunchedEffect(Unit) {
        delay(5500L)
        triggerSmoothFinish()
    }

    // 🎯 TextureView ব্যবহার করে হার্ডওয়্যার-অ্যাক্সিলারেটেড আলফা ব্লেন্ডিং
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = splashAlpha.value
            }
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    isOpaque = false // ট্রানজিশনের সময় স্বচ্ছতা নিশ্চিত করতে
                    exoPlayer.setVideoTextureView(this)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
