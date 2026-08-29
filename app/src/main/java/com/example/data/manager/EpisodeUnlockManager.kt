package com.example.data.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * EpisodeUnlockManager
 * Manages rewarded unlock state for episodes with a 2-hour expiration window using SharedPreferences.
 * Rules:
 * 1. Episode 1 is ALWAYS free for all users.
 * 2. VIP users (`is_vip == true`) have all episodes permanently unlocked.
 * 3. Free users unlock Episodes 2+ for 2 Hours upon completing a Start.io Rewarded Video Ad.
 * 4. During the 2-hour window, re-watching does not require additional ads.
 */
class EpisodeUnlockManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "EpisodeUnlockManager"
        private const val PREFS_NAME = "drama_flix_unlocked_episodes_prefs"
        private const val KEY_PREFIX_EXPIRY = "unlock_expiry_"
        
        // 2 Hours in milliseconds: 2 * 60 * 60 * 1000L = 7,200,000 ms
        const val UNLOCK_DURATION_MS: Long = 2 * 60 * 60 * 1000L

        @Volatile
        private var INSTANCE: EpisodeUnlockManager? = null

        fun getInstance(context: Context): EpisodeUnlockManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: EpisodeUnlockManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private fun getStorageKey(dramaSlug: String, episodeNumber: Int): String {
        return "$KEY_PREFIX_EXPIRY${dramaSlug}_ep$episodeNumber"
    }

    /**
     * Checks if a specific episode is currently unlocked.
     *
     * @param dramaSlug The slug identifier of the drama/series
     * @param episodeNumber The episode number
     * @param isVip Whether the current user has active VIP status
     * @return True if episode 1, if user is VIP, or if unlocked within the 2-hour window
     */
    fun isEpisodeUnlocked(dramaSlug: String, episodeNumber: Int, isVip: Boolean): Boolean {
        // 1. Episode 1 is always free
        if (episodeNumber <= 1) {
            return true
        }

        // 2. VIP users have permanent full access
        if (isVip) {
            return true
        }

        // 3. Check SharedPreferences for temporary 2-hour unlock expiry
        val key = getStorageKey(dramaSlug, episodeNumber)
        val expiryTime = prefs.getLong(key, 0L)
        val currentTime = System.currentTimeMillis()

        if (expiryTime > currentTime) {
            Log.d(TAG, "Episode $episodeNumber of '$dramaSlug' is unlocked until timestamp $expiryTime (in ${(expiryTime - currentTime) / 60000} mins)")
            return true
        } else if (expiryTime != 0L) {
            // Clean expired key
            prefs.edit().remove(key).apply()
        }

        return false
    }

    /**
     * Unlocks an episode for configurable hours (default 2 Hours) after successful Rewarded Ad completion.
     *
     * @param dramaSlug The drama slug identifier
     * @param episodeNumber The episode number to unlock
     * @param hours Number of hours to unlock (defaults to 2 hours)
     * @return The expiry timestamp in milliseconds
     */
    fun unlockEpisodeForDuration(dramaSlug: String, episodeNumber: Int, hours: Int = 2): Long {
        val durationMs = if (hours > 0) hours.toLong() * 60 * 60 * 1000L else UNLOCK_DURATION_MS
        val expiryTime = System.currentTimeMillis() + durationMs
        val key = getStorageKey(dramaSlug, episodeNumber)
        prefs.edit().putLong(key, expiryTime).apply()
        Log.i(TAG, "✓ Episode $episodeNumber for '$dramaSlug' unlocked for $hours Hours (Expiry: $expiryTime)")
        return expiryTime
    }

    fun unlockEpisodeFor2Hours(dramaSlug: String, episodeNumber: Int): Long {
        return unlockEpisodeForDuration(dramaSlug, episodeNumber, 2)
    }

    /**
     * Gets the remaining unlock time in milliseconds, or 0 if locked/expired.
     */
    fun getRemainingUnlockTimeMs(dramaSlug: String, episodeNumber: Int): Long {
        val key = getStorageKey(dramaSlug, episodeNumber)
        val expiryTime = prefs.getLong(key, 0L)
        val diff = expiryTime - System.currentTimeMillis()
        return if (diff > 0) diff else 0L
    }

    /**
     * Clear all unlocked episode states (e.g. on logout/debug)
     */
    fun clearAllUnlocks() {
        prefs.edit().clear().apply()
    }
}
