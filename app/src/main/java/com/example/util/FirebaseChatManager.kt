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

object FirebaseChatManager {
    private const val TAG = "FirebaseChatManager"
    private const val CHAT_COLLECTION = "community_global_chat"
    private const val R2_WORKER_UPLOAD_URL = "https://dramaflixbucket.imranhosine52.workers.dev"

    // ৫০ মেগাবাইট লিমিট (বাইটে)
    const val MAX_VIDEO_SIZE_BYTES = 50L * 1024L * 1024L

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS) // ৫০ এমবি ভিডিও আপলোডের জন্য পর্যাপ্ত সময়
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

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

    suspend fun sendTextMessage(
        senderId: String,
        senderName: String,
        senderAvatar: String?,
        isVip: Boolean,
        text: String,
        replyToMessage: ChatMessage? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
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

    /**
     * 🖼️ ছবি আপলোড
     */
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
            val mediaUrl = json.optString("mediaUrl")

            if (mediaUrl.isBlank()) return@withContext false

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
            withContext(Dispatchers.Main) { onError?.invoke(e.localizedMessage ?: "Upload failed") }
            false
        }
    }

    /**
     * 🎬 ৫০ MB-এর ভিডিও আপলোড লজিক
     */
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
            // ১. ফাইলের সাইজ যাচাই (৫০ এমবির কম কিনা)
            var fileSize = 0L
            context.contentResolver.query(videoUri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst() && sizeIndex != -1) {
                    fileSize = cursor.getLong(sizeIndex)
                }
            }

            if (fileSize > MAX_VIDEO_SIZE_BYTES) {
                withContext(Dispatchers.Main) {
                    onError?.invoke("⚠️ ভিডিও ফাইল ৫০ MB এর চেয়ে বড় হতে পারবে না!")
                }
                return@withContext false
            }

            // ২. ক্যাশে ফাইল কপি করে স্ট্রিম আপলোড
            val tempFile = File(context.cacheDir, "temp_upload_${System.currentTimeMillis()}.mp4")
            context.contentResolver.openInputStream(videoUri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            val fileBody = tempFile.asRequestBody("video/mp4".toMediaTypeOrNull())
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("type", "video")
                .addFormDataPart("file", "vid_${System.currentTimeMillis()}.mp4", fileBody)
                .build()

            val request = Request.Builder().url(R2_WORKER_UPLOAD_URL).post(requestBody).build()
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            tempFile.delete() // ক্যাশ মুছে ফেলা

            val json = JSONObject(responseBody)
            val mediaUrl = json.optString("mediaUrl")

            if (mediaUrl.isBlank()) return@withContext false

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
                "replyToText" to (replyToMessage?.text?.ifBlank { "🎥 Video" }),
                "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            firestore.collection(CHAT_COLLECTION).add(messageData).await()
            true
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onError?.invoke(e.localizedMessage ?: "Video upload failed") }
            false
        }
    }

    /**
     * 🎙️ ভয়েস মেসেজ আপলোড লজিক
     */
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
                .addFormDataPart("file", "voice_${System.currentTimeMillis()}.m4a", fileBody)
                .build()

            val request = Request.Builder().url(R2_WORKER_UPLOAD_URL).post(requestBody).build()
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            audioFile.delete()

            val json = JSONObject(responseBody)
            val mediaUrl = json.optString("mediaUrl")

            if (mediaUrl.isBlank()) return@withContext false

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
            withContext(Dispatchers.Main) { onError?.invoke(e.localizedMessage ?: "Voice upload failed") }
            false
        }
    }

    suspend fun deleteMessage(messageId: String): Boolean = withContext(Dispatchers.IO) {
        if (messageId.isBlank()) return@withContext false
        try {
            firestore.collection(CHAT_COLLECTION).document(messageId).delete().await()
            true
        } catch (e: Exception) {
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
