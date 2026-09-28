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
 * 📊 আপলোড প্রোগ্রেস (০% - ১০০%) পরিমাপ করার জন্য কাস্টম RequestBody
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
        val buffer = ByteArray(8 * 1024)
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
    // 🌐 VPS 1: রিলস ফিড, ইন্টারঅ্যাকশন ও পেজ স্ট্যাটাস API
    // =========================================================================

    /**
     * রিলস ফিড ফেচিং (For You / Following)
     * URL: https://playdramaflix.com/api/v1/tiktok-manager.php?action=get_reels
     */
    @GET("tiktok-manager.php")
    suspend fun getReelsFeed(
        @Query("action") action: String = "get_reels",
        @Query("tab") tab: String = "for_you",
        @Query("user_id") userId: Int? = null,
        @Query("page") page: Int = 1
    ): Response<ReelsFeedResponse>

    /**
     * রিলস লাইক / ভিউ / শেয়ার ট্র্যাকিং
     * URL: https://playdramaflix.com/api/v1/tiktok-manager.php
     * Parameters: action=interact_reel, reel_id, user_id, type ("view", "like", "share")
     */
    @FormUrlEncoded
    @POST("tiktok-manager.php")
    suspend fun interactReel(
        @Field("action") action: String = "interact_reel",
        @Field("reel_id") reelId: Int,
        @Field("user_id") userId: Int,
        @Field("type") type: String
    ): Response<ReelInteractionResponse>

    /**
     * ক্রিয়েটর পেজ স্ট্যাটাস চেক
     * URL: https://playdramaflix.com/api/v1/tiktok-manager.php?action=get_my_page&user_id={user_id}
     */
    @GET("tiktok-manager.php")
    suspend fun getMyCreatorPage(
        @Query("action") action: String = "get_my_page",
        @Query("user_id") userId: Int
    ): Response<MyPageResponse>

    /**
     * ক্রিয়েটর পেজ ফলো / আনফলো
     */
    @FormUrlEncoded
    @POST("tiktok-manager.php")
    suspend fun toggleFollowPage(
        @Field("action") action: String = "toggle_follow_page",
        @Field("page_id") pageId: Int,
        @Field("user_id") userId: Int
    ): Response<PageFollowResponse>

    // =========================================================================
    // 🚀 VPS 2: ভিডিও আপলোড ট্রান্সকোডার ইঞ্জিন API
    // =========================================================================

    /**
     * রিলস ভিডিও আপলোড (VPS 2)
     * URL: https://api.playdramaflix.com/api/v1/upload-reel
     */
    @Multipart
    @POST("upload-reel")
    suspend fun uploadReel(
        @Part("user_id") userId: RequestBody,
        @Part("page_id") pageId: RequestBody,
        @Part("title") title: RequestBody?,
        @Part("description") description: RequestBody?,
        @Part video: MultipartBody.Part
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
            level = HttpLoggingInterceptor.Level.BODY
        }

        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS) // বড় ভিডিও ফাইল আপলোডের জন্য ১২০ সেকেন্ড
            .build()
    }

    // VPS 1 সার্ভিস (ফিড এবং ইন্টারঅ্যাকশনের জন্য)
    val vps1Service: ReelsApiService by lazy {
        Retrofit.Builder()
            .baseUrl(VPS1_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ReelsApiService::class.java)
    }

    // VPS 2 সার্ভিস (শুধুমাত্র ভিডিও আপলোড ও ট্রান্সকোডিংয়ের জন্য)
    val vps2UploadService: ReelsApiService by lazy {
        Retrofit.Builder()
            .baseUrl(VPS2_UPLOAD_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ReelsApiService::class.java)
    }
}
