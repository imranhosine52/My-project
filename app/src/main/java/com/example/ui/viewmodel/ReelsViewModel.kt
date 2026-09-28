package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.CreatorPageDto
import com.example.data.model.ReelVideoQuality
import com.example.data.model.UserReelDto
import com.example.data.repository.ReelsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReelsFeedUiState(
    val isLoading: Boolean = true,
    val activeTab: String = "for_you", // "for_you" or "following"
    val reels: List<UserReelDto> = emptyList(),
    val selectedQuality: ReelVideoQuality = ReelVideoQuality.QUALITY_720P,
    val errorMessage: String? = null
)

data class ReelUploadUiState(
    val isCheckingPage: Boolean = true,
    val creatorPage: CreatorPageDto? = null,
    val isUploading: Boolean = false,
    val uploadProgress: Int = 0,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null
)

class ReelsViewModel(
    private val repository: ReelsRepository
) : ViewModel() {

    private val _feedState = MutableStateFlow(ReelsFeedUiState())
    val feedState: StateFlow<ReelsFeedUiState> = _feedState.asStateFlow()

    private val _uploadState = MutableStateFlow(ReelUploadUiState())
    val uploadState: StateFlow<ReelUploadUiState> = _uploadState.asStateFlow()

    // ডুপ্লিকেট ভিউ কল বন্ধ করার জন্য ট্র্যাক করা সেট
    private val viewedReelIds = mutableSetOf<Int>()

    init {
        loadFeed(tab = "for_you")
        checkMyCreatorPage()
    }

    // =========================================================================
    // 🌟 ১. রিলস ফিড ফেচিং (VPS 1)
    // =========================================================================
    fun loadFeed(tab: String = _feedState.value.activeTab) {
        viewModelScope.launch {
            _feedState.update { it.copy(isLoading = it.reels.isEmpty(), activeTab = tab, errorMessage = null) }
            val result = repository.getReelsFeed(tab = tab, page = 1)
            if (result.isSuccess) {
                _feedState.update { 
                    it.copy(
                        isLoading = false,
                        reels = result.getOrDefault(emptyList()),
                        errorMessage = null
                    ) 
                }
            } else {
                _feedState.update { 
                    it.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to load feed"
                    ) 
                }
            }
        }
    }

    // =========================================================================
    // 🎛️ ২. মাল্টি-কোয়ালিটি ভিডিও রেজোলিউশন চেঞ্জার
    // =========================================================================
    fun setVideoQuality(quality: ReelVideoQuality) {
        _feedState.update { it.copy(selectedQuality = quality) }
    }

    // =========================================================================
    // 👁️ ৩. স্বয়ংক্রিয় ভিউ ট্র্যাকিং
    // =========================================================================
    fun trackReelView(reelId: Int) {
        if (viewedReelIds.add(reelId)) {
            viewModelScope.launch {
                repository.interactReel(reelId = reelId, type = "view")
            }
        }
    }

    // =========================================================================
    // ❤️ ৪. অপটিমিস্টিক লাইক টগল (ডাবল-ট্যাপ ও হার্ট আইকন)
    // =========================================================================
    fun toggleLike(reel: UserReelDto) {
        val currentLiked = reel.isLiked
        val newLiked = !currentLiked
        val updatedLikesCount = if (newLiked) reel.likesCount + 1 else (reel.likesCount - 1).coerceAtLeast(0)

        // তৎক্ষণাৎ UI আপডেট
        _feedState.update { state ->
            val updatedList = state.reels.map {
                if (it.id == reel.id) it.copy(isLiked = newLiked, rawLikesCount = updatedLikesCount)
                else it
            }
            state.copy(reels = updatedList)
        }

        // ব্যাকগ্রাউন্ডে সার্ভারে রিকোয়েস্ট
        viewModelScope.launch {
            val res = repository.interactReel(reelId = reel.id, type = "like")
            if (res.isFailure) {
                // সার্ভারে এরর হলে পূর্বের অবস্থায় রোলব্যাক
                _feedState.update { state ->
                    val rollbackList = state.reels.map {
                        if (it.id == reel.id) it.copy(isLiked = currentLiked, rawLikesCount = reel.likesCount)
                        else it
                    }
                    state.copy(reels = rollbackList)
                }
            }
        }
    }

    // =========================================================================
    // ↗️ ৫. শেয়ার ট্র্যাকিং ও নেটিভ শেয়ার শিট
    // =========================================================================
    fun shareReel(context: Context, reel: UserReelDto) {
        // UI-তে শেয়ার কাউন্টার বাড়ানো
        _feedState.update { state ->
            val updatedList = state.reels.map {
                if (it.id == reel.id) it.copy(rawSharesCount = it.sharesCount + 1)
                else it
            }
            state.copy(reels = updatedList)
        }

        // সার্ভারে শেয়ার ইভেন্ট হিট
        viewModelScope.launch {
            repository.interactReel(reelId = reel.id, type = "share")
        }

        // অ্যান্ড্রয়েড শেয়ার শিট ওপেন
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "Watch this trending reel by ${reel.pageName} (${reel.displayHandle}) on PlayDramaFlix:\n${reel.rawVideoUrl}"
            )
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Reel via"))
    }

    // =========================================================================
    // ➕ ৬. ক্রিয়েটর পেজ ফলো / আনফলো
    // =========================================================================
    fun toggleFollowCreator(pageId: Int) {
        viewModelScope.launch {
            val result = repository.toggleFollowPage(pageId)
            if (result.isSuccess) {
                val isFollowingNow = result.getOrDefault(false)
                _feedState.update { state ->
                    val updated = state.reels.map {
                        if (it.pageId == pageId) it.copy(isFollowing = isFollowingNow)
                        else it
                    }
                    state.copy(reels = updated)
                }
            }
        }
    }

    // =========================================================================
    // 🔍 ৭. ক্রিয়েটর পেজ স্ট্যাটাস চেক
    // =========================================================================
    fun checkMyCreatorPage() {
        viewModelScope.launch {
            _uploadState.update { it.copy(isCheckingPage = true) }
            val pageResult = repository.getMyCreatorPage()
            _uploadState.update { 
                it.copy(
                    isCheckingPage = false,
                    creatorPage = pageResult.getOrNull()
                ) 
            }
        }
    }

    // =========================================================================
    // 🚀 ৮. লাইভ প্রোগ্রেস সহ ভিডিও আপলোড (VPS 2)
    // =========================================================================
    fun uploadVideoReel(
        title: String?,
        description: String?,
        videoUri: Uri,
        onComplete: (Boolean, String) -> Unit
    ) {
        val page = _uploadState.value.creatorPage
        if (page == null || !page.isApproved) {
            onComplete(false, "You must have an approved Creator Page to upload reels!")
            return
        }

        viewModelScope.launch {
            _uploadState.update { it.copy(isUploading = true, uploadProgress = 0, errorMessage = null) }

            val result = repository.uploadReel(
                pageId = page.id,
                title = title,
                description = description,
                videoUri = videoUri,
                onProgressUpdate = { percent ->
                    _uploadState.update { it.copy(uploadProgress = percent) }
                }
            )

            if (result.isSuccess) {
                val msg = result.getOrNull()?.message ?: "Reel uploaded successfully!"
                _uploadState.update { it.copy(isUploading = false, isSuccess = true, uploadProgress = 100) }
                onComplete(true, msg)
                loadFeed(tab = "for_you") // ফিড রিফ্রেশ
            } else {
                val err = result.exceptionOrNull()?.message ?: "Upload failed on server."
                _uploadState.update { it.copy(isUploading = false, errorMessage = err) }
                onComplete(false, err)
            }
        }
    }

    fun resetUploadState() {
        _uploadState.update { 
            it.copy(
                isUploading = false,
                uploadProgress = 0,
                isSuccess = false,
                errorMessage = null
            ) 
        }
    }
}

class ReelsViewModelFactory(
    private val repository: ReelsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReelsViewModel::class.java)) {
            return ReelsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
