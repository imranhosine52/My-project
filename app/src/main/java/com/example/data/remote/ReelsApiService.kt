package com.example.data.remote

import com.example.data.model.MyPageResponse
import com.example.data.model.PageFollowResponse
import com.example.data.model.ReelInteractionResponse
import com.example.data.model.ReelUploadResponse
import com.example.data.model.ReelsFeedResponse
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
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit

/**
 * 📊 লাইভ আপলোড প্রোগ্রেস (০% - ১০০%) ট্র্যাকিং RequestBody
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

/**
 * 📡 Reels & Creator API Interface
 */
interface ReelsApiService {

    // =========================================================================
    // 🌐 VPS 1: ফিড, লাইক, ভিউ, শেয়ার ও পেজ স্ট্যাটাস
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

    @GET("tiktok-manager.php")
    suspend fun getMyCreatorPage(
        @Query("action") action: String = "get_my_page",
        @Query("user_id") userId: Int
    ): Response<MyPageResponse>

    @FormUrlEncoded
    @POST("tiktok-manager.php")
    suspend fun toggleFollowPage(
        @Field("action") action: String = "toggle_follow_page",
        @Field("page_id") pageId: Int,
        @Field("user_id") userId: Int
    ): Response<PageFollowResponse>

    /**
     * 🛠️ ক্রিয়েটর পেজ প্রোফাইল এডিট API
     * URL: https://playdramaflix.com/api/v1/tiktok-manager.php
     */
    @Multipart
    @POST("tiktok-manager.php?action=update_page")
    suspend fun updateCreatorPageProfile(
        @Part("user_id") userId: RequestBody,
        @Part("page_id") pageId: RequestBody,
        @Part("page_name") pageName: RequestBody,
        @Part("handle") handle: RequestBody,
        @Part("bio") bio: RequestBody?,
        @Part("custom_link") customLink: RequestBody?,
        @Part avatar: MultipartBody.Part? = null
    ): Response<ApplyPageResponse>

    // =========================================================================
    // 🚀 VPS 2: সম্পূর্ণ ফেসবুক/ইনস্টাগ্রাম রিলস আপলোড কন্ট্রাক্ট
    // =========================================================================
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
        @Part video: MultipartBody.Part,
        @Part customThumb: MultipartBody.Part? = null
    ): Response<ReelUploadResponse>
}

/**
 * ⚡ Dual Base URL API Client Factory
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
            level = HttpLoggingInterceptor.Level.HEADERS
        }

        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(180, TimeUnit.SECONDS) // বড় ফাইল আপলোডের জন্য ১৮০ সেকেন্ড
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
