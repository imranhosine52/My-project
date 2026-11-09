@file:kotlin.OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.media3.common.util.UnstableApi::class
)

package com.example.ui.screens.localplayer.components

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Rational
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.model.AdvancedAudioTrackInfo
import com.example.data.model.AdvancedSubtitleTrackInfo
import com.example.data.model.LocalVideoItem
import com.example.ui.screens.player.components.PlayerGesturesOverlay
import com.example.ui.screens.player.components.PlayerSpeedDrawer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private fun findActivity(context: Context): Activity? {
    var ctx = context
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * 🌍 ISO 639-1 / 639-2 ল্যাঙ্গুয়েজ কোড থেকে পরিচ্ছন্ন নাম নির্ণায়ক
 */
private fun resolveLanguageDisplayName(langCode: String?, label: String?): String {
    val cleanLabel = label?.trim() ?: ""
    if (cleanLabel.isNotBlank() && !cleanLabel.equals("und", true) && !cleanLabel.equals("unknown", true)) {
        return cleanLabel
    }
    val code = langCode?.trim()?.lowercase() ?: ""
    return when {
        code in listOf("hi", "hin") -> "Hindi"
        code in listOf("bn", "ben") -> "Bengali"
        code in listOf("en", "eng") -> "English"
        code in listOf("zh", "zho", "chi") -> "Chinese"
        code in listOf("ja", "jpn") -> "Japanese"
        code in listOf("ko", "kor") -> "Korean"
        code in listOf("ta", "tam") -> "Tamil"
        code in listOf("te", "tel") -> "Telugu"
        code in listOf("ur", "urd") -> "Urdu"
        code in listOf("es", "spa") -> "Spanish"
        code in listOf("fr", "fra", "fre") -> "French"
        code in listOf("de", "deu", "ger") -> "German"
        code in listOf("ru", "rus") -> "Russian"
        code in listOf("ar", "ara") -> "Arabic"
        code in listOf("pt", "por") -> "Portuguese"
        code in listOf("it", "ita") -> "Italian"
        code in listOf("tr", "tur") -> "Turkish"
        code.isNotBlank() -> Locale(code).displayLanguage.takeIf { it.isNotBlank() } ?: code.uppercase()
        else -> "Audio"
    }
}

/**
 * 🔊 অডিও চ্যানেল কনফিগারেশন নির্ণায়ক (Mono, Stereo 2.0, 5.1 Surround)
 */
private fun resolveAudioChannelLayout(channels: Int): String {
    return when (channels) {
        1 -> "Mono"
        2 -> "Stereo 2.0"
        6 -> "5.1 Surround"
        8 -> "7.1 Surround"
        else -> if (channels > 2) "$channels Channels" else "Stereo"
    }
}

/**
 * 🏷️ অডিও কোডেক নির্ণায়ক (AAC, AC3, EAC3, MP3)
 */
private fun resolveAudioCodecFormat(mimeType: String?): String {
    return when {
        mimeType == null -> "Audio"
        mimeType.contains("eac3", true) -> "E-AC3"
        mimeType.contains("ac3", true) -> "AC3"
        mimeType.contains("aac", true) -> "AAC"
        mimeType.contains("mp4a", true) -> "AAC"
        mimeType.contains("mpeg", true) || mimeType.contains("mp3", true) -> "MP3"
        mimeType.contains("opus", true) -> "OPUS"
        mimeType.contains("vorbis", true) -> "VORBIS"
        mimeType.contains("dts", true) -> "DTS"
        mimeType.contains("flac", true) -> "FLAC"
        else -> mimeType.substringAfterLast("/").substringAfterLast("-").uppercase()
    }
}

@Composable
fun LocalPlayerVideoBox(
    exoPlayer: ExoPlayer,
    currentVideo: LocalVideoItem,
    folderVideos: List<LocalVideoItem>,
    isLandscape: Boolean,
    isPlaying: Boolean,
    currentPositionMs: Long,
    totalDurationMs: Long,
    onBackClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSeek: (seconds: Int) -> Unit,
    onSeekFinished: (positionMs: Long) -> Unit,
    onSelectVideo: (LocalVideoItem) -> Unit,
    onNextVideoClick: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { findActivity(context) }
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val configuration = LocalConfiguration.current
    val coroutineScope = rememberCoroutineScope()

    var isControlsVisible by remember { mutableStateOf(true) }
    var isScreenLocked by rememberSaveable { mutableStateOf(false) }

    // ⚡ ২X স্পিড
    var is2xActive by remember { mutableStateOf(false) }
    var previousSpeedBefore2x by remember { mutableFloatStateOf(1.0f) }

    // =========================================================================
    // 🎧 Phase 4: মাল্টি-অডিও ট্র্যাক স্টেট
    // =========================================================================
    var availableAudioTracks by remember { mutableStateOf<List<AdvancedAudioTrackInfo>>(emptyList()) }
    var selectedAudioTrackId by rememberSaveable { mutableStateOf("default") }
    var currentAudioLabel by rememberSaveable(currentVideo.id) { mutableStateOf("Audio") }

    // =========================================================================
    // 💬 Phase 5: মাল্টি-সাবটাইটেল ট্র্যাক স্টেট
    // =========================================================================
    var availableSubtitleTracks by remember { mutableStateOf<List<AdvancedSubtitleTrackInfo>>(emptyList()) }
    var selectedSubtitleTrackId by rememberSaveable { mutableStateOf("off") }
    var currentSubtitleLabel by rememberSaveable { mutableStateOf("CC") }
    var currentSubtitleDelayMs by rememberSaveable { mutableLongStateOf(0L) }
    var currentSubtitleSizeScale by rememberSaveable { mutableFloatStateOf(1.0f) }

    var isMuted by remember { mutableStateOf(false) }
    var bufferedPositionMs by remember { mutableLongStateOf(0L) }
    var isBuffering by remember { mutableStateOf(exoPlayer.playbackState == Player.STATE_BUFFERING) }

    // ড্রয়ার টাইপ: "none", "audio", "subtitles", "speed", "playlist"
    var sideDrawerType by remember { mutableStateOf("none") }
    val showSideDrawer = (sideDrawerType != "none")

    val speedOptions = remember { listOf(2.0f, 1.5f, 1.25f, 1.0f, 0.75f, 0.5f) }
    var currentSpeed by rememberSaveable { mutableFloatStateOf(1.0f) }

    // 🔍 জুম ও প্যান স্টেট
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var zoomOffset by remember { mutableStateOf(Offset.Zero) }

    // ☀️ উজ্জ্বলতা ও 🔊 ভলিউম স্টেট
    var brightnessLevel by remember {
        mutableFloatStateOf(activity?.window?.attributes?.screenBrightness?.takeIf { it > 0 } ?: 0.5f)
    }
    var showBrightnessOverlay by remember { mutableStateOf(false) }

    var volumeFloat by remember {
        val maxV = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
        val curV = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
        mutableFloatStateOf(if (maxV > 0) curV / maxV else 0.5f)
    }
    var showVolumeOverlay by remember { mutableStateOf(false) }

    var isHorizontalScrubbing by remember { mutableStateOf(false) }
    var scrubTargetPositionMs by remember { mutableLongStateOf(0L) }

    var isUserSeeking by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableLongStateOf(0L) }

    var showLeftDoubleTapAnim by remember { mutableStateOf(false) }
    var showRightDoubleTapAnim by remember { mutableStateOf(false) }

    // =========================================================================
    // 🎧 ExoPlayer থেকে সমস্ত অডিও ও সাবটাইটেল ট্র্যাক নিখুঁতভাবে এক্সট্র্যাক্ট করা
    // =========================================================================
    fun extractTracks(tracks: Tracks) {
        val foundAudioTracks = mutableListOf<AdvancedAudioTrackInfo>()
        val foundSubtitleTracks = mutableListOf<AdvancedSubtitleTrackInfo>()

        for ((groupIndex, group) in tracks.groups.withIndex()) {
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val lang = format.language?.lowercase() ?: ""
                    val isSelected = group.isTrackSelected(i)

                    val readableName = resolveLanguageDisplayName(lang, format.label)
                    val channelInfo = resolveAudioChannelLayout(format.channelCount)
                    val codecName = resolveAudioCodecFormat(format.sampleMimeType)

                    val trackId = "${groupIndex}_$i"
                    if (isSelected) {
                        selectedAudioTrackId = trackId
                        currentAudioLabel = readableName
                    }

                    foundAudioTracks.add(
                        AdvancedAudioTrackInfo(
                            id = trackId,
                            languageCode = lang.ifBlank { "und" },
                            displayName = if (foundAudioTracks.any { it.displayName == readableName }) {
                                "$readableName (${foundAudioTracks.size + 1})"
                            } else readableName,
                            channelInfo = channelInfo,
                            codec = codecName,
                            bitRate = format.bitrate,
                            groupIndex = groupIndex,
                            trackIndex = i,
                            isSelected = isSelected
                        )
                    )
                }
            } else if (group.type == C.TRACK_TYPE_TEXT) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val lang = format.language?.lowercase() ?: ""
                    val isSelected = group.isTrackSelected(i)

                    val readableName = when {
                        !format.label.isNullOrBlank() -> format.label!!.trim()
                        lang in listOf("bn", "ben") -> "Bangla Subtitle"
                        lang in listOf("en", "eng") -> "English Subtitle"
                        lang in listOf("hi", "hin") -> "Hindi Subtitle"
                        lang in listOf("zh", "zho", "chi") -> "Chinese Subtitle"
                        lang in listOf("ja", "jpn") -> "Japanese Subtitle"
                        lang in listOf("ko", "kor") -> "Korean Subtitle"
                        lang.isNotBlank() -> "${Locale(lang).displayLanguage} Subtitle"
                        else -> "Subtitle ${foundSubtitleTracks.size + 1}"
                    }

                    val trackId = "sub_${groupIndex}_$i"
                    if (isSelected) {
                        selectedSubtitleTrackId = trackId
                        currentSubtitleLabel = "CC"
                    }

                    foundSubtitleTracks.add(
                        AdvancedSubtitleTrackInfo(
                            id = trackId,
                            languageCode = lang.ifBlank { "und" },
                            displayName = readableName,
                            isExternal = false,
                            groupIndex = groupIndex,
                            trackIndex = i,
                            isSelected = isSelected
                        )
                    )
                }
            }
        }

        availableAudioTracks = foundAudioTracks
        availableSubtitleTracks = foundSubtitleTracks
        if (availableAudioTracks.isNotEmpty() && (selectedAudioTrackId == "default" || currentAudioLabel == "Audio")) {
            currentAudioLabel = availableAudioTracks.first().displayName
        }
    }

    // =========================================================================
    // 🔀 প্লেব্যাক না থামিয়ে অডিও ট্র্যাক পরিবর্তন (Seamless Switching)
    // =========================================================================
    fun applyAudioTrack(track: AdvancedAudioTrackInfo) {
        selectedAudioTrackId = track.id
        currentAudioLabel = track.displayName

        val currentTracks = exoPlayer.currentTracks
        val targetGroup = currentTracks.groups.getOrNull(track.groupIndex)

        if (targetGroup != null && targetGroup.type == C.TRACK_TYPE_AUDIO) {
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setOverrideForType(TrackSelectionOverride(targetGroup.mediaTrackGroup, track.trackIndex))
                .build()
            Toast.makeText(context, "Audio: ${track.displayName} (${track.channelInfo})", Toast.LENGTH_SHORT).show()
        }
    }

    // =========================================================================
    // 💬 প্লেব্যাক না থামিয়ে সাবটাইটেল পরিবর্তন (Seamless Subtitle Switching)
    // =========================================================================
    fun applySubtitleTrack(subTrack: AdvancedSubtitleTrackInfo?) {
        if (subTrack == null) {
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .build()
            selectedSubtitleTrackId = "off"
            currentSubtitleLabel = "CC"
            Toast.makeText(context, "Subtitles: Off", Toast.LENGTH_SHORT).show()
        } else {
            val currentTracks = exoPlayer.currentTracks
            val targetGroup = currentTracks.groups.getOrNull(subTrack.groupIndex)
            if (targetGroup != null && targetGroup.type == C.TRACK_TYPE_TEXT) {
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(TrackSelectionOverride(targetGroup.mediaTrackGroup, subTrack.trackIndex))
                    .build()
                selectedSubtitleTrackId = subTrack.id
                currentSubtitleLabel = "CC"
                Toast.makeText(context, "Subtitle: ${subTrack.displayName}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // =========================================================================
    // 📁 এক্সটার্নাল সাবটাইটেল (.srt, .vtt) লোডিং ইঞ্জিন
    // =========================================================================
    fun loadExternalSubtitleFile(uri: Uri) {
        try {
            val uriStr = uri.toString().lowercase()
            val mimeType = when {
                uriStr.endsWith(".vtt") -> MimeTypes.TEXT_VTT
                uriStr.endsWith(".ass") || uriStr.endsWith(".ssa") -> MimeTypes.TEXT_SSA
                else -> MimeTypes.APPLICATION_SUBRIP
            }

            val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(uri)
                .setMimeType(mimeType)
                .setLanguage("und")
                .setLabel("External Subtitle")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()

            val currentPos = exoPlayer.currentPosition
            val isCurrentlyPlaying = exoPlayer.isPlaying
            val currentMediaItem = exoPlayer.currentMediaItem
            if (currentMediaItem != null) {
                val updatedItem = currentMediaItem.buildUpon()
                    .setSubtitleConfigurations(listOf(subtitleConfig))
                    .build()
                exoPlayer.setMediaItem(updatedItem, currentPos)
                exoPlayer.prepare()
                if (isCurrentlyPlaying) exoPlayer.play()
                Toast.makeText(context, "External subtitle loaded!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot load subtitle: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                extractTracks(tracks)
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = (state == Player.STATE_BUFFERING)
                bufferedPositionMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(isPlaying, isUserSeeking) {
        while (isPlaying) {
            bufferedPositionMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)
            delay(500L)
        }
    }

    fun triggerSkip(seconds: Int) {
        onSeek(seconds)
        coroutineScope.launch {
            if (seconds < 0) {
                showLeftDoubleTapAnim = true
                delay(600)
                showLeftDoubleTapAnim = false
            } else {
                showRightDoubleTapAnim = true
                delay(600)
                showRightDoubleTapAnim = false
            }
        }
    }

    LaunchedEffect(isControlsVisible, isPlaying, isScreenLocked, showSideDrawer) {
        if (isControlsVisible && isPlaying && !isScreenLocked && !showSideDrawer) {
            delay(5000L)
            isControlsVisible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            // =============================================================
            // 👆 সোয়াইপ জেসচার (আগের ডিজাইন ১০০% বজায় রাখা হয়েছে)
            // =============================================================
            .pointerInput(isScreenLocked, isLandscape, showSideDrawer) {
                if (isScreenLocked || showSideDrawer) return@pointerInput

                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    val isNearLeftEdge = (firstDown.position.x < 48.dp.toPx())
                    val isNearRightEdge = (firstDown.position.x > size.width - 48.dp.toPx())

                    val isLeft = firstDown.position.x < size.width / 2f
                    var isTransforming = false
                    var isVerticalDragging = false
                    var isHorizontalDragging = false
                    var isEdgeSwipingPlaylist = false
                    var totalDragY = 0f
                    var totalDragX = 0f
                    val touchSlop = viewConfiguration.touchSlop
                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
                    val startScrubPos = exoPlayer.currentPosition

                    if (isLeft && maxVol > 0) {
                        val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                        volumeFloat = cur / maxVol
                    }

                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.size

                        if (pointerCount >= 2) {
                            isTransforming = true
                            showBrightnessOverlay = false
                            showVolumeOverlay = false
                            isHorizontalScrubbing = false

                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()

                            zoomScale = (zoomScale * zoomChange).coerceIn(1.0f, 3.0f)
                            if (zoomScale > 1.0f) {
                                val maxOffsetX = (size.width * (zoomScale - 1f)) / 2f
                                val maxOffsetY = (size.height * (zoomScale - 1f)) / 2f
                                zoomOffset = Offset(
                                    x = (zoomOffset.x + panChange.x).coerceIn(-maxOffsetX, maxOffsetX),
                                    y = (zoomOffset.y + panChange.y).coerceIn(-maxOffsetY, maxOffsetY)
                                )
                            } else {
                                zoomOffset = Offset.Zero
                            }
                            event.changes.forEach { it.consume() }
                        } else if (pointerCount == 1 && !isTransforming) {
                            val change = event.changes.first()
                            val dragY = change.position.y - change.previousPosition.y
                            val dragX = change.position.x - change.previousPosition.x

                            totalDragY += dragY
                            totalDragX += dragX

                            if (isLandscape && !isVerticalDragging && (isNearRightEdge && totalDragX < -touchSlop || isNearLeftEdge && totalDragX > touchSlop)) {
                                isEdgeSwipingPlaylist = true
                            }

                            if (!isVerticalDragging && !isHorizontalDragging && !isEdgeSwipingPlaylist && abs(totalDragY) > touchSlop && abs(totalDragY) > abs(totalDragX)) {
                                isVerticalDragging = true
                                if (isLeft) showVolumeOverlay = true else showBrightnessOverlay = true
                            }

                            if (!isVerticalDragging && !isHorizontalDragging && !isEdgeSwipingPlaylist && abs(totalDragX) > touchSlop && abs(totalDragX) > abs(totalDragY)) {
                                isHorizontalDragging = true
                                isHorizontalScrubbing = true
                            }

                            if (isVerticalDragging) {
                                val delta = -dragY / (size.height * 0.70f)
                                if (isLeft) {
                                    volumeFloat = (volumeFloat + delta).coerceIn(0f, 1f)
                                    val targetVolInt = (volumeFloat * maxVol).roundToInt()
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolInt, 0)
                                } else {
                                    brightnessLevel = (brightnessLevel + delta).coerceIn(0.01f, 1.0f)
                                    activity?.window?.let { win ->
                                        val lp = win.attributes
                                        lp.screenBrightness = brightnessLevel
                                        win.attributes = lp
                                    }
                                }
                                change.consume()
                            } else if (isHorizontalDragging) {
                                val deltaSec = (totalDragX / 5.5f).toInt()
                                val targetPos = (startScrubPos + (deltaSec * 1000L)).coerceIn(0L, totalDurationMs.coerceAtLeast(1L))
                                scrubTargetPositionMs = targetPos
                                change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (isEdgeSwipingPlaylist) {
                        sideDrawerType = "playlist"
                        isControlsVisible = false
                    } else if (isHorizontalDragging) {
                        onSeekFinished(scrubTargetPositionMs)
                        exoPlayer.seekTo(scrubTargetPositionMs)
                    }

                    showBrightnessOverlay = false
                    showVolumeOverlay = false
                    isHorizontalScrubbing = false
                }
            }
            .pointerInput(isScreenLocked, showSideDrawer, isUserSeeking) {
                if (isScreenLocked) {
                    detectTapGestures(
                        onTap = {
                            isControlsVisible = !isControlsVisible
                        }
                    )
                    return@pointerInput
                }

                detectTapGestures(
                    onPress = {
                        var holdJob: Job? = null
                        try {
                            holdJob = coroutineScope.launch {
                                delay(400)
                                if (!isUserSeeking && !isHorizontalScrubbing && !showSideDrawer) {
                                    is2xActive = true
                                    previousSpeedBefore2x = currentSpeed
                                    exoPlayer.setPlaybackSpeed(2.0f)
                                }
                            }
                            tryAwaitRelease()
                        } finally {
                            holdJob?.cancel()
                            if (is2xActive) {
                                is2xActive = false
                                exoPlayer.setPlaybackSpeed(previousSpeedBefore2x)
                            }
                        }
                    },
                    onTap = {
                        if (showSideDrawer) {
                            sideDrawerType = "none"
                            isControlsVisible = false
                        } else {
                            isControlsVisible = !isControlsVisible
                        }
                    },
                    onDoubleTap = { offset ->
                        if (!isScreenLocked && !showSideDrawer) {
                            if (zoomScale > 1.05f) {
                                zoomScale = 1.0f
                                zoomOffset = Offset.Zero
                            } else {
                                if (offset.x < size.width / 2) triggerSkip(-10) else triggerSkip(10)
                            }
                        }
                    }
                )
            }
    ) {
        // ১. ExoPlayer সারফেস
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    keepScreenOn = true
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                if (view.player != exoPlayer) view.player = exoPlayer
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = zoomScale,
                    scaleY = zoomScale,
                    translationX = zoomOffset.x,
                    translationY = zoomOffset.y
                )
        )

        // ২. জেসচার ওভারলে (ভলিউম, ব্রাইটনেস, ২X স্পিড)
        PlayerGesturesOverlay(
            is2xActive = is2xActive,
            isHorizontalScrubbing = isHorizontalScrubbing,
            scrubTargetPositionMs = scrubTargetPositionMs,
            totalDurationMs = totalDurationMs,
            showLeftDoubleTapAnim = showLeftDoubleTapAnim,
            showRightDoubleTapAnim = showRightDoubleTapAnim,
            showVolumeOverlay = showVolumeOverlay && !showSideDrawer,
            volumeFloat = volumeFloat,
            showBrightnessOverlay = showBrightnessOverlay && !showSideDrawer,
            brightnessLevel = brightnessLevel,
            screenWidthDp = configuration.screenWidthDp.dp,
            modifier = Modifier.fillMaxSize()
        )

        // ৩. বাফারিং স্পিনার
        if (isBuffering) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF00E5FF), strokeWidth = 3.dp, modifier = Modifier.size(42.dp))
            }
        }

        // ৪. সম্পূর্ণ কন্ট্রোল ওভারলে
        LocalPlayerControlsOverlay(
            isControlsVisible = isControlsVisible,
            isScreenLocked = isScreenLocked,
            isPlaying = isPlaying,
            isMuted = isMuted,
            isLandscape = isLandscape,
            videoTitle = currentVideo.title,
            currentPositionMs = currentPositionMs,
            totalDurationMs = totalDurationMs,
            bufferedPositionMs = bufferedPositionMs,
            isUserSeeking = isUserSeeking,
            scrubPosition = scrubPosition,
            currentSpeed = currentSpeed,
            currentAudioLabel = currentAudioLabel,
            currentSubtitleLabel = currentSubtitleLabel,
            hasMultipleAudio = availableAudioTracks.size > 1,
            hasSubtitles = availableSubtitleTracks.isNotEmpty(),
            isSubtitleActive = (selectedSubtitleTrackId != "off"),
            onBackClick = onBackClick,
            onPiPClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        val params = PictureInPictureParams.Builder()
                            .setAspectRatio(Rational(16, 9))
                            .build()
                        isControlsVisible = false
                        activity?.enterPictureInPictureMode(params)
                    } catch (_: Exception) {
                        activity?.enterPictureInPictureMode()
                    }
                }
            },
            onCastClick = {
                try {
                    context.startActivity(Intent(Settings.ACTION_CAST_SETTINGS))
                } catch (_: Exception) {
                    Toast.makeText(context, "Searching for TV/Display...", Toast.LENGTH_SHORT).show()
                }
            },
            onPlayPauseClick = onPlayPauseClick,
            onMuteToggle = {
                isMuted = !isMuted
                exoPlayer.volume = if (isMuted) 0f else 1f
            },
            onLockToggle = {
                if (isScreenLocked) {
                    isScreenLocked = false
                    isControlsVisible = true
                    Toast.makeText(context, "Screen Unlocked", Toast.LENGTH_SHORT).show()
                } else {
                    isScreenLocked = true
                    isControlsVisible = false
                    sideDrawerType = "none"
                    Toast.makeText(context, "Screen Locked", Toast.LENGTH_SHORT).show()
                }
            },
            onSeekStarted = { isUserSeeking = true },
            onSeeking = { scrubPosition = it },
            onSeekFinished = { pos ->
                onSeekFinished(pos)
                exoPlayer.seekTo(pos)
                isUserSeeking = false
            },
            onNextVideoClick = onNextVideoClick,
            onSpeedClick = { sideDrawerType = "speed" },
            onAudioClick = { sideDrawerType = "audio" },
            onSubtitleClick = { sideDrawerType = "subtitles" },
            onToggleFullscreen = onToggleFullscreen,
            modifier = Modifier.fillMaxSize()
        )

        // =============================================================
        // 🚪 ৫. ল্যান্ডস্কেপ সাইড ড্রয়ারসমূহ (অডিও, সাবটাইটেল, স্পিড, প্লেলিস্ট)
        // =============================================================
        AnimatedVisibility(
            visible = showSideDrawer,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(280, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(220)),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(240, easing = FastOutSlowInEasing)
            ) + fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(if (sideDrawerType == "playlist") 285.dp else 240.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.45f),
                                Color.Black.copy(alpha = 0.78f)
                            )
                        )
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
            ) {
                when (sideDrawerType) {
                    "audio" -> {
                        LocalPlayerAudioDrawer(
                            availableAudioTracks = availableAudioTracks,
                            selectedAudioTrackId = selectedAudioTrackId,
                            onSelectAudioTrack = { track ->
                                applyAudioTrack(track)
                                sideDrawerType = "none"
                            },
                            onClose = { sideDrawerType = "none" }
                        )
                    }
                    "subtitles" -> {
                        LocalPlayerSubtitleDrawer(
                            availableSubtitleTracks = availableSubtitleTracks,
                            selectedSubtitleTrackId = selectedSubtitleTrackId,
                            onSelectSubtitleTrack = { sub ->
                                applySubtitleTrack(sub)
                                sideDrawerType = "none"
                            },
                            onExternalSubtitleLoaded = { uri ->
                                loadExternalSubtitleFile(uri)
                                sideDrawerType = "none"
                            },
                            currentSubtitleDelayMs = currentSubtitleDelayMs,
                            onSubtitleDelayChanged = { delayMs ->
                                currentSubtitleDelayMs = delayMs
                            },
                            currentTextSizeScale = currentSubtitleSizeScale,
                            onSubtitleSizeScaleChanged = { scale ->
                                currentSubtitleSizeScale = scale
                            },
                            onClose = { sideDrawerType = "none" }
                        )
                    }
                    "speed" -> {
                        PlayerSpeedDrawer(
                            currentSpeed = currentSpeed,
                            speedOptions = speedOptions,
                            onSelectSpeed = { spd ->
                                currentSpeed = spd
                                exoPlayer.setPlaybackSpeed(spd)
                                sideDrawerType = "none"
                            }
                        )
                    }
                    "playlist" -> {
                        LocalPlayerFolderPlaylist(
                            folderName = currentVideo.folderName,
                            videos = folderVideos,
                            currentVideoId = currentVideo.id,
                            onSelectVideo = { item ->
                                onSelectVideo(item)
                                sideDrawerType = "none"
                            },
                            onNextVideoClick = {
                                onNextVideoClick()
                                sideDrawerType = "none"
                            },
                            isLandscapeDrawer = true
                        )
                    }
                }
            }
        }
    }
}
