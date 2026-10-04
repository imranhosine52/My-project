@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import coil.compose.AsyncImage
import com.example.data.model.CustomVideoAdDto
import com.example.data.model.TrackAdEventRequest
import com.example.data.remote.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

// 🎨 ইউটিউবের হুবহু কালার প্যালেট
private val YouTubeDarkBg = Color(0xFF0F0F0F)
private val YouTubeCardDark = Color(0xFF1E1E1E)
private val YouTubeAdYellow = Color(0xFFFFCC00)
private val YouTubeSkipButtonBg = Color(0xCC111111)
private val YouTubeSecondaryBtnBg = Color(0xFF272727)
private val YouTubeInstallPurple = Color(0xFFD0BCFF) // স্ক্রিনশটের পার্পল/কাস্টম ইন্সটল কালার

@SuppressLint("SetJavaScriptEnabled")
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

    // স্কিপ ও কাউন্টডাউন স্টেট
    val skipThresholdSec = ad.skipAfterSeconds
    val canBeSkipped = ad.isSkippable
    var remainingSecondsToSkip by remember { mutableIntStateOf(skipThresholdSec) }
    val isSkipButtonUnlocked = (remainingSecondsToSkip <= 0 && canBeSkipped)

    var isLiked by remember { mutableStateOf(false) }

    // 📊 ১. ভিউ ইভেন্ট ট্র্যাকার
    LaunchedEffect(ad.id) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ApiClient.apiService.trackCustomAdEvent(
                    TrackAdEventRequest(adId = ad.id, event = "view")
                )
            } catch (_: Exception) {}
        }
    }

    // 🚀 বিজ্ঞাপনের ExoPlayer
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

    // কাউন্টডাউন টাইমার
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

    // প্রগ্রেস বার ট্র্যাকার
    LaunchedEffect(isAdPlaying) {
        while (isAdPlaying) {
            currentAdPositionMs = adPlayer.currentPosition.coerceAtLeast(0L)
            val d = adPlayer.duration
            if (d > 0) totalAdDurationMs = d
            delay(100L)
        }
    }

    // 🎯 ক্লিক ট্র্যাকার এবং রিডাইরেকশন হ্যান্ডলার
    fun handleDestinationClick() {
        val target = ad.destinationTarget.trim()
        if (target.isBlank()) return

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

    // ব্যাক প্রেস লক
    BackHandler {
        if (isSkipButtonUnlocked) {
            onAdFinishedOrSkipped()
        } else if (canBeSkipped) {
            Toast.makeText(context, "Skip in ${remainingSecondsToSkip}s", Toast.LENGTH_SHORT).show()
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
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(YouTubeDarkBg)
        ) {
            // =========================================================================
            // 📺 ১. উপরের ভিডিও প্লেয়ার ফ্রেম (স্ক্রিনশটের হুবহু প্লেয়ার অংশ)
            // =========================================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9.5f)
                    .background(Color.Black)
            ) {
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

                // 🔝 উপরে ডানে: [ Visit advertiser ↗ ]
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 8.dp, end = 10.dp)
                        .clickable { handleDestinationClick() }
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

                // 🏷️ নিচে বাঁয়ে: [ Sponsored ⓘ ]
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Sponsored",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(13.dp)
                    )
                }

                // ⏭️ নিচে ডানে: [ Skip ❯| ] বাটন
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 10.dp, bottom = 8.dp)
                ) {
                    if (canBeSkipped) {
                        if (isSkipButtonUnlocked) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = YouTubeSkipButtonBg,
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.8f)),
                                modifier = Modifier.clickable { onAdFinishedOrSkipped() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Skip",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "Skip",
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = YouTubeSkipButtonBg
                            ) {
                                Text(
                                    text = "$remainingSecondsToSkip",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // 🟡 হলুদ প্রগ্রেস বার (ভিডিওর নিচে)
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

            // =========================================================================
            // 📑 ২. নিচের অংশ: ইউটিউবের হুবহু অ্যাড ডিটেইলস ও কাস্টম অ্যাকশন পেজ
            // =========================================================================
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ২.১ Sponsored হেডার এবং অপশনস (Like, Share, Close)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sponsored",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                            contentDescription = "Like",
                            tint = if (isLiked) YouTubeAdYellow else Color.White,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { isLiked = !isLiked }
                        )

                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = Color.White,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "${ad.title}: ${ad.destinationTarget}")
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Ad"))
                                }
                        )

                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Menu",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )

                        // ✕ বিজ্ঞাপন বন্ধ করার বাটন
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable {
                                    if (isSkipButtonUnlocked) onAdFinishedOrSkipped()
                                    else Toast.makeText(context, "Please wait ${remainingSecondsToSkip}s", Toast.LENGTH_SHORT).show()
                                }
                        )
                    }
                }

                // ২.২ ব্র্যান্ড লোগো, নাম ও প্লে-স্টোর ব্যাজ
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { handleDestinationClick() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ব্র্যান্ডের স্কয়ার রাউন্ডেড লোগো
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF222B3D)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storefront,
                            contentDescription = null,
                            tint = ad.parsedCtaColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = ad.title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = if (ad.isPlayStore) "Google Play · FREE" else "Official Sponsored Platform",
                            color = Color(0xFFAAAAAA),
                            fontSize = 12.sp
                        )
                    }
                }

                // =========================================================================
                // 🔘 ২.৩ অ্যাডমিন প্যানেল থেকে কাস্টমাইজড ডুয়াল অ্যাকশন বাটন
                // [ Learn more ]  [ Install / Order now / Visit website ]
                // =========================================================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // ১. সেকেন্ডারি বাটন: [ Learn more ]
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = YouTubeSecondaryBtnBg,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clickable { handleDestinationClick() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Learn more",
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // ২. প্রাইমারি ডায়নামিক বাটন: [ Install / Order now / Visit website / VIP ]
                    // অ্যাডমিন প্যানেলে যা লেখা থাকবে হুবহু সেটাই প্রদর্শিত হবে
                    val buttonText = ad.ctaText.ifBlank { "Install" }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = ad.parsedCtaColor.takeIf { it != Color(0xFF00E676) } ?: YouTubeInstallPurple,
                        modifier = Modifier
                            .weight(1.3f)
                            .height(42.dp)
                            .clickable { handleDestinationClick() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = buttonText,
                                color = Color.Black,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // ২.৪ স্ক্রিনশটের মতো রেটিং এবং ডাউনলোড সংখ্যা
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("4.5", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(Icons.Default.Star, contentDescription = null, tint = YouTubeAdYellow, modifier = Modifier.size(13.dp))
                        }
                        Text("Verified", color = Color(0xFF888888), fontSize = 10.5.sp)
                    }

                    Box(modifier = Modifier.height(24.dp).width(1.dp).background(Color(0xFF2A2A2A)))

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("100M+", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Downloads", color = Color(0xFF888888), fontSize = 10.5.sp)
                    }

                    Box(modifier = Modifier.height(24.dp).width(1.dp).background(Color(0xFF2A2A2A)))

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Finance / App", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Category", color = Color(0xFF888888), fontSize = 10.5.sp)
                    }
                }

                HorizontalDivider(color = Color(0xFF222222), thickness = 0.8.dp)

                // =========================================================================
                // 🌐 ২.৫ ওয়েব পেজ / লাইভ প্রিভিউ সেকশন
                // যদি ওয়েবসাইটের লিংক থাকে তবে সরাসরি নিচে ব্রাউজ করা যাবে
                // আর যদি লিংক না থাকে তবে সুন্দর ব্যানার প্রিভিউ কার্ড দেখাবে
                // =========================================================================
                val targetUrl = ad.destinationTarget.trim()
                val isHttpWeb = targetUrl.startsWith("http://") || targetUrl.startsWith("https://")

                if (isHttpWeb) {
                    Text(
                        text = "Website Preview",
                        color = Color(0xFFAAAAAA),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = YouTubeCardDark,
                        border = BorderStroke(0.8.dp, Color(0xFF333333)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        loadWithOverviewMode = true
                                        useWideViewPort = true
                                    }
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                            handleDestinationClick()
                                            return true
                                        }
                                    }
                                    loadUrl(targetUrl)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    // direct download বা Play Store হলে ইউটিউব ব্যানার কার্ড
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = YouTubeCardDark,
                        border = BorderStroke(0.8.dp, Color(0xFF2C2C2C)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { handleDestinationClick() }
                            .padding(top = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = YouTubeAdYellow,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "Official Verified Partner",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tap '$buttonText' to explore in your phone's browser or Google Play Store.",
                                color = Color(0xFFAAAAAA),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
