package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.data.model.ChatMessage
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

object FirebaseChatManager {
    private const val TAG = "FirebaseChatManager"
    private const val CHAT_COLLECTION = "community_global_chat"

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }

    /**
     * ⚡ রিয়েল-টাইম মেসেজ স্ট্রিম (যেকোনো ইউজার মেসেজ পাঠালে সাথে সাথে লাইভ ফ্লো হবে)
     */
    fun getLiveMessagesFlow(): Flow<List<ChatMessage>> = callbackFlow {
        val listenerRegistration = firestore.collection(CHAT_COLLECTION)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limitToLast(100) // সর্বশেষ ১০০টি মেসেজ লাইভ রাখবে
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
     * ✍️ টেক্সট মেসেজ পাঠানো
     */
    suspend fun sendTextMessage(
        senderId: String,
        senderName: String,
        senderAvatar: String?,
        isVip: Boolean,
        text: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val messageData = hashMapOf(
                "senderId" to senderId,
                "senderName" to senderName,
                "senderAvatar" to senderAvatar,
                "isVip" to isVip,
                "text" to text.trim(),
                "imageUrl" to null,
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
     * 🖼️ সুপার-ফাস্ট ছবি কম্প্রেস ও আপলোড করে মেসেজ পাঠানো
     */
    suspend fun uploadImageAndSendMessage(
        context: Context,
        imageUri: Uri,
        senderId: String,
        senderName: String,
        senderAvatar: String?,
        isVip: Boolean,
        captionText: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // ১. ছবি দ্রুত আপলোডের জন্য কম্প্রেস করা (ম্যাক্সিমাম ১০২৪px এবং ৮০% কোয়ালিটি)
            val inputStream = context.contentResolver.openInputStream(imageUri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) return@withContext false

            val scaledBitmap = scaleBitmapDown(originalBitmap, 1024)
            val baos = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 78, baos)
            val imageData = baos.toByteArray()

            // ২. ফায়ারবেস ক্লাউড স্টোরেজে আপলোড
            val filename = "chat_images/${UUID.randomUUID()}.jpg"
            val storageRef = storage.reference.child(filename)
            storageRef.putBytes(imageData).await()

            val downloadUrl = storageRef.downloadUrl.await().toString()

            // ৩. মেসেজ হিসেবে ফায়ারস্টোরে সেভ
            val messageData = hashMapOf(
                "senderId" to senderId,
                "senderName" to senderName,
                "senderAvatar" to senderAvatar,
                "isVip" to isVip,
                "text" to captionText.trim(),
                "imageUrl" to downloadUrl,
                "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            firestore.collection(CHAT_COLLECTION).add(messageData).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading image to chat: ${e.message}")
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
