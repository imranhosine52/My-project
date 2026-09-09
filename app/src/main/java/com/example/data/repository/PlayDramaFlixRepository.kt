package com.example.data.repository

import android.content.Context
import com.example.data.local.*
import com.example.data.model.*
import com.example.data.remote.ApiClient
import com.example.data.remote.PlayDramaFlixApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 🎬 PlayDramaFlixRepository (Unified Central Facade)
 * Delegating all features to specialized, clean sub-repositories.
 */
class PlayDramaFlixRepository(
    private val context: Context,
    private val apiService: PlayDramaFlixApiService = ApiClient.apiService,
    private val database: AppDatabase = AppDatabase.getInstance(context)
) {
    // 🧱 ৫টি ডেডিকেটেড সাব-রিপোজিটরি ইনস্ট্যান্স
    val contentRepository = ContentRepository(context, apiService, database)
    val authRepository = AuthRepository(context, apiService)
    val subscriptionRepository = SubscriptionRepository(context, apiService, authRepository)
    val interactionRepository = InteractionRepository(context, apiService, database, authRepository)
    val appConfigRepository = AppConfigRepository(context, apiService)

    // =========================================================================
    // 📺 1. CONTENT & WATCH PROGRESS DELEGATIONS
    // =========================================================================
    val continueWatchingFlow: Flow<List<WatchHistoryEntity>> = contentRepository.continueWatchingFlow
    val watchlistFlow: Flow<List<WatchlistEntity>> = contentRepository.watchlistFlow

    fun isItemInWatchlist(slug: String): Flow<Boolean> = contentRepository.isItemInWatchlist(slug)
    suspend fun toggleWatchlist(item: ContentItemDto, isInList: Boolean) = contentRepository.toggleWatchlist(item, isInList)
    suspend fun saveWatchProgress(content: ContentItemDto, episode: EpisodeDto, progressMs: Long, totalDurationMs: Long) =
        contentRepository.saveWatchProgress(content, episode, progressMs, totalDurationMs)
    suspend fun clearHistory() = contentRepository.clearHistory()
    suspend fun getContents(): Result<List<ContentItemDto>> = contentRepository.getContents()
    suspend fun getWatchDetails(slug: String, fallbackContent: ContentItemDto? = null): Result<WatchDetailResponse> =
        contentRepository.getWatchDetails(slug, fallbackContent)
    fun getFallbackContents(): List<ContentItemDto> = contentRepository.getFallbackContents()
    fun getFallbackWatchDetails(slug: String, fallbackContent: ContentItemDto? = null): WatchDetailResponse =
        contentRepository.getFallbackWatchDetails(slug, fallbackContent)

    // =========================================================================
    // 🔐 2. AUTHENTICATION & PROFILE DELEGATIONS
    // =========================================================================
    fun getSavedUserId(): String = authRepository.getSavedUserId()
    fun getSavedAccountId(): String = authRepository.getSavedAccountId()
    fun getSavedAuthToken(): String? = authRepository.getSavedAuthToken()
    fun isUserLoggedIn(): Boolean = authRepository.isUserLoggedIn()
    fun isUserVip(): Boolean = authRepository.isUserVip()
    fun getSavedUserProfile(): UserProfileDto? = authRepository.getSavedUserProfile()
    fun updateUserAvatarAndName(name: String?, avatarPath: String?): UserProfileDto = authRepository.updateUserAvatarAndName(name, avatarPath)
    fun saveUserSession(
        userId: String,
        token: String? = null,
        isVip: Boolean = false,
        user: UserProfileDto? = null,
        planName: String? = null,
        expiry: String? = null,
        daysLeft: Int? = null
    ) = authRepository.saveUserSession(userId, token, isVip, user, planName, expiry, daysLeft)
    fun clearUserSession() = authRepository.clearUserSession()
    suspend fun authenticateWithGoogle(googleId: String, email: String, name: String, avatar: String?): Result<GoogleAuthResponse> =
        authRepository.authenticateWithGoogle(googleId, email, name, avatar)
    suspend fun registerUser(name: String, emailOrPhone: String, password: String): Result<AuthResponse> =
        authRepository.registerUser(name, emailOrPhone, password)
    suspend fun loginUser(emailOrPhone: String, password: String): Result<AuthResponse> =
        authRepository.loginUser(emailOrPhone, password)
    suspend fun getUserProfile(userId: String): Result<UserProfileResponse> = authRepository.getUserProfile(userId)

    // =========================================================================
    // 👑 3. SUBSCRIPTION & PAYMENT DELEGATIONS
    // =========================================================================
    fun savePendingSubscriptionRequest(req: PendingSubscriptionRequestModel) = subscriptionRepository.savePendingSubscriptionRequest(req)
    fun getPendingSubscriptionRequest(userId: String? = null): PendingSubscriptionRequestModel? = subscriptionRepository.getPendingSubscriptionRequest(userId)
    fun clearPendingSubscriptionRequest(userId: String? = null) = subscriptionRepository.clearPendingSubscriptionRequest(userId)
    fun hasPendingSubscriptionRequest(userId: String? = null): Boolean = subscriptionRepository.hasPendingSubscriptionRequest(userId)
    suspend fun getSubscriptionPlans(): Result<SubscriptionPlansResponse> = subscriptionRepository.getSubscriptionPlans()
    suspend fun submitSubscription(request: SubscriptionSubmitRequest): Result<SubscriptionSubmitResponse> = subscriptionRepository.submitSubscription(request)
    suspend fun getSubscriptionStatus(userId: String?, deviceId: String? = null): Result<SubscriptionStatusResponse> =
        subscriptionRepository.getSubscriptionStatus(userId, deviceId)
    fun getFallbackSubscriptionPlans(): SubscriptionPlansResponse = subscriptionRepository.getFallbackSubscriptionPlans()

    // =========================================================================
    // 💬 4. INTERACTIONS, STATS & COMMENTS DELEGATIONS
    // =========================================================================
    fun getDramaStatsFlow(slug: String): Flow<DramaStatsEntity?> = interactionRepository.getDramaStatsFlow(slug)
    suspend fun getOrCreateDramaStats(slug: String, initialLikes: Int, initialViews: Long): DramaStatsEntity =
        interactionRepository.getOrCreateDramaStats(slug, initialLikes, initialViews)
    suspend fun recordOrganicView(slug: String, initialViews: Long = 0L) = interactionRepository.recordOrganicView(slug, initialViews)
    suspend fun toggleOrganicLike(slug: String, initialLikes: Int = 0): DramaStatsEntity = interactionRepository.toggleOrganicLike(slug, initialLikes)
    fun shouldRecord24hView(contentId: Any): Boolean = interactionRepository.shouldRecord24hView(contentId)
    fun mark24hViewRecorded(contentId: Any, updatedViews: Long = 0L) = interactionRepository.mark24hViewRecorded(contentId, updatedViews)
    fun getCached24hViews(contentId: Any): Long = interactionRepository.getCached24hViews(contentId)
    suspend fun recordVideoInteractionView(contentId: Any, force: Boolean = false): Result<ViewIncrementResponse> =
        interactionRepository.recordVideoInteractionView(contentId, force)
    suspend fun toggleInteractionLike(contentId: Any, episodeId: Any? = null): Result<LikeToggleResponse> =
        interactionRepository.toggleInteractionLike(contentId, episodeId)
    suspend fun fetchInteractionStatus(contentId: Any, episodeId: Any? = null): Result<InteractionStatusResponse> =
        interactionRepository.fetchInteractionStatus(contentId, episodeId)
    suspend fun fetchCommentsList(contentId: Any, episodeId: Any? = null, userId: Any? = null): Result<List<DramaApiComment>> =
        interactionRepository.fetchCommentsList(contentId, episodeId, userId)
    suspend fun postNewComment(
        contentId: Any,
        episodeId: Any? = null,
        parentId: Any? = null,
        commentText: String,
        authorName: String? = null,
        userId: Any? = null,
        authorAvatar: String? = null
    ): Result<DramaApiComment> = interactionRepository.postNewComment(contentId, episodeId, parentId, commentText, authorName, userId, authorAvatar)
    suspend fun toggleCommentLike(commentId: Any, userId: Any? = null): Result<CommentLikeApiResponse> = interactionRepository.toggleCommentLike(commentId, userId)
    suspend fun recordCommentShare(commentId: Any, userId: Any? = null): Result<CommentShareApiResponse> = interactionRepository.recordCommentShare(commentId, userId)
    suspend fun getUserActivity(userId: String): Result<UserActivityResponse> = interactionRepository.getUserActivity(userId)

    // =========================================================================
    // ⚙️ 5. APP CONFIG, ADS & NOTIFICATIONS DELEGATIONS
    // =========================================================================
    fun getDeletedNotificationIds(): Set<String> = appConfigRepository.getDeletedNotificationIds()
    fun saveDeletedNotificationId(id: String) = appConfigRepository.saveDeletedNotificationId(id)
    fun saveAllDeletedNotificationIds(ids: Collection<String>) = appConfigRepository.saveAllDeletedNotificationIds(ids)
    fun getReadNotificationIds(): Set<String> = appConfigRepository.getReadNotificationIds()
    fun saveReadNotificationId(id: String) = appConfigRepository.saveReadNotificationId(id)
    fun getInstalledAppVersion(): String = appConfigRepository.getInstalledAppVersion()
    suspend fun registerDevice(token: String, oneSignalId: String? = null) = appConfigRepository.registerDevice(token, oneSignalId)
    suspend fun checkAppVersion(currentVersion: String = getInstalledAppVersion()): Result<AppVersionCheckResponse> =
        appConfigRepository.checkAppVersion(currentVersion)
    fun getCachedAdsConfig(): AdsConfigResponse = appConfigRepository.getCachedAdsConfig()
    suspend fun fetchRemoteAdsConfig(): Result<AdsConfigResponse> = appConfigRepository.fetchRemoteAdsConfig()

    suspend fun getNotifications(): Result<List<NotificationItemDto>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getNotifications()
            if (response.isSuccessful && response.body()?.data?.isNotEmpty() == true) {
                Result.success(response.body()!!.data)
            } else {
                Result.success(getFallbackNotifications())
            }
        } catch (e: Exception) {
            Result.success(getFallbackNotifications())
        }
    }

    private fun getFallbackNotifications(): List<NotificationItemDto> {
        return listOf(
            NotificationItemDto(
                rawId = "notif_1",
                title = "Filter Hindi Dubbed | Full Series All Episodes Watch Online HD",
                message = "Watch Filter Hindi Dubbed | Full Series All Episodes Watch Online HD (Hindi Dubbed)...",
                url = "/filter-hindi-dubbed-full-series",
                slug = "filter-hindi-dubbed",
                posterUrl = "https://playdramaflix.com/public/uploads/posters/1787413105_6a89c271df941.webp",
                customTimeAgo = "19h ago",
                isRead = false
            )
        )
    }
}
