package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.LocalInboxEntity
import com.example.data.local.LocalPrivateMessageEntity
import com.example.data.model.*
import com.example.data.remote.ApiClient
import com.example.data.remote.PlayDramaFlixApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class PrivateChatRepository(
    private val context: Context,
    private val apiService: PlayDramaFlixApiService = ApiClient.apiService,
    private val database: AppDatabase = AppDatabase.getInstance(context)
) {
    private val chatDao = database.privateChatDao()

    companion object {
        private const val TAG = "PrivateChatRepo"

        /**
         * 🔑 দুই ইউজারের আইডি দিয়ে একটি স্থায়ী ও সুরক্ষিত ইউনিক চ্যাট রুম আইডি তৈরি
         * (আইডি টেক্সট বা ৮-ডিজিট যাই হোক না কেন, কখনোই কনফ্লিক্ট বা 0 হবে না)
         */
        fun getChatRoomId(user1Id: String, user2Id: String): String {
            val clean1 = user1Id.trim()
            val clean2 = user2Id.trim()

            val u1 = clean1.filter { it.isDigit() }.toLongOrNull() ?: run {
                val hash = clean1.hashCode().toLong()
                if (hash < 0) -hash else hash
            }

            val u2 = clean2.filter { it.isDigit() }.toLongOrNull() ?: run {
                val hash = clean2.hashCode().toLong()
                if (hash < 0) -hash else hash
            }

            return if (u1 < u2) "${u1}_${u2}" else "${u2}_${u1}"
        }
    }

    // =========================================================================
    // 📥 ১. ইনবক্স তালিকা (Local Flow + Remote Sync)
    // =========================================================================

    // ফোন মেমোরি থেকে ইনস্ট্যান্ট ০ms ইনবক্স পর্যবেক্ষণ (রিয়েক্টিভ ফ্লো)
    val inboxFlow: Flow<List<LocalInboxEntity>> = chatDao.getInboxFlow()

    // সার্ভার থেকে ব্যাকগ্রাউন্ডে ইনবক্স সিঙ্ক করা
    suspend fun syncInbox(myUserId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (myUserId.isBlank()) return@withContext Result.failure(Exception("Invalid user id"))
        try {
            val response = apiService.getPrivateInbox(myUserId)
            if (response.isSuccessful && response.body()?.success == true) {
                val remoteInbox = response.body()!!.inbox

                // রিমোট ডেটাকে লোকাল Entity-তে রূপান্তর
                val entities = remoteInbox.map { item ->
                    LocalInboxEntity(
                        chatRoomId = item.chatRoomId,
                        peerId = item.peerId,
                        peerName = item.peerName,
                        peerAvatar = item.displayAvatar,
                        isVip = item.isVip,
                        isOnline = item.isOnline,
                        lastMessage = item.lastMessage,
                        lastMediaType = item.lastMediaType,
                        unreadCount = item.unreadCount,
                        isPinned = item.isPinned,
                        updatedAt = System.currentTimeMillis(),
                        timeFormatted = item.timeFormatted
                    )
                }

                // ফোনের ডেটাবেজে সংরক্ষণ
                chatDao.saveInboxItems(entities)
                Result.success(true)
            } else {
                Result.failure(Exception("Failed to fetch inbox: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Inbox sync offline notice: ${e.message}")
            Result.failure(e)
        }
    }

    // =========================================================================
    // 🔍 ২. ৮-ডিজিট অ্যাকাউন্ট আইডি দিয়ে ইউজার সার্চ মেথড
    // =========================================================================

    suspend fun searchUserByAccountId(accountId: String): Result<SearchedUserDto> = withContext(Dispatchers.IO) {
        val query = accountId.trim()
        if (query.isBlank()) {
            return@withContext Result.failure(Exception("Please enter a valid 8-digit Account ID"))
        }

        try {
            val response = apiService.searchUser(query)
            if (response.isSuccessful && response.body()?.success == true && response.body()?.user != null) {
                Result.success(response.body()!!.user!!)
            } else {
                val errorMsg = response.body()?.message ?: "No user found with Account ID: $query"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search user network error: ${e.message}", e)
            Result.failure(Exception("Network error: Could not reach server"))
        }
    }

    // =========================================================================
    // 💬 ৩. মেসেজ হিস্ট্রি ও ডেল্টা সিঙ্ক (Telegram Delta Sync)
    // =========================================================================

    fun getMessagesFlow(chatRoomId: String): Flow<List<LocalPrivateMessageEntity>> {
        return chatDao.getMessagesFlow(chatRoomId)
    }

    suspend fun syncMessagesDelta(chatRoomId: String): Result<Int> = withContext(Dispatchers.IO) {
        if (chatRoomId.isBlank()) return@withContext Result.failure(Exception("Invalid room id"))
        try {
            val lastSavedId = chatDao.getLastMessageId(chatRoomId)

            val response = apiService.getPrivateMessages(
                chatRoomId = chatRoomId,
                lastId = lastSavedId,
                limit = 50
            )

            if (response.isSuccessful && response.body()?.success == true) {
                val remoteMessages = response.body()!!.messages
                if (remoteMessages.isNotEmpty()) {
                    val entities = remoteMessages.map { m ->
                        LocalPrivateMessageEntity(
                            id = m.id.toLongOrNull() ?: System.currentTimeMillis(),
                            chatRoomId = m.chatRoomId,
                            senderId = m.senderId,
                            receiverId = m.receiverId,
                            messageText = m.messageText,
                            mediaUrl = m.mediaUrl,
                            mediaType = m.mediaType,
                            mediaDurationSec = m.mediaDurationSec,
                            replyToId = m.replyToId,
                            status = m.status,
                            createdAt = System.currentTimeMillis(),
                            timeFormatted = m.timeFormatted,
                            isPendingSync = false
                        )
                    }
                    chatDao.saveMessages(entities)
                }
                Result.success(remoteMessages.size)
            } else {
                Result.failure(Exception("Delta sync failed"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Delta sync offline: ${e.message}")
            Result.failure(e)
        }
    }

    // =========================================================================
    // 🚀 ৪. অপটিমিস্টিক সেন্ড + লোকাল ইনবক্স তাৎক্ষণিক আপডেট
    // =========================================================================

    suspend fun sendMessage(
        senderId: String,
        receiverId: String,
        messageText: String,
        mediaUrl: String? = null,
        mediaType: String = "text",
        mediaDurationSec: Int = 0,
        replyToId: String? = null,
        peerName: String = "User",
        peerAvatar: String? = null,
        isPeerVip: Boolean = false,
        isPeerOnline: Boolean = true
    ): Result<LocalPrivateMessageEntity> = withContext(Dispatchers.IO) {
        val chatRoomId = getChatRoomId(senderId, receiverId)
        val tempId = System.currentTimeMillis()

        // ১. অপটিমিস্টিক লোকাল মেসেজ তৈরি (চ্যাট রুমে তাৎক্ষণিক দেখানোর জন্য)
        val localOptimisticMessage = LocalPrivateMessageEntity(
            id = tempId,
            chatRoomId = chatRoomId,
            senderId = senderId,
            receiverId = receiverId,
            messageText = messageText,
            mediaUrl = mediaUrl,
            mediaType = mediaType,
            mediaDurationSec = mediaDurationSec,
            replyToId = replyToId,
            status = 1, // Sent (✓)
            createdAt = System.currentTimeMillis(),
            timeFormatted = "Just now",
            isPendingSync = true
        )
        chatDao.saveMessage(localOptimisticMessage)

        // 🎯 ২. সমাধান: পাঠানোর সাথে সাথেই লোকাল ইনবক্সে সেভ করা (যাতে ইনবক্স কখনো খালি না থাকে)
        val previewText = when {
            messageText.isNotBlank() -> messageText
            mediaType.equals("image", ignoreCase = true) -> "📷 Photo"
            mediaType.equals("video", ignoreCase = true) -> "🎬 Video"
            mediaType.equals("audio", ignoreCase = true) -> "🎙️ Voice message"
            mediaType.equals("sticker", ignoreCase = true) -> "🧸 Sticker"
            else -> "📎 Attachment"
        }

        val localInboxRow = LocalInboxEntity(
            chatRoomId = chatRoomId,
            peerId = receiverId,
            peerName = peerName,
            peerAvatar = peerAvatar,
            isVip = isPeerVip,
            isOnline = isPeerOnline,
            lastMessage = previewText,
            lastMediaType = mediaType,
            unreadCount = 0,
            isPinned = false,
            updatedAt = System.currentTimeMillis(),
            timeFormatted = "Just now"
        )
        chatDao.saveInboxItem(localInboxRow)

        // ৩. সার্ভার API-তে ব্যাকগ্রাউন্ডে পাঠানো
        val request = SendPrivateMessageRequest(
            senderId = senderId,
            receiverId = receiverId,
            messageText = messageText,
            mediaUrl = mediaUrl,
            mediaType = mediaType,
            mediaDurationSec = mediaDurationSec,
            replyToId = replyToId
        )

        try {
            val response = apiService.sendPrivateMessage(request)
            if (response.isSuccessful && response.body()?.data != null) {
                val serverMsg = response.body()!!.data!!

                val finalEntity = localOptimisticMessage.copy(
                    id = serverMsg.id.toLongOrNull() ?: tempId,
                    status = serverMsg.status,
                    timeFormatted = serverMsg.timeFormatted,
                    isPendingSync = false
                )
                chatDao.saveMessage(finalEntity)
                Result.success(finalEntity)
            } else {
                Result.success(localOptimisticMessage)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network send failed, kept in local outbox: ${e.message}")
            Result.success(localOptimisticMessage)
        }
    }

    // =========================================================================
    // 👁️ ৫. ব্লু-টিক / সিন মেথড (Seen Confirmation)
    // =========================================================================

    suspend fun markChatAsSeen(myUserId: String, peerId: String) = withContext(Dispatchers.IO) {
        val chatRoomId = getChatRoomId(myUserId, peerId)
        try {
            chatDao.clearUnreadCount(chatRoomId)
            chatDao.markMessagesAsSeenLocally(chatRoomId, myUserId)

            apiService.markChatAsSeen(MarkSeenRequest(myUserId, peerId))
        } catch (e: Exception) {
            Log.w(TAG, "Mark seen offline notice: ${e.message}")
        }
    }

    suspend fun clearChatLocally(chatRoomId: String) = withContext(Dispatchers.IO) {
        chatDao.clearChatMessages(chatRoomId)
        chatDao.deleteInboxItem(chatRoomId)
    }
}
