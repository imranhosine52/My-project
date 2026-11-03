package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.LocalInboxEntity
import com.example.data.local.LocalPrivateMessageEntity
import com.example.data.repository.PlayDramaFlixRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// =============================================================================
// 📊 ১. ইনবক্স ও চ্যাট রুমের UI স্টেট ডেটা ক্লাস
// =============================================================================

data class PrivateInboxUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val searchQuery: String = "",
    val rawInbox: List<LocalInboxEntity> = emptyList(),
    val filteredInbox: List<LocalInboxEntity> = emptyList(),
    val errorMessage: String? = null
)

data class PrivateChatRoomUiState(
    val chatRoomId: String = "",
    val peerId: String = "",
    val peerName: String = "User",
    val peerAvatar: String? = null,
    val isPeerOnline: Boolean = false,
    val isPeerVip: Boolean = false,
    val messages: List<LocalPrivateMessageEntity> = emptyList(),
    val isSending: Boolean = false,
    val replyingToMessage: LocalPrivateMessageEntity? = null,
    val errorMessage: String? = null
)

// =============================================================================
// 🧠 ২. প্রাইভেট চ্যাট ভিউমডেল (State & Sync Engine)
// =============================================================================

class PrivateChatViewModel(
    private val repository: PlayDramaFlixRepository
) : ViewModel() {

    private val _inboxUiState = MutableStateFlow(PrivateInboxUiState(isLoading = true))
    val inboxUiState: StateFlow<PrivateInboxUiState> = _inboxUiState.asStateFlow()

    private val _chatRoomUiState = MutableStateFlow(PrivateChatRoomUiState())
    val chatRoomUiState: StateFlow<PrivateChatRoomUiState> = _chatRoomUiState.asStateFlow()

    private var messageObserverJob: Job? = null
    private var deltaSyncPollingJob: Job? = null

    init {
        observeLocalInbox()
    }

    // =========================================================================
    // 📥 ৩. ইনবক্স পর্যবেক্ষণ ও সিঙ্ক (Local-First Reactive Flow)
    // =========================================================================

    private fun observeLocalInbox() {
        viewModelScope.launch {
            repository.privateInboxFlow.collect { localList ->
                _inboxUiState.update { current ->
                    val filtered = if (current.searchQuery.isBlank()) {
                        localList
                    } else {
                        localList.filter { item ->
                            item.peerName.contains(current.searchQuery, ignoreCase = true) ||
                            item.lastMessage.contains(current.searchQuery, ignoreCase = true)
                        }
                    }
                    current.copy(
                        isLoading = false,
                        rawInbox = localList,
                        filteredInbox = filtered
                    )
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _inboxUiState.update { current ->
            val filtered = if (query.isBlank()) {
                current.rawInbox
            } else {
                current.rawInbox.filter { item ->
                    item.peerName.contains(query, ignoreCase = true) ||
                    item.lastMessage.contains(query, ignoreCase = true)
                }
            }
            current.copy(searchQuery = query, filteredInbox = filtered)
        }
    }

    fun refreshInbox(myUserId: String) {
        if (myUserId.isBlank()) return
        viewModelScope.launch {
            _inboxUiState.update { it.copy(isRefreshing = true) }
            repository.syncPrivateInbox(myUserId)
            _inboxUiState.update { it.copy(isRefreshing = false) }
        }
    }

    // =========================================================================
    // 💬 ৪. 1-to-1 চ্যাট রুম ওপেন এবং টেলিগ্রাম ডেল্টা সিঙ্ক
    // =========================================================================

    fun openChatRoom(
        myUserId: String,
        peerId: String,
        peerName: String,
        peerAvatar: String?,
        isPeerOnline: Boolean = false,
        isPeerVip: Boolean = false
    ) {
        val chatRoomId = repository.getPrivateChatRoomId(myUserId, peerId)

        _chatRoomUiState.update {
            it.copy(
                chatRoomId = chatRoomId,
                peerId = peerId,
                peerName = peerName,
                peerAvatar = peerAvatar,
                isPeerOnline = isPeerOnline,
                isPeerVip = isPeerVip,
                replyingToMessage = null,
                errorMessage = null
            )
        }

        // চ্যাটে প্রবেশ করামাত্র সাথে সাথে মেসেজগুলো Seen (✓✓) করে দেওয়া
        markAsSeen(myUserId, peerId)

        // ১. ফোনের Room ডেটাবেজ থেকে মেসেজ ফ্লো পর্যবেক্ষণ শুরু
        messageObserverJob?.cancel()
        messageObserverJob = viewModelScope.launch {
            repository.getPrivateChatMessagesFlow(chatRoomId).collect { msgList ->
                _chatRoomUiState.update { it.copy(messages = msgList) }
            }
        }

        // ২. আল্ট্রা-লাইটওয়েট ব্যাকগ্রাউন্ড ডেল্টা সিঙ্ক (প্রতি ৩ সেকেন্ডে শুধুমাত্র নতুন মেসেজ চেক)
        startDeltaSyncPolling(chatRoomId, myUserId, peerId)
    }

    private fun startDeltaSyncPolling(chatRoomId: String, myUserId: String, peerId: String) {
        deltaSyncPollingJob?.cancel()
        deltaSyncPollingJob = viewModelScope.launch(Dispatchers.IO) {
            // চ্যাটে ঢোকার সাথে সাথে একবার প্রথম সিঙ্ক
            repository.syncPrivateChatMessagesDelta(chatRoomId)

            while (isActive) {
                delay(3000L) // ৩ সেকেন্ড পর পর অতি ক্ষুদ্র (~১০০ বাইট) ডেল্টা সিঙ্ক
                val result = repository.syncPrivateChatMessagesDelta(chatRoomId)
                if (result.isSuccess && (result.getOrNull() ?: 0) > 0) {
                    // নতুন কোনো মেসেজ আসলে সাথে সাথে Seen করে দেওয়া
                    repository.markPrivateChatAsSeen(myUserId, peerId)
                }
            }
        }
    }

    fun closeChatRoom() {
        messageObserverJob?.cancel()
        deltaSyncPollingJob?.cancel()
        messageObserverJob = null
        deltaSyncPollingJob = null
        _chatRoomUiState.update { PrivateChatRoomUiState() }
    }

    // =========================================================================
    // 🚀 ৫. মেসেজ পাঠানো (Optimistic Send)
    // =========================================================================

    fun sendMessage(
        senderId: String,
        receiverId: String,
        messageText: String,
        mediaUrl: String? = null,
        mediaType: String = "text",
        mediaDurationSec: Int = 0
    ) {
        if (messageText.isBlank() && mediaUrl.isNullOrBlank()) return
        val replyId = _chatRoomUiState.value.replyingToMessage?.id?.toString()

        _chatRoomUiState.update { it.copy(isSending = true, replyingToMessage = null) }

        viewModelScope.launch {
            repository.sendPrivateChatMessage(
                senderId = senderId,
                receiverId = receiverId,
                messageText = messageText.trim(),
                mediaUrl = mediaUrl,
                mediaType = mediaType,
                mediaDurationSec = mediaDurationSec,
                replyToId = replyId
            )
            _chatRoomUiState.update { it.copy(isSending = false) }
        }
    }

    fun setReplyingTo(message: LocalPrivateMessageEntity?) {
        _chatRoomUiState.update { it.copy(replyingToMessage = message) }
    }

    fun markAsSeen(myUserId: String, peerId: String) {
        viewModelScope.launch {
            repository.markPrivateChatAsSeen(myUserId, peerId)
        }
    }

    fun clearChat(chatRoomId: String) {
        viewModelScope.launch {
            repository.clearPrivateChatLocally(chatRoomId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        closeChatRoom()
    }
}

// =============================================================================
// 🏭 ৩. ভিউমডেল ফ্যাক্টরি (Factory Provider)
// =============================================================================

class PrivateChatViewModelFactory(
    private val repository: PlayDramaFlixRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PrivateChatViewModel::class.java)) {
            return PrivateChatViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
