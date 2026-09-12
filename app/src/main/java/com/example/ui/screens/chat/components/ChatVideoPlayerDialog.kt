@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ui.screens.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * 🎬 ২ নম্বর ছবির ক্লিন ও আধুনিক ফুলস্ক্রিন ভিডিও প্লেয়ার ডায়ালগ
 */
@Composable
fun ChatVideoPlayerDialog(
    videoUrl: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(true) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // ভিডিও সারফেস
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false // কাস্টম কন্ট্রোল ব্যবহার করা হচ্ছে
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // ✕ ক্লোজ বাটন (টপ রাইট)
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(14.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(0.6f))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }

            // 🎯 ২ নম্বর ছবির মতো সেন্ট্রাল কন্ট্রোলস: [-10s] [Play/Pause] [+10s]
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(36.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // -10s বাটন
                IconButton(
                    onClick = {
                        val target = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                        exoPlayer.seekTo(target)
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = "-10s",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                // Play / Pause বাটন
                IconButton(
                    onClick = {
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                            isPlaying = false
                        } else {
                            exoPlayer.play()
                            isPlaying = true
                        }
                    },
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.Black,
                        modifier = Modifier.size(38.dp)
                    )
                }

                // +10s বাটন
                IconButton(
                    onClick = {
                        val target = (exoPlayer.currentPosition + 10000L).coerceAtMost(exoPlayer.duration)
                        exoPlayer.seekTo(target)
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = "+10s",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
    }
}
