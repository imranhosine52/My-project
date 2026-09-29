package com.example.util

import android.util.Log
import com.example.data.model.DirectChatMessageDto
import com.example.data.model.WebSocketChatFrame
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import java.util.concurrent.TimeUnit

/**
 * ⚡ Real-Time WebSocket Client Engine (Server Spec 7 - VPS 2)
 * Manages live connection to wss://api.playdramaflix.com/ws/chat/{user_id}
 */
object ChatWebSocketManager {
    private const val TAG = "ChatWebSocket"
    private const val WS_BASE_URL = "wss://api.playdramaflix.com/ws/chat/"

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }
    private val frameAdapter by lazy { moshi.adapter(WebSocketChatFrame::class.java) }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .pingInterval(15, TimeUnit.SECONDS) // Keep-Alive Heartbeat
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // Persistent streaming
            .retryOnConnectionFailure(true)
            .build()
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var currentWebSocket: WebSocket? = null
    private var activeUserId: Int? = null
    private var isManualDisconnect = false
    private var reconnectJob: Job? = null

    // Connection State Flow
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    // Incoming Raw Frames & Messages Flow
    private val _incomingFrames = MutableSharedFlow<WebSocketChatFrame>(extraBufferCapacity = 64)
    val incomingFrames: SharedFlow<WebSocketChatFrame> = _incomingFrames.asSharedFlow()

    private val _incomingMessages = MutableSharedFlow<DirectChatMessageDto>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<DirectChatMessageDto> = _incomingMessages.asSharedFlow()

    /**
     * 🔌 Connect to user-specific WebSocket channel
     */
    fun connect(userId: Int) {
        if (userId <= 0) return
        if (_isConnected.value && activeUserId == userId) return

        activeUserId = userId
        isManualDisconnect = false
        reconnectJob?.cancel()

        val socketUrl = "$WS_BASE_URL$userId"
        Log.d(TAG, "Connecting to WebSocket: $socketUrl")

        val request = Request.Builder()
            .url(socketUrl)
            .build()

        currentWebSocket?.cancel()
        currentWebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "✓ WebSocket Connected successfully for User: $userId")
                _isConnected.value = true
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch {
                    try {
                        val frame = frameAdapter.fromJson(text) ?: return@launch
                        _incomingFrames.emit(frame)

                        // If it's a chat message, convert to DTO and emit
                        if (frame.type == "message") {
                            val dto = DirectChatMessageDto(
                                id = "msg_${System.currentTimeMillis()}",
                                conversationId = frame.conversationId ?: "",
                                senderId = frame.senderId?.toString() ?: "",
                                senderName = frame.senderName ?: "User",
                                senderAvatar = frame.senderAvatar,
                                text = frame.text ?: "",
                                imageUrl = frame.imageUrl,
                                imageUrls = frame.imageUrls ?: emptyList(),
                                videoUrl = frame.videoUrl,
                                audioUrl = frame.audioUrl,
                                mediaDurationSec = frame.mediaDurationSec ?: 0L,
                                isRead = false,
                                timestamp = frame.timestamp
                            )
                            _incomingMessages.emit(dto)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Frame parse error: ${e.message}")
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                _isConnected.value = false
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _isConnected.value = false
                if (!isManualDisconnect) {
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket failure: ${t.message}")
                _isConnected.value = false
                if (!isManualDisconnect) {
                    scheduleReconnect()
                }
            }
        })
    }

    /**
     * 📤 Send Raw Frame
     */
    fun sendFrame(frame: WebSocketChatFrame): Boolean {
        return try {
            val jsonString = frameAdapter.toJson(frame)
            currentWebSocket?.send(jsonString) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Send frame failed: ${e.message}")
            false
        }
    }

    /**
     * 💬 Send Direct Text Message
     */
    fun sendTextMessage(
        conversationId: String,
        recipientId: String,
        senderId: String,
        senderName: String,
        senderAvatar: String?,
        text: String
    ): Boolean {
        val frame = WebSocketChatFrame(
            type = "message",
            conversationId = conversationId,
            senderId = senderId,
            recipientId = recipientId,
            senderName = senderName,
            senderAvatar = senderAvatar,
            text = text.trim(),
            timestamp = System.currentTimeMillis()
        )
        return sendFrame(frame)
    }

    /**
     * ✍️ Send Live Typing / Audio Recording status
     */
    fun sendActionStatus(
        conversationId: String,
        recipientId: String,
        senderId: String,
        action: String // "typing", "recording", "idle"
    ) {
        val frame = WebSocketChatFrame(
            type = "typing",
            conversationId = conversationId,
            senderId = senderId,
            recipientId = recipientId,
            action = action
        )
        sendFrame(frame)
    }

    /**
     * 👁️ Send Read Receipt
     */
    fun sendReadReceipt(conversationId: String, senderId: String, recipientId: String) {
        val frame = WebSocketChatFrame(
            type = "read",
            conversationId = conversationId,
            senderId = senderId,
            recipientId = recipientId
        )
        sendFrame(frame)
    }

    /**
     * 🔄 Auto-reconnect with 3-second delay
     */
    private fun scheduleReconnect() {
        if (isManualDisconnect) return
        val uid = activeUserId ?: return

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(3000L)
            if (!isManualDisconnect && !_isConnected.value) {
                Log.d(TAG, "Attempting WebSocket auto-reconnect for User: $uid...")
                connect(uid)
            }
        }
    }

    /**
     * 🛑 Disconnect cleanly
     */
    fun disconnect() {
        isManualDisconnect = true
        reconnectJob?.cancel()
        try {
            currentWebSocket?.close(1000, "User logged out / app closed")
        } catch (_: Exception) {}
        currentWebSocket = null
        _isConnected.value = false
        activeUserId = null
    }
}
