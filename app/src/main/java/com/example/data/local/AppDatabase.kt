package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        WatchHistoryEntity::class,
        WatchlistEntity::class,
        DramaStatsEntity::class,
        BrowserHistoryEntity::class,
        BrowserBookmarkEntity::class,
        CachedFeedEntity::class,        // 👈 নতুন: হোম ফিড ক্যাশ টেবিল
        CachedWatchDetailEntity::class  // 👈 নতুন: ভিডিও ও সমস্ত পর্বের লিঙ্ক ক্যাশ টেবিল
    ],
    version = 4, // 👈 টেবিল পরিবর্তনের কারণে ভার্সন ৪ করা হলো
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun dramaStatsDao(): DramaStatsDao
    abstract fun browserHistoryDao(): BrowserHistoryDao
    abstract fun browserBookmarkDao(): BrowserBookmarkDao
    abstract fun contentCacheDao(): ContentCacheDao // 👈 নতুন ক্যাশ ডাও যুক্ত করা হলো

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "playdramaflix_database"
                )
                // নতুন টেবিল তৈরির সময় অ্যাপ যেন ক্র্যাশ না করে
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
