package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.DramaFlixApplication
import com.example.MainActivity
import com.example.R
import com.example.data.repository.PlayDramaFlixRepository
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

class DramaFlixFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM_TOKEN", "New FCM token generated: $token")
        
        try {
            val repository = (application as? DramaFlixApplication)?.repository 
                ?: PlayDramaFlixRepository(applicationContext)
                
            CoroutineScope(Dispatchers.IO).launch {
                repository.registerDevice(token)
            }
            
            val topics = listOf("all_users", "all", "general", "dramaflix", "new_posts")
            for (topic in topics) {
                FirebaseMessaging.getInstance().subscribeToTopic(topic)
            }
        } catch (e: Exception) {
            Log.e("FCM_TOKEN", "Failed to register new token: ${e.message}")
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("FCM_MSG", "✓ FCM Message received from: ${remoteMessage.from}")

        val data = remoteMessage.data
        val notifType = data["type"] ?: "general"

        // ১. টাইটেল এক্সট্রাক্ট করা
        val title = remoteMessage.notification?.title
            ?: data["title"]
            ?: data["heading"]
            ?: if (notifType == "app_update") "🚀 New App Update Available!" else "New Drama Added!"

        // ২. মেসেজ বডি এক্সট্রাক্ট করা
        val body = remoteMessage.notification?.body
            ?: data["message"]
            ?: data["body"]
            ?: data["description"]
            ?: if (notifType == "app_update") "A new version of PlayDramaFlix is available. Update now to continue watching!" else "Check out the latest release on PlayDramaFlix!"

        // ৩. ইমেজ ইউআরএল এক্সট্রাক্ট করা
        val posterUrl = remoteMessage.notification?.imageUrl?.toString()
            ?: data["poster_url"]
            ?: data["poster"]
            ?: data["image"]
            ?: data["banner"]
            ?: data["thumbnail"]

        // ৪. স্মার্ট স্লাগ এক্সট্রাকশন (সব সম্ভাব্য কী এবং JSON সাপোর্ট)
        var slug = data["slug"]
            ?: data["content_slug"]
            ?: data["post_slug"]
            ?: data["target_slug"]
            ?: data["drama_slug"]
            ?: data["url"]
            ?: data["link"]
            ?: data["target_url"]
            ?: data["id"]
            ?: data["post_id"]
            ?: data["content_id"]

        // যদি ডাটা কোনো JSON স্ট্রিংয়ের ভেতরে থাকে (e.g. data: {"slug": "..."})
        if (slug.isNullOrBlank() && data.containsKey("data")) {
            try {
                val json = JSONObject(data["data"] ?: "{}")
                slug = json.optString("slug").takeIf { it.isNotBlank() }
                    ?: json.optString("post_slug").takeIf { it.isNotBlank() }
                    ?: json.optString("url").takeIf { it.isNotBlank() }
            } catch (_: Exception) {}
        }

        CoroutineScope(Dispatchers.IO).launch {
            showNotification(title, body, posterUrl, slug, notifType, data)
        }
    }

    private suspend fun showNotification(
        title: String,
        body: String,
        posterUrl: String?,
        slug: String?,
        notifType: String,
        extraData: Map<String, String>
    ) {
        val channelId = "high_importance_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // ১. নোটিফিকেশন চ্যানেল তৈরি
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
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        val effectiveSlug = slug ?: ""

        // ২. 🎯 ইউনিক Data URI সহ Explicit Intent (যাতে অ্যান্ড্রয়েড ওএস ইন্টেন্ট ক্যাশ ওভাররাইট না করে)
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            setPackage(packageName)
            // 👈 এই ইউনিক ডাটা ইউআরআই অ্যান্ড্রয়েডকে বাধ্য করে ফ্রেশ ইন্টেন্ট এক্সট্রাস পাস করতে
            data = Uri.parse("playdramaflix://watch/${if (effectiveSlug.isNotBlank()) effectiveSlug else System.currentTimeMillis().toString()}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

            for ((key, value) in extraData) {
                putExtra(key, value)
            }

            if (notifType == "app_update") {
                putExtra("EXTRA_OPEN_UPDATE_DIALOG", true)
                putExtra("type", "app_update")
            } else {
                putExtra("EXTRA_NOTIFICATION_SLUG", effectiveSlug)
                putExtra("slug", effectiveSlug)
                putExtra("content_slug", effectiveSlug)
                putExtra("target_slug", effectiveSlug)
                putExtra("drama_slug", effectiveSlug)
                putExtra("EXTRA_NOTIFICATION_TITLE", title)
                putExtra("EXTRA_NOTIFICATION_POSTER", posterUrl ?: "")
            }
        }

        val requestCode = (System.currentTimeMillis() % 100000).toInt()
        val pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ৩. ইমেজ লোড (৩.৫ সেকেন্ড টাইমআউট)
        var largeBitmap: Bitmap? = null
        if (!posterUrl.isNullOrBlank()) {
            withTimeoutOrNull(3500L) {
                try {
                    val loader = ImageLoader(this@DramaFlixFirebaseMessagingService)
                    val request = ImageRequest.Builder(this@DramaFlixFirebaseMessagingService)
                        .data(posterUrl)
                        .allowHardware(false)
                        .build()
                    val result = loader.execute(request)
                    if (result is SuccessResult) {
                        largeBitmap = (result.drawable as? BitmapDrawable)?.bitmap
                    }
                } catch (e: Exception) {
                    Log.w("FCM_IMG", "Image load skipped: ${e.message}")
                }
            }
        }

        // ৪. নোটিফিকেশন তৈরি
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

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

        notificationManager.notify(requestCode, builder.build())
        Log.d("FCM_NOTIF", "✓ Notification posted for slug: $effectiveSlug")
    }
}
