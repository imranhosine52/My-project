@file:OptIn(
    ExperimentalMaterial3Api::class,
    UnstableApi::class
)

package com.example.ui.screens.reels

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.UserStoryDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val IMAGE_STORY_DURATION_MS = 5000L // ছবির জন্য ৫ সেকেন্ড

@Composable
fun StoryViewerDialog(
    stories: List<UserStoryDto>,
    initialStoryIndex: Int = 0,
    onDismiss: () -> Unit
) {
    if (stories.isEmpty()) {
        onDismiss()
        return
    }

    val context = LocalContext.current
    var currentIndex by remember { mutableIntStateOf(initialStoryIndex.coerceIn(0, stories.size - 1)) }
    val currentStory = stories[currentIndex]

    var isPaused by remember { mutableStateOf(false) }
    var currentProgress by remember { mutableFloatStateOf(0f) }

    // 🚀 ভিডিও স্টোরির জন্য ডেডিকেটেড ExoPlayer
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = true
            setAudioAttributes(
                AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).setUsage(C.USAGE_MEDIA).build(),
                true
            )
        }
    }

    fun goToNextStory() {
        if (currentIndex < stories.size - 1) {
            currentIndex++
            currentProgress = 0f
        } else {
            onDismiss()
        }
    }

    fun goToPreviousStory() {
        if (currentIndex > 0) {
            currentIndex--
            currentProgress = 0f
        } else {
            currentProgress = 0f
        }
    }

    // ভিডিও স্টোরি হ্যান্ডলিং ও স্টেট লিসেনার
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    goToNextStory()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    // স্টোরি পরিবর্তন হলে প্লেয়ার রি-লোড
    LaunchedEffect(currentIndex) {
        currentProgress = 0f
        if (currentStory.isVideo && currentStory.mediaUrl.isNotBlank()) {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.setMediaItem(MediaItem.fromUri(currentStory.mediaUrl))
            exoPlayer.prepare()
            exoPlayer.play()
        } else {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }
    }

    // স্ক্রিনে চেপে ধরে রাখলে ভিডিও বা টাইমার পজ করা
    LaunchedEffect(isPaused) {
        if (currentStory.isVideo) {
            if (isPaused) exoPlayer.pause() else exoPlayer.play()
        }
    }

    // ⏱️ টাইমার ও প্রগ্রেস বার লুপ
    LaunchedEffect(currentIndex, isPaused, currentStory.isVideo) {
        if (currentStory.isVideo) {
            while (isActive) {
                if (!isPaused) {
                    val duration = exoPlayer.duration
                    val position = exoPlayer.currentPosition
                    if (duration > 0) {
                        currentProgress = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    }
                }
                delay(100L)
            }
        } else {
            // ছবির ক্ষেত্রে ৫ সেকেন্ড কাউন্টডাউন
            val stepTime = 50L
            val totalSteps = IMAGE_STORY_DURATION_MS / stepTime
            while (isActive && currentProgress < 1f) {
                if (!isPaused) {
                    currentProgress += (1f / totalSteps)
                }
                delay(stepTime)
            }
            if (currentProgress >= 1f && !isPaused) {
                goToNextStory()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                // 🎯 স্ক্রিনের বাম/ডান ট্যাপ এবং লং-প্রেস পজ জেসচার
                .pointerInput(currentIndex) {
                    detectTapGestures(
                        onPress = {
                            isPaused = true
                            tryAwaitRelease()
                            isPaused = false
                        },
                        onTap = { offset ->
                            if (offset.x < size.width * 0.35f) {
                                goToPreviousStory()
                            } else {
                                goToNextStory()
                            }
                        }
                    )
                }
        ) {
            // ১. মূল স্টোরি মিডিয়া (ইমেজ অথবা ভিডিও)
            if (currentStory.isVideo) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            setShutterBackgroundColor(android.graphics.Color.BLACK)
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(currentStory.mediaUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Story Image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // ২. ওপরে শ্যাডো ওভারলে
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                        )
                    )
            )

            // ৩. নিচে শ্যাডো ওভারলে (ক্যাপশনের জন্য)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.90f))
                        )
                    )
            )

            // =========================================================================
            // 🔝 ৪. ইনস্টাগ্রাম স্টাইল প্রগ্রেস বার ও ক্রিয়েটর হেডার
            // =========================================================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // প্রগ্রেস বারগুলো
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    stories.forEachIndexed { index, _ ->
                        val barProgress = when {
                            index < currentIndex -> 1f
                            index == currentIndex -> currentProgress
                            else -> 0f
                        }

                        LinearProgressIndicator(
                            progress = { barProgress },
                            modifier = Modifier
                                .weight(1f)
                                .height(2.5.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.35f)
                        )
                    }
                }

                // ক্রিয়েটর অবতার, নাম ও ক্লোজ বাটন
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, Color(0xFF00E676), CircleShape)
                        ) {
                            AsyncImage(
                                model = currentStory.userAvatar ?: "https://ui-avatars.com/api/?name=${currentStory.displayName}&background=00E676&color=000",
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Column {
                            Text(
                                text = currentStory.displayName,
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "24h Story",
                                color = Color(0xFF00E676),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Story",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // =========================================================================
            // 📝 ৫. নিচের ক্যাপশন টেক্সট
            // =========================================================================
            if (!currentStory.caption.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    Text(
                        text = currentStory.caption,
                        color = Color.White,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 20.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
