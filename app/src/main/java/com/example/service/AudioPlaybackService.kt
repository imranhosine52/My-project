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
import com.example.R
import com.example.util.LocalAudioPlayerManager
import kotlinx.coroutines.*

/**
 * 🎵 AudioPlaybackService
 * সমস্ত অ্যান্ড্রয়েড ফোন (Xiaomi/HyperOS, Samsung OneUI, Pixel, Vivo, Oppo ইত্যাদি) এর
 * নোটিফিকেশন প্যানেল এবং লক স্ক্রিনে Spotify/MX Player স্টাইলের ইন্টারঅ্যাক্টিভ মিডিয়া প্লেয়ার।
 */
class AudioPlaybackService : Service() {

    companion object {
        // 🎯 চ্যানেল আইডি আপডেট করা হয়েছে যাতে পুরাতন ডিভাইসে নতুন ভিজিবিলিটি রুলস কার্যকর হয়
        const val CHANNEL_ID = "playdramaflix_audio_playback_v3"
        const val NOTIFICATION_ID = 2026

        // 🔘 নোটিফিকেশন অ্যাকশন কমান্ডস
        const val ACTION_PLAY = "com.example.audio.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.audio.ACTION_PAUSE"
        const val ACTION_PLAY_PAUSE = "com.example.audio.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.audio.ACTION_NEXT"
        const val ACTION_PREV = "com.example.audio.ACTION_PREV"
        const val ACTION_STOP = "com.example.audio.ACTION_STOP"

        fun start(context: Context) {
            val intent = Intent(context, AudioPlaybackService::class.java).apply {
                action = ACTION_PLAY
            }
            ContextCompat.startForegroundService(context, intent)
        }

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
        syncPlaybackState()
        updateNotification()
        return START_STICKY
    }

    /**
     * 🎛️ অ্যান্ড্রয়েড সিস্টেম মিডিয়া সেশন সেটআপ (হেডসেট বাটন, লক-স্ক্রিন ও সিস্টেম কারাউসেল)
     */
    private fun setupMediaSession() {
        mediaSession = MediaSession(this, "DramaFlixMediaSession").apply {
            isActive = true
            setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
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
                    syncPlaybackState()
                    updateNotification()
                }
            }
        }
    }

    /**
     * ⏱️ সিস্টেম প্লেব্যাক স্টেট সিঙ্ক (Android 11+ SystemUI কারাউসেলের জন্য বাধ্যতামূলক)
     */
    private fun syncPlaybackState() {
        val session = mediaSession ?: return
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

        session.setPlaybackState(stateBuilder.build())
    }

    /**
     * 🔔 সব মোবাইলের নোটিফিকেশন প্যানেল ও লক স্ক্রিনে প্লেয়ার বিল্ড ও ডিসপ্লে
     */
    private fun updateNotification() {
        val session = mediaSession ?: return
        val track = LocalAudioPlayerManager.currentTrack.value

        // ট্র‍্যাকের নাম ও শিল্পী
        val titleText = track?.displayName?.ifBlank { track.title } ?: "Playing Audio"
        val artistText = track?.folderName?.ifBlank { "Local Audio" } ?: "DramaFlix Music"
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

        // ৩. অ্যান্ড্রয়েড সিস্টেম মেটাডাটা সিঙ্ক (অ্যালবাম কভার আর্ট সহ)
        val metadataBuilder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, titleText)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, artistText)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, artistText)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, LocalAudioPlayerManager.durationMs.value)

        if (coverBitmap != null && !coverBitmap.isRecycled) {
            metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, coverBitmap)
            metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ART, coverBitmap)
        }
        session.setMetadata(metadataBuilder.build())

        // ৪. অ্যান্ড্রয়েড সিস্টেম MediaStyle লেআউট
        val mediaStyle = Notification.MediaStyle()
            .setMediaSession(session.sessionToken)
            .setShowActionsInCompactView(0, 1, 2) // Previous (0), Play/Pause (1), Next (2) সবসময় দৃশ্যমান

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseTitle = if (isPlaying) "Pause" else "Play"

        val safeSmallIcon = try {
            val iconRes = applicationInfo.icon
            if (iconRes != 0) iconRes else R.mipmap.ic_launcher
        } catch (_: Exception) {
            R.mipmap.ic_launcher
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val notification = builder
            .setSmallIcon(safeSmallIcon)
            .setContentTitle(titleText)
            .setContentText(artistText)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(stopPending)
            // 🎯 CATEGORY_TRANSPORT হলো আসল চাবিকাঠি যা নোটিফিকেশন ড্রয়ারে শো করায়
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .setStyle(mediaStyle)
            // 🔘 বাটনসমূহ: Previous | Play/Pause | Next | Close
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPending)
            .addAction(playPauseIcon, playPauseTitle, playPausePending)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close", stopPending)
            .apply {
                if (coverBitmap != null && !coverBitmap.isRecycled) {
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

        // ৬. নোটিফিকেশন ড্রয়ারে তাৎক্ষণিক আপডেট নিশ্চিত করা
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
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
                "Audio Player Controls",
                // 🎯 IMPORTANCE_LOW কিন্তু সাউন্ডহীন ও নোটিফিকেশন ড্রয়ারে দৃশ্যমান
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows interactive audio controls in notification panel and lock screen"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
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
