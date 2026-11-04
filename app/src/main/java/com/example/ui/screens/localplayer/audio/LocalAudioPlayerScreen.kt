@file:kotlin.OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.media3.common.util.UnstableApi::class
)

package com.example.ui.screens.localplayer.audio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.model.LocalVideoItem
import com.example.ui.screens.localplayer.gallery.shareSingleFile
import com.example.util.LocalMediaScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.abs

private val AccentLavender = Color(0xFFD49BFF)
private val SubtleLavender = Color(0xFFA58FA8)

private fun formatAudioTime(millis: Long): String {
    if (millis <= 0) return "0:00"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

/**
 * 🖼️ গানের আসল কভার ফটো বা অ্যালবাম আর্ট লোডার
 */
@Composable
private fun AudioCoverArt(item: LocalVideoItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var embeddedArt by remember(item.id) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(item.id, item.path) {
        withContext(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                if (item.path.isNotBlank() && File(item.path).exists()) {
                    retriever.setDataSource(item.path)
                } else {
                    retriever.setDataSource(context, Uri.parse(item.contentUriString))
                }
                val rawBytes = retriever.embeddedPicture
                retriever.release()
                if (rawBytes != null) {
                    embeddedArt = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                }
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF281C2B)),
        contentAlignment = Alignment.Center
    ) {
        if (embeddedArt != null) {
            Image(
                bitmap = embeddedArt!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // প্রিমিয়াম ভিনাইল রেকর্ড লুক
            Box(
                modifier = Modifier
                    .fillMaxSize(0.72f)
                    .clip(CircleShape)
                    .background(Color(0xFF1E1420)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color(0xFF8A6E8D),
                    modifier = Modifier.size(68.dp)
                )
            }
        }
    }
}

/**
 * ⚡ আল্ট্রা-স্লিম ল্যাগ-মুক্ত টাইমলাইন বার
 */
@Composable
private fun UltraSlimAudioTimeline(
    currentPositionMs: Long,
    totalDurationMs: Long,
    onSeekStarted: () -> Unit,
    onSeeking: (Long) -> Unit,
    onSeekFinished: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(-1f) }

    val currentFraction = if (totalDurationMs > 0) {
        (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val effectiveProgress = if (isDragging && dragFraction in 0f..1f) dragFraction else currentFraction

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(20.dp)
            .pointerInput(totalDurationMs) {
                detectTapGestures { offset ->
                    if (totalDurationMs > 0) {
                        val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        val target = (fraction * totalDurationMs).toLong()
                        onSeeking(target)
                        onSeekFinished(target)
                    }
                }
            }
            .pointerInput(totalDurationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        onSeekStarted()
                        if (totalDurationMs > 0) {
                            val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            dragFraction = fraction
                            onSeeking((fraction * totalDurationMs).toLong())
                        }
                    },
                    onDragEnd = {
                        if (isDragging && dragFraction in 0f..1f && totalDurationMs > 0) {
                            val finalTarget = (dragFraction * totalDurationMs).toLong()
                            onSeeking(finalTarget)
                            onSeekFinished(finalTarget)
                        }
                        isDragging = false
                        dragFraction = -1f
                    },
                    onDragCancel = {
                        isDragging = false
                        dragFraction = -1f
                    },
                    onHorizontalDrag = { change, _ ->
                        if (totalDurationMs > 0) {
                            val newFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            dragFraction = newFraction
                            onSeeking((newFraction * totalDurationMs).toLong())
                        }
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().height(6.dp)) {
            val centerY = size.height / 2f
            val trackHeight = 2.5.dp.toPx()
            val thumbRadius = if (isDragging) 4.5.dp.toPx() else 3.5.dp.toPx()
            val trackWidth = size.width

            // ব্যাকগ্রাউন্ড ট্র্যাক
            drawLine(
                color = Color.White.copy(alpha = 0.20f),
                start = androidx.compose.ui.geometry.Offset(0f, centerY),
                end = androidx.compose.ui.geometry.Offset(trackWidth, centerY),
                strokeWidth = trackHeight,
                cap = StrokeCap.Round
            )

            // প্রোগ্রেস লাইন
            val activeEnd = trackWidth * effectiveProgress
            if (activeEnd > 0) {
                drawLine(
                    color = Color.White,
                    start = androidx.compose.ui.geometry.Offset(0f, centerY),
                    end = androidx.compose.ui.geometry.Offset(activeEnd.coerceIn(0f, trackWidth), centerY),
                    strokeWidth = trackHeight,
                    cap = StrokeCap.Round
                )
            }

            // মুভিং থাম্ব
            drawCircle(
                color = Color.White,
                radius = thumbRadius,
                center = androidx.compose.ui.geometry.Offset(activeEnd.coerceIn(0f, trackWidth), centerY)
            )
        }
    }
}

@Composable
fun LocalAudioPlayerScreen(
    initialAudio: LocalVideoItem,
    playlist: List<LocalVideoItem>,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var audioPlaylist by remember { mutableStateOf(if (playlist.isNotEmpty()) playlist else listOf(initialAudio)) }
    val initialIndex = remember(initialAudio.id) {
        audioPlaylist.indexOfFirst { it.id == initialAudio.id }.coerceAtLeast(0)
    }

    // 🌟 সোয়াইপ পেজার (ডানে-বামে টান দিলে গান পরিবর্তন হবে)
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { audioPlaylist.size }
    )

    val currentAudio = audioPlaylist.getOrNull(pagerState.currentPage) ?: initialAudio

    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(currentAudio.durationMs) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableLongStateOf(0L) }

    var showEqualizerSheet by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showFileDetailsDialog by remember { mutableStateOf(false) }

    // সাউন্ড এফএক্স
    var isEqEnabled by remember { mutableStateOf(true) }
    var bassBoostLevel by remember { mutableFloatStateOf(0.4f) }
    var volumeBoostGain by remember { mutableFloatStateOf(0.2f) }
    var selectedPresetName by remember { mutableStateOf("Pop") }

    var equalizerInstance by remember { mutableStateOf<Equalizer?>(null) }
    var bassBoostInstance by remember { mutableStateOf<BassBoost?>(null) }
    var loudnessEnhancerInstance by remember { mutableStateOf<LoudnessEnhancer?>(null) }

    // 🎨 প্রতিটি গানের জন্য আলাদা আলাদা লাক্সারি অ্যাম্বিয়েন্ট ব্যাকগ্রাউন্ড
    val paletteList = remember {
        listOf(
            listOf(Color(0xFF2B142D), Color(0xFF190B1B), Color(0xFF0F0611)), // Midnight Plum
            listOf(Color(0xFF101F36), Color(0xFF0B1424), Color(0xFF060B13)), // Deep Sapphire
            listOf(Color(0xFF0E281F), Color(0xFF081813), Color(0xFF040D0A)), // Dark Emerald
            listOf(Color(0xFF2E1118), Color(0xFF1B090E), Color(0xFF0E0407)), // Wine Crimson
            listOf(Color(0xFF1F1436), Color(0xFF120B22), Color(0xFF090512)), // Velvet Indigo
            listOf(Color(0xFF291B10), Color(0xFF180F08), Color(0xFF0D0804))  // Amber Noir
        )
    }

    val paletteIndex = remember(currentAudio.id) {
        (abs(currentAudio.id.hashCode()) % paletteList.size)
    }
    val currentPalette = paletteList[paletteIndex]

    val c1 by animateColorAsState(currentPalette[0], tween(600, easing = FastOutSlowInEasing), label = "c1")
    val c2 by animateColorAsState(currentPalette[1], tween(600, easing = FastOutSlowInEasing), label = "c2")
    val c3 by animateColorAsState(currentPalette[2], tween(600, easing = FastOutSlowInEasing), label = "c3")
    val dynamicGradient = Brush.verticalGradient(listOf(c1, c2, c3))

    // 🚀 ExoPlayer ইঞ্জিন
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
        }
    }

    // ট্র্যাক পরিবর্তন হলে ExoPlayer-এ লোড করা
    LaunchedEffect(currentAudio.id) {
        val mediaItem = MediaItem.fromUri(currentAudio.contentUri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    fun attachAudioEffects(audioSessionId: Int) {
        if (audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId != 0) {
            try {
                equalizerInstance?.release()
                bassBoostInstance?.release()
                loudnessEnhancerInstance?.release()

                equalizerInstance = Equalizer(0, audioSessionId).apply { enabled = isEqEnabled }
                bassBoostInstance = BassBoost(0, audioSessionId).apply {
                    enabled = isEqEnabled
                    if (strengthSupported) setStrength((bassBoostLevel * 1000).toInt().toShort())
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    loudnessEnhancerInstance = LoudnessEnhancer(audioSessionId).apply {
                        enabled = isEqEnabled
                        setTargetGain((volumeBoostGain * 1000).toInt())
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun playNextTrack() {
        if (pagerState.currentPage < audioPlaylist.size - 1) {
            coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
        } else if (audioPlaylist.isNotEmpty()) {
            coroutineScope.launch { pagerState.animateScrollToPage(0) }
        }
    }

    fun playPreviousTrack() {
        if (pagerState.currentPage > 0) {
            coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        } else if (audioPlaylist.isNotEmpty()) {
            coroutineScope.launch { pagerState.animateScrollToPage(audioPlaylist.size - 1) }
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
                    attachAudioEffects(exoPlayer.audioSessionId)
                } else if (state == Player.STATE_ENDED) {
                    playNextTrack()
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            equalizerInstance?.release()
            bassBoostInstance?.release()
            loudnessEnhancerInstance?.release()
            exoPlayer.release()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            if (!isUserSeeking) {
                currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
            }
            delay(300L)
        }
    }

    BackHandler { onBackClick() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(dynamicGradient)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // =============================================================
            // 🔝 ১. টপ বার
            // =============================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBackClick, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = "Playing Audio",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )

                // 🌟 থ্রি-ডট মেনু বাটন (গ্যালারির মতো কাজ করবে)
                Box {
                    IconButton(
                        onClick = { showOptionsMenu = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // গ্যালারির মতো থ্রি-ডট ড্রপডাউন অপশন মেনু
                    DropdownMenu(
                        expanded = showOptionsMenu,
                        onDismissRequest = { showOptionsMenu = false },
                        containerColor = Color(0xFF1B1422)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Share", color = Color.White) },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = Color.White) },
                            onClick = {
                                showOptionsMenu = false
                                shareSingleFile(context, currentAudio)
                            }
                        )

                        DropdownMenuItem(
                            text = {
                                val isStarred = LocalMediaScanner.getStarredPaths(context).contains(currentAudio.path)
                                Text(if (isStarred) "Remove from Starred" else "Add to Starred", color = Color.White)
                            },
                            leadingIcon = { Icon(Icons.Outlined.Star, contentDescription = null, tint = Color(0xFFFFB300)) },
                            onClick = {
                                showOptionsMenu = false
                                LocalMediaScanner.toggleStarred(context, currentAudio.path)
                                Toast.makeText(context, "Favorite updated", Toast.LENGTH_SHORT).show()
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Details", color = Color.White) },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = AccentLavender) },
                            onClick = {
                                showOptionsMenu = false
                                showFileDetailsDialog = true
                            }
                        )

                        HorizontalDivider(color = Color(0xFF2C2234), thickness = 0.6.dp)

                        DropdownMenuItem(
                            text = { Text("Move to Recycle Bin", color = Color(0xFFFF5252)) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF5252)) },
                            onClick = {
                                showOptionsMenu = false
                                LocalMediaScanner.moveToTrash(context, currentAudio.path)
                                Toast.makeText(context, "Moved to Recycle Bin", Toast.LENGTH_SHORT).show()
                                val updated = audioPlaylist.toMutableList().apply { remove(currentAudio) }
                                if (updated.isEmpty()) {
                                    onBackClick()
                                } else {
                                    audioPlaylist = updated
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // =============================================================
            // 🖼️ ২. ডানে-বামে সোয়াইপযোগ্য আসল অ্যালবাম আর্ট কার্ড
            // =============================================================
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentPadding = PaddingValues(horizontal = 6.dp)
            ) { page ->
                val track = audioPlaylist.getOrNull(page)
                if (track != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .shadow(elevation = 20.dp, shape = RoundedCornerShape(26.dp), spotColor = Color.Black)
                    ) {
                        AudioCoverArt(item = track, modifier = Modifier.fillMaxSize())
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // =============================================================
            // 📄 ৩. ট্র্যাক টাইটেল
            // =============================================================
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = currentAudio.displayName.ifBlank { currentAudio.title },
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    lineHeight = 22.sp,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${currentAudio.formattedSize} • ${currentAudio.folderName}",
                    color = SubtleLavender,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // =============================================================
            // ⏱️ ৪. আল্ট্রা-স্লিম টাইমলাইন বার
            // =============================================================
            Column(modifier = Modifier.fillMaxWidth()) {
                UltraSlimAudioTimeline(
                    currentPositionMs = if (isUserSeeking) scrubPositionMs else currentPositionMs,
                    totalDurationMs = totalDurationMs,
                    onSeekStarted = { isUserSeeking = true },
                    onSeeking = { scrubPositionMs = it },
                    onSeekFinished = { target ->
                        exoPlayer.seekTo(target)
                        currentPositionMs = target
                        isUserSeeking = false
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatAudioTime(if (isUserSeeking) scrubPositionMs else currentPositionMs),
                        color = SubtleLavender,
                        fontSize = 11.5.sp
                    )
                    Text(
                        text = formatAudioTime(totalDurationMs),
                        color = SubtleLavender,
                        fontSize = 11.5.sp
                    )
                }
            }

            // =============================================================
            // ⏯️ ৫. মেইন প্লেব্যাক কন্ট্রোলস
            // =============================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(
                    onClick = { playPreviousTrack() },
                    modifier = Modifier.size(54.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true, radius = 34.dp)
                        ) {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }

                IconButton(
                    onClick = { playNextTrack() },
                    modifier = Modifier.size(54.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            // =============================================================
            // 🎚️ ৬. বটম বার (ইকুয়ালাইজার ও প্লেলিস্ট কিউ)
            // =============================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { showEqualizerSheet = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = "Sound Mixer & Equalizer",
                        tint = if (isEqEnabled) AccentLavender else SubtleLavender,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = { showPlaylistSheet = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Playlist",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }

    // =========================================================================
    // 🎛️ ৭. সাউন্ড মিক্সার ও ইকুয়ালাইজার বটম শিট
    // =========================================================================
    if (showEqualizerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEqualizerSheet = false },
            containerColor = Color(0xFF1B131E),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF4A344D)) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = null,
                            tint = AccentLavender,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Audio Equalizer & Sound Mixer",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Switch(
                        checked = isEqEnabled,
                        onCheckedChange = { checked ->
                            isEqEnabled = checked
                            equalizerInstance?.enabled = checked
                            bassBoostInstance?.enabled = checked
                            loudnessEnhancerInstance?.enabled = checked
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AccentLavender
                        )
                    )
                }

                HorizontalDivider(color = Color(0xFF2E1F30), thickness = 0.8.dp)

                // ভলিউম বুস্টার
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Volume Booster (Extra Sound)", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                        Text("+${(volumeBoostGain * 100).toInt()}%", color = AccentLavender, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = volumeBoostGain,
                        onValueChange = { gain ->
                            volumeBoostGain = gain
                            loudnessEnhancerInstance?.setTargetGain((gain * 1000).toInt())
                        },
                        valueRange = 0f..1.5f,
                        enabled = isEqEnabled,
                        colors = SliderDefaults.colors(thumbColor = AccentLavender, activeTrackColor = AccentLavender)
                    )
                }

                // বেস বুস্ট
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Bass Boost", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                        Text("${(bassBoostLevel * 100).toInt()}%", color = AccentLavender, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = bassBoostLevel,
                        onValueChange = { level ->
                            bassBoostLevel = level
                            bassBoostInstance?.setStrength((level * 1000).toInt().toShort())
                        },
                        valueRange = 0f..1f,
                        enabled = isEqEnabled,
                        colors = SliderDefaults.colors(thumbColor = AccentLavender, activeTrackColor = AccentLavender)
                    )
                }

                // প্রিসেটসমূহ
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Presets", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                    val presets = listOf("Normal", "Pop", "Rock", "Heavy Metal", "Jazz", "Classical", "Bass Boost")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(presets) { preset ->
                            val isSel = (preset == selectedPresetName)
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSel) AccentLavender else Color(0xFF281A2A),
                                border = BorderStroke(0.8.dp, if (isSel) AccentLavender else Color(0xFF4A344D)),
                                modifier = Modifier.clickable(enabled = isEqEnabled) {
                                    selectedPresetName = preset
                                    Toast.makeText(context, "$preset applied", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text(
                                    text = preset,
                                    color = if (isSel) Color.Black else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }

    // =========================================================================
    // 📂 ৮. প্লেলিস্ট বটম শিট
    // =========================================================================
    if (showPlaylistSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPlaylistSheet = false },
            containerColor = Color(0xFF1B131E),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF4A344D)) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Playlist (${audioPlaylist.size} Tracks)",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(audioPlaylist, key = { it.id }) { track ->
                        val isCurrent = (track.id == currentAudio.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isCurrent) Color(0xFF332035) else Color.Transparent)
                                .clickable {
                                    val idx = audioPlaylist.indexOf(track)
                                    if (idx != -1) {
                                        coroutineScope.launch { pagerState.scrollToPage(idx) }
                                    }
                                    showPlaylistSheet = false
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (isCurrent) Icons.Default.PlayArrow else Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = if (isCurrent) AccentLavender else SubtleLavender,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.displayName.ifBlank { track.title },
                                    color = if (isCurrent) AccentLavender else Color.White,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.formattedSize,
                                    color = SubtleLavender,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }

    // =========================================================================
    // 📄 ৯. ফাইল ডিটেইলস ডায়ালগ
    // =========================================================================
    if (showFileDetailsDialog) {
        Dialog(onDismissRequest = { showFileDetailsDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1422))
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Audio Details", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    HorizontalDivider(color = Color(0xFF2C2234), thickness = 0.6.dp)

                    Text("Title: ${currentAudio.title}", color = Color.White, fontSize = 13.sp)
                    Text("Size: ${currentAudio.formattedSize}", color = Color(0xFF8E95A5), fontSize = 13.sp)
                    Text("Duration: ${currentAudio.formattedDuration}", color = Color(0xFF8E95A5), fontSize = 13.sp)
                    Text("Path: ${currentAudio.path}", color = Color(0xFF8E95A5), fontSize = 11.5.sp)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showFileDetailsDialog = false }) {
                            Text("OK", color = AccentLavender)
                        }
                    }
                }
            }
        }
    }
}
