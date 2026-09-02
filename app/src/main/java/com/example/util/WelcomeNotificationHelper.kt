package com.example.util

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

/**
 * 🔔 WelcomeNotificationHelper
 * নোটিফিকেশন পারমিশন এনাবল হলে স্বয়ংক্রিয়ভাবে ব্যাকগ্রাউন্ডে ওয়েলকাম অ্যালার্ট পাঠায়।
 */
object WelcomeNotificationHelper {
    private const val PREFS_NAME = "welcome_notification_prefs"
    private const val KEY_SHOWN = "has_shown_welcome_notif"
    private const val NOTIFICATION_ID = 9991

    fun sendWelcomeNotification(context: Context, force: Boolean = false) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!force && prefs.getBoolean(KEY_SHOWN, false)) {
            return
        }

        val channelId = "high_importance_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        // নোটিফিকেশনে ট্যাপ করলে অ্যাপ ওপেন হবে
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🎉 স্বাগতম PlayDramaFlix-এ!")
            .setContentText("আপনার নোটিফিকেশন চালু হয়েছে। নতুন ড্রামা ও এপিসোডের আপডেট সবার আগে পাবেন!")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("🎬 স্বাগতম PlayDramaFlix পরিবারে! নোটিফিকেশন সফলভাবে চালু হয়েছে। নতুন কে-ড্রামা, অ্যানিমে ও বাংলা/হিন্দি ডাবিং এপিসোড রিলিজের সাথে সাথে আপনি অ্যালার্ট পেয়ে যাবেন। এখনই দেখতে শুরু করুন!")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
        prefs.edit().putBoolean(KEY_SHOWN, true).apply()
    }
}
