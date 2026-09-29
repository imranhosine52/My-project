package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.data.model.*
import com.example.data.remote.ReelsApiClient
import com.example.util.ChatWebSocketManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class ChatRepository(
    private val context: Context,
    private val authRepository: AuthRepository = AuthRepository(context)
) {
    companion object {
        private const val TAG = "ChatRepository"
        private const val VPS2_API_BASE = "https://api.playdramaflix.com/api/v1/"
    }

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private val inboxAdapter by lazy { moshi.adapter(InboxConversationsResponse::class.java) }
    private val messagesAdapter by lazy { moshi.adapter(ChatMessagesResponse::class.java) }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun getCurrentUserId(): Int {
        return authRepository.getSavedUserId().filter { it.isDigit() }.toIntOrNull() ?: 0
    }

    // =========================================================================
    // 🔌 ১. WEBSOCKET CONNECTION & OBSERVABLES (Server Spec 7)
    // =========================================================================
    fun connectLiveSocket() {
        val myId = getCurrentUserId()
        if (myId > 0) {
            ChatWebSocketManager.connect(myId)
        }
    }

    fun disconnectLiveSocket() {
        ChatWebSocketManager.disconnect()
    }

    val isSocketConnected: StateFlow<Boolean> = ChatWebSocketManager.isConnected
    val incomingLiveMessages: SharedFlow<DirectChatMessageDto> = ChatWebSocketManager.incomingMessages
    val incomingLiveFrames: SharedFlow<WebSocketChatFrame> = ChatWebSocketManager.incomingFrames

    // =========================================================================
    // 📥 ২. INBOX CONVERSATIONS REST API (Server Spec 7)
    // =========================================================================
    suspend fun getInboxConversations(): Result<List<DirectConversationItem>> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        if (userId <= 0) {
            return@withContext Result.success(emptyList())
        }

        val url = "${VPS2_API_BASE}chat/conversations?user_id=$userId"

        try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()

            val response = httpClient.newCall(request).execute()
            val bodyString = response.body?.string().orEmpty()
            response.close()

            if (response.isSuccessful && bodyString.isNotBlank()) {
                val parsed = inboxAdapter.fromJson(bodyString)
                if (parsed != null && parsed.success) {
                    Result.success(parsed.conversations)
                } else {
                    Result.success(emptyList())
                }
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Inbox fetch error: ${e.message}")
            Result.failure(e)
        }
    }

    // =========================================================================
    // 💬 ৩. 1-ON-1 CHAT MESSAGES HISTORY (Server Spec 7)
    // =========================================================================
    suspend fun getChatMessages(conversationId: String): Result<List<DirectChatMessageDto>> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        val url = "${VPS2_API_BASE}chat/messages?conversation_id=$conversationId&user_id=$userId"

        try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()

            val response = httpClient.newCall(request).execute()
            val bodyString = response.body?.string().orEmpty()
            response.close()

            if (response.isSuccessful && bodyString.isNotBlank()) {
                val parsed = messagesAdapter.fromJson(bodyString)
                if (parsed != null && parsed.success) {
                    Result.success(parsed.messages)
                } else {
                    Result.success(emptyList())
                }
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Chat messages fetch error: ${e.message}")
            Result.failure(e)
        }
    }

    // =========================================================================
    // 📤 ৪. WEBSOCKET MESSAGE SENDER
    // =========================================================================
    fun sendDirectTextMessage(
        conversationId: String,
        recipientId: String,
        text: String,
        senderName: String,
        senderAvatar: String?
    ): Boolean {
        val myId = getCurrentUserId().toString()
        return ChatWebSocketManager.sendTextMessage(
            conversationId = conversationId,
            recipientId = recipientId,
            senderId = myId,
            senderName = senderName,
            senderAvatar = senderAvatar,
            text = text
        )
    }

    fun sendTypingStatus(conversationId: String, recipientId: String, isTyping: Boolean) {
        val myId = getCurrentUserId().toString()
        ChatWebSocketManager.sendActionStatus(
            conversationId = conversationId,
            recipientId = recipientId,
            senderId = myId,
            action = if (isTyping) "typing" else "idle"
        )
    }

    // =========================================================================
    // 🎙️ ৫. VOICE NOTE & IMAGE UPLOADER (VPS 2 R2 Ingest)
    // =========================================================================
    suspend fun uploadVoiceNote(audioFile: File): Result<String> = withContext(Dispatchers.IO) {
        val uploadUrl = "${VPS2_API_BASE}chat/upload-audio"

        try {
            val reqBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("type", "audio")
                .addFormDataPart(
                    "file",
                    audioFile.name,
                    audioFile.asRequestBody("audio/mp4".toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder().url(uploadUrl).post(reqBody).build()
            val response = httpClient.newCall(request).execute()
            val bodyString = response.body?.string().orEmpty()
            response.close()

            val json = JSONObject(bodyString)
            val audioUrl = json.optString("mediaUrl").ifBlank { json.optString("audioUrl") }

            if (audioUrl.isNotBlank()) {
                Result.success(audioUrl)
            } else {
                Result.failure(Exception("Audio URL not returned by server"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadChatImage(imageUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        val uploadUrl = "${VPS2_API_BASE}chat/upload-image"

        try {
            val inputStream = context.contentResolver.openInputStream(imageUri) ?: return@withContext Result.failure(Exception("Cannot open image"))
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (originalBitmap == null) return@withContext Result.failure(Exception("Invalid image"))

            val maxDimension = 1080
            val width = originalBitmap.width
            val height = originalBitmap.height
            val ratio = width.toFloat() / height.toFloat()
            val scaledBitmap = if (width > maxDimension || height > maxDimension) {
                if (width > height) {
                    Bitmap.createScaledBitmap(originalBitmap, maxDimension, (maxDimension / ratio).toInt().coerceAtLeast(1), true)
                } else {
                    Bitmap.createScaledBitmap(originalBitmap, (maxDimension * ratio).toInt().coerceAtLeast(1), maxDimension, true)
                }
            } else originalBitmap

            val baos = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
            val bytes = baos.toByteArray()

            val tempFile = File(context.cacheDir, "chat_img_${System.currentTimeMillis()}.jpg")
            FileOutputStream(tempFile).use { it.write(bytes) }

            val reqBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("type", "image")
                .addFormDataPart(
                    "file",
                    tempFile.name,
                    tempFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder().url(uploadUrl).post(reqBody).build()
            val response = httpClient.newCall(request).execute()
            val bodyString = response.body?.string().orEmpty()
            response.close()
            tempFile.delete()

            val json = JSONObject(bodyString)
            val imgUrl = json.optString("mediaUrl").ifBlank { json.optString("imageUrl") }

            if (imgUrl.isNotBlank()) {
                Result.success(imgUrl)
            } else {
                Result.failure(Exception("Image URL not returned by server"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
