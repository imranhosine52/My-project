package com.example.data.remote

import com.example.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface PlayDramaFlixApiService {

    // =========================================================================
    // 🎬 কন্টেন্ট ও ড্রামা ফেচিং (দেশ, ক্যাটাগরি, ধরন ও সর্টিং ফিল্টার সহ)
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

    @GET("notifications")
    suspend fun getNotifications(): Response<NotificationResponse>

    @POST("contents/{slug}/like")
    suspend fun toggleLike(
        @Path("slug") slug: String,
        @Body request: Map<String, Boolean> = emptyMap()
    ): Response<Map<String, Any>>

    @POST("contents/{slug}/view")
    suspend fun recordView(
        @Path("slug") slug: String
    ): Response<Map<String, Any>>

    @POST("devices/register")
    suspend fun registerDevice(
        @Body request: DeviceRegisterRequest
    ): Response<Map<String, Any>>

    // =========================================================================
    // 👑 SUBSCRIPTION & VIP PAYMENTS
    // =========================================================================

    @GET("subscription/plans")
    suspend fun getSubscriptionPlans(): Response<SubscriptionPlansResponse>

    @POST("subscription/submit")
    suspend fun submitSubscription(
        @Body request: SubscriptionSubmitRequest,
        @Header("Authorization") authHeader: String? = null
    ): Response<SubscriptionSubmitResponse>

    @POST("https://playdramaflix.com/api/v1/subscription/submit")
    suspend fun submitSubscriptionV1Direct(
        @Body request: SubscriptionSubmitRequest,
        @Header("Authorization") authHeader: String? = null
    ): Response<SubscriptionSubmitResponse>

    @POST("https://playdramaflix.com/api/subscription/submit")
    suspend fun submitSubscriptionRootDirect(
        @Body request: SubscriptionSubmitRequest,
        @Header("Authorization") authHeader: String? = null
    ): Response<SubscriptionSubmitResponse>

    @FormUrlEncoded
    @POST("https://playdramaflix.com/api/v1/subscription/submit")
    suspend fun submitSubscriptionForm(
        @Field("user_id") userId: String,
        @Field("user_name") userName: String? = null,
        @Field("user_email") userEmail: String? = null,
        @Field("user_phone") userPhone: String? = null,
        @Field("plan_id") planId: String,
        @Field("package_id") packageId: String = planId,
        @Field("plan_name") planName: String? = null,
        @Field("payment_method") paymentMethod: String,
        @Field("gateway") gateway: String = paymentMethod,
        @Field("method") method: String = paymentMethod,
        @Field("trx_id") trxId: String,
        @Field("transaction_id") transactionId: String = trxId,
        @Field("sender_number") senderNumber: String,
        @Field("sender_phone") senderPhone: String = senderNumber,
        @Field("phone") phone: String = senderNumber,
        @Field("amount") amount: String,
        @Field("price") price: String = amount,
        @Field("notes") notes: String? = null,
        @Field("status") status: String = "pending",
        @Header("Authorization") authHeader: String? = null
    ): Response<SubscriptionSubmitResponse>

    @FormUrlEncoded
    @POST("https://playdramaflix.com/ajax/subscription.php")
    suspend fun submitSubscriptionAjax(
        @Field("action") action: String = "submit_payment",
        @Field("user_id") userId: String,
        @Field("user_name") userName: String? = null,
        @Field("user_email") userEmail: String? = null,
        @Field("user_phone") userPhone: String? = null,
        @Field("plan_id") planId: String,
        @Field("package_id") packageId: String = planId,
        @Field("plan_name") planName: String? = null,
        @Field("payment_method") paymentMethod: String,
        @Field("gateway") gateway: String = paymentMethod,
        @Field("trx_id") trxId: String,
        @Field("transaction_id") transactionId: String = trxId,
        @Field("sender_number") senderNumber: String,
        @Field("sender_phone") senderPhone: String = senderNumber,
        @Field("amount") amount: String,
        @Field("notes") notes: String? = null,
        @Header("Authorization") authHeader: String? = null
    ): Response<SubscriptionSubmitResponse>

    @GET("subscription/status")
    suspend fun getSubscriptionStatus(
        @Query("user_id") userId: String? = null,
        @Query("device_id") deviceId: String? = null
    ): Response<SubscriptionStatusResponse>

    // =========================================================================
    // 💬 POST ENGAGEMENT & INTERACTION ENDPOINTS
    // =========================================================================

    // 1. Video View Increment (POST /api/v1/interaction/view)
    @POST("interaction/view")
    suspend fun recordVideoView(
        @Body request: ViewIncrementRequest
    ): Response<ViewIncrementResponse>

    // 2. Like / Unlike Toggle (POST /api/v1/interaction/like)
    @POST("interaction/like")
    suspend fun toggleInteractionLike(
        @Body request: LikeToggleRequest
    ): Response<LikeToggleResponse>

    // 3. Fetch Live Interaction Status (GET /api/v1/interaction/status)
    @GET("interaction/status")
    suspend fun getInteractionStatus(
        @Query("content_id") contentId: Any,
        @Query("episode_id") episodeId: Any? = null
    ): Response<InteractionStatusResponse>

    // 4. Fetch Comments List (GET /api/v1/comments)
    @GET("comments")
    suspend fun getComments(
        @Query("content_id") contentId: Any,
        @Query("episode_id") episodeId: Any? = null,
        @Query("user_id") userId: Any? = null
    ): Response<CommentsListResponse>

    @GET("https://playdramaflix.com/ajax/like_comment.php")
    suspend fun getCommentsAjax(
        @Query("action") action: String = "get_comments",
        @Query("content_id") contentId: Any,
        @Query("episode_id") episodeId: Any? = null
    ): Response<CommentsListResponse>

    // 5. Add New Comment (POST /api/v1/comments/add)
    @POST("comments/add")
    suspend fun postComment(
        @Body request: AddCommentApiRequest
    ): Response<AddCommentResponse>

    @FormUrlEncoded
    @POST("https://playdramaflix.com/ajax/like_comment.php")
    suspend fun postCommentAjax(
        @Field("action") action: String = "add_comment",
        @Field("content_id") contentId: Any,
        @Field("episode_id") episodeId: Any? = null,
        @Field("parent_id") parentId: Any? = null,
        @Field("user_id") userId: Any? = null,
        @Field("user_name") userName: String? = null,
        @Field("comment_text") commentText: String
    ): Response<AddCommentResponse>

    // 5b. Toggle Comment Like (POST /api/v1/comments/like)
    @POST("comments/like")
    suspend fun toggleCommentLike(
        @Body request: CommentLikeApiRequest
    ): Response<CommentLikeApiResponse>

    // 5c. Record Comment Share (POST /api/v1/comments/share)
    @POST("comments/share")
    suspend fun recordCommentShare(
        @Body request: CommentShareApiRequest
    ): Response<CommentShareApiResponse>

    // 6. Remote Version Check (GET /api/v1/app/version-check)
    @GET("app/version-check")
    suspend fun checkAppVersion(
        @Query("current_version") currentVersion: String
    ): Response<AppVersionCheckResponse>

    // 7. Unified User Auth & Profile APIs
    @POST("auth/google")
    suspend fun authenticateGoogle(
        @Body request: GoogleAuthRequest
    ): Response<GoogleAuthResponse>

    @POST("https://playdramaflix.com/api/v1/auth/google")
    suspend fun authenticateGoogleDirect(
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

    // 8. Remote Ad Configuration (GET /api/v1/ads-config)
    @GET("ads-config")
    suspend fun getAdsConfig(): Response<AdsConfigResponse>

    @GET("https://playdramaflix.com/api/v1/ads-config")
    suspend fun getAdsConfigDirect(): Response<AdsConfigResponse>

    // 9. User Activity (Likes & Comments)
    @GET("activity")
    suspend fun getUserActivity(
        @Query("user_id") userId: Any,
        @Query("type") type: String = "all"
    ): Response<UserActivityResponse>

    @GET("https://playdramaflix.com/api/v1/activity")
    suspend fun getUserActivityDirect(
        @Query("user_id") userId: Any,
        @Query("type") type: String = "all"
    ): Response<UserActivityResponse>

    // =========================================================================
    // 🎬 10. কাস্টম ভিডিও বিজ্ঞাপন কনফিগারেশন ও ইভেন্ট ট্র্যাকিং
    // =========================================================================
    
    // বিজ্ঞাপন লোড করা
    @GET("custom-ads")
    suspend fun getCustomAdsConfig(): Response<CustomAdsConfigResponse>

    // বিজ্ঞাপনের ভিউ ও ক্লিক ইভেন্ট ডাটাবেজে পাঠানো
    @POST("custom-ads/track")
    suspend fun trackCustomAdEvent(
        @Body request: TrackAdEventRequest
    ): Response<Map<String, Any>>
}
