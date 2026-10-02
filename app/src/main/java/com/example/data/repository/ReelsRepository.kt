package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.model.*
import com.example.data.remote.ReelsApiClient
import com.example.data.remote.ReelsApiService
import com.example.data.repository.reels.*

/**
 * 🎬 ReelsRepository (Master Unified Facade)
 * সমস্ত রিলস, ক্রিয়েটর পেজ, সোশ্যাল হাব ও সিরিজ সাব-রিপোজিটরির সেন্ট্রাল ফেসাড।
 * এটি সম্পূর্ণ Clean Architecture মেনে সব কলকে সংশ্লিষ্ট সাব-রিপোজিটরিতে ডেলিগেট করে।
 */
class ReelsRepository(
    private val context: Context,
    val vps1Service: ReelsApiService = ReelsApiClient.vps1Service,
    val vps2UploadService: ReelsApiService = ReelsApiClient.vps2UploadService,
    val authRepository: AuthRepository = AuthRepository(context)
) {
    companion object {
        const val MAX_REEL_DURATION_MS = ReelMediaHelper.MAX_REEL_DURATION_MS
        const val MAX_REEL_SIZE_BYTES = ReelMediaHelper.MAX_REEL_SIZE_BYTES
        const val MAX_SERIES_DURATION_MS = ReelMediaHelper.MAX_SERIES_DURATION_MS
        const val MAX_SERIES_SIZE_BYTES = ReelMediaHelper.MAX_SERIES_SIZE_BYTES
    }

    // 🧱 ৪টি ডেডিকেটেড সাব-রিপোজিটরি ইনস্ট্যান্স
    val creatorProfileRepository = CreatorProfileRepository(context, vps1Service, vps2UploadService, authRepository)
    val socialHubRepository = SocialHubRepository(context, vps1Service, authRepository)
    val seriesPlaylistRepository = SeriesPlaylistRepository(context, vps1Service, vps2UploadService, authRepository, creatorProfileRepository)
    val reelFeedRepository = ReelFeedRepository(context, vps1Service, vps2UploadService, authRepository, creatorProfileRepository)

    // =========================================================================
    // 🔐 AUTH & USER ID
    // =========================================================================
    fun getCurrentUserId(): Int = creatorProfileRepository.getCurrentUserId()

    // =========================================================================
    // 👤 ১. CREATOR & USER PROFILES DELEGATIONS
    // =========================================================================
    fun isCreatorFollowed(pageId: Long, userId: Int): Boolean =
        creatorProfileRepository.isCreatorFollowed(pageId, userId)

    fun isCreatorFollowed(pageId: Int, userId: Int): Boolean =
        creatorProfileRepository.isCreatorFollowed(pageId, userId)

    suspend fun getPublicCreatorProfile(pageId: Long): Result<PublicCreatorProfileDto> =
        creatorProfileRepository.getPublicCreatorProfile(pageId)

    suspend fun getUserRegularProfile(targetUserId: Int): Result<RegularUserProfileDto> =
        creatorProfileRepository.getUserRegularProfile(targetUserId)

    suspend fun getUserProfileMetrics(targetUserId: Int): Result<UserProfileMetricsDto?> =
        creatorProfileRepository.getUserProfileMetrics(targetUserId)

    suspend fun getSuggestedPages(): Result<List<SuggestedPageDto>> =
        creatorProfileRepository.getSuggestedPages()

    suspend fun toggleFollowPage(pageId: Long, targetUserId: Int = 0): Result<Boolean> =
        creatorProfileRepository.toggleFollowPage(pageId, targetUserId)

    suspend fun toggleFollowPage(pageId: Int, targetUserId: Int = pageId): Result<Boolean> =
        creatorProfileRepository.toggleFollowPage(pageId, targetUserId)

    suspend fun updateCreatorPageProfile(
        pageId: Int,
        pageName: String,
        handle: String,
        bio: String?,
        customLink: String?,
        avatarUri: Uri?,
        fallbackUserId: Int = 0
    ): Result<ApplyPageResponse> =
        creatorProfileRepository.updateCreatorPageProfile(pageId, pageName, handle, bio, customLink, avatarUri, fallbackUserId)

    suspend fun getMyCreatorPage(): Result<CreatorPageDto?> =
        creatorProfileRepository.getMyCreatorPage()

    suspend fun uploadUserAvatar(imageUri: Uri, fallbackUserId: Int = 0): Result<String> =
        creatorProfileRepository.uploadUserAvatar(imageUri, fallbackUserId)

    suspend fun uploadUserCover(imageUri: Uri, fallbackUserId: Int = 0): Result<String> =
        creatorProfileRepository.uploadUserCover(imageUri, fallbackUserId)

    // =========================================================================
    // 🤝 ২. SOCIAL HUB, FRIENDS & ACTIVITIES DELEGATIONS
    // =========================================================================
    suspend fun toggleFriend(targetUserId: Int): Result<Boolean> =
        socialHubRepository.toggleFriend(targetUserId)

    suspend fun getSocialActivities(): Result<SocialActivitiesResponse> =
        socialHubRepository.getSocialActivities()

    suspend fun getFriendRequests(): Result<FriendRequestsResponse> =
        socialHubRepository.getFriendRequests()

    suspend fun handleFriendRequest(requestId: Int, cmd: String): Result<Boolean> =
        socialHubRepository.handleFriendRequest(requestId, cmd)

    suspend fun getConfirmedFriends(targetUserId: Int? = null): Result<List<ConfirmedFriendDto>> =
        socialHubRepository.getConfirmedFriends(targetUserId)

    // =========================================================================
    // 📺 ৩. SERIES & PLAYLISTS DELEGATIONS
    // =========================================================================
    suspend fun createSeriesWorkflow(
        pageId: Long,
        title: String,
        description: String?,
        posterUri: Uri?,
        bannerUri: Uri?
    ): Result<CreatePlaylistResponse> =
        seriesPlaylistRepository.createSeriesWorkflow(pageId, title, description, posterUri, bannerUri)

    suspend fun createPlaylist(
        pageId: Long,
        title: String,
        description: String? = null,
        coverUrl: String? = null
    ): Result<CreatePlaylistResponse> =
        seriesPlaylistRepository.createPlaylist(pageId, title, description, coverUrl)

    suspend fun createPlaylist(
        pageId: Int,
        title: String,
        description: String? = null,
        coverUrl: String? = null
    ): Result<CreatePlaylistResponse> =
        seriesPlaylistRepository.createPlaylist(pageId, title, description, coverUrl)

    suspend fun getPlaylists(pageId: Long): Result<List<CreatorPlaylistDto>> =
        seriesPlaylistRepository.getPlaylists(pageId)

    suspend fun getPlaylists(pageId: Int): Result<List<CreatorPlaylistDto>> =
        seriesPlaylistRepository.getPlaylists(pageId)

    suspend fun getPlaylistReels(playlistId: Int): Result<List<UserReelDto>> =
        seriesPlaylistRepository.getPlaylistReels(playlistId)

    // =========================================================================
    // 🎬 ৪. REELS FEED, ALGORITHM, HASHTAGS & UPLOAD DELEGATIONS
    // =========================================================================
    suspend fun getReelsFeed(tab: String = "for_you", page: Int = 1): Result<List<UserReelDto>> =
        reelFeedRepository.getReelsFeed(tab, page)

    suspend fun trackReelWatch(
        reelId: Int,
        watchTimeSec: Int,
        isCompleted: Boolean,
        isSkipped: Boolean,
        isRewatch: Boolean
    ): Result<Boolean> =
        reelFeedRepository.trackReelWatch(reelId, watchTimeSec, isCompleted, isSkipped, isRewatch)

    suspend fun getTrendingHashtags(): Result<List<TrendingHashtagDto>> =
        reelFeedRepository.getTrendingHashtags()

    suspend fun getHashtagReels(tag: String, page: Int = 1): Result<HashtagDetailResponse> =
        reelFeedRepository.getHashtagReels(tag, page)

    suspend fun searchReelsWithCategory(
        query: String? = null,
        category: String? = null,
        page: Int = 1
    ): Result<List<UserReelDto>> =
        reelFeedRepository.searchReelsWithCategory(query, category, page)

    suspend fun uploadReel(
        pageId: Long,
        title: String?,
        description: String?,
        playlistId: Int? = null,
        episodeNum: Int = 1,
        videoUri: Uri,
        onProgressUpdate: (percent: Int) -> Unit
    ): Result<ReelUploadResponse> =
        reelFeedRepository.uploadReel(pageId, title, description, playlistId, episodeNum, videoUri, onProgressUpdate)

    suspend fun uploadReel(
        pageId: Int,
        title: String?,
        description: String?,
        playlistId: Int? = null,
        episodeNum: Int = 1,
        videoUri: Uri,
        onProgressUpdate: (percent: Int) -> Unit
    ): Result<ReelUploadResponse> =
        reelFeedRepository.uploadReel(pageId, title, description, playlistId, episodeNum, videoUri, onProgressUpdate)

    // =========================================================================
    // ❤️ ৫. SOCIAL INTERACTIONS, SAVES & COMMENTS DELEGATIONS
    // =========================================================================
    suspend fun interactReel(reelId: Int, type: String): Result<ReelInteractionResponse> =
        reelFeedRepository.interactReel(reelId, type)

    suspend fun toggleRepost(reelId: Int, caption: String? = null): Result<ToggleRepostResponse> =
        reelFeedRepository.toggleRepost(reelId, caption)

    suspend fun toggleSaveReel(reelId: Int): Result<ToggleSaveReelResponse> =
        reelFeedRepository.toggleSaveReel(reelId)

    suspend fun getSavedReels(targetUserId: Int? = null): Result<List<UserReelDto>> =
        reelFeedRepository.getSavedReels(targetUserId)

    suspend fun recordShare(reelId: Int, platform: String = "direct"): Result<RecordShareResponse> =
        reelFeedRepository.recordShare(reelId, platform)

    suspend fun getReelComments(reelId: Int): Result<List<ReelCommentDto>> =
        reelFeedRepository.getReelComments(reelId)

    suspend fun addReelComment(reelId: Int, text: String, parentId: Int? = null): Result<ReelCommentDto> =
        reelFeedRepository.addReelComment(reelId, text, parentId)

    suspend fun toggleCommentLike(commentId: Int): Result<ToggleCommentLikeResponse> =
        reelFeedRepository.toggleCommentLike(commentId)
}
