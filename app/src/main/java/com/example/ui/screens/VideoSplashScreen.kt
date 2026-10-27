@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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
import com.example.R
import kotlinx.coroutines.delay

// 🛡️ নিরাপদ Activity ফাইন্ডার
private fun findActivity(context: Context): Activity? {
    var current = context
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@Composable
fun VideoSplashScreen(onVideoFinished: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = remember(context) { findActivity(context) }

    // 🎯 ডাবল-কল প্রোটেকশন গার্ড
    var hasFinished by remember { mutableStateOf(false) }

    val safeFinish = remember(onVideoFinished) {
        {
            if (!hasFinished) {
                hasFinished = true
                onVideoFinished()
            }
        }
    }

    // 🎯 ফুলস্ক্রিন সিনেমাটিক করার জন্য স্ট্যাটাস বার ও নেভিগেশন বার হাইড করা
    DisposableEffect(activity) {
        activity?.window?.let { window ->
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            // স্প্ল্যাশ স্ক্রিন পার হলে সিস্টেম বার ফিরিয়ে আনা
            activity?.window?.let { window ->
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // 🎬 ভিডিও প্লেয়ার সেটআপ
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val uri = Uri.parse("android.resource://${context.packageName}/${R.raw.splash_video}")
            setMediaItem(MediaItem.fromUri(uri))
            
            // সিনেমাটিক অডিও কনফিগ
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                false // ব্যাকগ্রাউন্ডে অন্যান্য অডিওকে ডিস্টার্ব না করার সুবিধা
            )
            
            prepare()
            playWhenReady = true
        }
    }

    // 🛡️ অ্যাপ মিনিমাইজ হলে ভিডিও পজ করার লাইফসাইকেল অবজারভার
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                exoPlayer.pause()
            } else if (event == Lifecycle.Event.ON_RESUME) {
                if (!hasFinished) exoPlayer.play()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 🎧 প্লেব্যাক ফিনিশ ও এরর লিসেনার
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    safeFinish()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // কোনো এরর হলে কালো স্ক্রিনে আটকে না থেকে সাথে সাথে অ্যাপ ওপেন হবে
                safeFinish()
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // ⏳ সেফটি ফলব্যাক টাইমার (ভিডিও অতিরিক্ত বড় হলেও সর্বোচ্চ ৫.৫ সেকেন্ড পর অ্যাপ চালু হবে)
    LaunchedEffect(Unit) {
        delay(5500)
        safeFinish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM // স্ক্রিনের মাপে ফুল ফিট
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
