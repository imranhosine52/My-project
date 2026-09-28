package com.example.util

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.example.data.model.UserReelDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

@OptIn(UnstableApi::class)
object ReelsCachePreloadManager {
    private const val CACHE_DIR_NAME = "reels_video_rolling_cache"
    private const val CHUNK_WINDOW_SIZE = 10 // একবারে ১০টি ভিডিও

    private val preloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ExoPlayer-এর জন্য 300MB ডেডিকেটেড ক্যাশ
    private var simpleCache: SimpleCache? = null

    // সম্পূর্ণ অফলাইনে প্লে করার জন্য লোকাল টেম্পোরারি ফাইল ট্র্যাকার
    private val downloadedFilesMap = mutableMapOf<Int, File>()
    private var lastLoadedWindowIndex = -1

    fun initCache(context: Context) {
        if (simpleCache == null) {
            val cacheFolder = File(context.cacheDir, "media3_reels_cache")
            val evictor = LeastRecentlyUsedCacheEvictor(300L * 1024L * 1024L) // 300 MB
            val databaseProvider = StandaloneDatabaseProvider(context)
            simpleCache = SimpleCache(cacheFolder, evictor, databaseProvider)
        }
    }

    fun getCacheDataSourceFactory(context: Context): CacheDataSource.Factory {
        initCache(context)
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(10000)
            .setUserAgent("DramaFlix-Reels-Preloader")

        return CacheDataSource.Factory()
            .setCache(simpleCache!!)
            .setUpstreamDataSourceFactory(httpFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /**
     * 🎯 ১০-ভিডিও রোলিং প্রিলোডার:
     * প্রথম ১০টি ডাউনলোড করে রাখবে। ইউজার ৭ নম্বরে গেলে পরবর্তী ১০টি ডাউনলোড করবে এবং পেছনের ১০টি ক্লিয়ার করবে।
     */
    fun onUserScrolledToPosition(context: Context, currentIndex: Int, allReels: List<UserReelDto>) {
        if (allReels.isEmpty()) return

        val targetWindow = currentIndex / CHUNK_WINDOW_SIZE
        if (targetWindow != lastLoadedWindowIndex) {
            lastLoadedWindowIndex = targetWindow

            val startIndex = targetWindow * CHUNK_WINDOW_SIZE
            val endIndex = (startIndex + CHUNK_WINDOW_SIZE).coerceAtMost(allReels.size)

            val currentBatch = allReels.subList(startIndex, endIndex)

            preloadScope.launch {
                // ১. পূর্ববর্তী উইন্ডোর পুরোনো ভিডিও মেমোরি ক্লিয়ার করা (যদি আগের উইন্ডো থাকে)
                cleanupOldBatch(targetWindow, allReels)

                // ২. বর্তমান উইন্ডোর ১০টি ভিডিও ব্যাকগ্রাউন্ডে হাই-স্পিডে ডাউনলোড করা
                currentBatch.forEach { reel ->
                    downloadSingleReelVideo(context, reel)
                }
            }
        }
    }

    private fun downloadSingleReelVideo(context: Context, reel: UserReelDto) {
        val urlString = reel.rawVideoUrl
        if (urlString.isBlank() || downloadedFilesMap.containsKey(reel.id)) return

        try {
            val cacheFolder = File(context.cacheDir, CACHE_DIR_NAME)
            if (!cacheFolder.exists()) cacheFolder.mkdirs()

            val targetFile = File(cacheFolder, "reel_${reel.id}.mp4")
            if (targetFile.exists() && targetFile.length() > 50_000L) {
                downloadedFilesMap[reel.id] = targetFile
                return
            }

            val connection = URL(urlString).openConnection() as HttpURLConnection
            connection.connectTimeout = 7000
            connection.readTimeout = 12000
            connection.setRequestProperty("User-Agent", "DramaFlix")
            connection.connect()

            if (connection.responseCode in 200..299) {
                val input = connection.inputStream
                val output = FileOutputStream(targetFile)
                val buffer = ByteArray(16 * 1024)
                var bytesRead: Int

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }

                output.flush()
                output.close()
                input.close()

                downloadedFilesMap[reel.id] = targetFile
            }
            connection.disconnect()
        } catch (_: Exception) {}
    }

    private fun cleanupOldBatch(currentWindow: Int, allReels: List<UserReelDto>) {
        try {
            if (currentWindow > 0) {
                val oldStart = (currentWindow - 1) * CHUNK_WINDOW_SIZE
                val oldEnd = (oldStart + CHUNK_WINDOW_SIZE).coerceAtMost(allReels.size)
                val oldBatch = allReels.subList(oldStart, oldEnd)

                oldBatch.forEach { oldReel ->
                    downloadedFilesMap.remove(oldReel.id)?.let { file ->
                        if (file.exists()) file.delete()
                    }
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * প্লেয়ারের জন্য ভিডিও ইউআরএল বা অফলাইন লোকাল ফাইল পাথ রিটার্ন করা
     */
    fun resolvePlaybackUri(reel: UserReelDto): Uri {
        val localFile = downloadedFilesMap[reel.id]
        return if (localFile != null && localFile.exists() && localFile.length() > 50_000L) {
            Uri.fromFile(localFile) // 🎯 সম্পূর্ণ অফলাইন লোকাল ফাইল
        } else {
            Uri.parse(reel.rawVideoUrl) // অনলাইন লিংক (ক্যাশ প্রক্সি দিয়ে চলবে)
        }
    }
}
