@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.view.Gravity
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
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

    // 🌟 মসৃণ ট্রানজিশনের জন্য আলফা অ্যানিমেশন
    val splashAlpha = remember { Animatable(1f) }

    fun triggerSmoothFinish() {
        if (!isFadingOut) {
            isFadingOut = true
            coroutineScope.launch {
                // ভিডিও শেষ হওয়ার ২৫০-৩০০ms আগেই মসৃণ ফেড শুরু হবে
                splashAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = 350,
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

    // 🚀 এক্সোপ্লেয়ার (ল্যাগ-মুক্ত স্মুথ প্লেব্যাকের জন্য প্রস্তুত)
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

    // 🎯 ভিডিও শেষ হওয়ার ঠিক আগে ফেড শুরু করার নিখুঁত চেকার
    LaunchedEffect(exoPlayer) {
        while (isActive && !isFadingOut) {
            val duration = exoPlayer.duration
            val position = exoPlayer.currentPosition
            if (duration > 1000L && position >= (duration - 260L)) {
                triggerSmoothFinish()
                break
            }
            delay(80L) // অপ্টিমাইজড টাইমার যা CPU-কে ফ্রি রাখে
        }
    }

    // ব্যাকআপ ওয়াচডগ (সর্বোচ্চ ৫.৫ সেকেন্ড)
    LaunchedEffect(Unit) {
        delay(5500L)
        triggerSmoothFinish()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = splashAlpha.value
            }
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                // 🎯 ১. AspectRatioFrameLayout: ভিডিওর আসল অনুপাত হুবহু বজায় রাখবে (লোগো লম্বা হবে না)
                val aspectFrame = AspectRatioFrameLayout(ctx).apply {
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER
                    )
                }

                // 🎯 ২. TextureView: ৬০ FPS ল্যাগ-মুক্ত ভিডিও রেন্ডারিং
                val textureView = TextureView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    isOpaque = true // GPU আলফা ওভারহেড বন্ধ করে ল্যাগ দূর করা হয়েছে
                }

                aspectFrame.addView(textureView)
                exoPlayer.setVideoTextureView(textureView)

                // ভিডিওর সঠিক রেশিও অনুযায়ী ফ্রেম অ্যাডজাস্ট করা
                exoPlayer.addListener(object : Player.Listener {
                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        if (videoSize.width > 0 && videoSize.height > 0) {
                            aspectFrame.setAspectRatio(videoSize.width.toFloat() / videoSize.height.toFloat())
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            triggerSmoothFinish()
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        triggerSmoothFinish()
                    }
                })

                aspectFrame
            },
            modifier = Modifier.fillMaxSize()
        )
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.release()
        }
    }
}
