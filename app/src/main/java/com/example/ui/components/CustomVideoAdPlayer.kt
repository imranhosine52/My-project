@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

// 🎨 ইউটিউব অ্যাড কালার প্যালেট
private val YouTubeAdYellow = Color(0xFFFFCC00)
private val YouTubeSkipDarkBg = Color(0xCC000000)
private val YouTubeCardBg = Color(0xDD181818)

@Composable
fun CustomVideoAdDialog(
    ad: CustomVideoAdDto,
    onAdFinishedOrSkipped: () -> Unit,
    onNavigateInternalScreen: (target: String) -> Unit,
    onOpenExternalUrl: (url: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isAdPlaying by remember { mutableStateOf(true) }
    var currentAdPositionMs by remember { mutableLongStateOf(0L) }
    var totalAdDurationMs by remember { mutableLongStateOf(0L) }

    // স্কিপ কাউন্টডাউন স্টেট
    val skipThresholdSec = ad.skipAfterSeconds
    val canBeSkipped = ad.isSkippable
    var remainingSecondsToSkip by remember { mutableIntStateOf(skipThresholdSec) }
    val isSkipButtonUnlocked = (remainingSecondsToSkip <= 0 && canBeSkipped)

    // 📊 ১. ভিউ ইভেন্ট ট্র্যাকার (বিজ্ঞাপন শুরু হওয়া মাত্র সার্ভারে ১ বার ভিউ পাঠানো)
    LaunchedEffect(ad.id) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ApiClient.apiService.trackCustomAdEvent(
                    TrackAdEventRequest(adId = ad.id, event = "view")
                )
            } catch (_: Exception) {}
        }
    }

    // 🚀 বিজ্ঞাপনের ভিডিওর জন্য ফাস্ট-লোডিং ExoPlayer
    val adPlayer = remember {
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

    // ভিডিও লোড ও প্লে
    LaunchedEffect(ad.videoUrl) {
        if (ad.videoUrl.isNotBlank()) {
            try {
                adPlayer.stop()
                adPlayer.clearMediaItems()
                val mediaItem = MediaItem.fromUri(Uri.parse(ad.videoUrl))
                adPlayer.setMediaItem(mediaItem)
                adPlayer.prepare()
                adPlayer.play()
            } catch (_: Exception) {
                onAdFinishedOrSkipped()
            }
        } else {
            onAdFinishedOrSkipped()
        }
    }

    // প্লেয়ার স্টেট লিসেনার
    DisposableEffect(adPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    totalAdDurationMs = adPlayer.duration.coerceAtLeast(0L)
                } else if (state == Player.STATE_ENDED) {
                    onAdFinishedOrSkipped()
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isAdPlaying = playing
            }
        }
        adPlayer.addListener(listener)

        onDispose {
            adPlayer.removeListener(listener)
            adPlayer.stop()
            adPlayer.clearMediaItems()
            adPlayer.release()
        }
    }

    // ⏱️ ইউটিউব স্কিপ কাউন্টডাউন টাইমার
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

    // প্রগ্রেস পজিশন ট্র্যাকার
    LaunchedEffect(isAdPlaying) {
        while (isAdPlaying) {
            currentAdPositionMs = adPlayer.currentPosition.coerceAtLeast(0L)
            val d = adPlayer.duration
            if (d > 0) totalAdDurationMs = d
            delay(100L)
        }
    }

    // 🎯 ক্লিক ইভেন্ট ট্র্যাকার ও নেভিগেশন
    fun handleCtaClick() {
        val target = ad.destinationTarget.trim()
        if (target.isBlank()) return

        // 📊 সার্ভারে ক্লিক ইভেন্ট পাঠানো
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ApiClient.apiService.trackCustomAdEvent(
                    TrackAdEventRequest(adId = ad.id, event = "click")
                )
            } catch (_: Exception) {}
        }

        when {
            ad.isInternalApp -> {
                onAdFinishedOrSkipped()
                onNavigateInternalScreen(target)
            }
            ad.isPlayStore -> {
                try {
                    val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse(target)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(playIntent)
                } catch (_: Exception) {
                    val cleanWeb = if (target.startsWith("market://details?id=")) {
                        "https://play.google.com/store/apps/details?id=" + target.removePrefix("market://details?id=")
                    } else target
                    onOpenExternalUrl(cleanWeb)
                }
                onAdFinishedOrSkipped()
            }
            else -> {
                onOpenExternalUrl(target)
                onAdFinishedOrSkipped()
            }
        }
    }

    // বিজ্ঞাপন চলাকালীন ব্যাক প্রেস গার্ড
    BackHandler {
        if (isSkipButtonUnlocked) {
            onAdFinishedOrSkipped()
        } else if (canBeSkipped) {
            Toast.makeText(context, "You can skip ad in ${remainingSecondsToSkip}s", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(
        onDismissRequest = {
            if (isSkipButtonUnlocked) onAdFinishedOrSkipped()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // 📺 ১. বিজ্ঞাপনের ভিডিও সারফেস
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = adPlayer
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

            // =========================================================================
            // 🔝 ২. ইউটিউব টপ বার: [ Ad · 1 of 1 ] ও রিমেইনিং সেকেন্ডস
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // হলুদ "Ad" ব্যাজ
                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = YouTubeAdYellow
                    ) {
                        Text(
                            text = "Ad",
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                        )
                    }

                    // রিমেইনিং সময়
                    val currentSec = (currentAdPositionMs / 1000L).toInt()
                    val totalSec = (totalAdDurationMs / 1000L).toInt().coerceAtLeast(1)
                    val remainingSec = (totalSec - currentSec).coerceAtLeast(0)

                    Text(
                        text = "· 0:${String.format(Locale.US, "%02d", remainingSec)}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // স্পনসর নাম
                Text(
                    text = ad.title.take(24),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // =========================================================================
            // ⏭️ ৩. ইউটিউবের হুবহু স্কিপ অ্যাড বাটন (ডানপাশে নিচে ফ্লোটিং)
            // =========================================================================
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 32.dp)
            ) {
                if (canBeSkipped) {
                    if (isSkipButtonUnlocked) {
                        // 🎯 স্কিপ বাটন আনলক হলে ইউটিউবের হুবহু [ Skip Ad ❯| ] বাটন
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = YouTubeSkipDarkBg,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.8f)),
                            modifier = Modifier.clickable { onAdFinishedOrSkipped() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Skip Ad",
                                    color = Color.White,
                                    fontSize = 13.5.sp,
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
                        // ⏳ স্কিপ হওয়ার আগ পর্যন্ত কাউন্টডাউন
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = YouTubeSkipDarkBg,
                            border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.25f))
                        ) {
                            Text(
                                text = "You can skip ad in $remainingSecondsToSkip",
                                color = Color(0xFFE2E8F0),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                } else {
                    // নন-স্কিপেবল অ্যাড
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = YouTubeSkipDarkBg
                    ) {
                        Text(
                            text = "Video will play after ad",
                            color = Color(0xFFCCCCCC),
                            fontSize = 11.5.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // =========================================================================
            // 🛒 ৪. ইউটিউব স্টাইল বটম-লেফট স্পনসর কার্ড (CTA Card)
            // =========================================================================
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = YouTubeCardBg,
                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.15f)),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, bottom = 26.dp)
                    .widthIn(max = 260.dp)
                    .clickable { handleCtaClick() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = ad.title,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Sponsored · Visit site",
                            color = Color(0xFFAAAAAA),
                            fontSize = 10.5.sp,
                            maxLines = 1
                        )
                    }

                    // কাস্টম রঙের অ্যাকশন বাটন
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = ad.parsedCtaColor
                    ) {
                        Text(
                            text = ad.ctaText,
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // =========================================================================
            // 🟡 ৫. ইউটিউবের হুবহু হলুদ টাইমলাইন প্রগ্রেস বার (একদম নিচে)
            // =========================================================================
            val progressFraction = if (totalAdDurationMs > 0) {
                (currentAdPositionMs.toFloat() / totalAdDurationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f

            LinearProgressIndicator(
                progress = { progressFraction },
                color = YouTubeAdYellow, // 👈 ইউটিউবের হলুদ লাইন
                trackColor = Color.White.copy(alpha = 0.25f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.BottomCenter)
            )
        }
    }
}
