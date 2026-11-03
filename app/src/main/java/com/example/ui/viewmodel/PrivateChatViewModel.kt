package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.LocalInboxEntity
import com.example.data.local.LocalPrivateMessageEntity
import com.example.data.model.SearchedUserDto
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
    val selectedTab: Int = 0, // 0 = "Chats", 1 = "Find User by ID"
    val searchQuery: String = "",
    val rawInbox: List<LocalInboxEntity> = emptyList(),
    val filteredInbox: List<LocalInboxEntity> = emptyList(),
    // 🔍 ৮-ডিজিট আইডি দিয়ে ইউজার সার্চ স্টেট
    val searchAccountIdInput: String = "",
    val isSearchingUser: Boolean = false,
    val searchedUser: SearchedUserDto? = null,
    val searchUserError: String? = null,
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
                            item.lastMessage.contains(current.searchQuery, ignoreCase = true) ||
                            (item.chatRoomId.contains(current.searchQuery, ignoreCase = true))
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

    fun selectInboxTab(tabIndex: Int) {
        _inboxUiState.update { it.copy(selectedTab = tabIndex) }
    }

    fun onSearchQueryChanged(query: String) {
        _inboxUiState.update { current ->
            val filtered = if (query.isBlank()) {
                current.rawInbox
            } else {
                current.rawInbox.filter { item ->
                    item.peerName.contains(query, ignoreCase = true) ||
                    item.lastMessage.contains(query, ignoreCase = true) ||
                    (item.chatRoomId.contains(query, ignoreCase = true))
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
    // 🔍 ৪. ৮-ডিজিট একাউন্ট আইডি দিয়ে ইউজার সার্চ
    // =========================================================================

    fun onSearchAccountIdInputChanged(id: String) {
        _inboxUiState.update {
            it.copy(
                searchAccountIdInput = id.filter { char -> char.isDigit() }.take(8),
                searchUserError = null
            )
        }
    }

    fun searchUserByAccountId(myUserId: String) {
        val accountId = _inboxUiState.value.searchAccountIdInput.trim()
        if (accountId.length < 3) {
            _inboxUiState.update { it.copy(searchUserError = "Please enter a valid Account ID") }
            return
        }

        viewModelScope.launch {
            _inboxUiState.update {
                it.copy(
                    isSearchingUser = true,
                    searchedUser = null,
                    searchUserError = null
                )
            }

            val result = repository.privateChatRepository.searchUserByAccountId(accountId)

            if (result.isSuccess) {
                val user = result.getOrNull()
                if (user != null && user.peerId == myUserId) {
                    _inboxUiState.update {
                        it.copy(
                            isSearchingUser = false,
                            searchedUser = null,
                            searchUserError = "You cannot message your own Account ID 😊"
                        )
                    }
                } else {
                    _inboxUiState.update {
                        it.copy(
                            isSearchingUser = false,
                            searchedUser = user,
                            searchUserError = null
                        )
                    }
                }
            } else {
                val err = result.exceptionOrNull()?.message ?: "No user found with ID: $accountId"
                _inboxUiState.update {
                    it.copy(
                        isSearchingUser = false,
                        searchedUser = null,
                        searchUserError = err
                    )
                }
            }
        }
    }

    fun clearSearchedUser() {
        _inboxUiState.update {
            it.copy(
                searchAccountIdInput = "",
                searchedUser = null,
                searchUserError = null,
                isSearchingUser = false
            )
        }
    }

    // =========================================================================
    // 💬 ৫. 1-to-1 চ্যাট রুম ওপেন এবং টেলিগ্রাম ডেল্টা সিঙ্ক
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

        // ২. আল্ট্রা-লাইটওয়েট ব্যাকগ্রাউন্ড ডেল্টা সিঙ্ক
        startDeltaSyncPolling(chatRoomId, myUserId, peerId)
    }

    private fun startDeltaSyncPolling(chatRoomId: String, myUserId: String, peerId: String) {
        deltaSyncPollingJob?.cancel()
        deltaSyncPollingJob = viewModelScope.launch(Dispatchers.IO) {
            repository.syncPrivateChatMessagesDelta(chatRoomId)

            while (isActive) {
                delay(3000L)
                val result = repository.syncPrivateChatMessagesDelta(chatRoomId)
                if (result.isSuccess && (result.getOrNull() ?: 0) > 0) {
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
    // 🚀 ৬. মেসেজ পাঠানো (Optimistic Send + ইনবক্স সাথে সাথে আপডেট)
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

        val peerName = _chatRoomUiState.value.peerName
        val peerAvatar = _chatRoomUiState.value.peerAvatar
        val isPeerVip = _chatRoomUiState.value.isPeerVip
        val isPeerOnline = _chatRoomUiState.value.isPeerOnline

        _chatRoomUiState.update { it.copy(isSending = true, replyingToMessage = null) }

        viewModelScope.launch {
            // 🎯 পার্টনারের নাম ও ছবিসহ পাঠানো হচ্ছে যাতে ইনবক্স সাথে সাথে আপডেট হয়
            repository.privateChatRepository.sendMessage(
                senderId = senderId,
                receiverId = receiverId,
                messageText = messageText.trim(),
                mediaUrl = mediaUrl,
                mediaType = mediaType,
                mediaDurationSec = mediaDurationSec,
                replyToId = replyId,
                peerName = peerName,
                peerAvatar = peerAvatar,
                isPeerVip = isPeerVip,
                isPeerOnline = isPeerOnline
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
