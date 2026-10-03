package com.example.data.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * 🛡️ ReelInteractionGuard
 * ২৪ ঘণ্টার মধ্যে ১টি ডিভাইসে ১টি রিলসের জন্য সর্বোচ্চ ১টি ভিউ লক করে।
 */
class ReelInteractionGuard private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val TAG = "ReelInteractionGuard"
        private const val PREFS_NAME = "drama_flix_reel_interaction_guard_prefs"

        // ⏱️ ২৪ ঘণ্টা কুলডাউন (৮৬,৪০০,০০০ মিলিসেকেন্ড)
        const val VIEW_COOLDOWN_MS = 24 * 60 * 60 * 1000L
        const val BATCH_SYNC_INTERVAL_MS = 12 * 60 * 60 * 1000L

        private const val KEY_LAST_SERVER_SYNC = "key_last_server_sync_timestamp"
        private const val KEY_PENDING_VIEW_REEL_IDS = "key_pending_view_reel_ids"

        @Volatile
        private var INSTANCE: ReelInteractionGuard? = null

        fun getInstance(context: Context): ReelInteractionGuard {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ReelInteractionGuard(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // =========================================================================
    // 👁️ ২৪ ঘণ্টার ভিউ গার্ড চেকার (Device & User Synchronized Lock)
    // =========================================================================

    fun isViewEligible(reelId: Int, userId: Int = 0): Boolean {
        if (reelId <= 0) return false
        val currentTime = System.currentTimeMillis()

        // ডিভাইস লেভেল চেক
        val deviceLockKey = "view_lock_device_reel_$reelId"
        val lastDeviceViewTime = prefs.getLong(deviceLockKey, 0L)
        if (lastDeviceViewTime > 0L && (currentTime - lastDeviceViewTime) < VIEW_COOLDOWN_MS) {
            return false
        }

        // ইউজার লেভেল চেক
        if (userId > 0) {
            val userLockKey = "view_lock_user_${userId}_reel_$reelId"
            val lastUserViewTime = prefs.getLong(userLockKey, 0L)
            if (lastUserViewTime > 0L && (currentTime - lastUserViewTime) < VIEW_COOLDOWN_MS) {
                return false
            }
        }

        return true
    }

    @Synchronized
    fun recordLocalViewIfEligible(reelId: Int, userId: Int = 0): Boolean {
        if (!isViewEligible(reelId, userId)) {
            Log.d(TAG, "⛔ [24h Locked] Reel #$reelId view already counted today. Skipped.")
            return false
        }

        val currentTime = System.currentTimeMillis()
        val deviceLockKey = "view_lock_device_reel_$reelId"

        val pendingSet = getPendingViewReelIds().toMutableSet()
        pendingSet.add(reelId.toString())

        val editor = prefs.edit()
            .putLong(deviceLockKey, currentTime)
            .putStringSet(KEY_PENDING_VIEW_REEL_IDS, pendingSet)

        if (userId > 0) {
            editor.putLong("view_lock_user_${userId}_reel_$reelId", currentTime)
        }

        // 🎯 .commit() ব্যবহার করে তাৎক্ষণিক স্টোরেজে লিখে দেওয়া হলো
        editor.commit()

        Log.i(TAG, "✓ [24h Saved] Reel #$reelId counted! Locked for next 24 hours.")
        return true
    }

    fun getPendingViewReelIds(): Set<String> {
        return prefs.getStringSet(KEY_PENDING_VIEW_REEL_IDS, emptySet()) ?: emptySet()
    }

    @Synchronized
    fun isBatchSyncDue(): Boolean {
        val pendingViews = getPendingViewReelIds()
        if (pendingViews.isEmpty()) return false
        val lastSyncTime = prefs.getLong(KEY_LAST_SERVER_SYNC, 0L)
        return (System.currentTimeMillis() - lastSyncTime) >= BATCH_SYNC_INTERVAL_MS
    }

    @Synchronized
    fun markSyncCompleted(syncedReelIds: Collection<String>) {
        val currentPending = getPendingViewReelIds().toMutableSet()
        currentPending.removeAll(syncedReelIds.toSet())

        prefs.edit()
            .putStringSet(KEY_PENDING_VIEW_REEL_IDS, currentPending)
            .putLong(KEY_LAST_SERVER_SYNC, System.currentTimeMillis())
            .commit()
    }

    fun isCreatorFollowedLocally(pageId: Long, userId: Int): Boolean {
        val key = if (pageId > 0L) "followed_page_$pageId" else "followed_user_$userId"
        return prefs.getBoolean(key, false)
    }

    fun setCreatorFollowedLocally(pageId: Long, userId: Int, isFollowing: Boolean) {
        val key = if (pageId > 0L) "followed_page_$pageId" else "followed_user_$userId"
        prefs.edit().putBoolean(key, isFollowing).apply()
    }
}
