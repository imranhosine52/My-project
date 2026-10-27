@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ui.screens

import android.app.Activity
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
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import kotlinx.coroutines.delay

@Composable
fun VideoSplashScreen(onVideoFinished: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity

    // 🎯 ফুলস্ক্রিন সিনেমাটিক করার জন্য স্ট্যাটাস বার ও নেভিগেশন বার হাইড করা
    DisposableEffect(Unit) {
        activity?.window?.let { window ->
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            // ভিডিও শেষ হলে আবার স্ট্যাটাস বার ফিরিয়ে আনা
            activity?.window?.let { window ->
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // 🎬 ভিডিও প্লেয়ার সেটআপ
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            // আপনার res/raw/splash_video.mp4 ফাইলটি লিংক করা হলো
            val uri = Uri.parse("android.resource://${context.packageName}/${R.raw.splash_video}")
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }

    // ভিডিও শেষ হলে মূল অ্যাপে যাওয়ার লজিক
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    onVideoFinished()
                }
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // ⏳ সেফটি টাইমার (যদি কোনো কারণে ভিডিও প্লে হতে সমস্যা হয়, তবে ৬ সেকেন্ড পর অ্যাপ ওপেন হয়ে যাবে)
    LaunchedEffect(Unit) {
        delay(6000)
        onVideoFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black) // ভিডিওর পেছনে কালো ব্যাকগ্রাউন্ড
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // প্লে/পজ বাটন হাইড করা হলো
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM // স্ক্রিন জুড়ে ফিট হবে
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
