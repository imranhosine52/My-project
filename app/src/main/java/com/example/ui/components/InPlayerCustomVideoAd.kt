@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.model.CustomVideoAdDto
import com.example.data.model.TrackAdEventRequest
import com.example.data.remote.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private val YouTubeAdYellow = Color(0xFFFFCC00)
private val YouTubeSkipButtonBg = Color(0xDD141414)

private fun formatAdTime(millis: Long): String {
    if (millis <= 0L) return "00:00"
    val totalSeconds = millis / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

/**
 * ⏱️ ১. কাউন্টডাউন নোটিশ (ছবি ২ অনুযায়ী: হলুদ লাইন ছাড়া, ঘড়ি ছাড়া ও নিচে অবস্থিত)
 */
@Composable
fun InPlayerAdCountdownNotice(
    secondsLeft: Int,
    isDirectLink: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = 0.88f),
        shadowElevation = 4.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (isDirectLink) "Sponsor ad in ${secondsLeft}s..." else "Ad in ${secondsLeft}s...",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(YouTubeAdYellow),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$secondsLeft",
                    color = Color.Black,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

/**
 * 🎬 ২. ইন-প্লেয়ার কাস্টম ভিডিও অ্যাড ভিউ (ছবি ১ অনুযায়ী: VIP বাটন রিমুভড ও Skip বাটনের বর্ডার রিমুভড)
 */
@Composable
fun InPlayerCustomVideoAdView(
    ad: CustomVideoAdDto,
    onAdFinishedOrSkipped: () -> Unit,
    onOpenAdTarget: (target: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var isAdPlaying by remember { mutableStateOf(true) }
    var currentAdPositionMs by remember { mutableLongStateOf(0L) }
    var totalAdDurationMs by remember { mutableLongStateOf(0L) }

    val skipThresholdSec = ad.skipAfterSeconds
    val canBeSkipped = ad.isSkippable
    var remainingSecondsToSkip by remember { mutableIntStateOf(skipThresholdSec) }
    val isSkipButtonUnlocked = (remainingSecondsToSkip <= 0 && canBeSkipped)

    // ভিউ ট্র্যাকিং
    LaunchedEffect(ad.id) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ApiClient.apiService.trackCustomAdEvent(
                    TrackAdEventRequest(adId = ad.id, event = "view")
                )
            } catch (_: Exception) {}
        }
    }

    // অ্যাড প্লেয়ার ইনিশিয়ালাইজেশন
    val adExoPlayer = remember {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(1000, 15000, 500, 1000)
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build().apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_OFF
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true
                )
            }
    }

    LaunchedEffect(ad.videoUrl) {
        if (ad.videoUrl.isNotBlank()) {
            try {
                adExoPlayer.stop()
                adExoPlayer.clearMediaItems()
                val mediaItem = MediaItem.fromUri(Uri.parse(ad.videoUrl))
                adExoPlayer.setMediaItem(mediaItem)
                adExoPlayer.prepare()
                adExoPlayer.play()
            } catch (_: Exception) {
                onAdFinishedOrSkipped()
            }
        } else {
            onAdFinishedOrSkipped()
        }
    }

    DisposableEffect(adExoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    totalAdDurationMs = adExoPlayer.duration.coerceAtLeast(0L)
                } else if (state == Player.STATE_ENDED) {
                    onAdFinishedOrSkipped()
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isAdPlaying = playing
            }
        }
        adExoPlayer.addListener(listener)

        onDispose {
            adExoPlayer.removeListener(listener)
            adExoPlayer.stop()
            adExoPlayer.clearMediaItems()
            adExoPlayer.release()
        }
    }

    // স্কিপ কাউন্টডাউন টাইমার
    LaunchedEffect(isAdPlaying, remainingSecondsToSkip) {
        if (canBeSkipped && remainingSecondsToSkip > 0) {
            while (remainingSecondsToSkip > 0) {
                delay(1000L)
                if (isAdPlaying) {
                    remainingSecondsToSkip--
                }
            }
        }
    }

    // টাইমলাইন ট্র্যাক
    LaunchedEffect(isAdPlaying) {
        while (isAdPlaying) {
            currentAdPositionMs = adExoPlayer.currentPosition.coerceAtLeast(0L)
            val d = adExoPlayer.duration
            if (d > 0) totalAdDurationMs = d
            delay(100L)
        }
    }

    fun handleCtaClick() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ApiClient.apiService.trackCustomAdEvent(
                    TrackAdEventRequest(adId = ad.id, event = "click")
                )
            } catch (_: Exception) {}
        }
        onOpenAdTarget(ad.destinationTarget.ifBlank { "https://playdramaflix.com" })
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (adExoPlayer.isPlaying) adExoPlayer.pause() else adExoPlayer.play()
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = adExoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // ১. উপরে ডানে: [ Visit advertiser ↗ ] (বর্ডার ছাড়া ক্লিন ডিজাইন)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.Black.copy(alpha = 0.65f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 10.dp, end = 10.dp)
                .clickable { handleCtaClick() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Visit advertiser",
                    color = Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        // ২. নিচে বাঁয়ে: [ Ad ] হলুদ ব্যাজ ও টাইমলাইন
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 10.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(3.dp),
                color = YouTubeAdYellow
            ) {
                Text(
                    text = "Ad",
                    color = Color.Black,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }

            val currentFormatted = formatAdTime(currentAdPositionMs)
            val totalFormatted = formatAdTime(totalAdDurationMs)
            Text(
                text = "$currentFormatted / $totalFormatted",
                color = Color.White,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // ৩. নিচে ডানে: [ Skip Ad ❯| ] বাটন (বর্ডার লাইন সম্পূর্ণ রিমুভ করা হয়েছে)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 10.dp, bottom = 10.dp)
        ) {
            if (canBeSkipped) {
                if (isSkipButtonUnlocked) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = YouTubeSkipButtonBg,
                        modifier = Modifier.clickable { onAdFinishedOrSkipped() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = "Skip Ad",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Skip",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = YouTubeSkipButtonBg
                    ) {
                        Text(
                            text = "Skip in ${remainingSecondsToSkip}s",
                            color = Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // ৪. নিচের হলুদ প্রোগ্রেস বার (YouTube Style)
        val progressFraction = if (totalAdDurationMs > 0) {
            (currentAdPositionMs.toFloat() / totalAdDurationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f

        LinearProgressIndicator(
            progress = { progressFraction },
            color = YouTubeAdYellow,
            trackColor = Color.White.copy(alpha = 0.2f),
            modifier = Modifier
                .fillMaxWidth()
                .height(2.5.dp)
                .align(Alignment.BottomCenter)
        )
    }
}

/**
 * 🔒 ৩. ডাইরেক্ট লিংক ক্লিক অ্যাড ওভারলে (নন-স্কিপেবল)
 */
@Composable
fun InPlayerDirectLinkRequirementOverlay(
    remainingClicks: Int,
    onVisitSponsor: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF141720),
            shadowElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AdsClick,
                        contentDescription = null,
                        tint = YouTubeAdYellow,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Sponsored Visit Required",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Playback paused. Please visit the sponsor ad to resume video playback. ($remainingClicks visit remaining - Non-skippable)",
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Button(
                    onClick = onVisitSponsor,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = YouTubeAdYellow),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(38.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Visit Sponsor Ad ($remainingClicks left)",
                            color = Color.Black,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
