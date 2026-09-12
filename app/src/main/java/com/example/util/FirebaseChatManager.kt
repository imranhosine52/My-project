package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.data.model.ChatMessage
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
    private const val NOTIF_TOPIC = "community_group_notifications"

    private const val FCM_SERVER_KEY = "AIzaSyBrG0KQcy1zS6rp6YSYYHBTJ07ASpct0qo"
    const val ROOT_ADMIN_EMAIL = "yheysifat@gmail.com"

    fun isRootAdmin(email: String?): Boolean {
        return email?.trim()?.equals(ROOT_ADMIN_EMAIL, ignoreCase = true) == true
    }

    private const val R2_WORKER_UPLOAD_URL = "https://dramaflixbucket.imranhosine52.workers.dev"
    const val MAX_VIDEO_SIZE_BYTES = 50L * 1024L * 1024L

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
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

    fun sendReplyPushNotification(
        targetUserId: String,
        senderName: String,
        replyMessageText: String
    ) {
        if (targetUserId.isBlank()) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cleanBody = replyMessageText.ifBlank { "Sent you a message" }
                val jsonPayload = JSONObject().apply {
                    put("to", "/topics/user_$targetUserId")
                    put("priority", "high")
                    put("notification", JSONObject().apply {
                        put("title", "💬 $senderName replied to you")
                        put("body", cleanBody)
                        put("sound", "default")
                        put("click_action", "OPEN_COMMUNITY_CHAT")
                    })
                    put("data", JSONObject().apply {
                        put("type", "chat_reply")
                        put("title", "💬 $senderName replied to you")
                        put("message", cleanBody)
                        put("body", cleanBody)
                        put("sender_name", senderName)
                    })
                }

                val body = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url("https://fcm.googleapis.com/fcm/send")
                    .header("Authorization", "key=$FCM_SERVER_KEY")
                    .post(body)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    Log.d(TAG, "FCM Push Response: ${response.code}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "FCM Push dispatch error: ${e.message}")
            }
        }
    }

    // =========================================================================
    // 👁️ মেসেজ দেখার সাথে সাথে সিন (✓✓) করার ফাংশন
    // =========================================================================
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
            } catch (e: Exception) {
                Log.w(TAG, "Failed to mark messages as read: ${e.message}")
            }
        }
    }

    fun joinGroup(userId: String, userName: String, userAvatar: String?) {
        if (userId.isBlank()) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val memberData = hashMapOf(
                    "userId" to userId,
                    "userName" to userName,
                    "userAvatar" to userAvatar,
                    "joinedAt" to System.currentTimeMillis(),
                    "lastActive" to System.currentTimeMillis()
                )
                firestore.collection(MEMBERS_COLLECTION).document(userId).set(memberData)
                toggleGroupNotification(true)
                subscribeToUserTopic(userId)
            } catch (_: Exception) {}
        }
    }

    fun leaveGroup(userId: String) {
        if (userId.isBlank()) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                firestore.collection(MEMBERS_COLLECTION).document(userId).delete()
                toggleGroupNotification(false)
            } catch (_: Exception) {}
        }
    }

    fun pingUserPresence(userId: String, userName: String, userAvatar: String? = null) {
        if (userId.isBlank()) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val memberData = hashMapOf(
                    "userId" to userId,
                    "userName" to userName,
                    "userAvatar" to userAvatar,
                    "lastActive" to System.currentTimeMillis()
                )
                firestore.collection(MEMBERS_COLLECTION).document(userId).set(memberData, com.google.firebase.firestore.SetOptions.merge())
                subscribeToUserTopic(userId)
            } catch (_: Exception) {}
        }
    }

    fun getLiveGroupStatsFlow(): Flow<LiveGroupStats> = callbackFlow {
        val listener = firestore.collection(MEMBERS_COLLECTION)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val total = snapshot.size().coerceAtLeast(1)
                val now = System.currentTimeMillis()
                val online = snapshot.documents.count { doc ->
                    val lastActive = doc.getLong("lastActive") ?: 0L
                    (now - lastActive) < 180_000L
                }.coerceAtLeast(1)
                trySend(LiveGroupStats(totalMembers = total, onlineMembers = online))
            }
        awaitClose { listener.remove() }
    }

    fun getLiveMessagesFlow(): Flow<List<ChatMessage>> = callbackFlow {
        val listenerRegistration = firestore.collection(CHAT_COLLECTION)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limitToLast(150)
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
                "videoUrl" to null,
                "audioUrl" to null,
                "mediaDurationSec" to 0L,
                "viewsCount" to 1L,
                "replyToId" to replyToMessage?.id,
                "replyToName" to replyToMessage?.senderName,
                "replyToText" to (replyToMessage?.text?.ifBlank { "Message" }),
                "isRead" to false, // শুরুতে ১টি টিক
                "readBy" to listOf<String>(),
                "timestamp" to FieldValue.serverTimestamp()
            )
            firestore.collection(CHAT_COLLECTION).add(messageData).await()
            setUserActionStatus(senderId, senderName, "idle")
            pingUserPresence(senderId, senderName, senderAvatar)

            if (replyToMessage != null && replyToMessage.senderId != senderId) {
                sendReplyPushNotification(
                    targetUserId = replyToMessage.senderId,
                    senderName = senderName,
                    replyMessageText = text.trim()
                )
            }

            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun uploadImageAndSendMessage(
        context: Context,
        imageUri: Uri,
        senderId: String,
        senderName: String,
        senderEmail: String?,
        senderAvatar: String?,
        isVip: Boolean,
        captionText: String = "",
        replyToMessage: ChatMessage? = null,
        onError: ((String) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(imageUri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close() ?: return@withContext false

            val scaled = scaleBitmapDown(originalBitmap, 1024)
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 78, baos)
            val bytes = baos.toByteArray()

            val reqBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("type", "image")
                .addFormDataPart("file", "chat_${System.currentTimeMillis()}.jpg", bytes.toRequestBody("image/jpeg".toMediaTypeOrNull()))
                .build()

            val res = httpClient.newCall(Request.Builder().url(R2_WORKER_UPLOAD_URL).post(reqBody).build()).execute()
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
                "imageUrl" to mediaUrl,
                "videoUrl" to null,
                "audioUrl" to null,
                "mediaDurationSec" to 0L,
                "viewsCount" to 1L,
                "replyToId" to replyToMessage?.id,
                "replyToName" to replyToMessage?.senderName,
                "replyToText" to (replyToMessage?.text?.ifBlank { "📷 Photo" }),
                "isRead" to false,
                "readBy" to listOf<String>(),
                "timestamp" to FieldValue.serverTimestamp()
            )
            firestore.collection(CHAT_COLLECTION).add(messageData).await()
            setUserActionStatus(senderId, senderName, "idle")
            pingUserPresence(senderId, senderName, senderAvatar)

            if (replyToMessage != null && replyToMessage.senderId != senderId) {
                sendReplyPushNotification(
                    targetUserId = replyToMessage.senderId,
                    senderName = senderName,
                    replyMessageText = if (captionText.isNotBlank()) captionText else "📷 Sent a photo"
                )
            }

            true
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onError?.invoke(e.localizedMessage ?: "Image upload failed") }
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
            val totalBytes = tempFile.length()

            val progressBody = ProgressRequestBody(
                file = tempFile,
                contentType = "video/mp4",
                onProgress = { bytesWritten ->
                    val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000.0).coerceAtLeast(0.1)
                    val speed = bytesWritten / elapsedSec
                    val remainingBytes = (totalBytes - bytesWritten).coerceAtLeast(0)
                    val remainingSec = if (speed > 0) (remainingBytes / speed).toLong() else 3L
                    val percent = ((bytesWritten * 100) / totalBytes).toInt().coerceIn(0, 100)
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
                "videoUrl" to mediaUrl,
                "audioUrl" to null,
                "mediaDurationSec" to 0L,
                "viewsCount" to 1L,
                "replyToId" to replyToMessage?.id,
                "replyToName" to replyToMessage?.senderName,
                "replyToText" to (replyToMessage?.text?.ifBlank { "🎬 Video" }),
                "isRead" to false,
                "readBy" to listOf<String>(),
                "timestamp" to FieldValue.serverTimestamp()
            )
            firestore.collection(CHAT_COLLECTION).add(messageData).await()
            setUserActionStatus(senderId, senderName, "idle")
            pingUserPresence(senderId, senderName, senderAvatar)

            if (replyToMessage != null && replyToMessage.senderId != senderId) {
                sendReplyPushNotification(
                    targetUserId = replyToMessage.senderId,
                    senderName = senderName,
                    replyMessageText = if (captionText.isNotBlank()) captionText else "🎬 Sent a video"
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
                "videoUrl" to null,
                "audioUrl" to mediaUrl,
                "mediaDurationSec" to durationSeconds,
                "viewsCount" to 1L,
                "replyToId" to replyToMessage?.id,
                "replyToName" to replyToMessage?.senderName,
                "replyToText" to (replyToMessage?.text?.ifBlank { "🎤 Voice message" }),
                "isRead" to false,
                "readBy" to listOf<String>(),
                "timestamp" to FieldValue.serverTimestamp()
            )
            firestore.collection(CHAT_COLLECTION).add(messageData).await()
            setUserActionStatus(senderId, senderName, "idle")
            pingUserPresence(senderId, senderName, senderAvatar)

            if (replyToMessage != null && replyToMessage.senderId != senderId) {
                sendReplyPushNotification(
                    targetUserId = replyToMessage.senderId,
                    senderName = senderName,
                    replyMessageText = "🎤 Sent a voice message (${durationSeconds}s)"
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
}
