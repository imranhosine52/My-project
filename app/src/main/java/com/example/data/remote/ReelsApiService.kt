package com.example.data.remote

import com.example.data.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import okio.BufferedSink
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit

/**
 * 🚀 আপলোড প্রোগ্রেস (০% থেকে ১০০%) ট্র্যাকিংয়ের জন্য কাস্টম RequestBody
 */
class CountingRequestBody(
    private val file: File,
    private val contentType: String,
    private val onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit
) : RequestBody() {

    override fun contentType() = contentType.toMediaTypeOrNull()

    override fun contentLength(): Long = file.length()

    override fun writeTo(sink: BufferedSink) {
        val totalBytes = file.length()
        val buffer = ByteArray(32 * 1024)
        var bytesWritten = 0L

        FileInputStream(file).use { inputStream ->
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                sink.write(buffer, 0, read)
                bytesWritten += read
                onProgress(bytesWritten, totalBytes)
            }
        }
    }
}

interface ReelsApiService {

    // =========================================================================
    // 👑 ১. PUBLIC CREATOR PROFILE & 8-DIGIT PAGE ID (VPS 1)
    // =========================================================================

    @GET("tiktok-manager.php")
    suspend fun getPublicProfile(
        @Query("action") action: String = "get_public_profile",
        @Query("page_id") pageId: Long,
        @Query("viewer_id") viewerId: Int
    ): Response<PublicCreatorProfileResponse>

    @FormUrlEncoded
    @POST("tiktok-manager.php")
    suspend fun toggleFollowPage(
        @Field("action") action: String = "toggle_follow_page",
        @Field("page_id") pageId: Long,
        @Field("target_user_id") targetUserId: Int = 0,
        @Field("user_id") userId: Int
    ): Response<PageFollowResponse>

    @GET("tiktok-manager.php")
    suspend fun getUserProfileMetrics(
        @Query("action") action: String = "get_user_profile",
        @Query("target_user_id") targetUserId: Int,
        @Query("page_id") pageId: Int = targetUserId,
        @Query("user_id") userId: Int = targetUserId,
        @Query("viewer_id") viewerId: Int
    ): Response<UserProfileMetricsResponse>

    @GET("tiktok-manager.php")
    suspend fun getSuggestedPages(
        @Query("action") action: String = "get_suggested_pages",
        @Query("user_id") userId: Int
    ): Response<SuggestedPagesResponse>

    @Multipart
    @POST("tiktok-manager.php?action=update_page")
    suspend fun updateCreatorPageProfile(
        @Part("action") action: RequestBody,
        @Part("user_id") userId: RequestBody,
        @Part("page_id") pageId: RequestBody,
        @Part("page_name") pageName: RequestBody,
        @Part("handle") handle: RequestBody,
        @Part("bio") bio: RequestBody?,
        @Part("custom_link") customLink: RequestBody?,
        @Part avatar: MultipartBody.Part? = null
    ): Response<ApplyPageResponse>

    @GET("tiktok-manager.php")
    suspend fun getMyCreatorPage(
        @Query("action") action: String = "get_my_page",
        @Query("user_id") userId: Int
    ): Response<MyPageResponse>

    @Multipart
    @POST("tiktok-manager.php?action=apply_page")
    suspend fun applyForCreatorPage(
        @Part("action") action: RequestBody,
        @Part("user_id") userId: RequestBody,
        @Part("page_name") pageName: RequestBody,
        @Part("handle") handle: RequestBody,
        @Part("bio") bio: RequestBody?,
        @Part avatar: MultipartBody.Part? = null
    ): Response<ApplyPageResponse>

    // =========================================================================
    // 📺 ২. MINI-DRAMA SERIES & PLAYLIST ENDPOINTS (VPS 1)
    // =========================================================================

    @GET("tiktok-manager.php")
    suspend fun getPlaylists(
        @Query("action") action: String = "get_playlists",
        @Query("page_id") pageId: Long
    ): Response<PlaylistListResponse>

    @GET("tiktok-manager.php")
    suspend fun getPlaylistReels(
        @Query("action") action: String = "get_playlist_reels",
        @Query("playlist_id") playlistId: Int
    ): Response<PlaylistReelsResponse>

    // =========================================================================
    // 🎬 ৩. REELS FEED & FYP ALGORITHM TRACKING (VPS 1)
    // =========================================================================

    @GET("tiktok-manager.php")
    suspend fun getReelsFeed(
        @Query("action") action: String = "get_reels",
        @Query("tab") tab: String = "for_you",
        @Query("user_id") userId: Int? = null,
        @Query("page") page: Int = 1
    ): Response<ReelsFeedResponse>

    @FormUrlEncoded
    @POST("tiktok-manager.php")
    suspend fun interactReel(
        @Field("action") action: String = "interact_reel",
        @Field("reel_id") reelId: Int,
        @Field("user_id") userId: Int,
        @Field("type") type: String
    ): Response<ReelInteractionResponse>

    @FormUrlEncoded
    @POST("tiktok-manager.php")
    suspend fun trackReelWatch(
        @Field("action") action: String = "track_reel_watch",
        @Field("reel_id") reelId: Int,
        @Field("user_id") userId: Int,
        @Field("watch_time_sec") watchTimeSec: Int,
        @Field("is_completed") isCompleted: Boolean,
        @Field("is_skipped") isSkipped: Boolean,
        @Field("is_rewatch") isRewatch: Boolean
    ): Response<Map<String, Any>>

    // =========================================================================
    // 💬 ৪. COMMENTS, REPOSTS, SAVES & SHARES (VPS 1)
    // =========================================================================

    @GET("tiktok-manager.php")
    suspend fun getReelComments(
        @Query("action") action: String = "get_comments",
        @Query("reel_id") reelId: Int,
        @Query("user_id") userId: Int? = null
    ): Response<ReelCommentsResponse>

    @FormUrlEncoded
    @POST("tiktok-manager.php")
    suspend fun addReelComment(
        @Field("action") action: String = "add_comment",
        @Field("reel_id") reelId: Int,
        @Field("user_id") userId: Int,
        @Field("comment_text") commentText: String,
        @Field("parent_id") parentId: Int? = null
    ): Response<AddReelCommentResponse>

    @FormUrlEncoded
    @POST("tiktok-manager.php")
    suspend fun toggleCommentLike(
        @Field("action") action: String = "toggle_comment_like",
        @Field("comment_id") commentId: Int,
        @Field("user_id") userId: Int
    ): Response<ToggleCommentLikeResponse>

    @FormUrlEncoded
    @POST("tiktok-manager.php")
    suspend fun toggleRepost(
        @Field("action") action: String = "toggle_repost",
        @Field("reel_id") reelId: Int,
        @Field("user_id") userId: Int,
        @Field("repost_caption") caption: String? = null
    ): Response<ToggleRepostResponse>

    @FormUrlEncoded
    @POST("tiktok-manager.php")
    suspend fun toggleSaveReel(
        @Field("action") action: String = "toggle_save_reel",
        @Field("reel_id") reelId: Int,
        @Field("user_id") userId: Int
    ): Response<ToggleSaveReelResponse>

    @GET("tiktok-manager.php")
    suspend fun getSavedReels(
        @Query("action") action: String = "get_saved_reels",
        @Query("user_id") userId: Int
    ): Response<SavedReelsResponse>

    @FormUrlEncoded
    @POST("tiktok-manager.php")
    suspend fun recordShare(
        @Field("action") action: String = "record_share",
        @Field("reel_id") reelId: Int,
        @Field("user_id") userId: Int,
        @Field("platform") platform: String = "direct"
    ): Response<RecordShareResponse>

    // =========================================================================
    // 🏷️ ৫. NEW: HASHTAGS EXPLORER & CATEGORY SEARCH (VPS 1)
    // =========================================================================

    /**
     * নির্দিষ্ট হ্যাশট্যাগের অধীনে থাকা সমস্ত রিলস ফেচ করা
     */
    @GET("tiktok-manager.php")
    suspend fun getHashtagReels(
        @Query("action") action: String = "get_hashtag_reels",
        @Query("tag") tag: String,
        @Query("page") page: Int = 1,
        @Query("user_id") userId: Int? = null
    ): Response<HashtagDetailResponse>

    /**
     * কি-ওয়ার্ড এবং ক্যাটাগরি (Entertainment, Comedy ইত্যাদি) ফিল্টার সহ রিলস সার্চ
     */
    @GET("tiktok-manager.php")
    suspend fun searchReels(
        @Query("action") action: String = "search_reels",
        @Query("q") query: String? = null,
        @Query("category") category: String? = null,
        @Query("page") page: Int = 1,
        @Query("user_id") userId: Int? = null
    ): Response<SearchReelsResponse>

    // =========================================================================
    // 🚀 ৬. MEDIA UPLOAD & TRANSCODING INGEST (VPS 2)
    // =========================================================================

    @Multipart
    @POST("user/upload-avatar")
    suspend fun uploadUserAvatar(
        @Part("user_id") userId: RequestBody,
        @Part image: MultipartBody.Part
    ): Response<MediaUploadResponse>

    @Multipart
    @POST("user/upload-cover")
    suspend fun uploadUserCover(
        @Part("user_id") userId: RequestBody,
        @Part image: MultipartBody.Part
    ): Response<MediaUploadResponse>

    @Multipart
    @POST("create-playlist")
    suspend fun createSeriesWorkflow(
        @Part("user_id") userId: RequestBody,
        @Part("page_id") pageId: RequestBody,
        @Part("title") title: RequestBody,
        @Part("description") description: RequestBody?,
        @Part poster: MultipartBody.Part? = null,
        @Part banner: MultipartBody.Part? = null
    ): Response<CreatePlaylistResponse>

    @Multipart
    @POST("upload-reel")
    suspend fun uploadFullReelWorkflow(
        @Part("user_id") userId: RequestBody,
        @Part("page_id") pageId: RequestBody,
        @Part("title") title: RequestBody?,
        @Part("description") description: RequestBody?,
        @Part("hashtags") hashtags: RequestBody?,
        @Part("category") category: RequestBody?,
        @Part("link_url") linkUrl: RequestBody?,
        @Part("privacy") privacy: RequestBody?,
        @Part("playlist_id") playlistId: RequestBody? = null,
        @Part("episode_num") episodeNum: RequestBody? = null,
        @Part video: MultipartBody.Part,
        @Part customThumb: MultipartBody.Part? = null
    ): Response<ReelUploadResponse>

    // =========================================================================
    // 💬 ৭. CHAT & INBOX REST APIS (VPS 2)
    // =========================================================================

    @GET("chat/conversations")
    suspend fun getInboxConversations(
        @Query("user_id") userId: Int
    ): Response<InboxConversationsResponse>

    @GET("chat/messages")
    suspend fun getChatMessages(
        @Query("conversation_id") conversationId: String,
        @Query("user_id") userId: Int
    ): Response<ChatMessagesResponse>
}

/**
 * 🌐 Retrofit Client Singletons
 */
object ReelsApiClient {
    private const val VPS1_BASE_URL = "https://playdramaflix.com/api/v1/"
    private const val VPS2_UPLOAD_URL = "https://api.playdramaflix.com/api/v1/"

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    val vps1Service: ReelsApiService by lazy {
        Retrofit.Builder()
            .baseUrl(VPS1_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ReelsApiService::class.java)
    }

    val vps2UploadService: ReelsApiService by lazy {
        Retrofit.Builder()
            .baseUrl(VPS2_UPLOAD_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ReelsApiService::class.java)
    }
}
