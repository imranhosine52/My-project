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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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

// 🎨 ভিডিওর সাইড ও কর্নারের সাথে ১০০% মেলানো মিডনাইট অ্যাম্বিয়েন্ট কালার
private val SplashAmbientDark = Color(0xFF04050A)
private val SplashVideoEdgeTone = Color(0xFF060712)
private val SplashCenterGlow = Color(0xFF080A18)

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

    val splashAlpha = remember { Animatable(1f) }

    fun triggerSmoothFinish() {
        if (!isFadingOut) {
            isFadingOut = true
            coroutineScope.launch {
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
        Box(modifier = Modifier.fillMaxSize().background(SplashAmbientDark))
        return
    }

    // 🚀 ৬০ FPS ল্যাগ-মুক্ত এক্সোপ্লেয়ার
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

    // ভিডিও শেষ হওয়ার ঠিক ২৫০ms আগেই মসৃণ ফেড ট্রিগার
    LaunchedEffect(exoPlayer) {
        while (isActive && !isFadingOut) {
            val duration = exoPlayer.duration
            val position = exoPlayer.currentPosition
            if (duration > 1000L && position >= (duration - 260L)) {
                triggerSmoothFinish()
                break
            }
            delay(75L)
        }
    }

    LaunchedEffect(Unit) {
        delay(5500L)
        triggerSmoothFinish()
    }

    // =========================================================================
    // 🌟 ফুল-স্ক্রিন সিমলেস কন্টেইনার
    // =========================================================================
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = splashAlpha.value
            }
            // 🎯 উপরে ও নিচে সলিড কালো না রেখে ভিডিওর সাইডের রঙের সাথে ম্যাচ করা গ্রেডিয়েন্ট
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SplashAmbientDark,
                        SplashVideoEdgeTone,
                        SplashCenterGlow,
                        SplashVideoEdgeTone,
                        SplashAmbientDark
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // ১. স্বাভাবিক সাইজে থাকা সেন্ট্রাল ভিডিও প্লেয়ার
        AndroidView(
            factory = { ctx ->
                val aspectFrame = AspectRatioFrameLayout(ctx).apply {
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER
                    )
                }

                val textureView = TextureView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    isOpaque = true
                }

                aspectFrame.addView(textureView)
                exoPlayer.setVideoTextureView(textureView)

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

        // =====================================================================
        // 🎯 ২. উপরের সংযোগস্থল মিলিয়ে দেওয়ার সফট ফেদারিং ওভারলে
        // =====================================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.24f)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            SplashAmbientDark,
                            SplashVideoEdgeTone.copy(alpha = 0.95f),
                            SplashVideoEdgeTone.copy(alpha = 0.50f),
                            Color.Transparent
                        )
                    )
                )
        )

        // =====================================================================
        // 🎯 ৩. নিচের সংযোগস্থল মিলিয়ে দেওয়ার সফট ফেদারিং ওভারলে
        // =====================================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.24f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            SplashVideoEdgeTone.copy(alpha = 0.50f),
                            SplashVideoEdgeTone.copy(alpha = 0.95f),
                            SplashAmbientDark
                        )
                    )
                )
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
