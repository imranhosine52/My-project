package com.example.data.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * 🛡️ ReelInteractionGuard
 * -------------------------------------------------------------
 * ১. ২৪ ঘণ্টার কঠোর ভিউ গার্ড: একই রিলস ২৪ ঘণ্টার মধ্যে ১ বারের বেশি কাউন্ট হবে না।
 * ২. ডিভাইস ও ইউজার লেভেল ডাবল চেকার (গেস্ট বা লগইন যেকোনো অবস্থায় কাজ করবে)।
 * ৩. ২৪ ঘণ্টা পার হলে স্বয়ংক্রিয়ভাবে রিসেট হয়ে আবার নতুন ভিউ কাউন্ট হওয়ার সুযোগ পাবে।
 * ৪. ব্যাকগ্রাউন্ড সার্ভার সিঙ্ক কিউ এবং ফলো-স্টেট লোকাল ক্যাশিং হ্যান্ডলার।
 */
class ReelInteractionGuard private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val TAG = "ReelInteractionGuard"
        private const val PREFS_NAME = "drama_flix_reel_interaction_guard_prefs"

        // ⏱️ ২৪ ঘণ্টা কুলডাউন (১ দিন = ৮৬,৪০০,০০০ মিলিসেকেন্ড)
        const val VIEW_COOLDOWN_MS = 24 * 60 * 60 * 1000L

        // ⏱️ ১২ ঘণ্টার ফলব্যাক ব্যাচ সিঙ্ক ইন্টারভাল
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
    // 👁️ ১. ২৪ ঘণ্টার ভিউ গার্ড লজিক (Device & User Level Check)
    // =========================================================================

    /**
     * চেক করে রিলটি গত ২৪ ঘণ্টার মধ্যে এই ডিভাইস বা ইউজার অ্যাকাউন্ট থেকে দেখা হয়েছে কিনা।
     * @return true হলে নতুন ভিউ কাউন্ট করা যাবে, false হলে ২৪ ঘণ্টার মধ্যে ইতিমধ্যে দেখা হয়েছে।
     */
    fun isViewEligible(reelId: Int, userId: Int = 0): Boolean {
        val currentTime = System.currentTimeMillis()

        // ক) ডিভাইস লেভেল লক চেক (গেস্ট হলেও কাজ করবে)
        val deviceLockKey = "view_lock_device_reel_$reelId"
        val lastDeviceViewTime = prefs.getLong(deviceLockKey, 0L)
        if (lastDeviceViewTime > 0L && (currentTime - lastDeviceViewTime) < VIEW_COOLDOWN_MS) {
            return false
        }

        // খ) ইউজার লেভেল লক চেক (লগইন করা থাকলে)
        if (userId > 0) {
            val userLockKey = "view_lock_user_${userId}_reel_$reelId"
            val lastUserViewTime = prefs.getLong(userLockKey, 0L)
            if (lastUserViewTime > 0L && (currentTime - lastUserViewTime) < VIEW_COOLDOWN_MS) {
                return false
            }
        }

        return true
    }

    /**
     * রিলস দেখার পর ভিউ সেভ করে।
     * ২৪ ঘণ্টার মধ্যে আগে দেখা না হয়ে থাকলে লোকাল স্টোরেজে লক করে true রিটার্ন করে।
     * ২৪ ঘণ্টার মধ্যে আগে দেখা হয়ে থাকলে কোনো অ্যাকশন না নিয়ে সরাসরি false রিটার্ন করে।
     */
    @Synchronized
    fun recordLocalViewIfEligible(reelId: Int, userId: Int = 0): Boolean {
        // ২৪ ঘণ্টার চেক
        if (!isViewEligible(reelId, userId)) {
            Log.d(TAG, "⛔ [View Guard] Reel #$reelId is already counted within 24 hours. Skipped.")
            return false
        }

        val currentTime = System.currentTimeMillis()
        val deviceLockKey = "view_lock_device_reel_$reelId"

        // পেন্ডিং কিউতে যোগ করা (যাতে সার্ভারে পাঠানো যায়)
        val pendingSet = getPendingViewReelIds().toMutableSet()
        pendingSet.add(reelId.toString())

        val editor = prefs.edit()
            .putLong(deviceLockKey, currentTime)
            .putStringSet(KEY_PENDING_VIEW_REEL_IDS, pendingSet)

        if (userId > 0) {
            val userLockKey = "view_lock_user_${userId}_reel_$reelId"
            editor.putLong(userLockKey, currentTime)
        }

        editor.apply()

        Log.i(TAG, "✓ [View Guard] Reel #$reelId: 1 View counted! Locked for next 24 hours.")
        return true
    }

    // =========================================================================
    // ⏰ ২. ব্যাচ সিঙ্ক ও পেন্ডিং কিউ হ্যান্ডলার
    // =========================================================================

    /**
     * সার্ভারে পাঠানোর অপেক্ষায় থাকা পেন্ডিং ভিউ আইডিগুলোর তালিকা
     */
    fun getPendingViewReelIds(): Set<String> {
        return prefs.getStringSet(KEY_PENDING_VIEW_REEL_IDS, emptySet()) ?: emptySet()
    }

    /**
     * ১২ ঘণ্টা পার হয়েছে কিনা এবং কোনো পেন্ডিং ভিউ জমা আছে কিনা যাচাই করে
     */
    @Synchronized
    fun isBatchSyncDue(): Boolean {
        val pendingViews = getPendingViewReelIds()
        if (pendingViews.isEmpty()) return false

        val lastSyncTime = prefs.getLong(KEY_LAST_SERVER_SYNC, 0L)
        val currentTime = System.currentTimeMillis()

        return (currentTime - lastSyncTime) >= BATCH_SYNC_INTERVAL_MS
    }

    /**
     * সার্ভারে সফলভাবে পাঠানো সম্পন্ন হলে কিউ থেকে আইডিগুলো ক্লিয়ার করা
     */
    @Synchronized
    fun markSyncCompleted(syncedReelIds: Collection<String>) {
        val currentPending = getPendingViewReelIds().toMutableSet()
        currentPending.removeAll(syncedReelIds.toSet())

        prefs.edit()
            .putStringSet(KEY_PENDING_VIEW_REEL_IDS, currentPending)
            .putLong(KEY_LAST_SERVER_SYNC, System.currentTimeMillis())
            .apply()

        Log.i(TAG, "✓ [Sync] Cleared ${syncedReelIds.size} views from pending queue. Remaining: ${currentPending.size}")
    }

    // =========================================================================
    // 🛡️ ৩. ফলো ও লাইক লোকাল ক্যাশ হেল্পার
    // =========================================================================

    fun isCreatorFollowedLocally(pageId: Long, userId: Int): Boolean {
        val key = if (pageId > 0L) "followed_page_$pageId" else "followed_user_$userId"
        return prefs.getBoolean(key, false)
    }

    fun setCreatorFollowedLocally(pageId: Long, userId: Int, isFollowing: Boolean) {
        val key = if (pageId > 0L) "followed_page_$pageId" else "followed_user_$userId"
        prefs.edit().putBoolean(key, isFollowing).apply()
    }

    /**
     * টেস্ট বা ডিবাগিংয়ের জন্য ক্যাশ ক্লিয়ার মেথড
     */
    fun clearAllViewLocks() {
        prefs.edit().clear().apply()
        Log.i(TAG, "✓ All view cooldown locks have been cleared.")
    }
}
