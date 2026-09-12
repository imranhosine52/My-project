package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.data.model.ChatMessage
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class UserChatStatus(
    val userId: String = "",
    val userName: String = "",
    val action: String = "idle"
)

object FirebaseChatManager {
    private const val TAG = "FirebaseChatManager"
    private const val CHAT_COLLECTION = "community_global_chat"
    private const val STATUS_COLLECTION = "community_user_live_status"
    private const val NOTIF_TOPIC = "community_group_notifications"

    // 👑 ৩ নম্বর ছবির রুট এডমিন/ওনার ইমেইল
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
            .writeTimeout(90, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun toggleGroupNotification(enable: Boolean) {
        try {
            if (enable) FirebaseMessaging.getInstance().subscribeToTopic(NOTIF_TOPIC)
            else FirebaseMessaging.getInstance().unsubscribeFromTopic(NOTIF_TOPIC)
        } catch (_: Exception) {}
    }

    fun getLiveMessagesFlow(): Flow<List<ChatMessage>> = callbackFlow {
        val listenerRegistration = firestore.collection(CHAT_COLLECTION)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limitToLast(120)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val messages = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(ChatMessage::class.java)?.copy(id = doc.id)
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
                    val isFresh = (System.currentTimeMillis() - time) < 5000L
                    if (uid.isNotBlank() && uid != currentUserId && action != "idle" && isFresh) {
                        UserChatStatus(uid, name, action)
                    } else null
                }
                trySend(activeList)
            }
        awaitClose { listener.remove() }
    }

    suspend fun setUserActionStatus(userId: String, userName: String, action: String) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        try {
            val data = hashMapOf(
                "userId" to userId,
                "userName" to userName,
                "action" to action,
                "updatedAt" to System.currentTimeMillis()
            )
            firestore.collection(STATUS_COLLECTION).document(userId).set(data).await()
        } catch (_: Exception) {}
    }

    /**
     * 👁️ সিন কাউন্টার বাড়ানো
     */
    suspend fun recordMessageSeen(messageId: String) = withContext(Dispatchers.IO) {
        if (messageId.isBlank()) return@withContext
        try {
            firestore.collection(CHAT_COLLECTION).document(messageId)
                .update("viewsCount", FieldValue.increment(1))
        } catch (_: Exception) {}
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
                "viewsCount" to (1L..5L).random(), // ইনিশিয়াল সিন কাউন্টার
                "replyToId" to replyToMessage?.id,
                "replyToName" to replyToMessage?.senderName,
                "replyToText" to (replyToMessage?.text?.ifBlank { "Message" }),
                "timestamp" to FieldValue.serverTimestamp()
            )
            firestore.collection(CHAT_COLLECTION).add(messageData).await()
            setUserActionStatus(senderId, senderName, "idle")
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
                "timestamp" to FieldValue.serverTimestamp()
            )
            firestore.collection(CHAT_COLLECTION).add(messageData).await()
            setUserActionStatus(senderId, senderName, "idle")
            true
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onError?.invoke(e.localizedMessage ?: "Image upload failed") }
            false
        }
    }

    suspend fun uploadVideoAndSendMessage(
        context: Context,
        videoUri: Uri,
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
            var fileSize = 0L
            context.contentResolver.query(videoUri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst() && sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
            }
            if (fileSize > MAX_VIDEO_SIZE_BYTES) {
                withContext(Dispatchers.Main) { onError?.invoke("⚠️ Video file exceeds 50 MB limit!") }
                return@withContext false
            }

            val tempFile = File(context.cacheDir, "vid_${System.currentTimeMillis()}.mp4")
            context.contentResolver.openInputStream(videoUri)?.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
            }

            val reqBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("type", "video")
                .addFormDataPart("file", tempFile.name, tempFile.asRequestBody("video/mp4".toMediaTypeOrNull()))
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
                "imageUrl" to null,
                "videoUrl" to mediaUrl,
                "audioUrl" to null,
                "mediaDurationSec" to 0L,
                "viewsCount" to 1L,
                "replyToId" to replyToMessage?.id,
                "replyToName" to replyToMessage?.senderName,
                "replyToText" to (replyToMessage?.text?.ifBlank { "🎬 Video" }),
                "timestamp" to FieldValue.serverTimestamp()
            )
            firestore.collection(CHAT_COLLECTION).add(messageData).await()
            setUserActionStatus(senderId, senderName, "idle")
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
                "timestamp" to FieldValue.serverTimestamp()
            )
            firestore.collection(CHAT_COLLECTION).add(messageData).await()
            setUserActionStatus(senderId, senderName, "idle")
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

    private fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap
        val ratio = width.toFloat() / height.toFloat()
        return Bitmap.createScaledBitmap(bitmap, if (width > height) maxDimension else (maxDimension * ratio).toInt(), if (width > height) (maxDimension / ratio).toInt() else maxDimension, true)
    }
}
