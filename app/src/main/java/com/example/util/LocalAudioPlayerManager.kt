@file:kotlin.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.model.LocalVideoItem
import com.example.service.AudioPlaybackService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * 🎵 LocalAudioPlayerManager
 * লোকাল অডিও প্লেব্যাকের সেন্ট্রাল কন্ট্রোলার ও ব্যাকগ্রাউন্ড সার্ভিস সিঙ্ক ইঞ্জিন।
 */
object LocalAudioPlayerManager {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var exoPlayer: ExoPlayer? = null
    private var appContext: Context? = null

    // 🎵 প্লেব্যাক স্টেটসমূহ (UI ও সার্ভিসের জন্য থ্রেড-সেফ StateFlow)
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
    private var cachedCoverBitmap: Bitmap? = null

    /**
     * নোটিফিকেশন সার্ভিস ও UI-এর জন্য কভার আর্ট রিটার্ন করে
     */
    fun getCoverBitmap(): Bitmap? = cachedCoverBitmap

    /**
     * ভিজ্যুয়ালাইজার বা ইকুয়ালাইজারের জন্য ExoPlayer ইনস্ট্যান্স
     */
    fun getPlayer(): ExoPlayer? = exoPlayer

    /**
     * অডিও প্লেয়ার ইঞ্জিন ইনিশিয়ালাইজেশন
     */
    fun init(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app

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
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        _durationMs.value = duration.coerceAtLeast(0L)
                        _audioSessionId.value = audioSessionId
                    } else if (state == Player.STATE_ENDED) {
                        playNext()
                    }
                }
            })
        }
    }

    /**
     * 🎶 অডিও ট্র্যাক চালানো ও সাথে সাথে নোটিফিকেশন সার্ভিস সক্রিয় করা
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

        // 🚀 ১. কভার আর্টের জন্য অপেক্ষা না করে সাথে সাথে সার্ভিস স্টার্ট (ইনস্ট্যান্ট নোটিফিকেশন)
        AudioPlaybackService.start(context)

        // 🌟 ২. ব্যাকগ্রাউন্ড থ্রেডে কভার আর্ট লোড এবং নোটিফিকেশন আপডেট
        scope.launch(Dispatchers.IO) {
            val art = extractAlbumArt(context, track)
            cachedCoverBitmap = art
            // ইমেজ ডিকোড হয়ে গেলে নোটিফিকেশনে কভার আর্ট সিঙ্ক করা
            withContext(Dispatchers.Main) {
                AudioPlaybackService.start(context)
            }
        }
    }

    /**
     * অডিও ফাইল থেকে অ্যালবাম আর্ট বা থাম্বনেইল উদ্ধার করে (Memory Safe)
     */
    private fun extractAlbumArt(context: Context, track: LocalVideoItem): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            if (track.path.isNotBlank() && File(track.path).exists()) {
                retriever.setDataSource(track.path)
            } else {
                retriever.setDataSource(context, Uri.parse(track.contentUriString))
            }
            val rawBytes = retriever.embeddedPicture
            if (rawBytes != null && rawBytes.isNotEmpty()) {
                val opts = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, opts)

                // মেমোরি সুরক্ষার জন্য বড় ইমেজকে ২৫৬x২৫৬ সাইজে ডাউনস্কেল করা
                var sampleSize = 1
                while (opts.outWidth / sampleSize > 256 || opts.outHeight / sampleSize > 256) {
                    sampleSize *= 2
                }

                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }
                BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
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
     * 🛑 গান বন্ধ করা, স্টেট রিসেট করা এবং নোটিফিকেশন সরানো
     */
    fun stopAndDismiss() {
        exoPlayer?.stop()
        _isPlaying.value = false
        _isMiniPlayerVisible.value = false
        cachedCoverBitmap = null
        stopPositionTracker()
        appContext?.let { AudioPlaybackService.stop(it) }
    }

    private fun startPositionTracker() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    _currentPositionMs.value = player.currentPosition.coerceAtLeast(0L)
                    _durationMs.value = player.duration.coerceAtLeast(0L)
                }
                delay(350L)
            }
        }
    }

    private fun stopPositionTracker() {
        positionJob?.cancel()
        positionJob = null
    }
}
