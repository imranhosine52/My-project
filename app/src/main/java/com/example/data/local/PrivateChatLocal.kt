package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// =============================================================================
// 🗄️ ১. লোকাল ইনবক্স টেবিল (Local Inbox Entity)
// =============================================================================

@Entity(tableName = "local_inbox")
data class LocalInboxEntity(
    @PrimaryKey val chatRoomId: String,
    val peerId: String,
    val peerName: String,
    val peerAvatar: String?,
    val isVip: Boolean = false,
    val isOnline: Boolean = false,
    val lastMessage: String,
    val lastMediaType: String = "text",
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val timeFormatted: String
)

// =============================================================================
// 💬 ২. লোকাল প্রাইভেট মেসেজ টেবিল (Local Private Messages Entity)
// =============================================================================

@Entity(
    tableName = "local_private_messages",
    indices = [
        Index(value = ["chatRoomId", "id"]),
        Index(value = ["chatRoomId"])
    ]
)
data class LocalPrivateMessageEntity(
    @PrimaryKey val id: Long, // সার্ভারের ইউনিক মেসেজ আইডি
    val chatRoomId: String,
    val senderId: String,
    val receiverId: String,
    val messageText: String,
    val mediaUrl: String? = null,
    val mediaType: String = "text",
    val mediaDurationSec: Int = 0,
    val replyToId: String? = null,
    val status: Int = 1, // 1 = Sent (✓), 2 = Delivered (✓✓), 3 = Seen (Blue ✓✓)
    val createdAt: Long = System.currentTimeMillis(),
    val timeFormatted: String,
    val isPendingSync: Boolean = false // অফলাইনে পাঠানো হলে পরে অটো সিঙ্কের ফ্ল্যাগ
)

// =============================================================================
// ⚡ ৩. হাই-পারফরম্যান্স চ্যাট ও ইনবক্স ডাও (Private Chat DAO)
// =============================================================================

@Dao
interface PrivateChatDao {

    // --- ক) ইনবক্স কুয়েরি (Reactive Flow) ---

    @Query("SELECT * FROM local_inbox ORDER BY isPinned DESC, updatedAt DESC")
    fun getInboxFlow(): Flow<List<LocalInboxEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveInboxItems(items: List<LocalInboxEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveInboxItem(item: LocalInboxEntity)

    @Query("UPDATE local_inbox SET unreadCount = 0 WHERE chatRoomId = :chatRoomId")
    suspend fun clearUnreadCount(chatRoomId: String)

    @Query("DELETE FROM local_inbox WHERE chatRoomId = :chatRoomId")
    suspend fun deleteInboxItem(chatRoomId: String)


    // --- খ) মেসেজ হিস্ট্রি ও টেলিগ্রাম ডেল্টা সিঙ্ক কুয়েরি ---

    // রিয়েল-টাইম মেসেজ পর্যবেক্ষণ (নিচ থেকে উপরে সাজানো)
    @Query("SELECT * FROM local_private_messages WHERE chatRoomId = :chatRoomId ORDER BY id ASC")
    fun getMessagesFlow(chatRoomId: String): Flow<List<LocalPrivateMessageEntity>>

    // ফোনের শেষ মেসেজ আইডি বের করা (যাতে সার্ভার থেকে শুধু এর পরের নতুনগুলো আনা যায়)
    @Query("SELECT COALESCE(MAX(id), 0) FROM local_private_messages WHERE chatRoomId = :chatRoomId")
    suspend fun getLastMessageId(chatRoomId: String): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMessages(messages: List<LocalPrivateMessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMessage(message: LocalPrivateMessageEntity)

    // ব্লু-টিক বা সিন স্ট্যাটাস লোকাললি আপডেট করা
    @Query("UPDATE local_private_messages SET status = 3 WHERE chatRoomId = :chatRoomId AND receiverId = :myUserId")
    suspend fun markMessagesAsSeenLocally(chatRoomId: String, myUserId: String)

    @Query("DELETE FROM local_private_messages WHERE chatRoomId = :chatRoomId")
    suspend fun clearChatMessages(chatRoomId: String)
}
