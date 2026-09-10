@file:OptIn(UnstableApi::class)

package com.example.ui.screens.shorts

import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.ui.screens.SleekSkipIconOnline

@Composable
fun ShortsVideoSurface(
    exoPlayer: ExoPlayer,
    persistentWebView: WebView,
    useWebPlayerFallback: Boolean,
    isBuffering: Boolean,
    currentEpNum: Int,
    isPlaying: Boolean,
    isControlsVisible: Boolean,
    isRewindActive: Boolean,
    isForwardActive: Boolean,
    rewindRotation: Float,
    forwardRotation: Float,
    rewindAlpha: Float,
    forwardAlpha: Float,
    onBackClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onTapSurface: () -> Unit,
    onDoubleTapSkip: (seconds: Int) -> Unit,
    onPlayPauseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        if (useWebPlayerFallback) {
            AndroidView(
                factory = {
                    (persistentWebView.parent as? ViewGroup)?.removeView(persistentWebView)
                    persistentWebView
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                },
                update = { view ->
                    view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onTapSurface() },
                            onDoubleTap = { offset ->
                                if (offset.x < size.width / 2) onDoubleTapSkip(-10) else onDoubleTapSkip(10)
                            }
                        )
                    }
            )
        }

        if (isBuffering && !useWebPlayerFallback) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF00E676), strokeWidth = 3.dp, modifier = Modifier.size(44.dp))
            }
        }

        // 🔝 টপ বার: [< Ep1] ও [ডাউনলোড আইকন ↓]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.clickable { onBackClick() }
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                Text("Ep$currentEpNum", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            IconButton(
                onClick = onDownloadClick,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                Icon(Icons.Outlined.FileDownload, contentDescription = "Download", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        // ⏯️ অন-স্ক্রিন কন্ট্রোলস (-10s, Play/Pause, +10s)
        if (isControlsVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("-10s", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(y = (-30).dp).alpha(rewindAlpha))
                        IconButton(onClick = { onDoubleTapSkip(-10) }, modifier = Modifier.size(46.dp).rotate(rewindRotation)) {
                            SleekSkipIconOnline(isForward = false, color = Color.White)
                        }
                    }

                    IconButton(
                        onClick = onPlayPauseClick,
                        modifier = Modifier.size(60.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    Box(contentAlignment = Alignment.Center) {
                        Text("+10s", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(y = (-30).dp).alpha(forwardAlpha))
                        IconButton(onClick = { onDoubleTapSkip(10) }, modifier = Modifier.size(46.dp).rotate(forwardRotation)) {
                            SleekSkipIconOnline(isForward = true, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
