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

    @POST("https://www.playdramaflix.com/api/v1/subscription/submit")
    suspend fun submitSubscriptionV1Direct(
        @Body request: SubscriptionSubmitRequest,
        @Header("Authorization") authHeader: String? = null
    ): Response<SubscriptionSubmitResponse>

    @POST("https://www.playdramaflix.com/api/subscription/submit")
    suspend fun submitSubscriptionRootDirect(
        @Body request: SubscriptionSubmitRequest,
        @Header("Authorization") authHeader: String? = null
    ): Response<SubscriptionSubmitResponse>

    @FormUrlEncoded
    @POST("https://www.playdramaflix.com/api/v1/subscription/submit")
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
    @POST("https://www.playdramaflix.com/ajax/subscription.php")
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

    @GET("comments")
    suspend fun getComments(
        @Query("content_id") contentId: Any,
        @Query("episode_id") episodeId: Any? = null,
        @Query("user_id") userId: Any? = null
    ): Response<CommentsListResponse>

    @GET("https://www.playdramaflix.com/ajax/like_comment.php")
    suspend fun getCommentsAjax(
        @Query("action") action: String = "get_comments",
        @Query("content_id") contentId: Any,
        @Query("episode_id") episodeId: Any? = null
    ): Response<CommentsListResponse>

    @POST("comments/add")
    suspend fun postComment(
        @Body request: AddCommentApiRequest
    ): Response<AddCommentResponse>

    @FormUrlEncoded
    @POST("https://www.playdramaflix.com/ajax/like_comment.php")
    suspend fun postCommentAjax(
        @Field("action") action: String = "add_comment",
        @Field("content_id") contentId: Any,
        @Field("episode_id") episodeId: Any? = null,
        @Field("parent_id") parentId: Any? = null,
        @Field("user_id") userId: Any? = null,
        @Field("user_name") userName: String? = null,
        @Field("comment_text") commentText: String
    ): Response<AddCommentResponse>

    @POST("comments/like")
    suspend fun toggleCommentLike(
        @Body request: CommentLikeApiRequest
    ): Response<CommentLikeApiResponse>

    @POST("comments/share")
    suspend fun recordCommentShare(
        @Body request: CommentShareApiRequest
    ): Response<CommentShareApiResponse>

    @GET("app/version-check")
    suspend fun checkAppVersion(
        @Query("current_version") currentVersion: String
    ): Response<AppVersionCheckResponse>

    @POST("auth/google")
    suspend fun authenticateGoogle(
        @Body request: GoogleAuthRequest
    ): Response<GoogleAuthResponse>

    @POST("https://www.playdramaflix.com/api/v1/auth/google")
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

    @GET("ads-config")
    suspend fun getAdsConfig(): Response<AdsConfigResponse>

    @GET("https://www.playdramaflix.com/api/v1/ads-config")
    suspend fun getAdsConfigDirect(): Response<AdsConfigResponse>

    @GET("activity")
    suspend fun getUserActivity(
        @Query("user_id") userId: Any,
        @Query("type") type: String = "all"
    ): Response<UserActivityResponse>

    @GET("https://www.playdramaflix.com/api/v1/activity")
    suspend fun getUserActivityDirect(
        @Query("user_id") userId: Any,
        @Query("type") type: String = "all"
    ): Response<UserActivityResponse>

    @GET("custom-ads")
    suspend fun getCustomAdsConfig(): Response<CustomAdsConfigResponse>

    @POST("custom-ads/track")
    suspend fun trackCustomAdEvent(
        @Body request: TrackAdEventRequest
    ): Response<Map<String, Any>>

    @GET("analytics/public-live")
    suspend fun getPublicLiveAnalytics(): Response<PublicLiveAnalyticsResponse>

    // =========================================================================
    // 💬 15. TELEGRAM-STYLE 1-TO-1 PRIVATE CHAT & INBOX ENDPOINTS
    // =========================================================================

    // ১. ইনবক্স তালিকা ফেচ
    @GET("chat/inbox")
    suspend fun getPrivateInbox(
        @Query("user_id") userId: String
    ): Response<InboxResponse>

    // ২. নির্দিষ্ট চ্যাটরুমের মেসেজ ডেল্টা সিঙ্ক (last_id দিয়ে অফলাইন-ফার্স্ট সিঙ্ক)
    @GET("chat/messages")
    suspend fun getPrivateMessages(
        @Query("chat_room_id") chatRoomId: String,
        @Query("last_id") lastId: Long = 0L,
        @Query("limit") limit: Int = 50
    ): Response<PrivateMessagesResponse>

    // ৩. মেসেজ পাঠানো (টেক্সট, মিডিয়া বা ভয়েস)
    @POST("chat/send")
    suspend fun sendPrivateMessage(
        @Body request: SendPrivateMessageRequest
    ): Response<SendPrivateMessageResponse>

    // ৪. মেসেজ রিড / সিন (ডাবল ব্লু-টিক কনফার্মেশন)
    @POST("chat/seen")
    suspend fun markChatAsSeen(
        @Body request: MarkSeenRequest
    ): Response<GenericChatActionResponse>

    // ৫. 🔍 নতুন: ৮-ডিজিট একাউন্ট আইডি বা কুয়েরি দিয়ে ইউজার সার্চ
    @GET("chat/search-user")
    suspend fun searchUser(
        @Query("account_id") accountId: String
    ): Response<UserSearchResponse>
}
