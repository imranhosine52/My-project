package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

object FirebaseChatManager {
    private const val TAG = "FirebaseChatManager"
    private const val CHAT_COLLECTION = "community_global_chat"

    // 🔗 আপনার লাইভ ক্লাউডফ্লেয়ার ওয়ার্কারের লিংক:
    private const val R2_WORKER_UPLOAD_URL = "https://dramaflixbucket.imranhosine52.workers.dev"

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(35, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    /**
     * ⚡ রিয়েল-টাইম ফায়ারস্টোর মেসেজ স্ট্রিম
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

        awaitClose {
            listenerRegistration.remove()
        }
    }

    /**
     * ✍️ সাধারণ টেক্সট মেসেজ পাঠানো
     */
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
                "replyToId" to replyToMessage?.id,
                "replyToName" to replyToMessage?.senderName,
                "replyToText" to (replyToMessage?.text?.ifBlank { "📷 Photo" }),
                "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            firestore.collection(CHAT_COLLECTION).add(messageData).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending text message: ${e.message}")
            false
        }
    }

    /**
     * 🚀 ক্লাউডফ্লেয়ার R2-তে ছবি আপলোড করে মেসেজ পাঠানো (মূল সার্ভারে ০% লোড)
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
            // ১. ছবি কম্প্রেস করা (ম্যাক্সিমাম ১০২৪px এবং ৭৮% কোয়ালিটি যাতে ১-২ সেকেন্ডে দ্রুত আপলোড হয়)
            val inputStream = context.contentResolver.openInputStream(imageUri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) {
                withContext(Dispatchers.Main) { onError?.invoke("Cannot read image file") }
                return@withContext false
            }

            val scaledBitmap = scaleBitmapDown(originalBitmap, 1024)
            val baos = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 78, baos)
            val imageData = baos.toByteArray()

            // ২. Cloudflare Worker-এ ফাইল পাঠানো
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    "chat_${System.currentTimeMillis()}.jpg",
                    imageData.toRequestBody("image/jpeg".toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url(R2_WORKER_UPLOAD_URL)
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful || responseBody.isBlank()) {
                withContext(Dispatchers.Main) { onError?.invoke("R2 Upload failed: HTTP ${response.code}") }
                return@withContext false
            }

            // ৩. Cloudflare R2 থেকে পাওয়া ছবির লিংকটি এক্সট্র্যাক্ট করা
            val json = JSONObject(responseBody)
            val r2ImageUrl = json.optString("imageUrl")

            if (r2ImageUrl.isBlank()) {
                withContext(Dispatchers.Main) { onError?.invoke("Could not retrieve R2 image URL") }
                return@withContext false
            }

            // ৪. ছবির লিংক দিয়ে ফায়ারস্টোরে মেসেজ সেভ
            val messageData = hashMapOf(
                "senderId" to senderId,
                "senderName" to senderName,
                "senderAvatar" to senderAvatar,
                "isVip" to isVip,
                "text" to captionText.trim(),
                "imageUrl" to r2ImageUrl,
                "replyToId" to replyToMessage?.id,
                "replyToName" to replyToMessage?.senderName,
                "replyToText" to (replyToMessage?.text?.ifBlank { "📷 Photo" }),
                "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            firestore.collection(CHAT_COLLECTION).add(messageData).await()
            true

        } catch (e: Exception) {
            Log.e(TAG, "Error uploading to R2 Worker: ${e.message}", e)
            withContext(Dispatchers.Main) {
                onError?.invoke(e.localizedMessage ?: "Upload error")
            }
            false
        }
    }

    /**
     * 🗑️ নিজের মেসেজ ডিলিট করা
     */
    suspend fun deleteMessage(messageId: String): Boolean = withContext(Dispatchers.IO) {
        if (messageId.isBlank()) return@withContext false
        try {
            firestore.collection(CHAT_COLLECTION).document(messageId).delete().await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting message: ${e.message}")
            false
        }
    }

    private fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxDimension && height <= maxDimension) return bitmap

        val ratio = width.toFloat() / height.toFloat()
        val targetWidth: Int
        val targetHeight: Int

        if (width > height) {
            targetWidth = maxDimension
            targetHeight = (maxDimension / ratio).toInt()
        } else {
            targetHeight = maxDimension
            targetWidth = (maxDimension * ratio).toInt()
        }

        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }
}
