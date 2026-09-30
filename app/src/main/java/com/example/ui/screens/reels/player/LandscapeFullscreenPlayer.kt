@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ui.screens.reels.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.StarBorder
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.UserReelDto
import kotlinx.coroutines.delay
import java.util.Locale

private val TikTokRed = Color(0xFFFE2C55)
private val HeartRed = Color(0xFFFF2A4B)
private val StarGold = Color(0xFFFFD700)

/**
 * 🎬 ২ নম্বর ছবির হুবহু ল্যান্ডস্কেপ ফুলস্ক্রিন প্লেয়ার:
 * - টপ বার: ব্যাক, হ্যাশট্যাগ/টাইটেল, ক্রিয়েটর প্রোফাইল ও লাল Follow বাটন
 * - সাইড বার: ব্রাইটনেস, ভলিউম ও স্ক্রিন লক (🔒)
 * - বটম বার: প্লে/পজ, লাইক, কমেন্ট, স্টার, টাইমস্ট্যাম্প, স্লাইডার টাইমলাইন, স্পিড ও কোয়ালিটি
 */
@Composable
fun LandscapeFullscreenPlayer(
    reel: UserReelDto,
    exoPlayer: ExoPlayer,
    isSaved: Boolean,
    saveCount: Int,
    isLoggedIn: Boolean,
    onRequireLogin: () -> Unit,
    onToggleLike: () -> Unit,
    onCommentClick: () -> Unit,
    onSaveClick: () -> Unit,
    onShareClick: () -> Unit,
    onFollowClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onQualityClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // 🎯 ওপেন হলে ল্যান্ডস্কেপ রোটেট হবে, বন্ধ হলে আগের স্বাভাবিক পোট্রেট মোডে ফিরবে
    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    BackHandler {
        onDismiss()
    }

    var showControls by remember { mutableStateOf(true) }
    var isScreenLocked by remember { mutableStateOf(false) }

    var currentPositionMs by remember { mutableLongStateOf(exoPlayer.currentPosition.coerceAtLeast(0L)) }
    var totalDurationMs by remember { mutableLongStateOf(exoPlayer.duration.coerceAtLeast(0L)) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderDragPositionMs by remember { mutableFloatStateOf(0f) }

    // প্লেয়ার টাইম ও ডিউরেশন আপডেট লুপ
    LaunchedEffect(Unit) {
        while (true) {
            if (!isDraggingSlider) {
                currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
            }
            delay(250L)
        }
    }

    // ৪ সেকেন্ড পর কন্ট্রোল স্বয়ংক্রিয়ভাবে হাইড হওয়া
    LaunchedEffect(showControls, isScreenLocked) {
        if (showControls && !isScreenLocked) {
            delay(4500L)
            showControls = false
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
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            showControls = !showControls
                        }
                    )
                }
        ) {
            // =========================================================================
            // 📺 ১. ফুলস্ক্রিন ল্যান্ডস্কেপ ভিডিও প্লেয়ার
            // =========================================================================
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // =========================================================================
            // 🔒 ২. সাইড স্ক্রিন লক আইকন (২ নম্বর ছবি)
            // =========================================================================
            AnimatedVisibility(
                visible = showControls || isScreenLocked,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 20.dp)
            ) {
                IconButton(
                    onClick = { isScreenLocked = !isScreenLocked },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isScreenLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = "Lock Screen",
                        tint = if (isScreenLocked) TikTokRed else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // =========================================================================
            // 🎛️ ৩. সম্পূর্ণ ল্যান্ডস্কেপ কন্ট্রোল ওভারলে (২ নম্বর ছবি)
            // =========================================================================
            AnimatedVisibility(
                visible = showControls && !isScreenLocked,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(180)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // 🔝 ক) টপ বার (ব্যাক, হ্যাশট্যাগ/টাইটেল, ক্রিয়েটর অ্যাভাটার, নাম ও লাল Follow বাটন)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                                )
                            )
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // ব্যাক বাটন
                                IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Exit Fullscreen",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // টাইটেল / হ্যাশট্যাগ
                                Text(
                                    text = reel.title?.ifBlank { "#TrendingReel" } ?: "#TrendingReel",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                // ক্রিয়েটর অ্যাভাটার + নাম + লাল Follow বাটন
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color.Black.copy(alpha = 0.45f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(reel.pageAvatar ?: "https://ui-avatars.com/api/?name=${reel.pageName}&background=222838&color=fff")
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )

                                    Text(
                                        text = reel.pageName.ifBlank { reel.displayHandle },
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (reel.isFollowing) Color(0xFF262C38) else TikTokRed,
                                        modifier = Modifier.clickable {
                                            if (!isLoggedIn) onRequireLogin() else onFollowClick()
                                        }
                                    ) {
                                        Text(
                                            text = if (reel.isFollowing) "Following" else "Follow",
                                            color = Color.White,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            // ডানে শেয়ার বাটন
                            IconButton(onClick = onShareClick, modifier = Modifier.size(34.dp)) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Share",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // 🛠️ খ) সাইড ভলিউম আইকন
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 20.dp, top = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        IconButton(
                            onClick = {},
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Volume",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // 🎬 গ) বটম বার (প্লে/পজ, লাইক/কমেন্ট/স্টার, টাইমস্ট্যাম্প, সিগবার স্লাইডার, স্পিড ও কোয়ালিটি)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.90f))
                                )
                            )
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // ১. টাইমস্ট্যাম্প ও সিগবার স্লাইডার (২ নম্বর ছবি)
                            val currentSeconds = if (isDraggingSlider) (sliderDragPositionMs / 1000).toLong() else (currentPositionMs / 1000)
                            val totalSeconds = (totalDurationMs / 1000)
                            val progressValue = if (totalDurationMs > 0) {
                                if (isDraggingSlider) sliderDragPositionMs / totalDurationMs.toFloat() else currentPositionMs / totalDurationMs.toFloat()
                            } else 0f

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = formatDurationMs(currentSeconds),
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )

                                Slider(
                                    value = progressValue.coerceIn(0f, 1f),
                                    onValueChange = { frac ->
                                        isDraggingSlider = true
                                        sliderDragPositionMs = frac * totalDurationMs
                                    },
                                    onValueChangeFinished = {
                                        exoPlayer.seekTo(sliderDragPositionMs.toLong())
                                        isDraggingSlider = false
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = Color.White,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier.weight(1f)
                                )

                                Text(
                                    text = formatDurationMs(totalSeconds),
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // ২. অ্যাকশন রো (প্লে/পজ + লাইক/কমেন্ট/স্টার + স্পিড ও কোয়ালিটি)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                                ) {
                                    // প্লে / পজ বাটন
                                    IconButton(
                                        onClick = {
                                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (exoPlayer.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "Play/Pause",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    // লাইক বাটন
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.clickable {
                                            if (!isLoggedIn) onRequireLogin() else onToggleLike()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (reel.isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                            contentDescription = "Like",
                                            tint = if (reel.isLiked) HeartRed else Color.White,
                                            modifier = Modifier.size(19.dp)
                                        )
                                        Text(
                                            text = "${reel.likesCount}",
                                            color = Color.White,
                                            fontSize = 12.sp
                                        )
                                    }

                                    // কমেন্ট বাটন
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.clickable { onCommentClick() }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ChatBubbleOutline,
                                            contentDescription = "Comment",
                                            tint = Color.White,
                                            modifier = Modifier.size(19.dp)
                                        )
                                        Text(
                                            text = "${reel.commentsCount}",
                                            color = Color.White,
                                            fontSize = 12.sp
                                        )
                                    }

                                    // স্টার / সেভ বাটন
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.clickable {
                                            if (!isLoggedIn) onRequireLogin() else onSaveClick()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (isSaved) Icons.Default.Star else Icons.Outlined.StarBorder,
                                            contentDescription = "Star Save",
                                            tint = if (isSaved) StarGold else Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "$saveCount",
                                            color = Color.White,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                // ডানে স্পিড, কোয়ালিটি ও এক্সিট বাটন (২ নম্বর ছবি)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    // স্পিড বাটন
                                    Text(
                                        text = "Speed",
                                        color = Color.White,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.clickable { onSpeedClick() }
                                    )

                                    // কোয়ালিটি বাটন
                                    Text(
                                        text = "Quality",
                                        color = Color.White,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.clickable { onQualityClick() }
                                    )

                                    // রোটেশন এক্সিট আইকন
                                    IconButton(
                                        onClick = onDismiss,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FullscreenExit,
                                            contentDescription = "Exit Fullscreen",
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDurationMs(totalSeconds: Long): String {
    if (totalSeconds <= 0) return "00:00"
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
