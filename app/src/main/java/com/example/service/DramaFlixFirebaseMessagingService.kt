package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.MainActivity
import com.example.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DramaFlixFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM_TOKEN", "New device token generated: $token")
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("FCM_MSG", "Message received from: ${remoteMessage.from}")

        val data = remoteMessage.data
        val notifType = data["type"] ?: "general"
        
        val title = remoteMessage.notification?.title 
            ?: data["title"] 
            ?: if (notifType == "app_update") "🚀 New App Update Available!" else "New Drama Added!"
            
        val body = remoteMessage.notification?.body 
            ?: data["message"] 
            ?: data["body"] 
            ?: if (notifType == "app_update") "A new version of PlayDramaFlix is available. Update now to continue watching!" else "Check out the latest release on PlayDramaFlix!"
            
        val posterUrl = remoteMessage.notification?.imageUrl?.toString() 
            ?: data["poster_url"] 
            ?: data["image"] 
            ?: data["thumbnail"]
            
        val slug = data["slug"] ?: data["content_slug"] ?: data["post_slug"] ?: data["url"]

        CoroutineScope(Dispatchers.IO).launch {
            showNotification(title, body, posterUrl, slug, notifType)
        }
    }

    private suspend fun showNotification(
        title: String,
        body: String,
        posterUrl: String?,
        slug: String?,
        notifType: String
    ) {
        val channelId = "high_importance_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 📢 অ্যান্ড্রয়েড ৮.০+ নোটিফিকেশন চ্যানেল তৈরি
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "New Post & App Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for newly added drama series, movies, and app updates."
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // 🚀 নোটিফিকেশনে ট্যাপ করলে অ্যাকশন হ্যান্ডলিং
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (notifType == "app_update") {
                // 👈 অ্যাপ আপডেট হলে সরাসরি আপডেট পপ-আপ চালু করার ইন্টেন্ট
                action = "OPEN_APP_UPDATE"
                putExtra("EXTRA_OPEN_UPDATE_DIALOG", true)
                putExtra("type", "app_update")
                putExtra("click_action", "OPEN_APP_UPDATE")
            } else {
                // 🎬 ড্রামা নোটিফিকেশন হলে নির্দিষ্ট ড্রামা প্লেয়ার ওপেন হবে
                putExtra("EXTRA_NOTIFICATION_SLUG", slug)
                putExtra("EXTRA_NOTIFICATION_TITLE", title)
                putExtra("EXTRA_NOTIFICATION_POSTER", posterUrl)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 🖼️ পোস্টার ইমেজ বিটম্যাপ লোডার
        var largeBitmap: Bitmap? = null
        if (!posterUrl.isNullOrBlank()) {
            try {
                val loader = ImageLoader(this)
                val request = ImageRequest.Builder(this)
                    .data(posterUrl)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    largeBitmap = (result.drawable as? BitmapDrawable)?.bitmap
                }
            } catch (e: Exception) {
                Log.e("FCM_IMG", "Failed to load poster bitmap for notification", e)
            }
        }

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        if (largeBitmap != null) {
            builder.setLargeIcon(largeBitmap)
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(largeBitmap)
                    .setBigContentTitle(title)
                    .setSummaryText(body)
            )
        } else {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(body)
            )
        }

        notificationManager.notify((System.currentTimeMillis() % 100000).toInt(), builder.build())
    }
}
