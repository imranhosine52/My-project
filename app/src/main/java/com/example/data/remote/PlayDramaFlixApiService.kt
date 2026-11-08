package com.example.data.remote

import com.example.data.model.*
import retrofit2.Response
import retrofit2.http.*

/**
 * 📡 PlayDramaFlix Central REST API Service Interface
 * All endpoints match backend `routes.php` and controller actions precisely.
 */
interface PlayDramaFlixApiService {

    // =========================================================================
    // 🎬 ১. কন্টেন্ট ফিড ও ড্রামা স্ট্রিমিং ডেটা
    // =========================================================================

    @GET("contents")
    suspend fun getContents(
        @Query("country") country: String? = null,
        @Query("type") type: String? = null,
        @Query("category") category: String? = null,
        @Query("language") language: String? = null,
        @Query("sort") sort: String? = null,
        @Query("search") search: String? = null,
        @Query("q") q: String? = null,
        @Query("page") page: Int? = 1
    ): Response<ContentResponse>

    @GET("watch/{slug}")
    suspend fun getWatchDetails(
        @Path("slug") slug: String
    ): Response<WatchDetailResponse>

    // =========================================================================
    // 👀 ২. ভিডিও ভিউ, লাইক ও মাল্টি-ইমোজি রিঅ্যাকশন (Interaction API)
    // =========================================================================

    @POST("interaction/view")
    suspend fun recordVideoView(
        @Body request: ViewIncrementRequest
    ): Response<ViewIncrementResponse>

    @POST("interaction/like")
    suspend fun toggleInteractionLike(
        @Body request: LikeToggleRequest
    ): Response<LikeToggleResponse>

    @POST("interaction/react")
    suspend fun toggleReaction(
        @Body request: ReactionToggleRequest
    ): Response<ReactionToggleResponse>

    @GET("interaction/status")
    suspend fun getInteractionStatus(
        @Query("content_id") contentId: Any,
        @Query("episode_id") episodeId: Any? = null,
        @Query("user_id") userId: Any? = null
    ): Response<InteractionStatusResponse>

    // =========================================================================
    // 💬 ৩. কমেন্ট, রিপ্লাই, কমেন্ট লাইক ও শেয়ার
    // =========================================================================

    @GET("comments")
    suspend fun getComments(
        @Query("content_id") contentId: Any,
        @Query("episode_id") episodeId: Any? = null,
        @Query("user_id") userId: Any? = null
    ): Response<CommentsListResponse>

    @POST("comments/add")
    suspend fun postComment(
        @Body request: AddCommentApiRequest
    ): Response<AddCommentResponse>

    @POST("comments/like")
    suspend fun toggleCommentLike(
        @Body request: CommentLikeApiRequest
    ): Response<CommentLikeApiResponse>

    @POST("comments/share")
    suspend fun recordCommentShare(
        @Body request: CommentShareApiRequest
    ): Response<CommentShareApiResponse>

    // =========================================================================
    // 👤 ৪. ইউজার অথেন্টিকেশন ও প্রোফাইল
    // =========================================================================

    @POST("auth/google")
    suspend fun authenticateGoogle(
        @Body request: GoogleAuthRequest
    ): Response<GoogleAuthResponse>

    @POST("auth/register")
    suspend fun registerUser(
        @Body request: AuthRegisterRequest
    ): Response<AuthResponse>

    @POST("auth/login")
    suspend fun loginUser(
        @Body request: AuthLoginRequest
    ): Response<AuthResponse>

    @GET("auth/profile")
    suspend fun getUserProfile(
        @Query("user_id") userId: String
    ): Response<UserProfileResponse>

    // =========================================================================
    // 👑 ৫. সাবস্ক্রিপশন, VIP পেমেন্ট ও প্রমো কোড
    // =========================================================================

    @GET("subscription/plans")
    suspend fun getSubscriptionPlans(): Response<SubscriptionPlansResponse>

    @POST("subscription/submit")
    suspend fun submitSubscription(
        @Body request: SubscriptionSubmitRequest,
        @Header("Authorization") authHeader: String? = null
    ): Response<SubscriptionSubmitResponse>

    @GET("subscription/status")
    suspend fun getSubscriptionStatus(
        @Query("user_id") userId: String? = null,
        @Query("device_id") deviceId: String? = null,
        @Query("trx_id") trxId: String? = null,
        @Query("account_id") accountId: String? = null,
        @Query("email") email: String? = null
    ): Response<SubscriptionStatusResponse>

    @POST("subscription/redeem-promo")
    suspend fun redeemPromoCode(
        @Body request: PromoCodeRedeemRequest
    ): Response<PromoCodeRedeemResponse>

    // =========================================================================
    // 📡 ৬. বিজ্ঞাপন কনফিগারেশন ও ট্র্যাকিং
    // =========================================================================

    @GET("ads-config")
    suspend fun getAdsConfig(): Response<AdsConfigResponse>

    @GET("custom-ads")
    suspend fun getCustomAdsConfig(): Response<CustomAdsConfigResponse>

    @POST("custom-ads/track")
    suspend fun trackCustomAdEvent(
        @Body request: TrackAdEventRequest
    ): Response<Map<String, Any>>

    // =========================================================================
    // 📱 ৭. অ্যাপ ভার্সন, ডিভাইস ও নোটিফিকেশন
    // =========================================================================

    @GET("app/version-check")
    suspend fun checkAppVersion(
        @Query("current_version") currentVersion: String
    ): Response<AppVersionCheckResponse>

    @POST("devices/register")
    suspend fun registerDevice(
        @Body request: DeviceRegisterRequest
    ): Response<Map<String, Any>>

    @GET("notifications")
    suspend fun getNotifications(): Response<NotificationResponse>

    // =========================================================================
    // 📊 ৮. অ্যানালিটিক্স ও ইউজার অ্যাক্টিভিটি
    // =========================================================================

    @GET("analytics/public-live")
    suspend fun getPublicLiveAnalytics(): Response<PublicLiveAnalyticsResponse>

    @GET("activity")
    suspend fun getUserActivity(
        @Query("user_id") userId: Any,
        @Query("type") type: String = "all"
    ): Response<UserActivityResponse>

    // =========================================================================
    // 🛡️ Backward Compatibility Aliases (রিমুভ করার পূর্বে সেফটি ট্রানজিশন)
    // =========================================================================

    @Deprecated("Use authenticateGoogle directly", ReplaceWith("authenticateGoogle(request)"))
    @POST("auth/google")
    suspend fun authenticateGoogleDirect(@Body request: GoogleAuthRequest) = authenticateGoogle(request)

    @Deprecated("Use getAdsConfig directly", ReplaceWith("getAdsConfig()"))
    @GET("ads-config")
    suspend fun getAdsConfigDirect() = getAdsConfig()

    @Deprecated("Use getUserActivity directly", ReplaceWith("getUserActivity(userId, type)"))
    @GET("activity")
    suspend fun getUserActivityDirect(@Query("user_id") userId: Any, @Query("type") type: String = "all") = getUserActivity(userId, type)
}
