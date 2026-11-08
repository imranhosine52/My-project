@file:kotlin.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.MediaMetadataRetriever
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.MainActivity
import com.example.data.model.LocalVideoItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

object LocalAudioPlayerManager {
    private const val CHANNEL_ID = "local_audio_player_channel"
    private const val NOTIFICATION_ID = 2026

    const val ACTION_PLAY_PAUSE = "com.example.audio.ACTION_PLAY_PAUSE"
    const val ACTION_NEXT = "com.example.audio.ACTION_NEXT"
    const val ACTION_PREV = "com.example.audio.ACTION_PREV"
    const val ACTION_STOP = "com.example.audio.ACTION_STOP"

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var exoPlayer: ExoPlayer? = null
    private var appContext: Context? = null
    private var mediaSession: MediaSession? = null

    // 🎵 প্লেব্যাক স্টেটসমূহ (থ্রেড-সেফ)
    private val _currentTrack = MutableStateFlow<LocalVideoItem?>(null)
    val currentTrack: StateFlow<LocalVideoItem?> = _currentTrack.asStateFlow()

    private val _playlist = MutableStateFlow<List<LocalVideoItem>>(emptyList())
    val playlist: StateFlow<List<LocalVideoItem>> = _playlist.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _isMiniPlayerVisible = MutableStateFlow(false)
    val isMiniPlayerVisible: StateFlow<Boolean> = _isMiniPlayerVisible.asStateFlow()

    private val _audioSessionId = MutableStateFlow(C.AUDIO_SESSION_ID_UNSET)
    val audioSessionId: StateFlow<Int> = _audioSessionId.asStateFlow()

    private var positionJob: Job? = null
    private var isReceiverRegistered = false

    // 🖼️ হাই-পারফরম্যান্স কভার আর্ট ক্যাশিং (যাতে Play/Pause করলে ফ্লিকার না করে)
    private var cachedCoverBitmap: Bitmap? = null

    // 📻 নোটিফিকেশন বাটন রিসিভার
    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_PLAY_PAUSE -> togglePlayPause()
                ACTION_NEXT -> playNext()
                ACTION_PREV -> playPreviousTrackOrRewind()
                ACTION_STOP -> stopAndDismiss()
            }
        }
    }

    /**
     * ইঞ্জিন ও মিডিয়া সেশন ইনিশিয়ালাইজেশন
     */
    fun init(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app

        createNotificationChannel(app)
        registerActionReceiver(app)
        setupMediaSession(app)

        exoPlayer = ExoPlayer.Builder(app).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                    if (playing) startPositionTracker() else stopPositionTracker()
                    syncPlaybackState()
                    updateNotification()
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        _durationMs.value = duration.coerceAtLeast(0L)
                        _audioSessionId.value = audioSessionId
                        syncPlaybackState()
                        updateNotification()
                    } else if (state == Player.STATE_ENDED) {
                        playNext()
                    }
                }
            })
        }
    }

    private fun setupMediaSession(context: Context) {
        mediaSession = MediaSession(context, "LocalAudioMediaSession").apply {
            isActive = true
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { resume() }
                override fun onPause() { pause() }
                override fun onSkipToNext() { playNext() }
                override fun onSkipToPrevious() { playPreviousTrackOrRewind() }
                override fun onSeekTo(pos: Long) { seekTo(pos) }
                override fun onStop() { stopAndDismiss() }
            })
        }
    }

    fun getPlayer(): ExoPlayer? = exoPlayer

    /**
     * 🎶 নতুন ট্র্যাক চালানো
     */
    fun playTrack(context: Context, track: LocalVideoItem, trackList: List<LocalVideoItem> = emptyList()) {
        init(context)
        val player = exoPlayer ?: return

        _currentTrack.value = track
        if (trackList.isNotEmpty()) {
            _playlist.value = trackList
        } else if (_playlist.value.none { it.id == track.id }) {
            _playlist.value = listOf(track)
        }

        _isMiniPlayerVisible.value = true

        val mediaItem = MediaItem.fromUri(track.contentUri)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()

        // গান প্লে হওয়ার সাথে সাথেই ব্যাকগ্রাউন্ডে কভার ফটো লোড করা হবে
        scope.launch(Dispatchers.IO) {
            cachedCoverBitmap = extractAlbumArt(context, track)
            withContext(Dispatchers.Main) {
                syncMediaMetadata(track, cachedCoverBitmap)
                updateNotification()
            }
        }
    }

    private fun extractAlbumArt(context: Context, track: LocalVideoItem): Bitmap? {
        return try {
            val retriever = MediaMetadataRetriever()
            if (track.path.isNotBlank() && File(track.path).exists()) {
                retriever.setDataSource(track.path)
            } else {
                retriever.setDataSource(context, Uri.parse(track.contentUriString))
            }
            val raw = retriever.embeddedPicture
            retriever.release()
            if (raw != null) BitmapFactory.decodeByteArray(raw, 0, raw.size) else null
        } catch (_: Exception) {
            null
        }
    }

    fun resume() {
        exoPlayer?.play()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
        _currentPositionMs.value = positionMs
        syncPlaybackState()
    }

    fun playNext() {
        val current = _currentTrack.value ?: return
        val list = _playlist.value
        val currentIndex = list.indexOfFirst { it.id == current.id }
        if (currentIndex != -1 && currentIndex < list.size - 1) {
            appContext?.let { playTrack(it, list[currentIndex + 1], list) }
        } else if (list.isNotEmpty()) {
            appContext?.let { playTrack(it, list.first(), list) }
        }
    }

    fun playPreviousTrackOrRewind() {
        val player = exoPlayer ?: return
        if (player.currentPosition > 3000L) {
            seekTo(0)
            return
        }
        playPrevious()
    }

    private fun playPrevious() {
        val current = _currentTrack.value ?: return
        val list = _playlist.value
        val currentIndex = list.indexOfFirst { it.id == current.id }
        if (currentIndex > 0) {
            appContext?.let { playTrack(it, list[currentIndex - 1], list) }
        } else if (list.isNotEmpty()) {
            appContext?.let { playTrack(it, list.last(), list) }
        }
    }

    /**
     * 🛑 গান বন্ধ করা ও নোটিফিকেশন রিমুভ করা
     */
    fun stopAndDismiss() {
        exoPlayer?.stop()
        _isPlaying.value = false
        _isMiniPlayerVisible.value = false
        cachedCoverBitmap = null
        stopPositionTracker()
        mediaSession?.isActive = false
        cancelNotification()
    }

    private fun startPositionTracker() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    _currentPositionMs.value = player.currentPosition.coerceAtLeast(0L)
                    _durationMs.value = player.duration.coerceAtLeast(0L)
                }
                delay(400L)
            }
        }
    }

    private fun stopPositionTracker() {
        positionJob?.cancel()
        positionJob = null
    }

    // =========================================================================
    // 🎛️ মিডিয়া সেশন সিঙ্ক (থ্রেড-সেফ)
    // =========================================================================
    private fun syncPlaybackState() {
        val isPlayingState = _isPlaying.value
        val position = _currentPositionMs.value
        val speed = if (isPlayingState) 1.0f else 0f
        val state = if (isPlayingState) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED

        val stateBuilder = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_SEEK_TO or
                        PlaybackState.ACTION_STOP
            )
            .setState(state, position, speed)

        mediaSession?.setPlaybackState(stateBuilder.build())
    }

    private fun syncMediaMetadata(track: LocalVideoItem, coverBitmap: Bitmap?) {
        val metadataBuilder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, track.displayName.ifBlank { track.title })
            .putString(MediaMetadata.METADATA_KEY_ARTIST, track.folderName.ifBlank { "Music" })
            .putString(MediaMetadata.METADATA_KEY_ALBUM, track.folderName.ifBlank { "Local Audio" })
            .putLong(MediaMetadata.METADATA_KEY_DURATION, _durationMs.value)

        if (coverBitmap != null) {
            metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, coverBitmap)
        }

        mediaSession?.setMetadata(metadataBuilder.build())
    }

    // =========================================================================
    // 🔔 Spotify স্টাইল সিস্টেম মিডিয়া নোটিফিকেশন (অ্যান্ড্রয়েড ফ্রেমওয়ার্ক MediaStyle)
    // =========================================================================
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Player",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows interactive playback controls in notification panel"
                setShowBadge(false)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun registerActionReceiver(context: Context) {
        if (isReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(ACTION_PLAY_PAUSE)
            addAction(ACTION_NEXT)
            addAction(ACTION_PREV)
            addAction(ACTION_STOP)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(notificationReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(notificationReceiver, filter)
        }
        isReceiverRegistered = true
    }

    private fun updateNotification() {
        val context = appContext ?: return
        val track = _currentTrack.value ?: return
        val session = mediaSession ?: return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 🎯 Android 14 সেফটি: setPackage(context.packageName)
        val prevIntent = PendingIntent.getBroadcast(context, 1, Intent(ACTION_PREV).setPackage(context.packageName), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val playPauseIntent = PendingIntent.getBroadcast(context, 2, Intent(ACTION_PLAY_PAUSE).setPackage(context.packageName), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val nextIntent = PendingIntent.getBroadcast(context, 3, Intent(ACTION_NEXT).setPackage(context.packageName), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopIntent = PendingIntent.getBroadcast(context, 4, Intent(ACTION_STOP).setPackage(context.packageName), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val isCurrentlyPlaying = _isPlaying.value
        val playPauseIcon = if (isCurrentlyPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        // 🌟 অ্যান্ড্রয়েডের আসল বিল্ট-ইন MediaStyle
        val mediaStyle = Notification.MediaStyle()
            .setMediaSession(session.sessionToken)
            .setShowActionsInCompactView(0, 1, 2) // Prev, Play/Pause, Next (Close will be hidden in compact mode)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        val notification = builder
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(track.displayName.ifBlank { track.title })
            .setContentText(track.folderName.ifBlank { "Music" })
            .setSubText("PlayDramaFlix Audio")
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(stopIntent) // 👈 পজ থাকা অবস্থায় সোয়াইপ করলে প্লেয়ার পুরোপুরি বন্ধ হয়ে যাবে
            .setOngoing(isCurrentlyPlaying) // 👈 প্লে থাকা অবস্থায় নোটিফিকেশন মোছা যাবে না, পজ করলে মোছা যাবে
            .setVisibility(Notification.VISIBILITY_PUBLIC) // লক স্ক্রিনে সুন্দরভাবে দেখাবে
            .setStyle(mediaStyle)
            // 🔘 ৫টি প্রফেশনাল মিডিয়া আইকন বাটন
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevIntent)
            .addAction(playPauseIcon, if (isCurrentlyPlaying) "Pause" else "Play", playPauseIntent)
            .addAction(android.R.drawable.ic_media_next, "Next", nextIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close", stopIntent) // ✕ ক্লোজ বাটন
            .apply {
                if (cachedCoverBitmap != null) {
                    setLargeIcon(cachedCoverBitmap) // কভার আর্ট ব্যাকগ্রাউন্ড হবে
                }
            }
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun cancelNotification() {
        val context = appContext ?: return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }
}
