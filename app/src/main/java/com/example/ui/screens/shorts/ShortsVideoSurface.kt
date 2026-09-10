@file:OptIn(UnstableApi::class)

package com.example.ui.screens.shorts

import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@Composable
fun ShortsVideoSurface(
    exoPlayer: ExoPlayer,
    persistentWebView: WebView,
    useWebPlayerFallback: Boolean,
    isBuffering: Boolean,
    currentEpNum: Int = 1,
    isPlaying: Boolean = true,
    isControlsVisible: Boolean = true,
    isImmersiveFullscreen: Boolean = false,
    onBackClick: () -> Unit = {},
    onDownloadClick: () -> Unit = {},
    onTapSurface: () -> Unit = {},
    onDoubleTapFullscreen: () -> Unit = {},
    onPlayPauseClick: () -> Unit = {},
    onSeekSkip: (seconds: Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            // 🎯 সিঙ্গেল ট্যাপে কন্ট্রোলস শো/হাইড এবং ডাবল ট্যাপে ফুলস্ক্রিন টগল
            .pointerInput(isImmersiveFullscreen) {
                detectTapGestures(
                    onTap = { onTapSurface() },
                    onDoubleTap = { onDoubleTapFullscreen() }
                )
            }
    ) {
        // ১. Web Embed ফলব্যাক প্লেয়ার
        if (useWebPlayerFallback) {
            AndroidView(
                factory = {
                    (persistentWebView.parent as? ViewGroup)?.removeView(persistentWebView)
                    persistentWebView
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // ২. Native ExoPlayer ভিডিও প্লেয়ার
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        // ফুল স্ক্রিন টিকটক রেশিও ফিলের জন্য ZOOM মোড
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                },
                update = { view ->
                    view.player = exoPlayer
                    view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // ৩. ভিডিও বাফারিং লোডিং স্পিনার (শুধুমাত্র বাফারিং হলে সেন্টারে ভেসে উঠবে)
        if (isBuffering && !useWebPlayerFallback) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color(0xFF00E676),
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(44.dp)
                )
            }
        }
    }
}
