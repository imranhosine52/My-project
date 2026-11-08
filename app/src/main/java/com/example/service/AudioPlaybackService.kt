package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.util.LocalAudioPlayerManager
import kotlinx.coroutines.*

/**
 * 🎵 AudioPlaybackService
 * MX Player / Spotify স্টাইল Android System MediaStyle Notification Controller
 * এবং দীর্ঘস্থায়ী ব্যাকগ্রাউন্ড মিডিয়া প্লেব্যাক সার্ভিস।
 */
class AudioPlaybackService : Service() {

    companion object {
        const val CHANNEL_ID = "mx_style_audio_playback_channel"
        const val NOTIFICATION_ID = 2026

        // 🔘 নোটিফিকেশন অ্যাকশন বাটন কমান্ডস
        const val ACTION_PLAY = "com.example.audio.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.audio.ACTION_PAUSE"
        const val ACTION_PLAY_PAUSE = "com.example.audio.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.audio.ACTION_NEXT"
        const val ACTION_PREV = "com.example.audio.ACTION_PREV"
        const val ACTION_STOP = "com.example.audio.ACTION_STOP"

        /**
         * সার্ভিস চালু করার স্ট্যাটিক হেলপার
         */
        fun start(context: Context) {
            val intent = Intent(context, AudioPlaybackService::class.java).apply {
                action = ACTION_PLAY
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * সার্ভিস বন্ধ করার স্ট্যাটিক হেলপার
         */
        fun stop(context: Context) {
            val intent = Intent(context, AudioPlaybackService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var observerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupMediaSession()
        observePlayerState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> LocalAudioPlayerManager.togglePlayPause()
            ACTION_PLAY -> LocalAudioPlayerManager.resume()
            ACTION_PAUSE -> LocalAudioPlayerManager.pause()
            ACTION_NEXT -> LocalAudioPlayerManager.playNext()
            ACTION_PREV -> LocalAudioPlayerManager.playPreviousTrackOrRewind()
            ACTION_STOP -> {
                LocalAudioPlayerManager.stopAndDismiss()
                stopForegroundService()
                return START_NOT_STICKY
            }
        }
        updateNotification()
        return START_STICKY
    }

    /**
     * 🎛️ অ্যান্ড্রয়েড সিস্টেম মিডিয়া সেশন সেটআপ (হেডসেট বাটন ও লক-স্ক্রিনের জন্য)
     */
    private fun setupMediaSession() {
        mediaSession = MediaSession(this, "MXAudioSession").apply {
            isActive = true
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    LocalAudioPlayerManager.resume()
                }

                override fun onPause() {
                    LocalAudioPlayerManager.pause()
                }

                override fun onSkipToNext() {
                    LocalAudioPlayerManager.playNext()
                }

                override fun onSkipToPrevious() {
                    LocalAudioPlayerManager.playPreviousTrackOrRewind()
                }

                override fun onSeekTo(pos: Long) {
                    LocalAudioPlayerManager.seekTo(pos)
                }

                override fun onStop() {
                    LocalAudioPlayerManager.stopAndDismiss()
                    stopForegroundService()
                }
            })
        }
    }

    /**
     * 🔄 প্লেয়ারের লাইভ স্টেট পর্যবেক্ষণ (StateFlow Observer)
     */
    private fun observePlayerState() {
        observerJob?.cancel()
        observerJob = serviceScope.launch {
            launch {
                LocalAudioPlayerManager.isPlaying.collect {
                    syncPlaybackState()
                    updateNotification()
                }
            }
            launch {
                LocalAudioPlayerManager.currentTrack.collect {
                    updateNotification()
                }
            }
        }
    }

    /**
     * অ্যান্ড্রয়েড সিস্টেম প্লেব্যাক স্টেট সিঙ্ক
     */
    private fun syncPlaybackState() {
        val isPlaying = LocalAudioPlayerManager.isPlaying.value
        val position = LocalAudioPlayerManager.currentPositionMs.value
        val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED

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
            .setState(state, position, if (isPlaying) 1.0f else 0f)

        mediaSession?.setPlaybackState(stateBuilder.build())
    }

    /**
     * 🔔 MX Player / Spotify স্টাইল নোটিফিকেশন তৈরি ও আপডেট
     */
    private fun updateNotification() {
        val track = LocalAudioPlayerManager.currentTrack.value ?: return
        val session = mediaSession ?: return
        val isPlaying = LocalAudioPlayerManager.isPlaying.value
        val coverBitmap = LocalAudioPlayerManager.getCoverBitmap()

        // ১. নোটিফিকেশন কার্ডে ট্যাপ করলে অ্যাপ রিস্টার্ট ছাড়া সরাসরি ওপেন হওয়া
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ২. ইন্টারঅ্যাক্টিভ বাটন পেন্ডিং ইনটেন্টস
        val prevPending = createActionIntent(ACTION_PREV, 1)
        val playPausePending = createActionIntent(ACTION_PLAY_PAUSE, 2)
        val nextPending = createActionIntent(ACTION_NEXT, 3)
        val stopPending = createActionIntent(ACTION_STOP, 4)

        // ৩. মিডিয়া মেটাডাটা সিঙ্ক (কভার আর্ট সহ)
        val metadataBuilder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, track.displayName.ifBlank { track.title })
            .putString(MediaMetadata.METADATA_KEY_ARTIST, track.folderName.ifBlank { "Local Audio" })
            .putString(MediaMetadata.METADATA_KEY_ALBUM, track.folderName.ifBlank { "Music Library" })
            .putLong(MediaMetadata.METADATA_KEY_DURATION, LocalAudioPlayerManager.durationMs.value)

        if (coverBitmap != null) {
            metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, coverBitmap)
        }
        session.setMetadata(metadataBuilder.build())

        // ৪. অ্যান্ড্রয়েড সিস্টেম MediaStyle লেআউট
        val mediaStyle = Notification.MediaStyle()
            .setMediaSession(session.sessionToken)
            .setShowActionsInCompactView(0, 1, 2) // Prev (0), Play/Pause (1), Next (2)

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val notification = builder
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(track.displayName.ifBlank { track.title })
            .setContentText(track.folderName.ifBlank { "Playing in Background" })
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(stopPending)
            .setOngoing(isPlaying)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setStyle(mediaStyle)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPending)
            .addAction(playPauseIcon, if (isPlaying) "Pause" else "Play", playPausePending)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close", stopPending)
            .apply {
                if (coverBitmap != null) {
                    setLargeIcon(coverBitmap)
                }
            }
            .build()

        // ৫. অ্যান্ড্রয়েড ১০+ এর জন্য foregroundServiceType সহ সার্ভিস ফোরগ্রাউন্ডে চালু করা
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createActionIntent(actionName: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, AudioPlaybackService::class.java).apply {
            action = actionName
            `package` = packageName
        }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Player Notification",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows interactive audio controls in notification panel and lockscreen"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun stopForegroundService() {
        observerJob?.cancel()
        mediaSession?.isActive = false
        mediaSession?.release()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        stopForegroundService()
    }
}
