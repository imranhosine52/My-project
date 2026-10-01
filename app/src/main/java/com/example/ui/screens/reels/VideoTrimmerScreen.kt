@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ui.screens.reels

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale

private val ActionGreen = Color(0xFF00E676)
private val CyanAccent = Color(0xFF00E5FF)
private val PureBlack = Color(0xFF000000)

private const val MIN_TRIM_DURATION_MS = 3_000L       // ৩ সেকেন্ড
private const val REEL_MAX_DURATION_MS = 180_000L    // ৩ মিনিট (সাধারণ রিল)
private const val SERIES_MAX_DURATION_MS = 600_000L  // ১০ মিনিট (সিরিজ পর্ব)

@Composable
fun VideoTrimmerScreen(
    videoUri: Uri,
    isSeries: Boolean = false,
    onBackClick: () -> Unit,
    onNextClick: (trimmedVideoPath: String, isMuted: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val maxAllowedDurationMs = remember(isSeries) {
        if (isSeries) SERIES_MAX_DURATION_MS else REEL_MAX_DURATION_MS
    }

    var isMuted by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }

    var videoTotalDurationMs by remember { mutableLongStateOf(10_000L) }
    var trimRange by remember { mutableStateOf(0f..10_000f) }
    var currentPlaybackPositionMs by remember { mutableLongStateOf(0L) }

    // ১. ভিডিওর মোট ডিউরেশন বের করা ও ডাইনামিক রেঞ্জ ইনিশিয়ালাইজেশন
    LaunchedEffect(videoUri, maxAllowedDurationMs) {
        withContext(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, videoUri)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val duration = durationStr?.toLongOrNull() ?: 15_000L
                retriever.release()

                videoTotalDurationMs = duration
                val endTrim = duration.toFloat().coerceAtMost(maxAllowedDurationMs.toFloat())
                trimRange = 0f..endTrim
            } catch (_: Exception) {}
        }
    }

    // ২. Media3 ExoPlayer লাইভ প্রিভিউ ইঞ্জিন
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = true
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
        }
    }

    LaunchedEffect(videoUri) {
        exoPlayer.setMediaItem(MediaItem.fromUri(videoUri))
        exoPlayer.prepare()
        exoPlayer.play()
    }

    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    // ৩. ট্রিম করা অংশের মধ্যে ভিডিও সীমাবদ্ধ রাখা (লুপ প্লেব্যাক)
    LaunchedEffect(trimRange) {
        val startMs = trimRange.start.toLong()
        val endMs = trimRange.endInclusive.toLong()

        while (true) {
            val currentPos = exoPlayer.currentPosition
            currentPlaybackPositionMs = currentPos

            if (currentPos < startMs || currentPos >= endMs) {
                exoPlayer.seekTo(startMs)
            }
            delay(100L)
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    BackHandler(enabled = !isExporting) {
        onBackClick()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        // 📺 পূর্ণদৈর্ঘ্য ভিডিও প্লেয়ার সারফেস
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

        // =========================================================================
        // 🔝 টপ বার: [ Back ]  [ Sound On/Off ]  [ Next Button ]
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
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            // মিউট / আনমিউট বাটন
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.6f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                modifier = Modifier.clickable { isMuted = !isMuted }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Mute Toggle",
                        tint = if (isMuted) Color(0xFFFF5252) else (if (isSeries) CyanAccent else ActionGreen),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isMuted) "Sound Off" else "Sound On",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 'Next' বাটন
            Button(
                onClick = {
                    if (isExporting) return@Button
                    val startMs = trimRange.start.toLong()
                    val endMs = trimRange.endInclusive.toLong()
                    val diff = endMs - startMs

                    if (diff < MIN_TRIM_DURATION_MS) {
                        Toast.makeText(context, "Video must be at least 3 seconds long!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (diff > maxAllowedDurationMs) {
                        val limitLabel = if (isSeries) "10 minutes" else "3 minutes"
                        Toast.makeText(context, "Video exceeds $limitLabel limit!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isExporting = true
                    exoPlayer.pause()

                    coroutineScope.launch {
                        val exportedFile = exportTrimmedVideo(
                            context = context,
                            sourceUri = videoUri,
                            startMs = startMs,
                            endMs = endMs,
                            muteAudio = isMuted
                        )
                        isExporting = false

                        if (exportedFile != null && exportedFile.exists()) {
                            onNextClick(exportedFile.absolutePath, isMuted)
                        } else {
                            Toast.makeText(context, "Failed to trim video", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSeries) CyanAccent else ActionGreen
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text(
                    text = "Next ➔",
                    color = Color.Black,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // =========================================================================
        // ✂️ বটম প্যানেল: ডুয়েল থাম্ব রেঞ্জ স্লাইডার ও ডাইনামিক লিমিট লেবেল
        // =========================================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f))
                    )
                )
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val selectedLengthSec = ((trimRange.endInclusive - trimRange.start) / 1000f)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Selected: ${String.format(Locale.US, "%.1fs", selectedLengthSec)}",
                    color = if (isSeries) CyanAccent else ActionGreen,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isSeries) "Series Limit: Max 10m" else "Reel Limit: Max 3m",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // 🎯 ডুয়েল থাম্ব রেঞ্জ স্লাইডার
            RangeSlider(
                value = trimRange,
                onValueChange = { newRange ->
                    val diff = newRange.endInclusive - newRange.start
                    if (diff in (MIN_TRIM_DURATION_MS.toFloat())..(maxAllowedDurationMs.toFloat())) {
                        trimRange = newRange
                        exoPlayer.seekTo(newRange.start.toLong())
                    }
                },
                valueRange = 0f..videoTotalDurationMs.toFloat().coerceAtLeast(10_000f),
                colors = SliderDefaults.colors(
                    thumbColor = if (isSeries) CyanAccent else ActionGreen,
                    activeTrackColor = if (isSeries) CyanAccent else ActionGreen,
                    inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(trimRange.start.toLong()),
                    color = Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatTime(trimRange.endInclusive.toLong()),
                    color = Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // এক্সপোর্ট লোডিং ওভারলে
        if (isExporting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141926)),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSeries) CyanAccent.copy(alpha = 0.6f) else ActionGreen.copy(alpha = 0.6f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = if (isSeries) CyanAccent else ActionGreen,
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = if (isSeries) "Preparing Series Episode..." else "Trimming Reel...",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private suspend fun exportTrimmedVideo(
    context: Context,
    sourceUri: Uri,
    startMs: Long,
    endMs: Long,
    muteAudio: Boolean
): File? = withContext(Dispatchers.IO) {
    try {
        val outputFile = File(context.cacheDir, "trimmed_video_${System.currentTimeMillis()}.mp4")
        val extractor = MediaExtractor()
        extractor.setDataSource(context, sourceUri, null)

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val trackCount = extractor.trackCount
        val trackIndexMap = HashMap<Int, Int>()

        for (i in 0 until trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""

            if (mime.startsWith("video/")) {
                val dstIndex = muxer.addTrack(format)
                trackIndexMap[i] = dstIndex
            } else if (mime.startsWith("audio/") && !muteAudio) {
                val dstIndex = muxer.addTrack(format)
                trackIndexMap[i] = dstIndex
            }
        }

        muxer.start()

        val startUs = startMs * 1000L
        val endUs = endMs * 1000L
        val bufferSize = 1024 * 1024
        val dstBuf = ByteBuffer.allocate(bufferSize)
        val bufferInfo = MediaCodec.BufferInfo()

        for (i in 0 until trackCount) {
            if (!trackIndexMap.containsKey(i)) continue

            extractor.selectTrack(i)
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val dstTrack = trackIndexMap[i]!!

            while (true) {
                bufferInfo.size = extractor.readSampleData(dstBuf, 0)
                if (bufferInfo.size < 0) {
                    bufferInfo.size = 0
                    break
                }
                bufferInfo.presentationTimeUs = extractor.sampleTime
                if (bufferInfo.presentationTimeUs > endUs) break

                bufferInfo.flags = extractor.sampleFlags
                muxer.writeSampleData(dstTrack, dstBuf, bufferInfo)
                extractor.advance()
            }
            extractor.unselectTrack(i)
        }

        muxer.stop()
        muxer.release()
        extractor.release()

        outputFile
    } catch (_: Exception) {
        try {
            val fallback = File(context.cacheDir, "fallback_video_${System.currentTimeMillis()}.mp4")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                java.io.FileOutputStream(fallback).use { output -> input.copyTo(output) }
            }
            fallback
        } catch (_: Exception) {
            null
        }
    }
}

private fun formatTime(millis: Long): String {
    if (millis <= 0) return "00:00"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
