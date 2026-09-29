--- START OF FILE ui/viewmodel/ReelsViewModel.kt ---

package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.CreatorPageDto
import com.example.data.model.ReelVideoQuality
import com.example.data.model.UserProfileMetricsDto
import com.example.data.model.UserReelDto
import com.example.data.repository.ReelsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReelsFeedUiState(
    val isLoading: Boolean = true,
    val activeTab: String = "for_you",
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

data class UserProfileUiState(
    val isLoading: Boolean = false,
    val profile: UserProfileMetricsDto? = null,
    val isUploadingAvatar: Boolean = false,
    val isUploadingCover: Boolean = false,
    val errorMessage: String? = null
)

class ReelsViewModel(
    val repository: ReelsRepository
) : ViewModel() {

    private val _feedState = MutableStateFlow(ReelsFeedUiState())
    val feedState: StateFlow<ReelsFeedUiState> = _feedState.asStateFlow()

    private val _uploadState = MutableStateFlow(ReelUploadUiState())
    val uploadState: StateFlow<ReelUploadUiState> = _uploadState.asStateFlow()

    private val _profileState = MutableStateFlow(UserProfileUiState())
    val profileState: StateFlow<UserProfileUiState> = _profileState.asStateFlow()

    private val viewedReelIds = mutableSetOf<Int>()

    init {
        loadFeed(tab = "for_you")
        checkMyCreatorPage()
    }

    // =========================================================================
    // 👑 ১. REAL-TIME PROFILE METRICS (Server Spec 1)
    // =========================================================================
    fun loadUserProfileMetrics(targetUserId: Int) {
        viewModelScope.launch {
            _profileState.update { it.copy(isLoading = it.profile == null, errorMessage = null) }
            val result = repository.getUserProfileMetrics(targetUserId)

            if (result.isSuccess) {
                _profileState.update {
                    it.copy(
                        isLoading = false,
                        profile = result.getOrNull(),
                        errorMessage = null
                    )
                }
            } else {
                _profileState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to load profile"
                    )
                }
            }
        }
    }

    // 🎯 প্রোফাইল স্ক্রিন থেকে ফলো টগল এবং ফিডের রিলসে তাৎক্ষণিক স্টেট সিঙ্ক
    fun toggleFollowUser(targetUserId: Int) {
        val currentProfile = _profileState.value.profile ?: return
        val currentIsFollowing = currentProfile.isFollowing
        val newIsFollowing = !currentIsFollowing
        val delta = if (newIsFollowing) 1L else -1L
        val newFollowersCount = (currentProfile.followersCount + delta).coerceAtLeast(0L)

        // ১. প্রোফাইল স্ক্রিন তাৎক্ষণিক আপডেট
        _profileState.update { state ->
            state.copy(
                profile = currentProfile.copy(
                    rawIsFollowing = newIsFollowing,
                    rawFollowersCount = newFollowersCount
                )
            )
        }

        // ২. ফিডের সব রিলসে এই ক্রিয়েটরের ফলো বাটন সাথে সাথে সিঙ্ক করা
        _feedState.update { state ->
            val updatedList = state.reels.map { reel ->
                if (reel.userId == targetUserId || (reel.pageId > 0 && reel.pageId == targetUserId)) {
                    reel.copy(isFollowing = newIsFollowing)
                } else {
                    reel
                }
            }
            state.copy(reels = updatedList)
        }

        // ৩. ব্যাকগ্রাউন্ডে সার্ভার এপিআই কল
        viewModelScope.launch {
            val result = repository.toggleFollowPage(
                pageId = currentProfile.pageId ?: targetUserId,
                targetUserId = targetUserId
            )
            if (result.isFailure) {
                // ব্যর্থ হলে রোলব্যাক
                _profileState.update { state -> state.copy(profile = currentProfile) }
                _feedState.update { state ->
                    val rollbackList = state.reels.map { reel ->
                        if (reel.userId == targetUserId || (reel.pageId > 0 && reel.pageId == targetUserId)) {
                            reel.copy(isFollowing = currentIsFollowing)
                        } else reel
                    }
                    state.copy(reels = rollbackList)
                }
            }
        }
    }

    // 🎯 ফিড স্ক্রিনের রিলস থেকে ফলো টগল (pageId এবং userId ফলব্যাক সহ)
    fun toggleFollowCreator(pageId: Int, targetUserId: Int = pageId) {
        val resolvedPageId = if (pageId > 0) pageId else targetUserId
        val resolvedUserId = if (targetUserId > 0) targetUserId else pageId

        // ১. ফিডে অপটিমিস্টিক আপডেট
        var previousState = false
        _feedState.update { state ->
            val targetReel = state.reels.find {
                (resolvedPageId > 0 && it.pageId == resolvedPageId) || it.userId == resolvedUserId
            }
            previousState = targetReel?.isFollowing ?: false
            val newState = !previousState

            val updated = state.reels.map { reel ->
                if ((resolvedPageId > 0 && reel.pageId == resolvedPageId) || reel.userId == resolvedUserId) {
                    reel.copy(isFollowing = newState)
                } else {
                    reel
                }
            }
            state.copy(reels = updated)
        }

        // ২. প্রোফাইল স্ক্রিন যদি খোলা থাকে সেটিও সিঙ্ক করা
        val curProfile = _profileState.value.profile
        if (curProfile != null && (curProfile.userId == resolvedUserId || curProfile.pageId == resolvedPageId)) {
            val delta = if (!previousState) 1L else -1L
            _profileState.update { state ->
                state.copy(
                    profile = curProfile.copy(
                        rawIsFollowing = !previousState,
                        rawFollowersCount = (curProfile.followersCount + delta).coerceAtLeast(0L)
                    )
                )
            }
        }

        // ৩. ব্যাকগ্রাউন্ডে সার্ভার সিঙ্ক
        viewModelScope.launch {
            val result = repository.toggleFollowPage(resolvedPageId, resolvedUserId)
            if (result.isFailure) {
                // রোলব্যাক
                _feedState.update { state ->
                    val rollback = state.reels.map { reel ->
                        if ((resolvedPageId > 0 && reel.pageId == resolvedPageId) || reel.userId == resolvedUserId) {
                            reel.copy(isFollowing = previousState)
                        } else reel
                    }
                    state.copy(reels = rollback)
                }
                if (curProfile != null && (curProfile.userId == resolvedUserId || curProfile.pageId == resolvedPageId)) {
                    _profileState.update { it.copy(profile = curProfile) }
                }
            }
        }
    }

    // =========================================================================
    // 🖼️ ২. AVATAR & COVER UPLOAD (Server Spec 2)
    // =========================================================================
    fun uploadAvatar(imageUri: Uri, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _profileState.update { it.copy(isUploadingAvatar = true) }
            val result = repository.uploadUserAvatar(imageUri)
            _profileState.update { it.copy(isUploadingAvatar = false) }

            if (result.isSuccess) {
                val newAvatarUrl = result.getOrNull()
                _profileState.update { state ->
                    state.copy(
                        profile = state.profile?.copy(avatar = newAvatarUrl)
                    )
                }
                onComplete(true, newAvatarUrl)
            } else {
                val err = result.exceptionOrNull()?.message ?: "Avatar upload failed"
                onComplete(false, err)
            }
        }
    }

    fun uploadCover(imageUri: Uri, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _profileState.update { it.copy(isUploadingCover = true) }
            val result = repository.uploadUserCover(imageUri)
            _profileState.update { it.copy(isUploadingCover = false) }

            if (result.isSuccess) {
                val newCoverUrl = result.getOrNull()
                _profileState.update { state ->
                    state.copy(
                        profile = state.profile?.copy(cover = newCoverUrl)
                    )
                }
                onComplete(true, newCoverUrl)
            } else {
                val err = result.exceptionOrNull()?.message ?: "Cover upload failed"
                onComplete(false, err)
            }
        }
    }

    // =========================================================================
    // 🎬 ৩. REELS FEED & INTERACTIONS
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

    fun setVideoQuality(quality: ReelVideoQuality) {
        _feedState.update { it.copy(selectedQuality = quality) }
    }

    fun trackReelView(reelId: Int) {
        if (viewedReelIds.add(reelId)) {
            viewModelScope.launch {
                repository.interactReel(reelId = reelId, type = "view")
            }
        }
    }

    fun toggleLike(reel: UserReelDto) {
        val currentLiked = reel.isLiked
        val newLiked = !currentLiked
        val updatedLikesCount = if (newLiked) reel.likesCount + 1 else (reel.likesCount - 1).coerceAtLeast(0)

        _feedState.update { state ->
            val updatedList = state.reels.map {
                if (it.id == reel.id) it.copy(isLiked = newLiked, rawLikesCount = updatedLikesCount)
                else it
            }
            state.copy(reels = updatedList)
        }

        viewModelScope.launch {
            val res = repository.interactReel(reelId = reel.id, type = "like")
            if (res.isFailure) {
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

    fun shareReel(context: Context, reel: UserReelDto) {
        _feedState.update { state ->
            val updatedList = state.reels.map {
                if (it.id == reel.id) it.copy(rawSharesCount = it.sharesCount + 1)
                else it
            }
            state.copy(reels = updatedList)
        }

        viewModelScope.launch {
            repository.interactReel(reelId = reel.id, type = "share")
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "Watch this trending reel by ${reel.pageName} (${reel.displayHandle}) on PlayDramaFlix:\n${reel.videoUrl}"
            )
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Reel via"))
    }

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
                loadFeed(tab = "for_you")
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
