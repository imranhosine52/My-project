package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

object VipStatusNotificationHelper {
    private const val TAG = "VipNotifHelper"
    private const val PREFS_NAME = "vip_notification_guard_prefs"
    private const val CHANNEL_ID = "vip_status_updates_channel"
    private const val CHANNEL_NAME = "VIP Subscription Alerts"

    private const val NOTIF_ID_APPROVED = 9992
    private const val NOTIF_ID_REJECTED = 9993

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when VIP subscription is approved or rejected."
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 150, 300)
                setSound(soundUri, audioAttributes)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 👑 ১. VIP অ্যাক্টিভেশন নোটিফিকেশন (🎯 Once-Only Guard সহ - জীবনে একবারই আসবে)
     */
    fun showVipApprovedNotification(
        context: Context,
        planName: String = "VIP Pass",
        activationKey: String = ""
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val guardKey = "notified_approved_" + activationKey.ifBlank { planName }

        // 🚫 গার্ড ১: এই প্ল্যান বা ট্রানজেকশনের জন্য ইতিমধ্যে নোটিফিকেশন দেওয়া হয়ে থাকলে সাথে সাথে ব্লক
        if (prefs.getBoolean(guardKey, false)) {
            Log.d(TAG, "🔇 Suppressed duplicate VIP Approved notification for key: $guardKey")
            return
        }

        // 🚫 গার্ড ২: গত ২৪ ঘণ্টার মধ্যে অলরেডি কোনো অ্যাক্টিভেশন নোটিফিকেশন পেয়ে থাকলে ব্লক
        val lastNotifiedTime = prefs.getLong("last_approved_timestamp", 0L)
        val now = System.currentTimeMillis()
        if (now - lastNotifiedTime < 24 * 60 * 60 * 1000L && activationKey.isBlank()) {
            Log.d(TAG, "🔇 Suppressed recurring VIP Approved notification.")
            return
        }

        // সেভ করে রাখা যাতে আর দ্বিতীয়বার না আসে
        prefs.edit()
            .putBoolean(guardKey, true)
            .putLong("last_approved_timestamp", now)
            .apply()

        try {
            ensureNotificationChannel(context)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("EXTRA_OPEN_VIP", true)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                NOTIF_ID_APPROVED,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("👑 VIP Pass Activated!")
                .setContentText("Congratulations! Your $planName is now active. Enjoy ad-free 1080p streaming!")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("🎉 Congratulations! Your $planName payment has been verified and approved. Enjoy 100% ad-free 1080p Full HD streaming and unlimited downloads!")
                )
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(true)
                .setSound(soundUri)
                .setVibrate(longArrayOf(0, 300, 150, 300))
                .setContentIntent(pendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()

            notificationManager.notify(NOTIF_ID_APPROVED, notification)
            Log.d(TAG, "✓ VIP Approved Notification posted successfully (One-time only).")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show approved notification: ${e.message}")
        }
    }

    /**
     * ⚠️ ২. VIP রিজেকশন নোটিফিকেশন (🎯 Once-Only Guard সহ)
     */
    fun showVipRejectedNotification(
        context: Context,
        reason: String = "Invalid TrxID or insufficient amount.",
        trxId: String = ""
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val guardKey = "notified_rejected_" + trxId.ifBlank { "last_rejection" }

        if (prefs.getBoolean(guardKey, false)) {
            Log.d(TAG, "🔇 Suppressed duplicate VIP Rejected notification.")
            return
        }

        prefs.edit().putBoolean(guardKey, true).apply()

        try {
            ensureNotificationChannel(context)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("EXTRA_OPEN_VIP", true)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                NOTIF_ID_REJECTED,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("⚠️ VIP Payment Verification Failed")
                .setContentText("Payment was rejected: $reason")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("⚠️ Your VIP payment verification was rejected ($reason). Tap here to review your submission or retry with correct details.")
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setSound(soundUri)
                .setVibrate(longArrayOf(0, 250, 100, 250))
                .setContentIntent(pendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()

            notificationManager.notify(NOTIF_ID_REJECTED, notification)
            Log.d(TAG, "✓ VIP Rejected Notification posted successfully (One-time only).")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show rejected notification: ${e.message}")
        }
    }

    /**
     * 🔄 লগআউট করলে গার্ড মেমোরি রিসেট করা
     */
    fun resetGuard(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
