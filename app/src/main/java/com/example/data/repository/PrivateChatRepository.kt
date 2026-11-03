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

        // 🔑 দুই ইউজারের আইডি দিয়ে একটি স্থায়ী ইউনিক চ্যাট রুম আইডি তৈরি
        fun getChatRoomId(user1Id: String, user2Id: String): String {
            val u1 = user1Id.filter { it.isDigit() }.toLongOrNull() ?: 0L
            val u2 = user2Id.filter { it.isDigit() }.toLongOrNull() ?: 0L
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
                        peerAvatar = item.peerAvatar,
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
    // 💬 ২. মেসেজ হিস্ট্রি ও ডেল্টা সিঙ্ক (Telegram Delta Sync)
    // =========================================================================

    // নির্দিষ্ট চ্যাট রুমের সমস্ত মেসেজ ফোন মেমোরি থেকে লাইভ ফ্লো
    fun getMessagesFlow(chatRoomId: String): Flow<List<LocalPrivateMessageEntity>> {
        return chatDao.getMessagesFlow(chatRoomId)
    }

    // শুধুমাত্র নতুন মেসেজ সার্ভার থেকে সিঙ্ক করা (জিরো ব্যান্ডউইথ ও জিরো লোড)
    suspend fun syncMessagesDelta(chatRoomId: String): Result<Int> = withContext(Dispatchers.IO) {
        if (chatRoomId.isBlank()) return@withContext Result.failure(Exception("Invalid room id"))
        try {
            // ফোনে থাকা শেষ মেসেজের আইডি বের করা
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
    // 🚀 ৩. অপটিমিস্টিক সেন্ড (০-সেকেন্ডে স্ক্রিনে দেখানো + ব্যাকগ্রাউন্ড সেন্ড)
    // =========================================================================

    suspend fun sendMessage(
        senderId: String,
        receiverId: String,
        messageText: String,
        mediaUrl: String? = null,
        mediaType: String = "text",
        mediaDurationSec: Int = 0,
        replyToId: String? = null
    ): Result<LocalPrivateMessageEntity> = withContext(Dispatchers.IO) {
        val chatRoomId = getChatRoomId(senderId, receiverId)
        val tempId = System.currentTimeMillis() // অস্থায়ী ইউনিক আইডি

        // ১. অপটিমিস্টিক লোকাল মেসেজ তৈরি (সাথে সাথে স্ক্রিনে দেখানোর জন্য)
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

        // ফোনে তাৎক্ষণিক সেভ (ইউজার চোখের পলকে মেসেজ দেখতে পাবে)
        chatDao.saveMessage(localOptimisticMessage)

        // ২. সার্ভার API-তে ব্যাকগ্রাউন্ডে পাঠানো
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

                // অস্থায়ী আইডি মুছে আসল সার্ভার আইডি ও স্ট্যাটাস দিয়ে রিপ্লেস
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
    // 👁️ ৪. ব্লু-টিক / সিন মেথড (Seen Confirmation)
    // =========================================================================

    suspend fun markChatAsSeen(myUserId: String, peerId: String) = withContext(Dispatchers.IO) {
        val chatRoomId = getChatRoomId(myUserId, peerId)
        try {
            // ফোনে সাথে সাথে আনরিড কাউন্ট ০ এবং মেসেজ স্ট্যাটাস Seen (3) করা
            chatDao.clearUnreadCount(chatRoomId)
            chatDao.markMessagesAsSeenLocally(chatRoomId, myUserId)

            // সার্ভারে রিকোয়েস্ট পাঠানো
            apiService.markChatAsSeen(MarkSeenRequest(myUserId, peerId))
        } catch (e: Exception) {
            Log.w(TAG, "Mark seen offline notice: ${e.message}")
        }
    }

    // চ্যাট হিস্ট্রি সম্পূর্ণ মুছে ফেলা
    suspend fun clearChatLocally(chatRoomId: String) = withContext(Dispatchers.IO) {
        chatDao.clearChatMessages(chatRoomId)
        chatDao.deleteInboxItem(chatRoomId)
    }
}
