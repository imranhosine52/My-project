package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
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
    val suggestedPages: List<SuggestedPageDto> = emptyList(),
    val isSuggestedPagesLoading: Boolean = false,
    val selectedQuality: ReelVideoQuality = ReelVideoQuality.QUALITY_720P,
    val errorMessage: String? = null
)

data class ReelUploadUiState(
    val isCheckingPage: Boolean = true,
    val creatorPage: CreatorPageDto? = null,
    val myPlaylists: List<CreatorPlaylistDto> = emptyList(),
    val isPlaylistsLoading: Boolean = false,
    val isCreatingPlaylist: Boolean = false,
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

    init {
        loadFeed(tab = "for_you")
        loadSuggestedPages()
        checkMyCreatorPage()
    }

    // =========================================================================
    // 👁️ ১. ২৪ ঘণ্টার লোকাল ভিউ ট্র্যাকার (ডাটাবেজ কল ছাড়া লোকাল সেভ)
    // =========================================================================
    /**
     * ভিডিও দেখলে সরাসরি সার্ভারে কোনো রিকোয়েস্ট যাবে না।
     * প্রথমে লোকাল মেমোরিতে ভিউ জমা হবে।
     * প্রতি ১২ ঘণ্টা (২৪ ঘণ্টায় ২ বার) পর পর স্বয়ংক্রিয়ভাবে সার্ভার ডাটাবেজে পুশ হবে।
     */
    fun trackReelView(reelId: Int) {
        viewModelScope.launch {
            val isEligibleNewView = repository.reelFeedRepository.recordReelViewLocal(reelId)

            // যদি ২৪ ঘণ্টা পার হওয়ার কারণে নতুন ভিউ পাওয়ার যোগ্য হয়, তবে UI-তে +১ ভিউ আপডেট হবে
            if (isEligibleNewView) {
                _feedState.update { state ->
                    val updatedReels = state.reels.map { reel ->
                        if (reel.id == reelId) {
                            reel.copy(rawViewsCount = reel.viewsCount + 1L)
                        } else reel
                    }
                    state.copy(reels = updatedReels)
                }
            }
        }
    }

    // =========================================================================
    // ❤️ ২. ডুপ্লিকেট-প্রুফ লাইক মেকানিজম (Strict +1 / -1 Toggle)
    // =========================================================================
    fun toggleLike(reel: UserReelDto) {
        val targetReelId = reel.id
        val currentReel = _feedState.value.reels.find { it.id == targetReelId } ?: reel
        val wasLiked = currentReel.isLiked

        // স্টেট টগল: আগে লাইক থাকলে এখন আনলাইক, আগে না থাকলে লাইক
        val newLikedState = !wasLiked
        // গাণিতিক সুরক্ষা: লাইক হলে ঠিক +১, আনলাইক হলে ঠিক -১
        val delta = if (newLikedState) 1L else -1L
        val calculatedLikesCount = (currentReel.likesCount + delta).coerceAtLeast(0L)

        // অপটিমিস্টিক UI আপডেট (তাত্ক্ষণিক রেসপন্স)
        _feedState.update { state ->
            val updated = state.reels.map {
                if (it.id == targetReelId) {
                    it.copy(isLiked = newLikedState, rawLikesCount = calculatedLikesCount)
                } else it
            }
            state.copy(reels = updated)
        }

        viewModelScope.launch {
            val res = repository.interactReel(reelId = targetReelId, type = "like")
            if (res.isFailure) {
                // সার্ভারে ব্যর্থ হলে পূর্বের অবস্থায় রোলব্যাক
                _feedState.update { state ->
                    val rollback = state.reels.map {
                        if (it.id == targetReelId) {
                            it.copy(isLiked = wasLiked, rawLikesCount = currentReel.likesCount)
                        } else it
                    }
                    state.copy(reels = rollback)
                }
            }
        }
    }

    // =========================================================================
    // ➕ ৩. ডুপ্লিকেট-প্রুফ ফলো মেকানিজম (Strict +1 / -1 Follow/Unfollow)
    // =========================================================================
    fun toggleFollowCreator(pageId: Int, targetUserId: Int = pageId) {
        val resolvedPageId = if (pageId > 0) pageId else targetUserId
        val resolvedUserId = if (targetUserId > 0) targetUserId else pageId

        // বর্তমান ফলো স্টেট বের করা
        val targetReel = _feedState.value.reels.find {
            (resolvedPageId > 0 && it.pageId == resolvedPageId) || it.userId == resolvedUserId
        }
        val wasFollowing = targetReel?.isFollowing
            ?: _feedState.value.suggestedPages.find { it.pageId == resolvedPageId }?.isFollowing
            ?: false

        val newFollowingState = !wasFollowing
        val delta = if (newFollowingState) 1L else -1L

        // ১. ফিডের সমস্ত রিলসে ফলো স্টেট সিঙ্ক করা
        _feedState.update { state ->
            val updatedReels = state.reels.map { reel ->
                if ((resolvedPageId > 0 && reel.pageId == resolvedPageId) || reel.userId == resolvedUserId) {
                    reel.copy(isFollowing = newFollowingState)
                } else reel
            }

            // ২. সাজেস্টেড পেজ লিস্টেও ফলোয়ার্স সংখ্যা নিখুঁত +১ / -১ করা
            val updatedPages = state.suggestedPages.map { page ->
                if (page.pageId == resolvedPageId || page.userId == resolvedUserId) {
                    page.copy(
                        rawIsFollowing = newFollowingState,
                        rawFollowersCount = (page.followersCount + delta).coerceAtLeast(0L)
                    )
                } else page
            }

            state.copy(reels = updatedReels, suggestedPages = updatedPages)
        }

        // ৩. ওপেন করা প্রোফাইল অবজেক্টেও ফলোয়ার্স সংখ্যা সিঙ্ক
        val curProfile = _profileState.value.profile
        if (curProfile != null && (curProfile.userId == resolvedUserId || curProfile.pageId == resolvedPageId)) {
            _profileState.update { state ->
                state.copy(
                    profile = curProfile.copy(
                        rawIsFollowing = newFollowingState,
                        rawFollowersCount = (curProfile.followersCount + delta).coerceAtLeast(0L)
                    )
                )
            }
        }

        // ৪. সার্ভারে কল পাঠানো
        viewModelScope.launch {
            val result = repository.toggleFollowPage(resolvedPageId, resolvedUserId)
            if (result.isFailure) {
                // ব্যর্থ হলে রোলব্যাক
                _feedState.update { state ->
                    val rollbackReels = state.reels.map { reel ->
                        if ((resolvedPageId > 0 && reel.pageId == resolvedPageId) || reel.userId == resolvedUserId) {
                            reel.copy(isFollowing = wasFollowing)
                        } else reel
                    }
                    val rollbackPages = state.suggestedPages.map { page ->
                        if (page.pageId == resolvedPageId || page.userId == resolvedUserId) {
                            page.copy(
                                rawIsFollowing = wasFollowing,
                                rawFollowersCount = (page.followersCount - delta).coerceAtLeast(0L)
                            )
                        } else page
                    }
                    state.copy(reels = rollbackReels, suggestedPages = rollbackPages)
                }
                if (curProfile != null && (curProfile.userId == resolvedUserId || curProfile.pageId == resolvedPageId)) {
                    _profileState.update { it.copy(profile = curProfile) }
                }
            }
        }
    }

    fun toggleFollowSuggestedPage(pageId: Int, userId: Int = pageId) {
        toggleFollowCreator(pageId, userId)
    }

    // =========================================================================
    // 🌟 ৪. SUGGESTED CREATORS & PAGES
    // =========================================================================
    fun loadSuggestedPages() {
        viewModelScope.launch {
            _feedState.update { it.copy(isSuggestedPagesLoading = it.suggestedPages.isEmpty()) }
            val result = repository.getSuggestedPages()
            if (result.isSuccess) {
                _feedState.update {
                    it.copy(
                        isSuggestedPagesLoading = false,
                        suggestedPages = result.getOrDefault(emptyList())
                    )
                }
            } else {
                _feedState.update { it.copy(isSuggestedPagesLoading = false) }
            }
        }
    }

    // =========================================================================
    // 👑 ৫. REAL-TIME PROFILE METRICS
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

    // =========================================================================
    // 🖼️ ৬. AVATAR & COVER UPLOAD
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
    // 📺 ৭. SERIES & PLAYLIST MANAGEMENT
    // =========================================================================
    fun loadCreatorPlaylists(pageId: Long) {
        viewModelScope.launch {
            _uploadState.update { it.copy(isPlaylistsLoading = true) }
            val result = repository.getPlaylists(pageId)
            if (result.isSuccess) {
                _uploadState.update {
                    it.copy(
                        isPlaylistsLoading = false,
                        myPlaylists = result.getOrDefault(emptyList())
                    )
                }
            } else {
                _uploadState.update { it.copy(isPlaylistsLoading = false) }
            }
        }
    }

    fun loadCreatorPlaylists(pageId: Int) = loadCreatorPlaylists(pageId.toLong())

    fun createSeriesWorkflow(
        pageId: Long,
        title: String,
        description: String? = null,
        posterUri: Uri? = null,
        bannerUri: Uri? = null,
        onComplete: (Boolean, Int?, String?) -> Unit = { _, _, _ -> }
    ) {
        viewModelScope.launch {
            _uploadState.update { it.copy(isCreatingPlaylist = true, errorMessage = null) }
            val result = repository.createSeriesWorkflow(
                pageId = pageId,
                title = title,
                description = description,
                posterUri = posterUri,
                bannerUri = bannerUri
            )
            _uploadState.update { it.copy(isCreatingPlaylist = false) }

            if (result.isSuccess) {
                val resp = result.getOrNull()
                val newPlaylistId = resp?.playlistId
                loadCreatorPlaylists(pageId)
                onComplete(true, newPlaylistId, resp?.message ?: "Series created successfully!")
            } else {
                val err = result.exceptionOrNull()?.message ?: "Failed to create series"
                _uploadState.update { it.copy(errorMessage = err) }
                onComplete(false, null, err)
            }
        }
    }

    fun createPlaylist(
        pageId: Int,
        title: String,
        description: String? = null,
        onComplete: (Boolean, Int?, String?) -> Unit = { _, _, _ -> }
    ) = createSeriesWorkflow(
        pageId = pageId.toLong(),
        title = title,
        description = description,
        posterUri = null,
        bannerUri = null,
        onComplete = onComplete
    )

    suspend fun getPlaylistEpisodes(playlistId: Int): List<UserReelDto> {
        return repository.getPlaylistReels(playlistId).getOrDefault(emptyList())
    }

    // =========================================================================
    // 🎬 ৮. REELS FEED & INTERACTIONS
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
                "Watch this trending reel by ${reel.pageName} (${reel.displayHandle}) on PlayDramaFlix:\n${reel.shareUrl}"
            )
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Reel via"))
    }

    fun checkMyCreatorPage() {
        viewModelScope.launch {
            _uploadState.update { it.copy(isCheckingPage = true) }
            val pageResult = repository.getMyCreatorPage()
            val page = pageResult.getOrNull()
            _uploadState.update { 
                it.copy(
                    isCheckingPage = false,
                    creatorPage = page
                ) 
            }
            if (page != null && page.id > 0) {
                loadCreatorPlaylists(page.id)
            }
        }
    }

    fun uploadVideoReel(
        title: String?,
        description: String?,
        playlistId: Int? = null,
        episodeNum: Int = 1,
        videoUri: Uri,
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
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
                playlistId = playlistId,
                episodeNum = episodeNum,
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
