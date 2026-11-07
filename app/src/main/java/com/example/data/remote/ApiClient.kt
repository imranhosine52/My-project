package com.example.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val BASE_URL = "https://www.playdramaflix.com/api/v1/"

    // 🎯 সেশন এক্সপায়ার / ডিভাইস লিমিট অতিক্রান্ত হলে অটো-লগআউট লিসেনার
    var onSessionExpiredListener: (() -> Unit)? = null
    var authTokenProvider: (() -> String?)? = null

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    // 🛡️ অটোমেটিক Bearer Token ইনজেকশন ও 401 সেশন এক্সপায়ারি ইন্টারসেপ্টর
    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()

        // টোকেন থাকলে স্বয়ংক্রিয়ভাবে হেডারে যোগ হবে
        val token = authTokenProvider?.invoke()
        if (!token.isNullOrBlank() && originalRequest.header("Authorization") == null) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        val response = chain.proceed(requestBuilder.build())

        // 🛑 অন্য ডিভাইসে লগইন করার কারণে যদি সার্ভার 401 পাঠায়, তাহলে অ্যাপ স্বয়ংক্রিয়ভাবে সেশন এক্সপায়ারের সিগন্যাল পাবে
        if (response.code == 401) {
            onSessionExpiredListener?.invoke()
        }

        response
    }

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    val apiService: PlayDramaFlixApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(PlayDramaFlixApiService::class.java)
    }
}
