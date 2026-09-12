package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.data.model.ChatMessage
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
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
    val action: String = "idle" // "typing", "recording", "uploading_video", "idle"
)

object FirebaseChatManager {
    private const val TAG = "FirebaseChatManager"
    private const val CHAT_COLLECTION = "community_global_chat"
    private const val STATUS_COLLECTION = "community_user_live_status"

    // 🔗 আপনার লাইভ ক্লাউডফ্লেয়ার ওয়ার্কারের লিংক
    private const val R2_WORKER_UPLOAD_URL = "https://dramaflixbucket.imranhosine52.workers.dev"

    const val MAX_VIDEO_SIZE_BYTES = 50L * 1024L * 1024L // 50 MB

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * ⚡ রিয়েল-টাইম চ্যাট মেসেজ স্ট্রিম
     */
    fun getLiveMessagesFlow(): Flow<List<ChatMessage>> = callbackFlow {
        val listenerRegistration = firestore.collection(CHAT_COLLECTION)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limitToLast(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Chat listen failed: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val messages = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(ChatMessage::class.java)
                    }
                    trySend(messages)
                }
            }
        awaitClose { listenerRegistration.remove() }
    }

    /**
     * 📡 লাইভ টাইপিং ও অ্যাকশন পর্যবেক্ষণ (অন্য কেউ টাইপিং বা অডিও রেকর্ড করলে)
     */
    fun getLiveActiveActionUsersFlow(currentUserId: String): Flow<List<UserChatStatus>> = callbackFlow {
        val listener = firestore.collection(STATUS_COLLECTION)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                val activeList = snapshot.documents.mapNotNull { doc ->
                    val uid = doc.getString("userId") ?: ""
                    val name = doc.getString("userName") ?: "Someone"
                    val action = doc.getString("action") ?: "idle"
                    val time = doc.getLong("updatedAt") ?: 0L

                    // ৫ সেকেন্ডের বেশি পুরনো হলে অগ্রাহ্য করবে
                    val isFresh = (System.currentTimeMillis() - time) < 5000L

                    if (uid.isNotBlank() && uid != currentUserId && action != "idle" && isFresh) {
                        UserChatStatus(uid, name, action)
                    } else null
                }
                trySend(activeList)
            }
        awaitClose { listener.remove() }
    }

    /**
     * 🔄 নিজের বর্তমান অ্যাকশন আপডেট করা
     */
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

    suspend fun sendTextMessage(
        senderId: String,
        senderName: String,
        senderAvatar: String?,
        isVip: Boolean,
        text: String,
        replyToMessage: ChatMessage? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            setUserActionStatus(senderId, senderName, "idle")
            val messageData = hashMapOf(
                "senderId" to senderId,
                "senderName" to senderName,
                "senderAvatar" to senderAvatar,
                "isVip" to isVip,
                "text" to text.trim(),
                "imageUrl" to null,
                "videoUrl" to null,
                "audioUrl" to null,
                "replyToId" to replyToMessage?.id,
                "replyToName" to replyToMessage?.senderName,
                "replyToText" to (replyToMessage?.text?.ifBlank { "Attachment" }),
                "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            firestore.collection(CHAT_COLLECTION).add(messageData).await()
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
        senderAvatar: String?,
        isVip: Boolean,
        captionText: String = "",
        replyToMessage: ChatMessage? = null,
        onError: ((String) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(imageUri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) {
                withContext(Dispatchers.Main) { onError?.invoke("Cannot read image") }
                return@withContext false
            }

            val scaledBitmap = scaleBitmapDown(originalBitmap, 1024)
            val baos = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 78, baos)
            val imageData = baos.toByteArray()

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("type", "image")
                .addFormDataPart("file", "chat_${System.currentTimeMillis()}.jpg", imageData.toRequestBody("image/jpeg".toMediaTypeOrNull()))
                .build()

            val request = Request.Builder().url(R2_WORKER_UPLOAD_URL).post(requestBody).build()
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            val json = JSONObject(responseBody)
            val mediaUrl = json.optString("mediaUrl").ifBlank { json.optString("imageUrl") }

            if (mediaUrl.isBlank()) return@withContext false

            setUserActionStatus(senderId, senderName, "idle")

            val messageData = hashMapOf(
                "senderId" to senderId,
                "senderName" to senderName,
                "senderAvatar" to senderAvatar,
                "isVip" to isVip,
                "text" to captionText.trim(),
                "imageUrl" to mediaUrl,
                "videoUrl" to null,
                "audioUrl" to null,
                "replyToId" to replyToMessage?.id,
                "replyToName" to replyToMessage?.senderName,
                "replyToText" to (replyToMessage?.text?.ifBlank { "📷 Photo" }),
                "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            firestore.collection(CHAT_COLLECTION).add(messageData).await()
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
                if (cursor.moveToFirst() && sizeIndex != -1) {
                    fileSize = cursor.getLong(sizeIndex)
                }
            }

            if (fileSize > MAX_VIDEO_SIZE_BYTES) {
                withContext(Dispatchers.Main) { onError?.invoke("⚠️ ভিডিও ফাইল ৫০ MB এর চেয়ে বড় হতে পারবে না!") }
                return@withContext false
            }

            setUserActionStatus(senderId, senderName, "uploading_video")

            val tempFile = File(context.cacheDir, "vid_${System.currentTimeMillis()}.mp4")
            context.contentResolver.openInputStream(videoUri)?.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
            }

            val fileBody = tempFile.asRequestBody("video/mp4".toMediaTypeOrNull())
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("type", "video")
                .addFormDataPart("file", tempFile.name, fileBody)
                .build()

            val request = Request.Builder().url(R2_WORKER_UPLOAD_URL).post(requestBody).build()
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            tempFile.delete()

            val json = JSONObject(responseBody)
            val mediaUrl = json.optString("mediaUrl").ifBlank { json.optString("imageUrl") }

            if (mediaUrl.isBlank()) return@withContext false

            setUserActionStatus(senderId, senderName, "idle")

            val messageData = hashMapOf(
                "senderId" to senderId,
                "senderName" to senderName,
                "senderAvatar" to senderAvatar,
                "isVip" to isVip,
                "text" to captionText.trim(),
                "imageUrl" to null,
                "videoUrl" to mediaUrl,
                "audioUrl" to null,
                "replyToId" to replyToMessage?.id,
                "replyToName" to replyToMessage?.senderName,
                "replyToText" to (replyToMessage?.text?.ifBlank { "🎬 Video" }),
                "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            firestore.collection(CHAT_COLLECTION).add(messageData).await()
            true
        } catch (e: Exception) {
            setUserActionStatus(senderId, senderName, "idle")
            withContext(Dispatchers.Main) { onError?.invoke(e.localizedMessage ?: "Video upload failed") }
            false
        }
    }

    suspend fun uploadVoiceAndSendMessage(
        audioFile: File,
        durationSeconds: Int,
        senderId: String,
        senderName: String,
        senderAvatar: String?,
        isVip: Boolean,
        replyToMessage: ChatMessage? = null,
        onError: ((String) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val fileBody = audioFile.asRequestBody("audio/mp4".toMediaTypeOrNull())
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("type", "audio")
                .addFormDataPart("file", audioFile.name, fileBody)
                .build()

            val request = Request.Builder().url(R2_WORKER_UPLOAD_URL).post(requestBody).build()
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            audioFile.delete()

            val json = JSONObject(responseBody)
            val mediaUrl = json.optString("mediaUrl").ifBlank { json.optString("imageUrl") }

            if (mediaUrl.isBlank()) return@withContext false

            setUserActionStatus(senderId, senderName, "idle")

            val messageData = hashMapOf(
                "senderId" to senderId,
                "senderName" to senderName,
                "senderAvatar" to senderAvatar,
                "isVip" to isVip,
                "text" to "",
                "imageUrl" to null,
                "videoUrl" to null,
                "audioUrl" to mediaUrl,
                "mediaDurationSec" to durationSeconds,
                "replyToId" to replyToMessage?.id,
                "replyToName" to replyToMessage?.senderName,
                "replyToText" to (replyToMessage?.text?.ifBlank { "🎤 Voice message" }),
                "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            firestore.collection(CHAT_COLLECTION).add(messageData).await()
            true
        } catch (e: Exception) {
            setUserActionStatus(senderId, senderName, "idle")
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
        val targetWidth = if (width > height) maxDimension else (maxDimension * ratio).toInt()
        val targetHeight = if (width > height) (maxDimension / ratio).toInt() else maxDimension
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }
}
