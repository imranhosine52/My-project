package com.example.data.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * 🛡️ ReelInteractionGuard
 * ১. ২৪ ঘণ্টার মধ্যে ১টি রিলের জন্য মাত্র ১টি ভিউ লোকাল মেমোরিতে জমা রাখে।
 * ২. ২৪ ঘণ্টায় মাত্র ২ বার (প্রতি ১২ ঘণ্টা পর পর) সমস্ত জমা হওয়া ভিউ সার্ভারে পাঠানোর সময় নির্ধারণ করে।
 * ৩. লাইক ও ফলোর ডাবল/ফেইক কাউন্ট গাণিতিকভাবে প্রতিরোধ করে।
 */
class ReelInteractionGuard(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val TAG = "ReelInteractionGuard"
        private const val PREFS_NAME = "drama_flix_reel_interaction_guard_prefs"

        // ২৪ ঘণ্টা কুলডাউন (একই রিল ২৪ ঘণ্টায় ১ বার কাউন্ট হবে)
        const val VIEW_COOLDOWN_MS = 24 * 60 * 60 * 1000L // 86,400,000 ms

        // ২৪ ঘণ্টায় ২ বার সিঙ্ক (প্রতি ১২ ঘণ্টা পর পর সার্ভারে ব্যাচ রিকোয়েস্ট যাবে)
        const val BATCH_SYNC_INTERVAL_MS = 12 * 60 * 60 * 1000L // 43,200,000 ms

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
    // 👁️ ১. ২৪ ঘণ্টায় মাত্র ১টি ভিউ লোকাল স্টোরেজে কাউন্ট করার লজিক
    // =========================================================================

    /**
     * চেক করে রিলটি গত ২৪ ঘণ্টার মধ্যে দেখা হয়েছে কিনা।
     * যদি না দেখা হয়ে থাকে, তবে লোকাল পেন্ডিং কিউতে (Queue) জমা করে এবং true দেয়।
     */
    @Synchronized
    fun recordLocalViewIfEligible(reelId: Int, userId: Int): Boolean {
        val userReelKey = "view_time_${userId}_$reelId"
        val lastViewTime = prefs.getLong(userReelKey, 0L)
        val currentTime = System.currentTimeMillis()

        // ২৪ ঘণ্টার মধ্যে আগে দেখা হলে কাউন্ট হবে না
        if (lastViewTime > 0L && (currentTime - lastViewTime) < VIEW_COOLDOWN_MS) {
            return false
        }

        // ২৪ ঘণ্টা পর নতুন ভিউ: টাইমস্ট্যাম্প আপডেট করা হলো
        val pendingSet = getPendingViewReelIds().toMutableSet()
        pendingSet.add(reelId.toString())

        prefs.edit()
            .putLong(userReelKey, currentTime)
            .putStringSet(KEY_PENDING_VIEW_REEL_IDS, pendingSet)
            .apply()

        Log.d(TAG, "✓ Reel #$reelId counted locally. Total pending views in phone: ${pendingSet.size}")
        return true
    }

    // =========================================================================
    // ⏰ ২. ১২ ঘণ্টার ব্যাচ সিঙ্ক চেকার (২৪ ঘণ্টায় ২ বার)
    // =========================================================================

    /**
     * চেক করে গত সিঙ্কের পর ১২ ঘণ্টা পার হয়েছে কিনা এবং লোকাল ফোনে কোনো ভিউ জমা আছে কিনা।
     */
    @Synchronized
    fun isBatchSyncDue(): Boolean {
        val pendingViews = getPendingViewReelIds()
        if (pendingViews.isEmpty()) return false

        val lastSyncTime = prefs.getLong(KEY_LAST_SERVER_SYNC, 0L)
        val currentTime = System.currentTimeMillis()

        // প্রথমবারের জন্য অথবা ১২ ঘণ্টা পার হয়ে থাকলে true হবে
        return (currentTime - lastSyncTime) >= BATCH_SYNC_INTERVAL_MS
    }

    /**
     * বর্তমানে ফোনে যে ভিউগুলো সার্ভারে পাঠানোর অপেক্ষায় জমা আছে তাদের আইডি লিস্ট
     */
    fun getPendingViewReelIds(): Set<String> {
        return prefs.getStringSet(KEY_PENDING_VIEW_REEL_IDS, emptySet()) ?: emptySet()
    }

    /**
     * সার্ভারে সফলভাবে ডাটা পাঠানো সম্পন্ন হলে পেন্ডিং কিউ ক্লিয়ার করা ও নতুন টাইম সেভ করা
     */
    @Synchronized
    fun markSyncCompleted(syncedReelIds: Collection<String>) {
        val currentPending = getPendingViewReelIds().toMutableSet()
        currentPending.removeAll(syncedReelIds.toSet())

        prefs.edit()
            .putStringSet(KEY_PENDING_VIEW_REEL_IDS, currentPending)
            .putLong(KEY_LAST_SERVER_SYNC, System.currentTimeMillis())
            .apply()

        Log.i(TAG, "✓ Batch sync finished. Cleared ${syncedReelIds.size} views. Remaining: ${currentPending.size}")
    }

    // =========================================================================
    // 🛡️ ৩. লাইক ও ফলো ডাবল কাউন্ট রোধের লোকাল হেল্পার
    // =========================================================================

    fun isCreatorFollowedLocally(pageId: Long, userId: Int): Boolean {
        val key = if (pageId > 0L) "followed_page_$pageId" else "followed_user_$userId"
        return prefs.getBoolean(key, false)
    }

    fun setCreatorFollowedLocally(pageId: Long, userId: Int, isFollowing: Boolean) {
        val key = if (pageId > 0L) "followed_page_$pageId" else "followed_user_$userId"
        prefs.edit().putBoolean(key, isFollowing).apply()
    }
}
