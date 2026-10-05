package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.AudioAttributes
import android.media.RingtoneManager
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
import com.example.util.FirebaseChatManager
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

            // ১. সাধারণ সকল ইউজারদের টপিকসমূহ
            val topics = listOf("all_users", "all", "general", "dramaflix", "new_posts")
            for (topic in topics) {
                FirebaseMessaging.getInstance().subscribeToTopic(topic)
            }

            // ২. 🎯 স্মার্ট টপিক সেগ্রিগেশন (VIP বনাম Free ইউজার)
            val authPrefs = getSharedPreferences("play_drama_flix_auth_prefs", Context.MODE_PRIVATE)
            val isUserVip = authPrefs.getBoolean("is_vip", false) ||
                    (authPrefs.getString("user_plan", "free")?.lowercase() in listOf("vip", "premium"))

            if (isUserVip) {
                FirebaseMessaging.getInstance().subscribeToTopic("vip_users")
                FirebaseMessaging.getInstance().unsubscribeFromTopic("free_users")
            } else {
                FirebaseMessaging.getInstance().subscribeToTopic("free_users")
                FirebaseMessaging.getInstance().unsubscribeFromTopic("vip_users")
            }

            // ৩. চ্যাট গ্রুপ নোটিফিকেশন টপিক
            val chatPrefs = getSharedPreferences("play_drama_flix_chat_group_prefs", Context.MODE_PRIVATE)
            if (!chatPrefs.getBoolean("is_group_muted", false)) {
                FirebaseMessaging.getInstance().subscribeToTopic("community_group_notifications")
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
        val clickAction = data["click_action"] ?: ""

        val authPrefs = getSharedPreferences("play_drama_flix_auth_prefs", Context.MODE_PRIVATE)
        val isUserVip = authPrefs.getBoolean("is_vip", false) ||
                (authPrefs.getString("user_plan", "free")?.lowercase() in listOf("vip", "premium"))

        // =========================================================================
        // 👑 🚫 ১. VIP ইউজারদের জন্য VIP Promo নোটিফিকেশন ক্লায়েন্ট সাইডে শতভাগ ব্লক
        // =========================================================================
        if (isUserVip && (notifType == "vip_promo" || clickAction == "OPEN_VIP_CHECKOUT")) {
            Log.d("FCM_MSG", "🔇 Blocked VIP promo notification because user already has active VIP subscription.")
            return // 👈 নোটিফিকেশন ডিসপ্লে না করে সরাসরি থামিয়ে দেওয়া হলো
        }

        // =========================================================================
        // 🚫 ২. নিজের পাঠানো চ্যাট মেসেজের নোটিফিকেশন ফিল্টার
        // =========================================================================
        val isChatNotification = notifType in listOf("community_chat", "group_chat", "chat_reply", "chat")

        if (isChatNotification) {
            val incomingSenderId = (data["sender_id"] ?: data["senderId"] ?: data["sender"] ?: data["user_id"] ?: "").trim()
            val incomingSenderEmail = (data["sender_email"] ?: data["senderEmail"] ?: data["email"] ?: "").trim().lowercase()
            val incomingSenderName = (data["sender_name"] ?: data["senderName"] ?: "").trim()

            val myUserId = authPrefs.getString("user_id", "")?.trim() ?: ""
            val myAccountId = authPrefs.getString("account_id", "")?.trim() ?: ""
            val myEmail = authPrefs.getString("user_email", "")?.trim()?.lowercase() ?: ""
            val myName = authPrefs.getString("user_name", "")?.trim() ?: ""
            val guestId = authPrefs.getString("permanent_guest_id", "")?.trim() ?: ""
            val isOwner = FirebaseChatManager.isRootAdmin(myEmail)

            val rawTitle = remoteMessage.notification?.title ?: data["title"] ?: data["heading"] ?: ""

            val isSelfId = incomingSenderId.isNotBlank() && (
                incomingSenderId == myUserId ||
                incomingSenderId == myAccountId ||
                incomingSenderId == guestId ||
                (isOwner && (incomingSenderId == "owner_yheysifat" || incomingSenderId.contains("sifat", ignoreCase = true)))
            )

            val isSelfEmail = incomingSenderEmail.isNotBlank() && (
                incomingSenderEmail.equals(myEmail, ignoreCase = true) ||
                (isOwner && incomingSenderEmail.equals(FirebaseChatManager.ROOT_ADMIN_EMAIL, ignoreCase = true))
            )

            val isSelfName = (myName.isNotBlank() && (
                incomingSenderName.equals(myName, ignoreCase = true) ||
                rawTitle.contains(myName, ignoreCase = true)
            )) || (isOwner && (
                rawTitle.contains("Hey Sifat", ignoreCase = true) ||
                incomingSenderName.contains("Hey Sifat", ignoreCase = true)
            ))

            if (isSelfId || isSelfEmail || isSelfName) {
                Log.d("FCM_MSG", "🔇 Blocked self-sent chat notification successfully.")
                return
            }
        }

        // 🔕 ৩. মিউট অপশন চেক
        val chatPrefs = getSharedPreferences("play_drama_flix_chat_group_prefs", Context.MODE_PRIVATE)
        val isGroupMuted = chatPrefs.getBoolean("is_group_muted", false)

        if (isGroupMuted && isChatNotification) {
            Log.d("FCM_MSG", "🔕 Group chat notifications are muted by user.")
            return
        }

        val title = remoteMessage.notification?.title
            ?: data["title"]
            ?: data["heading"]
            ?: if (notifType == "chat_reply") "💬 New Reply in Community Chat"
               else "New Drama Added!"

        val body = remoteMessage.notification?.body
            ?: data["message"]
            ?: data["body"]
            ?: data["description"]
            ?: if (notifType == "app_update") "A new version of PlayDramaFlix is available." 
               else "Check out the latest release on PlayDramaFlix!"

        val posterUrl = remoteMessage.notification?.imageUrl?.toString()
            ?: data["poster_url"]
            ?: data["image"]
            ?: data["poster"]
            ?: data["thumbnail"]
            ?: data["thumb_url"]
            ?: data["banner"]

        // ৪. ড্রামার স্লাগ শনাক্তকরণ
        var slug = data["slug"]
            ?: data["content_slug"]
            ?: data["post_slug"]
            ?: data["target_slug"]
            ?: data["drama_slug"]
            ?: data["url"]
            ?: data["link"]
            ?: data["id"]

        if (slug.isNullOrBlank() && data.containsKey("data")) {
            try {
                val json = JSONObject(data["data"] ?: "{}")
                slug = json.optString("slug").takeIf { it.isNotBlank() }
                    ?: json.optString("post_slug").takeIf { it.isNotBlank() }
                    ?: json.optString("url").takeIf { it.isNotBlank() }
                    ?: json.optString("id").takeIf { it.isNotBlank() }
            } catch (_: Exception) {}
        }

        val rawType = data["type"] ?: data["content_type"] ?: ""
        val rawCategory = data["category"] ?: data["categories"] ?: ""
        val isShorts = rawType.equals("shorts", ignoreCase = true) ||
                       data["is_shorts"] == "1" ||
                       data["is_shorts"] == "true" ||
                       rawCategory.contains("short", ignoreCase = true) ||
                       (slug?.contains("short", ignoreCase = true) == true) ||
                       title.contains("short", ignoreCase = true)

        CoroutineScope(Dispatchers.IO).launch {
            showNotification(title, body, posterUrl, slug, notifType, isShorts, data)
        }
    }

    private suspend fun showNotification(
        title: String,
        body: String,
        posterUrl: String?,
        slug: String?,
        notifType: String,
        isShorts: Boolean,
        extraData: Map<String, String>
    ) {
        val channelId = if (notifType == "chat_reply" || notifType == "community_chat") {
            "community_chat_channel"
        } else {
            "high_importance_channel"
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = if (notifType == "chat_reply" || notifType == "community_chat") {
                "Community Chat Replies"
            } else {
                "New Post & App Alerts"
            }

            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for chat replies, drama series, and updates."
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                setSound(defaultSoundUri, audioAttributes)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        val effectiveSlug = slug?.trim()?.trim('/') ?: ""

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

            for ((key, value) in extraData) {
                putExtra(key, value)
            }

            // =========================================================================
            // 🎯 নোটিফিকেশন রাউটিং লজিক (চ্যাট, ভিআইপি, আপডেট বা ড্রামা)
            // =========================================================================
            if (notifType == "chat_reply" || notifType == "community_chat") {
                putExtra("EXTRA_OPEN_COMMUNITY_CHAT", true)
                putExtra("type", "chat_reply")
            } else if (notifType == "vip_promo" || notifType == "vip_status_update") {
                putExtra("EXTRA_OPEN_VIP", true)
                putExtra("type", notifType)
            } else if (notifType == "app_update") {
                putExtra("EXTRA_OPEN_UPDATE_DIALOG", true)
                putExtra("type", "app_update")
            } else {
                putExtra("EXTRA_NOTIFICATION_SLUG", effectiveSlug)
                putExtra("slug", effectiveSlug)
                putExtra("content_slug", effectiveSlug)
                putExtra("post_slug", effectiveSlug)
                putExtra("title", title)
                putExtra("IS_SHORTS", isShorts)
                putExtra("content_type", if (isShorts) "shorts" else "series")
            }
        }

        val requestCode = (System.currentTimeMillis() % 100000).toInt()
        val pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        var largeBitmap: Bitmap? = null
        if (!posterUrl.isNullOrBlank()) {
            withTimeoutOrNull(4000L) {
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

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(defaultSoundUri)
            .setVibrate(longArrayOf(0, 250, 150, 250))
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
        Log.d("FCM_NOTIF", "✓ Notification posted: $title")
    }
}
