package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.BlockedUserInfo
import com.example.data.model.ChatMessage
import com.example.data.model.GroupMemberInfo
import com.example.data.model.PinnedMessageInfo
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class UserChatStatus(
    val userId: String = "",
    val userName: String = "",
    val action: String = "idle"
)

data class LiveGroupStats(
    val totalMembers: Int = 1,
    val onlineMembers: Int = 1
)

object FirebaseChatManager {
    private const val TAG = "FirebaseChatManager"
    private const val CHAT_COLLECTION = "community_global_chat"
    private const val STATUS_COLLECTION = "community_user_live_status"
    private const val MEMBERS_COLLECTION = "community_group_members"
    private const val BLOCKED_COLLECTION = "community_blocked_users"
    private const val PINNED_DOC = "community_meta_info/pinned_message"
    private const val NOTIF_TOPIC = "community_group_notifications"

    const val ROOT_ADMIN_EMAIL = "yheysifat@gmail.com"

    fun isRootAdmin(email: String?): Boolean {
        return email?.trim()?.equals(ROOT_ADMIN_EMAIL, ignoreCase = true) == true
    }

    private const val R2_WORKER_UPLOAD_URL = "https://dramaflixbucket.imranhosine52.workers.dev"
    const val MAX_VIDEO_SIZE_BYTES = 50L * 1024L * 1024L

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    fun subscribeToUserTopic(userId: String) {
        if (userId.isBlank()) return
        try {
            FirebaseMessaging.getInstance().subscribeToTopic("user_$userId")
        } catch (_: Exception) {}
    }

    fun toggleGroupNotification(enable: Boolean) {
        try {
            if (enable) FirebaseMessaging.getInstance().subscribeToTopic(NOTIF_TOPIC)
            else FirebaseMessaging.getInstance().unsubscribeFromTopic(NOTIF_TOPIC)
        } catch (_: Exception) {}
    }

    // 🚀 Cloudflare Worker দিয়ে দ্রুত FCM নোটিফিকেশন ট্রিগার করা
    private fun sendPushNotificationViaWorker(
        targetTopic: String,
        senderName: String,
        messageText: String,
        isReply: Boolean,
        mediaUrl: String? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val workerUrl = "$R2_WORKER_UPLOAD_URL/send-chat-notification"

                val jsonBody = JSONObject().apply {
                    put("topic", targetTopic)
                    put("title", if (isReply) "💬 $senderName replied to you" else "💬 $senderName")
                    put("message", messageText.ifBlank { "Sent an attachment" })
                    put("type", if (isReply) "chat_reply" else "community_chat")
                    if (!mediaUrl.isNullOrBlank()) {
                        put("image", mediaUrl)
                    }
                }

                val requestBody = jsonBody.toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

                val request = Request.Builder()
                    .url(workerUrl)
                    .post(requestBody)
                    .build()

                val response = httpClient.newCall(request).execute()
                response.close()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to dispatch push notification: ${e.message}")
            }
        }
    }

    // =========================================================================
    // 🔍 ইউজার আগে থেকেই ক্লাউডে জয়েন আছে কি না তা চেক করা (আনইনস্টলের পর অটো রিকভারি)
    // =========================================================================
    suspend fun isUserAlreadyJoined(userId: String, userEmail: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            // ১. ওনার চেক
            if (isRootAdmin(userEmail)) return@withContext true

            // ২. ডিরেক্ট আইডি দিয়ে চেক
            if (userId.isNotBlank()) {
                val doc = firestore.collection(MEMBERS_COLLECTION).document(userId).get().await()
                if (doc.exists()) return@withContext true
            }

            // ৩. ইমেইল দিয়ে চেক
            if (!userEmail.isNullOrBlank()) {
                val query = firestore.collection(MEMBERS_COLLECTION)
                    .whereEqualTo("userEmail", userEmail.trim().lowercase())
                    .limit(1)
                    .get()
                    .await()
                if (!query.isEmpty) return@withContext true
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    // =========================================================================
    // 🟢 গ্রুপে জয়েন করা (ডুপ্লিকেট প্রতিরোধে ফিক্সড আইডি ব্যবহার)
    // =========================================================================
    fun joinGroup(userId: String, userName: String, userAvatar: String?, userEmail: String?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // ওনারের জন্য নির্দিষ্ট ফিক্সড আইডি, বাকিদের জন্য তাদের ইউনিক আইডি
                val targetDocId = if (isRootAdmin(userEmail)) "owner_yheysifat" else userId
                if (targetDocId.isBlank()) return@launch

                val memberData = hashMapOf(
                    "userId" to targetDocId,
                    "userName" to userName,
                    "userAvatar" to userAvatar,
                    "userEmail" to userEmail?.trim()?.lowercase(),
                    "joinedAt" to System.currentTimeMillis(),
                    "lastActive" to System.currentTimeMillis()
                )

                firestore.collection(MEMBERS_COLLECTION).document(targetDocId)
                    .set(memberData, com.google.firebase.firestore.SetOptions.merge())

                // যদি আগের কোনো ডুপ্লিকেট ডকুমেন্ট থেকে থাকে তবে ক্লিনআপ
                if (isRootAdmin(userEmail)) {
                    val duplicates = firestore.collection(MEMBERS_COLLECTION)
                        .whereEqualTo("userEmail", ROOT_ADMIN_EMAIL)
                        .get().await()
                    for (d in duplicates.documents) {
                        if (d.id != "owner_yheysifat") {
                            firestore.collection(MEMBERS_COLLECTION).document(d.id).delete()
                        }
                    }
                }

                toggleGroupNotification(true)
                subscribeToUserTopic(targetDocId)
            } catch (_: Exception) {}
        }
    }

    // =========================================================================
    // 🔴 গ্রুপ থেকে লিভ নেওয়া (সংখ্যা ও আইডি তাৎক্ষণিক মাইনাস হবে)
    // =========================================================================
    fun leaveGroup(userId: String, userEmail: String?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val targetDocId = if (isRootAdmin(userEmail)) "owner_yheysifat" else userId
                if (targetDocId.isNotBlank()) {
                    firestore.collection(MEMBERS_COLLECTION).document(targetDocId).delete().await()
                }

                // ইমেইল দিয়ে কোনো ডুপ্লিকেট থাকলে তাও ডিলিট
                if (!userEmail.isNullOrBlank()) {
                    val query = firestore.collection(MEMBERS_COLLECTION)
                        .whereEqualTo("userEmail", userEmail.trim().lowercase())
                        .get().await()
                    for (d in query.documents) {
                        firestore.collection(MEMBERS_COLLECTION).document(d.id).delete()
                    }
                }

                toggleGroupNotification(false)
            } catch (_: Exception) {}
        }
    }

    fun pingUserPresence(userId: String, userName: String, userAvatar: String? = null, userEmail: String? = null) {
        val targetDocId = if (isRootAdmin(userEmail)) "owner_yheysifat" else userId
        if (targetDocId.isBlank()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val memberData = hashMapOf(
                    "userId" to targetDocId,
                    "userName" to userName,
                    "userAvatar" to userAvatar,
                    "userEmail" to userEmail?.trim()?.lowercase(),
                    "lastActive" to System.currentTimeMillis()
                )
                firestore.collection(MEMBERS_COLLECTION).document(targetDocId)
                    .set(memberData, com.google.firebase.firestore.SetOptions.merge())
                subscribeToUserTopic(targetDocId)
            } catch (_: Exception) {}
        }
    }

    // =========================================================================
    // 👥 মেম্বার তালিকা লাইভ স্ট্রিম (ডুপ্লিকেট ১০০% ফিল্টার করে ১ বারই দেখাবে)
    // =========================================================================
    fun getLiveGroupMembersFlow(): Flow<List<GroupMemberInfo>> = callbackFlow {
        val listener = firestore.collection(MEMBERS_COLLECTION)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val rawList = snapshot.documents.mapNotNull { doc ->
                    val uid = doc.getString("userId") ?: doc.id
                    val name = doc.getString("userName") ?: "Member"
                    val avatar = doc.getString("userAvatar")
                    val email = doc.getString("userEmail")
                    val joined = doc.getLong("joinedAt") ?: doc.getLong("lastActive") ?: 0L
                    val last = doc.getLong("lastActive") ?: joined
                    val isOwner = isRootAdmin(email) || name.contains("Hey Sifat", ignoreCase = true)
                    GroupMemberInfo(
                        userId = uid,
                        userName = name,
                        userAvatar = avatar,
                        userEmail = email,
                        isOwner = isOwner,
                        isVip = isOwner || (doc.getBoolean("isVip") == true),
                        joinedAt = joined,
                        lastActive = last
                    )
                }

                // 🎯 ডুপ্লিকেট রিমুভার: একই ইমেইল বা ওনারের নাম একাধিক থাকলে কেবল ১ বারই আসবে
                val uniqueList = rawList.distinctBy { member ->
                    if (!member.userEmail.isNullOrBlank()) member.userEmail.lowercase()
                    else if (member.isOwner) "owner_unique_admin"
                    else member.userId
                }.sortedWith(compareByDescending<GroupMemberInfo> { it.isOwner }.thenByDescending { it.lastActive })

                trySend(uniqueList)
            }
        awaitClose { listener.remove() }
    }

    // 📊 রিয়েল-টাইম লাইভ মেম্বার কাউন্টার (ডুপ্লিকেট বাদ দিয়ে সঠিক সংখ্যা)
    fun getLiveGroupStatsFlow(): Flow<LiveGroupStats> = callbackFlow {
        val listener = firestore.collection(MEMBERS_COLLECTION)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val rawMembers = snapshot.documents.mapNotNull { doc ->
                    val name = doc.getString("userName") ?: ""
                    val email = doc.getString("userEmail")
                    val lastActive = doc.getLong("lastActive") ?: 0L
                    val isOwner = isRootAdmin(email) || name.contains("Hey Sifat", ignoreCase = true)
                    Pair(if (isOwner) "owner_admin" else (email ?: doc.id), lastActive)
                }

                val uniqueMembers = rawMembers.distinctBy { it.first }
                val total = uniqueMembers.size.coerceAtLeast(1)
                val now = System.currentTimeMillis()
                val online = uniqueMembers.count { (now - it.second) < 180_000L }.coerceAtLeast(1)

                trySend(LiveGroupStats(totalMembers = total, onlineMembers = online))
            }
        awaitClose { listener.remove() }
    }

    // =========================================================================
    // 💬 চ্যাট মেসেজিং ও অন্যান্য ফাংশনসমূহ
    // =========================================================================
    fun getLiveMessagesFlow(): Flow<List<ChatMessage>> = callbackFlow {
        val listenerRegistration = firestore.collection(CHAT_COLLECTION)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limitToLast(300)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val messages = snapshot.documents.mapNotNull { doc ->
                    try {
                        doc.toObject(ChatMessage::class.java)?.copy(id = doc.id)
                    } catch (_: Exception) {
                        null
                    }
                }
                trySend(messages)
            }
        awaitClose { listenerRegistration.remove() }
    }

    fun getLiveActiveActionUsersFlow(currentUserId: String): Flow<List<UserChatStatus>> = callbackFlow {
        val listener = firestore.collection(STATUS_COLLECTION)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val activeList = snapshot.documents.mapNotNull { doc ->
                    val uid = doc.getString("userId") ?: ""
                    val name = doc.getString("userName") ?: "Someone"
                    val action = doc.getString("action") ?: "idle"
                    val time = doc.getLong("updatedAt") ?: 0L
                    val isFresh = (System.currentTimeMillis() - time) < 4000L
                    if (uid.isNotBlank() && uid != currentUserId && action != "idle" && isFresh) {
                        UserChatStatus(uid, name, action)
                    } else null
                }
                trySend(activeList)
            }
        awaitClose { listener.remove() }
    }

    fun setUserActionStatus(userId: String, userName: String, action: String) {
        if (userId.isBlank()) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val data = hashMapOf(
                    "userId" to userId,
                    "userName" to userName,
                    "action" to action,
                    "updatedAt" to System.currentTimeMillis()
                )
                firestore.collection(STATUS_COLLECTION).document(userId).set(data)
            } catch (_: Exception) {}
        }
    }

    suspend fun sendTextMessage(
        senderId: String,
        senderName: String,
        senderEmail: String?,
        senderAvatar: String?,
        isVip: Boolean,
        text: String,
        replyToMessage: ChatMessage? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val isOwner = isRootAdmin(senderEmail)
            val messageData = hashMapOf(
                "senderId" to senderId,
                "senderName" to senderName,
                "senderEmail" to senderEmail,
                "senderAvatar" to senderAvatar,
                "isVip" to (isVip || isOwner),
                "isOwner" to isOwner,
                "text" to text.trim(),
                "imageUrl" to null,
                "imageUrls" to emptyList<String>(),
                "videoUrl" to null,
                "audioUrl" to null,
                "mediaDurationSec" to 0L,
                "viewsCount" to 1L,
                "replyToId" to replyToMessage?.id,
                "replyToName" to replyToMessage?.senderName,
                "replyToText" to (replyToMessage?.text?.ifBlank { "Message" }),
                "isRead" to false,
                "readBy" to listOf<String>(),
                "isPinned" to false,
                "timestamp" to FieldValue.serverTimestamp()
            )
            firestore.collection(CHAT_COLLECTION).add(messageData).await()
            setUserActionStatus(senderId, senderName, "idle")
            pingUserPresence(senderId, senderName, senderAvatar, senderEmail)

            if (replyToMessage != null && replyToMessage.senderId != senderId) {
                sendPushNotificationViaWorker(
                    targetTopic = "user_${replyToMessage.senderId}",
                    senderName = senderName,
                    messageText = text.trim(),
                    isReply = true,
                    mediaUrl = null
                )
            } else {
                sendPushNotificationViaWorker(
                    targetTopic = NOTIF_TOPIC,
                    senderName = senderName,
                    messageText = text.trim(),
                    isReply = false,
                    mediaUrl = null
                )
            }

            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun uploadMultipleImagesAndSendMessage(
        context: Context,
        imageUris: List<Uri>,
        senderId: String,
        senderName: String,
        senderEmail: String?,
        senderAvatar: String?,
        isVip: Boolean,
        captionText: String = "",
        replyToMessage: ChatMessage? = null,
        onError: ((String) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (imageUris.isEmpty()) return@withContext false
        try {
            val uploadedUrls = mutableListOf<String>()

            for (uri in imageUris) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val originalBitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()

                    if (originalBitmap != null) {
                        val scaled = scaleBitmapDown(originalBitmap, 1024)
                        val baos = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.JPEG, 78, baos)
                        val bytes = baos.toByteArray()

                        val reqBody = MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart("type", "image")
                            .addFormDataPart("file", "chat_${System.currentTimeMillis()}_${(100..999).random()}.jpg", bytes.toRequestBody("image/jpeg".toMediaTypeOrNull()))
                            .build()

                        val res = httpClient.newCall(Request.Builder().url(R2_WORKER_UPLOAD_URL).post(reqBody).build()).execute()
                        val json = JSONObject(res.body?.string() ?: "")
                        val mediaUrl = json.optString("mediaUrl").ifBlank { json.optString("imageUrl") }
                        if (mediaUrl.isNotBlank()) {
                            uploadedUrls.add(mediaUrl)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Single image upload failed: ${e.message}")
                }
            }

            if (uploadedUrls.isEmpty()) {
                withContext(Dispatchers.Main) { onError?.invoke("Failed to upload images.") }
                return@withContext false
            }

            val isOwner = isRootAdmin(senderEmail)
            val firstImageUrl = uploadedUrls.firstOrNull()

            val messageData = hashMapOf(
                "senderId" to senderId,
                "senderName" to senderName,
                "senderEmail" to senderEmail,
                "senderAvatar" to senderAvatar,
                "isVip" to (isVip || isOwner),
                "isOwner" to isOwner,
                "text" to captionText.trim(),
                "imageUrl" to firstImageUrl,
                "imageUrls" to uploadedUrls,
                "videoUrl" to null,
                "audioUrl" to null,
                "mediaDurationSec" to 0L,
                "viewsCount" to 1L,
                "replyToId" to replyToMessage?.id,
                "replyToName" to replyToMessage?.senderName,
                "replyToText" to (replyToMessage?.text?.ifBlank { "📷 Photos" }),
                "isRead" to false,
                "readBy" to listOf<String>(),
                "isPinned" to false,
                "timestamp" to FieldValue.serverTimestamp()
            )
            firestore.collection(CHAT_COLLECTION).add(messageData).await()
            setUserActionStatus(senderId, senderName, "idle")
            pingUserPresence(senderId, senderName, senderAvatar, senderEmail)

            val displayCaption = captionText.trim().ifBlank { "📷 Sent a photo" }

            if (replyToMessage != null && replyToMessage.senderId != senderId) {
                sendPushNotificationViaWorker(
                    targetTopic = "user_${replyToMessage.senderId}",
                    senderName = senderName,
                    messageText = displayCaption,
                    isReply = true,
                    mediaUrl = firstImageUrl
                )
            } else {
                sendPushNotificationViaWorker(
                    targetTopic = NOTIF_TOPIC,
                    senderName = senderName,
                    messageText = displayCaption,
                    isReply = false,
                    mediaUrl = firstImageUrl
                )
            }

            true
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onError?.invoke(e.localizedMessage ?: "Image upload error") }
            false
        }
    }

    suspend fun uploadVideoWithProgressAndSendMessage(
        context: Context,
        videoUri: Uri,
        senderId: String,
        senderName: String,
        senderEmail: String?,
        senderAvatar: String?,
        isVip: Boolean,
        captionText: String = "",
        replyToMessage: ChatMessage? = null,
        onProgress: (percent: Int, secondsLeft: Long) -> Unit,
        onError: ((String) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            var fileSize = 0L
            context.contentResolver.query(videoUri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst() && sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
            }
            if (fileSize > MAX_VIDEO_SIZE_BYTES) {
                withContext(Dispatchers.Main) { onError?.invoke("⚠️ Video exceeds 50 MB limit!") }
                return@withContext false
            }

            val thumbnailBitmap = getVideoFrameThumbnail(context, videoUri)
            var thumbnailUrl: String? = null
            if (thumbnailBitmap != null) {
                try {
                    val baos = ByteArrayOutputStream()
                    thumbnailBitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
                    val thumbBytes = baos.toByteArray()
                    val thumbReq = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("type", "image")
                        .addFormDataPart("file", "thumb_${System.currentTimeMillis()}.jpg", thumbBytes.toRequestBody("image/jpeg".toMediaTypeOrNull()))
                        .build()
                    val thumbRes = httpClient.newCall(Request.Builder().url(R2_WORKER_UPLOAD_URL).post(thumbReq).build()).execute()
                    val jsonThumb = JSONObject(thumbRes.body?.string() ?: "")
                    thumbnailUrl = jsonThumb.optString("mediaUrl").ifBlank { jsonThumb.optString("imageUrl") }
                } catch (_: Exception) {}
            }

            val tempFile = File(context.cacheDir, "vid_${System.currentTimeMillis()}.mp4")
            context.contentResolver.openInputStream(videoUri)?.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
            }

            val startTime = System.currentTimeMillis()
            val totalBytes: Long = tempFile.length()

            val progressBody = ProgressRequestBody(
                file = tempFile,
                contentType = "video/mp4",
                onProgress = { bytesWritten: Long ->
                    val elapsedSec: Double = ((System.currentTimeMillis() - startTime) / 1000.0).coerceAtLeast(0.1)
                    val speed: Double = bytesWritten.toDouble() / elapsedSec
                    val remainingBytes: Long = (totalBytes - bytesWritten).coerceAtLeast(0L)
                    val remainingSec: Long = if (speed > 0.0) (remainingBytes / speed).toLong() else 3L
                    val percent: Int = if (totalBytes > 0L) {
                        ((bytesWritten * 100L) / totalBytes).toInt().coerceIn(0, 100)
                    } else 0
                    onProgress(percent, remainingSec)
                }
            )

            val reqBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("type", "video")
                .addFormDataPart("file", tempFile.name, progressBody)
                .build()

            val res = httpClient.newCall(Request.Builder().url(R2_WORKER_UPLOAD_URL).post(reqBody).build()).execute()
            tempFile.delete()

            val json = JSONObject(res.body?.string() ?: "")
            val mediaUrl = json.optString("mediaUrl").ifBlank { json.optString("imageUrl") }
            if (mediaUrl.isBlank()) return@withContext false

            val isOwner = isRootAdmin(senderEmail)
            val messageData = hashMapOf(
                "senderId" to senderId,
                "senderName" to senderName,
                "senderEmail" to senderEmail,
                "senderAvatar" to senderAvatar,
                "isVip" to (isVip || isOwner),
                "isOwner" to isOwner,
                "text" to captionText.trim(),
                "imageUrl" to thumbnailUrl,
                "imageUrls" to emptyList<String>(),
                "videoUrl" to mediaUrl,
                "audioUrl" to null,
                "mediaDurationSec" to 0L,
                "viewsCount" to 1L,
                "replyToId" to replyToMessage?.id,
                "replyToName" to replyToMessage?.senderName,
                "replyToText" to (replyToMessage?.text?.ifBlank { "🎬 Video" }),
                "isRead" to false,
                "readBy" to listOf<String>(),
                "isPinned" to false,
                "timestamp" to FieldValue.serverTimestamp()
            )
            firestore.collection(CHAT_COLLECTION).add(messageData).await()
            setUserActionStatus(senderId, senderName, "idle")
            pingUserPresence(senderId, senderName, senderAvatar, senderEmail)

            val displayCaption = captionText.trim().ifBlank { "🎬 Sent a video" }

            if (replyToMessage != null && replyToMessage.senderId != senderId) {
                sendPushNotificationViaWorker(
                    targetTopic = "user_${replyToMessage.senderId}",
                    senderName = senderName,
                    messageText = displayCaption,
                    isReply = true,
                    mediaUrl = thumbnailUrl
                )
            } else {
                sendPushNotificationViaWorker(
                    targetTopic = NOTIF_TOPIC,
                    senderName = senderName,
                    messageText = displayCaption,
                    isReply = false,
                    mediaUrl = thumbnailUrl
                )
            }

            true
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onError?.invoke(e.localizedMessage ?: "Video upload failed") }
            false
        }
    }

    suspend fun uploadVoiceAndSendMessage(
        audioFile: File,
        durationSeconds: Long,
        senderId: String,
        senderName: String,
        senderEmail: String?,
        senderAvatar: String?,
        isVip: Boolean,
        replyToMessage: ChatMessage? = null,
        onError: ((String) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val reqBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("type", "audio")
                .addFormDataPart("file", audioFile.name, audioFile.asRequestBody("audio/mp4".toMediaTypeOrNull()))
                .build()

            val res = httpClient.newCall(Request.Builder().url(R2_WORKER_UPLOAD_URL).post(reqBody).build()).execute()
            audioFile.delete()

            val json = JSONObject(res.body?.string() ?: "")
            val mediaUrl = json.optString("mediaUrl").ifBlank { json.optString("imageUrl") }
            if (mediaUrl.isBlank()) return@withContext false

            val isOwner = isRootAdmin(senderEmail)
            val messageData = hashMapOf(
                "senderId" to senderId,
                "senderName" to senderName,
                "senderEmail" to senderEmail,
                "senderAvatar" to senderAvatar,
                "isVip" to (isVip || isOwner),
                "isOwner" to isOwner,
                "text" to "",
                "imageUrl" to null,
                "imageUrls" to emptyList<String>(),
                "videoUrl" to null,
                "audioUrl" to mediaUrl,
                "mediaDurationSec" to durationSeconds,
                "viewsCount" to 1L,
                "replyToId" to replyToMessage?.id,
                "replyToName" to replyToMessage?.senderName,
                "replyToText" to (replyToMessage?.text?.ifBlank { "🎤 Voice message" }),
                "isRead" to false,
                "readBy" to listOf<String>(),
                "isPinned" to false,
                "timestamp" to FieldValue.serverTimestamp()
            )
            firestore.collection(CHAT_COLLECTION).add(messageData).await()
            setUserActionStatus(senderId, senderName, "idle")
            pingUserPresence(senderId, senderName, senderAvatar, senderEmail)

            if (replyToMessage != null && replyToMessage.senderId != senderId) {
                sendPushNotificationViaWorker(
                    targetTopic = "user_${replyToMessage.senderId}",
                    senderName = senderName,
                    messageText = "🎤 Voice message",
                    isReply = true,
                    mediaUrl = null
                )
            } else {
                sendPushNotificationViaWorker(
                    targetTopic = NOTIF_TOPIC,
                    senderName = senderName,
                    messageText = "🎤 Voice message",
                    isReply = false,
                    mediaUrl = null
                )
            }

            true
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onError?.invoke(e.localizedMessage ?: "Voice upload failed") }
            false
        }
    }

    suspend fun deleteMessage(messageId: String): Boolean = withContext(Dispatchers.IO) {
        if (messageId.isBlank()) return@withContext false
        try {
            firestore.collection(CHAT_COLLECTION).document(messageId).delete().await()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun getVideoFrameThumbnail(context: Context, uri: Uri): Bitmap? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(1000000)
            retriever.release()
            frame?.let { scaleBitmapDown(it, 480) }
        } catch (_: Exception) {
            null
        }
    }

    private fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap
        val ratio = width.toFloat() / height.toFloat()
        return Bitmap.createScaledBitmap(bitmap, if (width > height) maxDimension else (maxDimension * ratio).toInt(), if (width > height) (maxDimension / ratio).toInt() else maxDimension, true)
    }

    suspend fun pinMessage(message: ChatMessage, adminName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val data = hashMapOf(
                "messageId" to message.id,
                "text" to (message.text.ifBlank { if (message.imageUrls.isNotEmpty() || message.imageUrl != null) "📷 Photo" else if (message.videoUrl != null) "🎬 Video" else "Voice Message" }),
                "senderName" to message.senderName,
                "pinnedBy" to adminName,
                "pinnedAt" to System.currentTimeMillis()
            )
            firestore.document(PINNED_DOC).set(data).await()
            firestore.collection(CHAT_COLLECTION).document(message.id).update("isPinned", true).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun unpinMessage(messageId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.document(PINNED_DOC).delete().await()
            if (messageId.isNotBlank()) {
                firestore.collection(CHAT_COLLECTION).document(messageId).update("isPinned", false).await()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getLivePinnedMessageFlow(): Flow<PinnedMessageInfo?> = callbackFlow {
        val listener = firestore.document(PINNED_DOC)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    trySend(null)
                    return@addSnapshotListener
                }
                val info = PinnedMessageInfo(
                    messageId = snapshot.getString("messageId") ?: "",
                    text = snapshot.getString("text") ?: "",
                    senderName = snapshot.getString("senderName") ?: "",
                    pinnedBy = snapshot.getString("pinnedBy") ?: "",
                    pinnedAt = snapshot.getLong("pinnedAt") ?: 0L
                )
                trySend(info)
            }
        awaitClose { listener.remove() }
    }

    suspend fun blockUser(targetUserId: String, targetUserName: String, targetEmail: String?): Boolean = withContext(Dispatchers.IO) {
        if (targetUserId.isBlank()) return@withContext false
        try {
            val data = hashMapOf(
                "userId" to targetUserId,
                "userName" to targetUserName,
                "userEmail" to targetEmail,
                "blockedAt" to System.currentTimeMillis()
            )
            firestore.collection(BLOCKED_COLLECTION).document(targetUserId).set(data).await()
            firestore.collection(MEMBERS_COLLECTION).document(targetUserId).delete().await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun unblockUser(targetUserId: String): Boolean = withContext(Dispatchers.IO) {
        if (targetUserId.isBlank()) return@withContext false
        try {
            firestore.collection(BLOCKED_COLLECTION).document(targetUserId).delete().await()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getLiveBlockedUsersFlow(): Flow<List<BlockedUserInfo>> = callbackFlow {
        val listener = firestore.collection(BLOCKED_COLLECTION)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val list = snapshot.documents.mapNotNull { doc ->
                    BlockedUserInfo(
                        userId = doc.getString("userId") ?: doc.id,
                        userName = doc.getString("userName") ?: "Blocked User",
                        userEmail = doc.getString("userEmail"),
                        blockedAt = doc.getLong("blockedAt") ?: 0L
                    )
                }
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    fun isUserBlockedFlow(userId: String): Flow<Boolean> = callbackFlow {
        if (userId.isBlank()) {
            trySend(false)
            close()
            return@callbackFlow
        }
        val listener = firestore.collection(BLOCKED_COLLECTION).document(userId)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot != null && snapshot.exists())
            }
        awaitClose { listener.remove() }
    }

    suspend fun kickUser(targetUserId: String): Boolean = withContext(Dispatchers.IO) {
        if (targetUserId.isBlank()) return@withContext false
        try {
            firestore.collection(MEMBERS_COLLECTION).document(targetUserId).delete().await()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun markMessagesAsRead(viewerId: String, messages: List<ChatMessage>) {
        if (viewerId.isBlank()) return
        val unreadMessages = messages.filter {
            it.senderId != viewerId && !it.readBy.contains(viewerId) && it.id.isNotBlank()
        }
        if (unreadMessages.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val batch = firestore.batch()
                for (msg in unreadMessages.take(25)) {
                    val docRef = firestore.collection(CHAT_COLLECTION).document(msg.id)
                    batch.update(
                        docRef,
                        mapOf(
                            "isRead" to true,
                            "readBy" to FieldValue.arrayUnion(viewerId)
                        )
                    )
                }
                batch.commit().await()
            } catch (_: Exception) {}
        }
    }
}

class ProgressRequestBody(
    private val file: File,
    private val contentType: String,
    private val onProgress: (bytesWritten: Long) -> Unit
) : RequestBody() {
    override fun contentType() = contentType.toMediaTypeOrNull()
    override fun contentLength(): Long = file.length()

    override fun writeTo(sink: BufferedSink) {
        val buffer = ByteArray(8 * 1024)
        var bytesWritten = 0L
        FileInputStream(file).use { inputStream ->
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                sink.write(buffer, 0, read)
                bytesWritten += read.toLong()
                onProgress(bytesWritten)
            }
        }
    }
}
