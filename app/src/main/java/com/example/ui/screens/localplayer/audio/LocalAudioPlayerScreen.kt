@file:OptIn(
    ExperimentalMaterial3Api::class,
    androidx.media3.common.util.UnstableApi::class
)

package com.example.ui.screens.localplayer.audio

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.model.LocalVideoItem
import kotlinx.coroutines.delay
import java.util.Locale

// ১ নম্বর স্ক্রিনশটের হুবহু ডার্ক বার্গান্ডি কালার প্যালেট
private val PlumDarkGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF281928),
        Color(0xFF1E131F),
        Color(0xFF140D15)
    )
)
private val ArtCardBg = Color(0xFF332135)
private val ArtIconTint = Color(0xFF8A6E8D)
private val AccentLavender = Color(0xFFD49BFF)
private val SubtleLavender = Color(0xFFA58FA8)

private fun formatAudioTime(millis: Long): String {
    if (millis <= 0) return "0:00"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

@Composable
fun LocalAudioPlayerScreen(
    initialAudio: LocalVideoItem,
    playlist: List<LocalVideoItem>,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentAudio by remember(initialAudio.id) { mutableStateOf(initialAudio) }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(initialAudio.durationMs) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableLongStateOf(0L) }

    var showEqualizerSheet by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }

    // 🎚️ সাউন্ড এফএক্স স্টেটসমূহ (ইকুয়ালাইজার, বেস বুস্ট, ভলিউম বুস্টার)
    var isEqEnabled by remember { mutableStateOf(true) }
    var bassBoostLevel by remember { mutableFloatStateOf(0.4f) } // 0f to 1f
    var volumeBoostGain by remember { mutableFloatStateOf(0.2f) } // 0f to 1f (+200% পর্যন্ত)
    var selectedPresetName by remember { mutableStateOf("Pop") }

    // সাউন্ড ইঞ্জিন অবজেক্ট
    var equalizerInstance by remember { mutableStateOf<Equalizer?>(null) }
    var bassBoostInstance by remember { mutableStateOf<BassBoost?>(null) }
    var loudnessEnhancerInstance by remember { mutableStateOf<LoudnessEnhancer?>(null) }

    // 🚀 হাই-পারফরম্যান্স ExoPlayer ইঞ্জিন
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

    // পরবর্তী ও পূর্ববর্তী গান প্লে করার লজিক
    fun playNextTrack() {
        val currentIndex = playlist.indexOfFirst { it.id == currentAudio.id }
        if (currentIndex != -1 && currentIndex < playlist.size - 1) {
            currentAudio = playlist[currentIndex + 1]
        } else if (playlist.isNotEmpty()) {
            currentAudio = playlist.first()
        }
    }

    fun playPreviousTrack() {
        val currentIndex = playlist.indexOfFirst { it.id == currentAudio.id }
        if (currentIndex > 0) {
            currentAudio = playlist[currentIndex - 1]
        } else if (playlist.isNotEmpty()) {
            currentAudio = playlist.last()
        }
    }

    // অডিও লোড ও সেশন আইডি বাইন্ডিং
    LaunchedEffect(currentAudio.id) {
        val mediaItem = MediaItem.fromUri(currentAudio.contentUri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    // অডিও ইফেক্ট ইনিশিয়ালাইজেশন
    fun attachAudioEffects(audioSessionId: Int) {
        if (audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId != 0) {
            try {
                equalizerInstance?.release()
                bassBoostInstance?.release()
                loudnessEnhancerInstance?.release()

                // ১. Equalizer
                val eq = Equalizer(0, audioSessionId).apply {
                    enabled = isEqEnabled
                }
                equalizerInstance = eq

                // ২. Bass Boost
                val bb = BassBoost(0, audioSessionId).apply {
                    enabled = isEqEnabled
                    if (strengthSupported) {
                        setStrength((bassBoostLevel * 1000).toInt().toShort())
                    }
                }
                bassBoostInstance = bb

                // ৩. Loudness Enhancer (Vidmate স্টাইল এক্সট্রা সাউন্ড বুস্ট)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    val le = LoudnessEnhancer(audioSessionId).apply {
                        enabled = isEqEnabled
                        setTargetGain((volumeBoostGain * 1000).toInt()) // up to 1000 mB gain
                    }
                    loudnessEnhancerInstance = le
                }
            } catch (_: Exception) {}
        }
    }

    // প্লেয়ার লিসেনার
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

    // পজিশন ট্র্যাকার
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
            .background(PlumDarkGradient)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // =============================================================
            // 🔝 ১. টপ বার (ব্যাক বাটন ও টাইটেল)
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

                IconButton(
                    onClick = { showPlaylistSheet = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // =============================================================
            // 🎵 ২. সেন্ট্রাল বড় মিউজিক আর্ট কার্ড (স্ক্রিনশট ১ হুবহু লুক)
            // =============================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .aspectRatio(1f)
                    .shadow(elevation = 20.dp, shape = RoundedCornerShape(26.dp), spotColor = Color.Black)
                    .clip(RoundedCornerShape(26.dp))
                    .background(ArtCardBg),
                contentAlignment = Alignment.Center
            ) {
                // ওয়াটারমার্ক ও বড় মিউজিক নোট
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = ArtIconTint,
                    modifier = Modifier.fillMaxSize(0.62f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // =============================================================
            // 📄 ৩. ট্র্যাক টাইটেল ও সাবটাইটেল (স্ক্রিনশট ১ অনুযায়ী)
            // =============================================================
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = currentAudio.displayName.ifBlank { currentAudio.title },
                    color = Color.White,
                    fontSize = 17.5.sp,
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
            // ⏱️ ৪. সিকবার ও টাইমলাইন (০:০৩ / ০:৩৩)
            // =============================================================
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = if (isUserSeeking) scrubPositionMs.toFloat() else currentPositionMs.toFloat(),
                    onValueChange = {
                        isUserSeeking = true
                        scrubPositionMs = it.toLong()
                    },
                    onValueChangeFinished = {
                        exoPlayer.seekTo(scrubPositionMs)
                        currentPositionMs = scrubPositionMs
                        isUserSeeking = false
                    },
                    valueRange = 0f..(totalDurationMs.coerceAtLeast(1L).toFloat()),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White.copy(alpha = 0.85f),
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
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
            // ⏯️ ৫. মেইন প্লেব্যাক কন্ট্রোলস (<< , ▶/⏸ , >>)
            // =============================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // পূর্ববর্তী ট্র্যাক বা রিওয়াইন্ড বাটন (<<)
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

                // বড় প্লে/পজ বাটন (সেন্টার)
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

                // পরবর্তী ট্র্যাক বা ফাস্ট-ফরোয়ার্ড বাটন (>>)
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
            // 🎚️ ৬. বটম বার (ইকুয়ালাইজার ও প্লেলিস্ট আইকন)
            // =============================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // বামে: সাউন্ড মিক্সার ও ইকুয়ালাইজার বাটন (🎚️)
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

                // ডানে: ফোল্ডার ট্র্যাক লিস্ট বা কিউ ড্রয়ার
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
    // 🎛️ ৭. Vidmate স্টাইল সাউন্ড মিক্সার ও ইকুয়ালাইজার বটম শিট
    // =========================================================================
    if (showEqualizerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEqualizerSheet = false },
            containerColor = Color(0xFF1C131E),
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

                // ৭.১) Vidmate স্টাইল অতিরিক্ত ভলিউম বুস্টার (Volume Booster)
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

                // ৭.২) বেস বুস্ট কন্ট্রোলার (Bass Boost)
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

                // ৭.৩) ইকুয়ালাইজার প্রিসেটসমূহ (Presets)
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
                                    Toast.makeText(context, "$preset mode applied", Toast.LENGTH_SHORT).show()
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
    // 📂 ৮. প্লেলিস্ট / কিউ বটম শিট
    // =========================================================================
    if (showPlaylistSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPlaylistSheet = false },
            containerColor = Color(0xFF1C131E),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF4A344D)) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Playlist (${playlist.size} Tracks)",
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
                    items(playlist, key = { it.id }) { track ->
                        val isCurrent = (track.id == currentAudio.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isCurrent) Color(0xFF332035) else Color.Transparent)
                                .clickable {
                                    currentAudio = track
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
}
